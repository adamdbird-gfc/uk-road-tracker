package com.roadprints.capture;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Explains and opens Android's controls for location and notifications. */
public class PermissionsActivity extends Activity {
    private static final int NAVY=0xFF0B1C50, CARD=0xFF233B78, TEAL=0xFF67D5CC, MUTED=0xFFD3DCED;
    private TextView locationState, notificationState;
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24),dp(24),dp(24),dp(24)); root.setBackgroundColor(NAVY);
        LinearLayout brand=RoadprintsHeader.create(this);brand.setPadding(0,0,0,dp(22));root.addView(brand);
        root.addView(text("UTILITIES",13,TEAL,true));
        root.addView(text("Permissions",32,Color.WHITE,true));
        TextView intro=text("Roadprints needs location access to record journeys. Notifications show the active tracking service.",15,MUTED,false);
        intro.setPadding(0,dp(8),0,dp(20)); root.addView(intro);
        LinearLayout location=card();
        location.addView(text("Location",19,Color.WHITE,true));
        locationState=text("",15,TEAL,false); locationState.setPadding(0,dp(8),0,dp(12)); location.addView(locationState);
        Button locationAction=button("ENABLE LOCATION");
        locationAction.setOnClickListener(v -> {
            if (hasLocation()) openAppSettings();
            else requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},41);
        });
        location.addView(locationAction); root.addView(location,params());
        LinearLayout notifications=card();
        notifications.addView(text("Notifications",19,Color.WHITE,true));
        notificationState=text("",15,TEAL,false); notificationState.setPadding(0,dp(8),0,dp(12)); notifications.addView(notificationState);
        Button notificationAction=button("ENABLE NOTIFICATIONS");
        notificationAction.setOnClickListener(v -> {
            if (notificationsEnabled()) openNotificationSettings();
            else if (Build.VERSION.SDK_INT>=33) requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},42);
            else openNotificationSettings();
        });
        notifications.addView(notificationAction); root.addView(notifications,params());
        TextView note=text("Android controls when an app can grant or revoke permissions. Use the buttons above to open its system permission controls.",14,MUTED,false);
        note.setPadding(0,dp(8),0,dp(18)); root.addView(note);
        RoadprintsHeader.installUtilityPage(this,root,"BACK TO UTILITIES",this::finish);
        refreshStates();
    }
    @Override protected void onResume(){super.onResume(); if(locationState!=null)refreshStates();}
    private void refreshStates(){
        boolean loc=hasLocation(); locationState.setText(loc?"Location access is on":"Location access is off");
        notificationState.setText(notificationsEnabled()?"Notifications are on":"Notifications are off");
    }
    private boolean hasLocation(){return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED;}
    private boolean notificationsEnabled(){
        android.app.NotificationManager nm=(android.app.NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        boolean enabled=nm!=null && nm.areNotificationsEnabled();
        return enabled && (Build.VERSION.SDK_INT<33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED);
    }
    private void openAppSettings(){Intent i=new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName()));startActivity(i);}
    private void openNotificationSettings(){
        if(Build.VERSION.SDK_INT>=26){Intent i=new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName());startActivity(i);}
        else openAppSettings();
    }
    private LinearLayout card(){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(18),dp(16),dp(18),dp(16));c.setBackground(rounded(CARD));return c;}
    private Button button(String label){Button b=new Button(this);b.setText(label);b.setTextColor(Color.WHITE);b.setTypeface(null,android.graphics.Typeface.BOLD);b.setBackground(rounded(0xFF35558F));return b;}
    private LinearLayout.LayoutParams params(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(12);return p;}
    private android.graphics.drawable.GradientDrawable rounded(int c){android.graphics.drawable.GradientDrawable d=new android.graphics.drawable.GradientDrawable();d.setColor(c);d.setCornerRadius(dp(15));d.setStroke(dp(1),0xFF496096);return d;}
    private TextView text(String s,float z,int c,boolean b){TextView v=new TextView(this);v.setText(s);v.setTextSize(z);v.setTextColor(c);if(b)v.setTypeface(null,android.graphics.Typeface.BOLD);return v;}
    private int dp(int x){return Math.round(x*getResources().getDisplayMetrics().density);}
}
