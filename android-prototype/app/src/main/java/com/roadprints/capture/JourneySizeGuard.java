package com.roadprints.capture;

import org.json.JSONArray;
import org.json.JSONObject;

/** Resource admission only, not a journey-duration or completion rule. Never edits evidence. */
final class JourneySizeGuard {
    static final int MAX_SINGLE_MATCH_POINTS = 20_000;
    private JourneySizeGuard() {}
    static int rawCount(JSONObject journey) {
        if(journey==null)return 0;
        JSONArray raw=journey.optJSONArray("raw_capture_samples");
        return raw!=null?raw.length():journey.optInt("gps_point_count",0);
    }
    static int routeCount(JSONObject journey) {
        if(journey==null)return 0;
        JSONArray samples=journey.optJSONArray("capture_route_samples");
        JSONObject geometry=journey.optJSONObject("route_geometry");
        JSONArray coords=geometry==null?null:geometry.optJSONArray("coordinates");
        return Math.max(samples==null?0:samples.length(),coords==null?0:coords.length());
    }
    static boolean requiresReview(JSONObject journey) {
        return Math.max(rawCount(journey),routeCount(journey))>MAX_SINGLE_MATCH_POINTS;
    }
    static String message(JSONObject journey) {
        return "This recording contains "+rawCount(journey)+" original GPS samples and "+routeCount(journey)
                +" route samples. Full matching is limited to "+MAX_SINGLE_MATCH_POINTS
                +" points per recording to protect memory. Export Debug Journey and review separate movement episodes first. "
                +"The original recording, timestamps and GPS evidence are unchanged. No completion credit has been added.";
    }
}
