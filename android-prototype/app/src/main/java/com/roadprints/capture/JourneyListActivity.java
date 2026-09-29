package com.roadprints.capture;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.text.InputType;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.Duration;
import java.time.Instant;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class JourneyListActivity extends Activity {
    private static final String API_BASE_URL = "https://uk-road-tracker-api.onrender.com";
    private final ExecutorService processor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static final String[] FILTER_LABELS = {
            "All", "🚗 Driving", "👟 On foot", "🚌 Bus",
            "🚆 Train", "🚲 Cycling", "✈️ Flight", "⛴ Ferry"
    };
    private static final String[] FILTER_VALUES = {
            "all", "driving", "walking", "bus", "train", "cycling", "plane", "ferry"
    };

    private LinearLayout journeyList;
    private TextView count;
    private TextView readinessSummary;
    private List<JSONObject> journeys;
    private int activeFilter = 0;
    private final List<TextView> filterChips = new ArrayList<>();

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Window window = getWindow();
        window.setStatusBarColor(0xFF0B1C50);
        window.setNavigationBarColor(0xFF10275D);
        buildScreen();
        journeys = JourneyStore.all(this);
        render();
    }

    @Override
    protected void onDestroy() {
        processor.shutdownNow();
        super.onDestroy();
    }

    private void buildScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF0B1C50);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(36, 26, 36, 12);

        LinearLayout brandRow = new LinearLayout(this);
        brandRow.setGravity(Gravity.CENTER_VERTICAL);
        brandRow.setPadding(0, 0, 0, 28);

        ImageView logoMark = new ImageView(this);
        logoMark.setImageResource(R.drawable.roadprints_mark);
        logoMark.setContentDescription("Roadprints");
        logoMark.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        LinearLayout.LayoutParams markParams = new LinearLayout.LayoutParams(42, 42);
        markParams.setMargins(0, 0, 10, 0);
        brandRow.addView(logoMark, markParams);

        TextView logo = new TextView(this);
        logo.setText("roadprints");
        logo.setTextSize(22);
        logo.setTypeface(null, android.graphics.Typeface.BOLD);
        logo.setTextColor(Color.WHITE);
        logo.setPadding(0, 0, 0, 0);
        brandRow.addView(logo);

        LinearLayout headingRow = new LinearLayout(this);
        headingRow.setGravity(Gravity.CENTER_VERTICAL);
        headingRow.setPadding(0, 0, 0, 8);

        LinearLayout heading = new LinearLayout(this);
        heading.setOrientation(LinearLayout.VERTICAL);
        heading.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        TextView eyebrow = new TextView(this);
        eyebrow.setText("YOUR TRAVEL RECORD");
        eyebrow.setTextSize(13);
        eyebrow.setTypeface(null, android.graphics.Typeface.BOLD);
        eyebrow.setTextColor(0xFF67D5CC);

        TextView title = new TextView(this);
        title.setText("Journeys");
        title.setTextSize(36);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setTextColor(Color.WHITE);

        heading.addView(eyebrow);
        heading.addView(title);

        count = new TextView(this);
        count.setTextSize(22);
        count.setTypeface(null, android.graphics.Typeface.BOLD);
        count.setTextColor(0xFFF7C450);
        count.setGravity(Gravity.CENTER);
        count.setPadding(22, 14, 22, 14);
        count.setBackground(pill(0x332F4D91, 0x334D6CA6, 40));

        headingRow.addView(heading);
        headingRow.addView(count);

        TextView intro = new TextView(this);
        intro.setText("Choose a journey to inspect its route and correct any section that does not belong.");
        intro.setTextSize(16);
        intro.setTextColor(0xFFD3DCED);
        intro.setPadding(0, 8, 0, 12);

        readinessSummary = new TextView(this);
        readinessSummary.setTextSize(12);
        readinessSummary.setTextColor(0xFFB9C5D8);
        readinessSummary.setLineSpacing(0, 1.1f);
        readinessSummary.setPadding(0, 0, 0, 16);

        HorizontalScrollView filterScroll = new HorizontalScrollView(this);
        filterScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout filters = new LinearLayout(this);
        filters.setOrientation(LinearLayout.HORIZONTAL);
        for (int index = 0; index < FILTER_LABELS.length; index++) {
            final int selected = index;
            TextView filter = new TextView(this);
            filter.setText(FILTER_LABELS[index]);
            filter.setTextSize(13);
            filter.setGravity(Gravity.CENTER);
            filter.setTypeface(null, android.graphics.Typeface.BOLD);
            filter.setPadding(18, 0, 18, 0);
            filter.setMinHeight(48);
            filterChips.add(filter);
            filter.setOnClickListener(v -> {
                activeFilter = selected;
                updateFilterStyles();
                render();
            });
            LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, 48);
            chipParams.setMargins(0, 0, 10, 0);
            filters.addView(filter, chipParams);
        }
        filterScroll.addView(filters);
        updateFilterStyles();

        journeyList = new LinearLayout(this);
        journeyList.setOrientation(LinearLayout.VERTICAL);
        journeyList.setPadding(0, 18, 0, 18);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(journeyList);
        scroll.setFillViewport(true);

        content.addView(brandRow);
        content.addView(headingRow);
        content.addView(intro);
        content.addView(readinessSummary);
        content.addView(filterScroll);
        content.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));

        root.addView(content, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        View bottomNavigation = buildBottomNavigation();
        root.addView(bottomNavigation);
        setContentView(root);
        applySystemBarInsets(root, content, bottomNavigation);
    }

    private void applySystemBarInsets(View root, View content, View bottomNavigation) {
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                android.graphics.Insets bars =
                        insets.getInsets(WindowInsets.Type.systemBars());
                content.setPadding(36, 26 + bars.top, 36, 12);
                bottomNavigation.setPadding(8, 0, 8, 0);
                LinearLayout.LayoutParams navParams =
                        (LinearLayout.LayoutParams) bottomNavigation.getLayoutParams();
                navParams.height = 76;
                navParams.bottomMargin = bars.bottom;
                bottomNavigation.setLayoutParams(navParams);
            }
            return insets;
        });
        root.requestApplyInsets();
    }

    private View buildBottomNavigation() {
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        nav.setPadding(8, 0, 8, 0);
        nav.setBackgroundColor(0xFF10275D);

        int[] iconResources = {
                R.drawable.ic_nav_map, R.drawable.ic_nav_journeys, R.drawable.ic_nav_progress,
                R.drawable.ic_nav_achievements, R.drawable.ic_nav_collections
        };
        String[] labels = {"Map", "Journeys", "Progress", "Achievements", "Collections"};
        for (int index = 0; index < labels.length; index++) {
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            item.setPadding(0, 4, 0, 0);
            item.setClipChildren(false);

            ImageView icon = new ImageView(this);
            icon.setImageResource(iconResources[index]);
            icon.setContentDescription(labels[index]);
            icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            icon.setColorFilter(index == 1 ? 0xFFF7C450 : 0xFFB9C5D8,
                    android.graphics.PorterDuff.Mode.SRC_IN);
            item.addView(icon, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 32));

            TextView label = new TextView(this);
            label.setText(labels[index]);
            label.setTextSize(10);
            label.setGravity(Gravity.CENTER);
            label.setIncludeFontPadding(false);
            label.setMaxLines(1);
            label.setTextColor(index == 1 ? 0xFFF7C450 : 0xFFB9C5D8);
            item.addView(label, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 24));

            LinearLayout.LayoutParams itemParams = new LinearLayout.LayoutParams(0, 68, 1);
            nav.addView(item, itemParams);
        }
        return nav;
    }

    private void render() {
        if (journeyList == null || journeys == null) return;
        journeyList.removeAllViews();
        updateReadinessSummary();
        int visible = 0;
        for (JSONObject journey : journeys) {
            if (!"all".equals(FILTER_VALUES[activeFilter])
                    && !FILTER_VALUES[activeFilter].equals(
                    journey.optString("mode", "unknown"))) continue;
            journeyList.addView(createCard(journey));
            visible++;
        }
        count.setText(String.valueOf(visible));
        if (visible == 0) {
            TextView empty = new TextView(this);
            empty.setText("No journeys match this filter.");
            empty.setTextSize(16);
            empty.setTextColor(0xFFD3DCED);
            empty.setPadding(0, 28, 0, 28);
            journeyList.addView(empty);
        }
    }

    private View createCard(JSONObject journey) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(22, 20, 22, 20);
        card.setBackground(roundRect(0xFF233B78, 0xFF46649E, 28));
        card.setElevation(2);

        String mode = journey.optString("mode", "unknown");
        double metres = journey.optDouble("distance_meters", 0);
        JSONObject geometry = journey.optJSONObject("route_geometry");
        JSONArray coordinates = geometry == null
                ? null : geometry.optJSONArray("coordinates");
        int points = coordinates == null ? 0 : coordinates.length();

        TextView label = new TextView(this);
        label.setText(displayMode(mode).toUpperCase() + "  •  "
                + displayTime(journey.optString("started_at")).toUpperCase());
        label.setTextSize(12);
        label.setTypeface(null, android.graphics.Typeface.BOLD);
        label.setTextColor(0xFF67D5CC);

        TextView heading = new TextView(this);
        String savedTitle = journey.optString("title", "");
        JSONObject source = journey.optJSONObject("source");
        boolean imported = source != null
                && "timeline_import".equals(source.optString("type"));
        heading.setText(savedTitle.length() > 0 ? savedTitle
                : imported ? "Imported journey" : "Captured journey");
        heading.setTextSize(23);
        heading.setTypeface(null, android.graphics.Typeface.BOLD);
        heading.setTextColor(Color.WHITE);
        heading.setPadding(0, 8, 0, 0);

        TextView summary = new TextView(this);
        summary.setText(String.format("%.1f mi  •  %s  •  %d GPS points",
                metres / 1609.344, journeyDuration(journey), points));
        summary.setTextSize(16);
        summary.setTextColor(0xFFD3DCED);
        summary.setPadding(0, 4, 0, 0);

        TextView evidence = new TextView(this);
        String statusText = journeyStatusText(journey, points);
        evidence.setText(statusText);
        evidence.setTextSize(14);
        evidence.setTextColor(statusText.startsWith("Insufficient")
                || statusText.startsWith("Processing failed") ? 0xFFF7C450 : 0xFF67D5CC);
        evidence.setPadding(0, 16, 0, 16);

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        TextView inspect = actionButton("View & refine", 0xFF102047, Color.WHITE);
        inspect.setOnClickListener(v -> showDetails(journey));
        actions.addView(inspect, new LinearLayout.LayoutParams(0, 52, 1));

        card.addView(label);
        card.addView(heading);
        card.addView(summary);
        card.addView(evidence);
        card.addView(actions);
        card.setOnClickListener(v -> showDetails(journey));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, 16);
        card.setLayoutParams(params);
        return card;
    }

    private TextView actionButton(String text, int background, int foreground) {
        TextView button = new TextView(this);
        button.setText(text);
        button.setTextSize(14);
        button.setGravity(Gravity.CENTER);
        button.setTypeface(null, android.graphics.Typeface.BOLD);
        button.setTextColor(foreground);
        button.setPadding(12, 0, 12, 0);
        button.setBackground(roundRect(background, 0xFF46649E, 18));
        return button;
    }

    private void updateFilterStyles() {
        for (int index = 0; index < filterChips.size(); index++) {
            boolean selected = index == activeFilter;
            TextView chip = filterChips.get(index);
            chip.setTextColor(selected ? 0xFF0B1C50 : Color.WHITE);
            chip.setBackground(roundRect(
                    selected ? 0xFFF7C450 : 0x1AFFFFFF,
                    selected ? 0xFFF7C450 : 0xFF46649E,
                    28));
        }
    }

    private GradientDrawable roundRect(int fill, int stroke, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(radius);
        drawable.setStroke(1, stroke);
        return drawable;
    }

    private GradientDrawable pill(int fill, int stroke, int radius) {
        return roundRect(fill, stroke, radius);
    }

    private void showDetails(JSONObject journey) {
        JSONObject geometry = journey.optJSONObject("route_geometry");
        JSONArray coordinates = geometry == null
                ? null : geometry.optJSONArray("coordinates");
        TextView previewLabel = new TextView(this);
        previewLabel.setText("ROUTE PREVIEW");
        previewLabel.setTextSize(12);
        previewLabel.setTypeface(null, android.graphics.Typeface.BOLD);
        previewLabel.setTextColor(0xFF506070);
        previewLabel.setPadding(24, 16, 24, 8);

        RoutePreviewView preview = new RoutePreviewView(this, coordinates);
        preview.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 360));

        double metres = journey.optDouble("distance_meters", 0);
        int points = coordinates == null ? 0 : coordinates.length();
        TextView message = new TextView(this);
        message.setText(String.format(
                "Start: %s\nEnd: %s\nDuration: %s\n%s • %.0f m • %d GPS points",
                displayTime(journey.optString("started_at")),
                displayTime(journey.optString("ended_at")),
                journeyDuration(journey),
                displayMode(journey.optString("mode", "unknown")),
                metres, points));
        message.setTextSize(16);
        message.setTextColor(0xFFD3DCED);
        message.setPadding(24, 16, 24, 16);

        TextView transportLabel = new TextView(this);
        transportLabel.setText("Transport type");
        transportLabel.setTextSize(14);
        transportLabel.setTextColor(0xFF67D5CC);
        transportLabel.setPadding(24, 12, 24, 0);

        String[] transportModes = {"driving", "walking", "cycling", "bus",
                "train", "plane", "ferry", "running", "unknown"};
        String[] transportLabels = {"Driving", "On foot", "Cycling", "Bus",
                "Train", "Plane", "Ferry", "Running", "Unknown"};
        Spinner transport = new Spinner(this);
        ArrayAdapter<String> transportAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_dropdown_item, transportLabels);
        transport.setAdapter(transportAdapter);
        String currentMode = journey.optString("mode", "unknown");
        for (int index = 0; index < transportModes.length; index++) {
            if (transportModes[index].equals(currentMode)) {
                transport.setSelection(index);
                break;
            }
        }
        transport.setBackgroundColor(0xFF0B1C50);
        transport.setPadding(24, 0, 24, 0);

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(0, 0, 0, 8);
        container.setBackground(roundRect(0xFF0B1C50, 0xFF46649E, 24));

        EditText titleInput = new EditText(this);
        titleInput.setHint("Name this journey");
        titleInput.setText(journey.optString("title", ""));
        titleInput.setTextSize(18);
        titleInput.setSingleLine(true);
        titleInput.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        titleInput.setTextColor(Color.WHITE);
        titleInput.setHintTextColor(0xFFB9C5D8);
        titleInput.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(0xFFF7C450));
        titleInput.setPadding(24, 8, 24, 8);

        TextView titleLabel = new TextView(this);
        titleLabel.setText("Journey name");
        titleLabel.setTextSize(14);
        titleLabel.setTextColor(0xFF67D5CC);
        titleLabel.setPadding(24, 12, 24, 0);

        container.addView(previewLabel);
        container.addView(preview);
        container.addView(message);
        container.addView(titleLabel);
        container.addView(titleInput);
        container.addView(transportLabel);
        container.addView(transport);

        Button saveTitle = styledModalButton("SAVE JOURNEY NAME", 0xFF102047, Color.WHITE);
        saveTitle.setOnClickListener(v -> {
            JourneyStore.updateTitle(this, journey.optString("journey_id"),
                    titleInput.getText().toString());
            journeys = JourneyStore.all(this);
            render();
            Toast.makeText(this, "Journey name saved", Toast.LENGTH_SHORT).show();
        });
        container.addView(saveTitle);

        Button saveTransport = styledModalButton("SAVE TRANSPORT TYPE", 0xFFF7C450, 0xFF0B1C50);
        saveTransport.setOnClickListener(v -> {
            String selectedMode = transportModes[transport.getSelectedItemPosition()];
            JourneyStore.updateMode(this, journey.optString("journey_id"), selectedMode);
            journeys = JourneyStore.all(this);
            render();
            Toast.makeText(this, "Journey updated", Toast.LENGTH_SHORT).show();
        });
        container.addView(saveTransport);

        Button process = styledModalButton("", 0xFFE2E8F0, 0xFF0B1C50);
        String processingStatus = journey.optString("processing_status", "pending");
        String mode = journey.optString("mode", "unknown");
        boolean footMode = isFootMode(mode);
        boolean processableMode = footMode || isRoadMode(mode);
        if (points < 2) {
            process.setText("NOT ENOUGH GPS EVIDENCE");
        } else if ("complete".equals(processingStatus)) {
            process.setText("PROCESSED");
        } else if ("processing".equals(processingStatus)) {
            process.setText("PROCESSING…");
        } else if (!processableMode) {
            process.setText("NO MATCHING REQUIRED");
        } else if ("failed".equals(processingStatus)) {
            process.setText(footMode ? "RETRY ON-FOOT MATCH" : "RETRY ROAD MATCH");
        } else {
            process.setText(footMode ? "PROCESS ON-FOOT JOURNEY" : "PROCESS ROAD JOURNEY");
        }
        process.setEnabled(points >= 2 && processableMode && !"processing".equals(processingStatus)
                && !"complete".equals(processingStatus));
        process.setOnClickListener(v -> processJourney(journey, process));
        container.addView(process);

        if ("complete".equals(processingStatus)) {
            Button refineMatched = styledModalButton(
                    "EDIT MATCHED JOURNEY", 0xFF233B78, Color.WHITE);
            refineMatched.setOnClickListener(v -> showMatchedRefinement(journey));
            container.addView(refineMatched);
        }

        Button share = styledModalButton("SHARE JOURNEY JSON", 0xFFE2E8F0, 0xFF0B1C50);
        share.setOnClickListener(v -> shareJourney(journey));
        container.addView(share);

        Button delete = styledModalButton("DELETE JOURNEY", 0xFFFFE1E1, 0xFFB3261E);
        delete.setOnClickListener(v -> confirmDelete(journey));
        container.addView(delete);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(container)
                .setPositiveButton("CLOSE", null)
                .create();
        dialog.setOnShowListener(ignored -> {
            Window dialogWindow = dialog.getWindow();
            if (dialogWindow != null) {
                dialogWindow.setBackgroundDrawable(roundRect(0xFF0B1C50, 0xFF46649E, 24));
                dialogWindow.setStatusBarColor(0xFF071337);
                dialogWindow.setNavigationBarColor(0xFF071337);
                dialogWindow.setLayout(
                        (int) (getResources().getDisplayMetrics().widthPixels * 0.94f),
                        WindowManager.LayoutParams.WRAP_CONTENT);
            }
            int closeId = AlertDialog.BUTTON_POSITIVE;
            Button close = dialog.getButton(closeId);
            close.setTextColor(0xFFF7C450);
            close.setTextSize(14);
            close.setTypeface(null, android.graphics.Typeface.BOLD);
            close.setAllCaps(false);
        });
        dialog.show();
    }

    private Button styledModalButton(String text, int background, int foreground) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(13);
        button.setTypeface(null, android.graphics.Typeface.BOLD);
        button.setTextColor(foreground);
        button.setAllCaps(false);
        button.setMinHeight(52);
        button.setPadding(16, 0, 16, 0);
        button.setBackground(roundRect(background, 0xFFB8C4D5, 16));
        return button;
    }

    private void showMatchedRefinement(JSONObject journey) {
        new AlertDialog.Builder(this)
                .setTitle("Edit matched journey")
                .setMessage("This journey has been matched and can now be refined. "
                        + "The next correction controls will let you exclude or restore "
                        + "matched evidence while keeping the original GPS journey unchanged.")
                .setPositiveButton("Continue", (dialog, which) -> showDetails(journey))
                .setNegativeButton("Close", null)
                .show();
    }

    private void confirmDelete(JSONObject journey) {
        new AlertDialog.Builder(this)
                .setTitle("Delete journey?")
                .setMessage("This will permanently remove this journey from this device.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (dialog, which) -> {
                    JourneyStore.delete(this, journey.optString("journey_id"));
                    journeys = JourneyStore.all(this);
                    render();
                    Toast.makeText(this, "Journey deleted", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void processJourney(JSONObject journey, Button button) {
        JSONObject geometry = journey.optJSONObject("route_geometry");
        JSONArray coordinates = geometry == null ? null : geometry.optJSONArray("coordinates");
        if (coordinates == null || coordinates.length() < 2) {
            Toast.makeText(this, "At least two GPS points are required", Toast.LENGTH_SHORT).show();
            return;
        }
        String mode = journey.optString("mode", "unknown");
        String endpoint = ("walking".equals(mode) || "running".equals(mode)
                || "pedestrian".equals(mode)) ? "/match-walking" : "/match";
        try {
            JSONArray points = new JSONArray();
            for (int index = 0; index < coordinates.length(); index++) {
                JSONArray coordinate = coordinates.optJSONArray(index);
                if (coordinate != null && coordinate.length() >= 2
                        && !coordinate.isNull(0) && !coordinate.isNull(1)) {
                    points.put(new JSONObject().put("lat", coordinate.optDouble(1))
                            .put("lng", coordinate.optDouble(0)));
                }
            }
            if (points.length() < 2) {
                throw new IllegalStateException("At least two valid GPS points are required");
            }
            JSONObject payload = new JSONObject().put("points", points);
            String journeyId = journey.optString("journey_id");
            String stageKey = isFootMode(mode) ? "foot_matching" : "road_matching";
            journey.put("processing_status", "processing");
            JSONObject stages = journey.optJSONObject("stage_statuses");
            if (stages == null) stages = new JSONObject();
            stages.put(stageKey, "processing");
            journey.put("stage_statuses", stages);
            JSONObject processingState = journey.optJSONObject("processing");
            if (processingState == null) processingState = new JSONObject();
            processingState.put(stageKey, "processing");
            journey.put("processing", processingState);
            JourneyStore.save(this, journey);
            button.setEnabled(false);
            button.setText("PROCESSING…");
            processor.execute(() -> {
                try {
                    JSONObject result = postJson(API_BASE_URL + endpoint, payload);
                    JSONObject stored = JourneyStore.get(this, journeyId);
                    if (stored == null) throw new IllegalStateException("Journey is no longer available");
                    stored.put("processing_status", "complete");
                    JSONObject storedStages = stored.optJSONObject("stage_statuses");
                    if (storedStages == null) storedStages = new JSONObject();
                    storedStages.put(stageKey, "complete");
                    stored.put("stage_statuses", storedStages);
                    JSONObject storedProcessing = stored.optJSONObject("processing");
                    if (storedProcessing == null) storedProcessing = new JSONObject();
                    storedProcessing.put(stageKey, "complete");
                    stored.put("processing", storedProcessing);
                    stored.put("processing_result", result);
                    stored.put("processing_completed_at", Instant.now().toString());
                    JourneyStore.save(this, stored);
                    mainHandler.post(() -> {
                        button.setEnabled(true);
                        button.setText("PROCESSED");
                        journeys = JourneyStore.all(this);
                        render();
                        Toast.makeText(this, "Journey processed by Roadprints", Toast.LENGTH_SHORT).show();
                    });
                } catch (Exception error) {
                    try {
                        JSONObject stored = JourneyStore.get(this, journeyId);
                        if (stored != null) {
                            stored.put("processing_status", "failed");
                            stored.put("error_summary", error.getMessage());
                            JSONObject storedStages = stored.optJSONObject("stage_statuses");
                            if (storedStages == null) storedStages = new JSONObject();
                            storedStages.put(stageKey, "failed");
                            stored.put("stage_statuses", storedStages);
                            JSONObject storedProcessing = stored.optJSONObject("processing");
                            if (storedProcessing == null) storedProcessing = new JSONObject();
                            storedProcessing.put(stageKey, "failed");
                            stored.put("processing", storedProcessing);
                            JourneyStore.save(this, stored);
                        }
                    } catch (Exception ignored) { }
                    mainHandler.post(() -> {
                        button.setEnabled(true);
                        button.setText("RETRY PROCESSING");
                        Toast.makeText(this, "Processing failed: " + error.getMessage(), Toast.LENGTH_LONG).show();
                    });
                }
            });
        } catch (Exception error) {
            button.setEnabled(true);
            button.setText("PROCESS JOURNEY");
            Toast.makeText(this, "Could not prepare journey: " + error.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private JSONObject postJson(String urlValue, JSONObject payload) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(urlValue).openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(90000);
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Accept", "application/json");
        try (OutputStream output = connection.getOutputStream()) {
            output.write(payload.toString().getBytes(StandardCharsets.UTF_8));
        }
        int status = connection.getResponseCode();
        java.io.InputStream stream = status >= 200 && status < 300
                ? connection.getInputStream() : connection.getErrorStream();
        StringBuilder body = new StringBuilder();
        if (stream != null) {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) body.append(line);
            }
        }
        if (status < 200 || status >= 300) {
            JSONObject error = new JSONObject(body.length() == 0 ? "{}" : body.toString());
            throw new IllegalStateException("HTTP " + status + ": "
                    + error.optString("detail", "API request failed"));
        }
        return new JSONObject(body.toString());
    }

    private void updateReadinessSummary() {
        if (readinessSummary == null) return;
        int roadReady = 0;
        int footReady = 0;
        int noMatch = 0;
        int insufficient = 0;
        int processed = 0;
        int failed = 0;
        for (JSONObject journey : journeys) {
            int points = pointCount(journey);
            String status = journey.optString("processing_status", "pending");
            String mode = journey.optString("mode", "unknown");
            if ("complete".equals(status)) {
                processed++;
            } else if ("failed".equals(status)) {
                failed++;
            } else if (points < 2) {
                insufficient++;
            } else if (isFootMode(mode)) {
                footReady++;
            } else if (isRoadMode(mode)) {
                roadReady++;
            } else {
                noMatch++;
            }
        }
        String firstLine = "Ready: " + roadReady + " road • " + footReady + " on foot"
                + " • " + noMatch + " no matching";
        String secondLine = "Processed: " + processed + " • Failed: " + failed
                + " • Needs GPS: " + insufficient;
        readinessSummary.setText(firstLine + "\n" + secondLine);
    }

    private int pointCount(JSONObject journey) {
        JSONObject geometry = journey.optJSONObject("route_geometry");
        JSONArray coordinates = geometry == null ? null : geometry.optJSONArray("coordinates");
        return coordinates == null ? 0 : coordinates.length();
    }

    private boolean isFootMode(String mode) {
        return "walking".equals(mode) || "running".equals(mode)
                || "pedestrian".equals(mode);
    }

    private boolean isRoadMode(String mode) {
        return "driving".equals(mode) || "bus".equals(mode)
                || "cycling".equals(mode) || "unknown".equals(mode);
    }

    private String journeyStatusText(JSONObject journey, int points) {
        String status = journey.optString("processing_status", "pending");
        String mode = journey.optString("mode", "unknown");
        if ("complete".equals(status)) return "Processed by Roadprints";
        if ("processing".equals(status)) return "Processing journey…";
        if ("failed".equals(status)) return "Processing failed — retry available";
        if (points < 2) return "Insufficient GPS evidence";
        if (isFootMode(mode)) return "Ready for on-foot matching";
        if (isRoadMode(mode)) return "Ready for road matching";
        return "No route matching required";
    }

    private void shareJourney(JSONObject journey) {
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("application/json");
        share.putExtra(Intent.EXTRA_SUBJECT, "Roadprints journey");
        share.putExtra(Intent.EXTRA_TEXT, journey.toString());
        startActivity(Intent.createChooser(share, "Share journey data"));
    }

    private String displayMode(String value) {
        if ("driving".equals(value)) return "Driving";
        if ("walking".equals(value)) return "On foot";
        if ("cycling".equals(value)) return "Cycling";
        if ("bus".equals(value)) return "Bus";
        if ("train".equals(value)) return "Train";
        if ("plane".equals(value)) return "Plane";
        if ("ferry".equals(value)) return "Ferry";
        return "Unknown";
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

    private String journeyDuration(JSONObject journey) {
        try {
            long seconds = Math.max(0, Duration.between(
                    Instant.parse(journey.optString("started_at")),
                    Instant.parse(journey.optString("ended_at"))).getSeconds());
            long hours = seconds / 3600;
            long minutes = (seconds % 3600) / 60;
            long remaining = seconds % 60;
            if (hours > 0) return String.format("%dh %02dm", hours, minutes);
            if (minutes > 0) return String.format("%dm %02ds", minutes, remaining);
            return String.format("%ds", remaining);
        } catch (Exception ignored) {
            return "Unknown duration";
        }
    }
}
