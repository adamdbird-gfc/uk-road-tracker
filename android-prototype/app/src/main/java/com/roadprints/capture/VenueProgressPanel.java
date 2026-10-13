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
final class VenueProgressPanel {
    private static final int TEAL=0xFF67D5CC,MUTED=0xFFB9C5D8,GOLD=0xFFF7C450;
    private VenueProgressPanel(){}
    static void populate(Activity activity,String collection,LinearLayout panel,Set<String> expanded,Runnable changed){
        populateFiltered(activity,collection,panel,expanded,changed,"","All",true);
    }
    private static void populateFiltered(Activity activity,String collection,LinearLayout panel,Set<String> expanded,Runnable changed,String query,String filter,boolean controls){
        JSONArray all=VenueCollection.catalogue(activity,collection),pubs=new JSONArray();
        Set<String> completed=VenueCollection.completed(activity,collection);
        for(int i=0;i<all.length();i++){
            JSONObject row=all.optJSONObject(i);if(row==null)continue;
            boolean visited=completed.contains(row.optString("id"));
            String searchable=(row.optString("name")+" "+row.optString("town")+" "+row.optString("postcode")+" "+row.optString("road")).toLowerCase(java.util.Locale.ROOT);
            if(!searchable.contains(query.toLowerCase(java.util.Locale.ROOT))||("Visited".equals(filter)&&!visited)||("Remaining".equals(filter)&&visited))continue;
            pubs.put(row);
        }
        if(controls){
            android.widget.EditText search=new android.widget.EditText(activity);search.setSingleLine(true);search.setHint("Search name, town or postcode");search.setTextColor(Color.WHITE);search.setHintTextColor(MUTED);search.setTag("venue_search:"+collection);panel.addView(search);
            LinearLayout filters=new LinearLayout(activity),results=new LinearLayout(activity);results.setOrientation(LinearLayout.VERTICAL);
            String[] selected={"All"};
            for(String label:new String[]{"All","Visited","Remaining"}){
                Button choice=button(activity,label);filters.addView(choice,new LinearLayout.LayoutParams(0,-2,1));
                choice.setOnClickListener(v->{selected[0]=label;results.removeAllViews();populateFiltered(activity,collection,results,expanded,changed,search.getText().toString().trim(),label,false);});
            }
            panel.addView(filters);panel.addView(results);
            android.os.Handler handler=new android.os.Handler(android.os.Looper.getMainLooper());Runnable[] refresh={null};
            search.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void onTextChanged(CharSequence s,int a,int b,int c){
                if(refresh[0]!=null)handler.removeCallbacks(refresh[0]);refresh[0]=()->{results.removeAllViews();populateFiltered(activity,collection,results,expanded,changed,search.getText().toString().trim(),selected[0],false);};handler.postDelayed(refresh[0],200);
            }public void afterTextChanged(android.text.Editable e){}});
            populateFiltered(activity,collection,results,expanded,changed,"","All",false);return;
        }
        Set<String> visited=VenueCollection.completed(activity,collection),currentIds=new HashSet<>();
        Map<String,Map<String,List<JSONObject>>> groups=new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        int count=0;
        for(int i=0;i<pubs.length();i++){
            JSONObject pub=pubs.optJSONObject(i);if(pub==null)continue;
            currentIds.add(pub.optString("id"));if(visited.contains(pub.optString("id")))count++;
            VenueCollection.Definition definition=VenueCollection.definition(collection);
            String countyName=VenueCollection.group(definition,pub,true),town=VenueCollection.group(definition,pub,false);
            groups.computeIfAbsent(countyName,key->new TreeMap<>(String.CASE_INSENSITIVE_ORDER))
                    .computeIfAbsent(town,key->new ArrayList<>()).add(pub);
        }
        panel.addView(text(activity,count+" of "+pubs.length()+" mapped "+VenueCollection.definition(collection).plural+" visited",16,Color.WHITE,true));
        panel.addView(text(activity,"Supported visits check in automatically. Manual check-in is available from each venue. Uncertain visits need your confirmation.",12,MUTED,false));
        Button manual=button(activity,"Manual check"),importButton=button(activity,"Import from Timeline");
        manual.setTag("pub_manual_check");importButton.setTag("pub_timeline_import");
        panel.addView(manual);panel.addView(importButton);
        manual.setOnClickListener(v->VenueVisitUi.reviewNative(activity,collection,changed));
        importButton.setOnClickListener(v->PubVisitUi.importTimeline(activity));
        for(Map.Entry<String,Map<String,List<JSONObject>>> county:groups.entrySet()){
            int total=0,done=0;
            for(List<JSONObject> town:county.getValue().values())for(JSONObject pub:town){total++;if(visited.contains(pub.optString("id")))done++;}
            final String key=(CollectionAccessStore.SPOONS.equals(collection)?"pub-county:":collection+"-group:")+county.getKey();
            accordion(activity,panel,county.getKey(),done+" of "+total+" visited",key,expanded,body->{
                for(Map.Entry<String,List<JSONObject>> town:county.getValue().entrySet()){
                    int seen=0;for(JSONObject pub:town.getValue())if(visited.contains(pub.optString("id")))seen++;
                    String townKey=key+":town:"+town.getKey();
                    accordion(activity,body,town.getKey(),seen+" of "+town.getValue().size()+" visited",townKey,expanded,rows->{
                        town.getValue().sort(Comparator.comparing(pub->pub.optString("name"),String.CASE_INSENSITIVE_ORDER));
                        for(JSONObject pub:town.getValue())addPubRow(activity,collection,rows,pub,visited,changed);
                    });
                }
            });
        }
        Map<String,JSONObject> archived=new TreeMap<>();
        JSONArray records=VenueVisitStore.records(activity,collection);
        for(int i=0;i<records.length();i++){
            JSONObject record=records.optJSONObject(i);if(record==null||!"All".equals(filter)||!query.isEmpty()||PubVisitStore.find(all,record.optString("venue_id"))!=null)continue;
            try{archived.put(record.optString("venue_id"),new JSONObject().put("id",record.getString("venue_id"))
                    .put("name",record.getString("name")).put("town",record.optString("town")).put("location_label","Historical visit"));}
            catch(Exception invalid){throw new IllegalStateException(invalid);}
        }
        if(!archived.isEmpty())accordion(activity,panel,"Previous catalogue venues",archived.size()+" visited","pub-archive",expanded,rows->{
            for(JSONObject pub:archived.values())addPubRow(activity,collection,rows,pub,visited,changed);
        });
    }
    private static void addPubRow(Activity activity,String collection,LinearLayout rows,JSONObject pub,Set<String> visited,Runnable changed){
        TextView row=text(activity,(visited.contains(pub.optString("id"))?"✓ ":"○ ")+pub.optString("name"),14,
                visited.contains(pub.optString("id"))?GOLD:MUTED,false);
        row.setTag("pub-row:"+pub.optString("id"));row.setMinHeight(dp(activity,48));
        row.setContentDescription(pub.optString("name")+(visited.contains(pub.optString("id"))?", visited":", not visited"));
        rows.addView(row);row.setOnClickListener(v->VenueVisitUi.details(activity,collection,pub,changed));
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
