package com.roadprints.capture;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
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
        assertNull(JourneyCorrectionUtils.retainedRoadFeature(journey, roadFeature));
        JSONObject other = new JSONObject().put("properties", new JSONObject().put("name", "Other Road"));
        assertSame(other, JourneyCorrectionUtils.retainedRoadFeature(journey, other));
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

        assertNull(JourneyCorrectionUtils.retainedRoadFeature(journey, roadFeature));
    }
    @Test
    public void clipsSpurAndRetainsCrossingParallelAndSameNameEvidence() throws Exception {
        JSONArray spur = line(-.1000, 51.5, -.0998, 51.5);
        JSONObject main = road("A226", line(-.1010, 51.5, -.0990, 51.5), 140);
        JSONObject parallel = road("A226", line(-.1010, 51.50005, -.0990, 51.50005), 140);
        JSONObject crossing = road("Albion Road", line(-.0999, 51.499, -.0999, 51.501), 220);
        JSONObject unrelated = road("A226", line(-.1200, 51.5, -.1190, 51.5), 70);
        JSONObject match = new JSONObject()
                .put("geojson", new JSONObject().put("features", new JSONArray().put(road("", spur, 14))))
                .put("road_geojson", new JSONObject().put("features",
                        new JSONArray().put(main).put(parallel).put(crossing).put(unrelated)));
        JSONObject journey = new JSONObject().put("processing_result", match)
                .put("journey_corrections", new JSONObject().put("removed_matched_segments", new JSONArray().put(0))
                        .put("removed_road_ids", new JSONArray().put("ref:A226").put("name:albion road")));

        JSONObject retained = JourneyCorrectionUtils.retainedRoadFeature(journey, main);
        assertEquals(126, retained.getJSONObject("properties").getDouble("distance_m"), .001);
        assertEquals("MultiLineString", retained.getJSONObject("geometry").getString("type"));
        assertEquals(2, retained.getJSONObject("geometry").getJSONArray("coordinates").length());
        assertSame(parallel, JourneyCorrectionUtils.retainedRoadFeature(journey, parallel));
        assertSame(crossing, JourneyCorrectionUtils.retainedRoadFeature(journey, crossing));
        assertSame(unrelated, JourneyCorrectionUtils.retainedRoadFeature(journey, unrelated));
        assertEquals(1, JourneyCorrectionUtils.removedRoadRecords(journey, List.of(spur), java.util.Set.of(0)).size());
        journey.put("journey_corrections", new JSONObject());
        assertSame(main, JourneyCorrectionUtils.retainedRoadFeature(journey, main));
        assertEquals(140, main.getJSONObject("properties").getDouble("distance_m"), 0);
    }

    private static JSONArray line(double x, double y, double endX, double endY) throws Exception {
        return new JSONArray().put(new JSONArray().put(x).put(y)).put(new JSONArray().put(endX).put(endY));
    }

    private static JSONObject road(String name, JSONArray coords, double distance) throws Exception {
        JSONObject properties = new JSONObject().put("name", name).put("distance_m", distance);
        if (name.startsWith("A2")) properties.put("road_ref", name);
        return new JSONObject().put("properties", properties)
                .put("geometry", new JSONObject().put("type", "LineString").put("coordinates", coords));
    }
}
