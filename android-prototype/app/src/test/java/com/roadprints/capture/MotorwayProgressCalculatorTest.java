package com.roadprints.capture;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class MotorwayProgressCalculatorTest {
    @Test public void repeatedJourneysAddMileageButCountCanonicalCoverageOnce() throws Exception {
        Context app = RuntimeEnvironment.getApplication();
        JSONArray anchors = new JSONArray().put(new JSONArray().put(-0.2305338).put(51.6869996))
                .put(new JSONArray().put(-0.2313622).put(51.6874356))
                .put(new JSONArray().put(-0.2450000).put(51.7000000))
                .put(new JSONArray().put(-0.2460000).put(51.7010000));
        JSONObject cache = new JSONObject().put("roads", new JSONObject().put("M25",
                new JSONObject().put("total_km", 234.8).put("anchors", anchors)));
        JSONArray route = new JSONArray().put(anchors.getJSONArray(0))
                .put(anchors.getJSONArray(1));
        JSONObject geometry = new JSONObject().put("type", "LineString")
                .put("coordinates", route);
        JSONObject feature = new JSONObject().put("type", "Feature")
                .put("properties", new JSONObject().put("road_ref", "M25")
                        .put("distance_m", 1000))
                .put("geometry", geometry);
        JSONObject journey = new JSONObject().put("journey_id", "journey-1")
                .put("mode", "driving").put("processing_status", "complete")
                .put("processing_result", new JSONObject().put("motorway_geojson",
                        new JSONObject().put("type", "FeatureCollection")
                                .put("features", new JSONArray().put(feature))));

        MotorwayProgressCalculator calculator = new MotorwayProgressCalculator(app, cache);
        calculator.addJourney(journey);
        calculator.addJourney(new JSONObject(journey.toString()).put("journey_id", "journey-2"));
        MotorwayProgressCalculator.Summary summary = calculator.finish();

        assertEquals(1, summary.roads.size());
        MotorwayProgressCalculator.Road motorway = summary.roads.get(0);
        assertEquals("M25", motorway.ref);
        assertEquals(2000, motorway.matchedMetres, 0.001);
        assertEquals(2, motorway.journeyIds.size());
        assertTrue(motorway.referenceAvailable);
        assertTrue(motorway.percent() > 0);
        assertTrue(motorway.percent() <= 100);
        assertFalse(motorway.coveredMapSections.isEmpty());
        assertFalse(motorway.incompleteMapSections.isEmpty());
        assertTrue(summary.gbPercent() > 0);
    }

    @Test public void bundledSharedRefIsSeparatedIntoGreatBritainAndNorthernIreland() throws Exception {
        Context app = RuntimeEnvironment.getApplication();
        JSONArray anchors = new JSONArray()
                .put(new JSONArray().put(-6.0100).put(54.6000))
                .put(new JSONArray().put(-6.0110).put(54.6010));
        JSONObject cache = new JSONObject().put("roads", new JSONObject().put("M1",
                new JSONObject().put("anchors", anchors)));
        JSONArray route = new JSONArray()
                .put(new JSONArray().put(-6.0100).put(54.6000))
                .put(new JSONArray().put(-6.0110).put(54.6010));
        JSONObject feature = new JSONObject().put("type", "Feature")
                .put("properties", new JSONObject().put("road_ref", "M1")
                        .put("distance_m", 1500))
                .put("geometry", new JSONObject().put("type", "LineString")
                        .put("coordinates", route));
        JSONObject journey = new JSONObject().put("journey_id", "ni-journey")
                .put("mode", "driving").put("processing_status", "complete")
                .put("processing_result", new JSONObject().put("motorway_geojson",
                        new JSONObject().put("type", "FeatureCollection")
                                .put("features", new JSONArray().put(feature))));

        MotorwayProgressCalculator calculator = new MotorwayProgressCalculator(app, cache);
        calculator.addJourney(journey);
        MotorwayProgressCalculator.Summary summary = calculator.finish();

        assertEquals(1, summary.roads.size());
        assertEquals("NI", summary.roads.get(0).region);
        assertTrue(summary.roads.get(0).referenceAvailable);
        assertFalse(summary.missingReferences);
    }

    @Test public void bundledReferencesLoadForAllCurrentlySupportedMotorwayLabels() throws Exception {
        Context app = RuntimeEnvironment.getApplication();
        JSONObject root;
        try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(
                app.getAssets().open("canonical-motorways-v1.json"),
                java.nio.charset.StandardCharsets.UTF_8))) {
            StringBuilder text = new StringBuilder(); String line;
            while ((line = reader.readLine()) != null) text.append(line);
            root = new JSONObject(text.toString());
        }
        JSONObject refs = root.getJSONObject("roads");
        JSONArray features = new JSONArray();
        java.util.Iterator<String> names = refs.keys();
        while (names.hasNext()) {
            String ref = names.next();
            JSONArray anchors = refs.getJSONObject(ref).getJSONArray("anchors");
            if (anchors.length() < 2) continue;
            JSONArray route = new JSONArray().put(anchors.getJSONArray(0)).put(anchors.getJSONArray(1));
            features.put(new JSONObject().put("type", "Feature")
                    .put("properties", new JSONObject().put("road_ref", ref).put("distance_m", 100))
                    .put("geometry", new JSONObject().put("type", "LineString").put("coordinates", route)));
        }
        JSONObject journey = new JSONObject().put("journey_id", "all-motorways")
                .put("mode", "driving").put("processing_status", "complete")
                .put("processing_result", new JSONObject().put("motorway_geojson",
                        new JSONObject().put("type", "FeatureCollection").put("features", features)));
        MotorwayProgressCalculator calculator = new MotorwayProgressCalculator(app);
        calculator.addJourney(journey);
        MotorwayProgressCalculator.Summary summary = calculator.finish();
        assertTrue("Unexpected missing refs: " + summary.missingReferenceRoads,
                summary.missingReferenceRoads.isEmpty());
    }

}
