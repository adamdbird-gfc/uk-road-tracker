package com.roadprints.capture;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class MatchingCoordinatorTest {
    private Context app;
    private MatchingCoordinator coordinator;
    @Before public void setUp() {
        app = RuntimeEnvironment.getApplication();
        JourneyStore.deleteAll(app);
        CrashReporter.clear(app);
    }
    @After public void tearDown() {
        if (coordinator != null) coordinator.shutdownForTest();
        JourneyStore.deleteAll(app);
    }
    private void save(String id, String mode, String status) throws Exception {
        JourneyStore.save(app, new JSONObject().put("journey_id", id).put("mode", mode)
                .put("processing_status", status).put("source", new JSONObject().put("type", "capture"))
                .put("route_geometry", new JSONObject().put("type", "LineString").put("coordinates",
                        new JSONArray("[[0.3,51.4],[0.31,51.41]]"))));
    }
    private JSONObject route() throws Exception {
        return new JSONObject("{\"geojson\":{\"features\":[{\"geometry\":{\"type\":\"LineString\",\"coordinates\":[[0.3,51.4],[0.31,51.41]]}}]}}");
    }
    private void await(MatchingCoordinator.State expected) throws Exception {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (coordinator.snapshot().state != expected && System.nanoTime() < end) Thread.sleep(10);
        assertEquals(coordinator.snapshot().message, expected, coordinator.snapshot().state);
    }
    @Test public void separatesModesAndRecoversInterruptedWithoutRematchingComplete() throws Exception {
        save("road", "bus", "processing"); save("foot", "walking", "pending"); save("train", "train", "pending");
        AtomicInteger road = new AtomicInteger(), foot = new AtomicInteger();
        coordinator = new MatchingCoordinator(app, (isFoot, payload) -> {
            ("/match-walking".equals(isFoot) ? foot : road).incrementAndGet(); return route();
        });
        coordinator.start(); await(MatchingCoordinator.State.COMPLETE);
        assertEquals(1, road.get()); assertEquals(1, foot.get());
        assertEquals(2, coordinator.snapshot().matched);
        assertEquals("complete", JourneyStore.get(app, "road").optString("processing_status"));
        assertEquals("pending", JourneyStore.get(app, "train").optString("processing_status"));
        coordinator.start(); await(MatchingCoordinator.State.COMPLETE);
        assertEquals(1, road.get()); assertEquals(1, foot.get());
    }
    @Test public void unclassifiedVehicleLegIsNotSentToRoadMatching() throws Exception {
        JSONObject unreviewed = new JSONObject().put("journey_id", "unreviewed")
                .put("mode", "unknown").put("processing_status", "pending")
                .put("transport_confirmation", "required")
                .put("source", new JSONObject().put("type", "android_activity_capture"))
                .put("route_geometry", new JSONObject().put("type", "LineString")
                        .put("coordinates", new JSONArray("[[0.3,51.4],[0.31,51.41]]")));
        JourneyStore.save(app, unreviewed);
        AtomicInteger calls = new AtomicInteger();
        coordinator = new MatchingCoordinator(app, (isFoot, payload) -> {
            calls.incrementAndGet(); return route();
        });
        coordinator.start(); await(MatchingCoordinator.State.COMPLETE);
        assertEquals(0, calls.get());
        assertEquals("pending", JourneyStore.get(app, "unreviewed").optString("processing_status"));
    }
    @Test public void pauseSavesInFlightAndResumeDoesNotDuplicateWork() throws Exception {
        for (int i = 0; i < 5; i++) save("foot" + i, "walking", "pending");
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        coordinator = new MatchingCoordinator(app, (foot, payload) -> {
            if (calls.incrementAndGet() == 1) { entered.countDown(); assertTrue(release.await(10, TimeUnit.SECONDS)); }
            return route();
        });
        coordinator.start(); assertTrue(entered.await(10, TimeUnit.SECONDS));
        coordinator.start(); // Repeated tap must not start a second queue.
        coordinator.pause(); release.countDown(); await(MatchingCoordinator.State.PAUSED);
        assertEquals(1, calls.get()); assertEquals(1, coordinator.snapshot().matched);
        coordinator.start(); await(MatchingCoordinator.State.COMPLETE);
        assertEquals(5, calls.get());
    }
    @Test public void deletionDuringMatchDoesNotRecreateArchive() throws Exception {
        save("delete-me", "walking", "pending");
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        coordinator = new MatchingCoordinator(app, (foot, payload) -> {
            entered.countDown(); assertTrue(release.await(10, TimeUnit.SECONDS)); return route();
        });
        coordinator.start(); assertTrue(entered.await(10, TimeUnit.SECONDS));
        JourneyStore.delete(app, "delete-me"); release.countDown(); await(MatchingCoordinator.State.COMPLETE);
        assertNull(JourneyStore.get(app, "delete-me")); assertEquals(1, coordinator.snapshot().failed);
    }
    @Test public void editDuringMatchPreservesNewModeAndRejectsOldResult() throws Exception {
        save("edit-me", "walking", "pending");
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        coordinator = new MatchingCoordinator(app, (foot, payload) -> {
            entered.countDown(); assertTrue(release.await(10, TimeUnit.SECONDS)); return route();
        });
        coordinator.start("edit-me"); assertTrue(entered.await(10, TimeUnit.SECONDS));
        JourneyStore.updateMode(app, "edit-me", "bus"); release.countDown(); await(MatchingCoordinator.State.COMPLETE);
        JSONObject stored = JourneyStore.get(app, "edit-me");
        assertEquals("bus", stored.optString("mode")); assertFalse(stored.has("processing_result"));
        assertEquals("failed", stored.optString("processing_status"));
    }
    @Test public void failedJourneyCanRetryAndSuccessfulJourneyIsSkipped() throws Exception {
        save("retry", "walking", "pending"); AtomicInteger calls = new AtomicInteger();
        coordinator = new MatchingCoordinator(app, (foot, payload) -> {
            if (calls.incrementAndGet() == 1) throw new IllegalStateException("Network unavailable");
            return route();
        });
        coordinator.start(); await(MatchingCoordinator.State.COMPLETE);
        assertEquals(1, coordinator.snapshot().failed);
        coordinator.start(); await(MatchingCoordinator.State.COMPLETE);
        assertEquals(1, coordinator.snapshot().matched); assertEquals(0, coordinator.snapshot().failed);
        coordinator.start(); await(MatchingCoordinator.State.COMPLETE); assertEquals(2, calls.get());
    }
    @Test public void longRoadTraceIsSampledToMatcherLimitAndKeepsBothEndpoints() throws Exception {
        JSONArray coordinates = new JSONArray();
        for (int i = 0; i < 1794; i++) {
            coordinates.put(new JSONArray().put(0.3 + i * 0.00001).put(51.4 + i * 0.00001));
        }
        JourneyStore.save(app, new JSONObject().put("journey_id", "long-road")
                .put("mode", "driving").put("processing_status", "pending")
                .put("source", new JSONObject().put("type", "timeline_import"))
                .put("capture_quality", new JSONObject().put("source_route_points", 1794))
                .put("route_geometry", new JSONObject().put("type", "LineString").put("coordinates", coordinates)));
        AtomicInteger sentCount = new AtomicInteger();
        coordinator = new MatchingCoordinator(app, (foot, payload) -> {
            assertEquals("/match", foot);
            JSONArray sent = payload.optJSONArray("points");
            sentCount.set(sent.length());
            assertEquals(0.3, sent.getJSONObject(0).optDouble("lng"), 0.000001);
            assertEquals(0.31793, sent.getJSONObject(sent.length() - 1).optDouble("lng"), 0.000001);
            return route();
        });
        coordinator.start("long-road"); await(MatchingCoordinator.State.COMPLETE);
        assertEquals(MatchingCoordinator.MAX_MATCH_REQUEST_POINTS, sentCount.get());
        JSONObject stored = JourneyStore.get(app, "long-road");
        JSONObject attempt = stored.optJSONObject("last_match_attempt");
        assertEquals(1794, attempt.optInt("source_points"));
        assertEquals(500, attempt.optInt("submitted_points"));
        assertTrue(attempt.optBoolean("points_reduced"));
    }

    @Test public void longWalkingTraceIsSampledToMatcherLimitAndKeepsBothEndpoints() throws Exception {
        JSONArray coordinates = new JSONArray();
        for (int i = 0; i < 844; i++) {
            coordinates.put(new JSONArray().put(0.15 + i * 0.00001).put(52.2 + i * 0.00001));
        }
        JourneyStore.save(app, new JSONObject().put("journey_id", "long-walk")
                .put("title", "Coldhams Common parkrun")
                .put("mode", "walking").put("processing_status", "pending")
                .put("source", new JSONObject().put("type", "android_activity_capture"))
                .put("route_geometry", new JSONObject().put("type", "LineString").put("coordinates", coordinates)));
        AtomicInteger sentCount = new AtomicInteger();
        coordinator = new MatchingCoordinator(app, (foot, payload) -> {
            assertEquals("/match-walking", foot);
            JSONArray sent = payload.optJSONArray("points");
            sentCount.set(sent.length());
            assertEquals(0.15, sent.getJSONObject(0).optDouble("lng"), 0.000001);
            assertEquals(0.15843, sent.getJSONObject(sent.length() - 1).optDouble("lng"), 0.000001);
            return route();
        });
        coordinator.start("long-walk"); await(MatchingCoordinator.State.COMPLETE);
        assertEquals(MatchingCoordinator.MAX_MATCH_REQUEST_POINTS, sentCount.get());
        JSONObject attempt = JourneyStore.get(app, "long-walk").optJSONObject("last_match_attempt");
        assertEquals("/match-walking", attempt.optString("endpoint"));
        assertEquals(844, attempt.optInt("source_points"));
        assertEquals(500, attempt.optInt("submitted_points"));
        assertTrue(attempt.optBoolean("points_reduced"));
    }

    @Test public void matcherValidationErrorSummaryDoesNotIncludeEchoedCoordinates() throws Exception {
        JSONObject error = new JSONObject()
                .put("type", "too_long")
                .put("loc", new JSONArray().put("body").put("points"))
                .put("msg", "List should have at most 500 items after validation, not 844")
                .put("input", new JSONArray().put(new JSONObject().put("lat", 52.2081).put("lng", 0.1543)));
        String summary = CrashReporter.summarizeMatcherError(422, new JSONArray().put(error).toString());
        assertTrue(summary.contains("HTTP 422"));
        assertTrue(summary.contains("body.points"));
        assertTrue(summary.contains("too_long"));
        assertTrue(summary.contains("not 844"));
        assertFalse(summary.contains("input"));
        assertFalse(summary.contains("52.2081"));
        assertFalse(summary.contains("0.1543"));
    }

    @Test public void shorterRoadFailureKeepsItsExactRequestSizeAndAddsUsefulDebugReport() throws Exception {
        JSONArray coordinates = new JSONArray();
        for (int i = 0; i < 114; i++) {
            coordinates.put(new JSONArray().put(0.3 + i * 0.00001).put(51.4 + i * 0.00001));
        }
        JourneyStore.save(app, new JSONObject().put("journey_id", "road-114")
                .put("title", "Gravesend Station - Home")
                .put("mode", "driving").put("processing_status", "pending")
                .put("source", new JSONObject().put("type", "timeline_import"))
                .put("capture_quality", new JSONObject().put("source_route_points", 114))
                .put("route_geometry", new JSONObject().put("type", "LineString").put("coordinates", coordinates)));
        AtomicInteger sentCount = new AtomicInteger();
        coordinator = new MatchingCoordinator(app, (foot, payload) -> {
            sentCount.set(payload.optJSONArray("points").length());
            throw new IllegalStateException("HTTP 422: test matcher detail");
        });
        coordinator.start("road-114"); await(MatchingCoordinator.State.COMPLETE);
        assertEquals(114, sentCount.get());
        JSONObject stored = JourneyStore.get(app, "road-114");
        assertEquals("failed", stored.optString("processing_status"));
        String report = CrashReporter.getDiagnosticReports(app);
        assertTrue(report.contains("Journey title: Gravesend Station - Home"));
        assertTrue(report.contains("GPS points: 114"));
        assertTrue(report.contains("Points submitted: 114"));
        assertTrue(report.contains("HTTP 422: test matcher detail"));
    }


    private JSONObject cycleRoute() throws Exception {
        return route().put("matching_mode", "cycling").put("matched_distance_m", 100)
                .put("matched_distance_is_deduplicated", true);
    }
    @Test public void cyclingUsesSeparateEndpointAndStageWithoutCallingRoadOrFoot() throws Exception {
        save("cycle", "cycling", "pending"); save("bicycle", "bicycle", "pending");
        AtomicInteger calls = new AtomicInteger();
        coordinator = new MatchingCoordinator(app, (endpoint, payload) -> {
            assertEquals("/match-cycling", endpoint); assertFalse(payload.has("road_recovery"));
            calls.incrementAndGet(); return cycleRoute();
        });
        coordinator.start(); await(MatchingCoordinator.State.COMPLETE);
        assertEquals(2, calls.get()); assertEquals(2, coordinator.snapshot().cycleMatched);
        assertEquals(0, coordinator.snapshot().roadMatched); assertEquals(0, coordinator.snapshot().footMatched);
        JSONObject saved = JourneyStore.get(app, "cycle");
        assertEquals("complete", saved.getJSONObject("processing").getString("cycle_matching"));
        assertEquals("/match-cycling", saved.getJSONObject("last_match_attempt").getString("endpoint"));
        assertEquals(100, saved.getDouble("distance_meters"), 0);
        assertEquals("validated_cycling_match", saved.getString("distance_source"));
    }
    @Test public void failedCycleMatchPreservesTraceAndCanRetry() throws Exception {
        save("cycle", "cycling", "pending"); AtomicInteger calls = new AtomicInteger();
        String raw = JourneyStore.get(app, "cycle").getJSONObject("route_geometry").toString();
        coordinator = new MatchingCoordinator(app, (endpoint, payload) -> {
            if (calls.incrementAndGet() == 1) throw new IllegalStateException("HTTP 503: Cycling router busy");
            return cycleRoute();
        });
        coordinator.start("cycle"); await(MatchingCoordinator.State.COMPLETE);
        assertEquals("failed", JourneyStore.get(app, "cycle").getString("processing_status"));
        assertEquals(raw, JourneyStore.get(app, "cycle").getJSONObject("route_geometry").toString());
        coordinator.start("cycle"); await(MatchingCoordinator.State.COMPLETE);
        assertEquals("complete", JourneyStore.get(app, "cycle").getString("processing_status"));
    }
    @Test public void cycleCannotReuseCarMatchAfterChangingTransport() throws Exception {
        save("change", "driving", "complete");
        JSONObject before=JourneyStore.get(app,"change"); before.put("processing_result",route()); JourneyStore.save(app,before);
        JourneyStore.updateMode(app,"change","cycling");
        JSONObject after=JourneyStore.get(app,"change");
        assertFalse(after.has("processing_result")); assertEquals("pending",after.getString("processing_status"));
        coordinator=new MatchingCoordinator(app,(endpoint,payload)->{assertEquals("/match-cycling",endpoint);return cycleRoute();});
        coordinator.start("change"); await(MatchingCoordinator.State.COMPLETE);
        assertEquals(1,coordinator.snapshot().cycleMatched);
    }
    @Test public void cyclingRejectsUnverifiedDistanceAndEndpointOnlyTimeline() throws Exception {
        save("cycle", "cycling", "pending");
        JSONObject sparse=JourneyStore.get(app,"cycle");
        sparse.put("source",new JSONObject().put("type","timeline_import"));
        sparse.put("capture_quality",new JSONObject().put("source_route_points",0));JourneyStore.save(app,sparse);
        coordinator=new MatchingCoordinator(app,(endpoint,payload)->cycleRoute());
        coordinator.start();await(MatchingCoordinator.State.COMPLETE);assertEquals(0,coordinator.snapshot().total);
        sparse.put("capture_quality",new JSONObject().put("source_route_points",2));JourneyStore.save(app,sparse);
        coordinator.shutdownForTest(); coordinator=new MatchingCoordinator(app,(endpoint,payload)->route());
        coordinator.start();await(MatchingCoordinator.State.COMPLETE);assertEquals(1,coordinator.snapshot().failed);
        assertFalse(JourneyStore.get(app,"cycle").has("processing_result"));
    }
}
