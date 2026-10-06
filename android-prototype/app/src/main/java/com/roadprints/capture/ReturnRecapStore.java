package com.roadprints.capture;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONObject;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Small persisted inbox; an import or rematch never becomes a new recording. */
final class ReturnRecapStore {
    static final String PREFS = "roadprints_return_recap";
    private ReturnRecapStore() {}

    static synchronized List<JSONObject> collect(Context context, List<JSONObject> summaries, long now) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!prefs.contains("visited_at")) {
            prefs.edit().putLong("visited_at", now).apply();
            return new ArrayList<>(); // Upgrading must not recap the existing archive.
        }
        long previous = prefs.getLong("visited_at", now);
        Set<String> pending = new HashSet<>(prefs.getStringSet("pending", new HashSet<>()));
        List<JSONObject> result = new ArrayList<>();
        Set<String> present = new HashSet<>();
        for (JSONObject journey : summaries) {
            if (!isRecording(journey)) continue;
            String id = journey.optString("journey_id");
            long ended = time(journey.optString("ended_at"));
            if (!id.isEmpty() && (pending.contains(id) || (ended > previous && ended <= now))) {
                present.add(id);
                result.add(journey);
            }
        }
        prefs.edit().putLong("visited_at", now).putStringSet("pending", present).apply();
        return result;
    }

    static synchronized void dismiss(Context context, Set<String> ids) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        Set<String> pending = new HashSet<>(prefs.getStringSet("pending", new HashSet<>()));
        pending.removeAll(ids);
        prefs.edit().putStringSet("pending", pending).apply();
    }

    static boolean isRecording(JSONObject journey) {
        JSONObject source = journey.optJSONObject("source");
        return source != null && "android_activity_capture".equals(source.optString("type"));
    }

    static long time(String value) {
        try { return Instant.parse(value).toEpochMilli(); }
        catch (Exception ignored) { return Long.MIN_VALUE; }
    }

    static void clear(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply();
    }
}
