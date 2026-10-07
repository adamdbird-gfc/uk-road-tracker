package com.roadprints.capture;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Entitlement gates the collection, never the retention of automatic visit evidence. */
final class ServiceStationStore {
    private static final String PREFS="roadprints_collections_v1", AUTOMATIC="service_station_automatic", REVISION="revision";
    private static final String BACKFILL="automatic_visits_v2_backfill", MIGRATION="automatic_visits_v2_migration";
    private static final Object STATE_LOCK=new Object(), TIMELINE_LOCK=new Object();
    private static final java.util.concurrent.ExecutorService BACKFILL_EXECUTOR=java.util.concurrent.Executors.newSingleThreadExecutor();
    private static final java.util.List<Runnable> CALLBACKS=new java.util.ArrayList<>();
    private static boolean backfillRunning;
    private static long generation;
    private static volatile JSONArray catalogue;
    private ServiceStationStore() {}
    static boolean unlocked(Context context) { return prefs(context).getBoolean("service_stations_unlocked",false); }
    static void unlockForTesting(Context context) {
        prefs(context).edit().putBoolean("service_stations_unlocked",true).apply();bumpRevision(context);
        ensureHistoricalVisits(context,null);
    }
    static long revision(Context context) { return prefs(context).getLong(REVISION,0); }
    private static void bumpRevision(Context context) {
        synchronized(STATE_LOCK) { prefs(context).edit().putLong(REVISION,revision(context)+1).apply(); }
    }
    static boolean historicalBackfillComplete(Context context) { ensureMigration(context);return prefs(context).getBoolean(BACKFILL,false); }
    static Set<String> automatic(Context context) { ensureMigration(context);return new HashSet<>(prefs(context).getStringSet(AUTOMATIC,java.util.Collections.emptySet())); }
    static Set<String> completed(Context context) {
        Set<String> result=automatic(context);result.addAll(ServiceStationVisitStore.stationIds(context));return result;
    }
    static JSONArray stations(Context context) throws Exception {
        JSONArray cached=catalogue;if(cached!=null)return cached;
        try(InputStream input=context.getAssets().open("uk-motorway-services-v1.json")) {
            java.io.ByteArrayOutputStream output=new java.io.ByteArrayOutputStream();byte[] buffer=new byte[8192];int n;
            while((n=input.read(buffer))!=-1)output.write(buffer,0,n);
            JSONArray result=new JSONObject(new String(output.toByteArray(),StandardCharsets.UTF_8)).optJSONArray("services");
            if(result==null)throw new IllegalStateException("Service station catalogue has no services");
            catalogue=result;return result;
        }
    }
    static int recordJourney(Context context,JSONObject journey) {
        ensureMigration(context);
        try {
            Set<String> ids=ServiceStationEvidence.visits(journey,stations(context));
            if(ServiceStationVisitStore.replaceJourney(context,journey.optString("journey_id"),ids)) {
                bumpRevision(context);
                if(unlocked(context)) BACKFILL_EXECUTOR.execute(()->AchievementStore.recognizeServiceAchievements(context));
            }
            return ids.size();
        }catch(Exception error){throw new IllegalStateException("Service station evidence could not be evaluated",error);}
    }
    static void forgetJourney(Context context,String journeyId) {
        if(ServiceStationVisitStore.replaceJourney(context,journeyId,java.util.Collections.emptySet()))bumpRevision(context);
    }
    private static void ensureMigration(Context context) {
        boolean migrated=false;
        synchronized(STATE_LOCK) {
            SharedPreferences p=prefs(context);if(p.getBoolean(MIGRATION,false))return;
            // Old proximity guesses are discarded, but confirmed Timeline matches survive.
            SharedPreferences.Editor edit=p.edit().remove("service_station_manual")
                    .remove("service_station_confirmed_journey_visits_v1").putBoolean(MIGRATION,true).putBoolean(BACKFILL,false);
            if(!p.getBoolean("confirmed_timeline_only_v1",false))edit.remove(AUTOMATIC);
            edit.putBoolean("confirmed_timeline_only_v1",true).putLong(REVISION,revision(context)+1).apply();
            migrated=true;
        }
        if(migrated)AchievementStore.clearLegacyServiceUnlocks(context);
    }
    static int recordConfirmedTimelineVisits(Context context,JSONArray visits) {
        ensureMigration(context);
        try {
            synchronized(TIMELINE_LOCK) { TimelineVisitStore.merge(context,visits);rebuildTimelineMatches(context); }
            if(unlocked(context))BACKFILL_EXECUTOR.execute(()->AchievementStore.recognizeServiceAchievements(context));
            return automatic(context).size();
        }catch(Exception error){android.util.Log.w("Roadprints","Could not persist Timeline visits",error);return 0;}
    }
    private static void rebuildTimelineMatches(Context context) throws Exception {
        long epoch; synchronized(STATE_LOCK){epoch=generation;}
        JSONArray visits=TimelineVisitStore.all(context),services=stations(context);Set<String> ids=new HashSet<>();
        for(int i=0;i<visits.length();i++) {
            JSONObject visit=visits.optJSONObject(i);if(visit==null)continue;
            JSONObject station=ServiceStationEvidence.nearest(services,visit.optDouble("lat",Double.NaN),visit.optDouble("lng",Double.NaN),350);
            if(station!=null)ids.add(station.optString("id"));
        }
        synchronized(STATE_LOCK) {
            if(epoch==generation&&!ids.equals(automatic(context))) {
                prefs(context).edit().putStringSet(AUTOMATIC,ids).apply();bumpRevision(context);
            }
        }
    }
    /** One background scan after upgrade/purchase. Each archive record is projected, not fully loaded. */
    static void ensureHistoricalVisits(Context context,Runnable onComplete) {
        Context app=context.getApplicationContext();ensureMigration(app);
        synchronized(STATE_LOCK) {
            if(prefs(app).getBoolean(BACKFILL,false)) { post(onComplete);return; }
            if(onComplete!=null)CALLBACKS.add(onComplete);
            if(backfillRunning)return;backfillRunning=true;
        }
        BACKFILL_EXECUTOR.execute(()->{
            boolean done=false;long epoch;
            synchronized(STATE_LOCK){epoch=generation;}
            try {
                for(int attempt=0;attempt<3&&!done;attempt++) {
                    long archiveRevision=JourneyStore.dataRevision(app);JSONObject records=new JSONObject();
                    JSONArray services=stations(app);
                    JourneyStore.forEachServiceStationEvidence(app,journey->{
                        Set<String> ids=ServiceStationEvidence.visits(journey,services);
                        if(!ids.isEmpty())try {records.put(journey.optString("journey_id"),ServiceStationVisitStore.array(ids));}
                        catch(Exception error){throw new IllegalStateException(error);}
                    });
                    synchronized(TIMELINE_LOCK){rebuildTimelineMatches(app);}
                    synchronized(JourneyStore.class) {
                        if(JourneyStore.dataRevision(app)!=archiveRevision)continue;
                        synchronized(STATE_LOCK) {
                            if(epoch!=generation)break;

                            if(ServiceStationVisitStore.replaceAll(app,records))bumpRevision(app);
                            prefs(app).edit().putBoolean(BACKFILL,true).apply();bumpRevision(app);done=true;
                        }
                    }
                }
                if(done&&unlocked(app))AchievementStore.recognizeServiceAchievements(app);
            }catch(Exception error){android.util.Log.w("Roadprints","Historical service visit scan will be retried",error);}
            java.util.List<Runnable> callbacks;
            synchronized(STATE_LOCK){backfillRunning=false;callbacks=new java.util.ArrayList<>(CALLBACKS);CALLBACKS.clear();}
            for(Runnable callback:callbacks)post(callback);
        });
    }
    private static void post(Runnable callback) { if(callback!=null)new Handler(Looper.getMainLooper()).post(callback); }
    static ServiceStationAchievements.Snapshot achievementSnapshot(Context context) {
        try {
            Set<String> visits=completed(context);
            ServiceStationAchievements.Snapshot result=ServiceStationAchievements.calculate(stations(context),visits,unlocked(context));
            if(unlocked(context)) {
                SharedPreferences achievementPrefs=context.getSharedPreferences("roadprints_achievements_v1",Context.MODE_PRIVATE);
                JSONObject earned=new JSONObject(achievementPrefs.getString("unlocked","{}"));
                JSONObject proofs=new JSONObject(achievementPrefs.getString("service_goal_proofs_v1","{}"));
                for(AchievementStore.Definition d:AchievementStore.definitions())if("service-station".equals(d.type)&&earned.has(d.id)) {
                    JSONArray proof=proofs.optJSONArray(d.id);boolean supported=proof!=null&&proof.length()>0;
                    if(proof!=null)for(int i=0;i<proof.length();i++)supported&=visits.contains(proof.optString(i));
                    if(supported&&result.values.getOrDefault(d.id,0.0)<d.target) {
                        result.values.put(d.id,d.target);
                        result.display.put(d.id,result.display.get(d.id)+" · earned milestone retained");
                    }
                }
            }
            return result;
        }
        catch(Exception error){throw new IllegalStateException("Service achievement catalogue unavailable",error);}
    }
    static Map<String,Double> achievementValues(Context context) { return achievementSnapshot(context).values; }
    static void resetHistory(Context context) {
        synchronized(STATE_LOCK){generation++;prefs(context).edit().putBoolean(BACKFILL,false).apply();bumpRevision(context);}
    }
    static void clearOnDeleteAll(Context context) {
        synchronized(STATE_LOCK) {
            generation++;TimelineVisitStore.clear(context);ServiceStationVisitStore.clear(context);
            // Deleting journeys does not revoke a purchased entitlement.
            prefs(context).edit().remove("service_station_manual").remove(AUTOMATIC).putBoolean(BACKFILL,false)
                    .putBoolean(MIGRATION,true).putLong(REVISION,revision(context)+1).apply();
        }
    }
    private static SharedPreferences prefs(Context context) {return context.getApplicationContext().getSharedPreferences(PREFS,Context.MODE_PRIVATE);}
}
