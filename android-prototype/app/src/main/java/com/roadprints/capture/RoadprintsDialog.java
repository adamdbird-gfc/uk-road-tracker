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
        return show(context,title,message,content,primaryLabel,primaryAction,secondaryLabel,secondaryAction,true);
    }
    static AlertDialog show(Context context, String title, String message, View content,
                            String primaryLabel, Runnable primaryAction,
                            String secondaryLabel, Runnable secondaryAction, boolean dismissOnAction) {
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
        TextView close=closeControl(context,null);
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
        AlertDialog dialog=new AlertDialog.Builder(context).create();
        dialog.setView(panel,0,0,0,0);
        close.setOnClickListener(v->dialog.dismiss());
        boolean destructive=isDestructive(primaryLabel);
        if(!destructive)addAction(context,panel,dialog,primaryLabel,primaryAction,true,dismissOnAction);
        if(secondaryLabel!=null)addAction(context,panel,dialog,secondaryLabel,secondaryAction,false,dismissOnAction);
        if(destructive)addAction(context,panel,dialog,primaryLabel,primaryAction,true,dismissOnAction);
        if(content!=null){
            java.util.List<View> removals=new java.util.ArrayList<>();collectDestructive(content,removals);
            for(View remove:removals){if(remove.getParent() instanceof ViewGroup)((ViewGroup)remove.getParent()).removeView(remove);panel.addView(remove,new LinearLayout.LayoutParams(-1,-2));}
        }
        dialog.show();
        if(dialog.getWindow()!=null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            int width=Math.min(dp(context,560),context.getResources().getDisplayMetrics().widthPixels-dp(context,32));
            dialog.getWindow().setLayout(width,ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        return dialog;
    }
    private static void addAction(Context context,LinearLayout panel,AlertDialog dialog,String label,Runnable action,boolean primary,boolean dismissOnAction) {
        if(label==null||"close".equalsIgnoreCase(label.trim()))return;
        Button button=new Button(context);button.setText(label);button.setAllCaps(false);
        button.setTag(primary?"roadprints_dialog_primary":"roadprints_dialog_secondary");
        button.setTextSize(15);button.setTypeface(null,Typeface.BOLD);
        button.setTextColor(isDestructive(label)?0xFFFF8A8A:primary?0xFF0B1C50:0xFF67D5CC);
        button.setMinHeight(dp(context,52));button.setMinimumHeight(dp(context,52));
        button.setPadding(dp(context,16),dp(context,12),dp(context,16),dp(context,12));
        button.setBackground(background(context,isDestructive(label)?Color.TRANSPARENT:primary?0xFFF7C450:0xFF233B78,14,false));
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);params.topMargin=dp(context,12);
        panel.addView(button,params);
        button.setOnClickListener(v->{if(dismissOnAction)dialog.dismiss();if(action!=null)action.run();});
    }
    private static boolean isDestructive(String label){return label!=null&&(label.toLowerCase(java.util.Locale.ROOT).startsWith("clear")||label.toLowerCase(java.util.Locale.ROOT).startsWith("delete")||label.toLowerCase(java.util.Locale.ROOT).startsWith("remove"));}
    private static void collectDestructive(View view,java.util.List<View> found){
        if("roadprints_destructive".equals(view.getTag())){found.add(view);return;}
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)collectDestructive(group.getChildAt(i),found);}
    }
    static TextView closeControl(Context context,Runnable action){
        TextView close=new TextView(context);close.setText("CLOSE");close.setTextSize(13);close.setTypeface(null,Typeface.BOLD);
        close.setTextColor(0xFFF7C450);close.setGravity(Gravity.CENTER);close.setContentDescription("Close");
        close.setTag("roadprints_dialog_close");close.setMinHeight(dp(context,48));close.setMinWidth(dp(context,64));
        close.setPadding(dp(context,8),0,dp(context,8),0);close.setClickable(true);close.setFocusable(true);
        if(action!=null)close.setOnClickListener(v->action.run());return close;
    }
    /** Move an existing close handler into a consistent top-right card header. */
    static void placeClose(LinearLayout card,TextView close){
        if(close.getParent() instanceof ViewGroup)((ViewGroup)close.getParent()).removeView(close);
        close.setText("CLOSE");close.setTextSize(13);close.setTypeface(null,Typeface.BOLD);close.setTextColor(0xFFF7C450);
        close.setTag("roadprints_dialog_close");close.setContentDescription("Close");close.setMinHeight(dp(card.getContext(),48));
        close.setMinWidth(dp(card.getContext(),64));close.setPadding(dp(card.getContext(),8),0,dp(card.getContext(),8),0);
        LinearLayout header=new LinearLayout(card.getContext());header.setGravity(Gravity.CENTER_VERTICAL);
        if(card.getChildCount()>0){View first=card.getChildAt(0);card.removeView(first);header.addView(first,new LinearLayout.LayoutParams(0,-2,1));}
        else header.addView(new View(card.getContext()),new LinearLayout.LayoutParams(0,0,1));
        header.addView(close);card.addView(header,0);
    }
    /** Standardise legacy native confirmations while preserving button callbacks and input views. */
    static final class Builder extends AlertDialog.Builder {
        private CharSequence caption;
        Builder(Context context){super(new android.view.ContextThemeWrapper(context,R.style.RoadprintsPopupTheme));}
        @Override public AlertDialog.Builder setTitle(CharSequence title){caption=title;return super.setTitle(title);}
        @Override public AlertDialog create(){
            final AlertDialog[] holder={null};
            if(caption!=null){
                LinearLayout header=new LinearLayout(getContext());header.setGravity(Gravity.CENTER_VERTICAL);header.setPadding(dp(getContext(),20),dp(getContext(),8),dp(getContext(),12),0);
                TextView title=new TextView(getContext());title.setText(caption);title.setTextColor(Color.WHITE);title.setTextSize(20);title.setTypeface(null,Typeface.BOLD);
                header.addView(title,new LinearLayout.LayoutParams(0,-2,1));header.addView(closeControl(getContext(),()->holder[0].dismiss()));setCustomTitle(header);
            }
            holder[0]=super.create();
            if(caption!=null)holder[0].setOnShowListener(d->{
                AlertDialog dialog=holder[0];if(dialog.getWindow()!=null){dialog.getWindow().setBackgroundDrawable(background(getContext(),0xFF0B1C50,22,true));dialog.getWindow().setLayout(Math.min(dp(getContext(),560),getContext().getResources().getDisplayMetrics().widthPixels-dp(getContext(),32)),-2);}
                for(int which:new int[]{AlertDialog.BUTTON_POSITIVE,AlertDialog.BUTTON_NEGATIVE,AlertDialog.BUTTON_NEUTRAL}){
                    Button button=dialog.getButton(which);if(button==null)continue;
                    if("close".equalsIgnoreCase(button.getText().toString()))button.setVisibility(View.GONE);
                    else button.setTextColor(isDestructive(button.getText().toString())?0xFFFF8A8A:0xFF67D5CC);
                }
                recolourLegacyText(dialog.getWindow().getDecorView());
                Button destructive=null;
                for(int which:new int[]{AlertDialog.BUTTON_POSITIVE,AlertDialog.BUTTON_NEGATIVE,AlertDialog.BUTTON_NEUTRAL}){Button b=dialog.getButton(which);if(b!=null&&isDestructive(b.getText().toString()))destructive=b;}
                if(destructive!=null&&destructive.getParent() instanceof LinearLayout){
                    LinearLayout actions=(LinearLayout)destructive.getParent();actions.setOrientation(LinearLayout.VERTICAL);actions.removeView(destructive);actions.addView(destructive,new LinearLayout.LayoutParams(-1,dp(getContext(),52)));
                }
            });
            return holder[0];
        }
    }
    private static void recolourLegacyText(View view){
        if(view instanceof TextView&&!(view instanceof Button)){
            TextView text=(TextView)view;if(text.getCurrentTextColor()==Color.BLACK)text.setTextColor(Color.WHITE);
        }
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)recolourLegacyText(group.getChildAt(i));}
    }
    private static GradientDrawable background(Context context,int colour,int radius,boolean border) {
        GradientDrawable shape=new GradientDrawable();shape.setColor(colour);shape.setCornerRadius(dp(context,radius));
        if(border)shape.setStroke(dp(context,1),0xFF496096);return shape;
    }
    private static int dp(Context context,int value){return Math.round(value*context.getResources().getDisplayMetrics().density);}
}
