package com.roadprints.capture;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Shared modal layout: fixed header close, scrollable body, spaced full-width actions. */
final class RoadprintsDialog {
    static AlertDialog show(Context context, String title, String message, View content,
                            String primaryLabel, Runnable primaryAction,
                            String secondaryLabel, Runnable secondaryAction) {
        LinearLayout panel=new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(context,20),dp(context,12),dp(context,20),dp(context,20));
        panel.setBackground(background(context,0xFF0B1C50,22,true));
        LinearLayout header=new LinearLayout(context);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView heading=new TextView(context);
        heading.setText(title);heading.setTextColor(Color.WHITE);heading.setTextSize(20);
        heading.setTypeface(null,Typeface.BOLD);heading.setPadding(0,dp(context,8),dp(context,8),dp(context,8));
        header.addView(heading,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        Button close=new Button(context);
        close.setText("CLOSE");close.setAllCaps(false);close.setTextSize(13);
        close.setTypeface(null,Typeface.BOLD);close.setTextColor(0xFFF7C450);
        close.setContentDescription("Close");close.setMinWidth(0);close.setMinimumWidth(0);
        close.setMinHeight(dp(context,48));close.setMinimumHeight(dp(context,48));
        close.setPadding(dp(context,8),0,dp(context,8),0);
        close.setBackground(background(context,Color.TRANSPARENT,12,false));
        header.addView(close,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT));
        panel.addView(header);
        ScrollView scroll=new ScrollView(context) {
            @Override protected void onMeasure(int widthSpec,int heightSpec) {
                int limit=(int)(getResources().getDisplayMetrics().heightPixels*.5);
                if(View.MeasureSpec.getMode(heightSpec)!=View.MeasureSpec.UNSPECIFIED)
                    limit=Math.min(limit,View.MeasureSpec.getSize(heightSpec));
                super.onMeasure(widthSpec,View.MeasureSpec.makeMeasureSpec(limit,View.MeasureSpec.AT_MOST));
            }
        };
        LinearLayout body=new LinearLayout(context);body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(0,dp(context,12),0,dp(context,8));
        if(message!=null) {
            TextView text=new TextView(context);text.setText(message);text.setTextColor(0xFFB9C5D8);
            text.setTextSize(16);text.setLineSpacing(dp(context,3),1);
            body.addView(text);
        }
        if(content!=null)body.addView(content);
        scroll.addView(body);panel.addView(scroll);
        AlertDialog dialog=new AlertDialog.Builder(context).setView(panel,0,0,0,0).create();
        close.setOnClickListener(v->dialog.dismiss());
        addAction(context,panel,dialog,primaryLabel,primaryAction,true);
        if(secondaryLabel!=null)addAction(context,panel,dialog,secondaryLabel,secondaryAction,false);
        dialog.show();
        if(dialog.getWindow()!=null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            int width=Math.min(dp(context,560),context.getResources().getDisplayMetrics().widthPixels-dp(context,32));
            dialog.getWindow().setLayout(width,ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        return dialog;
    }
    private static void addAction(Context context,LinearLayout panel,AlertDialog dialog,String label,Runnable action,boolean primary) {
        Button button=new Button(context);button.setText(label);button.setAllCaps(false);
        button.setTextSize(15);button.setTypeface(null,Typeface.BOLD);
        button.setTextColor(primary?0xFF0B1C50:0xFF67D5CC);
        button.setMinHeight(dp(context,52));button.setMinimumHeight(dp(context,52));
        button.setPadding(dp(context,16),dp(context,12),dp(context,16),dp(context,12));
        button.setBackground(background(context,primary?0xFFF7C450:0xFF233B78,14,false));
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);params.topMargin=dp(context,12);
        panel.addView(button,params);
        button.setOnClickListener(v->{dialog.dismiss();if(action!=null)action.run();});
    }
    private static GradientDrawable background(Context context,int colour,int radius,boolean border) {
        GradientDrawable shape=new GradientDrawable();shape.setColor(colour);shape.setCornerRadius(dp(context,radius));
        if(border)shape.setStroke(dp(context,1),0xFF496096);return shape;
    }
    private static int dp(Context context,int value){return Math.round(value*context.getResources().getDisplayMetrics().density);}
}
