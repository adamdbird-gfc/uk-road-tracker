package com.roadprints.capture;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.*;

/** Recovers optional local evidence and validates native walking recordings.
 * Timeline imports and traces without timing evidence retain their geometry. */
final class WalkingJourneyPreparation {
    static final class Prepared {
        final JSONArray points=new JSONArray(), recoveredSamples=new JSONArray();
        final JSONObject details=new JSONObject();
        boolean validated;
        final JSONArray validatedSamples=new JSONArray();
        JSONObject quality;
        double distance;
        WalkingArrivalTiming.Candidate arrival;
    }
    static Prepared prepare(Context context, JSONObject journey) throws Exception {
        JSONArray coords=journey.getJSONObject("route_geometry").getJSONArray("coordinates");
        JSONObject source=journey.optJSONObject("source");
        boolean nativeWalk=("walking".equals(journey.optString("mode")) || "running".equals(journey.optString("mode"))) && source!=null
                && "android_activity_capture".equals(source.optString("type"));
        Map<String,List<FootTraceValidator.Sample>> evidence=new HashMap<>();
        JSONArray saved=journey.optJSONArray("raw_capture_samples");
        if(saved!=null) for(int i=0;i<saved.length();i++) {
            JSONArray p=saved.optJSONArray(i);
            if(p!=null && p.length()>=4 && p.optLong(7,-1)<=60000) add(evidence,new FootTraceValidator.Sample(
                    p.optDouble(0),p.optDouble(1),p.optDouble(2),p.optLong(3),p.optDouble(4,-1),i));
        }
        List<JSONObject> activities=new ArrayList<>();
        boolean recovered=false;
        if(nativeWalk && saved==null) {
            File temp=File.createTempFile("walk-evidence-", ".txt",context.getCacheDir());
            try {
                try(OutputStream out=new FileOutputStream(temp)) { MovementDiagnostics.writeReport(context,out); }
                long start=parseTime(journey.optString("started_at")),end=parseTime(journey.optString("ended_at"));
                try(BufferedReader reader=new BufferedReader(new InputStreamReader(new FileInputStream(temp),StandardCharsets.UTF_8))) {
                    String line;
                    while((line=reader.readLine())!=null) {
                        if(!line.startsWith("{")) continue;
                        JSONObject item;
                        try { item=new JSONObject(line); } catch(Exception malformed) { continue; }
                        long time=parseTime(item.optString("timestamp_utc"));
                        if(time<start || time>end || start<=0 || end<=0) continue;
                        if("activity_transition".equals(item.optString("event"))) activities.add(item);
                        if(!"location_sample".equals(item.optString("event"))) continue;
                        String detail=item.optString("detail");
                        if(!"recording_walking".equals(detail) && !"walk_candidate".equals(detail)) continue;
                        add(evidence,new FootTraceValidator.Sample(item.optDouble("longitude"),item.optDouble("latitude"),
                                item.optDouble("accuracy_m",0),parseTime(item.optString("location_time_utc")),
                                item.optDouble("speed_mps",-1),0));
                        recovered=true;
                    }
                }
            } catch(IOException unavailable) { /* Diagnostic evidence is optional. */ }
            finally { temp.delete(); }
        }
        List<FootTraceValidator.Sample> samples=new ArrayList<>();
        long previousTime=0;
        for(int i=0;i<coords.length();i++) {
            JSONArray coord=coords.getJSONArray(i); double lon=coord.getDouble(0),lat=coord.getDouble(1);
            FootTraceValidator.Sample found=null;
            for(FootTraceValidator.Sample candidate:evidence.getOrDefault(key(lon,lat),Collections.emptyList())) {
                if(candidate.time>previousTime) { found=candidate; break; }
            }
            FootTraceValidator.Sample sample=new FootTraceValidator.Sample(lon,lat,found==null?0:found.accuracy,
                    found==null?0:found.time,found==null?-1:found.speed,i);
            if(found!=null) previousTime=found.time;
            samples.add(sample);
        }
        int stationEnd=-1;
        JSONObject prior=journey.optJSONObject("walking_validation");
        if(nativeWalk && prior!=null && prior.optInt("version")==1
                && prior.optInt("source_points")==samples.size() && prior.optInt("station_tail_points")>0) {
            long arrival=parseTime(prior.optString("station_arrival_time_utc"));
            if(arrival>0) for(int i=0;i<samples.size();i++)
                if(samples.get(i).time>0 && samples.get(i).time<=arrival) stationEnd=i;
        }
        if(nativeWalk && stationEnd<0 && recovered && !activities.isEmpty()) {
            try(InputStreamReader stations=new InputStreamReader(context.getAssets().open("rail-stations/stations.csv"),StandardCharsets.UTF_8)) {
                stationEnd=stationWaitingEnd(samples,activities,RailStationCatalog.read(stations),
                        parseTime(journey.optString("ended_at")));
            } catch(IOException unavailable) { /* Keep the full trace if public station data is unavailable. */ }
        }
        List<FootTraceValidator.Sample> leg=stationEnd>=1?new ArrayList<>(samples.subList(0,stationEnd+1)):samples;
        int vehicleTail=nativeWalk?vehicleTailStart(leg):-1;
        int vehicleTailPoints=vehicleTail>=1?leg.size()-vehicleTail:0;
        if(vehicleTail>=1)leg=new ArrayList<>(leg.subList(0,vehicleTail));
        WalkingCaptureEvidence.Result capture=nativeWalk?WalkingCaptureEvidence.prepare(leg):null;
        FootTraceValidator.Result result=capture!=null?capture.route:new FootTraceValidator.Result(leg,leg.size(),false);
        if(nativeWalk && result.timed && !result.resolved) throw new IllegalStateException(
                "This walking recording contains a GPS gap that cannot be validated. Your original recording is preserved.");
        Prepared prepared=new Prepared(); prepared.validated=nativeWalk && result.timed;
        prepared.distance=result.distance;
        prepared.arrival=prepared.validated?WalkingArrivalTiming.fromRaw(saved,leg):null;
        if(saved==null)prepared.arrival=WalkingArrivalTiming.candidate(leg,capture);
        for(FootTraceValidator.Sample point:result.samples) prepared.points.put(new JSONObject().put("lng",point.lon).put("lat",point.lat));
        for(FootTraceValidator.Sample point:result.samples) prepared.validatedSamples.put(sampleJson(point));
        prepared.quality=CaptureQualityValidator.inspect(prepared.validatedSamples,true);
        if(prepared.validated && !prepared.quality.optBoolean("resolved")) throw new IllegalStateException(
                "This walking recording has a moving GPS gap or unreliable route evidence. Your original recording is preserved.");
        if(prepared.validated) for(FootTraceValidator.Sample point:samples) prepared.recoveredSamples.put(sampleJson(point));
        prepared.details.put("version",1).put("timing_available",result.timed)
                .put("source_points",samples.size()).put("validated_points",result.samples.size())
                .put("removed_gps_points",result.removed).put("station_tail_points",stationEnd>=1?samples.size()-stationEnd-1:0)
                .put("evidence_source",saved!=null?"saved_capture_samples":recovered?"local_movement_log":"coordinates_only")
                .put("validated_gps_distance_m",result.distance)
                .put("capture_preparation_version",3).put("rejected_fixes",capture==null?0:capture.rejectedFixes)
                .put("stationary_points",capture==null?0:capture.stationaryPoints)
                .put("vehicle_tail_points",vehicleTailPoints)
                .put("stationary_tail_points",capture==null?0:capture.stationaryTail);
        if(stationEnd>=1) prepared.details.put("station_arrival_time_utc",
                java.time.Instant.ofEpochMilli(samples.get(stationEnd).time).toString());
        return prepared;
    }
    /** End foot credit at the first fix of sustained, accurate vehicle travel.
     * Unmeasured network observations cannot interrupt that evidence. */
    static int vehicleTailStart(List<FootTraceValidator.Sample> samples) {
        FootTraceValidator.Sample first=null,previous=null;
        int firstIndex=-1,count=0;double path=0;
        for(int i=0;i<samples.size();i++) {
            FootTraceValidator.Sample p=samples.get(i);
            if(!WalkingStillnessDetector.usable(p)||p.accuracy>25||!Double.isFinite(p.speed)||p.speed<0)continue;
            if(p.speed<6 || previous!=null&&(p.time-previous.time>60000
                    || !CaptureQualityValidator.reliable(previous,p,false))) {
                first=null;previous=null;firstIndex=-1;count=0;path=0;
                if(p.speed<6)continue;
            }
            if(first==null){first=p;firstIndex=i;}
            if(previous!=null)path+=CaptureStartGate.reliableMovementIncrement((float)FootTraceValidator.metres(previous,p),
                    (float)previous.accuracy,(float)p.accuracy);
            previous=p;count++;
            if(count>=3&&p.time-first.time>=20000&&path>=100) {
                List<FootTraceValidator.Sample> prefix=samples.subList(0,firstIndex);
                return CaptureMovementEvidence.hasJourneyMovement(prefix,"walking")?firstIndex:-1;
            }
        }
        return -1;
    }
    static int stationWaitingEnd(List<FootTraceValidator.Sample> samples,List<JSONObject> activities,
                                 RailStationCatalog catalog,long endedAt) {
        for(JSONObject event:activities) {
            if(!"still".equals(event.optString("activity")) || !"entered".equals(event.optString("transition"))) continue;
            long time=parseTime(event.optString("timestamp_utc"));
            if(endedAt-time<5*60000L) continue;
            int at=-1;
            for(int i=0;i<samples.size();i++) if(samples.get(i).time>0 && samples.get(i).time<=time) at=i;
            if(at<1) continue;
            FootTraceValidator.Sample anchor=samples.get(at);
            RailStationCatalog.Station station=catalog.nearest(anchor.lat,anchor.lon,(float)anchor.accuracy);
            if(station==null) continue;
            boolean waiting=true;
            for(int i=at+1;i<samples.size();i++) {
                FootTraceValidator.Sample p=samples.get(i);
                if(p.time<=0 || (p.accuracy<=50 && p.speed>=6) || RailStationCatalog.metres(p.lat,p.lon,station.latitude,station.longitude)
                        >station.radius+Math.min(80,Math.max(0,p.accuracy))) { waiting=false; break; }
            }
            if(waiting) return at;
        }
        return -1;
    }
    static JSONArray sampleJson(FootTraceValidator.Sample p) throws Exception {
        return new JSONArray().put(p.lon).put(p.lat).put(p.accuracy).put(p.time).put(p.speed);
    }
    private static void add(Map<String,List<FootTraceValidator.Sample>> evidence,FootTraceValidator.Sample p) {
        evidence.computeIfAbsent(key(p.lon,p.lat),unused->new ArrayList<>()).add(p);
    }
    private static String key(double lon,double lat) { return Math.round(lon*1e7)+":"+Math.round(lat*1e7); }
    private static long parseTime(String value) {
        try { return OffsetDateTime.parse(value).toInstant().toEpochMilli(); } catch(Exception invalid) { return 0; }
    }
}
