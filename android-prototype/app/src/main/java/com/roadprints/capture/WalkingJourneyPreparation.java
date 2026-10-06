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
        double distance;
    }
    static Prepared prepare(Context context, JSONObject journey) throws Exception {
        JSONArray coords=journey.getJSONObject("route_geometry").getJSONArray("coordinates");
        JSONObject source=journey.optJSONObject("source");
        boolean nativeWalk="walking".equals(journey.optString("mode")) && source!=null
                && "android_activity_capture".equals(source.optString("type"));
        Map<String,List<FootTraceValidator.Sample>> evidence=new HashMap<>();
        JSONArray saved=journey.optJSONArray("raw_capture_samples");
        if(saved!=null) for(int i=0;i<saved.length();i++) {
            JSONArray p=saved.optJSONArray(i);
            if(p!=null && p.length()>=4) add(evidence,new FootTraceValidator.Sample(
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
        JSONObject captureValidation=journey.optJSONObject("capture_validation");
        boolean alreadyValidated=nativeWalk && captureValidation!=null && captureValidation.optInt("version")==1
                && captureValidation.optBoolean("timing_available") && captureValidation.optBoolean("resolved",true);
        FootTraceValidator.Result result=nativeWalk && !alreadyValidated?FootTraceValidator.validate(leg)
                :new FootTraceValidator.Result(leg,leg.size(),false);
        if(nativeWalk && result.timed && !result.resolved) throw new IllegalStateException(
                "This walking recording contains a GPS gap that cannot be validated. Your original recording is preserved.");
        Prepared prepared=new Prepared(); prepared.validated=nativeWalk && (result.timed || alreadyValidated);
        prepared.distance=result.distance;
        for(FootTraceValidator.Sample point:result.samples) prepared.points.put(new JSONObject().put("lng",point.lon).put("lat",point.lat));
        if(prepared.validated) for(FootTraceValidator.Sample point:samples) prepared.recoveredSamples.put(sampleJson(point));
        prepared.details.put("version",1).put("timing_available",result.timed || alreadyValidated)
                .put("source_points",samples.size()).put("validated_points",result.samples.size())
                .put("removed_gps_points",result.removed).put("station_tail_points",samples.size()-leg.size())
                .put("evidence_source",saved!=null?"saved_capture_samples":recovered?"local_movement_log":"coordinates_only")
                .put("validated_gps_distance_m",result.distance);
        if(stationEnd>=1) prepared.details.put("station_arrival_time_utc",
                java.time.Instant.ofEpochMilli(samples.get(stationEnd).time).toString());
        return prepared;
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
