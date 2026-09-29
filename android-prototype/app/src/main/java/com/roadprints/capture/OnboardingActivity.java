package com.roadprints.capture;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

public class OnboardingActivity extends Activity {
    private static final String PREFS = "roadprints_onboarding";
    private static final String COMPLETE = "complete";

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(0xFF0B1C50);
        getWindow().setNavigationBarColor(0xFF0B1C50);

        SharedPreferences preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
        if (preferences.getBoolean(COMPLETE, false)) {
            openCapture();
            return;
        }
        buildScreen();
    }

    private void buildScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(36, 72, 36, 36);
        root.setBackgroundColor(0xFF0B1C50);

        ImageView mark = new ImageView(this);
        mark.setImageResource(R.drawable.roadprints_mark);
        mark.setContentDescription("Roadprints");
        mark.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        root.addView(mark, new LinearLayout.LayoutParams(112, 112));

        TextView brand = text("roadprints", 34, Color.WHITE, true);
        brand.setGravity(Gravity.CENTER);
        brand.setPadding(0, 12, 0, 4);
        root.addView(brand);

        TextView tagline = text("Every journey tells a story.", 17, 0xFF67D5CC, false);
        tagline.setGravity(Gravity.CENTER);
        tagline.setPadding(0, 0, 0, 36);
        root.addView(tagline);

        TextView intro = text("How would you like to begin?", 22, Color.WHITE, true);
        intro.setGravity(Gravity.CENTER);
        intro.setPadding(0, 0, 0, 10);
        root.addView(intro);

        TextView explanation = text(
                "Bring in your Google Timeline journeys, or start building your Roadprints journey from today.",
                16, 0xFFD3DCED, false);
        explanation.setGravity(Gravity.CENTER);
        explanation.setPadding(0, 0, 0, 28);
        root.addView(explanation);

        Button timeline = routeButton("I HAVE TIMELINE DATA");
        timeline.setOnClickListener(v -> {
            markComplete();
            startActivity(new Intent(this, TimelineImportActivity.class));
        });
        root.addView(timeline, buttonParams());

        Button fresh = routeButton("I'M STARTING FRESH");
        fresh.setOnClickListener(v -> {
            markComplete();
            openCapture();
        });
        root.addView(fresh, buttonParams());

        if (JourneyStore.count(this) > 0) {
            Button saved = routeButton("VIEW SAVED JOURNEYS");
            saved.setOnClickListener(v ->
                    startActivity(new Intent(this, JourneyListActivity.class)));
            root.addView(saved, buttonParams());
        }

        TextView privacy = text(
                "Your journey history stays on this device.",
                13, 0xFF9FB3D0, false);
        privacy.setGravity(Gravity.CENTER);
        privacy.setPadding(0, 28, 0, 0);
        root.addView(privacy);

        setContentView(root);
    }

    private TextView text(String value, float size, int colour, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(colour);
        if (bold) view.setTypeface(null, Typeface.BOLD);
        return view;
    }

    private Button routeButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(14);
        button.setTextColor(0xFF0B1C50);
        button.setTypeface(null, Typeface.BOLD);
        button.setAllCaps(false);
        button.setBackgroundColor(0xFFF7C450);
        return button;
    }

    private LinearLayout.LayoutParams buttonParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 58);
        params.setMargins(0, 0, 0, 14);
        return params;
    }

    private void markComplete() {
        getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit().putBoolean(COMPLETE, true).apply();
    }

    private void openCapture() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
