package com.roadprints.capture;

import org.json.JSONArray;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Deterministic geometry-only A/B evidence for one fixed Map fixture.
 *
 * Never changes accepted source lines, reference coverage, NCN credit or the
 * Android renderer. Intended for bounded JVM validation, not per-frame use.
 * The canonical fingerprint is orientation- and section-order-independent,
 * but keeps distinct physical paths separate (no fuzzy geometry union).
 */
final class CumulativeMapSnapshotComparison {
    private CumulativeMapSnapshotComparison() {}

    record Snapshot(String scenarioId, int acceptedSections, int displaySections,
                    int displayPoints, double missingMetres, double unsupportedMetres,
                    String acceptedFingerprint, String geometryFingerprint) {
        JSONObject json() throws Exception {
            return new JSONObject().put("scenario", scenarioId)
                    .put("accepted_sections", acceptedSections)
                    .put("display_sections", displaySections)
                    .put("display_points", displayPoints)
                    .put("max_accepted_to_display_m", missingMetres)
                    .put("max_display_to_accepted_m", unsupportedMetres)
                    .put("accepted_fingerprint_sha256", acceptedFingerprint)
                    .put("display_fingerprint_sha256", geometryFingerprint);
        }
    }

    static Snapshot snapshot(String scenarioId, List<JSONArray> accepted,
                             List<JSONArray> displayed) throws Exception {
        if (scenarioId == null || scenarioId.isBlank() || accepted == null
                || displayed == null || accepted.isEmpty() || displayed.isEmpty())
            throw new IllegalArgumentException("Snapshot requires scenario, evidence and display");
        double missing = 0, unsupported = 0;
        int points = 0;
        for (JSONArray line : accepted) {
            double deviation = CumulativeMapShapeAudit.acceptedToCumulativeDeviationMetres(
                    line, displayed);
            if (!Double.isFinite(deviation))
                throw new IllegalArgumentException("Invalid accepted or displayed line");
            missing = Math.max(missing, deviation);
        }
        for (JSONArray line : displayed) {
            points += line.length();
            double deviation = CumulativeMapShapeAudit.acceptedToCumulativeDeviationMetres(
                    line, accepted);
            if (!Double.isFinite(deviation))
                throw new IllegalArgumentException("Invalid accepted or displayed line");
            unsupported = Math.max(unsupported, deviation);
        }
        return new Snapshot(scenarioId, accepted.size(), displayed.size(), points,
                missing, unsupported, fingerprint(accepted), fingerprint(displayed));
    }

    /**
     * Capture the actual prepared MapRoutes geometry passed to the overview
     * renderer, not a parallel hand-written approximation of the accumulator.
     * Only TRAVELLED lines are included; incomplete reference-road background
     * layers must never masquerade as accepted journey coverage.
     *
     * Runs only on bounded validation fixtures. It allocates JSON for compact
     * local intervals just as MapRoutes.localRoadLines does for replay; never
     * invoke it on the owner's full archive or the UI drawing thread.
     */
    static Snapshot snapshotPreparedOverview(String scenarioId, List<JSONArray> accepted,
                                             MapActivity.MapRoutes prepared) throws Exception {
        if (prepared == null) throw new IllegalArgumentException("Missing prepared Map");
        List<JSONArray> displayed = new ArrayList<>();
        final int maxSections = 512;
        final int maxPoints = 8192;
        int[] totalPoints = {0};
        // Same travelled-geometry sources as displayMapRoutes(): its
        // setRouteData(null, sections), setLocalRoadLayer(localRoadLayer),
        // setMotorwayCoverageSegments(incomplete, covered) and
        // setARoadCoverageSegments(incomplete, covered).
        appendBounded(displayed, prepared.sections, maxSections, maxPoints, totalPoints);
        appendBounded(displayed, prepared.localRoadLines, maxSections, maxPoints, totalPoints);
        appendBounded(displayed, prepared.coveredMotorwaySections,
                maxSections, maxPoints, totalPoints);
        appendBounded(displayed, prepared.coveredARoadSections,
                maxSections, maxPoints, totalPoints);
        appendBounded(displayed, prepared.ferrySections,
                maxSections, maxPoints, totalPoints);
        return snapshot(scenarioId, accepted, displayed);
    }

    private static void appendBounded(List<JSONArray> destination, List<JSONArray> source,
                                      int maxSections, int maxPoints, int[] totalPoints) {
        for (JSONArray geometry : source) {
            if (destination.size() >= maxSections || geometry == null
                    || geometry.length() < 2
                    || geometry.length() > maxPoints - totalPoints[0])
                throw new IllegalArgumentException("Map A/B fixture exceeds bounded geometry budget");
            destination.add(geometry);
            totalPoints[0] += geometry.length();
        }
    }

    /**
     * Read an existing Map snapshot in the same wire format emitted by
     * MapActivity.mapRoutesToCache(). Only derived travelled geometry is read.
     * This is deliberately offline, bounded and non-mutating. Never load the
     * owner's full Map on the Android UI thread for audit purposes.
     */
    static Snapshot snapshotSerializedOverview(String scenarioId, List<JSONArray> accepted,
                                               JSONObject persisted) throws Exception {
        if (persisted == null || !persisted.has("sections")
                || !persisted.has("localRoads") || !persisted.has("coveredMotorways")
                || !persisted.has("coveredARoads") || !persisted.has("ferries"))
            throw new IllegalArgumentException("Missing Map snapshot geometry collections");
        List<JSONArray> displayed = new ArrayList<>();
        final int maxSections = 512, maxPoints = 8192;
        int[] points = {0};
        for (String key : new String[]{"sections", "coveredMotorways", "coveredARoads", "ferries"}) {
            JSONArray rows = persisted.getJSONArray(key);
            for (int i = 0; i < rows.length(); i++) {
                JSONArray line = rows.optJSONArray(i);
                if (line == null || line.length() < 2
                        || displayed.size() >= maxSections
                        || line.length() > maxPoints - points[0])
                    throw new IllegalArgumentException("Map A/B fixture exceeds bounded geometry budget");
                displayed.add(line);
                points[0] += line.length();
            }
        }
        JSONArray locals = persisted.getJSONArray("localRoads");
        for (int i = 0; i < locals.length(); i++) {
            if (i >= maxSections)
                throw new IllegalArgumentException("Map A/B fixture exceeds bounded local roads");
            LocalRoadCoverage.Section section =
                    LocalRoadCoverage.Section.decode(locals.getJSONObject(i));
            for (LocalRoadCoverage.Line interval : section.lines) {
                if (displayed.size() >= maxSections || points[0] + 2 > maxPoints)
                    throw new IllegalArgumentException("Map A/B fixture exceeds bounded geometry budget");
                displayed.add(interval.points());
                points[0] += 2;
            }
        }
        return snapshot(scenarioId, accepted, displayed);
    }

    /**
     * Diagnostic exports are NOT the actual saved journey file format.
     * Only accept a complete, uncorrected matched journey, because applying
     * owner removals needs the full correction-aware production path.
     */
    static List<JSONArray> acceptedUncorrectedDiagnostic(JSONObject diagnostic) {
        if (diagnostic == null || !"roadprints_journey_diagnostics".equals(
                diagnostic.optString("format")))
            throw new IllegalArgumentException("Expected Roadprints journey diagnostic export");
        JSONObject journey = diagnostic.optJSONObject("journey");
        if (journey == null || !"complete".equals(journey.optString("processing_status"))
                || journey.has("journey_corrections"))
            throw new IllegalArgumentException(
                    "Use production correction-aware geometry for corrected journeys");
        JSONObject processing = journey.optJSONObject("processing_result");
        if (processing == null || !"ok".equals(processing.optString("status")))
            throw new IllegalArgumentException("Expected complete accepted match");
        List<JSONArray> matched = AcceptedRoadRoute.matchedLines(processing);
        if (matched.isEmpty()) throw new IllegalArgumentException("No accepted route geometry");
        return matched;
    }

    /**
     * Compare one accepted journey with a geographically scoped union of
     * prepared Map lines. Other archived journeys may legitimately contribute
     * extra roads; their presence must NOT be labelled invented geometry here.
     * No unfiltered full-archive geometry is materialised by this helper.
     */
    record CorridorReport(String scenarioId, int acceptedPieces, int displayedPieces,
                          int displayedPoints, double maxUnrepresentedMetres,
                          String acceptedFingerprint, String displayFingerprint) {
        JSONObject json() throws Exception {
            return new JSONObject().put("scenario", scenarioId)
                    .put("accepted_pieces", acceptedPieces)
                    .put("displayed_pieces", displayedPieces)
                    .put("displayed_points", displayedPoints)
                    .put("max_accepted_to_display_m",
                            Double.isFinite(maxUnrepresentedMetres)
                                    ? maxUnrepresentedMetres : JSONObject.NULL)
                    .put("accepted_coverage_status",
                            Double.isFinite(maxUnrepresentedMetres)
                                    ? "measured" : "no_display_coverage")
                    .put("accepted_fingerprint_sha256", acceptedFingerprint)
                    .put("display_fingerprint_sha256", displayFingerprint)
                    .put("extra_lines_attribution", "unassessed_other_journeys_may_contribute");
        }
    }

    static CorridorReport comparePreparedCorridor(String scenarioId, List<JSONArray> accepted,
            MapActivity.MapRoutes prepared, MapCorridorGeometry.Bounds window) throws Exception {
        if (prepared == null || accepted == null || accepted.isEmpty() || window == null)
            throw new IllegalArgumentException("Missing prepared Map or corridor evidence");
        List<JSONArray> visible = new ArrayList<>();
        int[] points = {0};
        appendCorridor(visible, prepared.sections, window, points);
        appendCorridor(visible, prepared.localRoadLines, window, points);
        appendCorridor(visible, prepared.coveredMotorwaySections, window, points);
        appendCorridor(visible, prepared.coveredARoadSections, window, points);
        appendCorridor(visible, prepared.ferrySections, window, points);
        return compareCorridorLines(scenarioId, accepted, visible, window);
    }

    static CorridorReport compareSerializedCorridor(String scenarioId, List<JSONArray> accepted,
            JSONObject persisted, MapCorridorGeometry.Bounds window) throws Exception {
        if (persisted == null || window == null || accepted == null || accepted.isEmpty()
                || !persisted.has("sections") || !persisted.has("localRoads")
                || !persisted.has("coveredMotorways") || !persisted.has("coveredARoads")
                || !persisted.has("ferries"))
            throw new IllegalArgumentException("Incomplete Map snapshot or accepted evidence");
        List<JSONArray> visible = new ArrayList<>();
        int[] points = {0};
        for (String key : new String[]{"sections","coveredMotorways","coveredARoads","ferries"}) {
            JSONArray source = persisted.getJSONArray(key);
            for (int i=0;i<source.length();i++) {
                JSONArray line = source.optJSONArray(i);
                if (line == null) throw new IllegalArgumentException("Malformed Map snapshot line");
                addClipped(visible, line, window, points);
            }
        }
        JSONArray locals=persisted.getJSONArray("localRoads");
        for (int i=0;i<locals.length();i++) {
            LocalRoadCoverage.Section section =
                    LocalRoadCoverage.Section.decode(locals.getJSONObject(i));
            for (LocalRoadCoverage.Line interval : section.lines)
                addClipped(visible, interval.points(), window, points);
        }
        return compareCorridorLines(scenarioId, accepted, visible, window);
    }

    private static CorridorReport compareCorridorLines(String scenarioId,
            List<JSONArray> accepted, List<JSONArray> visible,
            MapCorridorGeometry.Bounds window) throws Exception {
        if (scenarioId == null || scenarioId.isBlank() || window == null)
            throw new IllegalArgumentException("Missing corridor ID or bounds");
        List<JSONArray> acceptedVisible=new ArrayList<>();
        for (JSONArray route:accepted) {
            acceptedVisible.addAll(MapCorridorGeometry.clip(route,window));
        }
        if (acceptedVisible.isEmpty())
            throw new IllegalArgumentException("Accepted journey does not cross corridor");
        double worst=0;
        for (JSONArray route:acceptedVisible) {
            double deviation=CumulativeMapShapeAudit.acceptedToCumulativeDeviationMetres(route,visible);
            worst=Math.max(worst,deviation);
        }
        return new CorridorReport(scenarioId,acceptedVisible.size(),visible.size(),
                visible.stream().mapToInt(JSONArray::length).sum(),
                worst,fingerprint(acceptedVisible),fingerprint(visible));
    }

    private static void appendCorridor(List<JSONArray> destination, List<JSONArray> source,
            MapCorridorGeometry.Bounds window, int[] points) {
        for (JSONArray line : source) addClipped(destination,line,window,points);
    }

    private static void addClipped(List<JSONArray> destination,JSONArray line,
            MapCorridorGeometry.Bounds window,int[] points) {
        for (JSONArray piece:MapCorridorGeometry.clip(line,window)) {
            if (destination.size() >= 512 || points[0]+piece.length() > 8192)
                throw new IllegalArgumentException("Map corridor fixture exceeds bounded geometry budget");
            destination.add(piece);
            points[0] += piece.length();
        }
    }

    /** The same physical vertices in reverse direction or different list order
     * produce an identical fingerprint. No accepted points are rounded away:
     * the fingerprint identifies exact test geometry, not fuzzy equivalence. */
    private static String fingerprint(List<JSONArray> lines) throws Exception {
        List<String> encodings = new ArrayList<>(lines.size());
        for (JSONArray line : lines) {
            List<String> points = new ArrayList<>(line.length());
            for (int i = 0; i < line.length(); i++) {
                JSONArray p = line.optJSONArray(i);
                points.add(Double.toString(p.optDouble(0)) + ","
                        + Double.toString(p.optDouble(1)));
            }
            String forward = String.join(";", points);
            Collections.reverse(points);
            String reverse = String.join(";", points);
            encodings.add(forward.compareTo(reverse) <= 0 ? forward : reverse);
        }
        Collections.sort(encodings);
        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        byte[] digest = sha.digest(String.join("\n", encodings)
                .getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder(digest.length * 2);
        for (byte b : digest) hex.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
        return hex.toString();
    }

    static JSONObject compare(Snapshot baseline, Snapshot candidate) throws Exception {
        if (baseline == null || candidate == null
                || !baseline.scenarioId().equals(candidate.scenarioId())
                || !baseline.acceptedFingerprint().equals(candidate.acceptedFingerprint()))
            throw new IllegalArgumentException("A/B snapshots must share scenario and accepted evidence");
        return new JSONObject().put("scenario", baseline.scenarioId())
                .put("baseline", baseline.json())
                .put("candidate", candidate.json())
                .put("delta_max_missing_m",
                        candidate.missingMetres() - baseline.missingMetres())
                .put("delta_max_unsupported_m",
                        candidate.unsupportedMetres() - baseline.unsupportedMetres())
                .put("delta_display_sections",
                        candidate.displaySections() - baseline.displaySections());
    }
}
