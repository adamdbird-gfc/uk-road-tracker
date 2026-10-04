package com.roadprints.capture;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Timeline import and saved journey deletion tools. */
public class DataManagementActivity extends Activity {
    private static final int NAVY=0xFF0B1C50,CARD=0xFF233B78,TEAL=0xFF67D5CC,MUTED=0xFFD3DCED;
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private TextView status;
    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(24),dp(24),dp(24),dp(24));root.setBackgroundColor(NAVY);
        LinearLayout brand=RoadprintsHeader.create(this);brand.setPadding(0,0,0,dp(22));root.addView(brand);
        root.addView(text("UTILITIES",13,TEAL,true));root.addView(text("Data management",32,Color.WHITE,true));
        TextView intro=text("Import Timeline data again or manage journeys saved on this device.",15,MUTED,false);intro.setPadding(0,dp(8),0,dp(20));root.addView(intro);
        TextView importHeading=text("Import data",19,Color.WHITE,true);importHeading.setPadding(0,dp(12),0,dp(8));root.addView(importHeading);
        addAction(root,"LOAD TIMELINE DATA","Import journeys from a Timeline export",false,()->startActivity(new Intent(this,TimelineImportActivity.class)));
        addAction(root,"ADD SERVICE STATION VISITS","Import Timeline data for service-station visits",false,()->{Intent i=new Intent(this,TimelineImportActivity.class);i.putExtra("service_only",true);startActivity(i);});
        TextView deleteHeading=text("Delete saved journeys",19,Color.WHITE,true);deleteHeading.setPadding(0,dp(16),0,dp(8));root.addView(deleteHeading);
        addAction(root,"DELETE ROAD DATA","Driving, bus and cycling journeys",true,()->confirmDelete("Delete road data?","This removes driving, bus and cycling journeys from this device.",new String[]{"driving","bus","cycling"}));
        addAction(root,"DELETE ON-FOOT DATA","Walking journeys",true,()->confirmDelete("Delete on-foot data?","This removes walking journeys from this device.",new String[]{"walking"}));
        addAction(root,"DELETE ALL SAVED DATA","Remove every saved journey",true,this::confirmDeleteAll);
        status=text("",14,TEAL,false);status.setPadding(0,dp(14),0,dp(8));root.addView(status);
        RoadprintsHeader.installUtilityPage(this,root,"BACK TO UTILITIES",this::finish);
    }
    private void addAction(LinearLayout root,String label,String detail,boolean destructive,Runnable action){
        Button b=button(label,destructive);
        b.setGravity(Gravity.CENTER_VERTICAL|Gravity.START);
        b.setTextAlignment(android.view.View.TEXT_ALIGNMENT_VIEW_START);
        b.setPadding(dp(18),0,dp(18),0);
        b.setMinHeight(dp(58));
        b.setOnClickListener(v->action.run());
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(4);root.addView(b,p);
        TextView d=text(detail,13,MUTED,false);d.setPadding(dp(18),0,dp(8),dp(12));root.addView(d);
    }
    private void confirmDelete(String title,String message,String[] modes){new AlertDialog.Builder(this).setTitle(title).setMessage(message).setNegativeButton("Cancel",null).setPositiveButton("Delete",(d,w)->{status.setText("Deleting journeys…");io.execute(()->{int deleted;try{deleted=JourneyStore.deleteByModes(getApplicationContext(),modes);}catch(Exception e){main.post(()->status.setText("Could not delete journeys."));return;}int count=deleted;main.post(()->status.setText(count+" journey"+(count==1?"":"s")+" deleted from this device."));});}).show();}
    private void confirmDeleteAll(){new AlertDialog.Builder(this).setTitle("Delete all saved data?").setMessage("This removes every saved journey and returns you to the Roadprints welcome screen.").setNegativeButton("Cancel",null).setPositiveButton("Delete all",(d,w)->{status.setText("Deleting saved journeys…");io.execute(()->{try{JourneyStore.deleteAll(getApplicationContext());}catch(Exception e){main.post(()->status.setText("Could not delete saved journeys."));return;}main.post(()->{getSharedPreferences("roadprints_onboarding",MODE_PRIVATE).edit().remove("complete").apply();startActivity(new Intent(this,OnboardingActivity.class));finish();});});}).show();}
    @Override protected void onDestroy(){io.shutdownNow();super.onDestroy();}
    private Button button(String s,boolean destructive){Button b=new Button(this);b.setText(s);b.setTextColor(Color.WHITE);b.setTypeface(null,android.graphics.Typeface.BOLD);b.setBackground(rounded(destructive?0xFF8D3047:0xFF35558F,destructive?0xFFC45B70:0xFF496096));return b;}
    private android.graphics.drawable.GradientDrawable rounded(int c,int stroke){android.graphics.drawable.GradientDrawable d=new android.graphics.drawable.GradientDrawable();d.setColor(c);d.setCornerRadius(dp(14));d.setStroke(dp(1),stroke);return d;}
    private TextView text(String s,float z,int c,boolean b){TextView v=new TextView(this);v.setText(s);v.setTextSize(z);v.setTextColor(c);if(b)v.setTypeface(null,android.graphics.Typeface.BOLD);return v;}
    private int dp(int x){return Math.round(x*getResources().getDisplayMetrics().density);}
}
