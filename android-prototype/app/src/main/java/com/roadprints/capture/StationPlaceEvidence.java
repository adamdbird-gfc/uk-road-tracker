package com.roadprints.capture;

import org.json.JSONObject;

/** A station handoff identifies the origin; it is not evidence for a later destination. */
final class StationPlaceEvidence {
    static RailStationCatalog.Station stationAt(RailStationCatalog catalog, String code,
                                                double lat, double lon, double accuracy) {
        if (catalog == null || code == null || !Double.isFinite(lat) || !Double.isFinite(lon)
                || !Double.isFinite(accuracy) || accuracy <= 0 || accuracy > 80) return null;
        for (RailStationCatalog.Station station : catalog.stations) {
            if (code.equals(station.code) && RailStationCatalog.metres(lat,lon,station.latitude,station.longitude)
                    <= station.radius) return station;
        }
        return null;
    }

    static JSONObject arrival(String journeyId, FootTraceValidator.Sample anchor, long confirmedAt,
                              ContextMap.Evidence map, String candidateCode, String reason,
                              RailStationCatalog catalog) throws org.json.JSONException {
        RailStationCatalog.Station station = stationAt(catalog,candidateCode,anchor.lat,anchor.lon,anchor.accuracy);
        JSONObject result = PlaceBoundaryEvidence.arrival(journeyId,anchor,confirmedAt,map,
                station == null ? null : station.code,reason);
        if (result != null && station != null) result.put("name",station.name+" ("+station.code+")");
        return result;
    }

    static String code(JSONObject stop) {
        String id=stop.optString("place_id");
        return "station".equals(stop.optString("kind")) && id.startsWith("rail:") ? id.substring(5) : "";
    }

    static boolean reviewable(JSONObject stop, RailStationCatalog catalog) {
        String code=code(stop);
        // Keep explicit user decisions available for correction. A missing catalogue is not
        // proof that a saved station is wrong, but it cannot validate an unreviewed suggestion.
        return code.isEmpty() || stop.has("reviewed_at_ms") || stationAt(catalog,code,
                stop.optDouble("latitude"),stop.optDouble("longitude"),stop.optDouble("accuracy_m")) != null;
    }

    static String label(JSONObject stop, RailStationCatalog catalog) {
        String name=stop.optString("name","Estimated stop"), code=code(stop);
        if (catalog != null && !code.isEmpty()) for (RailStationCatalog.Station station : catalog.stations) {
            if (station.code.equals(code) && (name.equals("Station "+code) || name.equals(station.name)
                    || name.equals(station.name+" ("+code+")"))) return station.name+" ("+code+")";
        }
        return name;
    }
}
