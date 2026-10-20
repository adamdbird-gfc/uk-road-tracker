package com.roadprints.capture;

import android.app.Activity;
import android.graphics.Color;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.EditText;
import java.util.*;

/** NCN section within the existing Progress screen; activity and groups survive navigation. */
final class NcnProgressUi {
    static final int PURPLE=0xFF8D55C7,ORANGE=0xFFFF9B42,IDENTITY=0xFFD3B5FF;
    static String label(String mode){return mode.equals("cycling")?"Cycling":mode.equals("on_foot")?"Walking & running":"Combined";}
    static void add(Activity activity,LinearLayout parent){
        LinearLayout panel=new LinearLayout(activity);panel.setOrientation(android.widget.LinearLayout.VERTICAL);panel.setPadding(dp(activity,18),dp(activity,18),dp(activity,18),dp(activity,18));panel.setTag("progress:ncn");parent.addView(panel);
        final Object[] rendered={null};final boolean[] renderedEnabled={NcnProgressStore.enabled(activity)};final String[] activityMode={""};final TextView[] statusView={null};
        Runnable update=new Runnable(){public void run(){
            if(activity.isFinishing()||activity.isDestroyed()||!panel.isAttachedToWindow())return;
            NcnProgressStore.ensure(activity);Object snapshot=NcnProgressStore.current();String mode=NcnProgressStore.mode(activity);
            if(snapshot!=rendered[0]||!mode.equals(activityMode[0])||renderedEnabled[0]!=NcnProgressStore.enabled(activity)){renderedEnabled[0]=NcnProgressStore.enabled(activity);rendered[0]=snapshot;activityMode[0]=mode;populate(activity,panel,statusView);}
            if(statusView[0]!=null)statusView[0].setText(NcnProgressStore.status());View retry=panel.findViewWithTag("ncn_retry");if(retry!=null)retry.setVisibility(NcnProgressStore.status().contains("could not")?View.VISIBLE:View.GONE);panel.postDelayed(this,1000);
        }};
        populate(activity,panel,statusView);panel.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener(){public void onViewAttachedToWindow(View v){panel.removeCallbacks(update);panel.post(update);}public void onViewDetachedFromWindow(View v){panel.removeCallbacks(update);}});panel.post(update);
    }
    private static void populate(Activity a,LinearLayout panel,TextView[] statusView){
        panel.removeAllViews();statusView[0]=null;panel.addView(text(a,"National Cycle Network",21,Color.WHITE));
        if(!NcnProgressStore.enabled(a)){panel.addView(text(a,"Explore verified-open NCN sections with walking, running and cycling journeys.",14,Color.WHITE));panel.addView(action(a,"ENABLE NCN COLLECTION",()->{NcnProgressStore.activate(a,true);populate(a,panel,statusView);}));return;}
        statusView[0]=text(a,NcnProgressStore.status(),13,0xFF67D5CC);panel.addView(statusView[0]);
        TextView retry=action(a,"RETRY ASSESSMENT",()->NcnProgressStore.retry(a));retry.setTag("ncn_retry");retry.setVisibility(NcnProgressStore.status().contains("could not")?View.VISIBLE:View.GONE);panel.addView(retry);
        NcnProgressStore.Snapshot s=NcnProgressStore.current();if(s==null)return;
        String mode=NcnProgressStore.mode(a);LinearLayout modes=new LinearLayout(a);for(String m:new String[]{"combined","cycling","on_foot"})modes.addView(action(a,(m.equals(mode)?"✓ ":"")+label(m),()->{NcnProgressStore.mode(a,m);populate(a,panel,statusView);}),new LinearLayout.LayoutParams(0,-2,1));panel.addView(modes);
        try{
            double done=s.ledger.networkMetres(mode),total=s.catalogue.networkLength;
            CoverageSummary summary=new CoverageSummary(a,PURPLE);summary.bind("NCN network",done,total,"Unique verified-open network · "+label(mode));panel.addView(summary,new LinearLayout.LayoutParams(-1,-2));
            EditText search=new EditText(a);search.setSingleLine(true);search.setTextColor(Color.WHITE);search.setHintTextColor(0xFFB9C5D8);search.setHint("Search route number or name");search.setContentDescription("Search NCN routes");panel.addView(search);
            android.content.SharedPreferences prefs=a.getSharedPreferences("ncn_ui",0);search.setText(prefs.getString("search",""));
            List<View> rows=new ArrayList<>();List<String> terms=new ArrayList<>();Map<String,LinearLayout> groups=new LinkedHashMap<>();
            for(String group:new String[]{"national","regional","named"}){
                LinearLayout entries=new LinearLayout(a);entries.setOrientation(android.widget.LinearLayout.VERTICAL);groups.put(group,entries);boolean open=prefs.getBoolean(group,group.equals("national"));
                TextView heading=action(a,(open?"▾ ":"▸ ")+group.substring(0,1).toUpperCase(Locale.UK)+group.substring(1)+" routes",()->{});
                heading.setOnClickListener(v->{boolean next=entries.getVisibility()!=View.VISIBLE;entries.setVisibility(next?View.VISIBLE:View.GONE);prefs.edit().putBoolean(group,next).apply();heading.setText((next?"▾ ":"▸ ")+group.substring(0,1).toUpperCase(Locale.UK)+group.substring(1)+" routes");});panel.addView(heading);panel.addView(entries);entries.setVisibility(open?View.VISIBLE:View.GONE);
                List<String> keys=new ArrayList<>();for(String key:s.catalogue.routes.keySet())if(group.equals(s.catalogue.routes.get(key).optString("group")))keys.add(key);
                keys.sort((x,y)->{try{return Integer.compare(Integer.parseInt(x.split(":")[1]),Integer.parseInt(y.split(":")[1]));}catch(Exception e){return x.compareTo(y);}});
                if(keys.isEmpty())entries.addView(text(a,"No independently verified "+group+" identities in this checkpoint.",13,0xFFB9C5D8));
                for(String key:keys){org.json.JSONObject route=s.catalogue.routes.get(key);double length=route.getDouble("length_m"),covered=s.ledger.metres(key,mode);boolean measurable=route.optBoolean("measurable",true);
                    LinearLayout row=new LinearLayout(a);row.setOrientation(android.widget.LinearLayout.VERTICAL);row.setPadding(dp(a,14),dp(a,10),dp(a,14),dp(a,10));row.setBackground(JourneyCardHeader.background(a,0xFF182F62,0xFF46649E,12));LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2);rp.bottomMargin=dp(a,8);entries.addView(row,rp);
                    // Keep identity and figures in one accessible header; purple identity matches the map.
                    row.addView(text(a,route.optString("name",key)+(measurable?String.format(Locale.UK," · %.1f%%",100*covered/length):" · assessment held"),17,IDENTITY));
                    row.addView(text(a,measurable?DistanceUnits.format(a,covered)+" travelled / "+DistanceUnits.format(a,length)+" verified open":"Preserved alternative inventory",13,0xFFB9C5D8));
                    if(measurable)row.addView(new RoadCompletionBar(a,covered/length,PURPLE),new LinearLayout.LayoutParams(-1,dp(a,8)));
                    TextView viewMap=action(a,"VIEW MAP  →",()->open(a,key));viewMap.setContentDescription("View map and contributing journeys for "+route.optString("name",key));row.addView(viewMap);
                    row.setMinimumHeight(dp(a,48));row.setFocusable(true);row.setOnClickListener(v->open(a,key));rows.add(row);terms.add((key+" "+route.optString("name")).toLowerCase(Locale.UK));
                }
            }
            Runnable filter=()->{String q=search.getText().toString().trim().toLowerCase(Locale.UK);prefs.edit().putString("search",q).apply();if(q.isEmpty())for(Map.Entry<String,LinearLayout> group:groups.entrySet())group.getValue().setVisibility(prefs.getBoolean(group.getKey(),group.getKey().equals("national"))?View.VISIBLE:View.GONE);for(int i=0;i<rows.size();i++){View row=rows.get(i);row.setVisibility(q.isEmpty()||terms.get(i).contains(q)?View.VISIBLE:View.GONE);if(!q.isEmpty()&&row.getVisibility()==View.VISIBLE)((View)row.getParent()).setVisibility(View.VISIBLE);}};
            search.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence v,int st,int count,int after){}public void onTextChanged(CharSequence v,int st,int before,int count){filter.run();}public void afterTextChanged(android.text.Editable e){}});filter.run();
            panel.addView(text(a,s.catalogue.routes.size()+" supported identities · "+s.catalogue.heldCount+" evidence holds excluded. Open reference gaps remain in totals. Deferred alternatives earn no inferred credit. Purple completed; solid orange verified-open remaining.",12,0xFFB9C5D8));panel.addView(text(a,s.catalogue.attribution,11,0xFFB9C5D8));
        }catch(Exception e){panel.addView(text(a,"NCN progress unavailable. Retry assessment.",14,Color.WHITE));}
    }
    static void open(Activity a,String route){RoadDetailSelection selection=new RoadDetailSelection(RoadDetailSelection.NCN,route,"","","");selection.ncnMode=NcnProgressStore.mode(a);a.startActivity(selection.intent(a,false));}
    static TextView action(Activity a,String label,Runnable click){TextView v=text(a,label,14,0xFFF7C450);v.setMinHeight(dp(a,48));v.setFocusable(true);v.setOnClickListener(w->click.run());return v;}
    static int dp(Activity a,int n){return Math.round(n*a.getResources().getDisplayMetrics().density);}
    static TextView text(Activity a,String value,int size,int color){TextView v=new TextView(a);v.setText(value);v.setTextSize(size);v.setTextColor(color);v.setPadding(0,dp(a,8),0,dp(a,8));return v;}
}
