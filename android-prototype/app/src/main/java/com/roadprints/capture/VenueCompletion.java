package com.roadprints.capture;
import android.content.Context;
import org.json.*;
import java.time.Instant;
import java.util.*;

/** Reviewed active targets and future closures; changes never manufacture a visit. */
final class VenueCompletion {
    static boolean special(JSONObject g){return Arrays.asList("group_percent","catalogue_percent","county_percent","future_closure").contains(g.optString("measure"));}
    static boolean complete(JSONObject root){
        return root!=null&&"reviewed_uk_completion_v1".equals(root.optString("approval_scope"))&&root.optBoolean("coverage_complete")&&root.optJSONArray("venues")!=null&&root.optInt("completion_target",-1)==root.optJSONArray("venues").length()&&root.optJSONObject("coverage_review")!=null&&VenueCatalogueApproval.valid(root);
    }
    static boolean available(Context context,AchievementStore.Definition definition){
        if(definition.venueRule==null||!"county_percent".equals(definition.venueRule.optString("measure")))return true;
        JSONArray catalogue=VenueCollection.catalogue(context,definition.collectionId);
        for(int i=0;i<catalogue.length();i++){
            JSONObject v=catalogue.optJSONObject(i),c=v==null?null:v.optJSONObject("county");
            if(c!=null&&definition.venueRule.optString("county_code").equals(c.optString("code")))return true;
        }return false;
    }
    static boolean supported(JSONObject proof){String source=proof.optString("source");return !proof.optBoolean("pending")&&!proof.optBoolean("dismissed")&&Arrays.asList("confirmed_live_checkin","confirmed_timeline_visit","named_timeline_visit","native_stop","confirmed_native_stop").contains(source);}
    static Set<String> visited(JSONArray records){Set<String> ids=new HashSet<>();for(int i=0;i<records.length();i++){JSONObject p=records.optJSONObject(i);if(p!=null&&supported(p))ids.add(p.optString("venue_id",p.optString("pub_id")));}ids.remove("");return ids;}
    static VenueAchievements.Snapshot calculate(Context context,VenueCollection.Definition collection,JSONObject root,JSONArray records,boolean enabled){
        VenueAchievements.Snapshot out=new VenueAchievements.Snapshot();boolean certified=complete(root);Set<String> visits=enabled?visited(records):Collections.emptySet();
        JSONArray venues=root.optJSONArray("venues");if(venues==null)venues=new JSONArray();
        Map<String,List<JSONObject>> counties=new TreeMap<>();Map<String,String> names=new HashMap<>(),historicCounty=new HashMap<>();
        List<JSONObject> active=new ArrayList<>();
        for(int i=0;i<venues.length();i++){JSONObject v=venues.optJSONObject(i);if(v==null)continue;active.add(v);JSONObject c=v.optJSONObject("county");if(c!=null){String code=c.optString("code");counties.computeIfAbsent(code,k->new ArrayList<>()).add(v);names.put(code,c.optString("name"));historicCounty.put(v.optString("id"),code);}}
        for(int i=0;i<records.length();i++){JSONObject p=records.optJSONObject(i);if(p!=null&&!p.optString("county_code").isEmpty())historicCounty.putIfAbsent(p.optString("venue_id",p.optString("pub_id")),p.optString("county_code"));}
        JSONArray lifecycle=root.optJSONArray("lifecycle");if(lifecycle==null)lifecycle=new JSONArray();
        for(int i=0;i<lifecycle.length();i++){JSONObject v=lifecycle.optJSONObject(i);JSONObject c=v==null?null:v.optJSONObject("county");if(c!=null)historicCounty.put(v.optString("id"),c.optString("code"));}
        android.content.SharedPreferences prefs=context.getSharedPreferences("roadprints_achievements_v1",0);String key=collection.id+":completion_proofs_v1";JSONObject saved;
        try{saved=new JSONObject(prefs.getString(key,"{}"));}catch(Exception invalid){saved=new JSONObject();}
        for(int i=0;i<collection.goals.length();i++){
            JSONObject g=collection.goals.optJSONObject(i);if(!special(g))continue;
            String id=g.optString("id"),measure=g.optString("measure");Set<String> proof=new HashSet<>();List<String> rows=new ArrayList<>();int done=0,total=0;double value=0;
            if("future_closure".equals(measure)){
                for(int n=0;n<lifecycle.length();n++){
                    JSONObject closure=lifecycle.optJSONObject(n);if(closure==null||!qualifyingClosure(root,closure))continue;
                    boolean seen=beforeClosure(records,closure);if(seen){done++;proof.add(closure.optString("id"));}total++;rows.add((seen?"✓ ":"○ ")+closure.optString("name")+" · permanently closed "+closure.optString("permanently_closed_at"));
                }
                value=done>0?1:0;out.display.put(id,done+" qualifying closed pubs visited"+(total==0?" · no verified future closures yet":""));
            }else if("group_percent".equals(measure)){
                total=counties.size();for(String code:counties.keySet()){
                    boolean seen=false;for(String venue:visits)if(code.equals(historicCounty.get(venue))){proof.add(venue);seen=true;}
                    if(seen)done++;rows.add((seen?"✓ ":"○ ")+names.get(code));
                }
                value=total==0?0:100.0*done/total;out.display.put(id,done+" / "+total+" eligible historic counties visited");
            }else{
                List<JSONObject> target="county_percent".equals(measure)?counties.getOrDefault(g.optString("county_code"),Collections.emptyList()):active;
                total=target.size();for(JSONObject v:target){boolean seen=visits.contains(v.optString("id"));if(seen){done++;proof.add(v.optString("id"));}rows.add((seen?"✓ ":"○ ")+v.optString("name")+" · "+v.optString("town"));}
                value=total==0?0:100.0*done/total;out.display.put(id,done+" / "+total+" active pubs visited");
            }
            if(!"future_closure".equals(measure)&&!certified){value=0;out.display.put(id,"Completion awaits a verified active catalogue");}
            if(enabled&&!"future_closure".equals(measure)){
                JSONArray old=saved.optJSONArray(id);boolean retained=old!=null&&old.length()>0;
                if(old!=null)for(int n=0;n<old.length();n++){
                    String venue=old.optString(n);retained&=visits.contains(venue);
                    if("county_percent".equals(measure))retained&=g.optString("county_code").equals(historicCounty.get(venue));
                }
                if(value>=100&&certified){try{saved.put(id,new JSONArray(new TreeSet<>(proof)));}catch(Exception invalid){throw new IllegalStateException(invalid);}}
                else if(retained){value=100;out.display.put(id,out.display.get(id)+" · earned completion retained");}
                else saved.remove(id);
            }
            out.values.put(id,enabled?value:0.0);out.lists.put(id,rows);out.proofs.put(id,proof);
        }
        if(enabled)prefs.edit().putString(key,saved.toString()).apply();return out;
    }
    static boolean qualifyingClosure(JSONObject root,JSONObject closure){
        if(!complete(root))return false;
        try{return "permanently_closed".equals(closure.getString("status"))&&!closure.optString("evidence_url").isEmpty()&&Instant.parse(closure.getString("permanently_closed_at")).isAfter(Instant.parse(root.getString("lifecycle_started_at")))&&Instant.parse(closure.getString("known_active_from")).isBefore(Instant.parse(closure.getString("permanently_closed_at")))&&!Instant.parse(closure.getString("permanently_closed_at")).isAfter(Instant.now());}catch(Exception invalid){return false;}
    }
    private static boolean beforeClosure(JSONArray records,JSONObject closure){
        for(int i=0;i<records.length();i++){JSONObject p=records.optJSONObject(i);if(p==null||!supported(p)||!closure.optString("id").equals(p.optString("venue_id",p.optString("pub_id"))))continue;
            JSONArray underlying=p.optJSONArray("evidence_records");if(underlying==null)underlying=new JSONArray().put(p);
            for(int n=0;n<underlying.length();n++){JSONObject evidence=underlying.optJSONObject(n);if(evidence==null||!supported(evidence))continue;try{if(Instant.parse(evidence.getString("visited_at")).isBefore(Instant.parse(closure.getString("permanently_closed_at"))))return true;}catch(Exception invalid){/* Unknown legacy dates cannot prove an earlier visit. */}}
        }return false;
    }
    static JSONObject archived(Context context,String collection,String venueId){
        try{
            JSONObject root=VenueCatalogueUpdates.forCollection(VenueCollection.definition(collection)).root(context);JSONArray rows=root.optJSONArray("lifecycle");if(rows==null)return null;
            for(int i=0;i<rows.length();i++){JSONObject v=rows.optJSONObject(i);if(v!=null&&venueId.equals(v.optString("id"))&&qualifyingClosure(root,v))return new JSONObject(v.toString()).put("lng",v.getDouble("lon"));}
        }catch(Exception unavailable){return null;}return null;
    }
    static boolean before(JSONObject venue,String at){try{return Instant.parse(at).isBefore(Instant.parse(venue.getString("permanently_closed_at")));}catch(Exception invalid){return false;}}
    static void clear(Context context,String collection){context.getSharedPreferences("roadprints_achievements_v1",0).edit().remove(collection+":completion_proofs_v1").apply();}
    private VenueCompletion(){}
}
