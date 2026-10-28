package com.roadprints.capture;

import java.time.Instant;
import org.json.JSONArray;
import org.json.JSONObject;

/** Post-arrival observations qualify visits independently of the journey's travel boundary. */
final class ArrivalDwellEvidence {
    private ArrivalDwellEvidence() {}

    static boolean append(JSONObject journey,JSONObject stop,JSONArray observations) throws Exception {
        JSONObject source=journey.optJSONObject("source");
        if(source==null||!"android_activity_capture".equals(source.optString("type"))
                ||!journey.optString("journey_id").equals(stop.optString("journey_id")))return false;
        JSONArray stops=journey.optJSONArray("stops");JSONObject arrival=null;
        if(stops!=null)for(int i=0;i<stops.length();i++) {
            JSONObject candidate=stops.optJSONObject(i);
            if(candidate!=null&&"arrival".equals(candidate.optString("role"))
                    &&stop.optString("id").equals(candidate.optString("id")))arrival=candidate;
        }
        if(arrival==null||"rejected".equals(arrival.optString("classification")))return false;
        long at=Instant.parse(arrival.getString("arrived_at")).toEpochMilli();
        JSONArray existing=journey.optJSONArray("arrival_dwell_samples");
        JSONArray out=existing==null?new JSONArray():new JSONArray(existing.toString());
        JSONArray combined=samples(journey);
        long last=combined.length()==0?at-1:combined.getJSONArray(combined.length()-1).optLong(3);
        int before=out.length();
        for(int i=0;i<observations.length()&&out.length()<120;i++) {
            JSONArray row=observations.optJSONArray(i);if(row==null||row.length()<5)continue;
            long time=row.optLong(3);
            if(time<=last||time<at||time>at+600_000L)continue;
            // Keep observed speed, accuracy and gaps: the shared detector decides
            // whether they support dwell, rather than manufacturing quiet evidence.
            out.put(new JSONArray(row.toString()));last=time;
        }
        if(out.length()==before)return false;
        journey.put("arrival_dwell_samples",out);
        return true;
    }

    static JSONArray samples(JSONObject journey) {
        JSONArray raw=journey.optJSONArray("raw_capture_samples");
        if(raw==null)raw=journey.optJSONArray("capture_route_samples");
        JSONArray out=new JSONArray();long last=0;
        for(JSONArray part:new JSONArray[]{raw,journey.optJSONArray("arrival_dwell_samples")}) {
            if(part==null)continue;
            for(int i=0;i<part.length();i++) {
                JSONArray row=part.optJSONArray(i);if(row==null)continue;
                long time=row.optLong(3);
                if(time>last){out.put(row);last=time;}
            }
        }
        return out;
    }
}
