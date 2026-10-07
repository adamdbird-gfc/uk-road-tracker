package com.roadprints.capture;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Service collection goals all use the same named-site catalogue and automatic visit set. */
final class ServiceStationAchievements {
    static final String[] ROADS = {"M1","M2","M3","M4","M5","M6","M6 Toll","M8","M9","M11",
            "M20","M23","M25","M27","M40","M42","M48","M54","M56","M57","M58",
            "M61","M62","M65","M74","M90","M180","A1(M)","A74(M)"};
    static final String[][] SPECIAL = {
        {"service-filling-gap","Filling the Gap","Watford Gap","msa:watford gap:52.3071:-1.122"},
        {"service-farmers-friend","Farmer’s Friend","Gloucester","msa:westmorland gloucester:51.8191:-2.226"},
        {"service-say-i-do","Say I Do","Gretna Green","msa:welcome break gretna green:55.0083:-3.0857"},
        {"service-end-of-line","End of the Line","Pont Abraham","msa:pont abraham:51.747:-4.0654"},
        {"service-moneybags","Moneybags","Norton Canes","msa:norton-canes:52.6643:-1.9688"},
        {"service-being-posh","Being Posh","Peterborough","msa:peterborough:52.5314:-0.3215"}
    };
    static final String[][] OPERATORS = {
        {"service-loyalty-points","Loyalty Points","Moto"},
        {"service-welcome-regular","Welcome Regular","Welcome Break"},
        {"service-chefs-special","Chef’s Special","Roadchef"},
        {"service-extra-mile","Extra Mile","Extra"}
    };
    private ServiceStationAchievements() {}
    static String roadId(String road) { return "service-road-"+road.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]", ""); }
    static void definitions(List<AchievementStore.Definition> out) {
        add(out,"service-first-stop","First Stop","Visit your first motorway service area.",1);
        int[] targets={25,50,75,100};
        String[] titles={"Services · Quarter Collected","Services · Halfway There","Services · Three Quarters",
                "Completed It, Mate: Services"};
        for(int i=0;i<targets.length;i++) add(out,"service-percent-"+targets[i],titles[i],
                "Visit "+targets[i]+"% of the service stations in the collection.",targets[i]);
        for(String road:ROADS) add(out,roadId(road),"All Services on "+road,
                "Visit every service station on "+road+" in the collection.",100);
        for(String[] item:SPECIAL) add(out,item[0],item[1],"Stop at "+item[2]+" Services.",1);
        for(String[] item:OPERATORS) add(out,item[0],item[1],"Visit every "+item[2]+" service station in the collection.",100);
        add(out,"service-farm-to-motorway","Farm to Motorway","Visit Tebay, Gloucester and Cairn Lodge.",3);
        add(out,"service-three-nations","Three Nations, One Collection",
                "Visit at least one service station in England, Scotland and Wales.",3);
    }
    private static void add(List<AchievementStore.Definition> out,String id,String title,String copy,double target) {
        out.add(new AchievementStore.Definition(id,"⛽",title,copy,copy,"service-station",target,null,0,0,0));
    }

    static final class Snapshot {
        final Map<String,Double> values=new LinkedHashMap<>();
        final Map<String,List<String>> lists=new LinkedHashMap<>();
        final Map<String,String> display=new LinkedHashMap<>();
    }
    static Snapshot calculate(JSONArray catalogue,Set<String> visited,boolean unlocked) {
        Snapshot result=new Snapshot();
        List<JSONObject> all=new ArrayList<>();
        for(int i=0;i<catalogue.length();i++) {
            JSONObject station=catalogue.optJSONObject(i);
            if(station!=null&&!station.optString("id","").isEmpty())all.add(station);
        }
        all.sort((a,b)->a.optString("name").compareToIgnoreCase(b.optString("name")));
        int count=count(all,visited);
        goal(result,"service-first-stop",all,visited,unlocked,count>=1?1:0);
        for(int target:new int[]{25,50,75,100})goal(result,"service-percent-"+target,all,visited,unlocked,percent(all,visited));
        for(String road:ROADS) {
            List<JSONObject> group=new ArrayList<>();
            for(JSONObject station:all)if(road.equals(station.optString("road")))group.add(station);
            goal(result,roadId(road),group,visited,unlocked,percent(group,visited));
        }
        for(String[] item:OPERATORS) {
            List<JSONObject> group=new ArrayList<>();
            for(JSONObject station:all)if(item[2].equals(station.optString("operator_group")))group.add(station);
            goal(result,item[0],group,visited,unlocked,percent(group,visited));
        }
        for(String[] item:SPECIAL) {
            List<JSONObject> group=new ArrayList<>();
            for(JSONObject station:all)if(item[3].equals(station.optString("id")))group.add(station);
            goal(result,item[0],group,visited,unlocked,count(group,visited)>0?1:0);
        }
        List<JSONObject> farms=new ArrayList<>();
        for(JSONObject station:all) {
            String id=station.optString("id");
            if(id.equals("msa:westmorland tebay:54.451:-2.6088")
                    ||id.equals("msa:westmorland gloucester:51.8191:-2.226")
                    ||id.equals("msa:cairn lodge:55.5838:-3.8233"))farms.add(station);
        }
        goal(result,"service-farm-to-motorway",farms,visited,unlocked,count(farms,visited));
        List<String> nations=new ArrayList<>();int nationsVisited=0;
        for(String country:new String[]{"England","Scotland","Wales"}) {
            boolean seen=false;
            for(JSONObject station:all)if(country.equals(station.optString("country"))&&visited.contains(station.optString("id")))seen=true;
            if(seen)nationsVisited++;
            nations.add((seen?"✓ ":"○ ")+country);
        }
        result.values.put("service-three-nations",unlocked?(double)nationsVisited:0.0);
        result.lists.put("service-three-nations",nations);
        result.display.put("service-three-nations",nationsVisited+" / 3 nations visited");
        return result;
    }
    private static void goal(Snapshot result,String id,List<JSONObject> stations,Set<String> visited,boolean unlocked,double value) {
        result.values.put(id,unlocked?value:0.0);
        List<String> list=new ArrayList<>();
        for(JSONObject station:stations) list.add((visited.contains(station.optString("id"))?"✓ ":"○ ")+station.optString("name")+" · "+station.optString("road"));
        result.lists.put(id,list);
        result.display.put(id,count(stations,visited)+" / "+stations.size()+" service stations visited"
                +(stations.isEmpty()?"":" · "+String.format(java.util.Locale.UK,"%.1f%%",percent(stations,visited))));
    }
    private static int count(List<JSONObject> stations,Set<String> visited) {
        int count=0;for(JSONObject station:stations)if(visited.contains(station.optString("id")))count++;return count;
    }
    private static double percent(List<JSONObject> stations,Set<String> visited) {
        return stations.isEmpty()?0:100.0*count(stations,visited)/stations.size();
    }
}
