package com.roadprints.capture;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.List;

/** Focused Progress map with independent, bounded loading and retained geometry. */
public final class RoadDetailActivity extends Activity {
    private LinearLayout root,stats,skeleton;
    private FrameLayout body,mapFrame;
    private RoutePreviewView map;
    private View zoom,loadingPanel;
    private TextView status,loadingLabel,journeyAction,sectionStatus,retry,legend,recordedLabel;
    private RoadCompletionBar progress;
    private CoverageSummary coverageSummary;
    private RoadDetailSelection selection;
    private boolean cumulative,geometryShown;
    private int generation;
    private long loadedRevision=Long.MIN_VALUE;
    private RoadDetailModel model;
    private android.app.AlertDialog journeyDialog;
    private final android.os.Handler handler=new android.os.Handler(android.os.Looper.getMainLooper());
    static final long LOAD_TIMEOUT_MS=30_000;
    private java.util.concurrent.Future<?> pending;
    private boolean loading;
    private final NavigationTrace navigation=new NavigationTrace("focused");
    private Runnable timeout;
    interface Loader {
        RoadDetailModel load(android.content.Context c,RoadDetailSelection s,boolean cumulative)throws Exception;
        default RoadDetailModel progressive(android.content.Context c,RoadDetailSelection s,boolean cumulative,java.util.function.Consumer<RoadDetailModel> ready)throws Exception {
            RoadDetailModel result=load(c,s,cumulative);if(!cumulative)ready.accept(result.geometryPreview());return result;
        }
    }
    private static final Loader DEFAULT_LOADER=new Loader(){
        public RoadDetailModel load(android.content.Context c,RoadDetailSelection s,boolean all)throws Exception{return RoadDetailModel.load(c,s,all);}
        public RoadDetailModel progressive(android.content.Context c,RoadDetailSelection s,boolean all,java.util.function.Consumer<RoadDetailModel> ready)throws Exception{return RoadDetailModel.load(c,s,all,ready);}
    };
    static Loader loader=DEFAULT_LOADER;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);androidx.core.view.WindowCompat.setDecorFitsSystemWindows(getWindow(),false);getWindow().setStatusBarColor(0xFF0B1C50);getWindow().setNavigationBarColor(0xFF10275D);
        try{selection=RoadDetailSelection.from(getIntent());}catch(IllegalArgumentException invalid){finish();return;}
        cumulative=getIntent().getBooleanExtra("road_journeys",false);
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(0xFF0B1C50);root.setPadding(dp(16),dp(6),dp(16),dp(12));
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(text(selection.title(),22),new LinearLayout.LayoutParams(0,-2,1));
        header.addView(RoadprintsDialog.closeControl(this,this::finish));root.addView(header,new LinearLayout.LayoutParams(-1,-2));
        stats=new LinearLayout(this);stats.setOrientation(LinearLayout.VERTICAL);stats.setPadding(dp(12),dp(10),dp(12),dp(12));
        stats.setBackground(JourneyCardHeader.background(this,0xFF233B78,0xFF46649E,14));root.addView(stats);
        status=text("Loading your road…",14);status.setTag("road_statistics");stats.addView(status);
        skeleton=new LinearLayout(this);skeleton.setOrientation(LinearLayout.VERTICAL);skeleton.setTag("road_stats_skeleton");skeleton.setContentDescription("Road statistics loading");
        for(int i=0;i<2;i++){View line=new View(this);line.setBackgroundColor(0xFF46649E);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(i==0?-1:dp(150),dp(10));lp.topMargin=dp(8);skeleton.addView(line,lp);}stats.addView(skeleton);
        int colour=RoadDetailSelection.NCN.equals(selection.kind)?NcnProgressUi.PURPLE:RoadDetailSelection.MOTORWAY.equals(selection.kind)?RoadCompletionBar.MOTORWAY:RoadCompletionBar.A_ROAD;
        progress=new RoadCompletionBar(this,0,colour);progress.setTag("road_completion_graph");progress.setVisibility(View.GONE);
        coverageSummary=new CoverageSummary(this,colour,progress);coverageSummary.setVisibility(View.GONE);stats.addView(coverageSummary,new LinearLayout.LayoutParams(-1,-2));
        stats.setPadding(0,0,0,0);
        legend=text("",12);legend.setPadding(dp(14),dp(6),dp(14),dp(8));stats.addView(legend);
        if(RoadDetailSelection.NCN.equals(selection.kind)){
            LinearLayout modes=new LinearLayout(this);for(String mode:new String[]{"combined","cycling","on_foot"})modes.addView(action(NcnProgressUi.label(mode),()->{NcnProgressStore.mode(this,mode);selection.ncnMode=mode;loadedRevision=Long.MIN_VALUE;geometryShown=false;load();}),new LinearLayout.LayoutParams(0,-2,1));root.addView(modes);
        }
        sectionStatus=text("",13);sectionStatus.setVisibility(View.GONE);root.addView(sectionStatus);
        recordedLabel=text("RECORDED SECTIONS",12);recordedLabel.setTextColor(0xFF67D5CC);recordedLabel.setPadding(0,dp(6),0,dp(6));recordedLabel.setVisibility(View.GONE);root.addView(recordedLabel);
        body=new FrameLayout(this);root.addView(body,new LinearLayout.LayoutParams(-1,0,1));
        mapFrame=new FrameLayout(this);mapFrame.setTag("road_map_container");body.addView(mapFrame,new FrameLayout.LayoutParams(-1,-1));
        map=RoutePreviewView.overview(this);map.setFlatRoadMapStyle(true);map.setOverviewLoading(true);mapFrame.addView(map,new FrameLayout.LayoutParams(-1,-1));
        zoom=MapZoomControls.add(mapFrame,map);zoom.setVisibility(View.VISIBLE);
        LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setGravity(Gravity.CENTER);panel.setPadding(dp(16),dp(12),dp(16),dp(12));
        panel.setBackground(JourneyCardHeader.background(this,0xEE0B1C50,0xFF46649E,14));panel.setTag("road_loading_overlay");
        ProgressBar spinner=new ProgressBar(this);spinner.setTag("road_loading_spinner");spinner.setIndeterminate(true);spinner.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(0xFF67D5CC));panel.addView(spinner,new LinearLayout.LayoutParams(dp(32),dp(32)));
        loadingLabel=text("Preparing road geometry…",14);panel.addView(loadingLabel);
        retry=action("RETRY",this::load);retry.setVisibility(View.GONE);panel.addView(retry);
        loadingPanel=panel;mapFrame.addView(panel,new FrameLayout.LayoutParams(-2,-2,Gravity.CENTER));
        journeyAction=action("Preparing contributing journeys…",()->showJourneys(model.journeys,null));journeyAction.setEnabled(false);root.addView(journeyAction);
        journeyAction.setGravity(Gravity.CENTER);journeyAction.setTextColor(0xFF0B1C50);
        journeyAction.setBackground(JourneyCardHeader.background(this,0xFFF7C450,0xFFF7C450,14));
        LinearLayout.LayoutParams actionParams=new LinearLayout.LayoutParams(-1,-2);actionParams.topMargin=dp(10);root.removeView(journeyAction);root.addView(journeyAction,actionParams);
        if(!cumulative&&!RoadDetailSelection.MOTORWAY.equals(selection.kind)&&!RoadDetailSelection.A_ROAD.equals(selection.kind)&&!RoadDetailSelection.NCN.equals(selection.kind)){stats.setVisibility(View.GONE);recordedLabel.setVisibility(View.VISIBLE);}
        setContentView(root);
        root.setOnApplyWindowInsetsListener((v,insets)->{
            root.setPadding(dp(16)+insets.getSystemWindowInsetLeft(),dp(6)+insets.getSystemWindowInsetTop(),dp(16)+insets.getSystemWindowInsetRight(),dp(12)+insets.getSystemWindowInsetBottom());return insets;
        });root.requestApplyInsets();
    }
    @Override protected void onResume(){super.onResume();navigation.resume(this);navigation.frame(mapFrame,"container","");if(selection!=null&&!loading&&loadedRevision!=JourneyStore.dataRevision(this))load();else if(model!=null){showStatistics();navigation.frame(map,"first_map","cache=retained");navigation.frame(map,"ready","cache=retained");}}
    private void load() {
        cancelPending();int request=++generation;long revision=JourneyStore.dataRevision(this);
        if(loadedRevision!=revision)geometryShown=false;
        String key=RoadDetailCache.key(this,selection,cumulative,revision);
        RoadDetailModel cached=RoadDetailCache.get(key);
        if(cached!=null&&loader==DEFAULT_LOADER){model=cached;loadedRevision=revision;show();return;}
        if(loader==DEFAULT_LOADER&&!cumulative&&!geometryShown){RoadDetailModel preview=ProgressActivity.focusedPreview(this,selection);if(preview!=null){model=preview;showGeometry();}}
        map.setMapRoadTapListener(null);
        loading=true;loadingPanel.findViewWithTag("road_loading_spinner").setVisibility(View.VISIBLE);status.setOnClickListener(null);loadingPanel.setVisibility(geometryShown?View.GONE:View.VISIBLE);loadingLabel.setText("Preparing road geometry…");retry.setVisibility(View.GONE);
        if(!geometryShown){coverageSummary.setVisibility(View.GONE);status.setVisibility(View.VISIBLE);status.setText("Loading your road…");skeleton.setVisibility(View.VISIBLE);progress.setVisibility(View.GONE);}
        journeyAction.setEnabled(false);
        deadline(request,()->showFailure("Loading took too long. Tap to retry."));
        pending=ScreenDataLoader.interactive(()->{
            try {
                RoadDetailModel result=loader.progressive(getApplicationContext(),selection,cumulative,preview->runOnUiThread(()->{
                    if(!current(request)||revision!=JourneyStore.dataRevision(this))return;
                    model=preview;if(!geometryShown)showGeometry();else showStatistics();journeyAction.setText("Preparing contributing journeys…");
                }));
                runOnUiThread(()->{
                    if(!current(request))return;cancelPending();
                    if(revision!=JourneyStore.dataRevision(this)){showFailure("Journey data changed. Tap to retry.");return;}
                    model=result;loadedRevision=revision;RoadDetailCache.put(key,result);show();
                });
            }catch(Exception failure){runOnUiThread(()->{if(current(request)){cancelPending();showFailure("This road could not be loaded. Tap to retry.");}});}
        });
    }
    private boolean current(int request){return request==generation&&!isFinishing()&&!isDestroyed();}
    private void deadline(int request,Runnable failure){timeout=()->{if(!current(request)||!loading)return;generation++;cancelPending();failure.run();};handler.postDelayed(timeout,LOAD_TIMEOUT_MS);}
    private void cancelPending(){loading=false;if(timeout!=null)handler.removeCallbacks(timeout);if(pending!=null){pending.cancel(true);pending=null;}}
    private void showFailure(String message){
        coverageSummary.setVisibility(View.GONE);status.setVisibility(View.VISIBLE);status.setText(message);status.setOnClickListener(v->load());skeleton.setVisibility(View.GONE);loadingPanel.setVisibility(View.VISIBLE);
        ((View)loadingPanel.findViewWithTag("road_loading_spinner")).setVisibility(View.GONE);loadingLabel.setText(message);retry.setVisibility(View.VISIBLE);
        journeyAction.setEnabled(false);
    }
    private void showStatistics(){
        skeleton.setVisibility(View.GONE);coverageSummary.setVisibility(View.GONE);status.setVisibility(View.VISIBLE);
        stats.setVisibility(cumulative||model.fullReference?View.VISIBLE:View.GONE);
        recordedLabel.setVisibility(!cumulative&&!model.fullReference?View.VISIBLE:View.GONE);
        legend.setText(model.ncn?"Purple: completed · Solid orange: remaining · "+NcnProgressUi.label(selection.ncnMode):cumulative?"":model.fullReference?"Red: remaining · "+(RoadDetailSelection.MOTORWAY.equals(selection.kind)?"Blue":"Green")+": travelled":"");
        if(cumulative){status.setText(model.journeys.size()+" contributing journeys · newest first");progress.setVisibility(View.GONE);return;}
        if(model.fullReference){
            status.setVisibility(View.GONE);coverageSummary.setVisibility(View.VISIBLE);progress.setVisibility(View.VISIBLE);
            coverageSummary.bind(selection.title(),model.uniqueMetres,model.referenceMetres,model.ncn?"Verified-open route":"Estimated unique road coverage");
        }else if(model.ncn){stats.setVisibility(View.VISIBLE);recordedLabel.setVisibility(View.GONE);status.setText("PRESERVED ALTERNATIVE INVENTORY\nOpen-target assessment held; no completion percentage");progress.setVisibility(View.GONE);}
        else{status.setText("RECORDED SECTIONS\nFull-road distance and completion unavailable");progress.setVisibility(View.GONE);}
    }
    private void showGeometry(){
        showStatistics();loadingPanel.setVisibility(View.GONE);zoom.setVisibility(View.VISIBLE);map.setOverviewLoading(false);
        if(model.ncn)map.setNcnCoverageSegments(model.remaining,model.covered);
        else if(model.fullReference&&RoadDetailSelection.MOTORWAY.equals(selection.kind))map.setMotorwayCoverageSegments(model.remaining,model.covered);
        else if(model.fullReference)map.setARoadCoverageSegments(model.remaining,model.covered);
        else map.setRouteData(null,model.covered);
        map.setContentDescription(model.ncn?"NCN map. Purple completed, solid orange remaining. Tap a section for eligible journeys. Pinch or use zoom buttons.":"Map of "+selection.title()+(model.fullReference?". Red: remaining. "+(RoadDetailSelection.MOTORWAY.equals(selection.kind)?"Blue":"Green")+": travelled. Tap a section for journeys.":". Recorded sections. Use View Journeys for contributing journeys.")+" Pinch or use zoom buttons.");
        java.util.List<org.json.JSONArray> fit=new java.util.ArrayList<>(model.covered);fit.addAll(model.remaining);
        map.fitRoadGeometry(fit);
        geometryShown=true;navigation.frame(map,"first_map","kind="+selection.kind+" sections="+(model.covered.size()+model.remaining.size()));
        if(model.covered.isEmpty()&&model.remaining.isEmpty()){sectionStatus.setText("No retained mapped sections are available for this road.");sectionStatus.setVisibility(View.VISIBLE);}
    }
    private void show(){
        status.setOnClickListener(null);
        if(cumulative){showStatistics();body.removeAllViews();body.addView(journeyList(model.journeys),new FrameLayout.LayoutParams(-1,-1));journeyAction.setVisibility(View.GONE);return;}
        if(!geometryShown)showGeometry();else showStatistics();loadingPanel.setVisibility(View.GONE);
        navigation.frame(map,"ready","kind="+selection.kind+" contributors="+model.journeys.size());
        journeyAction.setText("VIEW JOURNEYS · "+model.journeys.size());journeyAction.setEnabled(true);map.setMapRoadTapListener(model.fullReference||model.ncn?this::inspectSection:null);
    }
    private void inspectSection(double latitude,double longitude){
        cancelPending();final int request=++generation;loading=true;
        final RoadDetailModel.Section section=model.sectionAt(latitude,longitude);
        if(model.ncn&&section!=null&&!section.travelled){loading=false;showJourneys(java.util.Collections.emptyList(),section);return;}
        sectionStatus.setText("SELECTED SECTION · Finding contributing journeys…");sectionStatus.setVisibility(View.VISIBLE);
        deadline(request,()->{sectionStatus.setText("SELECTED SECTION · Lookup took too long. Tap the section to retry.");});
        pending=ScreenDataLoader.interactive(()->{
            try{List<RoadDetailModel.Entry> related=model.journeysAt(getApplicationContext(),selection,model.ncn&&section!=null?section.latitude:latitude,model.ncn&&section!=null?section.longitude:longitude);
                runOnUiThread(()->{if(!current(request))return;cancelPending();sectionStatus.setVisibility(View.GONE);showJourneys(related,section);});
            }catch(Exception error){runOnUiThread(()->{if(current(request)){cancelPending();sectionStatus.setText("SELECTED SECTION · Journeys unavailable. Tap the section to retry.");}});}
        });
    }
    private void showJourneys(List<RoadDetailModel.Entry> entries,RoadDetailModel.Section section){
        String message=section==null?"WHOLE ROAD · "+entries.size()+" contributing journeys · newest first"
                :"SELECTED MAPPED SECTION · "+DistanceUnits.format(this,section.metres)+" · "+(section.travelled?"Travelled":"Remaining")
                +"\nTravel frequency: "+entries.size()+" saved journeys near this section. Newest first.";
        journeyDialog=RoadprintsDialog.show(this,selection.title(),message,journeyList(entries),"CLOSE",null,null,null);
    }
    private ScrollView journeyList(List<RoadDetailModel.Entry> entries){
        ScrollView scroll=new ScrollView(this);LinearLayout rows=new LinearLayout(this);rows.setOrientation(LinearLayout.VERTICAL);scroll.addView(rows);appendPage(rows,entries,0);return scroll;
    }
    private void appendPage(LinearLayout rows,List<RoadDetailModel.Entry> entries,int start){
        int end=Math.min(entries.size(),start+30);
        for(int i=start;i<end;i++){
            RoadDetailModel.Entry entry=entries.get(i);LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(16),dp(14),dp(16),dp(14));
            card.setBackground(JourneyCardHeader.background(this,0xFF233B78,0xFF46649E,18));card.setTag("road_journey_"+entry.id);
            JourneyCardHeader shared=new JourneyCardHeader(this,entry.summary);LinearLayout row=new LinearLayout(this);row.addView(shared.details);row.addView(shared.icon);card.addView(row);
            Runnable open=()->{if(journeyDialog!=null)journeyDialog.dismiss();if(!entry.id.isEmpty())startActivity(new Intent(this,JourneyMapEditorActivity.class).putExtra("journey_id",entry.id));};
            TextView view=action("VIEW JOURNEY",open);card.addView(view);card.setOnClickListener(v->open.run());
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(12);rows.addView(card,lp);
        }
        if(end<entries.size()){TextView more=action("SHOW MORE JOURNEYS",()->{});more.setOnClickListener(v->{rows.removeView(more);appendPage(rows,entries,end);});rows.addView(more);}
        if(entries.isEmpty())rows.addView(text("No contributing journeys remain.",14));
    }
    private TextView text(String value,int size){return JourneyCardHeader.text(this,value,size,Color.WHITE,false);}
    private TextView action(String label,Runnable click){TextView v=text(label,14);v.setPadding(0,dp(12),0,dp(12));v.setTextColor(0xFFF7C450);v.setMinHeight(dp(48));v.setFocusable(true);v.setOnClickListener(w->click.run());return v;}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    @Override protected void onPause(){generation++;cancelPending();if(journeyDialog!=null)journeyDialog.dismiss();super.onPause();}
    @Override protected void onDestroy(){generation++;cancelPending();handler.removeCallbacksAndMessages(null);super.onDestroy();}
}
