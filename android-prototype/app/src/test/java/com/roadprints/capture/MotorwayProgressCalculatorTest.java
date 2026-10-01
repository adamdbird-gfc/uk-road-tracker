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
@Config(sdk = 28, assetDir = "src/main/assets")
public class MotorwayProgressCalculatorTest {
    @Test public void repeatedJourneysAddMileageButCountCanonicalCoverageOnce() throws Exception {
        Context app = RuntimeEnvironment.getApplication();
        JSONObject cache = new JSONObject(readAsset(app));
        JSONArray anchors = cache.getJSONObject("roads").getJSONObject("M25")
                .getJSONArray("anchors");
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

        MotorwayProgressCalculator calculator = new MotorwayProgressCalculator(app);
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

    private String readAsset(Context context) throws Exception {
        try (java.util.zip.GZIPInputStream input = new java.util.zip.GZIPInputStream(
                     context.getAssets().open("canonical-motorways-v1.json.gz"));
             java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) output.write(buffer, 0, read);
            return output.toString("UTF-8");
        }
    }
}
