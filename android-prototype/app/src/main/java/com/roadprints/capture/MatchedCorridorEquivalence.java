package com.roadprints.capture;

import org.json.JSONArray;

/**
 * Conservative physical-shape equivalence for accepted matched Map fragments.
 * Used for presentation/contributor union only: never road or NCN completion.
 *
 * Exact geometry is dealt with separately. To join slightly different matches
 * here both endpoints, total distance, the full intervening shape and sample
 * midpoints must agree. Two nearby roads sharing an intersection cannot qualify
 * merely because their endpoints or names agree.
 */
final class MatchedCorridorEquivalence {
    private MatchedCorridorEquivalence() {}

    private static final double METRES_PER_DEGREE = 111_195.08;
    private static final double MAX_ENDPOINT_METRES = 2.5;
    private static final double MAX_SHAPE_METRES = 1.5;
    private static final double MIN_CORRIDOR_METRES = 20.0;
    private static final double MAX_LENGTH_DIFFERENCE_METRES = 3.0;
    private static final double MAX_RELATIVE_LENGTH_DIFFERENCE = 0.03;
    private static final int MAX_VERTICES = 128;

    static boolean samePhysicalShape(JSONArray first, JSONArray second) {
        if (!usable(first) || !usable(second)) return false;
        // Fast allocation-free geographic endpoint gate. Most of the bounded
        // corridor ledger is nowhere near the candidate; do not build arrays
        // for those pairs on a 256MiB owner device.
        JSONArray fa = first.optJSONArray(0), fb = first.optJSONArray(first.length()-1);
        JSONArray sa = second.optJSONArray(0), sb = second.optJSONArray(second.length()-1);
        if (!validEndpoint(fa) || !validEndpoint(fb)
                || !validEndpoint(sa) || !validEndpoint(sb)) return false;
        boolean nearForward = endpointDistance(fa, sa) <= MAX_ENDPOINT_METRES
                && endpointDistance(fb, sb) <= MAX_ENDPOINT_METRES;
        boolean nearReverse = endpointDistance(fa, sb) <= MAX_ENDPOINT_METRES
                && endpointDistance(fb, sa) <= MAX_ENDPOINT_METRES;
        if (!nearForward && !nearReverse) return false;
        double referenceLatitude = (fa.optDouble(1) + sa.optDouble(1)) / 2;
        double longitudeScale = Math.cos(Math.toRadians(referenceLatitude));
        if (longitudeScale < 0.1) return false; // Near-polar map ambiguity.
        double[][] a = projected(first, longitudeScale), b = projected(second, longitudeScale);
        if (a == null || b == null) return false;
        double aLength = length(a), bLength = length(b);
        if (aLength < MIN_CORRIDOR_METRES || bLength < MIN_CORRIDOR_METRES) return false;
        if (Math.abs(aLength - bLength) > Math.min(MAX_LENGTH_DIFFERENCE_METRES,
                MAX_RELATIVE_LENGTH_DIFFERENCE * Math.max(aLength, bLength))) return false;

        boolean forward = distance(a[0], b[0]) <= MAX_ENDPOINT_METRES
                && distance(a[a.length - 1], b[b.length - 1]) <= MAX_ENDPOINT_METRES;
        boolean reverse = distance(a[0], b[b.length - 1]) <= MAX_ENDPOINT_METRES
                && distance(a[a.length - 1], b[0]) <= MAX_ENDPOINT_METRES;
        if (!forward && !reverse) return false;
        // Symmetric point-to-polyline including segment midpoints: prevents
        // the longer, differently bent route being swallowed by a shorter path.
        return closeThroughout(a, b) && closeThroughout(b, a);
    }

    private static boolean validEndpoint(JSONArray p) {
        if (p == null || p.length() < 2) return false;
        double x = p.optDouble(0, Double.NaN), y = p.optDouble(1, Double.NaN);
        return Double.isFinite(x) && Double.isFinite(y)
                && Math.abs(x) <= 180 && Math.abs(y) <= 90;
    }

    private static double endpointDistance(JSONArray a, JSONArray b) {
        double latitude = (a.optDouble(1) + b.optDouble(1)) / 2;
        return METRES_PER_DEGREE * Math.hypot(
                (a.optDouble(0) - b.optDouble(0)) * Math.cos(Math.toRadians(latitude)),
                a.optDouble(1) - b.optDouble(1));
    }

    private static boolean usable(JSONArray line) {
        return line != null && line.length() >= 2 && line.length() <= MAX_VERTICES;
    }

    private static double[][] projected(JSONArray line, double lonScale) {
        double[][] points = new double[line.length()][2];
        for (int i = 0; i < line.length(); i++) {
            JSONArray p = line.optJSONArray(i);
            if (p == null || p.length() < 2) return null;
            double lon = p.optDouble(0, Double.NaN), lat = p.optDouble(1, Double.NaN);
            if (!Double.isFinite(lon) || !Double.isFinite(lat)
                    || Math.abs(lon) > 180 || Math.abs(lat) > 90) return null;
            points[i][0] = lon * lonScale * METRES_PER_DEGREE;
            points[i][1] = lat * METRES_PER_DEGREE;
        }
        return points;
    }

    private static double length(double[][] points) {
        double total = 0;
        for (int i = 1; i < points.length; i++) total += distance(points[i-1], points[i]);
        return total;
    }

    private static boolean closeThroughout(double[][] source, double[][] target) {
        for (int i = 0; i < source.length; i++) {
            if (nearest(source[i], target) > MAX_SHAPE_METRES) return false;
            if (i > 0) {
                double[] middle = {(source[i-1][0] + source[i][0]) / 2,
                        (source[i-1][1] + source[i][1]) / 2};
                if (nearest(middle, target) > MAX_SHAPE_METRES) return false;
            }
        }
        return true;
    }

    private static double nearest(double[] point, double[][] route) {
        double closest = Double.POSITIVE_INFINITY;
        for (int i = 1; i < route.length; i++) {
            double dx = route[i][0] - route[i-1][0], dy = route[i][1] - route[i-1][1];
            double lengthSquared = dx * dx + dy * dy;
            if (lengthSquared < 0.01) continue;
            double t = ((point[0] - route[i-1][0]) * dx
                    + (point[1] - route[i-1][1]) * dy) / lengthSquared;
            t = Math.max(0, Math.min(1, t));
            double distance = Math.hypot(point[0] - route[i-1][0] - t*dx,
                    point[1] - route[i-1][1] - t*dy);
            closest = Math.min(closest, distance);
        }
        return closest;
    }

    private static double distance(double[] a, double[] b) {
        return Math.hypot(a[0] - b[0], a[1] - b[1]);
    }
}
