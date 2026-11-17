package com.roadprints.capture;

import static com.roadprints.capture.LocalRoadNetwork.*;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;

/** Binds accepted effective saved routes to reference sections without consulting source GPS density. */
final class AcceptedRoadRoute {
    private static final int MAX_LEGS = 35_000;

    /** All accepted human road travel contributes to one physical coverage ledger. */
    static boolean eligibleMode(String rawMode) {
        if (rawMode == null) return false;
        String mode = rawMode.trim().toLowerCase(Locale.ROOT);
        return JourneyFilters.isRoadMode(mode) || JourneyFilters.isFootMode(mode)
                || MatchingCoordinator.isCycle(mode);
    }

    record Projection(Map<Edge, List<Interval>> sections, List<JSONArray> residual,
                      long eligibleMm, long excludedMm, long unresolvedMm, long majorOrOtherMm,
                      Map<String, Integer> unresolvedLegs) {}

    static List<JSONArray> matchedLines(JSONObject result) {
        List<JSONArray> lines = new ArrayList<>();
        JSONObject geojson = result == null ? null : result.optJSONObject("geojson");
        JSONArray features = geojson == null ? null : geojson.optJSONArray("features");
        if (features == null) return lines;
        for (int i = 0; i < features.length(); i++) {
            JSONObject feature = features.optJSONObject(i);
            JSONObject geometry = feature == null ? null : feature.optJSONObject("geometry");
            if (geometry == null) continue;
            JSONArray points = geometry.optJSONArray("coordinates");
            if (points == null) continue;
            if ("LineString".equals(geometry.optString("type")) && points.length() >= 2) {
                lines.add(points);
            } else if ("MultiLineString".equals(geometry.optString("type"))) {
                for (int part = 0; part < points.length(); part++) {
                    JSONArray line = points.optJSONArray(part);
                    if (line != null && line.length() >= 2) lines.add(line);
                }
            }
        }
        return lines;
    }

    static Projection project(RoadReference reference, JSONObject journey) throws IOException, JSONException {
        JSONObject result = journey.optJSONObject("processing_result");
        if (result == null || !"complete".equals(journey.optString("processing_status"))
                || !eligibleMode(journey.optString("mode"))
                || (result.has("status") && !"ok".equals(result.optString("status")))) return null;
        List<JSONArray> lines = matchedLines(result);
        if (lines.isEmpty()) return null;
        JSONObject corrections = journey.optJSONObject("journey_corrections");
        Set<Integer> indices = indices(corrections);
        int legCount = 0;
        for (JSONArray line : lines) {
            legCount += line.length() - 1;
            if (legCount > MAX_LEGS) return null;
        }
        // An invalid index has no trustworthy physical scope. Keep the legacy view.
        for (int index : indices) if (index < 0 || index >= legCount) return null;
        Set<String> legacyIds = indices.isEmpty() ? legacyIds(corrections) : Set.of();

        Map<Edge, List<CumulativeRoadUnion.Interval>> denied = new LinkedHashMap<>();
        int correctionIndex = 0;
        if (!indices.isEmpty()) for (JSONArray line : lines) {
            for (int i = 1; i < line.length(); i++, correctionIndex++) {
                if (!indices.contains(correctionIndex)) continue;
                JSONArray a = line.optJSONArray(i - 1), b = line.optJSONArray(i);
                if (!valid(a) || !valid(b)) continue;
                CanonicalRoadAttribution.Leg leg = CanonicalRoadAttribution.attributeAcceptedLeg(reference,
                        a.getDouble(0), a.getDouble(1), b.getDouble(0), b.getDouble(1));
                for (CanonicalRoadAttribution.Alignment alignment : leg.alignments()) {
                    Span span = alignment.span();
                    denied.computeIfAbsent(span.edge(), ignored -> new ArrayList<>())
                            .add(new CumulativeRoadUnion.Interval(span.interval().start(), span.interval().end()));
                }
            }
        }

        Map<Edge, List<Interval>> sections = new LinkedHashMap<>();
        Map<String, Integer> unresolved = new LinkedHashMap<>();
        List<JSONArray> residual = new ArrayList<>();
        long eligibleMm = 0, excludedMm = 0, unresolvedMm = 0, majorOrOtherMm = 0;
        int index = 0;
        for (JSONArray line : lines) {
            JSONArray current = null;
            for (int i = 1; i < line.length(); i++, index++) {
                if (Thread.currentThread().isInterrupted()) throw new CancellationException();
                JSONArray a = line.optJSONArray(i - 1), b = line.optJSONArray(i);
                boolean removed = indices.contains(index);
                if (!valid(a) || !valid(b)) {
                    current = endFragment(residual, current);
                    unresolved.merge("invalid_coordinate", 1, Integer::sum);
                    continue;
                }
                double ax = a.getDouble(0), ay = a.getDouble(1), bx = b.getDouble(0), by = b.getDouble(1);
                long length = Math.round(111_195_080 * Math.hypot((bx - ax)
                        * Math.cos(Math.toRadians((ay + by) / 2)), by - ay));
                if (removed) {
                    excludedMm += length;
                    current = endFragment(residual, current);
                    continue;
                }
                eligibleMm += length;
                if (length == 0) continue;
                CanonicalRoadAttribution.Leg leg = CanonicalRoadAttribution.attributeAcceptedLeg(reference, ax, ay, bx, by);
                if (leg.unresolvedReason() != null) {
                    unresolved.merge(leg.unresolvedReason(), 1, Integer::sum);
                    unresolvedMm += length;
                }
                double cursor = 0;
                for (CanonicalRoadAttribution.Alignment alignment : leg.alignments()) {
                    Span span = alignment.span();
                    Edge edge = span.edge();
                    if (!edge.eligible) {
                        majorOrOtherMm += Math.round(length * (alignment.end() - alignment.start()));
                        continue;
                    }
                    boolean legacyExcluded = excludes(legacyIds, edge.label);
                    if (legacyExcluded) {
                        excludedMm += Math.round(length * (alignment.end() - alignment.start()));
                    } else {
                        List<CumulativeRoadUnion.Interval> exclusions = denied.get(edge);
                        if (exclusions == null) {
                            sections.computeIfAbsent(edge, ignored -> new ArrayList<>()).add(span.interval());
                        } else {
                            List<CumulativeRoadUnion.Interval> retained = List.of(
                                    new CumulativeRoadUnion.Interval(span.interval().start(), span.interval().end()));
                            for (CumulativeRoadUnion.Interval exclusion : exclusions)
                                retained = CumulativeRoadUnion.subtract(retained, exclusion);
                            int retainedMm = 0;
                            for (CumulativeRoadUnion.Interval interval : retained) {
                                sections.computeIfAbsent(edge, ignored -> new ArrayList<>())
                                        .add(new Interval(interval.startMm(), interval.endMm()));
                                retainedMm += interval.endMm() - interval.startMm();
                            }
                            excludedMm += span.interval().end() - span.interval().start() - retainedMm;
                        }
                    }
                    // Keep unresolved pieces and major roads in the established legacy
                    // drawing pipeline. Only suppress the exact resolved local interval.
                    double start = alignment.start(), end = alignment.end();
                    if (start - cursor > 0 && (start - cursor) * length > 1)
                        current = addFragment(current, point(a, b, cursor), point(a, b, start));
                    current = endFragment(residual, current);
                    cursor = Math.max(cursor, end);
                }
                if (cursor == 0 || (1 - cursor) * length > 1)
                    current = addFragment(current, point(a, b, cursor), b);
            }
            endFragment(residual, current);
        }
        sections.replaceAll((edge, intervals) -> union(intervals));
        return new Projection(sections, residual, eligibleMm, excludedMm, unresolvedMm, majorOrOtherMm, unresolved);
    }

    private static Set<Integer> indices(JSONObject corrections) {
        JSONArray removed = corrections == null ? null : corrections.optJSONArray("removed_matched_segments");
        Set<Integer> result = new LinkedHashSet<>();
        if (removed != null) for (int i = 0; i < removed.length(); i++) result.add(removed.optInt(i, -1));
        return result;
    }

    private static Set<String> legacyIds(JSONObject corrections) {
        JSONArray removed = corrections == null ? null : corrections.optJSONArray("removed_road_ids");
        Set<String> result = new LinkedHashSet<>();
        if (removed != null) for (int i = 0; i < removed.length(); i++) result.add(removed.optString(i));
        return result;
    }

    private static boolean excludes(Set<String> ids, String label) {
        if (ids.isEmpty() || label.isBlank()) return false;
        return ids.contains("name:" + label.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " "))
                || ids.contains("ref:" + label.replaceAll("\\s+", "").toUpperCase(Locale.ROOT));
    }

    private static boolean valid(JSONArray point) {
        if (point == null || point.length() < 2) return false;
        double lon = point.optDouble(0), lat = point.optDouble(1);
        return lon >= -180 && lon <= 180 && lat >= -90 && lat <= 90;
    }

    private static JSONArray endFragment(List<JSONArray> residual, JSONArray current) {
        if (current != null && current.length() >= 2) residual.add(current);
        return null;
    }

    private static JSONArray addFragment(JSONArray current, JSONArray first, JSONArray last) {
        if (current == null) current = new JSONArray().put(first);
        return current.put(last);
    }

    private static JSONArray point(JSONArray a, JSONArray b, double t) throws JSONException {
        if (t == 0) return a;
        if (t == 1) return b;
        return new JSONArray().put(a.getDouble(0) + t * (b.getDouble(0) - a.getDouble(0)))
                .put(a.getDouble(1) + t * (b.getDouble(1) - a.getDouble(1)));
    }
}
