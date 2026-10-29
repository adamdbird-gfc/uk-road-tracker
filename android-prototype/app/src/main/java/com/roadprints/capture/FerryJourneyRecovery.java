package com.roadprints.capture;

import android.content.Context;
import android.util.AtomicFile;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.json.*;

/** Explicit owner-reviewed correction. Durable journal is replayed under JourneyStore's lock
 * before any archive operation; original evidence is retained outside the journey namespace. */
final class FerryJourneyRecovery {
    private static boolean replaying;
    private static File journal(Context c){return new File(c.getFilesDir(),"ferry_recovery_pending.json");}
    private static File original(Context c,String id){return new File(c.getFilesDir(),"ferry_original_"+id+".json");}
    static void write(File file,JSONObject value) throws Exception {
        AtomicFile atomic=new AtomicFile(file);FileOutputStream out=null;
        try {out=atomic.startWrite();out.write(value.toString().getBytes(StandardCharsets.UTF_8));atomic.finishWrite(out);}
        catch(Exception e){if(out!=null)atomic.failWrite(out);throw e;}
    }
    static JSONObject read(File file) throws Exception {
        try(InputStream in=new AtomicFile(file).openRead()) {
            ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int n;
            while((n=in.read(buffer))!=-1)out.write(buffer,0,n);
            return new JSONObject(out.toString("UTF-8"));
        }
    }
    static void resume(Context c) {
        if(replaying||!(journal(c).isFile()||new File(journal(c).getPath()+".bak").isFile()))return;
        replaying=true;
        try {
            JSONObject job=read(journal(c));JSONArray records=job.getJSONArray("records");
            for(int i=0;i<records.length();i++)JourneyStore.save(c,records.getJSONObject(i));
            JSONArray remove=job.optJSONArray("remove");
            if(remove!=null)for(int i=0;i<remove.length();i++)JourneyStore.delete(c,remove.getString(i));
            new AtomicFile(journal(c)).delete();
        } catch(Exception e){throw new IllegalStateException("Journey correction needs recovery; original evidence retained",e);}
        finally {replaying=false;}
    }
    static int split(Context c,String id,int expectedRevision) throws Exception {
        synchronized(JourneyStore.class) {
            JSONObject source=JourneyStore.get(c,id);
            if(source==null)throw new IllegalStateException("Journey unavailable");
            if(source.has("ferry_recovery"))return source.getJSONObject("ferry_recovery").getJSONArray("ids").length();
            if(source.optInt("revision")!=expectedRevision)throw new IllegalStateException("Journey changed; review it again");
            if(source.has("journey_corrections"))throw new IllegalStateException("Restore route edits before splitting this recording");
            JSONArray samples=source.optJSONArray("capture_route_samples");
            FerryJourneyEvidence.Review review=FerryJourneyEvidence.inspect(samples);
            if(!review.canSplit())throw new IllegalStateException(review.reason);
            int[] boundaries={0,review.start,review.end,samples.length()-1};
            JSONArray ids=new JSONArray(),records=new JSONArray();
            int count=0;
            for(int i=0;i<3;i++)if(boundaries[i+1]>boundaries[i])ids.put(count++==0?id:id+"-ferry-v1-"+i);
            int record=0;
            for(int part=0;part<3;part++) {
                int from=boundaries[part],to=boundaries[part+1];if(to<=from)continue;
                JSONObject child=new JSONObject(source.toString());String childId=ids.getString(record++);
                if(!childId.equals(id)&&JourneyStore.get(c,childId)!=null)throw new IllegalStateException("Correction identity already exists; review required");
                for(String key:new String[]{"processing_result","last_match_attempt","error_summary","processing_completed_at","walking_outcome","original_distance_meters","road_preparation","arrival_dwell_samples","stops","places","context_evidence"})child.remove(key);
                String mode=part==1?"ferry":MatchingCoordinator.isRoad(source.optString("mode"))?source.optString("mode"):"unknown";
                JSONArray segment=new JSONArray(),coordinates=new JSONArray(),raw=new JSONArray(),rawGeometry=new JSONArray();
                for(int i=from;i<=to;i++){JSONArray p=samples.getJSONArray(i);segment.put(p);coordinates.put(new JSONArray().put(p.getDouble(0)).put(p.getDouble(1)));}
                long start=segment.getJSONArray(0).getLong(3),end=segment.getJSONArray(segment.length()-1).getLong(3);
                JSONArray originals=source.optJSONArray("raw_capture_samples");
                if(originals!=null)for(int i=0;i<originals.length();i++){JSONArray p=originals.optJSONArray(i);if(p!=null&&p.optLong(3)>=start&&p.optLong(3)<=end){raw.put(p);rawGeometry.put(new JSONArray().put(p.getDouble(0)).put(p.getDouble(1)));}}
                JSONObject stages=new JSONObject().put("import","complete").put("road_matching",MatchingCoordinator.isRoad(mode)?"pending":"not_required")
                        .put("foot_matching","not_required").put("cycle_matching","not_required");
                child.put("journey_id",childId).put("revision",source.optInt("revision",1)+1).put("mode",mode)
                        .put("transport_confirmation","required").put("started_at",Instant.ofEpochMilli(start).toString()).put("ended_at",Instant.ofEpochMilli(end).toString())
                        .put("title",part==1?"Ferry crossing":"Road section")
                        .put("capture_route_samples",segment).put("raw_capture_samples",raw)
                        .put("raw_capture_geometry",new JSONObject().put("type","LineString").put("coordinates",rawGeometry))
                        .put("route_geometry",new JSONObject().put("type","LineString").put("coordinates",coordinates))
                        .put("distance_meters",MatchingCoordinator.traceDistance(segment)).put("gps_point_count",segment.length())
                        .put("capture_quality",new JSONObject().put("gps_points",segment.length()).put("status","usable"))
                        .put("recording_quality",CaptureQualityValidator.inspect(segment,false))
                        .put("processing_status",MatchingCoordinator.isRoad(mode)?"pending":"complete").put("processing",stages).put("stage_statuses",new JSONObject(stages.toString()))
                        .put("ferry_recovery",new JSONObject().put("source_id",id).put("backup_key",id+"-r"+source.optInt("revision",1)).put("ids",ids).put("boundaries",review.json()).put("reviewed",true));
                records.put(child);
            }
            // Backup before journal; neither can enter normal history or map evidence.
            String backupKey=id+"-r"+source.optInt("revision",1);
            if(!original(c,backupKey).exists())write(original(c,backupKey),source);
            write(journal(c),new JSONObject().put("records",records));resume(c);return records.length();
        }
    }
    static void restore(Context c,String id) throws Exception {
        synchronized(JourneyStore.class) {
            JSONObject current=JourneyStore.get(c,id);if(current==null||!current.has("ferry_recovery"))return;
            JSONObject recovery=current.getJSONObject("ferry_recovery");String root=recovery.getString("source_id");
            JSONObject source=read(original(c,recovery.getString("backup_key")));source.put("revision",Math.max(source.optInt("revision"),current.optInt("revision"))+1);
            JSONArray remove=new JSONArray(),ids=recovery.getJSONArray("ids");
            for(int i=0;i<ids.length();i++)if(!root.equals(ids.getString(i)))remove.put(ids.getString(i));
            write(journal(c),new JSONObject().put("records",new JSONArray().put(source)).put("remove",remove));resume(c);
        }
    }
}
