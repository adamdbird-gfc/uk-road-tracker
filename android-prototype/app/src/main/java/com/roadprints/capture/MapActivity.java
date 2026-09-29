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

public class MapActivity extends Activity {
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(0xFF0B1C50);
        getWindow().setNavigationBarColor(0xFF10275D);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF0B1C50);

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

        TextView subtitle = new TextView(this);
        subtitle.setText("Road coverage will appear here as journeys are matched.");
        subtitle.setTextSize(14);
        subtitle.setTextColor(0xFFD3DCED);
        subtitle.setPadding(0, dp(4), 0, 0);

        heading.addView(eyebrow);
        heading.addView(title);
        heading.addView(subtitle);
        root.addView(heading);

        RoutePreviewView map = new RoutePreviewView(this);
        map.setContentDescription("Interactive OpenStreetMap. Pinch to zoom and drag to move.");
        root.addView(map, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));

        View bottomNavigation = buildBottomNavigation();
        root.addView(bottomNavigation);
        setContentView(root);
        applySystemBarInsets(root, heading, bottomNavigation);
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
        nav.setBackgroundColor(0xFF10275D);

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
            icon.setColorFilter(index == 0 ? 0xFFF7C450 : 0xFFB9C5D8,
                    android.graphics.PorterDuff.Mode.SRC_IN);
            item.addView(icon, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(32)));

            TextView label = new TextView(this);
            label.setText(labels[index]);
            label.setTextSize(10);
            label.setGravity(Gravity.CENTER);
            label.setIncludeFontPadding(false);
            label.setMaxLines(1);
            label.setTextColor(index == 0 ? 0xFFF7C450 : 0xFFB9C5D8);
            item.addView(label, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(24)));

            if (index == 0 || index == 1) {
                item.setClickable(true);
                item.setFocusable(true);
                item.setOnClickListener(v -> {
                    if (selected == 1) {
                        startActivity(new Intent(this, JourneyListActivity.class));
                        finish();
                    }
                });
            } else {
                item.setAlpha(0.55f);
                item.setContentDescription(labels[index] + " is not available in this prototype.");
            }
            nav.addView(item, new LinearLayout.LayoutParams(0, dp(68), 1));
        }
        return nav;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
