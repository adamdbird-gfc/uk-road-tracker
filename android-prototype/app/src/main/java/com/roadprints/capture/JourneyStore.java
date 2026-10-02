package com.roadprints.capture;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.JsonReader;
import android.util.JsonToken;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public final class JourneyStore {
    private static final String PREFS = "roadprints_journeys_v1";
    private static final String MIGRATED = "journeys_migrated_to_archive";
    private static final String LOW_QUALITY_PRUNED = "insufficient_timeline_journeys_pruned";
    private static final String DATA_REVISION = "journey_archive_revision";
    private static final String PREFIX = "journey_";
    private static final String SUFFIX = ".json";

    public interface JourneyVisitor {
        void visit(JSONObject journey);
    }

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
            bumpDataRevision(context);
            try { ServiceStationStore.recordJourney(context,journey); }
            catch(Exception stationError){android.util.Log.w("Roadprints","Service-station visit check skipped",stationError);}
        } catch (Exception error) {
            throw new IllegalStateException("Could not save journey locally", error);
        }
    }

    public static synchronized int count(Context context) {
        migrateLegacy(context);
        return archiveFiles(context).size();
    }

    /** Cheap token for invalidating screen summaries when saved journey data changes. */
    public static synchronized long dataRevision(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getLong(DATA_REVISION, 0L);
    }

    public static synchronized JSONObject latest(Context context) {
        migrateLegacy(context);
        List<File> files = archiveFiles(context);
        if (files.isEmpty()) return null;
        File latest = Collections.max(files, Comparator.comparingLong(File::lastModified));
        return upgrade(context, read(latest));
    }

    public static synchronized List<JSONObject> all(Context context) {
        migrateLegacy(context);
        List<File> files = archiveFiles(context);
        files.sort((left, right) -> Long.compare(right.lastModified(), left.lastModified()));
        List<JSONObject> journeys = new ArrayList<>();
        for (File file : files) {
            JSONObject journey = upgrade(context, read(file));
            if (journey != null) journeys.add(journey);
        }
        return journeys;
    }

    /**
     * Loads only the compact fields needed to show the journey list. Matcher
     * GeoJSON can be large, so the list screen must not retain every result in
     * memory just to display titles, dates and processing states.
     */
    public static synchronized List<JSONObject> allSummaries(Context context) {
        migrateLegacy(context);
        List<File> files = archiveFiles(context);
        files.sort((left, right) -> Long.compare(right.lastModified(), left.lastModified()));
        List<JSONObject> summaries = new ArrayList<>();
        for (File file : files) {
            try {
                JSONObject summary = readSummary(file);
                if (summary != null) summaries.add(summary);
            } catch (Exception ignored) {
                // A compact summary is best-effort; skip only this malformed record.
            }
        }
        return summaries;
    }

    /**
     * Reads only list-screen fields. Journey files can contain thousands of GPS
     * points and large matched GeoJSON arrays, so materializing the whole file
     * as a String and JSONObject can exhaust the app heap just to draw a list.
     */
    private static JSONObject readSummary(File file) throws IOException, org.json.JSONException {
        if (file == null || !file.exists()) return null;
        JSONObject summary = new JSONObject();
        int routePointCount = 0;
        boolean hasStoredMatch = false;
        try (JsonReader reader = new JsonReader(new InputStreamReader(
                new FileInputStream(file), StandardCharsets.UTF_8))) {
            reader.beginObject();
            while (reader.hasNext()) {
                String name = reader.nextName();
                switch (name) {
                    case "journey_id": case "started_at": case "ended_at":
                    case "distance_meters": case "mode": case "title":
                    case "processing_status": case "error_summary":
                        summary.put(name, readScalar(reader));
                        break;
                    case "source": case "capture_quality":
                    case "stage_statuses": case "processing":
                        summary.put(name, readFlatObject(reader));
                        break;
                    case "route_geometry":
                        routePointCount = readRouteGeometry(reader);
                        break;
                    case "processing_result":
                        hasStoredMatch = readProcessingResult(reader);
                        break;
                    default:
                        reader.skipValue();
                        break;
                }
            }
            reader.endObject();
        }

        summary.put("_route_point_count", routePointCount);
        summary.put("_has_stored_match", hasStoredMatch);
        if (!summary.has("capture_quality")) {
            summary.put("capture_quality", new JSONObject()
                    .put("gps_points", routePointCount)
                    .put("distance_meters", summary.optDouble("distance_meters", 0))
                    .put("status", routePointCount >= 2 ? "usable" : "insufficient_gps_data"));
        } else {
            JSONObject quality = summary.optJSONObject("capture_quality");
            if (quality != null && !quality.has("gps_points")) {
                quality.put("gps_points", routePointCount);
            }
        }
        return summary;
    }

    private static Object readScalar(JsonReader reader) throws IOException {
        JsonToken token = reader.peek();
        if (token == JsonToken.STRING || token == JsonToken.NUMBER) return reader.nextString();
        if (token == JsonToken.BOOLEAN) return reader.nextBoolean();
        if (token == JsonToken.NULL) { reader.nextNull(); return JSONObject.NULL; }
        reader.skipValue();
        return JSONObject.NULL;
    }

    /** Keeps metadata objects small and flat, ignoring unexpected nested payloads. */
    private static JSONObject readFlatObject(JsonReader reader)
            throws IOException, org.json.JSONException {
        JSONObject value = new JSONObject();
        if (reader.peek() != JsonToken.BEGIN_OBJECT) { reader.skipValue(); return value; }
        reader.beginObject();
        while (reader.hasNext()) {
            String name = reader.nextName();
            JsonToken token = reader.peek();
            if (token == JsonToken.STRING || token == JsonToken.NUMBER
                    || token == JsonToken.BOOLEAN || token == JsonToken.NULL) {
                value.put(name, readScalar(reader));
            } else {
                reader.skipValue();
            }
        }
        reader.endObject();
        return value;
    }

    private static int readRouteGeometry(JsonReader reader) throws IOException {
        if (reader.peek() != JsonToken.BEGIN_OBJECT) { reader.skipValue(); return 0; }
        int points = 0;
        reader.beginObject();
        while (reader.hasNext()) {
            String name = reader.nextName();
            if ("coordinates".equals(name)) points = countArrayItems(reader);
            else reader.skipValue();
        }
        reader.endObject();
        return points;
    }

    private static int countArrayItems(JsonReader reader) throws IOException {
        if (reader.peek() != JsonToken.BEGIN_ARRAY) { reader.skipValue(); return 0; }
        int count = 0;
        reader.beginArray();
        while (reader.hasNext()) { reader.skipValue(); count++; }
        reader.endArray();
        return count;
    }

    private static boolean readProcessingResult(JsonReader reader) throws IOException {
        if (reader.peek() != JsonToken.BEGIN_OBJECT) { reader.skipValue(); return false; }
        boolean found = false;
        reader.beginObject();
        while (reader.hasNext()) {
            String name = reader.nextName();
            if ("geojson".equals(name)) found = readGeoJson(reader);
            else reader.skipValue();
        }
        reader.endObject();
        return found;
    }

    private static boolean readGeoJson(JsonReader reader) throws IOException {
        if (reader.peek() != JsonToken.BEGIN_OBJECT) { reader.skipValue(); return false; }
        boolean found = false;
        reader.beginObject();
        while (reader.hasNext()) {
            String name = reader.nextName();
            if ("features".equals(name)) found = readFeatures(reader);
            else reader.skipValue();
        }
        reader.endObject();
        return found;
    }

    private static boolean readFeatures(JsonReader reader) throws IOException {
        if (reader.peek() != JsonToken.BEGIN_ARRAY) { reader.skipValue(); return false; }
        boolean found = false;
        reader.beginArray();
        while (reader.hasNext()) found = readFeature(reader) || found;
        reader.endArray();
        return found;
    }

    private static boolean readFeature(JsonReader reader) throws IOException {
        if (reader.peek() != JsonToken.BEGIN_OBJECT) { reader.skipValue(); return false; }
        boolean found = false;
        reader.beginObject();
        while (reader.hasNext()) {
            String name = reader.nextName();
            if ("geometry".equals(name)) found = readMatchedGeometry(reader) || found;
            else reader.skipValue();
        }
        reader.endObject();
        return found;
    }

    private static boolean readMatchedGeometry(JsonReader reader) throws IOException {
        if (reader.peek() != JsonToken.BEGIN_OBJECT) { reader.skipValue(); return false; }
        String type = "";
        boolean line = false;
        reader.beginObject();
        while (reader.hasNext()) {
            String name = reader.nextName();
            if ("type".equals(name) && reader.peek() == JsonToken.STRING) {
                type = reader.nextString();
            } else if ("coordinates".equals(name)) {
                if ("LineString".equals(type)) {
                    line = countArrayItems(reader) >= 2;
                } else if ("MultiLineString".equals(type)) {
                    line = hasMultiLine(reader);
                } else {
                    reader.skipValue();
                }
            } else {
                reader.skipValue();
            }
        }
        reader.endObject();
        return line;
    }

    private static boolean hasMultiLine(JsonReader reader) throws IOException {
        if (reader.peek() != JsonToken.BEGIN_ARRAY) { reader.skipValue(); return false; }
        boolean found = false;
        reader.beginArray();
        while (reader.hasNext()) found = countArrayItems(reader) >= 2 || found;
        reader.endArray();
        return found;
    }

    /** Visit one full journey at a time, so consumers can project large result files and release them. */
    public static synchronized void forEach(Context context, JourneyVisitor visitor) {
        migrateLegacy(context);
        List<File> files = archiveFiles(context);
        files.sort((left, right) -> Long.compare(right.lastModified(), left.lastModified()));
        for (File file : files) {
            JSONObject journey = upgrade(context, read(file));
            if (journey != null) visitor.visit(journey);
        }
    }

    private static boolean hasStoredRoute(JSONObject result) {
        JSONObject geojson = result == null ? null : result.optJSONObject("geojson");
        JSONArray features = geojson == null ? null : geojson.optJSONArray("features");
        if (features == null) return false;
        for (int index = 0; index < features.length(); index++) {
            JSONObject feature = features.optJSONObject(index);
            JSONObject geometry = feature == null ? null : feature.optJSONObject("geometry");
            if (geometry == null) continue;
            String type = geometry.optString("type", "");
            JSONArray coordinates = geometry.optJSONArray("coordinates");
            if ("LineString".equals(type) && coordinates != null && coordinates.length() >= 2) return true;
            if ("MultiLineString".equals(type) && coordinates != null) {
                for (int line = 0; line < coordinates.length(); line++) {
                    JSONArray segment = coordinates.optJSONArray(line);
                    if (segment != null && segment.length() >= 2) return true;
                }
            }
        }
        return false;
    }

    public static synchronized JSONObject get(Context context, String journeyId) {
        migrateLegacy(context);
        return upgrade(context, read(
                new File(context.getFilesDir(), PREFIX + journeyId + SUFFIX)));
    }

    public static synchronized void delete(Context context, String journeyId) {
        migrateLegacy(context);
        File file = new File(context.getFilesDir(), PREFIX + journeyId + SUFFIX);
        if (file.exists()) {
            if (!file.delete()) throw new IllegalStateException("Could not delete journey archive");
            bumpDataRevision(context);
        }
    }

    public static synchronized int countByModes(Context context, String... modes) {
        int count = 0;
        for (JSONObject journey : all(context)) {
            String value = journey.optString("mode", "unknown");
            for (String mode : modes) {
                if (mode.equals(value)) {
                    count++;
                    break;
                }
            }
        }
        return count;
    }

    public static synchronized int deleteByModes(Context context, String... modes) {
        int deleted = 0;
        List<JSONObject> journeys = all(context);
        for (JSONObject journey : journeys) {
            String value = journey.optString("mode", "unknown");
            boolean match = false;
            for (String mode : modes) {
                if (mode.equals(value)) {
                    match = true;
                    break;
                }
            }
            if (match) {
                delete(context, journey.optString("journey_id"));
                deleted++;
            }
        }
        return deleted;
    }

    public static synchronized int deleteAll(Context context) {
        int deleted = 0;
        for (JSONObject journey : all(context)) {
            delete(context, journey.optString("journey_id"));
            deleted++;
        }
        ServiceStationStore.clearOnDeleteAll(context);
        return deleted;
    }

    public static synchronized void updateTitle(Context context, String journeyId, String title) {
        migrateLegacy(context);
        File file = new File(context.getFilesDir(), PREFIX + journeyId + SUFFIX);
        JSONObject journey = read(file);
        if (journey == null) return;
        try {
            String trimmed = title == null ? "" : title.trim();
            if (trimmed.length() > 0) journey.put("title", trimmed);
            else journey.remove("title");
            journey.put("revision", journey.optInt("revision", 1) + 1);
            save(context, journey);
        } catch (Exception error) {
            throw new IllegalStateException("Could not update journey title", error);
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

    private static void pruneInsufficientTimelineJourneys(Context context) {
        SharedPreferences preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (preferences.getBoolean(LOW_QUALITY_PRUNED, false)) return;

        boolean complete = true;
        for (File file : archiveFiles(context)) {
            JSONObject journey = read(file);
            if (!isInsufficientTimelineJourney(journey)) continue;
            if (!file.delete()) complete = false;
            else bumpDataRevision(context);
        }
        if (complete) {
            preferences.edit().putBoolean(LOW_QUALITY_PRUNED, true).apply();
        }
    }

    private static boolean isInsufficientTimelineJourney(JSONObject journey) {
        if (journey == null) return false;
        JSONObject source = journey.optJSONObject("source");
        if (source == null || !"timeline_import".equals(source.optString("type", ""))) {
            return false;
        }
        String mode = journey.optString("mode", "unknown");
        JSONObject quality = journey.optJSONObject("capture_quality");
        if ("driving".equals(mode) || "bus".equals(mode)) {
            return quality == null || quality.optInt("source_route_points", 0) < 2;
        }
        if ("walking".equals(mode) || "running".equals(mode) || "pedestrian".equals(mode)) {
            return quality == null || quality.optInt("gps_points", 0) < 2;
        }
        return false;
    }

    private static JSONObject upgrade(Context context, JSONObject journey) {
        if (journey == null) return null;
        try {
            boolean needsUpgrade = !journey.has("places") || !journey.has("stops")
                    || !journey.has("capture_quality");
            ensureSharedFields(journey);
            if (needsUpgrade) save(context, journey);
        } catch (Exception ignored) {
            // Preserve the readable journey even if a best-effort upgrade fails.
        }
        return journey;
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

    private static void bumpDataRevision(Context context) {
        SharedPreferences preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        preferences.edit().putLong(DATA_REVISION,
                preferences.getLong(DATA_REVISION, 0L) + 1L).apply();
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
