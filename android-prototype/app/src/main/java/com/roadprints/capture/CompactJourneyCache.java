package com.roadprints.capture;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.JSONObject;
import org.json.JSONArray;
import java.io.File;
import java.nio.charset.StandardCharsets;

/** Disposable per-source screen rows. No global invalidation when one archive file changes. */
final class CompactJourneyCache implements AutoCloseable {
    interface Reader {JSONObject read(File file)throws Exception;}
    private final SQLiteDatabase db;private final String kind;
    int reads,hits,writes,skipped;
    long payloadBytes, payloadLookupNanos, payloadDecodeNanos;
    private final boolean measurePayload;
    CompactJourneyCache(Context context, String kind) {
        this.kind = kind;
        measurePayload = "progress-v1".equals(kind);
        // Accepted road projections take minutes to rebuild for an owner-sized
        // journey archive. Android may evict getCacheDir() contents at any time,
        // including while installing a ~1GB APK. Keep only these derived road
        // contributions in durable no-backup app storage. Original journeys
        // and their source tokens remain authoritative.
        boolean durable = kind.startsWith(RoadReference.ALGORITHM + "-");
        File file = new File(durable ? context.getNoBackupFilesDir() : context.getCacheDir(),
                "compact-" + kind + ".db");
        File legacy = new File(context.getCacheDir(), "compact-" + kind + ".db");
        boolean alreadyPresent = file.exists();
        SQLiteDatabase opened;
        try {
            opened = open(file);
        } catch (android.database.sqlite.SQLiteDatabaseCorruptException corrupt) {
            SQLiteDatabase.deleteDatabase(file);
            opened = open(file);
        }
        if (durable && !alreadyPresent && legacy.exists()) {
            // Migration is opportunistic and never deletes the legacy source.
            // SQLite ATTACH reads the complete committed DB, including any WAL,
            // while avoiding a second in-memory copy of thousands of JSON rows.
            try {
                opened.execSQL("ATTACH DATABASE ? AS legacy_cache",
                        new Object[]{legacy.getAbsolutePath()});
                try {
                    opened.execSQL("INSERT OR IGNORE INTO rows(kind,source,token,data,sha)"
                            + " SELECT kind,source,token,data,sha FROM legacy_cache.rows WHERE kind=?",
                            new Object[]{kind});
                    opened.execSQL("INSERT OR IGNORE INTO row_index(kind,source,token,data,sha)"
                            + " SELECT kind,source,token,data,sha FROM legacy_cache.row_index WHERE kind=?",
                            new Object[]{kind});
                } finally {
                    opened.execSQL("DETACH DATABASE legacy_cache");
                }
            } catch (android.database.sqlite.SQLiteException migrationUnavailable) {
                android.util.Log.w("RoadprintsTiming", "local_road_cache_migration=unavailable");
            }
        }
        if (durable) {
            int rows = 0;
            try (Cursor status = opened.rawQuery("SELECT COUNT(*) FROM rows WHERE kind=?",
                    new String[]{kind})) {
                if (status.moveToFirst()) rows = status.getInt(0);
            } catch (android.database.sqlite.SQLiteException unavailable) {
                android.util.Log.w("RoadprintsTiming", "local_road_cache_count=unavailable");
            }
            PerformanceDiagnostics.timing("local_road_cache_storage", 0,
                    "store=non_backup_app_files prior_exists=" + alreadyPresent
                            + " legacy_exists=" + legacy.exists() + " rows=" + rows);
        }
        db = opened;
        db.beginTransaction();
    }

    private static SQLiteDatabase open(File file) {
        SQLiteDatabase db = SQLiteDatabase.openOrCreateDatabase(file, null);
        db.execSQL("CREATE TABLE IF NOT EXISTS rows (kind TEXT,source TEXT,token TEXT,"
                + " data BLOB,sha TEXT,PRIMARY KEY(kind,source))");
        db.execSQL("CREATE TABLE IF NOT EXISTS row_index (kind TEXT,source TEXT,token TEXT,"
                + " data TEXT,sha TEXT,PRIMARY KEY(kind,source))");
        return db;
    }

    /** Bulk scan of compact indexes. Only confirmed exclusions need to be kept. */
    java.util.Map<String, String> excludedSources(java.util.function.Predicate<JSONArray> wanted) {
        java.util.Map<String, String> excluded = new java.util.HashMap<>();
        try (Cursor cursor = db.rawQuery("SELECT source,token,data,sha FROM row_index WHERE kind=?",
                new String[]{kind})) {
            while (cursor.moveToNext()) {
                if (Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException();
                try {
                    String data = cursor.getString(2);
                    if (NcnJson.hash(data.getBytes(StandardCharsets.UTF_8)).equals(cursor.getString(3))
                            && !wanted.test(new JSONArray(data))) {
                        excluded.put(cursor.getString(0), cursor.getString(1));
                    }
                } catch (org.json.JSONException invalid) { /* Validate from the original payload. */ }
            }
        } catch (android.database.sqlite.SQLiteException invalid) {
            excluded.clear();
        }
        return excluded;
    }

    JSONObject get(File file, Reader reader) throws Exception {
        return get(file, reader, null, null);
    }
    JSONObject getRoad(File file, Reader reader, boolean includeMajorSpans) throws Exception {
        return get(file, reader, null, null, !includeMajorSpans);
    }
    JSONObject get(File file, Reader reader, java.util.function.Predicate<JSONArray> wanted,
                   java.util.function.Function<JSONObject, JSONArray> indexer) throws Exception {
        return get(file, reader, wanted, indexer, false);
    }
    private JSONObject get(File file, Reader reader, java.util.function.Predicate<JSONArray> wanted,
                   java.util.function.Function<JSONObject, JSONArray> indexer,
                   boolean omitMajorSpans) throws Exception {
        String token=JourneyStore.fileToken(file);
        JSONArray index = null;
        if (wanted != null) {
            try (Cursor cursor = db.rawQuery(
                    "SELECT data,sha FROM row_index WHERE kind=? AND source=? AND token=?",
                    new String[]{kind, file.getName(), token})) {
                if (cursor.moveToFirst()) {
                    String data = cursor.getString(0);
                    if (NcnJson.hash(data.getBytes(StandardCharsets.UTF_8)).equals(cursor.getString(1))) {
                        index = new JSONArray(data);
                        if (!wanted.test(index)) { skipped++; return null; }
                    }
                }
            } catch (org.json.JSONException | android.database.sqlite.SQLiteException invalid) {
                // A missing screen index falls back to the authoritative row.
            }
        }
        JSONObject value = null;
        long lookupStarted = measurePayload ? System.nanoTime() : 0;
        long decodeStarted = 0;
        try (Cursor cursor = db.rawQuery("SELECT data,sha FROM rows WHERE kind=? AND source=? AND token=?",
                new String[]{kind, file.getName(), token})) {
            if (cursor.moveToFirst()) {
                byte[] data = cursor.getBlob(0);
                String expected = cursor.getString(1);
                if (measurePayload) {
                    decodeStarted = System.nanoTime();
                    payloadBytes += data.length;
                }
                if (NcnJson.hash(data).equals(expected)) {
                    value = omitMajorSpans ? readLocalSpans(data)
                            : new JSONObject(new String(data, StandardCharsets.UTF_8));
                    hits++;
                }
            }
        } catch (org.json.JSONException | android.database.sqlite.SQLiteException invalid) {
            // Rebuild only this derived row.
        } finally {
            if (measurePayload) {
                long end = System.nanoTime();
                payloadLookupNanos += (decodeStarted == 0 ? end : decodeStarted) - lookupStarted;
                if (decodeStarted != 0) payloadDecodeNanos += end - decodeStarted;
            }
        }
        boolean fresh = value == null;
        if (fresh) { value = reader.read(file); reads++; }
        if (value == null) return null;
        if (fresh || (index == null && indexer != null)) try {
            if (token.equals(JourneyStore.fileToken(file))) {
                if (index == null && indexer != null) {
                    index = indexer.apply(value);
                    String encoded = index.toString();
                    if (encoded.length() <= 64_000) {
                        ContentValues lookup = key(file, token);
                        lookup.put("data", encoded);
                        lookup.put("sha", NcnJson.hash(encoded.getBytes(StandardCharsets.UTF_8)));
                        db.insertWithOnConflict("row_index", null, lookup, SQLiteDatabase.CONFLICT_REPLACE);
                        commitPeriodically();
                    }
                }
                if (fresh) {
                    byte[] data = value.toString().getBytes(StandardCharsets.UTF_8);
                    if (data.length <= 900_000) {
                        ContentValues row = key(file, token);
                        row.put("data", data);
                        row.put("sha", NcnJson.hash(data));
                        db.insertWithOnConflict("rows", null, row, SQLiteDatabase.CONFLICT_REPLACE);
                        commitPeriodically();
                    }
                }
            }
        } catch (android.database.sqlite.SQLiteException optional) { /* Original evidence is authoritative. */ }
        return wanted != null && index != null && !wanted.test(index) ? null : value;
    }
    /** Driving/cycling do not consume the walking-to-A-road reconciliation payload. */
    private static JSONObject readLocalSpans(byte[] data) throws Exception {
        JSONObject value = new JSONObject();
        try (android.util.JsonReader reader = new android.util.JsonReader(
                new java.io.InputStreamReader(new java.io.ByteArrayInputStream(data),
                        StandardCharsets.UTF_8))) {
            reader.beginObject();
            while (reader.hasNext()) {
                String field = reader.nextName();
                if ("majorSpans".equals(field) || "majorIntervals".equals(field))
                    reader.skipValue();
                else value.put(field, JourneyStore.readEvidenceValue(reader));
            }
            reader.endObject();
        }
        return value;
    }
    private ContentValues key(File file, String token) {
        ContentValues row = new ContentValues();
        row.put("kind", kind);
        row.put("source", file.getName());
        row.put("token", token);
        return row;
    }

    private void commitPeriodically() {
        if (++writes % 25 == 0) {
            db.setTransactionSuccessful();
            db.endTransaction();
            db.beginTransaction();
        }
    }

    void invalidate(File file) {
        String[] key = {kind, file.getName()};
        db.delete("rows", "kind=? AND source=?", key);
        db.delete("row_index", "kind=? AND source=?", key);
    }
    @Override public void close(){if(db.inTransaction()){db.setTransactionSuccessful();db.endTransaction();}db.close();}
}

