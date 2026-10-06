package com.roadprints.capture;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** On-demand, bounded replay of saved matches and canonical coverage additions. */
final class DiscoveryReplay {
    static final class Section {
        final JSONArray points;
        final int color;
        Section(JSONArray points, int color) { this.points = points; this.color = color; }
    }
    final List<Section> routes = new ArrayList<>(), discoveries = new ArrayList<>();
    final Set<String> labels = new java.util.LinkedHashSet<>();
    int points;
    boolean simplified;

    static DiscoveryReplay calculate(Context context, Set<String> ids) throws Exception {
        return calculate(context, ids, () -> false);
    }

    static DiscoveryReplay calculate(Context context, Set<String> ids, java.util.function.BooleanSupplier cancelled) throws Exception {
        DiscoveryReplay output = new DiscoveryReplay();
        List<JSONObject> candidates = new ArrayList<>();
        long first = Long.MAX_VALUE;
        Set<String> candidateRefs = new HashSet<>();
        for (String id : ids) {
            JSONObject journey = JourneyStore.get(context, id);
            if (journey == null || !"complete".equals(journey.optString("processing_status"))) continue;
            String mode = journey.optString("mode");
            if (!("driving".equals(mode) || "bus".equals(mode) || "walking".equals(mode)
                    || "running".equals(mode) || "pedestrian".equals(mode))) continue;
            JSONObject match = journey.optJSONObject("processing_result");
            if (match != null) for (String key : new String[]{"motorway_geojson", "a_road_geojson"}) {
                JSONObject collection = match.optJSONObject(key);
                JSONArray features = collection == null ? null : collection.optJSONArray("features");
                if (features != null) for (int i = 0; i < features.length(); i++) {
                    JSONObject feature = features.optJSONObject(i);
                    JSONObject props = feature == null ? null : feature.optJSONObject("properties");
                    if (props != null) for (String ref : props.optString("road_ref", props.optString("ref")).split("[;,/]"))
                        candidateRefs.add(ref.trim().replaceAll("\\s+", "").toUpperCase(Locale.ROOT));
                }
            }
            // Keep IDs and summaries, not entire journey geometries, between reads.
            if (ReturnRecapStore.time(journey.optString("started_at")) == Long.MIN_VALUE) continue;
            candidates.add(new JSONObject().put("journey_id", id)
                    .put("started_at", journey.optString("started_at")));
            first = Math.min(first, ReturnRecapStore.time(journey.optString("started_at")));
        }
        if (candidates.isEmpty()) return output;
        final long cutoff = first;
        Map<String, Set<Integer>> priorMotorways = new HashMap<>(), priorARoads = new HashMap<>();
        Set<String> priorNames = new HashSet<>();
        // Finish each calculator before constructing the next to release spatial indexes.
        MotorwayProgressCalculator oldMotorways = new MotorwayProgressCalculator(context, null, false, false);
        ARoadProgressCalculator oldARoads = new ARoadProgressCalculator(context, false);
        JourneyStore.forEachRoadEvidence(context, cancelled, journey -> {
            if (!ids.contains(journey.optString("journey_id")) && older(journey, cutoff)) {
                oldMotorways.addJourney(journey, candidateRefs);
                oldARoads.addJourney(journey, candidateRefs);
                if ("complete".equals(journey.optString("processing_status")))
                    priorNames.addAll(localFeatures(journey).keySet());
            }
        });
        for (MotorwayProgressCalculator.Road road : oldMotorways.finish().roads)
            priorMotorways.put(road.id, new HashSet<>(road.coveredSections));
        for (ARoadProgressCalculator.Road road : oldARoads.finish().roads)
            priorARoads.put(road.id, new HashSet<>(road.covered));
        candidates.sort((a, b) -> Long.compare(ReturnRecapStore.time(a.optString("started_at")),
                ReturnRecapStore.time(b.optString("started_at"))));
        MotorwayProgressCalculator motorways = new MotorwayProgressCalculator(context, null, false, false);
        for (JSONObject row : candidates) {
            JSONObject journey = JourneyStore.get(context, row.optString("journey_id"));
            if (journey == null || !"complete".equals(journey.optString("processing_status"))) continue;
            motorways.addJourney(journey);
            for (JSONArray route : JourneyListActivity.matchedRouteSegmentsForDisplay(journey))
                output.add(output.routes, route, 0xFF101820);
            for (Map.Entry<String, JSONObject> entry : localFeatures(journey).entrySet()) {
                if (!priorNames.add(entry.getKey())) continue;
                JSONObject feature = entry.getValue();
                JSONObject props = feature.optJSONObject("properties");
                output.labels.add(props.optString("name", props.optString("road_name")));
                for (JSONArray line : lines(feature.optJSONObject("geometry")))
                    output.add(output.discoveries, line, 0xFF101820);
            }
        }
        output.addCanonical(motorways.newlyCoveredSections(priorMotorways), 0xFF176DB5);
        motorways.finish();
        ARoadProgressCalculator aRoads = new ARoadProgressCalculator(context, false);
        for (JSONObject row : candidates) {
            JSONObject journey = JourneyStore.get(context, row.optString("journey_id"));
            aRoads.addJourney(journey);
        }
        output.addCanonical(aRoads.newlyCoveredSections(priorARoads), 0xFF008755);
        aRoads.finish();
        return output;
    }

    static DiscoveryReplay fromJourney(JSONObject journey) {
        DiscoveryReplay output=new DiscoveryReplay();
        if(journey!=null&&"complete".equals(journey.optString("processing_status")))
            for(JSONArray route:JourneyListActivity.matchedRouteSegmentsForDisplay(journey))
                output.add(output.routes,route,0xFF101820);
        return output;
    }

    static DiscoveryReplay loadRoutes(Context context, Set<String> ids) throws Exception {
        DiscoveryReplay output = new DiscoveryReplay();
        for (String id : ids) {
            DiscoveryReplay selected=fromJourney(JourneyStore.get(context,id));
            for(Section section:selected.routes)output.add(output.routes,section.points,section.color);
        }
        return output;
    }

    JSONObject toJson() throws Exception {
        JSONObject value=new JSONObject().put("format",1).put("simplified",simplified);
        for(String key:new String[]{"routes","discoveries"}) {
            JSONArray sections=new JSONArray();
            for(Section section:"routes".equals(key)?routes:discoveries)
                sections.put(new JSONObject().put("points",section.points).put("color",section.color));
            value.put(key,sections);
        }
        JSONArray names=new JSONArray();for(String label:labels){if(names.length()>=100)break;names.put(label);}
        return value.put("labels",names);
    }

    static DiscoveryReplay fromJson(JSONObject value) {
        if(value==null||value.optInt("format")!=1||value.optJSONArray("routes")==null
                ||value.optJSONArray("discoveries")==null||value.optJSONArray("labels")==null)return null;
        DiscoveryReplay replay=new DiscoveryReplay();
        for(String key:new String[]{"routes","discoveries"}) {
            JSONArray sections=value.optJSONArray(key);
            for(int i=0;i<Math.min(600,sections.length());i++) {
                JSONObject section=sections.optJSONObject(i);if(section==null)return null;
                replay.add("routes".equals(key)?replay.routes:replay.discoveries,section.optJSONArray("points"),section.optInt("color",0xFF101820));
            }
        }
        JSONArray labels=value.optJSONArray("labels");
        for(int i=0;i<Math.min(100,labels.length());i++)replay.labels.add(labels.optString(i));
        replay.simplified|=value.optBoolean("simplified");return replay;
    }

    private static boolean older(JSONObject journey, long cutoff) {
        return ReturnRecapStore.time(journey.optString("started_at")) < cutoff;
    }

    private void addCanonical(Map<String, List<JSONArray>> fresh, int color) {
        for (Map.Entry<String, List<JSONArray>> entry : fresh.entrySet()) {
            String label = entry.getKey().replaceFirst("^(GB|NI):", "");
            labels.add("New stretch of " + label);
            for (JSONArray line : entry.getValue()) add(discoveries, line, color);
        }
    }

    private void add(List<Section> target, JSONArray line, int color) {
        if (line == null || line.length() < 2) return;
        if (points >= 20_000 || routes.size() + discoveries.size() >= 600) {
            simplified = true; return;
        }
        int count = Math.min(1000, Math.min(line.length(), 20_000 - points));
        if (count < 2) return;
        JSONArray retained = new JSONArray();
        for (int i = 0; i < count; i++)
            retained.put(line.optJSONArray((int) ((long) i * (line.length() - 1) / (count - 1))));
        if (count < line.length()) simplified = true;
        target.add(new Section(retained, color));
        points += count;
    }

    static List<JSONArray> lines(JSONObject geometry) {
        if (geometry == null) return Collections.emptyList();
        JSONArray coordinates = geometry.optJSONArray("coordinates");
        List<JSONArray> result = new ArrayList<>();
        if ("LineString".equals(geometry.optString("type")) && coordinates != null)
            result.add(coordinates);
        else if ("MultiLineString".equals(geometry.optString("type")) && coordinates != null)
            for (int i = 0; i < coordinates.length(); i++) {
                JSONArray line = coordinates.optJSONArray(i); if (line != null) result.add(line);
            }
        return result;
    }

    static Map<String, JSONObject> localFeatures(JSONObject journey) {
        Map<String, JSONObject> result = new LinkedHashMap<>();
        JSONObject match = journey.optJSONObject("processing_result");
        JSONObject collection = match == null ? null : match.optJSONObject("road_geojson");
        JSONArray features = collection == null ? null : collection.optJSONArray("features");
        if (features == null) return result;
        for (int i = 0; i < features.length(); i++) {
            JSONObject feature = features.optJSONObject(i);
            if (feature == null || JourneyCorrectionUtils.excludesRoadFeature(journey, feature)) continue;
            JSONObject props = feature.optJSONObject("properties");
            if (props == null) continue;
            String ref = props.optString("road_ref", props.optString("ref")).trim();
            if (!ref.isEmpty() && !ref.matches("\\d+(?:[.,]\\d+)?")) continue;
            String name = props.optString("name", props.optString("road_name")).trim();
            if (name.isEmpty() || name.matches("\\d+(?:[.,]\\d+)?")) continue;
            String key = "name:" + name.toLowerCase(Locale.UK).replaceAll("\\s+", " ");
            result.putIfAbsent(key, feature);
        }
        return result;
    }
}
