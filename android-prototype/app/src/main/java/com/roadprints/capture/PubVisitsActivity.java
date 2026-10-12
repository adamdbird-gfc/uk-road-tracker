package com.roadprints.capture;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;
import org.json.JSONArray;
import org.json.JSONObject;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/** Evidence-backed live check-ins and review of imported Timeline place visits. */
public class PubVisitsActivity extends Activity {
    private static final int NAVY=0xFF0B1C50,MUTED=0xFFD3DCED,TEAL=0xFF67D5CC;
    private LinearLayout body;
    private int loadGeneration,page;
    private CancellationTokenSource locationRequest;
    private JSONObject selectedPub;
    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20),dp(20),dp(20),dp(20));root.setBackgroundColor(NAVY);
        root.addView(RoadprintsHeader.create(this));
        root.addView(text("UK WETHERSPOONS COLLECTION",12,TEAL,true));
        root.addView(text("Pub visits",30,Color.WHITE,true));
        body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);root.addView(body);
        RoadprintsHeader.installUtilityPage(this,root,"BACK",this::finish);
    }
    @Override protected void onResume(){super.onResume();load();}
    @Override protected void onDestroy(){
        loadGeneration++;
        if(locationRequest!=null)locationRequest.cancel();
        super.onDestroy();
    }
    private void load(){
        final int generation=++loadGeneration;
        body.removeAllViews();
        android.widget.ProgressBar spinner=new android.widget.ProgressBar(this);
        body.addView(spinner,new LinearLayout.LayoutParams(dp(36),dp(36)));
        body.addView(text("Checking saved pub visits…",15,MUTED,false));
        ScreenDataLoader.execute(()->{
            JSONArray pubs=CollectionMapCatalog.spoons(this),saved=new JSONArray(),pending=new JSONArray();
            String error=null;
            try{
                PubVisitStore.reconcile(this,pubs);
                saved=PubVisitStore.records(this);pending=PubVisitStore.pending(this,pubs);
            }catch(Exception failure){error="Saved visits could not be read. Please try again.";}
            final JSONArray records=saved,review=pending;final String problem=error;
            runOnUiThread(()->{
                if(isFinishing()||isDestroyed()||generation!=loadGeneration)return;
                render(pubs,records,review,problem);
            });
        });
    }
    private void render(JSONArray pubs,JSONArray records,JSONArray pending,String problem){
        body.removeAllViews();
        if(!CollectionAccessStore.installed(this,CollectionAccessStore.SPOONS)){
            body.addView(text("Install the Spoons collection from Collections first.",16,MUTED,false));return;
        }
        Map<String,JSONObject> unique=new LinkedHashMap<>();
        for(int i=0;i<records.length();i++){
            JSONObject r=records.optJSONObject(i);if(r==null)continue;
            String id=r.optString("pub_id");JSONObject prior=unique.get(id);
            if(prior==null||r.optString("visited_at").compareTo(prior.optString("visited_at"))>0)unique.put(id,r);
        }
        body.addView(text(PubVisitStore.visitedLabel(unique.size()),23,TEAL,true));
        body.addView(text(pubs.length()+" reviewed pubs currently on the map. Visits are stored locally; achievements are coming later.",14,MUTED,false));
        if(problem!=null)body.addView(text(problem,15,0xFFF9C74F,false));
        selectedPub=PubVisitStore.find(pubs,getIntent().getStringExtra("pub_id"));
        if(selectedPub!=null){
            body.addView(text(selectedPub.optString("name"),21,Color.WHITE,true));
            body.addView(text(selectedPub.optString("location_label","Reviewed map location"),14,MUTED,false));
            body.addView(text("Check your location, then confirm that you are visiting this named pub. Precise check-in evidence stays on this device.",14,MUTED,false));
            action("CHECK LOCATION FOR CHECK-IN",this::checkLocation);
        }else body.addView(text("To check in now, open the pub's card on the map.",14,MUTED,false));
        action("IMPORT TIMELINE PLACE VISITS",()->startActivity(new Intent(this,TimelineImportActivity.class)
                .putExtra("service_only",true).putExtra("pub_visits_only",true)));
        body.addView(text("Timeline visits to review",22,Color.WHITE,true));
        body.addView(text("Named pub visits can be recognised from Timeline. Nearby unnamed places, shared buildings and airports need your confirmation. A location alone is never a pub visit.",14,MUTED,false));
        if(pending.length()==0)body.addView(text("No nearby Timeline visits need review. Import a Timeline file if place visits have not been saved yet.",14,MUTED,false));
        if(page>=pending.length())page=0;
        int end=Math.min(page+50,pending.length());
        if(pending.length()>50)body.addView(text("Showing "+(page+1)+"–"+end+" of "+pending.length()+" visits.",14,MUTED,false));
        for(int i=page;i<end;i++){
            JSONObject item=pending.optJSONObject(i);
            JSONObject visit=item==null?null:item.optJSONObject("visit");
            JSONArray choices=item==null?null:item.optJSONArray("pubs");
            if(visit==null||choices==null)continue;
            body.addView(text(date(visit.optString("start"))+" · "+visit.optString("place_name","Unnamed Timeline place"),16,Color.WHITE,true));
            for(int j=0;j<choices.length();j++){
                JSONObject pub=choices.optJSONObject(j);if(pub==null)continue;
                action("REVIEW: "+pub.optString("name"),()->new AlertDialog.Builder(this)
                        .setTitle("Did you visit "+pub.optString("name")+"?")
                        .setMessage("Timeline recorded "+visit.optString("place_name","a nearby place")+" on "+date(visit.optString("start"))+
                                ". Confirm only if you visited this pub, rather than another place in the same building.")
                        .setNegativeButton("Cancel",null).setPositiveButton("CONFIRM PUB VISIT",(dialog,which)->
                                saveTimeline(visit.optString("id"),pub.optString("id"))).show());
            }
            action("NOT A PUB VISIT",()->{
                try{PubVisitStore.dismissTimeline(this,visit.optString("id"));load();}
                catch(Exception failure){Toast.makeText(this,"Could not save your choice. Please try again.",Toast.LENGTH_LONG).show();}
            });
        }
        if(end<pending.length())action("SHOW NEXT 50",()->{page=end;load();});
        else if(page>0)action("BACK TO FIRST 50",()->{page=0;load();});
        body.addView(text("Visited pubs",22,Color.WHITE,true));
        java.util.List<JSONObject> sorted=new java.util.ArrayList<>(unique.values());
        sorted.sort((a,b)->a.optString("name").compareToIgnoreCase(b.optString("name")));
        for(JSONObject record:sorted){
            body.addView(text(record.optString("name"),17,Color.WHITE,true));
            String source=sourceLabel(record);
            body.addView(text(date(record.optString("visited_at"))+" · "+source,14,MUTED,false));
            action("REVIEW RECORDED VISITS: "+record.optString("name"),()->{
                LinearLayout history=new LinearLayout(this);history.setOrientation(LinearLayout.VERTICAL);history.setPadding(dp(16),dp(12),dp(16),dp(12));
                final AlertDialog[] historyDialog={null};
                for(int i=0;i<records.length();i++){
                    JSONObject proof=records.optJSONObject(i);
                    if(proof==null||!record.optString("pub_id").equals(proof.optString("pub_id")))continue;
                    TextView row=new TextView(this);row.setPadding(0,dp(14),0,dp(14));
                    row.setText(date(proof.optString("visited_at"))+" · "+sourceLabel(proof)+"\nRemove if incorrect");
                    row.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("Remove this visit?")
                            .setMessage("This removes only this visit. Other supported visits will be retained. A removed Timeline visit will not be added again on reimport.")
                            .setNegativeButton("Cancel",null).setPositiveButton("REMOVE VISIT",(dialog,which)->{
                                try{PubVisitStore.removeProof(this,proof.optString("record_key"));if(historyDialog[0]!=null)historyDialog[0].dismiss();load();}
                                catch(Exception invalid){Toast.makeText(this,"Could not remove this visit.",Toast.LENGTH_LONG).show();}
                            }).show());
                    history.addView(row);
                }
                android.widget.ScrollView scroll=new android.widget.ScrollView(this);scroll.addView(history);
                historyDialog[0]=new AlertDialog.Builder(this).setTitle(record.optString("name")).setView(scroll).setPositiveButton("Close",null).create();
                historyDialog[0].show();
            });
        }
    }
    private static String sourceLabel(JSONObject record){
        return "confirmed_live_checkin".equals(record.optString("source"))?"Live check-in":
                "confirmed_timeline_visit".equals(record.optString("source"))?"Confirmed Timeline visit":"Named Timeline place visit";
    }
    private void saveTimeline(String visitId,String pubId){
        ScreenDataLoader.execute(()->{
            String problem=null;
            try{PubVisitStore.confirmTimeline(this,visitId,pubId);}
            catch(Exception failure){problem="The original visit could not support this confirmation. Please refresh and try again.";}
            final String result=problem;
            runOnUiThread(()->{if(isFinishing()||isDestroyed())return;
                Toast.makeText(this,result==null?"Pub visit saved":result,Toast.LENGTH_LONG).show();load();});
        });
    }
    private void checkLocation(){
        if(selectedPub==null)return;
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},91);
            Toast.makeText(this,"Precise location is needed. After granting it, tap Check location again.",Toast.LENGTH_LONG).show();return;
        }
        if(locationRequest!=null)locationRequest.cancel();
        locationRequest=new CancellationTokenSource();
        Toast.makeText(this,"Checking your location…",Toast.LENGTH_SHORT).show();
        try{
            LocationServices.getFusedLocationProviderClient(this)
                    .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY,locationRequest.getToken())
                    .addOnSuccessListener(this,fix->confirmLocation(fix))
                    .addOnFailureListener(this,error->Toast.makeText(this,"Location could not be checked. Please try again.",Toast.LENGTH_LONG).show());
        }catch(SecurityException denied){Toast.makeText(this,"Location permission is required.",Toast.LENGTH_LONG).show();}
    }
    private void confirmLocation(Location fix){
        if(isFinishing()||isDestroyed()||selectedPub==null)return;
        String problem=PubVisitEvidence.liveProblem(selectedPub,fix,SystemClock.elapsedRealtimeNanos());
        if(!problem.isEmpty()){Toast.makeText(this,problem,Toast.LENGTH_LONG).show();return;}
        String pubId=selectedPub.optString("id"),name=selectedPub.optString("name");
        new AlertDialog.Builder(this).setTitle("Visiting "+name+"?")
                .setMessage("Your location is nearby. Confirm that you are inside this pub, rather than only at the airport, holiday park or another nearby place.")
                .setNegativeButton("Cancel",null).setPositiveButton("CONFIRM PUB VISIT",(dialog,which)->{
                    try{PubVisitStore.confirmLive(this,pubId,fix,true);Toast.makeText(this,"Pub visit saved",Toast.LENGTH_SHORT).show();load();}
                    catch(Exception error){Toast.makeText(this,error.getMessage(),Toast.LENGTH_LONG).show();}
                }).show();
    }
    private void action(String label,Runnable click){
        TextView button=text(label,14,NAVY,true);button.setGravity(Gravity.CENTER);
        button.setPadding(dp(12),dp(13),dp(12),dp(13));
        GradientDrawable shape=new GradientDrawable();shape.setColor(0xFFF7C450);shape.setCornerRadius(dp(12));button.setBackground(shape);
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);params.topMargin=dp(8);params.bottomMargin=dp(6);
        body.addView(button,params);button.setOnClickListener(v->click.run());
    }
    private static String date(String iso){
        try{return DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm").withZone(ZoneId.systemDefault()).format(Instant.parse(iso));}
        catch(Exception invalid){return "Date unavailable";}
    }
    private TextView text(String value,float size,int colour,boolean bold){
        TextView result=new TextView(this);result.setText(value);result.setTextSize(size);result.setTextColor(colour);
        result.setPadding(0,dp(8),0,dp(4));if(bold)result.setTypeface(null,android.graphics.Typeface.BOLD);return result;
    }
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
}
