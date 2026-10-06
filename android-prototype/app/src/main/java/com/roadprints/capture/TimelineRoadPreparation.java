package com.roadprints.capture;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/** Uses local Timeline evidence without changing the archived source geometry. */
final class TimelineRoadPreparation {
    static final long GAP_MS = 30 * 60 * 1000L;
    static final class Prepared {
        final List<JSONArray> sections = new ArrayList<>();
        final JSONObject details = new JSONObject();
        boolean changed;
    }

    static Prepared prepare(JSONObject journey, JSONArray points) throws Exception {
        Prepared out = new Prepared();
        JSONObject evidence = journey.optJSONObject("timeline_match_evidence");
        JSONArray times = evidence == null ? null : evidence.optJSONArray("point_times_ms");
        if (times == null || times.length() != points.length()
                || !"timeline_import".equals(journey.optJSONObject("source") == null ? ""
                    : journey.optJSONObject("source").optString("type"))) {
            out.sections.add(points); return out;
        }
        // Rounded minute timestamps can repeat. They must never be interpreted
        // as instantaneous movement or used to reorder equal-time samples.
        for (int i = 0; i < times.length(); i++) {
            if (times.optLong(i, -1) < 0 || (i > 0 && times.optLong(i) < times.optLong(i - 1))) {
                out.sections.add(points); return out;
            }
        }
        int end = points.length();
        JSONArray parking = evidence.optJSONArray("parking_coordinates");
        boolean appendedEnd = evidence.optBoolean("end_appended", false);
        if (appendedEnd && end >= 3 && parking != null && parking.length() == 2) {
            JSONObject last = points.getJSONObject(end - 2), endpoint = points.getJSONObject(end - 1);
            double jump = distance(last, endpoint);
            double corroboration = distance(last, new JSONObject().put("lng", parking.getDouble(0))
                    .put("lat", parking.getDouble(1)));
            long elapsed = times.getLong(end - 1) - times.getLong(end - 2);
            long parkingTime = evidence.optLong("parking_time_ms", -1);
            if (Double.isFinite(jump) && Double.isFinite(corroboration)
                    && jump >= 2000 && corroboration <= 500 && elapsed >= 0 && elapsed <= 600000
                    && jump / Math.max(1, elapsed / 1000.0) > 55
                    && Math.abs(parkingTime - times.getLong(end - 1)) <= 600000) {
                end--; out.changed = true;
                out.details.put("endpoint_action", "omitted_contradictory_activity_end")
                        .put("endpoint_jump_m", Math.round(jump))
                        .put("parking_to_last_sample_m", Math.round(corroboration));
            }
        }
        JSONArray section = new JSONArray();
        JSONArray gaps = new JSONArray();
        for (int i = 0; i < end; i++) {
            if (i > 0 && times.getLong(i) - times.getLong(i - 1) > GAP_MS) {
                if (section.length() < 2)
                    throw new IllegalStateException("A Timeline recording gap leaves a section with too little GPS evidence. The original route is preserved.");
                out.sections.add(section); section = new JSONArray(); out.changed = true;
                gaps.put(new JSONObject().put("after_point_index", i - 1)
                        .put("duration_ms", times.getLong(i) - times.getLong(i - 1)));
            }
            section.put(points.get(i));
        }
        if (section.length() < 2)
            throw new IllegalStateException("Not enough Timeline points remain to validate this section. The original route is preserved.");
        out.sections.add(section);
        out.details.put("version", 1).put("sections", out.sections.size()).put("unrecorded_gaps", gaps)
                .put("original_geometry_preserved", true);
        return out;
    }

    static double distance(JSONObject a, JSONObject b) {
        double lat1 = Math.toRadians(a.optDouble("lat")), lat2 = Math.toRadians(b.optDouble("lat"));
        double dlat = lat2 - lat1, dlng = Math.toRadians(b.optDouble("lng") - a.optDouble("lng"));
        double h = Math.pow(Math.sin(dlat / 2), 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.pow(Math.sin(dlng / 2), 2);
        return 6371008.8 * 2 * Math.asin(Math.min(1, Math.sqrt(h)));
    }

    /** Retain failure details without duplicating all matcher map geometry. */
    static JSONObject diagnostics(JSONObject result) throws Exception {
        JSONObject out = new JSONObject();
        for (String key : new String[]{"input_points", "matched_tracepoints", "matched_distance_m",
                "matched_point_indices", "unmatched_point_indices", "road_recovery", "matched_distance_is_deduplicated"}) {
            if (result.has(key)) out.put(key, result.get(key));
        }
        JSONArray failures = result.optJSONArray("failed_sections"), retained = new JSONArray();
        if (failures != null) for (int i = 0; i < Math.min(100, failures.length()); i++) {
            JSONObject original = failures.optJSONObject(i); if (original == null) continue;
            JSONObject item = new JSONObject();
            for (String key : new String[]{"points", "start_point_index", "end_point_index", "detail"}) {
                if (original.has(key)) {
                    Object value = original.get(key);
                    if (value instanceof String && ((String)value).length() > 500)
                        value = ((String)value).substring(0, 500);
                    item.put(key, value);
                }
            }
            retained.put(item);
        }
        out.put("failed_sections", retained);
        return out;
    }

    static JSONObject merge(List<JSONObject> results) throws Exception {
        if (results.size() == 1) return results.get(0);
        JSONObject out = new JSONObject().put("status", "ok");
        boolean distanceVerified = true;
        for (JSONObject result : results) distanceVerified &= result.optBoolean("matched_distance_is_deduplicated", false);
        out.put("matched_distance_is_deduplicated", distanceVerified);
        for (String key : new String[]{"input_points", "matched_tracepoints", "chunks_used", "points_sent_to_matcher"}) {
            int total = 0; for (JSONObject result : results) total += result.optInt(key);
            out.put(key, total);
        }
        for (String key : new String[]{"matched_distance_m", "other_road_distance_m"}) {
            double total = 0; for (JSONObject result : results) {
                double value = result.optDouble(key, 0);
                if (!Double.isFinite(value) || value < 0) throw new IllegalStateException("Invalid matched road distance");
                total += value;
            }
            out.put(key, total);
        }
        for (String key : new String[]{"geojson", "road_geojson", "motorway_geojson", "a_road_geojson"}) {
            JSONArray features = new JSONArray();
            for (JSONObject result : results) {
                JSONObject collection = result.optJSONObject(key);
                JSONArray source = collection == null ? null : collection.optJSONArray("features");
                if (source != null) for (int i = 0; i < source.length(); i++) features.put(source.get(i));
            }
            out.put(key, new JSONObject().put("type", "FeatureCollection").put("features", features));
        }
        JSONArray failures = new JSONArray();
        for (JSONObject result : results) {
            JSONArray source = result.optJSONArray("failed_sections");
            if (source != null) for (int i = 0; i < source.length(); i++) failures.put(source.get(i));
        }
        return out.put("failed_sections", failures);
    }
}
