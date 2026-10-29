package com.roadprints.capture;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class JourneyDebugExporterTest {
    @Test public void preservesRouteMatchErrorsAndOptionalPointEvidence() throws Exception {
        JSONObject saved = new JSONObject("{\"journey_id\":\"walk\",\"title\":\"Work → station\","
                + "\"distance_meters\":5002.726116964514,\"route_geometry\":{\"type\":\"LineString\","
                + "\"coordinates\":[[-0.1066922,51.5141578],[-0.1,51.5]]},"
                + "\"gps_samples\":[{\"accuracy_m\":19.387754,\"timestamp_utc\":\"2026-10-05T16:49:54Z\"}],"
                + "\"processing_result\":{\"matched_distance_m\":4913.5,\"matcher\":\"reference\"},"
                + "\"last_match_attempt\":{\"error\":\"HTTP 504\"},\"optional\":null,\"confirmed\":false}");
        JSONObject exported = export(saved.toString());
        assertEquals(saved.toString(), exported.getJSONObject("journey").toString());
        assertEquals("roadprints_journey_diagnostics", exported.getString("format"));
        assertEquals(BuildConfig.VERSION_NAME, exported.getString("app_version"));
        assertTrue(exported.getBoolean("export_complete"));
    }

    @Test public void exportsEveryPointWithoutAddingMissingMetadata() throws Exception {
        JSONArray points = new JSONArray();
        for (int i = 0; i < 10000; i++) points.put(new JSONArray().put(-0.1 + i * 0.000001).put(51.5));
        JSONObject saved = new JSONObject().put("journey_id", "large")
                .put("route_geometry", new JSONObject().put("coordinates", points));
        JSONObject exported = export(saved.toString()).getJSONObject("journey");
        JSONArray actual = exported.getJSONObject("route_geometry").getJSONArray("coordinates");
        assertEquals(10000, actual.length());
        assertEquals(points.getJSONArray(9999).getDouble(0), actual.getJSONArray(9999).getDouble(0), 0);
        assertFalse(exported.has("gps_samples"));
    }

    @Test public void openSnapshotSurvivesArchiveReplacementAndDoesNotChangeRevision() throws Exception {
        Context app = RuntimeEnvironment.getApplication();
        app.getSharedPreferences("roadprints_journeys_v1", Context.MODE_PRIVATE)
                .edit().putBoolean("journeys_migrated_to_archive", true).commit();
        File archive = new File(app.getFilesDir(), "journey_snapshot.json");
        writeFile(archive, "{\"journey_id\":\"snapshot\",\"revision\":1}");
        long revision = JourneyStore.dataRevision(app);
        try (InputStream snapshot = JourneyStore.openDiagnosticSnapshot(app, "snapshot")) {
            assertTrue(archive.delete());
            writeFile(archive, "{\"journey_id\":\"snapshot\",\"revision\":2}");
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            JourneyDebugExporter.writeSnapshot(snapshot, output);
            JSONObject exported = new JSONObject(output.toString("UTF-8"));
            assertEquals(1, exported.getJSONObject("journey").getInt("revision"));
        }
        assertEquals(revision, JourneyStore.dataRevision(app));
        assertEquals(2, JourneyStore.get(app, "snapshot").getInt("revision"));
    }

    @Test public void rejectsIncompleteArchiveWithoutSuccessMarker() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            JourneyDebugExporter.writeSnapshot(new ByteArrayInputStream(
                    "{\"journey_id\":\"broken\"".getBytes(StandardCharsets.UTF_8)), output);
            fail("Expected incomplete archive to fail");
        } catch (IOException expected) {
            assertFalse(output.toString("UTF-8").contains("export_complete"));
        }
    }

    private static JSONObject export(String saved) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        JourneyDebugExporter.writeSnapshot(new ByteArrayInputStream(saved.getBytes(StandardCharsets.UTF_8)), output);
        return new JSONObject(output.toString("UTF-8"));
    }

    private static void writeFile(File file, String text) throws IOException {
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(text.getBytes(StandardCharsets.UTF_8));
        }
    }
}
