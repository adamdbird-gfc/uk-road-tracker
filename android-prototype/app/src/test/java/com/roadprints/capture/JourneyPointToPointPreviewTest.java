package com.roadprints.capture;

import org.json.JSONArray;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class JourneyPointToPointPreviewTest {
    @Test
    public void trainAndPlanePreviewsUseOnlyRecordedEndpoints() throws Exception {
        JSONArray coordinates = new JSONArray()
                .put(new JSONArray().put(-0.1).put(51.5))
                .put(new JSONArray().put(0.2).put(51.7))
                .put(new JSONArray().put(1.2).put(52.0));

        for (String mode : new String[]{"train", "plane"}) {
            assertTrue(JourneyListActivity.isPointToPointMode(mode));
            JSONArray preview = JourneyListActivity.endpointCoordinates(coordinates);
            assertEquals(2, preview.length());
            assertEquals(-0.1, preview.getJSONArray(0).getDouble(0), 0.0);
            assertEquals(51.5, preview.getJSONArray(0).getDouble(1), 0.0);
            assertEquals(1.2, preview.getJSONArray(1).getDouble(0), 0.0);
            assertEquals(52.0, preview.getJSONArray(1).getDouble(1), 0.0);
        }
        assertFalse(JourneyListActivity.isPointToPointMode("driving"));
    }
}
