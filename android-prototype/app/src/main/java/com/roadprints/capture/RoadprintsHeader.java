package com.roadprints.capture;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Shared tappable Roadprints wordmark for the main app destinations. */
final class RoadprintsHeader {
    private RoadprintsHeader() {}

    static LinearLayout create(Activity activity) {
        LinearLayout brand = new LinearLayout(activity);
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
        return brand;
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
