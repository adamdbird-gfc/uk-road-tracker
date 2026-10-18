package com.roadprints.capture;

import android.content.Context;
import android.util.AtomicFile;
import org.json.JSONObject;
import org.json.JSONArray;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Explicit activation, resumable local assessments and stable-revision publication. */
final class NcnProgressStore {
    private static final int ASSESSMENT_VERSION=1;
    private static final String PREFS="roadprints_ncn_internal_trial_v1";
    private static final ExecutorService WORKER=Executors.newSingleThreadExecutor();
    private static boolean running;
    private static final java.util.concurrent.atomic.AtomicBoolean changeQueued=new java.util.concurrent.atomic.AtomicBoolean();
    private static volatile Snapshot snapshot;
    private static volatile String status="Not activated";
    static final class Snapshot {
        final NcnCatalogue catalogue;final NcnLedger ledger;final long archiveRevision;
        final java.util.Map<String,JSONObject> metadata;
        Snapshot(NcnCatalogue c,NcnLedger l,long revision,java.util.Map<String,JSONObject> m){catalogue=c;ledger=l;archiveRevision=revision;metadata=m;}
    }
    static boolean enabled(Context c){return c.getSharedPreferences(PREFS,0).getBoolean("enabled",false);}
    static long revision(Context c){return c.getSharedPreferences(PREFS,0).getLong("revision",0);}
    static String status(){return status;}
    static Snapshot current(){return snapshot;}
    static synchronized void activate(Context c,boolean value) {
        c.getSharedPreferences(PREFS,0).edit().putBoolean("enabled",value).putLong("epoch",c.getSharedPreferences(PREFS,0).getLong("epoch",0)+1).putLong("revision",revision(c)+1).commit();
        if(value)ensure(c);else{snapshot=null;status="Not activated";}
    }
    static synchronized void ensure(Context context) {
        if(!enabled(context)||running)return;
        if(snapshot!=null&&snapshot.archiveRevision==JourneyStore.dataRevision(context))return;
        Context app=context.getApplicationContext();running=true;status="Assessing saved walking and cycling journeys…";
        long epoch=app.getSharedPreferences(PREFS,0).getLong("epoch",0);
        WORKER.execute(()->{
            boolean retry=false;
            try {
                Snapshot result=assess(app);
                synchronized(NcnProgressStore.class) {
                if(enabled(app)&&app.getSharedPreferences(PREFS,0).getLong("epoch",0)==epoch&&result.archiveRevision==JourneyStore.dataRevision(app)) {
                    snapshot=result;status="Ready · internal eight-inventory trial";
                    app.getSharedPreferences(PREFS,0).edit().putLong("revision",revision(app)+1).commit();
                }else retry=enabled(app);
                }
            }catch(Exception failure){status="NCN assessment could not finish. Open the trial to retry.";}
            finally{synchronized(NcnProgressStore.class){running=false;}if(retry)ensure(app);}
        });
    }
    static void changed(Context context) {
        if(!enabled(context)||!changeQueued.compareAndSet(false,true))return;
        Context app=context.getApplicationContext();WORKER.execute(()->{changeQueued.set(false);ensure(app);});
    }
    private static Snapshot assess(Context app) throws Exception {
        NcnCatalogue catalogue=NcnCatalogue.load(app);long revision=JourneyStore.dataRevision(app);
        NcnMatcher matcher=new NcnMatcher(catalogue);NcnLedger ledger=new NcnLedger(catalogue);
        java.util.Map<String,JSONObject> metadata=new java.util.LinkedHashMap<>();
        File directory=new File(app.getFilesDir(),"ncn-trial/"+catalogue.version);if(!directory.isDirectory()&&!directory.mkdirs())throw new java.io.IOException("Cannot create assessment cache");
        Set<String> live=new HashSet<>();int[] checked={0};Exception[] failed={null};
        JourneyStore.forEach(app,journey->{
            if(failed[0]!=null||!enabled(app))return;
            try {
                String id=journey.optString("journey_id");if(id.isEmpty())return;
                String fingerprint=NcnJson.hash(journey.toString().getBytes(StandardCharsets.UTF_8));
                String filename=NcnJson.hash(id.getBytes(StandardCharsets.UTF_8))+".json";live.add(filename);AtomicFile file=new AtomicFile(new File(directory,filename));JSONObject assessment=null;
                try {JSONObject cached=new JSONObject(new String(NcnCatalogue.read(file.openRead()),StandardCharsets.UTF_8));if(cached.optInt("assessment_version")==ASSESSMENT_VERSION&&catalogue.version.equals(cached.optString("catalogue"))&&fingerprint.equals(cached.optString("fingerprint"))&&NcnJson.hash(cached.getJSONObject("assessment").toString().getBytes(StandardCharsets.UTF_8)).equals(cached.optString("assessment_sha256")))assessment=cached.getJSONObject("assessment");}catch(Exception absent){/* Fresh or interrupted checkpoint: reassess this journey only. */}
                if(assessment==null){assessment=matcher.assess(journey);write(file,new JSONObject().put("assessment_version",ASSESSMENT_VERSION).put("catalogue",catalogue.version).put("fingerprint",fingerprint).put("assessment",assessment).put("assessment_sha256",NcnJson.hash(assessment.toString().getBytes(StandardCharsets.UTF_8))));}
                ledger.put(id,assessment);metadata.put(id,new JSONObject().put("journey_id",id).put("title",journey.optString("title",journey.optString("name","Journey"))).put("started_at",journey.optString("started_at")));
                if(++checked[0]%25==0)status="Assessing NCN history · "+checked[0]+" journeys checked";
            }catch(Exception error){failed[0]=error;}
        });
        if(failed[0]!=null)throw failed[0];
        // Deleted journeys are absent from the newly assembled ledger; stale cache rows cannot contribute.
        if(revision==JourneyStore.dataRevision(app)) {File[] files=directory.listFiles();if(files!=null)for(File file:files)if(file.getName().endsWith(".json")&&!live.contains(file.getName()))new AtomicFile(file).delete();}
        // Build interval unions on this worker before a UI ever receives the snapshot.
        for(String route:catalogue.routes.keySet())ledger.metres(route,"combined");
        return new Snapshot(catalogue,ledger,revision,metadata);
    }
    private static void write(AtomicFile file,JSONObject value) throws Exception {
        FileOutputStream out=null;try{out=file.startWrite();out.write(value.toString().getBytes(StandardCharsets.UTF_8));file.finishWrite(out);}
        catch(Exception failure){if(out!=null)file.failWrite(out);throw failure;}
    }
    static RoadDetailModel detail(Context context,String route,String mode) throws Exception {
        if(!enabled(context))throw new IllegalStateException("Activate the NCN internal trial first");
        ensure(context);Snapshot s=snapshot;
        if(s==null||s.archiveRevision!=JourneyStore.dataRevision(context))throw new IllegalStateException("NCN assessment still running");
        if(!s.catalogue.routes.containsKey(route))throw new IllegalArgumentException("Unknown trial route");
        RoadDetailModel result=new RoadDetailModel();result.ncn=true;result.fullReference=true;
        result.referenceMetres=s.catalogue.routes.get(route).getDouble("length_m");result.uniqueMetres=s.ledger.metres(route,mode);result.percent=100*result.uniqueMetres/result.referenceMetres;
        for(NcnCatalogue.Path path:s.catalogue.paths)if(path.route.equals(route)) {
            java.util.List<double[]> covered=s.ledger.source(path,mode,null);double cursor=0;
            for(double[] span:covered){if(span[0]>cursor)result.remaining.add(NcnLedger.slice(path,cursor,span[0]));result.covered.add(NcnLedger.slice(path,span[0],span[1]));cursor=Math.max(cursor,span[1]);}
            double end=path.positions[path.positions.length-1];if(cursor<end)result.remaining.add(NcnLedger.slice(path,cursor,end));
        }
        for(String id:s.ledger.activities.keySet()) {
            RoadDetailModel.Entry entry=new RoadDetailModel.Entry(s.metadata.get(id));
            for(NcnCatalogue.Path path:s.catalogue.paths)if(path.route.equals(route))for(double[] span:s.ledger.source(path,mode,s.ledger.activities.get(id)))entry.lines.add(NcnLedger.slice(path,span[0],span[1]));
            if(!entry.lines.isEmpty())result.journeys.add(entry);
        }
        result.journeys.sort((a,b)->b.date.compareTo(a.date));return result;
    }
}
