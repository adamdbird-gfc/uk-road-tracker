package com.roadprints.capture;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

public class GrowingActivity extends Activity {
    private static final int NAVY = 0xFF0B1C50;
    private static final int CARD = 0xFF233B78;
    private static final int TEAL = 0xFF67D5CC;
    private static final int MUTED = 0xFFD3DCED;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refresh = new Runnable() {
        @Override public void run() { renderSnapshot(); handler.postDelayed(this, 700); }
    };
    private TextView overall, roadDetails, footDetails, message;
    private ProgressBar overallBar, roadBar, footBar;
    private Button action;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
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
        overall = text("0 / 0 checked", 24, Color.WHITE, true);
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

        TextView hint = text("Matching continues while you move between Map, Journeys and Progress.", 13, MUTED, false);
        hint.setPadding(0, dp(12), 0, dp(4));
        content.addView(hint);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout nav = new LinearLayout(this);
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(dp(14), dp(10), dp(14), dp(10));
        nav.setBackgroundColor(0xFF10275D);
        addNav(nav, "MAP", MapActivity.class);
        addNav(nav, "JOURNEYS", JourneyListActivity.class);
        addNav(nav, "PROGRESS", ProgressActivity.class);
        root.addView(nav);
        setContentView(root);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            int top = 0, bottom = 0;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                top = bars.top; bottom = bars.bottom;
            } else { top = insets.getSystemWindowInsetTop(); bottom = insets.getSystemWindowInsetBottom(); }
            content.setPadding(dp(26), dp(30) + top, dp(26), dp(24));
            nav.setPadding(dp(14), dp(10), dp(14), dp(10) + bottom);
            return insets;
        });
        root.requestApplyInsets();
    }

    private void renderSnapshot() {
        if (overall == null) return;
        MatchingCoordinator.Snapshot s = MatchingCoordinator.get(this).snapshot();
        overall.setText(s.checked + " / " + s.total + " checked  ·  " + s.matched + " matched  ·  " + s.failed + " failed");
        overallBar.setMax(Math.max(1, s.total)); overallBar.setProgress(s.checked);
        roadDetails.setText(s.roadChecked + " / " + s.roadTotal + " checked  ·  " + s.roadMatched + " matched  ·  " + (s.roadChecked - s.roadMatched) + " need retry");
        roadBar.setMax(Math.max(1, s.roadTotal)); roadBar.setProgress(s.roadChecked);
        footDetails.setText(s.footChecked + " / " + s.footTotal + " checked  ·  " + s.footMatched + " matched  ·  " + (s.footChecked - s.footMatched) + " need retry");
        footBar.setMax(Math.max(1, s.footTotal)); footBar.setProgress(s.footChecked);
        message.setText(s.message);
        if (s.state == MatchingCoordinator.State.RUNNING) { action.setEnabled(true); action.setText("PAUSE GROWING"); }
        else if (s.state == MatchingCoordinator.State.PAUSING) { action.setText("PAUSING…"); action.setEnabled(false); }
        else if (s.state == MatchingCoordinator.State.PREPARING) { action.setText("PREPARING…"); action.setEnabled(false); }
        else if (s.state == MatchingCoordinator.State.PAUSED) { action.setEnabled(true); action.setText("RESUME GROWING"); }
        else if (s.state == MatchingCoordinator.State.COMPLETE) { action.setEnabled(true); action.setText(s.failed > 0 ? "RETRY UNMATCHED JOURNEYS" : "CHECK FOR MORE JOURNEYS"); }
        else { action.setEnabled(true); action.setText("START GROWING"); }
    }

    private void addNav(LinearLayout nav, String label, Class<?> target) {
        TextView item = text(label, 11, Color.WHITE, true);
        item.setGravity(Gravity.CENTER);
        item.setPadding(dp(12), dp(12), dp(12), dp(12));
        item.setOnClickListener(v -> startActivity(new Intent(this, target).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)));
        nav.addView(item, new LinearLayout.LayoutParams(0, -2, 1));
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
