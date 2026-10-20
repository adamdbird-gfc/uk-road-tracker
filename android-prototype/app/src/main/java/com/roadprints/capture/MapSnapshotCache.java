package com.roadprints.capture;

import android.content.Context;
import android.util.AtomicFile;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/** Revision is checked before decompression; checksum covers encoded bytes without reserialisation. */
final class MapSnapshotCache {
    private static final int LIMIT=48*1024*1024,MAGIC=0x52504d32;
    private static AtomicFile file(Context c) {
        return new AtomicFile(new File(c.getFilesDir(),"screen-cache/map-routes.bin"));
    }
    static JSONObject read(Context c,long revision) {
        AtomicFile snapshot=file(c);
        if(!snapshot.getBaseFile().isFile())return legacy(c,revision);
        try(DataInputStream header=new DataInputStream(snapshot.openRead())) {
            if(header.readInt()!=MAGIC||header.readLong()!=revision)return null;
            String expected=header.readUTF();int length=header.readInt();
            if(length<0||length>LIMIT)return null;
            try(InputStream in=new GZIPInputStream(header);ByteArrayOutputStream out=new ByteArrayOutputStream(Math.min(length,1024*1024))) {
                byte[] buffer=new byte[8192];int count;
                while((count=in.read(buffer))!=-1){if(out.size()+count>length)return null;out.write(buffer,0,count);}
                byte[] payload=out.toByteArray();
                if(payload.length!=length||!NcnJson.hash(payload).equals(expected))return null;
                return new JSONObject(new String(payload,StandardCharsets.UTF_8));
            }
        } catch(Exception invalid) {return null;}
    }
    static void write(Context c,long revision,JSONObject data) {
        AtomicFile snapshot=file(c);FileOutputStream raw=null;
        try {
            byte[] payload=data.toString().getBytes(StandardCharsets.UTF_8);
            if(payload.length>LIMIT){android.util.Log.i("RoadprintsTiming","map_cache_write=over_limit");return;}
            raw=snapshot.startWrite();DataOutputStream header=new DataOutputStream(raw);
            header.writeInt(MAGIC);header.writeLong(revision);header.writeUTF(NcnJson.hash(payload));header.writeInt(payload.length);header.flush();
            GZIPOutputStream gzip=new GZIPOutputStream(raw);gzip.write(payload);gzip.finish();gzip.flush();
            snapshot.finishWrite(raw);raw=null;
        } catch(Exception optional) {if(raw!=null)snapshot.failWrite(raw);}
    }
    private static JSONObject legacy(Context c,long revision) {
        AtomicFile old=new AtomicFile(new File(c.getFilesDir(),"screen-cache/map-routes.gz"));
        try(InputStream in=new GZIPInputStream(old.openRead());ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            byte[] buffer=new byte[8192];int count;
            while((count=in.read(buffer))!=-1){if(out.size()+count>LIMIT)return null;out.write(buffer,0,count);}
            JSONObject root=new JSONObject(out.toString("UTF-8"));
            if(root.getInt("format")!=1||root.getLong("revision")!=revision)return null;
            JSONObject payload=root.getJSONObject("data");
            return NcnJson.hash(payload.toString().getBytes(StandardCharsets.UTF_8)).equals(root.getString("sha256"))?payload:null;
        } catch(Exception invalid) {return null;}
    }
}

