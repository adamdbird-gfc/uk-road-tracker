package com.roadprints.capture;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;

final class ContextConsent {
    static final String COPY="Nearby map data helps Roadprints recognise arrivals and transport changes. "
            +"When enabled, Roadprints requests an approximate map area from our shared service. "
            +"The service caches public OpenStreetMap data and may request that area from its map provider. "
            +"These requests contain no journey trace, names or journey identifiers. "
            +"Movement recording and offline station recognition still work with this off. "
            +"You can change this in Utilities > Tracking settings.";
    static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences("roadprints_capture_state",Context.MODE_PRIVATE);
    }
    static void choose(Context context,boolean enabled) {
        preferences(context).edit().putBoolean("public_map_context",enabled).putBoolean("context_explained_v1",true).apply();
    }
    static void maybeExplain(Activity activity) {
        if(activity.isFinishing()||activity.isDestroyed())return;
        if(!activity.getSharedPreferences("roadprints_onboarding",Context.MODE_PRIVATE).getBoolean("complete",false)
                || preferences(activity).getBoolean("context_explained_v1",false))return;
        preferences(activity).edit().putBoolean("context_explained_v1",true).apply();
        boolean enabled=ContextMapCache.enabled(activity);
        RoadprintsDialog.show(activity,"Smarter journey stops",COPY,null,
                enabled?"KEEP ENABLED":"ENABLE MAP CONTEXT",()->choose(activity,true),
                enabled?"TURN OFF":"NOT NOW",()->choose(activity,false));
    }
}
