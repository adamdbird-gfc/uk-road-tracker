package com.roadprints.capture;

import android.content.Context;
import android.location.Location;
import android.os.SystemClock;
import org.json.JSONArray;
import org.json.JSONObject;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/** Durable pub-specific proofs; catalogue refreshes never erase accepted visits. */
final class PubVisitStore {
    static final String PREFS="roadprints_pub_visits_v1";
    private static long generation;
    private static boolean replacingHistory;
    private PubVisitStore(){}
    static String visitedLabel(int count){return count+(count==1?" pub visited":" pubs visited");}
    private static JSONObject read(Context context){
        try{return new JSONObject(context.getSharedPreferences(PREFS,0).getString("records","{}"));}
        catch(Exception invalid){return new JSONObject();}
    }
    private static void write(Context context,JSONObject records){
        android.content.SharedPreferences p=context.getSharedPreferences(PREFS,0);
        if(!p.edit().putString("records",records.toString()).putLong("revision",p.getLong("revision",0)+1).commit())
            throw new IllegalStateException("Pub visit could not be saved");
    }
    static synchronized Set<String> completed(Context context){
        Set<String> result=new HashSet<>();JSONObject records=read(context);
        java.util.Iterator<String> keys=records.keys();
        while(keys.hasNext()){
            JSONObject record=records.optJSONObject(keys.next());
            if(record!=null&&!record.optString("pub_id").isEmpty())result.add(record.optString("pub_id"));
        }
        return result;
    }
    static synchronized JSONArray records(Context context){
        JSONArray result=new JSONArray();JSONObject saved=read(context);
        java.util.Iterator<String> keys=saved.keys();
        while(keys.hasNext()){
            String key=keys.next();JSONObject record=saved.optJSONObject(key);
            if(record!=null&&!record.optString("pub_id").isEmpty())try{result.put(record.put("record_key",key));}
            catch(Exception invalid){throw new IllegalStateException(invalid);}
        }
        return result;
    }
    static void ensureHistory(Context context,Runnable callback){
        Context app=context.getApplicationContext();
        if(!CollectionAccessStore.installed(app,CollectionAccessStore.SPOONS))return;
        ScreenDataLoader.execute(()->{
            try{reconcile(app,CollectionMapCatalog.spoons(app));}
            catch(Exception error){android.util.Log.w("Roadprints","Pub history reconciliation failed",error);}
            if(callback!=null)new android.os.Handler(android.os.Looper.getMainLooper()).post(callback);
        });
    }
    static int reconcile(Context context,JSONArray pubs) throws Exception {
        if(!CollectionAccessStore.installed(context,CollectionAccessStore.SPOONS))return 0;
        JSONObject records;long epoch;
        synchronized(PubVisitStore.class){if(replacingHistory)return 0;records=read(context);epoch=generation;}
        JSONArray visits=TimelineVisitStore.all(context);JSONObject additions=new JSONObject();
        long now=System.currentTimeMillis();
        for(int i=0;i<visits.length();i++){
            JSONObject visit=visits.optJSONObject(i);
            if(visit==null)continue;
            String key="timeline:"+visit.optString("id");
            if(records.has(key))continue;
            JSONArray candidates=PubVisitEvidence.candidates(visit,pubs,now);
            String match=PubVisitEvidence.namedMatch(visit,candidates);
            JSONObject pub=find(candidates,match);
            if(pub!=null){
                additions.put(key,proof(pub,"named_timeline_visit",visit.getString("start"))
                        .put("source_visit_id",visit.getString("id"))
                        .put("source_place_name",visit.optString("place_name")));
            }
        }
        synchronized(PubVisitStore.class){
            if(epoch!=generation||replacingHistory||!CollectionAccessStore.installed(context,CollectionAccessStore.SPOONS))return 0;
            records=read(context);int added=0;
            java.util.Iterator<String> keys=additions.keys();
            while(keys.hasNext()){String key=keys.next();if(!records.has(key)){records.put(key,additions.get(key));added++;}}
            if(added>0)write(context,records);
            return added;
        }
    }
    static JSONArray pending(Context context,JSONArray pubs) throws Exception {
        JSONArray result=new JSONArray(),visits=TimelineVisitStore.all(context);
        JSONObject records;synchronized(PubVisitStore.class){records=read(context);}
        long now=System.currentTimeMillis();
        for(int i=0;i<visits.length();i++){
            JSONObject visit=visits.optJSONObject(i);
            if(visit==null||records.has("timeline:"+visit.optString("id")))continue;
            JSONArray nearby=PubVisitEvidence.candidates(visit,pubs,now);
            if(nearby.length()>0)result.put(new JSONObject().put("visit",visit).put("pubs",nearby));
        }
        return result;
    }
    static void confirmTimeline(Context context,String visitId,String pubId) throws Exception {
        requireInstalled(context);
        long epoch;synchronized(PubVisitStore.class){epoch=generation;}
        JSONArray pubs=CollectionMapCatalog.spoons(context),visits=TimelineVisitStore.all(context);
        for(int i=0;i<visits.length();i++){
            JSONObject visit=visits.getJSONObject(i);
            if(!visitId.equals(visit.optString("id")))continue;
            JSONObject pub=find(PubVisitEvidence.candidates(visit,pubs,System.currentTimeMillis()),pubId);
            if(pub==null)throw new IllegalArgumentException("This Timeline visit does not support that pub");
            synchronized(PubVisitStore.class){
                requireInstalled(context);
                if(epoch!=generation)throw new IllegalStateException("Visit history changed; please try again");
                JSONObject records=read(context);
                records.put("timeline:"+visitId,proof(pub,"confirmed_timeline_visit",visit.getString("start"))
                        .put("source_visit_id",visitId).put("confirmed_at",Instant.now().toString()));
                write(context,records);return;
            }
        }
        throw new IllegalArgumentException("The original Timeline visit is unavailable");
    }
    static synchronized void dismissTimeline(Context context,String visitId) throws Exception {
        requireInstalled(context);
        JSONObject records=read(context);records.put("timeline:"+visitId,new JSONObject().put("dismissed",true));
        write(context,records);
    }
    static synchronized void removeProof(Context context,String key) throws Exception {
        requireInstalled(context);
        JSONObject records=read(context);
        if(!records.has(key))return;
        if(key.startsWith("timeline:"))records.put(key,new JSONObject().put("dismissed",true));
        else if(key.startsWith("live:"))records.remove(key);
        else throw new IllegalArgumentException("Unsupported visit record");
        write(context,records);
    }
    static synchronized void confirmLive(Context context,String pubId,Location fix,boolean confirmed) throws Exception {
        requireInstalled(context);
        JSONObject pub=find(CollectionMapCatalog.spoons(context),pubId);
        if(pub==null||!confirmed)throw new IllegalArgumentException("Confirm the named pub first");
        String problem=PubVisitEvidence.liveProblem(pub,fix,SystemClock.elapsedRealtimeNanos());
        if(!problem.isEmpty())throw new IllegalArgumentException(problem);
        JSONObject records=read(context);
        // Repeated taps in the same quarter-hour do not create duplicate check-ins.
        String key="live:"+pubId+":"+System.currentTimeMillis()/900000L;
        records.put(key,proof(pub,"confirmed_live_checkin",Instant.now().toString())
                .put("confirmed_at",Instant.now().toString()).put("accuracy_m",fix.getAccuracy())
                .put("lat",fix.getLatitude()).put("lng",fix.getLongitude()).put("fix_time_ms",fix.getTime()));
        write(context,records);
    }
    private static JSONObject proof(JSONObject pub,String source,String at) throws Exception {
        return new JSONObject().put("pub_id",pub.getString("id")).put("name",pub.getString("name"))
                .put("town",pub.optString("town")).put("source",source).put("visited_at",at);
    }
    static JSONObject find(JSONArray pubs,String id){
        if(id==null||id.isEmpty())return null;
        for(int i=0;i<pubs.length();i++){
            JSONObject pub=pubs.optJSONObject(i);
            if(pub!=null&&id.equals(pub.optString("id")))return pub;
        }
        return null;
    }
    private static void requireInstalled(Context context){
        if(!CollectionAccessStore.installed(context,CollectionAccessStore.SPOONS))
            throw new IllegalStateException("Install the Spoons collection first");
    }
    static synchronized void clear(Context context){
        generation++;
        context.getSharedPreferences(PREFS,0).edit().clear().commit();
    }
    static synchronized void beginHistoryReplacement(){generation++;replacingHistory=true;}
    static synchronized void endHistoryReplacement(){generation++;replacingHistory=false;}
}
