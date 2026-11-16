package com.roadprints.capture;

import org.json.JSONException;
import org.json.JSONObject;

/** The archive owns the effective distance and keeps its original value for recovery. */
final class RailwayMileage {
    private static final String DISTANCE = "distance_meters";
    private static final String PROVENANCE = "railway_distance";
    private static final String ORIGINAL = "previous_distance_meters";

    private RailwayMileage() {}

    static boolean qualifies(JSONObject receipt) {
        double metres = receipt.optDouble("inferred_m");
        return "inferred".equals(receipt.optString("confidence"))
                && "network_route_inferred".equals(receipt.optString("reason"))
                && RailwayMatcher.VERSION.equals(receipt.optString("matcher"))
                && RegionalRailwayCatalogue.SHA256.equals(receipt.optString("catalogue_sha256"))
                && NetworkFirstRailway.SHA256.equals(receipt.optString("network_catalogue_sha256"))
                && receipt.optString("network_path_sha256").matches("[0-9a-f]{64}")
                && Double.isFinite(metres) && metres > 0;
    }

    static boolean apply(JSONObject journey, JSONObject receipt) throws JSONException {
        if (!"train".equals(journey.optString("mode"))
                || !journey.getString("journey_id").equals(receipt.optString("journey_id"))
                || !qualifies(receipt)) return false;
        JSONObject previous = journey.optJSONObject(PROVENANCE);
        double metres = receipt.getDouble("inferred_m");
        if (previous != null && metres == journey.optDouble(DISTANCE)
                && receipt.getString("fingerprint").equals(previous.optString("fingerprint"))
                && receipt.getString("network_path_sha256").equals(previous.optString("network_path_sha256"))) {
            return false;
        }
        JSONObject provenance = new JSONObject();
        for (String field : new String[]{"fingerprint", "matcher", "catalogue", "catalogue_sha256",
                "network_catalogue", "network_catalogue_sha256", "network_path_sha256",
                "network_branch", "inferred_endpoints"}) {
            provenance.put(field, receipt.opt(field));
        }
        provenance.put("method", "inferred_physical_rail_path").put(DISTANCE, metres)
                .put(ORIGINAL, previous == null ? journey.opt(DISTANCE) : previous.opt(ORIGINAL));
        journey.put(PROVENANCE, provenance).put(DISTANCE, metres);
        return true;
    }

    static boolean restore(JSONObject journey) throws JSONException {
        JSONObject provenance = journey.optJSONObject(PROVENANCE);
        if (provenance == null) return false;
        if (journey.optDouble(DISTANCE) == provenance.optDouble(DISTANCE)) {
            journey.put(DISTANCE, provenance.opt(ORIGINAL));
        }
        journey.remove(PROVENANCE);
        return true;
    }
}
