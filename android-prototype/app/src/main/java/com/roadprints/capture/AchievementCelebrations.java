package com.roadprints.capture;

import android.content.Context;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/** Earned milestones and celebration delivery have separate durable state. */
final class AchievementCelebrations {
    private static final String PREFS="roadprints_achievement_celebrations_v1";
    static final class Event {
        final AchievementStore.Definition definition;
        final int level;
        Event(AchievementStore.Definition definition,int level){this.definition=definition;this.level=level;}
    }
    private AchievementCelebrations(){}
    private static JSONObject seen(Context context){
        try{return new JSONObject(context.getSharedPreferences(PREFS,0).getString("seen_levels","{}"));}
        catch(Exception invalid){return new JSONObject();}
    }
    static synchronized List<Event> pending(Context context){
        JSONObject seen=seen(context);List<Event> out=new ArrayList<>();
        for(AchievementStore.Progress progress:AchievementStore.catalogueSnapshot(context).achievements){
            AchievementStore.Definition d=progress.definition;
            if(!progress.unlocked||progress.level<=seen.optInt(d.id))continue;
            if("service-station".equals(d.type)&&!ServiceStationStore.unlocked(context))continue;
            if(!d.collectionId.isEmpty()&&!CollectionAccessStore.SERVICES.equals(d.collectionId)&&!CollectionAccessStore.installed(context,d.collectionId))continue;
            out.add(new Event(d,progress.level));
        }
        return out;
    }
    static synchronized void acknowledge(Context context,Event event){
        // Delivery acknowledgement can never unlock a badge or acknowledge a future level.
        for(AchievementStore.Progress progress:AchievementStore.catalogueSnapshot(context).achievements){
            if(!progress.definition.id.equals(event.definition.id))continue;
            if(!progress.unlocked||event.level<=0||event.level>progress.level)return;
            JSONObject seen=seen(context);
            try{seen.put(event.definition.id,Math.max(seen.optInt(event.definition.id),event.level));}
            catch(Exception invalid){throw new IllegalStateException(invalid);}
            if(!context.getSharedPreferences(PREFS,0).edit().putString("seen_levels",seen.toString()).commit())
                throw new IllegalStateException("Celebration acknowledgement could not be saved");
            return;
        }
    }
}
