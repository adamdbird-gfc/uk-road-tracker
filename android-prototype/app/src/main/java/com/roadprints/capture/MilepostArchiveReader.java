package com.roadprints.capture;

import android.content.Context;
import android.util.JsonReader;
import android.util.JsonToken;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.BooleanSupplier;
import org.json.*;

/** Read-only projection of one committed archive. No legacy migration, upgrades or
 * geometry fallback. Decoder budgets apply before JSON graph allocation. */
final class MilepostArchiveReader {
    static final int MAX_NODES=150000, MAX_TEXT=512, MAX_DEPTH=12;
    static final String REVISION="milepost-projection-2";
    static File file(Context c,String id) {
        if(id==null||id.isEmpty()||id.length()>512||id.contains("/")||id.contains("\\"))throw new IllegalArgumentException("Invalid archive identity");
        return new File(c.getFilesDir(),"journey_"+id+".json");
    }
    static String token(Context c,String id) {
        File f=file(c,id);return f.isFile()?REVISION+":"+MilepostDiscoveryLedger.EVALUATOR+":"+JourneyStore.fileToken(f):"deleted";
    }
    static final class Snapshot {
        final String stamp;
        final MilepostProximity.Evidence evidence;
        Snapshot(String stamp,MilepostProximity.Evidence evidence){this.stamp=stamp;this.evidence=evidence;}
    }
    static Snapshot read(Context c,String id,BooleanSupplier cancelled)throws Exception {
        String stamp;FileInputStream input;
        synchronized(JourneyStore.class) {
            check(cancelled);stamp=token(c,id);input=new FileInputStream(file(c,id));
        }
        JSONObject row=new JSONObject().put("journey_id",id);
        try(JsonReader r=new JsonReader(new InputStreamReader(new FilterInputStream(input) {
            int bytes;
            @Override public int read(byte[] b,int offset,int length)throws IOException {
                check(cancelled);int n=super.read(b,offset,Math.min(8192,length));
                if(n>0&&(bytes+=n)>8*1024*1024)throw new Limit();return n;
            }
            @Override public int read()throws IOException {check(cancelled);if(++bytes>8*1024*1024)throw new Limit();return super.read();}
        },StandardCharsets.UTF_8))) {
            Budget budget=new Budget(cancelled);
            r.beginObject();
            while(r.hasNext()) {
                check(cancelled);String k=r.nextName();
                if(Arrays.asList("journey_id","started_at","ended_at","mode","source",
                        "capture_decisions","capture_route_samples","route_geometry","recording_quality","capture_validation",
                        "activity_recognition_history","transport_changes","journey_corrections").contains(k))row.put(k,value(r,b,0));
                else r.skipValue(); // Matcher/road trees, raw GPS, diagnostics and Timeline envelopes are never retained.
            }
            r.endObject();
        } catch(java.util.concurrent.CancellationException cancelledRead) {throw cancelledRead;
        } catch(IOException|org.json.JSONException|IllegalStateException|IllegalArgumentException malformed) {
            // Bounded, typed pending evidence; never qualify a truncated observation array.
            row=new JSONObject().put("journey_id",id).put("source",new JSONObject().put("type","projection_limit"));
        }
        synchronized(JourneyStore.class) {
            check(cancelled);
            if(!stamp.equals(token(c,id)))throw new ConcurrentModificationException("Archive replaced during projection");
        }
        if(!id.equals(row.optString("journey_id")))row=new JSONObject().put("journey_id",id);
        return new Snapshot(stamp,MilepostJourneyEvidence.nativeArchive(row));
    }
    static void check(BooleanSupplier cancelled) {
        if(cancelled.getAsBoolean()||Thread.currentThread().isInterrupted())throw new java.util.concurrent.CancellationException();
    }
    private static final class Limit extends IOException {private static final long serialVersionUID=1L;}
    private static final class Budget {
        int nodes; final BooleanSupplier cancelled;
        Budget(BooleanSupplier c){cancelled=c;}
    }
    private static Object value(JsonReader r,Budget b,int depth)throws Exception {
        check(b.cancelled);if(++b.nodes>MAX_NODES||depth>MAX_DEPTH)throw new Limit();
        switch(r.peek()) {
            case BEGIN_OBJECT:
                JSONObject object=new JSONObject();r.beginObject();
                while(r.hasNext()){String key=r.nextName();if(key.length()>MAX_TEXT)throw new Limit();object.put(key,value(r,b,depth+1));}
                r.endObject();return object;
            case BEGIN_ARRAY:
                JSONArray array=new JSONArray();r.beginArray();
                while(r.hasNext()){if(array.length()>=MilepostJourneyEvidence.MAX_POINTS)throw new Limit();array.put(value(r,b,depth+1));}
                r.endArray();return array;
            case NUMBER:
                String n=r.nextString();if(n.length()>MAX_TEXT)throw new Limit();
                try{return Long.parseLong(n);}catch(NumberFormatException decimal){return Double.parseDouble(n);}
            case BOOLEAN:return r.nextBoolean();
            case NULL:r.nextNull();return JSONObject.NULL;
            default:String s=r.nextString();if(s.length()>MAX_TEXT)throw new Limit();return s;
        }
    }
}
