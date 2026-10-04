package com.roadprints.capture;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Shared tappable Roadprints wordmark for the main app destinations. */
final class RoadprintsHeader {
    private RoadprintsHeader() {}

    static LinearLayout create(Activity activity) {
        StatusHeader brand = new StatusHeader(activity);
        brand.setOrientation(LinearLayout.HORIZONTAL);
        brand.setGravity(Gravity.CENTER_VERTICAL);
        brand.setClickable(true);
        brand.setFocusable(true);
        brand.setContentDescription("Roadprints home");
        brand.setOnClickListener(view -> {
            activity.startActivity(new Intent(activity, MainActivity.class));
            activity.finish();
        });

        ImageView mark = new ImageView(activity);
        mark.setImageResource(R.drawable.roadprints_mark);
        mark.setContentDescription("Roadprints");
        mark.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        LinearLayout.LayoutParams markParams = new LinearLayout.LayoutParams(
                dp(activity, 42), dp(activity, 42));
        markParams.rightMargin = dp(activity, 10);
        brand.addView(mark, markParams);

        TextView wordmark = new TextView(activity);
        wordmark.setText("roadprints");
        wordmark.setTextSize(22);
        wordmark.setTypeface(null, android.graphics.Typeface.BOLD);
        wordmark.setTextColor(Color.WHITE);
        brand.addView(wordmark, new LinearLayout.LayoutParams(0, -2, 1));
        brand.setStatusControl(new GrowingStatusControl(activity, brand));
        return brand;
    }

    static void installUtilityPage(Activity activity, LinearLayout content,
                                   String backLabel, Runnable onBack) {
        activity.getWindow().setStatusBarColor(0xFF0B1C50);
        activity.getWindow().setNavigationBarColor(0xFF10275D);
        LinearLayout page = new LinearLayout(activity);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(0xFF0B1C50);
        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(0xFF0B1C50);
        scroll.addView(content, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        page.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        page.addView(backLink(activity, backLabel, onBack),
                new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(activity, 58)));
        activity.setContentView(page);
    }

    static TextView backLink(Activity activity, String label, Runnable onBack) {
        TextView back = new TextView(activity);
        back.setText(label);
        back.setTextSize(14);
        back.setTypeface(null, android.graphics.Typeface.BOLD);
        back.setTextColor(0xFF67D5CC);
        back.setGravity(Gravity.CENTER);
        back.setBackgroundColor(Color.TRANSPARENT);
        back.setClickable(true);
        back.setFocusable(true);
        back.setOnClickListener(view -> onBack.run());
        back.setPadding(dp(activity, 24), 0, dp(activity, 24), 0);
        return back;
    }

    private static final class StatusHeader extends LinearLayout {
        private GrowingStatusControl statusControl;

        StatusHeader(Activity activity) {
            super(activity);
        }

        void setStatusControl(GrowingStatusControl control) {
            statusControl = control;
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            if (statusControl != null) statusControl.start();
        }

        @Override
        protected void onDetachedFromWindow() {
            if (statusControl != null) statusControl.stop();
            super.onDetachedFromWindow();
        }
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
