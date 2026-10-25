package com.roadprints.capture;

import org.json.*;
import java.time.Instant;

/** Only material input/rule changes reopen a deterministic GPS-quality rejection. */
final class MatchRetryPolicy {
    // Increment when capture/foot preparation rules change, never for a UI-only release.
    static final int QUALITY_RULES_VERSION=1;
    static String input(JSONObject journey) {
        JSONObject evidence=new JSONObject();
        try {for(String key:new String[]{"journey_id","mode","source","route_geometry","capture_route_samples",
                "raw_capture_samples","walking_validation"})if(journey.has(key))evidence.put(key,journey.opt(key));}
        catch(Exception invalid){throw new IllegalStateException(invalid);}
        return NcnJson.digest(evidence);
    }
    static boolean qualityFailure(Throwable error) {
        if(error instanceof CaptureEvidenceFailure)return true;
        String text=String.valueOf(error==null?null:error.getMessage()).toLowerCase(java.util.Locale.ROOT);
        return text.contains("gps gap")||text.contains("gap while moving")
                ||text.contains("moving gps gap")||text.contains("unreliable gps fixes")
                ||text.contains("not enough reliable gps points");
    }
    static boolean adoptLegacy(JSONObject journey)throws Exception {
        if(journey.has("match_retry_state")||!"failed".equals(journey.optString("processing_status"))
                ||!qualityFailure(new IllegalStateException(journey.optString("error_summary"))))return false;
        JSONObject attempt=journey.optJSONObject("last_match_attempt");
        String time=attempt==null?"unknown":attempt.optString("finished_at_utc",attempt.optString("started_at_utc","unknown"));
        reject(journey,time);return true;
    }
    static void reject(JSONObject journey,String time)throws Exception {
        journey.put("match_retry_state",new JSONObject().put("category","capture_quality")
                .put("input_sha256",input(journey)).put("rules_revision",QUALITY_RULES_VERSION)
                .put("original_failure_at_utc",time).put("retry_requires","changed_evidence_or_quality_rules"));
    }
    static boolean qualityBlocked(JSONObject journey) {
        JSONObject state=journey.optJSONObject("match_retry_state");
        return state!=null&&"capture_quality".equals(state.optString("category"))
                &&state.optInt("rules_revision")==QUALITY_RULES_VERSION
                &&state.optString("input_sha256").equals(input(journey));
    }
    static boolean coolingDown(JSONObject journey) {
        JSONObject attempt=journey.optJSONObject("last_match_attempt");
        if(attempt==null)return false;
        try{return Instant.parse(attempt.optString("next_retry_at_utc")).isAfter(Instant.now());}
        catch(Exception absent){return false;}
    }
}

final class CaptureEvidenceFailure extends IllegalStateException {
    CaptureEvidenceFailure(String message){super(message);}
}
