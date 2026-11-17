package com.roadprints.capture;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.JSONObject;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.function.BooleanSupplier;

/** Disposable compact records. Original journeys stay authoritative; each complete row is atomic. */
final class MapJourneyStore implements AutoCloseable {
    static final int FORMAT=2;
    interface Factory { JSONObject build(JSONObject journey) throws Exception; }
    interface Visitor { void visit(Record record) throws Exception; }
    final Context context;
    private SQLiteDatabase db;
    private final boolean bypass;
    int reused,rebuilt,sourceReads,legacyReads,writes,oversized;
    long payloadBytes, payloadLookupNanos, payloadDecodeNanos;
    MapJourneyStore(Context context) {this(context,false);}
    MapJourneyStore(Context context,boolean bypass) {
        this.bypass=bypass;
        this.context=context.getApplicationContext();
        File file=new File(context.getCacheDir(),"map-journeys-v1.db");
        try {open(file);}
        catch(android.database.sqlite.SQLiteDatabaseCorruptException corrupt) {
            if(db!=null)db.close();
            SQLiteDatabase.deleteDatabase(file);open(file);
        }
        db.beginTransaction();
    }
    private void open(File file) {
        db=SQLiteDatabase.openOrCreateDatabase(file,null);
        db.execSQL("CREATE TABLE IF NOT EXISTS entries (source TEXT PRIMARY KEY, token TEXT NOT NULL, format INTEGER NOT NULL, header BLOB NOT NULL, header_sha TEXT NOT NULL, payload BLOB, payload_sha TEXT)");
    }
    final class Record {
        final File source;final String token;final JSONObject header;
        private JSONObject original;
        private boolean cached;
        Record(File source,String token,JSONObject header,JSONObject original,boolean cached) {
            this.source=source;this.token=token;this.header=header;this.original=original;this.cached=cached;
        }
        JSONObject raw() throws Exception {
            if(original==null) {
                original=bypass?null:JourneyProjectionCache.read(context,source,"map-v1");
                if(original!=null)legacyReads++;
                else {original=JourneyStore.mapEvidence(context,source,true);sourceReads++;}
            }
            return original;
        }
        JSONObject prepared(Factory factory) throws Exception {
            if (cached) {
                long lookupStarted = System.nanoTime();
                long decodeStarted = 0;
                try (Cursor cursor = db.rawQuery("SELECT payload,payload_sha FROM entries WHERE source=? AND token=? AND format=?",
                        new String[]{source.getName(), token, Integer.toString(FORMAT)})) {
                    if (cursor.moveToFirst() && !cursor.isNull(0)) {
                        byte[] bytes = cursor.getBlob(0);
                        String expected = cursor.getString(1);
                        decodeStarted = System.nanoTime();
                        payloadBytes += bytes.length;
                        if (NcnJson.hash(bytes).equals(expected)) {
                            JSONObject value = readCoveragePayload(bytes);
                            reused++;
                            return value;
                        }
                    }
                } catch (org.json.JSONException | java.io.IOException | IllegalStateException invalid) { /* Rebuild this derived row only. */ }
                finally {
                    long end = System.nanoTime();
                    payloadLookupNanos += (decodeStarted == 0 ? end : decodeStarted) - lookupStarted;
                    if (decodeStarted != 0) payloadDecodeNanos += end - decodeStarted;
                }
            }
            return rebuild(factory);
        }
        JSONObject rebuild(Factory factory) throws Exception {
            JSONObject value=factory.build(raw());rebuilt++;
            save(this,value);cached=true;
            return value;
        }
    }
    /** Read major-road contributions without materialising legacy journey polylines. */
    private static JSONObject readCoveragePayload(byte[] bytes) throws Exception {
        JSONObject value = new JSONObject();
        try (android.util.JsonReader reader = new android.util.JsonReader(
                new java.io.InputStreamReader(new java.io.ByteArrayInputStream(bytes),
                        StandardCharsets.UTF_8))) {
            reader.beginObject();
            while (reader.hasNext()) {
                String key = reader.nextName();
                if ("motorway".equals(key) || "a_road".equals(key))
                    value.put(key, JourneyStore.readEvidenceValue(reader));
                else reader.skipValue();
            }
            reader.endObject();
        }
        value.getJSONArray("motorway");
        value.getJSONArray("a_road");
        return value;
    }

    private static JSONObject header(JSONObject journey) throws Exception {
        JSONObject value=new JSONObject();
        for(String key:new String[]{"journey_id","mode","processing_status","started_at","ended_at",
                "gps_point_count","_route_point_count","capture_quality","walking_outcome"})
            if(journey.has(key))value.put(key,journey.opt(key));
        JSONObject source=journey.optJSONObject("source");
        if(source!=null)value.put("source",new JSONObject().put("type",source.optString("type")));
        value.put("_has_stored_match",JourneyFilters.hasStoredMatch(journey));
        return value;
    }
    private void save(Record record,JSONObject prepared) throws Exception {
        if(!record.token.equals(JourneyStore.fileToken(record.source)))return;
        byte[] head=record.header.toString().getBytes(StandardCharsets.UTF_8);
        ContentValues row=new ContentValues();
        row.put("source",record.source.getName());row.put("token",record.token);row.put("format",FORMAT);
        row.put("header",head);row.put("header_sha",NcnJson.hash(head));
        if(prepared!=null) {
            byte[] bytes=prepared.toString().getBytes(StandardCharsets.UTF_8);
            // Keep rows below Android's CursorWindow limit; larger originals remain fully usable.
            if(bytes.length<=1024*1024){row.put("payload",bytes);row.put("payload_sha",NcnJson.hash(bytes));}
            else oversized++;
        }
        db.insertWithOnConflict("entries",null,row,SQLiteDatabase.CONFLICT_REPLACE);
        // Small transactions allow interrupted first-use materialisation to resume.
        if(++writes%25==0){db.setTransactionSuccessful();db.endTransaction();db.beginTransaction();}
    }
    void forEach(BooleanSupplier cancelled,Visitor visitor) throws Exception {
        JourneyStore.prepareArchive(context);
        java.util.List<File> files = JourneyStore.archiveFiles(context);
        // Headers are small. Keep their order from the archive enumeration,
        // which also determines which lines fit the existing display budget.
        java.util.Map<String, CachedHeader> headers = bypass
                ? java.util.Collections.emptyMap() : readHeaders(cancelled);
        for (File source : files) {
            checkCancelled(cancelled);
            String token = JourneyStore.fileToken(source);
            CachedHeader known = bypass ? null : headers.remove(source.getName());
            JSONObject head = null;
            if (known != null && token.equals(known.token)) {
                try { head = new JSONObject(known.data); }
                catch (org.json.JSONException invalid) { /* Rebuild this record. */ }
            }
            if (!bypass && known == null) head = readHeader(source, token);
            JSONObject raw=null;
            if(head==null) {
                raw=bypass?null:JourneyProjectionCache.read(context,source,"map-v1");
                if(raw!=null)legacyReads++;
                else {raw=JourneyStore.mapEvidence(context,source,true);sourceReads++;}
                if(raw==null)continue;
                head=header(raw);
            }
            Record record=new Record(source,token,head,raw,raw==null);
            visitor.visit(record);
            // A prepared payload commits its header in the same write. Save a
            // header separately only for rows that did not need a payload.
            if (raw != null && !record.cached) save(record, null);
        }
    }
    private record CachedHeader(String token, String data) {}

    private java.util.Map<String, CachedHeader> readHeaders(BooleanSupplier cancelled) {
        java.util.Map<String, CachedHeader> headers = new java.util.HashMap<>();
        try (Cursor cursor = db.rawQuery("SELECT source,token,header,header_sha FROM entries WHERE format=?",
                new String[]{Integer.toString(FORMAT)})) {
            while (headers.size() < 4096 && cursor.moveToNext()) {
                checkCancelled(cancelled);
                byte[] bytes = cursor.getBlob(2);
                if (bytes.length <= 4096 && NcnJson.hash(bytes).equals(cursor.getString(3))) {
                    headers.put(cursor.getString(0), new CachedHeader(cursor.getString(1),
                            new String(bytes, StandardCharsets.UTF_8)));
                }
            }
        } catch (android.database.sqlite.SQLiteException invalid) {
            headers.clear();
        }
        return headers;
    }

    private JSONObject readHeader(File source, String token) {
        try (Cursor cursor = db.rawQuery("SELECT header,header_sha FROM entries WHERE source=? AND token=? AND format=?",
                new String[]{source.getName(), token, Integer.toString(FORMAT)})) {
            if (cursor.moveToFirst()) {
                byte[] bytes = cursor.getBlob(0);
                if (NcnJson.hash(bytes).equals(cursor.getString(1))) {
                    return new JSONObject(new String(bytes, StandardCharsets.UTF_8));
                }
            }
        } catch (org.json.JSONException | android.database.sqlite.SQLiteException invalid) {
            // Fallback to the source if the disposable record cannot be read.
        }
        return null;
    }

    private static void checkCancelled(BooleanSupplier cancelled) {
        if (cancelled.getAsBoolean() || Thread.currentThread().isInterrupted()) {
            throw new java.util.concurrent.CancellationException();
        }
    }

    @Override public void close() {
        if(db!=null){if(db.inTransaction()){db.setTransactionSuccessful();db.endTransaction();}db.close();db=null;}
    }
}

