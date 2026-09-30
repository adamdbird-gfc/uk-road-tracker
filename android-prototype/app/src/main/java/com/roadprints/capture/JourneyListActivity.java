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
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.text.InputType;
import android.text.Editable;
import android.text.TextWatcher;

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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

public class JourneyListActivity extends Activity {
    private static final String API_BASE_URL = "https://uk-road-tracker-api.onrender.com";
    private static final int INITIAL_JOURNEY_CARDS = 40;
    private static final int ADDITIONAL_JOURNEY_CARDS = 40;
    private final ExecutorService processor = Executors.newFixedThreadPool(3);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static final String[] FILTER_LABELS = {
            "All", "🚗 Driving", "👟 On foot", "🚌 Bus",
            "🚆 Train", "🚲 Cycling", "✈️ Flight", "⛴ Ferry", "Unknowns"
    };
    private static final String[] FILTER_VALUES = {
            "all", "driving", "walking", "bus", "train", "cycling", "plane", "ferry", "unknown"
    };

    private LinearLayout journeyList;
    private View journeyRoot;
    private TextView count;
    private TextView readinessSummary;
    private TextView captureStatus;
    private Button batchMatchButton;
    private volatile boolean batchCancelRequested;
    private boolean batchRunning;
    private List<JSONObject> journeys;
    private int journeyCardLimit = INITIAL_JOURNEY_CARDS;
    private int refreshGeneration;
    private int activeFilter = 0;
    private final List<TextView> filterChips = new ArrayList<>();

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Window window = getWindow();
        window.setStatusBarColor(0xFF0B1C50);
        window.setNavigationBarColor(0xFF10275D);
        buildScreen();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateCaptureStatus();
        refreshJourneysAsync();
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
        count.setTextColor(0xFF0B1C50);
        count.setGravity(Gravity.CENTER);
        count.setPadding(22, 14, 22, 14);
        count.setBackground(pill(0xFFF7C450, 0xFFF7C450, 40));

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
        filterScroll.setPadding(0, dp(10), 0, dp(10));
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
                journeyCardLimit = INITIAL_JOURNEY_CARDS;
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
        journeyList.setPadding(dp(10), dp(18), dp(10), dp(18));
        TextView loading = new TextView(this);
        loading.setText("Loading journeys…");
        loading.setTextSize(16);
        loading.setTextColor(0xFFD3DCED);
        loading.setPadding(0, dp(28), 0, dp(28));
        journeyList.addView(loading);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(journeyList);
        scroll.setFillViewport(true);

        content.addView(brandRow);
        content.addView(headingRow);
        content.addView(intro);
        LinearLayout capturePanel = new LinearLayout(this);
        capturePanel.setOrientation(LinearLayout.VERTICAL);
        capturePanel.setPadding(dp(14), dp(10), dp(14), dp(10));
        capturePanel.setBackground(pill(0xFF172F68, 0xFF29457F, dp(16)));
        captureStatus = new TextView(this);
        captureStatus.setTextSize(12);
        captureStatus.setTextColor(0xFFD3DCED);
        captureStatus.setPadding(0, 0, 0, dp(8));
        capturePanel.addView(captureStatus);
        Button captureButton = styledModalButton("CAPTURE A JOURNEY", 0xFF233B78, Color.WHITE);
        captureButton.setOnClickListener(v -> startActivity(
                new Intent(this, MainActivity.class)));
        capturePanel.addView(captureButton, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(46)));
        LinearLayout.LayoutParams captureParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        captureParams.bottomMargin = dp(12);
        content.addView(capturePanel, captureParams);
        content.addView(readinessSummary);
        batchMatchButton = styledModalButton("MATCH READY JOURNEYS", 0xFFF7C450, 0xFF0B1C50);
        batchMatchButton.setEnabled(false);
        batchMatchButton.setOnClickListener(v -> startBatchMatch());
        LinearLayout.LayoutParams batchParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48));
        batchParams.bottomMargin = dp(8);
        content.addView(batchMatchButton, batchParams);
        content.addView(filterScroll);
        content.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));

        root.addView(content, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        View bottomNavigation = buildBottomNavigation();
        root.addView(bottomNavigation);
        journeyRoot = root;
        setContentView(root);
        applySystemBarInsets(root, content, bottomNavigation);
    }

    private void updateCaptureStatus() {
        if (captureStatus == null) return;
        if (CaptureService.isActive(this)) {
            captureStatus.setText("A journey is being recorded. Open capture to check its progress.");
        } else if (CaptureService.isArmed(this)) {
            captureStatus.setText("Automatic tracking is on and ready to record journeys.");
        } else {
            captureStatus.setText("Automatic tracking is off. Start a manual capture or enable it.");
        }
    }

    private void applySystemBarInsets(View root, View content, View bottomNavigation) {
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            int top;
            int bottom;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                android.graphics.Insets bars =
                        insets.getInsets(WindowInsets.Type.systemBars());
                top = bars.top;
                bottom = bars.bottom;
            } else {
                top = insets.getSystemWindowInsetTop();
                bottom = insets.getSystemWindowInsetBottom();
            }
            content.setPadding(dp(36), dp(26) + top, dp(36), dp(12));
            bottomNavigation.setPadding(dp(8), 0, dp(8), bottom);
            LinearLayout.LayoutParams navParams =
                    (LinearLayout.LayoutParams) bottomNavigation.getLayoutParams();
            navParams.height = dp(68) + bottom;
            navParams.bottomMargin = 0;
            bottomNavigation.setLayoutParams(navParams);
            return insets;
        });
        root.requestApplyInsets();
    }

    private View buildBottomNavigation() {
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        nav.setPadding(dp(8), 0, dp(8), 0);
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
            item.setPadding(0, dp(4), 0, 0);
            item.setClipChildren(false);

            ImageView icon = new ImageView(this);
            icon.setImageResource(iconResources[index]);
            icon.setContentDescription(labels[index]);
            icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            icon.setColorFilter(index == 1 ? 0xFFF7C450 : 0xFFB9C5D8,
                    android.graphics.PorterDuff.Mode.SRC_IN);
            item.addView(icon, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(32)));

            TextView label = new TextView(this);
            label.setText(labels[index]);
            label.setTextSize(10);
            label.setGravity(Gravity.CENTER);
            label.setIncludeFontPadding(false);
            label.setMaxLines(1);
            label.setTextColor(index == 1 ? 0xFFF7C450 : 0xFFB9C5D8);
            item.addView(label, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(24)));

            LinearLayout.LayoutParams itemParams = new LinearLayout.LayoutParams(0, dp(68), 1);
            nav.addView(item, itemParams);
            if (index == 0 || index == 2) {
                final int selected = index;
                item.setClickable(true);
                item.setFocusable(true);
                item.setOnClickListener(v -> {
                    if (selected == 0) {
                        startActivity(new Intent(this, MapActivity.class));
                    } else {
                        startActivity(new Intent(this, ProgressActivity.class));
                    }
                    finish();
                });
            }
        }
        return nav;
    }

    private void render() {
        if (journeyList == null || journeys == null) return;
        sortJourneysNewestFirst();
        journeyList.removeAllViews();
        updateReadinessSummary();
        updateAvailableFilters();
        int visible = 0;
        int rendered = 0;
        for (JSONObject journey : journeys) {
            if (!shouldShowJourney(journey) || !matchesActiveFilter(journey)) continue;
            visible++;
            if (rendered < journeyCardLimit) {
                journeyList.addView(createCard(journey));
                rendered++;
            }
        }
        count.setText(String.format(java.util.Locale.UK, "%,d", visible));
        if (visible == 0) {
            TextView empty = new TextView(this);
            empty.setText("No journeys match this filter.");
            empty.setTextSize(16);
            empty.setTextColor(0xFFD3DCED);
            empty.setPadding(0, 28, 0, 28);
            journeyList.addView(empty);
        } else if (visible > rendered) {
            Button more = styledModalButton("SHOW MORE JOURNEYS · " + (visible - rendered)
                    + " REMAINING", 0xFF233B78, Color.WHITE);
            more.setOnClickListener(v -> {
                journeyCardLimit += ADDITIONAL_JOURNEY_CARDS;
                render();
            });
            journeyList.addView(more);
        }
    }

    private void refreshJourneysAsync() {
        if (journeyList == null || batchRunning) return;
        final int generation = ++refreshGeneration;
        processor.execute(() -> {
            List<JSONObject> loaded;
            try {
                loaded = JourneyStore.all(this);
                journeys = loaded;
                recoverInterruptedMatches();
            } catch (Exception error) {
                mainHandler.post(() -> {
                    if (isFinishing() || generation != refreshGeneration) return;
                    journeyList.removeAllViews();
                    TextView failure = new TextView(this);
                    failure.setText("Journeys could not be loaded. Return and try again.");
                    failure.setTextColor(0xFFD3DCED);
                    failure.setTextSize(16);
                    journeyList.addView(failure);
                });
                return;
            }
            mainHandler.post(() -> {
                if (isFinishing() || generation != refreshGeneration) return;
                journeys = loaded;
                render();
            });
        });
    }

    private boolean shouldShowJourney(JSONObject journey) {
        String mode = journey.optString("mode", "unknown").trim().toLowerCase();
        // Journeys that cannot be matched because they lack route points add no
        // review value. Keep them in the archive, but omit them from this list.
        if (isRoadMode(mode) || isFootMode(mode)) {
            return hasEnoughMatchingEvidence(journey);
        }
        return true;
    }

    private boolean matchesActiveFilter(JSONObject journey) {
        if (activeFilter == 0) return true;
        String mode = journey.optString("mode", "unknown").trim().toLowerCase();
        if (activeFilter == FILTER_VALUES.length - 1) return isUnknownMode(mode);
        return FILTER_VALUES[activeFilter].equals(mode);
    }

    private boolean isUnknownMode(String mode) {
        return mode.isEmpty() || "unknown".equals(mode) || "other".equals(mode)
                || "unclassified".equals(mode) || "transit".equals(mode)
                || "other_travel".equals(mode);
    }

    private void updateAvailableFilters() {
        for (int filterIndex = 0; filterIndex < filterChips.size(); filterIndex++) {
            boolean available = filterIndex == 0;
            for (JSONObject journey : journeys) {
                if (shouldShowJourney(journey)
                        && matchesFilter(journey, filterIndex)) {
                    available = true;
                    break;
                }
            }
            filterChips.get(filterIndex).setVisibility(available ? View.VISIBLE : View.GONE);
            if (filterIndex == activeFilter && !available) activeFilter = 0;
        }
        updateFilterStyles();
    }

    private boolean matchesFilter(JSONObject journey, int filterIndex) {
        String mode = journey.optString("mode", "unknown").trim().toLowerCase();
        if (filterIndex == 0) return true;
        if (filterIndex == FILTER_VALUES.length - 1) return isUnknownMode(mode);
        return FILTER_VALUES[filterIndex].equals(mode);
    }

    private void sortJourneysNewestFirst() {
        journeys.sort((left, right) -> Long.compare(
                journeyStartMillis(right), journeyStartMillis(left)));
    }

    private long journeyStartMillis(JSONObject journey) {
        try {
            return Instant.parse(journey.optString("started_at")).toEpochMilli();
        } catch (Exception ignored) {
            return Long.MIN_VALUE;
        }
    }

    private View createCard(JSONObject journey) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(24), dp(20), dp(24), dp(20));
        card.setBackground(roundRect(0xFF233B78, 0xFF46649E, dp(18)));
        card.setElevation(dp(2));

        String mode = journey.optString("mode", "unknown");
        double metres = journey.optDouble("distance_meters", 0);
        JSONObject geometry = journey.optJSONObject("route_geometry");
        JSONArray coordinates = geometry == null
                ? null : geometry.optJSONArray("coordinates");
        int points = coordinates == null ? 0 : coordinates.length();

        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);
        details.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1));

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

        details.addView(label);
        details.addView(heading);
        details.addView(summary);
        details.addView(evidence);

        LinearLayout cardContent = new LinearLayout(this);
        cardContent.setOrientation(LinearLayout.HORIZONTAL);
        cardContent.setGravity(Gravity.TOP);
        cardContent.addView(details);

        TextView modeIcon = new TextView(this);
        modeIcon.setText(transportIcon(mode));
        modeIcon.setTextSize(26);
        modeIcon.setGravity(Gravity.CENTER);
        modeIcon.setContentDescription(displayMode(mode));
        modeIcon.setBackground(roundRect(0xFF304B88, 0xFF8EA7D4, dp(12)));
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(44), dp(44));
        iconParams.setMargins(dp(12), dp(2), 0, 0);
        cardContent.addView(modeIcon, iconParams);

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        TextView inspect = actionButton("VIEW & REFINE", 0xFF102047, Color.WHITE);
        inspect.setOnClickListener(v -> showDetails(journey));
        actions.addView(inspect, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(52)));

        card.addView(cardContent);
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        actionParams.topMargin = dp(16);
        card.addView(actions, actionParams);
        card.setOnClickListener(v -> showDetails(journey));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, dp(16));
        card.setLayoutParams(params);
        return card;
    }

    private TextView actionButton(String text, int background, int foreground) {
        TextView button = new TextView(this);
        button.setText(text.toUpperCase());
        button.setTextSize(13);
        button.setGravity(Gravity.CENTER);
        button.setTypeface(null, android.graphics.Typeface.BOLD);
        button.setTextColor(foreground);
        button.setPadding(dp(16), 0, dp(16), 0);
        button.setBackground(roundRect(background, 0xFF46649E, dp(12)));
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

    private List<JSONArray> matchedRouteSegments(JSONObject result) {
        List<JSONArray> segments = new ArrayList<>();
        if (result == null) return segments;
        JSONObject geojson = result.optJSONObject("geojson");
        JSONArray features = geojson == null ? null : geojson.optJSONArray("features");
        if (features == null) return segments;
        for (int index = 0; index < features.length(); index++) {
            JSONObject feature = features.optJSONObject(index);
            JSONObject geometry = feature == null ? null : feature.optJSONObject("geometry");
            if (geometry == null) continue;
            String type = geometry.optString("type", "");
            JSONArray coordinates = geometry.optJSONArray("coordinates");
            if ("LineString".equals(type) && coordinates != null
                    && coordinates.length() >= 2) {
                segments.add(coordinates);
            } else if ("MultiLineString".equals(type) && coordinates != null) {
                for (int segmentIndex = 0; segmentIndex < coordinates.length(); segmentIndex++) {
                    JSONArray segment = coordinates.optJSONArray(segmentIndex);
                    if (segment != null && segment.length() >= 2) segments.add(segment);
                }
            }
        }
        return segments;
    }

    private List<JSONArray> matchedRouteSegmentsForDisplay(JSONObject journey) {
        List<JSONArray> routes = matchedRouteSegments(
                journey.optJSONObject("processing_result"));
        JSONObject corrections = journey.optJSONObject("journey_corrections");
        JSONArray removedValues = corrections == null
                ? null : corrections.optJSONArray("removed_matched_segments");
        java.util.Set<Integer> removed = new java.util.HashSet<>();
        if (removedValues != null) {
            for (int index = 0; index < removedValues.length(); index++) {
                int edge = removedValues.optInt(index, -1);
                if (edge >= 0) removed.add(edge);
            }
        }
        if (removed.isEmpty()) return routes;

        List<JSONArray> visible = new ArrayList<>();
        int edgeIndex = 0;
        for (JSONArray route : routes) {
            JSONArray current = null;
            for (int index = 1; index < route.length(); index++, edgeIndex++) {
                if (removed.contains(edgeIndex)) {
                    if (current != null && current.length() >= 2) visible.add(current);
                    current = null;
                    continue;
                }
                if (current == null) {
                    current = new JSONArray();
                    current.put(route.optJSONArray(index - 1));
                }
                current.put(route.optJSONArray(index));
            }
            if (current != null && current.length() >= 2) visible.add(current);
        }
        return visible;
    }

    private void showDetails(JSONObject journey) {
        JSONObject geometry = journey.optJSONObject("route_geometry");
        JSONArray coordinates = geometry == null ? null : geometry.optJSONArray("coordinates");
        double metres = journey.optDouble("distance_meters", 0);
        int points = coordinates == null ? 0 : coordinates.length();
        List<JSONArray> matchedSegments = matchedRouteSegmentsForDisplay(journey);
        boolean hasMatchedGeometry = !matchedRouteSegments(
                journey.optJSONObject("processing_result")).isEmpty();
        int side = dp(24);

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setBackground(roundRect(0xFF0B1C50, 0xFF46649E, dp(20)));
        container.setClipToOutline(true);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(side, dp(18), side, dp(12));
        TextView modalTitle = new TextView(this);
        modalTitle.setText("View & refine journey");
        modalTitle.setTextSize(23);
        modalTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        modalTitle.setTextColor(Color.WHITE);
        header.addView(modalTitle, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        TextView close = new TextView(this);
        close.setText("CLOSE");
        close.setTextSize(13);
        close.setTypeface(null, android.graphics.Typeface.BOLD);
        close.setTextColor(0xFFF7C450);
        close.setGravity(Gravity.CENTER);
        close.setPadding(dp(12), dp(12), 0, dp(12));
        header.addView(close);
        container.addView(header);

        ScrollView scroll = new ScrollView(this);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        TextView previewLabel = new TextView(this);
        previewLabel.setText("ROUTE PREVIEW");
        previewLabel.setTextSize(13);
        previewLabel.setTypeface(null, android.graphics.Typeface.BOLD);
        previewLabel.setLetterSpacing(0.06f);
        previewLabel.setTextColor(0xFFD3DCED);
        previewLabel.setPadding(side, dp(8), side, dp(10));
        body.addView(previewLabel);

        // Keep the map aperture edge-to-edge; surrounding content uses a larger inset.
        RoutePreviewView preview = new RoutePreviewView(this, coordinates, matchedSegments);
        body.addView(preview, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(190)));
        if (!matchedSegments.isEmpty()) {
            TextView routeLegend = new TextView(this);
            routeLegend.setText("Blue: matched route  ·  Grey: original GPS trace");
            routeLegend.setTextSize(11);
            routeLegend.setTextColor(0xFFD3DCED);
            routeLegend.setPadding(side, dp(6), side, dp(2));
            body.addView(routeLegend);
        }

        LinearLayout fields = new LinearLayout(this);
        fields.setOrientation(LinearLayout.VERTICAL);
        fields.setPadding(side, dp(18), side, dp(12));
        fields.addView(detailRow("Start", displayTime(journey.optString("started_at"))));
        fields.addView(detailRow("End", displayTime(journey.optString("ended_at"))));
        fields.addView(detailRow("Duration", journeyDuration(journey)));

        TextView routeFacts = new TextView(this);
        routeFacts.setText(String.format("%s  ·  %.0f m  ·  %d GPS points",
                displayMode(journey.optString("mode", "unknown")), metres, points));
        routeFacts.setTextSize(14);
        routeFacts.setTextColor(0xFFD3DCED);
        routeFacts.setPadding(0, dp(8), 0, dp(18));
        fields.addView(routeFacts);

        fields.addView(fieldLabel("Journey name"));
        EditText titleInput = new EditText(this);
        titleInput.setHint("Name this journey");
        titleInput.setText(journey.optString("title", ""));
        titleInput.setTextSize(16);
        titleInput.setSingleLine(true);
        titleInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        titleInput.setTextColor(Color.WHITE);
        titleInput.setHintTextColor(0xFFB9C5D8);
        titleInput.setBackground(roundRect(0xFF142957, 0xFF7188B8, dp(10)));
        titleInput.setPadding(dp(14), dp(8), dp(14), dp(8));
        titleInput.setMinHeight(dp(52));
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        inputParams.topMargin = dp(8);
        fields.addView(titleInput, inputParams);

        TextView transportLabel = fieldLabel("Transport type");
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        labelParams.topMargin = dp(16);
        fields.addView(transportLabel, labelParams);

        String[] transportModes = {"driving", "walking", "cycling", "bus",
                "train", "plane", "ferry", "running", "unknown"};
        String[] transportLabels = {"Driving", "On foot", "Cycling", "Bus",
                "Train", "Plane", "Ferry", "Running", "Unknown"};
        Spinner transport = new Spinner(this);
        ArrayAdapter<String> transportAdapter = new ArrayAdapter<String>(
                this, android.R.layout.simple_spinner_item, transportLabels) {
            @Override
            public View getView(int position, View convertView, android.view.ViewGroup parent) {
                return styleTransportText(super.getView(position, convertView, parent), false);
            }

            @Override
            public View getDropDownView(
                    int position, View convertView, android.view.ViewGroup parent) {
                return styleTransportText(
                        super.getDropDownView(position, convertView, parent), true);
            }

            private View styleTransportText(View view, boolean dropdown) {
                TextView text = (TextView) view;
                text.setTextColor(Color.WHITE);
                text.setTextSize(16);
                if (dropdown) {
                    text.setBackgroundColor(0xFF142957);
                    text.setPadding(dp(16), dp(12), dp(16), dp(12));
                } else {
                    text.setGravity(Gravity.CENTER_VERTICAL);
                    text.setPadding(dp(10), 0, dp(10), 0);
                }
                return text;
            }
        };
        transport.setAdapter(transportAdapter);
        String currentMode = journey.optString("mode", "unknown");
        boolean matchedTransport = false;
        for (int index = 0; index < transportModes.length; index++) {
            if (transportModes[index].equals(currentMode)) {
                transport.setSelection(index);
                matchedTransport = true;
                break;
            }
        }
        if (!matchedTransport && isUnknownMode(currentMode)) {
            transport.setSelection(transportModes.length - 1);
        }
        final String[] savedTitle = {titleInput.getText().toString().trim()};
        final String[] savedMode = {transportModes[transport.getSelectedItemPosition()]};
        transport.setBackground(roundRect(0xFF142957, 0xFF7188B8, dp(10)));
        transport.setPopupBackgroundDrawable(roundRect(0xFF142957, 0xFF7188B8, dp(10)));
        transport.setDropDownVerticalOffset(dp(4));
        transport.setPadding(dp(10), 0, dp(10), 0);
        transport.setMinimumHeight(dp(52));
        LinearLayout.LayoutParams transportParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(52));
        transportParams.topMargin = dp(8);
        fields.addView(transport, transportParams);

        String processingStatus = journey.optString("processing_status", "pending");
        String mode = journey.optString("mode", "unknown");
        boolean footMode = isFootMode(mode);
        boolean processableMode = footMode || isRoadMode(mode);
        boolean enoughEvidence = hasEnoughMatchingEvidence(journey);
        TextView status = new TextView(this);
        String statusText;
        int statusColor;
        if (!processableMode) {
            statusText = "No route matching required for this transport type";
            statusColor = 0xFFB9C5D8;
        } else if (!enoughEvidence) {
            statusText = isRoadMode(mode)
                    ? "Insufficient Timeline points for reliable road matching"
                    : "Not enough GPS evidence to match this journey";
            statusColor = 0xFFF7C450;
        } else if ("complete".equals(processingStatus) && hasMatchedGeometry) {
            statusText = "✓  Processed · matched route available";
            statusColor = 0xFF8BE0B1;
        } else if ("complete".equals(processingStatus)) {
            statusText = "No matched route geometry saved · retry matching";
            statusColor = 0xFFF7C450;
        } else if ("processing".equals(processingStatus)) {
            statusText = "Matching in progress…";
            statusColor = 0xFFF7C450;
        } else if ("failed".equals(processingStatus)) {
            statusText = "Matching failed · you can retry below";
            statusColor = 0xFFF7C450;
        } else {
            statusText = "Not yet matched to roads";
            statusColor = 0xFFB9C5D8;
        }
        status.setText(statusText);
        status.setTextSize(14);
        status.setTypeface(null, android.graphics.Typeface.BOLD);
        status.setTextColor(statusColor);
        status.setPadding(0, dp(18), 0, dp(8));
        fields.addView(status);
        body.addView(fields);
        scroll.addView(body);
        container.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));

        final AlertDialog[] dialogRef = new AlertDialog[1];
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.VERTICAL);
        actions.setPadding(side, dp(12), side, dp(8));
        Button saveChanges = styledModalButton("SAVE CHANGES", 0xFFF7C450, 0xFF0B1C50);
        Runnable updateSaveState = () -> {
            boolean changed = !titleInput.getText().toString().trim().equals(savedTitle[0])
                    || !transportModes[transport.getSelectedItemPosition()].equals(savedMode[0]);
            saveChanges.setEnabled(changed);
            saveChanges.setAlpha(changed ? 1f : 0.48f);
        };
        Runnable saveEdits = () -> {
            String updatedTitle = titleInput.getText().toString().trim();
            String updatedMode = transportModes[transport.getSelectedItemPosition()];
            JourneyStore.updateTitle(this, journey.optString("journey_id"), updatedTitle);
            JourneyStore.updateMode(this, journey.optString("journey_id"), updatedMode);
            try {
                journey.put("title", updatedTitle);
                journey.put("mode", updatedMode);
            } catch (Exception ignored) { }
            savedTitle[0] = updatedTitle;
            savedMode[0] = updatedMode;
            journeys = JourneyStore.all(this);
            render();
            updateSaveState.run();
            Toast.makeText(this, "Journey updated", Toast.LENGTH_SHORT).show();
        };
        titleInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence text, int start, int before, int count) {
                updateSaveState.run();
            }
            @Override public void afterTextChanged(Editable text) { }
        });
        transport.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(
                    AdapterView<?> parent, View view, int position, long id) {
                updateSaveState.run();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {
                updateSaveState.run();
            }
        });
        updateSaveState.run();
        saveChanges.setOnClickListener(v -> saveEdits.run());
        actions.addView(saveChanges, actionLayoutParams());

        LinearLayout.LayoutParams secondaryParams = actionLayoutParams();
        secondaryParams.topMargin = dp(8);
        if ("complete".equals(processingStatus) && hasMatchedGeometry) {
            Button refineMatched = styledModalButton(
                    "EDIT MATCHED JOURNEY", 0xFF233B78, Color.WHITE);
            refineMatched.setOnClickListener(v -> requestCloseWithUnsavedChanges(
                    dialogRef[0], hasUnsavedChanges(titleInput, transport,
                            transportModes, savedTitle[0], savedMode[0]),
                    saveEdits, () -> showMatchedRefinement(journey, dialogRef)));
            actions.addView(refineMatched, secondaryParams);
        } else if (processableMode && enoughEvidence) {
            String buttonLabel = "complete".equals(processingStatus)
                    ? "RETRY MATCHING"
                    : "failed".equals(processingStatus)
                    ? (footMode ? "RETRY ON-FOOT MATCH" : "RETRY ROAD MATCH")
                    : "processing".equals(processingStatus) ? "MATCHING…"
                    : (footMode ? "MATCH ON-FOOT JOURNEY" : "MATCH ROAD JOURNEY");
            Button processButton = styledModalButton(buttonLabel, 0xFF233B78, Color.WHITE);
            processButton.setEnabled(!"processing".equals(processingStatus));
            processButton.setOnClickListener(v -> processJourney(journey, processButton, status, dialogRef));
            actions.addView(processButton, secondaryParams);
        }

        TextView delete = new TextView(this);
        delete.setText("Delete journey");
        delete.setTextSize(14);
        delete.setTextColor(0xFFFF8A8A);
        delete.setGravity(Gravity.CENTER);
        delete.setPadding(dp(12), dp(14), dp(12), dp(8));
        delete.setOnClickListener(v -> confirmDelete(journey));
        actions.addView(delete, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        container.addView(actions);

        close.setOnClickListener(v -> requestCloseWithUnsavedChanges(
                dialogRef[0], hasUnsavedChanges(titleInput, transport,
                        transportModes, savedTitle[0], savedMode[0]),
                saveEdits, () -> {
                    if (dialogRef[0] != null) dialogRef[0].dismiss();
                }));
        AlertDialog dialog = new AlertDialog.Builder(this).setView(container).create();
        dialogRef[0] = dialog;
        dialog.setCanceledOnTouchOutside(false);
        dialog.setOnKeyListener((dialogInterface, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                if (event.getAction() == KeyEvent.ACTION_UP) {
                    requestCloseWithUnsavedChanges(
                            dialog,
                            hasUnsavedChanges(titleInput, transport, transportModes,
                                    savedTitle[0], savedMode[0]),
                            saveEdits, dialog::dismiss);
                }
                return true;
            }
            return false;
        });
        dialog.setOnShowListener(ignored -> {
            Window dialogWindow = dialog.getWindow();
            if (dialogWindow != null) {
                dialogWindow.setBackgroundDrawableResource(android.R.color.transparent);
                dialogWindow.setStatusBarColor(0xFF071337);
                dialogWindow.setNavigationBarColor(0xFF071337);
                dialogWindow.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
                dialogWindow.setLayout(
                        (int) (getResources().getDisplayMetrics().widthPixels * 0.94f),
                        (int) (getResources().getDisplayMetrics().heightPixels * 0.84f));
            }
        });
        dialog.show();
    }

    private boolean hasUnsavedChanges(
            EditText title, Spinner transport, String[] modes,
            String savedTitle, String savedMode) {
        return !title.getText().toString().trim().equals(savedTitle)
                || !modes[transport.getSelectedItemPosition()].equals(savedMode);
    }

    private void requestCloseWithUnsavedChanges(
            AlertDialog dialog, boolean dirty, Runnable saveAction, Runnable continueAction) {
        if (!dirty) {
            continueAction.run();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Unsaved changes")
                .setMessage("Save your journey changes before closing?")
                .setPositiveButton("Save changes", (prompt, which) -> {
                    saveAction.run();
                    continueAction.run();
                })
                .setNegativeButton("Continue without saving",
                        (prompt, which) -> continueAction.run())
                .setNeutralButton("Keep editing", null)
                .show();
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private LinearLayout.LayoutParams actionLayoutParams() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(52));
    }

    private TextView fieldLabel(String text) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextSize(14);
        label.setTypeface(null, android.graphics.Typeface.BOLD);
        label.setTextColor(0xFF67D5CC);
        return label;
    }

    private View detailRow(String labelText, String valueText) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(4), 0, dp(4));
        TextView label = new TextView(this);
        label.setText(labelText);
        label.setTextSize(14);
        label.setTypeface(null, android.graphics.Typeface.BOLD);
        label.setTextColor(0xFF67D5CC);
        row.addView(label, new LinearLayout.LayoutParams(dp(92),
                LinearLayout.LayoutParams.WRAP_CONTENT));
        TextView value = new TextView(this);
        value.setText(valueText);
        value.setTextSize(15);
        value.setTextColor(0xFFE7ECF5);
        row.addView(value, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        return row;
    }

    private Button styledModalButton(String text, int background, int foreground) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(13);
        button.setTypeface(null, android.graphics.Typeface.BOLD);
        button.setTextColor(foreground);
        button.setAllCaps(false);
        button.setMinHeight(dp(52));
        button.setPadding(dp(16), 0, dp(16), 0);
        button.setBackground(roundRect(background, background, dp(12)));
        return button;
    }

    private void showMatchedRefinement(JSONObject journey, AlertDialog[] dialogRef) {
        Intent editor = new Intent(this, JourneyMapEditorActivity.class);
        editor.putExtra("journey_json", journey.toString());
        startActivity(editor);
        if (dialogRef[0] != null) dialogRef[0].dismiss();
    }

    private void confirmDelete(JSONObject journey) {
        new AlertDialog.Builder(this)
                .setTitle("Delete journey?")
                .setMessage("This will permanently remove this journey from this device.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (dialog, which) -> {
                    JourneyStore.delete(this, journey.optString("journey_id"));
                    Toast.makeText(this, "Journey deleted", Toast.LENGTH_SHORT).show();
                    refreshJourneysAsync();
                })
                .show();
    }

    private void recoverInterruptedMatches() {
        for (JSONObject journey : journeys) {
            if (!"processing".equals(journey.optString("processing_status"))) continue;
            try {
                journey.put("processing_status", "pending");
                JSONObject stages = journey.optJSONObject("stage_statuses");
                if (stages != null) {
                    String stage = isFootMode(journey.optString("mode", "unknown"))
                            ? "foot_matching" : "road_matching";
                    if ("processing".equals(stages.optString(stage))) stages.put(stage, "pending");
                }
                JSONObject processing = journey.optJSONObject("processing");
                if (processing != null) {
                    String stage = isFootMode(journey.optString("mode", "unknown"))
                            ? "foot_matching" : "road_matching";
                    if ("processing".equals(processing.optString(stage))) processing.put(stage, "pending");
                }
                JourneyStore.save(this, journey);
            } catch (Exception ignored) { }
        }
    }

    private void startBatchMatch() {
        if (batchRunning) return;
        List<JSONObject> queue = new ArrayList<>();
        // The list is already loaded while this screen is visible. Re-reading every
        // archive file here adds a large synchronous disk scan just as matching starts.
        for (JSONObject journey : journeys) {
            if (!canMatchJourney(journey)) continue;
            String status = journey.optString("processing_status", "pending");
            if ("processing".equals(status)) continue;
            if (!"complete".equals(status) || !hasStoredMatch(journey)) queue.add(journey);
        }
        if (queue.isEmpty()) {
            Toast.makeText(this, "There are no journeys ready to match", Toast.LENGTH_SHORT).show();
            return;
        }

        batchRunning = true;
        batchCancelRequested = false;
        updateReadinessSummary();
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(28), dp(44), dp(28), dp(28));
        panel.setGravity(Gravity.CENTER_VERTICAL);
        panel.setBackgroundColor(0xFF0B1C50);
        TextView heading = new TextView(this);
        heading.setText("Growing your map");
        heading.setTextSize(22);
        heading.setTypeface(null, android.graphics.Typeface.BOLD);
        heading.setTextColor(Color.WHITE);
        TextView details = new TextView(this);
        details.setText("Preparing " + queue.size() + " journeys to match…");
        details.setTextSize(15);
        details.setTextColor(0xFFD3DCED);
        details.setPadding(0, dp(10), 0, dp(14));
        ProgressBar progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(queue.size());
        progress.setProgress(0);
        Button stop = styledModalButton("STOP AFTER IN-FLIGHT MATCHES", 0xFF233B78, Color.WHITE);
        LinearLayout.LayoutParams stopParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48));
        stopParams.topMargin = dp(18);
        panel.addView(heading);
        panel.addView(details);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(8));
        progressParams.topMargin = dp(10);
        panel.addView(progress, progressParams);
        panel.addView(stop, stopParams);
        setContentView(panel);
        panel.setOnApplyWindowInsetsListener((view, insets) -> {
            int top = 0;
            int bottom = 0;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                top = bars.top;
                bottom = bars.bottom;
            } else {
                top = insets.getSystemWindowInsetTop();
                bottom = insets.getSystemWindowInsetBottom();
            }
            view.setPadding(dp(28), dp(44) + top, dp(28), dp(28) + bottom);
            return insets;
        });
        panel.requestApplyInsets();
        stop.setOnClickListener(v -> {
            batchCancelRequested = true;
            stop.setEnabled(false);
            stop.setText("FINISHING IN-FLIGHT MATCHES…");
            details.setText("In-flight matches will be saved before the queue stops.");
        });

        java.util.concurrent.ConcurrentLinkedQueue<JSONObject> roadWork =
                new java.util.concurrent.ConcurrentLinkedQueue<>();
        java.util.concurrent.ConcurrentLinkedQueue<JSONObject> footWork =
                new java.util.concurrent.ConcurrentLinkedQueue<>();
        for (JSONObject item : queue) {
            (isFootMode(item.optString("mode", "unknown")) ? footWork : roadWork).add(item);
        }
        AtomicInteger finished = new AtomicInteger();
        AtomicInteger completed = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();
        CountDownLatch workers = new CountDownLatch(3);
        for (int worker = 0; worker < 3; worker++) {
            final java.util.concurrent.ConcurrentLinkedQueue<JSONObject> lane =
                    worker == 2 ? footWork : roadWork;
            processor.execute(() -> {
                try {
                    JSONObject item;
                    while (!batchCancelRequested && (item = lane.poll()) != null) {
                        try {
                            markJourneyProcessing(item);
                            matchJourneyForBatch(item);
                            completed.incrementAndGet();
                        } catch (Exception error) {
                            failed.incrementAndGet();
                            markJourneyFailed(item, error);
                            if (Thread.currentThread().isInterrupted()) batchCancelRequested = true;
                        }
                        int done = finished.incrementAndGet();
                        int succeeded = completed.get();
                        int errors = failed.get();
                        mainHandler.post(() -> {
                            progress.setProgress(done);
                            details.setText(done + " of " + queue.size() + " journeys checked · "
                                    + succeeded + " matched · " + errors + " need retry");
                            // Keep the progress screen lightweight. Re-reading and
                            // rebuilding every journey card after each result caused
                            // repeated main-thread disk I/O and UI stalls on large imports.
                        });
                    }
                } finally {
                    workers.countDown();
                    if (workers.getCount() == 0) {
                        final int checked = finished.get();
                        final int succeeded = completed.get();
                        final int errors = failed.get();
                        final boolean stopped = batchCancelRequested;
                        mainHandler.post(() -> {
                            batchRunning = false;
                            heading.setText(stopped ? "Matching paused" : "Map growth complete");
                            details.setText(checked + " of " + queue.size() + " checked · "
                                    + succeeded + " matched · " + errors + " failed"
                                    + (stopped ? " · remaining journeys are ready to resume" : ""));
                            stop.setText("RETURN TO JOURNEYS");
                            stop.setEnabled(true);
                            stop.setOnClickListener(v -> {
                                setContentView(journeyRoot);
                                refreshJourneysAsync();
                            });
                            stop.setVisibility(View.VISIBLE);
                        });
                    }
                }
            });
        }
    }

    private boolean hasStoredMatch(JSONObject journey) {
        JSONObject result = journey.optJSONObject("processing_result");
        return result != null && !matchedRouteSegments(result).isEmpty();
    }

    private void markJourneyProcessing(JSONObject journey) throws Exception {
        String stageKey = isFootMode(journey.optString("mode", "unknown"))
                ? "foot_matching" : "road_matching";
        journey.put("processing_status", "processing");
        JSONObject stages = journey.optJSONObject("stage_statuses");
        if (stages == null) stages = new JSONObject();
        stages.put(stageKey, "processing");
        journey.put("stage_statuses", stages);
        JSONObject processing = journey.optJSONObject("processing");
        if (processing == null) processing = new JSONObject();
        processing.put(stageKey, "processing");
        journey.put("processing", processing);
        journey.remove("processing_result");
        JourneyStore.save(this, journey);
    }

    private void matchJourneyForBatch(JSONObject journey) throws Exception {
        JSONObject geometry = journey.optJSONObject("route_geometry");
        JSONArray coordinates = geometry == null ? null : geometry.optJSONArray("coordinates");
        JSONArray points = new JSONArray();
        if (coordinates != null) {
            for (int index = 0; index < coordinates.length(); index++) {
                JSONArray coordinate = coordinates.optJSONArray(index);
                if (coordinate != null && coordinate.length() >= 2
                        && !coordinate.isNull(0) && !coordinate.isNull(1)) {
                    points.put(new JSONObject().put("lat", coordinate.optDouble(1))
                            .put("lng", coordinate.optDouble(0)));
                }
            }
        }
        if (points.length() < 2) throw new IllegalStateException("At least two GPS points are required");
        String mode = journey.optString("mode", "unknown");
        String endpoint = isFootMode(mode) ? "/match-walking" : "/match";
        JSONObject result = postJsonWithRetry(API_BASE_URL + endpoint, new JSONObject().put("points", points));
        if (matchedRouteSegments(result).isEmpty()) {
            throw new IllegalStateException("Matcher returned no route geometry");
        }
        JSONObject stored = JourneyStore.get(this, journey.optString("journey_id"));
        if (stored == null) throw new IllegalStateException("Journey is no longer available");
        String stageKey = isFootMode(mode) ? "foot_matching" : "road_matching";
        stored.put("processing_status", "complete");
        JSONObject stages = stored.optJSONObject("stage_statuses");
        if (stages == null) stages = new JSONObject();
        stages.put(stageKey, "complete");
        stored.put("stage_statuses", stages);
        JSONObject processing = stored.optJSONObject("processing");
        if (processing == null) processing = new JSONObject();
        processing.put(stageKey, "complete");
        stored.put("processing", processing);
        stored.put("processing_result", result);
        stored.put("processing_completed_at", Instant.now().toString());
        stored.remove("error_summary");
        JourneyStore.save(this, stored);
    }

    private JSONObject postJsonWithRetry(String endpoint, JSONObject payload) throws Exception {
        long[] delays = {750L, 2000L};
        for (int attempt = 0; ; attempt++) {
            try {
                return postJson(endpoint, payload);
            } catch (Exception error) {
                String message = error.getMessage() == null ? "" : error.getMessage().toLowerCase();
                boolean transientFailure = message.contains("http 429")
                        || message.matches("(?s).*http 5[0-9][0-9].*")
                        || message.contains("timeout") || message.contains("failed to connect")
                        || message.contains("connection reset");
                if (!transientFailure || attempt >= delays.length) throw error;
                Thread.sleep(delays[attempt]);
            }
        }
    }

    private void markJourneyFailed(JSONObject journey, Exception error) {
        try {
            JSONObject stored = JourneyStore.get(this, journey.optString("journey_id"));
            if (stored == null) return;
            String stageKey = isFootMode(stored.optString("mode", "unknown"))
                    ? "foot_matching" : "road_matching";
            stored.put("processing_status", "failed");
            stored.put("error_summary", error.getMessage());
            JSONObject stages = stored.optJSONObject("stage_statuses");
            if (stages == null) stages = new JSONObject();
            stages.put(stageKey, "failed");
            stored.put("stage_statuses", stages);
            JSONObject processing = stored.optJSONObject("processing");
            if (processing == null) processing = new JSONObject();
            processing.put(stageKey, "failed");
            stored.put("processing", processing);
            JourneyStore.save(this, stored);
        } catch (Exception ignored) { }
    }

    private void processJourney(JSONObject journey, Button button, TextView status, AlertDialog[] dialogRef) {
        if (batchRunning) {
            Toast.makeText(this, "Journey matching is already running", Toast.LENGTH_SHORT).show();
            return;
        }
        JSONObject geometry = journey.optJSONObject("route_geometry");
        JSONArray coordinates = geometry == null ? null : geometry.optJSONArray("coordinates");
        if (!canMatchJourney(journey)) {
            Toast.makeText(this, "This journey does not meet the route-matching evidence threshold",
                    Toast.LENGTH_LONG).show();
            return;
        }
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
            journey.remove("processing_result");
            JourneyStore.save(this, journey);
            button.setEnabled(false);
            button.setText("MATCHING…");
            status.setText("Matching in progress…");
            status.setTextColor(0xFFF7C450);
            processor.execute(() -> {
                try {
                    JSONObject result = postJson(API_BASE_URL + endpoint, payload);
                    if (matchedRouteSegments(result).isEmpty()) {
                        throw new IllegalStateException(
                                "Matcher returned no route geometry to display");
                    }
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
                        button.setEnabled(false);
                        button.setVisibility(View.GONE);
                        if (dialogRef[0] != null) dialogRef[0].dismiss();
                        journeys = JourneyStore.all(this);
                        render();
                        JSONObject refreshed = JourneyStore.get(this, journeyId);
                        if (refreshed != null) showDetails(refreshed);
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
                        status.setText("Matching failed · you can retry below");
                        status.setTextColor(0xFFF7C450);
                        Toast.makeText(this, "Processing failed: " + error.getMessage(), Toast.LENGTH_LONG).show();
                    });
                }
            });
        } catch (Exception error) {
            button.setEnabled(true);
            button.setText("PROCESS JOURNEY");
            status.setText("Could not prepare journey for matching");
            status.setTextColor(0xFFF7C450);
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
        int processed = 0;
        int failed = 0;
        int matching = 0;
        for (JSONObject journey : journeys) {
            String status = journey.optString("processing_status", "pending");
            String mode = journey.optString("mode", "unknown");
            if ((isFootMode(mode) || isRoadMode(mode))
                    && !hasEnoughMatchingEvidence(journey)) {
                continue;
            }
            if (!isFootMode(mode) && !isRoadMode(mode)) {
                noMatch++;
            } else if ("complete".equals(status) && hasStoredMatch(journey)) {
                processed++;
            } else if ("failed".equals(status)
                    || ("complete".equals(status) && !hasStoredMatch(journey))) {
                failed++;
            } else if ("processing".equals(status)) {
                matching++;
            } else if (isFootMode(mode)) {
                footReady++;
            } else {
                roadReady++;
            }
        }
        String firstLine = "Ready: " + roadReady + " road • " + footReady + " on foot"
                + " • " + noMatch + " no matching";
        String secondLine = "Processed: " + processed + " • Failed: " + failed
                + (matching > 0 ? " • Matching: " + matching : "");
        readinessSummary.setText(firstLine + "\n" + secondLine);
        if (batchMatchButton != null) {
            int ready = roadReady + footReady + failed;
            batchMatchButton.setVisibility(ready > 0 ? View.VISIBLE : View.GONE);
            batchMatchButton.setText(batchRunning ? "MATCHING JOURNEYS…" :
                    "MATCH READY JOURNEYS · " + ready);
            batchMatchButton.setEnabled(!batchRunning && ready > 0);
            batchMatchButton.setAlpha(batchRunning || ready == 0 ? 0.55f : 1f);
        }
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
        return "driving".equals(mode) || "bus".equals(mode);
    }

    private boolean hasEnoughMatchingEvidence(JSONObject journey) {
        if (pointCount(journey) < 2) return false;
        String mode = journey.optString("mode", "unknown");
        if (isRoadMode(mode)) {
            JSONObject source = journey.optJSONObject("source");
            if (source != null && "timeline_import".equals(source.optString("type", ""))) {
                JSONObject quality = journey.optJSONObject("capture_quality");
                return quality != null && quality.optInt("source_route_points", 0) >= 2;
            }
        }
        return isFootMode(mode) || isRoadMode(mode);
    }

    private boolean canMatchJourney(JSONObject journey) {
        String mode = journey.optString("mode", "unknown");
        return (isFootMode(mode) || isRoadMode(mode))
                && hasEnoughMatchingEvidence(journey);
    }

    private String journeyStatusText(JSONObject journey, int points) {
        String status = journey.optString("processing_status", "pending");
        String mode = journey.optString("mode", "unknown");
        if (!isFootMode(mode) && !isRoadMode(mode)) return "No route matching required";
        if (!hasEnoughMatchingEvidence(journey)) {
            return isRoadMode(mode)
                    ? "Insufficient Timeline points for reliable road matching"
                    : "Insufficient GPS evidence";
        }
        if ("complete".equals(status)) return "Processed by Roadprints";
        if ("processing".equals(status)) return "Processing journey…";
        if ("failed".equals(status)) return "Processing failed — retry available";
        if (isFootMode(mode)) return "Ready for on-foot matching";
        return "Ready for road matching";
    }

    private void shareJourney(JSONObject journey) {
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("application/json");
        share.putExtra(Intent.EXTRA_SUBJECT, "Roadprints journey");
        share.putExtra(Intent.EXTRA_TEXT, journey.toString());
        startActivity(Intent.createChooser(share, "Share journey data"));
    }

    private String transportIcon(String mode) {
        switch (mode) {
            case "driving": return "🚗";
            case "walking":
            case "running": return "👟";
            case "bus": return "🚌";
            case "train": return "🚆";
            case "cycling": return "🚲";
            case "plane": return "✈️";
            case "ferry": return "⛴";
            default: return "📍";
        }
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
