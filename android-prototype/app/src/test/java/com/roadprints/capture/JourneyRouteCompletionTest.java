package com.roadprints.capture;

import org.json.JSONArray;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class JourneyRouteCompletionTest {
    @Test public void addsUnmatchedGpsTailToCompleteTheEditorRoute() throws Exception {
        JSONArray trace = new JSONArray()
                .put(new JSONArray().put(0.0).put(51.5))
                .put(new JSONArray().put(0.001).put(51.5))
                .put(new JSONArray().put(0.002).put(51.5))
                .put(new JSONArray().put(0.003).put(51.5))
                .put(new JSONArray().put(0.004).put(51.5));
        JSONArray matched = new JSONArray()
                .put(new JSONArray().put(0.0).put(51.5))
                .put(new JSONArray().put(0.001).put(51.5))
                .put(new JSONArray().put(0.002).put(51.5));

        List<JSONArray> extensions = JourneyMapEditorActivity.unmatchedEndpointTrace(
                trace, Collections.singletonList(matched));

        assertEquals(1, extensions.size());
        JSONArray tail = extensions.get(0);
        assertEquals(3, tail.length());
        assertEquals(0.002, tail.getJSONArray(0).getDouble(0), 0.0);
        assertEquals(0.004, tail.getJSONArray(2).getDouble(0), 0.0);
    }
}
