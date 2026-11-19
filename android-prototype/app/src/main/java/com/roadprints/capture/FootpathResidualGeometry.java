package com.roadprints.capture;

import org.json.JSONArray;
import java.util.ArrayList;
import java.util.List;

/**
 * Accepted, owner-corrected on-foot route fragments without a nearby canonical
 * UK reference edge. These are DISPLAY-ONLY candidate paths, never road/NCN
 * completion. A nearby road is a conservative hold, not a permission to draw
 * a second overlapping stroke or to claim it was traversed.
 */
final class FootpathResidualGeometry {
    private FootpathResidualGeometry() {}
    static final int VERSION = 1;
    static final int MAX_POINTS = 160;
    static final int MAX_LINES = 24;
    static final double MAX_LEG_METRES = 150;

    interface NearReference {
        boolean nearReference(double ax, double ay, double bx, double by) throws Exception;
    }

    record Projection(JSONArray lines, boolean truncated, int heldNearby, int heldLong) {}

    static Projection extract(List<JSONArray> acceptedResidual, NearReference nearby)
            throws Exception {
        JSONArray lines = new JSONArray();
        if (acceptedResidual == null || nearby == null)
            return new Projection(lines, false, 0, 0);
        int points = 0, near = 0, longLegs = 0;
        boolean truncated = false;
        for (JSONArray route : acceptedResidual) {
            if (route == null) continue;
            JSONArray current = null;
            for (int i = 1; i < route.length(); i++) {
                JSONArray a = route.optJSONArray(i - 1), b = route.optJSONArray(i);
                boolean accepted = false;
                if (valid(a) && valid(b)) {
                    double ax = a.optDouble(0), ay = a.optDouble(1);
                    double bx = b.optDouble(0), by = b.optDouble(1);
                    double metres = 111_195.08 * Math.hypot(
                            (bx - ax) * Math.cos(Math.toRadians((ay + by) / 2)), by - ay);
                    if (metres > 0 && metres <= MAX_LEG_METRES) {
                        if (nearby.nearReference(ax, ay, bx, by)) near++;
                        else accepted = true;
                    } else longLegs++;
                }
                if (!accepted) {
                    if (current != null && current.length() >= 2) {
                        lines.put(current);
                        if (lines.length() >= MAX_LINES) {
                            truncated = true;
                            return new Projection(lines, truncated, near, longLegs);
                        }
                    }
                    current = null;
                    continue;
                }
                if (current == null) {
                    if (points + 2 > MAX_POINTS) {
                        truncated = true;
                        return new Projection(lines, truncated, near, longLegs);
                    }
                    current = new JSONArray().put(a);
                    points++;
                }
                if (points + 1 > MAX_POINTS) {
                    truncated = true;
                    return new Projection(lines, truncated, near, longLegs);
                }
                current.put(b);
                points++;
            }
            if (current != null && current.length() >= 2) {
                lines.put(current);
                if (lines.length() >= MAX_LINES) {
                    truncated = true;
                    return new Projection(lines, truncated, near, longLegs);
                }
            }
        }
        return new Projection(lines, truncated, near, longLegs);
    }

    private static boolean valid(JSONArray point) {
        if (point == null || point.length() < 2) return false;
        double lon = point.optDouble(0, Double.NaN);
        double lat = point.optDouble(1, Double.NaN);
        return Double.isFinite(lon) && Double.isFinite(lat)
                && lon >= -180 && lon <= 180 && lat >= -90 && lat <= 90;
    }
}
