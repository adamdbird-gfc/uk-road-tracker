package com.roadprints.capture;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

public final class JourneyStore {
    private static final String PREFS = "roadprints_journeys_v1";
    private static final String COUNT = "journey_count";

    private JourneyStore() {}

    public static void save(Context context, JSONObject journey) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        int count = prefs.getInt(COUNT, 0);
        prefs.edit()
                .putString("journey:" + journey.optString("journey_id"), journey.toString())
                .putInt(COUNT, count + 1)
                .apply();
    }

    public static int count(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(COUNT, 0);
    }
}
