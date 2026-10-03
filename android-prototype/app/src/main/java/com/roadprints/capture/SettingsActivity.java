package com.roadprints.capture;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** User preferences shared by summaries and progress screens. */
public class SettingsActivity extends Activity {
    private static final int NAVY = 0xFF0B1C50;
    private static final int CARD = 0xFF233B78;
    private static final int SELECTED = 0xFF35558F;
    private static final int TEAL = 0xFF67D5CC;
    private static final int MUTED = 0xFFD3DCED;
    private TextView milesOption;
    private TextView kilometresOption;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(NAVY);
        getWindow().setNavigationBarColor(0xFF10275D);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(24), dp(24), dp(28));
        root.setBackgroundColor(NAVY);
        LinearLayout brand = RoadprintsHeader.create(this);
        brand.setPadding(0, 0, 0, dp(30));
        root.addView(brand);
        TextView eyebrow = text("UTILITIES", 13, TEAL, true);
        eyebrow.setPadding(0, 0, 0, dp(5));
        root.addView(eyebrow);
        root.addView(text("Settings", 32, Color.WHITE, true));
        TextView label = text("Distance units", 19, Color.WHITE, true);
        label.setPadding(0, dp(26), 0, dp(5));
        root.addView(label);
        TextView detail = text("Choose how journey distances are shown across Roadprints.",
                15, MUTED, false);
        detail.setPadding(0, 0, 0, dp(16));
        root.addView(detail);

        milesOption = option("Miles", "mi");
        kilometresOption = option("Kilometres", "km");
        addOption(root, milesOption);
        addOption(root, kilometresOption);
        milesOption.setOnClickListener(view -> choose(false));
        kilometresOption.setOnClickListener(view -> choose(true));

        TextView progressNote = text("The Progress screen also has a quick unit switch. "
                + "It uses and updates this same preference.", 14, MUTED, false);
        progressNote.setPadding(0, dp(18), 0, 0);
        root.addView(progressNote);
        choose(DistanceUnits.usesKilometres(this));

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        setContentView(scroll);
    }

    private TextView option(String title, String abbreviation) {
        TextView view = new TextView(this);
        view.setText(title + "  (" + abbreviation + ")");
        view.setTextSize(17);
        view.setTextColor(Color.WHITE);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setPadding(dp(18), 0, dp(18), 0);
        view.setMinHeight(dp(62));
        view.setBackground(rounded(CARD));
        view.setClickable(true);
        view.setFocusable(true);
        return view;
    }

    private void addOption(LinearLayout root, TextView option) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(62));
        params.bottomMargin = dp(10);
        root.addView(option, params);
    }

    private void choose(boolean kilometres) {
        DistanceUnits.setKilometres(this, kilometres);
        milesOption.setBackground(rounded(kilometres ? CARD : SELECTED));
        kilometresOption.setBackground(rounded(kilometres ? SELECTED : CARD));
        milesOption.setContentDescription("Miles" + (kilometres ? "" : ", selected"));
        kilometresOption.setContentDescription("Kilometres" + (kilometres ? ", selected" : ""));
    }

    private android.graphics.drawable.GradientDrawable rounded(int color) {
        android.graphics.drawable.GradientDrawable shape = new android.graphics.drawable.GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(15));
        shape.setStroke(dp(1), 0xFF496096);
        return shape;
    }

    private TextView text(String value, float size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(null, android.graphics.Typeface.BOLD);
        return view;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
