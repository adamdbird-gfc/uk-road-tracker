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
import android.os.IBinder;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class CaptureService extends Service {
    public static final String ACTION_START = "com.roadprints.capture.START";
    public static final String ACTION_STOP = "com.roadprints.capture.STOP";
    public static final String ACTION_UPDATE = "com.roadprints.capture.UPDATE";
    public static final String EXTRA_MODE = "mode";
    public static final String EXTRA_ACTIVE = "active";
    public static final String EXTRA_DISTANCE = "distance_meters";
    public static final String EXTRA_POINTS = "points";
    public static final String EXTRA_MESSAGE = "message";

    private static final String STATE_PREFS = "roadprints_capture_state";
    private static final String STATE_ACTIVE = "active";
    private static final String STATE_MODE = "mode";
    private static final String CHANNEL_ID = "roadprints_recording";
    private static final int NOTIFICATION_ID = 41;

    private LocationManager locationManager;
    private final List<Location> points = new ArrayList<>();
    private Location lastPoint;
    private String journeyId;
    private String startedAt;
    private String mode;
    private double distanceMetres;

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

    @Override
    public void onCreate() {
        super.onCreate();
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            finishCapture();
        } else if (intent != null && ACTION_START.equals(intent.getAction())) {
            startCapture(intent.getStringExtra(EXTRA_MODE));
        }
        return START_NOT_STICKY;
    }

    private void startCapture(String requestedMode) {
        if (isActive(this)) return;

        mode = requestedMode == null ? "driving" : requestedMode;
        journeyId = UUID.randomUUID().toString();
        startedAt = Instant.now().toString();
        distanceMetres = 0;
        lastPoint = null;
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
                    .clear().apply();
            broadcastUpdate("Location permission is required to record.");
            stopSelf();
        }
    }

    private void finishCapture() {
        if (!isActive(this)) {
            stopSelf();
            return;
        }

        locationManager.removeUpdates(locationListener);
        try {
            JSONObject journey = new JSONObject();
            journey.put("journey_id", journeyId);
            journey.put("revision", 1);
            journey.put("source", new JSONObject().put("type", "android_foreground_service"));
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
            getSharedPreferences(STATE_PREFS, MODE_PRIVATE).edit().clear().apply();
            points.clear();
            journeyId = null;
            startedAt = null;
            mode = null;
            distanceMetres = 0;
            lastPoint = null;
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
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
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("Roadprints recording")
                .setContentText("Recording " + (mode == null ? "journey" : mode)
                        + " - tap to return to the app")
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
        channel.setDescription("Shows when Roadprints is recording a journey");
        NotificationManager manager =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        manager.createNotificationChannel(channel);
    }

    private void broadcastUpdate(String message) {
        Intent update = new Intent(ACTION_UPDATE);
        update.setPackage(getPackageName());
        update.putExtra(EXTRA_ACTIVE, isActive(this));
        update.putExtra(EXTRA_MODE, mode);
        update.putExtra(EXTRA_DISTANCE, distanceMetres);
        update.putExtra(EXTRA_POINTS, points.size());
        update.putExtra(EXTRA_MESSAGE, message);
        sendBroadcast(update);
    }

    @Override
    public void onDestroy() {
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
