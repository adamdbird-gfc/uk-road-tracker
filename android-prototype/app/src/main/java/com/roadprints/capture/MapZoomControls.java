package com.roadprints.capture;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Identical external zoom controls for overview and focused road maps. */
final class MapZoomControls {
    static View add(FrameLayout frame,RoutePreviewView map) {
        Context c=frame.getContext();LinearLayout controls=new LinearLayout(c);controls.setOrientation(LinearLayout.VERTICAL);
        controls.setGravity(Gravity.CENTER);controls.setTag("map_zoom_controls");
        controls.addView(button(c,"+","Zoom in",map::zoomIn));
        controls.addView(new View(c),new LinearLayout.LayoutParams(1,dp(c,8)));
        controls.addView(button(c,"−","Zoom out",map::zoomOut));
        FrameLayout.LayoutParams params=new FrameLayout.LayoutParams(dp(c,48),-2,Gravity.END|Gravity.CENTER_VERTICAL);
        params.setMargins(0,0,dp(c,12),0);frame.addView(controls,params);return controls;
    }
    private static TextView button(Context c,String label,String description,Runnable action) {
        TextView button=new TextView(c);button.setText(label);button.setTextSize(27);button.setTypeface(null,Typeface.BOLD);
        button.setTextColor(0xFF10275D);button.setGravity(Gravity.CENTER);button.setContentDescription(description);
        GradientDrawable shape=new GradientDrawable();shape.setColor(Color.WHITE);shape.setCornerRadius(dp(c,8));button.setBackground(shape);
        button.setElevation(dp(c,4));button.setFocusable(true);button.setOnClickListener(v->action.run());
        button.setLayoutParams(new LinearLayout.LayoutParams(dp(c,48),dp(c,48)));return button;
    }
    private static int dp(Context c,int n){return Math.round(n*c.getResources().getDisplayMetrics().density);}
}
