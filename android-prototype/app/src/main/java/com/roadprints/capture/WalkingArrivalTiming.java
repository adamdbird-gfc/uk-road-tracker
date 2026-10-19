package com.roadprints.capture;

import java.time.Instant;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/** Correct derived pedestrian arrival times while retaining original capture evidence. */
final class WalkingArrivalTiming {
    static final class Candidate {
        final long arrival, confirmed;
        Candidate(long arrival,long confirmed){this.arrival=arrival;this.confirmed=confirmed;}
    }
    static Candidate candidate(List<FootTraceValidator.Sample> samples,WalkingCaptureEvidence.Result prepared){
        if(prepared==null || !prepared.route.timed || !prepared.route.resolved
                || prepared.stationaryTail<=0 || prepared.stationaryTail>=samples.size())return null;
        long arrival=samples.get(samples.size()-prepared.stationaryTail-1).time;
        long confirmed=samples.get(samples.size()-1).time;
        return arrival>0 && confirmed-arrival>=CaptureStartGate.STILLNESS_END_THRESHOLD_MS
                ?new Candidate(arrival,confirmed):null;
    }
    static Candidate fromRaw(JSONArray raw,List<FootTraceValidator.Sample> aligned)throws Exception{
        if(raw==null || aligned.size()<2)return null;
        for(FootTraceValidator.Sample p:aligned)if(p.time<=0)return null;
        java.util.ArrayList<FootTraceValidator.Sample> samples=new java.util.ArrayList<>();
        long previous=0;
        for(int i=0;i<raw.length();i++){
            JSONArray p=raw.optJSONArray(i);
            if(p==null || p.optLong(3)<=previous)continue;
            previous=p.optLong(3);
            samples.add(new FootTraceValidator.Sample(p.optDouble(0),p.optDouble(1),p.optDouble(2),previous,p.optDouble(4,-1),i));
        }
        Candidate candidate=candidate(samples,WalkingCaptureEvidence.prepare(samples));
        return candidate!=null && candidate.arrival>=aligned.get(0).time
                &&candidate.arrival<=aligned.get(aligned.size()-1).time?candidate:null;
    }
    static boolean apply(JSONObject journey,Candidate candidate)throws Exception{
        JSONObject source=journey.optJSONObject("source");
        if(candidate==null || source==null || !"android_activity_capture".equals(source.optString("type"))
                || !("walking".equals(journey.optString("mode")) || "running".equals(journey.optString("mode"))))return false;
        long start=time(journey.optString("started_at")),end=time(journey.optString("ended_at"));
        if(start<=0 || end<=0 || candidate.arrival<=start || candidate.arrival>=end
                || candidate.confirmed-candidate.arrival<CaptureStartGate.STILLNESS_END_THRESHOLD_MS)return false;
        String arrival=Instant.ofEpochMilli(candidate.arrival).toString();
        if(!journey.has("original_ended_at"))journey.put("original_ended_at",journey.getString("ended_at"));
        journey.put("ended_at",arrival).put("end_time_evidence",new JSONObject().put("version",1)
                .put("source","validated_stationary_tail").put("arrived_at",arrival)
                .put("confirmed_at",Instant.ofEpochMilli(candidate.confirmed).toString()));
        JSONArray stops=journey.optJSONArray("stops");
        if(stops!=null)for(int i=0;i<stops.length();i++){
            JSONObject stop=stops.optJSONObject(i);
            if(stop==null || !"arrival".equals(stop.optString("role"))
                    || !"gps_dwell".equals(stop.optString("source"))
                    || !"estimated".equals(stop.optString("classification")))continue;
            if(stop.has("arrived_at")&&!stop.has("original_arrived_at"))stop.put("original_arrived_at",stop.get("arrived_at"));
            if(stop.has("boundary_at")&&!stop.has("original_boundary_at"))stop.put("original_boundary_at",stop.get("boundary_at"));
            stop.put("arrived_at",arrival).put("boundary_at",arrival);
            long confirmation=time(stop.optString("confirmed_at"));
            if(confirmation>=candidate.arrival)stop.put("dwell_seconds",(confirmation-candidate.arrival)/1000);
        }
        return true;
    }
    private static long time(String value){try{return Instant.parse(value).toEpochMilli();}catch(Exception invalid){return 0;}}
}
