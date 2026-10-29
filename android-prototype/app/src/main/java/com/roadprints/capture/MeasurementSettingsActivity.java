package com.roadprints.capture;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

/** Optional measurement choices, equally available to new and existing installations. */
public class MeasurementSettingsActivity extends Activity {
    private static final int JADE = 0xFF67D5CC;
    private static final int MUTED = 0xFFD3DCED;
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF0B1C50);
        root.setPadding(dp(24), dp(24), dp(24), dp(28));
        root.addView(RoadprintsHeader.create(this));
        TextView title = text("Privacy and diagnostics", 28, Color.WHITE);
        title.setPadding(0, dp(22), 0, dp(12)); root.addView(title);
        root.addView(text("Help us improve Roadprints. Both options are optional and start off. "
                + "Journeys still record and match whichever you choose.", 16, MUTED));
        Switch usage = choice(root, "Share usage analytics", BetaMeasurement.usageEnabled(this),
                "Share screens used, journey modes, capture and matching outcomes, import totals and processing times with Google Analytics. "
                + "We do not add GPS routes, journey names or addresses to these events. Advertising ID collection and personalised advertising are disabled.");
        Switch diagnostics = choice(root, "Share diagnostics", BetaMeasurement.diagnosticsEnabled(this),
                "Share crashes, app freezes, sanitised matching failures and performance timings with Firebase. "
                + "Reports include app version, device information and crash stack traces. "
                + "Your detailed movement logs stay on this device unless you choose to export them.");
        Switch internal = choice(root, "Internal test device", BetaMeasurement.internalDevice(this),
                "Use on our development test devices to keep their activity separate from beta testers.");
        root.addView(text("Change these choices here at any time. Switching usage analytics off also resets the local analytics identifier; "
                + "previously uploaded reports are not deleted by this switch.", 14, MUTED));
        Button save = button("SAVE CHOICES");
        save.setOnClickListener(v -> {
            BetaMeasurement.saveChoices(this, usage.isChecked(), diagnostics.isChecked(), internal.isChecked());
            finish();
        });
        root.addView(save);
        Button test = button("SEND DIAGNOSTIC TEST");
        test.setEnabled(BetaMeasurement.diagnosticsEnabled(this));
        test.setOnClickListener(v -> {
            BetaMeasurement.sendDiagnosticTest(this);
            android.widget.Toast.makeText(this, "Test queued for Firebase. Your app will keep running.", android.widget.Toast.LENGTH_LONG).show();
        });
        root.addView(test);
        root.addView(text("To send a test report, save diagnostics as on, then reopen this screen. "
                + "If your child is testing, review these sharing choices together.", 14, MUTED));
        RoadprintsHeader.installUtilityPage(this, root, "BACK TO ROADPRINTS", this::finish);
    }
    private Switch choice(LinearLayout root, String title, boolean checked, String detail) {
        Switch toggle = new Switch(this);
        toggle.setText(title); toggle.setTextSize(18); toggle.setTextColor(Color.WHITE);
        toggle.setMinHeight(dp(56)); toggle.setChecked(checked);
        toggle.setThumbTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{JADE,0xFFCFD8E8}));
        toggle.setTrackTintList(ColorStateList.valueOf(0xFF446291));
        toggle.setPadding(0, dp(16), 0, 0); root.addView(toggle);
        root.addView(text(detail, 14, MUTED)); return toggle;
    }
    private Button button(String label) {
        Button button = new Button(this); button.setText(label);
        button.setTextColor(0xFF0B1C50); button.setBackgroundTintList(ColorStateList.valueOf(JADE));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(18); button.setLayoutParams(params); return button;
    }
    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this); view.setText(value); view.setTextSize(size); view.setTextColor(color);
        view.setPadding(0, dp(6), 0, dp(8)); return view;
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
