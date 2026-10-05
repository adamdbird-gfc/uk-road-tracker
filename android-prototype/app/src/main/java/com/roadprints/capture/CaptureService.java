package com.roadprints.capture;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.util.AtomicFile;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import com.google.android.gms.location.ActivityRecognition;
import com.google.android.gms.location.ActivityRecognitionClient;
import com.google.android.gms.location.ActivityTransition;
import com.google.android.gms.location.ActivityTransitionRequest;
import com.google.android.gms.location.DetectedActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CaptureService extends Service {
    public static final String ACTION_START = "com.roadprints.capture.START";
    public static final String ACTION_RESUME = "com.roadprints.capture.RESUME";
    public static final String ACTION_STOP = "com.roadprints.capture.STOP";
    public static final String ACTION_ARM = "com.roadprints.capture.ARM";
    public static final String ACTION_DISARM = "com.roadprints.capture.DISARM";
    public static final String ACTION_ACTIVITY = "com.roadprints.capture.ACTIVITY";
    public static final String ACTION_UPDATE = "com.roadprints.capture.UPDATE";
    public static final String ACTION_DIAGNOSTICS_CHANGED = "com.roadprints.capture.DIAGNOSTICS_CHANGED";
    public static final String EXTRA_MODE = "mode";
    public static final String EXTRA_ACTIVE = "active";
    public static final String EXTRA_ARMED = "armed";
    public static final String EXTRA_DISTANCE = "distance_meters";
    public static final String EXTRA_POINTS = "points";
    public static final String EXTRA_MESSAGE = "message";
    public static final String EXTRA_ACTIVITY_TYPE = "activity_type";
    public static final String EXTRA_CONFIDENCE = "confidence";
    public static final String EXTRA_TRANSITION = "transition";

    private static final String STATE_PREFS = "roadprints_capture_state";
    private static final String STATE_ACTIVE = "active";
    private static final String STATE_MODE = "mode";
    private static final String STATE_ARMED = "armed";
    private static final String CHANNEL_ID = "roadprints_recording";
    private static final int NOTIFICATION_ID = 41;
    private static final long STILLNESS_END_THRESHOLD_MS = CaptureStartGate.STILLNESS_END_THRESHOLD_MS;
    private static final long MODE_CHANGE_CONFIRMATION_MS = 15_000L;
    private static final long CHECKPOINT_INTERVAL_MS = 15_000L;
    private static final float STILLNESS_MOVEMENT_THRESHOLD_METRES = 35f;
    static final long ROAD_CAPTURE_INTERVAL_MS = 8_000L;
    static final long WALK_CAPTURE_INTERVAL_MS = 10_000L;
    static final float ROAD_CAPTURE_MIN_DISTANCE_METRES = 20f;
    static final float WALK_CAPTURE_MIN_DISTANCE_METRES = 10f;

    private LocationManager locationManager;
    private ActivityRecognitionClient activityClient;
    private PendingIntent activityPendingIntent;
    private final List<Location> points = new ArrayList<>();
    private final List<Location> candidatePoints = new ArrayList<>();
    private Location candidateOrigin;
    private Location candidateMovementLast;
    private float candidateMovementMetres;
    private Location departureAnchor;
    private long candidateStartedAtMs;
    private long candidateStationarySince;
    private Location candidateStationaryAnchor;
    private String candidateStartedAt;
    private String candidateMode;
    private Location lastPoint;
    private String journeyId;
    private String startedAt;
    private String mode;
    private double distanceMetres;
    private long stationarySince;
    private Location stationaryAnchor;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService captureIo = Executors.newSingleThreadExecutor();
    private boolean captureFinishing;
    private boolean checkpointPending;
    private boolean diagnosticUpdatesRegistered;
    private final Runnable diagnosticWindowExpiry = () -> {
        MovementDiagnostics.isRunning(this);
        refreshDiagnosticLocationUpdates();
    };
    private String nextCaptureMode;
    private String pendingActivityMode;
    private RailStationCatalog stationCatalog;
    private StationJourneyModel stationModel;
    private String currentActivity = "unknown";
    private String stationEvidenceReason, stationEvidenceCode;
    private List<Location> nextCapturePoints;
    private String nextCaptureStartedAt;
    private int stationEndIndex = -1;
    private boolean automaticCapture;

    private final Runnable confirmActivityModeChange = this::commitPendingActivityMode;
    private AtomicFile checkpointFile;
    private final Runnable finishIfStill = () -> {
        if (isActive(this) && stationarySince != 0
                && CaptureStartGate.shouldEndAfterStillness(System.currentTimeMillis() - stationarySince)) {
            if (stationModel != null && stationModel.holdStill(mode, System.currentTimeMillis())) {
                handler.postDelayed(this.finishIfStill, 30_000L);
            } else {
                if (stationModel!=null && stationModel.stationaryEndIndex(mode)>=1) {
                    stationEndIndex=stationModel.stationaryEndIndex(mode);
                    stationEvidenceReason="station_arrival_waiting";
                    stationEvidenceCode=stationModel.stationCode();
                }
                finishCapture();
            }
        }
    };
    private final Runnable candidateTimeout = () -> {
        if (candidateMode != null) cancelStartCandidate("Movement was too short to save as a journey.");
    };
    private final Runnable candidateStillTimeout = () -> {
        if (candidateMode != null && candidateStationarySince != 0
                && CaptureStartGate.shouldEndAfterStillness(System.currentTimeMillis() - candidateStationarySince)) {
            cancelStartCandidate("The stop lasted too long to count as the start of a journey.");
        }
    };

    private final LocationListener locationListener = new LocationListener() {
        @Override
        public void onLocationChanged(Location location) {
            if (!isActive(CaptureService.this)) {
                if (candidateMode != null) {
                    MovementDiagnostics.recordLocation(CaptureService.this, location, "walk_candidate");
                    collectCandidateLocation(location);
                } else {
                    MovementDiagnostics.recordLocation(CaptureService.this, location, "armed_waiting_for_movement");
                }
                return;
            }
            MovementDiagnostics.recordLocation(CaptureService.this, location, "recording_" + mode);
            if (lastPoint != null) distanceMetres += lastPoint.distanceTo(location);
            lastPoint = location;
            points.add(location);
            clearStillnessIfMovementContinues(location);
            evaluateStationLocation(location, points.size() - 1);
            if (captureFinishing) return;
            updateNotification();
            broadcastUpdate("Recording " + mode + " locally...");
        }

        @Override public void onProviderEnabled(String provider) {
            MovementDiagnostics.recordEvent(CaptureService.this, "location_provider_enabled", provider);
        }
        @Override public void onProviderDisabled(String provider) {
            MovementDiagnostics.recordEvent(CaptureService.this, "location_provider_disabled", provider);
        }
    };

    public static boolean isActive(Context context) {
        return context.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE)
                .getBoolean(STATE_ACTIVE, false);
    }

    public static boolean isArmed(Context context) {
        return context.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE)
                .getBoolean(STATE_ARMED, false);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        checkpointFile = new AtomicFile(new File(getFilesDir(), "roadprints_active_capture.json"));
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        activityClient = ActivityRecognition.getClient(this);
        createNotificationChannel();
        restoreDepartureAnchor();
        if (isActive(this)) restoreActiveCapture();
        captureIo.execute(() -> {
            try (InputStream input = getAssets().open("rail-stations/stations.csv")) {
                RailStationCatalog loaded = RailStationCatalog.read(new InputStreamReader(input, StandardCharsets.UTF_8));
                handler.post(() -> {
                    stationCatalog = loaded;
                    resetStationModel();
                    if (isActive(this) && lastPoint != null) evaluateStationLocation(lastPoint, points.size()-1);
                    MovementDiagnostics.recordEvent(this, "station_reference_ready", "Bundled station reference loaded locally.");
                });
            } catch (Exception error) {
                MovementDiagnostics.recordEvent(this, "station_reference_unavailable", error.getClass().getSimpleName());
            }
        });
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            stopSelf(startId);
            return START_NOT_STICKY;
        }

        String action = intent.getAction();
        if (ACTION_STOP.equals(action)) {
            cancelStartCandidate(null);
            nextCaptureMode = null;
            nextCapturePoints = null;
            nextCaptureStartedAt = null;
            stationEndIndex = -1;
            clearPendingActivityMode();
            finishCapture();
        } else if (ACTION_START.equals(action)) {
            startCapture(intent.getStringExtra(EXTRA_MODE));
        } else if (ACTION_ARM.equals(action)) {
            armTracking();
        } else if (ACTION_DISARM.equals(action)) {
            disarmTracking();
        } else if (ACTION_DIAGNOSTICS_CHANGED.equals(action)) {
            if (isArmed(this)) startForegroundWithNotification();
            refreshDiagnosticLocationUpdates();
        } else if (ACTION_ACTIVITY.equals(action)) {
            if (!isArmed(this) && !isActive(this)) {
                // A transition can race with disarming or arrive after an old subscription fires.
                // Since this request came from startForegroundService(), stop promptly when idle.
                stopSelf(startId);
                return START_NOT_STICKY;
            }
            // Promote before processing the event. Candidate detection may wait for GPS points
            // and previously left this startForegroundService() request unpromoted.
            startForegroundWithNotification();
            handleTransition(
                    intent.getIntExtra(EXTRA_ACTIVITY_TYPE, DetectedActivity.UNKNOWN),
                    intent.getIntExtra(EXTRA_TRANSITION, -1));
        } else if (ACTION_RESUME.equals(action) && !isActive(this)) {
            broadcastUpdate("No recoverable active journey was found.");
        }
        return START_NOT_STICKY;
    }

    private void armTracking() {
        // Re-register even when the preference says armed; this also repairs a
        // subscription after Android has recreated the service.
        if (isArmed(this)) {
            removeActivityUpdates();
        }

        getSharedPreferences(STATE_PREFS, MODE_PRIVATE).edit()
                .putBoolean(STATE_ARMED, true)
                .apply();
        startForegroundWithNotification();
        MovementDiagnostics.recordEvent(this, "tracking_armed", "Automatic tracking enabled.");
        refreshDiagnosticLocationUpdates();

        try {
            activityPendingIntent = PendingIntent.getBroadcast(
                    this,
                    42,
                    new Intent(this, ActivityRecognitionReceiver.class),
                    PendingIntent.FLAG_UPDATE_CURRENT
                            | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                            ? PendingIntent.FLAG_MUTABLE : 0));
            ActivityTransitionRequest request = new ActivityTransitionRequest(Arrays.asList(
                    transition(DetectedActivity.STILL),
                    transition(DetectedActivity.WALKING),
                    transition(DetectedActivity.ON_FOOT),
                    transition(DetectedActivity.RUNNING),
                    transition(DetectedActivity.ON_BICYCLE),
                    transition(DetectedActivity.IN_VEHICLE)));
            activityClient.requestActivityTransitionUpdates(request, activityPendingIntent)
                    .addOnSuccessListener(unused ->
                            broadcastUpdate("Activity transition subscription confirmed. Waiting for movement..."))
                    .addOnFailureListener(error -> {
                        getSharedPreferences(STATE_PREFS, MODE_PRIVATE).edit()
                                .putBoolean(STATE_ARMED, false).apply();
                        broadcastUpdate("Activity transition failed: "
                                + (error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage()));
                        stopForeground(STOP_FOREGROUND_REMOVE);
                        stopSelf();
                    });
        } catch (SecurityException error) {
            getSharedPreferences(STATE_PREFS, MODE_PRIVATE).edit()
                    .putBoolean(STATE_ARMED, false).apply();
            broadcastUpdate("Activity recognition permission is required.");
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
        }
    }

    private void disarmTracking() {
        MovementDiagnostics.recordEvent(this, "tracking_disarmed", "Automatic tracking disabled.");
        cancelStartCandidate(null);
        nextCaptureMode = null;
        clearPendingActivityMode();
        getSharedPreferences(STATE_PREFS, MODE_PRIVATE).edit()
                .putBoolean(STATE_ARMED, false).apply();
        removeActivityUpdates();
        handler.removeCallbacks(finishIfStill);
        if (isActive(this)) {
            finishCapture();
        } else {
            broadcastUpdate("Automatic tracking stopped.");
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
        }
    }

    private ActivityTransition transition(int activityType) {
        return new ActivityTransition.Builder()
                .setActivityType(activityType)
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                .build();
    }

    private void handleTransition(int activityType, int transitionType) {
        if (!isArmed(this)) return;

        String activityLabel = activityLabel(activityType);
        boolean entering = transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER;
        MovementDiagnostics.recordActivity(this, activityLabel, entering ? "entered" : "left",
                isArmed(this), isActive(this), mode);
        broadcastUpdate("Android detected " + (entering ? "entered " : "left ")
                + activityLabel + ".");

        if (entering) {
            currentActivity = activityType == DetectedActivity.IN_VEHICLE ? "vehicle"
                    : (activityType == DetectedActivity.STILL ? "still"
                    : (modeForActivity(activityType) != null && "walking".equals(modeForActivity(activityType)) ? "walking" : "unknown"));
            if (stationModel != null) stationModel.activity(currentActivity, System.currentTimeMillis(), points.size()-1);
        }
        if (!entering) {
            if (activityType == DetectedActivity.STILL) {
                stationarySince = 0;
                stationaryAnchor = null;
                handler.removeCallbacks(finishIfStill);
            }
            return;
        }

        if (activityType == DetectedActivity.STILL) {
            clearPendingActivityMode();
            if (!isActive(this) && candidateMode != null) {
                candidateStationarySince = System.currentTimeMillis();
                candidateStationaryAnchor = candidatePoints.isEmpty()
                        ? (candidateOrigin == null ? null : new Location(candidateOrigin))
                        : new Location(candidatePoints.get(candidatePoints.size() - 1));
                handler.removeCallbacks(candidateStillTimeout);
                handler.postDelayed(candidateStillTimeout, CaptureStartGate.CANDIDATE_STILL_CANCEL_MS);
                broadcastUpdate("Brief stop detected; keeping the movement check open...");
            }
            if (isActive(this)) {
                if (stationarySince == 0) {
                    stationarySince = System.currentTimeMillis();
                    stationaryAnchor = lastPoint == null ? null : new Location(lastPoint);
                }
                handler.removeCallbacks(finishIfStill);
                handler.postDelayed(finishIfStill, Math.max(1L, STILLNESS_END_THRESHOLD_MS
                        - (System.currentTimeMillis()-stationarySince)));
                broadcastUpdate("Stationary detected; checking for a genuine stop for 5 minutes...");
            }
            return;
        }

        stationarySince = 0;
        stationaryAnchor = null;
        handler.removeCallbacks(finishIfStill);
        clearCandidateStillness();
        String detectedMode = modeForActivity(activityType);
        if (detectedMode != null) {
            if (!isActive(this)) {
                beginStartCandidate(detectedMode);
            } else if (stationModel != null && automaticCapture && stationModel.ownsModeChange(mode)) {
                clearPendingActivityMode();
                broadcastUpdate("Checking station arrival or departure before changing the journey leg...");
            } else if (detectedMode.equals(mode)) {
                clearPendingActivityMode();
            } else {
                pendingActivityMode = detectedMode;
                handler.removeCallbacks(confirmActivityModeChange);
                handler.postDelayed(confirmActivityModeChange, MODE_CHANGE_CONFIRMATION_MS);
                broadcastUpdate("Checking the change to " + detectedMode
                        + " before starting a new journey leg...");
            }
        }
    }

    private void clearPendingActivityMode() {
        pendingActivityMode = null;
        handler.removeCallbacks(confirmActivityModeChange);
    }

    private void commitPendingActivityMode() {
        String confirmedMode = pendingActivityMode;
        if (confirmedMode == null || !isActive(this) || confirmedMode.equals(mode)) {
            clearPendingActivityMode();
            return;
        }
        if (captureFinishing) {
            handler.postDelayed(confirmActivityModeChange, 1_000L);
            return;
        }
        if (stationModel != null && automaticCapture && stationModel.ownsModeChange(mode)) {
            clearPendingActivityMode();
            return;
        }
        clearPendingActivityMode();
        if (points.size() < 2) {
            broadcastUpdate("Activity changed, but this segment is too short to split safely.");
            return;
        }
        // IN_VEHICLE cannot distinguish trains from road vehicles. Save that leg
        // as Unknown so it can be classified in Journeys without road matching.
        nextCaptureMode = confirmedMode;
        int boundary = Math.max(1, points.size()-1);
        prepareStationSplit(boundary, boundary, confirmedMode);
        finishCapture();
        broadcastUpdate("Activity change confirmed; saving this journey leg...");
    }

    private void clearStillnessIfMovementContinues(Location location) {
        if (stationarySince == 0 || stationaryAnchor == null
                || location == null || !location.hasAccuracy()
                || location.getAccuracy() > 50f) {
            return;
        }
        float accuracyAllowance = location.getAccuracy();
        if (stationaryAnchor.hasAccuracy()) {
            accuracyAllowance += stationaryAnchor.getAccuracy();
        } else {
            accuracyAllowance += 15f;
        }
        float threshold = Math.max(STILLNESS_MOVEMENT_THRESHOLD_METRES, accuracyAllowance);
        if (stationaryAnchor.distanceTo(location) < threshold) return;

        stationarySince = 0;
        stationaryAnchor = null;
        handler.removeCallbacks(finishIfStill);
        broadcastUpdate("GPS shows movement continuing; keeping the journey open.");
    }

    private String activityLabel(int activityType) {
        if (activityType == DetectedActivity.STILL) return "still";
        if (activityType == DetectedActivity.IN_VEHICLE) return "in a vehicle";
        if (activityType == DetectedActivity.ON_BICYCLE) return "cycling";
        if (activityType == DetectedActivity.WALKING) return "walking";
        if (activityType == DetectedActivity.ON_FOOT) return "on foot";
        if (activityType == DetectedActivity.RUNNING) return "running";
        return "unknown movement";
    }

    private String modeForActivity(int activityType) {
        // Android's activity API reports both trains and road vehicles as
        // IN_VEHICLE. Keep this unclassified until the user confirms transport.
        if (activityType == DetectedActivity.IN_VEHICLE) return "unknown";
        if (activityType == DetectedActivity.ON_BICYCLE) return "cycling";
        if (activityType == DetectedActivity.WALKING
                || activityType == DetectedActivity.ON_FOOT
                || activityType == DetectedActivity.RUNNING) {
            return "walking";
        }
        return null;
    }

    private void startCapture(String requestedMode) {
        if (isActive(this)) return;
        cancelStartCandidate(null);
        clearDepartureAnchor();
        automaticCapture = false;
        activateCapture(requestedMode, Instant.now().toString(), null);
    }

    private void activateCapture(String requestedMode, String captureStartedAt, List<Location> initialPoints) {
        if (isActive(this)) return;
        mode = requestedMode == null ? "driving" : requestedMode;
        diagnosticUpdatesRegistered = false;
        MovementDiagnostics.recordEvent(this, "journey_capture_started", "Mode: " + mode);
        journeyId = UUID.randomUUID().toString();
        startedAt = captureStartedAt == null ? Instant.now().toString() : captureStartedAt;
        distanceMetres = 0;
        lastPoint = null;
        stationarySince = 0;
        stationaryAnchor = null;
        points.clear();
        if (initialPoints != null) {
            for (Location point : initialPoints) {
                Location copy = new Location(point);
                if (lastPoint != null) distanceMetres += lastPoint.distanceTo(copy);
                points.add(copy);
                lastPoint = copy;
            }
        }
        resetStationModel();
        getSharedPreferences(STATE_PREFS, MODE_PRIVATE).edit()
                .putBoolean(STATE_ACTIVE, true).putString(STATE_MODE, mode).apply();
        startForegroundWithNotification();
        try {
            requestCaptureLocationUpdates();
            scheduleCaptureCheckpoint();
            broadcastUpdate("Confirmed " + mode + " journey; recording locally...");
        } catch (SecurityException error) {
            getSharedPreferences(STATE_PREFS, MODE_PRIVATE).edit().putBoolean(STATE_ACTIVE, false).apply();
            broadcastUpdate("Location permission is required to record.");
            if (!isArmed(this)) { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(); }
        }
    }

    private void beginStartCandidate(String requestedMode) {
        if (isActive(this) || captureFinishing || requestedMode == null) return;
        if (requestedMode.equals(candidateMode)) return;
        cancelStartCandidate(null);
        candidateMode = requestedMode;
        diagnosticUpdatesRegistered = false;
        candidateMovementMetres = 0f;
        candidateMovementLast = null;
        candidateStartedAtMs = System.currentTimeMillis();
        MovementDiagnostics.recordEvent(this, "journey_candidate_started", "Mode: " + requestedMode);
        candidateStationarySince = 0;
        candidateStationaryAnchor = null;
        candidateStartedAt = Instant.now().toString();
        candidatePoints.clear();
        candidateOrigin = recentLastKnownLocation();
        candidateMovementMetres = 0f;
        candidateMovementLast = candidateOrigin == null ? null : new Location(candidateOrigin);
        if (candidateOrigin == null && departureAnchor != null) {
            candidateOrigin = new Location(departureAnchor);
            candidatePoints.add(new Location(departureAnchor));
        } else if (candidateOrigin != null) candidatePoints.add(new Location(candidateOrigin));
        try {
            requestLocationUpdatesForMode(candidateMode, true);
            handler.removeCallbacks(candidateTimeout);
            handler.postDelayed(candidateTimeout, CaptureStartGate.START_CANDIDATE_TIMEOUT_MS);
            broadcastUpdate("Checking movement before saving a journey...");
        } catch (SecurityException error) {
            cancelStartCandidate("Location permission is required to confirm movement.");
        }
    }

    @SuppressLint("MissingPermission")
    private Location recentLastKnownLocation() {
        try {
            Location gps = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            Location network = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            Location best = gps;
            if (network != null && (best == null || network.getTime() > best.getTime())) best = network;
            if (best == null || !best.hasAccuracy() || best.getAccuracy() > 50f
                    || System.currentTimeMillis() - best.getTime() > 60_000L) return null;
            return new Location(best);
        } catch (SecurityException ignored) { return null; }
    }

    private void collectCandidateLocation(Location location) {
        if (candidateMode == null || location == null || !location.hasAccuracy()
                || location.getAccuracy() > 50f) return;
        if (candidateStationarySince != 0 && candidateStationaryAnchor != null) {
            float allowance = location.getAccuracy()
                    + (candidateStationaryAnchor.hasAccuracy() ? candidateStationaryAnchor.getAccuracy() : 15f);
            if (candidateStationaryAnchor.distanceTo(location) >= Math.max(STILLNESS_MOVEMENT_THRESHOLD_METRES, allowance)) {
                clearCandidateStillness();
            }
        }
        if (candidateOrigin == null) {
            candidateOrigin = new Location(location);
            candidateMovementLast = new Location(location);
        }
        if (candidateMovementLast != null) {
            float stepMetres = candidateMovementLast.distanceTo(location);
            float previousAccuracy = candidateMovementLast.hasAccuracy()
                    ? candidateMovementLast.getAccuracy() : 15f;
            // Credit only movement beyond GPS uncertainty so small location jitter
            // does not accumulate into a false journey start.
            candidateMovementMetres += CaptureStartGate.reliableMovementIncrement(
                    stepMetres, previousAccuracy, location.getAccuracy());
        }
        candidateMovementLast = new Location(location);
        if (candidatePoints.isEmpty()
                || candidatePoints.get(candidatePoints.size() - 1).distanceTo(location) >= 5f) {
            candidatePoints.add(new Location(location));
            if (candidatePoints.size() > 48) candidatePoints.remove(0);
        }
        float fromOrigin = candidateOrigin.distanceTo(location);
        float fromStop = departureAnchor == null ? Float.MAX_VALUE : departureAnchor.distanceTo(location);
        boolean walking = "walking".equals(candidateMode);
        MovementDiagnostics.recordEvent(this, "journey_candidate_sample",
                "mode=" + candidateMode
                        + "; elapsed_ms=" + (System.currentTimeMillis() - candidateStartedAtMs)
                        + "; path_m=" + Math.round(candidateMovementMetres)
                        + "; straight_line_m=" + Math.round(fromOrigin)
                        + "; accuracy_m=" + Math.round(location.getAccuracy())
                        + "; previous_stop_distance_m=" + (departureAnchor == null ? "none" : Math.round(fromStop))
                        + "; previous_stop_radius_enforced=" + (!walking && departureAnchor != null)
                        + "; required_path_m=" + Math.round(CaptureStartGate.minimumMovementMetres(candidateMode)));
        float confirmedMovement = walking ? candidateMovementMetres : fromOrigin;
        boolean requireDepartureRadius = !walking && departureAnchor != null;
        if (!CaptureStartGate.shouldConfirmStart(candidateMode,
                System.currentTimeMillis() - candidateStartedAtMs,
                confirmedMovement, requireDepartureRadius, fromStop)) return;
        String confirmedMode = candidateMode;
        String confirmedStart = candidateStartedAt;
        MovementDiagnostics.recordEvent(this, "journey_candidate_confirmed",
                "Mode: " + confirmedMode + "; uncertainty-adjusted path distance reached start threshold.");
        List<Location> seedPoints = new ArrayList<>(candidatePoints);
        cancelStartCandidate(null);
        clearDepartureAnchor();
        automaticCapture = true;
        activateCapture(confirmedMode, confirmedStart, seedPoints);
    }

    private void clearCandidateStillness() {
        candidateStationarySince = 0;
        candidateStationaryAnchor = null;
        handler.removeCallbacks(candidateStillTimeout);
    }

    private void cancelStartCandidate(String message) {
        handler.removeCallbacks(candidateTimeout);
        handler.removeCallbacks(candidateStillTimeout);
        clearCandidateStillness();
        if (candidateMode == null) return;
        candidateMode = null;
        candidateStartedAt = null;
        candidateStartedAtMs = 0;
        candidateOrigin = null;
        candidateMovementLast = null;
        candidateMovementMetres = 0f;
        candidatePoints.clear();
        if (!isActive(this)) locationManager.removeUpdates(locationListener);
        if (message != null && isArmed(this)) {
            MovementDiagnostics.recordEvent(this, "journey_candidate_rejected", message);
            broadcastUpdate(message);
        }
        if (isArmed(this) && MovementDiagnostics.isRunning(this)) requestDiagnosticLocationUpdates();
    }

    private void restoreDepartureAnchor() {
        SharedPreferences preferences = getSharedPreferences(STATE_PREFS, MODE_PRIVATE);
        if (!preferences.contains("departure_lat_e7") || !preferences.contains("departure_lng_e7")) return;
        Location anchor = new Location("roadprints_stop");
        anchor.setLatitude(preferences.getLong("departure_lat_e7", 0L) / 10_000_000.0);
        anchor.setLongitude(preferences.getLong("departure_lng_e7", 0L) / 10_000_000.0);
        anchor.setAccuracy(preferences.getFloat("departure_accuracy", 25f));
        departureAnchor = anchor;
    }

    private void storeDepartureAnchor(Location anchor) {
        departureAnchor = anchor == null ? null : new Location(anchor);
        SharedPreferences.Editor editor = getSharedPreferences(STATE_PREFS, MODE_PRIVATE).edit();
        if (anchor == null) {
            editor.remove("departure_lat_e7").remove("departure_lng_e7").remove("departure_accuracy");
        } else {
            editor.putLong("departure_lat_e7", Math.round(anchor.getLatitude() * 10_000_000.0))
                    .putLong("departure_lng_e7", Math.round(anchor.getLongitude() * 10_000_000.0))
                    .putFloat("departure_accuracy", anchor.hasAccuracy() ? anchor.getAccuracy() : 25f);
        }
        editor.apply();
    }

    private void clearDepartureAnchor() { storeDepartureAnchor(null); }

    private JSONArray restoredStationState;

    private void resetStationModel() {
        stationModel = stationCatalog == null || !automaticCapture ? null : new StationJourneyModel(stationCatalog);
        if (stationModel == null) return;
        if (restoredStationState != null) {
            String[] state = new String[restoredStationState.length()];
            for (int i=0;i<state.length;i++) state[i]=restoredStationState.optString(i);
            stationModel.restore(state, points.size());
            restoredStationState = null;
            currentActivity = stationModel.currentActivity();
        } else {
            // Seed station origin from reliable retained candidate points, without making a split.
            for (int i=0;i<points.size();i++) {
                Location point=points.get(i);
                stationModel.sample(mode,i,point.getTime(),point.getLatitude(),point.getLongitude(),
                        point.hasAccuracy()?point.getAccuracy():Float.POSITIVE_INFINITY,
                        point.hasSpeed()?point.getSpeed():-1);
            }
            stationModel.activity(currentActivity,System.currentTimeMillis(),Math.max(0,points.size()-1));
        }
    }

    private void evaluateStationLocation(Location location, int index) {
        if (!automaticCapture || stationModel==null || captureFinishing) return;
        StationJourneyModel.Decision decision=stationModel.sample(mode,index,location.getTime(),
                location.getLatitude(),location.getLongitude(),
                location.hasAccuracy()?location.getAccuracy():Float.POSITIVE_INFINITY,
                location.hasSpeed()?location.getSpeed():-1);
        if (decision==null) return;
        stationEvidenceReason=decision.reason; stationEvidenceCode=decision.stationCode;
        MovementDiagnostics.recordEvent(this,"station_transition_confirmed",
                "reason="+decision.reason+"; station="+decision.stationCode+"; next_mode="+decision.nextMode);
        clearPendingActivityMode();
        if (decision.endIndex<0) {
            mode=decision.nextMode;
            getSharedPreferences(STATE_PREFS,MODE_PRIVATE).edit().putString(STATE_MODE,mode).apply();
            return;
        }
        prepareStationSplit(decision.endIndex,decision.startIndex,decision.nextMode);
        finishCapture();
    }

    private void prepareStationSplit(int endIndex,int startIndex,String continueMode) {
        if (points.size()<2) return;
        stationEndIndex=Math.max(1,Math.min(endIndex,points.size()-1));
        int first=Math.max(1,Math.min(startIndex,points.size()-1));
        nextCapturePoints=new ArrayList<>();
        for (Location point:points.subList(first,points.size())) nextCapturePoints.add(new Location(point));
        nextCaptureStartedAt=Instant.ofEpochMilli(nextCapturePoints.get(0).getTime()).toString();
        nextCaptureMode=continueMode;
    }

    private void finishCapture() {
        if (!isActive(this)) {
            if (!isArmed(this)) stopSelf();
            return;
        }
        if (captureFinishing) return;

        captureFinishing = true;
        handler.removeCallbacks(checkpointCapture);
        locationManager.removeUpdates(locationListener);

        // Snapshot the small live-capture state quickly on the service thread.
        // JourneyStore.save is synchronized with archive reads, so serializing and
        // saving here could block Android's main thread while another screen scans
        // a large journey archive.
        final List<Location> fullCapturePoints = new ArrayList<>(points);
        final boolean stationBoundary = stationEndIndex >= 1 && stationEndIndex < points.size();
        final List<Location> savedPoints = new ArrayList<>(stationBoundary
                ? points.subList(0, stationEndIndex + 1) : points);
        final String savedStationReason = stationEvidenceReason;
        final String savedStationCode = stationEvidenceCode;
        final String savedJourneyId = journeyId;
        final String savedStartedAt = startedAt;
        final String savedMode = mode;
        MovementDiagnostics.recordEvent(this, "journey_capture_finishing",
                "Mode: " + savedMode + "; GPS points: " + points.size()
                        + "; distance_m: " + Math.round(distanceMetres));
        diagnosticUpdatesRegistered = false;
        final double savedDistanceMetres = stationBoundary ? routeDistanceMetres(savedPoints) : distanceMetres;
        final int savedGpsPointCount = savedPoints.size();
        final boolean confirmedStationaryStop = stationarySince != 0
                && System.currentTimeMillis() - stationarySince
                >= CaptureStartGate.STILLNESS_END_THRESHOLD_MS;
        final String savedEndedAt = stationBoundary
                ? Instant.ofEpochMilli(savedPoints.get(savedPoints.size()-1).getTime()).toString()
                : Instant.now().toString();
        final String savedTimezone = ZoneId.systemDefault().toString();
        final Location savedStopAnchor = stationaryAnchor == null
                ? null : new Location(stationaryAnchor);
        broadcastUpdate("Saving journey locally...");

        captureIo.execute(() -> {
            boolean savedSuccessfully = false;
            try {
                JSONObject journey = new JSONObject();
                journey.put("journey_id", savedJourneyId);
                journey.put("revision", 1);
                journey.put("source", new JSONObject().put("type", "android_activity_capture"));
                journey.put("started_at", savedStartedAt);
                journey.put("ended_at", savedEndedAt);
                journey.put("timezone", savedTimezone);
                journey.put("mode", savedMode);
                journey.put("transport_confirmation", "required");
                if (savedStationReason != null) journey.put("station_evidence", new JSONObject()
                        .put("reason", savedStationReason).put("station_code", savedStationCode)
                        .put("classification", "suggested"));
                if (stationBoundary) journey.put("raw_capture_geometry", new JSONObject()
                        .put("type", "LineString").put("coordinates", coordinates(fullCapturePoints)));
                List<Location> routePoints = confirmedStationaryStop && !stationBoundary
                        ? collapseStationaryEndpoint(savedPoints, savedStopAnchor) : savedPoints;
                double routeDistance = routePoints.size() < savedPoints.size()
                        ? routeDistanceMetres(routePoints) : savedDistanceMetres;
                journey.put("distance_meters", routeDistance);
                journey.put("gps_point_count", savedGpsPointCount);
                journey.put("route_geometry", new JSONObject()
                        .put("type", "LineString")
                        .put("coordinates", coordinates(routePoints)));
                journey.put("processing", new JSONObject()
                        .put("import", "complete")
                        .put("road_matching", roadMode(savedMode) ? "pending" : "not_required")
                        .put("foot_matching", "walking".equals(savedMode) ? "pending" : "not_required"));
                JSONArray serviceStationCandidates = ServiceStationStore.trackedCandidates(
                        getApplicationContext(), savedPoints);
                if (serviceStationCandidates.length() > 0)
                    journey.put("service_station_candidates", serviceStationCandidates);
                JourneyStore.save(getApplicationContext(), journey);
                MovementDiagnostics.recordEvent(getApplicationContext(), "journey_saved",
                        "Mode: " + savedMode + "; GPS points: " + savedGpsPointCount
                                + "; distance_m: " + Math.round(routeDistance));
                if (roadMode(savedMode) || "walking".equals(savedMode)) {
                    MatchingCoordinator.get(getApplicationContext()).start(savedJourneyId);
                }
                checkpointFile.delete();
                savedSuccessfully = true;
            } catch (Exception ignored) {
                // Report the failure on the service thread after the disk operation.
            }

            final boolean completed = savedSuccessfully;
            handler.post(() -> completeCaptureSave(completed, savedStopAnchor));
        });
    }

    private void completeCaptureSave(boolean savedSuccessfully, Location savedStopAnchor) {
        captureFinishing = false;
        if (savedSuccessfully) {
            String continueMode = nextCaptureMode;
            List<Location> continuePoints = nextCapturePoints;
            String continueStartedAt = nextCaptureStartedAt;
            String continueStationCode = stationEvidenceCode;
            String continueStationReason = stationEvidenceReason;
            nextCaptureMode = null; nextCapturePoints = null; nextCaptureStartedAt = null;
            stationEndIndex = -1; stationEvidenceReason = null; stationEvidenceCode = null;
            getSharedPreferences(STATE_PREFS, MODE_PRIVATE).edit()
                    .putBoolean(STATE_ACTIVE, false)
                    .remove(STATE_MODE)
                    .apply();
            points.clear();
            journeyId = null;
            startedAt = null;
            mode = null;
            distanceMetres = 0;
            lastPoint = null;
            stationarySince = 0;
            stationaryAnchor = null;
            handler.removeCallbacks(finishIfStill);
            if (continueMode != null) {
                clearDepartureAnchor();
                automaticCapture = true;
                activateCapture(continueMode, continueStartedAt, continuePoints);
                stationEvidenceCode = continueStationCode;
                stationEvidenceReason = continueStationReason;
                broadcastUpdate("Started a new " + continueMode + " journey leg.");
            } else {
                storeDepartureAnchor(savedStopAnchor);
                if (isArmed(this)) {
                    updateNotification();
                    refreshDiagnosticLocationUpdates();
                }
            }
            broadcastUpdate("Journey saved locally. Review its transport type next time.");
            if (!isArmed(this)) {
                stopForeground(STOP_FOREGROUND_REMOVE);
                stopSelf();
            }
        } else {
            nextCaptureMode = null; nextCapturePoints = null; nextCaptureStartedAt = null;
            stationEndIndex = -1;
            broadcastUpdate("Could not save journey. It is still open; tap Stop to retry.");
            // Retain the points and recording state so a later Stop can retry.
            try {
                requestCaptureLocationUpdates();
                scheduleCaptureCheckpoint();
            } catch (SecurityException ignored) {
                broadcastUpdate("Could not resume location updates after the save failed.");
            }
        }
    }

    private boolean roadMode(String journeyMode) {
        return "driving".equals(journeyMode) || "bus".equals(journeyMode);
    }

    @SuppressLint("MissingPermission")
    private void requestCaptureLocationUpdates() {
        requestLocationUpdatesForMode(mode, false);
    }

    @SuppressLint("MissingPermission")
    private void requestLocationUpdatesForMode(String requestedMode, boolean candidate) {
        boolean road = roadMode(requestedMode);
        long interval = candidate ? 5_000L : (road ? ROAD_CAPTURE_INTERVAL_MS : WALK_CAPTURE_INTERVAL_MS);
        float minDistance = candidate ? 5f : (road ? ROAD_CAPTURE_MIN_DISTANCE_METRES : WALK_CAPTURE_MIN_DISTANCE_METRES);
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,
                interval, minDistance, locationListener);
        diagnosticUpdatesRegistered = false;
    }

    @SuppressLint("MissingPermission")
    private void refreshDiagnosticLocationUpdates() {
        boolean diagnosticWindowOpen = MovementDiagnostics.isRunning(this);
        if (diagnosticWindowOpen && isArmed(this)
                && !isActive(this) && candidateMode == null) {
            requestDiagnosticLocationUpdates();
        } else {
            handler.removeCallbacks(diagnosticWindowExpiry);
            if ((!diagnosticWindowOpen || !isArmed(this))
                    && !isActive(this) && candidateMode == null && diagnosticUpdatesRegistered) {
                locationManager.removeUpdates(locationListener);
                diagnosticUpdatesRegistered = false;
            }
        }
    }

    @SuppressLint("MissingPermission")
    private void requestDiagnosticLocationUpdates() {
        if (!MovementDiagnostics.isRunning(this) || !isArmed(this)
                || isActive(this) || candidateMode != null || diagnosticUpdatesRegistered) return;
        try {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,
                    30_000L, 10f, locationListener);
            diagnosticUpdatesRegistered = true;
            handler.removeCallbacks(diagnosticWindowExpiry);
            handler.postDelayed(diagnosticWindowExpiry,
                    Math.max(1_000L, MovementDiagnostics.until(this) - System.currentTimeMillis()));
            MovementDiagnostics.recordEvent(this, "movement_sampling_started",
                    "GPS samples requested at most every 30 seconds while waiting for movement.");
        } catch (SecurityException error) {
            MovementDiagnostics.recordEvent(this, "location_updates_failed",
                    "GPS permission: " + error.getClass().getSimpleName());
        }
    }

    private void scheduleCaptureCheckpoint() {
        handler.removeCallbacks(checkpointCapture);
        handler.postDelayed(checkpointCapture, CHECKPOINT_INTERVAL_MS);
    }

    private final Runnable checkpointCapture = new Runnable() {
        @Override public void run() {
            if (!isActive(CaptureService.this) || captureFinishing) return;
            if (!checkpointPending) {
                checkpointPending = true;
                final List<Location> savedPoints = new ArrayList<>(points);
                final String savedJourneyId = journeyId;
                final String savedStartedAt = startedAt;
                final String savedMode = mode;
                final double savedDistanceMetres = distanceMetres;
                final long savedStationarySince = stationarySince;
                final boolean savedAutomatic = automaticCapture;
                final String savedStationReason = stationEvidenceReason, savedStationCode = stationEvidenceCode;
                final String[] stationState = stationModel == null ? null : stationModel.checkpoint();
                captureIo.execute(() -> {
                    FileOutputStream output = null;
                    try {
                        JSONObject snapshot = checkpointSnapshot(savedJourneyId, savedStartedAt,
                                savedMode, savedDistanceMetres, savedStationarySince, savedPoints);
                        snapshot.put("automatic_capture", savedAutomatic);
                        snapshot.put("station_reason", savedStationReason);
                        snapshot.put("station_code", savedStationCode);
                        if (stationState != null) snapshot.put("station_state", new JSONArray(Arrays.asList(stationState)));
                        output = checkpointFile.startWrite();
                        output.write(snapshot.toString().getBytes(StandardCharsets.UTF_8));
                        checkpointFile.finishWrite(output);
                    } catch (Exception ignored) {
                        if (output != null) checkpointFile.failWrite(output);
                        // Keep the prior atomic checkpoint if this write fails.
                    } finally {
                        handler.post(() -> checkpointPending = false);
                    }
                });
            }
            handler.postDelayed(this, CHECKPOINT_INTERVAL_MS);
        }
    };

    private JSONObject checkpointSnapshot(String savedJourneyId, String savedStartedAt,
            String savedMode, double savedDistanceMetres, long savedStationarySince,
            List<Location> savedRoutePoints) throws org.json.JSONException {
        JSONObject snapshot = new JSONObject();
        snapshot.put("journey_id", savedJourneyId);
        snapshot.put("started_at", savedStartedAt);
        snapshot.put("mode", savedMode);
        snapshot.put("distance_meters", savedDistanceMetres);
        snapshot.put("stationary_since", savedStationarySince);
        JSONArray savedPoints = new JSONArray();
        for (Location point : savedRoutePoints) {
            savedPoints.put(new JSONArray()
                    .put(point.getLongitude())
                    .put(point.getLatitude())
                    .put(point.hasAccuracy() ? point.getAccuracy() : 0)
                    .put(point.getTime())
                    .put(point.hasSpeed() ? point.getSpeed() : -1));
        }
        snapshot.put("points", savedPoints);
        return snapshot;
    }

    private void restoreActiveCapture() {
        try (InputStream input = checkpointFile.openRead();
             BufferedReader reader = new BufferedReader(new InputStreamReader(input,
                     StandardCharsets.UTF_8))) {
            StringBuilder json = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) json.append(line);
            JSONObject snapshot = new JSONObject(json.toString());
            JSONArray savedPoints = snapshot.optJSONArray("points");
            if (savedPoints == null || savedPoints.length() == 0
                    || snapshot.optString("journey_id", "").isEmpty()) {
                throw new IllegalStateException("Incomplete capture checkpoint");
            }

            journeyId = snapshot.optString("journey_id");
            startedAt = snapshot.optString("started_at");
            mode = snapshot.optString("mode", "walking");
            distanceMetres = snapshot.optDouble("distance_meters", 0);
            stationarySince = snapshot.optLong("stationary_since", 0);
            automaticCapture = snapshot.optBoolean("automatic_capture", true);
            restoredStationState = snapshot.optJSONArray("station_state");
            stationEvidenceReason = snapshot.isNull("station_reason") ? null : snapshot.optString("station_reason", null);
            stationEvidenceCode = snapshot.isNull("station_code") ? null : snapshot.optString("station_code", null);
            points.clear();
            for (int index = 0; index < savedPoints.length(); index++) {
                JSONArray coordinate = savedPoints.optJSONArray(index);
                if (coordinate == null || coordinate.length() < 2) continue;
                Location point = new Location("checkpoint");
                point.setLongitude(coordinate.optDouble(0));
                point.setLatitude(coordinate.optDouble(1));
                if (coordinate.length() > 2 && coordinate.optDouble(2) > 0) {
                    point.setAccuracy((float) coordinate.optDouble(2));
                }
                if (coordinate.length() > 3) point.setTime(coordinate.optLong(3));
                if (coordinate.length() > 4 && coordinate.optDouble(4, -1) >= 0)
                    point.setSpeed((float)coordinate.optDouble(4));
                points.add(point);
            }
            if (points.isEmpty()) throw new IllegalStateException("No saved GPS points");
            lastPoint = new Location(points.get(points.size() - 1));

            startForegroundWithNotification();
            scheduleCaptureCheckpoint();
            if (stationarySince > 0) {
                long remaining = Math.max(1L, STILLNESS_END_THRESHOLD_MS
                        - (System.currentTimeMillis() - stationarySince));
                handler.postDelayed(finishIfStill, remaining);
            }
            try {
                requestCaptureLocationUpdates();
                broadcastUpdate("Recovered the active journey from its local checkpoint.");
            } catch (SecurityException error) {
                broadcastUpdate("Journey recovered; location permission is needed to resume tracking.");
            }
        } catch (Exception error) {
            getSharedPreferences(STATE_PREFS, MODE_PRIVATE).edit()
                    .putBoolean(STATE_ACTIVE, false)
                    .remove(STATE_MODE)
                    .apply();
            checkpointFile.delete();
            broadcastUpdate("The previous recording could not be recovered.");
        }
    }

    private JSONArray coordinates(List<Location> routePoints) throws org.json.JSONException {
        JSONArray coordinates = new JSONArray();
        for (Location point : routePoints) {
            coordinates.put(new JSONArray()
                    .put(point.getLongitude())
                    .put(point.getLatitude()));
        }
        return coordinates;
    }

    static List<Location> collapseStationaryEndpoint(List<Location> routePoints, Location stopAnchor) {
        List<Location> result = new ArrayList<>();
        if (routePoints == null) return result;
        if (stopAnchor == null || routePoints.size() < 4) {
            result.addAll(routePoints);
            return result;
        }
        float radius = stopAnchor.hasAccuracy()
                ? Math.max(20f, Math.min(35f, stopAnchor.getAccuracy()))
                : 25f;
        int suffixStart = routePoints.size();
        for (int index = routePoints.size() - 1; index >= 0; index--) {
            Location point = routePoints.get(index);
            if (point == null || point.distanceTo(stopAnchor) > radius) break;
            suffixStart = index;
        }
        int clusteredPoints = routePoints.size() - suffixStart;
        if (suffixStart == 0 || clusteredPoints < 3) {
            result.addAll(routePoints);
            return result;
        }
        for (int index = 0; index < suffixStart; index++) result.add(routePoints.get(index));
        result.add(new Location(stopAnchor));
        return result;
    }

    private static double routeDistanceMetres(List<Location> routePoints) {
        double distance = 0;
        for (int index = 1; index < routePoints.size(); index++) {
            distance += routePoints.get(index - 1).distanceTo(routePoints.get(index));
        }
        return distance;
    }

    private void removeActivityUpdates() {
        // Recreate the same immutable PendingIntent when Android has already
        // recreated the service, so legacy subscriptions can still be removed.
        PendingIntent pendingIntent = activityPendingIntent;
        if (pendingIntent == null) {
            pendingIntent = PendingIntent.getBroadcast(
                    this,
                    42,
                    new Intent(this, ActivityRecognitionReceiver.class),
                    PendingIntent.FLAG_UPDATE_CURRENT
                            | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                            ? PendingIntent.FLAG_MUTABLE : 0));
        }
        // Clear both APIs so upgrades from v0.4.3 cannot leave an old
        // sampling subscription delivering callbacks to this receiver.
        try {
            activityClient.removeActivityUpdates(pendingIntent);
            activityClient.removeActivityTransitionUpdates(pendingIntent);
        } catch (SecurityException error) {
            // Permission can be revoked while tracking is armed. Cleanup must
            // still finish instead of crashing when the service is stopped.
            android.util.Log.w("Roadprints", "Activity permission revoked during cleanup", error);
        }
        activityPendingIntent = null;
    }

    private void startForegroundWithNotification() {
        Notification notification = buildNotification();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private Notification buildNotification() {
        Intent openApp = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, openApp,
                PendingIntent.FLAG_UPDATE_CURRENT
                            | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                            ? PendingIntent.FLAG_MUTABLE : 0));

        String text = isActive(this)
                ? "Recording " + (mode == null ? "journey" : mode)
                : "Automatic tracking active";
        return new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("Roadprints")
                .setContentText(text + " - tap to return to the app")
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setOngoing(true)
                .setContentIntent(pendingIntent)
                .build();
    }

    private void updateNotification() {
        NotificationManager manager =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        manager.notify(NOTIFICATION_ID, buildNotification());
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "Roadprints recording", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Shows when Roadprints is recording or watching for movement");
        NotificationManager manager =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        manager.createNotificationChannel(channel);
    }

    private void broadcastUpdate(String message) {
        Intent update = new Intent(ACTION_UPDATE);
        update.setPackage(getPackageName());
        update.putExtra(EXTRA_ACTIVE, isActive(this));
        update.putExtra(EXTRA_ARMED, isArmed(this));
        update.putExtra(EXTRA_MODE, mode);
        update.putExtra(EXTRA_DISTANCE, distanceMetres);
        update.putExtra(EXTRA_POINTS, points.size());
        update.putExtra(EXTRA_MESSAGE, message);
        sendBroadcast(update);
    }

    @Override
    public void onDestroy() {
        removeActivityUpdates();
        handler.removeCallbacks(checkpointCapture);
        handler.removeCallbacks(diagnosticWindowExpiry);
        clearPendingActivityMode();
        if (isActive(this)) {
            try {
                locationManager.removeUpdates(locationListener);
            } catch (Exception ignored) {}
        }
        captureIo.shutdown();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}

