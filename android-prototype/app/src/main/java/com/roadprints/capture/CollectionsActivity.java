package com.roadprints.capture;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Collection installation and purchase-free beta access. */
public class CollectionsActivity extends Activity {
    private static final int NAVY=0xFF0B1C50, NAV_BAR=0xFF10275D, PANEL=0xFF182F62;
    private static final int CARD=0xFF233B78, MUTED=0xFFB9C5D8, GOLD=0xFFF7C450, TEAL=0xFF67D5CC;
    private LinearLayout body;
    private TextView summary;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(NAVY); getWindow().setNavigationBarColor(NAV_BAR);
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(NAVY);
        LinearLayout header=new LinearLayout(this); header.setOrientation(LinearLayout.VERTICAL); header.setPadding(dp(18),dp(17),dp(18),dp(13));
        LinearLayout brand=RoadprintsHeader.create(this); brand.setPadding(0,0,0,dp(20)); header.addView(brand);
        TextView eyebrow=text("YOUR TRAVEL RECORD",12,TEAL,true), title=text("Collections",28,Color.WHITE,true);
        summary=text("Collect the motorway service stations you visit.",14,MUTED,false); summary.setPadding(0,dp(5),0,0);
        header.addView(eyebrow); header.addView(title); header.addView(summary); root.addView(header);
        ScrollView scroll=new ScrollView(this); scroll.setFillViewport(true);
        body=new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL); body.setPadding(dp(18),dp(4),dp(18),dp(18));
        scroll.addView(body); root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        View nav=bottomNavigation(); root.addView(GrowingStatusControl.create(this)); root.addView(nav); setContentView(root); applyInsets(root,header,nav); render();
        for(VenueCollection.Definition collection:VenueCollection.all())VenueVisitStore.ensureHistory(this,collection.id,()->{if(!isFinishing()&&!isDestroyed())render();});
    }

    private void render() {
        body.removeAllViews();
        summary.setText("Choose the collections you want to explore. Beta access is free.");
        LinearLayout ncn=panel();ncn.addView(text("↗ National Cycle Network",20,Color.WHITE,true));ncn.addView(text("Verified-open sections across the UK. Walking, running and cycling only. One-time catalogue download about 136 MB; available offline afterwards.",14,MUTED,false));
        if(!NcnProgressStore.enabled(this))addAction(ncn,"Enable NCN collection",()->{NcnProgressStore.activate(this,true);render();});
        else {addAction(ncn,"Show on map",()->{CollectionAccessStore.select(this,CollectionAccessStore.NCN);startActivity(new Intent(this,MapActivity.class).putExtra("collection_id",CollectionAccessStore.NCN));});addAction(ncn,"View progress",()->startActivity(new Intent(this,ProgressActivity.class).putExtra("progress_section","ncn")));addAction(ncn,"Remove collection",()->{NcnProgressStore.activate(this,false);render();});}body.addView(ncn);
        for(VenueCollection.Definition collection:VenueCollection.all())addCollection(collection.id,collection.description,collection.siteStop?R.drawable.ic_roadprints_fuel:R.drawable.ic_nav_collections);
    }

    private void addCollection(String id,String copy,int drawable) {
        LinearLayout card=panel();
        LinearLayout heading=new LinearLayout(this);heading.setGravity(Gravity.CENTER_VERTICAL);
        ImageView icon=RoadprintsIcons.image(this,drawable,NAVY,null);
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        icon.setPadding(dp(6),dp(6),dp(6),dp(6));icon.setBackground(roundRect(TEAL,dp(10)));
        LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(dp(40),dp(40));ip.rightMargin=dp(12);
        heading.addView(icon,ip);heading.addView(text(CollectionAccessStore.name(id),20,Color.WHITE,true),new LinearLayout.LayoutParams(0,-2,1));
        card.addView(heading);
        TextView description=text(copy,14,MUTED,false);description.setPadding(0,dp(10),0,dp(12));card.addView(description);
        boolean installed=CollectionAccessStore.installed(this,id);
        if(!installed) {
            addAction(card,"Install for beta testing",()->{
                CollectionAccessStore.installForTesting(this,id);render();
                VenueVisitStore.ensureHistory(this,id,()->{
                    if(!isFinishing()&&!isDestroyed())render();
                });
                if(CollectionAccessStore.SERVICES.equals(id)){VenueVisitStore.ensureHistory(this,id,null);ServiceStationStore.ensureHistoricalVisits(this,()->{
                    if(!isFinishing()&&!isDestroyed())render();
                });}
            });
        } else {
            TextView status=text("Installed · beta access",13,TEAL,true);status.setPadding(0,0,0,dp(12));card.addView(status);
            addAction(card,"Show on map",()->{
                CollectionAccessStore.select(this,id);
                startActivity(new Intent(this,MapActivity.class).putExtra("collection_id",id));finish();
            });
            if(CollectionAccessStore.SERVICES.equals(id)) {
                if(!ServiceStationStore.historicalBackfillComplete(this))ServiceStationStore.ensureHistoricalVisits(this,()->{
                    if(!isFinishing()&&!isDestroyed())render();
                });
                addAction(card,"View progress",()->{startActivity(new Intent(this,ProgressActivity.class).putExtra("progress_section","services"));finish();});
            }
            if(!CollectionAccessStore.SERVICES.equals(id))
                addAction(card,"View progress",()->{startActivity(new Intent(this,ProgressActivity.class).putExtra("progress_section",id));finish();});
            TextView remove=text("Remove collection",14,0xFFFF8A8A,false);remove.setGravity(Gravity.CENTER);remove.setPadding(dp(12),dp(14),dp(12),dp(8));
            remove.setOnClickListener(v->RoadprintsDialog.show(this,"Remove "+CollectionAccessStore.name(id)+"?",
                    "The collection will be hidden from your map and progress. Your existing visits will be kept.",null,
                    "Remove collection",()->{
                        CollectionAccessStore.remove(this,id);render();
                    },"Cancel",null));card.addView(remove);
        }
        body.addView(card);
    }
    private void addAction(LinearLayout parent,String label,Runnable action) {
        TextView button=text(label,15,NAVY,true);button.setGravity(Gravity.CENTER);
        button.setPadding(dp(12),dp(13),dp(12),dp(13));button.setBackground(roundRect(GOLD,dp(12)));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(8);parent.addView(button,p);
        button.setOnClickListener(v->action.run());
    }

    private int naturalCompare(String a,String b) {
        return a.toLowerCase(Locale.ROOT).compareTo(b.toLowerCase(Locale.ROOT));
    }
    private void addGroupLabel(String label) { TextView view=text(label,12,GOLD,true);view.setPadding(dp(4),dp(20),dp(4),dp(5));body.addView(view); }
    private LinearLayout panel(){LinearLayout result=new LinearLayout(this);result.setOrientation(LinearLayout.VERTICAL);result.setPadding(dp(16),dp(15),dp(16),dp(15));result.setBackground(roundRect(PANEL,dp(17)));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(8);result.setLayoutParams(p);return result;}
    private TextView text(String value,float size,int color,boolean bold){TextView v=new TextView(this);v.setText(value);v.setTextSize(size);v.setTextColor(color);if(bold)v.setTypeface(null,Typeface.BOLD);return v;}
    private GradientDrawable roundRect(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(radius);return d;}

    private View bottomNavigation() {
        LinearLayout nav=new LinearLayout(this);nav.setGravity(Gravity.TOP|Gravity.CENTER_HORIZONTAL);nav.setPadding(dp(8),0,dp(8),0);nav.setBackgroundColor(NAV_BAR);
        int[] icons={R.drawable.ic_nav_map,R.drawable.ic_nav_journeys,R.drawable.ic_nav_progress,R.drawable.ic_nav_achievements,R.drawable.ic_nav_collections};
        String[] labels={"Map","Journeys","Progress","Achievements","Collections"};
        for(int index=0;index<labels.length;index++){
            final int target=index;LinearLayout item=new LinearLayout(this);item.setOrientation(LinearLayout.VERTICAL);item.setGravity(Gravity.TOP|Gravity.CENTER_HORIZONTAL);item.setPadding(0,dp(4),0,0);
            ImageView icon=new ImageView(this);icon.setImageResource(icons[index]);icon.setScaleType(ImageView.ScaleType.FIT_CENTER);icon.setColorFilter(index==4?GOLD:MUTED,android.graphics.PorterDuff.Mode.SRC_IN);item.addView(icon,new LinearLayout.LayoutParams(-1,dp(32)));
            TextView label=text(labels[index],10,index==4?GOLD:MUTED,false);label.setGravity(Gravity.CENTER);label.setMaxLines(1);item.addView(label,new LinearLayout.LayoutParams(-1,dp(24)));
            if(index!=4)item.setOnClickListener(v->navigate(target));nav.addView(item,new LinearLayout.LayoutParams(0,dp(68),1));
        }return nav;
    }
    private void navigate(int i){Class<?> target=i==0?MapActivity.class:i==1?JourneyListActivity.class:i==2?ProgressActivity.class:AchievementsActivity.class;startActivity(new Intent(this,target));finish();}
    private void applyInsets(View root,View header,View nav){root.setOnApplyWindowInsetsListener((view,insets)->{int top,bottom;if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.R){android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars());top=bars.top;bottom=bars.bottom;}else{top=insets.getSystemWindowInsetTop();bottom=insets.getSystemWindowInsetBottom();}header.setPadding(dp(18),dp(17)+top,dp(18),dp(13));LinearLayout.LayoutParams p=(LinearLayout.LayoutParams)nav.getLayoutParams();p.height=dp(68)+bottom;nav.setPadding(dp(8),0,dp(8),bottom);nav.setLayoutParams(p);return insets;});root.requestApplyInsets();}
    private int dp(float value){return Math.round(value*getResources().getDisplayMetrics().density);}
}




