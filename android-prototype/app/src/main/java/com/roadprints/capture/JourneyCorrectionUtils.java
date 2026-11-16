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
    private static final Map<JSONObject, RemovalScope> REMOVAL_CACHE = new WeakHashMap<>();

    private JourneyCorrectionUtils() {}

    /** Edge indices belong to a particular matched geometry, never to its replacement. */
    static void reconcileMatchedRemovals(JSONObject journey, JSONObject replacement) throws Exception {
        JSONObject corrections=journey.optJSONObject("journey_corrections");
        JSONArray removed=corrections==null?null:corrections.optJSONArray("removed_matched_segments");
        if(removed==null||removed.length()==0)return;
        List<String> before=matchedEdgeKeys(journey.optJSONObject("processing_result")),after=matchedEdgeKeys(replacement);
        if(before.equals(after))return;
        Set<String> exclusions=new LinkedHashSet<>();
        Map<String,Integer> selected=new LinkedHashMap<>(),all=new LinkedHashMap<>();
        for(String edge:before)all.put(edge,all.getOrDefault(edge,0)+1);
        for(int i=0;i<removed.length();i++){
            int index=removed.optInt(i,-1);
            if(index<0||index>=before.size())throw editedMatchChanged();
            String edge=before.get(index);exclusions.add(edge);selected.put(edge,selected.getOrDefault(edge,0)+1);
        }
        // Exact physical edges only: do not transfer an edit to a nearby footway.
        for(String edge:exclusions)if(!all.get(edge).equals(selected.get(edge))||!after.contains(edge))throw editedMatchChanged();
        JSONArray mapped=new JSONArray();for(int i=0;i<after.size();i++)if(exclusions.contains(after.get(i)))mapped.put(i);
        corrections.put("removed_matched_segments",mapped);
    }
    static final class EditedMatchRetained extends IllegalStateException {
        EditedMatchRetained(String message){super(message);}
    }
    private static EditedMatchRetained editedMatchChanged(){return new EditedMatchRetained(
            "The new match changes sections you edited. Your previous route and edits are preserved; review or restore those sections before rematching.");}
    private static List<String> matchedEdgeKeys(JSONObject result) throws Exception {
        List<String> keys=new ArrayList<>();
        for(JSONArray line:JourneyMapEditorActivity.matchedRouteSegments(result))for(int i=1;i<line.length();i++){
            JSONArray a=line.getJSONArray(i-1),b=line.getJSONArray(i);
            String first=Double.toHexString(a.getDouble(0))+","+Double.toHexString(a.getDouble(1));
            String second=Double.toHexString(b.getDouble(0))+","+Double.toHexString(b.getDouble(1));
            keys.add(first.compareTo(second)<=0?first+"|"+second:second+"|"+first);
        }
        return keys;
    }

    static Set<Integer> removedEdgeIndices(JSONObject corrections) {
        Set<Integer> result = new LinkedHashSet<>();
        JSONArray values = corrections == null ? null : corrections.optJSONArray("removed_matched_segments");
        if (values != null) {
            for (int i = 0; i < values.length(); i++) result.add(values.optInt(i, -1));
        }
        return result;
    }

    static Set<String> savedRoadIds(JSONObject corrections) {
        Set<String> result = new LinkedHashSet<>();
        JSONArray values = corrections == null ? null : corrections.optJSONArray("removed_road_ids");
        if (values != null) {
            for (int i = 0; i < values.length(); i++) {
                String id = values.optString(i, "").trim();
                if (!id.isEmpty()) result.add(id);
            }
        }
        return result;
    }

    private record RemovalScope(List<RouteRemoval.Segment> segments, Set<String> legacyIds) {}
    private record TrimmedLine(List<JSONArray> fragments, double original, double removed) {}

    private static RemovalScope scope(JSONObject journey, JSONObject corrections) {
        synchronized (REMOVAL_CACHE) {
            RemovalScope known = REMOVAL_CACHE.get(corrections);
            if (known != null) return known;
        }
        Set<Integer> indices = removedEdgeIndices(corrections);
        List<RouteRemoval.Segment> selected = indices.isEmpty() ? Collections.emptyList()
                : RouteRemoval.selected(JourneyMapEditorActivity.matchedRouteSegments(
                        journey.optJSONObject("processing_result")), indices);
        boolean located = !selected.isEmpty() && selected.size() == indices.size();
        RemovalScope scope = new RemovalScope(selected, located ? Collections.emptySet() : savedRoadIds(corrections));
        synchronized (REMOVAL_CACHE) { REMOVAL_CACHE.put(corrections, scope); }
        return scope;
    }

    /** Retain only the road geometry and distance outside an explicit removal. */
    static JSONObject retainedRoadFeature(JSONObject journey, JSONObject feature) {
        if (journey == null || feature == null) return feature;
        JSONObject corrections = journey.optJSONObject("journey_corrections");
        if (corrections == null) return feature;
        RemovalScope scope = scope(journey, corrections);
        JSONObject properties = feature.optJSONObject("properties");
        if (properties != null && !scope.legacyIds.isEmpty()) {
            for (String id : roadIds(properties)) if (scope.legacyIds.contains(id)) return null;
        }
        if (scope.segments.isEmpty()) return feature;
        List<JSONArray> lines = DiscoveryReplay.lines(feature.optJSONObject("geometry"));
        List<JSONArray> retained = new ArrayList<>();
        double original = 0, removed = 0;
        for (JSONArray line : lines) {
            TrimmedLine trimmed = trimLine(line, scope.segments);
            retained.addAll(trimmed.fragments);
            original += trimmed.original;
            removed += trimmed.removed;
        }
        if (removed <= 0) return feature;
        if (retained.isEmpty()) return null;
        try {
            JSONObject clipped = copy(feature);
            JSONObject geometry = copy(feature.getJSONObject("geometry"));
            geometry.put("type", retained.size() == 1 ? "LineString" : "MultiLineString");
            geometry.put("coordinates", retained.size() == 1 ? retained.get(0) : new JSONArray(retained));
            clipped.put("geometry", geometry);
            if (properties != null && properties.has("distance_m")) {
                JSONObject adjusted = copy(properties);
                adjusted.put("distance_m", properties.optDouble("distance_m", 0) * Math.max(0, 1 - removed / original));
                clipped.put("properties", adjusted);
            }
            return clipped;
        } catch (org.json.JSONException invalid) {
            return null;
        }
    }

    private static JSONObject copy(JSONObject source) throws org.json.JSONException {
        JSONObject result = new JSONObject();
        for (java.util.Iterator<String> keys = source.keys(); keys.hasNext();) {
            String key = keys.next();
            result.put(key, source.get(key));
        }
        return result;
    }

    private static TrimmedLine trimLine(JSONArray line, List<RouteRemoval.Segment> removals) {
        List<JSONArray> fragments = new ArrayList<>();
        JSONArray path = null;
        double original = 0, removed = 0;
        for (int i = 1; i < line.length(); i++) {
            JSONArray a = line.optJSONArray(i - 1), b = line.optJSONArray(i);
            RouteRemoval.Segment edge = RouteRemoval.segment(a, b);
            if (edge == null) {
                path = null;
                continue;
            }
            double scaleX = 111_195.08 * Math.cos(Math.toRadians(edge.a().y()));
            RouteRemoval.Segment metres = RouteRemoval.metres(edge, scaleX, 111_195.08);
            double length = metres.length();
            if (length == 0) continue;
            original += length;
            List<double[]> cuts = new ArrayList<>();
            for (RouteRemoval.Segment removal : removals) {
                double[] cut = RouteRemoval.overlap(metres, RouteRemoval.metres(removal, scaleX, 111_195.08));
                if (cut != null) cuts.add(cut);
            }
            cuts.sort(java.util.Comparator.comparingDouble(cut -> cut[0]));
            double cursor = 0;
            for (double[] cut : cuts) {
                if (cut[1] <= cursor) continue;
                if (cut[0] > cursor + 1e-8) appendFragment(fragments, path, edge, cursor, cut[0]);
                path = null;
                removed += (cut[1] - Math.max(cursor, cut[0])) * length;
                cursor = cut[1];
            }
            if (cursor < 1 - 1e-8) path = appendFragment(fragments, path, edge, cursor, 1);
        }
        return new TrimmedLine(fragments, original, removed);
    }

    private static JSONArray appendFragment(List<JSONArray> fragments, JSONArray path,
                                            RouteRemoval.Segment edge, double start, double end) {
        if (path == null) {
            path = new JSONArray().put(RouteRemoval.coordinate(edge, start));
            fragments.add(path);
        }
        path.put(RouteRemoval.coordinate(edge, end));
        return path;
    }

    static List<JSONObject> removedRoadRecords(JSONObject journey, List<JSONArray> routes,
                                                Set<Integer> removedEdges) {
        List<JSONObject> records = new ArrayList<>();
        if (removedEdges.isEmpty()) return records;
        List<RouteRemoval.Segment> selected = RouteRemoval.selected(routes, removedEdges);
        Map<String, String> found = new LinkedHashMap<>();
        for (JSONObject feature : roadFeatures(journey)) {
            JSONObject properties = feature.optJSONObject("properties");
            if (properties == null) continue;
            for (JSONArray line : DiscoveryReplay.lines(feature.optJSONObject("geometry"))) {
                if (trimLine(line, selected).removed <= 0) continue;
                for (String id : roadIds(properties)) found.putIfAbsent(id, roadLabel(properties));
                break;
            }
        }
        for (Map.Entry<String, String> entry : found.entrySet()) {
            try {
                records.add(new JSONObject().put("id", entry.getKey()).put("label", entry.getValue()));
            } catch (org.json.JSONException ignored) { }
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
