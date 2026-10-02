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

public class JourneyListActivity extends Activity {
    private static final String API_BASE_URL = "https://uk-road-tracker-api.onrender.com";
    private static final int INITIAL_JOURNEY_CARDS = 40;
    private static final int ADDITIONAL_JOURNEY_CARDS = 40;
    private static final Object SUMMARY_CACHE_LOCK = new Object();
    private static List<JSONObject> processJourneySummaries;
    private static long processJourneyRevision = Long.MIN_VALUE;
    private static int savedJourneyScrollY;
    private static int savedJourneyCardLimit = INITIAL_JOURNEY_CARDS;
    private static int savedJourneyFilter;
    private final ExecutorService processor = Executors.newFixedThreadPool(3);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private GrowingStatusControl growingStatus;
    private static final String[] FILTER_LABELS = {
            "All", "🚗 Driving", "👟 On foot", "🚌 Bus",
            "🚆 Train", "🚲 Cycling", "✈️ Flight", "⛴ Ferry", "Unknowns"
    };
    private static final String[] FILTER_VALUES = {
            "all", "driving", "walking", "bus", "train", "cycling", "plane", "ferry", "unknown"
    };

    private LinearLayout journeyList;
    private ScrollView journeyScroll;
    private View journeyRoot;
    private TextView count;
    private TextView readinessSummary;
    private boolean launchGrowingAfterLoad;
    private List<JSONObject> journeys;
    private int journeyCardLimit = INITIAL_JOURNEY_CARDS;
    private int refreshGeneration;
    private int activeFilter = 0;
    private final List<TextView> filterChips = new ArrayList<>();

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        journeyCardLimit = Math.max(INITIAL_JOURNEY_CARDS, savedJourneyCardLimit);
        activeFilter = savedJourneyFilter;
        Window window = getWindow();
        launchGrowingAfterLoad = getIntent().getBooleanExtra("open_growing", false);
        window.setStatusBarColor(0xFF0B1C50);
        window.setNavigationBarColor(0xFF10275D);
        buildScreen();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (growingStatus != null) growingStatus.start();
        refreshJourneysAsync();
    }

    @Override
    protected void onPause() {
        if (journeyScroll != null) savedJourneyScrollY = journeyScroll.getScrollY();
        savedJourneyCardLimit = journeyCardLimit;
        savedJourneyFilter = activeFilter;
        if (growingStatus != null) growingStatus.stop();
        super.onPause();
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
        logo.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        brandRow.addView(logo);
        growingStatus = new GrowingStatusControl(this, brandRow);

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
        journeyScroll = scroll;
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
        journeyRoot = root;
        setContentView(root);
        applySystemBarInsets(root, content, bottomNavigation);
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
        if (journeyList == null) return;
        final int generation = ++refreshGeneration;
        final android.content.Context appContext = getApplicationContext();
        final long initialRevision = JourneyStore.dataRevision(appContext);
        List<JSONObject> cached = null;
        synchronized (SUMMARY_CACHE_LOCK) {
            if (processJourneySummaries != null && processJourneyRevision == initialRevision) {
                cached = new ArrayList<>(processJourneySummaries);
            }
        }
        if (cached != null) {
            journeys = cached;
            if (launchGrowingAfterLoad) {
                launchGrowingAfterLoad = false;
                startBatchMatch();
            } else {
                render();
                restoreJourneyScroll();
            }
            return;
        }
        processor.execute(() -> {
            List<JSONObject> loaded;
            try {
                loaded = JourneyStore.allSummaries(appContext);
                journeys = loaded;
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
            final long resultRevision = JourneyStore.dataRevision(appContext);
            if (resultRevision == initialRevision) {
                synchronized (SUMMARY_CACHE_LOCK) {
                    processJourneySummaries = new ArrayList<>(loaded);
                    processJourneyRevision = resultRevision;
                }
            }
            mainHandler.post(() -> {
                if (isFinishing() || generation != refreshGeneration) return;
                journeys = loaded;
                if (launchGrowingAfterLoad) {
                    launchGrowingAfterLoad = false;
                    startBatchMatch();
                } else {
                    render();
                    restoreJourneyScroll();
                }
            });
        });
    }

    private void restoreJourneyScroll() {
        if (journeyScroll != null && savedJourneyScrollY > 0) {
            journeyScroll.post(() -> journeyScroll.scrollTo(0, savedJourneyScrollY));
        }
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
        int points = pointCount(journey);

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
        inspect.setOnClickListener(v -> loadAndShowDetails(journey));
        actions.addView(inspect, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(52)));

        card.addView(cardContent);
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        actionParams.topMargin = dp(16);
        card.addView(actions, actionParams);
        card.setOnClickListener(v -> loadAndShowDetails(journey));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, dp(16));
        card.setLayoutParams(params);
        return card;
    }

    private void loadAndShowDetails(JSONObject summary) {
        final String journeyId = summary.optString("journey_id", "");
        if (journeyId.isEmpty()) return;
        processor.execute(() -> {
            JSONObject loaded;
            try {
                loaded = JourneyStore.get(getApplicationContext(), journeyId);
            } catch (Exception error) {
                loaded = null;
            }
            JSONObject journey = loaded;
            mainHandler.post(() -> {
                if (isFinishing()) return;
                if (journey == null) {
                    Toast.makeText(this,
                            "This journey could not be loaded. Your saved data is unchanged.",
                            Toast.LENGTH_LONG).show();
                    return;
                }
                showDetails(journey);
            });
        });
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

        // Flights and trains are shown as a simple endpoint-to-endpoint connection,
        // matching the POC. Their detailed Timeline trace remains saved in the journey.
        String journeyMode = journey.optString("mode", "unknown").trim().toLowerCase();
        boolean pointToPoint = isPointToPointMode(journeyMode);
        JSONArray previewCoordinates = pointToPoint
                ? endpointCoordinates(coordinates) : coordinates;
        List<JSONArray> previewMatches = pointToPoint
                ? java.util.Collections.emptyList() : matchedSegments;

        // Keep the map aperture edge-to-edge; surrounding content uses a larger inset.
        RoutePreviewView preview = new RoutePreviewView(this, previewCoordinates, previewMatches);
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
        if ("required".equals(journey.optString("transport_confirmation"))) {
            statusText = "Confirm the transport type before matching this journey";
            statusColor = 0xFFF7C450;
        } else if (!processableMode) {
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
            statusText = "No matched route is available · run matching from Progress";
            statusColor = 0xFFF7C450;
        } else if ("processing".equals(processingStatus)) {
            statusText = "Matching in progress…";
            statusColor = 0xFFF7C450;
        } else if ("failed".equals(processingStatus)) {
            statusText = "Matching failed · retry from Progress";
            statusColor = 0xFFF7C450;
        } else {
            statusText = "Ready to match from Progress";
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
            refreshJourneysAsync();
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

    private void startBatchMatch() {
        MatchingCoordinator.get(this).start();
        startActivity(new Intent(this, GrowingActivity.class));
    }

    private boolean hasStoredMatch(JSONObject journey) {
        if (journey.has("_has_stored_match")) {
            return journey.optBoolean("_has_stored_match", false);
        }
        JSONObject result = journey.optJSONObject("processing_result");
        return result != null && !matchedRouteSegments(result).isEmpty();
    }

    private void processJourney(JSONObject journey, Button button, TextView status, AlertDialog[] dialogRef) {
        if (!canMatchJourney(journey)) {
            Toast.makeText(this, "This journey does not meet the route-matching evidence threshold",
                    Toast.LENGTH_LONG).show();
            return;
        }
        MatchingCoordinator.get(this).start(journey.optString("journey_id"));
        if (dialogRef[0] != null) dialogRef[0].dismiss();
        startActivity(new Intent(this, GrowingActivity.class));
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

    }

    static boolean isPointToPointMode(String mode) {
        return "train".equals(mode) || "plane".equals(mode);
    }

    static JSONArray endpointCoordinates(JSONArray coordinates) {
        if (coordinates == null || coordinates.length() < 2) return coordinates;
        JSONArray endpoints = new JSONArray();
        endpoints.put(coordinates.optJSONArray(0));
        endpoints.put(coordinates.optJSONArray(coordinates.length() - 1));
        return endpoints;
    }

    private int pointCount(JSONObject journey) {
        if (journey.has("_route_point_count")) {
            return journey.optInt("_route_point_count", 0);
        }
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
