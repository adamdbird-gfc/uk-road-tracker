package com.roadprints.capture;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class JourneyCorrectionUtilsTest {
    @Test
    public void associatesRemovedMatchedEdgeWithRoadAndExcludesItsEvidence() throws Exception {
        JSONArray route = new JSONArray()
                .put(new JSONArray().put(-0.1000).put(51.5000))
                .put(new JSONArray().put(-0.0990).put(51.5000));
        JSONObject roadFeature = new JSONObject()
                .put("type", "Feature")
                .put("properties", new JSONObject().put("name", "School Lane"))
                .put("geometry", new JSONObject().put("type", "LineString")
                        .put("coordinates", new JSONArray()
                                .put(new JSONArray().put(-0.1000).put(51.5000))
                                .put(new JSONArray().put(-0.0990).put(51.5000))));
        JSONObject journey = new JSONObject()
                .put("processing_result", new JSONObject()
                        .put("geojson", new JSONObject().put("features",
                                new JSONArray().put(new JSONObject().put("geometry",
                                        new JSONObject().put("type", "LineString")
                                                .put("coordinates", route)))))
                        .put("road_geojson", new JSONObject().put("features",
                                new JSONArray().put(roadFeature))));

        List<JSONObject> removed = JourneyCorrectionUtils.removedRoadRecords(
                journey, Collections.singletonList(route), new HashSet<>(Collections.singleton(0)));
        assertEquals(1, removed.size());
        assertEquals("name:school lane", removed.get(0).optString("id"));
        assertEquals("School Lane", removed.get(0).optString("label"));

        journey.put("journey_corrections", new JSONObject()
                .put("removed_matched_segments", new JSONArray().put(0))
                .put("removed_road_ids", new JSONArray().put("name:school lane")));
        assertTrue(JourneyCorrectionUtils.excludesRoadFeature(journey, roadFeature));
        assertFalse(JourneyCorrectionUtils.excludesRoadFeature(journey,
                new JSONObject().put("properties", new JSONObject().put("name", "Other Road"))));
    }

    @Test
    public void derivesRoadExclusionsForPreviouslySavedEdgeOnlyCorrections() throws Exception {
        JSONArray route = new JSONArray()
                .put(new JSONArray().put(-0.1000).put(51.5000))
                .put(new JSONArray().put(-0.0990).put(51.5000));
        JSONObject roadFeature = new JSONObject()
                .put("properties", new JSONObject().put("road_ref", "A2").put("name", "A2"))
                .put("geometry", new JSONObject().put("type", "LineString")
                        .put("coordinates", route));
        JSONObject journey = new JSONObject()
                .put("processing_result", new JSONObject()
                        .put("geojson", new JSONObject().put("features",
                                new JSONArray().put(new JSONObject().put("geometry",
                                        new JSONObject().put("type", "LineString")
                                                .put("coordinates", route)))))
                        .put("road_geojson", new JSONObject().put("features",
                                new JSONArray().put(roadFeature))))
                .put("journey_corrections", new JSONObject()
                        .put("removed_matched_segments", new JSONArray().put(0)));

        assertTrue(JourneyCorrectionUtils.removedRoadIds(journey).contains("ref:A2"));
        assertTrue(JourneyCorrectionUtils.excludesRoadFeature(journey, roadFeature));
    }
}
