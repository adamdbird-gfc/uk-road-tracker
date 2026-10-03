package com.roadprints.capture;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** Local-only diagnostic report review and copy screen. */
public class DebugActivity extends Activity {
    private static final int NAVY=0xFF0B1C50,MUTED=0xFFD3DCED;
    @Override protected void onCreate(Bundle state){
        super.onCreate(state);getWindow().setStatusBarColor(NAVY);getWindow().setNavigationBarColor(0xFF10275D);
        String report=CrashReporter.getDiagnosticReports(this);boolean available=!report.isEmpty();
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(24),dp(24),dp(24),dp(28));root.setBackgroundColor(NAVY);
        root.addView(RoadprintsHeader.create(this));root.addView(text("Debug",32,Color.WHITE,true));
        TextView info=text(available?"Review the report before copying. It stays on this device until you choose to copy it.":"No reports have been saved on this device yet. Reports stay on this device and are not sent automatically.",15,MUTED,false);info.setPadding(0,dp(12),0,dp(12));root.addView(info);
        TextView details=text(available?report:"If a journey match fails or Roadprints crashes, diagnostic details will appear here.",13,MUTED,false);details.setTextIsSelectable(true);root.addView(details);
        TextView back=text("BACK TO UTILITIES",14,0xFF67D5CC,true);back.setGravity(Gravity.CENTER);back.setOnClickListener(v->finish());root.addView(back,new LinearLayout.LayoutParams(-1,dp(54)));
        ScrollView scroll=new ScrollView(this);scroll.addView(root);setContentView(scroll);
        if(available)new AlertDialog.Builder(this).setTitle("Debug report ready").setMessage("The report is stored on this device. Copy it only when you want to share it.").setNegativeButton("Close",null).setPositiveButton("Copy report",(d,w)->{ClipboardManager cb=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);cb.setPrimaryClip(ClipData.newPlainText("Roadprints debug reports",report));Toast.makeText(this,"Debug report copied",Toast.LENGTH_SHORT).show();}).setNeutralButton("Clear reports",(d,w)->{CrashReporter.clear(this);finish();}).show();
    }
    private TextView text(String s,float z,int c,boolean b){TextView v=new TextView(this);v.setText(s);v.setTextSize(z);v.setTextColor(c);if(b)v.setTypeface(null,android.graphics.Typeface.BOLD);return v;}
    private int dp(int x){return Math.round(x*getResources().getDisplayMetrics().density);}
}
