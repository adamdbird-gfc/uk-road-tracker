package com.roadprints.capture;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

/** A real loading state, separate from an empty travel record. Facts work offline. */
final class MapLoadingView extends LinearLayout {
    static final String[] FACTS = {
        "The M1 stretches about 193 miles between London and Leeds.",
        "Britain’s first motorway was the Preston Bypass. It opened in 1958 and is now part of the M6.",
        "British roads first received their route numbers in 1922.",
        "The final section of the M25 opened in 1986.",
        "Britain’s familiar road signs were designed by Jock Kinneir and Margaret Calvert. Their system launched in 1965.",
        "The Romans built about 2,000 miles of roads in Britain. Routes such as Watling Street are still followed today."
    };
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final TextView fact;
    private int factIndex = java.util.concurrent.ThreadLocalRandom.current().nextInt(FACTS.length);
    private boolean running;
    private final Runnable rotate = new Runnable() {
        @Override public void run() {
            if (!running) return;
            factIndex = (factIndex + 1) % FACTS.length;
            fact.setText(FACTS[factIndex]);
            handler.postDelayed(this, 8000);
        }
    };

    MapLoadingView(Context context, boolean updating) {
        super(context);
        setOrientation(VERTICAL);
        setPadding(dp(20), dp(18), dp(20), dp(18));
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xF50B1C50);
        background.setCornerRadius(dp(16));
        background.setStroke(dp(1), 0xFF67D5CC);
        setBackground(background);
        setElevation(dp(4));
        LinearLayout titleRow = new LinearLayout(context);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        ProgressBar spinner = new ProgressBar(context);
        spinner.setIndeterminate(true);
        spinner.setIndeterminateTintList(ColorStateList.valueOf(0xFFF7C450));
        spinner.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        LayoutParams spinnerParams = new LayoutParams(dp(28), dp(28));
        spinnerParams.rightMargin = dp(12);
        titleRow.addView(spinner, spinnerParams);
        TextView title = text(updating ? "Growing your Roadprints" : "Finding your Roadprints", 18, 0xFFFFFFFF, true);
        title.setAccessibilityLiveRegion(ACCESSIBILITY_LIVE_REGION_POLITE);
        titleRow.addView(title, new LayoutParams(0, -2, 1));
        addView(titleRow);
        TextView message = text(updating ? "Adding new journeys to your map. You can keep exploring."
                : "Loading the roads you’ve travelled. A little road knowledge for the journey…", 13, 0xFFD3DCED, false);
        message.setPadding(0, dp(10), 0, dp(16));
        addView(message);
        addView(text("ALONG THE WAY", 11, 0xFF67D5CC, true));
        fact = text(FACTS[factIndex], 15, 0xFFFFFFFF, false);
        fact.setPadding(0, dp(6), 0, 0);
        addView(fact);
    }

    void start() {
        if (running) return;
        running = true;
        handler.postDelayed(rotate, 8000);
    }

    void stop() {
        running = false;
        handler.removeCallbacks(rotate);
    }

    @Override protected void onDetachedFromWindow() {
        stop();
        super.onDetachedFromWindow();
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(getContext());
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(null, Typeface.BOLD);
        return view;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
