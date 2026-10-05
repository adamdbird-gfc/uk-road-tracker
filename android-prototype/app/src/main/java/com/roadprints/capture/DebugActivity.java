package com.roadprints.capture;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.content.Intent;
import android.view.Gravity;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/** Local-only diagnostic report review and copy screen. */
public class DebugActivity extends Activity {
    private static final int NAVY=0xFF0B1C50,MUTED=0xFFD3DCED;
    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        String report=CrashReporter.getDiagnosticReports(this);CrashReporter.markDiagnosticReportsViewed(this);boolean available=!report.isEmpty();
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(24),dp(24),dp(24),dp(28));root.setBackgroundColor(NAVY);
        LinearLayout brand=RoadprintsHeader.create(this);brand.setPadding(0,0,0,dp(22));root.addView(brand);root.addView(text("UTILITIES",13,0xFF67D5CC,true));root.addView(text("Debug",32,Color.WHITE,true));
        TextView info=text(available?"Review the report before copying. It stays on this device until you choose to copy it.":"No reports have been saved on this device yet. Reports stay on this device and are not sent automatically.",15,MUTED,false);info.setPadding(0,dp(12),0,dp(12));root.addView(info);
        TextView details=text(available?report:"If a journey match fails or Roadprints crashes, diagnostic details will appear here.",13,MUTED,false);details.setTextIsSelectable(true);root.addView(details);
        root.addView(text("Movement diagnostics",21,Color.WHITE,true));
        TextView movementStatus=text(MovementDiagnostics.status(this),14,MUTED,false);
        movementStatus.setPadding(0,dp(8),0,dp(6));
        root.addView(movementStatus);
        TextView privacy=text("Optional 24-hour diagnostic: precise GPS samples and Android activity changes stay on this device. Nothing is sent automatically. The log is removed after 7 days.",13,MUTED,false);
        privacy.setPadding(0,0,0,dp(12));
        root.addView(privacy);
        boolean collecting=MovementDiagnostics.isRunning(this);
        root.addView(action(collecting?"STOP MOVEMENT LOG":"START 24-HOUR MOVEMENT LOG",
                collecting?0xFFF9C74F:0xFF29437F,collecting?NAVY:Color.WHITE,()->{
            if(collecting){
                MovementDiagnostics.stop(this,"Stopped by user.");
                Intent changed=new Intent(this,CaptureService.class);
                changed.setAction(CaptureService.ACTION_DIAGNOSTICS_CHANGED);
                startService(changed);
                recreate();
            }else if(!CaptureService.isArmed(this)){
                new AlertDialog.Builder(this).setTitle("Automatic tracking is off")
                        .setMessage("Turn on automatic tracking first, then start the movement log here.")
                        .setPositiveButton("OK",null).show();
            }else{
                new AlertDialog.Builder(this).setTitle("Record movement diagnostics?")
                        .setMessage("For up to 24 hours, Roadprints will save Android activity changes and precise GPS samples about every 30 seconds while it waits for movement, plus samples while confirming or recording a journey. This log stays on this phone, is never uploaded automatically, and is removed after 7 days. Starting a new log replaces any previous movement log. You can stop it at any time and copy it from this screen.")
                        .setNegativeButton("Cancel",null)
                        .setPositiveButton("Start log",(dialog,which)->{
                            MovementDiagnostics.start(this);
                            Intent changed=new Intent(this,CaptureService.class);
                            changed.setAction(CaptureService.ACTION_DIAGNOSTICS_CHANGED);
                            startService(changed);
                            recreate();
                        }).show();
            }
        }));
        String movementReport=MovementDiagnostics.getReport(this);
        if(!movementReport.isEmpty()){
            root.addView(action("COPY MOVEMENT LOG",0xFF29437F,Color.WHITE,()->{
                String latestReport=MovementDiagnostics.getReport(this);
                ClipboardManager cb=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
                cb.setPrimaryClip(ClipData.newPlainText("Roadprints movement diagnostics",latestReport));
                Toast.makeText(this,"Movement log copied. It contains precise location data.",Toast.LENGTH_LONG).show();
            }));
            root.addView(action("CLEAR MOVEMENT LOG",0xFF182B5A,MUTED,()->new AlertDialog.Builder(this)
                    .setTitle("Clear movement log?")
                    .setMessage("The local movement log, including its GPS coordinates, will be deleted.")
                    .setNegativeButton("Cancel",null)
                    .setPositiveButton("Clear",(dialog,which)->{MovementDiagnostics.clear(this);recreate();})
                    .show()));
        }
        RoadprintsHeader.installUtilityPage(this,root,"BACK TO UTILITIES",this::finish);
        if(available)new AlertDialog.Builder(this).setTitle("Debug report ready").setMessage("The report is stored on this device. Copy it only when you want to share it.").setNegativeButton("Close",null).setPositiveButton("Copy report",(d,w)->{ClipboardManager cb=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);cb.setPrimaryClip(ClipData.newPlainText("Roadprints debug reports",report));Toast.makeText(this,"Debug report copied",Toast.LENGTH_SHORT).show();}).setNeutralButton("Clear reports",(d,w)->{CrashReporter.clear(this);finish();}).show();
    }
    private TextView action(String label,int background,int foreground,Runnable onClick){
        TextView view=text(label,15,foreground,true);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(14),dp(16),dp(14),dp(16));
        GradientDrawable shape=new GradientDrawable();
        shape.setColor(background);
        shape.setCornerRadius(dp(16));
        view.setBackground(shape);
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin=dp(8);
        params.bottomMargin=dp(4);
        view.setLayoutParams(params);
        view.setOnClickListener(v->onClick.run());
        return view;
    }
    private TextView text(String s,float z,int c,boolean b){TextView v=new TextView(this);v.setText(s);v.setTextSize(z);v.setTextColor(c);if(b)v.setTypeface(null,android.graphics.Typeface.BOLD);return v;}
    private int dp(int x){return Math.round(x*getResources().getDisplayMetrics().density);}
}
