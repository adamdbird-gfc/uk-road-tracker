package com.roadprints.capture;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.HashSet;
import java.util.Set;

/** Qualifies stops from original timed GPS evidence, never from matched or drawn routes. */
final class ServiceStationEvidence {
    static final long MIN_DWELL_MS = 180_000L, MAX_GAP_MS = 90_000L;
    static final double STOP_RADIUS_METRES = 125, MAX_ACCURACY_METRES = 50;
    private ServiceStationEvidence() {}

    static Set<String> visits(JSONObject journey, JSONArray stations) {
        JSONObject source = journey.optJSONObject("source");
        if (source == null || !"android_activity_capture".equals(source.optString("type")))
            return new HashSet<>();
        // Raw samples include stationary tails removed from the displayed journey route.
        JSONArray samples = journey.optJSONArray("raw_capture_samples");
        if (samples == null) samples = journey.optJSONArray("capture_route_samples");
        return stops(samples, stations);
    }

    static Set<String> stops(JSONArray samples, JSONArray stations) {
        Set<String> result = new HashSet<>();
        if (samples == null || stations == null) return result;
        String active = "";
        long first = 0, previous = 0;
        double anchorLat = 0, anchorLng = 0;
        int count = 0;
        for (int i = 0; i < samples.length(); i++) {
            JSONArray p = samples.optJSONArray(i);
            double lng = p == null ? Double.NaN : p.optDouble(0, Double.NaN);
            double lat = p == null ? Double.NaN : p.optDouble(1, Double.NaN);
            double accuracy = p == null ? Double.NaN : p.optDouble(2, Double.NaN);
            long time = p == null ? 0 : p.optLong(3, 0);
            double speed = p == null ? -1 : p.optDouble(4, -1);
            if (!valid(lat, lng) || !Double.isFinite(accuracy) || accuracy <= 0
                    || accuracy > MAX_ACCURACY_METRES || time <= 0
                    || !Double.isFinite(speed) || speed > 2.0) {
                active = ""; count = 0; previous = 0;
                continue;
            }
            // Repeated fixes do not add time or evidence. Reversed timestamps break a stop.
            if (time == previous) continue;
            JSONObject nearest = nearest(stations, lat, lng, STOP_RADIUS_METRES);
            if (nearest == null) { active = ""; count = 0; previous = 0; continue; }
            String id = nearest.optString("id", "");
            boolean reset = !id.equals(active) || time < previous
                    || time - previous > MAX_GAP_MS
                    || metres(lat, lng, anchorLat, anchorLng) > 80;
            if (reset) {
                active = id; first = time; count = 1; anchorLat = lat; anchorLng = lng;
            } else count++;
            previous = time;
            if (!id.isEmpty() && count >= 4 && time - first >= MIN_DWELL_MS) result.add(id);
        }
        return result;
    }

    static JSONObject nearest(JSONArray stations, double lat, double lng, double limit) {
        JSONObject nearest = null;
        double closest = limit;
        for (int i = 0; i < stations.length(); i++) {
            JSONObject station = stations.optJSONObject(i);
            if (station == null) continue;
            double distance = distance(station, lat, lng);
            if (distance <= closest) { nearest = station; closest = distance; }
        }
        return nearest;
    }

    static double distance(JSONObject station, double lat, double lng) {
        double distance = Double.MAX_VALUE;
        JSONArray points = station.optJSONArray("points");
        if (points != null) for (int i = 0; i < points.length(); i++) {
            JSONObject point = points.optJSONObject(i);
            if (point != null) distance = Math.min(distance, metres(lat, lng,
                    point.optDouble("lat", Double.NaN), point.optDouble("lng", Double.NaN)));
        }
        // Use a centroid only for legacy catalogue entries with no site points.
        if (distance == Double.MAX_VALUE) distance = metres(lat, lng,
                station.optDouble("lat", Double.NaN), station.optDouble("lng", Double.NaN));
        return distance;
    }

    private static boolean valid(double lat, double lng) {
        return Double.isFinite(lat) && Double.isFinite(lng) && Math.abs(lat) <= 90 && Math.abs(lng) <= 180;
    }
    static double metres(double lat1, double lng1, double lat2, double lng2) {
        if (!valid(lat1, lng1) || !valid(lat2, lng2)) return Double.MAX_VALUE;
        double r = Math.PI / 180, dlat = (lat2-lat1)*r, dlng = (lng2-lng1)*r;
        double a = Math.pow(Math.sin(dlat/2), 2) + Math.cos(lat1*r)*Math.cos(lat2*r)*Math.pow(Math.sin(dlng/2), 2);
        a = Math.max(0, Math.min(1, a));
        return 6_371_000 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
    }
}
