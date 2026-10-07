package com.roadprints.capture;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.HashSet;
import java.util.Set;

/** Derived links backed by original capture samples; user confirmations are not evidence. */
final class ServiceStationVisitStore {
    private static final String PREFS="roadprints_collections_v1";
    private static final String RECORDS="service_station_detected_journey_visits_v2";
    private ServiceStationVisitStore() {}
    static synchronized boolean replaceJourney(Context context,String journeyId,Set<String> stations) {
        if(journeyId==null||journeyId.isEmpty())return false;
        JSONObject records=read(context);
        if(ids(records.optJSONArray(journeyId)).equals(stations))return false;
        try {
            if(stations.isEmpty())records.remove(journeyId);
            else records.put(journeyId,array(stations));
        }catch(Exception error){throw new IllegalStateException(error);}
        write(context,records);return true;
    }
    static synchronized boolean replaceAll(Context context,JSONObject records) {
        JSONObject previous=read(context);
        boolean same=previous.length()==records.length();
        java.util.Iterator<String> keys=records.keys();
        while(same&&keys.hasNext()) {
            String key=keys.next();same=ids(previous.optJSONArray(key)).equals(ids(records.optJSONArray(key)));
        }
        if(same)return false;
        write(context,records);return true;
    }
    static JSONArray array(Set<String> ids) {
        JSONArray result=new JSONArray();for(String id:new java.util.TreeSet<>(ids))result.put(id);return result;
    }
    static synchronized Set<String> stationIds(Context context) {
        Set<String> result=new HashSet<>();JSONObject records=read(context);
        java.util.Iterator<String> keys=records.keys();while(keys.hasNext())result.addAll(ids(records.optJSONArray(keys.next())));
        return result;
    }
    static synchronized Set<String> journeyIdsForStation(Context context,String stationId) {
        Set<String> result=new HashSet<>();JSONObject records=read(context);
        java.util.Iterator<String> keys=records.keys();while(keys.hasNext()) {
            String id=keys.next();if(ids(records.optJSONArray(id)).contains(stationId))result.add(id);
        }return result;
    }
    static synchronized void clear(Context context) {
        prefs(context).edit().remove(RECORDS).remove("service_station_confirmed_journey_visits_v1").apply();
    }
    private static Set<String> ids(JSONArray array) {
        Set<String> result=new HashSet<>();if(array!=null)for(int i=0;i<array.length();i++) {
            String id=array.optString(i,"");if(!id.isEmpty())result.add(id);
        }return result;
    }
    private static JSONObject read(Context context) {
        try{return new JSONObject(prefs(context).getString(RECORDS,"{}"));}
        catch(Exception error){return new JSONObject();}
    }
    private static void write(Context context,JSONObject records) { prefs(context).edit().putString(RECORDS,records.toString()).apply(); }
    private static SharedPreferences prefs(Context context) { return context.getApplicationContext().getSharedPreferences(PREFS,Context.MODE_PRIVATE); }
}
