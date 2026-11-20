package com.roadprints.capture;

import org.json.JSONArray;

/**
 * Non-mutating geometry-fidelity measurement for comparing an accepted saved
 * journey path with a candidate cumulative Map display path.
 *
 * Reports distance in metres, not a pass/fail tolerance: the owner has not
 * approved a universal simplification tolerance. Coverage/NCN accounting is
 * not involved. Never treats endpoint agreement as proof of path agreement.
 */
final class CumulativeMapShapeAudit {
    private static final double METRES_PER_DEGREE = 111_195.08;
    private CumulativeMapShapeAudit() {}

    /**
     * Symmetric worst-case deviation between two polylines. Samples every
     * source vertex and the midpoint of every source edge in BOTH directions.
     * This catches a curved accepted roundabout being replaced by a chord and
     * prevents an extra invented spur being treated as equivalent geometry.
     */
    static double symmetricDeviationMetres(JSONArray accepted, JSONArray displayed) {
        if (!validLine(accepted) || !validLine(displayed)) return Double.POSITIVE_INFINITY;
        double max = Math.max(directed(accepted, displayed), directed(displayed, accepted));
        return Double.isFinite(max) ? max : Double.POSITIVE_INFINITY;
    }

    private static double directed(JSONArray source, JSONArray target) {
        double maximum = 0;
        for (int i = 0; i < source.length(); i++) {
            JSONArray point = source.optJSONArray(i);
            maximum = Math.max(maximum, closest(point, target));
            if (i > 0) {
                JSONArray previous = source.optJSONArray(i - 1);
                JSONArray midpoint = new JSONArray()
                        .put((previous.optDouble(0) + point.optDouble(0)) / 2)
                        .put((previous.optDouble(1) + point.optDouble(1)) / 2);
                maximum = Math.max(maximum, closest(midpoint, target));
            }
        }
        return maximum;
    }

    private static double closest(JSONArray point, JSONArray line) {
        double latitude = point.optDouble(1);
        double scale = Math.cos(Math.toRadians(latitude));
        double px = point.optDouble(0) * scale * METRES_PER_DEGREE;
        double py = latitude * METRES_PER_DEGREE;
        double nearest = Double.POSITIVE_INFINITY;
        for (int i = 1; i < line.length(); i++) {
            JSONArray a = line.optJSONArray(i - 1), b = line.optJSONArray(i);
            double ax = a.optDouble(0) * scale * METRES_PER_DEGREE;
            double ay = a.optDouble(1) * METRES_PER_DEGREE;
            double bx = b.optDouble(0) * scale * METRES_PER_DEGREE;
            double by = b.optDouble(1) * METRES_PER_DEGREE;
            double dx = bx - ax, dy = by - ay;
            double squared = dx * dx + dy * dy;
            double portion = squared == 0 ? 0 : Math.max(0, Math.min(1,
                    ((px - ax) * dx + (py - ay) * dy) / squared));
            nearest = Math.min(nearest, Math.hypot(px - ax - portion * dx,
                    py - ay - portion * dy));
        }
        return nearest;
    }

    private static boolean validLine(JSONArray line) {
        if (line == null || line.length() < 2) return false;
        for (int i = 0; i < line.length(); i++) {
            JSONArray point = line.optJSONArray(i);
            if (point == null || point.length() < 2) return false;
            double lon = point.optDouble(0, Double.NaN);
            double lat = point.optDouble(1, Double.NaN);
            if (!Double.isFinite(lon) || !Double.isFinite(lat)
                    || Math.abs(lon) > 180 || Math.abs(lat) > 90) return false;
        }
        return true;
    }
}
