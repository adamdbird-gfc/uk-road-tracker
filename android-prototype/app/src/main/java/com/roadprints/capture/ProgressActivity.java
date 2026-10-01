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

public class ProgressActivity extends Activity {
    private LinearLayout statisticsContent;
    private boolean hasResumed;
    private GrowingStatusControl growingStatus;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private int statsLoadGeneration;
    private static final int NAVY = 0xFF0B1C50;
    private static final int NAV_BAR = 0xFF10275D;
    private static final int CARD = 0xFF233B78;
    private static final int MUTED = 0xFFB9C5D8;
    private static final int GOLD = 0xFFF7C450;
    private static final int TEAL = 0xFF67D5CC;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
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
                    addStatistics(statisticsContent, result);
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

        JourneyStore.forEach(context, journey -> {
            String mode = journey.optString("mode", "unknown").toLowerCase(Locale.ROOT);
            double metres = Math.max(0, journey.optDouble("distance_meters", 0));
            if (isShownInJourneysList(journey, mode)) stats.activities++;

            if ("driving".equals(mode) || "bus".equals(mode)) {
                stats.drivingMetres += metres;
                if ("complete".equals(journey.optString("processing_status", ""))) {
                    collectUniqueMatchedEdges(journey, uniqueRoadEdges);
                    collectMotorwayMatches(journey, stats.motorwayMetresByRef);
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
        });

        stats.uniqueDrivingMetres = sumEdges(uniqueRoadEdges);
        stats.uniqueFootMetres = sumEdges(uniqueFootEdges);
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

    private void collectMotorwayMatches(JSONObject journey, Map<String, Double> totals) {
        JSONObject result = journey.optJSONObject("processing_result");
        JSONObject geojson = result == null ? null : result.optJSONObject("motorway_geojson");
        JSONArray features = geojson == null ? null : geojson.optJSONArray("features");
        if (features == null) return;
        for (int index = 0; index < features.length(); index++) {
            JSONObject feature = features.optJSONObject(index);
            JSONObject properties = feature == null ? null : feature.optJSONObject("properties");
            if (properties == null) continue;
            String ref = properties.optString("road_ref", "").toUpperCase(Locale.ROOT)
                    .replaceAll("\\s+", "");
            if ("M6T".equals(ref) || "M6TOLL".equals(ref)) ref = "M6 Toll";
            if (!ref.matches("M[0-9]+[A-Z]?|M6 Toll")) continue;
            JSONObject geometry = feature.optJSONObject("geometry");
            JSONArray coordinates = geometry == null ? null : geometry.optJSONArray("coordinates");
            JSONArray point = coordinates == null ? null : coordinates.optJSONArray(0);
            if ("MultiLineString".equals(geometry == null ? "" : geometry.optString("type"))) {
                JSONArray firstLine = coordinates == null ? null : coordinates.optJSONArray(0);
                point = firstLine == null ? null : firstLine.optJSONArray(0);
            }
            boolean northernIreland = point != null && point.length() >= 2
                    && point.optDouble(0) < -5.3 && point.optDouble(1) > 53.9
                    && point.optDouble(1) < 55.6;
            String key = northernIreland ? "NI:" + ref : ref;
            double metres = properties.optDouble("distance_m", 0);
            if (Double.isFinite(metres) && metres > 0) {
                totals.put(key, totals.getOrDefault(key, 0.0) + metres);
            } else {
                totals.putIfAbsent(key, 0.0);
            }
        }
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

    private void addStatistics(LinearLayout parent, DistanceStats stats) {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(18), dp(18), dp(18), dp(16));
        panel.setBackground(roundRect(0xFF182F62, dp(20)));

        TextView heading = new TextView(this);
        heading.setText("Collective statistics");
        heading.setTextSize(23);
        heading.setTypeface(null, android.graphics.Typeface.BOLD);
        heading.setTextColor(Color.WHITE);
        heading.setPadding(0, 0, 0, dp(14));
        panel.addView(heading);

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
        addMotorwaySummary(parent, stats);
    }

    private void addMotorwaySummary(LinearLayout parent, DistanceStats stats) {
        if (stats.motorwayMetresByRef.isEmpty()) return;
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(18), dp(16), dp(18), dp(14));
        panel.setBackground(roundRect(0xFF182F62, dp(20)));
        LinearLayout.LayoutParams panelParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        panelParams.topMargin = dp(12);

        TextView heading = new TextView(this);
        heading.setText("Motorways travelled");
        heading.setTextSize(20);
        heading.setTypeface(null, android.graphics.Typeface.BOLD);
        heading.setTextColor(Color.WHITE);
        panel.addView(heading);

        double totalMetres = 0;
        for (double metres : stats.motorwayMetresByRef.values()) totalMetres += metres;
        TextView summary = new TextView(this);
        summary.setText(String.format(Locale.UK, "%d motorways · %s matched travel, including repeat journeys",
                stats.motorwayMetresByRef.size(), formatMiles(totalMetres)));
        summary.setTextSize(13);
        summary.setTextColor(MUTED);
        summary.setPadding(0, dp(4), 0, dp(10));
        panel.addView(summary);

        List<String> refs = new ArrayList<>(stats.motorwayMetresByRef.keySet());
        refs.sort((left, right) -> {
            String leftRef = left.replace("NI:", "");
            String rightRef = right.replace("NI:", "");
            int numeric = Integer.compare(motorwayNumber(leftRef), motorwayNumber(rightRef));
            return numeric != 0 ? numeric : left.compareTo(right);
        });
        for (String ref : refs) {
            String label = ref.startsWith("NI:") ? ref.substring(3) + " · Northern Ireland" : ref;
            TextView row = new TextView(this);
            row.setText(String.format(Locale.UK, "%s    %s", label,
                    formatMiles(stats.motorwayMetresByRef.get(ref))));
            row.setTextSize(14);
            row.setTextColor(Color.WHITE);
            row.setPadding(0, dp(5), 0, dp(5));
            panel.addView(row);
        }
        parent.addView(panel, panelParams);
    }

    private int motorwayNumber(String ref) {
        String digits = ref.replaceAll("[^0-9]", "");
        try { return Integer.parseInt(digits); }
        catch (NumberFormatException ignored) { return Integer.MAX_VALUE; }
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
        return String.format(Locale.UK, "%,.1f mi", metres / 1609.344);
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
        final Map<String, Double> motorwayMetresByRef = new HashMap<>();
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
