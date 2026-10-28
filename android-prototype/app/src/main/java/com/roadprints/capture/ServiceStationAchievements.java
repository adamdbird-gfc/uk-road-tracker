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
