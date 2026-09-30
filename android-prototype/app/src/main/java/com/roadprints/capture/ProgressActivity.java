package com.roadprints.capture;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONObject;

import java.util.List;

public class ProgressActivity extends Activity {
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
        intro.setText("See which journeys have matched and what still needs attention.");
        intro.setTextSize(14);
        intro.setTextColor(0xFFD3DCED);
        intro.setPadding(0, dp(4), 0, 0);

        heading.addView(eyebrow);
        heading.addView(title);
        heading.addView(intro);
        root.addView(heading);

        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(22), dp(8), dp(22), dp(22));
        scroll.addView(content);

        Counts counts = summarize(JourneyStore.all(this));
        addCard(content, "MATCHED ROUTES", counts.matched,
                counts.roadMatched + " road/bus · " + counts.footMatched + " on foot",
                TEAL);
        addCard(content, "READY TO MATCH", counts.roadReady + counts.footReady,
                counts.roadReady + " road/bus · " + counts.footReady + " on foot",
                GOLD);
        addCard(content, "MATCHING FAILED", counts.failed,
                "Open a journey to retry its match.",
                0xFFF28C9B);
        addCard(content, "OTHER TRAVEL", counts.other,
                "Train, flight, cycling and other modes are kept in your journey history.",
                MUTED);

        TextView openJourneys = new TextView(this);
        openJourneys.setText("VIEW JOURNEYS");
        openJourneys.setTextSize(14);
        openJourneys.setTypeface(null, android.graphics.Typeface.BOLD);
        openJourneys.setTextColor(NAVY);
        openJourneys.setGravity(Gravity.CENTER);
        openJourneys.setBackground(roundRect(GOLD, dp(14)));
        openJourneys.setPadding(dp(16), dp(16), dp(16), dp(16));
        openJourneys.setClickable(true);
        openJourneys.setFocusable(true);
        openJourneys.setOnClickListener(v ->
                startActivity(new Intent(this, JourneyListActivity.class)));
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        buttonParams.topMargin = dp(4);
        content.addView(openJourneys, buttonParams);

        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        View bottomNavigation = buildBottomNavigation();
        root.addView(bottomNavigation);
        setContentView(root);
        applySystemBarInsets(root, heading, bottomNavigation);
    }

    private Counts summarize(List<JSONObject> journeys) {
        Counts counts = new Counts();
        for (JSONObject journey : journeys) {
            String mode = journey.optString("mode", "unknown");
            String status = journey.optString("processing_status", "pending");
            boolean road = "driving".equals(mode) || "bus".equals(mode);
            boolean foot = "walking".equals(mode) || "running".equals(mode)
                    || "pedestrian".equals(mode);
            if (!road && !foot) {
                counts.other++;
                continue;
            }
            if (!hasEnoughEvidence(journey, road)) {
                continue;
            }
            boolean hasGeometry = !matchedSegments(journey).isEmpty();
            if ("complete".equals(status) && hasGeometry) {
                counts.matched++;
                if (foot) counts.footMatched++;
                else counts.roadMatched++;
            } else if ("failed".equals(status)) {
                counts.failed++;
            } else if (foot) {
                counts.footReady++;
            } else {
                counts.roadReady++;
            }
        }
        return counts;
    }

    private boolean hasEnoughEvidence(JSONObject journey, boolean road) {
        JSONObject quality = journey.optJSONObject("capture_quality");
        if (road && "timeline_import".equals(
                journey.optJSONObject("source") == null ? ""
                        : journey.optJSONObject("source").optString("type", ""))) {
            return quality != null && quality.optInt("source_route_points", 0) >= 2;
        }
        int points = quality == null ? 0 : quality.optInt("gps_points", 0);
        return points >= 2;
    }

    private java.util.List<org.json.JSONArray> matchedSegments(JSONObject journey) {
        java.util.List<org.json.JSONArray> routes = new java.util.ArrayList<>();
        JSONObject result = journey.optJSONObject("processing_result");
        JSONObject geojson = result == null ? null : result.optJSONObject("geojson");
        org.json.JSONArray features = geojson == null ? null : geojson.optJSONArray("features");
        if (features == null) return routes;
        for (int index = 0; index < features.length(); index++) {
            JSONObject feature = features.optJSONObject(index);
            JSONObject geometry = feature == null ? null : feature.optJSONObject("geometry");
            if (geometry == null) continue;
            String type = geometry.optString("type", "");
            org.json.JSONArray coordinates = geometry.optJSONArray("coordinates");
            if ("LineString".equals(type) && coordinates != null && coordinates.length() >= 2) {
                routes.add(coordinates);
            } else if ("MultiLineString".equals(type) && coordinates != null) {
                for (int line = 0; line < coordinates.length(); line++) {
                    org.json.JSONArray segment = coordinates.optJSONArray(line);
                    if (segment != null && segment.length() >= 2) routes.add(segment);
                }
            }
        }
        return routes;
    }

    private void addCard(LinearLayout parent, String label, int value,
                         String detail, int accent) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(16), dp(18), dp(16));
        card.setBackground(roundRect(CARD, dp(16)));

        TextView title = new TextView(this);
        title.setText(label);
        title.setTextSize(12);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setTextColor(accent);

        TextView count = new TextView(this);
        count.setText(String.format(java.util.Locale.UK, "%,d", value));
        count.setTextSize(28);
        count.setTypeface(null, android.graphics.Typeface.BOLD);
        count.setTextColor(Color.WHITE);
        count.setPadding(0, dp(2), 0, dp(2));

        TextView description = new TextView(this);
        description.setText(detail);
        description.setTextSize(13);
        description.setTextColor(MUTED);

        card.addView(title);
        card.addView(count);
        card.addView(description);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(12);
        parent.addView(card, params);
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

    private static final class Counts {
        int matched;
        int roadMatched;
        int footMatched;
        int roadReady;
        int footReady;
        int failed;
        int insufficient;
        int other;
    }
}
