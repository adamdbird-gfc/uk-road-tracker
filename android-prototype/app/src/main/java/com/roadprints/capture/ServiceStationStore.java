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
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/** Local entitlement, visit ledger and bundled UK motorway services catalogue. */
final class ServiceStationStore {
    private static final String PREFS = "roadprints_collections_v1";
    private static final String MANUAL = "service_station_manual";
    private static final String AUTOMATIC = "service_station_automatic";
    private static final String REVISION = "revision";
    private static final String HISTORICAL_BACKFILL_COMPLETE = "historical_backfill_v1_complete";
    private static final AtomicBoolean BACKFILL_RUNNING = new AtomicBoolean(false);
    private static final java.util.concurrent.CopyOnWriteArrayList<Runnable> BACKFILL_CALLBACKS =
            new java.util.concurrent.CopyOnWriteArrayList<>();
    private static volatile JSONArray catalogue;
    private ServiceStationStore() {}

    static boolean unlocked(Context context) {
        return prefs(context).getBoolean("service_stations_unlocked", false);
    }
    static void unlockForTesting(Context context) {
        prefs(context).edit().putBoolean("service_stations_unlocked", true)
                .putLong(REVISION, revision(context)+1).apply();
    }
    static long revision(Context context) { return prefs(context).getLong(REVISION, 0); }
    static boolean historicalBackfillComplete(Context context) {
        return prefs(context).getBoolean(HISTORICAL_BACKFILL_COMPLETE, false);
    }
    static Set<String> manual(Context context) { return set(context, MANUAL); }
    static Set<String> automatic(Context context) { return set(context, AUTOMATIC); }
    static Set<String> completed(Context context) {
        Set<String> result=manual(context); result.addAll(automatic(context)); return result;
    }
    static void setManual(Context context, String id, boolean visited) {
        SharedPreferences p=prefs(context);
        Set<String> ids=set(context, MANUAL);
        if (visited) ids.add(id); else ids.remove(id);
        p.edit().putStringSet(MANUAL, ids).putLong(REVISION, revision(context)+1).apply();
    }
    static JSONArray stations(Context context) throws Exception {
        JSONArray cached=catalogue;
        if (cached != null) return cached;
        try (InputStream input=context.getAssets().open("uk-motorway-services-v1.json")) {
            byte[] bytes=new byte[input.available()];
            int offset=0, count;
            while(offset<bytes.length && (count=input.read(bytes,offset,bytes.length-offset))>=0) offset+=count;
            JSONArray result=new JSONObject(new String(bytes,0,offset,StandardCharsets.UTF_8))
                    .optJSONArray("services");
            catalogue=result==null?new JSONArray():result;
            return catalogue;
        }
    }
    static int recordJourney(Context context, JSONObject journey) {
        if (!unlocked(context)) return 0;
        Set<String> discovered = journeyVisits(context, journey);
        if (discovered.isEmpty()) return 0;
        SharedPreferences p=prefs(context);
        Set<String> visits=set(context, AUTOMATIC);
        int before=visits.size();
        visits.addAll(discovered);
        if(visits.size()!=before) {
            p.edit().putStringSet(AUTOMATIC,visits).putLong(REVISION,revision(context)+1).apply();
        }
        return visits.size()-before;
    }

    /** Backfill imported journeys once when the service-station collection is unlocked. */
    static void ensureHistoricalVisits(Context context, Runnable onComplete) {
        Context app=context.getApplicationContext();
        if (onComplete != null) BACKFILL_CALLBACKS.add(onComplete);
        if (historicalBackfillComplete(app)) {
            if (onComplete != null && BACKFILL_CALLBACKS.remove(onComplete)) onComplete.run();
            return;
        }
        if (!unlocked(app)) {
            if (onComplete != null) BACKFILL_CALLBACKS.remove(onComplete);
            return;
        }
        if (!BACKFILL_RUNNING.compareAndSet(false, true)) return;
        ScreenDataLoader.execute(() -> {
            boolean succeeded=false;
            try {
                Set<String> visits=automatic(app);
                JSONArray stations=stations(app);
                final Set<String> allIds=new HashSet<>();
                for(int i=0;i<stations.length();i++) {
                    JSONObject station=stations.optJSONObject(i);
                    if(station!=null) allIds.add(station.optString("id", ""));
                }
                JourneyStore.forEach(app, journey -> {
                    if(visits.size()<allIds.size()) visits.addAll(journeyVisits(app, journey));
                });
                SharedPreferences p=prefs(app);
                p.edit().putStringSet(AUTOMATIC,visits)
                        .putBoolean(HISTORICAL_BACKFILL_COMPLETE,true)
                        .putLong(REVISION,revision(app)+1).apply();
                succeeded=true;
            } catch(Exception error) {
                android.util.Log.w("Roadprints", "Historical service-station scan will retry", error);
            } finally {
                BACKFILL_RUNNING.set(false);
            }
            if(succeeded) {
                java.util.List<Runnable> callbacks = new java.util.ArrayList<>(BACKFILL_CALLBACKS);
                BACKFILL_CALLBACKS.clear();
                new Handler(Looper.getMainLooper()).post(() -> {
                    for (Runnable callback : callbacks) callback.run();
                });
            } else {
                BACKFILL_CALLBACKS.clear();
            }
        });
    }

    private static Set<String> journeyVisits(Context context, JSONObject journey) {
        Set<String> found=new HashSet<>();
        if(journey==null || !"complete".equals(journey.optString("processing_status", ""))) return found;
        String mode=journey.optString("mode", "");
        if (!"driving".equals(mode) && !"bus".equals(mode)) return found;
        JSONObject route=journey.optJSONObject("route_geometry");
        JSONArray points=route==null?null:route.optJSONArray("coordinates");
        if(points==null || points.length()<2) return found;
        try {
            JSONArray stations=stations(context);
            for(int i=0;i<stations.length();i++) {
                JSONObject station=stations.optJSONObject(i);
                if(station==null) continue;
                String id=station.optString("id", "");
                if(id.isEmpty()) continue;
                double lng=station.optDouble("lng", Double.NaN), lat=station.optDouble("lat", Double.NaN);
                for(int point=1;point<points.length();point++) {
                    JSONArray a=points.optJSONArray(point-1), b=points.optJSONArray(point);
                    if(a==null||b==null||a.length()<2||b.length()<2) continue;
                    if(pointSegmentDistance(lng,lat,a.optDouble(0),a.optDouble(1),
                            b.optDouble(0),b.optDouble(1))<=350) { found.add(id); break; }
                }
            }
        } catch(Exception ignored) { return found; }
        return found;
    }

    static Map<String, Double> achievementValues(Context context) {
        Set<String> complete=completed(context);
        boolean unlocked=unlocked(context);
        Map<String, Double> values=new HashMap<>();
        values.put("service-first-stop",unlocked&&complete.size()>=1?1.0:0.0);
        values.put("service-ten-stops",unlocked&&complete.size()>=10?1.0:0.0);
        values.put("service-moneybags",unlocked&&complete.contains("msa:norton-canes:52.6643:-1.9688")?1.0:0.0);
        values.put("service-being-posh",unlocked&&complete.contains("msa:peterborough:52.5314:-0.3215")?1.0:0.0);
        return values;
    }
    static void clearOnDeleteAll(Context context) {
        prefs(context).edit().remove("service_stations_unlocked").remove(MANUAL)
                .remove(AUTOMATIC).putLong(REVISION,revision(context)+1).apply();
    }
    private static Set<String> set(Context context,String key) {
        return new HashSet<>(prefs(context).getStringSet(key, java.util.Collections.emptySet()));
    }
    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS,Context.MODE_PRIVATE);
    }
    private static double pointSegmentDistance(double lng,double lat,double ax,double ay,double bx,double by) {
        double mx=111320.0*Math.cos(Math.toRadians(lat)), my=111320.0;
        double px=(lng-ax)*mx, py=(lat-ay)*my, dx=(bx-ax)*mx, dy=(by-ay)*my;
        double d=dx*dx+dy*dy, t=d<=0?0:Math.max(0,Math.min(1,(px*dx+py*dy)/d));
        return Math.hypot(px-t*dx,py-t*dy);
    }
}
