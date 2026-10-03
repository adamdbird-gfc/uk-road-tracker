package com.roadprints.capture;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/** Durable local source ledger of explicit Google Timeline place visits. */
final class TimelineVisitStore {
    private static final String FILE_NAME = "timeline-place-visits-v1.json";
    private static final Object LOCK = new Object();
    private TimelineVisitStore() {}

    static int merge(Context context, JSONArray incoming) throws Exception {
        if (incoming == null || incoming.length() == 0) return 0;
        synchronized (LOCK) {
            Map<String, JSONObject> visits = readMap(context);
            int before = visits.size();
            for (int i = 0; i < incoming.length(); i++) {
                JSONObject visit = incoming.optJSONObject(i);
                if (visit == null || !Double.isFinite(visit.optDouble("lat", Double.NaN))
                        || !Double.isFinite(visit.optDouble("lng", Double.NaN))) continue;
                String id = visit.optString("id", "");
                if (id.isEmpty()) continue;
                JSONObject prior = visits.get(id);
                if (prior == null) {
                    visits.put(id, new JSONObject(visit.toString()));
                } else {
                    // Enrich an existing stable visit record without replacing its original evidence.
                    for (java.util.Iterator<String> keys = visit.keys(); keys.hasNext();) {
                        String key = keys.next();
                        if (!prior.has(key)) prior.put(key, visit.opt(key));
                    }
                }
            }
            if (visits.size() != before || incoming.length() > 0) write(context, visits);
            return visits.size() - before;
        }
    }

    static JSONArray all(Context context) throws Exception {
        synchronized (LOCK) {
            Map<String, JSONObject> visits = readMap(context);
            JSONArray result = new JSONArray();
            for (JSONObject visit : visits.values()) result.put(visit);
            return result;
        }
    }

    static void clear(Context context) {
        synchronized (LOCK) {
            File file = new File(context.getFilesDir(), FILE_NAME);
            File temp = new File(context.getFilesDir(), FILE_NAME + ".tmp");
            file.delete();
            temp.delete();
        }
    }

    private static Map<String, JSONObject> readMap(Context context) throws Exception {
        Map<String, JSONObject> result = new LinkedHashMap<>();
        File file = new File(context.getFilesDir(), FILE_NAME);
        if (!file.isFile()) return result;
        byte[] bytes;
        try (FileInputStream input = new FileInputStream(file)) {
            bytes = new byte[(int) Math.min(Integer.MAX_VALUE, file.length())];
            int offset = 0, count;
            while (offset < bytes.length && (count = input.read(bytes, offset, bytes.length-offset)) > 0)
                offset += count;
        }
        JSONArray array = new JSONObject(new String(bytes, StandardCharsets.UTF_8)).optJSONArray("visits");
        if (array != null) for (int i=0; i<array.length(); i++) {
            JSONObject visit = array.optJSONObject(i);
            if (visit != null) {
                String id = visit.optString("id", "");
                if (!id.isEmpty()) result.put(id, visit);
            }
        }
        return result;
    }

    private static void write(Context context, Map<String, JSONObject> visits) throws Exception {
        JSONArray array = new JSONArray();
        for (JSONObject visit : visits.values()) array.put(visit);
        JSONObject document = new JSONObject();
        document.put("schema_version", 1);
        document.put("visits", array);
        File file = new File(context.getFilesDir(), FILE_NAME);
        File temp = new File(context.getFilesDir(), FILE_NAME + ".tmp");
        try (FileOutputStream output = new FileOutputStream(temp)) {
            output.write(document.toString().getBytes(StandardCharsets.UTF_8));
            output.getFD().sync();
        }
        if (!temp.renameTo(file)) {
            try (FileOutputStream output = new FileOutputStream(file)) {
                output.write(document.toString().getBytes(StandardCharsets.UTF_8));
                output.getFD().sync();
            }
            temp.delete();
        }
    }
}
