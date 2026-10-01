package com.roadprints.capture;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Small live shortcut shown in the heading of the main app screens. */
public final class GrowingStatusControl {
    private final Activity activity;
    private final TextView chip;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            update();
            handler.postDelayed(this, 700);
        }
    };

    public GrowingStatusControl(Activity activity, LinearLayout headingRow) {
        this.activity = activity;
        chip = new TextView(activity);
        chip.setText("● Growing");
        chip.setTextSize(12);
        chip.setTypeface(null, android.graphics.Typeface.BOLD);
        chip.setTextColor(Color.WHITE);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(12), dp(9), dp(12), dp(9));
        chip.setBackground(background());
        chip.setContentDescription("Open matcher progress");
        chip.setVisibility(View.GONE);
        chip.setClickable(true);
        chip.setFocusable(true);
        chip.setOnClickListener(v -> activity.startActivity(new Intent(activity, GrowingActivity.class)));
        headingRow.addView(chip, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    public void start() {
        handler.removeCallbacks(refresh);
        refresh.run();
    }

    public void stop() { handler.removeCallbacks(refresh); chip.clearAnimation(); }

    private void update() {
        MatchingCoordinator.Snapshot snapshot = MatchingCoordinator.get(activity).snapshot();
        if (snapshot.state == MatchingCoordinator.State.IDLE) {
            chip.clearAnimation();
            chip.setVisibility(View.GONE);
            return;
        }
        chip.setVisibility(View.VISIBLE);
        chip.setText(snapshot.state == MatchingCoordinator.State.PAUSED ? "● Paused"
                : snapshot.state == MatchingCoordinator.State.COMPLETE ? (snapshot.failed > 0 ? "● Retry" : "● Ready")
                : snapshot.state == MatchingCoordinator.State.ERROR ? "● Retry" : "● Growing");
        if (snapshot.state != MatchingCoordinator.State.RUNNING && snapshot.state != MatchingCoordinator.State.PREPARING) {
            chip.clearAnimation();
            return;
        }
        if (chip.getAnimation() == null) {
            AlphaAnimation pulse = new AlphaAnimation(0.45f, 1f);
            pulse.setDuration(750);
            pulse.setRepeatMode(Animation.REVERSE);
            pulse.setRepeatCount(Animation.INFINITE);
            chip.startAnimation(pulse);
        }
    }

    private GradientDrawable background() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(0xFF233B78);
        drawable.setCornerRadius(dp(24));
        drawable.setStroke(dp(1), 0xFF46649E);
        return drawable;
    }
    private int dp(float value) { return Math.round(value * activity.getResources().getDisplayMetrics().density); }
}
