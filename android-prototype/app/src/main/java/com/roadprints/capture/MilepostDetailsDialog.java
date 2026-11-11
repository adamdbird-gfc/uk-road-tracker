package com.roadprints.capture;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;

/** Shared map and preview dialog. A locked pin never binds identity or source details. */
final class MilepostDetailsDialog {
    static AlertDialog show(Context context, MilepostPresentationReader.Discovery discovery, String name,
                            String designer, double latitude, double longitude) {
        boolean found = discovery != null;
        int design = found ? MilepostPresentationReader.Discovery.designIndex(designer) : 4;
        LinearLayout body = new LinearLayout(context);
        body.setOrientation(LinearLayout.VERTICAL);

        int accent = found ? 0xFFD9BFFF : 0xFF9DE8E2;
        LinearLayout artwork = new LinearLayout(context);
        artwork.setGravity(Gravity.CENTER);
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xFF172F65);
        background.setCornerRadius(dp(context,16));
        background.setStroke(dp(context,1),0xFF46649E);
        artwork.setBackground(background);
        int image = found ? MilepostArtworkView.DESIGNS[design] : R.drawable.milepost_silhouette;
        artwork.addView(MilepostArtworkView.icon(context,image),new LinearLayout.LayoutParams(dp(context,100),dp(context,100)));
        body.addView(artwork,new LinearLayout.LayoutParams(-1,dp(context,108)));

        addText(body,found ? discovery.date==null ? "Discovered · date unavailable"
                : "Discovered "+displayDate(discovery.date) : "Undiscovered",14,accent,true);
        if(found){
            String title = discoveredTitle(name,designer);
            String designLabel = MilepostPresentationReader.Discovery.designLabel(design);
            if(design<4&&!title.equals(designLabel))addText(body,designLabel,16,Color.WHITE,true);
            addText(body,MilepostPresentation.designerContext(design),15,0xFFCED8EA,false);
            for(int index=0;index<MilepostAchievements.IDS.length;index++){
                if((discovery.relatedAwards&(1<<index))!=0)
                    addText(body,"Related achievement · "+MilepostPresentation.relatedAward(index),14,accent,false);
            }
            TextView location=addText(body,"View location on OpenStreetMap ↗",14,accent,true);
            location.setMinHeight(dp(context,48));
            location.setGravity(Gravity.CENTER_VERTICAL);
            location.setOnClickListener(view->{
                Uri uri=Uri.parse("https://www.openstreetmap.org/?mlat="+latitude+"&mlon="+longitude
                        +"#map=18/"+latitude+"/"+longitude);
                try{context.startActivity(new Intent(Intent.ACTION_VIEW,uri));}
                catch(android.content.ActivityNotFoundException unavailable){
                    android.widget.Toast.makeText(context,"No browser is available to open the location.",android.widget.Toast.LENGTH_SHORT).show();
                }
            });
            addText(body,"© OpenStreetMap contributors",12,0xFFB9C5D8,false);
        }else{
            addText(body,"Find it on your next outing",17,Color.WHITE,true);
            addText(body,"Pass within 100 metres on a recorded walk, run or cycle and Roadprints will mark it as discovered.",
                    15,0xFFCED8EA,false);
        }
        return RoadprintsDialog.show(context,found ? discoveredTitle(name,designer) : "Millennium Milepost",
                null,body,null,null,null,null);
    }

    static String discoveredTitle(String name,String designer){
        return name==null ? MilepostPresentationReader.Discovery.designLabel(
                MilepostPresentationReader.Discovery.designIndex(designer)) : name;
    }

    static String displayDate(String date){
        return LocalDate.parse(date).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(Locale.UK));
    }

    private static TextView addText(LinearLayout body,String copy,int size,int color,boolean bold){
        TextView text=new TextView(body.getContext());
        text.setText(copy);
        text.setTextSize(size);
        text.setTextColor(color);
        if(bold)text.setTypeface(null,Typeface.BOLD);
        text.setPadding(0,dp(body.getContext(),9),0,0);
        text.setLineSpacing(dp(body.getContext(),2),1);
        body.addView(text,new LinearLayout.LayoutParams(-1,-2));
        return text;
    }

    private static int dp(Context context,int value){
        return Math.round(value*context.getResources().getDisplayMetrics().density);
    }
}
