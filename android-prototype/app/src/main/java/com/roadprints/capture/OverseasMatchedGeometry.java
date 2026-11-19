package com.roadprints.capture;

import org.json.JSONArray;
import java.util.ArrayList;
import java.util.List;

/**
 * Bounded fragments from an ALREADY correction-trimmed accepted saved route.
 * This is a presentation fallback, not road/NCN completion evidence.
 *
 * The packaged UK reference covers a rectangular *index*, not the country.
 * Northern France (including Calais) can lie within those index bounds.
 * Where a leg lies inside the rectangle, callers must consult actual nearby
 * UK road-reference membership, not assume that it is British.
 */
final class OverseasMatchedGeometry {
    interface ReferenceProximity {
        boolean nearUkReference(double longitude, double latitude) throws Exception;
    }
    private OverseasMatchedGeometry() {}
    static final double WEST = -9, EAST = 3, SOUTH = 49, NORTH = 62;
    private static final double MAX_SINGLE_LEG_METRES = 30_000;

    static boolean outsideReference(double lon, double lat) {
        return Double.isFinite(lon) && Double.isFinite(lat)
                && (lon < WEST || lon > EAST || lat < SOUTH || lat > NORTH);
    }

    static List<JSONArray> outsideReferenceFragments(List<JSONArray> correctedLines,
                                                       int maxFragments, int maxPoints) {
        try {
            // Conservative legacy fast path, retained for callers that cannot
            // consult an opened UK RoadReference. Never claim this is complete
            // international detection.
            return unsupportedFragments(correctedLines, maxFragments, maxPoints,
                    (lon, lat) -> true);
        } catch (Exception impossible) {
            return new ArrayList<>();
        }
    }

    /**
     * Include only legs not attributable to the vicinity of the UK catalogue.
     * A failed reference lookup must not turn a UK road into a duplicate line;
     * propagate failures so the caller can record a held/unresolved journey.
     *
     * Uses midpoint lookups for short map-matched legs. Long connectors are
     * held, not drawn as a speculative straight line across a sea or country.
     */
    static List<JSONArray> unsupportedFragments(List<JSONArray> correctedLines,
                                                  int maxFragments, int maxPoints,
                                                  ReferenceProximity reference) throws Exception {
        return unsupportedFragments(correctedLines, maxFragments, maxPoints, reference, null);
    }

    static List<JSONArray> unsupportedFragments(List<JSONArray> correctedLines,
                                                  int maxFragments, int maxPoints,
                                                  ReferenceProximity reference,
                                                  boolean[] truncated) throws Exception {
        List<JSONArray> result = new ArrayList<>();
        if (correctedLines == null || reference == null || maxFragments <= 0 || maxPoints < 2)
            return result;
        int retained = 0;
        for (JSONArray line : correctedLines) {
            if (line == null) continue;
            JSONArray current = null;
            for (int i = 1; i < line.length(); i++) {
                JSONArray a = line.optJSONArray(i - 1), b = line.optJSONArray(i);
                boolean accepted = false;
                if (valid(a) && valid(b)) {
                    double ax = a.optDouble(0), ay = a.optDouble(1);
                    double bx = b.optDouble(0), by = b.optDouble(1);
                    double metres = 111_195 * Math.hypot(
                            (bx - ax) * Math.cos(Math.toRadians((ay + by) / 2)),
                            by - ay);
                    if (metres > 0 && metres <= MAX_SINGLE_LEG_METRES) {
                        double midLon = (ax + bx) / 2, midLat = (ay + by) / 2;
                        accepted = outsideReference(midLon, midLat)
                                || !reference.nearUkReference(midLon, midLat);
                    }
                }
                if (!accepted) {
                    if (current != null && current.length() >= 2) {
                        result.add(current);
                        if (result.size() >= maxFragments) {
                            if (truncated != null && truncated.length > 0) truncated[0] = true;
                            return result;
                        }
                    }
                    current = null;
                    continue;
                }
                if (current == null) {
                    if (retained + 2 > maxPoints) {
                        if (truncated != null && truncated.length > 0) truncated[0] = true;
                        return result;
                    }
                    current = new JSONArray().put(a);
                    retained++;
                }
                if (retained + 1 > maxPoints) {
                    if (truncated != null && truncated.length > 0) truncated[0] = true;
                    return result;
                }
                current.put(b);
                retained++;
            }
            if (current != null && current.length() >= 2) {
                result.add(current);
                if (result.size() >= maxFragments) {
                            if (truncated != null && truncated.length > 0) truncated[0] = true;
                            return result;
                        }
            }
        }
        return result;
    }

    /**
     * Exact (metre-scale quantised) matched-shape identity for repeated accepted
     * travel. Reversing direction must not add another identical display line.
     * This is intentionally stricter than geographical corridor equivalence:
     * neighbouring distinct routes must never be merged by proximity alone.
     */
    static String displayIdentity(JSONArray line) {
        if (line == null || line.length() < 2) return "";
        StringBuilder forward = new StringBuilder(line.length() * 24);
        StringBuilder reverse = new StringBuilder(line.length() * 24);
        for (int i = 0; i < line.length(); i++) appendQuantised(forward, line.optJSONArray(i));
        for (int i = line.length() - 1; i >= 0; i--) appendQuantised(reverse, line.optJSONArray(i));
        String first = forward.toString(), second = reverse.toString();
        return first.compareTo(second) <= 0 ? first : second;
    }

    private static void appendQuantised(StringBuilder out, JSONArray point) {
        if (!valid(point)) { out.append("invalid;"); return; }
        out.append(Math.round(point.optDouble(0) * 100_000))
                .append(',').append(Math.round(point.optDouble(1) * 100_000)).append(';');
    }

    private static boolean valid(JSONArray point) {
        if (point == null || point.length() < 2) return false;
        double lon = point.optDouble(0, Double.NaN), lat = point.optDouble(1, Double.NaN);
        return Double.isFinite(lon) && Double.isFinite(lat)
                && lon >= -180 && lon <= 180 && lat >= -90 && lat <= 90;
    }
}
