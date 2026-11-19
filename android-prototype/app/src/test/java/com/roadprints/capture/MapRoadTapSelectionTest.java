package com.roadprints.capture;

import static org.junit.Assert.*;
import org.json.JSONObject;
import org.junit.Test;

public final class MapRoadTapSelectionTest {
    @Test public void savedInternationalRoadNameAndReferenceWinOverFallback() throws Exception {
        assertEquals("D940", MapRoadTapSelection.label(new JSONObject()
                .put("road_ref", "D940").put("name", "Route côtière"), "driving"));
        assertEquals("Rue de Rivoli", MapRoadTapSelection.label(new JSONObject()
                .put("name", "Rue de Rivoli"), "walking"));
        assertEquals("Carrer de Jaume I", MapRoadTapSelection.label(new JSONObject()
                .put("road_name", "Carrer de Jaume I"), "cycling"));
    }

    @Test public void unnamedAcceptedRoutesRemainTappableWithoutInventedRoadNames() {
        assertEquals("Public footpath", MapRoadTapSelection.label(null, "walking"));
        assertEquals("Public footpath", MapRoadTapSelection.label(new JSONObject(), "running"));
        assertEquals("Unnamed road", MapRoadTapSelection.label(null, "driving"));
        assertEquals("Unnamed road", MapRoadTapSelection.label(new JSONObject(), "cycling"));
    }

    @Test public void repeatedAcceptedRoadsShareTapButParallelOnesDoNot() {
        assertTrue(MapRoadTapSelection.sameLocalFeature("Rue de Rivoli",
                "RUE DE RIVOLI", 0.5, 7));
        assertFalse(MapRoadTapSelection.sameLocalFeature("Rue de Rivoli",
                "Rue de Rivoli", 0.5, 25));
        assertFalse(MapRoadTapSelection.sameLocalFeature("Rue de Rivoli",
                "Rue des Archives", 0.5, 1));
        assertTrue(MapRoadTapSelection.sameLocalFeature("Public footpath",
                "public footpath", 0.5, 7));
        assertFalse(MapRoadTapSelection.sameLocalFeature("Public footpath",
                "Public footpath", 0.5, 12));
    }

    @Test public void invalidDistancesNeverGrantAttribution() {
        assertFalse(MapRoadTapSelection.sameLocalFeature("Unnamed road",
                "Unnamed road", Double.NaN, 0));
        assertFalse(MapRoadTapSelection.sameLocalFeature("Unnamed road",
                "Unnamed road", 0, Double.POSITIVE_INFINITY));
    }
}
