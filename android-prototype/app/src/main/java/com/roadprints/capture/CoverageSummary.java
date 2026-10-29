package com.roadprints.capture;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.Locale;

/** Compact coverage dashboard shared by road detail and the physical NCN union. */
final class CoverageSummary extends LinearLayout {
    private final TextView identity,percent,completed,remaining,reference;
    final RoadCompletionBar bar;
    CoverageSummary(Context c,int color){this(c,color,null);}
    CoverageSummary(Context c,int color,RoadCompletionBar existing){
        super(c);setOrientation(VERTICAL);setPadding(dp(14),dp(12),dp(14),dp(12));
        setBackground(JourneyCardHeader.background(c,0xFF233B78,0xFF46649E,14));
        LinearLayout top=new LinearLayout(c);top.setGravity(Gravity.CENTER_VERTICAL);
        identity=RouteIdentifierBadge.create(c,"",color);identity.setTag("coverage_route_badge");top.addView(identity,new LayoutParams(dp(RouteIdentifierBadge.WIDTH_DP),dp(RouteIdentifierBadge.HEIGHT_DP)));
        top.addView(new android.view.View(c),new LayoutParams(0,1,1));
        percent=label("",30,Color.WHITE,true);percent.setPadding(dp(12),0,0,0);top.addView(percent);addView(top);
        reference=label("",12,0xFFB9C5D8,false);addView(reference);
        bar=existing==null?new RoadCompletionBar(c,0,color):existing;
        LayoutParams bp=new LayoutParams(-1,dp(12));bp.topMargin=dp(12);bp.bottomMargin=dp(10);addView(bar,bp);
        LinearLayout distances=new LinearLayout(c);completed=label("",14,Color.WHITE,true);remaining=label("",14,0xFFB9C5D8,false);remaining.setGravity(Gravity.END);
        distances.addView(completed,new LayoutParams(0,-2,1));distances.addView(remaining,new LayoutParams(0,-2,1));addView(distances);
    }
    void bind(String name,double done,double total,String scope){
        double fraction=total>0?Math.max(0,Math.min(1,done/total)):0;
        identity.setText("NCN network".equals(name)?"NCN":name);identity.setContentDescription(name);percent.setText(String.format(Locale.UK,"%.1f%%",100*fraction));
        reference.setText(scope+" · "+DistanceUnits.format(getContext(),total)+" reference");
        completed.setText(DistanceUnits.format(getContext(),done)+"\nTravelled");
        remaining.setText(DistanceUnits.format(getContext(),Math.max(0,total-done))+"\nRemaining");
        bar.setFraction(fraction);bar.setContentDescription(name+" "+percent.getText()+" complete");
    }
    private TextView label(String s,int size,int color,boolean bold){TextView v=new TextView(getContext());v.setText(s);v.setTextSize(size);v.setTextColor(color);if(bold)v.setTypeface(null,Typeface.BOLD);return v;}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
}
