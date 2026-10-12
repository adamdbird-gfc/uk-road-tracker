package com.roadprints.capture;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Lazy county/town/pub accordions using the Progress page's shared panel. */
final class PubProgressPanel {
    private static final int TEAL=0xFF67D5CC,MUTED=0xFFB9C5D8,GOLD=0xFFF7C450;
    private PubProgressPanel(){}
    static void populate(Activity activity,LinearLayout panel,Set<String> expanded,Runnable changed){
        JSONArray pubs=CollectionMapCatalog.spoons(activity);
        Set<String> visited=PubVisitStore.completed(activity),currentIds=new HashSet<>();
        Map<String,Map<String,List<JSONObject>>> groups=new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        int count=0;
        for(int i=0;i<pubs.length();i++){
            JSONObject pub=pubs.optJSONObject(i);if(pub==null)continue;
            currentIds.add(pub.optString("id"));if(visited.contains(pub.optString("id")))count++;
            JSONObject county=pub.optJSONObject("county");
            String countyName=county==null?"Other UK pubs":county.optString("name","Other UK pubs");
            String town=pub.optString("town");if(town.isEmpty())town="Other locations";
            groups.computeIfAbsent(countyName,key->new TreeMap<>(String.CASE_INSENSITIVE_ORDER))
                    .computeIfAbsent(town,key->new ArrayList<>()).add(pub);
        }
        panel.addView(text(activity,count+" of "+pubs.length()+" mapped pubs visited",16,Color.WHITE,true));
        panel.addView(text(activity,"Confirmed pub visits from Timeline and live check-ins. Grouped by historic county, then town.",12,MUTED,false));
        Button manual=button(activity,"Manual check"),importButton=button(activity,"Import from Timeline");
        manual.setTag("pub_manual_check");importButton.setTag("pub_timeline_import");
        panel.addView(manual);panel.addView(importButton);
        manual.setOnClickListener(v->PubVisitUi.review(activity,changed));
        importButton.setOnClickListener(v->PubVisitUi.importTimeline(activity));
        for(Map.Entry<String,Map<String,List<JSONObject>>> county:groups.entrySet()){
            int total=0,done=0;
            for(List<JSONObject> town:county.getValue().values())for(JSONObject pub:town){total++;if(visited.contains(pub.optString("id")))done++;}
            final String key="pub-county:"+county.getKey();
            accordion(activity,panel,county.getKey(),done+" of "+total+" visited",key,expanded,body->{
                for(Map.Entry<String,List<JSONObject>> town:county.getValue().entrySet()){
                    int seen=0;for(JSONObject pub:town.getValue())if(visited.contains(pub.optString("id")))seen++;
                    String townKey=key+":town:"+town.getKey();
                    accordion(activity,body,town.getKey(),seen+" of "+town.getValue().size()+" visited",townKey,expanded,rows->{
                        town.getValue().sort(Comparator.comparing(pub->pub.optString("name"),String.CASE_INSENSITIVE_ORDER));
                        for(JSONObject pub:town.getValue())addPubRow(activity,rows,pub,visited,changed);
                    });
                }
            });
        }
        Map<String,JSONObject> archived=new TreeMap<>();
        JSONArray records=PubVisitStore.records(activity);
        for(int i=0;i<records.length();i++){
            JSONObject record=records.optJSONObject(i);if(record==null||currentIds.contains(record.optString("pub_id")))continue;
            try{archived.put(record.optString("pub_id"),new JSONObject().put("id",record.getString("pub_id"))
                    .put("name",record.getString("name")).put("town",record.optString("town")).put("location_label","Historical visit"));}
            catch(Exception invalid){throw new IllegalStateException(invalid);}
        }
        if(!archived.isEmpty())accordion(activity,panel,"Previous catalogue pubs",archived.size()+" visited","pub-archive",expanded,rows->{
            for(JSONObject pub:archived.values())addPubRow(activity,rows,pub,visited,changed);
        });
    }
    private static void addPubRow(Activity activity,LinearLayout rows,JSONObject pub,Set<String> visited,Runnable changed){
        TextView row=text(activity,(visited.contains(pub.optString("id"))?"✓ ":"○ ")+pub.optString("name"),14,
                visited.contains(pub.optString("id"))?GOLD:MUTED,false);
        row.setTag("pub-row:"+pub.optString("id"));row.setMinHeight(dp(activity,48));
        row.setContentDescription(pub.optString("name")+(visited.contains(pub.optString("id"))?", visited":", not visited"));
        rows.addView(row);row.setOnClickListener(v->PubVisitUi.details(activity,pub,changed));
    }
    private static void accordion(Activity activity,LinearLayout parent,String title,String summary,String key,
                                  Set<String> expanded,java.util.function.Consumer<LinearLayout> populate){
        LinearLayout card=new LinearLayout(activity);card.setOrientation(LinearLayout.VERTICAL);card.setTag(key);
        GradientDrawable shape=new GradientDrawable();shape.setColor(key.contains(":town:")?0xFF263F75:0xFF203A73);shape.setCornerRadius(dp(activity,12));card.setBackground(shape);
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);params.setMargins(dp(activity,4),dp(activity,5),dp(activity,4),dp(activity,5));parent.addView(card,params);
        LinearLayout header=new LinearLayout(activity);header.setGravity(Gravity.CENTER_VERTICAL);header.setPadding(dp(activity,12),dp(activity,8),dp(activity,12),dp(activity,8));
        LinearLayout labels=new LinearLayout(activity);labels.setOrientation(LinearLayout.VERTICAL);
        labels.addView(text(activity,title,15,Color.WHITE,true));labels.addView(text(activity,summary,11,TEAL,false));
        header.addView(labels,new LinearLayout.LayoutParams(0,-2,1));
        TextView arrow=text(activity,"⌄",22,MUTED,false);header.addView(arrow);card.addView(header);
        LinearLayout body=new LinearLayout(activity);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(activity,8),0,dp(activity,8),dp(activity,6));body.setVisibility(View.GONE);card.addView(body);
        Runnable toggle=()->{
            boolean open=body.getVisibility()!=View.VISIBLE;
            if(open&&body.getChildCount()==0)populate.accept(body);
            body.setVisibility(open?View.VISIBLE:View.GONE);arrow.setText(open?"⌃":"⌄");
            if(open)expanded.add(key);else expanded.remove(key);
            header.setContentDescription(title+", "+summary+(open?", expanded":", collapsed"));
        };
        header.setTag(key+":header");header.setClickable(true);header.setFocusable(true);header.setOnClickListener(v->toggle.run());
        header.setContentDescription(title+", "+summary+", collapsed");
        if(expanded.contains(key))toggle.run();
    }
    private static Button button(Activity activity,String title){
        Button button=new Button(activity);button.setText(title);button.setAllCaps(false);button.setTextSize(14);button.setTextColor(0xFF0B1C50);
        button.setMinHeight(dp(activity,48));GradientDrawable bg=new GradientDrawable();bg.setColor(GOLD);bg.setCornerRadius(dp(activity,12));button.setBackground(bg);
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);params.topMargin=dp(activity,10);params.bottomMargin=dp(activity,5);button.setLayoutParams(params);return button;
    }
    private static TextView text(Activity activity,String title,int size,int colour,boolean bold){
        TextView result=new TextView(activity);result.setText(title);result.setTextSize(size);result.setTextColor(colour);result.setPadding(0,dp(activity,4),0,dp(activity,4));
        if(bold)result.setTypeface(null,Typeface.BOLD);return result;
    }
    private static int dp(Activity activity,int value){return Math.round(value*activity.getResources().getDisplayMetrics().density);}
}
