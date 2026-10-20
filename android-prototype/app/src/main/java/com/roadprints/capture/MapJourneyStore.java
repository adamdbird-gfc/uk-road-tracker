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
    static final int FORMAT=1;
    interface Factory { JSONObject build(JSONObject journey) throws Exception; }
    interface Visitor { void visit(Record record) throws Exception; }
    final Context context;
    private SQLiteDatabase db;
    private final boolean bypass;
    int reused,rebuilt,sourceReads,legacyReads,writes;
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
            if(cached)try(Cursor cursor=db.rawQuery("SELECT payload,payload_sha FROM entries WHERE source=? AND token=? AND format=?",
                    new String[]{source.getName(),token,Integer.toString(FORMAT)})) {
                if(cursor.moveToFirst()&&!cursor.isNull(0)) {
                    byte[] bytes=cursor.getBlob(0);
                    if(NcnJson.hash(bytes).equals(cursor.getString(1))) {
                        JSONObject value=new JSONObject(new String(bytes,StandardCharsets.UTF_8));
                        for(String key:new String[]{"motorway","a_road","roads","motorways"})value.getJSONArray(key);
                        reused++;return value;
                    }
                }
            } catch(org.json.JSONException invalid) { /* Rebuild this derived row only. */ }
            return rebuild(factory);
        }
        JSONObject rebuild(Factory factory) throws Exception {
            JSONObject value=factory.build(raw());rebuilt++;
            save(this,value);cached=true;
            return value;
        }
    }
    private static JSONObject header(JSONObject journey) throws Exception {
        JSONObject value=new JSONObject();
        for(String key:new String[]{"journey_id","mode","processing_status","started_at","ended_at",
                "gps_point_count","_route_point_count","source","capture_quality","walking_outcome"})
            if(journey.has(key))value.put(key,journey.opt(key));
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
            if(bytes.length<=32*1024*1024){row.put("payload",bytes);row.put("payload_sha",NcnJson.hash(bytes));}
        }
        db.insertWithOnConflict("entries",null,row,SQLiteDatabase.CONFLICT_REPLACE);
        // Small transactions allow interrupted first-use materialisation to resume.
        if(++writes%25==0){db.setTransactionSuccessful();db.endTransaction();db.beginTransaction();}
    }
    void forEach(BooleanSupplier cancelled,Visitor visitor) throws Exception {
        JourneyStore.prepareArchive(context);
        for(File source:JourneyStore.archiveFiles(context)) {
            if(cancelled.getAsBoolean()||Thread.currentThread().isInterrupted())throw new java.util.concurrent.CancellationException();
            String token=JourneyStore.fileToken(source);JSONObject head=null;
            if(!bypass)try(Cursor cursor=db.rawQuery("SELECT header,header_sha FROM entries WHERE source=? AND token=? AND format=?",
                    new String[]{source.getName(),token,Integer.toString(FORMAT)})) {
                if(cursor.moveToFirst()) {
                    byte[] bytes=cursor.getBlob(0);
                    if(NcnJson.hash(bytes).equals(cursor.getString(1)))head=new JSONObject(new String(bytes,StandardCharsets.UTF_8));
                }
            } catch(org.json.JSONException corrupt) {head=null;}
            JSONObject raw=null;
            if(head==null) {
                raw=bypass?null:JourneyProjectionCache.read(context,source,"map-v1");
                if(raw!=null)legacyReads++;
                else {raw=JourneyStore.mapEvidence(context,source,true);sourceReads++;}
                if(raw==null)continue;
                head=header(raw);
            }
            Record record=new Record(source,token,head,raw,raw==null);
            if(raw!=null)save(record,null);
            visitor.visit(record);
        }
    }
    @Override public void close() {
        if(db!=null){if(db.inTransaction()){db.setTransactionSuccessful();db.endTransaction();}db.close();db=null;}
    }
}

