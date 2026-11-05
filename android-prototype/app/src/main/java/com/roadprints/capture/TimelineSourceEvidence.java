package com.roadprints.capture;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Bounded preservation metadata, deliberately separate from matching/validation inputs.
 * Times address the deduplicated imported route, NOT raw GPS observations.
 * No consumer may infer accuracy, continuity or validated movement from this envelope.
 */
final class TimelineSourceEvidence {
    static final int MAX_POINTS = 10000;
    static final int MAX_TEXT = 256;

    private TimelineSourceEvidence() {}

    static JSONObject preserve(JSONObject segment, String mode, JSONArray times,
                               int routePoints, int sourcePoints, boolean endAppended)
            throws org.json.JSONException {
        boolean semantic = segment.has("activity");
        JSONObject activity = semantic ? segment.optJSONObject("activity") : segment;
        JSONObject result = new JSONObject().put("version", 1)
                .put("format", semantic ? "semanticSegments" : "timelineObjects")
                .put("validation_status", "unvalidated")
                .put("normalized_mode_at_import", mode)
                .put("source_route_points", sourcePoints)
                .put("route_points", routePoints);
        JSONObject duration = activity == null ? null : activity.optJSONObject("duration");
        copy(result, "source_started_at", semantic ? segment : duration,
                semantic ? "startTime" : "startTimestamp");
        copy(result, "source_ended_at", semantic ? segment : duration,
                semantic ? "endTime" : "endTimestamp");
        if (activity != null) {
            Object candidate = activity.opt("topCandidate");
            if (semantic && candidate instanceof JSONObject) {
                copy(result, "original_activity_type", (JSONObject) candidate, "type");
                copy(result, "candidate_probability", (JSONObject) candidate, "probability");
            } else copy(result, "original_activity_type", activity,
                    semantic ? "topCandidate" : "activityType");
            copy(result, "activity_probability", activity, "probability");
            copy(result, "source_confidence", activity, "confidence");
        }
        String status = !semantic ? "unavailable_in_imported_legacy_path"
                : routePoints > MAX_POINTS ? "point_limit_exceeded"
                : times == null || times.length() != routePoints ? "unaligned" : "preserved";
        if ("preserved".equals(status)) {
            JSONArray snapshot = new JSONArray();
            for (int i = 0; i < times.length(); i++) {
                Object time = times.opt(i);
                if (!(time instanceof Long || time instanceof Integer)
                        || ((Number) time).longValue() < 0) {
                    status = "missing_or_invalid"; break;
                }
                snapshot.put(((Number) time).longValue());
            }
            if ("preserved".equals(status)) result.put("route_point_times_ms", snapshot);
        }
        result.put("route_timing_status", status);
        if (semantic) result.put("route_time_basis", "activity_endpoints_and_sorted_timelinePath")
                .put("coordinate_deduplication", "consecutive_1e-7_degrees_latest_time")
                .put("end_appended", endAppended);
        return result;
    }

    private static void copy(JSONObject result, String target, JSONObject source, String key)
            throws org.json.JSONException {
        if (source == null || !source.has(key) || source.isNull(key)) return;
        Object value = source.opt(key);
        if (value instanceof String && ((String) value).length() <= MAX_TEXT
                || value instanceof Number && Double.isFinite(((Number) value).doubleValue())
                || value instanceof Boolean) result.put(target, value);
        else result.put(target + "_status", "unsupported_or_oversized");
    }
}
