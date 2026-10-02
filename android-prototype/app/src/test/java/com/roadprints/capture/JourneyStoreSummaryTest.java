package com.roadprints.capture;

import android.content.Context;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class JourneyStoreSummaryTest {
    @Test public void summaryReadsMetadataAndCountsLargeGeometryWithoutLoadingItAsJson() throws Exception {
        Context app = RuntimeEnvironment.getApplication();
        File archive = new File(app.getFilesDir(), "journey_streaming-summary.json");
        StringBuilder journey = new StringBuilder(1_500_000);
        journey.append("{\"journey_id\":\"streaming-summary\",\"mode\":\"driving\",")
                .append("\"started_at\":\"2026-10-02T10:00:00Z\",\"distance_meters\":1234,")
                .append("\"source\":{\"type\":\"timeline_import\"},")
                .append("\"capture_quality\":{\"gps_points\":2,\"source_route_points\":3},")
                .append("\"route_geometry\":{\"type\":\"LineString\",\"coordinates\":[");
        appendCoordinates(journey, 40_000);
        journey.append("]},\"processing_status\":\"complete\",\"processing_result\":{\"geojson\":{")
                .append("\"features\":[{\"geometry\":{\"type\":\"LineString\",\"coordinates\":[");
        appendCoordinates(journey, 10_000);
        journey.append("]}}]}}}");
        try (FileOutputStream output = new FileOutputStream(archive)) {
            output.write(journey.toString().getBytes(StandardCharsets.UTF_8));
        }

        List<JSONObject> summaries = JourneyStore.allSummaries(app);

        assertEquals(1, summaries.size());
        JSONObject summary = summaries.get(0);
        assertEquals("streaming-summary", summary.getString("journey_id"));
        assertEquals("timeline_import", summary.getJSONObject("source").getString("type"));
        assertEquals(40_000, summary.getInt("_route_point_count"));
        assertTrue(summary.getBoolean("_has_stored_match"));
        assertEquals(3, summary.getJSONObject("capture_quality").getInt("source_route_points"));
    }

    private static void appendCoordinates(StringBuilder json, int count) {
        for (int index = 0; index < count; index++) {
            if (index > 0) json.append(',');
            json.append('[').append(-0.1 + index * 0.000001).append(",51.4]");
        }
    }
}
