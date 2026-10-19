package com.roadprints.capture;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.time.LocalDate;
import java.time.ZoneId;

/** Shared, immutable selection; filtering never changes stored journey evidence. */
final class JourneyFilters {
    final int mode;
    final String status;
    final JourneyDateFilter date;
    JourneyFilters(int mode, String status, JourneyDateFilter date) {
        this.mode = Math.max(0, Math.min(FILTER_VALUES.length - 1, mode));
        this.status = status == null ? "all" : status;
        this.date = date;
    }
    static JourneyFilters all() { return new JourneyFilters(0, "all", JourneyDateFilter.all()); }
    boolean isAll() { return mode == 0 && "all".equals(status) && "all".equals(date.preset); }
    boolean matches(JSONObject journey, LocalDate today, ZoneId zone) {
        return matchesFilter(journey, mode) && matchesJourneyStatusFilter(journey, status)
                && date.matches(journey, today, zone);
    }
    String summary() { return FILTER_LABELS[mode] + " · " + statusLabel(status) + " · " + date.label(); }
    static final String[] FILTER_LABELS = {
            "All transport", "Driving", "On foot", "Bus",
            "Train", "Cycling", "Flight", "Ferry", "Unknown"
    };
    static final String[] FILTER_VALUES = {
            "all", "driving", "walking", "bus", "train", "cycling", "plane", "ferry", "unknown"
    };

    static final String[] STATUS_VALUES = {"all", "matched", "ready", "matching", "failed", "no_match"};
    static final String[] STATUS_LABELS = {"All statuses", "Matched", "Ready to match", "Matching", "Needs retry", "No matching needed"};
    static boolean isUnknownMode(String mode) {
        return mode.isEmpty() || "unknown".equals(mode) || "other".equals(mode)
                || "unclassified".equals(mode) || "transit".equals(mode)
                || "other_travel".equals(mode);
    }

    static boolean matchesFilter(JSONObject journey, int filterIndex) {
        String mode = journey.optString("mode", "unknown").trim().toLowerCase(java.util.Locale.ROOT);
        if (filterIndex == 0) return true;
        if (filterIndex == FILTER_VALUES.length - 1) return isUnknownMode(mode);
        if (filterIndex == 2) return isFootMode(mode);
        if (filterIndex == 5) return MatchingCoordinator.isCycle(mode);
        return FILTER_VALUES[filterIndex].equals(mode);
    }

    static boolean matchesJourneyStatusFilter(JSONObject journey, String filter) {
        if ("all".equals(filter)) return true;
        String mode = journey.optString("mode", "unknown").trim().toLowerCase(java.util.Locale.ROOT);
        String status = journey.optString("processing_status", "pending");
        if ("no_match".equals(filter)) return !isRoadMode(mode) && !isFootMode(mode) && !MatchingCoordinator.isCycle(mode);
        if (!isRoadMode(mode) && !isFootMode(mode) && !MatchingCoordinator.isCycle(mode)) return false;
        if ("matched".equals(filter)) return "complete".equals(status) && hasStoredMatch(journey);
        if ("failed".equals(filter)) return "failed".equals(status)
                || ("complete".equals(status) && !hasStoredMatch(journey) && !WalkingMatchOutcome.terminal(journey));
        if ("matching".equals(filter)) return "processing".equals(status);
        if ("ready".equals(filter)) return canMatchJourney(journey)
                && !"complete".equals(status) && !"failed".equals(status)
                && !"processing".equals(status);
        return true;
    }

    static int pointCount(JSONObject journey) {
        if (journey.has("gps_point_count")) {
            return journey.optInt("gps_point_count", 0);
        }
        if (journey.has("_route_point_count")) {
            return journey.optInt("_route_point_count", 0);
        }
        JSONObject geometry = journey.optJSONObject("route_geometry");
        JSONArray coordinates = geometry == null ? null : geometry.optJSONArray("coordinates");
        return coordinates == null ? 0 : coordinates.length();
    }

    static boolean isFootMode(String mode) {
        return "walking".equals(mode) || "running".equals(mode)
                || "pedestrian".equals(mode);
    }

    static boolean isRoadMode(String mode) {
        return "driving".equals(mode) || "bus".equals(mode);
    }

    static boolean hasEnoughMatchingEvidence(JSONObject journey) {
        if (pointCount(journey) < 2) return false;
        String mode = journey.optString("mode", "unknown");
        if (isRoadMode(mode) || MatchingCoordinator.isCycle(mode)) {
            JSONObject source = journey.optJSONObject("source");
            if (source != null && "timeline_import".equals(source.optString("type", ""))) {
                JSONObject quality = journey.optJSONObject("capture_quality");
                return quality != null && quality.optInt("source_route_points", 0) >= 2;
            }
        }
        return isFootMode(mode) || isRoadMode(mode) || MatchingCoordinator.isCycle(mode);
    }

    static boolean canMatchJourney(JSONObject journey) {
        String mode = journey.optString("mode", "unknown");
        return (isFootMode(mode) || isRoadMode(mode) || MatchingCoordinator.isCycle(mode))
                && hasEnoughMatchingEvidence(journey);
    }

    static boolean hasStoredMatch(JSONObject journey) {
        if (journey.has("_has_stored_match")) {
            return journey.optBoolean("_has_stored_match", false);
        }
        JSONObject result = journey.optJSONObject("processing_result");
        return result != null && !matchedRouteSegments(result).isEmpty();
    }

    private static List<JSONArray> matchedRouteSegments(JSONObject result) {
        List<JSONArray> segments = new ArrayList<>();
        if (result == null) return segments;
        JSONObject geojson = result.optJSONObject("geojson");
        JSONArray features = geojson == null ? null : geojson.optJSONArray("features");
        if (features == null) return segments;
        for (int index = 0; index < features.length(); index++) {
            JSONObject feature = features.optJSONObject(index);
            JSONObject geometry = feature == null ? null : feature.optJSONObject("geometry");
            if (geometry == null) continue;
            String type = geometry.optString("type", "");
            JSONArray coordinates = geometry.optJSONArray("coordinates");
            if ("LineString".equals(type) && coordinates != null
                    && coordinates.length() >= 2) {
                segments.add(coordinates);
            } else if ("MultiLineString".equals(type) && coordinates != null) {
                for (int segmentIndex = 0; segmentIndex < coordinates.length(); segmentIndex++) {
                    JSONArray segment = coordinates.optJSONArray(segmentIndex);
                    if (segment != null && segment.length() >= 2) segments.add(segment);
                }
            }
        }
        return segments;
    }
    static String statusLabel(String value) {
        for (int i = 0; i < STATUS_VALUES.length; i++) if (STATUS_VALUES[i].equals(value)) return STATUS_LABELS[i];
        return STATUS_LABELS[0];
    }
}
