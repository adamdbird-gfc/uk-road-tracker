package com.roadprints.capture;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.List;

/** A child of Progress: closing or Back returns to its existing section and scroll position. */
public final class RoadDetailActivity extends Activity {
    private LinearLayout root,body;
    private TextView status;
    private RoadDetailSelection selection;
    private boolean cumulative;
    private int generation;
    private long loadedRevision=Long.MIN_VALUE;
    private RoadDetailModel model;
    private android.app.AlertDialog journeyDialog;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(0xFF0B1C50);getWindow().setNavigationBarColor(0xFF10275D);
        try { selection=RoadDetailSelection.from(getIntent()); } catch(IllegalArgumentException invalid) { finish();return; }
        cumulative=getIntent().getBooleanExtra("road_journeys",false);
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(0xFF0B1C50);
        root.setPadding(dp(16),dp(12),dp(16),dp(12));
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title=text(selection.title(),22);header.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        TextView close=action("CLOSE",this::finish);header.addView(close);root.addView(header);
        status=text("Loading your road…",14);status.setPadding(0,dp(12),0,dp(12));root.addView(status);
        body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);root.addView(body,new LinearLayout.LayoutParams(-1,0,1));
        setContentView(root);
        root.setOnApplyWindowInsetsListener((v,insets)->{
            root.setPadding(dp(16)+insets.getSystemWindowInsetLeft(),dp(12)+insets.getSystemWindowInsetTop(),
                    dp(16)+insets.getSystemWindowInsetRight(),dp(12)+insets.getSystemWindowInsetBottom());return insets;
        });
    }
    @Override protected void onResume() {
        super.onResume();if(selection!=null&&loadedRevision!=JourneyStore.dataRevision(this))load();
    }
    private void load() {
        int request=++generation;long revision=JourneyStore.dataRevision(this);
        status.setText("Loading your road…");body.removeAllViews();
        ScreenDataLoader.execute(()->{
            try {
                RoadDetailModel result=RoadDetailModel.load(getApplicationContext(),selection,cumulative);
                runOnUiThread(()->{
                    if(isFinishing()||isDestroyed()||request!=generation)return;
                    if(revision!=JourneyStore.dataRevision(this)){load();return;}
                    model=result;loadedRevision=revision;show();
                });
            } catch(Exception failure) {
                runOnUiThread(()->{
                    if(isFinishing()||isDestroyed()||request!=generation)return;
                    status.setText("This road could not be loaded. Tap to retry.");status.setOnClickListener(v->load());
                });
            }
        });
    }
    private void show() {
        status.setOnClickListener(null);
        if(cumulative) {
            status.setText(model.journeys.size()+" contributing journeys · newest first");
            body.addView(journeyList(model.journeys),new LinearLayout.LayoutParams(-1,-1));return;
        }
        status.setText(summary());
        if(model.covered.isEmpty()&&model.remaining.isEmpty()) {
            body.addView(text("No retained mapped sections are available for this road.",15));return;
        }
        TextView legend=text(model.fullReference?"Red: remaining · "+(RoadDetailSelection.MOTORWAY.equals(selection.kind)?"Blue":"Green")+": travelled"
                :"Recorded sections · tap a line to see journeys",13);body.addView(legend);
        RoutePreviewView map=RoutePreviewView.overview(this);
        if(model.fullReference&&RoadDetailSelection.MOTORWAY.equals(selection.kind))map.setMotorwayCoverageSegments(model.remaining,model.covered);
        else if(model.fullReference)map.setARoadCoverageSegments(model.remaining,model.covered);
        else map.setRouteData(null,model.covered);
        map.setContentDescription("Map of "+selection.title()+". Tap a road section to view its journeys.");
        map.setMapRoadTapListener((latitude,longitude)->{
            final int request=++generation;
            status.setText("Finding journeys on this section…");
            ScreenDataLoader.execute(()->{
                List<RoadDetailModel.Entry> related=model.journeysAt(getApplicationContext(),selection,latitude,longitude);
                runOnUiThread(()->{
                    if(isFinishing()||isDestroyed()||request!=generation)return;
                    status.setText(summary());
                    journeyDialog=RoadprintsDialog.show(this,selection.title(),related.isEmpty()?"No retained journeys on this section.":"Journeys on this section",
                            journeyList(related),"CLOSE",null,null,null);
                });
            });
        });
        body.addView(map,new LinearLayout.LayoutParams(-1,0,1));
        if(model.mapLimited)body.addView(text("Map simplified for performance.",12));
        body.addView(action("VIEW JOURNEYS",()->{
            journeyDialog=RoadprintsDialog.show(this,selection.title(),"Matched journeys · newest first",journeyList(model.journeys),"CLOSE",null,null,null);
        }));
    }
    private String summary() {
        return model.fullReference?DistanceUnits.format(this,model.uniqueMetres)+" travelled of "
                +DistanceUnits.format(this,model.referenceMetres)+String.format(java.util.Locale.UK," · %.1f%%",model.percent)
                :"Recorded sections only · full road coverage unavailable";
    }
    private ScrollView journeyList(List<RoadDetailModel.Entry> entries) {
        ScrollView scroll=new ScrollView(this);LinearLayout rows=new LinearLayout(this);rows.setOrientation(LinearLayout.VERTICAL);scroll.addView(rows);
        // Build in bounded pages so large imports do not create thousands of Views at once.
        appendPage(rows,entries,0);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(-1,-2));return scroll;
    }
    private void appendPage(LinearLayout rows,List<RoadDetailModel.Entry> entries,int start) {
        int end=Math.min(entries.size(),start+40);
        for(int i=start;i<end;i++) {
            RoadDetailModel.Entry entry=entries.get(i);
            TextView row=action(entry.title+"\n"+formatDate(entry.date),()->{
                if(journeyDialog!=null)journeyDialog.dismiss();
                if(!entry.id.isEmpty())startActivity(new Intent(this,JourneyMapEditorActivity.class).putExtra("journey_id",entry.id));
            });rows.addView(row);
        }
        if(end<entries.size()) {
            TextView more=action("SHOW MORE JOURNEYS",()->{});
            more.setOnClickListener(v->{rows.removeView(more);appendPage(rows,entries,end);});rows.addView(more);
        }
        if(entries.isEmpty())rows.addView(text("No contributing journeys remain.",14));
    }
    private String formatDate(String value) {
        try{return java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy · HH:mm",java.util.Locale.UK)
                .withZone(java.time.ZoneId.systemDefault()).format(java.time.Instant.parse(value));}
        catch(Exception ignored){return "Date unavailable";}
    }
    private TextView text(String value,int size) {TextView v=new TextView(this);v.setText(value);v.setTextSize(size);v.setTextColor(Color.WHITE);return v;}
    private TextView action(String label,Runnable click) {TextView v=text(label,14);v.setPadding(dp(12),dp(14),dp(12),dp(14));v.setTextColor(0xFFF7C450);
        v.setClickable(true);v.setFocusable(true);v.setMinHeight(dp(48));v.setOnClickListener(w->click.run());return v;}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    @Override protected void onPause(){generation++;if(journeyDialog!=null)journeyDialog.dismiss();super.onPause();}
    @Override protected void onDestroy(){generation++;super.onDestroy();}
}
