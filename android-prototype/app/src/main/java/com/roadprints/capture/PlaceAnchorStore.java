package com.roadprints.capture;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.time.Instant;

/** One compact local last-stop record, included in existing preferences backup. */
final class PlaceAnchorStore {
    private static final String PREFS="roadprints_place_anchor_v1";
    static JSONObject last(Context context) {
        try {return new JSONObject(context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getString("stop",""));}
        catch(Exception absent){return null;}
    }
    static void record(Context context,JSONObject journey) {
        JSONArray stops=journey.optJSONArray("stops");if(stops==null)return;
        JSONObject old=last(context);
        for(int i=0;i<stops.length();i++) {
            JSONObject stop=stops.optJSONObject(i);if(stop==null||!"arrival".equals(stop.optString("role")))continue;
            try {
                boolean same=old!=null&&old.optString("id").equals(stop.optString("id"));
                long time=Instant.parse(stop.getString("arrived_at")).toEpochMilli();
                long previous=old==null?0:Instant.parse(old.getString("arrived_at")).toEpochMilli();
                if(!same&&time<previous)continue;
                if("rejected".equals(stop.optString("classification"))) {if(same)clear(context);continue;}
                context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putString("stop",stop.toString()).apply();
                old=stop;
            } catch(Exception invalid){ /* Invalid or imported legacy stops are not origin evidence. */ }
        }
    }
    static void forget(Context context,String journeyId) {
        JSONObject stop=last(context);
        if(stop!=null&&journeyId.equals(stop.optString("journey_id")))clear(context);
    }
    static void clear(Context context){context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().clear().apply();}
}
