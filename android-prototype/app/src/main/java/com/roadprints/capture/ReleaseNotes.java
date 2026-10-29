package com.roadprints.capture;

import android.content.Context;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Bundled, versioned notes stay available offline and never show a future release. */
final class ReleaseNotes {
    static final class Change {
        final String title;
        final String description;
        Change(String title, String description) {
            this.title = title;
            this.description = description;
        }
    }

    static final class Release {
        final String versionName;
        final int versionCode;
        final LocalDate date;
        final String since;
        final List<Change> changes;
        Release(String versionName, int versionCode, LocalDate date, String since, List<Change> changes) {
            this.versionName = versionName;
            this.versionCode = versionCode;
            this.date = date;
            this.since = since;
            this.changes = Collections.unmodifiableList(changes);
        }
    }

    static List<Release> load(Context context) throws IOException, JSONException {
        try (InputStream input = context.getAssets().open("release-notes.json");
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            return parse(new String(output.toByteArray(), StandardCharsets.UTF_8),
                    BuildConfig.VERSION_CODE, BuildConfig.VERSION_NAME);
        }
    }

    static List<Release> parse(String raw, int installedCode, String installedName) throws JSONException {
        JSONObject document = new JSONObject(raw);
        if (document.getInt("schema_version") != 1) throw new JSONException("Unsupported release notes");
        JSONArray releases = document.getJSONArray("releases");
        Set<Integer> codes = new HashSet<>();
        List<Release> result = new ArrayList<>();
        boolean currentFound = false;
        for (int i = 0; i < releases.length(); i++) {
            JSONObject row = releases.getJSONObject(i);
            int code = row.getInt("version_code");
            if (code <= 0 || !codes.add(code)) throw new JSONException("Duplicate or invalid release code");
            String name = required(row, "version_name");
            LocalDate date;
            try { date = LocalDate.parse(required(row, "release_date")); }
            catch (java.time.DateTimeException error) { throw new JSONException("Invalid release date"); }
            List<Change> changes = new ArrayList<>();
            JSONArray entries = row.getJSONArray("changes");
            for (int j = 0; j < entries.length(); j++) {
                JSONObject entry = entries.getJSONObject(j);
                changes.add(new Change(required(entry, "title"), required(entry, "description")));
            }
            if (changes.isEmpty()) throw new JSONException("Release has no notes");
            if (code == installedCode) {
                if (!installedName.equals(name)) throw new JSONException("Release version does not match app");
                currentFound = true;
            }
            if (code <= installedCode) result.add(new Release(name, code, date, row.optString("since", ""), changes));
        }
        if (!currentFound) throw new JSONException("Installed release has no notes");
        Collections.sort(result, (a, b) -> Integer.compare(b.versionCode, a.versionCode));
        return Collections.unmodifiableList(result);
    }

    private static String required(JSONObject row, String key) throws JSONException {
        String value = row.getString(key).trim();
        if (value.isEmpty()) throw new JSONException("Missing release note text");
        return value;
    }

    private ReleaseNotes() { }
}
