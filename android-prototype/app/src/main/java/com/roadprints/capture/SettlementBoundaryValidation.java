package com.roadprints.capture;

import java.io.IOException;
import org.json.JSONArray;
import org.json.JSONObject;

/** Validate structure before a public boundary becomes persistent reference evidence. */
final class SettlementBoundaryValidation {
    static void validate(JSONObject geometry) throws Exception {
        validate(geometry, 0);
    }
    private static void validate(JSONObject geometry, int depth) throws Exception {
        if (geometry == null || depth > 4) throw new IOException("Invalid boundary wrapper");
        String type = geometry.optString("type");
        if ("Feature".equals(type)) { validate(geometry.optJSONObject("geometry"), depth + 1); return; }
        if ("FeatureCollection".equals(type)) {
            JSONArray features = geometry.optJSONArray("features");
            if (features == null || features.length() == 0) throw new IOException("Empty boundary features");
            for (int i = 0; i < features.length(); i++) validate(features.optJSONObject(i), depth + 1);
            return;
        }
        JSONArray coordinates = geometry.optJSONArray("coordinates");
        if ("Polygon".equals(type)) { polygon(coordinates); return; }
        if ("MultiPolygon".equals(type) && coordinates != null && coordinates.length() > 0) {
            for (int i = 0; i < coordinates.length(); i++) polygon(coordinates.optJSONArray(i));
            return;
        }
        throw new IOException("Unsupported boundary geometry");
    }
    private static void polygon(JSONArray polygon) throws Exception {
        if (polygon == null || polygon.length() == 0) throw new IOException("Empty boundary polygon");
        for (int r = 0; r < polygon.length(); r++) {
            JSONArray ring = polygon.optJSONArray(r);
            if (ring == null || ring.length() < 4) throw new IOException("Invalid boundary ring");
            for (int p = 0; p < ring.length(); p++)
                if (!RoadDetailGeometry.valid(ring.optJSONArray(p))) throw new IOException("Invalid boundary coordinate");
            JSONArray first = ring.optJSONArray(0), last = ring.optJSONArray(ring.length() - 1);
            if (first.optDouble(0) != last.optDouble(0) || first.optDouble(1) != last.optDouble(1))
                throw new IOException("Open boundary ring");
        }
    }
}
