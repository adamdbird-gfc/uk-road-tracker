package com.roadprints.capture;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.content.Intent;
import android.content.Context;
import android.net.Uri;
import android.view.Gravity;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.io.OutputStream;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/** Local-only diagnostic report review and copy screen. */
public class DebugActivity extends Activity {
    private static final int SAVE_MOVEMENT_LOG = 4101;
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
                new RoadprintsDialog.Builder(this).setTitle("Automatic tracking is off")
                        .setMessage("Turn on automatic tracking first, then start the movement log here.")
                        .setPositiveButton("OK",null).show();
            }else{
                new RoadprintsDialog.Builder(this).setTitle("Record movement diagnostics?")
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
        if(MovementDiagnostics.hasReport(this)){
            root.addView(action("DOWNLOAD MOVEMENT LOG",0xFF29437F,Color.WHITE,()->{
                downloadMovementLog();
            }));
            root.addView(action("COPY MOVEMENT LOG",0xFF29437F,Color.WHITE,()->{
                String latestReport=MovementDiagnostics.getClipboardReport(this);
                if(latestReport==null || !copyReportToClipboard(this,"Roadprints movement diagnostics",latestReport)){
                    new RoadprintsDialog.Builder(this).setTitle("Download the movement log")
                            .setMessage("This log could not be copied safely. Download the complete file to share it.")
                            .setNegativeButton("Cancel",null)
                            .setPositiveButton("DOWNLOAD LOG",(dialog,which)->downloadMovementLog()).show();
                }else Toast.makeText(this,"Movement log copied. It contains precise location data.",Toast.LENGTH_LONG).show();
            }));
        }
        root.addView(action("RAIL STATION DATA",0xFF172D5B,MUTED,()->{
            android.widget.ScrollView scroll=new android.widget.ScrollView(this);
            TextView attribution=text("Rail station data: davwheat / UK Railway Stations, derived from Trainline EU. Sources include OpenStreetMap, SNCF OpenData, GeoNames, Digitraffic.fi, OpenTransportData.swiss and admin.ch. Distributed under the Open Database License (ODbL). Station lookup runs locally; transport suggestions still require confirmation. This reference covers Great Britain National Rail stations; underground journeys are outside this first release.\n\nhttps://github.com/davwheat/uk-railway-stations\nhttps://github.com/trainline-eu/stations\nhttps://opendatacommons.org/licenses/odbl/1-0/",14,Color.WHITE,false);
            attribution.setPadding(dp(16),dp(12),dp(16),dp(12));
            android.text.util.Linkify.addLinks(attribution,android.text.util.Linkify.WEB_URLS);
            scroll.addView(attribution);
            new RoadprintsDialog.Builder(this).setTitle("Rail station data").setView(scroll)
                    .setPositiveButton("Close",null).show();
        }));
        if(MovementDiagnostics.hasReport(this)){
            root.addView(action("CLEAR MOVEMENT LOG",0xFF182B5A,0xFFFF8A8A,()->new RoadprintsDialog.Builder(this)
                    .setTitle("Clear movement log?")
                    .setMessage("The local movement log, including its GPS coordinates, will be deleted.")
                    .setNegativeButton("Cancel",null)
                    .setPositiveButton("Clear",(dialog,which)->{
                        MovementDiagnostics.stop(this,"Cleared by user.");
                        Intent changed=new Intent(this,CaptureService.class);
                        changed.setAction(CaptureService.ACTION_DIAGNOSTICS_CHANGED);
                        startService(changed);
                        MovementDiagnostics.clear(this);
                        recreate();
                    })
                    .show()));
        }
        RoadprintsHeader.installUtilityPage(this,root,"BACK TO UTILITIES",this::finish);
        if(available)RoadprintsDialog.show(this,"Debug report ready",
                "The report is stored on this device. Copy it only when you want to share it.",null,
                "COPY REPORT",()->{
                    boolean copied=copyReportToClipboard(this,"Roadprints debug reports",report);
                    Toast.makeText(this,copied?"Debug report copied":"Report could not be copied. Please try again.",Toast.LENGTH_LONG).show();
                },"CLEAR REPORTS",()->{CrashReporter.clear(this);finish();});
    }
    private void downloadMovementLog(){
        Intent save=new Intent(Intent.ACTION_CREATE_DOCUMENT);
        save.addCategory(Intent.CATEGORY_OPENABLE);
        save.setType("text/plain");
        String stamp=DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
                .withZone(ZoneOffset.UTC).format(Instant.now());
        save.putExtra(Intent.EXTRA_TITLE,"roadprints-movement-"+stamp+".txt");
        startActivityForResult(save,SAVE_MOVEMENT_LOG);
    }
    static boolean copyReportToClipboard(Context context,String label,String report){
        if(report==null || report.isEmpty() || report.length()>MovementDiagnostics.MAX_CLIPBOARD_CHARS)return false;
        try{
            ClipboardManager clipboard=(ClipboardManager)context.getSystemService(CLIPBOARD_SERVICE);
            if(clipboard==null)return false;
            clipboard.setPrimaryClip(ClipData.newPlainText(label,report));
            return true;
        }catch(RuntimeException unavailable){
            return false;
        }
    }
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode!=SAVE_MOVEMENT_LOG || resultCode!=RESULT_OK || data==null)return;
        Uri uri=data.getData();
        if(uri==null)return;
        Context app=getApplicationContext();
        Toast.makeText(app,"Saving movement log…",Toast.LENGTH_SHORT).show();
        new Thread(()->{
            String message;
            try(OutputStream output=app.getContentResolver().openOutputStream(uri,"wt")){
                if(output==null)throw new IOException("Could not open the selected file.");
                MovementDiagnostics.writeReport(app,output);
                message="Complete movement log saved.";
            }catch(Exception error){
                message="Movement log could not be saved. Please try again.";
            }
            final String result=message;
            new android.os.Handler(android.os.Looper.getMainLooper()).post(()->
                    Toast.makeText(app,result,Toast.LENGTH_LONG).show());
        },"movement-log-export").start();
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
