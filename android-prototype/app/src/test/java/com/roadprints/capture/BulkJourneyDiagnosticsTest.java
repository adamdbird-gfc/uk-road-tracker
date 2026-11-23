package com.roadprints.capture;

import android.content.Context;
import android.content.SharedPreferences;

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
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class BulkJourneyDiagnosticsTest {
    @Test public void fullArchivePreservesEvidenceAndDoesNotChangeTheStore() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        SharedPreferences prefs = context.getSharedPreferences("roadprints_journeys_v1", Context.MODE_PRIVATE);
        prefs.edit().putBoolean("journeys_migrated_to_archive", true).commit();
        int size = 1700;
        String coordinates = "[1.222222222222222222,2.444444444444444444],[1.3,2.5]";
        String pattern = "{\"journey_id\":\"j%04d\",\"mode\":\"%s\",\"started_at\":\"2025-01-01T00:00:00Z\","
                + "\"source\":{\"type\":\"synthetic\"},\"route_geometry\":{\"type\":\"LineString\",\"coordinates\":["
                + coordinates + "]},\"processing_result\":{\"geojson\":{\"features\":[]}},\"unknown\":null}";
        Map<File, byte[]> saved = new HashMap<>();
        for (int i = 0; i < size; i++) {
            String id = String.format(java.util.Locale.ROOT, "j%04d", i);
            File file = JourneyStore.archiveFile(context, id);
            String mode = new String[]{"walking","driving","cycling","train"}[i % 4];
            byte[] bytes = String.format(java.util.Locale.ROOT, pattern, i, mode).getBytes(StandardCharsets.UTF_8);
            Files.write(file.toPath(), bytes);
            saved.put(file, bytes);
        }
        long revision = JourneyStore.dataRevision(context);
        int[] last = {-1};
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        assertEquals(size, BulkJourneyDiagnostics.write(context, output, () -> false, (n,total) -> {
            assertEquals(size, total);
            assertEquals(last[0]+1, n);
            last[0] = n;
        }));

        Set<String> ids = new HashSet<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(output.toByteArray()))) {
            ZipEntry entry;
            for (int n = 1; n <= size; n++) {
                entry = zip.getNextEntry();
                assertEquals("journeys/" + n + ".json", entry.getName());
                String body = read(zip);
                assertTrue(body.contains(coordinates));
                JSONObject record = new JSONObject(body);
                JSONObject journey = record.getJSONObject("journey");
                assertTrue(ids.add(journey.getString("journey_id")));
                assertEquals("synthetic", journey.getJSONObject("source").getString("type"));
                assertTrue(journey.has("unknown") && journey.isNull("unknown"));
                assertTrue(contains(record.getJSONArray("stored"), "route_geometry.coordinates"));
                assertTrue(contains(record.getJSONArray("unavailable"), "capture_route_samples"));
                assertTrue(contains(record.getJSONArray("unavailable"), "processing_result.geojson.features"));
            }
            assertEquals("manifest.json", zip.getNextEntry().getName());
            JSONObject manifest = new JSONObject(read(zip));
            assertEquals(size, manifest.getInt("journey_count"));
            assertTrue(manifest.getBoolean("export_complete"));
            assertEquals(size, manifest.getJSONArray("journeys").length());
            assertNull(zip.getNextEntry());
        }
        assertEquals(size, last[0]);
        assertEquals(revision, JourneyStore.dataRevision(context));
        for (Map.Entry<File, byte[]> entry : saved.entrySet())
            assertArrayEquals(entry.getValue(), Files.readAllBytes(entry.getKey().toPath()));
    }

    @Test public void preMigrationRecordsAreReadAndCancellationLeavesNoCompleteManifest() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        SharedPreferences prefs = context.getSharedPreferences("roadprints_journeys_v1", Context.MODE_PRIVATE);
        prefs.edit().putString("journey:legacy","{\"journey_id\":\"legacy\"}").commit();
        AtomicBoolean cancel = new AtomicBoolean();
        ByteArrayOutputStream partial = new ByteArrayOutputStream();
        assertThrows(IOException.class, () -> BulkJourneyDiagnostics.write(context, partial, cancel::get,
                (done, total) -> cancel.set(done == 1)));
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(partial.toByteArray()))) {
            assertEquals("journeys/1.json", zip.getNextEntry().getName());
            assertEquals("legacy", new JSONObject(read(zip)).getJSONObject("journey").getString("journey_id"));
            assertNull(zip.getNextEntry());
        }
        assertFalse(prefs.getBoolean("journeys_migrated_to_archive", false));
        assertFalse(JourneyStore.archiveFile(context, "legacy").exists());
    }

    @Test public void corruptArchiveEntryFailsWithRecordPosition() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        File file = JourneyStore.archiveFile(context, "bad");
        Files.write(file.toPath(), "{".getBytes(StandardCharsets.UTF_8));
        IOException error = assertThrows(IOException.class, () -> BulkJourneyDiagnostics.write(context,
                new ByteArrayOutputStream(), () -> false, (done,total) -> {}));
        assertTrue(error.getMessage().contains("journey 1 of 1"));
    }

    private static boolean contains(JSONArray values, String target) {
        for (int i = 0; i < values.length(); i++) if (target.equals(values.optString(i))) return true;
        return false;
    }

    private static String read(ZipInputStream zip) throws IOException {
        return new String(zip.readAllBytes(), StandardCharsets.UTF_8);
    }
}
