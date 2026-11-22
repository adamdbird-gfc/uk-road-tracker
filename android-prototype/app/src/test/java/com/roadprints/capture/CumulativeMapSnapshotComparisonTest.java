package com.roadprints.capture;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.*;

/** Deterministic before/after evidence for general cumulative Map geometry. */
public final class CumulativeMapSnapshotComparisonTest {
    private static JSONArray line(String value) throws Exception { return new JSONArray(value); }

    @Test public void productionCanonicalChordComparedWithShapePreservingCandidate()
            throws Exception {
        JSONArray accepted = line("[[0.374,51.44],[0.375,51.4402],[0.376,51.44]]");
        LocalRoadCoverage.Section baselineSection = new LocalRoadCoverage.Section(
                "synthetic-reference", "Curved fixture", 100_000,
                new JSONArray("[0.374,51.44]"), new JSONArray("[0.376,51.44]"),
                Map.of("journey-a", new int[]{0,100_000},
                        "journey-b", new int[]{0,100_000}));
        List<JSONArray> oldOutput = List.of(baselineSection.lines.get(0).points());
        List<JSONArray> candidateOutput = List.of(
                line("[[0.374,51.44],[0.375,51.4402]]"),
                line("[[0.375,51.4402],[0.376,51.44]]"));
        var oldSnapshot = CumulativeMapSnapshotComparison.snapshot(
                "generic-curve", List.of(accepted), oldOutput);
        var newSnapshot = CumulativeMapSnapshotComparison.snapshot(
                "generic-curve", List.of(accepted), candidateOutput);
        assertTrue(oldSnapshot.missingMetres() > 15);
        assertTrue(oldSnapshot.unsupportedMetres() > 5);
        assertEquals(0, newSnapshot.missingMetres(), 0.0001);
        assertEquals(0, newSnapshot.unsupportedMetres(), 0.0001);
        JSONObject report = CumulativeMapSnapshotComparison.compare(oldSnapshot,newSnapshot);
        assertTrue(report.getDouble("delta_max_missing_m") < 0);
        assertTrue(report.getDouble("delta_max_unsupported_m") < 0);
        assertEquals(1, oldSnapshot.displaySections());
        assertEquals(2, newSnapshot.displaySections());
        System.out.println("cumulative_map_ab=" + report);
    }

    @Test public void fleetStreetStyleBranchesRejectSpuriousCrossStreetLine()
            throws Exception {
        // Synthetic street-grid geometry; NOT an export of the owner's London
        // journey evidence. The real London A/B scenario is documented in
        // docs/map-validation/LONDON_FLEET_STREET_TEMPLE.md.
        JSONArray fleet = line("[[0.001,51.51],[0.002,51.51003],[0.003,51.51007]]");
        JSONArray side = line("[[0.002,51.51003],[0.00208,51.5104]]");
        JSONArray other = line("[[0.003,51.51007],[0.00306,51.5097]]");
        List<JSONArray> accepted = List.of(fleet,side,other);
        List<JSONArray> clean = List.of(
                line("[[0.001,51.51],[0.002,51.51003]]"),
                line("[[0.002,51.51003],[0.003,51.51007]]"),
                side,other);
        List<JSONArray> withShortcut = List.of(clean.get(0),clean.get(1),side,other,
                line("[[0.00208,51.5104],[0.00306,51.5097]]"));
        var baseline = CumulativeMapSnapshotComparison.snapshot(
                "london-fleet-street-synthetic", accepted, withShortcut);
        var candidate = CumulativeMapSnapshotComparison.snapshot(
                "london-fleet-street-synthetic", accepted, clean);
        assertEquals(0, baseline.missingMetres(), 0.0001);
        assertTrue("Unaccepted cross-street shortcut must appear in reverse metric",
                baseline.unsupportedMetres() > 10);
        assertEquals(0, candidate.missingMetres(), 0.0001);
        assertEquals(0, candidate.unsupportedMetres(), 0.0001);
        assertEquals(-1, CumulativeMapSnapshotComparison.compare(baseline,candidate)
                .getInt("delta_display_sections"));
        System.out.println("cumulative_map_ab="
                + CumulativeMapSnapshotComparison.compare(baseline,candidate));
    }

    @Test public void fingerprintIgnoresDirectionAndOrderingButNotChangedGeometry()
            throws Exception {
        List<JSONArray> accepted = List.of(line("[[0.1,51.4],[0.2,51.4]]"));
        var one = CumulativeMapSnapshotComparison.snapshot("fingerprint", accepted,
                List.of(line("[[0.1,51.4],[0.15,51.4]]"),
                        line("[[0.15,51.4],[0.2,51.4]]")));
        var reversed = CumulativeMapSnapshotComparison.snapshot("fingerprint", accepted,
                List.of(line("[[0.2,51.4],[0.15,51.4]]"),
                        line("[[0.15,51.4],[0.1,51.4]]")));
        var shifted = CumulativeMapSnapshotComparison.snapshot("fingerprint", accepted,
                List.of(line("[[0.1,51.4],[0.15,51.40001]]"),
                        line("[[0.15,51.40001],[0.2,51.4]]")));
        assertEquals(one.geometryFingerprint(), reversed.geometryFingerprint());
        assertNotEquals(one.geometryFingerprint(), shifted.geometryFingerprint());
    }

    @Test public void refusesToCompareDifferentAcceptedGeometryEvenWithSameCount()
            throws Exception {
        var old = CumulativeMapSnapshotComparison.snapshot("same-location",
                List.of(line("[[0,50],[0.001,50]]")),
                List.of(line("[[0,50],[0.001,50]]")));
        var changedEvidence = CumulativeMapSnapshotComparison.snapshot("same-location",
                List.of(line("[[0,50],[0.001,50.001]]")),
                List.of(line("[[0,50],[0.001,50.001]]")));
        assertNotEquals(old.acceptedFingerprint(), changedEvidence.acceptedFingerprint());
        try {
            CumulativeMapSnapshotComparison.compare(old, changedEvidence);
            fail("A/B must reject changed accepted source data even with equal line counts");
        } catch (IllegalArgumentException expected) { /* pass */ }
    }

    @Test public void refusesToCompareDifferentScenariosOrEvidenceCounts()
            throws Exception {
        var a = CumulativeMapSnapshotComparison.snapshot("a",
                List.of(line("[[0,50],[0.001,50]]")),
                List.of(line("[[0,50],[0.001,50]]")));
        var b = CumulativeMapSnapshotComparison.snapshot("b",
                List.of(line("[[0,50],[0.001,50]]")),
                List.of(line("[[0,50],[0.001,50]]")));
        try {
            CumulativeMapSnapshotComparison.compare(a,b);
            fail("Cannot claim A/B comparability across different scenario IDs");
        } catch (IllegalArgumentException expected) { /* pass */ }
    }
}
