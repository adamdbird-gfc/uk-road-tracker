package com.roadprints.capture;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/** Explicitly confirmed links between captured journeys and service stations. */
final class ServiceStationVisitStore {
    private static final String PREFS = "roadprints_collections_v1";
    private static final String RECORDS = "service_station_confirmed_journey_visits_v1";

    private ServiceStationVisitStore() {}

    static synchronized void confirm(Context context, String stationId, String journeyId) {
        if (stationId == null || stationId.isEmpty()
                || journeyId == null || journeyId.isEmpty()) return;
        JSONArray records = read(context);
        for (int i = 0; i < records.length(); i++) {
            JSONObject record = records.optJSONObject(i);
            if (record != null && stationId.equals(record.optString("station_id", ""))
                    && journeyId.equals(record.optString("journey_id", ""))) return;
        }
        JSONObject record = new JSONObject();
        try {
            record.put("station_id", stationId);
            record.put("journey_id", journeyId);
            record.put("confirmed_at", Instant.now().toString());
            record.put("source", "user_confirmed_capture");
            records.put(record);
        } catch (org.json.JSONException ignored) {
            return;
        }
        write(context, records);
    }

    static synchronized Set<String> stationIds(Context context) {
        Set<String> result = new HashSet<>();
        JSONArray records = read(context);
        for (int i = 0; i < records.length(); i++) {
            JSONObject record = records.optJSONObject(i);
            if (record != null) {
                String id = record.optString("station_id", "");
                if (!id.isEmpty()) result.add(id);
            }
        }
        return result;
    }

    static synchronized Set<String> journeyIdsForStation(Context context, String stationId) {
        Set<String> result = new HashSet<>();
        JSONArray records = read(context);
        for (int i = 0; i < records.length(); i++) {
            JSONObject record = records.optJSONObject(i);
            if (record != null && stationId.equals(record.optString("station_id", ""))) {
                String id = record.optString("journey_id", "");
                if (!id.isEmpty()) result.add(id);
            }
        }
        return result;
    }

    static synchronized void clear(Context context) {
        prefs(context).edit().remove(RECORDS).apply();
    }

    private static JSONArray read(Context context) {
        try {
            return new JSONArray(prefs(context).getString(RECORDS, "[]"));
        } catch (Exception ignored) {
            return new JSONArray();
        }
    }

    private static void write(Context context, JSONArray records) {
        prefs(context).edit().putString(RECORDS, records.toString()).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
