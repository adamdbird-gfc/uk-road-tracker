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
                    String geometryFingerprint) {
        JSONObject json() throws Exception {
            return new JSONObject().put("scenario", scenarioId)
                    .put("accepted_sections", acceptedSections)
                    .put("display_sections", displaySections)
                    .put("display_points", displayPoints)
                    .put("max_accepted_to_display_m", missingMetres)
                    .put("max_display_to_accepted_m", unsupportedMetres)
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
                missing, unsupported, fingerprint(displayed));
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
                || baseline.acceptedSections() != candidate.acceptedSections())
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
