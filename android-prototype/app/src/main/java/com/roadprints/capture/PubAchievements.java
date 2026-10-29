package com.roadprints.capture;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Fixed reviewed pub identities. Only accepted PubVisitStore proofs contribute. */
final class PubAchievements {
    private PubAchievements(){}
    static void definitions(List<AchievementStore.Definition> out){VenueAchievements.definitions(VenueCollection.definition("wetherspoons"),out);}
    static final class Snapshot{
        final Map<String,Double> values=new LinkedHashMap<>();
        final Map<String,List<String>> lists=new LinkedHashMap<>();
    }
    static Snapshot snapshot(Context context){
        JSONArray catalogue=CollectionMapCatalog.spoons(context),records=PubVisitStore.records(context);
        return calculate(catalogue,records,CollectionAccessStore.installed(context,CollectionAccessStore.SPOONS),categories(context));
    }
    static JSONArray categories(Context context){
        try(java.io.InputStream in=context.getAssets().open("collections/wetherspoons-achievement-venues-v1.json")){
            java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();byte[] b=new byte[4096];int n;
            while((n=in.read(b))!=-1)out.write(b,0,n);
            return new JSONObject(out.toString("UTF-8")).getJSONArray("venues");
        }catch(Exception error){throw new IllegalStateException("Reviewed pub achievement venues unavailable",error);}
    }
    static Snapshot calculate(JSONArray catalogue,JSONArray records,boolean installed,JSONArray categories){
        Set<String> visited=new HashSet<>();Map<String,String> nations=new HashMap<>();
        if(installed)for(int i=0;i<records.length();i++){
            JSONObject proof=records.optJSONObject(i);if(proof==null)continue;
            String source=proof.optString("source"),id=proof.optString("pub_id",proof.optString("venue_id"));
            if(id.isEmpty()||!("confirmed_live_checkin".equals(source)||"confirmed_timeline_visit".equals(source)||"named_timeline_visit".equals(source)||"native_stop".equals(source)||"confirmed_native_stop".equals(source)))continue;
            visited.add(id);if(!proof.optString("nation").isEmpty())nations.put(id,proof.optString("nation"));
        }
        // The legacy category argument is retained for callers; rules come from the reviewed registry.
        VenueCollection.Definition definition=VenueCollection.definition("wetherspoons");
        VenueAchievements.Snapshot shared=VenueAchievements.calculate(definition,catalogue,visited,installed,nations);
        Snapshot result=new Snapshot();result.values.putAll(shared.values);result.lists.putAll(shared.lists);return result;
    }
}
