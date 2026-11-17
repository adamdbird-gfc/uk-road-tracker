package com.roadprints.capture;

import static com.roadprints.capture.LocalRoadNetwork.*;
import static org.junit.Assert.*;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class LocalRoadCoverageTest {
    private static final String START = "2026-11-15T12:00:00Z";
    private static final String END = "2026-11-15T12:10:00Z";
    private static final long TIME = Instant.parse(START).toEpochMilli();

    @Test public void cachedContributionRejectsMalformedIntervalsRatherThanSilentlyFallingBack()
            throws Exception {
        JSONArray valid = new JSONArray().put(9).put(1).put(2).put("Road")
                .put(.1).put(51.4).put(.2).put(51.4).put(1000).put(100).put(900);
        assertEquals(List.of(new Interval(100, 900)),
                LocalRoadCoverage.decodeCachedIntervals(valid, 1000));
        JSONArray outside = new JSONArray(valid.toString()).put(900).put(1100);
        assertThrows(IllegalArgumentException.class,
                () -> LocalRoadCoverage.decodeCachedIntervals(outside, 1000));
        JSONArray truncated = new JSONArray(valid.toString()).put(950);
        assertThrows(IllegalArgumentException.class,
                () -> LocalRoadCoverage.decodeCachedIntervals(truncated, 1000));
        JSONArray inverted = new JSONArray().put(9).put(1).put(2).put("Road")
                .put(.1).put(51.4).put(.2).put(51.4).put(1000).put(900).put(100);
        assertThrows(IllegalArgumentException.class,
                () -> LocalRoadCoverage.decodeCachedIntervals(inverted, 1000));
    }

    @Test public void nineMileCoverageRepeatsAndSeparatedIntervals() throws Exception {
        LocalRoadNetwork network = new LocalRoadNetwork(0, 0, 1, 1);
        long nineMiles = 14_484_096;
        Edge road = new Edge(1, 1, 2, "Nine miles", new Point(40, 40),
                new Point(40 + nineMiles / 1000.0, 40), 0, true);
        network.add(road);
        List<Interval> complete = new ArrayList<>();
        for (int i = 0; i < 500; i++) {
            List<Span> spans = network.observe(new Fix(road.at(i / 500.0), TIME + 2000L * i, 2),
                    new Fix(road.at((i + 1) / 500.0), TIME + 2000L * (i + 1), 2));
            assertEquals(1, spans.size());
            complete.add(spans.get(0).interval());
        }
        complete = LocalRoadNetwork.union(complete);
        assertEquals(List.of(new Interval(0, (int) nineMiles)), complete);
        Map<String, List<Interval>> history = new LinkedHashMap<>();
        history.put("first", complete);
        history.put("repeat", complete);
        LocalRoadCoverage.Section full = section(network, road, history);
        assertEquals(nineMiles, full.uniqueMillimetres);
        assertEquals(1, full.lines.size());
        assertEquals(List.of("first", "repeat"), full.lines.get(0).contributors(.5));

        List<Interval> first = List.of(new Interval(0, 3_000_000), new Interval(7_000_000, 10_000_000));
        history = new LinkedHashMap<>();
        history.put("first", first);
        history.put("repeat", List.of(new Interval(8_000_000, 9_000_000)));
        LocalRoadCoverage.Section partial = section(network, road, history);
        assertEquals(6_000_000, partial.uniqueMillimetres);
        assertEquals(first, partial.lines.stream().map(LocalRoadCoverage.Line::interval).toList());
        assertEquals(List.of("first"), partial.lines.get(0).contributors(.5));
        assertEquals(List.of("first", "repeat"), partial.lines.get(1).contributors(.5));
        LocalRoadCoverage.Section restored = LocalRoadCoverage.Section.decode(partial.encode());
        assertEquals(partial.uniqueMillimetres, restored.uniqueMillimetres);
        assertEquals(List.of("first", "repeat"), restored.lines.get(1).contributors(.5));
    }

    @Test public void nineMileLegacyLineKeepsOnlyTheUncoveredGap() throws Exception {
        LocalRoadNetwork network = new LocalRoadNetwork(0, 0, 1, 1);
        Edge road = new Edge(1, 1, 2, "", new Point(40, 40), new Point(14524.096, 40), 0, true);
        network.add(road);
        JSONArray geometry = new JSONArray().put(network.coordinates(road.a)).put(network.coordinates(road.b));
        List<JSONArray> covered = LocalRoadCoverage.remainingFragments(network, geometry,
                Map.of(road, List.of(new Interval(0, road.mm))));
        assertTrue(covered.isEmpty());
        List<JSONArray> gap = LocalRoadCoverage.remainingFragments(network, geometry,
                Map.of(road, List.of(new Interval(0, 3_000_000), new Interval(7_000_000, road.mm))));
        assertEquals(1, gap.size());
        assertEquals(3_040, network.point(gap.get(0).getJSONArray(0).getDouble(0), 0).x(), .001);
        assertEquals(7_040, network.point(gap.get(0).getJSONArray(1).getDouble(0), 0).x(), .001);
        assertEquals(2, geometry.length());
    }

    @Test public void parallelRoadsBranchesUncertaintyAndForbiddenDirection() {
        LocalRoadNetwork network = new LocalRoadNetwork(0, 0, 1, 1);
        Edge south = new Edge(1, 1, 2, "", new Point(40, 40), new Point(100, 40), 1, true);
        Edge north = new Edge(2, 3, 4, "", new Point(40, 53), new Point(100, 53), 0, true);
        Edge branch = new Edge(3, 2, 5, "", new Point(100, 40), new Point(100, 100), 0, true);
        Edge unrelated = new Edge(4, 6, 7, "", new Point(100, 40), new Point(160, 40), 0, true);
        for (Edge edge : List.of(south, north, branch, unrelated)) network.add(edge);
        assertEquals(south, match(network, 50, 40, 75, 40, 2).get(0).edge());
        assertEquals(north, match(network, 50, 53, 75, 53, 2).get(0).edge());
        assertEquals(branch, match(network, 100, 50, 100, 75, 2).get(0).edge());
        assertTrue(match(network, 50, 46, 75, 46, 2).isEmpty());
        assertTrue(match(network, 50, 40, 75, 40, 6).isEmpty());
        assertTrue(match(network, 75, 40, 50, 40, 2).isEmpty());
        assertTrue(match(network, 88, 40, 113, 40, 2).isEmpty());
        assertTrue(match(network, 2, 40, 20, 40, 2).isEmpty());
    }

    @Test public void packagedReferenceIsPinnedAndRejectsMissingOrAlteredBytes() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        assertEquals(1540, network(context).edges.size());
        assertEquals(896, network(context).edges.values().stream().filter(edge -> edge.eligible).count());
        assertThrows(java.io.IOException.class, () -> LocalRoadNetwork.load(new ByteArrayInputStream(new byte[0])));
        android.content.ContextWrapper missing = new android.content.ContextWrapper(context) {
            @Override public android.content.res.AssetManager getAssets() {
                throw new IllegalStateException("Reference is unavailable");
            }
        };
        assertNull(LocalRoadCoverage.open(missing));
    }

    @Test public void heldArchiveRowsReportTheirOriginWithoutLoggingFileDetails() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        File source = new File(context.getCacheDir(), "private-journey-marker.json");
        Files.write(source.toPath(), "[".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        try (MapJourneyStore records = new MapJourneyStore(context);
             LocalRoadCoverage coverage = LocalRoadCoverage.open(context)) {
            assertNotNull(coverage);
            var record = records.new Record(source, JourneyStore.fileToken(source),
                    new JSONObject().put("mode", "driving"), null, true);
            assertNull(coverage.contribution(record));
            assertEquals(1, coverage.held);
            assertTrue(coverage.failureBreakdown().contains("LocalRoadCoverage.readAcceptedContribution"));
            assertFalse(coverage.failureBreakdown().contains("private-journey-marker"));

            var routes = new MapActivity.MapRoutes();
            assertTrue(routes.canCacheSnapshot());
            routes.localCoverageDegraded = true;
            assertFalse(routes.canCacheSnapshot());
            routes.localCoverageDegraded = false;
            routes.localReferenceAvailable = false;
            assertFalse(routes.canCacheSnapshot());
        }
    }

    @Test public void mapUsesAcceptedTimelineAndNativeContributorsAndDoesNotWriteArchive() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        LocalRoadNetwork network = network(context);
        Edge edge = new Edge(1000, 1, 2, "Canal Road",
                network.point(0.3753447, 51.44112821), network.point(0.3753871, 51.44128429), 0, true);
        write(context, "drive-one", "driving", network, edge);
        write(context, "drive-two", "driving", network, edge);
        write(context, "walk", "walking", network, edge);
        write(context, "run", "running", network, edge);
        write(context, "bike", "cycling", network, edge);
        Map<String, byte[]> before = new LinkedHashMap<>();
        for (File file : JourneyStore.archiveFiles(context)) before.put(file.getName(), Files.readAllBytes(file.toPath()));

        MapActivity screen = Robolectric.buildActivity(MapActivity.class).create().get();
        java.lang.reflect.Method read = MapActivity.class.getDeclaredMethod("readMapRoutes",
                JourneyFilters.class, LocalDate.class, java.time.ZoneId.class);
        read.setAccessible(true);
        LocalDate day = LocalDate.parse("2026-11-15");
        MapActivity.MapRoutes all = (MapActivity.MapRoutes) read.invoke(screen, JourneyFilters.all(), day, ZoneOffset.UTC);
        assertEquals(5, all.matchedJourneys);
        assertEquals(1, all.localRoadLines.size());
        assertEquals(3, all.sections.size());
        assertEquals(2, all.localRoadByLine.get(all.localRoadLines.get(0)).contributors(.5).size());
        JourneyFilters onFoot = new JourneyFilters(2, "matched", JourneyDateFilter.all());
        MapActivity.MapRoutes foot = (MapActivity.MapRoutes) read.invoke(screen, onFoot, day, ZoneOffset.UTC);
        assertTrue(foot.localRoadLines.isEmpty());
        assertEquals(2, foot.sections.size());

        try (MapJourneyStore records = new MapJourneyStore(context);
             LocalRoadCoverage projection = LocalRoadCoverage.open(context)) {
            records.forEach(() -> false, projection::contribution);
            assertEquals(2, projection.reused);
            assertEquals(0, projection.calculated);
        }
        for (File file : JourneyStore.archiveFiles(context)) assertArrayEquals(before.get(file.getName()), Files.readAllBytes(file.toPath()));

        write(context, "drive-three", "driving", network, edge);
        try (MapJourneyStore records = new MapJourneyStore(context);
             LocalRoadCoverage projection = LocalRoadCoverage.open(context)) {
            records.forEach(() -> false, projection::contribution);
            assertEquals(2, projection.reused);
            assertEquals(1, projection.calculated);
        }
        screen.finish();
    }

    @Test public void editedCaptureKeepsDistantCoverageAndRestorationRecoversRemovedInterval() throws Exception {
        LocalRoadNetwork network = new LocalRoadNetwork(0, 0, .02, .02);
        Edge first = new Edge(1, 101, 102, "First Road", new Point(50, 50), new Point(90, 50), 0, true);
        Edge second = new Edge(2, 201, 202, "Second Road", new Point(200, 50), new Point(240, 50), 0, true);
        network.add(first);
        network.add(second);
        JSONArray firstRoute = new JSONArray().put(network.coordinates(first.at(.25)))
                .put(network.coordinates(first.at(.75)));
        JSONArray samples = new JSONArray();
        Point[] points = {first.at(.25), first.at(.75), second.at(.25), second.at(.75)};
        for (int i = 0; i < points.length; i++) {
            JSONArray xy = network.coordinates(points[i]);
            samples.put(new JSONArray().put(xy.getDouble(0)).put(xy.getDouble(1))
                    .put(2).put(TIME + i * 2000L).put(10).put("gps"));
        }
        JSONObject route = new JSONObject().put("type", "LineString").put("coordinates", firstRoute);
        JSONObject row = new JSONObject().put("raw_capture_samples", samples)
                .put("processing_result", new JSONObject().put("geojson", new JSONObject()
                        .put("features", new JSONArray().put(new JSONObject().put("geometry", route)))))
                .put("journey_corrections", new JSONObject().put("removed_matched_segments", new JSONArray().put(0))
                        .put("removed_road_ids", new JSONArray().put("name:second road")));
        java.nio.file.Path file = Files.createTempFile("local-road-edits", ".json");
        try {
            Files.write(file, row.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            JSONArray edited = LocalRoadCoverage.readContribution(network, file.toFile(), START, END).getJSONArray("spans");
            assertEquals(1, edited.length());
            assertEquals(second.id(), edited.getJSONArray(0).getString(0));
            assertEquals(20000, edited.getJSONArray(0).getInt(2) - edited.getJSONArray(0).getInt(1));

            row.remove("journey_corrections");
            Files.write(file, row.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            JSONArray restored = LocalRoadCoverage.readContribution(network, file.toFile(), START, END).getJSONArray("spans");
            assertEquals(2, restored.length());
            assertEquals(first.id(), restored.getJSONArray(0).getString(0));
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test public void unresolvedRemovalHoldsOnlyNearbyObservationSpans() {
        LocalRoadNetwork network = new LocalRoadNetwork(0, 0, .02, .02);
        Edge edge = new Edge(1, 101, 102, "Road", new Point(30, 50), new Point(220, 50), 0, true);
        network.add(edge);
        Map<Edge, List<Interval>> spans = new LinkedHashMap<>();
        spans.put(edge, new java.util.ArrayList<>(List.of(new Interval(10000, 20000), new Interval(95000, 110000))));
        network.applyRemoval(new RouteRemoval.Segment(new Point(45, 54), new Point(49, 54)), spans);
        assertEquals(List.of(new Interval(95000, 110000)), spans.get(edge));
    }

    private static LocalRoadCoverage.Section section(LocalRoadNetwork network, Edge edge,
            Map<String, List<Interval>> history) throws Exception {
        Map<String, int[]> compact = new LinkedHashMap<>();
        history.forEach((id, intervals) -> compact.put(id, LocalRoadCoverage.endpoints(intervals)));
        return new LocalRoadCoverage.Section(edge.id(), edge.label, edge.mm,
                network.coordinates(edge.a), network.coordinates(edge.b), compact);
    }

    private static List<Span> match(LocalRoadNetwork network, double x, double y, double endX,
                                    double endY, double accuracy) {
        return match(network, new Point(x, y), new Point(endX, endY), accuracy);
    }

    private static List<Span> match(LocalRoadNetwork network, Point a, Point b, double accuracy) {
        return network.observe(new Fix(a, TIME, accuracy), new Fix(b, TIME + 2000, accuracy));
    }

    private static LocalRoadNetwork network(Context context) throws Exception {
        try (InputStream input = context.getAssets().open(ASSET)) { return LocalRoadNetwork.load(input); }
    }

    private static void write(Context context, String id, String mode, LocalRoadNetwork network, Edge edge)
            throws Exception {
        JSONArray coords = new JSONArray().put(network.coordinates(edge.at(.1))).put(network.coordinates(edge.at(.9)));
        JSONArray samples = new JSONArray();
        for (int i = 0; i < 2; i++) {
            JSONArray point = coords.getJSONArray(i);
            samples.put(new JSONArray().put(point.getDouble(0)).put(point.getDouble(1))
                    .put(2).put(TIME + 2000L * i).put(10).put("gps"));
        }
        JSONObject geometry = new JSONObject().put("type", "LineString").put("coordinates", coords);
        JSONObject matched = new JSONObject().put("features", new JSONArray()
                .put(new JSONObject().put("geometry", geometry)));
        JSONObject row = new JSONObject().put("journey_id", id).put("title", "Owner title")
                .put("mode", mode).put("transport_confirmation", "confirmed").put("distance_meters", 31)
                .put("started_at", START).put("ended_at", END).put("processing_status", "complete")
                .put("source", new JSONObject().put("type", "android_activity_capture"))
                .put("raw_capture_samples", samples).put("route_geometry", geometry)
                .put("processing_result", new JSONObject().put("geojson", matched));
        if ("drive-two".equals(id)) {
            row.put("source", new JSONObject().put("type", "google_timeline"));
            row.remove("raw_capture_samples");
        }
        if ("drive-one".equals(id)) {
            JSONObject phantom = new JSONObject().put("type", "LineString").put("coordinates",
                    new JSONArray().put(network.coordinates(new Point(-400, 100)))
                            .put(network.coordinates(new Point(-380, 100))));
            matched.getJSONArray("features").put(new JSONObject().put("geometry", phantom));
            row.put("journey_corrections", new JSONObject().put("removed_matched_segments", new JSONArray().put(1))
                    .put("removed_road_ids", new JSONArray().put("name:" + edge.label.toLowerCase(java.util.Locale.ROOT))));
        }
        Files.write(JourneyStore.archiveFile(context, id).toPath(), row.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
