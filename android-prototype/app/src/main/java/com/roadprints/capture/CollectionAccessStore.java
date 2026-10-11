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
        if(SERVICES.equals(id))return "Motorway Service Stations";
        if(SPOONS.equals(id))return "UK Wetherspoons Collection";
        return "None";
    }
    static boolean known(String id){return SERVICES.equals(id)||SPOONS.equals(id);}
    static boolean entitled(Context context,String id) {
        if(SERVICES.equals(id))return ServiceStationStore.unlocked(context);
        return SPOONS.equals(id)&&prefs(context).getBoolean("wetherspoons_test_access",false);
    }
    static boolean installed(Context context,String id) {
        // Existing Services owners retain their current installation after upgrade.
        return entitled(context,id)&&prefs(context).getBoolean(id+"_installed",SERVICES.equals(id));
    }
    static List<String> installed(Context context) {
        List<String> result=new ArrayList<>();
        for(String id:new String[]{SERVICES,SPOONS})if(installed(context,id))result.add(id);
        return result;
    }
    static synchronized void installForTesting(Context context,String id) {
        if(!known(id))throw new IllegalArgumentException("Unknown collection");
        if(SERVICES.equals(id)&&!entitled(context,id))ServiceStationStore.unlockForTesting(context);
        SharedPreferences.Editor edit=prefs(context).edit();
        if(SPOONS.equals(id))edit.putBoolean("wetherspoons_test_access",true);
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
        if(ids.size()==1)return ids.get(0);
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
