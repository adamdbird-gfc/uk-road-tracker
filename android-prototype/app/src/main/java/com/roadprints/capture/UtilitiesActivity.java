package com.roadprints.capture;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Entry point for app utilities that do not belong on the main capture screen. */
public class UtilitiesActivity extends Activity {
    private static final int NAVY = 0xFF0B1C50;
    private static final int CARD = 0xFF233B78;
    private static final int TEAL = 0xFF67D5CC;
    private static final int MUTED = 0xFFD3DCED;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(NAVY);
        getWindow().setNavigationBarColor(0xFF10275D);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(NAVY);
        root.setPadding(dp(24), dp(24), dp(24), dp(28));
        LinearLayout header = RoadprintsHeader.create(this);
        header.setPadding(0, 0, 0, dp(26));
        root.addView(header);
        TextView eyebrow = text("ROADPRINTS", 13, TEAL, true);
        root.addView(eyebrow);
        root.addView(text("Utilities", 32, Color.WHITE, true));
        TextView copy = text("Manage tracking, permissions and your saved data.", 16, MUTED, false);
        copy.setPadding(0, dp(8), 0, dp(20));
        root.addView(copy);

        add(root, "Permissions", "Manage location access and notifications",
                PermissionsActivity.class);
        add(root, "Data management", "Load Timeline data and manage saved journeys",
                DataManagementActivity.class);
        add(root, "Tracking settings", "Journey type, automatic tracking and manual capture",
                TrackingSettingsActivity.class);
        add(root, "Settings", "Distance units and display preferences", SettingsActivity.class);
        TextView debug = menuItem("Debug", "View or copy diagnostic reports");
        debug.setOnClickListener(v -> startActivity(new Intent(this, DebugActivity.class)));
        root.addView(debug, params());
        TextView back = text("BACK TO ROADPRINTS", 14, TEAL, true);
        back.setGravity(Gravity.CENTER);
        back.setClickable(true);
        back.setFocusable(true);
        back.setPadding(0, dp(16), 0, dp(4));
        back.setOnClickListener(v -> finish());
        root.addView(back, new LinearLayout.LayoutParams(-1, dp(52)));
        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        setContentView(scroll);
    }
    private void add(LinearLayout root, String title, String detail, Class<?> target) {
        TextView item = menuItem(title, detail);
        item.setOnClickListener(v -> startActivity(new Intent(this, target)));
        root.addView(item, params());
    }
    private LinearLayout.LayoutParams params() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(78));
        p.bottomMargin = dp(10);
        return p;
    }
    private TextView menuItem(String title, String detail) {
        TextView item = new TextView(this);
        item.setText(title + "\n" + detail);
        item.setTextSize(16);
        item.setTextColor(Color.WHITE);
        item.setLineSpacing(dp(2), 1f);
        item.setPadding(dp(18), dp(12), dp(18), dp(12));
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setBackground(rounded(CARD));
        item.setClickable(true);
        item.setFocusable(true);
        return item;
    }
    private android.graphics.drawable.GradientDrawable rounded(int color) {
        android.graphics.drawable.GradientDrawable shape = new android.graphics.drawable.GradientDrawable();
        shape.setColor(color); shape.setCornerRadius(dp(16)); shape.setStroke(dp(1), 0xFF46649E);
        return shape;
    }
    private TextView text(String value, float size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value); view.setTextSize(size); view.setTextColor(color);
        if (bold) view.setTypeface(null, android.graphics.Typeface.BOLD);
        return view;
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
