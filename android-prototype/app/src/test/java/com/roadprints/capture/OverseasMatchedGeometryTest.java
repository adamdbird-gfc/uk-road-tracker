package com.roadprints.capture;

import org.json.JSONArray;
import org.junit.Test;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

/** International presentation must not reintroduce UK parallel matched lines. */
public final class OverseasMatchedGeometryTest {
    private static JSONArray point(double lon, double lat) {
        return new JSONArray().put(lon).put(lat);
    }
    private static JSONArray line(double... coords) {
        JSONArray a = new JSONArray();
        for (int i = 0; i < coords.length; i += 2) a.put(point(coords[i], coords[i + 1]));
        return a;
    }
    @Test public void franceAndIbizaAreEligibleButUkJourneysAreNot() {
        assertEquals(1, OverseasMatchedGeometry.outsideReferenceFragments(
                Arrays.asList(line(2.45, 48.1, 2.5, 48.12)), 10, 50).size());
        assertEquals(1, OverseasMatchedGeometry.outsideReferenceFragments(
                Arrays.asList(line(1.42, 38.9, 1.45, 38.91)), 10, 50).size());
        assertTrue(OverseasMatchedGeometry.outsideReferenceFragments(
                Arrays.asList(line(0.37, 51.44, 0.38, 51.45)), 10, 50).isEmpty());
    }
    @Test public void noSpeculativeBorderConnectorOrFootpathDuplicate() {
        List<JSONArray> fragments = OverseasMatchedGeometry.outsideReferenceFragments(
                Arrays.asList(line(2.3, 48.2, 2.4, 48.4, 2.5, 49.5, 2.6, 48.3, 2.7, 48.1)), 10, 50);
        assertEquals(2, fragments.size());
        assertEquals(2, fragments.get(0).length());
        assertEquals(2, fragments.get(1).length());
    }
    @Test public void boundedAndInvalidPointsNeverDrawFalseConnections() {
        assertTrue(OverseasMatchedGeometry.outsideReferenceFragments(
                Arrays.asList(line(2.1, 48.2, 2.2, 48.3)), 0, 10).isEmpty());
        assertTrue(OverseasMatchedGeometry.outsideReferenceFragments(
                Arrays.asList(line(2.1, 48.2, 2.2, 48.3)), 10, 1).isEmpty());
        assertTrue(OverseasMatchedGeometry.outsideReferenceFragments(
                Arrays.asList(line(2.1, 48.2, 200, 48.3)), 10, 10).isEmpty());
        assertTrue(OverseasMatchedGeometry.outsideReferenceFragments(
                Arrays.asList(line(2.1, 48.2, 2.2, 48.3, 2.3, 48.4)), 10, 2).isEmpty());
    }
    @Test public void preservePreCorrectedSegmentsWithoutRehydratingRemovedParts() {
        // Inputs are post-removal fragments, not the original journey line.
        List<JSONArray> fragments = OverseasMatchedGeometry.outsideReferenceFragments(
                Arrays.asList(line(2.1, 48.2, 2.2, 48.3),
                              line(2.4, 48.4, 2.5, 48.5)), 10, 12);
        assertEquals(2, fragments.size());
        assertEquals(2.4, fragments.get(1).getJSONArray(0).getDouble(0), 0.0001);
    }
}
