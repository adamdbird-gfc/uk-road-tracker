package com.roadprints.capture;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.List;

/** Installation and map preference only; never manufactures or removes visit evidence. */
final class CollectionAccessStore {
    static final String SERVICES="services", SPOONS="wetherspoons", NONE="none";
    static final String PREFS="roadprints_collection_access_v1";
    private CollectionAccessStore() {}
    static String name(String id) {
        if(known(id))return VenueCollection.definition(id).name;
        return "None";
    }
    static boolean known(String id){return VenueCollection.known(id);}
    static boolean entitled(Context context,String id) {
        if(SERVICES.equals(id))return ServiceStationStore.unlocked(context);
        return known(id)&&prefs(context).getBoolean(id+"_test_access",false);
    }
    static boolean installed(Context context,String id) {
        // Existing Services owners retain their current installation after upgrade.
        return entitled(context,id)&&prefs(context).getBoolean(id+"_installed",SERVICES.equals(id));
    }
    static List<String> installed(Context context) {
        List<String> result=new ArrayList<>();
        for(VenueCollection.Definition definition:VenueCollection.all())if(installed(context,definition.id))result.add(definition.id);
        return result;
    }
    static synchronized void installForTesting(Context context,String id) {
        if(!known(id))throw new IllegalArgumentException("Unknown collection");
        if(SERVICES.equals(id)&&!entitled(context,id))ServiceStationStore.unlockForTesting(context);
        SharedPreferences.Editor edit=prefs(context).edit();
        if(!SERVICES.equals(id))edit.putBoolean(id+"_test_access",true);
        edit.putBoolean(id+"_installed",true).putLong("revision",revision(context)+1).apply();
        // Retain an existing map selection; Show on map is an explicit separate action.
    }
    static synchronized void remove(Context context,String id) {
        if(!known(id))throw new IllegalArgumentException("Unknown collection");
        prefs(context).edit().putBoolean(id+"_installed",false)
                .putLong("revision",revision(context)+1).apply();
        // Entitlement, catalogue identity and journey-backed visits remain intact.
    }
    static String selected(Context context) {
        List<String> ids=installed(context);
        if(ids.isEmpty())return NONE;
        String value=prefs(context).getString("map_collection",SERVICES);
        return NONE.equals(value)||ids.contains(value)?value:ids.get(0);
    }
    static synchronized boolean select(Context context,String id) {
        if(!NONE.equals(id)&&!installed(context,id))return false;
        prefs(context).edit().putString("map_collection",id).apply();return true;
    }
    static long revision(Context context){return prefs(context).getLong("revision",0);}
    private static SharedPreferences prefs(Context context){return context.getApplicationContext().getSharedPreferences(PREFS,Context.MODE_PRIVATE);}
}
