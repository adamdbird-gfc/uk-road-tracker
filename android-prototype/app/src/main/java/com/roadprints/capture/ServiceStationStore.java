package com.roadprints.capture;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Local entitlement, visit ledger and bundled UK motorway services catalogue. */
final class ServiceStationStore {
    private static final String PREFS = "roadprints_collections_v1";
    private static final String MANUAL = "service_station_manual";
    private static final String AUTOMATIC = "service_station_automatic";
    private static final String REVISION = "revision";
    private static final String HISTORICAL_BACKFILL_COMPLETE = "historical_backfill_v1_complete";
    private static final String CONFIRMED_ONLY_MIGRATION = "confirmed_timeline_only_v1";
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
        ensureConfirmedOnlyMigration(context);
        return prefs(context).getBoolean(HISTORICAL_BACKFILL_COMPLETE, false);
    }
    static Set<String> manual(Context context) { return set(context, MANUAL); }
    static Set<String> automatic(Context context) {
        ensureConfirmedOnlyMigration(context);
        return set(context, AUTOMATIC);
    }
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
        // Passing close to a station does not prove that a user stopped there.
        return 0;
    }

    private static void ensureConfirmedOnlyMigration(Context context) {
        SharedPreferences p=prefs(context);
        if(p.getBoolean(CONFIRMED_ONLY_MIGRATION,false)) return;
        p.edit().remove(AUTOMATIC).putBoolean(CONFIRMED_ONLY_MIGRATION,true)
                .putBoolean(HISTORICAL_BACKFILL_COMPLETE,true)
                .putLong(REVISION,revision(context)+1).apply();
    }

    /**
     * Retain explicit Timeline place visits as source evidence, then rebuild station matches.
     * The raw visit ledger is authoritative; automatic station IDs are a derived cache.
     */
    static int recordConfirmedTimelineVisits(Context context, JSONArray visits) {
        ensureConfirmedOnlyMigration(context);
        automatic(context);
        try {
            TimelineVisitStore.merge(context, visits);
            rebuildConfirmedTimelineMatches(context);
        } catch (Exception error) {
            android.util.Log.w("Roadprints", "Could not persist Timeline place visits", error);
            return 0;
        }
        return automatic(context).size();
    }

    private static void rebuildConfirmedTimelineMatches(Context context) throws Exception {
        JSONArray evidence = TimelineVisitStore.all(context);
        JSONArray services = stations(context);
        Set<String> credited = new HashSet<>();
        for (int i = 0; i < evidence.length(); i++) {
            JSONObject visit = evidence.optJSONObject(i);
            if (visit == null) continue;
            double lat = visit.optDouble("lat", Double.NaN);
            double lng = visit.optDouble("lng", Double.NaN);
            if (!Double.isFinite(lat) || !Double.isFinite(lng)) continue;
            JSONObject nearest = null;
            double nearestMetres = Double.MAX_VALUE;
            for (int j = 0; j < services.length(); j++) {
                JSONObject service = services.optJSONObject(j);
                if (service == null) continue;
                double distance = haversineMetres(lat, lng,
                        service.optDouble("lat", Double.NaN),
                        service.optDouble("lng", Double.NaN));
                if (distance < nearestMetres) {
                    nearestMetres = distance;
                    nearest = service;
                }
            }
            if (nearest != null && nearestMetres <= 350) {
                String id = nearest.optString("id", "");
                if (!id.isEmpty()) credited.add(id);
            }
        }
        Set<String> existing = set(context, AUTOMATIC);
        if (!existing.equals(credited)) {
            prefs(context).edit().putStringSet(AUTOMATIC, credited)
                    .putLong(REVISION, revision(context) + 1).apply();
        }
    }

    private static double haversineMetres(double lat1,double lng1,double lat2,double lng2) {
        if(!Double.isFinite(lat2)||!Double.isFinite(lng2)) return Double.MAX_VALUE;
        double radians=Math.PI/180d,dLat=(lat2-lat1)*radians,dLng=(lng2-lng1)*radians;
        double a=Math.sin(dLat/2)*Math.sin(dLat/2)+Math.cos(lat1*radians)*Math.cos(lat2*radians)
                *Math.sin(dLng/2)*Math.sin(dLng/2);
        return 6_371_000d*2d*Math.atan2(Math.sqrt(a),Math.sqrt(1d-a));
    }

    /** Route paths cannot reconstruct the confirmed Timeline place visits. */
    static void ensureHistoricalVisits(Context context, Runnable onComplete) {
        ensureConfirmedOnlyMigration(context);
        if(onComplete!=null) onComplete.run();
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
        TimelineVisitStore.clear(context);
        prefs(context).edit().remove("service_stations_unlocked").remove(MANUAL)
                .remove(AUTOMATIC).remove(CONFIRMED_ONLY_MIGRATION)
                .remove(HISTORICAL_BACKFILL_COMPLETE)
                .putLong(REVISION,revision(context)+1).apply();
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
