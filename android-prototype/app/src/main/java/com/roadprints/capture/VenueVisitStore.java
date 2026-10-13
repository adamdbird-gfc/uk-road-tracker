package com.roadprints.capture;

import android.content.Context;
import android.location.Location;
import android.os.SystemClock;
import org.json.JSONArray;
import org.json.JSONObject;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/** Collection-scoped durable visit proofs, shared by all venue collections. */
final class VenueVisitStore {
    static final String PREFS="roadprints_venue_visits_v1";
    private static long generation;
    private static boolean replacingHistory;
    private VenueVisitStore(){}
    static long revision(Context context,String collection){return context.getSharedPreferences(PREFS,0).getLong(collection+":revision",0);}
    private static JSONObject read(Context context,String collection){
        migrate(context,collection);
        try{return new JSONObject(context.getSharedPreferences(PREFS,0).getString(collection+":records","{}"));}
        catch(Exception invalid){throw new IllegalStateException("Visit history could not be read",invalid);}
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
        return groupSessions(result);
    }
    private static long time(JSONObject record){try{return Instant.parse(record.optString("visited_at")).toEpochMilli();}catch(Exception absent){return 0;}}
    private static boolean sameSession(JSONObject a,JSONObject b){
        long one=time(a),two=time(b);
        if(one<=0||two<=0||!a.optString("venue_id").equals(b.optString("venue_id")))return false;
        JSONObject ae=a.optJSONObject("evidence"),be=b.optJSONObject("evidence");
        long oneEnd=ae==null?one:Math.max(one,ae.optLong("to_ms",one)),twoEnd=be==null?two:Math.max(two,be.optLong("to_ms",two));
        return one<=twoEnd+900000&&two<=oneEnd+900000;
    }
    private static JSONArray groupSessions(JSONArray rows){
        JSONArray out=new JSONArray();
        try{for(int i=0;i<rows.length();i++){
            JSONObject row=rows.getJSONObject(i),group=null;
            for(int j=0;j<out.length();j++)if(sameSession(out.getJSONObject(j),row)){group=out.getJSONObject(j);break;}
            if(group==null){group=new JSONObject(row.toString()).put("evidence_records",new JSONArray());out.put(group);}
            group.getJSONArray("evidence_records").put(new JSONObject(row.toString()));
        }}catch(Exception invalid){throw new IllegalStateException(invalid);}return out;
    }
    static void ensureHistory(Context context,String collection,Runnable callback){
        Context app=context.getApplicationContext();
        if(!CollectionAccessStore.installed(app,collection))return;
        ScreenDataLoader.execute(()->{
            try{reconcile(app,collection,VenueCollection.catalogue(app,collection));ensureNativeHistory(app,collection);}
            catch(Exception error){android.util.Log.w("Roadprints","Pub history reconciliation failed",error);}
            if(callback!=null)new android.os.Handler(android.os.Looper.getMainLooper()).post(callback);
        });
    }
    private static void ensureNativeHistory(Context context,String collection)throws Exception{
        long revision=JourneyStore.dataRevision(context),epoch;
        synchronized(VenueVisitStore.class){
            if(replacingHistory)return;epoch=generation;
            if(context.getSharedPreferences(PREFS,0).getLong(collection+":native_scan_revision",-1)==revision)return;
        }
        JourneyStore.forEachServiceStationEvidence(context,journey->{
            synchronized(VenueVisitStore.class){if(epoch==generation&&!replacingHistory)try{recordJourney(context,collection,journey);}catch(Exception invalid){throw new IllegalStateException("Saved visit evidence could not be evaluated",invalid);}}
        });
        long currentRevision=JourneyStore.dataRevision(context);
        synchronized(VenueVisitStore.class){if(epoch==generation&&currentRevision==revision)
            context.getSharedPreferences(PREFS,0).edit().putLong(collection+":native_scan_revision",revision).apply();}
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
        // Reconcile changed/deleted source visits, retaining historical venue IDs across catalogue removals.
        java.util.Map<String,JSONObject> sources=new java.util.HashMap<>();
        for(int i=0;i<visits.length();i++){JSONObject visit=visits.optJSONObject(i);if(visit!=null)sources.put(visit.optString("id"),visit);}
        synchronized(VenueVisitStore.class){
            if(epoch!=generation||replacingHistory)return 0;
            records=read(context,collection);java.util.List<String> invalid=new java.util.ArrayList<>();java.util.Iterator<String> keys=records.keys();
            while(keys.hasNext()){
                String key=keys.next();JSONObject proof=records.optJSONObject(key);if(!key.startsWith("timeline:")||proof==null||proof.optBoolean("dismissed"))continue;
                JSONObject source=sources.get(proof.optString("source_visit_id"));
                if(source==null||!PubVisitEvidence.validVisit(source,now)){invalid.add(key);continue;}
                JSONObject venue=find(pubs,proof.optString("venue_id"));if(venue==null)continue;
                JSONArray nearby=VenueEvidence.candidates(collection,source,pubs,now);
                if(find(nearby,proof.optString("venue_id"))==null
                        ||("named_timeline_visit".equals(proof.optString("source"))&&!proof.optString("venue_id").equals(VenueEvidence.namedMatch(collection,source,nearby))))invalid.add(key);
            }
            for(String key:invalid)records.remove(key);
            if(!invalid.isEmpty())write(context,collection,records);
        }
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
        JSONObject target=records.optJSONObject(key);java.util.List<String> remove=new java.util.ArrayList<>();
        java.util.Iterator<String> keys=records.keys();
        while(keys.hasNext()){String candidate=keys.next();JSONObject row=records.optJSONObject(candidate);
            if(candidate.equals(key)||(row!=null&&sameSession(target,row)))remove.add(candidate);}
        for(String candidate:remove){JSONObject row=records.optJSONObject(candidate);
            if(candidate.startsWith("timeline:")||candidate.startsWith("native:")||candidate.startsWith("legacy-"))
                records.put(candidate,new JSONObject().put("dismissed",true).put("journey_id",row.optString("journey_id")).put("venue_id",row.optString("venue_id")));
            else if(candidate.startsWith("live:"))records.remove(candidate);
            else throw new IllegalArgumentException("Unsupported visit record");
        }
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
        context.getSharedPreferences(PREFS,0).edit().remove(collection+":records").remove(collection+":revision").remove(collection+":native_scan_revision").putBoolean(collection+":migrated",true).commit();
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
        if(CollectionAccessStore.SERVICES.equals(collection))try{
            android.content.SharedPreferences legacy=context.getSharedPreferences("roadprints_collections_v1",0);
            JSONObject links=new JSONObject(legacy.getString("service_station_detected_journey_visits_v2","{}"));
            java.util.Iterator<String> journeys=links.keys();
            while(journeys.hasNext()){
                String id=journeys.next();JSONArray ids=links.optJSONArray(id);if(ids==null)continue;
                for(int i=0;i<ids.length();i++){
                    String venue=ids.optString(i);JSONObject row=find(VenueCollection.catalogue(context,collection),venue);
                    if(row!=null)records.put("legacy-native:"+id+":"+venue,proof(collection,row,"legacy_journey_visit","").put("journey_id",id));
                }
            }
            if(legacy.getBoolean("confirmed_timeline_only_v1",false))for(String venue:legacy.getStringSet("service_station_automatic",java.util.Collections.emptySet())){
                JSONObject row=find(VenueCollection.catalogue(context,collection),venue);
                if(row!=null)records.put("legacy-timeline:"+venue,proof(collection,row,"legacy_timeline_visit",""));
            }
        }catch(Exception invalid){throw new IllegalStateException("Existing Services history could not be migrated",invalid);}
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
            if(proof!=null&&id.equals(proof.optString("journey_id"))&&!proof.optBoolean("dismissed"))records.remove(key);
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
        generation++;
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
            if(proof!=null&&proof.optBoolean("pending")&&!proof.optBoolean("dismissed"))try{
                boolean alreadyConfirmed=false;java.util.Iterator<String> other=records.keys();
                while(other.hasNext()){JSONObject accepted=records.optJSONObject(other.next());
                    if(accepted!=null&&!accepted.optBoolean("pending")&&!accepted.optBoolean("dismissed")&&sameSession(accepted,proof))alreadyConfirmed=true;}
                if(!alreadyConfirmed)out.put(new JSONObject(proof.toString()).put("record_key",key));
            }
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
                else records.put(key,new JSONObject().put("dismissed",true).put("journey_id",journeyId).put("venue_id",record.optString("venue_id")));
                write(context,collection,records);
            }
        }
    }

    static synchronized JSONObject serviceLinks(Context context){
        JSONObject out=new JSONObject();JSONArray rows=records(context,CollectionAccessStore.SERVICES);
        try{for(int i=0;i<rows.length();i++){
            JSONObject row=rows.getJSONObject(i);String id=row.optString("journey_id");if(id.isEmpty())continue;
            JSONArray ids=out.optJSONArray(id);if(ids==null){ids=new JSONArray();out.put(id,ids);}
            boolean exists=false;for(int j=0;j<ids.length();j++)exists|=row.optString("venue_id").equals(ids.optString(j));
            if(!exists)ids.put(row.optString("venue_id"));
        }}catch(Exception invalid){throw new IllegalStateException(invalid);}return out;
    }
    static synchronized boolean replaceServiceJourney(Context context,String journeyId,Set<String> ids){
        if(journeyId==null||journeyId.isEmpty())return false;
        String collection=CollectionAccessStore.SERVICES;JSONObject records=read(context,collection),before;
        try{
            before=new JSONObject(records.toString());java.util.Iterator<String> keys=before.keys();Set<String> kept=new HashSet<>();
            while(keys.hasNext()){
                String key=keys.next();JSONObject row=records.optJSONObject(key);
                if(row==null||!journeyId.equals(row.optString("journey_id")))continue;
                String venue=row.optString("venue_id");
                if(!venue.isEmpty()&&ids.contains(venue))kept.add(venue);
                else if(!row.optBoolean("dismissed"))records.remove(key);
            }
            for(String id:ids)if(!kept.contains(id)){
                String key="legacy-native:"+journeyId+":"+id;
                if(records.has(key))continue;
                JSONObject row=find(VenueCollection.catalogue(context,collection),id);
                // Synthetic/legacy unknown identities retain the ID without inventing venue metadata.
                if(row==null)row=new JSONObject().put("id",id).put("name",id);
                records.put(key,proof(collection,row,"legacy_journey_visit","").put("journey_id",journeyId));
            }
            if(before.toString().equals(records.toString()))return false;
            write(context,collection,records);return true;
        }catch(Exception invalid){throw new IllegalStateException(invalid);}
    }
    static synchronized boolean replaceAllServiceJourneys(Context context,JSONObject links){
        JSONObject before=serviceLinks(context);java.util.Set<String> journeys=new HashSet<>();
        java.util.Iterator<String> keys=before.keys();while(keys.hasNext())journeys.add(keys.next());
        keys=links.keys();while(keys.hasNext())journeys.add(keys.next());boolean changed=false;
        for(String journey:journeys){Set<String> ids=new HashSet<>();JSONArray rows=links.optJSONArray(journey);if(rows!=null)for(int i=0;i<rows.length();i++)ids.add(rows.optString(i));
            changed|=replaceServiceJourney(context,journey,ids);
        }return changed;
    }
    static synchronized void replaceServiceTimeline(Context context,Set<String> ids){
        String collection=CollectionAccessStore.SERVICES;JSONObject records=read(context,collection);
        try{
            JSONObject old=new JSONObject(records.toString());java.util.Iterator<String> keys=old.keys();
            while(keys.hasNext()){String key=keys.next();JSONObject row=records.optJSONObject(key);
                if(key.startsWith("legacy-timeline:")&&row!=null&&!row.optBoolean("dismissed")&&!ids.contains(row.optString("venue_id")))records.remove(key);}
            for(String id:ids){String key="legacy-timeline:"+id;if(records.has(key))continue;
                JSONObject venue=find(VenueCollection.catalogue(context,collection),id);if(venue!=null)records.put(key,proof(collection,venue,"legacy_timeline_visit",""));}
            if(!old.toString().equals(records.toString()))write(context,collection,records);
        }catch(Exception invalid){throw new IllegalStateException(invalid);}
    }
    static synchronized void beginHistoryReplacement(){generation++;replacingHistory=true;}
    static synchronized void endHistoryReplacement(){generation++;replacingHistory=false;}
}

