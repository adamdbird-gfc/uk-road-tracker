package com.roadprints.capture;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class OnboardingActivity extends Activity {
    private static final String PREFS = "roadprints_onboarding";
    private static final String COMPLETE = "complete";
    private static final String TRAVELLER = "traveller_profile";
    private static final int REQUEST_LOCATION = 41;
    private static final int NAVY = 0xFF0B1C50;
    private static final int CARD = 0xFF253B70;
    private static final int SELECTED = 0xFF35558F;
    private static final int TEAL = 0xFF27B9A9;
    private static final int GOLD = 0xFFF7C450;
    private int step = 0;
    private int travellerChoice = -1;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(NAVY);
        getWindow().setNavigationBarColor(NAVY);
        if (getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(COMPLETE, false)) {
            openCapture();
            return;
        }
        if (state != null) {
            step = state.getInt("step", 0);
            travellerChoice = state.getInt("traveller", -1);
        }
        showStep();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putInt("step", step);
        state.putInt("traveller", travellerChoice);
        super.onSaveInstanceState(state);
    }

    private void showStep() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(NAVY);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(30), dp(step == 0 ? 18 : 24), dp(30), dp(30));

        ImageView mark = new ImageView(this);
        mark.setImageResource(R.drawable.roadprints_mark);
        mark.setContentDescription("Roadprints");
        mark.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        root.addView(mark, new LinearLayout.LayoutParams(-1, dp(step == 0 ? 142 : 120)));
        TextView brand = text("roadprints", 38, Color.WHITE, true);
        brand.setGravity(Gravity.CENTER);
        root.addView(brand);
        TextView tagline = text("Every road tells your story.", 17, 0xFF67D5CC, false);
        tagline.setGravity(Gravity.CENTER);
        tagline.setPadding(0, dp(5), 0, dp(step == 0 ? 28 : 36));
        root.addView(tagline);

        if (step == 0) buildTravellerStep(root);
        else if (step == 1) buildPermissionStep(root);
        else buildStartingStep(root);
        scroll.addView(root);
        setContentView(scroll);
    }

    private void buildTravellerStep(LinearLayout root) {
        eyebrow(root, "A QUICK QUESTION");
        TextView title = text("How much of a traveller are you?", 28, Color.WHITE, true);
        title.setGravity(Gravity.CENTER);
        root.addView(title);
        TextView copy = text("Roadprints will use your answer to shape your starting experience.",
                17, 0xFFD3DCED, false);
        copy.setGravity(Gravity.CENTER);
        copy.setPadding(0, dp(10), 0, dp(20));
        root.addView(copy);

        String[] labels = {"Local explorer", "National traveller", "Always on the move"};
        String[] details = {"Mostly nearby roads and places.", "Regular journeys across the country.",
                "Journeys that take you further afield."};
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setPadding(dp(18), dp(9), dp(18), dp(9));
            card.setBackground(roundRect(i == travellerChoice ? SELECTED : CARD, dp(15)));
            card.setClickable(true);
            card.setFocusable(true);
            card.setOnClickListener(v -> { travellerChoice = index; showStep(); });
            card.addView(text(labels[i], 16, Color.WHITE, true));
            TextView detail = text(details[i], 14, 0xFFD3DCED, true);
            detail.setPadding(0, dp(3), 0, 0);
            card.addView(detail);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(76));
            params.bottomMargin = dp(10);
            root.addView(card, params);
        }
        TextView next = action("CONTINUE", TEAL);
        next.setEnabled(travellerChoice >= 0);
        next.setAlpha(travellerChoice >= 0 ? 1f : .45f);
        next.setOnClickListener(v -> {
            String[] profiles = {"local", "national", "frequent"};
            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                    .putString(TRAVELLER, profiles[travellerChoice]).apply();
            step = 1;
            showStep();
        });
        addButton(root, next, 24);
    }

    private void buildPermissionStep(LinearLayout root) {
        eyebrow(root, "LOCATION SETUP");
        root.addView(text("Can Roadprints follow your journeys?", 28, Color.WHITE, true));
        TextView copy = text("Location lets Roadprints recognise journeys while you travel. "
                + "Your archive stays on this device unless you choose otherwise.",
                17, 0xFFD3DCED, false);
        copy.setPadding(0, dp(12), 0, dp(24));
        root.addView(copy);
        TextView note = text("You can change this later in your device settings.",
                15, 0xFFD3DCED, false);
        note.setPadding(dp(16), dp(14), dp(16), dp(14));
        note.setBackground(roundRect(CARD, dp(15)));
        root.addView(note);
        TextView allow = action("ALLOW LOCATION", TEAL);
        allow.setOnClickListener(v -> requestLocation());
        addButton(root, allow, 26);
        TextView later = action("NOT NOW", CARD);
        later.setTextColor(Color.WHITE);
        later.setOnClickListener(v -> { step = 2; showStep(); });
        addButton(root, later, 10);
    }

    private void requestLocation() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            step = 2;
            showStep();
            return;
        }
        requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_LOCATION);
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == REQUEST_LOCATION) { step = 2; showStep(); }
    }

    private void buildStartingStep(LinearLayout root) {
        eyebrow(root, "YOUR STARTING POINT");
        root.addView(text("Have you got travel history?", 28, Color.WHITE, true));
        TextView copy = text("Choose how to begin your Roadprint. After this, Roadprints works quietly in the background.",
                17, 0xFFD3DCED, false);
        copy.setPadding(0, dp(12), 0, dp(24));
        root.addView(copy);
        TextView timeline = action("I HAVE TIMELINE DATA", TEAL);
        timeline.setOnClickListener(v -> {
            markComplete();
            startActivity(new Intent(this, TimelineImportActivity.class));
            finish();
        });
        addButton(root, timeline, 12);
        TextView fresh = action("NO — START FROM TODAY", TEAL);
        fresh.setOnClickListener(v -> { markComplete(); openCapture(); });
        addButton(root, fresh, 12);
        TextView privacy = text("Your journey history stays on this device.", 14, 0xFF9FB3D0, false);
        privacy.setGravity(Gravity.CENTER);
        privacy.setPadding(0, dp(16), 0, 0);
        root.addView(privacy);
    }

    private void eyebrow(LinearLayout root, String label) {
        TextView view = text(label, 13, 0xFF67D5CC, true);
        view.setLetterSpacing(.12f);
        view.setPadding(0, 0, 0, dp(10));
        root.addView(view);
    }

    private TextView action(String label, int color) {
        TextView button = text(label, 16, NAVY, true);
        button.setGravity(Gravity.CENTER);
        button.setBackground(roundRect(color, dp(15)));
        button.setMinHeight(dp(58));
        button.setClickable(true);
        button.setFocusable(true);
        return button;
    }

    private void addButton(LinearLayout root, View button, int top) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(58));
        params.topMargin = dp(top);
        root.addView(button, params);
    }

    private android.graphics.drawable.GradientDrawable roundRect(int color, int radius) {
        android.graphics.drawable.GradientDrawable drawable = new android.graphics.drawable.GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + .5f); }

    private TextView text(String value, float size, int colour, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(colour);
        if (bold) view.setTypeface(null, Typeface.BOLD);
        return view;
    }

    private void markComplete() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(COMPLETE, true).apply();
    }

    private void openCapture() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
