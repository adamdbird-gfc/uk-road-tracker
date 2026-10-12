package com.roadprints.capture;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
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
import java.util.ArrayList;
import java.util.List;

/** Shared Roadprints-styled pub review, history and live confirmation modals. */
final class PubVisitUi {
    private static final int NAVY=0xFF0B1C50,TEAL=0xFF67D5CC,MUTED=0xFFB9C5D8,GOLD=0xFFF7C450;
    private PubVisitUi(){}
    static void importTimeline(Activity activity){
        activity.startActivity(new Intent(activity,PubTimelineImportActivity.class));
    }
    static void review(Activity activity,Runnable changed){
        LinearLayout loading=new LinearLayout(activity);
        android.widget.ProgressBar spinner=new android.widget.ProgressBar(activity);spinner.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(TEAL));loading.addView(spinner);
        AlertDialog waiting=RoadprintsDialog.show(activity,"Manual check","Finding saved place visits to review…",loading,"Cancel",null,null,null);
        ScreenDataLoader.execute(()->{
            JSONArray pending=null;
            try{pending=PubVisitStore.pending(activity,CollectionMapCatalog.spoons(activity));}
            catch(Exception unavailable){ /* Report the failure without changing source records. */ }
            final JSONArray result=pending;
            activity.runOnUiThread(()->{
                if(activity.isFinishing()||activity.isDestroyed()||!waiting.isShowing())return;
                waiting.dismiss();
                if(result==null)RoadprintsDialog.show(activity,"Manual check","Saved visits could not be read. Please try again.",null,"Close",null,null,null);
                else showSlider(activity,result,changed);
            });
        });
    }
    static AlertDialog showSlider(Activity activity,JSONArray pending,Runnable changed){
        List<JSONObject> queue=new ArrayList<>();
        for(int i=0;i<pending.length();i++){
            JSONObject item=pending.optJSONObject(i);if(item==null)continue;
            JSONArray pubs=item.optJSONArray("pubs");if(pubs==null)continue;
            for(int j=0;j<pubs.length();j++)try{
                queue.add(new JSONObject().put("visit",item.getJSONObject("visit")).put("pub",pubs.getJSONObject(j)));
            }catch(Exception invalid){throw new IllegalArgumentException(invalid);}
        }
        if(queue.isEmpty())return RoadprintsDialog.show(activity,"Manual check",
                "No saved place visits need review. Import Timeline place visits to check more of your history.",null,
                "Import from Timeline",()->importTimeline(activity),"Close",null);
        LinearLayout content=vertical(activity),summary=vertical(activity);
        summary.setBackground(background(0xFF182F62,16));summary.setPadding(dp(activity,16),dp(activity,14),dp(activity,16),dp(activity,14));
        content.addView(summary);
        TextView counter=text(activity,"",13,TEAL,true);counter.setTag("pub_review_counter");content.addView(counter);
        LinearLayout navigation=new LinearLayout(activity);navigation.setGravity(Gravity.CENTER);
        Button previous=button(activity,"‹ Previous",false),next=button(activity,"Next ›",false);
        previous.setTag("pub_review_previous");next.setTag("pub_review_next");
        navigation.addView(previous,new LinearLayout.LayoutParams(0,-2,1));navigation.addView(next,new LinearLayout.LayoutParams(0,-2,1));content.addView(navigation);
        int[] index={0};boolean[] busy={false};AlertDialog[] holder={null};
        Runnable[] render={null};
        render[0]=()->{
            if(queue.isEmpty()){holder[0].dismiss();Toast.makeText(activity,"All saved visits reviewed",Toast.LENGTH_SHORT).show();return;}
            index[0]=Math.max(0,Math.min(index[0],queue.size()-1));
            JSONObject entry=queue.get(index[0]),pub=entry.optJSONObject("pub"),visit=entry.optJSONObject("visit");
            summary.removeAllViews();
            summary.addView(text(activity,pub.optString("name"),24,Color.WHITE,true));
            if(!pub.optString("town").isEmpty())summary.addView(text(activity,pub.optString("town"),15,MUTED,false));
            summary.addView(text(activity,date(visit.optString("start")),16,TEAL,true));
            String place=visit.optString("place_name");if(place.isEmpty())place="Unnamed Timeline place";
            summary.addView(text(activity,"Recorded place: "+place,14,MUTED,false));
            summary.addView(text(activity,"Confirm only if you visited this pub, rather than another place nearby.",14,MUTED,false));
            counter.setText((index[0]+1)+" of "+queue.size());
            previous.setEnabled(!busy[0]&&index[0]>0);next.setEnabled(!busy[0]&&index[0]+1<queue.size());
        };
        java.util.function.Consumer<Boolean> save=confirm->{
            if(busy[0]||queue.isEmpty())return;
            busy[0]=true;
            JSONObject entry=queue.get(index[0]);String visitId=entry.optJSONObject("visit").optString("id");
            String pubId=entry.optJSONObject("pub").optString("id");
            previous.setEnabled(false);next.setEnabled(false);
            ScreenDataLoader.execute(()->{
                String problem=null;
                try{if(confirm)PubVisitStore.confirmTimeline(activity,visitId,pubId);else PubVisitStore.dismissTimeline(activity,visitId);}
                catch(Exception unavailable){problem="This visit could not be saved. Please try again.";}
                final String result=problem;
                activity.runOnUiThread(()->{
                    if(activity.isFinishing()||activity.isDestroyed())return;
                    busy[0]=false;
                    if(result==null){
                        int removedBefore=0;
                        for(int i=0;i<index[0];i++)if(visitId.equals(queue.get(i).optJSONObject("visit").optString("id")))removedBefore++;
                        queue.removeIf(item->visitId.equals(item.optJSONObject("visit").optString("id")));
                        index[0]-=removedBefore;
                        if(changed!=null)changed.run();
                    }else Toast.makeText(activity,result,Toast.LENGTH_LONG).show();
                    if(holder[0].isShowing())render[0].run();
                });
            });
        };
        holder[0]=RoadprintsDialog.show(activity,"Manual check",null,content,"Confirm pub visit",()->save.accept(true),
                "Not a pub visit",()->save.accept(false),false);
        previous.setOnClickListener(v->{if(!busy[0]){index[0]--;render[0].run();}});
        next.setOnClickListener(v->{if(!busy[0]){index[0]++;render[0].run();}});
        float[] down={0,0};
        summary.setOnTouchListener((view,event)->{
            if(event.getAction()==MotionEvent.ACTION_DOWN){down[0]=event.getX();down[1]=event.getY();return true;}
            if(event.getAction()==MotionEvent.ACTION_UP){
                float dx=event.getX()-down[0],dy=event.getY()-down[1];
                if(!busy[0]&&Math.abs(dx)>dp(activity,48)&&Math.abs(dx)>Math.abs(dy)){
                    index[0]+=dx<0?1:-1;render[0].run();
                }else view.performClick();
                return true;
            }
            return false;
        });
        render[0].run();return holder[0];
    }
    static AlertDialog details(Activity activity,JSONObject pub,Runnable changed){
        LinearLayout body=vertical(activity);
        body.addView(text(activity,pub.optString("location_label","Reviewed map location"),13,MUTED,false));
        if(!pub.optString("attribution").isEmpty())body.addView(text(activity,pub.optString("attribution"),12,MUTED,false));
        JSONArray proofs=PubVisitStore.records(activity);int count=0;
        AlertDialog[] holder={null};
        for(int i=0;i<proofs.length();i++){
            JSONObject proof=proofs.optJSONObject(i);
            if(proof==null||!pub.optString("id").equals(proof.optString("pub_id")))continue;
            count++;
            body.addView(text(activity,date(proof.optString("visited_at"))+" · "+source(proof),15,Color.WHITE,false));
            TextView remove=text(activity,"Remove incorrect visit",14,0xFFFF8A8A,false);remove.setGravity(Gravity.CENTER);
            remove.setPadding(dp(activity,12),dp(activity,14),dp(activity,12),dp(activity,8));body.addView(remove);
            remove.setOnClickListener(v->RoadprintsDialog.show(activity,"Remove this visit?",
                    "Other supported visits will be retained. A removed Timeline visit will not return on reimport.",null,
                    "Remove visit",()->{
                        try{PubVisitStore.removeProof(activity,proof.optString("record_key"));holder[0].dismiss();if(changed!=null)changed.run();details(activity,pub,changed);}
                        catch(Exception failure){Toast.makeText(activity,"Could not remove this visit.",Toast.LENGTH_LONG).show();}
                    },"Cancel",null));
        }
        if(count==0)body.addView(text(activity,"Not visited",16,MUTED,false));
        boolean current=PubVisitStore.find(CollectionMapCatalog.spoons(activity),pub.optString("id"))!=null;
        holder[0]=RoadprintsDialog.show(activity,pub.optString("name"),pub.optString("town"),body,
                current?"Check in now":"Close",current?()->live(activity,pub,changed,null):null,current?"Close":null,null);
        return holder[0];
    }
    static AlertDialog live(Activity activity,JSONObject pub,Runnable changed,Runnable closed){
        TextView status=text(activity,"Check your location, then confirm you are inside this named pub.",15,MUTED,false);
        CancellationTokenSource[] request={null};
        AlertDialog[] holder={null};
        holder[0]=RoadprintsDialog.show(activity,pub.optString("name"),pub.optString("town"),status,
                "Check location",()->{
                    if(activity.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){
                        activity.requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},91);
                        status.setText("Grant precise location, then tap Check location again.");return;
                    }
                    if(request[0]!=null)request[0].cancel();request[0]=new CancellationTokenSource();
                    status.setText("Checking your location…");
                    try{LocationServices.getFusedLocationProviderClient(activity)
                            .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY,request[0].getToken())
                            .addOnSuccessListener(activity,fix->{
                                if(!holder[0].isShowing())return;
                                String problem=PubVisitEvidence.liveProblem(pub,fix,SystemClock.elapsedRealtimeNanos());
                                if(!problem.isEmpty()){status.setText(problem);return;}
                                RoadprintsDialog.show(activity,"Visiting "+pub.optString("name")+"?",
                                        "Confirm that you are inside this pub, rather than only at the airport, holiday park or another nearby place.",null,
                                        "Confirm pub visit",()->{
                                            try{PubVisitStore.confirmLive(activity,pub.optString("id"),fix,true);if(changed!=null)changed.run();holder[0].dismiss();}
                                            catch(Exception error){status.setText(error.getMessage());}
                                        },"Cancel",null);
                            }).addOnFailureListener(activity,error->status.setText("Location could not be checked. Please try again."));
                    }catch(SecurityException denied){status.setText("Precise location permission is required.");}
                },"Cancel",()->holder[0].dismiss(),false);
        holder[0].setOnDismissListener(dialog->{if(request[0]!=null)request[0].cancel();if(closed!=null)closed.run();});
        return holder[0];
    }
    static String date(String iso){
        try{return DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm").withZone(ZoneId.systemDefault()).format(Instant.parse(iso));}
        catch(Exception invalid){return "Date unavailable";}
    }
    private static String source(JSONObject record){
        return "confirmed_live_checkin".equals(record.optString("source"))?"Live check-in":
                "confirmed_timeline_visit".equals(record.optString("source"))?"Confirmed Timeline visit":"Timeline pub visit";
    }
    private static LinearLayout vertical(Activity activity){LinearLayout result=new LinearLayout(activity);result.setOrientation(LinearLayout.VERTICAL);return result;}
    private static TextView text(Activity activity,String label,int size,int colour,boolean bold){
        TextView result=new TextView(activity);result.setText(label);result.setTextSize(size);result.setTextColor(colour);
        result.setPadding(0,dp(activity,6),0,dp(activity,6));if(bold)result.setTypeface(null,Typeface.BOLD);return result;
    }
    private static Button button(Activity activity,String label,boolean primary){
        Button result=new Button(activity);result.setText(label);result.setAllCaps(false);result.setTextColor(primary?NAVY:TEAL);
        result.setTextSize(14);result.setMinHeight(dp(activity,48));result.setBackground(background(primary?GOLD:0xFF233B78,12));return result;
    }
    private static GradientDrawable background(int colour,int radius){GradientDrawable shape=new GradientDrawable();shape.setColor(colour);shape.setCornerRadius(radius);return shape;}
    private static int dp(Activity activity,int value){return Math.round(value*activity.getResources().getDisplayMetrics().density);}
}
