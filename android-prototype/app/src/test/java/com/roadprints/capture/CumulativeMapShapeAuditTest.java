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

    @Test public void invalidOrMissingEvidenceIsNotEquivalent() throws Exception {
        JSONArray path = line("[[0.3747,51.44],[0.3753,51.44]]");
        assertEquals(Double.POSITIVE_INFINITY,
                CumulativeMapShapeAudit.symmetricDeviationMetres(path,
                        line("[[0.3747,51.44],[999,51.44]]")), 0);
        assertEquals(Double.POSITIVE_INFINITY,
                CumulativeMapShapeAudit.symmetricDeviationMetres(null, path), 0);
    }
}
