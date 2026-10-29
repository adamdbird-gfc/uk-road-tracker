package com.roadprints.capture;
import org.json.JSONObject;
/** Behaviour depends on verified place evidence, never its display name or coordinates. */
final class DestinationPolicy {
    static boolean complex(String kind) {
        return kind!=null&&kind.matches("theme_park|resort|zoo|shopping_centre|airport|terminal|hospital|school|college|university|campus|stadium|event_venue|sports_centre");
    }
    static boolean bounded(JSONObject stop) {
        return stop!=null&&complex(stop.optString("kind"))&&PlaceGeometry.present(stop)
                &&!"rejected".equals(stop.optString("classification"));
    }
    static boolean quickEntry(ContextMap.Evidence e) {
        return e!=null&&e.destination&&e.site&&e.geometry!=null&&!e.throughRoad
                &&e.kind.matches("theme_park|resort|zoo|shopping_centre|airport|terminal|stadium|event_venue");
    }
    static boolean campus(JSONObject stop) {
        return stop.optString("kind").matches("school|college|university|campus");
    }
}
