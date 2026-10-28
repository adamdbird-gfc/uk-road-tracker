package com.roadprints.capture;

import android.content.Context;
import java.util.*;
import org.json.*;

/** Current discovered local roads, combined across modes and grouped by official town code. */
final class TownAchievementEvidence {
    private static final String PREFS = "roadprints_town_achievement_evidence_v1";
    private static final java.util.concurrent.ExecutorService INVENTORY_WORKER=java.util.concurrent.Executors.newSingleThreadExecutor();
    private static final Map<String,Long> inventoryAttempts=new HashMap<>();
    static final class Town {
        final String code, name;
        final Set<String> roads = new HashSet<>();
        int inventory = -1;
        Town(String code, String name) { this.code=code; this.name=name; }
        double percent() { return inventory>400 ? Math.min(100,100.0*roads.size()/inventory) : 0; }
    }
    final Map<String,Town> towns = new TreeMap<>();
    int unresolvedRoads;

    /** Match Progress's local-road identities and geometry selection exactly, including names containing digits. */
    static void addRoadDiscovery(JSONObject journey,Map<String,LinkedHashMap<String,JSONObject>> roads) {
        JSONObject result=journey.optJSONObject("processing_result");if(result==null)return;
        JSONObject geojson=result.optJSONObject("road_geojson");
        JSONArray features=geojson==null?null:geojson.optJSONArray("features");
        if(features==null||features.length()==0) {
            features=new JSONArray();
            for(String field:new String[]{"motorway_geojson","a_road_geojson"}) {
                JSONObject collection=result.optJSONObject(field);JSONArray fallback=collection==null?null:collection.optJSONArray("features");
                if(fallback!=null)for(int i=0;i<fallback.length();i++)features.put(fallback.opt(i));
            }
        }
        for(int i=0;i<features.length();i++) {
            JSONObject f=features.optJSONObject(i);if(f==null||JourneyCorrectionUtils.excludesRoadFeature(journey,f))continue;
            JSONObject props=f.optJSONObject("properties"),geometry=f.optJSONObject("geometry");if(props==null||geometry==null)continue;
            String kind=props.optString("highway").toLowerCase(Locale.ROOT);
            if(kind.endsWith("_link")||Arrays.asList("service","footway","path","steps","cycleway").contains(kind))continue;
            String raw=props.optString("road_ref",props.optString("ref",""));
            for(String part:raw.split("[;,/]")) {
                String label=part.trim().toUpperCase(Locale.ROOT).replaceAll("\\s+","");
                if(label.matches("\\d+(?:[.,]\\d+)?"))label="";
                if(label.isEmpty())label=props.optString("name",props.optString("road_name","")).trim();
                if(label.isEmpty()||label.matches("\\d+(?:[.,]\\d+)?")
                        ||label.matches("M[0-9]+[A-Z]?|M6T|M6TOLL|A[0-9]+\\(M\\)|[AB][0-9]+[A-Z]?"))continue;
                String id=label.matches(".*[0-9].*")?"ref:"+label:"name:"+label.toLowerCase(Locale.ROOT);
                roads.computeIfAbsent(id,key->new LinkedHashMap<>()).putIfAbsent(geometry.toString(),geometry);
            }
        }
    }

    static boolean record(Context context,String roadId,List<JSONObject> geometry,
                       List<LocalRoadSettlementMatcher.Settlement> matches) {
        JSONArray array=new JSONArray();
        TreeMap<String,LocalRoadSettlementMatcher.Settlement> unique=new TreeMap<>();
        if(matches!=null)for(LocalRoadSettlementMatcher.Settlement s:matches)if(s!=null&&!s.code.isEmpty())unique.put(s.code,s);
        try { for(LocalRoadSettlementMatcher.Settlement s:unique.values())
            array.put(new JSONObject().put("code",s.code).put("name",s.name));
        } catch(JSONException ignored) { return false; }
        String key=AchievementStore.roadEvidenceKey(roadId,geometry), value=array.toString();
        android.content.SharedPreferences p=context.getSharedPreferences(PREFS,0);
        if(!value.equals(p.getString(key,null))) {
            p.edit().putString(key,value).apply();
            return true;
        }
        return false;
    }

    void collect(Context context,CoreAchievementEvidence core) {
        for(Map.Entry<String,LinkedHashMap<String,JSONObject>> entry:core.townRoads.entrySet()) {
            String roadId=entry.getKey();
            List<JSONObject> geometry=new ArrayList<>(entry.getValue().values());
            String value=context.getSharedPreferences(PREFS,0)
                    .getString(AchievementStore.roadEvidenceKey(roadId,geometry),null);
            try {
                if(value==null) {
                    List<LocalRoadSettlementMatcher.Settlement> cached=LocalRoadSettlementMatcher.cached(context,roadId,geometry);
                    if(cached==null){unresolvedRoads++;continue;}
                    if(record(context,roadId,geometry,cached))AchievementStore.advanceEvidenceRevision(context);
                    value=context.getSharedPreferences(PREFS,0).getString(AchievementStore.roadEvidenceKey(roadId,geometry),"[]");
                }
                JSONArray matches=new JSONArray(value);
                for(int i=0;i<matches.length();i++) {
                    JSONObject s=matches.optJSONObject(i);if(s==null||s.optString("code").isEmpty())continue;
                    Town town=towns.computeIfAbsent(s.optString("code"),key->new Town(key,s.optString("name")));
                    town.roads.add(roadId);
                    town.inventory=LocalRoadSettlementMatcher.cachedInventoryCount(context,town.code);
                }
            } catch(Exception invalid) { unresolvedRoads++; }
        }
    }

    void requestInventories(Context context) {
        Context app=context.getApplicationContext();List<String> codes=new ArrayList<>();
        synchronized(inventoryAttempts) {
            long now=System.currentTimeMillis();
            for(Town town:towns.values())if(town.inventory<0
                    &&now-inventoryAttempts.getOrDefault(town.code,0L)>60000) {
                inventoryAttempts.put(town.code,now);codes.add(town.code);
            }
        }
        if(!codes.isEmpty())INVENTORY_WORKER.execute(()->{
            for(String code:codes)try { LocalRoadSettlementMatcher.inventoryCount(app,code); }
                catch(Exception unavailable) { android.util.Log.w("Roadprints","Achievement town inventory unavailable",unavailable); }
        });
    }

    double value(boolean roaming) {
        List<Double> percents=new ArrayList<>();
        for(Town town:towns.values())if(town.inventory>400)percents.add(town.percent());
        percents.sort(Collections.reverseOrder());
        int index=roaming?2:0;
        return percents.size()>index?percents.get(index):0;
    }

    List<String> contributions(double target) {
        List<Town> sorted=new ArrayList<>(towns.values());
        sorted.sort(Comparator.comparingDouble(Town::percent).reversed()
                .thenComparing(t->t.name,String.CASE_INSENSITIVE_ORDER).thenComparing(t->t.code));
        List<String> result=new ArrayList<>();
        for(Town town:sorted) {
            if(town.inventory<0)result.add("○ "+town.name+" · "+town.roads.size()+" roads discovered · town inventory pending");
            else if(town.inventory<=400)result.add("○ "+town.name+" · "+town.inventory+" roads in town · needs more than 400 to qualify");
            else result.add(String.format(Locale.UK,"%s %s · %,d / %,d roads · %.1f%% · %s %.0f%%",
                    town.percent()>=target?"✓":"○",town.name,town.roads.size(),town.inventory,town.percent(),
                    town.percent()>=target?"reached":"target",target));
        }
        if(unresolvedRoads>0)result.add("○ "+unresolvedRoads+" roads await town attribution in Progress");
        if(result.isEmpty())result.add("○ No town discovery evidence yet. Towns need more than 400 roads to qualify.");
        return result;
    }
}
