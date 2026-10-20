package com.roadprints.capture;

import android.content.Context;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/** Rebuildable per-file projections: a changed journey never invalidates its neighbours. */
final class JourneyProjectionCache {
    private static final int MAX_BYTES=32*1024*1024;
    private JourneyProjectionCache() {}
    private static File file(Context c, File source, String kind) {
        return new File(new File(c.getCacheDir(),"journey-projections/"+kind),
                source.getName()+".gz");
    }
    static JSONObject read(Context c, File source, String kind) {
        String token=JourneyStore.fileToken(source);
        File cached=file(c,source,kind);
        if(!cached.isFile())return null;
        try(InputStream in=new GZIPInputStream(new FileInputStream(cached));
                ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            byte[] buffer=new byte[8192];int count;
            while((count=in.read(buffer))!=-1){if(out.size()+count>MAX_BYTES)return null;out.write(buffer,0,count);}
            JSONObject root=new JSONObject(out.toString("UTF-8"));
            if(!token.equals(root.optString("source_token"))||!token.equals(JourneyStore.fileToken(source)))return null;
            JSONObject data=root.getJSONObject("data");
            if(!NcnJson.hash(data.toString().getBytes(StandardCharsets.UTF_8)).equals(root.optString("sha256")))return null;
            return data;
        } catch(Exception invalid) { return null; }
    }
    static void write(Context c, File source, String token, String kind, JSONObject data) {
        if(data==null||!token.equals(JourneyStore.fileToken(source)))return;
        File target=file(c,source,kind),temp=null;
        try {
            if(!target.getParentFile().isDirectory()&&!target.getParentFile().mkdirs())return;
            // Unique temporaries allow independent map/summary readers without collisions.
            temp=File.createTempFile("projection-",".tmp",target.getParentFile());
            byte[] payload=data.toString().getBytes(StandardCharsets.UTF_8);
            if(payload.length>MAX_BYTES)return;
            JSONObject root=new JSONObject().put("source_token",token).put("data",data)
                    .put("sha256",NcnJson.hash(payload));
            try(OutputStream out=new GZIPOutputStream(new FileOutputStream(temp))) {
                out.write(root.toString().getBytes(StandardCharsets.UTF_8));
            }
            if(token.equals(JourneyStore.fileToken(source)))
                Files.move(temp.toPath(),target.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        } catch(Exception optional) { /* Original archive remains authoritative. */ }
        finally {if(temp!=null)temp.delete();}
    }
}
