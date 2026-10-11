package com.roadprints.capture;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.HashSet;
import java.util.Set;

/** Honest non-error outcomes; no invented geometry or discovery from raw traces. */
final class WalkingMatchOutcome {
    static boolean recordedOnly(JSONObject result,int input) {
        String outcome=result.optString("walking_outcome");
        return ("no_route_found".equals(outcome)||"insufficient_detail".equals(outcome))
                && result.optInt("input_points",-1)==input && result.optInt("matched_tracepoints",-1)==0;
    }
    static boolean verifiedPartial(JSONObject result,int input) {
        int matched=result.optInt("matched_tracepoints",-1);
        double distance=result.optDouble("matched_distance_m",-1);
        if(input<2 || result.optInt("input_points",-1)!=input || matched<2 || matched>=input
                || !result.optBoolean("matched_distance_is_deduplicated")
                || !Double.isFinite(distance)||distance<=0)return false;
        JSONArray a=result.optJSONArray("matched_point_indices"),b=result.optJSONArray("unmatched_point_indices");
        if(a==null||b==null||a.length()!=matched||a.length()+b.length()!=input)return false;
        Set<Integer> seen=new HashSet<>();
        for(JSONArray list:new JSONArray[]{a,b})for(int i=0;i<list.length();i++){
            Object value=list.opt(i);if(!(value instanceof Number))return false;
            int index=((Number)value).intValue();
            if(((Number)value).doubleValue()!=index||index<0||index>=input||!seen.add(index))return false;
        }
        return seen.size()==input;
    }
    static boolean terminal(JSONObject journey) {
        String value=journey.optString("walking_outcome");
        return "no_route_found".equals(value)||"insufficient_detail".equals(value);
    }
    static String label(JSONObject journey) {
        switch(journey.optString("walking_outcome")) {
            case "no_route_found":return "Recorded · no mapped route found";
            case "insufficient_detail":return "Recorded · insufficient route detail";
            case "partial":return "Partially matched · only verified paths count";
            default:return null;
        }
    }
}
