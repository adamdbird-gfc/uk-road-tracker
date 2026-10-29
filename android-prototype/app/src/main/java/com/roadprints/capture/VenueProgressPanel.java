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
            VenueCollection.Definition definition=VenueCollection.definition(collection);
            LinearLayout data=new LinearLayout(activity);data.setOrientation(LinearLayout.VERTICAL);
            data.setTag("venue_data:"+collection);data.setPadding(dp(activity,12),dp(activity,8),dp(activity,12),dp(activity,8));
            data.setBackground(background(activity,0xFF142B5C,0xFF355184));
            data.addView(text(activity,"YOUR COLLECTION",11,TEAL,true));
            int totalVisited=0;for(int i=0;i<all.length();i++)if(completed.contains(all.optJSONObject(i).optString("id")))totalVisited++;
            TextView total=text(activity,totalVisited+" of "+all.length()+" mapped "+definition.plural+" visited",16,Color.WHITE,true);
            total.setTag("venue_total:"+collection);data.addView(total);
            data.addView(text(activity,"Visits check in automatically. Open a venue to check in manually, or review uncertain visits below.",12,MUTED,false));
            LinearLayout actions=new LinearLayout(activity);
            Button manual=button(activity,"Manual check"),importButton=button(activity,"Import from Timeline");
            manual.setTag("pub_manual_check");importButton.setTag("pub_timeline_import");
            addCompactButton(activity,actions,manual,0);addCompactButton(activity,actions,importButton,8);
            data.addView(actions);panel.addView(data);
            manual.setOnClickListener(v->VenueVisitUi.reviewNative(activity,collection,changed));
            importButton.setOnClickListener(v->PubVisitUi.importTimeline(activity));

            TextView searchLabel=text(activity,"FIND A "+definition.singular.toUpperCase(java.util.Locale.ROOT),11,TEAL,true);
            searchLabel.setPadding(0,dp(activity,16),0,dp(activity,6));panel.addView(searchLabel);
            android.widget.EditText search=searchField(activity,
                    CollectionAccessStore.SPOONS.equals(collection)?"Search name, town or postcode":"Search name, road or postcode",
                    "venue_search:"+collection);
            panel.addView(search,new LinearLayout.LayoutParams(-1,-2));
            LinearLayout filters=new LinearLayout(activity),results=new LinearLayout(activity);results.setOrientation(LinearLayout.VERTICAL);
            results.setTag("venue_results:"+collection);
            String[] selected={"All"};List<Button> choices=new ArrayList<>();
            for(String label:new String[]{"All","Visited","Remaining"}){
                Button choice=button(activity,label);choice.setTag("venue_filter:"+collection+":"+label);choices.add(choice);
                addCompactButton(activity,filters,choice,choices.size()==1?0:8);styleFilter(activity,choice,"All".equals(label));
                choice.setOnClickListener(v->{selected[0]=label;for(Button item:choices)styleFilter(activity,item,label.contentEquals(item.getText()));
                    results.removeAllViews();populateFiltered(activity,collection,results,expanded,changed,search.getText().toString().trim(),label,false);});
            }
            panel.addView(filters);panel.addView(results);
            android.os.Handler handler=new android.os.Handler(android.os.Looper.getMainLooper());Runnable[] refresh={null};
            search.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void onTextChanged(CharSequence s,int a,int b,int c){
                if(refresh[0]!=null)handler.removeCallbacks(refresh[0]);refresh[0]=()->{results.removeAllViews();populateFiltered(activity,collection,results,expanded,changed,search.getText().toString().trim(),selected[0],false);};handler.postDelayed(refresh[0],200);
            }public void afterTextChanged(android.text.Editable e){}});
            populateFiltered(activity,collection,results,expanded,changed,"","All",false);return;
        }
        Set<String> visited=completed;
        Map<String,Map<String,List<JSONObject>>> groups=new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        int count=0;
        for(int i=0;i<pubs.length();i++){
            JSONObject pub=pubs.optJSONObject(i);if(pub==null)continue;
            if(visited.contains(pub.optString("id")))count++;
            VenueCollection.Definition definition=VenueCollection.definition(collection);
            String countyName=VenueCollection.group(definition,pub,true),town=VenueCollection.group(definition,pub,false);
            groups.computeIfAbsent(countyName,key->new TreeMap<>(String.CASE_INSENSITIVE_ORDER))
                    .computeIfAbsent(town,key->new ArrayList<>()).add(pub);
        }
        TextView matches=text(activity,pubs.length()+" "+VenueCollection.definition(collection).plural+" shown · "+count+" visited",12,MUTED,false);
        matches.setTag("venue_matches:"+collection);matches.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);panel.addView(matches);
        if(pubs.length()==0)panel.addView(text(activity,"No matching venues. Try another search or filter.",14,Color.WHITE,false));
        // Show matching venues immediately while searching; keep normal browsing lazy.
        Set<String> visibleExpanded=query.isEmpty()?expanded:new HashSet<>(expanded);
        if(!query.isEmpty())for(Map.Entry<String,Map<String,List<JSONObject>>> group:groups.entrySet()){
            String key=(CollectionAccessStore.SPOONS.equals(collection)?"pub-county:":collection+"-group:")+group.getKey();
            visibleExpanded.add(key);for(String town:group.getValue().keySet())visibleExpanded.add(key+":town:"+town);
        }
        for(Map.Entry<String,Map<String,List<JSONObject>>> county:groups.entrySet()){
            int total=0,done=0;
            for(List<JSONObject> town:county.getValue().values())for(JSONObject pub:town){total++;if(visited.contains(pub.optString("id")))done++;}
            final String key=(CollectionAccessStore.SPOONS.equals(collection)?"pub-county:":collection+"-group:")+county.getKey();
            accordion(activity,panel,county.getKey(),done+" of "+total+" visited",key,visibleExpanded,body->{
                for(Map.Entry<String,List<JSONObject>> town:county.getValue().entrySet()){
                    int seen=0;for(JSONObject pub:town.getValue())if(visited.contains(pub.optString("id")))seen++;
                    String townKey=key+":town:"+town.getKey();
                    accordion(activity,body,town.getKey(),seen+" of "+town.getValue().size()+" visited",townKey,visibleExpanded,rows->{
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
    private static GradientDrawable background(Activity activity,int colour,int border){
        GradientDrawable bg=new GradientDrawable();bg.setColor(colour);bg.setCornerRadius(dp(activity,8));bg.setStroke(dp(activity,1),border);return bg;
    }
    static android.widget.EditText searchField(Activity activity,String hint,String tag){
        android.widget.EditText search=new android.widget.EditText(activity);search.setSingleLine(true);
        search.setHint(hint);search.setTextSize(14);search.setTextColor(0xFF0B1C50);search.setHintTextColor(0xFF506080);
        search.setBackground(background(activity,Color.WHITE,TEAL));search.setPadding(dp(activity,12),dp(activity,10),dp(activity,12),dp(activity,10));
        search.setMinHeight(dp(activity,48));search.setTag(tag);search.setContentDescription(hint);return search;
    }
    static void addCompactButton(Activity activity,LinearLayout row,Button button,int gap){
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,dp(activity,48),1);
        params.setMargins(dp(activity,gap),dp(activity,8),0,dp(activity,4));row.addView(button,params);
    }
    static void styleFilter(Activity activity,Button button,boolean selected){
        button.setSelected(selected);button.setTextColor(selected?0xFF0B1C50:Color.WHITE);
        button.setTypeface(null,selected?Typeface.BOLD:Typeface.NORMAL);
        button.setBackground(background(activity,selected?GOLD:0xFF142B5C,selected?GOLD:0xFF57709C));
        button.setContentDescription(button.getText()+(selected?", selected":", not selected"));
    }
    static Button button(Activity activity,String title){
        Button button=new Button(activity);button.setText(title);button.setAllCaps(false);button.setTextSize(12);button.setTextColor(TEAL);
        button.setMinHeight(0);button.setMinimumHeight(0);button.setMinWidth(0);button.setMinimumWidth(0);
        button.setPadding(dp(activity,6),0,dp(activity,6),0);button.setBackground(background(activity,0xFF142B5C,0xFF57709C));return button;
    }
    private static TextView text(Activity activity,String title,int size,int colour,boolean bold){
        TextView result=new TextView(activity);result.setText(title);result.setTextSize(size);result.setTextColor(colour);result.setPadding(0,dp(activity,4),0,dp(activity,4));
        if(bold)result.setTypeface(null,Typeface.BOLD);return result;
    }
    private static int dp(Activity activity,int value){return Math.round(value*activity.getResources().getDisplayMetrics().density);}
}
