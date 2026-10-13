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
final class VenueVisitStore {
    static final String PREFS="roadprints_venue_visits_v1";
    private static long generation;
    private static boolean replacingHistory;
    private VenueVisitStore(){}
    static long revision(Context context,String collection){return context.getSharedPreferences(PREFS,0).getLong(collection+":revision",0);}
    private static JSONObject read(Context context,String collection){
        migrate(context,collection);
        try{return new JSONObject(context.getSharedPreferences(PREFS,0).getString(collection+":records","{}"));}
        catch(Exception invalid){return new JSONObject();}
    }
    private static void write(Context context,String collection,JSONObject records){
        android.content.SharedPreferences p=context.getSharedPreferences(PREFS,0);
        if(!p.edit().putString(collection+":records",records.toString()).putLong(collection+":revision",p.getLong(collection+":revision",0)+1).commit())
            throw new IllegalStateException("Pub visit could not be saved");
    }
    static synchronized Set<String> completed(Context context,String collection){
        Set<String> result=new HashSet<>();JSONObject records=read(context,collection);
        java.util.Iterator<String> keys=records.keys();
        while(keys.hasNext()){
            JSONObject record=records.optJSONObject(keys.next());
            if(record!=null&&!record.optBoolean("pending")&&!record.optBoolean("dismissed")&&!record.optString("venue_id").isEmpty())result.add(record.optString("venue_id"));
        }
        return result;
    }
    static synchronized JSONArray records(Context context,String collection){
        JSONArray result=new JSONArray();JSONObject saved=read(context,collection);
        java.util.Iterator<String> keys=saved.keys();
        while(keys.hasNext()){
            String key=keys.next();JSONObject record=saved.optJSONObject(key);
            if(record!=null&&!record.optBoolean("pending")&&!record.optBoolean("dismissed")&&!record.optString("venue_id").isEmpty())try{result.put(record.put("record_key",key));}
            catch(Exception invalid){throw new IllegalStateException(invalid);}
        }
        return result;
    }
    static void ensureHistory(Context context,String collection,Runnable callback){
        Context app=context.getApplicationContext();
        if(!CollectionAccessStore.installed(app,collection))return;
        ScreenDataLoader.execute(()->{
            try{reconcile(app,collection,VenueCollection.catalogue(app,collection));}
            catch(Exception error){android.util.Log.w("Roadprints","Pub history reconciliation failed",error);}
            if(callback!=null)new android.os.Handler(android.os.Looper.getMainLooper()).post(callback);
        });
    }
    static int reconcile(Context context,String collection,JSONArray pubs) throws Exception {
        if(!CollectionAccessStore.installed(context,collection))return 0;
        JSONObject records;long epoch;
        synchronized(VenueVisitStore.class){
            if(replacingHistory)return 0;records=read(context,collection);epoch=generation;
            boolean changed=false;java.util.Iterator<String> existing=records.keys();
            while(existing.hasNext()){
                JSONObject proof=records.optJSONObject(existing.next());if(proof==null)continue;
                JSONObject pub=find(pubs,proof.optString("venue_id"));if(pub==null)continue;
                JSONObject county=pub.optJSONObject("county");
                if(proof.optString("nation").isEmpty()&&county!=null){proof.put("nation",county.optString("nation"));changed=true;}
                if(proof.optString("town").isEmpty()&&!pub.optString("town").isEmpty()){proof.put("town",pub.optString("town"));changed=true;}
            }
            if(changed)write(context,collection,records);
        }
        JSONArray visits=TimelineVisitStore.all(context);JSONObject additions=new JSONObject();
        long now=System.currentTimeMillis();
        for(int i=0;i<visits.length();i++){
            JSONObject visit=visits.optJSONObject(i);
            if(visit==null)continue;
            String key="timeline:"+visit.optString("id");
            if(records.has(key))continue;
            JSONArray candidates=VenueEvidence.candidates(collection,visit,pubs,now);
            String match=VenueEvidence.namedMatch(collection,visit,candidates);
            JSONObject pub=find(candidates,match);
            if(pub!=null){
                additions.put(key,proof(collection,pub,"named_timeline_visit",visit.getString("start"))
                        .put("source_visit_id",visit.getString("id"))
                        .put("source_place_name",visit.optString("place_name")));
            }
        }
        synchronized(VenueVisitStore.class){
            if(epoch!=generation||replacingHistory||!CollectionAccessStore.installed(context,collection))return 0;
            records=read(context,collection);int added=0;
            java.util.Iterator<String> keys=additions.keys();
            while(keys.hasNext()){String key=keys.next();if(!records.has(key)){records.put(key,additions.get(key));added++;}}
            if(added>0)write(context,collection,records);
            return added;
        }
    }
    static JSONArray pending(Context context,String collection,JSONArray pubs) throws Exception {
        JSONArray result=new JSONArray(),visits=TimelineVisitStore.all(context);
        JSONObject records;synchronized(VenueVisitStore.class){records=read(context,collection);}
        long now=System.currentTimeMillis();
        for(int i=0;i<visits.length();i++){
            JSONObject visit=visits.optJSONObject(i);
            if(visit==null||records.has("timeline:"+visit.optString("id")))continue;
            JSONArray nearby=VenueEvidence.candidates(collection,visit,pubs,now);
            if(nearby.length()>0)result.put(new JSONObject().put("visit",visit).put("pubs",nearby));
        }
        return result;
    }
    static void confirmTimeline(Context context,String collection,String visitId,String pubId) throws Exception {
        requireInstalled(context,collection);
        long epoch;synchronized(VenueVisitStore.class){epoch=generation;}
        JSONArray pubs=VenueCollection.catalogue(context,collection),visits=TimelineVisitStore.all(context);
        for(int i=0;i<visits.length();i++){
            JSONObject visit=visits.getJSONObject(i);
            if(!visitId.equals(visit.optString("id")))continue;
            JSONObject pub=find(VenueEvidence.candidates(collection,visit,pubs,System.currentTimeMillis()),pubId);
            if(pub==null)throw new IllegalArgumentException("This Timeline visit does not support that pub");
            synchronized(VenueVisitStore.class){
                requireInstalled(context,collection);
                if(epoch!=generation)throw new IllegalStateException("Visit history changed; please try again");
                JSONObject records=read(context,collection);
                records.put("timeline:"+visitId,proof(collection,pub,"confirmed_timeline_visit",visit.getString("start"))
                        .put("source_visit_id",visitId).put("confirmed_at",Instant.now().toString()));
                write(context,collection,records);return;
            }
        }
        throw new IllegalArgumentException("The original Timeline visit is unavailable");
    }
    static synchronized void dismissTimeline(Context context,String collection,String visitId) throws Exception {
        requireInstalled(context,collection);
        JSONObject records=read(context,collection);records.put("timeline:"+visitId,new JSONObject().put("dismissed",true));
        write(context,collection,records);
    }
    static synchronized void removeProof(Context context,String collection,String key) throws Exception {
        requireInstalled(context,collection);
        JSONObject records=read(context,collection);
        if(!records.has(key))return;
        if(key.startsWith("timeline:")||key.startsWith("native:"))records.put(key,new JSONObject().put("dismissed",true));
        else if(key.startsWith("live:"))records.remove(key);
        else throw new IllegalArgumentException("Unsupported visit record");
        write(context,collection,records);
    }
    static synchronized void confirmLive(Context context,String collection,String pubId,Location fix,boolean confirmed) throws Exception {
        requireInstalled(context,collection);
        JSONObject pub=find(VenueCollection.catalogue(context,collection),pubId);
        if(pub==null||!confirmed)throw new IllegalArgumentException("Confirm the named pub first");
        String problem=VenueEvidence.liveProblem(collection,pub,fix,SystemClock.elapsedRealtimeNanos());
        if(!problem.isEmpty())throw new IllegalArgumentException(problem);
        JSONObject records=read(context,collection);
        // Repeated taps in the same quarter-hour do not create duplicate check-ins.
        String key="live:"+pubId+":"+System.currentTimeMillis()/900000L;
        records.put(key,proof(collection,pub,"confirmed_live_checkin",Instant.now().toString())
                .put("confirmed_at",Instant.now().toString()).put("accuracy_m",fix.getAccuracy())
                .put("lat",fix.getLatitude()).put("lng",fix.getLongitude()).put("fix_time_ms",fix.getTime()));
        write(context,collection,records);
    }
    private static JSONObject proof(String collection,JSONObject pub,String source,String at) throws Exception {
        return new JSONObject().put("collection_id",collection).put("venue_id",pub.getString("id")).put("name",pub.getString("name"))
                .put("town",pub.optString("town")).put("nation",pub.optJSONObject("county")==null?"":pub.optJSONObject("county").optString("nation")).put("source",source).put("visited_at",at);
    }
    static JSONObject find(JSONArray pubs,String id){
        if(id==null||id.isEmpty())return null;
        for(int i=0;i<pubs.length();i++){
            JSONObject pub=pubs.optJSONObject(i);
            if(pub!=null&&id.equals(pub.optString("id")))return pub;
        }
        return null;
    }
    private static void requireInstalled(Context context,String collection){
        if(!CollectionAccessStore.installed(context,collection))
            throw new IllegalStateException("Install this collection first");
    }
    static synchronized void clear(Context context,String collection){
        generation++;
        context.getSharedPreferences(PREFS,0).edit().remove(collection+":records").remove(collection+":revision").putBoolean(collection+":migrated",true).commit();
    }

    private static void migrate(Context context,String collection){
        android.content.SharedPreferences prefs=context.getSharedPreferences(PREFS,0);
        if(prefs.getBoolean(collection+":migrated",false))return;
        JSONObject records=new JSONObject();
        if(CollectionAccessStore.SPOONS.equals(collection))try{
            records=new JSONObject(context.getSharedPreferences(PubVisitStore.PREFS,0).getString("records","{}"));
            java.util.Iterator<String> keys=records.keys();
            while(keys.hasNext()){
                JSONObject record=records.optJSONObject(keys.next());if(record==null)continue;
                if(record.has("pub_id"))record.put("venue_id",record.getString("pub_id"));
                record.put("collection_id",collection);
            }
        }catch(Exception invalid){throw new IllegalStateException("Existing visit history could not be migrated",invalid);}
        if(!prefs.edit().putString(collection+":records",records.toString())
                .putBoolean(collection+":migrated",true).commit())throw new IllegalStateException("Visit migration could not be saved");
    }
    static synchronized void recordJourney(Context context,String collection,JSONObject journey) throws Exception {
        if(!CollectionAccessStore.installed(context,collection)||replacingHistory)return;
        String id=journey.optString("journey_id");if(id.isEmpty())return;
        JSONObject records=read(context,collection),old=new JSONObject(records.toString());
        java.util.Iterator<String> keys=old.keys();
        while(keys.hasNext()){
            String key=keys.next();JSONObject proof=records.optJSONObject(key);
            if(key.startsWith("native:"+id+":")&&proof!=null&&!proof.optBoolean("dismissed"))records.remove(key);
        }
        JSONArray outcomes=VenueEvidence.nativeVisits(collection,journey,VenueCollection.catalogue(context,collection));
        for(int i=0;i<outcomes.length();i++){
            JSONObject outcome=outcomes.getJSONObject(i);String key="native:"+id+":"+outcome.getString("stop_key");
            if(records.has(key))continue;
            JSONObject previous=old.optJSONObject(key);
            if(previous!=null&&"confirmed_native_stop".equals(previous.optString("source"))){records.put(key,previous);continue;}
            JSONObject venue=outcome.getJSONObject("venue");
            JSONObject record=proof(collection,venue,outcome.getBoolean("automatic")?"native_stop":"pending_native_stop",outcome.getString("visited_at"))
                    .put("journey_id",id).put("stop_key",outcome.getString("stop_key"))
                    .put("pending",!outcome.getBoolean("automatic")).put("evidence",outcome.getJSONObject("evidence"));
            records.put(key,record);
        }
        if(!old.toString().equals(records.toString()))write(context,collection,records);
    }
    static synchronized void forgetJourney(Context context,String id){
        for(VenueCollection.Definition definition:VenueCollection.all()){
            String collection=definition.id;JSONObject records=read(context,collection);boolean changed=false;
            java.util.List<String> remove=new java.util.ArrayList<>();java.util.Iterator<String> keys=records.keys();
            while(keys.hasNext()){String key=keys.next();JSONObject proof=records.optJSONObject(key);
                if(proof!=null&&id.equals(proof.optString("journey_id")))remove.add(key);}
            for(String key:remove){records.remove(key);changed=true;}
            if(changed)write(context,collection,records);
        }
    }
    static synchronized JSONArray nativePending(Context context,String collection){
        JSONArray out=new JSONArray();JSONObject records=read(context,collection);java.util.Iterator<String> keys=records.keys();
        while(keys.hasNext()){
            String key=keys.next();JSONObject proof=records.optJSONObject(key);
            if(proof!=null&&proof.optBoolean("pending")&&!proof.optBoolean("dismissed"))try{out.put(new JSONObject(proof.toString()).put("record_key",key));}
            catch(Exception invalid){throw new IllegalStateException(invalid);}
        }return out;
    }
    static void reviewNative(Context context,String collection,String key,boolean confirmed) throws Exception {
        String journeyId;long epoch;
        synchronized(VenueVisitStore.class){
            requireInstalled(context,collection);JSONObject record=read(context,collection).optJSONObject(key);
            if(record==null||!record.optBoolean("pending")||record.optBoolean("dismissed"))throw new IllegalArgumentException("Visit proposal unavailable");
            journeyId=record.optString("journey_id");epoch=generation;
        }
        // Journey saves take JourneyStore before this ledger; retain the same lock ordering.
        synchronized(JourneyStore.class){
            if(JourneyStore.get(context,journeyId)==null)throw new IllegalArgumentException("Original journey unavailable");
            synchronized(VenueVisitStore.class){
                requireInstalled(context,collection);JSONObject records=read(context,collection),record=records.optJSONObject(key);
                if(epoch!=generation||record==null||!record.optBoolean("pending"))throw new IllegalArgumentException("Visit evidence changed");
                if(confirmed)record.put("pending",false).put("source","confirmed_native_stop").put("confirmed_at",Instant.now().toString());
                else records.put(key,new JSONObject().put("dismissed",true).put("journey_id",journeyId));
                write(context,collection,records);
            }
        }
    }

    static synchronized void beginHistoryReplacement(){generation++;replacingHistory=true;}
    static synchronized void endHistoryReplacement(){generation++;replacingHistory=false;}
}

