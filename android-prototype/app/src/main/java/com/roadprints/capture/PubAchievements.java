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
    static void definitions(List<AchievementStore.Definition> out){
        AchievementStore.Definition count=new AchievementStore.Definition("spoons-collector","","Spoon collector",
                "Visit 1, 10, 25, 50, 100, 250 and 500 distinct Wetherspoon pubs. Repeat visits count once.",
                "Distinct pubs with supported visits, including retained historical visits.","pub-visit",1,null,0,0,0);
        count.levels=new double[]{1,10,25,50,100,250,500};
        count.levelTitles=new String[]{"First spoon","Spoon collector · 10","Spoon collector · 25","Spoon collector · 50","Spoon collector · 100","Spoon collector · 250","Spoon collector · 500"};out.add(count);
        for(String[] row:NAMED)add(out,row[0],row[1],"Visit "+row[3]+", "+row[4]+".",1);
        add(out,"spoons-train","Train drinks","Visit a Wetherspoon at a railway station, including attached or converted station premises. Station presence alone does not qualify.",1);
        add(out,"spoons-plane","Plane drinks","Visit an airport Wetherspoon. Airport presence alone does not qualify.",1);
        add(out,"spoons-holiday","Holiday drinks","Visit a Wetherspoon at a Haven holiday park. Park presence alone does not qualify.",1);
        add(out,"spoons-stage","Stage presence","Visit all four curated performance venues: The Wallaw, The Palladium, Opera House and Royal Victoria Pavilion.",4);
        add(out,"spoons-nations","A Guinness, a Scotch, a cup of tea and a cwrw please?","Visit a Wetherspoon in England, Scotland, Wales and Northern Ireland. No drinks order is required.",4);
        add(out,"spoons-grand-tour","The grand tour","Visit a nominated pub in each of 12 areas. Either nominated pub qualifies in London and the South East.",12);
    }
    private static void add(List<AchievementStore.Definition> out,String id,String title,String copy,int target){
        out.add(new AchievementStore.Definition(id,"",title,copy,copy,"pub-visit",target,null,0,0,0));
    }
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
        Snapshot result=new Snapshot();Set<String> visited=new HashSet<>();Map<String,JSONObject> pubs=new HashMap<>();Map<String,String> nations=new HashMap<>();
        for(int i=0;i<catalogue.length();i++){JSONObject p=catalogue.optJSONObject(i);if(p!=null){pubs.put(p.optString("id"),p);JSONObject c=p.optJSONObject("county");if(c!=null)nations.put(p.optString("id"),c.optString("nation"));}}
        if(installed)for(int i=0;i<records.length();i++){
            JSONObject proof=records.optJSONObject(i);if(proof==null)continue;
            String source=proof.optString("source"),id=proof.optString("pub_id");
            if(id.isEmpty()||!("confirmed_live_checkin".equals(source)||"confirmed_timeline_visit".equals(source)||"named_timeline_visit".equals(source)||"native_stop".equals(source)||"confirmed_native_stop".equals(source)))continue;
            visited.add(id);if(!proof.optString("nation").isEmpty())nations.put(id,proof.optString("nation"));
        }
        result.values.put("spoons-collector",(double)visited.size());
        Map<String,List<String>> areas=new LinkedHashMap<>();
        for(String[] row:NAMED){
            boolean done=visited.contains(row[2]);result.values.put(row[0],done?1.0:0.0);
            result.lists.put(row[0],Arrays.asList((done?"✓ ":"○ ")+row[3]+" · "+row[4]));
            areas.computeIfAbsent(row[5],key->new ArrayList<>()).add(row[2]);
        }
        int areasDone=0;List<String> tour=new ArrayList<>();
        for(Map.Entry<String,List<String>> area:areas.entrySet()){
            boolean done=false;List<String> names=new ArrayList<>();
            for(String id:area.getValue()){done|=visited.contains(id);for(String[] row:NAMED)if(row[2].equals(id))names.add(row[3]+" ("+row[4]+")");}
            if(done)areasDone++;tour.add((done?"✓ ":"○ ")+area.getKey()+" · "+String.join(" or ",names));
        }
        result.values.put("spoons-grand-tour",(double)areasDone);result.lists.put("spoons-grand-tour",tour);
        int stage=0;List<String> stageRows=new ArrayList<>();
        for(String[] row:NAMED)if(STAGE.contains(row[2])){boolean done=visited.contains(row[2]);if(done)stage++;stageRows.add((done?"✓ ":"○ ")+row[3]+" · "+row[4]);}
        result.values.put("spoons-stage",(double)stage);result.lists.put("spoons-stage",stageRows);
        int nationsDone=0;List<String> nationRows=new ArrayList<>();
        for(String nation:new String[]{"England","Scotland","Wales","Northern Ireland"}){
            boolean done=false;for(String id:visited)if(nation.equals(nations.get(id)))done=true;
            if(done)nationsDone++;nationRows.add((done?"✓ ":"○ ")+nation);
        }
        result.values.put("spoons-nations",(double)nationsDone);result.lists.put("spoons-nations",nationRows);
        for(String type:new String[]{"train","plane","holiday"}){
            boolean done=false;List<String> rows=new ArrayList<>();
            for(int i=0;i<categories.length();i++){
                JSONObject p=categories.optJSONObject(i);if(p==null||!type.equals(p.optString("category")))continue;
                boolean seen=visited.contains(p.optString("id"));done|=seen;rows.add((seen?"✓ ":"○ ")+p.optString("name")+" · "+p.optString("town"));
            }
            result.values.put("spoons-"+type,done?1.0:0.0);result.lists.put("spoons-"+type,rows);
        }
        return result;
    }
}
