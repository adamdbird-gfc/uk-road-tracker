package com.roadprints.capture;

import android.app.Activity;
import android.content.Intent;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Common five-destination footer, including the active destination highlight. */
final class RoadprintsNavigation {
    private RoadprintsNavigation() {}

    static View create(Activity activity, int activeIndex) {
        LinearLayout nav = new LinearLayout(activity);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        nav.setPadding(dp(activity, 8), 0, dp(activity, 8), 0);
        nav.setBackgroundColor(0xFF10275D);
        int[] icons = {R.drawable.ic_nav_map, R.drawable.ic_nav_journeys,
                R.drawable.ic_nav_progress, R.drawable.ic_nav_achievements,
                R.drawable.ic_nav_collections};
        String[] labels = {"Map", "Journeys", "Progress", "Achievements", "Collections"};
        Class<?>[] destinations = {MapActivity.class, JourneyListActivity.class,
                ProgressActivity.class, AchievementsActivity.class, CollectionsActivity.class};
        for (int index = 0; index < labels.length; index++) {
            int selected = index;
            int color = index == activeIndex ? 0xFFF7C450 : 0xFFB9C5D8;
            LinearLayout item = new LinearLayout(activity);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            item.setPadding(0, dp(activity, 4), 0, 0);
            ImageView icon = new ImageView(activity);
            icon.setImageResource(icons[index]);
            icon.setContentDescription(labels[index]);
            icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            icon.setColorFilter(color, android.graphics.PorterDuff.Mode.SRC_IN);
            item.addView(icon, new LinearLayout.LayoutParams(-1, dp(activity, 32)));
            TextView label = new TextView(activity);
            label.setText(labels[index]);
            label.setTextSize(10);
            label.setGravity(Gravity.CENTER);
            label.setIncludeFontPadding(false);
            label.setMaxLines(1);
            label.setTextColor(color);
            item.addView(label, new LinearLayout.LayoutParams(-1, dp(activity, 24)));
            item.setClickable(true);
            item.setFocusable(true);
            item.setOnClickListener(view -> {
                if (selected != activeIndex) {
                    openDestination(activity, destinations[selected]);
                }
            });
            nav.addView(item, new LinearLayout.LayoutParams(0, dp(activity, 68), 1));
        }
        return nav;
    }

    static void openDestination(Activity activity, Class<?> destination) {
        Intent intent = new Intent(activity, destination);
        if (destination == MapActivity.class) {
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        }
        activity.startActivity(intent);
        // Keep the overview and its decoded tiles alive beneath the other tabs.
        if (!(activity instanceof MapActivity)
                || activity.getIntent().hasExtra("settlement_code")) activity.finish();
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
