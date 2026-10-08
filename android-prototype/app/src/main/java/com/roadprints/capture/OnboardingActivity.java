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


    private static final int NAVY = 0xFF0B1C50;
    private static final int CARD = 0xFF253B70;
    private static final int SELECTED = 0xFF35558F;
    private static final int TEAL = 0xFF27B9A9;
    private static final int GOLD = 0xFFF7C450;
    private int step = 0;
    private boolean welcomeShown;
    private WelcomeDiscoveryView discovery;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(NAVY);
        getWindow().setNavigationBarColor(NAVY);
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        androidx.core.view.WindowInsetsControllerCompat bars =
                androidx.core.view.WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        bars.setAppearanceLightStatusBars(false);
        bars.setAppearanceLightNavigationBars(false);
        if (getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(COMPLETE, false)) {
            openCapture();
            return;
        }
        if (state != null) {
            step = state.getInt("step", 0);
            welcomeShown = state.getBoolean("welcome_shown", false);
        }
        showStep();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putInt("step", step);
        state.putBoolean("welcome_shown", welcomeShown);
        super.onSaveInstanceState(state);
    }

    private void showStep() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(NAVY);
        // Apply real device safe areas outside the existing design padding.
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(scroll, (view, insets) -> {
            androidx.core.graphics.Insets safe = insets.getInsets(
                    androidx.core.view.WindowInsetsCompat.Type.systemBars()
                            | androidx.core.view.WindowInsetsCompat.Type.displayCutout());
            view.setPadding(safe.left, safe.top, safe.right, safe.bottom);
            return insets;
        });
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(30), dp(step == 0 ? 18 : 24), dp(30), dp(30));

        ImageView mark = new ImageView(this);
        mark.setImageResource(R.drawable.roadprints_mark);
        mark.setContentDescription("Roadprints");
        mark.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        root.addView(mark, new LinearLayout.LayoutParams(-1, dp(step == 0 ? 96 : 90)));
        TextView brand = text("roadprints", 38, Color.WHITE, true);
        brand.setGravity(Gravity.CENTER);
        root.addView(brand);
        TextView tagline = text("Every road tells your story.", 17, 0xFF67D5CC, false);
        tagline.setGravity(Gravity.CENTER);
        tagline.setPadding(0, dp(5), 0, dp(18));
        root.addView(tagline);

        if (step == 0) buildWelcomeStep(root);
        else if (step == 1) buildStartingStep(root);
        else buildPermissionStep(root);
        scroll.addView(root);
        setContentView(scroll);
        androidx.core.view.ViewCompat.requestApplyInsets(scroll);
    }

    private void buildWelcomeStep(LinearLayout root) {
        discovery = new WelcomeDiscoveryView(this, !welcomeShown);
        welcomeShown = true;
        root.addView(discovery, new LinearLayout.LayoutParams(-1, dp(240)));
        TextView example = text("An example of your map coming to life", 12, 0xFF9FB3D0, false);
        example.setGravity(Gravity.CENTER); example.setPadding(0, dp(6), 0, dp(18)); root.addView(example);
        TextView title = text("Every journey grows your map.", 28, Color.WHITE, true);
        title.setGravity(Gravity.CENTER); root.addView(title);
        TextView copy = text("Discover new roads, explore new places and watch your progress grow.",
                17, 0xFFD3DCED, false);
        copy.setGravity(Gravity.CENTER); copy.setPadding(0, dp(12), 0, dp(12)); root.addView(copy);
        TextView begin = action("LET’S BEGIN", GOLD);
        begin.setOnClickListener(v -> { step = 1; showStep(); }); addButton(root, begin, 16);
    }

    private void buildPermissionStep(LinearLayout root) {
        eyebrow(root, "START TRACKING");
        root.addView(text("Make your next journey count.", 28, Color.WHITE, true));
        TextView copy = text("Roadprints uses location and activity recognition to detect journeys. "
                + "Automatic tracking starts as soon as you grant precise location and physical activity permissions. "
                + "Notifications let you see when tracking is running.",
                17, 0xFFD3DCED, false);
        copy.setPadding(0, dp(12), 0, dp(20)); root.addView(copy);
        TextView note = text("Your saved journey archive stays on this device. Route matching sends route points "
                + "to our matching service. You can turn automatic tracking off in Utilities > Tracking settings.",
                14, 0xFFD3DCED, false);
        note.setPadding(dp(16), dp(14), dp(16), dp(14)); note.setBackground(roundRect(CARD, dp(15))); root.addView(note);
        TextView allow = action("ENABLE AUTOMATIC TRACKING", GOLD);
        allow.setOnClickListener(v -> {
            markComplete();
            startActivity(new Intent(this, MainActivity.class).putExtra("tracking_settings_screen", true)
                    .putExtra("onboarding_enable_tracking", true)); finish();
        }); addButton(root, allow, 24);
        TextView later = action("EXPLORE THE APP FIRST", CARD); later.setTextColor(Color.WHITE);
        later.setOnClickListener(v -> { markComplete(); openCapture(); }); addButton(root, later, 10);
    }

    private void buildStartingStep(LinearLayout root) {
        eyebrow(root, "YOUR STARTING POINT");
        root.addView(text("Where shall we begin?", 28, Color.WHITE, true));
        TextView copy = text("Bring your past journeys with you, or start discovering from today.", 17, 0xFFD3DCED, false);
        copy.setPadding(0, dp(12), 0, dp(24)); root.addView(copy);
        startingChoice(root, "IMPORT GOOGLE TIMELINE", "Discover where you’ve already been. Imperfect recordings may be partially matched.", () -> {
            markComplete(); startActivity(new Intent(this, TimelineImportActivity.class)); finish();
        });
        startingChoice(root, "START TRACKING JOURNEYS", "Build your map from today. We’ll help you set up permissions.", () -> { step = 2; showStep(); });
        startingChoice(root, "RESTORE A ROADPRINTS BACKUP", "Bring back your saved journeys, discoveries and preferences.", () -> {
            startActivity(new Intent(this, DataManagementActivity.class).putExtra("onboarding_restore", true));
        });
    }

    private void startingChoice(LinearLayout root, String label, String detail, Runnable click) {
        LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(18), dp(18), dp(18)); card.setBackground(roundRect(CARD, dp(15)));
        card.setClickable(true); card.setFocusable(true); card.setOnClickListener(v -> click.run());
        card.addView(text(label, 15, GOLD, true));
        TextView description = text(detail, 14, 0xFFD3DCED, false); description.setPadding(0, dp(7), 0, 0); card.addView(description);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.bottomMargin = dp(14); root.addView(card, params);
    }

    @Override public void onBackPressed() {
        if (step > 0) { step--; showStep(); } else super.onBackPressed();
    }

    @Override protected void onPause() {
        if (discovery != null) discovery.finishAnimation();
        super.onPause();
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

