package com.roadprints.capture;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int LOCATION_REQUEST = 41;
    private static final int TRACKING_REQUEST = 42;
    private static final String PENDING_TRACKING = "pending_tracking_permission";
    private static final String[] MODE_LABELS = {
            "Driving", "Walking", "Bus", "Train", "Cycling", "Plane", "Ferry", "Unknown"
    };
    private static final String[] MODE_VALUES = {
            "driving", "walking", "bus", "train", "cycling", "plane", "ferry", "unknown"
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
    private boolean pendingTrackingPermission;
    private final ExecutorService archiveIo = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private TextView deleteRoadAction;
    private TextView deleteFootAction;
    private TextView deleteAllAction;
    private TextView debugLink;
    private int archiveSummaryGeneration;

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
            if (message != null && status != null) status.setText(message);

            double metres = intent.getDoubleExtra(CaptureService.EXTRA_DISTANCE, 0);
            int points = intent.getIntExtra(CaptureService.EXTRA_POINTS, 0);
            if (active && distance != null) {
                distance.setText(String.format(
                        "Distance: %.0f m - %d points", metres, points));
            } else {
                if (saved != null) refreshSavedCount();
                if (distance != null) distance.setText("Distance: 0 m");
            }
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        pendingTrackingPermission = state != null && state.getBoolean(PENDING_TRACKING, false);
        if (!isTrackingSettingsScreen()) {
            if (CaptureService.isArmed(this)) repairTrackingSubscription();
            else if (CaptureService.isActive(this)) resumeActiveCapture();
            startActivity(new Intent(this, MapActivity.class));
            finish();
            return;
        }
        registerCaptureReceiver();
        buildScreen();
        capturing = CaptureService.isActive(this);
        tracking = CaptureService.isArmed(this);
        updateCaptureButton();
        updateTrackingButton();
        if (tracking) repairTrackingSubscription();
        else if (capturing) resumeActiveCapture();
        if (modeSpinner != null) modeSpinner.postDelayed(this::reviewLatestJourney, 350L);
        if (getIntent().getBooleanExtra("onboarding_enable_tracking", false)) {
            getIntent().removeExtra("onboarding_enable_tracking");
            if (!tracking && !pendingTrackingPermission) toggleTracking();
        }
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putBoolean(PENDING_TRACKING, pendingTrackingPermission);
        super.onSaveInstanceState(state);
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode != TRACKING_REQUEST || !pendingTrackingPermission) return;
        pendingTrackingPermission = false;
        // Check actual permission state: notification approval is optional, and
        // Android can return an empty result when the permission dialog closes.
        if (hasTrackingPermissions()) {
            startAutomaticTracking();
        } else {
            status.setText("Automatic tracking is off. Allow precise location and physical activity "
                    + "in Utilities > Permissions, then enable automatic tracking in Tracking settings.");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateDebugLink();
        if (captureButton != null) {
            capturing = CaptureService.isActive(this);
            tracking = CaptureService.isArmed(this);
            updateCaptureButton();
            updateTrackingButton();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (isTrackingSettingsScreen()) unregisterReceiver(captureReceiver);
        archiveIo.shutdownNow();
        super.onDestroy();
    }

    private void registerCaptureReceiver() {
        IntentFilter filter = new IntentFilter(CaptureService.ACTION_UPDATE);
        androidx.core.content.ContextCompat.registerReceiver(this, captureReceiver, filter,
                androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    private boolean isTrackingSettingsScreen() { return getIntent().getBooleanExtra("tracking_settings_screen", false); }

    private void buildScreen() {
        getWindow().setStatusBarColor(0xFF0B1C50);
        getWindow().setNavigationBarColor(0xFF10275D);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF0B1C50);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(24), dp(26), dp(24), dp(28));
        LinearLayout brand = new LinearLayout(this);
        brand.setGravity(Gravity.CENTER_VERTICAL);
        ImageView mark = new ImageView(this);
        mark.setImageResource(R.drawable.roadprints_mark);
        mark.setContentDescription("Roadprints");
        mark.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        brand.addView(mark, new LinearLayout.LayoutParams(dp(40), dp(40)));
        LinearLayout brandIdentity = new LinearLayout(this);
        brandIdentity.setOrientation(LinearLayout.VERTICAL);
        TextView wordmark = text("roadprints", 22, Color.WHITE, true);
        wordmark.setPadding(dp(10), 0, 0, 0);
        brandIdentity.addView(wordmark);
        TextView version = text("Version " + BuildConfig.VERSION_NAME
                + " (" + BuildConfig.VERSION_CODE + ")", 10, 0xFFB9C5D8, false);
        version.setPadding(dp(10), 0, 0, 0);
        brandIdentity.addView(version);
        brand.addView(brandIdentity, new LinearLayout.LayoutParams(0, -2, 1));
        content.addView(brand);
        TextView eyebrow = text("YOUR TRAVEL RECORD", 13, 0xFF67D5CC, true);
        eyebrow.setPadding(0, dp(30), 0, dp(4));
        content.addView(eyebrow);

        if (isTrackingSettingsScreen()) {
            content.addView(text("Tracking settings", 32, Color.WHITE, true));
            TextView subtitle = text("Choose a journey type and control automatic or manual recording.",
                    16, 0xFFD3DCED, false);
            subtitle.setPadding(0, dp(8), 0, dp(16));
            content.addView(subtitle);
            content.addView(text("Journey type", 16, 0xFF67D5CC, true));
            modeSpinner = new Spinner(this);
            ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                    this, android.R.layout.simple_spinner_item, MODE_LABELS) {
                private TextView whiteText(android.view.View row) {
                    TextView label = (TextView) row;
                    label.setTextColor(Color.WHITE);
                    label.setBackgroundColor(0xFF182F66);
                    return label;
                }
                @Override public android.view.View getView(int p, android.view.View c, android.view.ViewGroup parent) {
                    return whiteText(super.getView(p, c, parent));
                }
                @Override public android.view.View getDropDownView(int p, android.view.View c, android.view.ViewGroup parent) {
                    return whiteText(super.getDropDownView(p, c, parent));
                }
            };
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            modeSpinner.setAdapter(adapter);
            modeSpinner.setBackground(roundedBackground(0xFF182F66, 0xFF496096, dp(12)));
            modeSpinner.setPadding(dp(14), 0, dp(14), 0);
            LinearLayout.LayoutParams modeParams = new LinearLayout.LayoutParams(-1, dp(54));
            modeParams.bottomMargin = dp(14);
            content.addView(modeSpinner, modeParams);
            trackingButton = new Button(this);
            trackingButton.setOnClickListener(v -> toggleTracking());
            styleAction(trackingButton, 0xFFF7C450, 0xFF0B1C50);
            LinearLayout.LayoutParams trackingParams = new LinearLayout.LayoutParams(-1, dp(54));
            trackingParams.bottomMargin = dp(12);
            content.addView(trackingButton, trackingParams);
            captureButton = new Button(this);
            captureButton.setOnClickListener(v -> toggleCapture());
            styleAction(captureButton, 0xFF263F7C, Color.WHITE);
            LinearLayout.LayoutParams captureParams = new LinearLayout.LayoutParams(-1, dp(54));
            captureParams.bottomMargin = dp(18);
            content.addView(captureButton, captureParams);
            LinearLayout live = card();
            status = text("Ready to capture.", 15, 0xFF67D5CC, false);
            distance = text("Distance: 0 m", 20, Color.WHITE, true);
            distance.setPadding(0, dp(12), 0, 0);
            live.addView(status); live.addView(distance);
            content.addView(live);
        } else {
            Button utilities = new Button(this);
            utilities.setText("UTILITIES");
            styleAction(utilities, 0xFF263F7C, Color.WHITE);
            utilities.setOnClickListener(v -> startActivity(new Intent(this, UtilitiesActivity.class)));
            LinearLayout.LayoutParams utilityParams = new LinearLayout.LayoutParams(-1, dp(52));
            utilityParams.bottomMargin = dp(28);
            content.addView(utilities, utilityParams);
            content.addView(text("Your journeys", 21, Color.WHITE, true));
            saved = text("Loading saved journeys…", 15, 0xFFD3DCED, false);
            saved.setPadding(0, dp(5), 0, dp(12));
            content.addView(saved);
            LinearLayout nav = new LinearLayout(this);
            Button map = new Button(this); map.setText("OPEN MAP");
            styleAction(map, 0xFF263F7C, Color.WHITE);
            map.setOnClickListener(v -> startActivity(new Intent(this, MapActivity.class)));
            historyButton = new Button(this); historyButton.setText("OPEN JOURNEYS");
            styleAction(historyButton, 0xFF263F7C, Color.WHITE);
            historyButton.setOnClickListener(v -> startActivity(new Intent(this, JourneyListActivity.class)));
            nav.addView(map, new LinearLayout.LayoutParams(0, dp(50), 1));
            LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(0, dp(50), 1);
            hp.leftMargin = dp(10); nav.addView(historyButton, hp); content.addView(nav);
        }
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        root.addView(GrowingStatusControl.create(this));
        TextView utilityBack = null;
        if (isTrackingSettingsScreen()) {
            utilityBack = RoadprintsHeader.backLink(this, "BACK TO UTILITIES", this::finish);
            root.addView(utilityBack, new LinearLayout.LayoutParams(-1, dp(58)));
        }
        final TextView bottomBack = utilityBack;
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            int systemTop;
            int systemBottom;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                android.graphics.Insets bars = insets.getInsets(
                        android.view.WindowInsets.Type.systemBars());
                systemTop = bars.top;
                systemBottom = bars.bottom;
            } else {
                systemTop = insets.getSystemWindowInsetTop();
                systemBottom = insets.getSystemWindowInsetBottom();
            }
            content.setPadding(dp(24), dp(26) + systemTop, dp(24),
                    dp(28) + (bottomBack == null ? systemBottom : 0));
            if (bottomBack != null) {
                bottomBack.setPadding(dp(24), 0, dp(24), systemBottom);
                LinearLayout.LayoutParams backParams =
                        (LinearLayout.LayoutParams) bottomBack.getLayoutParams();
                backParams.height = dp(58) + systemBottom;
                bottomBack.setLayoutParams(backParams);
            }
            return insets;
        });
        setContentView(root);
        root.requestApplyInsets();
        if (!isTrackingSettingsScreen()) refreshArchiveSummary();
    }

    private void showDebugReport() {
        String report = CrashReporter.getDiagnosticReports(this);
        boolean available = !report.isEmpty();
        TextView details = text(available
                        ? "Review this report before copying. It stays on this device until you choose to copy it.\\n\\n" + report
                        : "No reports have been saved on this device yet. If a journey match fails or Roadprints crashes, tap debug here. Reports stay on this device and are not sent automatically.",
                13, 0xFFD3DCED, false);
        details.setTextIsSelectable(true);
        details.setPadding(dp(4), dp(8), dp(4), dp(8));
        ScrollView reportScroll = new ScrollView(this);
        reportScroll.addView(details);

        AlertDialog.Builder dialog = new AlertDialog.Builder(this)
                .setTitle(available ? "Debug reports" : "Debug")
                .setView(reportScroll)
                .setNegativeButton(available ? "Close" : "OK", null);
        if (available) {
            dialog.setPositiveButton("Copy report", (which, button) -> {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                clipboard.setPrimaryClip(ClipData.newPlainText("Roadprints debug reports", report));
                Toast.makeText(this, "Debug report copied", Toast.LENGTH_SHORT).show();
            });
            dialog.setNeutralButton("Clear reports", (which, button) -> {
                CrashReporter.clear(this);
                updateDebugLink();
                Toast.makeText(this, "Debug reports cleared", Toast.LENGTH_SHORT).show();
            });
        }
        dialog.show();
    }

    private void updateDebugLink() {
        if (debugLink != null) {
            debugLink.setText(CrashReporter.hasReports(this) ? "debug · report ready" : "debug");
        }
    }

    private LinearLayout card() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(18), dp(16), dp(18), dp(16));
        layout.setBackground(roundedBackground(0xFF233B78, 0xFF233B78, dp(18)));
        return layout;
    }

    private TextView text(String value, float size, int colour, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(colour);
        if (bold) view.setTypeface(null, Typeface.BOLD);
        return view;
    }

    private void styleAction(Button button, int background, int foreground) {
        button.setTextColor(foreground);
        button.setTextSize(14);
        button.setTypeface(null, Typeface.BOLD);
        button.setAllCaps(true);
        button.setBackground(roundedBackground(background, background, dp(14)));
        button.setElevation(dp(2));
    }

    private GradientDrawable roundedBackground(int colour, int stroke, int radius) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(colour);
        shape.setCornerRadius(radius);
        shape.setStroke(dp(1), stroke);
        return shape;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void refreshArchiveSummary() {
        if (saved == null) return;
        final int generation = ++archiveSummaryGeneration;
        saved.setText("Loading saved journeys…");
        saved.setOnClickListener(null);
        archiveIo.execute(() -> {
            List<JSONObject> journeys;
            try {
                journeys = JourneyStore.allSummaries(getApplicationContext());
            } catch (Exception error) {
                mainHandler.post(() -> {
                    if (isFinishing() || generation != archiveSummaryGeneration) return;
                    saved.setText("Saved journeys could not be loaded. Tap here to retry.");
                    saved.setOnClickListener(view -> refreshArchiveSummary());
                });
                return;
            }
            int roadCount = 0;
            int footCount = 0;
            for (JSONObject journey : journeys) {
                String mode = journey.optString("mode", "unknown");
                if ("driving".equals(mode) || "bus".equals(mode) || "cycling".equals(mode)) roadCount++;
                if ("walking".equals(mode) || "running".equals(mode)
                        || "pedestrian".equals(mode)) footCount++;
            }
            final int savedCount = journeys.size();
            final int roads = roadCount;
            final int foot = footCount;
            mainHandler.post(() -> {
                if (isFinishing() || generation != archiveSummaryGeneration) return;
                saved.setText("Saved journeys: " + savedCount);
                saved.setOnClickListener(null);
                setDeleteActionEnabled(deleteRoadAction, roads > 0);
                setDeleteActionEnabled(deleteFootAction, foot > 0);
                setDeleteActionEnabled(deleteAllAction, savedCount > 0);
            });
        });
    }

    private void refreshSavedCount() {
        if (saved == null) return;
        archiveIo.execute(() -> {
            int savedCount;
            try {
                savedCount = JourneyStore.count(getApplicationContext());
            } catch (Exception error) {
                mainHandler.post(() -> {
                    if (isFinishing()) return;
                    saved.setText("Saved journeys could not be refreshed. Tap here to retry.");
                    saved.setOnClickListener(view -> refreshArchiveSummary());
                });
                return;
            }
            final int count = savedCount;
            mainHandler.post(() -> {
                if (isFinishing()) return;
                saved.setText("Saved journeys: " + count);
                saved.setOnClickListener(null);
            });
        });
    }

    private void setDeleteActionEnabled(TextView button, boolean enabled) {
        if (button == null) return;
        button.setClickable(enabled);
        button.setFocusable(enabled);
        button.setTextColor(enabled ? 0xFFFFB7B7 : 0xFF9FB3D0);
        button.setAlpha(enabled ? 1f : 0.75f);
    }

    private TextView deleteButton(String label, boolean enabled) {
        TextView button = new TextView(this);
        button.setText(label);
        button.setTextSize(12);
        button.setGravity(Gravity.CENTER);
        button.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        button.setTextColor(enabled ? 0xFFFFB7B7 : 0xFF9FB3D0);
        button.setClickable(enabled);
        button.setFocusable(enabled);
        button.setAlpha(enabled ? 1f : 0.75f);
        return button;
    }

    private LinearLayout.LayoutParams deleteButtonParams() {
        return new LinearLayout.LayoutParams(0, 48, 1f);
    }

    private void confirmDeleteData(String title, String message, String[] modes) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (dialog, which) -> {
                    status.setText("Deleting journeys…");
                    archiveIo.execute(() -> {
                        final int deleted;
                        try {
                            deleted = JourneyStore.deleteByModes(getApplicationContext(), modes);
                        } catch (Exception error) {
                            mainHandler.post(() -> {
                                if (!isFinishing()) status.setText("Could not delete journeys.");
                            });
                            return;
                        }
                        mainHandler.post(() -> {
                            if (isFinishing()) return;
                            status.setText(deleted + " journey"
                                    + (deleted == 1 ? "" : "s") + " deleted from this device.");
                            refreshArchiveSummary();
                        });
                    });
                })
                .show();
    }

    private void confirmDeleteAll() {
        new AlertDialog.Builder(this)
                .setTitle("Delete all saved data?")
                .setMessage("This removes every saved journey and returns you to the Roadprints welcome screen.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete all", (dialog, which) -> {
                    status.setText("Deleting saved journeys…");
                    archiveIo.execute(() -> {
                        try {
                            JourneyStore.deleteAll(getApplicationContext());
                        } catch (Exception error) {
                            mainHandler.post(() -> {
                                if (!isFinishing()) status.setText("Could not delete saved journeys.");
                            });
                            return;
                        }
                        mainHandler.post(() -> {
                            if (isFinishing()) return;
                            getSharedPreferences("roadprints_onboarding", MODE_PRIVATE)
                                    .edit().remove("complete").apply();
                            startActivity(new Intent(this, OnboardingActivity.class));
                            finish();
                        });
                    });
                })
                .show();
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

    private void resumeActiveCapture() {
        Intent resume = new Intent(this, CaptureService.class)
                .setAction(CaptureService.ACTION_RESUME);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(resume);
        } else {
            startService(resume);
        }
    }

    private void toggleTracking() {
        if (tracking || CaptureService.isArmed(this)) {
            Intent stop = new Intent(this, CaptureService.class)
                    .setAction(CaptureService.ACTION_DISARM);
            startService(stop);
            return;
        }

        if (!hasTrackingPermissions()) {
            if (pendingTrackingPermission) return;
            pendingTrackingPermission = true;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                requestPermissions(new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.ACTIVITY_RECOGNITION,
                        Manifest.permission.POST_NOTIFICATIONS
                }, TRACKING_REQUEST);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                requestPermissions(new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.ACTIVITY_RECOGNITION
                }, TRACKING_REQUEST);
            } else {
                requestPermissions(new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                }, TRACKING_REQUEST);
            }
            return;
        }
        startAutomaticTracking();
    }

    private boolean hasTrackingPermissions() {
        boolean locationGranted = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        boolean activityGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
                || checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION)
                == PackageManager.PERMISSION_GRANTED;

        return locationGranted && activityGranted;
    }

    private void startAutomaticTracking() {
        // Permission completion enables tracking; it must never toggle it off.
        if (tracking || CaptureService.isArmed(this)) return;
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
        archiveIo.execute(() -> {
            final List<JSONObject> journeys;
            try {
                journeys = JourneyStore.allSummaries(getApplicationContext());
            } catch (Exception error) {
                mainHandler.post(() -> {
                    if (!isFinishing()) status.setText("Could not load saved journeys.");
                });
                return;
            }
            mainHandler.post(() -> showJourneyHistory(journeys));
        });
    }

    private void showJourneyHistory(List<JSONObject> journeys) {
        if (isFinishing()) return;
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
            int points = journey.optInt("_route_point_count", coordinates == null ? 0 : coordinates.length());
            rows[index] = String.format(
                    "%s • %s\n%s • %s • %d GPS points",
                    started, mode, DistanceUnits.format(this, metres),
                    journeyDuration(journey), points);
        }

        new AlertDialog.Builder(this)
                .setTitle("Saved journeys")
                .setItems(rows, (dialog, which) -> {
                    String journeyId = journeys.get(which).optString("journey_id");
                    archiveIo.execute(() -> {
                        JSONObject fullJourney = JourneyStore.get(getApplicationContext(), journeyId);
                        mainHandler.post(() -> {
                            if (!isFinishing() && fullJourney != null) editJourney(fullJourney);
                        });
                    });
                })
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
                "Start: %s\nEnd: %s\nDuration: %s\n%s across %d GPS points",
                displayTime(journey.optString("started_at")),
                displayTime(journey.optString("ended_at")),
                journeyDuration(journey), DistanceUnits.format(this, metres), points));
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

        Button shareButton = new Button(this);
        shareButton.setText("Share journey JSON");
        shareButton.setOnClickListener(v -> shareJourney(journey));

        container.addView(shareButton);
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
                    status.setText("Saving journey…");
                    archiveIo.execute(() -> {
                        try {
                            JourneyStore.updateMode(getApplicationContext(),
                                    journey.optString("journey_id"), selected);
                            mainHandler.post(() -> {
                                if (isFinishing()) return;
                                status.setText("Journey updated to " + selected + ".");
                                refreshSavedCount();
                            });
                        } catch (Exception error) {
                            mainHandler.post(() -> {
                                if (!isFinishing()) status.setText("Could not update journey.");
                            });
                        }
                    });
                })
                .show();
    }

    private void shareJourney(JSONObject journey) {
        try {
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("application/json");
            share.putExtra(Intent.EXTRA_SUBJECT, "Roadprints journey");
            share.putExtra(Intent.EXTRA_TEXT, journey.toString(2));
            startActivity(Intent.createChooser(share, "Share journey data"));
        } catch (Exception error) {
            status.setText("Could not share journey data.");
        }
    }

    private void confirmDeleteJourney(JSONObject journey) {
        new AlertDialog.Builder(this)
                .setTitle("Delete journey?")
                .setMessage("This removes the saved journey and its GPS route from this device.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (dialog, which) -> {
                    status.setText("Deleting journey…");
                    archiveIo.execute(() -> {
                        try {
                            JourneyStore.delete(getApplicationContext(),
                                    journey.optString("journey_id"));
                            mainHandler.post(() -> {
                                if (isFinishing()) return;
                                refreshSavedCount();
                                status.setText("Journey deleted from this device.");
                            });
                        } catch (Exception error) {
                            mainHandler.post(() -> {
                                if (!isFinishing()) status.setText("Could not delete journey.");
                            });
                        }
                    });
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
        if (capturing || !isTrackingSettingsScreen()) return;
        archiveIo.execute(() -> {
            JSONObject journey = JourneyStore.latest(getApplicationContext());
            mainHandler.post(() -> showLatestJourneyReview(journey));
        });
    }

    private void showLatestJourneyReview(JSONObject journey) {
        if (isFinishing() || capturing || journey == null
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
                "Latest journey: %s across %d GPS points. Was it recorded using the right transport?",
                DistanceUnits.format(this, metres), points));
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
                    status.setText("Saving journey…");
                    archiveIo.execute(() -> {
                        try {
                            JourneyStore.updateMode(getApplicationContext(),
                                    journey.optString("journey_id"), selected);
                            mainHandler.post(() -> {
                                if (isFinishing()) return;
                                status.setText("Journey updated to " + selected + ".");
                                refreshSavedCount();
                            });
                        } catch (Exception error) {
                            mainHandler.post(() -> {
                                if (!isFinishing()) status.setText("Could not update journey.");
                            });
                        }
                    });
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



