package com.roadprints.capture;

import org.json.JSONObject;
import java.util.Locale;

/**
 * Maps accepted matched road-feature metadata to the existing tap card.
 * A missing label must not make a visible travelled road/path untappable.
 * Scope remains on the accepted feature; no coordinates or road identities
 * are inferred from a nearby reference or another journey.
 */
final class MapRoadTapSelection {
    private MapRoadTapSelection() {}

    static String label(JSONObject properties, String mode) {
        String ref = properties == null ? "" : properties.optString("road_ref",
                properties.optString("ref", "")).trim();
        if (!ref.isEmpty()) return ref;
        String name = properties == null ? "" : properties.optString("name",
                properties.optString("road_name", "")).trim();
        if (!name.isEmpty()) return name;
        return JourneyFilters.isFootMode(mode) ? "Public footpath" : "Unnamed road";
    }

    static boolean sameLocalFeature(String nearestLabel, String otherLabel,
                                    double nearestDistanceMetres, double otherDistanceMetres) {
        if (nearestLabel == null || otherLabel == null
                || !nearestLabel.equalsIgnoreCase(otherLabel)
                || !Double.isFinite(nearestDistanceMetres)
                || !Double.isFinite(otherDistanceMetres)) return false;
        // Only immediately co-located accepted features can share a tap
        // result. Same-name roads elsewhere inside the old 120m search
        // radius are not necessarily the same physical traversed corridor.
        double margin = "Unnamed road".equalsIgnoreCase(nearestLabel)
                || "Public footpath".equalsIgnoreCase(nearestLabel) ? 8 : 12;
        return otherDistanceMetres <= nearestDistanceMetres + margin;
    }

    static String key(String label) {
        return label == null ? "" : label.trim().toUpperCase(Locale.ROOT);
    }
}
