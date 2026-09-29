package com.roadprints.capture;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public final class JourneyStore {
    private static final String PREFS = "roadprints_journeys_v1";
    private static final String MIGRATED = "journeys_migrated_to_archive";
    private static final String PREFIX = "journey_";
    private static final String SUFFIX = ".json";

    private JourneyStore() {}

    public static synchronized void save(Context context, JSONObject journey) {
        migrateLegacy(context);
        String id = journey.optString("journey_id", "unknown");
        File target = new File(context.getFilesDir(), PREFIX + id + SUFFIX);
        File temporary = new File(context.getFilesDir(), PREFIX + id + ".tmp");
        try (FileOutputStream output = new FileOutputStream(temporary)) {
            ensureSharedFields(journey);
            output.write(journey.toString().getBytes(StandardCharsets.UTF_8));
            output.flush();
            if (target.exists() && !target.delete()) {
                throw new IllegalStateException("Could not replace journey archive");
            }
            if (!temporary.renameTo(target)) {
                throw new IllegalStateException("Could not commit journey archive");
            }
        } catch (Exception error) {
            throw new IllegalStateException("Could not save journey locally", error);
        }
    }

    public static synchronized int count(Context context) {
        migrateLegacy(context);
        return archiveFiles(context).size();
    }

    public static synchronized JSONObject latest(Context context) {
        migrateLegacy(context);
        List<File> files = archiveFiles(context);
        if (files.isEmpty()) return null;
        File latest = Collections.max(files, Comparator.comparingLong(File::lastModified));
        return read(latest);
    }

    public static synchronized List<JSONObject> all(Context context) {
        migrateLegacy(context);
        List<File> files = archiveFiles(context);
        files.sort((left, right) -> Long.compare(right.lastModified(), left.lastModified()));
        List<JSONObject> journeys = new ArrayList<>();
        for (File file : files) {
            JSONObject journey = read(file);
            if (journey != null) journeys.add(journey);
        }
        return journeys;
    }

    public static synchronized JSONObject get(Context context, String journeyId) {
        migrateLegacy(context);
        return read(new File(context.getFilesDir(), PREFIX + journeyId + SUFFIX));
    }

    public static synchronized void delete(Context context, String journeyId) {
        migrateLegacy(context);
        File file = new File(context.getFilesDir(), PREFIX + journeyId + SUFFIX);
        if (file.exists() && !file.delete()) {
            throw new IllegalStateException("Could not delete journey archive");
        }
    }

    public static synchronized void updateMode(Context context, String journeyId, String mode) {
        migrateLegacy(context);
        File file = new File(context.getFilesDir(), PREFIX + journeyId + SUFFIX);
        JSONObject journey = read(file);
        if (journey == null) return;
        try {
            journey.put("mode", mode);
            journey.put("revision", journey.optInt("revision", 1) + 1);
            journey.put("transport_confirmation", "confirmed");
            save(context, journey);
        } catch (Exception error) {
            throw new IllegalStateException("Could not update journey mode", error);
        }
    }

    private static void ensureSharedFields(JSONObject journey)
            throws org.json.JSONException {
        if (!journey.has("places")) journey.put("places", new JSONArray());
        if (!journey.has("stops")) journey.put("stops", new JSONArray());
        if (!journey.has("capture_quality")) {
            JSONObject geometry = journey.optJSONObject("route_geometry");
            JSONArray coordinates = geometry == null
                    ? null : geometry.optJSONArray("coordinates");
            int points = coordinates == null ? 0 : coordinates.length();
            double distance = journey.optDouble("distance_meters", 0);
            journey.put("capture_quality", new JSONObject()
                    .put("gps_points", points)
                    .put("distance_meters", distance)
                    .put("status", points >= 2 ? "usable" : "insufficient_gps_data"));
        }
    }

    private static List<File> archiveFiles(Context context) {
        File[] files = context.getFilesDir().listFiles((directory, name) ->
                name.startsWith(PREFIX) && name.endsWith(SUFFIX));
        List<File> result = new ArrayList<>();
        if (files != null) Collections.addAll(result, files);
        return result;
    }

    private static JSONObject read(File file) {
        if (file == null || !file.exists()) return null;
        try (FileInputStream input = new FileInputStream(file);
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(input, StandardCharsets.UTF_8))) {
            StringBuilder text = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) text.append(line);
            return new JSONObject(text.toString());
        } catch (Exception error) {
            return null;
        }
    }

    private static synchronized void migrateLegacy(Context context) {
        SharedPreferences preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (preferences.getBoolean(MIGRATED, false)) return;

        preferences.edit().putBoolean(MIGRATED, true).apply();
        for (Map.Entry<String, ?> entry : preferences.getAll().entrySet()) {
            if (!entry.getKey().startsWith("journey:") || !(entry.getValue() instanceof String)) continue;
            try {
                JSONObject journey = new JSONObject((String) entry.getValue());
                if (!journey.has("transport_confirmation")) {
                    journey.put("transport_confirmation", "required");
                }
                save(context, journey);
            } catch (Exception ignored) {
                // Keep migration best-effort; a malformed legacy item must not block new captures.
            }
        }
        preferences.edit().putBoolean(MIGRATED, true).apply();
    }
}
