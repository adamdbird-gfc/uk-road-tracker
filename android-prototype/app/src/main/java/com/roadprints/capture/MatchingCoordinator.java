package com.roadprints.capture;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/** Process-wide owner for background matching. It deliberately holds no Activity or View. */
public final class MatchingCoordinator {
    private static final String API_BASE_URL = "https://uk-road-tracker-api.onrender.com";
    static final int MAX_MATCH_REQUEST_POINTS = 500;
    private static volatile MatchingCoordinator instance;

    public enum State { IDLE, PREPARING, RUNNING, PAUSING, PAUSED, COMPLETE, ERROR }

    public static final class Snapshot {
        public final State state;
        public final int total, checked, matched, failed;
        public final int roadTotal, roadChecked, roadMatched;
        public final int footTotal, footChecked, footMatched;
        public final String message;

        Snapshot(State state, int total, int checked, int matched, int failed,
                 int roadTotal, int roadChecked, int roadMatched,
                 int footTotal, int footChecked, int footMatched, String message) {
            this.state = state;
            this.total = total;
            this.checked = checked;
            this.matched = matched;
            this.failed = failed;
            this.roadTotal = roadTotal;
            this.roadChecked = roadChecked;
            this.roadMatched = roadMatched;
            this.footTotal = footTotal;
            this.footChecked = footChecked;
            this.footMatched = footMatched;
            this.message = message;
        }

        public boolean isGrowing() {
            return state == State.PREPARING || state == State.RUNNING
                    || state == State.PAUSING || state == State.PAUSED;
        }
    }

    private final Context app;
    // One coordinator thread prepares the queue while three match workers run.
    private final ExecutorService workers = Executors.newFixedThreadPool(4);
    private volatile boolean pauseRequested;
    private volatile State state = State.IDLE;
    private volatile String message = "Ready to grow your map";
    private final AtomicInteger checked = new AtomicInteger();
    private final AtomicInteger matched = new AtomicInteger();
    private final AtomicInteger failed = new AtomicInteger();
    private final AtomicInteger roadChecked = new AtomicInteger();
    private final AtomicInteger roadMatched = new AtomicInteger();
    private final AtomicInteger footChecked = new AtomicInteger();
    private final AtomicInteger footMatched = new AtomicInteger();
    private volatile int total, roadTotal, footTotal;

    interface Matcher { JSONObject match(boolean foot, JSONObject payload) throws Exception; }
    private final Matcher matcher;

    private MatchingCoordinator(Context context) { this(context, null); }
    MatchingCoordinator(Context context, Matcher matcher) {
        app = context.getApplicationContext();
        this.matcher = matcher;
    }
    void shutdownForTest() { workers.shutdownNow(); }


    public static MatchingCoordinator get(Context context) {
        MatchingCoordinator current = instance;
        if (current == null) {
            synchronized (MatchingCoordinator.class) {
                current = instance;
                if (current == null) instance = current = new MatchingCoordinator(context);
            }
        }
        return current;
    }

    public void start() { start(null); }

    public synchronized boolean start(String journeyId) { return start(journeyId, false); }

    public synchronized boolean rematch(String journeyId) { return start(journeyId, true); }

    private synchronized boolean start(String journeyId, boolean forceRematch) {
        if (state == State.PREPARING || state == State.RUNNING || state == State.PAUSING) return false;
        pauseRequested = false;
        state = State.PREPARING;
        message = forceRematch ? "Preparing journey to rematch…" : "Preparing journeys for matching…";
        workers.execute(() -> prepareAndRun(journeyId, forceRematch));
        return true;
    }

    public synchronized void pause() {
        if (state == State.RUNNING) {
            pauseRequested = true;
            state = State.PAUSING;
            message = "Pausing after current journeys…";
        }
    }

    public Snapshot snapshot() {
        return new Snapshot(state, total, checked.get(), matched.get(), failed.get(),
                roadTotal, roadChecked.get(), roadMatched.get(),
                footTotal, footChecked.get(), footMatched.get(), message);
    }

    private void prepareAndRun(String journeyId, boolean forceRematch) {
        try {
            List<String> road = new ArrayList<>();
            List<String> foot = new ArrayList<>();
            Map<String, String> queuedModes = new HashMap<>();
            if (journeyId != null) {
                // A per-journey request must not parse every large archived journey.
                JSONObject journey = JourneyStore.get(app, journeyId);
                if (journey != null) enqueueIfMatchable(journey, road, foot, queuedModes, forceRematch);
            } else {
                // Keep only IDs in the work queue; full GeoJSON stays on disk until a
                // worker needs that journey, avoiding a heap-sized archive snapshot.
                JourneyStore.forEach(app, journey -> enqueueIfMatchable(journey, road, foot, queuedModes, false));
            }
            roadTotal = road.size();
            footTotal = foot.size();
            total = roadTotal + footTotal;
            checked.set(0); matched.set(0); failed.set(0);
            roadChecked.set(0); roadMatched.set(0); footChecked.set(0); footMatched.set(0);
            if (total == 0) {
                state = State.COMPLETE;
                message = "All eligible journeys are matched";
                return;
            }
            state = State.RUNNING;
            message = "Matching journeys";
            ConcurrentLinkedQueue<String> roadWork = new ConcurrentLinkedQueue<>(road);
            ConcurrentLinkedQueue<String> footWork = new ConcurrentLinkedQueue<>(foot);
            CountDownLatch done = new CountDownLatch(3);
            for (int i = 0; i < 3; i++) {
                final boolean footLane = i == 2;
                workers.execute(() -> {
                    try {
                        ConcurrentLinkedQueue<String> lane = footLane ? footWork : roadWork;
                        String nextJourneyId;
                        while (!pauseRequested && (nextJourneyId = lane.poll()) != null) {
                            try {
                                JSONObject journey = markProcessing(nextJourneyId, queuedModes.get(nextJourneyId), forceRematch);
                                matchJourney(journey);
                                matched.incrementAndGet();
                                if (footLane) footMatched.incrementAndGet(); else roadMatched.incrementAndGet();
                            } catch (Exception error) {
                                failed.incrementAndGet();
                                markFailed(nextJourneyId, error);
                            } finally {
                                checked.incrementAndGet();
                                if (footLane) footChecked.incrementAndGet(); else roadChecked.incrementAndGet();
                            }
                        }
                    } finally {
                        done.countDown();
                    }
                });
            }
            done.await();
            if (pauseRequested) {
                state = State.PAUSED;
                message = "Remaining journeys are ready to resume";
            } else {
                state = State.COMPLETE;
                message = failed.get() == 0 ? "Map growth complete" : "Matching finished · some journeys need retry";
            }
        } catch (Exception error) {
            state = State.ERROR;
            message = "Matching stopped: " + safeMessage(error);
        }
    }

    private void enqueueIfMatchable(
            JSONObject journey, List<String> road, List<String> foot,
            Map<String, String> queuedModes, boolean forceRematch) {
        recoverIfInterrupted(journey);
        if (!canMatch(journey) || "processing".equals(journey.optString("processing_status"))) return;
        if (!forceRematch && "complete".equals(journey.optString("processing_status")) && hasStoredMatch(journey)) return;
        String journeyId = journey.optString("journey_id", "");
        if (journeyId.isEmpty()) return;
        String mode = journey.optString("mode", "unknown");
        queuedModes.put(journeyId, mode);
        (isFoot(mode) ? foot : road).add(journeyId);
    }

    private void recoverIfInterrupted(JSONObject journey) {
        if (journey == null || !"processing".equals(journey.optString("processing_status"))) return;
        synchronized (JourneyStore.class) {
            try {
                journey.put("processing_status", "pending");
                String stage = isFoot(journey.optString("mode", "unknown")) ? "foot_matching" : "road_matching";
                JSONObject stages = journey.optJSONObject("stage_statuses");
                if (stages != null) stages.put(stage, "pending");
                JSONObject processing = journey.optJSONObject("processing");
                if (processing != null) processing.put(stage, "pending");
                JourneyStore.save(app, journey);
            } catch (Exception error) { throw new IllegalStateException("Could not recover interrupted journey", error); }
        }
    }

    private JSONObject markProcessing(String journeyId, String queuedMode, boolean forceRematch) throws Exception {
        synchronized (JourneyStore.class) {
            JSONObject journey = JourneyStore.get(app, journeyId);
            if (journey == null) throw new IllegalStateException("Journey was deleted before matching");
            if (queuedMode != null && !queuedMode.equals(journey.optString("mode")))
                throw new IllegalStateException("Journey mode changed before matching · retry");
            if ("processing".equals(journey.optString("processing_status")))
                throw new IllegalStateException("Journey is already being matched · retry");
            if (!canMatch(journey)) throw new IllegalStateException("Journey is no longer eligible for matching");
            if (!forceRematch && "complete".equals(journey.optString("processing_status")) && hasStoredMatch(journey))
                throw new IllegalStateException("Journey already has a matched route");
            String stage = isFoot(journey.optString("mode", "unknown")) ? "foot_matching" : "road_matching";
            journey.put("processing_status", "processing");
            JSONObject stages = journey.optJSONObject("stage_statuses");
            if (stages == null) stages = new JSONObject();
            stages.put(stage, "processing"); journey.put("stage_statuses", stages);
            JSONObject processing = journey.optJSONObject("processing");
            if (processing == null) processing = new JSONObject();
            processing.put(stage, "processing"); journey.put("processing", processing);
            JourneyStore.save(app, journey);
            return journey;
        }
    }

    private void matchJourney(JSONObject journey) throws Exception {
        JSONObject geometry = journey.optJSONObject("route_geometry");
        JSONArray coordinates = geometry == null ? null : geometry.optJSONArray("coordinates");
        JSONArray points = new JSONArray();
        if (coordinates != null) for (int i = 0; i < coordinates.length(); i++) {
            JSONArray coordinate = coordinates.optJSONArray(i);
            if (coordinate != null && coordinate.length() >= 2 && !coordinate.isNull(0) && !coordinate.isNull(1)) {
                points.put(new JSONObject().put("lat", coordinate.optDouble(1)).put("lng", coordinate.optDouble(0)));
            }
        }
        if (points.length() < 2) throw new IllegalStateException("At least two GPS points are required");
        boolean foot = isFoot(journey.optString("mode", "unknown"));
        JSONArray requestPoints = sampleMatchPoints(points, MAX_MATCH_REQUEST_POINTS);
        String endpointPath = foot ? "/match-walking" : "/match";
        JSONObject attempt = new JSONObject()
                .put("started_at_utc", Instant.now().toString())
                .put("mode", journey.optString("mode", "unknown"))
                .put("endpoint", endpointPath)
                .put("source_points", points.length())
                .put("submitted_points", requestPoints.length())
                .put("points_reduced", requestPoints.length() < points.length());
        synchronized (JourneyStore.class) {
            JSONObject stored = JourneyStore.get(app, journey.optString("journey_id"));
            if (stored != null) {
                stored.put("last_match_attempt", attempt);
                JourneyStore.save(app, stored);
            }
        }
        JSONObject payload = new JSONObject().put("points", requestPoints);
        JSONObject result = matcher != null ? matcher.match(foot, payload)
                : postWithRetry(API_BASE_URL + endpointPath, payload);
        attempt.put("finished_at_utc", Instant.now().toString());
        if (result.has("input_points")) attempt.put("server_input_points", result.optInt("input_points"));
        if (result.has("matched_tracepoints")) attempt.put("server_matched_tracepoints", result.optInt("matched_tracepoints"));
        JSONArray failedSections = result.optJSONArray("failed_sections");
        int failedCount = failedSections == null ? 0 : failedSections.length();
        if (failedCount > 0) attempt.put("server_failed_sections", failedCount);
        if (!hasRoute(result)) throw new IllegalStateException("Matcher returned no route geometry");
        int serverInput = result.optInt("input_points", points.length());
        int serverMatched = result.optInt("matched_tracepoints", serverInput);
        if (serverMatched < serverInput) attempt.put("partial_match", true);
        if (shouldRejectMatchResult(serverMatched, serverInput, failedCount)) {
            synchronized (JourneyStore.class) {
                JSONObject stored = JourneyStore.get(app, journey.optString("journey_id"));
                if (stored != null) { stored.put("last_match_attempt", attempt); JourneyStore.save(app, stored); }
            }
            throw new IllegalStateException("Partial route match: " + serverMatched + " of " + serverInput
                    + " GPS points matched across " + failedCount + " unmatched section(s). Rematch to try again.");
        }
        synchronized (JourneyStore.class) {
        JSONObject stored = JourneyStore.get(app, journey.optString("journey_id"));
        if (stored == null) throw new IllegalStateException("Journey is no longer available");
        if (!journey.optString("mode").equals(stored.optString("mode"))
                || !geometry.toString().equals(String.valueOf(stored.optJSONObject("route_geometry")))) {
            throw new IllegalStateException("Journey changed during matching · retry with its current route and mode");
        }
        String stage = foot ? "foot_matching" : "road_matching";
        stored.put("processing_status", "complete");
        JSONObject stages = stored.optJSONObject("stage_statuses"); if (stages == null) stages = new JSONObject();
        stages.put(stage, "complete"); stored.put("stage_statuses", stages);
        JSONObject processing = stored.optJSONObject("processing"); if (processing == null) processing = new JSONObject();
        processing.put(stage, "complete"); stored.put("processing", processing);
        stored.put("processing_result", result);
        stored.put("last_match_attempt", attempt);
        stored.put("processing_completed_at", Instant.now().toString());
        stored.remove("error_summary");
        JourneyStore.save(app, stored);
        }
    }

    static boolean shouldRejectMatchResult(int matchedPoints, int inputPoints, int failedSections) {
        return failedSections > 0 || matchedPoints <= 0 || matchedPoints > inputPoints;
    }

    private void markFailed(String journeyId, Exception error) {
        try {
            synchronized (JourneyStore.class) {
            JSONObject stored = JourneyStore.get(app, journeyId);
            if (stored == null) return;
            String stage = isFoot(stored.optString("mode", "unknown")) ? "foot_matching" : "road_matching";
            stored.put("processing_status", "failed"); stored.put("error_summary", safeMessage(error));
            JSONObject attempt = stored.optJSONObject("last_match_attempt");
            if (attempt != null) {
                attempt.put("finished_at_utc", Instant.now().toString());
                attempt.put("error", safeMessage(error));
                stored.put("last_match_attempt", attempt);
            }
            JSONObject stages = stored.optJSONObject("stage_statuses"); if (stages == null) stages = new JSONObject();
            stages.put(stage, "failed"); stored.put("stage_statuses", stages);
            JSONObject processing = stored.optJSONObject("processing"); if (processing == null) processing = new JSONObject();
            processing.put(stage, "failed"); stored.put("processing", processing);
            JourneyStore.save(app, stored);
            CrashReporter.recordMatchFailure(app, stored, error);
            }
        } catch (Exception ignored) { }
    }

    static JSONArray sampleMatchPoints(JSONArray points, int maxPoints) throws Exception {
        if (points == null || points.length() <= maxPoints || maxPoints < 2) return points;
        JSONArray sampled = new JSONArray();
        int sourceCount = points.length();
        for (int index = 0; index < maxPoints; index++) {
            int sourceIndex = (int) Math.round(index * (sourceCount - 1.0) / (maxPoints - 1.0));
            sampled.put(points.get(sourceIndex));
        }
        return sampled;
    }

    private JSONObject postWithRetry(String endpoint, JSONObject payload) throws Exception {
        // Allow time for the free API instance to wake after an idle period.\n        long[] delays = {3_000L, 8_000L, 20_000L};
        for (int attempt = 0; ; attempt++) try {
            return post(endpoint, payload);
        } catch (Exception error) {
            String text = safeMessage(error).toLowerCase();
            boolean transientFailure = text.contains("http 429") || text.matches("(?s).*http 5[0-9][0-9].*")
                    || text.contains("timeout") || text.contains("failed to connect") || text.contains("connection reset");
            if (!transientFailure || attempt >= delays.length) throw error;
            Thread.sleep(delays[attempt]);
        }
    }

    private JSONObject post(String endpoint, JSONObject payload) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        try {
        connection.setRequestMethod("POST"); connection.setConnectTimeout(15000); connection.setReadTimeout(90000);
        connection.setDoOutput(true); connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Accept", "application/json");
        try (OutputStream output = connection.getOutputStream()) { output.write(payload.toString().getBytes(StandardCharsets.UTF_8)); }
        int status = connection.getResponseCode();
        java.io.InputStream stream = status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream();
        StringBuilder body = new StringBuilder();
        if (stream != null) try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line; while ((line = reader.readLine()) != null) body.append(line);
        }
        if (status < 200 || status >= 300) {
            throw new IllegalStateException(CrashReporter.summarizeMatcherError(
                    status, body.toString()));
        }
        return new JSONObject(body.toString());
        } finally { connection.disconnect(); }
    }

    private boolean canMatch(JSONObject j) {
        String mode = j.optString("mode", "unknown");
        if (!isFoot(mode) && !isRoad(mode)) return false;
        JSONObject geometry = j.optJSONObject("route_geometry");
        JSONArray points = geometry == null ? null : geometry.optJSONArray("coordinates");
        if (points == null || points.length() < 2) return false;
        if (isRoad(mode)) {
            JSONObject source = j.optJSONObject("source");
            if (source != null && "timeline_import".equals(source.optString("type", ""))) {
                JSONObject quality = j.optJSONObject("capture_quality");
                return quality != null && quality.optInt("source_route_points", 0) >= 2;
            }
        }
        return true;
    }

    private boolean hasStoredMatch(JSONObject j) { return hasRoute(j.optJSONObject("processing_result")); }
    private boolean hasRoute(JSONObject result) {
        JSONObject geo = result == null ? null : result.optJSONObject("geojson");
        JSONArray features = geo == null ? null : geo.optJSONArray("features");
        if (features == null) return false;
        for (int i = 0; i < features.length(); i++) {
            JSONObject feature = features.optJSONObject(i);
            JSONObject geometry = feature == null ? null : feature.optJSONObject("geometry");
            if (geometry == null) continue;
            String type = geometry.optString("type", "");
            JSONArray coordinates = geometry.optJSONArray("coordinates");
            if ("LineString".equals(type) && coordinates != null && coordinates.length() >= 2) return true;
            if ("MultiLineString".equals(type) && coordinates != null) for (int j = 0; j < coordinates.length(); j++) {
                JSONArray line = coordinates.optJSONArray(j); if (line != null && line.length() >= 2) return true;
            }
        }
        return false;
    }
    private boolean isFoot(String mode) { return "walking".equals(mode) || "running".equals(mode) || "pedestrian".equals(mode); }
    private boolean isRoad(String mode) { return "driving".equals(mode) || "bus".equals(mode); }
    private String safeMessage(Exception error) { return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage(); }
}
