package com.roadprints.capture;
import android.content.*;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.JSONObject;
import java.io.File;
import java.nio.charset.StandardCharsets;

/** Disposable per-source screen rows. No global invalidation when one archive file changes. */
final class CompactJourneyCache implements AutoCloseable {
    interface Reader {JSONObject read(File file)throws Exception;}
    private final SQLiteDatabase db;private final String kind;
    int reads,hits,writes;
    CompactJourneyCache(Context c,String kind){this.kind=kind;File file=new File(c.getCacheDir(),"compact-"+kind+".db");SQLiteDatabase opened;
        try{opened=open(file);}catch(android.database.sqlite.SQLiteDatabaseCorruptException corrupt){SQLiteDatabase.deleteDatabase(file);opened=open(file);}db=opened;db.beginTransaction();}
    private static SQLiteDatabase open(File file){SQLiteDatabase db=SQLiteDatabase.openOrCreateDatabase(file,null);db.execSQL("CREATE TABLE IF NOT EXISTS rows (kind TEXT,source TEXT,token TEXT,data BLOB,sha TEXT,PRIMARY KEY(kind,source))");return db;}
    JSONObject get(File file,Reader reader)throws Exception {
        String token=JourneyStore.fileToken(file);
        try(Cursor cursor=db.rawQuery("SELECT data,sha FROM rows WHERE kind=? AND source=? AND token=?",new String[]{kind,file.getName(),token})){
            if(cursor.moveToFirst()){byte[] data=cursor.getBlob(0);if(NcnJson.hash(data).equals(cursor.getString(1))){hits++;return new JSONObject(new String(data,StandardCharsets.UTF_8));}}
        }catch(org.json.JSONException|android.database.sqlite.SQLiteException invalid){/* Rebuild only this row. */}
        JSONObject value=reader.read(file);reads++;
        try {if(value!=null&&token.equals(JourneyStore.fileToken(file))) {
            byte[] data=value.toString().getBytes(StandardCharsets.UTF_8);
            if(data.length<=900000){ContentValues row=new ContentValues();row.put("kind",kind);row.put("source",file.getName());row.put("token",token);row.put("data",data);row.put("sha",NcnJson.hash(data));db.insertWithOnConflict("rows",null,row,SQLiteDatabase.CONFLICT_REPLACE);if(++writes%25==0){db.setTransactionSuccessful();db.endTransaction();db.beginTransaction();}}
        }
        }catch(android.database.sqlite.SQLiteException optional){/* Original evidence is authoritative. */}
        return value;
    }
    void invalidate(File file){db.delete("rows","kind=? AND source=?",new String[]{kind,file.getName()});}
    @Override public void close(){if(db.inTransaction()){db.setTransactionSuccessful();db.endTransaction();}db.close();}
}
