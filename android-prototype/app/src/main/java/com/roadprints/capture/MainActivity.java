package com.roadprints.capture;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MainActivity extends Activity {
    private static final int LOCATION_REQUEST = 41;
    private static final String[] MODE_LABELS = {
            "Driving", "Walking", "Bus", "Train", "Cycling", "Plane", "Ferry"
    };
    private static final String[] MODE_VALUES = {
            "driving", "walking", "bus", "train", "cycling", "plane", "ferry"
    };

    private LocationManager locationManager;
    private LocationListener locationListener;
    private final List<Location> points = new ArrayList<>();
    private String journeyId;
    private String startedAt;
    private String mode = "driving";
    private double distanceMetres;
    private Location lastPoint;

    private TextView status;
    private TextView distance;
    private TextView saved;
    private Button captureButton;
    private Spinner modeSpinner;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        buildScreen();
        modeSpinner.postDelayed(this::reviewLatestJourney, 350L);
    }

    private void buildScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(32, 48, 32, 32);
        root.setBackgroundColor(0xFFF6F9FC);

        TextView title = new TextView(this);
        title.setText("Roadprints");
        title.setTextSize(30);
        title.setTextColor(0xFF0A2B43);
        title.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView subtitle = new TextView(this);
        subtitle.setText("Android prototype v0.2.2 - manual controls for testing");
        subtitle.setTextSize(15);
        subtitle.setGravity(Gravity.CENTER_HORIZONTAL);
        subtitle.setPadding(0, 8, 0, 32);

        modeSpinner = new Spinner(this);
        modeSpinner.setAdapter(new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_dropdown_item, MODE_LABELS));

        captureButton = new Button(this);
        captureButton.setText("Start capture");
        captureButton.setOnClickListener(v -> toggleCapture());

        status = new TextView(this);
        status.setText("Ready. Journeys are archived on this device.");
        status.setTextSize(16);
        status.setPadding(0, 32, 0, 12);

        distance = new TextView(this);
        distance.setText("Distance: 0 m");
        distance.setTextSize(18);

        saved = new TextView(this);
        saved.setText("Saved prototype journeys: " + JourneyStore.count(this));
        saved.setPadding(0, 24, 0, 0);

        root.addView(title);
        root.addView(subtitle);
        root.addView(modeSpinner);
        root.addView(captureButton);
        root.addView(status);
        root.addView(distance);
        root.addView(saved);
        setContentView(root);
    }

    private void toggleCapture() {
        if (journeyId == null) {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_REQUEST);
                return;
            }
            beginCapture();
        } else {
            finishCapture();
        }
    }

    private void beginCapture() {
        journeyId = UUID.randomUUID().toString();
        startedAt = Instant.now().toString();
        mode = MODE_VALUES[modeSpinner.getSelectedItemPosition()];
        points.clear();
        distanceMetres = 0;
        lastPoint = null;
        captureButton.setText("Stop and save journey");
        status.setText("Recording " + mode + " locally...");
        distance.setText("Distance: 0 m");

        locationListener = new LocationListener() {
            @Override public void onLocationChanged(Location location) {
                if (lastPoint != null) distanceMetres += lastPoint.distanceTo(location);
                lastPoint = location;
                points.add(location);
                distance.setText(String.format(
                        "Distance: %.0f m - %d points", distanceMetres, points.size()));
            }
            @Override public void onProviderEnabled(String provider) {}
            @Override public void onProviderDisabled(String provider) {}
        };

        try {
            locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER, 2000L, 5f, locationListener);
        } catch (SecurityException error) {
            status.setText("Location permission is required to record.");
            journeyId = null;
        }
    }

    private void finishCapture() {
        if (locationListener != null) locationManager.removeUpdates(locationListener);
        try {
            JSONObject journey = new JSONObject();
            journey.put("journey_id", journeyId);
            journey.put("revision", 1);
            journey.put("source", new JSONObject().put("type", "android_foreground_capture"));
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
            status.setText("Journey saved locally. Review its transport type next time.");
            saved.setText("Saved prototype journeys: " + JourneyStore.count(this));
        } catch (Exception error) {
            status.setText("Could not save journey: " + error.getMessage());
        } finally {
            journeyId = null;
            locationListener = null;
            points.clear();
            captureButton.setText("Start capture");
        }
    }

    private boolean roadMode(String journeyMode) {
        return journeyMode.equals("driving") || journeyMode.equals("bus");
    }

    private void reviewLatestJourney() {
        JSONObject journey = JourneyStore.latest(this);
        if (journey == null
                || !"required".equals(journey.optString("transport_confirmation"))) {
            return;
        }

        String currentMode = journey.optString("mode", "driving");
        Spinner reviewSpinner = new Spinner(this);
        reviewSpinner.setAdapter(new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_dropdown_item, MODE_LABELS));
        reviewSpinner.setSelection(modeIndex(currentMode));

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(24, 0, 24, 0);
        TextView message = new TextView(this);
        message.setText("Was your latest journey recorded using the right transport?");
        message.setTextSize(16);
        message.setPadding(0, 0, 0, 16);
        container.addView(message);
        container.addView(reviewSpinner);

        new AlertDialog.Builder(this)
                .setTitle("Review journey")
                .setView(container)
                .setNegativeButton("Later", null)
                .setPositiveButton("Save transport", (dialog, which) -> {
                    String selected = MODE_VALUES[reviewSpinner.getSelectedItemPosition()];
                    JourneyStore.updateMode(this, journey.optString("journey_id"), selected);
                    status.setText("Journey updated to " + selected + ".");
                })
                .show();
    }

    private int modeIndex(String value) {
        for (int index = 0; index < MODE_VALUES.length; index++) {
            if (MODE_VALUES[index].equals(value)) return index;
        }
        return 0;
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
}
