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

/** Service-station collection port; other demonstration collections stay out of the app. */
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
        summary=text("Collect the service stations you visit.",14,MUTED,false); summary.setPadding(0,dp(5),0,0);
        header.addView(eyebrow); header.addView(title); header.addView(summary); root.addView(header);
        ScrollView scroll=new ScrollView(this); scroll.setFillViewport(true);
        body=new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL); body.setPadding(dp(18),dp(4),dp(18),dp(18));
        scroll.addView(body); root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        View nav=bottomNavigation(); root.addView(GrowingStatusControl.create(this)); root.addView(nav); setContentView(root); applyInsets(root,header,nav); render();
    }

    private void render() {
        body.removeAllViews();
        LinearLayout card=panel();
        TextView name=text("⛽  Service stations",20,Color.WHITE,true);
        TextView copy=text("Collect motorway service areas across Great Britain and Northern Ireland. Visits can be confirmed from journeys or marked manually.",14,MUTED,false);
        copy.setPadding(0,dp(8),0,dp(14)); card.addView(name); card.addView(copy);
        if(!ServiceStationStore.unlocked(this)) {
            TextView fakePayment=text("Unlock · £0.99",16,NAVY,true); fakePayment.setGravity(Gravity.CENTER);
            fakePayment.setPadding(dp(12),dp(13),dp(12),dp(13)); fakePayment.setBackground(roundRect(GOLD,dp(12)));
            fakePayment.setClickable(true); fakePayment.setOnClickListener(v -> new AlertDialog.Builder(this)
                    .setTitle("Service stations")
                    .setMessage("This is a simulated checkout for testing. No payment will be taken.")
                    .setNegativeButton("Cancel",null)
                    .setPositiveButton("Continue",(dialog,which)->{
                        ServiceStationStore.unlockForTesting(this);
                        render();
                    }).show());
            card.addView(fakePayment);
            body.addView(card);
            TextView note=text("The service-station collection is the only collection available in this build.",13,MUTED,false);
            note.setPadding(dp(4),dp(12),dp(4),0); body.addView(note);
            summary.setText("One collection available · test unlock");
            return;
        }
        body.addView(card);
        ServiceStationStore.historicalBackfillComplete(this); // Clears legacy route-proximity guesses once.
        summary.setText("Your service station collection is available from Progress.");
        TextView message=text("View the full motorway-by-motorway list, confirmed visits and collection progress on the Progress screen.",14,MUTED,false);
        message.setPadding(dp(4),dp(8),dp(4),dp(12));body.addView(message);
        TextView openProgress=text("View service stations in Progress  →",15,NAVY,true);
        openProgress.setGravity(Gravity.CENTER);
        openProgress.setPadding(dp(12),dp(13),dp(12),dp(13));
        openProgress.setBackground(roundRect(GOLD,dp(12)));
        openProgress.setOnClickListener(v->{startActivity(new Intent(this,ProgressActivity.class));finish();});
        body.addView(openProgress);
    }

    private void addRegion(JSONArray all,String region,Set<String> complete,Set<String> automatic) {
        List<JSONObject> values=new ArrayList<>();
        for(int i=0;i<all.length();i++){JSONObject item=all.optJSONObject(i);if(item!=null&&region.equals(item.optString("region","GB")))values.add(item);}
        values.sort(Comparator.comparing((JSONObject o)->o.optString("road"),this::naturalCompare)
                .thenComparing(o->o.optString("name"),String.CASE_INSENSITIVE_ORDER));
        if(values.isEmpty()) {
            TextView empty=text(region.equals("NI")?"No Northern Ireland service areas are listed yet.":"No service areas listed.",13,MUTED,false);
            empty.setPadding(dp(5),dp(6),dp(5),dp(8)); body.addView(empty); return;
        }
        String previous="";
        for(JSONObject station:values) {
            String road=station.optString("road","Unknown road");
            if(!road.equals(previous)){TextView roadTitle=text(road,16,TEAL,true);roadTitle.setPadding(dp(5),dp(13),dp(5),dp(3));body.addView(roadTitle);previous=road;}
            String id=station.optString("id"), stationName=station.optString("name","Service area");
            CheckBox check=new CheckBox(this);check.setText(stationName+(automatic.contains(id)?" · Journey confirmed":""));
            check.setTextColor(Color.WHITE);check.setButtonTintList(android.content.res.ColorStateList.valueOf(complete.contains(id)?GOLD:MUTED));
            check.setChecked(complete.contains(id));check.setEnabled(!automatic.contains(id));
            check.setPadding(dp(5),dp(2),dp(5),dp(2));
            check.setOnCheckedChangeListener((button,isChecked)->{
                ServiceStationStore.setManual(this,id,isChecked);
                render();
            });body.addView(check);
        }
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
            ImageView icon=new ImageView(this);icon.setImageResource(icons[index]);icon.setColorFilter(index==4?GOLD:MUTED,android.graphics.PorterDuff.Mode.SRC_IN);item.addView(icon,new LinearLayout.LayoutParams(-1,dp(32)));
            TextView label=text(labels[index],10,index==4?GOLD:MUTED,false);label.setGravity(Gravity.CENTER);label.setMaxLines(1);item.addView(label,new LinearLayout.LayoutParams(-1,dp(24)));
            if(index!=4)item.setOnClickListener(v->navigate(target));nav.addView(item,new LinearLayout.LayoutParams(0,dp(68),1));
        }return nav;
    }
    private void navigate(int i){Class<?> target=i==0?MapActivity.class:i==1?JourneyListActivity.class:i==2?ProgressActivity.class:AchievementsActivity.class;startActivity(new Intent(this,target));finish();}
    private void applyInsets(View root,View header,View nav){root.setOnApplyWindowInsetsListener((view,insets)->{int top,bottom;if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.R){android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars());top=bars.top;bottom=bars.bottom;}else{top=insets.getSystemWindowInsetTop();bottom=insets.getSystemWindowInsetBottom();}header.setPadding(dp(18),dp(17)+top,dp(18),dp(13));LinearLayout.LayoutParams p=(LinearLayout.LayoutParams)nav.getLayoutParams();p.height=dp(68)+bottom;nav.setPadding(dp(8),0,dp(8),bottom);nav.setLayoutParams(p);return insets;});root.requestApplyInsets();}
    private int dp(float value){return Math.round(value*getResources().getDisplayMetrics().density);}
}

