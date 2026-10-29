package com.roadprints.capture;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.*;

/** NCN within Progress: shared road identifiers and collection controls, retained query/mode. */
final class NcnProgressUi {
    static final int PURPLE=0xFF8D55C7,ORANGE=0xFFFF9B42,IDENTITY=0xFFD3B5FF;
    static String label(String mode){return mode.equals("cycling")?"Cycling":mode.equals("on_foot")?"Walking & running":"Combined";}
    static void add(Activity activity,LinearLayout parent){
        LinearLayout panel=new LinearLayout(activity);panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(activity,18),dp(activity,18),dp(activity,18),dp(activity,18));panel.setTag("progress:ncn");parent.addView(panel);
        final Object[] rendered={NcnProgressStore.current()};final boolean[] enabled={NcnProgressStore.enabled(activity)};
        final String[] renderedMode={NcnProgressStore.mode(activity)};final TextView[] statusView={null};
        Runnable update=new Runnable(){public void run(){
            if(activity.isFinishing()||activity.isDestroyed()||!panel.isAttachedToWindow())return;
            NcnProgressStore.ensure(activity);Object snapshot=NcnProgressStore.current();String mode=NcnProgressStore.mode(activity);
            if(snapshot!=rendered[0]||!mode.equals(renderedMode[0])||enabled[0]!=NcnProgressStore.enabled(activity)){
                enabled[0]=NcnProgressStore.enabled(activity);rendered[0]=snapshot;renderedMode[0]=mode;populate(activity,panel,statusView,renderedMode);
            }
            if(statusView[0]!=null)statusView[0].setText(NcnProgressStore.status());
            View retry=panel.findViewWithTag("ncn_retry");if(retry!=null)retry.setVisibility(NcnProgressStore.status().contains("could not")?View.VISIBLE:View.GONE);
            panel.postDelayed(this,1000);
        }};
        populate(activity,panel,statusView,renderedMode);
        panel.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener(){public void onViewAttachedToWindow(View v){panel.removeCallbacks(update);panel.post(update);}public void onViewDetachedFromWindow(View v){panel.removeCallbacks(update);}});panel.post(update);
    }
    private static void populate(Activity a,LinearLayout panel,TextView[] statusView,String[] renderedMode){
        panel.removeAllViews();statusView[0]=null;panel.addView(text(a,"National Cycle Network",21,Color.WHITE));
        if(!NcnProgressStore.enabled(a)){
            panel.addView(text(a,"Explore verified-open NCN sections with walking, running and cycling journeys.",14,Color.WHITE));
            panel.addView(action(a,"ENABLE NCN COLLECTION",()->{NcnProgressStore.activate(a,true);populate(a,panel,statusView,renderedMode);}));return;
        }
        statusView[0]=text(a,NcnProgressStore.status(),13,0xFF67D5CC);panel.addView(statusView[0]);
        TextView retry=action(a,"RETRY ASSESSMENT",()->NcnProgressStore.retry(a));retry.setTag("ncn_retry");retry.setVisibility(NcnProgressStore.status().contains("could not")?View.VISIBLE:View.GONE);panel.addView(retry);
        NcnProgressStore.Snapshot snapshot=NcnProgressStore.current();if(snapshot==null)return;
        try{
            android.content.SharedPreferences prefs=a.getSharedPreferences("ncn_ui",0);
            CoverageSummary summary=new CoverageSummary(a,PURPLE);summary.setTag("ncn_network_summary");panel.addView(summary,new LinearLayout.LayoutParams(-1,-2));
            TextView searchLabel=text(a,"FIND A ROUTE",11,0xFF67D5CC);searchLabel.setTypeface(null,Typeface.BOLD);searchLabel.setPadding(0,dp(a,16),0,dp(a,6));panel.addView(searchLabel);
            LinearLayout searchRow=new LinearLayout(a);searchRow.setGravity(Gravity.CENTER_VERTICAL);
            EditText search=VenueProgressPanel.searchField(a,"Search route number or name","ncn_search");search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);search.setText(prefs.getString("search",""));
            searchRow.addView(search,new LinearLayout.LayoutParams(0,-2,1));
            Button clear=VenueProgressPanel.button(a,"Clear");clear.setTag("ncn_clear_search");clear.setContentDescription("Clear NCN route search");VenueProgressPanel.styleFilter(a,clear,false);
            LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(dp(a,60),dp(a,48));cp.leftMargin=dp(a,8);searchRow.addView(clear,cp);panel.addView(searchRow);
            clear.setOnClickListener(v->{search.setText("");dismissKeyboard(a,search);});
            search.setOnEditorActionListener((v,id,event)->{if(id==EditorInfo.IME_ACTION_SEARCH||id==EditorInfo.IME_ACTION_DONE){dismissKeyboard(a,search);return true;}return false;});
            LinearLayout modes=new LinearLayout(a);panel.addView(modes);Map<String,Button> choices=new LinkedHashMap<>();
            List<RouteRow> rows=new ArrayList<>();List<Group> groups=new ArrayList<>();
            TextView matches=text(a,"",12,0xFFB9C5D8);matches.setTag("ncn_matches");matches.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);panel.addView(matches);
            Set<String> searchCollapsed=new HashSet<>();
            for(String id:new String[]{"national","regional","named"}){
                Group group=new Group();group.id=id;group.title=id.substring(0,1).toUpperCase(Locale.UK)+id.substring(1)+" routes";
                group.heading=action(a,"",()->{});group.heading.setTag("ncn_group_header:"+id);panel.addView(group.heading);
                group.entries=new LinearLayout(a);group.entries.setOrientation(LinearLayout.VERTICAL);group.entries.setTag("ncn_group:"+id);panel.addView(group.entries);groups.add(group);
                List<String> keys=new ArrayList<>();for(String key:snapshot.catalogue.routes.keySet())if(id.equals(snapshot.catalogue.routes.get(key).optString("group")))keys.add(key);
                keys.sort((x,y)->{try{return Integer.compare(Integer.parseInt(x.split(":")[1]),Integer.parseInt(y.split(":")[1]));}catch(Exception e){return x.compareTo(y);}});
                if(keys.isEmpty())group.entries.addView(text(a,"No independently verified "+id+" identities in this checkpoint.",13,0xFFB9C5D8));
                for(String key:keys){RouteRow row=new RouteRow(a,key,snapshot.catalogue.routes.get(key));group.rows.add(row);rows.add(row);group.entries.addView(row.view);}
            }
            boolean[] modeFailed={false};
            Runnable filter=()->{
                if(modeFailed[0]){for(Group group:groups){group.heading.setVisibility(View.GONE);group.entries.setVisibility(View.GONE);}matches.setText("NCN progress could not refresh. Select an activity to retry.");return;}
                String query=search.getText().toString().trim();prefs.edit().putString("search",query).apply();String q=normalise(query);int count=0;
                for(Group group:groups){int found=0;for(RouteRow row:group.rows){boolean visible=q.isEmpty()||row.term.contains(q);row.view.setVisibility(visible?View.VISIBLE:View.GONE);if(visible)found++;}count+=found;
                    boolean show=q.isEmpty()||found>0;group.heading.setVisibility(show?View.VISIBLE:View.GONE);
                    boolean open=q.isEmpty()?prefs.getBoolean(group.id,group.id.equals("national")):!searchCollapsed.contains(group.id);
                    group.entries.setVisibility(show&&open?View.VISIBLE:View.GONE);group.heading.setText((open?"▾ ":"▸ ")+group.title);
                    group.heading.setContentDescription(group.title+", "+found+" matching routes, "+(open?"expanded":"collapsed"));
                }
                matches.setText(count==0?"No matching routes. Try another search or clear your search.":count+(count==1?" route shown · ":" routes shown · ")+label(NcnProgressStore.mode(a)));
            };
            for(Group group:groups)group.heading.setOnClickListener(v->{
                boolean next=group.entries.getVisibility()!=View.VISIBLE;
                if(search.getText().toString().trim().isEmpty())prefs.edit().putBoolean(group.id,next).apply();else if(next)searchCollapsed.remove(group.id);else searchCollapsed.add(group.id);
                filter.run();
            });
            Runnable refreshMode=()->{
                String mode=NcnProgressStore.mode(a);renderedMode[0]=mode;
                for(Map.Entry<String,Button> choice:choices.entrySet())VenueProgressPanel.styleFilter(a,choice.getValue(),choice.getKey().equals(mode));
                try{
                    double done=snapshot.ledger.networkMetres(mode);
                    for(RouteRow row:rows)row.bind(a,snapshot,mode);
                    summary.bind("NCN network",done,snapshot.catalogue.networkLength,"Unique verified-open network · "+label(mode));summary.setVisibility(View.VISIBLE);modeFailed[0]=false;
                }catch(Exception failure){summary.setVisibility(View.GONE);modeFailed[0]=true;}
                filter.run();
            };
            for(String mode:new String[]{"combined","cycling","on_foot"}){
                Button choice=VenueProgressPanel.button(a,label(mode));choice.setTag("ncn_mode:"+mode);choices.put(mode,choice);
                VenueProgressPanel.addCompactButton(a,modes,choice,choices.size()==1?0:8);
                choice.setOnClickListener(v->{dismissKeyboard(a,search);NcnProgressStore.mode(a,mode);refreshMode.run();});
            }
            search.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence v,int st,int count,int after){}public void onTextChanged(CharSequence v,int st,int before,int count){searchCollapsed.clear();filter.run();}public void afterTextChanged(android.text.Editable e){}});
            refreshMode.run();
            panel.addView(text(a,snapshot.catalogue.routes.size()+" supported identities · "+snapshot.catalogue.heldCount+" evidence holds excluded. Open reference gaps remain in totals. Deferred alternatives earn no inferred credit. Purple completed; solid orange verified-open remaining.",12,0xFFB9C5D8));panel.addView(text(a,snapshot.catalogue.attribution,11,0xFFB9C5D8));
        }catch(Exception error){panel.addView(text(a,"NCN progress unavailable. Retry assessment.",14,Color.WHITE));}
    }
    private static final class Group {String id,title;TextView heading;LinearLayout entries;List<RouteRow> rows=new ArrayList<>();}
    private static final class RouteRow {
        final String key,term;final org.json.JSONObject route;final LinearLayout view;final TextView percent,meta;final RoadCompletionBar bar;
        RouteRow(Activity a,String key,org.json.JSONObject route){
            this.key=key;this.route=route;term=normalise(key+" "+route.optString("name"));
            view=new LinearLayout(a);view.setOrientation(LinearLayout.VERTICAL);view.setTag("ncn_route:"+key);view.setMinimumHeight(dp(a,64));
            LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2);rp.bottomMargin=dp(a,6);view.setLayoutParams(rp);
            LinearLayout top=new LinearLayout(a);top.setGravity(Gravity.CENTER_VERTICAL);top.setPadding(0,dp(a,8),0,dp(a,2));
            TextView badge=RouteIdentifierBadge.create(a,key.replace(":"," "),PURPLE);badge.setTag("ncn_badge:"+key);top.addView(badge,new LinearLayout.LayoutParams(dp(a,78),dp(a,32)));
            bar=new RoadCompletionBar(a,0,PURPLE);bar.setTag("ncn_bar:"+key);LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(0,dp(a,12),1);bp.setMargins(dp(a,9),0,dp(a,9),0);top.addView(bar,bp);
            percent=text(a,"",14,Color.WHITE);percent.setTag("ncn_percent:"+key);percent.setPadding(0,0,0,0);percent.setTypeface(null,Typeface.BOLD);percent.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);top.addView(percent,new LinearLayout.LayoutParams(dp(a,55),-2));view.addView(top);
            meta=text(a,"",11,0xFFB9C5D8);meta.setPadding(dp(a,88),0,0,dp(a,4));view.addView(meta);
            view.setFocusable(true);view.setOnClickListener(v->open(a,key));
        }
        void bind(Activity a,NcnProgressStore.Snapshot snapshot,String mode)throws Exception{
            double length=route.optDouble("length_m"),covered=snapshot.ledger.metres(key,mode);boolean measurable=route.optBoolean("measurable",true);
            percent.setText(measurable?String.format(Locale.UK,"%.1f%%",length>0?100*covered/length:0):"Held");
            bar.setVisibility(measurable?View.VISIBLE:View.INVISIBLE);bar.setFraction(measurable&&length>0?covered/length:0);
            String identity=key.replace(":"," "),name=route.optString("name",identity);
            String description=(normalise(name).equals(normalise(identity))?"":name+" · ")+(measurable?DistanceUnits.format(a,covered)+" / "+DistanceUnits.format(a,length)+" reference":"Assessment held · preserved inventory");
            String value=description+" · VIEW MAP →";SpannableString styled=new SpannableString(value);styled.setSpan(new ForegroundColorSpan(0xFFF7C450),description.length()+3,value.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);meta.setText(styled);
            view.setContentDescription(identity+", "+percent.getText()+", "+description+", view map and contributing journeys");
        }
    }
    private static String normalise(String value){return value.toLowerCase(Locale.UK).replaceAll("[\\s:]+","");}
    private static void dismissKeyboard(Activity a,View input){InputMethodManager manager=(InputMethodManager)a.getSystemService(android.content.Context.INPUT_METHOD_SERVICE);if(manager!=null)manager.hideSoftInputFromWindow(input.getWindowToken(),0);input.clearFocus();if(input.getParent() instanceof View){View parent=(View)input.getParent();parent.setFocusableInTouchMode(true);parent.requestFocus();}}
    static void open(Activity a,String route){View focus=a.getCurrentFocus();if(focus!=null)dismissKeyboard(a,focus);RoadDetailSelection selection=new RoadDetailSelection(RoadDetailSelection.NCN,route,"","","");selection.ncnMode=NcnProgressStore.mode(a);a.startActivity(selection.intent(a,false));}
    static TextView action(Activity a,String label,Runnable click){TextView v=text(a,label,14,0xFFF7C450);v.setMinHeight(dp(a,48));v.setFocusable(true);v.setOnClickListener(w->click.run());return v;}
    static int dp(Activity a,int n){return Math.round(n*a.getResources().getDisplayMetrics().density);}
    static TextView text(Activity a,String value,int size,int color){TextView v=new TextView(a);v.setText(value);v.setTextSize(size);v.setTextColor(color);v.setPadding(0,dp(a,8),0,dp(a,8));return v;}
}
