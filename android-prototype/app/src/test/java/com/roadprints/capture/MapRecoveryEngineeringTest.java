package com.roadprints.capture;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.Map;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class MapRecoveryEngineeringTest {
    @Test public void legacyTruncationKeepsPreviouslyDisplayedFragmentsWhenRefreshIsDeferred() throws Exception {
        Context app = RuntimeEnvironment.getApplication();
        File source = new File(app.getCacheDir(), "recovery-legacy-overseas-partial.json");
        Files.write(source.toPath(), "{}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String stale = "{\"rows\":[{\"points\":[[10,50],[10.001,50]],\"original\":2}],\"truncated\":true}";
        try {
            try (CompactJourneyCache old = new CompactJourneyCache(app, "map-overseas-v1")) {
                old.get(source, file -> new JSONObject(stale),
                        index -> true, row -> new JSONArray().put(1));
            }
            try (CompactJourneyCache upgraded = new CompactJourneyCache(app, "map-overseas-v1")) {
                JSONObject held = MapActivity.acceptedOverseasProjection(upgraded, source,
                        file -> null, false);
                assertNotNull(held);
                assertEquals(stale, held.toString());
            }
            try (CompactJourneyCache reopened = new CompactJourneyCache(app, "map-overseas-v1")) {
                JSONObject recovered = MapActivity.acceptedOverseasProjection(reopened, source,
                        file -> new JSONObject(stale), true);
                assertNotNull(recovered);
                assertEquals(2, recovered.getInt("projectionVersion"));
                assertEquals(1, recovered.getJSONArray("rows").length());
            }
        } finally { source.delete(); }
    }

    @Test public void warmBuild313TruncationIsRefreshedAndRetainedOnNextOpen() throws Exception {
        Context app = RuntimeEnvironment.getApplication();
        File source = new File(app.getCacheDir(), "recovery-legacy-overseas-truncated.json");
        Files.write(source.toPath(), "{\"source\":\"accepted\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        try {
            // Simulate the previous release's durable (unversioned) projection,
            // which discarded the accepted prefix at the point limit.
            try (CompactJourneyCache old = new CompactJourneyCache(app, "map-overseas-v1")) {
                JSONObject stale = old.get(source, file -> new JSONObject()
                        .put("rows", new JSONArray()).put("truncated", true),
                        index -> true, value -> new JSONArray().put(1));
                assertNotNull(stale);
                assertFalse(stale.has("projectionVersion"));
            }
            final int[] recomputations = {0};
            try (CompactJourneyCache upgraded = new CompactJourneyCache(app, "map-overseas-v1")) {
                JSONObject recovered = MapActivity.acceptedOverseasProjection(upgraded, source,
                        file -> {
                            recomputations[0]++;
                            JSONArray line = new JSONArray("[[10,50],[10.001,50],[10.002,50]]");
                            return new JSONObject().put("rows", new JSONArray().put(
                                    new JSONObject().put("points", line).put("original", 3)))
                                    .put("truncated", true);
                        }, false);
                assertNotNull("Old truncated projection must be recomputed", recovered);
                assertEquals(1, recomputations[0]);
                assertEquals(2, recovered.getInt("projectionVersion"));
                assertEquals(3, recovered.getJSONArray("rows").getJSONObject(0)
                        .getJSONArray("points").length());
                JSONObject reused = MapActivity.acceptedOverseasProjection(upgraded, source,
                        file -> { throw new AssertionError("Versioned result must be reused"); }, false);
                assertNotNull(reused);
                assertEquals(3, reused.getJSONArray("rows").getJSONObject(0)
                        .getJSONArray("points").length());
            }
        } finally { source.delete(); }
    }
    @Test public void deferredOverseasEmptyProjectionIsResolvedAndIndexed() throws Exception {
        Context app = RuntimeEnvironment.getApplication();
        File source = new File(app.getCacheDir(), "recovery-overseas-empty.json");
        Files.write(source.toPath(), "{}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        try (CompactJourneyCache cache = new CompactJourneyCache(app, "map-overseas-v1")) {
            JSONObject empty = cache.get(source, file -> new JSONObject()
                    .put("rows", new JSONArray()).put("truncated", false),
                    index -> true, value -> new JSONArray().put(
                            value.optJSONArray("rows").length() > 0 ? 1 : 0));
            assertNotNull("Empty accepted projection is a completed calculation", empty);
            assertEquals(0, empty.getJSONArray("rows").length());
            assertEquals(JourneyStore.fileToken(source),
                    cache.excludedSources(index -> index.optInt(0, 1) != 0).get(source.getName()));
        } finally { source.delete(); }
    }
    @Test public void cappedWorldwideFragmentRetainsVerifiedPrefixInsteadOfDroppingIt() throws Exception {
        JSONArray line = new JSONArray("[[10,50],[10.001,50],[10.002,50],[10.003,50]]");
        boolean[] truncated = {false};
        java.util.List<JSONArray> fragments = OverseasMatchedGeometry.unsupportedFragments(
                java.util.List.of(line), 48, 3, (longitude, latitude) -> false, truncated);
        assertTrue("Budget must be reported", truncated[0]);
        assertEquals("Already accepted prefix must remain visible", 1, fragments.size());
        assertEquals(3, fragments.get(0).length());
        assertEquals(10.002, fragments.get(0).getJSONArray(2).getDouble(0), 0.000001);
    }
    @Test public void trainAircraftAndUnfinishedMatchingDoNotInvalidateRoadMap() throws Exception {
        Context app = RuntimeEnvironment.getApplication();
        JourneyStore.save(app, journey("recovery-scope-existing"));
        long map = JourneyStore.mapRevision(app);
        for (String mode : new String[]{"train", "plane", "walking", "driving", "cycling"}) {
            JSONObject row = journey("recovery-scope-" + mode).put("mode", mode)
                    .put("processing_status", "pending");
            JourneyStore.save(app, row);
            assertEquals(mode, map, JourneyStore.mapRevision(app));
            row.put("processing_status", "processing");
            JourneyStore.save(app, row);
            row.put("processing_status", "failed");
            JourneyStore.save(app, row);
            assertEquals(mode, map, JourneyStore.mapRevision(app));
            row.put("processing_status", "complete");
            JourneyStore.save(app, row);
            if ("train".equals(mode) || "plane".equals(mode)) {
                assertEquals(mode, map, JourneyStore.mapRevision(app));
                JourneyStore.delete(app, row.getString("journey_id"));
                assertEquals(mode, map, JourneyStore.mapRevision(app));
            } else {
                assertTrue(mode, JourneyStore.mapRevision(app) > map);
                map = JourneyStore.mapRevision(app);
            }
        }
        JSONObject road = JourneyStore.get(app, "recovery-scope-driving").put("processing_status", "pending");
        JourneyStore.save(app, road);
        assertTrue("Withdrawing previously complete coverage still invalidates",
                JourneyStore.mapRevision(app) > map);
        JourneyStore.deleteAll(app);
    }
    @Test public void firstLoadUkOnlyFallbackIsExcludedWithoutReportingWorldwideHold() throws Exception {
        Context app = RuntimeEnvironment.getApplication();
        JourneyStore.deleteAll(app);
        JSONObject row = journey("recovery-first-uk");
        row.put("processing_result", new JSONObject().put("geojson",
                new JSONObject().put("type", "FeatureCollection").put("features",
                        new JSONArray().put(new JSONObject().put("type", "Feature")
                                .put("geometry", new JSONObject().put("type", "LineString")
                                        .put("coordinates", new JSONArray("[[0.375,51.44],[0.376,51.44]]")))))));
        JourneyStore.save(app, row);
        var controller = org.robolectric.Robolectric.buildActivity(MapActivity.class).create();
        try {
            var read = MapActivity.class.getDeclaredMethod("readMapRoutes", JourneyFilters.class,
                    java.time.LocalDate.class, java.time.ZoneId.class);
            read.setAccessible(true);
            MapActivity.MapRoutes routes = (MapActivity.MapRoutes) read.invoke(controller.get(),
                    JourneyFilters.all(), java.time.LocalDate.of(2026, 11, 20), java.time.ZoneId.of("UTC"));
            assertTrue(routes.localReferenceAvailable);
            assertFalse("A new verified empty UK fallback row is not a resource hold",
                    routes.internationalCoverageIncomplete);
        } finally { controller.destroy(); JourneyStore.deleteAll(app); }
    }
    @Test public void deferredFootProjectionNeverCachesOldSpansAgainstANewerSourceToken() throws Exception {
        Context app = RuntimeEnvironment.getApplication();
        File source = new File(app.getCacheDir(), "recovery-deferred-foot.json");
        Files.write(source.toPath(), "{}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String before = JourneyStore.fileToken(source);
        var project = MapActivity.class.getDeclaredMethod("acceptedFootProjection",
                CompactJourneyCache.class, ARoadProgressCalculator.class, File.class, String.class, Map.class);
        project.setAccessible(true);
        try (CompactJourneyCache cache = new CompactJourneyCache(app, "recovery-deferred-test")) {
            cache.get(source, file -> new JSONObject().put("rows", new JSONArray()).put("sections", 0));
            assertNotNull(project.invoke(null, cache, new ARoadProgressCalculator(app, false),
                    source, before, Map.of()));
            Files.write(source.toPath(), "{\"updated\":1}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            assertNull(project.invoke(null, cache, new ARoadProgressCalculator(app, false),
                    source, before, Map.of()));
        } finally { source.delete(); }
    }
    @Test public void sameRevisionDegradedMapReturnsWithoutArchiveWorkAndKeepsCamera() throws Exception {
        Context app = RuntimeEnvironment.getApplication();
        var controller = org.robolectric.Robolectric.buildActivity(MapActivity.class).create();
        MapActivity screen = controller.get();
        try {
            RoutePreviewView view = RoutePreviewView.overview(screen);
            view.layout(0, 0, 800, 800);
            view.restoreCameraState(.375, 51.44, 14);
            Field map = MapActivity.class.getDeclaredField("mapView");
            Field shown = MapActivity.class.getDeclaredField("hasDisplayedMap");
            Field revision = MapActivity.class.getDeclaredField("displayedMapRevision");
            var current = MapActivity.class.getDeclaredMethod("mapDataRevision");
            var refresh = MapActivity.class.getDeclaredMethod("refreshMap");
            for (Field field : new Field[]{map, shown, revision}) field.setAccessible(true);
            current.setAccessible(true); refresh.setAccessible(true);
            map.set(screen, view); shown.setBoolean(screen, true);
            revision.setLong(screen, (long) current.invoke(screen));
            MapActivity.trimProcessMapCache(android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW);
            org.robolectric.shadows.ShadowLog.clear();
            refresh.invoke(screen);
            assertSame(view, map.get(screen));
            assertArrayEquals(new double[]{.375, 51.44, 14}, view.cameraState(), .000001);
            assertTrue(org.robolectric.shadows.ShadowLog.getLogsForTag("RoadprintsTiming").stream()
                    .anyMatch(log -> log.msg.contains("source=retained_view")));
            assertFalse(org.robolectric.shadows.ShadowLog.getLogsForTag("RoadprintsTiming").stream()
                    .anyMatch(log -> log.msg.startsWith("map_request_queued")));
        } finally { controller.destroy(); }
    }

    @Test public void finalSectionTransferPreservesUniqueCoverageAndContributorIntervals() throws Exception {
        Context app = RuntimeEnvironment.getApplication();
        try (LocalRoadCoverage local = LocalRoadCoverage.open(app)) {
            assertNotNull(local);
            LocalRoadNetwork.Edge edge = new LocalRoadNetwork.Edge(123, 1, 2, "Shared street",
                    RoadReference.point(.375, 51.44), RoadReference.point(.376, 51.44),
                    0, true, 100000, -1);
            local.add("drive", Map.of(edge, java.util.List.of(new LocalRoadNetwork.Interval(0, 100000))));
            local.add("walk", Map.of(edge, java.util.List.of(new LocalRoadNetwork.Interval(20000, 60000))));
            var expected = local.sections().get(0);
            java.util.List<LocalRoadCoverage.Section> transferred = new java.util.ArrayList<>();
            local.drainSections(transferred::add);
            assertEquals(1, transferred.size());
            assertEquals(expected.uniqueMillimetres, transferred.get(0).uniqueMillimetres);
            assertArrayEquals(expected.history.get("walk"), transferred.get(0).history.get("walk"));
            assertTrue(local.sections().isEmpty());
        }
    }
    private JSONObject journey(String id) throws Exception {
        return new JSONObject().put("journey_id", id).put("mode", "walking")
                .put("processing_status", "complete").put("started_at", "2026-11-20T12:00:00Z")
                .put("ended_at", "2026-11-20T12:05:00Z")
                .put("processing_result", new JSONObject().put("geojson",
                        new JSONObject().put("type", "FeatureCollection")
                                .put("features", new JSONArray())));
    }

    @Test public void dwellRetryAndTitleDoNotRebuildMapButGeometryModeAndDeletionDo() throws Exception {
        Context app = RuntimeEnvironment.getApplication();
        JSONObject row = journey("recovery-map-revision");
        JourneyStore.save(app, row);
        long map = JourneyStore.mapRevision(app);
        row = JourneyStore.get(app, row.getString("journey_id"));
        row.put("arrival_dwell_samples", new JSONArray().put(new JSONObject().put("duration", 30)));
        row.put("last_match_attempt", new JSONObject().put("message", "retry"));
        JourneyStore.save(app, row);
        assertEquals(map, JourneyStore.mapRevision(app));
        JourneyStore.updateTitle(app, row.getString("journey_id"), "Fresh title");
        assertEquals(map, JourneyStore.mapRevision(app));
        row = JourneyStore.get(app, row.getString("journey_id"));
        row.put("journey_corrections", new JSONObject().put("removed_matched_segments", new JSONArray("[0]")));
        JourneyStore.save(app, row);
        assertTrue(JourneyStore.mapRevision(app) > map);
        map = JourneyStore.mapRevision(app);
        JourneyStore.updateMode(app, row.getString("journey_id"), "running");
        assertTrue(JourneyStore.mapRevision(app) > map);
        map = JourneyStore.mapRevision(app);
        JourneyStore.deleteAll(app);
        assertTrue(JourneyStore.mapRevision(app) > map);
    }

    @Test public void everyDisplayedGeometryAndDateChangeInvalidatesEvidence() throws Exception {
        JSONObject before = journey("hash");
        for (String key : new String[]{"geojson", "road_geojson", "a_road_geojson",
                "motorway_geojson", "cycling_geojson"}) {
            JSONObject after = new JSONObject(before.toString());
            after.getJSONObject("processing_result").put(key, new JSONObject().put("marker", 1));
            assertNotEquals(key, JourneyStore.mapEvidenceHash(before), JourneyStore.mapEvidenceHash(after));
        }
        JSONObject after = new JSONObject(before.toString()).put("ended_at", "2026-11-21T00:01:00Z");
        assertNotEquals(JourneyStore.mapEvidenceHash(before), JourneyStore.mapEvidenceHash(after));
        after = new JSONObject(before.toString()).put("route_geometry",
                new JSONObject().put("coordinates", new JSONArray("[[0,51],[0.001,51]]")));
        assertNotEquals(JourneyStore.mapEvidenceHash(before), JourneyStore.mapEvidenceHash(after));
    }

    @Test public void mapFingerprintIgnoresNumericStorageTypesButPreservesRealChanges() throws Exception {
        JSONObject original = new JSONObject().put("coordinates",
                new JSONArray().put(new JSONArray().put(0d).put(51d))
                        .put(new JSONArray().put(.125).put(51.5)));
        JSONObject stored = new JSONObject(original.toString());
        assertEquals(MapEvidenceFingerprint.digest(original), MapEvidenceFingerprint.digest(stored));
        stored.getJSONArray("coordinates").getJSONArray(0).put(0, .0001);
        assertNotEquals(MapEvidenceFingerprint.digest(original), MapEvidenceFingerprint.digest(stored));
    }

    @Test public void worldwideAndFootMajorCachesSurviveDisposableCacheRemoval() throws Exception {
        Context app = RuntimeEnvironment.getApplication();
        File source = new File(app.getFilesDir(), "recovery-durable-source.json");
        Files.write(source.toPath(), "{}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        try {
            for (String kind : new String[]{"map-overseas-v1", "map-foot-major-v1"}) {
                try (CompactJourneyCache cache = new CompactJourneyCache(app, kind)) {
                    cache.get(source, file -> new JSONObject().put("marker", 7));
                }
                assertTrue(new File(app.getNoBackupFilesDir(), "compact-" + kind + ".db").isFile());
                android.database.sqlite.SQLiteDatabase.deleteDatabase(
                        new File(app.getCacheDir(), "compact-" + kind + ".db"));
                try (CompactJourneyCache cache = new CompactJourneyCache(app, kind)) {
                    JSONObject row = cache.get(source, file -> { throw new AssertionError("Lost durable row"); });
                    assertEquals(7, row.getInt("marker"));
                }
            }
        } finally { source.delete(); }
    }

    @Test public void negativeWorldwideIndexSurvivesReopenAndSourceChangesInvalidateIt() throws Exception {
        Context app = RuntimeEnvironment.getApplication();
        File source = new File(app.getCacheDir(), "recovery-world-row.json");
        Files.write(source.toPath(), "{}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        try {
            try (CompactJourneyCache cache = new CompactJourneyCache(app, "recovery-world-test")) {
                cache.get(source, file -> new JSONObject().put("rows", new JSONArray()),
                        index -> index.optInt(0, 1) != 0, value -> new JSONArray().put(0));
            }
            try (CompactJourneyCache cache = new CompactJourneyCache(app, "recovery-world-test")) {
                Map<String, String> excluded = cache.excludedSources(index -> index.optInt(0, 1) != 0);
                assertEquals(JourneyStore.fileToken(source), excluded.get(source.getName()));
                assertNull(cache.get(source, file -> { throw new AssertionError("UK row decoded again"); },
                        index -> index.optInt(0, 1) != 0, value -> new JSONArray().put(0)));
                assertEquals(1, cache.skipped);
                Files.write(source.toPath(), "{\"changed\":true}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
                assertNotEquals(JourneyStore.fileToken(source), excluded.get(source.getName()));
                JSONObject changed = cache.get(source,
                        file -> new JSONObject().put("rows", new JSONArray("[1]")),
                        index -> index.optInt(0, 1) != 0, value -> new JSONArray().put(1));
                assertNotNull(changed);
            }
        } finally { source.delete(); }
    }

    @Test public void drivingCacheReadSkipsUnusedMajorGeometryWithoutChangingStoredFootPayload() throws Exception {
        Context app = RuntimeEnvironment.getApplication();
        File source = new File(app.getCacheDir(), "recovery-major-row.json");
        Files.write(source.toPath(), "{}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        try {
            try (CompactJourneyCache cache = new CompactJourneyCache(app, "recovery-major-test")) {
                cache.get(source, file -> new JSONObject().put("spans", new JSONArray())
                        .put("majorSpans", new JSONArray("[[1,2,3]]")).put("majorIntervals", 1));
            }
            try (CompactJourneyCache cache = new CompactJourneyCache(app, "recovery-major-test")) {
                JSONObject road = cache.getRoad(source, file -> { throw new AssertionError(); }, false);
                assertFalse(road.has("majorSpans"));
                JSONObject foot = cache.getRoad(source, file -> { throw new AssertionError(); }, true);
                assertEquals(1, foot.getJSONArray("majorSpans").length());
                assertEquals(1, foot.getInt("majorIntervals"));
            }
        } finally { source.delete(); }
    }

    @Test public void trimReleasesStrongCacheButKeepsLiveDisplayedSnapshotAvailable() throws Exception {
        MapActivity.clearProcessMapCache();
        MapActivity.MapRoutes live = new MapActivity.MapRoutes();
        Field strong = MapActivity.class.getDeclaredField("processMapCache");
        Field weak = MapActivity.class.getDeclaredField("displayedMapCache");
        strong.setAccessible(true); weak.setAccessible(true);
        strong.set(null, live);
        weak.set(null, new java.lang.ref.WeakReference<>(live));
        MapActivity.trimProcessMapCache(android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW);
        assertNull(strong.get(null));
        assertSame(live, ((java.lang.ref.WeakReference<?>) weak.get(null)).get());
        MapActivity.clearProcessMapCache();
        assertNull(((java.lang.ref.WeakReference<?>) weak.get(null)).get());
    }
}
