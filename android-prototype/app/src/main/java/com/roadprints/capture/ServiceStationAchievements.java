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
            "M61","M62","M65","M74","M90","M180","A1(M)","A74(M)","NI:M1","NI:M2"};
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
    static void definitions(List<AchievementStore.Definition> out) {VenueAchievements.definitions(VenueCollection.definition("services"),out);}
    static String groupRoad(JSONObject station) { return ("NI".equals(station.optString("region"))?"NI:":"")+station.optString("road"); }
    private static String roadLabel(String road) { return road.startsWith("NI:")?road.substring(3)+" (Northern Ireland)":road; }
    private static void add(List<AchievementStore.Definition> out,String id,String title,String copy,double target) {
        out.add(new AchievementStore.Definition(id,"⛽",title,copy,copy,"service-station",target,null,0,0,0));
    }

    static final class Snapshot {
        final Map<String,Double> values=new LinkedHashMap<>();
        final Map<String,List<String>> lists=new LinkedHashMap<>();
        final Map<String,Set<String>> proofs=new LinkedHashMap<>();
        final Map<String,String> display=new LinkedHashMap<>();
    }
    static Snapshot calculate(JSONArray catalogue,Set<String> visited,boolean unlocked) {
        VenueAchievements.Snapshot shared=VenueAchievements.calculate(VenueCollection.definition("services"),catalogue,visited,unlocked,java.util.Collections.emptyMap());
        Snapshot result=new Snapshot();result.values.putAll(shared.values);result.lists.putAll(shared.lists);result.proofs.putAll(shared.proofs);result.display.putAll(shared.display);return result;
    }
}
