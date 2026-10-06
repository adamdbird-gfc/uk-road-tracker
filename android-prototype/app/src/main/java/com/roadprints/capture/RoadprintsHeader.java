package com.roadprints.capture;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.os.Build;
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
        TextView utilities = new TextView(activity);
        utilities.setText("⚙ Utilities");
        utilities.setTextSize(12);
        utilities.setTextColor(0xFFF7C450);
        utilities.setTypeface(null, android.graphics.Typeface.BOLD);
        utilities.setPadding(dp(activity, 10), dp(activity, 12), 0, dp(activity, 12));
        utilities.setClickable(true);
        utilities.setFocusable(true);
        utilities.setContentDescription("Open Utilities");
        utilities.setOnClickListener(view -> activity.startActivity(
                new Intent(activity, UtilitiesActivity.class)));
        brand.addView(utilities);
        LinearLayout statusRow = new LinearLayout(activity);
        statusRow.setGravity(Gravity.CENTER_VERTICAL);
        brand.setStatusControl(new GrowingStatusControl(activity, statusRow));
        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.VERTICAL);
        header.addView(brand);
        header.addView(statusRow);
        return header;
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
        TextView footer = backLink(activity, backLabel, onBack);
        page.addView(footer, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(activity, 58)));
        int left = content.getPaddingLeft();
        int top = content.getPaddingTop();
        int right = content.getPaddingRight();
        int bottom = content.getPaddingBottom();
        page.setOnApplyWindowInsetsListener((view, insets) -> {
            int systemTop;
            int systemBottom;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                android.graphics.Insets bars = insets.getInsets(
                        android.view.WindowInsets.Type.systemBars());
                systemTop = bars.top;
                systemBottom = bars.bottom;
            } else {
                systemTop = insets.getSystemWindowInsetTop();
                systemBottom = insets.getSystemWindowInsetBottom();
            }
            content.setPadding(left, top + systemTop, right, bottom);
            footer.setPadding(dp(activity, 24), 0, dp(activity, 24), systemBottom);
            LinearLayout.LayoutParams footerParams =
                    (LinearLayout.LayoutParams) footer.getLayoutParams();
            footerParams.height = dp(activity, 58) + systemBottom;
            footer.setLayoutParams(footerParams);
            return insets;
        });
        activity.setContentView(page);
        page.requestApplyInsets();
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

