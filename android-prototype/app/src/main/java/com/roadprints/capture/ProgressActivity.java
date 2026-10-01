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
import android.widget.LinearLayout;
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

public class ProgressActivity extends Activity {
    private LinearLayout statisticsContent;
    private boolean hasResumed;
    private GrowingStatusControl growingStatus;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private int statsLoadGeneration;
    private DistanceStats loadedStats;
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
        useKilometres = getSharedPreferences("roadprints_display", MODE_PRIVATE)
                .getBoolean("distance_kilometres", false);
        getWindow().setStatusBarColor(NAVY);
        getWindow().setNavigationBarColor(NAV_BAR);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(NAVY);

        LinearLayout heading = new LinearLayout(this);
        heading.setOrientation(LinearLayout.VERTICAL);
        heading.setPadding(dp(22), dp(18), dp(22), dp(12));

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
        growingStatus = new GrowingStatusControl(this, titleRow);
        heading.addView(titleRow);
        heading.addView(intro);
        root.addView(heading);

        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(22), dp(8), dp(22), dp(22));
        scroll.addView(content);

        statisticsContent = content;
        TextView loading = new TextView(this);
        loading.setText("Loading your progress…");
        loading.setTextColor(MUTED);
        content.addView(loading);

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
        if (growingStatus != null) growingStatus.start();
        if (hasResumed) {
            loadStatistics();
        } else {
            hasResumed = true;
        }
    }

    @Override
    protected void onPause() {
        if (growingStatus != null) growingStatus.stop();
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

    private void loadStatistics() {
        final int generation = ++statsLoadGeneration;
        if (statisticsContent != null) {
            statisticsContent.removeAllViews();
            TextView loading = new TextView(this);
            loading.setText("Loading your progress…");
            loading.setTextColor(MUTED);
            statisticsContent.addView(loading);
        }
        final android.content.Context appContext = getApplicationContext();
        new Thread(() -> {
            DistanceStats stats = null;
            Exception failure = null;
            try {
                stats = summarizeSavedJourneys(appContext);
            } catch (Exception error) {
                failure = error;
            }
            final DistanceStats result = stats;
            final Exception loadError = failure;
            mainHandler.post(() -> {
                if (generation != statsLoadGeneration || isFinishing() || isDestroyed()) return;
                statisticsContent.removeAllViews();
                if (loadError == null) {
                    loadedStats = result;
                    renderStatistics(statisticsContent, result);
                } else {
                    TextView error = new TextView(this);
                    error.setText("Couldn't load progress. Your saved journeys are still on this device.");
                    error.setTextColor(MUTED);
                    statisticsContent.addView(error);
                }
            });
        }, "roadprints-progress-load").start();
    }

    private DistanceStats summarizeSavedJourneys(android.content.Context context) {
        DistanceStats stats = new DistanceStats();
        Map<String, Double> uniqueRoadEdges = new HashMap<>();
        Map<String, Double> uniqueFootEdges = new HashMap<>();
        MotorwayProgressCalculator motorwayCalculator = new MotorwayProgressCalculator(context);

        JourneyStore.forEach(context, journey -> {
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
            }
        });

        stats.uniqueDrivingMetres = sumEdges(uniqueRoadEdges);
        stats.uniqueFootMetres = sumEdges(uniqueFootEdges);
        stats.motorwayProgress = motorwayCalculator.finish();
        return stats;
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
        addRoadDiscoveryPanel(parent, stats);
        addMotorwayAggregatePanel(parent, stats);
        addMotorwayCoveragePanel(parent, stats);
        addCollectiveStatistics(parent, stats);
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

        TextView heading = new TextView(this);
        heading.setText(titleText);
        heading.setTextSize(22);
        heading.setTypeface(null, android.graphics.Typeface.BOLD);
        heading.setTextColor(Color.WHITE);
        heading.setPadding(0, 0, 0, dp(14));
        panel.addView(heading);
        return panel;
    }

    private void addRoadDiscoveryPanel(LinearLayout parent, DistanceStats stats) {
        if (stats.discoveredRoads.isEmpty()) return;
        LinearLayout panel = statisticsPanel("Road discovery");
        TextView summary = new TextView(this);
        int driven = 0, foot = 0, both = 0;
        for (RoadDiscoveryItem road : stats.discoveredRoads.values()) {
            if (road.driven) driven++;
            if (road.onFoot) foot++;
            if (road.driven && road.onFoot) both++;
        }
        summary.setText(String.format(Locale.UK, "%d roads discovered\n%d driven · %d on foot%s",
                stats.discoveredRoads.size(), driven, foot,
                both > 0 ? " · " + both + " in both" : ""));
        summary.setTextSize(14);
        summary.setTextColor(MUTED);
        summary.setPadding(0, 0, 0, dp(10));
        panel.addView(summary);

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
            TextView label = new TextView(this);
            label.setText(category + " · " + roads.size());
            label.setTextSize(15);
            label.setTextColor(Color.WHITE);
            label.setPadding(dp(13), dp(12), dp(13), dp(12));
            label.setCompoundDrawablePadding(dp(8));
            label.setCompoundDrawablesWithIntrinsicBounds(0, 0,
                    android.R.drawable.arrow_down_float, 0);
            group.addView(label);
            LinearLayout rows = new LinearLayout(this);
            rows.setOrientation(LinearLayout.VERTICAL);
            rows.setVisibility(View.GONE);
            for (RoadDiscoveryItem road : roads) {
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(12), dp(8), dp(12), dp(8));
                TextView roadName = new TextView(this);
                roadName.setText(road.label);
                roadName.setTextSize(14);
                roadName.setTypeface(null, android.graphics.Typeface.BOLD);
                roadName.setTextColor(Color.WHITE);
                if ("Motorways".equals(category)) {
                    roadName.setGravity(Gravity.CENTER);
                    roadName.setPadding(dp(10), dp(6), dp(10), dp(6));
                    roadName.setBackground(signBadge());
                }
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
            group.addView(rows);
            label.setOnClickListener(v -> rows.setVisibility(
                    rows.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE));
        }
        parent.addView(panel);
    }

    private void addMotorwayAggregatePanel(LinearLayout parent, DistanceStats stats) {
        MotorwayProgressCalculator.Summary summary = stats.motorwayProgress;
        if (summary == null || summary.roads.isEmpty()) return;
        LinearLayout panel = statisticsPanel("Motorway aggregate");
        addDistanceUnitToggle(panel);
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
            View bar = motorwayBar(maximum > 0 ? road.matchedMetres / maximum : 0);
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
            warning.setText("One or more motorway references could not be loaded. Their coverage is not included in the totals.");
            warning.setTextSize(12);
            warning.setTextColor(0xFFFFD166);
            warning.setPadding(0, 0, 0, dp(8));
            panel.addView(warning);
        }
        for (MotorwayProgressCalculator.Road road : summary.roads) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(8), 0, dp(2));
            row.addView(motorwayBadge(road), new LinearLayout.LayoutParams(dp(78), dp(32)));
            double percent = road.percent();
            View bar = motorwayBar(Double.isFinite(percent) ? percent / 100.0 : 0);
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

    private void addDistanceUnitToggle(LinearLayout panel) {
        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setPadding(0, 0, 0, dp(10));
        TextView miles = unitButton("Miles", !useKilometres);
        TextView kilometres = unitButton("km", useKilometres);
        controls.addView(miles);
        controls.addView(kilometres);
        miles.setOnClickListener(v -> setDistanceUnit(false));
        kilometres.setOnClickListener(v -> setDistanceUnit(true));
        panel.addView(controls);
    }

    private TextView unitButton(String label, boolean selected) {
        TextView button = new TextView(this);
        button.setText(label);
        button.setTextSize(13);
        button.setTypeface(null, android.graphics.Typeface.BOLD);
        button.setTextColor(selected ? NAVY : MUTED);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(12), dp(8), dp(12), dp(8));
        button.setBackground(roundRect(selected ? GOLD : 0xFF233B78, dp(10)));
        return button;
    }

    private void setDistanceUnit(boolean kilometres) {
        if (useKilometres == kilometres) return;
        useKilometres = kilometres;
        getSharedPreferences("roadprints_display", MODE_PRIVATE).edit()
                .putBoolean("distance_kilometres", kilometres).apply();
        if (loadedStats != null) renderStatistics(statisticsContent, loadedStats);
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

    private View motorwayBar(double fraction) {
        LinearLayout track = new LinearLayout(this);
        track.setBackground(roundRect(0xFF122554, dp(20)));
        View fill = new View(this);
        fill.setBackground(roundRect(TEAL, dp(20)));
        int width = Math.max(0, Math.min(100, (int) Math.round(fraction * 100)));
        track.addView(fill, new LinearLayout.LayoutParams(0, -1, width));
        if (width < 100) track.addView(new View(this),
                new LinearLayout.LayoutParams(0, -1, Math.max(1, 100 - width)));
        return track;
    }

    private void addCollectiveStatistics(LinearLayout parent, DistanceStats stats) {
        LinearLayout panel = statisticsPanel("Collective statistics");

        double total = stats.drivingMetres + stats.footMetres + stats.trainMetres
                + stats.ferryMetres + stats.flightMetres + stats.transitMetres
                + stats.cyclingMetres + stats.unknownMetres;
        double uniqueDriving = stats.uniqueDrivingMetres;
        double uniqueFoot = stats.uniqueFootMetres;
        double uniqueTotal = uniqueDriving + uniqueFoot;

        List<StatItem> items = new ArrayList<>();
        items.add(new StatItem("Total distance", total, true));
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

    private LinearLayout.LayoutParams weightedCardParams(boolean first) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        if (first) params.rightMargin = dp(7);
        else params.leftMargin = dp(7);
        return params;
    }

    private View statCard(StatItem item) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(13), dp(13), dp(10), dp(13));
        card.setMinimumHeight(dp(76));
        int fill = item.primary ? 0xFFEAF0FF : 0xFFF4F6FA;
        card.setBackground(roundRect(fill, dp(14)));

        TextView label = new TextView(this);
        label.setText(item.label);
        label.setTextSize(12);
        label.setTypeface(null, android.graphics.Typeface.BOLD);
        label.setTextColor(0xFF5A6880);

        TextView value = new TextView(this);
        value.setText(item.count
                ? String.format(Locale.UK, "%,d", Math.round(item.value))
                : item.percent
                        ? String.format(Locale.UK, "%.1f%%", item.value)
                        : formatMiles(item.value));
        value.setTextSize(19);
        value.setTypeface(null, android.graphics.Typeface.BOLD);
        value.setTextColor(0xFF10275D);
        value.setPadding(0, dp(4), 0, 0);
        card.addView(label);
        card.addView(value);
        return card;
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
        MotorwayProgressCalculator.Summary motorwayProgress;
    }

    private static final class RoadDiscoveryItem {
        final String id;
        final String label;
        final String category;
        final Set<String> journeyIds = new HashSet<>();
        boolean driven;
        boolean onFoot;
        RoadDiscoveryItem(String id, String label, String category) {
            this.id = id;
            this.label = label;
            this.category = category;
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
            heading.setPadding(dp(22), dp(18) + top, dp(22), dp(12));
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
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        nav.setPadding(dp(8), 0, dp(8), 0);
        nav.setBackgroundColor(NAV_BAR);
        int[] icons = {R.drawable.ic_nav_map, R.drawable.ic_nav_journeys,
                R.drawable.ic_nav_progress, R.drawable.ic_nav_achievements,
                R.drawable.ic_nav_collections};
        String[] labels = {"Map", "Journeys", "Progress", "Achievements", "Collections"};
        for (int index = 0; index < labels.length; index++) {
            int selected = index;
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            item.setPadding(0, dp(4), 0, 0);
            ImageView icon = new ImageView(this);
            icon.setImageResource(icons[index]);
            icon.setContentDescription(labels[index]);
            icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            icon.setColorFilter(index == 2 ? GOLD : MUTED,
                    android.graphics.PorterDuff.Mode.SRC_IN);
            item.addView(icon, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(32)));
            TextView label = new TextView(this);
            label.setText(labels[index]);
            label.setTextSize(10);
            label.setGravity(Gravity.CENTER);
            label.setIncludeFontPadding(false);
            label.setMaxLines(1);
            label.setTextColor(index == 2 ? GOLD : MUTED);
            item.addView(label, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(24)));
            if (index <= 2) {
                item.setClickable(true);
                item.setFocusable(true);
                item.setOnClickListener(v -> {
                    if (selected == 0) {
                        startActivity(new Intent(this, MapActivity.class));
                        finish();
                    } else if (selected == 1) {
                        startActivity(new Intent(this, JourneyListActivity.class));
                        finish();
                    }
                });
            } else {
                item.setAlpha(0.55f);
            }
            nav.addView(item, new LinearLayout.LayoutParams(0, dp(68), 1));
        }
        return nav;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

}
