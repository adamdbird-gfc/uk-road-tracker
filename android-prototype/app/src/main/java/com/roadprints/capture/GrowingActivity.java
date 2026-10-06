package com.roadprints.capture;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import java.text.NumberFormat;
import java.util.Locale;

public class GrowingActivity extends Activity {
    private static final int NAVY = 0xFF0B1C50;
    private static final int CARD = 0xFF233B78;
    private static final int TEAL = 0xFF67D5CC;
    private static final int MUTED = 0xFFD3DCED;
    private final NumberFormat counts = NumberFormat.getIntegerInstance(Locale.UK);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refresh = new Runnable() {
        @Override public void run() { renderSnapshot(); handler.postDelayed(this, 700); }
    };
    private TextView overall, roadDetails, footDetails, message;
    private ProgressBar overallBar, roadBar, footBar;
    private Button action;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView())
                .setAppearanceLightStatusBars(false);
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView())
                .setAppearanceLightNavigationBars(false);
        getWindow().setStatusBarColor(NAVY);
        getWindow().setNavigationBarColor(0xFF10275D);
        if (getIntent().getBooleanExtra("start_matching", false)) MatchingCoordinator.get(this).start();
        buildScreen();
    }

    @Override protected void onResume() { super.onResume(); refresh.run(); }
    @Override protected void onPause() { handler.removeCallbacks(refresh); super.onPause(); }

    private void buildScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(NAVY);

        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(26), dp(30), dp(26), dp(24));
        scroll.addView(content);
        LinearLayout brand = RoadprintsHeader.create(this);
        LinearLayout.LayoutParams brandParams = new LinearLayout.LayoutParams(-1, -2);
        brandParams.bottomMargin = dp(18);
        content.addView(brand, brandParams);

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text("Your Roadprint is forming", 25, Color.WHITE, true);
        titleRow.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        TextView close = text("CLOSE", 14, 0xFFF7C450, true);
        close.setGravity(Gravity.CENTER);
        close.setPadding(dp(8), dp(10), dp(4), dp(10));
        close.setOnClickListener(v -> finish());
        titleRow.addView(close);
        content.addView(titleRow);

        TextView eyebrow = text("ROADPRINTS IS GROWING", 12, TEAL, true);
        eyebrow.setLetterSpacing(.08f);
        eyebrow.setPadding(0, dp(20), 0, dp(4));
        content.addView(eyebrow);
        message = text("Preparing journeys…", 16, MUTED, false);
        message.setPadding(0, 0, 0, dp(18));
        content.addView(message);

        LinearLayout overallCard = card();
        TextView totalTitle = text("Matching your journeys", 18, Color.WHITE, true);
        overall = text("", 15, Color.WHITE, false);
        overall.setPadding(0, dp(6), 0, dp(10));
        overallBar = progressBar();
        overallCard.addView(totalTitle); overallCard.addView(overall); overallCard.addView(overallBar);
        content.addView(overallCard, cardParams());

        LinearLayout roadCard = laneCard("ROAD MATCHING", "Driving and bus routes");
        roadDetails = text("Preparing road journeys…", 15, MUTED, false);
        roadBar = progressBar();
        roadCard.addView(roadDetails); roadCard.addView(roadBar);
        content.addView(roadCard, cardParams());

        LinearLayout footCard = laneCard("WALKING MATCHING", "Walking and running routes");
        footDetails = text("Preparing walking journeys…", 15, MUTED, false);
        footBar = progressBar();
        footCard.addView(footDetails); footCard.addView(footBar);
        content.addView(footCard, cardParams());

        action = new Button(this);
        action.setTextColor(NAVY);
        action.setAllCaps(false);
        action.setTextSize(14);
        action.setTypeface(null, android.graphics.Typeface.BOLD);
        action.setMinHeight(dp(52));
        action.setBackground(roundRect(0xFFF7C450, dp(14)));
        action.setOnClickListener(v -> {
            MatchingCoordinator coordinator = MatchingCoordinator.get(this);
            if (coordinator.snapshot().state == MatchingCoordinator.State.RUNNING) coordinator.pause();
            else coordinator.start();
            renderSnapshot();
        });
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(-1, dp(54));
        actionParams.topMargin = dp(20);
        content.addView(action, actionParams);

        TextView hint = text("Matching continues while you explore the app.", 13, MUTED, false);
        hint.setPadding(0, dp(12), 0, dp(4));
        content.addView(hint);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        View nav = RoadprintsNavigation.create(this, -1);
        root.addView(nav, new LinearLayout.LayoutParams(-1, dp(68)));
        setContentView(root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            androidx.core.graphics.Insets safe = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            root.setPadding(safe.left, 0, safe.right, 0);
            content.setPadding(dp(26), dp(30) + safe.top, dp(26), dp(24));
            nav.setPadding(dp(8), 0, dp(8), safe.bottom);
            LinearLayout.LayoutParams navParams = (LinearLayout.LayoutParams) nav.getLayoutParams();
            navParams.height = dp(68) + safe.bottom;
            nav.setLayoutParams(navParams);
            return insets;
        });
        ViewCompat.requestApplyInsets(root);
    }

    private void renderSnapshot() {
        if (overall == null) return;
        MatchingCoordinator.Snapshot s = MatchingCoordinator.get(this).snapshot();
        overall.setText(summary(s.checked, s.total, s.matched, s.failed));
        overallBar.setMax(Math.max(1, s.total)); overallBar.setProgress(s.checked);
        roadDetails.setText(summary(s.roadChecked, s.roadTotal, s.roadMatched, s.roadChecked - s.roadMatched));
        roadBar.setMax(Math.max(1, s.roadTotal)); roadBar.setProgress(s.roadChecked);
        footDetails.setText(summary(s.footChecked, s.footTotal, s.footMatched, s.footChecked - s.footMatched));
        footBar.setMax(Math.max(1, s.footTotal)); footBar.setProgress(s.footChecked);
        message.setText(s.message);
        if (s.state == MatchingCoordinator.State.RUNNING) { action.setEnabled(true); action.setText("PAUSE GROWING"); }
        else if (s.state == MatchingCoordinator.State.PAUSING) { action.setText("PAUSING…"); action.setEnabled(false); }
        else if (s.state == MatchingCoordinator.State.PREPARING) { action.setText("PREPARING…"); action.setEnabled(false); }
        else if (s.state == MatchingCoordinator.State.PAUSED) { action.setEnabled(true); action.setText("RESUME GROWING"); }
        else if (s.state == MatchingCoordinator.State.COMPLETE) { action.setEnabled(true); action.setText(s.failed > 0 ? "RETRY UNMATCHED JOURNEYS" : "CHECK FOR MORE JOURNEYS"); }
        else { action.setEnabled(true); action.setText("START GROWING"); }
    }

    private CharSequence summary(int checked, int total, int matched, int retry) {
        SpannableStringBuilder result = new SpannableStringBuilder();
        appendValue(result, "Checked", counts.format(checked) + " / " + counts.format(total));
        appendValue(result, "Matched", counts.format(matched));
        appendValue(result, "Need retry", counts.format(retry));
        return result;
    }

    private void appendValue(SpannableStringBuilder result, String title, String value) {
        if (result.length() > 0) result.append("\n");
        int start = result.length();
        result.append(title).append(": ");
        result.setSpan(new ForegroundColorSpan(TEAL), start, result.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        result.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), start, result.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        start = result.length();
        result.append(value);
        result.setSpan(new ForegroundColorSpan(Color.WHITE), start, result.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private LinearLayout laneCard(String eyebrow, String title) {
        LinearLayout box = card();
        TextView small = text(eyebrow, 12, TEAL, true);
        TextView name = text(title, 17, Color.WHITE, true);
        name.setPadding(0, dp(3), 0, dp(9));
        box.addView(small); box.addView(name);
        return box;
    }
    private LinearLayout card() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(16), dp(18), dp(16));
        box.setBackground(roundRect(CARD, dp(18)));
        return box;
    }
    private LinearLayout.LayoutParams cardParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(14);
        return params;
    }
    private ProgressBar progressBar() {
        ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(1); bar.setProgress(0);
        return bar;
    }
    private TextView text(String value, int size, int color, boolean bold) {
        TextView text = new TextView(this);
        text.setText(value); text.setTextSize(size); text.setTextColor(color);
        if (bold) text.setTypeface(null, android.graphics.Typeface.BOLD);
        return text;
    }
    private GradientDrawable roundRect(int color, int radius) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(radius); return d;
    }
    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
