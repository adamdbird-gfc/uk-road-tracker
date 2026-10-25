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
        public final int cycleTotal, cycleChecked, cycleMatched;
        public final String message;

        Snapshot(State state, int total, int checked, int matched, int failed,
                 int roadTotal, int roadChecked, int roadMatched,
                 int footTotal, int footChecked, int footMatched,
                 int cycleTotal, int cycleChecked, int cycleMatched, String message) {
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
            this.cycleTotal = cycleTotal; this.cycleChecked = cycleChecked; this.cycleMatched = cycleMatched;
            this.message = message;
        }

        public boolean isGrowing() {
            return state == State.PREPARING || state == State.RUNNING
                    || state == State.PAUSING || state == State.PAUSED;
        }
    }

    private final Context app;
    // One coordinator thread prepares two road workers and one worker per active mode.
    private final ExecutorService workers = Executors.newFixedThreadPool(5);
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
    private final AtomicInteger cycleChecked = new AtomicInteger();
    private final AtomicInteger cycleMatched = new AtomicInteger();
    private volatile int total, roadTotal, footTotal, cycleTotal;

    interface Matcher { JSONObject match(String endpoint, JSONObject payload) throws Exception; }
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

    synchronized boolean startCaptured(String journeyId) {return start(journeyId,false,"capture_saved");}
    private synchronized boolean start(String journeyId, boolean forceRematch) {
        return start(journeyId,forceRematch,forceRematch?"user_rematch":journeyId==null?"batch_request":"journey_request");
    }
    private synchronized boolean start(String journeyId, boolean forceRematch,String trigger) {
        if (state == State.PREPARING || state == State.RUNNING || state == State.PAUSING) return false;
        pauseRequested = false;
        state = State.PREPARING;
        message = forceRematch ? "Preparing journey to rematch…" : "Preparing journeys for matching…";
        workers.execute(() -> prepareAndRun(journeyId, forceRematch,trigger));
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
                footTotal, footChecked.get(), footMatched.get(),
                cycleTotal, cycleChecked.get(), cycleMatched.get(), message);
    }

    private void prepareAndRun(String journeyId, boolean forceRematch,String trigger) {
        try {
            List<String> road = new ArrayList<>();
            List<String> foot = new ArrayList<>();
            List<String> cycle = new ArrayList<>();
            Map<String, String> queuedModes = new HashMap<>();
            boolean qualityHeld=false;
            if (journeyId != null) {
                // A per-journey request must not parse every large archived journey.
                JSONObject journey = JourneyStore.get(app, journeyId);
                if (journey != null) {
                    enqueueIfMatchable(journey, road, foot, cycle, queuedModes, forceRematch);
                    qualityHeld=MatchRetryPolicy.qualityBlocked(journey);
                }
            } else {
                // Keep only IDs in the work queue; full GeoJSON stays on disk until a
                // worker needs that journey, avoiding a heap-sized archive snapshot.
                JourneyStore.forEach(app, journey -> enqueueIfMatchable(journey, road, foot, cycle, queuedModes, false));
            }
            roadTotal = road.size();
            footTotal = foot.size();
            cycleTotal = cycle.size();
            total = roadTotal + footTotal + cycleTotal;
            checked.set(0); matched.set(0); failed.set(0);
            roadChecked.set(0); roadMatched.set(0); footChecked.set(0); footMatched.set(0); cycleChecked.set(0); cycleMatched.set(0);
            if (total == 0) {
                state = State.COMPLETE;
                message = qualityHeld?"GPS quality review required · original recording preserved":"No journeys currently eligible for matching";
                return;
            }
            state = State.RUNNING;
            message = "Matching journeys";
            ConcurrentLinkedQueue<String> roadWork = new ConcurrentLinkedQueue<>(road);
            ConcurrentLinkedQueue<String> footWork = new ConcurrentLinkedQueue<>(foot);
            ConcurrentLinkedQueue<String> cycleWork = new ConcurrentLinkedQueue<>(cycle);
            CountDownLatch done = new CountDownLatch(4);
            for (int i = 0; i < 4; i++) {
                final boolean footLane = i == 2;
                final boolean cycleLane = i == 3;
                workers.execute(() -> {
                    try {
                        ConcurrentLinkedQueue<String> lane = cycleLane ? cycleWork : footLane ? footWork : roadWork;
                        String nextJourneyId;
                        while (!pauseRequested && (nextJourneyId = lane.poll()) != null) {
                            JSONObject measurementJourney = null;
                            BetaMeasurement.Operation measurement = BetaMeasurement.operation(app, "journey_match");
                            try {
                                JSONObject journey = markProcessing(nextJourneyId, queuedModes.get(nextJourneyId), forceRematch);
                                if(journey==null)continue; // Recheck at execution without inventing a new failure.
                                measurementJourney = journey;
                                android.os.Bundle parameters = BetaMeasurement.journeyParameters(journey);
                                parameters.putString("attempt_type", forceRematch ? "rematch" : "match");
                                BetaMeasurement.event(app, "match_started", parameters);
                                matchJourney(journey);
                                parameters.putLong("duration_ms", measurement.duration());
                                BetaMeasurement.event(app, "match_completed", parameters);
                                matched.incrementAndGet();
                                if (cycleLane) cycleMatched.incrementAndGet(); else if (footLane) footMatched.incrementAndGet(); else roadMatched.incrementAndGet();
                            } catch (Exception error) {
                                failed.incrementAndGet();
                                markFailed(nextJourneyId, error, measurementJourney,trigger,cycleLane?"cycle_matching":footLane?"foot_matching":"road_matching");
                                if (measurementJourney != null) {
                                    android.os.Bundle parameters = BetaMeasurement.journeyParameters(measurementJourney);
                                    parameters.putString("attempt_type", forceRematch ? "rematch" : "match");
                                    parameters.putString("error_category", BetaMeasurement.errorCategory(error));
                                    parameters.putLong("duration_ms", measurement.duration());
                                    BetaMeasurement.event(app, "match_failed", parameters);
                                    BetaMeasurement.reportFailure(app, error);
                                }
                            } finally {
                                measurement.close();
                                checked.incrementAndGet();
                                if (cycleLane) cycleChecked.incrementAndGet(); else if (footLane) footChecked.incrementAndGet(); else roadChecked.incrementAndGet();
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
            JSONObject journey, List<String> road, List<String> foot, List<String> cycle,
            Map<String, String> queuedModes, boolean forceRematch) {
        try{if(MatchRetryPolicy.adoptLegacy(journey))JourneyStore.save(app,journey);}
        catch(Exception ignored){return;}
        if(MatchRetryPolicy.qualityBlocked(journey)||MatchRetryPolicy.coolingDown(journey))return;
        recoverIfInterrupted(journey);
        if (!canMatch(journey) || "processing".equals(journey.optString("processing_status"))) return;
        if (!forceRematch && "complete".equals(journey.optString("processing_status")) && (hasStoredMatch(journey)||WalkingMatchOutcome.terminal(journey))) return;
        String journeyId = journey.optString("journey_id", "");
        if (journeyId.isEmpty()) return;
        String mode = journey.optString("mode", "unknown");
        queuedModes.put(journeyId, mode);
        (isCycle(mode) ? cycle : isFoot(mode) ? foot : road).add(journeyId);
    }

    private void recoverIfInterrupted(JSONObject journey) {
        if (journey == null || !"processing".equals(journey.optString("processing_status"))) return;
        synchronized (JourneyStore.class) {
            try {
                journey.put("processing_status", "pending");
                String stage = matchingStage(journey.optString("mode", "unknown"));
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
            if (!forceRematch && "complete".equals(journey.optString("processing_status")) && (hasStoredMatch(journey)||WalkingMatchOutcome.terminal(journey)))
                throw new IllegalStateException("Journey already has a matched route");
            if(MatchRetryPolicy.qualityBlocked(journey)||MatchRetryPolicy.coolingDown(journey))return null;
            journey.put("last_match_attempt",new JSONObject().put("attempt_id",java.util.UUID.randomUUID().toString())
                    .put("started_at_utc",Instant.now().toString()));
            String stage = matchingStage(journey.optString("mode", "unknown"));
            journey.remove("walking_outcome");
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
        if(isFoot(journey.optString("mode"))&&!DestinationJourneyEvidence.purposeful(journey)&&ResortJourneyPolicy.internal(new DestinationCatalog(app),journey.optJSONArray("capture_route_samples")))
            throw new IllegalStateException("Movement inside a recognised resort does not require road matching. The original recording is preserved.");
        JSONObject geometry = journey.optJSONObject("route_geometry");
        JSONArray coordinates = geometry == null ? null : geometry.optJSONArray("coordinates");
        JSONArray points = new JSONArray();
        if (coordinates != null) for (int i = 0; i < coordinates.length(); i++) {
            JSONArray coordinate = coordinates.optJSONArray(i);
            if (coordinate != null && coordinate.length() >= 2 && !coordinate.isNull(0) && !coordinate.isNull(1)) {
                points.put(new JSONObject().put("lat", coordinate.optDouble(1)).put("lng", coordinate.optDouble(0)));
            }
        }
        if (FerryJourneyEvidence.inspect(journey.optJSONArray("capture_route_samples")).hasMarineEvidence())
            throw new IllegalStateException("Ferry movement requires transport review before road or cycling matching. The original recording is preserved.");
        if (points.length() < 2) throw new IllegalStateException("At least two GPS points are required");
        String mode = journey.optString("mode", "unknown");
        boolean foot = isFoot(mode);
        boolean cycle = isCycle(mode);
        JSONObject source = journey.optJSONObject("source");
        boolean timelineImport = source != null && "timeline_import".equals(source.optString("type"));
        boolean capturedDrive = !foot && !cycle && source != null
                && "android_activity_capture".equals(source.optString("type"))
                && DrivingJourneyPreparation.aligned(journey,points);
        WalkingJourneyPreparation.Prepared walking = foot
                ? WalkingJourneyPreparation.prepare(app, journey) : null;
        if (source != null && "android_activity_capture".equals(source.optString("type"))) {
            JSONObject quality = walking != null && walking.validated ? walking.quality
                    : CaptureQualityValidator.inspect(journey.optJSONArray("capture_route_samples"), foot);
            synchronized (JourneyStore.class) {
                JSONObject saved = JourneyStore.get(app, journey.optString("journey_id"));
                if (saved != null) { saved.put("recording_quality", quality); JourneyStore.save(app, saved); }
            }
            if (!quality.optBoolean("resolved")) throw new CaptureEvidenceFailure(
                    "This app recording contains unreliable GPS fixes or a gap while moving. Full matching is blocked; your original recording is preserved. Use Debug Journey to report this capture issue.");
        }
        if (walking != null) points = walking.points;
        if (foot) points=WalkingTraceSimplifier.simplify(points);
        if (points.length() < 2) throw new IllegalStateException("Not enough reliable GPS points to match this walk. The original recording is preserved.");
        if (cycle) {
            double[][] fixes = new double[points.length()][2];
            for (int i=0;i<points.length();i++) { JSONObject fix=points.getJSONObject(i); fixes[i][0]=fix.getDouble("lng"); fixes[i][1]=fix.getDouble("lat"); }
            JSONArray reduced = new JSONArray();
            for (int index : CyclingTraceSimplifier.retainedIndices(fixes)) reduced.put(points.get(index));
            points = reduced;
        }
        JSONArray requestPoints = sampleMatchPoints(points, MAX_MATCH_REQUEST_POINTS);
        TimelineRoadPreparation.Prepared road = (foot || cycle) ? null : (capturedDrive ? DrivingJourneyPreparation.prepare(journey, points) : TimelineRoadPreparation.prepare(journey, points));
        List<JSONArray> roadRequests = new ArrayList<>();
        int submittedRoadPoints = 0;
        if (road != null) for (JSONArray section : road.sections) {
            if(capturedDrive){
                for(JSONArray request:DrivingJourneyPreparation.requests(section,MAX_MATCH_REQUEST_POINTS)){
                    roadRequests.add(request);submittedRoadPoints+=request.length();
                }
            }else{
                JSONArray sampled = sampleMatchPoints(section, MAX_MATCH_REQUEST_POINTS);
                roadRequests.add(sampled); submittedRoadPoints += sampled.length();
            }
        }
        String endpointPath = endpointForMode(mode);
        JSONObject attempt = new JSONObject(journey.getJSONObject("last_match_attempt").toString())
                .put("mode", journey.optString("mode", "unknown"))
                .put("endpoint", endpointPath)
                .put("source_points", coordinates.length())
                .put("submitted_points", requestPoints.length())
                .put("points_reduced", requestPoints.length() < coordinates.length());
        if (walking != null) attempt.put("walking_validation", walking.details);
        if (road != null) attempt.put("road_preparation", road.details)
                .put("submitted_points", submittedRoadPoints)
                .put("points_reduced", submittedRoadPoints < points.length());
        synchronized (JourneyStore.class) {
            JSONObject stored = JourneyStore.get(app, journey.optString("journey_id"));
            if (stored != null) {
                stored.put("last_match_attempt", attempt);
                JourneyStore.save(app, stored);
            }
        }
        attempt.put("raw_gps_distance_m",traceDistance(journey.optJSONArray("raw_capture_samples")))
                .put("filtered_gps_distance_m",traceDistance(journey.optJSONArray("capture_route_samples")))
                .put("submitted_geometry_distance_m",road==null?pointDistance(requestPoints):requestDistance(roadRequests));
        persistAttempt(journey,attempt);
        JSONObject result;
        boolean acceptedEndpointPartial = false;
        if (road == null) {
            JSONObject payload = new JSONObject().put("points", requestPoints);
            if(foot)payload.put("walking_recovery",true);
            attempt.put("request_bytes",payload.toString().getBytes(StandardCharsets.UTF_8).length);
            persistAttempt(journey,attempt);
            result = matcher != null ? matcher.match(endpointPath, payload)
                    : postWithRetry(API_BASE_URL + endpointPath, payload);
        } else {
            List<JSONObject> results = new ArrayList<>();
            JSONArray sectionDiagnostics = new JSONArray();
            attempt.put("section_diagnostics", sectionDiagnostics);
            for (int index = 0; index < roadRequests.size(); index++) {
                JSONArray submitted = roadRequests.get(index);
                JSONObject payload = new JSONObject().put("points", submitted).put("road_recovery", true);
                attempt.put("request_bytes",payload.toString().getBytes(StandardCharsets.UTF_8).length);
                persistAttempt(journey,attempt);
                JSONObject sectionResult = matcher != null ? matcher.match(endpointPath, payload)
                        : postWithRetry(API_BASE_URL + endpointPath, payload);
                if (capturedDrive) {
                    JSONArray retry=DrivingJourneyPreparation.retryPoints(sectionResult,submitted,journey);
                    if(retry!=null) {
                        sectionDiagnostics.put(TimelineRoadPreparation.diagnostics(sectionResult)
                                .put("section_index",index).put("submitted_points",submitted)
                                .put("retry_reason","redundant_interior_fixes"));
                        int removedCount=submitted.length()-retry.length();
                        attempt.put("submitted_points",attempt.optInt("submitted_points")-removedCount)
                                .put("points_reduced",true);
                        submitted=retry;
                        payload=new JSONObject().put("points",submitted).put("road_recovery",true);
                        sectionResult=matcher!=null ? matcher.match(endpointPath,payload)
                                : postWithRetry(API_BASE_URL+endpointPath,payload);
                        road.details.put("redundant_fix_retry",true);
                    }
                    JSONObject integrity=DrivingJourneyPreparation.distanceIntegrity(sectionResult,submitted);
                    sectionDiagnostics.put(new JSONObject().put("section_index",index).put("integrity",integrity));
                    attempt.put("distance_integrity",integrity);
                    persistAttempt(journey,attempt);
                    DrivingJourneyPreparation.validateDistance(sectionResult,submitted);
                }
                sectionDiagnostics.put(TimelineRoadPreparation.diagnostics(sectionResult)
                        .put("section_index", index).put("submitted_points", submitted));
                // Persist before validation, including on failed or interrupted attempts.
                synchronized (JourneyStore.class) {
                    JSONObject stored = JourneyStore.get(app, journey.optString("journey_id"));
                    if (stored != null) { stored.put("last_match_attempt", attempt); JourneyStore.save(app, stored); }
                }
                JSONArray failures = sectionResult.optJSONArray("failed_sections");
                boolean capturedPartial = capturedDrive
                        && DrivingJourneyPreparation.supportedPartial(sectionResult, submitted);
                boolean supportedPartial = capturedPartial || (timelineImport
                        && TimelineRoadPreparation.importPartial(sectionResult, submitted));
                if (supportedPartial) {
                    sectionResult.put(capturedPartial ? "capture_partial_match" : "timeline_partial_match", true)
                            .put("partial_match_reason", capturedPartial ? "unmatched_capture_samples" : "incomplete_timeline_data");
                    if (!capturedPartial && TimelineRoadPreparation.endpointPartial(sectionResult, submitted)) sectionResult.put("endpoint_partial_match", true);
                    acceptedEndpointPartial = true; road.changed = true;
                    road.details.put(capturedPartial ? "capture_partial_match" : "timeline_partial_match", true);
                }
                if (!hasRoute(sectionResult)
                        || (!supportedPartial && shouldRejectMatchResult(sectionResult.optInt("matched_tracepoints", submitted.length()),
                            sectionResult.optInt("input_points", submitted.length()), failures == null ? 0 : failures.length()))) {
                    result = sectionResult;
                    attempt.put("failed_section_index", index);
                    acceptedEndpointPartial = false;
                    results.clear(); results.add(result); break;
                }
                results.add(sectionResult);
            }
            result = TimelineRoadPreparation.merge(results);
        }
        if (road != null && timelineImport && road.changed) {
            result.put("timeline_partial_match", true).put("partial_match_reason", "incomplete_timeline_data");
        }
        attempt.put("finished_at_utc", Instant.now().toString());
        if (result.has("input_points")) attempt.put("server_input_points", result.optInt("input_points"));
        if (result.has("matched_tracepoints")) attempt.put("server_matched_tracepoints", result.optInt("matched_tracepoints"));
        JSONArray failedSections = result.optJSONArray("failed_sections");
        int failedCount = failedSections == null ? 0 : failedSections.length();
        if (failedCount > 0) attempt.put("server_failed_sections", failedCount);
        if(foot && WalkingMatchOutcome.recordedOnly(result,requestPoints.length())) {
            synchronized(JourneyStore.class) {
                JSONObject stored=JourneyStore.get(app,journey.optString("journey_id"));
                if(stored==null || !journey.optString("mode").equals(stored.optString("mode"))
                        || !geometry.toString().equals(String.valueOf(stored.optJSONObject("route_geometry"))))
                    throw new IllegalStateException("Journey changed during matching · retry");
                stored.put("processing_status","complete").put("walking_outcome",result.getString("walking_outcome"))
                        .put("processing_result",result).put("last_match_attempt",attempt);
                for(String key:new String[]{"stage_statuses","processing"}) {
                    JSONObject stages=stored.optJSONObject(key);if(stages==null)stages=new JSONObject();
                    stages.put("foot_matching","complete");stored.put(key,stages);
                }
                if(walking!=null && walking.validated)WalkingArrivalTiming.apply(stored,walking.arrival);
                stored.remove("error_summary");stored.remove("match_retry_state");
                DiagnosticNotifications.resolved(app,stored.optString("journey_id"));stored.put("processing_completed_at",Instant.now().toString());
                JourneyStore.save(app,stored);
            }
            return;
        }
        if (!hasRoute(result)) throw new IllegalStateException("Matcher returned no route geometry");
        if (walking != null && walking.validated) {
            double matched = result.optDouble("matched_distance_m", -1);
            if (!Double.isFinite(matched) || matched <= 0
                    || matched > Math.max(walking.distance * 1.6, walking.distance + 300)) {
                throw new IllegalStateException("Matched walking route is substantially longer than the validated GPS evidence. The original recording is preserved.");
            }
            if (walking.details.optInt("vehicle_tail_points")>0)
                result.put("capture_partial_match",true).put("partial_match_reason","vehicle_tail_excluded");
        }
        if (cycle) {
            double matchedDistance = result.optDouble("matched_distance_m", -1);
            double recordedDistance = journey.optDouble("distance_meters", 0);
            if (!"cycling".equals(result.optString("matching_mode"))
                    || !result.optBoolean("matched_distance_is_deduplicated")
                    || !Double.isFinite(matchedDistance) || matchedDistance <= 0
                    || (recordedDistance > 0 && matchedDistance > Math.max(recordedDistance * 1.6, recordedDistance + 300)))
                throw new IllegalStateException("Cycling match has no reliable distance or exceeds the saved GPS evidence. Your original recording is preserved.");
        }
        int serverInput = result.optInt("input_points", points.length());
        int serverMatched = result.optInt("matched_tracepoints", serverInput);
        if (serverMatched < serverInput) attempt.put("partial_match", true);
        boolean walkingPartial=foot && WalkingMatchOutcome.verifiedPartial(result,requestPoints.length());
        if(walkingPartial) {
            double evidence=journey.optDouble("distance_meters",0);
            if(evidence<=0)for(int i=1;i<requestPoints.length();i++)evidence+=TimelineRoadPreparation.distance(requestPoints.getJSONObject(i-1),requestPoints.getJSONObject(i));
            walkingPartial=Double.isFinite(evidence) && evidence>0
                    && result.optDouble("matched_distance_m")<=Math.max(evidence*1.6,evidence+300);
            if(walkingPartial)result.put("walking_partial_match",true);
        }
        if (shouldRejectMatchResult(serverMatched, serverInput, failedCount) && !acceptedEndpointPartial && !walkingPartial) {
            synchronized (JourneyStore.class) {
                JSONObject stored = JourneyStore.get(app, journey.optString("journey_id"));
                if (stored != null) { stored.put("last_match_attempt", attempt); JourneyStore.save(app, stored); }
            }
            String reload = !foot && "timeline_import".equals(journey.optJSONObject("source") == null ? ""
                    : journey.optJSONObject("source").optString("type")) && !journey.has("timeline_match_evidence")
                    ? " Reload this journey using Utilities → Data management → Load Timeline Data to add its timing evidence." : " Rematch to try again.";
            throw new IllegalStateException("Partial route match: " + serverMatched + " of " + serverInput
                    + " GPS points matched; " + (serverInput-serverMatched) + " points remain unmatched"
                    + (failedCount>0 ? " across " + failedCount + " failed section(s)" : "") + "." + reload);
        }
        synchronized (JourneyStore.class) {
        JSONObject stored = JourneyStore.get(app, journey.optString("journey_id"));
        if (stored == null) throw new IllegalStateException("Journey is no longer available");
        if (!journey.optString("mode").equals(stored.optString("mode"))
                || !geometry.toString().equals(String.valueOf(stored.optJSONObject("route_geometry")))) {
            throw new IllegalStateException("Journey changed during matching · retry with its current route and mode");
        }
        String stage = matchingStage(mode);
        stored.put("processing_status", "complete");
        JSONObject stages = stored.optJSONObject("stage_statuses"); if (stages == null) stages = new JSONObject();
        stages.put(stage, "complete"); stored.put("stage_statuses", stages);
        JSONObject processing = stored.optJSONObject("processing"); if (processing == null) processing = new JSONObject();
        processing.put(stage, "complete"); stored.put("processing", processing);
        stored.put("processing_result", result);
        if(foot)stored.put("walking_outcome",walkingPartial?"partial":"matched");
        if (road != null && road.changed && !capturedDrive) {
            double distance = result.optDouble("matched_distance_m", -1);
            if (!Double.isFinite(distance) || distance <= 0
                    || !result.optBoolean("matched_distance_is_deduplicated", false))
                throw new IllegalStateException("The prepared road match has no valid distance. The original route is preserved.");
            if (!stored.has("original_distance_meters"))
                stored.put("original_distance_meters", stored.optDouble("distance_meters", 0));
            stored.put("road_preparation", road.details).put("distance_meters", distance)
                    .put("distance_source", "validated_road_sections");
        }
        if (capturedDrive) {
            stored.put("road_preparation",road.details);
            if(result.optBoolean("capture_partial_match")) {
                if(!stored.has("original_distance_meters"))stored.put("original_distance_meters",stored.optDouble("distance_meters",0));
                stored.put("distance_meters",result.getDouble("matched_distance_m"))
                        .put("distance_source","validated_road_sections");
            }
        }
        if (walking != null && walking.validated) {
            if (!stored.has("original_distance_meters"))
                stored.put("original_distance_meters", stored.optDouble("distance_meters", 0));
            if (!stored.has("raw_capture_samples") && walking.recoveredSamples.length() > 0)
                stored.put("raw_capture_samples", walking.recoveredSamples);
            WalkingArrivalTiming.apply(stored,walking.arrival);
            stored.put("walking_validation", walking.details);
            stored.put("matched_walking_distance_meters",result.getDouble("matched_distance_m"));
            if(!walkingPartial) {
                stored.put("distance_meters",result.getDouble("matched_distance_m"));
                stored.put("distance_source","validated_walking_match");
            }
        }
        if (cycle) {
            if (!stored.has("original_distance_meters")) stored.put("original_distance_meters", stored.optDouble("distance_meters", 0));
            stored.put("distance_meters", result.getDouble("matched_distance_m"));
            stored.put("distance_source", "validated_cycling_match");
        }
        stored.put("last_match_attempt", attempt);
        stored.put("processing_completed_at", Instant.now().toString());
        stored.remove("error_summary");stored.remove("match_retry_state");
        DiagnosticNotifications.resolved(app,stored.optString("journey_id"));
        JourneyStore.save(app, stored);
        JSONObject replaySource=stored.optJSONObject("source");
        if(replaySource!=null&&"android_activity_capture".equals(replaySource.optString("type")))
            DiscoveryReplayCache.request(app,java.util.Collections.singleton(stored.optString("journey_id")),null);
        }
    }

    static boolean shouldRejectMatchResult(int matchedPoints, int inputPoints, int failedSections) {
        return failedSections > 0 || inputPoints < 2 || matchedPoints != inputPoints;
    }

    private void markFailed(String journeyId, Exception error, JSONObject expected,String trigger,String worker) {
        if(expected==null)return; // No matching attempt owns this saved revision.
        try {
            synchronized (JourneyStore.class) {
            JSONObject stored = JourneyStore.get(app, journeyId);
            if (stored == null) return;
            if(expected!=null && (!expected.optString("mode").equals(stored.optString("mode"))
                    || !String.valueOf(expected.optJSONObject("route_geometry")).equals(String.valueOf(stored.optJSONObject("route_geometry")))
                    || !MatchRetryPolicy.input(expected).equals(MatchRetryPolicy.input(stored))))return;
            String stage = matchingStage(stored.optString("mode", "unknown"));
            stored.put("processing_status", "failed"); stored.put("error_summary", safeMessage(error));
            JSONObject attempt = stored.optJSONObject("last_match_attempt");
            if (attempt != null) {
                attempt.put("finished_at_utc", Instant.now().toString());
                attempt.put("error", safeMessage(error)).put("failure_category",MatchFailure.category(error))
                        .put("retryable",MatchFailure.retryable(error));
                if(error instanceof MatchFailure){
                    MatchFailure failure=(MatchFailure)error;attempt.put("http_status",failure.status);
                    if(failure.retryAfterSeconds>0)attempt.put("retry_after_seconds",failure.retryAfterSeconds)
                            .put("next_retry_at_utc",Instant.now().plusSeconds(failure.retryAfterSeconds).toString());
                }
                stored.put("last_match_attempt", attempt);
            }
            JSONObject stages = stored.optJSONObject("stage_statuses"); if (stages == null) stages = new JSONObject();
            stages.put(stage, "failed"); stored.put("stage_statuses", stages);
            JSONObject processing = stored.optJSONObject("processing"); if (processing == null) processing = new JSONObject();
            processing.put(stage, "failed"); stored.put("processing", processing);
            if(MatchRetryPolicy.qualityFailure(error))MatchRetryPolicy.reject(stored,Instant.now().toString());
            else stored.remove("match_retry_state");
            JourneyStore.save(app, stored);
            CrashReporter.recordMatchFailure(app, stored, error,trigger,worker);
            }
        } catch (Exception ignored) { }
    }

    private void persistAttempt(JSONObject journey,JSONObject attempt) throws Exception {
        synchronized(JourneyStore.class) {
            JSONObject saved=JourneyStore.get(app,journey.optString("journey_id"));
            if(saved!=null && saved.optString("mode").equals(journey.optString("mode"))) {
                saved.put("last_match_attempt",attempt);JourneyStore.save(app,saved);
            }
        }
    }
    static double traceDistance(JSONArray samples) {
        double distance=0;
        if(samples!=null)for(int i=1;i<samples.length();i++) {
            JSONArray a=samples.optJSONArray(i-1),b=samples.optJSONArray(i);
            if(a!=null&&b!=null)distance+=RailStationCatalog.metres(a.optDouble(1),a.optDouble(0),b.optDouble(1),b.optDouble(0));
        }
        return Double.isFinite(distance)?distance:0;
    }
    private static double pointDistance(JSONArray points) throws Exception {
        double d=0;for(int i=1;i<points.length();i++)d+=TimelineRoadPreparation.distance(points.getJSONObject(i-1),points.getJSONObject(i));return d;
    }
    private static double requestDistance(List<JSONArray> requests) throws Exception {
        double d=0;for(JSONArray points:requests)d+=pointDistance(points);return d;
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
        // Allow time for the free API instance to wake after an idle period.
        long[] delays = {3_000L};
        for (int attempt = 0; ; attempt++) try {
            return post(endpoint, payload);
        } catch (Exception error) {
            if (!MatchFailure.retryNow(error,attempt)) throw error;
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
            throw new MatchFailure(status,CrashReporter.summarizeMatcherError(
                    status, body.toString()),MatchFailure.retryAfter(connection.getHeaderField("Retry-After")));
        }
        return new JSONObject(body.toString());
        } finally { connection.disconnect(); }
    }

    private boolean canMatch(JSONObject j) {
        String mode = j.optString("mode", "unknown");
        if (!isFoot(mode) && !isRoad(mode) && !isCycle(mode)) return false;
        JSONObject geometry = j.optJSONObject("route_geometry");
        JSONArray points = geometry == null ? null : geometry.optJSONArray("coordinates");
        if (points == null || points.length() < 2) return false;
        if (isRoad(mode) || isCycle(mode)) {
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
    static boolean isCycle(String mode) { return "cycling".equals(mode) || "bicycle".equals(mode); }
    static String endpointForMode(String mode) {
        if (isCycle(mode)) return "/match-cycling";
        if ("walking".equals(mode) || "running".equals(mode) || "pedestrian".equals(mode)) return "/match-walking";
        return "/match";
    }
    static String matchingStage(String mode) {
        if (isCycle(mode)) return "cycle_matching";
        return "/match-walking".equals(endpointForMode(mode)) ? "foot_matching" : "road_matching";
    }
    static boolean isFoot(String mode) { return "walking".equals(mode) || "running".equals(mode) || "pedestrian".equals(mode); }
    static boolean isRoad(String mode) { return "driving".equals(mode) || "bus".equals(mode); }
    private String safeMessage(Exception error) { return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage(); }
}

