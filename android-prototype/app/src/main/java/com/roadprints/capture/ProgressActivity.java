package com.roadprints.capture;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.ImageView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONObject;
import org.json.JSONArray;

import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.Comparator;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ProgressActivity extends Activity {
    private static final Object STATS_CACHE_LOCK = new Object();
    private static DistanceStats processCachedStats;
    private static long processCachedRevision = Long.MIN_VALUE;
    private static boolean statsLoadInFlight;
    private static java.util.concurrent.Executor statisticsExecutor = ScreenDataLoader::execute;
    private static final List<java.lang.ref.WeakReference<ProgressActivity>> screens = new ArrayList<>();
    private static final Set<String> expandedRoadCategories = new HashSet<>();
    private static final ExecutorService SETTLEMENT_WORKER = Executors.newSingleThreadExecutor();
    private static int savedScrollY;
    private LinearLayout statisticsContent;
    private ScrollView statisticsScroll;
    private final Map<String, TextView> progressJumpChips = new LinkedHashMap<>();
    private boolean hasResumed;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private int statsLoadGeneration;
    private DistanceStats loadedStats;
    private long loadedRevision = Long.MIN_VALUE;
    private boolean renderedKilometres;
    private boolean renderingPendingStats;
    private LinearLayout roadDiscoveryHost;
    private TextView refreshStatus;
    private final Runnable revisionTicker = new Runnable() {
        @Override public void run() { if (isFinishing() || isDestroyed()) return; loadStatistics(); mainHandler.postDelayed(this, 3000); }
    };
    private TextView loadingDetail;
    private TextView localRoadProgressMessage;
    private TextView localRoadProgressDetail;
    private ProgressBar localRoadProgressBar;
    private Runnable localRoadProgressTicker;
    private boolean useKilometres;
    private static final int NAVY = 0xFF0B1C50;
    private static final int NAV_BAR = 0xFF10275D;
    private static final int CARD = 0xFF233B78;
    private static final int MUTED = 0xFFB9C5D8;
    private static final int GOLD = 0xFFF7C450;
    private static final int TEAL = 0xFF67D5CC;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        useKilometres = DistanceUnits.usesKilometres(this);
        getWindow().setStatusBarColor(NAVY);
        getWindow().setNavigationBarColor(NAV_BAR);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(NAVY);

        LinearLayout heading = new LinearLayout(this);
        heading.setOrientation(LinearLayout.VERTICAL);
        heading.setPadding(dp(18), dp(18), dp(18), dp(12));

        LinearLayout brand = RoadprintsHeader.create(this);
        brand.setPadding(0, 0, 0, dp(24));
        heading.addView(brand);

        TextView eyebrow = new TextView(this);
        eyebrow.setText("YOUR TRAVEL RECORD");
        eyebrow.setTextSize(12);
        eyebrow.setTypeface(null, android.graphics.Typeface.BOLD);
        eyebrow.setTextColor(TEAL);

        TextView title = new TextView(this);
        title.setText("Progress");
        title.setTextSize(28);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setTextColor(Color.WHITE);

        TextView intro = new TextView(this);
        intro.setText("Distance from your imported and tracked journeys.");
        intro.setTextSize(14);
        intro.setTextColor(0xFFD3DCED);
        intro.setPadding(0, dp(4), 0, 0);

        heading.addView(eyebrow);
        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        title.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
        titleRow.addView(title);
        heading.addView(titleRow);
        heading.addView(intro);
        HorizontalScrollView progressJumpMenu = buildProgressJumpMenu();
        LinearLayout.LayoutParams jumpParams = new LinearLayout.LayoutParams(-1, -2);
        jumpParams.topMargin = dp(14);
        heading.addView(progressJumpMenu, jumpParams);
        root.addView(heading);

        ScrollView scroll = new ScrollView(this);
        statisticsScroll = scroll;
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(8), dp(18), dp(22));
        scroll.addView(content);

        statisticsContent = content;
        scroll.setOnScrollChangeListener((View view, int x, int y, int oldX, int oldY) ->
                updateActiveProgressJump());
        refreshStatus = new TextView(this); refreshStatus.setTextColor(MUTED); refreshStatus.setTextSize(12);
        refreshStatus.setPadding(0, dp(8), 0, 0); heading.addView(refreshStatus);
        synchronized (STATS_CACHE_LOCK) { screens.add(new java.lang.ref.WeakReference<>(this)); }
        DistanceStats initial;
        synchronized (STATS_CACHE_LOCK) { initial = processCachedStats; }
        if (initial == null) initial = readStatisticsSnapshot();
        if (initial == null) { initial = new DistanceStats(); initial.pending = true; }
        loadedStats = initial; renderStatistics(content, initial);

        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        View bottomNavigation = buildBottomNavigation();
        root.addView(bottomNavigation);
        setContentView(root);
        applySystemBarInsets(root, heading, bottomNavigation);
        loadStatistics();
    }

    @Override
    protected void onResume() {
        super.onResume();
        useKilometres = DistanceUnits.usesKilometres(this);
        mainHandler.removeCallbacks(revisionTicker); mainHandler.postDelayed(revisionTicker, 3000);
        if (hasResumed) {
            loadStatistics();
        } else {
            hasResumed = true;
        }
    }

    @Override
    protected void onPause() {
        if (statisticsScroll != null) savedScrollY = statisticsScroll.getScrollY();
        stopLocalRoadProgressTicker();
        mainHandler.removeCallbacks(revisionTicker);
        super.onPause();
    }

    private boolean isShownInJourneysList(JSONObject journey, String mode) {
        boolean road = "driving".equals(mode) || "bus".equals(mode);
        boolean foot = "walking".equals(mode) || "running".equals(mode)
                || "pedestrian".equals(mode);
        if (!road && !foot) return true;

        JSONObject geometry = journey.optJSONObject("route_geometry");
        JSONArray coordinates = geometry == null ? null : geometry.optJSONArray("coordinates");
        if (coordinates == null || coordinates.length() < 2) return false;

        if (road) {
            JSONObject source = journey.optJSONObject("source");
            if (source != null && "timeline_import".equals(source.optString("type", ""))) {
                JSONObject quality = journey.optJSONObject("capture_quality");
                return quality != null && quality.optInt("source_route_points", 0) >= 2;
            }
        }
        return true;
    }

    @Override protected void onDestroy() {
        statsLoadGeneration++;
        mainHandler.removeCallbacks(revisionTicker); stopLocalRoadProgressTicker();
        synchronized (STATS_CACHE_LOCK) { screens.removeIf(ref -> ref.get() == null || ref.get() == this); }
        super.onDestroy();
    }

    static void clearCachedStatistics(android.content.Context context) {
        synchronized (STATS_CACHE_LOCK) { processCachedStats = null; processCachedRevision = Long.MIN_VALUE; }
        context.getSharedPreferences("roadprints_progress_snapshot", MODE_PRIVATE).edit().clear().commit();
        synchronized (STATS_CACHE_LOCK) { for (java.lang.ref.WeakReference<ProgressActivity> ref : screens) {
            ProgressActivity screen = ref.get(); if (screen == null) continue;
            screen.mainHandler.post(() -> { if (screen.isFinishing() || screen.isDestroyed()) return;
                screen.loadedStats = new DistanceStats(); screen.loadedStats.pending = true;
                screen.renderStatistics(screen.statisticsContent, screen.loadedStats); screen.loadStatistics(); });
        } }
    }

    private void loadStatistics() {
        final android.content.Context app = getApplicationContext();
        final long revision = JourneyStore.dataRevision(app);
        final int generation = statsLoadGeneration;
        DistanceStats cached; long cachedRevision;
        synchronized (STATS_CACHE_LOCK) { cached = processCachedStats; cachedRevision = processCachedRevision; }
        if (cached != null && (loadedStats != cached || renderedKilometres != useKilometres)) {
            loadedStats = cached; renderStatistics(statisticsContent, cached); restoreProgressScroll();
        } else if (loadedStats != null && renderedKilometres != useKilometres) {
            renderStatistics(statisticsContent, loadedStats); restoreProgressScroll();
        }
        if (cached != null && cachedRevision == revision) {
            loadedRevision = revision; refreshStatus.setVisibility(View.GONE);
            enrichLocalRoadTowns(cached, generation); loadLocalTownInventoriesAsync(cached, generation);
            return;
        }
        refreshStatus.setText(loadedStats == null || loadedStats.pending ? "Preparing your progress…" : "Updating progress · previous figures remain available");
        refreshStatus.setVisibility(View.VISIBLE);
        synchronized (STATS_CACHE_LOCK) { if (statsLoadInFlight) return; statsLoadInFlight = true; }
        statisticsExecutor.execute(() -> {
            DistanceStats result = null; Exception failure = null;
            try { result = summarizeSavedJourneys(app, generation); } catch (Exception error) { failure = error; }
            boolean current;
            synchronized (JourneyStore.class) {
                current = failure == null && JourneyStore.dataRevision(app) == revision;
                synchronized (STATS_CACHE_LOCK) {
                    if (current) { processCachedStats = result; processCachedRevision = revision; writeStatisticsSnapshot(app, result, revision); }
                    statsLoadInFlight = false;
                }
            }
            final Exception error = failure;
            final List<ProgressActivity> listeners = new ArrayList<>();
            synchronized (STATS_CACHE_LOCK) { for (java.lang.ref.WeakReference<ProgressActivity> ref : screens) if (ref.get() != null) listeners.add(ref.get()); }
            for (ProgressActivity screen : listeners) screen.mainHandler.post(() -> {
                if (screen.isFinishing() || screen.isDestroyed()) return;
                if (current) { screen.loadStatistics(); }
                else if (error != null) {
                    screen.refreshStatus.setText("Progress update couldn't finish · tap to retry");
                    screen.refreshStatus.setOnClickListener(v -> screen.loadStatistics());
                } else {
                    // Matching changed the archive during this scan. Keep the last known
                    // figures visible and coalesce another scan instead of publishing mixed totals.
                    screen.mainHandler.postDelayed(() -> { if (!screen.isFinishing() && !screen.isDestroyed()) screen.loadStatistics(); }, 1500);
                }
            });
        });
    }

    private static final String[] SNAPSHOT_FIELDS = {"drivingMetres", "footMetres", "trainMetres", "ferryMetres", "flightMetres", "transitMetres", "cyclingMetres", "unknownMetres", "uniqueDrivingMetres", "uniqueFootMetres"};
    private static void writeStatisticsSnapshot(android.content.Context context, DistanceStats stats, long revision) {
        try {
            JSONObject json = new JSONObject().put("version",1).put("revision",revision).put("activities",stats.activities);
            for (String name : SNAPSHOT_FIELDS) { java.lang.reflect.Field field = DistanceStats.class.getDeclaredField(name); field.setAccessible(true); json.put(name,field.getDouble(stats)); }
            context.getSharedPreferences("roadprints_progress_snapshot", MODE_PRIVATE).edit().putString("summary",json.toString()).apply();
        } catch (Exception error) { android.util.Log.w("Roadprints", "Progress snapshot unavailable", error); }
    }
    private DistanceStats readStatisticsSnapshot() {
        try {
            String saved = getSharedPreferences("roadprints_progress_snapshot", MODE_PRIVATE).getString("summary",null);
            if (saved == null) return null;
            JSONObject json = new JSONObject(saved); if (json.optInt("version") != 1) return null;
            DistanceStats stats = new DistanceStats(); stats.summaryOnly = true; stats.activities = json.optInt("activities");
            for (String name : SNAPSHOT_FIELDS) { java.lang.reflect.Field field = DistanceStats.class.getDeclaredField(name); field.setAccessible(true); field.setDouble(stats,json.getDouble(name)); }
            return stats;
        } catch (Exception invalid) { return null; }
    }

    private void restoreProgressScroll() {
        if (statisticsScroll != null) {
            statisticsScroll.post(() -> {
                if (savedScrollY > 0) statisticsScroll.scrollTo(0, savedScrollY);
                updateActiveProgressJump();
            });
        } else {
            updateActiveProgressJump();
        }
    }

    private DistanceStats summarizeSavedJourneys(android.content.Context context, int generation) {
        DistanceStats stats = new DistanceStats();
        Map<String, Double> uniqueRoadEdges = new HashMap<>();
        Map<String, Double> uniqueFootEdges = new HashMap<>();
        MotorwayProgressCalculator motorwayCalculator = new MotorwayProgressCalculator(
                context, null, true, false);
        ARoadProgressCalculator aRoadCalculator = new ARoadProgressCalculator(context, false);
        int[] checkedJourneys = {0};

        JourneyStore.forEach(context, journey -> {
            int checked = ++checkedJourneys[0];
            if (checked % 50 == 0) {
                updateLoadingMessage(generation,
                        "Checking saved journeys… " + String.format(Locale.UK, "%,d", checked) + " checked");
            }
            String mode = journey.optString("mode", "unknown").toLowerCase(Locale.ROOT);
            double metres = Math.max(0, journey.optDouble("distance_meters", 0));
            if (isShownInJourneysList(journey, mode)) stats.activities++;

            if ("driving".equals(mode) || "bus".equals(mode)) {
                stats.drivingMetres += metres;
                if ("complete".equals(journey.optString("processing_status", ""))) {
                    collectUniqueMatchedEdges(journey, uniqueRoadEdges);
                }
            } else if ("walking".equals(mode) || "running".equals(mode)
                    || "pedestrian".equals(mode)) {
                stats.footMetres += metres;
                if ("complete".equals(journey.optString("processing_status", ""))) {
                    collectUniqueMatchedEdges(journey, uniqueFootEdges);
                }
            } else if ("train".equals(mode)) {
                stats.trainMetres += metres;
            } else if ("ferry".equals(mode)) {
                stats.ferryMetres += metres;
            } else if ("flight".equals(mode) || "plane".equals(mode)) {
                stats.flightMetres += metres;
            } else if ("transit".equals(mode)) {
                stats.transitMetres += metres;
            } else if ("cycling".equals(mode) || "bicycle".equals(mode)) {
                stats.cyclingMetres += metres;
            } else {
                stats.unknownMetres += metres;
            }
            if ("complete".equals(journey.optString("processing_status", ""))) {
                collectRoadDiscovery(journey, stats, mode);
                motorwayCalculator.addJourney(journey);
                aRoadCalculator.addJourney(journey);
            }
        });

        stats.uniqueDrivingMetres = sumEdges(uniqueRoadEdges);
        stats.uniqueFootMetres = sumEdges(uniqueFootEdges);
        uniqueRoadEdges.clear();
        uniqueFootEdges.clear();
        updateLoadingMessage(generation, "Calculating motorway coverage…");
        stats.motorwayProgress = motorwayCalculator.finish();
        stats.aRoadProgress = aRoadCalculator.finish();
        return stats;
    }

    private void updateLoadingMessage(int generation, String message) {
        mainHandler.post(() -> {
            if (generation != statsLoadGeneration || loadingDetail == null) return;
            loadingDetail.setText(message);
        });
    }

    private void collectUniqueMatchedEdges(JSONObject journey, Map<String, Double> edges) {
        List<JSONArray> routes = matchedSegments(journey);
        JSONObject corrections = journey.optJSONObject("journey_corrections");
        JSONArray removedValues = corrections == null
                ? null : corrections.optJSONArray("removed_matched_segments");
        Set<Integer> removed = new HashSet<>();
        if (removedValues != null) {
            for (int index = 0; index < removedValues.length(); index++) {
                int edge = removedValues.optInt(index, -1);
                if (edge >= 0) removed.add(edge);
            }
        }

        int edgeIndex = 0;
        for (JSONArray route : routes) {
            for (int pointIndex = 1; pointIndex < route.length(); pointIndex++, edgeIndex++) {
                JSONArray a = route.optJSONArray(pointIndex - 1);
                JSONArray b = route.optJSONArray(pointIndex);
                if (removed.contains(edgeIndex) || a == null || b == null
                        || a.length() < 2 || b.length() < 2) continue;
                double aLng = a.optDouble(0, Double.NaN);
                double aLat = a.optDouble(1, Double.NaN);
                double bLng = b.optDouble(0, Double.NaN);
                double bLat = b.optDouble(1, Double.NaN);
                if (!Double.isFinite(aLng) || !Double.isFinite(aLat)
                        || !Double.isFinite(bLng) || !Double.isFinite(bLat)) continue;
                String first = pointKey(aLng, aLat);
                String second = pointKey(bLng, bLat);
                String key = first.compareTo(second) <= 0
                        ? first + "|" + second : second + "|" + first;
                edges.putIfAbsent(key, haversineMetres(aLng, aLat, bLng, bLat));
            }
        }
    }

    private void collectRoadDiscovery(JSONObject journey, DistanceStats stats, String mode) {
        boolean driven = "driving".equals(mode) || "bus".equals(mode);
        boolean foot = "walking".equals(mode) || "running".equals(mode)
                || "pedestrian".equals(mode);
        if (!driven && !foot) return;
        JSONObject result = journey.optJSONObject("processing_result");
        JSONObject geojson = result == null ? null : result.optJSONObject("road_geojson");
        JSONArray features = geojson == null ? null : geojson.optJSONArray("features");
        if (features == null || features.length() == 0) {
            features = new JSONArray();
            appendFeatures(features, result == null ? null : result.optJSONObject("motorway_geojson"));
            appendFeatures(features, result == null ? null : result.optJSONObject("a_road_geojson"));
        }
        String journeyId = journey.optString("journey_id", "");
        for (int index = 0; index < features.length(); index++) {
            JSONObject feature = features.optJSONObject(index);
            if (JourneyCorrectionUtils.excludesRoadFeature(journey, feature)) continue;
            JSONObject properties = feature == null ? null : feature.optJSONObject("properties");
            if (properties == null) continue;
            String rawRef = properties.optString("road_ref", properties.optString("ref", ""));
            String name = properties.optString("name", properties.optString("road_name", ""));
            String kind = properties.optString("highway", "").toLowerCase(Locale.ROOT);
            if (kind.endsWith("_link") || "service".equals(kind) || "footway".equals(kind)
                    || "path".equals(kind) || "steps".equals(kind) || "cycleway".equals(kind)) continue;
            for (String item : rawRef.split("[;,/]")) {
                String ref = item.trim().toUpperCase(Locale.ROOT);
                String label = roadLabel(ref, name);
                if (label == null) continue;
                String category = roadCategory(label);
                String id = category.equals("Local roads") && !label.matches(".*[0-9].*")
                        ? "name:" + label.toLowerCase(Locale.ROOT) : "ref:" + label;
                RoadDiscoveryItem road = stats.discoveredRoads.computeIfAbsent(id,
                        ignored -> new RoadDiscoveryItem(id, label, category));
                JSONObject geometry = feature.optJSONObject("geometry");
                if (geometry != null && "Local roads".equals(category)) {
                    String geometryKey = geometryEvidenceKey(geometry);
                    if (road.geometryEvidenceKeys.add(geometryKey)) {
                        road.geometryEvidence.add(geometry);
                    }
                }
                if (foot) road.onFoot = true;
                else road.driven = true;
                if (!journeyId.isEmpty()) road.journeyIds.add(journeyId);
            }
        }
    }

    private void appendFeatures(JSONArray target, JSONObject geojson) {
        JSONArray features = geojson == null ? null : geojson.optJSONArray("features");
        if (features == null) return;
        for (int index = 0; index < features.length(); index++) target.put(features.opt(index));
    }

    private String geometryEvidenceKey(JSONObject geometry) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(
                    geometry.toString().getBytes(StandardCharsets.UTF_8));
            char[] hex = "0123456789abcdef".toCharArray();
            char[] key = new char[digest.length * 2];
            for (int index = 0; index < digest.length; index++) {
                int value = digest[index] & 0xff;
                key[index * 2] = hex[value >>> 4];
                key[index * 2 + 1] = hex[value & 0x0f];
            }
            return new String(key);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is not available", impossible);
        }
    }

    private String roadLabel(String ref, String name) {
        String cleaned = ref.replaceAll("\\s+", "");
        if (cleaned.matches("\\d+(?:[.,]\\d+)?")) cleaned = "";
        if (!cleaned.isEmpty()) {
            if (cleaned.matches("M6T|M6TOLL")) return "M6 Toll";
            return cleaned;
        }
        String trimmedName = name == null ? "" : name.trim();
        return trimmedName.isEmpty() || trimmedName.matches("\\d+(?:[.,]\\d+)?")
                ? null : trimmedName;
    }

    private String roadCategory(String label) {
        if (label.matches("M[0-9]+[A-Z]?|M6 Toll|A[0-9]+\\(M\\)")) return "Motorways";
        if (label.matches("A[0-9]+[A-Z]?")) return "A roads";
        if (label.matches("B[0-9]+[A-Z]?")) return "B roads";
        return "Local roads";
    }

    private double sumEdges(Map<String, Double> edges) {
        double total = 0;
        for (double metres : edges.values()) total += metres;
        return total;
    }

    private String pointKey(double longitude, double latitude) {
        return String.format(Locale.US, "%.5f,%.5f", longitude, latitude);
    }

    private double haversineMetres(double longitudeA, double latitudeA,
                                   double longitudeB, double latitudeB) {
        double radius = 6_371_000.0;
        double latA = Math.toRadians(latitudeA);
        double latB = Math.toRadians(latitudeB);
        double deltaLat = latB - latA;
        double deltaLng = Math.toRadians(longitudeB - longitudeA);
        double h = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
                + Math.cos(latA) * Math.cos(latB)
                * Math.sin(deltaLng / 2) * Math.sin(deltaLng / 2);
        return radius * 2 * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h));
    }

    private List<JSONArray> matchedSegments(JSONObject journey) {
        List<JSONArray> routes = new ArrayList<>();
        JSONObject result = journey.optJSONObject("processing_result");
        JSONObject geojson = result == null ? null : result.optJSONObject("geojson");
        JSONArray features = geojson == null ? null : geojson.optJSONArray("features");
        if (features == null) return routes;
        for (int index = 0; index < features.length(); index++) {
            JSONObject feature = features.optJSONObject(index);
            JSONObject geometry = feature == null ? null : feature.optJSONObject("geometry");
            if (geometry == null) continue;
            String type = geometry.optString("type", "");
            JSONArray coordinates = geometry.optJSONArray("coordinates");
            if ("LineString".equals(type) && coordinates != null && coordinates.length() >= 2) {
                routes.add(coordinates);
            } else if ("MultiLineString".equals(type) && coordinates != null) {
                for (int line = 0; line < coordinates.length(); line++) {
                    JSONArray segment = coordinates.optJSONArray(line);
                    if (segment != null && segment.length() >= 2) routes.add(segment);
                }
            }
        }
        return routes;
    }

    private void renderStatistics(LinearLayout parent, DistanceStats stats) {
        parent.removeAllViews();
        renderedKilometres = useKilometres;
        renderingPendingStats = stats.pending;
        addCollectiveStatistics(parent, stats);
        renderingPendingStats = false;
        roadDiscoveryHost = new LinearLayout(this); roadDiscoveryHost.setOrientation(LinearLayout.VERTICAL);
        parent.addView(roadDiscoveryHost);
        addRoadDiscoveryPanel(roadDiscoveryHost, stats);
        if (stats.pending || stats.summaryOnly) {
            for (String label : new String[]{"Road discovery", "Motorways", "A Roads"}) {
                LinearLayout scaffold = statisticsPanel(label);
                TextView state = new TextView(this); state.setText("Preparing saved road coverage…"); state.setTextColor(MUTED); scaffold.addView(state); parent.addView(scaffold);
            }
        }
        addMotorwayAggregatePanel(parent, stats);
        addMotorwayCoveragePanel(parent, stats);
        addARoadAggregatePanel(parent, stats);
        addARoadCoveragePanel(parent, stats);
        if (ServiceStationStore.unlocked(this)) addServiceStationPanel(parent);
        if (statisticsScroll != null) statisticsScroll.post(this::updateActiveProgressJump);
    }

    private HorizontalScrollView buildProgressJumpMenu() {
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(2), 0, dp(4));
        progressJumpChips.clear();
        addProgressJump(row, "Statistics", "statistics");
        addProgressJump(row, "Road discovery", "road_discovery");
        addProgressJump(row, "Motorways", "motorways");
        addProgressJump(row, "A Roads", "a_roads");
        if (ServiceStationStore.unlocked(this)) addProgressJump(row, "Service stations", "services");
        scroll.addView(row);
        setActiveProgressJump("statistics");
        return scroll;
    }

    private void addProgressJump(LinearLayout row, String label, String section) {
        TextView chip = new TextView(this);
        chip.setText(label);
        chip.setTextSize(12);
        chip.setTypeface(null, android.graphics.Typeface.BOLD);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(13), dp(9), dp(13), dp(9));
        progressJumpChips.put(section, chip);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, -2);
        params.rightMargin = dp(7);
        row.addView(chip, params);
        chip.setOnClickListener(v -> {
            View target = findProgressSection(section);
            if (target != null && statisticsScroll != null) {
                statisticsScroll.smoothScrollTo(0, Math.max(0, target.getTop() - dp(8)));
            }
        });
    }

    private View findProgressSection(String section) {
        if (statisticsContent == null) return null;
        String tag = "progress:" + section;
        for (int index = 0; index < statisticsContent.getChildCount(); index++) {
            View child = statisticsContent.getChildAt(index);
            if (tag.equals(child.getTag())) return child;
        }
        return null;
    }

    private void updateActiveProgressJump() {
        String active = "statistics";
        if (statisticsScroll != null && statisticsContent != null) {
            int threshold = statisticsScroll.getScrollY() + dp(28);
            for (String section : new String[] {"statistics", "road_discovery", "motorways", "a_roads", "services"}) {
                View target = findProgressSection(section);
                if (target != null && target.getTop() <= threshold) active = section;
            }
        }
        setActiveProgressJump(active);
    }

    private void setActiveProgressJump(String active) {
        for (Map.Entry<String, TextView> entry : progressJumpChips.entrySet()) {
            boolean selected = entry.getKey().equals(active);
            TextView chip = entry.getValue();
            chip.setTextColor(selected ? NAVY : Color.WHITE);
            chip.setBackground(roundRect(selected ? GOLD : CARD, dp(18)));
            chip.setSelected(selected);
        }
    }

    private void addServiceStationPanel(LinearLayout parent) {
        LinearLayout panel=statisticsPanel("Service stations");
        try {
            JSONArray stations=ServiceStationStore.stations(this);
            Set<String> completed=ServiceStationStore.completed(this);
            Set<String> automatic=ServiceStationStore.automatic(this);
            int visited=0;
            for(int i=0;i<stations.length();i++) {
                JSONObject station=stations.optJSONObject(i);
                if(station!=null&&completed.contains(station.optString("id",""))) visited++;
            }
            TextView count=new TextView(this);
            count.setText(visited+" of "+stations.length()+" service stations collected");
            count.setTextColor(Color.WHITE);count.setTextSize(16);
            count.setTypeface(null,android.graphics.Typeface.BOLD);panel.addView(count);
            TextView note=new TextView(this);
            note.setText("Automatic matches use confirmed Timeline place visits within 350 m. Re-import Timeline to refresh confirmed stops.");
            note.setTextColor(MUTED);note.setTextSize(12);note.setPadding(0,dp(5),0,dp(9));panel.addView(note);
            addServiceStationRegion(panel,stations,"GB",completed,automatic);
            addServiceStationRegion(panel,stations,"NI",completed,automatic);
        } catch(Exception error) {
            TextView message=new TextView(this);message.setText("Service station data could not be loaded.");
            message.setTextColor(MUTED);panel.addView(message);
        }
        parent.addView(panel);
    }

    private void addServiceStationRegion(LinearLayout panel,JSONArray stations,String region,
                                         Set<String> completed,Set<String> automatic) {
        List<JSONObject> values=new ArrayList<>();
        for(int i=0;i<stations.length();i++) {
            JSONObject station=stations.optJSONObject(i);
            if(station!=null&&region.equals(station.optString("region","GB"))) values.add(station);
        }
        values.sort(Comparator.comparing((JSONObject item)->item.optString("road",""),String.CASE_INSENSITIVE_ORDER)
                .thenComparing(item->item.optString("name",""),String.CASE_INSENSITIVE_ORDER));
        TextView regionTitle=new TextView(this);
        regionTitle.setText(region.equals("NI")?"NORTHERN IRELAND":"GREAT BRITAIN");
        regionTitle.setTextColor(GOLD);regionTitle.setTextSize(11);
        regionTitle.setTypeface(null,android.graphics.Typeface.BOLD);regionTitle.setPadding(0,dp(12),0,dp(4));
        panel.addView(regionTitle);
        String previousRoad="";
        for(JSONObject station:values) {
            String road=station.optString("road","Other motorway");
            if(!road.equals(previousRoad)) {
                TextView roadTitle=new TextView(this);roadTitle.setText(road);roadTitle.setTextColor(TEAL);
                roadTitle.setTextSize(14);roadTitle.setTypeface(null,android.graphics.Typeface.BOLD);
                roadTitle.setPadding(dp(4),dp(7),0,dp(2));panel.addView(roadTitle);previousRoad=road;
            }
            String id=station.optString("id","");
            CheckBox item=new CheckBox(this);
            item.setText(station.optString("name","Service area")
                    +(automatic.contains(id)?" · Timeline confirmed":""));
            item.setTextColor(Color.WHITE);item.setTextSize(13);
            item.setButtonTintList(android.content.res.ColorStateList.valueOf(completed.contains(id)?GOLD:MUTED));
            item.setChecked(completed.contains(id));item.setEnabled(!automatic.contains(id));panel.addView(item);
            item.setOnCheckedChangeListener((button,checked)->{
                int y=statisticsScroll==null?0:statisticsScroll.getScrollY();
                ServiceStationStore.setManual(this,id,checked);
                if(loadedStats!=null) renderStatistics(statisticsContent,loadedStats);
                if(statisticsScroll!=null) statisticsScroll.post(()->statisticsScroll.scrollTo(0,y));
            });
        }
    }

    private LinearLayout statisticsPanel(String titleText) {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(18), dp(18), dp(18), dp(16));
        panel.setBackground(roundRect(0xFF182F62, dp(20)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(12);
        panel.setLayoutParams(params);
        if ("Statistics summary".equals(titleText)) panel.setTag("progress:statistics");
        else if ("Road discovery".equals(titleText)) panel.setTag("progress:road_discovery");
        else if ("Motorway aggregate".equals(titleText) || "Motorway coverage".equals(titleText))
            panel.setTag("progress:motorways");
        else if ("A-road aggregate".equals(titleText) || "A-road coverage".equals(titleText))
            panel.setTag("progress:a_roads");
        else if ("Service stations".equals(titleText)) panel.setTag("progress:services");

        TextView heading = new TextView(this);
        heading.setText(titleText);
        heading.setTextSize(22);
        heading.setTypeface(null, android.graphics.Typeface.BOLD);
        heading.setTextColor(Color.WHITE);
        heading.setPadding(0, 0, 0, dp(14));
        panel.addView(heading);
        return panel;
    }

    private void addRoadSummaryRow(LinearLayout parent, String labelText, int value) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView label = new TextView(this);
        label.setText(labelText);
        label.setTextSize(13);
        label.setTypeface(null, android.graphics.Typeface.BOLD);
        label.setTextColor(TEAL);
        row.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
        TextView count = new TextView(this);
        count.setText(formatCount(value));
        count.setTextSize(14);
        count.setTypeface(null, android.graphics.Typeface.BOLD);
        count.setTextColor(Color.WHITE);
        count.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        row.addView(count);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(5);
        parent.addView(row, params);
    }

    private void addRoadDiscoveryPanel(LinearLayout parent, DistanceStats stats) {
        if (stats.discoveredRoads.isEmpty()) return;
        LinearLayout panel = statisticsPanel("Road discovery");
        int driven = 0, foot = 0, both = 0;
        for (RoadDiscoveryItem road : stats.discoveredRoads.values()) {
            if (road.driven) driven++;
            if (road.onFoot) foot++;
            if (road.driven && road.onFoot) both++;
        }
        LinearLayout summary = new LinearLayout(this);
        summary.setOrientation(LinearLayout.VERTICAL);
        summary.setPadding(dp(13), dp(11), dp(13), dp(11));
        summary.setBackground(roundRect(0xFF1C356A, dp(12)));
        addRoadSummaryRow(summary, "Roads discovered", stats.discoveredRoads.size());
        addRoadSummaryRow(summary, "Driven", driven);
        addRoadSummaryRow(summary, "On foot", foot);
        if (both > 0) addRoadSummaryRow(summary, "In both modes", both);
        LinearLayout.LayoutParams summaryParams = new LinearLayout.LayoutParams(-1, -2);
        summaryParams.bottomMargin = dp(3);
        panel.addView(summary, summaryParams);

        String[] categories = {"Motorways", "A roads", "B roads", "Local roads"};
        for (String category : categories) {
            List<RoadDiscoveryItem> roads = new ArrayList<>();
            for (RoadDiscoveryItem road : stats.discoveredRoads.values()) {
                if (category.equals(road.category)) roads.add(road);
            }
            if (roads.isEmpty()) continue;
            roads.sort(Comparator.comparing(item -> item.label, String.CASE_INSENSITIVE_ORDER));
            LinearLayout group = new LinearLayout(this);
            group.setOrientation(LinearLayout.VERTICAL);
            group.setBackground(roundRect(0xFF1C356A, dp(12)));
            LinearLayout.LayoutParams groupParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            groupParams.topMargin = dp(7);
            panel.addView(group, groupParams);
            LinearLayout header = new LinearLayout(this);
            header.setGravity(Gravity.CENTER_VERTICAL);
            header.setPadding(dp(13), dp(10), dp(13), dp(10));
            LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(dp(64), -2);
            badgeParams.rightMargin = dp(10);
            header.addView(countBadge(formatCount(roads.size())), badgeParams);
            TextView label = new TextView(this);
            label.setText(category);
            label.setTextSize(15);
            label.setTypeface(null, android.graphics.Typeface.BOLD);
            label.setTextColor(Color.WHITE);
            header.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
            TextView expand = new TextView(this);
            expand.setText("⌄");
            expand.setTextSize(22);
            expand.setTextColor(MUTED);
            expand.setGravity(Gravity.CENTER);
            header.addView(expand, new LinearLayout.LayoutParams(dp(36), dp(36)));
            header.setClickable(true);
            header.setFocusable(true);
            group.addView(header, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
            LinearLayout rows = new LinearLayout(this);
            rows.setOrientation(LinearLayout.VERTICAL);
            rows.setVisibility(View.GONE);
            group.addView(rows);
            boolean expanded = expandedRoadCategories.contains(category);
            if (expanded) {
                addRoadCategoryContents(rows, category, roads, stats);
                rows.setTag(Boolean.TRUE);
                rows.setVisibility(View.VISIBLE);
            }
            header.setOnClickListener(v -> {
                boolean open = rows.getVisibility() != View.VISIBLE;
                if (open && !Boolean.TRUE.equals(rows.getTag())) {
                    addRoadCategoryContents(rows, category, roads, stats);
                    rows.setTag(Boolean.TRUE);
                }
                rows.setVisibility(open ? View.VISIBLE : View.GONE);
                if (open) expandedRoadCategories.add(category);
                else expandedRoadCategories.remove(category);
            });
        }
        parent.addView(panel);
    }

    private void addRoadCategoryContents(LinearLayout parent, String category,
                                         List<RoadDiscoveryItem> roads, DistanceStats stats) {
        if ("Local roads".equals(category)) addLocalTownGroups(parent, roads, stats);
        else addRoadDiscoveryRows(parent, roads);
    }

    private void addLocalTownGroups(LinearLayout parent, List<RoadDiscoveryItem> roads,
                                    DistanceStats stats) {
        Map<String, LocalTownProgress> towns = localTownProgress(stats);
        if (towns.isEmpty()) {
            addLocalRoadProgressPanel(parent, stats);
            TextView state = new TextView(this);
            state.setText(stats.localRoadEnrichmentComplete
                    ? "No local roads were assigned to an official settlement."
                    : stats.settlementLookupFailures > 0
                        ? "Some town lookups failed. Reopen Progress to retry."
                        : "Finding towns for your local roads…");
            state.setTextSize(13);
            state.setTextColor(MUTED);
            state.setPadding(dp(13), dp(8), dp(13), dp(12));
            parent.addView(state);
            return;
        }

        Map<String, List<LocalTownProgress>> townsByCounty = new HashMap<>();
        for (LocalTownProgress town : towns.values()) {
            String county = localCountyName(town.settlement);
            townsByCounty.computeIfAbsent(county, ignored -> new ArrayList<>()).add(town);
        }
        List<String> counties = new ArrayList<>(townsByCounty.keySet());
        counties.sort(String.CASE_INSENSITIVE_ORDER);
        List<LocalTownProgress> ordered = new ArrayList<>(towns.values());
        ordered.sort(Comparator.comparing(item -> item.settlement.name,
                String.CASE_INSENSITIVE_ORDER));

        for (String county : counties) {
            List<LocalTownProgress> countyTowns = townsByCounty.get(county);
            countyTowns.sort(Comparator.comparing(item -> item.settlement.name,
                    String.CASE_INSENSITIVE_ORDER));

            LinearLayout countyCard = new LinearLayout(this);
            countyCard.setOrientation(LinearLayout.VERTICAL);
            countyCard.setBackground(roundRect(0xFF203A73, dp(12)));
            LinearLayout.LayoutParams countyParams = new LinearLayout.LayoutParams(-1, -2);
            countyParams.setMargins(dp(8), dp(5), dp(8), dp(5));
            parent.addView(countyCard, countyParams);

            LinearLayout countyHeader = new LinearLayout(this);
            countyHeader.setGravity(Gravity.CENTER_VERTICAL);
            countyHeader.setPadding(dp(13), dp(10), dp(10), dp(10));
            TextView countyName = new TextView(this);
            countyName.setText(county);
            countyName.setTextSize(15);
            countyName.setTypeface(null, android.graphics.Typeface.BOLD);
            countyName.setTextColor(Color.WHITE);
            countyHeader.addView(countyName, new LinearLayout.LayoutParams(0, -2, 1));
            TextView countyExpand = new TextView(this);
            countyExpand.setText("⌄");
            countyExpand.setTextSize(22);
            countyExpand.setTextColor(MUTED);
            countyExpand.setGravity(Gravity.CENTER);
            countyHeader.addView(countyExpand, new LinearLayout.LayoutParams(dp(36), dp(36)));
            countyCard.addView(countyHeader);
            LinearLayout countyContent = new LinearLayout(this);
            countyContent.setOrientation(LinearLayout.VERTICAL);
            countyContent.setVisibility(View.GONE);
            countyCard.addView(countyContent);

            for (LocalTownProgress town : countyTowns) {
                addLocalTownCard(countyContent, town);
            }
            String countyKey = "county:" + county;
            boolean expanded = expandedRoadCategories.contains(countyKey);
            if (expanded) countyContent.setVisibility(View.VISIBLE);
            countyHeader.setClickable(true);
            countyHeader.setFocusable(true);
            countyHeader.setOnClickListener(v -> {
                boolean open = countyContent.getVisibility() != View.VISIBLE;
                countyContent.setVisibility(open ? View.VISIBLE : View.GONE);
                if (open) expandedRoadCategories.add(countyKey);
                else expandedRoadCategories.remove(countyKey);
            });
        }

        Set<String> assigned = new HashSet<>();
        for (LocalTownProgress town : ordered) assigned.addAll(town.roads.keySet());
        int unresolved = roads.size() - assigned.size();
        if (unresolved > 0) {
            TextView pending = new TextView(this);
            String pendingMessage = stats.settlementLookupFailures > 0
                    ? " still need a town lookup · reopen Progress to retry."
                    : stats.localRoadEnrichmentComplete
                        ? " could not be assigned to a named settlement."
                        : " still being matched to a town.";
            pending.setText(formatCount(unresolved) + " local road"
                    + (unresolved == 1 ? "" : "s") + pendingMessage);
            pending.setTextSize(12);
            pending.setTextColor(MUTED);
            pending.setPadding(dp(13), dp(6), dp(13), dp(10));
            parent.addView(pending);
        }
        addLocalRoadProgressPanel(parent, stats);
    }

    /**
     * Converts source county/unitary-area labels into stable display groups.
     * This is keyed by the geographic area, not by individual settlements, so
     * newly added settlements inherit the same grouping automatically.
     */
    private String localCountyName(LocalRoadSettlementMatcher.Settlement settlement) {
        String county = settlement.county == null ? "" : settlement.county.trim();
        String region = settlement.region == null ? "" : settlement.region.trim();
        String name = settlement.name == null ? "" : settlement.name.trim();
        String countyKey = county.toLowerCase(Locale.ROOT);
        String nameKey = name.toLowerCase(Locale.ROOT);

        if (isLondonBorough(county) || isLondonBorough(name)
                || "london".equals(countyKey)
                || "greater london".equals(region.toLowerCase(Locale.ROOT))) return "London";

        String group = countyGroupForSourceArea(countyKey);
        if (group != null) return group;
        // Some older settlement records have no area metadata. Keep the
        // explicit settlement aliases as a compatibility fallback only.
        if ("blackpool".equals(nameKey)) return "Lancashire";
        if ("bracknell forest".equals(nameKey)) return "Berkshire";
        if ("brighton".equals(nameKey) || "brighton and hove".equals(nameKey))
            return "West Sussex";
        if (!county.isEmpty()) return county;
        if (!region.isEmpty()) return region;
        if (settlement.nation != null && !settlement.nation.trim().isEmpty())
            return settlement.nation.trim();
        return "Other UK areas";
    }

    private String countyGroupForSourceArea(String areaKey) {
        // Display groups for source unitary authorities that sit within a
        // broader county identity. Unlisted county labels pass through intact.
        switch (areaKey) {
            case "blackpool":
            case "blackburn with darwen":
                return "Lancashire";
            case "derby":
            case "city of derby":
                return "Derbyshire";
            case "medway":
                return "Kent";
            case "rochdale":
            case "salford":
            case "trafford":
                return "Greater Manchester";
            case "sandwell":
            case "walsall":
                return "West Midlands";
            case "sefton":
                return "Merseyside";
            case "southend-on-sea":
            case "thurrock":
                return "Essex";
            case "swindon":
                return "Wiltshire";
            case "bracknell forest":
            case "reading":
            case "slough":
            case "west berkshire":
            case "windsor and maidenhead":
            case "wokingham":
                return "Berkshire";
            case "brighton":
            case "brighton and hove":
                return "West Sussex";
            case "halton":
            case "warrington":
            case "cheshire east":
            case "cheshire west and chester":
                return "Cheshire";
            case "stoke-on-trent":
            case "staffordshire moorlands":
                return "Staffordshire";
            case "bath and north east somerset":
            case "north somerset":
                return "Somerset";
            case "south gloucestershire":
                return "Gloucestershire";
            case "bournemouth, christchurch and poole":
            case "dorset":
                return "Dorset";
            case "east riding of yorkshire":
            case "kingston upon hull":
            case "north east lincolnshire":
            case "north lincolnshire":
                return "Yorkshire";
            case "herefordshire":
                return "Herefordshire";
            case "telford and wrekin":
            case "shropshire":
                return "Shropshire";
            case "milton keynes":
            case "buckinghamshire":
                return "Buckinghamshire";
            case "luton":
            case "bedford":
            case "central bedfordshire":
                return "Bedfordshire";
            default:
                return null;
        }
    }

    private boolean isLondonBorough(String value) {
        if (value == null) return false;
        String borough = value.trim().toLowerCase(Locale.ROOT);
        borough = borough.replaceFirst("^london borough of\\s+", "");
        switch (borough) {
            case "barking and dagenham": case "barnet": case "bexley": case "brent":
            case "bromley": case "camden": case "city of london": case "croydon":
            case "ealing": case "enfield": case "greenwich": case "hackney":
            case "hammersmith and fulham": case "haringey": case "harrow":
            case "havering": case "hillingdon": case "hounslow": case "islington":
            case "kensington and chelsea": case "kingston upon thames": case "lambeth":
            case "lewisham": case "merton": case "newham": case "redbridge":
            case "richmond upon thames": case "southwark": case "sutton":
            case "tower hamlets": case "waltham forest": case "wandsworth":
            case "westminster": return true;
            default: return false;
        }
    }

    private void addLocalTownCard(LinearLayout parent, LocalTownProgress town) {
        LinearLayout townCard = new LinearLayout(this);
        townCard.setOrientation(LinearLayout.VERTICAL);
        townCard.setBackground(roundRect(0xFF263F75, dp(12)));
        LinearLayout.LayoutParams townParams = new LinearLayout.LayoutParams(-1, -2);
        townParams.setMargins(dp(8), dp(4), dp(8), dp(4));
        parent.addView(townCard, townParams);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(12), dp(10), dp(10), dp(10));
        LinearLayout heading = new LinearLayout(this);
        heading.setOrientation(LinearLayout.VERTICAL);
        TextView townName = new TextView(this);
        townName.setText(town.settlement.name);
        townName.setTextSize(15);
        townName.setTypeface(null, android.graphics.Typeface.BOLD);
        townName.setTextColor(Color.WHITE);
        heading.addView(townName);
        TextView townCoverage = new TextView(this);
        if (town.inventoryCount >= 0) {
            int percentage = town.inventoryCount == 0 ? 0
                    : Math.min(100, Math.round(100f * town.roads.size() / town.inventoryCount));
            townCoverage.setText(formatCount(town.roads.size()) + " of "
                    + formatCount(town.inventoryCount) + " roads · " + percentage + "%");
        } else {
            townCoverage.setText(formatCount(town.roads.size())
                    + " roads discovered · Loading total and percentage…");
        }
        townCoverage.setTextSize(11);
        townCoverage.setTextColor(MUTED);
        townCoverage.setPadding(0, dp(2), 0, 0);
        heading.addView(townCoverage);
        header.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));
        Button viewMap = new Button(this);
        viewMap.setText("View on map");
        viewMap.setAllCaps(false);
        viewMap.setTextSize(11);
        viewMap.setTextColor(TEAL);
        viewMap.setMinHeight(dp(38));
        viewMap.setPadding(dp(8), 0, dp(8), 0);
        viewMap.setBackground(roundRect(0xFF1A315F, dp(8)));
        viewMap.setOnClickListener(v -> openSettlementMap(town));
        header.addView(viewMap);
        townCard.addView(header);

        LinearLayout roadRows = new LinearLayout(this);
        roadRows.setOrientation(LinearLayout.VERTICAL);
        roadRows.setVisibility(View.GONE);
        townCard.addView(roadRows);
        List<RoadDiscoveryItem> townRoads = new ArrayList<>(town.roads.values());
        townRoads.sort(Comparator.comparing(item -> item.label,
                String.CASE_INSENSITIVE_ORDER));
        String townKey = "town:" + town.settlement.code;
        if (expandedRoadCategories.contains(townKey)) {
            addRoadDiscoveryRows(roadRows, townRoads);
            roadRows.setVisibility(View.VISIBLE);
        }
        header.setClickable(true);
        header.setOnClickListener(v -> {
            if (v == viewMap) return;
            boolean open = roadRows.getVisibility() != View.VISIBLE;
            if (open && roadRows.getChildCount() == 0) addRoadDiscoveryRows(roadRows, townRoads);
            roadRows.setVisibility(open ? View.VISIBLE : View.GONE);
            if (open) expandedRoadCategories.add(townKey);
            else expandedRoadCategories.remove(townKey);
        });
        viewMap.bringToFront();
    }

    private void addLocalRoadProgressPanel(LinearLayout parent, DistanceStats stats) {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(13), dp(11), dp(13), dp(12));
        panel.setBackground(roundRect(0xFF1A315F, dp(10)));

        localRoadProgressMessage = new TextView(this);
        localRoadProgressMessage.setTextSize(13);
        localRoadProgressMessage.setTypeface(null, android.graphics.Typeface.BOLD);
        localRoadProgressMessage.setTextColor(Color.WHITE);
        panel.addView(localRoadProgressMessage);

        localRoadProgressBar = new ProgressBar(this);
        localRoadProgressBar.setIndeterminate(true);
        localRoadProgressBar.setIndeterminateTintList(
                android.content.res.ColorStateList.valueOf(TEAL));
        LinearLayout.LayoutParams throbberParams = new LinearLayout.LayoutParams(dp(32), dp(32));
        throbberParams.topMargin = dp(8);
        panel.addView(localRoadProgressBar, throbberParams);

        localRoadProgressDetail = new TextView(this);
        localRoadProgressDetail.setTextSize(11);
        localRoadProgressDetail.setTextColor(MUTED);
        localRoadProgressDetail.setPadding(0, dp(6), 0, 0);
        panel.addView(localRoadProgressDetail);
        LinearLayout.LayoutParams panelParams = new LinearLayout.LayoutParams(-1, -2);
        panelParams.setMargins(dp(8), dp(7), dp(8), dp(4));
        parent.addView(panel, panelParams);
        refreshLocalRoadProgress(stats);
        startLocalRoadProgressTicker(stats);
    }

    private void refreshLocalRoadProgress(DistanceStats stats) {
        if (localRoadProgressMessage == null || localRoadProgressDetail == null) return;
        int total = stats.localRoadLookupTotal;
        int done = stats.localRoadLookupDone;
        int initiallyDone = stats.localRoadLookupInitialDone;
        int percent = total <= 0 ? 0 : Math.min(100, Math.round(100f * done / total));
        if (localRoadProgressBar != null)
            localRoadProgressBar.setVisibility(stats.localRoadEnrichmentRunning ? View.VISIBLE : View.GONE);
        if (stats.localRoadEnrichmentComplete) {
            View progressPanel = (View) localRoadProgressMessage.getParent();
            if (progressPanel.getParent() instanceof android.view.ViewGroup) {
                ((android.view.ViewGroup) progressPanel.getParent()).removeView(progressPanel);
            }
            localRoadProgressMessage = null;
            localRoadProgressDetail = null;
            localRoadProgressBar = null;
            return;
        }
        if (stats.localRoadEnrichmentRunning) {
            if ("cache".equals(stats.localRoadLookupStage)) {
                localRoadProgressMessage.setText("Checking saved town matches · "
                        + stats.localRoadCacheScanned + " of " + total + " roads");
            } else if ("metadata".equals(stats.localRoadLookupStage)) {
                localRoadProgressMessage.setText("Loading county information…");
            } else {
                localRoadProgressMessage.setText("Finding towns · " + done + " of " + total
                        + " local roads (" + percent + "%)");
            }
            long elapsedSeconds = Math.max(0L,
                    (System.currentTimeMillis() - stats.localRoadLookupStartedAt) / 1000L);
            String elapsed = "Elapsed " + formatDuration(elapsedSeconds);
            String estimate = "Estimating time left…";
            if ("cache".equals(stats.localRoadLookupStage)) {
                estimate = "Reading saved road matches";
            } else if ("metadata".equals(stats.localRoadLookupStage)) {
                estimate = "Applying county details to saved matches";
            }
            int freshlyChecked = done - initiallyDone;
            int roadsToCheckAtStart = total - initiallyDone;
            int sampleThreshold = Math.min(10, Math.max(3, roadsToCheckAtStart));
            if (freshlyChecked >= sampleThreshold && total > done
                    && stats.localRoadLookupStartedAt > 0
                    && stats.localRoadLookupLastCompletedAt > stats.localRoadLookupStartedAt) {
                // Measure only completed lookups. Including the currently-running road made
                // the estimate grow every second while the completion count stayed unchanged.
                long elapsedMillis = stats.localRoadLookupLastCompletedAt
                        - stats.localRoadLookupStartedAt;
                long remainingMillis = (elapsedMillis / freshlyChecked) * (total - done);
                estimate = "About " + formatDuration((remainingMillis + 999L) / 1000L)
                        + " left · rough estimate";
            } else if (freshlyChecked > 0 && total > done) {
                estimate = "Building estimate · " + freshlyChecked + " roads checked";
            } else if (freshlyChecked == 0 && total > done) {
                estimate = "Checking the first roads before estimating time";
            } else if (total > 0 && done >= total) {
                estimate = "Roads checked; loading town road totals…";
            }
            String current = stats.localRoadCurrentLabel == null
                    || stats.localRoadCurrentLabel.isEmpty() ? "" : " · Checking "
                    + stats.localRoadCurrentLabel;
            localRoadProgressDetail.setText(elapsed + " · " + estimate + current);
        } else if (stats.settlementLookupFailures > 0) {
            localRoadProgressMessage.setText("Town matching finished with lookup errors");
            localRoadProgressDetail.setText(formatCount(stats.settlementLookupFailures)
                    + " road lookups need retry. Reopen Progress to try again.");
        } else {
            localRoadProgressMessage.setText("Town matching is ready to start");
            localRoadProgressDetail.setText(formatCount(total) + " local roads with matched geometry.");
        }
    }

    private String formatDuration(long seconds) {
        if (seconds < 60) return seconds + " sec";
        long minutes = (seconds + 59) / 60;
        if (minutes < 60) return minutes + " min";
        long hours = minutes / 60;
        long remainingMinutes = minutes % 60;
        return remainingMinutes == 0 ? hours + " hr" : hours + " hr " + remainingMinutes + " min";
    }

    private void startLocalRoadProgressTicker(DistanceStats stats) {
        stopLocalRoadProgressTicker();
        localRoadProgressTicker = new Runnable() {
            @Override public void run() {
                if (loadedStats != stats || localRoadProgressMessage == null || isFinishing()) return;
                refreshLocalRoadProgress(stats);
                if (stats.localRoadEnrichmentRunning) {
                    mainHandler.postDelayed(this, 1000L);
                }
            }
        };
        mainHandler.post(localRoadProgressTicker);
    }

    private void stopLocalRoadProgressTicker() {
        if (localRoadProgressTicker != null) {
            mainHandler.removeCallbacks(localRoadProgressTicker);
            localRoadProgressTicker = null;
        }
        localRoadProgressMessage = null;
        localRoadProgressDetail = null;
        localRoadProgressBar = null;
    }

    private void openSettlementMap(LocalTownProgress town) {
        // Pass archive IDs only. The map screen streams matched geometry and
        // clips it to this settlement, keeping both Binder payload and heap use bounded.
        Set<String> contributingJourneyIds = new HashSet<>();
        for (RoadDiscoveryItem road : town.roads.values())
            contributingJourneyIds.addAll(road.journeyIds);
        List<String> orderedJourneyIds = new ArrayList<>(contributingJourneyIds);
        orderedJourneyIds.sort(String::compareTo);
        JSONArray journeyIds = new JSONArray();
        for (String journeyId : orderedJourneyIds) journeyIds.put(journeyId);
        Intent intent = new Intent(this, MapActivity.class);
        intent.putExtra("settlement_code", town.settlement.code);
        intent.putExtra("settlement_name", town.settlement.name);
        intent.putExtra("settlement_journey_ids", journeyIds.toString());
        startActivity(intent);
    }

    private void enrichLocalRoadTowns(DistanceStats stats, int generation) {
        List<RoadDiscoveryItem> localRoads = new ArrayList<>();
        synchronized (stats) {
            if (stats.localRoadEnrichmentRunning || stats.localRoadEnrichmentComplete) return;
            stats.localRoadEnrichmentRunning = true;
            stats.localRoadLookupStartedAt = System.currentTimeMillis();
            stats.localRoadLookupLastCompletedAt = stats.localRoadLookupStartedAt;
            stats.localRoadLookupStage = "cache";
            stats.localRoadLookupDone = 0;
            stats.localRoadLookupInitialDone = 0;
            stats.localRoadCacheScanned = 0;
            stats.localRoadLookupTotal = 0;
            stats.localRoadCurrentLabel = "";
            for (RoadDiscoveryItem road : stats.discoveredRoads.values()) {
                if (!"Local roads".equals(road.category) || road.geometryEvidence.isEmpty()) continue;
                localRoads.add(road);
                stats.localRoadLookupTotal++;
            }
        }
        postSettlementProgress(stats, generation);
        SETTLEMENT_WORKER.execute(() -> {
            Map<String, List<LocalRoadSettlementMatcher.Settlement>> cachedByRoad =
                    new LinkedHashMap<>();
            Map<String, LocalRoadSettlementMatcher.Settlement> metadataByName =
                    new LinkedHashMap<>();
            long lastUiUpdate = 0;
            for (RoadDiscoveryItem road : localRoads) {
                synchronized (stats) {
                    stats.localRoadCurrentLabel = road.label;
                    stats.localRoadCacheScanned++;
                }
                try {
                    List<LocalRoadSettlementMatcher.Settlement> cached =
                            LocalRoadSettlementMatcher.cached(getApplicationContext(),
                                    road.id, road.geometryEvidence);
                    if (cached != null) {
                        cachedByRoad.put(road.id, cached);
                        for (LocalRoadSettlementMatcher.Settlement settlement : cached) {
                            if (settlement.county.isEmpty() && settlement.region.isEmpty()
                                    && settlement.nation.isEmpty()) {
                                metadataByName.putIfAbsent(
                                        settlement.name.toLowerCase(Locale.ROOT), settlement);
                            }
                        }
                    }
                } catch (Exception error) {
                    android.util.Log.w("Roadprints", "Cached local settlement lookup could not be read", error);
                }
                long now = System.currentTimeMillis();
                if (now - lastUiUpdate >= 10000L) {
                    lastUiUpdate = now;
                    postSettlementProgress(stats, generation);
                }
            }

            if (!metadataByName.isEmpty()) {
                synchronized (stats) {
                    stats.localRoadLookupStage = "metadata";
                    stats.localRoadCurrentLabel = "";
                }
                postSettlementProgress(stats, generation);
                LocalRoadSettlementMatcher.hydrateMetadata(
                        new ArrayList<>(metadataByName.values()));
                for (RoadDiscoveryItem road : localRoads) {
                    List<LocalRoadSettlementMatcher.Settlement> cached = cachedByRoad.get(road.id);
                    if (cached == null) continue;
                    for (LocalRoadSettlementMatcher.Settlement settlement : cached) {
                        LocalRoadSettlementMatcher.Settlement metadata = metadataByName.get(
                                settlement.name.toLowerCase(Locale.ROOT));
                        if (metadata == null) continue;
                        settlement.county = metadata.county;
                        settlement.region = metadata.region;
                        settlement.nation = metadata.nation;
                    }
                    try {
                        LocalRoadSettlementMatcher.saveCached(getApplicationContext(), road.id,
                                road.geometryEvidence, cached);
                    } catch (Exception error) {
                        android.util.Log.w("Roadprints", "Hydrated settlement cache could not be saved", error);
                    }
                }
            }

            List<RoadDiscoveryItem> roadsToResolve = new ArrayList<>();
            for (RoadDiscoveryItem road : localRoads) {
                List<LocalRoadSettlementMatcher.Settlement> cached = cachedByRoad.get(road.id);
                if (cached == null) {
                    roadsToResolve.add(road);
                    continue;
                }
                synchronized (stats) {
                    stats.settlementMatches.put(road.id, cached);
                    stats.localRoadLookupDone++;
                    stats.localRoadLookupLastCompletedAt = System.currentTimeMillis();
                }
            }
            synchronized (stats) {
                stats.localRoadLookupInitialDone = stats.localRoadLookupDone;
                stats.localRoadLookupStage = "matching";
                stats.localRoadLookupStartedAt = System.currentTimeMillis();
                stats.localRoadLookupLastCompletedAt = stats.localRoadLookupStartedAt;
                stats.localRoadCurrentLabel = "";
            }
            postSettlementProgress(stats, generation);

            int changed = 0;
            int failures = 0;
            lastUiUpdate = 0;
            for (RoadDiscoveryItem road : roadsToResolve) {
                synchronized (stats) { stats.localRoadCurrentLabel = road.label; }
                try {
                    List<JSONObject> evidence = new ArrayList<>(road.geometryEvidence);
                    List<LocalRoadSettlementMatcher.Settlement> matches =
                            LocalRoadSettlementMatcher.resolve(getApplicationContext(), road.id, evidence);
                    AchievementStore.recordRoadSettlements(
                            getApplicationContext(), road.id, evidence, matches);
                    synchronized (stats) {
                        stats.settlementMatches.put(road.id, matches);
                        stats.localRoadLookupDone++;
                        stats.localRoadLookupLastCompletedAt = System.currentTimeMillis();
                    }
                    changed++;
                } catch (Exception error) {
                    failures++;
                    synchronized (stats) {
                        stats.localRoadLookupDone++;
                        stats.localRoadLookupLastCompletedAt = System.currentTimeMillis();
                    }
                    android.util.Log.w("Roadprints", "Local road town lookup failed", error);
                    try { Thread.sleep(1050L); }
                    catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
                long now = System.currentTimeMillis();
                if (now - lastUiUpdate >= 10000L) {
                    lastUiUpdate = now;
                    postSettlementProgress(stats, generation);
                }
            }

            synchronized (stats) {
                stats.localRoadEnrichmentRunning = false;
                stats.settlementLookupFailures = failures;
                stats.localRoadLookupStage = "complete";
                stats.localRoadCurrentLabel = "";
                stats.localRoadEnrichmentComplete = failures == 0;
            }
            postSettlementProgress(stats, generation);
        });
    }

    private Map<String, LocalTownProgress> localTownProgress(DistanceStats stats) {
        Map<String, LocalTownProgress> towns = new LinkedHashMap<>();
        synchronized (stats) {
            for (RoadDiscoveryItem road : stats.discoveredRoads.values()) {
                if (!"Local roads".equals(road.category)) continue;
                for (LocalRoadSettlementMatcher.Settlement settlement :
                        stats.settlementMatches.getOrDefault(road.id, java.util.Collections.emptyList())) {
                    if (settlement.code.isEmpty()) continue;
                    LocalTownProgress town = towns.computeIfAbsent(settlement.code,
                            ignored -> new LocalTownProgress(settlement));
                    town.inventoryCount = stats.settlementInventoryCounts.getOrDefault(
                            settlement.code, -1);
                    town.roads.put(road.id, road);
                }
            }
        }
        return towns;
    }

    private void loadLocalTownInventoriesAsync(DistanceStats stats, int generation) {
        boolean pending = false;
        synchronized (stats) {
            for (LocalTownProgress town : localTownProgress(stats).values()) if (!stats.settlementInventoryCounts.containsKey(town.settlement.code) && !stats.settlementInventoryPendingCodes.contains(town.settlement.code)) { pending = true; break; }
            if (!pending) return;
            if (stats.localTownInventoryLoadRunning) return;
            stats.localTownInventoryLoadRunning = true;
        }
        SETTLEMENT_WORKER.execute(() -> {
            resolveLocalTownInventories(stats);
            synchronized (stats) { stats.localTownInventoryLoadRunning = false; }
            postSettlementProgress(stats, generation);
        });
    }

    private void resolveLocalTownInventories(DistanceStats stats) {
        for (LocalTownProgress town : localTownProgress(stats).values()) {
            synchronized (stats) {
                if (stats.settlementInventoryCounts.containsKey(town.settlement.code)
                        || !stats.settlementInventoryLoadingCodes.add(town.settlement.code)) continue;
                stats.settlementInventoryPendingCodes.add(town.settlement.code);
            }
            try {
                int inventory = LocalRoadSettlementMatcher.inventoryCount(
                        getApplicationContext(), town.settlement.code);
                synchronized (stats) {
                    stats.settlementInventoryLoadingCodes.remove(town.settlement.code);
                    if (inventory >= 0) {
                        stats.settlementInventoryCounts.put(town.settlement.code, inventory);
                        stats.settlementInventoryPendingCodes.remove(town.settlement.code);
                    } else {
                        stats.settlementInventoryPendingCodes.add(town.settlement.code);
                    }
                }
            } catch (Exception error) {
                synchronized (stats) {
                    stats.settlementInventoryLoadingCodes.remove(town.settlement.code);
                    stats.settlementInventoryPendingCodes.add(town.settlement.code);
                }
                android.util.Log.w("Roadprints", "Town inventory unavailable", error);
            }
        }
    }

    private void postSettlementProgress(DistanceStats stats, int generation) {
        synchronized (STATS_CACHE_LOCK) {
            for (java.lang.ref.WeakReference<ProgressActivity> ref : screens) {
                ProgressActivity screen = ref.get();
                if (screen == null) continue;
                screen.mainHandler.post(() -> {
                    if (screen.loadedStats != stats || screen.isFinishing() || screen.isDestroyed() || screen.roadDiscoveryHost == null) return;
                    int y = screen.statisticsScroll == null ? 0 : screen.statisticsScroll.getScrollY();
                    screen.stopLocalRoadProgressTicker(); screen.roadDiscoveryHost.removeAllViews();
                    screen.addRoadDiscoveryPanel(screen.roadDiscoveryHost, stats); screen.refreshLocalRoadProgress(stats);
                    if (screen.statisticsScroll != null) screen.statisticsScroll.post(() -> screen.statisticsScroll.scrollTo(0, y));
                });
            }
        }
    }

    private void addRoadDiscoveryRows(LinearLayout rows, List<RoadDiscoveryItem> roads) {
        int rowIndex = 0;
        for (RoadDiscoveryItem road : roads) {
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(12), dp(8), dp(12), dp(8));
                row.setBackgroundColor(rowIndex++ % 2 == 0
                        ? 0xFF263F75 : 0xFF1B3267);
                TextView roadName = new TextView(this);
                roadName.setText(road.label);
                roadName.setTextSize(14);
                roadName.setTypeface(null, android.graphics.Typeface.BOLD);
                roadName.setTextColor(Color.WHITE);
                row.addView(roadName, new LinearLayout.LayoutParams(0,
                        LinearLayout.LayoutParams.WRAP_CONTENT, 1));
                TextView status = new TextView(this);
                status.setText(road.driven && road.onFoot ? "Driven + on foot"
                        : road.driven ? "Driven" : "On foot");
                status.setTextSize(12);
                status.setTextColor(MUTED);
                status.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
                row.addView(status);
                rows.addView(row);
                View divider = new View(this);
                divider.setBackgroundColor(0x334C6798);
                rows.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));
        }
    }

    private void addMotorwayAggregatePanel(LinearLayout parent, DistanceStats stats) {
        MotorwayProgressCalculator.Summary summary = stats.motorwayProgress;
        if (summary == null || summary.roads.isEmpty()) return;
        LinearLayout panel = statisticsPanel("Motorway aggregate");
        double totalMetres = 0;
        for (MotorwayProgressCalculator.Road road : summary.roads) {
            totalMetres += road.matchedMetres;
        }
        LinearLayout summaryRow = new LinearLayout(this);
        summaryRow.setOrientation(LinearLayout.HORIZONTAL);
        summaryRow.addView(statCard(new StatItem("Matched motorway distance",
                totalMetres, true)), weightedCardParams(true));
        summaryRow.addView(statCard(new StatItem("Motorways discovered",
                summary.roads.size(), false, false, true)), weightedCardParams(false));
        panel.addView(summaryRow);
        TextView note = new TextView(this);
        note.setText("Matched mileage adds each recorded journey. Repeated trips are included.");
        note.setTextSize(12);
        note.setTextColor(MUTED);
        note.setPadding(0, dp(10), 0, dp(8));
        panel.addView(note);

        List<MotorwayProgressCalculator.Road> roads = new ArrayList<>(summary.roads);
        roads.sort((left, right) -> Double.compare(right.matchedMetres, left.matchedMetres));
        double maximum = 1;
        for (MotorwayProgressCalculator.Road road : roads) maximum = Math.max(maximum, road.matchedMetres);
        for (MotorwayProgressCalculator.Road road : roads) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(6), 0, dp(2));
            TextView ref = motorwayBadge(road);
            row.addView(ref, new LinearLayout.LayoutParams(dp(78), dp(32)));
            View bar = motorwayBar(maximum > 0 ? road.matchedMetres / maximum : 0, 0xFF005EB8);
            LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(0, dp(12), 1);
            barParams.setMargins(dp(9), 0, dp(9), 0);
            row.addView(bar, barParams);
            TextView value = new TextView(this);
            value.setText(formatMiles(road.matchedMetres));
            value.setTextColor(Color.WHITE);
            value.setTextSize(14);
            value.setTypeface(null, android.graphics.Typeface.BOLD);
            value.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
            row.addView(value, new LinearLayout.LayoutParams(dp(84), -2));
            panel.addView(row);
            TextView meta = new TextView(this);
            meta.setText(road.journeyIds.size() + " matched journey"
                    + (road.journeyIds.size() == 1 ? "" : "s") + " contributed");
            meta.setTextColor(MUTED);
            meta.setTextSize(11);
            meta.setPadding(dp(88), 0, 0, dp(5));
            panel.addView(meta);
        }
        parent.addView(panel);
    }

    private void addMotorwayCoveragePanel(LinearLayout parent, DistanceStats stats) {
        MotorwayProgressCalculator.Summary summary = stats.motorwayProgress;
        if (summary == null || summary.roads.isEmpty()) return;
        LinearLayout panel = statisticsPanel("Motorway coverage");
        addCoverageMetric(panel, "Great Britain", summary.gbPercent(), summary.gbUniqueKm,
                2300.0, false);
        addCoverageMetric(panel, "Northern Ireland", summary.niPercent(), summary.niUniqueKm,
                65.0, summary.missingReferences && summary.roads.stream()
                        .anyMatch(road -> "NI".equals(road.region) && !road.referenceAvailable));
        addCoverageMetric(panel, "UK network", summary.ukPercent(), summary.ukUniqueKm(),
                2365.0, summary.missingReferences);
        TextView note = new TextView(this);
        note.setText("Each canonical motorway section counts once across journeys and carriageways. Individual motorway percentages use the saved reference sections.");
        note.setTextSize(12);
        note.setTextColor(MUTED);
        note.setPadding(0, dp(8), 0, dp(12));
        panel.addView(note);
        if (summary.missingReferences) {
            TextView warning = new TextView(this);
            warning.setText("Reference unavailable for: " + String.join(", ", summary.missingReferenceRoads)
                    + ". Coverage is excluded from the totals.");
            warning.setTextSize(12);
            warning.setTextColor(0xFFFFD166);
            warning.setPadding(0, 0, 0, dp(8));
            panel.addView(warning);
        }
        for (MotorwayProgressCalculator.Road road : summary.roads) {
            double percent = road.percent();
            if (!Double.isFinite(percent) || percent < 1.0) continue;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(8), 0, dp(2));
            row.addView(motorwayBadge(road), new LinearLayout.LayoutParams(dp(78), dp(32)));
            View bar = motorwayBar(Double.isFinite(percent) ? percent / 100.0 : 0, 0xFF005EB8);
            LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(0, dp(12), 1);
            barParams.setMargins(dp(9), 0, dp(9), 0);
            row.addView(bar, barParams);
            TextView value = new TextView(this);
            value.setText(Double.isFinite(percent) ? String.format(Locale.UK, "%.1f%%", percent) : "—");
            value.setTextColor(Color.WHITE);
            value.setTextSize(14);
            value.setTypeface(null, android.graphics.Typeface.BOLD);
            value.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
            row.addView(value, new LinearLayout.LayoutParams(dp(55), -2));
            panel.addView(row);
            TextView meta = new TextView(this);
            String label = Double.isFinite(percent)
                    ? String.format(Locale.UK, "%s estimated unique · %s reference",
                            formatMiles(road.estimatedUniqueKm() * 1000),
                            formatMiles(road.referenceKm * 1000))
                    : "Canonical reference unavailable";
            meta.setText(label);
            meta.setTextColor(MUTED);
            meta.setTextSize(11);
            meta.setPadding(dp(88), 0, 0, dp(4));
            panel.addView(meta);
        }
        parent.addView(panel);
    }

    private TextView aRoadBadge(String ref) {
        TextView badge = new TextView(this);
        badge.setText(ref);
        badge.setTextColor(Color.WHITE);
        badge.setTextSize(14);
        badge.setTypeface(null, android.graphics.Typeface.BOLD);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(roundRect(0xFF25834A, dp(6)));
        return badge;
    }

    private void addARoadAggregatePanel(LinearLayout parent, DistanceStats stats) {
        ARoadProgressCalculator.Summary summary=stats.aRoadProgress;
        if(summary==null||summary.roads.isEmpty())return;
        LinearLayout panel=statisticsPanel("A-road aggregate");
        LinearLayout cards=new LinearLayout(this);
        cards.setOrientation(LinearLayout.HORIZONTAL);
        cards.addView(statCard(new StatItem("Matched A-road distance",summary.matchedMetres,true)),weightedCardParams(true));
        cards.addView(statCard(new StatItem("A roads discovered",summary.roads.size(),false,false,true)),weightedCardParams(false));
        panel.addView(cards);
        List<ARoadProgressCalculator.Road> roads=summary.roads;
        double maximum=1;for(ARoadProgressCalculator.Road road:roads)maximum=Math.max(maximum,road.matchedMetres);
        TextView note=new TextView(this);note.setText("Matched mileage adds each recorded journey. Repeated trips are included.");note.setTextSize(12);note.setTextColor(MUTED);note.setPadding(0,dp(10),0,dp(8));panel.addView(note);
        for(ARoadProgressCalculator.Road road:roads){
            LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(0,dp(6),0,dp(2));
            row.addView(aRoadBadge(road.region.equals("NI")?road.ref+" · NI":road.ref),new LinearLayout.LayoutParams(dp(78),dp(32)));
            View bar=motorwayBar(road.matchedMetres/maximum, 0xFF25834A);LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(0,dp(12),1);bp.setMargins(dp(9),0,dp(9),0);row.addView(bar,bp);
            TextView value=new TextView(this);value.setText(formatMiles(road.matchedMetres));value.setTextColor(Color.WHITE);value.setTextSize(14);value.setTypeface(null,android.graphics.Typeface.BOLD);value.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);row.addView(value,new LinearLayout.LayoutParams(dp(84),-2));panel.addView(row);
            TextView meta=new TextView(this);meta.setText(road.journeyIds.size()+" matched journey"+(road.journeyIds.size()==1?"":"s")+" contributed");meta.setTextColor(MUTED);meta.setTextSize(11);meta.setPadding(dp(88),0,0,dp(5));panel.addView(meta);
        }
        parent.addView(panel);
    }

    private void addARoadCoveragePanel(LinearLayout parent,DistanceStats stats){
        ARoadProgressCalculator.Summary summary=stats.aRoadProgress;if(summary==null||summary.roads.isEmpty())return;
        LinearLayout panel=statisticsPanel("A-road coverage");
        addCoverageMetric(panel,"Discovered A-road references",summary.percent(),summary.totalKm(),summary.referenceKm()*0.6213711922,false);
        TextView note=new TextView(this);note.setText("Canonical A-road sections count once across journeys. This total covers roads with matched journeys; individual percentages use the saved road references.");note.setTextSize(12);note.setTextColor(MUTED);note.setPadding(0,dp(8),0,dp(10));panel.addView(note);
        if(!summary.missing.isEmpty()){TextView warning=new TextView(this);warning.setText("Reference unavailable for: "+String.join(", ",summary.missing)+". Coverage is excluded from the totals.");warning.setTextColor(0xFFFFD166);warning.setTextSize(12);warning.setPadding(0,0,0,dp(8));panel.addView(warning);}
        for(ARoadProgressCalculator.Road road:summary.roads){double pct=road.percent();if(!Double.isFinite(pct)||pct<1)continue;
            LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(0,dp(8),0,dp(2));row.addView(aRoadBadge(road.region.equals("NI")?road.ref+" · NI":road.ref),new LinearLayout.LayoutParams(dp(78),dp(32)));
            View bar=motorwayBar(pct/100, 0xFF25834A);LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(0,dp(12),1);bp.setMargins(dp(9),0,dp(9),0);row.addView(bar,bp);
            TextView value=new TextView(this);value.setText(String.format(Locale.UK,"%.1f%%",pct));value.setTextColor(Color.WHITE);value.setTextSize(14);value.setTypeface(null,android.graphics.Typeface.BOLD);value.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);row.addView(value,new LinearLayout.LayoutParams(dp(55),-2));panel.addView(row);
            TextView meta=new TextView(this);meta.setText(String.format(Locale.UK,"%s estimated unique · %s reference",formatMiles(road.uniqueKm()*1000),formatMiles(road.totalKm*1000)));meta.setTextColor(MUTED);meta.setTextSize(11);meta.setPadding(dp(88),0,0,dp(4));panel.addView(meta);
        }parent.addView(panel);
    }

    private void addCoverageMetric(LinearLayout panel, String title, double percent,
                                   double distanceKm, double totalMiles, boolean unavailable) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(13), dp(12), dp(13), dp(12));
        card.setBackground(roundRect(0xFF233B78, dp(14)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(7);
        TextView heading = new TextView(this);
        heading.setText(title);
        heading.setTextSize(12);
        heading.setTypeface(null, android.graphics.Typeface.BOLD);
        heading.setTextColor(MUTED);
        TextView value = new TextView(this);
        value.setText(unavailable ? "Reference unavailable"
                : String.format(Locale.UK, "%.1f%%", percent));
        value.setTextSize(21);
        value.setTypeface(null, android.graphics.Typeface.BOLD);
        value.setTextColor(Color.WHITE);
        TextView distance = new TextView(this);
        distance.setText(unavailable ? "Coverage total is incomplete"
                : formatMiles(distanceKm * 1000) + " of approximately "
                        + (useKilometres ? String.format(Locale.UK, "%.0f km", totalMiles / 0.6213711922)
                        : String.format(Locale.UK, "%.0f mi", totalMiles)));
        distance.setTextSize(11);
        distance.setTextColor(MUTED);
        card.addView(heading);
        card.addView(value);
        card.addView(distance);
        panel.addView(card, params);
    }

    private TextView motorwayBadge(MotorwayProgressCalculator.Road road) {
        TextView badge = new TextView(this);
        badge.setText("NI".equals(road.region) ? road.ref + " · NI" : road.ref);
        badge.setTextColor(Color.WHITE);
        badge.setTextSize(12);
        badge.setTypeface(null, android.graphics.Typeface.BOLD);
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(dp(5), dp(4), dp(5), dp(4));
        badge.setBackground(signBadge());
        return badge;
    }

    private android.graphics.drawable.GradientDrawable signBadge() {
        android.graphics.drawable.GradientDrawable drawable =
                new android.graphics.drawable.GradientDrawable();
        drawable.setColor(0xFF005EB8);
        drawable.setCornerRadius(dp(5));
        drawable.setStroke(dp(1), 0xFF003F78);
        return drawable;
    }

    private View motorwayBar(double fraction, int fillColor) {
        LinearLayout track = new LinearLayout(this);
        track.setBackground(roundRect(Color.WHITE, dp(20)));
        View fill = new View(this);
        fill.setBackground(roundRect(fillColor, dp(20)));
        int width = Math.max(0, Math.min(100, (int) Math.round(fraction * 100)));
        track.addView(fill, new LinearLayout.LayoutParams(0, -1, width));
        if (width < 100) track.addView(new View(this),
                new LinearLayout.LayoutParams(0, -1, Math.max(1, 100 - width)));
        return track;
    }

    private void addCollectiveStatistics(LinearLayout parent, DistanceStats stats) {
        LinearLayout panel = statisticsPanel("Statistics summary");

        double total = stats.drivingMetres + stats.footMetres + stats.trainMetres
                + stats.ferryMetres + stats.flightMetres + stats.transitMetres
                + stats.cyclingMetres + stats.unknownMetres;
        double uniqueDriving = stats.uniqueDrivingMetres;
        double uniqueFoot = stats.uniqueFootMetres;
        double uniqueTotal = uniqueDriving + uniqueFoot;

        addDistanceHero(panel, total);
        List<StatItem> items = new ArrayList<>();
        items.add(new StatItem("By driving / bus", stats.drivingMetres, false));
        items.add(new StatItem("On foot", stats.footMetres, false));
        items.add(new StatItem("By train", stats.trainMetres, false));
        items.add(new StatItem("By ferry", stats.ferryMetres, false));
        items.add(new StatItem("By plane", stats.flightMetres, false));
        items.add(new StatItem("Other transit", stats.transitMetres, false));
        items.add(new StatItem("By cycling", stats.cyclingMetres, false));
        items.add(new StatItem("Unknown", stats.unknownMetres, false));
        items.add(new StatItem("Unique distance", uniqueTotal, true));
        items.add(new StatItem("Unique driving", uniqueDriving, false));
        items.add(new StatItem("Unique on foot", uniqueFoot, false));
        items.add(new StatItem("Driving that was unique",
                stats.drivingMetres > 0 ? uniqueDriving / stats.drivingMetres * 100 : 0,
                false, true));
        items.add(new StatItem("Activities recorded", stats.activities, false, false, true));

        for (int index = 0; index < items.size(); index += 2) {
            if (index == 0) addStatSectionLabel(panel, "BY JOURNEY TYPE");
            if (index == 8) addStatSectionLabel(panel, "UNIQUE DISTANCE");
            if (index == 12) addStatSectionLabel(panel, "ACTIVITY");
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            StatItem first = items.get(index);
            row.addView(statCard(first), weightedCardParams(true));
            if (index + 1 < items.size()) {
                row.addView(statCard(items.get(index + 1)), weightedCardParams(false));
            }
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            rowParams.bottomMargin = dp(9);
            panel.addView(row, rowParams);
        }

        TextView note = new TextView(this);
        note.setText("Totals include imported and tracked journeys. Unique figures count matched route sections once; removed sections are excluded.");
        note.setTextSize(12);
        note.setTextColor(0xFFB9C5D8);
        note.setPadding(0, dp(5), 0, 0);
        panel.addView(note);
        parent.addView(panel);
    }

    private void addStatSectionLabel(LinearLayout panel, String title) {
        TextView heading = new TextView(this);
        heading.setText(title);
        heading.setTextSize(11);
        heading.setTypeface(null, android.graphics.Typeface.BOLD);
        heading.setTextColor(TEAL);
        heading.setLetterSpacing(0.08f);
        heading.setPadding(dp(2), dp(4), 0, dp(8));
        panel.addView(heading);
    }

    private void addDistanceHero(LinearLayout panel, double metres) {
        LinearLayout hero = new LinearLayout(this);
        hero.setGravity(Gravity.CENTER_VERTICAL);
        hero.setPadding(dp(17), dp(15), dp(17), dp(15));
        hero.setBackground(roundRect(0xFF263F7C, dp(16)));
        ImageView icon = RoadprintsIcons.image(this, R.drawable.ic_roadprints_road, TEAL, "Total distance");
        hero.addView(icon, new LinearLayout.LayoutParams(dp(48), dp(48)));
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(dp(12), 0, 0, 0);
        TextView label = new TextView(this);
        label.setText("TOTAL DISTANCE");
        label.setTextSize(11);
        label.setTypeface(null, android.graphics.Typeface.BOLD);
        label.setTextColor(0xFFB9C5D8);
        TextView value = new TextView(this);
        value.setText(renderingPendingStats ? "—" : formatMiles(metres));
        value.setTextSize(27);
        value.setTypeface(null, android.graphics.Typeface.BOLD);
        value.setTextColor(GOLD);
        value.setPadding(0, dp(2), 0, 0);
        copy.addView(label);
        copy.addView(value);
        hero.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(12);
        panel.addView(hero, params);
    }

    private TextView countBadge(String count) {
        TextView badge = new TextView(this);
        badge.setText(count);
        badge.setTextSize(12);
        badge.setTypeface(null, android.graphics.Typeface.BOLD);
        badge.setTextColor(NAVY);
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(0, dp(5), 0, dp(5));
        badge.setBackground(roundRect(GOLD, dp(14)));
        return badge;
    }

    private String formatCount(int count) {
        return String.format(Locale.UK, "%,d", count);
    }

    private LinearLayout.LayoutParams weightedCardParams(boolean first) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, dp(96), 1);
        if (first) params.rightMargin = dp(7);
        else params.leftMargin = dp(7);
        return params;
    }

    private View statCard(StatItem item) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(13), dp(13), dp(10), dp(13));
        card.setMinimumHeight(dp(76));
        boolean empty = item.value <= 0;
        int fill = empty ? 0xFF203563 : item.primary ? 0xFF304B88 : 0xFF233B78;
        card.setBackground(roundRect(fill, dp(14)));

        LinearLayout labelRow = new LinearLayout(this);
        labelRow.setGravity(Gravity.CENTER_VERTICAL);
        ImageView icon = RoadprintsIcons.image(this, statIcon(item.label), empty ? MUTED : TEAL, item.label);
        labelRow.addView(icon, new LinearLayout.LayoutParams(dp(24), dp(24)));
        TextView label = new TextView(this);
        label.setText(item.label);
        label.setTextSize(12);
        label.setTypeface(null, android.graphics.Typeface.BOLD);
        label.setTextColor(empty ? MUTED : Color.WHITE);
        label.setPadding(dp(6), 0, 0, 0);
        labelRow.addView(label);
        card.addView(labelRow);

        TextView value = new TextView(this);
        value.setText(renderingPendingStats ? "—" : item.count
                ? String.format(Locale.UK, "%,d", Math.round(item.value))
                : item.percent
                        ? String.format(Locale.UK, "%.1f%%", item.value)
                        : formatMiles(item.value));
        value.setTextSize(19);
        value.setTypeface(null, android.graphics.Typeface.BOLD);
        value.setTextColor(empty ? MUTED : Color.WHITE);
        value.setPadding(0, dp(4), 0, 0);
        card.addView(value);
        return card;
    }

    private int statIcon(String label) {
        if (label.startsWith("By driving") || label.equals("Unique driving")) return R.drawable.ic_roadprints_car;
        if (label.equals("On foot") || label.equals("Unique on foot")) return R.drawable.ic_roadprints_foot;
        if (label.contains("train")) return R.drawable.ic_roadprints_train;
        if (label.contains("ferry")) return R.drawable.ic_roadprints_ferry;
        if (label.contains("plane")) return R.drawable.ic_roadprints_plane;
        if (label.contains("cycling")) return R.drawable.ic_roadprints_bicycle;
        if (label.contains("transit")) return R.drawable.ic_roadprints_bus;
        if (label.equals("Unknown")) return R.drawable.ic_roadprints_unknown;
        if (label.equals("Unique distance")) return R.drawable.ic_roadprints_star;
        if (label.equals("Activities recorded")) return R.drawable.ic_roadprints_all;
        return R.drawable.ic_roadprints_road;
    }

    private String formatMiles(double metres) {
        return useKilometres
                ? String.format(Locale.UK, "%,.1f km", metres / 1000.0)
                : String.format(Locale.UK, "%,.1f mi", metres / 1609.344);
    }

    private static final class StatItem {
        final String label;
        final double value;
        final boolean primary;
        final boolean percent;
        final boolean count;
        StatItem(String label, double value, boolean primary) {
            this(label, value, primary, false, false);
        }
        StatItem(String label, double value, boolean primary, boolean percent) {
            this(label, value, primary, percent, false);
        }
        StatItem(String label, double value, boolean primary, boolean percent, boolean count) {
            this.label = label;
            this.value = value;
            this.primary = primary;
            this.percent = percent;
            this.count = count;
        }
    }

    private static final class DistanceStats {
        boolean pending;
        boolean summaryOnly;
        double drivingMetres;
        double footMetres;
        double trainMetres;
        double ferryMetres;
        double flightMetres;
        double transitMetres;
        double cyclingMetres;
        double unknownMetres;
        double uniqueDrivingMetres;
        double uniqueFootMetres;
        int activities;
        final Map<String, RoadDiscoveryItem> discoveredRoads = new LinkedHashMap<>();
        final Map<String, List<LocalRoadSettlementMatcher.Settlement>> settlementMatches = new LinkedHashMap<>();
        final Map<String, Integer> settlementInventoryCounts = new LinkedHashMap<>();
        final Set<String> settlementInventoryPendingCodes = new HashSet<>();
        final Set<String> settlementInventoryLoadingCodes = new HashSet<>();
        boolean localRoadEnrichmentRunning;
        boolean localTownInventoryLoadRunning;
        boolean localRoadEnrichmentComplete;
        int settlementLookupFailures;
        int localRoadLookupDone;
        int localRoadLookupTotal;
        int localRoadCacheScanned;
        int localRoadLookupInitialDone;
        long localRoadLookupStartedAt;
        long localRoadLookupLastCompletedAt;
        String localRoadLookupStage = "matching";
        String localRoadCurrentLabel = "";
        MotorwayProgressCalculator.Summary motorwayProgress;
        ARoadProgressCalculator.Summary aRoadProgress;
    }

    private static final class RoadDiscoveryItem {
        final String id;
        final String label;
        final String category;
        final Set<String> journeyIds = new HashSet<>();
        final List<JSONObject> geometryEvidence = new ArrayList<>();
        final Set<String> geometryEvidenceKeys = new HashSet<>();
        boolean driven;
        boolean onFoot;
        RoadDiscoveryItem(String id, String label, String category) {
            this.id = id;
            this.label = label;
            this.category = category;
        }
    }

    private static final class LocalTownProgress {
        final LocalRoadSettlementMatcher.Settlement settlement;
        final Map<String, RoadDiscoveryItem> roads = new LinkedHashMap<>();
        int inventoryCount = -1;
        LocalTownProgress(LocalRoadSettlementMatcher.Settlement settlement) {
            this.settlement = settlement;
        }
    }

    private android.graphics.drawable.GradientDrawable roundRect(int color, int radius) {
        android.graphics.drawable.GradientDrawable drawable =
                new android.graphics.drawable.GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private void applySystemBarInsets(View root, View heading, View bottomNavigation) {
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            int top;
            int bottom;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                top = bars.top;
                bottom = bars.bottom;
            } else {
                top = insets.getSystemWindowInsetTop();
                bottom = insets.getSystemWindowInsetBottom();
            }
            heading.setPadding(dp(18), dp(18) + top, dp(18), dp(12));
            LinearLayout.LayoutParams navParams =
                    (LinearLayout.LayoutParams) bottomNavigation.getLayoutParams();
            navParams.height = dp(68) + bottom;
            bottomNavigation.setPadding(dp(8), 0, dp(8), bottom);
            bottomNavigation.setLayoutParams(navParams);
            return insets;
        });
        root.requestApplyInsets();
    }

    private View buildBottomNavigation() {
        return RoadprintsNavigation.create(this, 2);
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

}


