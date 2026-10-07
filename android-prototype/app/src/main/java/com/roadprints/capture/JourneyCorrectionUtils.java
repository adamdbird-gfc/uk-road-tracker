package com.roadprints.capture;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/** Shared handling for user-removed route and road evidence. */
final class JourneyCorrectionUtils {
    private static final double ROAD_ASSOCIATION_METRES = 35.0;
    private static final Map<JSONObject, Set<String>> REMOVED_ROAD_CACHE = new WeakHashMap<>();

    private JourneyCorrectionUtils() {}

    static Set<String> removedRoadIds(JSONObject journey) {
        if (journey == null) return Collections.emptySet();
        synchronized (REMOVED_ROAD_CACHE) {
            Set<String> cached = REMOVED_ROAD_CACHE.get(journey);
            if (cached != null) return cached;
        }
        Set<String> output = new LinkedHashSet<>();
        JSONObject corrections = journey == null ? null : journey.optJSONObject("journey_corrections");
        JSONArray values = corrections == null ? null : corrections.optJSONArray("removed_road_ids");
        if (values != null) {
            for (int index = 0; index < values.length(); index++) {
                String id = values.optString(index, "").trim();
                if (!id.isEmpty()) output.add(id);
            }
        }
        if (!output.isEmpty() || journey == null || corrections == null) return output;

        // Older saved edits contain only route-edge indices. Derive the associated
        // road names on read so those corrections update the other screens too.
        JSONArray removedValues = corrections.optJSONArray("removed_matched_segments");
        if (removedValues == null || removedValues.length() == 0) return output;
        Set<Integer> removedEdges = new LinkedHashSet<>();
        for (int index = 0; index < removedValues.length(); index++) {
            int edge = removedValues.optInt(index, -1);
            if (edge >= 0) removedEdges.add(edge);
        }
        JSONObject result = journey.optJSONObject("processing_result");
        JSONObject geojson = result == null ? null : result.optJSONObject("geojson");
        JSONArray features = geojson == null ? null : geojson.optJSONArray("features");
        List<JSONArray> routes = new ArrayList<>();
        if (features != null) {
            for (int index = 0; index < features.length(); index++) {
                JSONObject feature = features.optJSONObject(index);
                JSONObject geometry = feature == null ? null : feature.optJSONObject("geometry");
                if (geometry == null) continue;
                String type = geometry.optString("type", "");
                JSONArray coordinates = geometry.optJSONArray("coordinates");
                if ("LineString".equals(type) && coordinates != null && coordinates.length() >= 2) {
                    routes.add(coordinates);
                } else if ("MultiLineString".equals(type) && coordinates != null) {
                    for (int line = 0; line < coordinates.length(); line++) {
                        JSONArray segment = coordinates.optJSONArray(line);
                        if (segment != null && segment.length() >= 2) routes.add(segment);
                    }
                }
            }
        }
        for (JSONObject record : removedRoadRecords(journey, routes, removedEdges)) {
            String id = record.optString("id", "");
            if (!id.isEmpty()) output.add(id);
        }
        Set<String> immutableResult = Collections.unmodifiableSet(output);
        synchronized (REMOVED_ROAD_CACHE) {
            REMOVED_ROAD_CACHE.put(journey, immutableResult);
        }
        return immutableResult;
    }

    static boolean excludesRoadFeature(JSONObject journey, JSONObject feature) {
        Set<String> removed = removedRoadIds(journey);
        if (removed.isEmpty()) return false;
        JSONObject properties = feature == null ? null : feature.optJSONObject("properties");
        if (properties == null) return false;
        for (String id : roadIds(properties)) if (removed.contains(id)) return true;
        return false;
    }

    static List<JSONObject> removedRoadRecords(JSONObject journey, List<JSONArray> routes,
                                                Set<Integer> removedEdges) {
        Map<String, String> found = new LinkedHashMap<>();
        if (journey == null || routes == null || removedEdges == null || removedEdges.isEmpty()) {
            return new ArrayList<>();
        }
        List<JSONObject> features = roadFeatures(journey);
        int edge = 0;
        for (JSONArray route : routes) {
            if (route == null) continue;
            for (int point = 1; point < route.length(); point++, edge++) {
                if (!removedEdges.contains(edge)) continue;
                JSONArray a = route.optJSONArray(point - 1);
                JSONArray b = route.optJSONArray(point);
                if (a == null || b == null || a.length() < 2 || b.length() < 2) continue;
                double lon = (a.optDouble(0) + b.optDouble(0)) / 2.0;
                double lat = (a.optDouble(1) + b.optDouble(1)) / 2.0;
                for (JSONObject feature : features) {
                    JSONObject geometry = feature.optJSONObject("geometry");
                    if (geometry == null || distanceToGeometryMetres(lon, lat, geometry)
                            > ROAD_ASSOCIATION_METRES) continue;
                    JSONObject properties = feature.optJSONObject("properties");
                    if (properties == null) continue;
                    String label = roadLabel(properties);
                    for (String id : roadIds(properties)) found.putIfAbsent(id, label);
                }
            }
        }
        List<JSONObject> records = new ArrayList<>();
        for (Map.Entry<String, String> entry : found.entrySet()) {
            try {
                records.add(new JSONObject().put("id", entry.getKey()).put("label", entry.getValue()));
            } catch (Exception ignored) { }
        }
        return records;
    }

    static boolean hasNewRemovals(Set<Integer> current, Set<Integer> original) {
        if (current == null || current.isEmpty()) return false;
        for (Integer edge : current) if (original == null || !original.contains(edge)) return true;
        return false;
    }

    private static List<JSONObject> roadFeatures(JSONObject journey) {
        List<JSONObject> output = new ArrayList<>();
        JSONObject result = journey.optJSONObject("processing_result");
        addFeatures(output, result == null ? null : result.optJSONObject("road_geojson"));
        if (output.isEmpty()) {
            addFeatures(output, result == null ? null : result.optJSONObject("motorway_geojson"));
            addFeatures(output, result == null ? null : result.optJSONObject("a_road_geojson"));
        }
        return output;
    }

    private static void addFeatures(List<JSONObject> output, JSONObject collection) {
        JSONArray values = collection == null ? null : collection.optJSONArray("features");
        if (values == null) return;
        for (int index = 0; index < values.length(); index++) {
            JSONObject feature = values.optJSONObject(index);
            if (feature != null) output.add(feature);
        }
    }

    private static Set<String> roadIds(JSONObject properties) {
        Set<String> ids = new LinkedHashSet<>();
        String rawRef = properties.optString("road_ref", properties.optString("ref", ""));
        for (String part : rawRef.split("[;,/]")) {
            String ref = part.trim().replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
            if (!ref.isEmpty() && !ref.matches("\\d+(?:[.,]\\d+)?")) ids.add("ref:" + ref);
        }
        if (ids.isEmpty()) {
            String name = properties.optString("name", properties.optString("road_name", ""));
            String normalized = normalize(name);
            if (!normalized.isEmpty() && !normalized.matches("\\d+(?:[.,]\\d+)?")) {
                ids.add("name:" + normalized);
            }
        }
        return ids;
    }

    private static String roadLabel(JSONObject properties) {
        String rawRef = properties.optString("road_ref", properties.optString("ref", ""));
        for (String part : rawRef.split("[;,/]")) {
            String ref = part.trim().replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
            if (!ref.isEmpty() && !ref.matches("\\d+(?:[.,]\\d+)?")) return ref;
        }
        return properties.optString("name", properties.optString("road_name", "Unnamed road")).trim();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    static double distanceToGeometryMetres(double longitude, double latitude,
                                                   JSONObject geometry) {
        String type = geometry.optString("type", "");
        JSONArray coordinates = geometry.optJSONArray("coordinates");
        double best = Double.MAX_VALUE;
        if ("LineString".equals(type)) return distanceToLineMetres(longitude, latitude, coordinates);
        if ("MultiLineString".equals(type) && coordinates != null) {
            for (int line = 0; line < coordinates.length(); line++) {
                best = Math.min(best, distanceToLineMetres(longitude, latitude,
                        coordinates.optJSONArray(line)));
            }
        }
        return best;
    }

    private static double distanceToLineMetres(double longitude, double latitude, JSONArray line) {
        if (line == null || line.length() == 0) return Double.MAX_VALUE;
        double best = Double.MAX_VALUE;
        double scaleX = 111320.0 * Math.cos(Math.toRadians(latitude));
        for (int index = 0; index < line.length(); index++) {
            JSONArray point = line.optJSONArray(index);
            if (point == null || point.length() < 2) continue;
            double px = (point.optDouble(0) - longitude) * scaleX;
            double py = (point.optDouble(1) - latitude) * 110540.0;
            best = Math.min(best, Math.hypot(px, py));
            if (index == 0) continue;
            JSONArray previous = line.optJSONArray(index - 1);
            if (previous == null || previous.length() < 2) continue;
            double ax = (previous.optDouble(0) - longitude) * scaleX;
            double ay = (previous.optDouble(1) - latitude) * 110540.0;
            double dx = px - ax, dy = py - ay;
            double lengthSquared = dx * dx + dy * dy;
            double amount = lengthSquared == 0 ? 0 : Math.max(0,
                    Math.min(1, -(ax * dx + ay * dy) / lengthSquared));
            best = Math.min(best, Math.hypot(ax + amount * dx, ay + amount * dy));
        }
        return best;
    }
}
