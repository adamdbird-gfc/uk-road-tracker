package com.roadprints.capture;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Fixed full-width jade status row above navigation; expands upwards while active. */
public final class GrowingStatusControl extends TextView {
    private final Activity activity;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private ValueAnimator resize;
    private int targetHeight;
    private final Runnable refresh=new Runnable() {
        @Override public void run() {
            render(MatchingCoordinator.get(activity).snapshot());
            handler.postDelayed(this,700);
        }
    };

    private GrowingStatusControl(Activity activity) {
        super(activity);this.activity=activity;
        setBackgroundColor(0xFF67D5CC);setTextColor(0xFF0B1C50);
        setTextSize(12);setTypeface(null,android.graphics.Typeface.BOLD);
        setGravity(Gravity.CENTER);setPadding(dp(16),0,dp(16),0);
        setText("✓ Ready");targetHeight=dp(28);
        setLayoutParams(new LinearLayout.LayoutParams(-1,targetHeight));
        setOnClickListener(v->activity.startActivity(new Intent(activity,GrowingActivity.class)));
        setClickable(false);setFocusable(false);
    }

    static View create(Activity activity) { return new GrowingStatusControl(activity); }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();handler.removeCallbacks(refresh);refresh.run();
    }
    @Override protected void onDetachedFromWindow() {
        handler.removeCallbacks(refresh);if(resize!=null)resize.cancel();
        super.onDetachedFromWindow();
    }

    void render(MatchingCoordinator.Snapshot snapshot) {
        boolean active=snapshot.isGrowing()||snapshot.state==MatchingCoordinator.State.ERROR
                ||(snapshot.state==MatchingCoordinator.State.COMPLETE&&snapshot.failed>0);
        String label;
        switch(snapshot.state) {
            case PAUSED: label="Ⅱ Paused";break;
            case PAUSING: label="Pausing";break;
            case PREPARING: label="Growing · preparing";break;
            case RUNNING: label="Growing · "+snapshot.checked+" / "+snapshot.total;break;
            case ERROR: label="Retry · matching needs attention";break;
            case COMPLETE: label=snapshot.failed>0?"Retry · "+snapshot.failed+" unmatched":"✓ Ready";break;
            default: label="✓ Ready";
        }
        setText(label+(active?" · View activity ›":""));
        setContentDescription(label+(active?". Open activity details":""));
        setClickable(active);setFocusable(active);
        int height=dp(active?52:28);
        if(targetHeight==height)return;
        targetHeight=height;if(resize!=null)resize.cancel();
        int from=getLayoutParams().height;
        resize=ValueAnimator.ofInt(from,height);resize.setDuration(220);
        resize.addUpdateListener(animator->{
            android.view.ViewGroup.LayoutParams params=getLayoutParams();
            params.height=(int)animator.getAnimatedValue();setLayoutParams(params);
        });resize.start();
    }
    private int dp(int value) { return Math.round(value*getResources().getDisplayMetrics().density); }
}
