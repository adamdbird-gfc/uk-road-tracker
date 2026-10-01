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
                .put(new JSONArray().put(-0.2313622).put(51.6874356));
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
        assertTrue(summary.gbPercent() > 0);
    }

    @Test public void bundledSharedRefIsSeparatedIntoGreatBritainAndNorthernIreland() throws Exception {
        Context app = RuntimeEnvironment.getApplication();
        JSONArray anchors = new JSONArray()
                .put(new JSONArray().put(-0.2305338).put(51.6869996))
                .put(new JSONArray().put(-0.2313622).put(51.6874356))
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

}
