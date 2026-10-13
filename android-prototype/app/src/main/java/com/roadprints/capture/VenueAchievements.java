package com.roadprints.capture;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.*;

/** Shared goal interpreter. Configuration selects identities; evidence remains in the visit ledger. */
final class VenueAchievements {
    static final class Snapshot {
        final Map<String,Double> values=new LinkedHashMap<>();
        final Map<String,List<String>> lists=new LinkedHashMap<>();
        final Map<String,Set<String>> proofs=new LinkedHashMap<>();
        final Map<String,String> display=new LinkedHashMap<>();
    }
    static Snapshot snapshot(android.content.Context context,VenueCollection.Definition collection){
        Set<String> visited=new HashSet<>();Map<String,String> nations=new HashMap<>();
        if(CollectionAccessStore.installed(context,collection.id)){
            JSONArray records=VenueVisitStore.records(context,collection.id);
            for(int i=0;i<records.length();i++){
                JSONObject proof=records.optJSONObject(i);if(proof==null||proof.optBoolean("pending")||proof.optBoolean("dismissed"))continue;
                String source=proof.optString("source"),id=proof.optString("venue_id");
                if(id.isEmpty()||!("confirmed_live_checkin".equals(source)||"confirmed_timeline_visit".equals(source)||"named_timeline_visit".equals(source)||"native_stop".equals(source)||"confirmed_native_stop".equals(source)))continue;
                visited.add(id);if(!proof.optString("nation").isEmpty())nations.put(id,proof.optString("nation"));
            }
        }
        return calculate(collection,VenueCollection.catalogue(context,collection.id),visited,CollectionAccessStore.installed(context,collection.id),nations);
    }
    static void definitions(VenueCollection.Definition collection,List<AchievementStore.Definition> out){
        JSONArray goals=collection.goals;
        for(int i=0;i<goals.length();i++){
            JSONObject g=goals.optJSONObject(i);
            AchievementStore.Definition d=new AchievementStore.Definition(g.optString("id"),collection.achievementIcon,g.optString("title"),g.optString("description"),g.optString("detail"),collection.achievementType,g.optDouble("target"),null,0,0,0);
            d.collectionId=collection.id;d.unit=g.optString("unit","qualifying visits");
            JSONArray levels=g.optJSONArray("levels"),titles=g.optJSONArray("level_titles");
            if(levels!=null){d.levels=new double[levels.length()];d.levelTitles=new String[levels.length()];for(int n=0;n<levels.length();n++){d.levels[n]=levels.optDouble(n);d.levelTitles[n]=titles.optString(n);}}
            out.add(d);
        }
    }
    static Snapshot calculate(VenueCollection.Definition collection,JSONArray catalogue,Set<String> visited,boolean enabled,Map<String,String> retainedNations){
        Snapshot out=new Snapshot();List<JSONObject> all=new ArrayList<>();
        for(int i=0;i<catalogue.length();i++){JSONObject v=catalogue.optJSONObject(i);if(v!=null&&!v.optString("id").isEmpty())all.add(v);}
        all.sort((a,b)->a.optString("name").compareToIgnoreCase(b.optString("name")));
        // Retained supported history contributes to count and nation goals, even after a catalogue removal.
        List<JSONObject> history=new ArrayList<>(all);Set<String> current=new HashSet<>();for(JSONObject v:all)current.add(v.optString("id"));
        for(String id:visited)if(!current.contains(id))try{history.add(new JSONObject().put("id",id).put("nation",retainedNations.getOrDefault(id,"")));}catch(Exception invalid){throw new IllegalStateException(invalid);}
        for(int i=0;i<collection.goals.length();i++){
            JSONObject goal=collection.goals.optJSONObject(i);String id=goal.optString("id"),measure=goal.optString("measure");
            List<String> rows=new ArrayList<>();Set<String> proof=new HashSet<>();double value;
            List<JSONObject> group=members(goal,all);int count=count(group,visited,proof);
            if("groups".equals(measure)){
                int done=0;JSONArray groups=goal.optJSONArray("groups");
                for(int n=0;n<groups.length();n++){
                    JSONObject item=groups.optJSONObject(n);Set<String> candidates=new HashSet<>();
                    List<JSONObject> subset=members(item,collection.siteStop?all:history);
                    // Explicit nation on a retained proof takes precedence over current catalogue metadata.
                    JSONObject selector=item.optJSONObject("selector");
                    if(selector!=null&&"nation".equals(selector.optString("field"))){subset=new ArrayList<>();for(JSONObject v:history)if(selector.optString("equals").equals(retainedNations.getOrDefault(v.optString("id"),field(v,"nation"))))subset.add(v);}
                    boolean seen=count(subset,visited,candidates)>0;
                    if(seen){
                        done++;
                        if(collection.siteStop){for(JSONObject v:subset)if(visited.contains(v.optString("id"))){proof.add(v.optString("id"));break;}}
                        else proof.addAll(candidates);
                    }
                    rows.add((seen?"✓ ":"○ ")+item.optString("label"));
                }
                value=done;out.display.put(id,done+" / "+(int)goal.optDouble("target")+" "+goal.optString("unit","groups"));
            }else{
                value="percent".equals(measure)?(group.isEmpty()?0:100.0*count/group.size()):"any".equals(measure)?(count>0?1:0):"distinct_history".equals(measure)?visited.size():count;
                for(JSONObject v:group)rows.add((visited.contains(v.optString("id"))?"✓ ":"○ ")+v.optString("goal_label",label(v,collection)));
                out.display.put(id,count+" / "+group.size()+" "+collection.plural+" visited"+(group.isEmpty()?"":" · "+String.format(Locale.UK,"%.1f%%",100.0*count/group.size())));
            }
            out.values.put(id,enabled?value:0.0);out.lists.put(id,rows);out.proofs.put(id,proof);
        }
        return out;
    }
    private static String label(JSONObject v,VenueCollection.Definition c){return v.optString("name")+" · "+v.optString(c.siteStop?"road":"town");}
    private static List<JSONObject> members(JSONObject rule,List<JSONObject> all){
        List<JSONObject> rows=new ArrayList<>();JSONArray members=rule.optJSONArray("members");JSONObject selector=rule.optJSONObject("selector");
        if(members!=null){for(int i=0;i<members.length();i++){JSONObject m=members.optJSONObject(i);JSONObject found=null;for(JSONObject v:all)if(m.optString("id").equals(v.optString("id"))){found=v;break;}
            if(found==null&&!m.has("label"))continue;
            try{JSONObject copy=found==null?new JSONObject().put("id",m.optString("id")):new JSONObject(found.toString());if(m.has("label"))copy.put("goal_label",m.optString("label"));rows.add(copy);}catch(Exception invalid){throw new IllegalStateException(invalid);}}
        }else for(JSONObject v:all)if(selector==null||selector.optString("equals").equals(field(v,selector.optString("field"))))rows.add(v);
        return rows;
    }
    private static String field(JSONObject v,String field){
        if("group_road".equals(field))return ("NI".equals(v.optString("region"))?"NI:":"")+v.optString("road");
        if("nation".equals(field)){JSONObject county=v.optJSONObject("county");return v.optString("nation",county==null?"":county.optString("nation"));}
        return v.optString(field);
    }
    private static int count(List<JSONObject> rows,Set<String> visited,Set<String> proof){int n=0;for(JSONObject v:rows)if(visited.contains(v.optString("id"))){n++;proof.add(v.optString("id"));}return n;}
    private VenueAchievements(){}
}
