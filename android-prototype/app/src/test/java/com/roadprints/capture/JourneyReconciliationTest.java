package com.roadprints.capture;

import android.content.Context;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.File;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class JourneyReconciliationTest {
    private Context app;

    @Before public void setup() {
        app = RuntimeEnvironment.getApplication();
        JourneyStore.deleteAll(app);
    }

    @After public void cleanup() { JourneyStore.deleteAll(app); }

    private byte[] write(JSONObject value) throws Exception {
        byte[] bytes = value.toString().getBytes(StandardCharsets.UTF_8);
        Files.write(JourneyStore.archiveFile(app, value.getString("journey_id")).toPath(), bytes);
        return bytes;
    }

    @Test public void restartResumesAndSameVersionReusesReceiptsWithoutChangingHistory() throws Exception {
        byte[] original = write(new JSONObject().put("journey_id", "first").put("revision", 7)
                .put("title", "User title").put("mode", "driving").put("distance_meters", 1234)
                .put("transport_confirmation", "confirmed")
                .put("journey_corrections", new JSONObject().put("removed_road_ids", new JSONArray().put("A2")))
                .put("raw_capture_samples", new JSONArray().put(new JSONArray().put(0.4).put(51.4).put(4).put(100))
                        .put(new JSONArray().put(0.41).put(51.41).put(4).put(200))));
        write(new JSONObject().put("journey_id", "second").put("mode", "walking"));
        long revision = JourneyStore.dataRevision(app);
        long collection = JourneyStore.ncnDataRevision(app);

        try (JourneyReconciliation run = new JourneyReconciliation(app)) {
            run.enqueue(JourneyReconciliation.BASELINE, List.of("first", "second"));
            assertEquals(0, run.runBatch(JourneyReconciliation.BASELINE, () -> true));
        }
        try (JourneyReconciliation run = new JourneyReconciliation(app)) {
            assertEquals(2, run.runBatch(JourneyReconciliation.BASELINE, () -> false));
            JSONObject receipt = run.receipt(JourneyReconciliation.BASELINE, "first");
            assertEquals("7", receipt.getString("source_revision"));
            assertEquals("UNCHANGED", receipt.getString("outcome"));
            assertEquals(NcnJson.hash(original), receipt.getString("source_sha"));
            assertEquals("User title", new JSONObject(receipt.getString("decisions")).getString("title"));
            assertEquals("EVIDENCE_UNAVAILABLE",
                    run.receipt(JourneyReconciliation.BASELINE, "second").getString("outcome"));
            run.enqueue(JourneyReconciliation.BASELINE, List.of("first"));
            assertEquals(1, run.runBatch(JourneyReconciliation.BASELINE, () -> false));
            assertEquals(receipt.toString(), run.receipt(JourneyReconciliation.BASELINE, "first").toString());
        }
        assertArrayEquals(original, Files.readAllBytes(JourneyStore.archiveFile(app, "first").toPath()));
        assertEquals(revision, JourneyStore.dataRevision(app));
        assertEquals(collection, JourneyStore.ncnDataRevision(app));
        try (SQLiteDatabase db = app.openOrCreateDatabase("journey-interpretation-v1.db", 0, null)) {
            assertEquals(2L, DatabaseUtils.queryNumEntries(db, "receipts"));
        }
        JourneyStore.delete(app, "first");
        File[] copies = new File(app.getFilesDir(), "journey-interpretation-v1").listFiles();
        assertEquals(1, copies.length);
        assertFalse(Arrays.equals(original, Files.readAllBytes(copies[0].toPath())));
    }

    @Test public void ruleCancellationAndArchiveChangeDoNotPublishStaleReceipt() throws Exception {
        write(new JSONObject().put("journey_id", "edited").put("title", "before"));
        AtomicInteger calls = new AtomicInteger();
        JourneyReconciliation.Rule changed = new JourneyReconciliation.Rule() {
            public String version() { return "test-2"; }
            public JourneyReconciliation.Comparison compare(JSONObject source, JSONObject decisions) throws Exception {
                if (calls.getAndIncrement() == 0) {
                    write(new JSONObject().put("journey_id", "edited").put("title", "after")
                            .put("transport_confirmation", "confirmed"));
                }
                source.put("title", "proposed");
                return new JourneyReconciliation.Comparison(JourneyReconciliation.Outcome.SAFE_CANDIDATE,
                        new JSONObject().put("mode", "cycling"));
            }
        };
        try (JourneyReconciliation run = new JourneyReconciliation(app)) {
            run.enqueue(changed, List.of("edited"));
            assertEquals(0, run.runBatch(changed, () -> calls.get() == 1));
            assertNull(run.receipt(changed, "edited"));
        }
        try (JourneyReconciliation run = new JourneyReconciliation(app)) {
            assertEquals(1, run.runBatch(changed, () -> false));
            JSONObject receipt = run.receipt(changed, "edited");
            assertEquals("REVIEW_REQUIRED", receipt.getString("outcome"));
            assertEquals("after", new JSONObject(receipt.getString("decisions")).getString("title"));
        }
        assertEquals("after", new JSONObject(new String(Files.readAllBytes(
                JourneyStore.archiveFile(app, "edited").toPath()), StandardCharsets.UTF_8)).getString("title"));
    }

    @Test public void explicitlyRequestedWorkYieldsToCaptureAndRecordsOversizedEvidence() throws Exception {
        File file = JourneyStore.archiveFile(app, "large");
        try (java.io.RandomAccessFile output = new java.io.RandomAccessFile(file, "rw")) {
            output.setLength(JourneyReconciliation.MAX_BASELINE_BYTES + 1);
        }
        try (JourneyReconciliation run = new JourneyReconciliation(app)) {
            run.enqueue(JourneyReconciliation.BASELINE, List.of("large"));
            app.getSharedPreferences("roadprints_capture_state", 0).edit().putBoolean("active", true).commit();
            try {
                assertEquals(0, run.runBatch(JourneyReconciliation.BASELINE, () -> false));
            } finally {
                app.getSharedPreferences("roadprints_capture_state", 0).edit().remove("active").commit();
            }
            assertEquals(1, run.runBatch(JourneyReconciliation.BASELINE, () -> false));
            assertEquals("TOO_LARGE", run.receipt(JourneyReconciliation.BASELINE, "large").getString("outcome"));
        }
    }
}
