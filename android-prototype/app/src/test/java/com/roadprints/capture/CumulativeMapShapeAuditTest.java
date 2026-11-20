package com.roadprints.capture;

import org.json.JSONArray;
import org.junit.Test;
import static org.junit.Assert.*;

/** Owner roundabout: equivalent path shape, not merely matching endpoints. */
public final class CumulativeMapShapeAuditTest {
    private static JSONArray line(String data) throws Exception { return new JSONArray(data); }

    @Test public void roundaboutArcCannotBeReplacedByStraightChord() throws Exception {
        JSONArray northArc = line("[[0.3747,51.44],[0.374788,51.440141],"
                + "[0.375,51.4402],[0.375212,51.440141],[0.3753,51.44]]");
        JSONArray chord = line("[[0.3747,51.44],[0.3753,51.44]]");
        assertTrue("The roundabout bow must not disappear as an endpoint chord",
                CumulativeMapShapeAudit.symmetricDeviationMetres(northArc, chord) > 15);
    }

    @Test public void reverseAndResampledSamePhysicalArcRemainEquivalent() throws Exception {
        JSONArray forward = line("[[0.3747,51.44],[0.375,51.4402],[0.3753,51.44]]");
        JSONArray backward = line("[[0.3753,51.44],[0.375,51.4402],[0.3747,51.44]]");
        assertEquals(0, CumulativeMapShapeAudit.symmetricDeviationMetres(forward, backward), 0.0001);
        JSONArray resampled = line("[[0.3747,51.44],[0.37485,51.4401],"
                + "[0.375,51.4402],[0.37515,51.4401],[0.3753,51.44]]");
        assertEquals(0, CumulativeMapShapeAudit.symmetricDeviationMetres(forward, resampled), 0.0001);
    }

    @Test public void oppositeRoundaboutArcsMustRemainGeographicallyDistinct()
            throws Exception {
        JSONArray north = line("[[0.3747,51.44],[0.375,51.4402],[0.3753,51.44]]");
        JSONArray south = line("[[0.3747,51.44],[0.375,51.4398],[0.3753,51.44]]");
        assertTrue(CumulativeMapShapeAudit.symmetricDeviationMetres(north, south) > 20);
    }

    @Test public void inventedSpurMustNotLookEquivalent() throws Exception {
        JSONArray accepted = line("[[0.3747,51.44],[0.3753,51.44]]");
        JSONArray withInventedDetour = line("[[0.3747,51.44],[0.375,51.4402],"
                + "[0.3753,51.44]]");
        assertTrue(CumulativeMapShapeAudit.symmetricDeviationMetres(
                accepted, withInventedDetour) > 15);
    }

    @Test public void acceptedStreetCanBeRepresentedBySplitUnionWithoutStackingJourneys()
            throws Exception {
        JSONArray accepted = line("[[0.374,51.44],[0.375,51.44],[0.376,51.44]]");
        java.util.List<JSONArray> split = java.util.List.of(
                line("[[0.374,51.44],[0.3745,51.44]]"),
                line("[[0.3745,51.44],[0.375,51.44]]"),
                line("[[0.375,51.44],[0.3755,51.44]]"),
                line("[[0.3755,51.44],[0.376,51.44]]"));
        assertEquals(0, CumulativeMapShapeAudit.acceptedToCumulativeDeviationMetres(
                accepted, split), 0.0001);
        assertEquals(0, CumulativeMapShapeAudit.acceptedToCumulativeDeviationMetres(
                line("[[0.376,51.44],[0.375,51.44],[0.374,51.44]]"), split), 0.0001);
    }

    @Test public void genericCurvedPathMustNotBeReplacedByEndpointChord()
            throws Exception {
        JSONArray accepted = line("[[0.374,51.44],[0.3744,51.44012],"
                + "[0.375,51.4402],[0.3756,51.44012],[0.376,51.44]]");
        java.util.List<JSONArray> canonicalChords = java.util.List.of(
                line("[[0.374,51.44],[0.376,51.44]]"));
        assertTrue("A reconstructed chord cannot represent the accepted street bend",
                CumulativeMapShapeAudit.acceptedToCumulativeDeviationMetres(
                        accepted, canonicalChords) > 15);
    }

    @Test public void unrelatedParallelPathCannotSatisfyRepresentation()
            throws Exception {
        JSONArray accepted = line("[[0.374,51.44],[0.376,51.44]]");
        java.util.List<JSONArray> adjacent = java.util.List.of(
                line("[[0.374,51.44005],[0.376,51.44005]]"));
        assertTrue(CumulativeMapShapeAudit.acceptedToCumulativeDeviationMetres(
                accepted, adjacent) > 5);
    }

    @Test public void onlyPartiallyCoveredPathReportsMissingMiddle()
            throws Exception {
        JSONArray accepted = line("[[0.374,51.44],[0.376,51.44]]");
        java.util.List<JSONArray> separated = java.util.List.of(
                line("[[0.374,51.44],[0.3745,51.44]]"),
                line("[[0.3755,51.44],[0.376,51.44]]"));
        assertTrue("Covered endpoints must not conceal an unrepresented middle",
                CumulativeMapShapeAudit.acceptedToCumulativeDeviationMetres(
                        accepted, separated) > 20);
    }

    @Test public void unionOrderAndRepeatedSectionsDoNotChangeDeviation()
            throws Exception {
        JSONArray accepted = line("[[0.374,51.44],[0.375,51.44],[0.376,51.44]]");
        JSONArray a = line("[[0.374,51.44],[0.375,51.44]]");
        JSONArray b = line("[[0.375,51.44],[0.376,51.44]]");
        double initial = CumulativeMapShapeAudit.acceptedToCumulativeDeviationMetres(
                accepted, java.util.List.of(a,b));
        assertEquals(initial, CumulativeMapShapeAudit.acceptedToCumulativeDeviationMetres(
                accepted, java.util.List.of(b,a,b,a)), 0.0001);
    }

    @Test public void actualCanonicalSectionProjectionExposesCurvedJourneyMismatch()
            throws Exception {
        // This exercises the production LocalRoadCoverage.Section -> Line.points()
        // display conversion rather than a hand-crafted stand-in chord.
        JSONArray acceptedBend = line("[[0.374,51.44],[0.3744,51.44012],"
                + "[0.375,51.4402],[0.3756,51.44012],[0.376,51.44]]");
        LocalRoadCoverage.Section canonical = new LocalRoadCoverage.Section(
                "fixture-edge", "Example curved road", 100_000,
                new JSONArray("[0.374,51.44]"), new JSONArray("[0.376,51.44]"),
                java.util.Map.of("accepted-journey", new int[]{0,100_000}));
        assertEquals(1, canonical.lines.size());
        JSONArray actualDisplayLine = canonical.lines.get(0).points();
        assertEquals("Canonical interval display currently contains only the two endpoints",
                2, actualDisplayLine.length());
        assertTrue("Real canonical projection can distort the shape of accepted travel",
                CumulativeMapShapeAudit.acceptedToCumulativeDeviationMetres(
                        acceptedBend, java.util.List.of(actualDisplayLine)) > 15);
        // This test records a verified fidelity gap. It does not fix the
        // renderer or claim that every reference edge is misaligned.
    }

    @Test public void actualCanonicalSectionsCanRepresentAPathAsMultipleSegments()
            throws Exception {
        JSONArray acceptedBend = line("[[0.374,51.44],[0.375,51.4402],[0.376,51.44]]");
        LocalRoadCoverage.Section left = new LocalRoadCoverage.Section(
                "fixture-left", "Bend section 1", 50_000,
                new JSONArray("[0.374,51.44]"), new JSONArray("[0.375,51.4402]"),
                java.util.Map.of("accepted-journey", new int[]{0,50_000}));
        LocalRoadCoverage.Section right = new LocalRoadCoverage.Section(
                "fixture-right", "Bend section 2", 50_000,
                new JSONArray("[0.375,51.4402]"), new JSONArray("[0.376,51.44]"),
                java.util.Map.of("accepted-journey", new int[]{0,50_000}));
        assertEquals(0, CumulativeMapShapeAudit.acceptedToCumulativeDeviationMetres(
                acceptedBend, java.util.List.of(left.lines.get(0).points(),
                        right.lines.get(0).points())), 0.0001);
    }

    @Test public void canonicalIntervalUnionReusesOneDisplaySectionForRepeatedTravel()
            throws Exception {
        LocalRoadCoverage.Section section = new LocalRoadCoverage.Section(
                "shared-edge", "Shared physical street", 100_000,
                new JSONArray("[0.374,51.44]"), new JSONArray("[0.376,51.44]"),
                java.util.Map.of("journey-1", new int[]{0,80_000},
                        "journey-2", new int[]{20_000,100_000}));
        assertEquals("Overlapping journeys must produce one physical interval",
                1, section.lines.size());
        assertEquals(100_000, section.uniqueMillimetres);
        assertEquals(2, section.history.size());
    }

    @Test public void invalidOrMissingEvidenceIsNotEquivalent() throws Exception {
        JSONArray path = line("[[0.3747,51.44],[0.3753,51.44]]");
        assertEquals(Double.POSITIVE_INFINITY,
                CumulativeMapShapeAudit.symmetricDeviationMetres(path,
                        line("[[0.3747,51.44],[999,51.44]]")), 0);
        assertEquals(Double.POSITIVE_INFINITY,
                CumulativeMapShapeAudit.symmetricDeviationMetres(null, path), 0);
    }
}
