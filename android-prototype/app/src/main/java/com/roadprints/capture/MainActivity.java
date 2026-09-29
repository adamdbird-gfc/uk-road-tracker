package com.roadprints.capture;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class MainActivity extends Activity {
    private static final int LOCATION_REQUEST = 41;
    private static final String[] MODE_LABELS = {
            "Driving", "Walking", "Bus", "Train", "Cycling", "Plane", "Ferry"
    };
    private static final String[] MODE_VALUES = {
            "driving", "walking", "bus", "train", "cycling", "plane", "ferry"
    };

    private TextView status;
    private TextView distance;
    private TextView saved;
    private Button captureButton;
    private Button trackingButton;
    private Button historyButton;
    private Spinner modeSpinner;
    private boolean capturing;
    private boolean tracking;

    private final BroadcastReceiver captureReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!CaptureService.ACTION_UPDATE.equals(intent.getAction())) return;
            boolean active = intent.getBooleanExtra(CaptureService.EXTRA_ACTIVE, false);
            boolean armed = intent.getBooleanExtra(CaptureService.EXTRA_ARMED, false);
            capturing = active;
            tracking = armed;
            updateCaptureButton();
            updateTrackingButton();

            String message = intent.getStringExtra(CaptureService.EXTRA_MESSAGE);
            if (message != null) status.setText(message);

            double metres = intent.getDoubleExtra(CaptureService.EXTRA_DISTANCE, 0);
            int points = intent.getIntExtra(CaptureService.EXTRA_POINTS, 0);
            if (active) {
                distance.setText(String.format(
                        "Distance: %.0f m - %d points", metres, points));
            } else {
                saved.setText("Saved prototype journeys: " + JourneyStore.count(MainActivity.this));
                distance.setText("Distance: 0 m");
            }
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        registerCaptureReceiver();
        buildScreen();
        capturing = CaptureService.isActive(this);
        tracking = CaptureService.isArmed(this);
        updateCaptureButton();
        updateTrackingButton();
        if (tracking) repairTrackingSubscription();
        modeSpinner.postDelayed(this::reviewLatestJourney, 350L);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (captureButton != null) {
            capturing = CaptureService.isActive(this);
            tracking = CaptureService.isArmed(this);
            updateCaptureButton();
            updateTrackingButton();
        }
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(captureReceiver);
        super.onDestroy();
    }

    private void registerCaptureReceiver() {
        IntentFilter filter = new IntentFilter(CaptureService.ACTION_UPDATE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(captureReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(captureReceiver, filter);
        }
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
        subtitle.setText("Android prototype v" + BuildConfig.VERSION_NAME + " - automatic tracking test");
        subtitle.setTextSize(15);
        subtitle.setGravity(Gravity.CENTER_HORIZONTAL);
        subtitle.setPadding(0, 8, 0, 32);

        modeSpinner = new Spinner(this);
        modeSpinner.setAdapter(new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_dropdown_item, MODE_LABELS));

        trackingButton = new Button(this);
        trackingButton.setOnClickListener(v -> toggleTracking());

        captureButton = new Button(this);
        captureButton.setOnClickListener(v -> toggleCapture());

        status = new TextView(this);
        status.setText("Ready. Enable automatic tracking to test movement detection.");
        status.setTextSize(16);
        status.setPadding(0, 32, 0, 12);

        distance = new TextView(this);
        distance.setText("Distance: 0 m");
        distance.setTextSize(18);

        saved = new TextView(this);
        saved.setText("Saved prototype journeys: " + JourneyStore.count(this));
        saved.setPadding(0, 24, 0, 0);

        historyButton = new Button(this);
        historyButton.setText("View saved journeys");
        historyButton.setOnClickListener(v -> showJourneyHistory());

        root.addView(title);
        root.addView(subtitle);
        root.addView(modeSpinner);
        root.addView(trackingButton);
        root.addView(captureButton);
        root.addView(status);
        root.addView(distance);
        root.addView(saved);
        root.addView(historyButton);
        setContentView(root);
    }

    private void repairTrackingSubscription() {
        Intent repair = new Intent(this, CaptureService.class)
                .setAction(CaptureService.ACTION_ARM);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(repair);
        } else {
            startService(repair);
        }
    }

    private void toggleTracking() {
        if (tracking || CaptureService.isArmed(this)) {
            Intent stop = new Intent(this, CaptureService.class)
                    .setAction(CaptureService.ACTION_DISARM);
            startService(stop);
            return;
        }

        boolean locationGranted = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        boolean activityGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
                || checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION)
                == PackageManager.PERMISSION_GRANTED;

        if (!locationGranted || !activityGranted) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                requestPermissions(new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.ACTIVITY_RECOGNITION,
                        Manifest.permission.POST_NOTIFICATIONS
                }, LOCATION_REQUEST);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                requestPermissions(new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.ACTIVITY_RECOGNITION
                }, LOCATION_REQUEST);
            } else {
                requestPermissions(new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                }, LOCATION_REQUEST);
            }
            return;
        }

        Intent start = new Intent(this, CaptureService.class)
                .setAction(CaptureService.ACTION_ARM);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(start);
        } else {
            startService(start);
        }
        tracking = true;
        updateTrackingButton();
        status.setText("Starting automatic tracking...");
    }

    private void toggleCapture() {
        if (capturing || CaptureService.isActive(this)) {
            Intent stop = new Intent(this, CaptureService.class)
                    .setAction(CaptureService.ACTION_STOP);
            startService(stop);
            return;
        }

        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                requestPermissions(new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.POST_NOTIFICATIONS
                }, LOCATION_REQUEST);
            } else {
                requestPermissions(new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                }, LOCATION_REQUEST);
            }
            return;
        }
        beginCapture();
    }

    private void beginCapture() {
        String mode = MODE_VALUES[modeSpinner.getSelectedItemPosition()];
        Intent start = new Intent(this, CaptureService.class)
                .setAction(CaptureService.ACTION_START)
                .putExtra(CaptureService.EXTRA_MODE, mode);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(start);
        } else {
            startService(start);
        }
        capturing = true;
        updateCaptureButton();
        status.setText("Starting background recording...");
        distance.setText("Distance: 0 m");
    }

    private void updateCaptureButton() {
        if (captureButton == null) return;
        captureButton.setText(capturing ? "Stop and save journey" : "Start capture");
    }

    private void updateTrackingButton() {
        if (trackingButton == null) return;
        trackingButton.setText(tracking
                ? "Disable automatic tracking"
                : "Enable automatic tracking");
    }

    private void showJourneyHistory() {
        List<JSONObject> journeys = JourneyStore.all(this);
        if (journeys.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle("Saved journeys")
                    .setMessage("No journeys have been saved on this device yet.")
                    .setPositiveButton("Close", null)
                    .show();
            return;
        }

        String[] rows = new String[journeys.size()];
        for (int index = 0; index < journeys.size(); index++) {
            JSONObject journey = journeys.get(index);
            double metres = journey.optDouble("distance_meters", 0);
            String mode = journey.optString("mode", "unknown");
            String started = displayTime(journey.optString("started_at", "Unknown time"));
            JSONObject geometry = journey.optJSONObject("route_geometry");
            JSONArray coordinates = geometry == null
                    ? null : geometry.optJSONArray("coordinates");
            int points = coordinates == null ? 0 : coordinates.length();
            rows[index] = String.format(
                    "%s • %s\n%.0f m • %s • %d GPS points",
                    started, mode, metres,
                    journeyDuration(journey), points);
        }

        new AlertDialog.Builder(this)
                .setTitle("Saved journeys")
                .setItems(rows, (dialog, which) -> editJourney(journeys.get(which)))
                .setPositiveButton("Close", null)
                .show();
    }

    private void editJourney(JSONObject journey) {
        Spinner reviewSpinner = new Spinner(this);
        reviewSpinner.setAdapter(new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_dropdown_item, MODE_LABELS));
        reviewSpinner.setSelection(modeIndex(journey.optString("mode", "driving")));

        double metres = journey.optDouble("distance_meters", 0);
        JSONObject geometry = journey.optJSONObject("route_geometry");
        JSONArray coordinates = geometry == null
                ? null : geometry.optJSONArray("coordinates");
        int points = coordinates == null ? 0 : coordinates.length();

        TextView message = new TextView(this);
        message.setText(String.format(
                "Start: %s\nEnd: %s\nDuration: %s\n%.0f m across %d GPS points",
                displayTime(journey.optString("started_at")),
                displayTime(journey.optString("ended_at")),
                journeyDuration(journey), metres, points));
        message.setTextSize(16);
        message.setPadding(24, 0, 24, 16);

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(0, 0, 0, 0);
        TextView previewLabel = new TextView(this);
        previewLabel.setText("Route preview");
        previewLabel.setTextSize(15);
        previewLabel.setTextColor(0xFF0A2B43);
        previewLabel.setPadding(24, 8, 24, 8);

        RoutePreviewView preview = new RoutePreviewView(this, coordinates);
        preview.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 420));

        container.addView(previewLabel);
        container.addView(preview);
        container.addView(message);
        container.addView(reviewSpinner);

        new AlertDialog.Builder(this)
                .setTitle("Journey details")
                .setView(container)
                .setNegativeButton("Close", null)
                .setNeutralButton("Delete", (dialog, which) -> confirmDeleteJourney(journey))
                .setPositiveButton("Save transport", (dialog, which) -> {
                    String selected = MODE_VALUES[reviewSpinner.getSelectedItemPosition()];
                    JourneyStore.updateMode(this, journey.optString("journey_id"), selected);
                    status.setText("Journey updated to " + selected + ".");
                    saved.setText("Saved prototype journeys: " + JourneyStore.count(this));
                })
                .show();
    }

    private void confirmDeleteJourney(JSONObject journey) {
        new AlertDialog.Builder(this)
                .setTitle("Delete journey?")
                .setMessage("This removes the saved journey and its GPS route from this device.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (dialog, which) -> {
                    try {
                        JourneyStore.delete(this, journey.optString("journey_id"));
                        saved.setText("Saved prototype journeys: " + JourneyStore.count(this));
                        status.setText("Journey deleted from this device.");
                    } catch (Exception error) {
                        status.setText("Could not delete journey.");
                    }
                })
                .show();
    }

    private String journeyDuration(JSONObject journey) {
        try {
            Instant start = Instant.parse(journey.optString("started_at"));
            Instant end = Instant.parse(journey.optString("ended_at"));
            long seconds = Math.max(0, Duration.between(start, end).getSeconds());
            long hours = seconds / 3600;
            long minutes = (seconds % 3600) / 60;
            long remainingSeconds = seconds % 60;
            if (hours > 0) return String.format("%dh %02dm", hours, minutes);
            if (minutes > 0) return String.format("%dm %02ds", minutes, remainingSeconds);
            return String.format("%ds", remainingSeconds);
        } catch (Exception ignored) {
            return "Unknown duration";
        }
    }

    private String displayTime(String value) {
        try {
            return DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm")
                    .withZone(ZoneId.systemDefault())
                    .format(Instant.parse(value));
        } catch (Exception ignored) {
            return value.replace("T", " ").replace("Z", "");
        }
    }

    private void reviewLatestJourney() {
        if (capturing) return;
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
        double metres = journey.optDouble("distance_meters", 0);
        int points = journey.optJSONObject("route_geometry") == null
                ? 0
                : journey.optJSONObject("route_geometry")
                        .optJSONArray("coordinates") == null
                ? 0
                : journey.optJSONObject("route_geometry")
                        .optJSONArray("coordinates").length();
        message.setText(String.format(
                "Latest journey: %.0f m across %d GPS points. Was it recorded using the right transport?",
                metres, points));
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
                    saved.setText("Saved prototype journeys: " + JourneyStore.count(this));
                })
                .show();
    }

    private int modeIndex(String value) {
        for (int index = 0; index < MODE_VALUES.length; index++) {
            if (MODE_VALUES[index].equals(value)) return index;
        }
        return 0;
    }
}
