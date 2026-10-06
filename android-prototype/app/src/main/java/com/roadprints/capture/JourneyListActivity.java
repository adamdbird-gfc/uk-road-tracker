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
import android.widget.FrameLayout;
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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

public class JourneyListActivity extends Activity {
    private static final int SAVE_JOURNEY_DEBUG = 4102;
    private String pendingDebugJourneyId;
    private static final String API_BASE_URL = "https://uk-road-tracker-api.onrender.com";
    private static final int INITIAL_JOURNEY_CARDS = 40;
    private static final int ADDITIONAL_JOURNEY_CARDS = 40;
    private static final Object SUMMARY_CACHE_LOCK = new Object();
    private static List<JSONObject> processJourneySummaries;
    private static long processJourneyRevision = Long.MIN_VALUE;
    private static long processDiscoveryRevision = Long.MIN_VALUE;
    private static Map<String, List<String>> processNewRoadHighlights = Collections.emptyMap();
    private static int savedJourneyScrollY;
    private static int savedJourneyCardLimit = INITIAL_JOURNEY_CARDS;
    private static int savedJourneyFilter;
    private static String savedJourneyStatusFilter = "all";
    private static JourneyDateFilter savedJourneyDateFilter = JourneyDateFilter.all();
    private final ExecutorService processor = Executors.newFixedThreadPool(3);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static final String[] FILTER_LABELS = {
            "All transport", "Driving", "On foot", "Bus",
            "Train", "Cycling", "Flight", "Ferry", "Unknown"
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
    private String activeJourneyStatusFilter = "all";
    private JourneyDateFilter activeDateFilter = JourneyDateFilter.all();
    private TextView filterSummary;
    private static final String[] STATUS_VALUES = {"all", "matched", "ready", "matching", "failed", "no_match"};
    private static final String[] STATUS_LABELS = {"All statuses", "Matched", "Ready to match", "Matching", "Needs retry", "No matching needed"};
    private Set<String> recapJourneyIds = Collections.emptySet();
    private Map<String, List<String>> newRoadHighlights = Collections.emptyMap();

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (state != null) pendingDebugJourneyId = state.getString("debug_journey_id");
        ArrayList<String> recap = getIntent().getStringArrayListExtra("recap_journey_ids");
        if (recap != null) recapJourneyIds = new HashSet<>(recap);
        journeyCardLimit = Math.max(INITIAL_JOURNEY_CARDS, savedJourneyCardLimit);
        activeFilter = savedJourneyFilter;
        activeJourneyStatusFilter = savedJourneyStatusFilter;
        activeDateFilter = savedJourneyDateFilter;
        if (state != null) {
            activeFilter = Math.max(0, Math.min(FILTER_VALUES.length - 1, state.getInt("filter_mode", activeFilter)));
            activeJourneyStatusFilter = state.getString("filter_status", activeJourneyStatusFilter);
            activeDateFilter = JourneyDateFilter.restore(state.getString("filter_date", "all"), state.getString("filter_from"), state.getString("filter_to"));
        }
        if (!recapJourneyIds.isEmpty() && state == null) { activeFilter = 0; activeJourneyStatusFilter = "all"; activeDateFilter = JourneyDateFilter.all(); }
        Window window = getWindow();
        launchGrowingAfterLoad = getIntent().getBooleanExtra("open_growing", false);
        window.setStatusBarColor(0xFF0B1C50);
        window.setNavigationBarColor(0xFF10275D);
        buildScreen();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshJourneysAsync();
    }

    @Override
    protected void onPause() {
        if (journeyScroll != null) savedJourneyScrollY = journeyScroll.getScrollY();
        savedJourneyCardLimit = journeyCardLimit;
        savedJourneyFilter = activeFilter;
        savedJourneyStatusFilter = activeJourneyStatusFilter;
        savedJourneyDateFilter = activeDateFilter;
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        refreshGeneration++;
        processor.shutdownNow();
        super.onDestroy();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putString("debug_journey_id", pendingDebugJourneyId);
        state.putInt("filter_mode", activeFilter);
        state.putString("filter_status", activeJourneyStatusFilter);
        state.putString("filter_date", activeDateFilter.preset);
        state.putString("filter_from", activeDateFilter.from == null ? null : activeDateFilter.from.toString());
        state.putString("filter_to", activeDateFilter.to == null ? null : activeDateFilter.to.toString());
        super.onSaveInstanceState(state);
    }

    private void exportJourneyDebug(String journeyId) {
        if (pendingDebugJourneyId != null) return;
        pendingDebugJourneyId = journeyId;
        Intent save = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        save.addCategory(Intent.CATEGORY_OPENABLE);
        save.setType("application/json");
        String stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
                .withZone(java.time.ZoneOffset.UTC).format(Instant.now());
        save.putExtra(Intent.EXTRA_TITLE, "roadprints-journey-" + stamp + ".json");
        try {
            startActivityForResult(save, SAVE_JOURNEY_DEBUG);
        } catch (android.content.ActivityNotFoundException error) {
            pendingDebugJourneyId = null;
            Toast.makeText(this, "No file-saving app is available.", Toast.LENGTH_LONG).show();
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != SAVE_JOURNEY_DEBUG) return;
        String journeyId = pendingDebugJourneyId;
        pendingDebugJourneyId = null;
        if (resultCode != RESULT_OK || data == null || data.getData() == null || journeyId == null) return;
        android.net.Uri uri = data.getData();
        android.content.Context app = getApplicationContext();
        Toast.makeText(app, "Saving journey diagnostics…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            String message;
            try (OutputStream output = app.getContentResolver().openOutputStream(uri, "wt")) {
                if (output == null) throw new java.io.IOException("Could not open selected file");
                JourneyDebugExporter.write(app, journeyId, output);
                message = "Journey diagnostics saved.";
            } catch (Exception error) {
                message = "Journey diagnostics could not be saved. Please try again.";
            }
            final String result = message;
            new Handler(Looper.getMainLooper()).post(() ->
                    Toast.makeText(app, result, Toast.LENGTH_LONG).show());
        }, "journey-debug-export").start();
    }

    private void buildScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF0B1C50);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(26), dp(18), dp(12));

        LinearLayout brandRow = new LinearLayout(this);
        brandRow.setGravity(Gravity.CENTER_VERTICAL);
        brandRow.setPadding(0, 0, 0, dp(28));
        brandRow.addView(RoadprintsHeader.create(this),
                new LinearLayout.LayoutParams(0, dp(44), 1));

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
        count.setVisibility(View.GONE);

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
        readinessSummary.setPadding(0, 0, 0, dp(8));
        LinearLayout filters = new LinearLayout(this);
        filters.setGravity(Gravity.CENTER_VERTICAL);
        ImageView filterIcon = RoadprintsIcons.image(this, R.drawable.ic_roadprints_filter, 0xFFF7C450, "Filters");
        filters.addView(filterIcon, new LinearLayout.LayoutParams(dp(24), dp(24)));
        TextView openFilters = actionButton("FILTER JOURNEYS", 0xFF233B78, Color.WHITE);
        openFilters.setTag("journey_filters_open");
        openFilters.setOnClickListener(v -> showJourneyFilters());
        LinearLayout.LayoutParams openParams = new LinearLayout.LayoutParams(0, dp(48), 1);
        openParams.setMargins(dp(8), 0, dp(8), 0);
        filters.addView(openFilters, openParams);
        TextView reset = actionButton("RESET", 0xFF0B1C50, 0xFFF7C450);
        reset.setOnClickListener(v -> { activeFilter = 0; activeJourneyStatusFilter = "all"; activeDateFilter = JourneyDateFilter.all(); applyJourneyFilters(); });
        filters.addView(reset, new LinearLayout.LayoutParams(dp(72), dp(48)));
        filterSummary = new TextView(this);
        filterSummary.setTextColor(0xFF67D5CC);
        filterSummary.setTextSize(13);
        filterSummary.setPadding(0, dp(8), 0, dp(4));
        filterSummary.setOnClickListener(v -> showJourneyFilters());

        journeyList = new LinearLayout(this);
        journeyList.setOrientation(LinearLayout.VERTICAL);
        journeyList.setPadding(0, dp(18), 0, dp(18));
        journeyList.addView(ScreenLoadingView.create(this, "Preparing your journeys",
                "Reading compact journey details without loading route files into memory."));

        ScrollView scroll = new ScrollView(this);
        journeyScroll = scroll;
        scroll.addView(journeyList);
        scroll.setFillViewport(true);

        content.addView(brandRow);
        content.addView(headingRow);
        content.addView(intro);
        content.addView(readinessSummary);
        content.addView(filters);
        content.addView(filterSummary);
        if (!recapJourneyIds.isEmpty()) {
            TextView recapNotice = new TextView(this);
            recapNotice.setText("New recordings · tap to show all journeys");
            recapNotice.setTextColor(0xFF67D5CC);
            recapNotice.setPadding(0, dp(12), 0, dp(12));
            recapNotice.setOnClickListener(view -> {
                recapJourneyIds = Collections.emptySet();
                recapNotice.setVisibility(View.GONE);
                render();
            });
            content.addView(recapNotice);
        }
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
            content.setPadding(dp(18), dp(26) + top, dp(18), dp(12));
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
            if (index == 0 || index == 2 || index == 3 || index == 4) {
                final int selected = index;
                item.setClickable(true);
                item.setFocusable(true);
                item.setOnClickListener(v -> {
                    if (selected == 0) {
                        startActivity(new Intent(this, MapActivity.class));
                    } else if (selected == 2) {
                        startActivity(new Intent(this, ProgressActivity.class));
                    } else if (selected == 4) {
                        startActivity(new Intent(this, CollectionsActivity.class));
                    } else {
                        startActivity(new Intent(this, AchievementsActivity.class));
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
        updateFilterStyles();
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
        count.setVisibility(View.VISIBLE);
        readinessSummary.setText(String.format(java.util.Locale.UK, "Showing: %,d journeys · newest first", visible));
        if (visible == 0) {
            TextView empty = new TextView(this);
            empty.setText("No journeys match this filter.");
            empty.setTextSize(16);
            empty.setTextColor(0xFFD3DCED);
            empty.setPadding(0, 28, 0, 28);
            journeyList.addView(empty);
        } else if (visible > rendered) {
            Button more = styledModalButton("SHOW MORE JOURNEYS · " + String.format(java.util.Locale.UK, "%,d", visible - rendered)
                    + " REMAINING", 0xFF233B78, Color.WHITE);
            more.setOnClickListener(v -> {
                journeyCardLimit += ADDITIONAL_JOURNEY_CARDS;
                render();
            });
            journeyList.addView(more);
        }
    }

    /** Prepares only compact rows; Map uses these before scheduling route geometry. */
    static List<JSONObject> preloadSummaries(android.content.Context context, long revision) throws Exception {
        synchronized (SUMMARY_CACHE_LOCK) {
            if (processJourneySummaries != null && processJourneyRevision == revision)
                return new ArrayList<>(processJourneySummaries);
        }
        JSONObject saved = PersistentScreenCache.read(context, "journey-summaries", revision);
        JSONArray rows = saved == null ? null : saved.optJSONArray("rows");
        List<JSONObject> loaded = new ArrayList<>();
        if (rows != null) {
            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.optJSONObject(i); if (row != null) loaded.add(row);
            }
        } else {
            loaded = JourneyStore.allSummaries(context);
            JSONArray compact = new JSONArray(); for (JSONObject row : loaded) compact.put(row);
            if (JourneyStore.dataRevision(context) == revision)
                PersistentScreenCache.write(context, "journey-summaries", revision,
                        new JSONObject().put("rows", compact));
        }
        if (JourneyStore.dataRevision(context) == revision) {
            synchronized (SUMMARY_CACHE_LOCK) {
                processJourneySummaries = new ArrayList<>(loaded);
                processJourneyRevision = revision;
            }
        }
        return loaded;
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
                loadNewRoadHighlights(initialRevision, generation);
                restoreJourneyScroll();
            }
            return;
        }
        ScreenDataLoader.execute(() -> {
            List<JSONObject> loaded;
            try {
                JSONObject saved=PersistentScreenCache.read(appContext,"journey-summaries",initialRevision);
                JSONArray rows=saved==null?null:saved.optJSONArray("rows");
                if(rows!=null){
                    loaded=new ArrayList<>(rows.length());
                    for(int i=0;i<rows.length();i++){JSONObject row=rows.optJSONObject(i);if(row!=null)loaded.add(row);}
                }else{
                    loaded=JourneyStore.allSummaries(appContext);
                    JSONArray compact=new JSONArray();for(JSONObject row:loaded)compact.put(row);
                    PersistentScreenCache.write(appContext,"journey-summaries",initialRevision,
                            new JSONObject().put("rows",compact));
                }
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
                    loadNewRoadHighlights(resultRevision, generation);
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
        if (!recapJourneyIds.isEmpty() && !recapJourneyIds.contains(journey.optString("journey_id"))) return false;
        if (!matchesJourneyStatusFilter(journey)) return false;
        return matchesFilter(journey, activeFilter) && activeDateFilter.matches(journey, java.time.LocalDate.now(), ZoneId.systemDefault());
    }

    private boolean matchesJourneyStatusFilter(JSONObject journey) {
        return matchesJourneyStatusFilter(journey, activeJourneyStatusFilter);
    }

    private boolean matchesJourneyStatusFilter(JSONObject journey, String filter) {
        if ("all".equals(filter)) return true;
        String mode = journey.optString("mode", "unknown").trim().toLowerCase();
        String status = journey.optString("processing_status", "pending");
        if ("no_match".equals(filter)) return !isRoadMode(mode) && !isFootMode(mode);
        if (!isRoadMode(mode) && !isFootMode(mode)) return false;
        if ("matched".equals(filter)) return "complete".equals(status) && hasStoredMatch(journey);
        if ("failed".equals(filter)) return "failed".equals(status)
                || ("complete".equals(status) && !hasStoredMatch(journey));
        if ("matching".equals(filter)) return "processing".equals(status);
        if ("ready".equals(filter)) return canMatchJourney(journey)
                && !"complete".equals(status) && !"failed".equals(status)
                && !"processing".equals(status);
        return true;
    }

    private boolean isUnknownMode(String mode) {
        return mode.isEmpty() || "unknown".equals(mode) || "other".equals(mode)
                || "unclassified".equals(mode) || "transit".equals(mode)
                || "other_travel".equals(mode);
    }

    private boolean matchesFilter(JSONObject journey, int filterIndex) {
        String mode = journey.optString("mode", "unknown").trim().toLowerCase();
        if (filterIndex == 0) return true;
        if (filterIndex == FILTER_VALUES.length - 1) return isUnknownMode(mode);
        if (filterIndex == 2) return isFootMode(mode);
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

    private void loadNewRoadHighlights(long revision, int generation) {
        if (processor.isShutdown()) return;
        synchronized (SUMMARY_CACHE_LOCK) {
            if (processDiscoveryRevision == revision) {
                newRoadHighlights = processNewRoadHighlights;
                render();
                return;
            }
        }
        try {
            processor.execute(() -> {
                Map<String, List<String>> highlights = readDiscoveryCache(revision);
                boolean calculated = highlights != null;
                if (highlights == null) {
                    highlights = new LinkedHashMap<>();
                    try {
                        List<JourneyDiscoveryRecord> records = new ArrayList<>();
                        JourneyStore.forEach(getApplicationContext(), journey -> {
                            if (!"complete".equals(journey.optString("processing_status"))) return;
                            String mode = journey.optString("mode", "unknown").toLowerCase();
                            if (!(isRoadMode(mode) || isFootMode(mode))) return;
                            Map<String, String> roads = new LinkedHashMap<>();
                            for (JSONObject feature : discoveryFeatures(journey)) {
                                JSONObject properties = feature.optJSONObject("properties");
                                if (properties == null) continue;
                                String rawRef = properties.optString("road_ref",
                                        properties.optString("ref", ""));
                                String name = properties.optString("name",
                                        properties.optString("road_name", "")).trim();
                                String[] refs = rawRef.trim().isEmpty() ? new String[]{""}
                                        : rawRef.trim().split("[;,/]");
                                for (String value : refs) {
                                    String ref = value.trim().toUpperCase();
                                    boolean usableRef = !ref.isEmpty()
                                            && !ref.matches("\\d+(?:[.,]\\d+)?");
                                    String label = usableRef ? ref : name;
                                    if (label.isEmpty()
                                            || label.matches("\\d+(?:[.,]\\d+)?")) continue;
                                    roads.putIfAbsent(usableRef ? "ref:" + ref
                                            : "name:" + label.toLowerCase(), label);
                                }
                            }
                            if (!roads.isEmpty()) records.add(new JourneyDiscoveryRecord(
                                    journey.optString("journey_id", ""),
                                    journeyStartMillis(journey), roads));
                        });
                        records.sort(Comparator.comparingLong(record -> record.startedAt));
                        Set<String> seen = new HashSet<>();
                        for (JourneyDiscoveryRecord record : records) {
                            List<String> fresh = new ArrayList<>();
                            for (Map.Entry<String, String> road : record.roads.entrySet())
                                if (!seen.contains(road.getKey())) fresh.add(road.getValue());
                            if (!fresh.isEmpty()) highlights.put(record.journeyId, fresh);
                            seen.addAll(record.roads.keySet());
                        }
                        calculated = true;
                    } catch (Exception error) {
                        android.util.Log.w("Roadprints",
                                "Journey road highlights could not be calculated", error);
                    }
                }
                final Map<String, List<String>> result = highlights;
                if (calculated && JourneyStore.dataRevision(getApplicationContext()) == revision) {
                    synchronized (SUMMARY_CACHE_LOCK) {
                        processDiscoveryRevision = revision;
                        processNewRoadHighlights = result;
                    }
                    writeDiscoveryCache(revision, result);
                }
                mainHandler.post(() -> {
                    if (isFinishing() || generation != refreshGeneration) return;
                    newRoadHighlights = result;
                    render();
                });
            });
        } catch (RejectedExecutionException ignored) {
            // The activity may be closing while its background queue is shutting down.
        }
    }

    private Map<String, List<String>> readDiscoveryCache(long revision) {
        JSONObject saved = PersistentScreenCache.read(getApplicationContext(),
                "journey-discovery-highlights", revision);
        JSONObject rows = saved == null ? null : saved.optJSONObject("rows");
        if (rows == null) return null;
        Map<String, List<String>> result = new LinkedHashMap<>();
        java.util.Iterator<String> keys = rows.keys();
        while (keys.hasNext()) {
            String journeyId = keys.next();
            JSONArray labels = rows.optJSONArray(journeyId);
            if (labels == null) continue;
            List<String> values = new ArrayList<>();
            for (int i = 0; i < labels.length(); i++) values.add(labels.optString(i));
            result.put(journeyId, values);
        }
        return result;
    }

    private void writeDiscoveryCache(long revision, Map<String, List<String>> highlights) {
        try {
            JSONObject rows = new JSONObject();
            for (Map.Entry<String, List<String>> entry : highlights.entrySet()) {
                JSONArray labels = new JSONArray();
                for (String label : entry.getValue()) labels.put(label);
                rows.put(entry.getKey(), labels);
            }
            PersistentScreenCache.write(getApplicationContext(), "journey-discovery-highlights",
                    revision, new JSONObject().put("rows", rows));
        } catch (Exception error) {
            android.util.Log.w("Roadprints", "Journey highlight cache could not be saved", error);
        }
    }

    private List<JSONObject> discoveryFeatures(JSONObject journey) {
        JSONObject result = journey.optJSONObject("processing_result");
        JSONObject roadGeoJson = result == null ? null : result.optJSONObject("road_geojson");
        JSONArray features = roadGeoJson == null ? null : roadGeoJson.optJSONArray("features");
        if (features == null || features.length() == 0) {
            features = new JSONArray();
            appendDiscoveryFeatures(features, result == null ? null : result.optJSONObject("motorway_geojson"));
            appendDiscoveryFeatures(features, result == null ? null : result.optJSONObject("a_road_geojson"));
        }
        List<JSONObject> output = new ArrayList<>();
        for (int index = 0; index < features.length(); index++) {
            JSONObject feature = features.optJSONObject(index);
            if (feature != null && !JourneyCorrectionUtils.excludesRoadFeature(journey, feature))
                output.add(feature);
        }
        return output;
    }

    private void appendDiscoveryFeatures(JSONArray target, JSONObject geojson) {
        JSONArray features = geojson == null ? null : geojson.optJSONArray("features");
        if (features == null) return;
        for (int index = 0; index < features.length(); index++) target.put(features.opt(index));
    }

    private static final class JourneyDiscoveryRecord {
        final String journeyId;
        final long startedAt;
        final Map<String, String> roads;
        JourneyDiscoveryRecord(String journeyId, long startedAt, Map<String, String> roads) {
            this.journeyId = journeyId;
            this.startedAt = startedAt;
            this.roads = roads;
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
        summary.setText(String.format("%s  •  %s  •  %d GPS points",
                DistanceUnits.format(this, metres), journeyDuration(journey), points));
        summary.setTextSize(16);
        summary.setTextColor(0xFFD3DCED);
        summary.setPadding(0, 4, 0, 0);

        TextView evidence = new TextView(this);
        String statusText = journeyStatusText(journey, points);
        evidence.setText(statusText);
        evidence.setTextSize(14);
        evidence.setTextColor(statusText.startsWith("Insufficient")
                || statusText.startsWith("Processing failed")
                || statusText.startsWith("Partially matched")
                || statusText.startsWith("Processed, but no matched route")
                ? 0xFFF7C450 : 0xFF67D5CC);
        evidence.setPadding(0, 16, 0, 16);

        details.addView(label);
        details.addView(heading);
        details.addView(summary);
        details.addView(evidence);
        addServiceStationConfirmation(details, journey);
        List<String> newRoads = newRoadHighlights.get(journey.optString("journey_id", ""));
        if (newRoads != null && !newRoads.isEmpty()) {
            TextView discoveries = new TextView(this);
            StringBuilder text = new StringBuilder("NEW ROADS · ").append(newRoads.size());
            int shown = Math.min(5, newRoads.size());
            for (int index = 0; index < shown; index++) {
                text.append(index == 0 ? "\n" : "  ·  ").append(newRoads.get(index));
            }
            if (newRoads.size() > shown) text.append("  ·  +").append(newRoads.size() - shown).append(" more");
            discoveries.setText(text.toString());
            discoveries.setTextSize(12);
            discoveries.setTypeface(null, android.graphics.Typeface.BOLD);
            discoveries.setTextColor(0xFFFFD36A);
            discoveries.setPadding(dp(11), dp(9), dp(11), dp(9));
            discoveries.setBackground(roundRect(0x332E5C9B, 0x887CB5EA, dp(10)));
            LinearLayout.LayoutParams discoveryParams = new LinearLayout.LayoutParams(-1, -2);
            discoveryParams.bottomMargin = dp(8);
            details.addView(discoveries, discoveryParams);
        }

        LinearLayout cardContent = new LinearLayout(this);
        cardContent.setOrientation(LinearLayout.HORIZONTAL);
        cardContent.setGravity(Gravity.TOP);
        cardContent.addView(details);

        ImageView modeIcon = RoadprintsIcons.image(this, RoadprintsIcons.transport(mode), 0xFF67D5CC, displayMode(mode));
        modeIcon.setPadding(dp(8), dp(8), dp(8), dp(8));
        modeIcon.setContentDescription(displayMode(mode));
        modeIcon.setBackground(roundRect(0xFF304B88, 0xFF8EA7D4, dp(12)));
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(44), dp(44));
        iconParams.setMargins(dp(12), dp(2), 0, 0);
        cardContent.addView(modeIcon, iconParams);

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        TextView inspect = actionButton("VIEW JOURNEY", 0xFF102047, Color.WHITE);
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

    private void addServiceStationConfirmation(LinearLayout details, JSONObject journey) {
        String processingStatus = journey.optString("processing_status", "");
        if (!ServiceStationStore.unlocked(this)
                || !("complete".equals(processingStatus) || "failed".equals(processingStatus))) return;
        JSONArray candidates = journey.optJSONArray("service_station_candidates");
        if (candidates == null || candidates.length() == 0) return;

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(12), dp(10), dp(12), dp(10));
        panel.setBackground(roundRect(0xFF1A3269, 0xFF67D5CC, dp(12)));
        TextView heading = new TextView(this);
        heading.setText("POSSIBLE SERVICE STATION STOP");
        heading.setTextColor(0xFF67D5CC);
        heading.setTextSize(12);
        heading.setTypeface(null, android.graphics.Typeface.BOLD);
        panel.addView(heading);

        for (int i = 0; i < candidates.length(); i++) {
            JSONObject candidate = candidates.optJSONObject(i);
            if (candidate == null) continue;
            String stationId = candidate.optString("id", "");
            String stationName = candidate.optString("name", "Service station");
            TextView name = new TextView(this);
            name.setText(stationName + " · Did you stop here?");
            name.setTextSize(14);
            name.setTextColor(Color.WHITE);
            name.setPadding(0, dp(8), 0, dp(8));
            panel.addView(name);

            LinearLayout choices = new LinearLayout(this);
            choices.setOrientation(LinearLayout.HORIZONTAL);
            TextView confirm = actionButton("Yes, I visited", 0xFFF7C450, 0xFF0B1C50);
            confirm.setOnClickListener(v -> resolveServiceStationCandidate(
                    journey, stationId, true));
            TextView dismiss = actionButton("No", 0xFF102047, 0xFFD3DCED);
            dismiss.setOnClickListener(v -> resolveServiceStationCandidate(
                    journey, stationId, false));
            LinearLayout.LayoutParams choiceParams = new LinearLayout.LayoutParams(
                    0, dp(44), 1);
            choices.addView(confirm, choiceParams);
            LinearLayout.LayoutParams dismissParams = new LinearLayout.LayoutParams(
                    0, dp(44), 1);
            dismissParams.leftMargin = dp(8);
            choices.addView(dismiss, dismissParams);
            panel.addView(choices);
        }

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(10);
        details.addView(panel, params);
    }

    private void resolveServiceStationCandidate(JSONObject journey, String stationId,
                                                boolean confirmedVisit) {
        String journeyId = journey.optString("journey_id", "");
        if (journeyId.isEmpty() || stationId.isEmpty()) return;
        try {
            processor.execute(() -> {
                try {
                    JSONObject latest = JourneyStore.get(getApplicationContext(), journeyId);
                    if (latest == null) return;
                    JSONArray existing = latest.optJSONArray("service_station_candidates");
                    JSONArray remaining = new JSONArray();
                    if (existing != null) {
                        for (int i = 0; i < existing.length(); i++) {
                            JSONObject item = existing.optJSONObject(i);
                            if (item != null && !stationId.equals(item.optString("id", "")))
                                remaining.put(item);
                        }
                    }
                    latest.put("service_station_candidates", remaining);
                    JourneyStore.save(getApplicationContext(), latest);
                    if (confirmedVisit) {
                        ServiceStationVisitStore.confirm(
                                getApplicationContext(), stationId, journeyId);
                    }
                    mainHandler.post(this::refreshJourneysAsync);
                } catch (Exception error) {
                    android.util.Log.w("Roadprints", "Could not save service station confirmation", error);
                    mainHandler.post(() -> Toast.makeText(this,
                            "Service station confirmation could not be saved.",
                            Toast.LENGTH_LONG).show());
                }
            });
        } catch (RejectedExecutionException ignored) {
            // The activity is already closing.
        }
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
        if (filterSummary != null) filterSummary.setText(FILTER_LABELS[activeFilter] + " · " + statusLabel(activeJourneyStatusFilter) + " · " + activeDateFilter.label());
    }

    private String statusLabel(String value) {
        for (int i = 0; i < STATUS_VALUES.length; i++) if (STATUS_VALUES[i].equals(value)) return STATUS_LABELS[i];
        return STATUS_LABELS[0];
    }

    private void applyJourneyFilters() {
        journeyCardLimit = INITIAL_JOURNEY_CARDS;
        render();
        if (journeyScroll != null) journeyScroll.post(() -> journeyScroll.scrollTo(0, 0));
    }

    private void showJourneyFilters() {
        final int[] mode = {activeFilter};
        final String[] status = {activeJourneyStatusFilter};
        final JourneyDateFilter[] date = {activeDateFilter};
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(18), dp(18), dp(18), dp(18));
        panel.setBackground(roundRect(0xFF0B1C50, 0xFF46649E, dp(18)));
        TextView title = new TextView(this);
        title.setText("Filter journeys"); title.setTextSize(24); title.setTextColor(Color.WHITE);
        panel.addView(title);
        TextView hint = new TextView(this);
        hint.setText("Combine transport, status and journey start date."); hint.setTextColor(0xFFB9C5D8);
        hint.setPadding(0, dp(8), 0, dp(12)); panel.addView(hint);
        ScrollView scroll = new ScrollView(this);
        LinearLayout choices = new LinearLayout(this); choices.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(choices);
        panel.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        Button apply = styledModalButton("SHOW JOURNEYS", 0xFFF7C450, 0xFF0B1C50);
        apply.setTag("journey_filter_apply");
        TextView error = new TextView(this); error.setTextColor(0xFFFF9393); panel.addView(error);
        Runnable update = () -> {
            int total = 0;
            if (journeys != null) for (JSONObject journey : journeys) {
                if (shouldShowJourney(journey) && (recapJourneyIds.isEmpty() || recapJourneyIds.contains(journey.optString("journey_id")))
                        && matchesFilter(journey, mode[0]) && matchesJourneyStatusFilter(journey, status[0])
                        && date[0].matches(journey, java.time.LocalDate.now(), ZoneId.systemDefault())) total++;
            }
            apply.setText(String.format(java.util.Locale.UK, "SHOW %,d JOURNEYS", total));
            apply.setEnabled(date[0].valid());
            error.setText(date[0].valid() ? "" : "Choose an end date on or after the start date.");
            error.setVisibility(date[0].valid() ? View.GONE : View.VISIBLE);
        };
        // Availability comes from the archive in this view, rather than another active
        // filter: choosing a narrow date must not hide the way to broaden transport.
        List<Integer> transportOptions = availableTransportOptions();
        List<Integer> statusOptions = availableStatusOptions();
        List<Integer> dateOptions = availableDateOptions();
        if (!transportOptions.contains(mode[0])) mode[0] = 0;
        int statusIndex = java.util.Arrays.asList(STATUS_VALUES).indexOf(status[0]);
        if (!statusOptions.contains(statusIndex)) status[0] = "all";
        addFilterHeading(choices, "Transport");
        Spinner transport = filterSpinner(choices, optionLabels(FILTER_LABELS, transportOptions), "Transport");
        transport.setSelection(transportOptions.indexOf(mode[0]));
        transport.setOnItemSelectedListener(selection(position -> { mode[0] = transportOptions.get(position); update.run(); }));
        addFilterHeading(choices, "Matching status");
        Spinner statuses = filterSpinner(choices, optionLabels(STATUS_LABELS, statusOptions), "Matching status");
        statuses.setSelection(statusOptions.indexOf(java.util.Arrays.asList(STATUS_VALUES).indexOf(status[0])));
        statuses.setOnItemSelectedListener(selection(position -> { status[0] = STATUS_VALUES[statusOptions.get(position)]; update.run(); }));
        addFilterHeading(choices, "Date");
        Spinner dates = filterSpinner(choices, optionLabels(JourneyDateFilter.LABELS, dateOptions), "Date");
        int dateIndex = java.util.Arrays.asList(JourneyDateFilter.VALUES).indexOf(date[0].preset);
        if (!dateOptions.contains(dateIndex)) date[0] = JourneyDateFilter.all();
        dates.setSelection(Math.max(0, dateOptions.indexOf(java.util.Arrays.asList(JourneyDateFilter.VALUES).indexOf(date[0].preset))));
        LinearLayout range = new LinearLayout(this); range.setOrientation(LinearLayout.VERTICAL); choices.addView(range);
        Button from = styledModalButton("FROM", 0xFF233B78, Color.WHITE);
        Button to = styledModalButton("TO", 0xFF233B78, Color.WHITE);
        addFilterButton(range, from); addFilterButton(range, to);
        Runnable rangeLabels = () -> {
            range.setVisibility("custom".equals(date[0].preset) ? View.VISIBLE : View.GONE);
            from.setText("From: " + (date[0].from == null ? "Choose date" : date[0].from.format(DateTimeFormatter.ofPattern("d MMM yyyy", java.util.Locale.UK))));
            to.setText("To: " + (date[0].to == null ? "Choose date" : date[0].to.format(DateTimeFormatter.ofPattern("d MMM yyyy", java.util.Locale.UK))));
        };
        from.setOnClickListener(v -> pickFilterDate(date[0].from, chosen -> { date[0] = new JourneyDateFilter("custom", chosen, date[0].to); rangeLabels.run(); update.run(); }));
        to.setOnClickListener(v -> pickFilterDate(date[0].to, chosen -> { date[0] = new JourneyDateFilter("custom", date[0].from, chosen); rangeLabels.run(); update.run(); }));
        dates.setOnItemSelectedListener(selection(position -> {
            date[0] = new JourneyDateFilter(JourneyDateFilter.VALUES[dateOptions.get(position)], date[0].from, date[0].to);
            if ("custom".equals(date[0].preset) && date[0].from == null) date[0] = new JourneyDateFilter("custom", latestJourneyDate(), latestJourneyDate());
            rangeLabels.run(); update.run();
        }));
        Button reset = styledModalButton("RESET FILTERS", 0xFF233B78, Color.WHITE);
        reset.setOnClickListener(v -> { mode[0] = 0; status[0] = "all"; date[0] = JourneyDateFilter.all(); transport.setSelection(0); statuses.setSelection(0); dates.setSelection(0); rangeLabels.run(); update.run(); });
        reset.setTag("journey_filter_reset");
        addFilterButton(panel, reset); addFilterButton(panel, apply);
        Button close = styledModalButton("CANCEL", 0xFF0B1C50, 0xFFF7C450); addFilterButton(panel, close);
        AlertDialog dialog = new AlertDialog.Builder(this).setView(panel).create();
        apply.setOnClickListener(v -> { if (!date[0].valid()) return; activeFilter = mode[0]; activeJourneyStatusFilter = status[0]; activeDateFilter = date[0]; applyJourneyFilters(); dialog.dismiss(); });
        close.setOnClickListener(v -> dialog.dismiss());
        rangeLabels.run(); update.run(); dialog.show();
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        dialog.getWindow().setLayout((int)(getResources().getDisplayMetrics().widthPixels * .94), (int)(getResources().getDisplayMetrics().heightPixels * .82));
    }

    private void addFilterHeading(LinearLayout parent, String label) {
        TextView heading = new TextView(this); heading.setText(label); heading.setTextColor(0xFF67D5CC); heading.setTextSize(16);
        heading.setPadding(0, dp(16), 0, dp(8)); parent.addView(heading);
    }

    private void addFilterButton(LinearLayout parent, Button button) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(52));
        params.topMargin = dp(12);
        parent.addView(button, params);
    }

    private boolean inFilterArchive(JSONObject journey) {
        return shouldShowJourney(journey) && (recapJourneyIds.isEmpty() || recapJourneyIds.contains(journey.optString("journey_id")));
    }

    private List<Integer> availableTransportOptions() {
        List<Integer> available = new ArrayList<>(); available.add(0);
        if (journeys != null) for (int i = 1; i < FILTER_VALUES.length; i++) {
            for (JSONObject journey : journeys) if (inFilterArchive(journey) && matchesFilter(journey, i)) { available.add(i); break; }
        }
        return available;
    }

    private List<Integer> availableStatusOptions() {
        List<Integer> available = new ArrayList<>(); available.add(0);
        if (journeys != null) for (int i = 1; i < STATUS_VALUES.length; i++) {
            for (JSONObject journey : journeys) if (inFilterArchive(journey) && matchesJourneyStatusFilter(journey, STATUS_VALUES[i])) { available.add(i); break; }
        }
        return available;
    }

    private List<Integer> availableDateOptions() {
        List<Integer> available = new ArrayList<>(); available.add(0);
        java.time.LocalDate today = java.time.LocalDate.now(); ZoneId zone = ZoneId.systemDefault();
        if (journeys != null) for (int i = 1; i < JourneyDateFilter.VALUES.length - 1; i++) {
            JourneyDateFilter candidate = new JourneyDateFilter(JourneyDateFilter.VALUES[i], null, null);
            for (JSONObject journey : journeys) if (inFilterArchive(journey) && candidate.matches(journey, today, zone)) { available.add(i); break; }
        }
        // Keep the custom range action and an explicitly saved range/preset accessible.
        int selected = java.util.Arrays.asList(JourneyDateFilter.VALUES).indexOf(activeDateFilter.preset);
        if (selected > 0 && selected < 4 && !available.contains(selected)) available.add(selected);
        available.add(4);
        return available;
    }

    private java.time.LocalDate latestJourneyDate() {
        long latest = Long.MIN_VALUE;
        if (journeys != null) for (JSONObject journey : journeys) if (inFilterArchive(journey)) latest = Math.max(latest, journeyStartMillis(journey));
        return latest == Long.MIN_VALUE ? java.time.LocalDate.now() : Instant.ofEpochMilli(latest).atZone(ZoneId.systemDefault()).toLocalDate();
    }

    private String[] optionLabels(String[] labels, List<Integer> indices) {
        String[] values = new String[indices.size()];
        for (int i = 0; i < indices.size(); i++) values[i] = labels[indices.get(i)];
        return values;
    }

    private Spinner filterSpinner(LinearLayout parent, String[] labels, String description) {
        Spinner spinner = new Spinner(this, Spinner.MODE_DROPDOWN);
        spinner.setContentDescription(description);
        spinner.setTag("journey_filter_" + description.toLowerCase(java.util.Locale.ROOT).replace(' ', '_'));
        spinner.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, labels) {
            @Override public View getView(int position, View convert, android.view.ViewGroup group) {
                LinearLayout row = new LinearLayout(JourneyListActivity.this); row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(16), 0, dp(16), 0);
                TextView label = filterOptionText(labels[position]);
                row.addView(label, new LinearLayout.LayoutParams(0, -1, 1));
                ImageView arrow = RoadprintsIcons.image(JourneyListActivity.this, R.drawable.ic_roadprints_chevron_down, 0xFFB9C5D8, null);
                arrow.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                row.addView(arrow, new LinearLayout.LayoutParams(dp(20), dp(20)));
                return row;
            }
            @Override public View getDropDownView(int position, View convert, android.view.ViewGroup group) {
                TextView label = filterOptionText(labels[position]); label.setPadding(dp(16), dp(12), dp(16), dp(12)); return label;
            }
        });
        spinner.setPopupBackgroundDrawable(roundRect(0xFF233B78, 0xFF46649E, dp(12)));
        spinner.setBackground(roundRect(0xFF233B78, 0xFF46649E, dp(12)));
        parent.addView(spinner, new LinearLayout.LayoutParams(-1, dp(48))); return spinner;
    }

    private TextView filterOptionText(String value) {
        TextView label = new TextView(this); label.setText(value); label.setTextColor(Color.WHITE);
        label.setTextSize(16); label.setGravity(Gravity.CENTER_VERTICAL); label.setMinHeight(dp(48)); return label;
    }

    private AdapterView.OnItemSelectedListener selection(java.util.function.IntConsumer listener) {
        return new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) { listener.accept(position); }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        };
    }

    private void pickFilterDate(java.time.LocalDate current, java.util.function.Consumer<java.time.LocalDate> selected) {
        java.time.LocalDate initial = current == null ? java.time.LocalDate.now() : current;
        android.app.DatePickerDialog dialog = new android.app.DatePickerDialog(this, R.style.RoadprintsDatePickerDialog,
                (picker, year, month, day) -> selected.accept(java.time.LocalDate.of(year, month + 1, day)),
                initial.getYear(), initial.getMonthValue() - 1, initial.getDayOfMonth());
        dialog.show();
        android.view.ViewTreeObserver.OnGlobalLayoutListener headerStyle = () -> styleCalendarYear(dialog.getDatePicker());
        dialog.getDatePicker().getViewTreeObserver().addOnGlobalLayoutListener(headerStyle);
        dialog.setOnDismissListener(closed -> dialog.getDatePicker().getViewTreeObserver().removeOnGlobalLayoutListener(headerStyle));
        styleCalendarYear(dialog.getDatePicker());
        styleCalendarButton(dialog.getButton(AlertDialog.BUTTON_POSITIVE), 0xFFF7C450, 0xFF0B1C50);
        styleCalendarButton(dialog.getButton(AlertDialog.BUTTON_NEGATIVE), 0xFF233B78, Color.WHITE);
    }

    private void styleCalendarYear(View view) {
        if (view instanceof TextView && ((TextView)view).getText().toString().matches("[12][0-9]{3}")) {
            ((TextView)view).setTextColor(Color.WHITE); view.setAlpha(1f);
        }
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup)view;
            for (int i = 0; i < group.getChildCount(); i++) styleCalendarYear(group.getChildAt(i));
        }
    }

    private void styleCalendarButton(Button button, int background, int foreground) {
        button.setTextColor(foreground); button.setTextSize(14); button.setMinHeight(dp(48));
        button.setStateListAnimator(null); button.setElevation(0);
        if (button.getParent() instanceof android.view.ViewGroup) {
            android.view.ViewGroup parent = (android.view.ViewGroup)button.getParent();
            parent.setClipChildren(false); parent.setClipToPadding(false);
            parent.setPadding(parent.getPaddingLeft(), dp(12), parent.getPaddingRight(), dp(12));
            android.view.ViewGroup.LayoutParams row = parent.getLayoutParams();
            if (row != null) { row.height = android.view.ViewGroup.LayoutParams.WRAP_CONTENT; parent.setLayoutParams(row); }
        }
        button.setPadding(dp(16), 0, dp(16), 0); button.setBackground(roundRect(background, background, dp(12)));
        android.view.ViewGroup.LayoutParams original = button.getLayoutParams();
        if (original instanceof android.view.ViewGroup.MarginLayoutParams) {
            android.view.ViewGroup.MarginLayoutParams margins = (android.view.ViewGroup.MarginLayoutParams)original;
            margins.height = dp(48);
            margins.setMargins(dp(6), 0, dp(6), 0); button.setLayoutParams(margins);
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

    private static List<JSONArray> matchedRouteSegments(JSONObject result) {
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

    static List<JSONArray> matchedRouteSegmentsForDisplay(JSONObject journey) {
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

    static final class JourneyPreview {
        final JSONArray coordinates;
        final List<JSONArray> matches;
        final String legend;
        JourneyPreview(JSONArray coordinates, List<JSONArray> matches, String legend) {
            this.coordinates = coordinates; this.matches = matches; this.legend = legend;
        }
    }

    static JourneyPreview journeyPreview(JSONObject journey) {
        JSONObject geometry = journey.optJSONObject("route_geometry");
        JSONArray coordinates = geometry == null ? null : geometry.optJSONArray("coordinates");
        String mode = journey.optString("mode", "unknown").trim().toLowerCase(java.util.Locale.ROOT);
        if (isPointToPointMode(mode)) return new JourneyPreview(endpointCoordinates(coordinates),
                java.util.Collections.emptyList(), "Point-to-point journey");
        JSONObject result = journey.optJSONObject("processing_result");
        if (!matchedRouteSegments(result).isEmpty()) {
            List<JSONArray> visible = matchedRouteSegmentsForDisplay(journey);
            boolean partial = isPartialMatchResult(result);
            return new JourneyPreview(null, visible, visible.isEmpty() ? "All matched sections removed"
                    : partial ? "Partially matched route · unmatched sections omitted" : "Matched route");
        }
        return new JourneyPreview(coordinates, java.util.Collections.emptyList(), "Recorded route");
    }

    private void refreshDetailPreview(AlertDialog dialog, JSONObject journey) {
        View root = dialog.getWindow() == null ? null : dialog.getWindow().getDecorView();
        if (root == null) return;
        RoutePreviewView preview = root.findViewWithTag("journey_route_preview");
        TextView legend = root.findViewWithTag("journey_route_legend");
        JourneyPreview route = journeyPreview(journey);
        if (preview != null) preview.setRouteData(route.coordinates, route.matches);
        if (legend != null) legend.setText(route.legend);
        View replayPlay = root.findViewWithTag("journey_replay_play");
        if (replayPlay != null) replayPlay.setVisibility(
                !isPointToPointMode(journey.optString("mode", "")) && !route.matches.isEmpty()
                        ? View.VISIBLE : View.GONE);
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
        JourneyPreview route = journeyPreview(journey);
        if(!pointToPoint&&!route.matches.isEmpty())
            DiscoveryReplayCache.request(getApplicationContext(),java.util.Collections.singleton(journey.optString("journey_id")),null);
        RoutePreviewView preview = new RoutePreviewView(this, route.coordinates, route.matches);
        preview.setTag("journey_route_preview");
        FrameLayout previewFrame = new FrameLayout(this);
        previewFrame.addView(preview, new FrameLayout.LayoutParams(-1, -1));
        TextView replayPlay = new TextView(this);
        replayPlay.setText("▶");
        replayPlay.setTag("journey_replay_play");
        replayPlay.setTextSize(22);
        replayPlay.setTextColor(0xFF0B1C50);
        replayPlay.setGravity(Gravity.CENTER);
        replayPlay.setBackground(roundRect(0xFFF7C450, 0xFF0B1C50, dp(24)));
        replayPlay.setContentDescription("Replay journey discoveries on the map");
        replayPlay.setClickable(true);
        replayPlay.setFocusable(true);
        replayPlay.setVisibility(!pointToPoint && !route.matches.isEmpty() ? View.VISIBLE : View.GONE);
        FrameLayout.LayoutParams playParams = new FrameLayout.LayoutParams(dp(48), dp(48),
                Gravity.BOTTOM | Gravity.LEFT);
        playParams.setMargins(dp(12), 0, 0, dp(12));
        previewFrame.addView(replayPlay, playParams);
        body.addView(previewFrame, new LinearLayout.LayoutParams(-1, dp(190)));
        TextView routeLegend = new TextView(this);
        routeLegend.setTag("journey_route_legend");
        routeLegend.setText(route.legend);
        routeLegend.setTextSize(11);
        routeLegend.setTextColor(0xFFD3DCED);
        routeLegend.setPadding(side, dp(6), side, dp(2));
        body.addView(routeLegend);

        LinearLayout fields = new LinearLayout(this);
        fields.setOrientation(LinearLayout.VERTICAL);
        fields.setPadding(side, dp(18), side, dp(12));
        fields.addView(detailRow("Start", displayTime(journey.optString("started_at"))));
        fields.addView(detailRow("End", displayTime(journey.optString("ended_at"))));
        fields.addView(detailRow("Duration", journeyDuration(journey)));
        fields.addView(detailRow("Distance", DistanceUnits.format(this, metres)));
        fields.addView(detailRow("GPS points", DistanceUnits.formatPointCount(points)));
        fields.addView(detailRow("Transport", displayMode(journey.optString("mode", "unknown"))));

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
        if ("unknown".equals(mode)
                && "required".equals(journey.optString("transport_confirmation"))) {
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
            boolean partial = isPartialMatchResult(journey.optJSONObject("processing_result"))
                    || journey.optJSONObject("processing_result").optBoolean("timeline_partial_match", false);
            statusText = partial ? (journey.optJSONObject("processing_result").optBoolean("timeline_partial_match", false)
                    ? "Partially matched · Timeline data is incomplete or inconsistent. Only matched sections count."
                    : journey.optJSONObject("processing_result").optBoolean("endpoint_partial_match", false)
                    ? "Road portion matched · start/end GPS samples remain unmatched"
                    : "Partial match · some route sections were not matched")
                    : "✓  Processed · matched route available";
            statusColor = partial ? 0xFFF7C450 : 0xFF8BE0B1;
        } else if ("complete".equals(processingStatus)) {
            statusText = "No matched route is available · retry matching from this journey";
            statusColor = 0xFFF7C450;
        } else if ("processing".equals(processingStatus)) {
            statusText = "Matching in progress…";
            statusColor = 0xFFF7C450;
        } else if ("failed".equals(processingStatus)) {
            JSONObject recordingQuality = journey.optJSONObject("recording_quality");
            statusText = recordingQuality != null && !recordingQuality.optBoolean("resolved", true)
                    ? "Recording quality issue · GPS gap or unreliable fixes. Original recording preserved."
                    : "Matching failed · retry from this journey";
            statusColor = 0xFFF7C450;
        } else {
            statusText = "Ready to match from this journey";
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
        replayPlay.setOnClickListener(v -> requestCloseWithUnsavedChanges(
                dialogRef[0], hasUnsavedChanges(titleInput, transport,
                        transportModes, savedTitle[0], savedMode[0]),
                saveEdits, () -> startActivity(JourneyReplayActivity.replayIntent(this, journey))));
        actions.addView(saveChanges, actionLayoutParams());

        LinearLayout.LayoutParams secondaryParams = actionLayoutParams();
        secondaryParams.topMargin = dp(8);
        Button matchJourney = null;
        if (processableMode && enoughEvidence) {
            boolean matched = "complete".equals(processingStatus) && hasMatchedGeometry;
            boolean matching = "processing".equals(processingStatus);
            String actionText = matched ? "VIEW & EDIT ON MAP"
                    : matching ? "MATCHING…"
                    : "failed".equals(processingStatus) || "complete".equals(processingStatus)
                        ? "RETRY MATCHING" : "MATCH JOURNEY";
            matchJourney = styledModalButton(actionText,
                    matched ? 0xFF233B78 : 0xFFF7C450,
                    matched ? Color.WHITE : 0xFF0B1C50);
            matchJourney.setEnabled(!matching);
            matchJourney.setAlpha(matching ? 0.62f : 1f);
            Button journeyAction = matchJourney;
            if (matched) {
                journeyAction.setOnClickListener(v -> requestCloseWithUnsavedChanges(
                        dialogRef[0], hasUnsavedChanges(titleInput, transport,
                                transportModes, savedTitle[0], savedMode[0]),
                        saveEdits, () -> showMatchedRefinement(journey, dialogRef)));
            } else if (!matching) {
                journeyAction.setOnClickListener(v -> requestCloseWithUnsavedChanges(
                        dialogRef[0], hasUnsavedChanges(titleInput, transport,
                                transportModes, savedTitle[0], savedMode[0]),
                        saveEdits, () -> startSingleJourneyMatch(
                                journey, journeyAction, status, dialogRef,
                                titleInput, transport, transportModes, savedTitle, savedMode, saveEdits)));
            }
            actions.addView(journeyAction, secondaryParams);
            if (matched) {
                Button rematch = styledModalButton("REMATCH JOURNEY", 0xFFF7C450, 0xFF0B1C50);
                LinearLayout.LayoutParams rematchParams = actionLayoutParams();
                rematchParams.topMargin = dp(8);
                rematch.setOnClickListener(v -> requestCloseWithUnsavedChanges(
                        dialogRef[0], hasUnsavedChanges(titleInput, transport,
                                transportModes, savedTitle[0], savedMode[0]),
                        saveEdits, () -> startSingleJourneyMatch(
                                journey, rematch, status, dialogRef,
                                titleInput, transport, transportModes, savedTitle, savedMode,
                                saveEdits, true)));
                actions.addView(rematch, rematchParams);
            }
        }

        TextView delete = new TextView(this);
        delete.setText("Delete Journey");
        delete.setTextSize(14);
        delete.setTextColor(0xFFFF8A8A);
        delete.setGravity(Gravity.CENTER);
        delete.setPadding(dp(12), dp(14), dp(12), dp(8));
        delete.setOnClickListener(v -> confirmDelete(journey));
        LinearLayout supplemental = new LinearLayout(this);
        supplemental.setGravity(Gravity.CENTER);
        supplemental.addView(delete);
        TextView divider = new TextView(this);
        divider.setText("|");
        divider.setTextColor(0xFF8FA2C5);
        divider.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        supplemental.addView(divider);
        TextView debug = new TextView(this);
        debug.setText("Debug Journey");
        debug.setTextSize(14);
        debug.setTextColor(0xFFD3DCED);
        debug.setGravity(Gravity.CENTER);
        debug.setPadding(dp(12), dp(14), dp(12), dp(8));
        debug.setOnClickListener(v -> exportJourneyDebug(journey.optString("journey_id")));
        supplemental.addView(debug);
        actions.addView(supplemental, new LinearLayout.LayoutParams(
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
        if (matchJourney != null
                && "processing".equals(journey.optString("processing_status"))) {
            watchSingleJourneyMatch(journey, matchJourney, status, dialogRef,
                    titleInput, transport, transportModes, savedTitle, savedMode, saveEdits, false);
        }
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
        String journeyId = journey == null ? "" : journey.optString("journey_id", "");
        if (journeyId.isEmpty()) {
            Toast.makeText(this, "Journey could not be opened", Toast.LENGTH_LONG).show();
            return;
        }
        Intent editor = new Intent(this, JourneyMapEditorActivity.class);
        editor.putExtra("journey_id", journeyId);
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

    private void startSingleJourneyMatch(
            JSONObject journey, Button action, TextView status, AlertDialog[] dialogRef,
            EditText titleInput, Spinner transport, String[] transportModes,
            String[] savedTitle, String[] savedMode, Runnable saveEdits) {
        startSingleJourneyMatch(journey, action, status, dialogRef, titleInput, transport,
                transportModes, savedTitle, savedMode, saveEdits, false);
    }

    private void startSingleJourneyMatch(
            JSONObject journey, Button action, TextView status, AlertDialog[] dialogRef,
            EditText titleInput, Spinner transport, String[] transportModes,
            String[] savedTitle, String[] savedMode, Runnable saveEdits, boolean forceRematch) {
        if (!canMatchJourney(journey)) {
            status.setText("This journey does not have enough route points to match.");
            return;
        }
        MatchingCoordinator coordinator = MatchingCoordinator.get(this);
        boolean accepted = forceRematch
                ? coordinator.rematch(journey.optString("journey_id"))
                : coordinator.start(journey.optString("journey_id"));
        if (!accepted) {
            status.setText("Another matching run is active. Try this journey again when it finishes.");
            action.setText("MATCH JOURNEY");
            action.setEnabled(true);
            action.setAlpha(1f);
            return;
        }
        action.setText("MATCHING…");
        action.setEnabled(false);
        action.setAlpha(0.62f);
        status.setText("Matching this journey…");
        status.setTextColor(0xFFF7C450);
        watchSingleJourneyMatch(journey, action, status, dialogRef,
                titleInput, transport, transportModes, savedTitle, savedMode, saveEdits, forceRematch);
    }

    private void watchSingleJourneyMatch(
            JSONObject journey, Button action, TextView status, AlertDialog[] dialogRef,
            EditText titleInput, Spinner transport, String[] transportModes,
            String[] savedTitle, String[] savedMode, Runnable saveEdits, boolean forceRematch) {
        AlertDialog dialog = dialogRef[0];
        if (dialog == null || !dialog.isShowing()) return;
        MatchingCoordinator.Snapshot snapshot = MatchingCoordinator.get(this).snapshot();
        if (snapshot.state == MatchingCoordinator.State.PREPARING
                || snapshot.state == MatchingCoordinator.State.RUNNING
                || snapshot.state == MatchingCoordinator.State.PAUSING) {
            mainHandler.postDelayed(
                    () -> watchSingleJourneyMatch(journey, action, status, dialogRef,
                            titleInput, transport, transportModes, savedTitle, savedMode, saveEdits, forceRematch), 1000L);
            return;
        }
        String journeyId = journey.optString("journey_id", "");
        processor.execute(() -> {
            JSONObject latest;
            try {
                latest = JourneyStore.get(getApplicationContext(), journeyId);
            } catch (Exception error) {
                latest = null;
            }
            JSONObject saved = latest;
            mainHandler.post(() -> {
                if (dialogRef[0] == null || !dialogRef[0].isShowing()) return;
                if (saved == null) {
                    status.setText("Journey status could not be refreshed. Retry matching.");
                    action.setText("RETRY MATCHING");
                    action.setEnabled(true);
                    action.setAlpha(1f);
                    return;
                }
                try {
                    journey.put("processing_status", saved.optString("processing_status", "pending"));
                    if (saved.has("processing_result")) {
                        journey.put("processing_result", saved.optJSONObject("processing_result"));
                    } else {
                        journey.remove("processing_result");
                    }
                } catch (Exception ignored) { }
                boolean matched = "complete".equals(saved.optString("processing_status"))
                        && !matchedRouteSegments(saved.optJSONObject("processing_result")).isEmpty();
                if (matched) {
                    refreshDetailPreview(dialogRef[0], saved);
                    boolean partial = isPartialMatchResult(saved.optJSONObject("processing_result"));
                    status.setText(partial ? "Partially matched · some source sections could not be matched"
                            : "✓  Processed · matched route available");
                    status.setTextColor(partial ? 0xFFF7C450 : 0xFF8BE0B1);
                    action.setText(forceRematch ? "REMATCH JOURNEY" : "VIEW & EDIT ON MAP");
                    action.setEnabled(true);
                    action.setAlpha(1f);
                    if (!forceRematch) action.setOnClickListener(v -> requestCloseWithUnsavedChanges(
                            dialogRef[0], hasUnsavedChanges(titleInput, transport,
                                    transportModes, savedTitle[0], savedMode[0]),
                            saveEdits,
                            () -> showMatchedRefinement(journey, dialogRef)));
                } else if ("processing".equals(saved.optString("processing_status"))) {
                    mainHandler.postDelayed(
                            () -> watchSingleJourneyMatch(journey, action, status, dialogRef,
                                    titleInput, transport, transportModes, savedTitle, savedMode, saveEdits, forceRematch), 1000L);
                } else {
                    status.setText("Matching failed or returned no route. You can retry here.");
                    status.setTextColor(0xFFF7C450);
                    action.setText("RETRY MATCHING");
                    action.setEnabled(true);
                    action.setAlpha(1f);
                    action.setOnClickListener(v -> startSingleJourneyMatch(
                            journey, action, status, dialogRef,
                            titleInput, transport, transportModes, savedTitle, savedMode, saveEdits));
                }
            });
        });
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
        if (journey.has("gps_point_count")) {
            return journey.optInt("gps_point_count", 0);
        }
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
        if ("complete".equals(status)) {
            if (!hasStoredMatch(journey)) return "Processed, but no matched route was saved";
            return isPartialMatchResult(journey.optJSONObject("processing_result"))
                    ? "Partially matched · rematch available" : "Processed by Roadprints";
        }
        if ("processing".equals(status)) return "Processing journey…";
        if ("failed".equals(status)) return "Processing failed — retry available";
        if (isFootMode(mode)) return "Ready for on-foot matching";
        return "Ready for road matching";
    }

    private static boolean isPartialMatchResult(JSONObject result) {
        if (result == null) return false;
        if (result.optBoolean("timeline_partial_match", false)) return true;
        JSONArray failed = result.optJSONArray("failed_sections");
        if (failed != null && failed.length() > 0) return true;
        int input = result.optInt("input_points", -1);
        int matched = result.optInt("matched_tracepoints", -1);
        return input >= 0 && matched >= 0 && matched < input;
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
        if (isFootMode(value)) return "On foot";
        if ("cycling".equals(value)) return "Cycling";
        if ("bus".equals(value)) return "Bus";
        if ("train".equals(value)) return "Train";
        if ("plane".equals(value)) return "Flight";
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


