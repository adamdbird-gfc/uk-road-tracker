package com.roadprints.capture;
import android.content.Context;
import android.location.Location;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Set;
/** Compatibility adapter; all new persistence and visit behaviour live in the shared engine. */
final class PubVisitStore {
    static final String PREFS="roadprints_pub_visits_v1";
    private static final String ID=CollectionAccessStore.SPOONS;
    private PubVisitStore(){}
    static String visitedLabel(int count){return count+(count==1?" pub visited":" pubs visited");}
    static long revision(Context c){return VenueVisitStore.revision(c,ID);}
    static Set<String> completed(Context c){return VenueVisitStore.completed(c,ID);}
    static JSONArray records(Context c){
        JSONArray out=VenueVisitStore.records(c,ID);
        for(int i=0;i<out.length();i++)try{JSONObject r=out.getJSONObject(i);r.put("pub_id",r.optString("venue_id"));}
        catch(Exception invalid){throw new IllegalStateException(invalid);}return out;
    }
    static void ensureHistory(Context c,Runnable done){VenueVisitStore.ensureHistory(c,ID,done);}
    static int reconcile(Context c,JSONArray rows)throws Exception{return VenueVisitStore.reconcile(c,ID,rows);}
    static JSONArray pending(Context c,JSONArray rows)throws Exception{return VenueVisitStore.pending(c,ID,rows);}
    static void confirmTimeline(Context c,String visit,String pub)throws Exception{VenueVisitStore.confirmTimeline(c,ID,visit,pub);}
    static void dismissTimeline(Context c,String visit)throws Exception{VenueVisitStore.dismissTimeline(c,ID,visit);}
    static void removeProof(Context c,String key)throws Exception{VenueVisitStore.removeProof(c,ID,key);}
    static void confirmLive(Context c,String id,Location fix,boolean confirmed)throws Exception{VenueVisitStore.confirmLive(c,ID,id,fix,confirmed);}
    static JSONObject find(JSONArray rows,String id){return VenueVisitStore.find(rows,id);}
    static void clear(Context c){VenueVisitStore.clear(c,ID);c.getSharedPreferences(PREFS,0).edit().clear().commit();}
    static void beginHistoryReplacement(){VenueVisitStore.beginHistoryReplacement();}
    static void endHistoryReplacement(){VenueVisitStore.endHistoryReplacement();}
}
