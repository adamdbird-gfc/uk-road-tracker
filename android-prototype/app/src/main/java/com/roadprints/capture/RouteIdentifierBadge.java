package com.roadprints.capture;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.TextView;

/** The 78 x 32 dp route identifier used by Progress rows and focused summaries. */
final class RouteIdentifierBadge {
    static final int WIDTH_DP=78,HEIGHT_DP=32;
    private RouteIdentifierBadge(){}
    static TextView create(Context context,String label,int color){
        TextView badge=new TextView(context);badge.setText(label);badge.setTextColor(Color.WHITE);
        badge.setTextSize(14);badge.setTypeface(null,Typeface.BOLD);badge.setGravity(Gravity.CENTER);
        badge.setMaxLines(2);badge.setEllipsize(TextUtils.TruncateAt.END);
        badge.setPadding(dp(context,5),dp(context,2),dp(context,5),dp(context,2));
        GradientDrawable shape=new GradientDrawable();shape.setColor(color);shape.setCornerRadius(dp(context,6));
        if(color==RoadCompletionBar.MOTORWAY)shape.setStroke(dp(context,1),0xFF003F78);
        badge.setBackground(shape);return badge;
    }
    private static int dp(Context c,int n){return Math.round(n*c.getResources().getDisplayMetrics().density);}
}
