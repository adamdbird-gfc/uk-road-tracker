package com.roadprints.capture;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.Iterator;
import java.util.NoSuchElementException;
import org.json.JSONArray;
import org.json.JSONObject;

/** Read-only, conservative adapter for the pinned Android archive schema.
 * Uses locally validated capture observations, independent of route matching. */
final class MilepostJourneyEvidence {
    static final int MAX_POINTS=10000;
    static final String REVISION="android-archive-milepost-1";
    private MilepostJourneyEvidence(){}

    static MilepostProximity.Marker candidate(JSONObject row){
        // Stage 1 is research-only, even if a caller tampers with its eligible flag.
        return new MilepostProximity.Marker(null,new MilepostProximity.Point(row.optDouble("longitude",Double.NaN),
                row.optDouble("latitude",Double.NaN)),false,String.valueOf(row.optInt("revision",1)));
    }
    static MilepostProximity.Evidence nativeArchive(JSONObject journey) throws Exception {
        return capturedEvidence(journey);
    }
    private static MilepostProximity.Evidence capturedEvidence(JSONObject journey) throws Exception {
        String mode=canonical(journey.optString("mode"));
        JSONObject source=journey.optJSONObject("source");
        String type=source==null?"":source.optString("type");
        JSONArray history=journey.optJSONArray("activity_recognition_history");
        JSONArray samples=journey.optJSONArray("capture_route_samples");
        JSONObject geometry=journey.optJSONObject("route_geometry");
        JSONArray coords=geometry==null?null:geometry.optJSONArray("coordinates");
        MilepostProximity.Outcome problem=null;
        if(!MilepostProximity.eligibleMode(mode))problem=MilepostProximity.Outcome.INELIGIBLE_MODE;
        else if(!"android_activity_capture".equals(type))problem="timeline_import".equals(type)
                ?MilepostProximity.Outcome.INSUFFICIENT_GEOMETRY:MilepostProximity.Outcome.UNTRUSTED_SOURCE;
        else if(!validDates(journey))problem=MilepostProximity.Outcome.INSUFFICIENT_GEOMETRY;
        else if(samples==null||coords==null||!"LineString".equals(geometry.optString("type"))
                ||samples.length()<3||samples.length()>MAX_POINTS||samples.length()!=coords.length())
            problem=MilepostProximity.Outcome.INSUFFICIENT_GEOMETRY;
        else {
            JSONObject savedQuality=journey.optJSONObject("recording_quality");
            if(savedQuality!=null&&!savedQuality.optBoolean("resolved"))problem=MilepostProximity.Outcome.UNRELIABLE_LOCATION;
            JSONObject capture=journey.optJSONObject("capture_validation");
            if(capture!=null&&!capture.optBoolean("resolved"))
                problem=MilepostProximity.Outcome.UNRELIABLE_LOCATION;
            JSONObject decisions=journey.optJSONObject("capture_decisions");
            if(decisions==null||!mode.equals(canonical(decisions.optString("mode_at_save")))
                    ||!"capture_activity_or_gps_context".equals(decisions.optString("mode_source")))
                problem=MilepostProximity.Outcome.UNTRUSTED_SOURCE;
            JSONArray changes=journey.optJSONArray("transport_changes");
            if(changes!=null)for(int i=0;i<changes.length();i++){
                JSONObject c=changes.optJSONObject(i);
                if(c==null||!mode.equals(canonical(c.optString("from")))||!mode.equals(canonical(c.optString("to"))))
                    problem=MilepostProximity.Outcome.UNTRUSTED_SOURCE;
            }
            // Edited geometry needs an exact original-observation -> retained-section mapping.
            // Existing archive corrections index matched edges; guessing that mapping is unsafe.
            JSONObject edits=journey.optJSONObject("journey_corrections");
            if(edits!=null&&edits.length()>0)problem=MilepostProximity.Outcome.INSUFFICIENT_GEOMETRY;
            if(problem==null)for(int i=0;i<samples.length();i++){
                JSONArray s=samples.optJSONArray(i),c=coords.optJSONArray(i);
                if(s==null||s.length()<5||c==null||c.length()<2
                        ||s.optDouble(0,Double.NaN)!=c.optDouble(0,Double.NaN)
                        ||s.optDouble(1,Double.NaN)!=c.optDouble(1,Double.NaN)){
                    problem=MilepostProximity.Outcome.UNRELIABLE_LOCATION;break;
                }
            }
            if(problem==null){
                long started=java.time.Instant.parse(journey.getString("started_at")).toEpochMilli();
                long ended=java.time.Instant.parse(journey.getString("ended_at")).toEpochMilli();
                for(int i=0;i<samples.length();i++)if(sample(samples,i).time<started||sample(samples,i).time>ended)
                    problem=MilepostProximity.Outcome.UNRELIABLE_LOCATION;
                JSONObject quality=CaptureQualityValidator.inspect(samples,!"cycling".equals(mode));
                if(!quality.optBoolean("resolved")){
                    problem=MilepostProximity.Outcome.UNRELIABLE_LOCATION;
                    JSONArray issues=quality.getJSONArray("issues");
                    for(int i=0;i<issues.length();i++)if("moving_gps_gap".equals(issues.getJSONObject(i).optString("reason")))
                        problem=MilepostProximity.Outcome.UNSUPPORTED_GAP;
                }
                // Stricter continuity than the generic displacement-dependent gap diagnostic:
                // no segment is inferred through any >120s observation silence.
                for(int i=1;i<samples.length();i++)if(sample(samples,i).time-sample(samples,i-1).time>120000)
                    problem=MilepostProximity.Outcome.UNSUPPORTED_GAP;
            }
            if(problem==null&&"cycling".equals(mode)&&(history==null||history.length()==0||history.length()>32))
                problem=MilepostProximity.Outcome.INSUFFICIENT_GEOMETRY;
            if(problem==null&&!"cycling".equals(mode)){
                java.util.List<FootTraceValidator.Sample> observations=new java.util.ArrayList<>(samples.length());
                for(int i=0;i<samples.length();i++)observations.add(sample(samples,i));
                WalkingCaptureEvidence.Result prepared=WalkingCaptureEvidence.prepare(observations);
                if(!prepared.route.resolved||prepared.route.removed>0||prepared.stationaryPoints>0||prepared.rejectedFixes>0)
                    problem=MilepostProximity.Outcome.UNRELIABLE_LOCATION;
            }
        }
        // Snapshot only the bounded observation array, never the matcher/full-road trees.
        // Digest includes all original input fields: callers must supply a single bounded archive row.
        String token="observations-v2:"+digestJson(journey);
        JSONArray retained=new JSONArray();
        if(problem==null)for(int i=0;i<samples.length();i++){
            JSONArray p=samples.getJSONArray(i),copy=new JSONArray();
            for(int k=0;k<5;k++)copy.put(p.get(k));retained.put(copy);
        }
        return new Archive(journey.optString("journey_id"),journey.optString("started_at"),journey.optString("ended_at"),mode,type,token,problem,retained,problem==null&&"cycling".equals(mode)&&history!=null?new JSONArray(history.toString()):new JSONArray());
    }
    private static boolean validDates(JSONObject j){
        try{return java.time.Instant.parse(j.getString("ended_at")).isAfter(java.time.Instant.parse(j.getString("started_at")));}
        catch(Exception invalid){return false;}
    }
    static String canonical(String mode){return "bicycle".equals(mode)?"cycling":"pedestrian".equals(mode)?"walking":mode;}
    static FootTraceValidator.Sample sample(JSONArray points,int i){
        JSONArray p=points.optJSONArray(i);
        return new FootTraceValidator.Sample(p.optDouble(0),p.optDouble(1),p.optDouble(2,-1),p.optLong(3),p.optDouble(4,-1),i);
    }
    static String digestJson(JSONObject value)throws Exception{
        MessageDigest md=MessageDigest.getInstance("SHA-256");hashValue(md,value,0);
        StringBuilder out=new StringBuilder();for(byte b:md.digest())out.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return out.toString();
    }
    private static void hashValue(MessageDigest md,Object value,int depth)throws Exception{
        if(depth>32)throw new IllegalArgumentException("Evidence nesting exceeds supported archive schema");
        if(value instanceof JSONObject){
            md.update((byte)'{');java.util.List<String> keys=new java.util.ArrayList<>();
            Iterator<String> names=((JSONObject)value).keys();while(names.hasNext())keys.add(names.next());
            java.util.Collections.sort(keys);
            for(String key:keys){hashValue(md,key,depth+1);hashValue(md,((JSONObject)value).get(key),depth+1);}
            md.update((byte)'}');
        }else if(value instanceof JSONArray){
            md.update((byte)'[');JSONArray a=(JSONArray)value;
            for(int i=0;i<a.length();i++)hashValue(md,a.get(i),depth+1);md.update((byte)']');
        }else{
            String scalar=value instanceof String?JSONObject.quote((String)value)
                    :value instanceof Number?JSONObject.numberToString((Number)value):String.valueOf(value);
            if(scalar.length()>65536)throw new IllegalArgumentException("Oversized evidence scalar");
            md.update(scalar.getBytes(StandardCharsets.UTF_8));md.update((byte)0);
        }
    }
    private static final class Archive implements MilepostProximity.Evidence {
        final String id,date,end,mode,source,token;
        final MilepostProximity.Outcome problem;
        final JSONArray samples,history;
        private java.util.List<MilepostProximity.Segment> supportedSegments;
        Archive(String id,String date,String end,String mode,String source,String token,MilepostProximity.Outcome problem,JSONArray samples,JSONArray history){
            this.id=id;this.date=date;this.end=end;this.mode=mode;this.source=source;this.token=token;this.problem=problem;this.samples=samples;this.history=history;
        }
        public String journeyId(){return id;}public String journeyDate(){return date;}public String journeyEndDate(){return end;}public String mode(){return mode;}
        public String source(){return source;}public String token(){return token;}public String validationRevision(){return REVISION;}
        public MilepostProximity.Outcome problem(){return problem;}
        private boolean cycleActivitySupports(long start,long end){
            boolean cycling=false;long previous=Long.MIN_VALUE;
            for(int i=0;i<history.length();i++){
                JSONObject event=history.optJSONObject(i);if(event==null)return false;
                long time;try{time=java.time.Instant.parse(event.getString("at_utc")).toEpochMilli();}catch(Exception invalid){return false;}
                if(time<previous)return false;previous=time;
                boolean supports="cycling".equals(event.optString("activity"))&&event.optBoolean("entering");
                if(time<=start)cycling=supports;
                else if(time<=end&&!supports)return false;
            }
            return cycling;
        }
        public Iterable<MilepostProximity.Segment> segments(){
            if(problem!=null)return Collections.emptyList();
            if(supportedSegments==null){
                java.util.List<MilepostProximity.Segment> segments=new java.util.ArrayList<>();
                for(SegmentIterator it=new SegmentIterator();it.hasNext();)segments.add(it.next());
                supportedSegments=Collections.unmodifiableList(segments);
            }
            return supportedSegments;
        }
        private final class SegmentIterator implements Iterator<MilepostProximity.Segment> {
            final java.util.List<FootTraceValidator.Sample> observations=new java.util.ArrayList<>(samples.length());
            SegmentIterator(){
                for(int i=0;i<samples.length();i++)observations.add(sample(samples,i));
            }
            int at=1;MilepostProximity.Segment next;
            public boolean hasNext(){
                while(next==null&&at<observations.size()){
                    if(Thread.currentThread().isInterrupted())throw new java.util.concurrent.CancellationException();
                    int i=at++;
                    FootTraceValidator.Sample a=observations.get(i-1),b=observations.get(i);
                    // Use time as well as point count: a normal 1Hz run needs
                    // more than three fixes to establish local movement.
                    int first=i-1,last=i;
                    while(first>0&&i-first<96&&a.time-observations.get(first-1).time<=45_000)first--;
                    while(last+1<observations.size()&&last-i<96&&observations.get(last+1).time-b.time<=45_000)last++;
                    if(last-first<2){if(first>0)first--;else if(last+1<observations.size())last++;}
                    java.util.List<FootTraceValidator.Sample> local=new java.util.ArrayList<>();
                    boolean cycle="cycling".equals(mode),vehicle=false;
                    for(int k=first;k<=last;k++){
                        FootTraceValidator.Sample p=observations.get(k);
                        if(!cycle)vehicle|=p.speed>=6;
                        if(local.isEmpty()||p.time-local.get(local.size()-1).time>=10_000||k==last)local.add(p);
                    }
                    if(vehicle||!CaptureMovementEvidence.hasJourneyMovement(local,mode))continue;
                    if("cycling".equals(mode)){
                        // Generic non-foot plausibility admits cars; retain cycling speed/provenance too.
                        boolean cycling=cycleActivitySupports(a.time,b.time);
                        for(FootTraceValidator.Sample p:local)cycling&=Double.isFinite(p.speed)&&p.speed>=.8&&p.speed<=12;
                        double elapsed=(b.time-a.time)/1000.0;
                        cycling&=FootTraceValidator.metres(a,b)<=12*elapsed+Math.min(50,a.accuracy+b.accuracy);
                        if(!cycling)continue;
                    }
                    // Local movement support does not make a stationary endpoint edge travel.
                    if(FootTraceValidator.metres(a,b)<=0)continue;
                    next=new MilepostProximity.Segment(new MilepostProximity.Point(a.lon,a.lat),
                            new MilepostProximity.Point(b.lon,b.lat),0,i-1,i);
                }
                return next!=null;
            }
            public MilepostProximity.Segment next(){if(!hasNext())throw new NoSuchElementException();MilepostProximity.Segment n=next;next=null;return n;}
        }
    }
}
