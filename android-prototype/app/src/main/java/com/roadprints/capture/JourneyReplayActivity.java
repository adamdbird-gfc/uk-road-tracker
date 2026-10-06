package com.roadprints.capture;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Focused, read-only journey replay. Closing returns to the calling screen. */
public class JourneyReplayActivity extends Activity {
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private RoutePreviewView map;
    private TextView status, action;
    private ProgressBar spinner;
    private Set<String> ids;
    private volatile boolean closed;
    private boolean animating, resumed;
    private DiscoveryReplay prepared;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(0xFF0B1C50);
        getWindow().setNavigationBarColor(0xFF10275D);
        androidx.core.view.WindowCompat.getInsetsController(getWindow(),getWindow().getDecorView())
                .setAppearanceLightStatusBars(false);
        androidx.core.view.WindowCompat.getInsetsController(getWindow(),getWindow().getDecorView())
                .setAppearanceLightNavigationBars(false);
        ids = new HashSet<>();
        ArrayList<String> values = getIntent().getStringArrayListExtra("journey_ids");
        if (values != null) ids.addAll(values);
        String id = getIntent().getStringExtra("journey_id");
        if (id != null && !id.isEmpty()) ids.add(id);
        buildScreen();
        loadJourney();
    }

    private void buildScreen() {
        LinearLayout root = new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF0B1C50);
        LinearLayout header = new LinearLayout(this);header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(20),dp(12),dp(20),dp(12));
        LinearLayout title = new LinearLayout(this);title.setGravity(Gravity.CENTER_VERTICAL);
        title.addView(text("Replay journey",21,Color.WHITE,true),new LinearLayout.LayoutParams(0,-2,1));
        TextView close = text("CLOSE",13,0xFFF7C450,true);
        close.setPadding(dp(12),dp(12),dp(12),dp(12));close.setOnClickListener(view -> finish());
        title.addView(close);header.addView(title);
        header.addView(text("Watch your route unfold. Surrounding recorded roads are shown at 50% opacity.",14,0xFFD3DCED,false));
        root.addView(header);
        FrameLayout frame = new FrameLayout(this);
        map = RoutePreviewView.overview(this);map.setFlatRoadMapStyle(true);
        map.setContentDescription("Journey replay map. Pinch to zoom and drag to move.");
        frame.addView(map,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout zoom = new LinearLayout(this);zoom.setOrientation(LinearLayout.VERTICAL);
        for (String label : new String[]{"+","−"}) {
            TextView button = text(label,30,0xFF0B1C50,true);button.setGravity(Gravity.CENTER);
            button.setBackground(background(Color.WHITE));
            button.setContentDescription("+".equals(label)?"Zoom in":"Zoom out");
            button.setOnClickListener(view -> { if("+".equals(label))map.zoomIn();else map.zoomOut(); });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(48),dp(48));
            params.bottomMargin=dp(8);zoom.addView(button,params);
        }
        FrameLayout.LayoutParams zoomParams = new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.RIGHT);
        zoomParams.setMargins(0,dp(14),dp(14),0);frame.addView(zoom,zoomParams);
        root.addView(frame,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout footer = new LinearLayout(this);footer.setOrientation(LinearLayout.VERTICAL);
        footer.setBackgroundColor(0xFF10275D);footer.setPadding(dp(20),dp(12),dp(20),dp(12));
        LinearLayout progress = new LinearLayout(this);progress.setGravity(Gravity.CENTER_VERTICAL);
        spinner = new ProgressBar(this);progress.addView(spinner,new LinearLayout.LayoutParams(dp(24),dp(24)));
        status = text("Opening your matched journey…",14,0xFF67D5CC,false);
        status.setPadding(dp(10),0,0,0);progress.addView(status,new LinearLayout.LayoutParams(0,-2,1));
        footer.addView(progress);
        action = text("PREPARING REPLAY…",13,0xFF0B1C50,true);action.setGravity(Gravity.CENTER);
        action.setBackground(background(0xFFF7C450));action.setEnabled(false);action.setAlpha(.6f);
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,dp(48));params.topMargin=dp(12);
        footer.addView(action,params);
        action.setOnClickListener(view -> {
            if(animating)map.finishDiscoveryReplay();else play();
        });
        root.addView(footer);
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root,(view,insets)->{
            androidx.core.graphics.Insets safe=insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars()
                    |androidx.core.view.WindowInsetsCompat.Type.displayCutout());
            root.setPadding(safe.left,0,safe.right,0);
            header.setPadding(dp(20),dp(12)+safe.top,dp(20),dp(12));
            footer.setPadding(dp(20),dp(12),dp(20),dp(12)+safe.bottom);return insets;
        });
        setContentView(root);androidx.core.view.ViewCompat.requestApplyInsets(root);
    }

    private void loadJourney() {
        worker.execute(()->{
            try {
                DiscoveryReplay trace = DiscoveryReplay.loadRoutes(getApplicationContext(),ids);
                main.post(()->{
                    if(closed)return;
                    if(trace.routes.isEmpty()){
                        spinner.setVisibility(View.GONE);status.setText("No matched route is available for replay.");return;
                    }
                    // The selected trace is visible before any whole-archive discovery scan.
                    map.startDiscoveryReplay(trace,()->{});map.finishDiscoveryReplay();
                    status.setText("Preparing discoveries… Your matched route is ready to explore.");
                });
                if(trace.routes.isEmpty())return;
                MapActivity.ReplayContext context=MapActivity.readReplayContext(getApplicationContext(),trace,()->closed);
                main.post(()->{if(!closed)map.setReplayContext(context.roads,context.motorways,context.aRoads);});
                DiscoveryReplay result=DiscoveryReplay.calculate(getApplicationContext(),ids,()->closed);
                main.post(()->{
                    if(closed)return;prepared=result;spinner.setVisibility(View.GONE);
                    action.setEnabled(true);action.setAlpha(1);play();
                    if(!resumed)map.finishDiscoveryReplay();
                });
            } catch(Exception error) {
                main.post(()->{if(!closed){spinner.setVisibility(View.GONE);
                    status.setText("Discoveries could not be prepared. Your matched route remains available.");
                    action.setText("RETRY REPLAY");action.setEnabled(true);action.setAlpha(1);
                    action.setOnClickListener(view->{spinner.setVisibility(View.VISIBLE);action.setEnabled(false);loadJourney();});}});
            }
        });
    }

    private void play(){
        if(prepared==null)return;animating=true;action.setText("SKIP ANIMATION");status.setText("Your journey is growing your map…");
        map.startDiscoveryReplay(prepared,()->{
            if(closed)return;animating=false;action.setText("PLAY AGAIN");
            String summary=prepared.labels.isEmpty()?"Your matched journey · no new road discoveries confirmed."
                    :"Your discoveries: "+android.text.TextUtils.join(" · ",prepared.labels);
            if(summary.length()>280)summary=summary.substring(0,277)+"…";
            status.setText(summary);
        });
    }
    @Override protected void onResume(){super.onResume();resumed=true;}
    @Override protected void onPause(){resumed=false;if(map!=null)map.finishDiscoveryReplay();super.onPause();}
    @Override protected void onDestroy(){closed=true;worker.shutdownNow();main.removeCallbacksAndMessages(null);super.onDestroy();}
    private TextView text(String value,int size,int color,boolean bold){TextView v=new TextView(this);v.setText(value);v.setTextSize(size);
        v.setTextColor(color);if(bold)v.setTypeface(null,Typeface.BOLD);return v;}
    private GradientDrawable background(int color){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(12));return d;}
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
}
