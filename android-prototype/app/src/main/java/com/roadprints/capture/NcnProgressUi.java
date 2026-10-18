package com.roadprints.capture;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Internal progress surface, deliberately separate from purchasable venue collections. */
final class NcnProgressUi {
    static void add(Activity activity,LinearLayout parent) {
        if(!NcnProgressStore.enabled(activity))return;
        LinearLayout panel=new LinearLayout(activity);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(dp(activity,18),dp(activity,18),dp(activity,18),dp(activity,18));panel.setTag("progress:ncn_trial");
        parent.addView(panel);NcnProgressStore.ensure(activity);
        final Object[] rendered={null};final String[] message={null};
        Runnable update=new Runnable(){public void run(){
            if(activity.isFinishing()||activity.isDestroyed()||!panel.isAttachedToWindow())return;
            NcnProgressStore.Snapshot snapshot=NcnProgressStore.current();String status=NcnProgressStore.status();
            if(snapshot!=rendered[0]||!status.equals(message[0])){rendered[0]=snapshot;message[0]=status;populate(activity,panel,snapshot,status);}
            panel.postDelayed(this,1000);
        }};
        populate(activity,panel,NcnProgressStore.current(),NcnProgressStore.status());panel.post(update);
    }
    private static void populate(Activity activity,LinearLayout panel,NcnProgressStore.Snapshot snapshot,String status) {
        panel.removeAllViews();panel.addView(text(activity,"National Cycle Network · internal trial",20,Color.WHITE));
        panel.addView(text(activity,"Eight captured source inventories · full published-route scope is still under review.",13,0xFFB9C5D8));
        panel.addView(text(activity,status,13,0xFF67D5CC));
        if(snapshot==null)return;
        try {
            double foot=0,cycle=0,both=0;
            for(String route:snapshot.catalogue.routes.keySet()){foot+=snapshot.ledger.metres(route,"on_foot");cycle+=snapshot.ledger.metres(route,"cycling");both+=snapshot.ledger.metres(route,"combined");}
            panel.addView(text(activity,"Unique on foot: "+DistanceUnits.format(activity,foot),16,Color.WHITE));
            panel.addView(text(activity,"Unique cycling: "+DistanceUnits.format(activity,cycle),16,Color.WHITE));
            panel.addView(text(activity,"Unique network in either mode: "+DistanceUnits.format(activity,both),16,Color.WHITE));
            for(String route:snapshot.catalogue.routes.keySet()) {
                double length=snapshot.catalogue.routes.get(route).getDouble("length_m"),covered=snapshot.ledger.metres(route,"combined");
                LinearLayout row=new LinearLayout(activity);row.setOrientation(LinearLayout.VERTICAL);row.setPadding(dp(activity,12),dp(activity,18),dp(activity,12),dp(activity,18));
                TextView badge=text(activity,route.replace("NCN:","Route ")+String.format(java.util.Locale.UK," · %.1f%%",100*covered/length),17,Color.WHITE);
                badge.setBackgroundColor(0xFFBF2834);badge.setPadding(dp(activity,12),dp(activity,8),dp(activity,12),dp(activity,8));row.addView(badge);
                TextView detail=text(activity,DistanceUnits.format(activity,covered)+" of "+DistanceUnits.format(activity,length)+" · view map",14,Color.WHITE);row.addView(detail);
                LinearLayout bar=new LinearLayout(activity);bar.setBackgroundColor(0xFFBF2834);
                android.view.View fill=new android.view.View(activity);fill.setBackgroundColor(Color.WHITE);float fraction=(float)Math.max(0,Math.min(1,covered/length));
                bar.addView(fill,new LinearLayout.LayoutParams(0,dp(activity,6),Math.max(.0001f,fraction)));bar.addView(new android.view.View(activity),new LinearLayout.LayoutParams(0,dp(activity,6),Math.max(.0001f,1-fraction)));row.addView(bar);
                row.setClickable(true);row.setFocusable(true);row.setMinimumHeight(dp(activity,48));row.setContentDescription(route+": view NCN map");
                row.setOnClickListener(v->activity.startActivity(new RoadDetailSelection(RoadDetailSelection.NCN,route,"","","").intent(activity,false)));panel.addView(row);
            }
            panel.addView(text(activity,snapshot.catalogue.attribution,11,0xFFB9C5D8));
            panel.addView(text(activity,"Progress uses supported saved source evidence. Older unsupported matches may require rematching; history never creates confirmation prompts.",12,0xFFB9C5D8));
        } catch(Exception invalid){panel.addView(text(activity,"NCN progress could not be displayed.",14,Color.WHITE));}
    }
    private static int dp(Activity a,int n){return Math.round(n*a.getResources().getDisplayMetrics().density);}
    private static TextView text(Activity a,String value,int size,int color){TextView v=new TextView(a);v.setText(value);v.setTextSize(size);v.setTextColor(color);v.setPadding(0,dp(a,8),0,dp(a,8));return v;}
}
