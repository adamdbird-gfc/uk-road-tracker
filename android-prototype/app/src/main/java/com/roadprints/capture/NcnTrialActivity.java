package com.roadprints.capture;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Opt-in development surface; never sold or advertised as the full NCN collection. */
public final class NcnTrialActivity extends Activity {
    private LinearLayout body;
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);getWindow().setStatusBarColor(0xFF0B1C50);getWindow().setNavigationBarColor(0xFF10275D);
        ScrollView scroll=new ScrollView(this);body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(24),dp(24),dp(24),dp(24));body.setBackgroundColor(0xFF0B1C50);scroll.addView(body);setContentView(scroll);
        body.setOnApplyWindowInsetsListener((v,insets)->{body.setPadding(dp(24)+insets.getSystemWindowInsetLeft(),dp(24)+insets.getSystemWindowInsetTop(),dp(24)+insets.getSystemWindowInsetRight(),dp(24)+insets.getSystemWindowInsetBottom());return insets;});
    }
    @Override protected void onResume(){super.onResume();render();}
    private void render() {
        body.removeAllViews();body.addView(text("NCN integration trial",24,Color.WHITE));
        body.addView(text("Internal test of eight captured route inventories. This is not the full National Cycle Network collection or a purchase. All assessment stays on this device.",15,0xFFB9C5D8));
        boolean active=NcnProgressStore.enabled(this);
        add(active?"Deactivate trial":"Activate and assess my history",()->{NcnProgressStore.activate(this,!active);render();});
        if(active){NcnProgressStore.ensure(this);add("Retry / refresh history",()->{NcnProgressStore.ensure(this);render();});add("View in Progress",()->startActivity(new Intent(this,ProgressActivity.class).putExtra("progress_section","ncn_trial")));NcnProgressUi.add(this,body);}
        add("Close",this::finish);
    }
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private void add(String label,Runnable action){TextView v=text(label,16,0xFFF7C450);v.setPadding(dp(12),dp(20),dp(12),dp(20));v.setMinimumHeight(dp(48));v.setClickable(true);v.setFocusable(true);v.setOnClickListener(w->action.run());body.addView(v);}
    private TextView text(String label,int size,int color){TextView v=new TextView(this);v.setText(label);v.setTextSize(size);v.setTextColor(color);v.setPadding(0,dp(12),0,dp(12));return v;}
}
