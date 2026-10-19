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
        JSONArray samples = ArrivalDwellEvidence.samples(journey);
        return stops(samples, stations);
    }

    static Set<String> stops(JSONArray samples, JSONArray stations) {
        Set<String> result=new HashSet<>();
        for(VenueStopDetector.Stop stop:VenueStopDetector.stops(samples,stations))result.add(stop.id);
        return result;
    }

    static JSONObject nearest(JSONArray stations, double lat, double lng, double limit) {
        JSONObject nearest = null;
        double closest = limit;
        for (int i = 0; i < stations.length(); i++) {
            JSONObject station = stations.optJSONObject(i);
            if (station == null) continue;
            if (ServiceSiteBoundary.present(station)) {
                if (ServiceSiteBoundary.supports(station,lat,lng,0)) {
                    // Overlapping sites require review; never choose one by catalogue ordering.
                    if (nearest != null && ServiceSiteBoundary.present(nearest)) return null;
                    nearest=station; closest=0;
                }
                continue;
            }
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
