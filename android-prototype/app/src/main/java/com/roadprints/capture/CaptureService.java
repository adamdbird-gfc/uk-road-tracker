package com.roadprints.capture;

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

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class CaptureService extends Service {
    public static final String ACTION_START = "com.roadprints.capture.START";
    public static final String ACTION_STOP = "com.roadprints.capture.STOP";
    public static final String ACTION_ARM = "com.roadprints.capture.ARM";
    public static final String ACTION_DISARM = "com.roadprints.capture.DISARM";
    public static final String ACTION_ACTIVITY = "com.roadprints.capture.ACTIVITY";
    public static final String ACTION_UPDATE = "com.roadprints.capture.UPDATE";
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
    private static final long STILLNESS_END_THRESHOLD_MS = 60_000L;

    private LocationManager locationManager;
    private ActivityRecognitionClient activityClient;
    private PendingIntent activityPendingIntent;
    private final List<Location> points = new ArrayList<>();
    private Location lastPoint;
    private String journeyId;
    private String startedAt;
    private String mode;
    private double distanceMetres;
    private long stationarySince;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable finishIfStill = () -> {
        if (isActive(this) && stationarySince != 0 && System.currentTimeMillis() - stationarySince >= STILLNESS_END_THRESHOLD_MS) {
            finishCapture();
        }
    };

    private final LocationListener locationListener = new LocationListener() {
        @Override
        public void onLocationChanged(Location location) {
            if (lastPoint != null) distanceMetres += lastPoint.distanceTo(location);
            lastPoint = location;
            points.add(location);
            updateNotification();
            broadcastUpdate("Recording " + mode + " locally...");
        }

        @Override public void onProviderEnabled(String provider) {}
        @Override public void onProviderDisabled(String provider) {}
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
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        activityClient = ActivityRecognition.getClient(this);
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;

        String action = intent.getAction();
        if (ACTION_STOP.equals(action)) {
            finishCapture();
        } else if (ACTION_START.equals(action)) {
            startCapture(intent.getStringExtra(EXTRA_MODE));
        } else if (ACTION_ARM.equals(action)) {
            armTracking();
        } else if (ACTION_DISARM.equals(action)) {
            disarmTracking();
        } else if (ACTION_ACTIVITY.equals(action)) {
            handleTransition(
                    intent.getIntExtra(EXTRA_ACTIVITY_TYPE, DetectedActivity.UNKNOWN),
                    intent.getIntExtra(EXTRA_TRANSITION, -1));
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
        broadcastUpdate("Android detected " + (entering ? "entered " : "left ")
                + activityLabel + ".");

        if (!entering) {
            if (activityType == DetectedActivity.STILL) {
                stationarySince = 0;
                handler.removeCallbacks(finishIfStill);
            }
            return;
        }

        if (activityType == DetectedActivity.STILL) {
            if (isActive(this)) {
                stationarySince = System.currentTimeMillis();
                handler.removeCallbacks(finishIfStill);
                handler.postDelayed(finishIfStill, STILLNESS_END_THRESHOLD_MS);
                broadcastUpdate("Stationary detected; keeping the journey open for 60 seconds...");
            }
            return;
        }

        stationarySince = 0;
        handler.removeCallbacks(finishIfStill);
        String detectedMode = modeForActivity(activityType);
        if (detectedMode != null && !isActive(this)) {
            startCapture(detectedMode);
        }
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
        if (activityType == DetectedActivity.IN_VEHICLE) return "driving";
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

        mode = requestedMode == null ? "driving" : requestedMode;
        journeyId = UUID.randomUUID().toString();
        startedAt = Instant.now().toString();
        distanceMetres = 0;
        lastPoint = null;
        stationarySince = 0;
        points.clear();

        getSharedPreferences(STATE_PREFS, MODE_PRIVATE).edit()
                .putBoolean(STATE_ACTIVE, true)
                .putString(STATE_MODE, mode)
                .apply();

        startForegroundWithNotification();
        try {
            locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER, 2000L, 5f, locationListener);
            broadcastUpdate("Recording " + mode + " locally...");
        } catch (SecurityException error) {
            getSharedPreferences(STATE_PREFS, MODE_PRIVATE).edit()
                    .putBoolean(STATE_ACTIVE, false).apply();
            broadcastUpdate("Location permission is required to record.");
            if (!isArmed(this)) {
                stopForeground(STOP_FOREGROUND_REMOVE);
                stopSelf();
            }
        }
    }

    private void finishCapture() {
        if (!isActive(this)) {
            if (!isArmed(this)) stopSelf();
            return;
        }

        locationManager.removeUpdates(locationListener);
        try {
            JSONObject journey = new JSONObject();
            journey.put("journey_id", journeyId);
            journey.put("revision", 1);
            journey.put("source", new JSONObject().put("type", "android_activity_capture"));
            journey.put("started_at", startedAt);
            journey.put("ended_at", Instant.now().toString());
            journey.put("timezone", ZoneId.systemDefault().toString());
            journey.put("mode", mode);
            journey.put("transport_confirmation", "required");
            journey.put("distance_meters", distanceMetres);
            journey.put("route_geometry", new JSONObject()
                    .put("type", "LineString")
                    .put("coordinates", coordinates()));
            journey.put("processing", new JSONObject()
                    .put("import", "complete")
                    .put("road_matching", roadMode(mode) ? "pending" : "not_required")
                    .put("foot_matching", mode.equals("walking") ? "pending" : "not_required"));
            JourneyStore.save(this, journey);
            broadcastUpdate("Journey saved locally. Review its transport type next time.");
        } catch (Exception error) {
            broadcastUpdate("Could not save journey: " + error.getMessage());
        } finally {
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
            if (isArmed(this)) {
                updateNotification();
            } else {
                stopForeground(STOP_FOREGROUND_REMOVE);
                stopSelf();
            }
        }
    }

    private boolean roadMode(String journeyMode) {
        return "driving".equals(journeyMode) || "bus".equals(journeyMode);
    }

    private JSONArray coordinates() throws org.json.JSONException {
        JSONArray coordinates = new JSONArray();
        for (Location point : points) {
            coordinates.put(new JSONArray()
                    .put(point.getLongitude())
                    .put(point.getLatitude()));
        }
        return coordinates;
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
        activityClient.removeActivityUpdates(pendingIntent);
        activityClient.removeActivityTransitionUpdates(pendingIntent);
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
        if (isActive(this)) {
            try {
                locationManager.removeUpdates(locationListener);
            } catch (Exception ignored) {}
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
