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
        boolean baseline = !prefs.contains("known_recordings");
        boolean legacy = prefs.contains("visited_at");
        long previous = prefs.getLong("visited_at", now);
        Set<String> known = new HashSet<>(prefs.getStringSet("known_recordings", new HashSet<>()));
        Set<String> pending = new HashSet<>(prefs.getStringSet("pending", new HashSet<>()));
        Set<String> acknowledged = new HashSet<>(prefs.getStringSet("acknowledged", new HashSet<>()));
        List<JSONObject> result = new ArrayList<>();
        Set<String> present = new HashSet<>();
        for (JSONObject journey : summaries) {
            if (!isRecording(journey)) continue;
            String id = journey.optString("journey_id");
            long ended = time(journey.optString("ended_at"));
            if (id.isEmpty() || ended == Long.MIN_VALUE || ended > now) continue;
            // Completion time can precede an early app visit: saving happens
            // asynchronously. Discover newly archived IDs, not a moving time window.
            boolean fresh = baseline ? legacy && ended > previous : !known.contains(id);
            if (fresh && !acknowledged.contains(id)) pending.add(id);
            known.add(id);
            present.add(id);
            if (pending.contains(id)) result.add(journey);
        }
        // A capture may enqueue after the summary snapshot was read. Never erase
        // an unseen pending ID merely because this older snapshot lacks it.
        prefs.edit().putLong("visited_at", now).putStringSet("known_recordings", known)
                .putStringSet("pending", pending).apply();
        return result;
    }

    /** Enqueue only the first committed save of an actual recording, never an import/rematch. */
    static synchronized void record(Context context, JSONObject journey) {
        if (!isRecording(journey) || journey.optString("journey_id").isEmpty()) return;
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        Set<String> pending = new HashSet<>(prefs.getStringSet("pending", new HashSet<>()));
        if (prefs.getStringSet("acknowledged", new HashSet<>()).contains(journey.optString("journey_id"))) return;
        pending.add(journey.optString("journey_id"));
        // Capture completion runs off the UI thread. Persist before broadcasting completion.
        if (!prefs.edit().putStringSet("pending", pending).commit())
            android.util.Log.w("RoadprintsTiming", "new_journeys_persist=failed");
        PerformanceDiagnostics.timing("new_journeys_created", 0, "pending=" + pending.size());
    }

    static boolean inboxReady(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("inbox_migrated",false);
    }
    static void inboxReady(Context context, boolean ready) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("inbox_migrated",ready).apply();
    }

    /** Read just the durable inbox before any optional historical discovery scan. */
    static synchronized List<JSONObject> pending(Context context) throws Exception {
        List<JSONObject> rows = new ArrayList<>();
        SharedPreferences prefs=context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        Set<String> pending=new HashSet<>(prefs.getStringSet("pending",new HashSet<>()));
        Set<String> stale=new HashSet<>();
        for (String id : pending) {
            if (id.contains("/") || id.indexOf((char)92)>=0) {stale.add(id);continue;}
            java.io.File source=new java.io.File(context.getFilesDir(), "journey_" + id + ".json");
            if(!source.isFile()){stale.add(id);continue;}
            try {
                JSONObject row = JourneyStore.readSummary(source);
                if (row != null && isRecording(row)) rows.add(row);
            } catch(Exception unavailable) { /* One interrupted read must not hide other pending recordings. */ }
        }
        if(!stale.isEmpty()){pending.removeAll(stale);prefs.edit().putStringSet("pending",pending).apply();}
        rows.sort((left,right) -> right.optString("ended_at").compareTo(left.optString("ended_at")));
        return rows;
    }

    static synchronized void dismiss(Context context, Set<String> ids) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        Set<String> pending = new HashSet<>(prefs.getStringSet("pending", new HashSet<>()));
        pending.removeAll(ids);
        Set<String> acknowledged = new HashSet<>(prefs.getStringSet("acknowledged", new HashSet<>()));
        acknowledged.addAll(ids);
        PerformanceDiagnostics.timing("new_journeys_acknowledged", 0, "count=" + ids.size());
        prefs.edit().putStringSet("pending", pending).putStringSet("acknowledged", acknowledged).apply();
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
