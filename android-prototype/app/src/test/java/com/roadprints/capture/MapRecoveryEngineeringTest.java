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

    @Test public void negativeWorldwideIndexSurvivesReopenAndSourceChangesInvalidateIt() throws Exception {
        Context app = RuntimeEnvironment.getApplication();
        File source = new File(app.getCacheDir(), "recovery-world-row.json");
        Files.writeString(source.toPath(), "{}");
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
                Files.writeString(source.toPath(), "{\"changed\":true}");
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
        Files.writeString(source.toPath(), "{}");
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
