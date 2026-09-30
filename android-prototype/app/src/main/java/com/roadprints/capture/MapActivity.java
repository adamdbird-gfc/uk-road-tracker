package com.roadprints.capture;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class MapActivity extends Activity {
    private static final int NAVY = 0xFF0B1C50;
    private static final int NAV_BAR = 0xFF10275D;
    private static final int MUTED = 0xFFB9C5D8;
    private static final int GOLD = 0xFFF7C450;

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
        heading.setPadding(dp(22), dp(18), dp(22), dp(14));

        TextView eyebrow = new TextView(this);
        eyebrow.setText("YOUR TRAVEL RECORD");
        eyebrow.setTextSize(12);
        eyebrow.setTypeface(null, android.graphics.Typeface.BOLD);
        eyebrow.setTextColor(0xFF67D5CC);

        TextView title = new TextView(this);
        title.setText("Map");
        title.setTextSize(28);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setTextColor(Color.WHITE);

        List<JSONArray> routes = matchedRoutes();
        TextView subtitle = new TextView(this);
        subtitle.setText(routes.isEmpty()
                ? "Matched journeys will appear here."
                : routes.size() + " matched route sections · black lines show your travel.");
        subtitle.setTextSize(14);
        subtitle.setTextColor(0xFFD3DCED);
        subtitle.setPadding(0, dp(4), 0, 0);

        heading.addView(eyebrow);
        heading.addView(title);
        heading.addView(subtitle);
        root.addView(heading);

        RoutePreviewView map = routes.isEmpty()
                ? new RoutePreviewView(this)
                : new RoutePreviewView(this, new JSONArray(), routes);
        map.setContentDescription(routes.isEmpty()
                ? "Map. Matched routes will appear here."
                : "Interactive OpenStreetMap showing " + routes.size()
                        + " successfully matched route sections.");
        root.addView(map, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));

        View bottomNavigation = buildBottomNavigation();
        root.addView(bottomNavigation);
        setContentView(root);
        applySystemBarInsets(root, heading, bottomNavigation);
    }

    private List<JSONArray> matchedRoutes() {
        List<JSONArray> routes = new ArrayList<>();
        for (JSONObject journey : JourneyStore.all(this)) {
            if (!"complete".equals(journey.optString("processing_status", ""))) continue;
            JSONObject result = journey.optJSONObject("processing_result");
            JSONObject geojson = result == null ? null : result.optJSONObject("geojson");
            JSONArray features = geojson == null ? null : geojson.optJSONArray("features");
            if (features == null) continue;
            for (int index = 0; index < features.length(); index++) {
                JSONObject feature = features.optJSONObject(index);
                JSONObject geometry = feature == null ? null : feature.optJSONObject("geometry");
                if (geometry == null) continue;
                String type = geometry.optString("type", "");
                JSONArray coordinates = geometry.optJSONArray("coordinates");
                if ("LineString".equals(type) && hasLinePoints(coordinates)) {
                    routes.add(coordinates);
                } else if ("MultiLineString".equals(type) && coordinates != null) {
                    for (int line = 0; line < coordinates.length(); line++) {
                        JSONArray segment = coordinates.optJSONArray(line);
                        if (hasLinePoints(segment)) routes.add(segment);
                    }
                }
            }
        }
        return routes;
    }

    private boolean hasLinePoints(JSONArray points) {
        return points != null && points.length() >= 2;
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
            heading.setPadding(dp(22), dp(18) + top, dp(22), dp(14));
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

        int[] iconResources = {
                R.drawable.ic_nav_map, R.drawable.ic_nav_journeys, R.drawable.ic_nav_progress,
                R.drawable.ic_nav_achievements, R.drawable.ic_nav_collections
        };
        String[] labels = {"Map", "Journeys", "Progress", "Achievements", "Collections"};
        for (int index = 0; index < labels.length; index++) {
            final int selected = index;
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            item.setPadding(0, dp(4), 0, 0);

            ImageView icon = new ImageView(this);
            icon.setImageResource(iconResources[index]);
            icon.setContentDescription(labels[index]);
            icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            icon.setColorFilter(index == 0 ? GOLD : MUTED,
                    android.graphics.PorterDuff.Mode.SRC_IN);
            item.addView(icon, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(32)));

            TextView label = new TextView(this);
            label.setText(labels[index]);
            label.setTextSize(10);
            label.setGravity(Gravity.CENTER);
            label.setIncludeFontPadding(false);
            label.setMaxLines(1);
            label.setTextColor(index == 0 ? GOLD : MUTED);
            item.addView(label, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(24)));

            if (index <= 2) {
                item.setClickable(true);
                item.setFocusable(true);
                item.setOnClickListener(v -> {
                    if (selected == 1) {
                        startActivity(new Intent(this, JourneyListActivity.class));
                        finish();
                    } else if (selected == 2) {
                        startActivity(new Intent(this, ProgressActivity.class));
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
