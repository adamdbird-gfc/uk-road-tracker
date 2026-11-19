package com.roadprints.capture;

import org.json.JSONArray;
import java.util.ArrayList;
import java.util.List;

/**
 * A bounded geographical filter for previously accepted matched journey lines.
 * It is NOT a matcher, a coverage ledger or a substitute for corrected evidence.
 *
 * The UK canonical road-reference extent is shared with RoadReference:
 * -9..3 longitude, 49..62 latitude. A leg only goes to overseas presentation
 * when both endpoints are outside that extent; boundary-crossing legs are held
 * for later clipping instead of drawing speculative international connectors.
 */
final class OverseasMatchedGeometry {
    private OverseasMatchedGeometry() {}
    private static final double WEST = -9, EAST = 3, SOUTH = 49, NORTH = 62;

    static boolean outsideReference(double lon, double lat) {
        return Double.isFinite(lon) && Double.isFinite(lat)
                && (lon < WEST || lon > EAST || lat < SOUTH || lat > NORTH);
    }

    /** Accept already corrected lines from MapActivity.matchedSegments. */
    static List<JSONArray> outsideReferenceFragments(List<JSONArray> correctedLines,
                                                       int maxFragments,
                                                       int maxPoints) {
        List<JSONArray> result = new ArrayList<>();
        if (correctedLines == null || maxFragments <= 0 || maxPoints < 2) return result;
        int retained = 0;
        for (JSONArray line : correctedLines) {
            if (line == null) continue;
            JSONArray current = null;
            for (int i = 1; i < line.length(); i++) {
                JSONArray a = line.optJSONArray(i - 1), b = line.optJSONArray(i);
                if (!valid(a) || !valid(b)
                        || !outsideReference(a.optDouble(0), a.optDouble(1))
                        || !outsideReference(b.optDouble(0), b.optDouble(1))) {
                    if (current != null && current.length() >= 2) {
                        result.add(current);
                        if (result.size() >= maxFragments) return result;
                    }
                    current = null;
                    continue;
                }
                if (current == null) {
                    if (retained + 2 > maxPoints) return result;
                    current = new JSONArray().put(a);
                    retained++;
                }
                if (retained + 1 > maxPoints) return result;
                current.put(b);
                retained++;
            }
            if (current != null && current.length() >= 2) {
                result.add(current);
                if (result.size() >= maxFragments) return result;
            }
        }
        return result;
    }

    private static boolean valid(JSONArray point) {
        if (point == null || point.length() < 2) return false;
        double lon = point.optDouble(0, Double.NaN), lat = point.optDouble(1, Double.NaN);
        return Double.isFinite(lon) && Double.isFinite(lat)
                && lon >= -180 && lon <= 180 && lat >= -90 && lat <= 90;
    }
}
