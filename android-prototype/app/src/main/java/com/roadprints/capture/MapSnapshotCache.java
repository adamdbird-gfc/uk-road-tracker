package com.roadprints.capture;

import android.content.Context;
import android.util.AtomicFile;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/** Atomic, compressed map snapshots, independent of the small general screen-cache limit. */
final class MapSnapshotCache {
    private static final int LIMIT=48*1024*1024;
    private static AtomicFile file(Context c) {
        return new AtomicFile(new File(c.getFilesDir(),"screen-cache/map-routes.gz"));
    }
    static JSONObject read(Context c,long revision) {
        try(InputStream in=new GZIPInputStream(file(c).openRead());ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            byte[] buffer=new byte[8192];int count;
            while((count=in.read(buffer))!=-1){if(out.size()+count>LIMIT)return null;out.write(buffer,0,count);}
            JSONObject root=new JSONObject(out.toString("UTF-8"));
            if(root.getInt("format")!=1||root.getLong("revision")!=revision)return null;
            JSONObject payload=root.getJSONObject("data");
            if(!NcnJson.hash(payload.toString().getBytes(StandardCharsets.UTF_8)).equals(root.getString("sha256")))return null;
            return payload;
        } catch(Exception invalid) {return null;}
    }
    static void write(Context c,long revision,JSONObject data) {
        AtomicFile file=file(c);FileOutputStream raw=null;
        try {
            byte[] payload=data.toString().getBytes(StandardCharsets.UTF_8);
            if(payload.length>LIMIT){android.util.Log.i("RoadprintsTiming","map_cache_write=over_limit");return;}
            JSONObject root=new JSONObject().put("format",1).put("revision",revision)
                    .put("data",data).put("sha256",NcnJson.hash(payload));
            raw=file.startWrite();
            // finish(), rather than close(), leaves the descriptor for AtomicFile's sync/commit.
            GZIPOutputStream gzip=new GZIPOutputStream(raw);
            gzip.write(root.toString().getBytes(StandardCharsets.UTF_8));gzip.finish();gzip.flush();
            file.finishWrite(raw);raw=null;
        } catch(Exception optional) {if(raw!=null)file.failWrite(raw);}
    }
}
