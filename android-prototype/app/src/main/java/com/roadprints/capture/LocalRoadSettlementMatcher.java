package com.roadprints.capture;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Resolves matched local-road geometry to public settlement facts. Route geometry is transient. */
final class LocalRoadSettlementMatcher {
    static final String API = "https://uk-road-tracker-api.onrender.com";
    private static final String CACHE = "roadprints_local_settlement_matches_v1";
    private static final String INVENTORIES = "roadprints_local_settlement_inventories_v1";

    static final class Settlement {
        final String code;
        final String name;
        Settlement(String code, String name) { this.code = code; this.name = name; }
    }

    private LocalRoadSettlementMatcher() {}

    static List<Settlement> resolve(Context context, String roadId, List<JSONObject> geometries)
            throws Exception {
        List<Settlement> cached = cached(context, roadId, geometries);
        if (cached != null) return cached;
        String cacheKey = roadId + ":" + Integer.toHexString(geometries.toString().hashCode());
        // The boundary endpoint accepts MultiLineString. Combine every matched
        // segment for this named road so a frequently travelled road only needs
        // one HTTP/PostGIS lookup, even when it appears in many journeys.
        JSONArray lines = new JSONArray();
        for (JSONObject geometry : geometries) {
            if (geometry == null) continue;
            String type = geometry.optString("type", "");
            JSONArray coordinates = geometry.optJSONArray("coordinates");
            if (coordinates == null) continue;
            if ("LineString".equals(type)) {
                if (coordinates.length() >= 2) lines.put(coordinates);
            } else if ("MultiLineString".equals(type)) {
                for (int index = 0; index < coordinates.length(); index++) {
                    JSONArray line = coordinates.optJSONArray(index);
                    if (line != null && line.length() >= 2) lines.put(line);
                }
            }
        }
        if (lines.length() == 0) return new ArrayList<>();
        JSONObject combined = new JSONObject().put("type", "MultiLineString")
                .put("coordinates", lines);
        JSONObject response = request("POST", API + "/settlements-for-geometry",
                new JSONObject().put("geometry", combined));
        Map<String, Settlement> result = new LinkedHashMap<>();
        for (Settlement settlement : decode(response.optJSONArray("settlements"))) {
            result.put(settlement.code.isEmpty() ? settlement.name : settlement.code, settlement);
        }
        Thread.sleep(1050L);
        JSONArray values = new JSONArray();
        for (Settlement settlement : result.values()) {
            values.put(new JSONObject().put("code", settlement.code).put("name", settlement.name));
        }
        JSONObject record = new JSONObject().put("settlements", values);
        context.getSharedPreferences(CACHE, Context.MODE_PRIVATE).edit()
                .putString(cacheKey, record.toString()).apply();
        return new ArrayList<>(result.values());
    }

    static List<Settlement> cached(
            Context context, String roadId, List<JSONObject> geometries) throws Exception {
        String cacheKey = roadId + ":" + Integer.toHexString(geometries.toString().hashCode());
        String saved = context.getSharedPreferences(CACHE, Context.MODE_PRIVATE)
                .getString(cacheKey, null);
        if (saved == null) return null;
        return decode(new JSONObject(saved).optJSONArray("settlements"));
    }

    static int inventoryCount(Context context, String code) throws Exception {
        String saved = context.getSharedPreferences(INVENTORIES, Context.MODE_PRIVATE)
                .getString(code, null);
        if (saved != null) return Integer.parseInt(saved);
        JSONObject response;
        try {
            response = request("GET", API + "/settlement-inventories/" + code, null);
        } catch (HttpFailure error) {
            if (error.status != 404) throw error;
            response = request("POST", API + "/settlement-inventories/request",
                    new JSONObject().put("settlement_code", code));
        }
        JSONObject inventory = response.optJSONObject("inventory");
        if (inventory == null) return -1;
        if ("not_requested".equals(inventory.optString("status"))) {
            response = request("POST", API + "/settlement-inventories/request",
                    new JSONObject().put("settlement_code", code));
            inventory = response.optJSONObject("inventory");
        }
        if (inventory == null || !"ready".equals(inventory.optString("status"))) return -1;
        int count = inventory.optInt("road_count", -1);
        if (count >= 0) context.getSharedPreferences(INVENTORIES, Context.MODE_PRIVATE)
                .edit().putString(code, Integer.toString(count)).apply();
        return count;
    }

    static JSONObject boundary(String code) throws Exception {
        try {
            JSONObject response = request("GET", API + "/settlement-boundaries/" + code, null);
            JSONObject boundary = response.optJSONObject("boundary");
            if (boundary != null) return boundary;
        } catch (Exception ignored) {
            // The published public boundary files provide a read-only fallback.
        }
        return request("GET", "https://adamdbird-gfc.github.io/uk-road-tracker/"
                + "settlement-inventories-v1/" + code + "-boundary.geojson", null);
    }

    private static List<Settlement> decode(JSONArray array) {
        List<Settlement> output = new ArrayList<>();
        if (array == null) return output;
        for (int index = 0; index < array.length(); index++) {
            JSONObject row = array.optJSONObject(index);
            if (row == null || row.optString("name").isEmpty()) continue;
            output.add(new Settlement(row.optString("code", ""), row.optString("name")));
        }
        return output;
    }

    private static JSONObject request(String method, String endpoint, JSONObject body)
            throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(12000);
        connection.setReadTimeout(25000);
        connection.setRequestProperty("Accept", "application/json");
        if (body != null) {
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            try (OutputStream output = connection.getOutputStream()) {
                output.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }
        }
        int status = connection.getResponseCode();
        InputStream stream = status >= 200 && status < 300
                ? connection.getInputStream() : connection.getErrorStream();
        StringBuilder text = new StringBuilder();
        if (stream != null) try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) text.append(line);
        }
        connection.disconnect();
        if (status < 200 || status >= 300) throw new HttpFailure(status, text.toString());
        return new JSONObject(text.toString());
    }

    private static final class HttpFailure extends Exception {
        final int status;
        HttpFailure(int status, String message) { super(message); this.status = status; }
    }
}
