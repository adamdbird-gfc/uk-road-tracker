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
    // Achievement id, title, stable pub id, venue, place, Grand Tour area.
    static final String[][] NAMED={
        {"spoons-rochester-castle","(Not) The OG","spoons-osm-n256404255","The Rochester Castle","Stoke Newington","London"},
        {"spoons-hawkshaw","Nice to meet you Sir","spoons-osm-n5141625722","The Sir John Hawkshaw","Cannon Street, London","London"},
        {"spoons-pavilion","It’s echoey in here","spoons-osm-w103966079","Royal Victoria Pavilion","Ramsgate","South East"},
        {"spoons-opera","Nessun dorma","spoons-osm-n1973967757","Opera House","Royal Tunbridge Wells","South East"},
        {"spoons-justice","Guilty as charged","spoons-osm-w277707569","The Chief Justice of the Common Pleas","Keswick","North West"},
        {"spoons-winter","It’s a kinda chilly","spoons-osm-n462615653","The Winter Gardens","Harrogate","Yorkshire and the Humber"},
        {"spoons-palladium","Sunday night at the Palladium","spoons-osm-w223309617","The Palladium","Llandudno","Wales"},
        {"spoons-wallaw","No way, downstairs loos!","spoons-osm-w134968549","The Wallaw","Blyth","North East"},
        {"spoons-kettleby","Pie & a Pint","spoons-osm-n1059478029","The Kettleby Cross","Melton Mowbray","East Midlands"},
        {"spoons-square-peg","Round Holes","spoons-osm-w94961209","The Square Peg","Birmingham","West Midlands"},
        {"spoons-corn-exchange","Buried in opulence","spoons-osm-n2291314726","The Corn Exchange","Bury St Edmunds","East of England"},
        {"spoons-imperial","Anyone for drinks in the orangery?","spoons-osm-w175308977","The Imperial","Exeter","South West"},
        {"spoons-counting-house","1, 2, 3, 4, 5","spoons-osm-n247937950","The Counting House","Glasgow","Scotland"},
        {"spoons-spirit-merchant","Single or double?","spoons-osm-n2964369609","The Spirit Merchant","Newtownards","Northern Ireland"}
    };
    static final Set<String> STAGE=new HashSet<>(Arrays.asList("spoons-osm-w134968549","spoons-osm-w223309617","spoons-osm-n1973967757","spoons-osm-w103966079"));
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
        // The legacy category argument remains a test/import seam; production uses the reviewed registry.
        VenueCollection.Definition definition=VenueCollection.definition("wetherspoons");
        VenueAchievements.Snapshot shared=VenueAchievements.calculate(definition,catalogue,visited,installed,nations);
        Snapshot result=new Snapshot();result.values.putAll(shared.values);result.lists.putAll(shared.lists);return result;
    }
}
