package com.roadprints.capture;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.Iterator;
import java.util.NoSuchElementException;
import org.json.JSONArray;
import org.json.JSONObject;

/** Read-only, conservative adapter for the pinned Android archive schema.
 * This class is deliberately not called by AchievementStore/JourneyStore or any lifecycle hook. */
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
        return nativeArchive(journey,null);
    }
    /** Only the streaming archive reader supplies the independently parsed match witness. */
    static MilepostProximity.Evidence projectedArchive(JSONObject journey,boolean matchPresent)throws Exception {
        return nativeArchive(journey,matchPresent);
    }
    private static MilepostProximity.Evidence nativeArchive(JSONObject journey,Boolean matchPresent) throws Exception {
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
        else if(!"complete".equals(journey.optString("processing_status"))||!hasMatch(journey,matchPresent))
            problem=MilepostProximity.Outcome.UNVALIDATED_MATCH;
        else if(!validDates(journey))problem=MilepostProximity.Outcome.INSUFFICIENT_GEOMETRY;
        else if(samples==null||coords==null||!"LineString".equals(geometry.optString("type"))
                ||samples.length()<3||samples.length()>MAX_POINTS||samples.length()!=coords.length())
            problem=MilepostProximity.Outcome.INSUFFICIENT_GEOMETRY;
        else {
            JSONObject savedQuality=journey.optJSONObject("recording_quality");
            if(savedQuality!=null&&!savedQuality.optBoolean("resolved"))problem=MilepostProximity.Outcome.UNRELIABLE_LOCATION;
            JSONObject walking=journey.optJSONObject("walking_validation");
            if(walking!=null)for(String key:new String[]{"removed_gps_points","station_tail_points","vehicle_tail_points","stationary_points"})
                if(walking.optInt(key)>0)problem=MilepostProximity.Outcome.INSUFFICIENT_GEOMETRY;
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
        String token=digestJson(journey);
        JSONArray retained=new JSONArray();
        if(problem==null)for(int i=0;i<samples.length();i++){
            JSONArray p=samples.getJSONArray(i),copy=new JSONArray();
            for(int k=0;k<5;k++)copy.put(p.get(k));retained.put(copy);
        }
        return new Archive(journey.optString("journey_id"),journey.optString("started_at"),journey.optString("ended_at"),mode,type,token,problem,retained,problem==null&&history!=null?new JSONArray(history.toString()):new JSONArray());
    }
    private static boolean hasMatch(JSONObject j,Boolean matchPresent){
        JSONObject r=j.optJSONObject("processing_result"),g=r==null?null:r.optJSONObject("geojson");
        JSONArray f=g==null?null:g.optJSONArray("features");
        if(r==null||!"ok".equals(r.optString("status")))return false;
        if(matchPresent==null&&(f==null||f.length()==0))return false;
        for(String flag:new String[]{"walking_partial_match","capture_partial_match","timeline_partial_match","endpoint_partial_match"})
            if(r.optBoolean(flag))return false;
        JSONArray failures=r.optJSONArray("failed_sections");
        if(failures!=null&&failures.length()>0)return false;
        int input=r.optInt("input_points",-1),matched=r.optInt("matched_tracepoints",-2);
        if(input<2||input!=matched)return false;
        if("cycling".equals(canonical(j.optString("mode")))&&(!"cycling".equals(r.optString("matching_mode"))
                ||!r.optBoolean("matched_distance_is_deduplicated")))return false;
        if(matchPresent!=null)return matchPresent;
        for(int i=0;i<f.length();i++){
            JSONObject feature=f.optJSONObject(i),geometry=feature==null?null:feature.optJSONObject("geometry");
            JSONArray coordinates=geometry==null?null:geometry.optJSONArray("coordinates");
            if(geometry!=null&&"LineString".equals(geometry.optString("type"))&&coordinates!=null&&coordinates.length()>=2)return true;
        }
        return false;
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
            return ()->new Iterator<MilepostProximity.Segment>(){
                int at=1;MilepostProximity.Segment next;
                public boolean hasNext(){
                    while(next==null&&at<samples.length()){
                        int i=at++;
                        // Local three-observation witness, not an archive-wide 'was moving' flag.
                        int start=Math.max(0,i-2),end=Math.min(samples.length()-1,start+2);
                        java.util.List<FootTraceValidator.Sample> local=new java.util.ArrayList<>(3);
                        for(int k=start;k<=end;k++)local.add(sample(samples,k));
                        if(!CaptureMovementEvidence.hasJourneyMovement(local,mode))continue;
                        FootTraceValidator.Sample a=sample(samples,i-1),b=sample(samples,i);
                        if(!"cycling".equals(mode)){
                            boolean vehicle=false;for(FootTraceValidator.Sample p:local)vehicle|=p.speed>=6;
                            if(vehicle)continue;
                        }
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
            };
        }
    }
}
