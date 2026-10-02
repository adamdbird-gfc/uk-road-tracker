package com.roadprints.capture;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

/** Shared waiting screen used while one revision of saved travel data is prepared. */
final class ScreenLoadingView {
    private ScreenLoadingView() {}

    static LinearLayout create(Context context, String title, String detail) {
        float density=context.getResources().getDisplayMetrics().density;
        LinearLayout view=new LinearLayout(context);
        view.setOrientation(LinearLayout.VERTICAL);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(density,24),dp(density,24),dp(density,24),dp(density,24));
        view.setMinimumHeight(dp(density,220));
        view.setBackgroundColor(0xFF0B1C50);
        ProgressBar progress=new ProgressBar(context);
        progress.setIndeterminate(true);
        progress.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(0xFF67D5CC));
        view.addView(progress,new LinearLayout.LayoutParams(dp(density,42),dp(density,42)));
        TextView heading=new TextView(context);
        heading.setText(title);
        heading.setTextSize(17);
        heading.setTypeface(null,android.graphics.Typeface.BOLD);
        heading.setTextColor(Color.WHITE);
        heading.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams headingParams=new LinearLayout.LayoutParams(-1,-2);
        headingParams.topMargin=dp(density,18);
        view.addView(heading,headingParams);
        TextView copy=new TextView(context);
        copy.setText(detail);
        copy.setTextSize(13);
        copy.setTextColor(0xFFB9C5D8);
        copy.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams copyParams=new LinearLayout.LayoutParams(-1,-2);
        copyParams.topMargin=dp(density,7);
        view.addView(copy,copyParams);
        return view;
    }

    private static int dp(float density,int value) { return Math.round(density*value); }
}
