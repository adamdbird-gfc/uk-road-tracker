package com.roadprints.capture;
import org.json.*;
final class DestinationJourneyEvidence {
    static boolean purposeful(JSONObject journey) {
        if("manual".equals(journey.optString("capture_intent")))return true;
        JSONArray stops=journey.optJSONArray("stops");
        if(stops!=null)for(int i=0;i<stops.length();i++) {
            JSONObject stop=stops.optJSONObject(i);if(stop!=null&&stop.optString("departure_reason")
                .matches("purposeful_activity_within_place|sustained_activity_within_place"))return true;
        }
        return false;
    }
    static boolean internal(JSONObject place,JSONArray samples) {
        if(!DestinationPolicy.bounded(place)||samples==null||samples.length()<2)return false;
        for(int i=0;i<samples.length();i++) {
            JSONArray row=samples.optJSONArray(i);if(row==null)return false;
            FootTraceValidator.Sample p=new FootTraceValidator.Sample(row.optDouble(0),row.optDouble(1),row.optDouble(2),row.optLong(3),row.optDouble(4,-1),i);
            if(!WalkingStillnessDetector.usable(p)||p.accuracy>50||PlaceVisitEvidence.outside(place,p))return false;
        }
        return true;
    }
}
