package com.roadprints.capture;

import static org.junit.Assert.*;

import android.content.Context;
import android.os.SystemClock;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.InputStreamReader;
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
            JourneyStore.recoverRailwayWrite(app);
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

    @Test public void repeatedNavigationRemainsResponsiveDuringHistoricalChecks() throws Exception {
        var instrumentation = InstrumentationRegistry.getInstrumentation();
        Context app = instrumentation.getTargetContext();
        assertTrue(app.getPackageName().endsWith(".milepostacceptance"));
        var controls = app.getSharedPreferences("roadprints_capture_state", 0);
        controls.edit().putBoolean("active", true).commit();
        var trace = sample(publicRoute(app));
        var nationalTrace = new JSONArray();
        try (var input = new BufferedReader(new InputStreamReader(instrumentation.getContext()
                .getAssets().open("railway/belfast-bangor.csv"), StandardCharsets.UTF_8))) {
            for (String line; (line=input.readLine())!=null;) {
                String[] point = line.split(",");
                nationalTrace.put(new JSONArray().put(Double.parseDouble(point[0]))
                        .put(Double.parseDouble(point[1])).put(8).put(START+Long.parseLong(point[2]))
                        .put(0).put("gps"));
            }
        }
        for (int index = 0; index < 1652; index++) {
            boolean train = index < 24;
            boolean national = train && index%2!=0;
            String id = "navigation-" + index;
            var journey = new JSONObject().put("journey_id", id)
                    .put("mode", train ? "train" : "unknown").put("title", "Saved journey " + index)
                    .put("distance_meters", train ? 43_900 : 200)
                    .put("started_at", Instant.ofEpochMilli(START - (train ? 0 : index * 60000L)).toString())
                    .put("ended_at", Instant.ofEpochMilli(national ? START+1_050_000 : END).toString());
            if (train) {
                journey.put("raw_capture_samples", national ? nationalTrace : trace);
            } else if (index % 3 == 0) {
                var coordinates = new JSONArray();
                for (int point = 0; point < 24; point++)
                    coordinates.put(new JSONArray().put(0.36 + point * .0001).put(51.44));
                var line = new JSONObject().put("type", "LineString").put("coordinates", coordinates);
                var feature = new JSONObject().put("type", "Feature").put("geometry", line)
                        .put("properties", new JSONObject().put("road_ref", "B" + (2000 + index))
                                .put("highway", "secondary"));
                var features = new JSONObject().put("type", "FeatureCollection")
                        .put("features", new JSONArray().put(feature));
                journey.put("mode", "driving").put("processing_status", "complete")
                        .put("route_geometry", line).put("processing_result",
                                new JSONObject().put("geojson", features).put("road_geojson", features));
            }
            var source = JourneyStore.archiveFile(app, id);
            Files.write(source.toPath(), journey.toString().getBytes(StandardCharsets.UTF_8));
            if (train) assertTrue(source.setLastModified(System.currentTimeMillis() + 120000 - index * 2000));
        }
        try {
            try (var screen = androidx.test.core.app.ActivityScenario.launch(JourneyListActivity.class)) {
                controls.edit().putBoolean("active", false).commit();
                RailwayTrialWorker.captureEnded(app);
                awaitOnMain(() -> RailwayTrialWorker.isChecking(), 12_000);
                awaitOnMain(() -> {
                    String message = RailwayTrialWorker.activityMessage();
                    return message != null && message.equals("Checking past train journeys…");
                }, 3000);
                assertTrue("A first journey must commit", waitForDistance(app, "navigation-0", 39_228, 30_000));
            }
            for (int i = 0; i < 2; i++) {
                exerciseScreen(ProgressActivity.class);
                exerciseScreen(MapActivity.class);
                exerciseScreen(JourneyListActivity.class);
            }
            try (var screen = androidx.test.core.app.ActivityScenario.launch(JourneyListActivity.class)) {
                assertTrue("Railway history should resume and finish", waitForDistance(app, "navigation-23", 22_997, 90_000));
                awaitOnMain(() -> !RailwayTrialWorker.isChecking(), 120_000);
            }
            double expectedTrain = 0;
            for (int i = 0; i < 24; i++) {
                var saved = JourneyStore.readSummary(JourneyStore.archiveFile(app, "navigation-" + i));
                double distance = saved.getDouble("distance_meters");
                assertEquals(i%2==0 ? 39_228 : 22_997, distance, 150);
                expectedTrain += distance;
            }
            try (var screen = androidx.test.core.app.ActivityScenario.launch(ProgressActivity.class)) {
                double total = expectedTrain;
                awaitOnMain(() -> {
                    try {
                        var prefs = app.getSharedPreferences("roadprints_progress_snapshot", 0);
                        var summary = new JSONObject(prefs.getString("summary", "{}"));
                        if (summary.optLong("revision", -1) != JourneyStore.dataRevision(app)) return false;
                        assertEquals(total, summary.getDouble("trainMetres"), .01);
                        assertEquals(1628 * 200, summary.getDouble("drivingMetres")
                                + summary.getDouble("unknownMetres"), .01);
                        return true;
                    } catch (org.json.JSONException unavailable) {
                        return false;
                    }
                }, 45_000);
            }
            assertEquals(1652, JourneyStore.archiveFiles(app).stream()
                    .filter(file -> file.getName().startsWith("journey_navigation-")).count());
        } finally {
            controls.edit().remove("active").commit();
            for (File file : JourneyStore.archiveFiles(app))
                if (file.getName().startsWith("journey_navigation-")) Files.deleteIfExists(file.toPath());
        }
    }

    @Test public void reconstructsCardiffInterchangeInBothDirections() throws Exception {
        var instrumentation = InstrumentationRegistry.getInstrumentation();
        Context app = instrumentation.getTargetContext();
        assertTrue(app.getPackageName().endsWith(".milepostacceptance"));
        var trace = new ArrayList<RailwayMatcher.Fix>();
        try (var input = new BufferedReader(new InputStreamReader(instrumentation.getContext()
                .getAssets().open("railway/paddington-swansea.csv"), StandardCharsets.UTF_8))) {
            for (String line; (line = input.readLine()) != null;) {
                var fields = line.split(",");
                trace.add(new RailwayMatcher.Fix(Double.parseDouble(fields[0]), Double.parseDouble(fields[1]),
                        8, START + Long.parseLong(fields[2]) * 72 / 100));
            }
        }
        // The sampled trace represents roughly 2h 7m to Cardiff and 40m onward to Swansea.
        long interchange = START + 10_610_000L * 72 / 100;
        var platform = trace.stream().filter(fix -> fix.time() == interchange).findFirst().orElseThrow();
        try (var graph = NationalRailwayCatalogue.open(app, () -> false)) {
            var matcher = new NationalRailway(graph);
            for (boolean timed : new boolean[]{false, true}) {
                var fixes = new ArrayList<RailwayMatcher.Fix>();
                for (var fix : trace) {
                    long time = fix.time();
                    fixes.add(new RailwayMatcher.Fix(fix.x(), fix.y(), fix.accuracy(),
                            timed && time > interchange ? time + 600_000 : time));
                    if (timed && time == interchange) {
                        // Same-platform interchange with intermittent GPS while waiting.
                        for (int seconds : new int[]{30, 80, 150, 310, 550})
                            fixes.add(new RailwayMatcher.Fix(platform.x() + seconds % 7,
                                    platform.y() - seconds % 5, 8, time + seconds * 1000L));
                    }
                }
                long end = fixes.get(fixes.size() - 1).time();
                for (boolean reverse : new boolean[]{false, true}) {
                    long cpu = SystemClock.currentThreadTimeMillis();
                    var result = matcher.infer(fixes, START, end, () -> false);
                    long elapsed = SystemClock.currentThreadTimeMillis() - cpu;
                    String label = "Cardiff interchange timed=" + timed + " reverse=" + reverse;
                    android.util.Log.i("RoadprintsRailwayRuntime", label + " cpu_ms=" + elapsed
                            + " candidates_ms=" + result.candidateMs() + " evidence_ms=" + result.evidenceMs());
                    assertEquals(label + " " + result.reason(), "inferred", result.confidence());
                    assertEquals(label, reverse ? "Swansea → London Paddington" : "London Paddington → Swansea",
                            result.endpoints());
                    assertEquals(label, 307_009, result.metres(), 40);
                    assertTrue(label + " exceeded 10 s CPU: " + elapsed, elapsed < 10_000);
                    java.util.Collections.reverse(fixes);
                    fixes.replaceAll(fix -> new RailwayMatcher.Fix(fix.x(), fix.y(), fix.accuracy(),
                            START + end - fix.time()));
                }
            }
        }
    }

    private static <T extends android.app.Activity> void exerciseScreen(Class<T> type) throws Exception {
        long start = SystemClock.elapsedRealtime();
        try (var screen = androidx.test.core.app.ActivityScenario.launch(type)) {
            awaitOnMain(() -> true, 4000);
            awaitOnMain(() -> RailwayTrialWorker.isChecking(), 4000);
        }
        long elapsed = SystemClock.elapsedRealtime() - start;
        android.util.Log.i("RoadprintsRailwayRuntime", "navigation=" + type.getSimpleName() + " elapsed_ms=" + elapsed);
        assertTrue(type.getSimpleName() + " launch and return exceeded 5 seconds: " + elapsed, elapsed < 5000);
    }

    private static void awaitOnMain(java.util.function.BooleanSupplier condition, long timeout) throws Exception {
        long deadline = SystemClock.elapsedRealtime() + timeout;
        var handler = new android.os.Handler(android.os.Looper.getMainLooper());
        do {
            var check = new java.util.concurrent.FutureTask<Boolean>(condition::getAsBoolean);
            handler.post(check);
            if (check.get(4, java.util.concurrent.TimeUnit.SECONDS)) return;
            Thread.sleep(75);
        } while (SystemClock.elapsedRealtime() < deadline);
        fail("Expected foreground state did not arrive");
    }

    private static boolean waitForDistance(Context app, String id, double expected, long timeout) throws Exception {
        long deadline = SystemClock.elapsedRealtime() + timeout;
        do {
            var row = JourneyStore.readSummary(JourneyStore.archiveFile(app, id));
            if (row != null && Math.abs(row.optDouble("distance_meters") - expected) < 150) return true;
            Thread.sleep(100);
        } while (SystemClock.elapsedRealtime() < deadline);
        return false;
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
