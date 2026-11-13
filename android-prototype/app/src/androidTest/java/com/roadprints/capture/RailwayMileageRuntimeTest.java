package com.roadprints.capture;

import static org.junit.Assert.*;

import android.content.Context;
import android.os.SystemClock;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.DataInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.zip.GZIPInputStream;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class RailwayMileageRuntimeTest {
    private static final String ASSET = "railway/network-first-r1.bin.gzip";
    private static final String ID = "railway-runtime";
    private static final long START = 1_790_000_000_000L;
    private static final long END = START + 3_600_000;
    private record Edge(double x, double y, double dx, double dy, double metres, boolean tunnel) {}

    @Test public void upgradeDistanceAndReopenArchiveOnAndroid() throws Exception {
        var instrumentation = InstrumentationRegistry.getInstrumentation();
        Context app = instrumentation.getTargetContext();
        assertTrue(app.getPackageName().endsWith(".milepostacceptance"));
        File source = JourneyStore.archiveFile(app, ID);
        var preferences = app.getSharedPreferences("roadprints_journeys_v1", 0);
        if ("verify".equals(InstrumentationRegistry.getArguments().getString("phase"))) {
            assertEquals(39_228, JourneyStore.readSummary(source).getDouble("distance_meters"), 150);
            long revision = preferences.getLong("journey_archive_revision", 0);
            assertEquals(revision + 1, JourneyStore.dataRevision(app));
            assertFalse(preferences.contains("railway_distance_pending_id"));
            assertTrue(JourneyStore.restoreRailwayDistance(app, ID));
            assertEquals(43_900, JourneyStore.readSummary(source).getDouble("distance_meters"), 0);
            return;
        }

        var journey = new JSONObject().put("journey_id", ID).put("mode", "train")
                .put("title", "Railway archive replay").put("distance_meters", 43_900)
                .put("started_at", Instant.ofEpochMilli(START).toString())
                .put("ended_at", Instant.ofEpochMilli(END).toString())
                .put("raw_capture_samples", sample(publicRoute(app)));
        Files.write(source.toPath(), journey.toString().getBytes(StandardCharsets.UTF_8));
        File output = new File(app.getFilesDir(), "railway-runtime-assessments");
        assertTrue(output.isDirectory() || output.mkdir());

        long started = SystemClock.elapsedRealtime();
        long cpu = SystemClock.currentThreadTimeMillis();
        try (var graph = RegionalRailwayCatalogue.open(app, app.getFilesDir(), () -> false)) {
            var network = NetworkFirstRailway.open(app.getAssets().open(ASSET), () -> false);
            var result = RailwayTrialWorker.assess(source, output, ID,
                    () -> new RailwayMatcher(graph, () -> false), () -> false, (fixes, from, to) -> network);
            assertEquals("Sidcup", result.getString("network_branch"));
            assertEquals(39_228, result.getDouble("inferred_m"), 150);
            assertThrows(CancellationException.class,
                    () -> JourneyStore.applyRailwayDistance(app, ID, result, () -> true));
            assertEquals(43_900, JourneyStore.readSummary(source).getDouble("distance_meters"), 0);
            assertTrue(JourneyStore.applyRailwayDistance(app, ID, result, () -> false));
            assertEquals(result.getDouble("inferred_m"), JourneyStore.readSummary(source).getDouble("distance_meters"), 0);
            assertEquals(result.getDouble("inferred_m"), JourneyStore.mapEvidence(app, source, true)
                    .getDouble("distance_meters"), 0);
        }
        long elapsed = SystemClock.elapsedRealtime() - started;
        long cpuElapsed = SystemClock.currentThreadTimeMillis() - cpu;
        android.util.Log.i("RoadprintsRailwayRuntime", "cold_assess_and_write_ms=" + elapsed + " cpu_ms=" + cpuElapsed);
        // Catch runaway cold processing on the CI emulator. This budget is not a Pixel 10 benchmark.
        assertTrue("Cold railway archive update exceeded 5 s CPU", cpuElapsed < 5_000);
        // Model a process ending after atomic archive replacement and before cache invalidation.
        assertTrue(preferences.edit().putString("railway_distance_pending_id", ID).commit());
    }

    private static List<Edge> publicRoute(Context app) throws Exception {
        try (var input = new DataInputStream(new GZIPInputStream(app.getAssets().open(ASSET)))) {
            assertEquals(0x52504e31, input.readInt());
            int edges = input.readUnsignedShort();
            int routes = input.readUnsignedShort();
            input.readFully(new byte[24 * input.readUnsignedByte()]);
            var catalogue = new ArrayList<Edge>();
            for (int i = 0; i < edges; i++) {
                input.readInt();
                boolean tunnel = (input.readUnsignedByte() & 2) != 0;
                catalogue.add(new Edge(700000 + input.readInt() / 100.0, 5700000 + input.readInt() / 100.0,
                        input.readInt() / 100.0, input.readInt() / 100.0, input.readInt() / 1000.0, tunnel));
            }
            for (int i = 0; i < routes; i++) {
                int branch = input.readUnsignedByte();
                boolean qualified = input.readBoolean();
                input.readUnsignedShort();
                int size = input.readUnsignedShort();
                var route = new ArrayList<Edge>();
                for (int j = 0; j < size; j++) route.add(catalogue.get(input.readUnsignedShort()));
                if (branch == 0 && qualified) return route;
            }
        }
        throw new AssertionError("Qualified public route missing");
    }

    private static JSONArray sample(List<Edge> route) {
        double length = route.stream().mapToDouble(Edge::metres).sum();
        double offset = 0;
        int index = 0;
        var points = new JSONArray();
        for (int i = 0; i <= 360; i++) {
            double distance = length * i / 360;
            while (index + 1 < route.size() && offset + route.get(index).metres < distance)
                offset += route.get(index++).metres;
            Edge edge = route.get(index);
            if (edge.tunnel || (i > 70 && i < 81)) continue;
            double part = (distance - offset) / edge.metres;
            double x = edge.x + part * edge.dx, y = edge.y + part * edge.dy;
            double lon = 0, lat = 51.5;
            long time = START + i * 10_000;
            for (int iteration = 0; iteration < 8; iteration++) {
                var current = RailwayProjection.fix(lon, lat, 8, time);
                lon += (x - current.x()) / 69_000;
                lat += (y - current.y()) / 111_000;
            }
            points.put(new JSONArray(List.of(lon, lat, 8, time, -1, "gps")));
        }
        return points;
    }
}
