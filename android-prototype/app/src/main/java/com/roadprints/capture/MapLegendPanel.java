package com.roadprints.capture;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.View;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Switch;
import android.widget.TextView;
/** One scrollable map key and control surface, without consuming map header space. */
final class MapLegendPanel {
    static AlertDialog show(Activity activity,boolean townMap,Runnable changed){
        LinearLayout body=new LinearLayout(activity);body.setOrientation(LinearLayout.VERTICAL);
        body.addView(text(activity,"ROADPRINTS LINES",14,0xFF67D5CC,true));
        for(MapLayerPreferences.Layer layer:MapLayerPreferences.Layer.values()){
            if(layer==MapLayerPreferences.Layer.BOUNDARIES&&!townMap)continue;
            LinearLayout row=new LinearLayout(activity);row.setGravity(Gravity.CENTER_VERTICAL);
            row.addView(new Sample(activity,layer.color,null,layer==MapLayerPreferences.Layer.BOUNDARIES),new LinearLayout.LayoutParams(dp(activity,62),dp(activity,48)));
            LinearLayout labels=new LinearLayout(activity);labels.setOrientation(LinearLayout.VERTICAL);
            labels.addView(text(activity,layer.label,15,Color.WHITE,true));labels.addView(text(activity,layer.detail,12,0xFFB9C5D8,false));
            row.addView(labels,new LinearLayout.LayoutParams(0,-2,1));
            Switch toggle=new Switch(activity);toggle.setTag("map_layer:"+layer.name());toggle.setMinHeight(dp(activity,48));
            toggle.setContentDescription("Show "+layer.label);toggle.setChecked(MapLayerPreferences.visible(activity,layer));
            toggle.setThumbTintList(ColorStateList.valueOf(0xFFF7C450));
            toggle.setOnCheckedChangeListener((button,on)->{MapLayerPreferences.set(activity,layer,on);changed.run();});row.addView(toggle);body.addView(row);
        }
        body.addView(text(activity,"These colours describe Roadprints overlays. Recorded travel can overlap the coloured coverage lines. The background map uses its own colours.",12,0xFFB9C5D8,false));
        if(!townMap){
            body.addView(text(activity,"COLLECTIONS ON THE MAP",14,0xFF67D5CC,true));
            RadioGroup group=new RadioGroup(activity);String selected=CollectionAccessStore.selected(activity);
            java.util.List<String> ids=new java.util.ArrayList<>();ids.add(CollectionAccessStore.NONE);ids.addAll(CollectionAccessStore.installed(activity));
            for(String id:ids){
                RadioButton option=new RadioButton(activity);option.setId(View.generateViewId());option.setText(CollectionAccessStore.NONE.equals(id)?"No collection":CollectionAccessStore.name(id));
                option.setTextColor(Color.WHITE);option.setMinHeight(dp(activity,48));option.setTag("map_collection:"+id);
                option.setButtonTintList(ColorStateList.valueOf(0xFF67D5CC));group.addView(option);if(id.equals(selected))group.check(option.getId());
                option.setOnClickListener(v->{CollectionAccessStore.select(activity,id);changed.run();});
            }body.addView(group);
            TextView manage=text(activity,"Manage collections",15,0xFFF7C450,true);manage.setMinHeight(dp(activity,48));manage.setGravity(Gravity.CENTER_VERTICAL);
            manage.setOnClickListener(v->activity.startActivity(new Intent(activity,CollectionsActivity.class)));body.addView(manage);
            body.addView(text(activity,"COLLECTION PINS",14,0xFF67D5CC,true));
            for(VenueCollection.Definition collection:VenueCollection.all()){
                String symbol=collection.pinLabel.isEmpty()?"•":collection.pinLabel;
                pin(activity,body,collection.shortName+" · not visited",collection.pinColor,symbol);
                pin(activity,body,collection.shortName+" · visited",0xFFF7C450,symbol);
            }
            pin(activity,body,"Numbered cluster · nearby venues",0xFF238F89,"3");
            body.addView(text(activity,"Gold marks a visited venue. A gold cluster contains at least one visited venue. Tap a cluster to zoom or choose a venue.",12,0xFFB9C5D8,false));
        }
        return RoadprintsDialog.show(activity,"Legend & map layers","Changes affect this map's display only.",body,null,null,null,null);
    }
    private static void pin(Activity a,LinearLayout parent,String label,int color,String symbol){
        LinearLayout row=new LinearLayout(a);row.setGravity(Gravity.CENTER_VERTICAL);row.addView(new Sample(a,color,symbol,false),new LinearLayout.LayoutParams(dp(a,62),dp(a,48)));
        row.addView(text(a,label,14,Color.WHITE,false),new LinearLayout.LayoutParams(0,-2,1));parent.addView(row);
    }
    private static TextView text(Context c,String label,int size,int color,boolean bold){TextView v=new TextView(c);v.setText(label);v.setTextSize(size);v.setTextColor(color);v.setPadding(dp(c,4),dp(c,6),dp(c,4),dp(c,6));if(bold)v.setTypeface(null,Typeface.BOLD);return v;}
    private static int dp(Context c,int value){return Math.round(value*c.getResources().getDisplayMetrics().density);}
    static final class Sample extends View {
        private final int color;private final String symbol;private final boolean outline;private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        Sample(Context c,int color,String symbol,boolean outline){super(c);this.color=color;this.symbol=symbol;this.outline=outline;setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);}
        @Override protected void onDraw(Canvas canvas){
            float x=getWidth()/2f,y=getHeight()/2f;paint.setStyle(Paint.Style.FILL);
            if(symbol==null){
                paint.setColor(0xFFEFF6FA);canvas.drawRoundRect(6,y-14,getWidth()-6,y+14,6,6,paint);
                paint.setColor(color);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(getContext(),4));paint.setStrokeCap(Paint.Cap.ROUND);
                if(outline){paint.setPathEffect(new android.graphics.DashPathEffect(new float[]{8,6},0));canvas.drawRect(14,y-9,getWidth()-14,y+9,paint);paint.setPathEffect(null);}
                else canvas.drawLine(14,y,getWidth()-14,y,paint);
            }else{
                float radius=dp(getContext(),"3".equals(symbol)?14:10);paint.setColor(Color.WHITE);canvas.drawCircle(x,y,radius+dp(getContext(),2),paint);paint.setColor(color);canvas.drawCircle(x,y,radius,paint);
                paint.setColor(Color.WHITE);paint.setTextSize(dp(getContext(),10));paint.setTextAlign(Paint.Align.CENTER);paint.setTypeface(Typeface.DEFAULT_BOLD);
                if("•".equals(symbol))canvas.drawCircle(x,y,dp(getContext(),3),paint);else canvas.drawText(symbol,x,y-(paint.ascent()+paint.descent())/2,paint);
            }
        }
    }
}
