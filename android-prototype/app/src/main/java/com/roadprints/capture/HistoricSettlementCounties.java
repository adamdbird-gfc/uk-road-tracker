package com.roadprints.capture;

import android.content.Context;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Public boundary memberships, keyed by settlement ID, never travel evidence. */
final class HistoricSettlementCounties {
    private static HistoricSettlementCounties shared;
    private static boolean unavailable;
    final String version;
    final Map<String, Membership> settlements = new HashMap<>();

    static final class Membership {
        final List<String> codes;
        final List<String> names;
        Membership(List<String> codes, List<String> names) {
            this.codes = Collections.unmodifiableList(codes);
            this.names = Collections.unmodifiableList(names);
        }
    }

    HistoricSettlementCounties(Context context) throws IOException {
        JSONObject countyRoot = read(context, "catalogue.json");
        JSONObject root = read(context, "settlements.bin");
        try {
            version = root.getString("county_version");
            if (root.getInt("format") != 1 || !version.equals(countyRoot.getString("version"))
                    || !root.getString("county_source_sha256").equals(countyRoot.getString("source_sha256")))
                throw new IOException("Settlement and county references differ");
            Map<String, String> countyNames = new HashMap<>();
            JSONArray counties = countyRoot.getJSONArray("counties");
            for (int i = 0; i < counties.length(); i++) {
                JSONObject county = counties.getJSONObject(i);
                if (countyNames.put(county.getString("code"), county.getString("name")) != null)
                    throw new IOException("Duplicate historic county ID");
            }
            if (countyNames.size() != HistoricCountyCatalog.COUNTY_COUNT)
                throw new IOException("Incomplete historic county reference");
            JSONObject rows = root.getJSONObject("settlements");
            Iterator<String> keys = rows.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                JSONArray values = rows.getJSONArray(key);
                List<String> codes = new ArrayList<>(), names = new ArrayList<>();
                Set<String> seen = new HashSet<>();
                if (values.length() == 0) throw new IOException("Empty settlement county membership");
                for (int i = 0; i < values.length(); i++) {
                    String code = values.getString(i), name = countyNames.get(code);
                    if (name == null || !seen.add(code)) throw new IOException("Invalid settlement county ID");
                    codes.add(code); names.add(name);
                }
                settlements.put(key, new Membership(codes, names));
            }
        } catch (JSONException error) {
            throw new IOException("Invalid settlement county reference", error);
        }
    }

    private static JSONObject read(Context context, String name) throws IOException {
        try (InputStream raw = context.getAssets().open(HistoricCountyCatalog.ASSETS + name);
             InputStream input = name.endsWith(".bin") ? new GZIPInputStream(raw) : raw) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192]; int n;
            while ((n = input.read(buffer)) != -1) bytes.write(buffer, 0, n);
            try { return new JSONObject(bytes.toString(StandardCharsets.UTF_8.name())); }
            catch (JSONException error) { throw new IOException("Invalid reference " + name, error); }
        }
    }

    static synchronized void align(Context context, List<LocalRoadSettlementMatcher.Settlement> rows) {
        if (rows.isEmpty() || unavailable) return;
        if (shared == null) {
            try { shared = new HistoricSettlementCounties(context.getApplicationContext()); }
            catch (IOException error) {
                // Reference enrichment must never discard a valid road/town cache.
                unavailable = true;
                android.util.Log.w("Roadprints", "Historic settlement reference unavailable", error);
                return;
            }
        }
        for (LocalRoadSettlementMatcher.Settlement row : rows) {
            Membership membership = shared.settlements.get(row.code);
            // Old labels and similarly named places must never guess membership.
            row.historicCountyCodes = membership == null ? Collections.emptyList() : membership.codes;
            row.historicCountyNames = membership == null ? Collections.emptyList() : membership.names;
            row.historicCountyVersion = membership == null ? "" : shared.version;
        }
    }

    static String displayGroup(LocalRoadSettlementMatcher.Settlement row) {
        return row.historicCountyNames.isEmpty()
                ? "Historic county not yet mapped" : row.historicCountyNames.get(0);
    }
}
