package com.roadprints.capture;

import android.content.Context;
import org.json.*;
import java.util.*;

/** Awards from a completed eligible interval ledger only; never scans journeys or opens SQLite. */
final class NcnAchievements {
    private static final String PREFS="roadprints_ncn_achievements_v1";
    private static String cachedEncoding;
    private static boolean cachedKilometres;
    private static List<AchievementStore.Progress> cachedRows=Collections.emptyList();
    static JSONObject prepare(NcnProgressStore.Snapshot snapshot,JSONObject previous) throws Exception {
        JSONObject history=previous.optJSONObject("history");
        history=history==null?new JSONObject():new JSONObject(history.toString());
        JSONArray rows=new JSONArray();
        double metres=snapshot.ledger.networkMetres("combined");
        add(rows,history,"ncn-first","First NCN discovery",metres>0?1:0,true,metres,0);
        add(rows,history,"ncn-network","National Cycle Network",percent(metres,snapshot.catalogue.networkLength),false,metres,snapshot.catalogue.networkLength);
        for(Map.Entry<String,JSONObject> route:snapshot.catalogue.routes.entrySet()){
            JSONObject data=route.getValue();double total=data.optDouble("length_m",0);
            if(!data.optBoolean("measurable",true)||!Double.isFinite(total)||total<=0)continue;
            double done=snapshot.ledger.metres(route.getKey(),"combined");
            String name=route.getKey().replace(":"," "),label=data.optString("name",name);
            if(!label.equals(name)&&!label.equals(route.getKey()))name+=" · "+label;
            add(rows,history,"ncn-route:"+route.getKey(),name,percent(done,total),false,done,total);
        }
        return new JSONObject().put("catalogue",snapshot.catalogue.version).put("archive_revision",snapshot.archiveRevision).put("rows",rows).put("history",history);
    }
    static double percent(double done,double total){
        if(!Double.isFinite(done)||!Double.isFinite(total)||done<=0||total<=0)return 0;
        // Only absorb floating-point arithmetic noise, never a rounded display percentage.
        if(Math.abs(total-done)<=Math.max(1e-7,total*1e-12))return 100;
        return Math.min(100,100*done/total);
    }
    private static void add(JSONArray rows,JSONObject history,String id,String name,double value,boolean first,double done,double total)throws Exception{
        int level=first?(value>=1?1:0):0;
        if(!first)for(double threshold:new double[]{25,50,75,100})if(value>=threshold)level++;
        JSONObject dates=history.optJSONObject(id);if(dates==null)dates=new JSONObject();
        for(int i=1;i<=level;i++)if(!dates.has(""+i))dates.put(""+i,System.currentTimeMillis());
        history.put(id,dates);
        rows.put(new JSONObject().put("id",id).put("name",name).put("first",first).put("value",value).put("level",level).put("done",done).put("total",total));
    }
    static JSONObject state(Context context){try{return new JSONObject(context.getSharedPreferences(PREFS,0).getString("state","{}"));}catch(Exception absent){return new JSONObject();}}
    // Called only after stable-revision validation, while publishing the associated snapshot.
    static void publish(Context context,JSONObject state){context.getSharedPreferences(PREFS,0).edit().putString("state",state.toString()).apply();}
    static synchronized List<AchievementStore.Progress> rows(Context context){
        String encoded=context.getSharedPreferences(PREFS,0).getString("state","{}");
        boolean km=DistanceUnits.usesKilometres(context);
        if(encoded.equals(cachedEncoding)&&km==cachedKilometres)return cachedRows;
        List<AchievementStore.Progress> result=new ArrayList<>();
        try{
            JSONObject state=new JSONObject(encoded),history=state.optJSONObject("history");JSONArray rows=state.optJSONArray("rows");
            for(int i=0;rows!=null&&i<rows.length();i++){
                JSONObject row=rows.getJSONObject(i);boolean first=row.getBoolean("first");String name=row.getString("name");
                AchievementStore.Definition d=new AchievementStore.Definition(row.getString("id"),"",first?name:name+" completion",
                    first?"Discover your first verified-open NCN section on foot or by bicycle.":"Complete 25%, 50%, 75% and 100% of "+name+" on foot or by bicycle.",
                    "Unique verified-open coverage · walking/running and cycling combined",first?"ncn-discovery":"ncn-completion",first?1:25,null,0,0,0);
                d.collectionId=CollectionAccessStore.NCN;d.unit=first?"discovery":"%";
                if(!first){d.levels=new double[]{25,50,75,100};d.levelTitles=new String[]{name+" · 25%",name+" · 50%",name+" · 75%",name+" · Completed it!"};}
                double value=row.getDouble("value"),target=AchievementStore.nextTarget(d,value);int level=row.getInt("level");
                AchievementStore.Progress p=new AchievementStore.Progress(d,value,(int)Math.min(target,value),(int)target,level>0,
                    first?(level>0?"First qualifying NCN discovery recognised":"Discover a verified-open NCN section"):
                    String.format(Locale.UK,"%.1f%% · %s / %s verified-open",value,DistanceUnits.format(context,row.getDouble("done")),DistanceUnits.format(context,row.getDouble("total")))+(level==4?" · complete":String.format(Locale.UK," · next %.0f%%",target)),null);
                p.level=level;
                List<String> milestones=new ArrayList<>();JSONObject dates=history==null?null:history.optJSONObject(d.id);
                for(int n=1;n<=(first?1:4);n++)if(dates!=null&&dates.has(""+n))milestones.add(AchievementStore.titleFor(d,n)+" · recognised "+new java.text.SimpleDateFormat("d MMM yyyy",Locale.UK).format(new Date(dates.getLong(""+n)))+(n>level?" · previous milestone":""));
                p.milestones=milestones;result.add(p);
            }
        }catch(Exception invalid){result.clear();}
        cachedEncoding=encoded;cachedKilometres=km;cachedRows=Collections.unmodifiableList(result);return cachedRows;
    }
    static List<AchievementStore.Definition> definitions(Context context){List<AchievementStore.Definition> out=new ArrayList<>();for(AchievementStore.Progress p:rows(context))out.add(p.definition);return out;}
}
