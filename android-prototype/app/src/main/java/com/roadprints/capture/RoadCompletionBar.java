package com.roadprints.capture;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.LinearLayout;

/** The same white-track, rounded weighted bar used by Progress and focused roads. */
final class RoadCompletionBar extends LinearLayout {
    static final int MOTORWAY=0xFF005EB8, A_ROAD=0xFF25834A;
    private final int fillColor;
    private double fraction;
    RoadCompletionBar(Context context,double fraction,int fillColor){
        super(context);this.fillColor=fillColor;setOrientation(HORIZONTAL);
        setBackground(shape(Color.WHITE));setFraction(fraction);
    }
    void setFraction(double value){
        fraction=Double.isFinite(value)?Math.max(0,Math.min(1,value)):0;removeAllViews();
        float width=(float)(fraction*100);
        View fill=new View(getContext());fill.setBackground(shape(fillColor));fill.setTag("road_progress_fill");
        addView(fill,new LayoutParams(0,-1,width));
        if(width<100)addView(new View(getContext()),new LayoutParams(0,-1,100-width));
        setContentDescription(String.format(java.util.Locale.UK,"%.1f percent complete",fraction*100));
    }
    double fraction(){return fraction;}
    int fillColor(){return fillColor;}
    private GradientDrawable shape(int colour){GradientDrawable d=new GradientDrawable();d.setColor(colour);d.setCornerRadius(20*getResources().getDisplayMetrics().density);return d;}
}
