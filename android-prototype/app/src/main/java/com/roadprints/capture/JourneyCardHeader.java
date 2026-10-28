package com.roadprints.capture;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ImageView;
import org.json.JSONObject;

/** Shared journey typography, local date/time, mode badge and available summary metadata. */
final class JourneyCardHeader {
    final LinearLayout details;
    final ImageView icon;
    JourneyCardHeader(Context context,JSONObject journey) {
        details=new LinearLayout(context);details.setOrientation(LinearLayout.VERTICAL);
        details.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1));
        String mode=journey.optString("mode","unknown"),display=JourneyListActivity.displayMode(mode);
        TextView label=text(context,display.toUpperCase(java.util.Locale.UK)+"  •  "+JourneyListActivity.displayTime(journey.optString("started_at")).toUpperCase(java.util.Locale.UK),12,0xFF67D5CC,true);
        String title=journey.optString("title",journey.optString("name",""));
        JSONObject source=journey.optJSONObject("source");
        if(title.isEmpty())title=source!=null&&"timeline_import".equals(source.optString("type"))?"Imported journey":"Captured journey";
        TextView heading=text(context,title,23,Color.WHITE,true);heading.setPadding(0,dp(context,4),0,0);
        String summary=(journey.has("distance_meters")?DistanceUnits.format(context,journey.optDouble("distance_meters"))+"  •  ":"")+JourneyListActivity.journeyDuration(journey);
        if(journey.has("gps_point_count")||journey.has("_route_point_count"))summary+="  •  "+JourneyFilters.pointCount(journey)+" GPS points";
        TextView info=text(context,summary,16,0xFFD3DCED,false);info.setPadding(0,dp(context,2),0,0);
        details.addView(label);details.addView(heading);details.addView(info);
        icon=RoadprintsIcons.image(context,RoadprintsIcons.transport(mode),0xFF67D5CC,display);icon.setPadding(dp(context,8),dp(context,8),dp(context,8),dp(context,8));
        icon.setBackground(background(context,0xFF304B88,0xFF8EA7D4,12));
        LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(dp(context,44),dp(context,44));ip.setMargins(dp(context,12),dp(context,2),0,0);icon.setLayoutParams(ip);
    }
    static GradientDrawable background(Context c,int fill,int border,int radius){GradientDrawable d=new GradientDrawable();d.setColor(fill);d.setCornerRadius(dp(c,radius));d.setStroke(dp(c,1),border);return d;}
    static TextView text(Context c,String value,int size,int colour,boolean bold){TextView v=new TextView(c);v.setText(value);v.setTextSize(size);v.setTextColor(colour);if(bold)v.setTypeface(null,Typeface.BOLD);return v;}
    static int dp(Context c,int n){return Math.round(n*c.getResources().getDisplayMetrics().density);}
}
