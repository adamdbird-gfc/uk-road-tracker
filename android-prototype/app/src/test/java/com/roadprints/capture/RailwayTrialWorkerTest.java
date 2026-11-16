package com.roadprints.capture;

import static org.junit.Assert.*;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35)
public class RailwayTrialWorkerTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();

    @Test public void interruptionAndRestartLeaveArchiveAndReceiptConsistent() throws Exception {
        File source = temp.newFile("journey_test.json");
        File output = temp.newFolder();
        String original = """
                {"journey_id":"test","mode":"train","title":"Original","distance_meters":42,
                 "started_at":"2026-11-12T09:00:00Z","ended_at":"2026-11-12T09:00:15Z",
                 "raw_capture_samples":[[0.3,51.4,8,1794474000000,15],[0.301,51.4,8,1794474005000,15],
                 [0.302,51.4,8,1794474010000,15],[0.303,51.4,8,1794474015000,15]]}
                """;
        Files.write(source.toPath(), original.getBytes(StandardCharsets.UTF_8));
        var matcher = new RailwayMatcher(RailwayStage4Test.graph(false, false), () -> false);
        assertThrows(CancellationException.class,
                () -> RailwayTrialWorker.assess(source, output, "test", () -> matcher, () -> true));
        assertEquals(0, output.list().length);

        var interrupted = new RailwayMatcher(RailwayStage4Test.graph(false, false), () -> true);
        assertThrows(CancellationException.class,
                () -> RailwayTrialWorker.assess(source, output, "test", () -> interrupted, () -> false));
        assertEquals(0, output.list().length);

        AtomicInteger opens = new AtomicInteger();
        RailwayTrialWorker.MatcherProvider provider = () -> {
            opens.incrementAndGet();
            return matcher;
        };
        JSONObject result = RailwayTrialWorker.assess(source, output, "test", provider, () -> false);
        assertEquals(42, result.getDouble("proposed_m"), 0);
        assertEquals(1, output.list().length);
        assertEquals(original, new String(Files.readAllBytes(source.toPath()), StandardCharsets.UTF_8));
        for (int i = 0; i < 100; i++) {
            assertEquals(result.toString(), RailwayTrialWorker.assess(source, output, "test", provider, () -> false).toString());
        }
        Files.write(source.toPath(), original.replace("Original", "Edited").getBytes(StandardCharsets.UTF_8));
        assertEquals("saved_fallback", RailwayTrialWorker.assess(source, output, "test", provider, () -> false)
                .getString("distance_provenance"));
        assertEquals(1, opens.get());

        Files.write(source.toPath(), original.replace("train", "driving").getBytes(StandardCharsets.UTF_8));
        assertNull(RailwayTrialWorker.assess(source, output, "test", provider, () -> false));
        assertEquals(1, opens.get());
    }

    @Test public void savedTrainAppearsInJourneyDetailsAfterCaptureEnds() throws Exception {
        var app = org.robolectric.RuntimeEnvironment.getApplication();
        org.robolectric.Shadows.shadowOf(app).grantPermissions(
                "com.roadprints.capture.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION");
        var capture = app.getSharedPreferences("roadprints_capture_state", 0);
        capture.edit().putBoolean("active", true).commit();
        long start = 1794474000000L;
        var samples = new org.json.JSONArray();
        try (var input = new java.io.BufferedReader(new java.io.InputStreamReader(
                getClass().getResourceAsStream("/railway/hdr-reference.csv"), StandardCharsets.UTF_8))) {
            for (String line; (line = input.readLine()) != null;) {
                String[] point = line.split(",");
                samples.put(new org.json.JSONArray().put(Double.parseDouble(point[0]))
                        .put(Double.parseDouble(point[1])).put(8).put(start + Long.parseLong(point[2]))
                        .put(-1).put("gps"));
            }
        }
        JSONObject journey = new JSONObject().put("journey_id", "railway-owner-test").put("mode", "train")
                .put("started_at", java.time.Instant.ofEpochMilli(start).toString())
                .put("ended_at", java.time.Instant.ofEpochMilli(start + 120_000).toString())
                .put("distance_meters", 1600).put("raw_capture_samples", samples);
        var controller = org.robolectric.Robolectric.buildActivity(JourneyListActivity.class).setup();
        var screen = controller.get();
        android.app.AlertDialog dialog = null;
        try {
            JourneyStore.save(app, journey);
            File saved = JourneyStore.archiveFile(app, "railway-owner-test");
            byte[] original = Files.readAllBytes(saved.toPath());
            var show = JourneyListActivity.class.getDeclaredMethod("showDetails", JSONObject.class);
            show.setAccessible(true);
            show.invoke(screen, journey);
            dialog = org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
            android.view.View root = dialog.getWindow().getDecorView();
            assertTrue(hasText(root, "Recorded distance"));
            assertTrue(hasText(root, "Waiting for recording to finish"));
            capture.edit().putBoolean("active", false).commit();
            RailwayTrialWorker.captureEnded(app);
            var looper = org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper());
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(20);
            while (!hasText(root, "Full match") && System.nanoTime() < deadline) {
                looper.idle();
                Thread.sleep(10);
            }
            assertTrue(hasText(root, "Full match"));
            assertTrue(hasText(root, "Proposed distance"));
            assertArrayEquals(original, Files.readAllBytes(saved.toPath()));
            assertEquals(1600, JourneyStore.get(app, "railway-owner-test").getDouble("distance_meters"), 0);
        } finally {
            if (dialog != null) dialog.dismiss();
            capture.edit().clear().commit();
            controller.pause().stop().destroy();
        }
    }

    private static boolean hasText(android.view.View view, String text) {
        if (view instanceof android.widget.TextView label && text.contentEquals(label.getText())) return true;
        if (view instanceof android.view.ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) {
                if (hasText(group.getChildAt(i), text)) return true;
            }
        }
        return false;
    }
}
