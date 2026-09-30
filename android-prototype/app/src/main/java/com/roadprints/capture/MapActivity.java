package com.roadprints.capture;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.FrameLayout;
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
        eyebrow.setTypeface(null, Typeface.BOLD);
        eyebrow.setTextColor(0xFF67D5CC);

        TextView title = new TextView(this);
        title.setText("Map");
        title.setTextSize(28);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(Color.WHITE);

        MapRoutes mapRoutes = readMapRoutes();
        TextView subtitle = new TextView(this);
        subtitle.setText(mapRoutes.sections.isEmpty()
                ? "Journeys will appear here as routes are added."
                : mapRoutes.matchedJourneys + " matched journeys · "
                        + mapRoutes.directJourneys + " other routes shown.");
        subtitle.setTextSize(14);
        subtitle.setTextColor(0xFFD3DCED);
        subtitle.setPadding(0, dp(4), 0, 0);

        heading.addView(eyebrow);
        heading.addView(title);
        heading.addView(subtitle);
        root.addView(heading);

        FrameLayout mapFrame = new FrameLayout(this);
        RoutePreviewView map = mapRoutes.sections.isEmpty()
                ? new RoutePreviewView(this)
                : new RoutePreviewView(this, mapRoutes.sections, true);
        map.setContentDescription("Interactive OpenStreetMap. Pinch to zoom and drag to move.");
        mapFrame.addView(map, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        addZoomControls(mapFrame, map);
        root.addView(mapFrame, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));

        View bottomNavigation = buildBottomNavigation();
        root.addView(bottomNavigation);
        setContentView(root);
        applySystemBarInsets(root, heading, bottomNavigation);
    }

    private MapRoutes readMapRoutes() {
        MapRoutes output = new MapRoutes();
        for (JSONObject journey : JourneyStore.all(this)) {
            String mode = journey.optString("mode", "unknown");
            if ("complete".equals(journey.optString("processing_status", ""))) {
                List<JSONArray> matched = matchedSegments(journey);
                if (!matched.isEmpty()) {
                    output.sections.addAll(matched);
                    output.matchedJourneys++;
                    continue;
                }
            }

            // Modes without a road/path matcher are drawn from their Timeline geometry.
            // Pending or failed road and walking journeys stay off the coverage map.
            if (!requiresMatching(mode)) {
                JSONObject geometry = journey.optJSONObject("route_geometry");
                JSONArray coordinates = geometry == null ? null
                        : geometry.optJSONArray("coordinates");
                if (coordinates != null && coordinates.length() >= 2) {
                    output.sections.add(coordinates);
                    output.directJourneys++;
                }
            }
        }
        return output;
    }

    private boolean requiresMatching(String mode) {
        return "driving".equals(mode) || "bus".equals(mode)
                || "walking".equals(mode) || "running".equals(mode)
                || "pedestrian".equals(mode);
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
            if ("LineString".equals(type) && hasLinePoints(coordinates)) {
                routes.add(coordinates);
            } else if ("MultiLineString".equals(type) && coordinates != null) {
                for (int line = 0; line < coordinates.length(); line++) {
                    JSONArray segment = coordinates.optJSONArray(line);
                    if (hasLinePoints(segment)) routes.add(segment);
                }
            }
        }
        return routes;
    }

    private boolean hasLinePoints(JSONArray points) {
        return points != null && points.length() >= 2;
    }

    private void addZoomControls(FrameLayout mapFrame, RoutePreviewView map) {
        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setGravity(Gravity.CENTER);
        controls.addView(zoomButton("+", "Zoom in", map::zoomIn));
        View gap = new View(this);
        controls.addView(gap, new LinearLayout.LayoutParams(1, dp(8)));
        controls.addView(zoomButton("−", "Zoom out", map::zoomOut));

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                dp(48), LinearLayout.LayoutParams.WRAP_CONTENT,
                Gravity.END | Gravity.CENTER_VERTICAL);
        params.setMargins(0, 0, dp(12), 0);
        mapFrame.addView(controls, params);
    }

    private TextView zoomButton(String label, String description, Runnable action) {
        TextView button = new TextView(this);
        button.setText(label);
        button.setTextSize(27);
        button.setTypeface(null, Typeface.BOLD);
        button.setTextColor(0xFF10275D);
        button.setGravity(Gravity.CENTER);
        button.setContentDescription(description);
        button.setBackground(roundRect(Color.WHITE, dp(8)));
        button.setElevation(dp(4));
        button.setClickable(true);
        button.setFocusable(true);
        button.setOnClickListener(v -> action.run());
        button.setLayoutParams(new LinearLayout.LayoutParams(dp(48), dp(48)));
        return button;
    }

    private GradientDrawable roundRect(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
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

        int[] icons = {R.drawable.ic_nav_map, R.drawable.ic_nav_journeys,
                R.drawable.ic_nav_progress, R.drawable.ic_nav_achievements,
                R.drawable.ic_nav_collections};
        String[] labels = {"Map", "Journeys", "Progress", "Achievements", "Collections"};
        for (int index = 0; index < labels.length; index++) {
            final int selected = index;
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            item.setPadding(0, dp(4), 0, 0);

            ImageView icon = new ImageView(this);
            icon.setImageResource(icons[index]);
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

    private static final class MapRoutes {
        final List<JSONArray> sections = new ArrayList<>();
        int matchedJourneys;
        int directJourneys;
    }
}
