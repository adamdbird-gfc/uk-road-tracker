package com.roadprints.capture;
import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.HashSet;
import java.util.Set;
/** Compatibility adapter into the shared collection ledger; no separate new visit persistence. */
final class ServiceStationVisitStore {
    static boolean replaceJourney(Context c,String journey,Set<String> ids){return VenueVisitStore.replaceServiceJourney(c,journey,ids);}
    static boolean replaceAll(Context c,JSONObject links){return VenueVisitStore.replaceAllServiceJourneys(c,links);}
    static JSONArray array(Set<String> ids){JSONArray out=new JSONArray();for(String id:new java.util.TreeSet<>(ids))out.put(id);return out;}
    static Set<String> stationIds(Context c){return VenueVisitStore.completed(c,CollectionAccessStore.SERVICES);}
    static Set<String> journeyIdsForStation(Context c,String station){
        Set<String> out=new HashSet<>();JSONObject links=VenueVisitStore.serviceLinks(c);java.util.Iterator<String> keys=links.keys();
        while(keys.hasNext()){String id=keys.next();JSONArray rows=links.optJSONArray(id);if(rows!=null)for(int i=0;i<rows.length();i++)if(station.equals(rows.optString(i)))out.add(id);}return out;
    }
    static void clear(Context c){VenueVisitStore.clear(c,CollectionAccessStore.SERVICES);c.getSharedPreferences("roadprints_collections_v1",0).edit().remove("service_station_detected_journey_visits_v2").remove("service_station_confirmed_journey_visits_v1").apply();}
}
