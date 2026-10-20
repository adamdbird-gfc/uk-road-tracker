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
    private static final int ASSESSMENT_VERSION=2;
    private static final String PREFS="roadprints_ncn_national_v2";
    private static final ExecutorService WORKER=Executors.newSingleThreadExecutor(task->{Thread t=new Thread(()->{android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);task.run();},"roadprints-ncn-assessment");return t;});
    private static boolean running;
    private static volatile int coalesced;
    private static long failedRevision=Long.MIN_VALUE;
    private static final java.util.concurrent.atomic.AtomicBoolean changeQueued=new java.util.concurrent.atomic.AtomicBoolean();
    private static volatile Snapshot snapshot;
    private static volatile String status="Not activated";
    static final class Snapshot {
        final NcnCatalogue catalogue;final NcnLedger ledger;final long archiveRevision;
        final java.util.Map<String,JSONObject> metadata;
        final int evidenceReads;
        Snapshot(NcnCatalogue c,NcnLedger l,long revision,java.util.Map<String,JSONObject> m){this(c,l,revision,m,0);}
        Snapshot(NcnCatalogue c,NcnLedger l,long revision,java.util.Map<String,JSONObject> m,int reads){catalogue=c;ledger=l;archiveRevision=revision;metadata=m;evidenceReads=reads;}
    }
    static boolean enabled(Context c){return c.getSharedPreferences(PREFS,0).getBoolean("enabled",false);}
    static long revision(Context c){return c.getSharedPreferences(PREFS,0).getLong("revision",0);}
    static String status(){return status;}
    static Snapshot current(){return snapshot;}
    static synchronized void activate(Context c,boolean value) {
        c.getSharedPreferences(PREFS,0).edit().putBoolean("enabled",value).putLong("epoch",c.getSharedPreferences(PREFS,0).getLong("epoch",0)+1).putLong("revision",revision(c)+1).commit();
        failedRevision=Long.MIN_VALUE;if(value)ensure(c);else{snapshot=null;status="Not activated";}
    }
    static void ensure(Context context) { ensure(context, "screen_or_startup"); }
    private static synchronized void ensure(Context context, String trigger) {
        if (!enabled(context)) return;
        if (running) { coalesced++; return; }
        if (failedRevision == JourneyStore.dataRevision(context)) return;
        if(snapshot!=null&&snapshot.archiveRevision==JourneyStore.dataRevision(context)){NcnCatalogueFiles.refreshIfDue(context);return;}
        NcnCatalogueFiles.refreshIfDue(context);
        Context app=context.getApplicationContext();running=true;coalesced=0;status="Assessing saved walking and cycling journeys…";
        long epoch=app.getSharedPreferences(PREFS,0).getLong("epoch",0);
        WORKER.execute(()->{
            boolean retry=false;
            try {
                long started=android.os.SystemClock.elapsedRealtime();
                java.util.function.BooleanSupplier cancelled=()->!enabled(app)||app.getSharedPreferences(PREFS,0).getLong("epoch",0)!=epoch;
                Snapshot result=restore(app);
                boolean restored=result!=null;
                if(result==null)result=assess(app,cancelled);
                final Snapshot completed=result;
                PerformanceDiagnostics.timing("ncn_assessment",android.os.SystemClock.elapsedRealtime()-started,"journeys="+result.metadata.size()+" restored="+restored+" trigger="+trigger+" coalesced="+coalesced);
                long awardStarted=android.os.SystemClock.elapsedRealtime();
                org.json.JSONObject awards=NcnAchievements.prepare(result,NcnAchievements.state(app));
                PerformanceDiagnostics.timing("ncn_achievements",android.os.SystemClock.elapsedRealtime()-awardStarted,"");
                // Complete optional atomic persistence before allowing a newer request to overtake it.
                if (!restored && !cancelled.getAsBoolean() && result.archiveRevision == JourneyStore.dataRevision(app))
                    try { checkpoint(app, completed); } catch (Exception cacheFailure) {
                        android.util.Log.w("RoadprintsTiming", "ncn_snapshot_write=skipped");
                    }
                synchronized(NcnProgressStore.class) {
                if(enabled(app)&&app.getSharedPreferences(PREFS,0).getLong("epoch",0)==epoch&&result.archiveRevision==JourneyStore.dataRevision(app)) {
                    boolean awardsChanged=NcnAchievements.publish(app,awards);
                    snapshot=result;status="Ready · verified-open NCN sections";
                    if(!restored||awardsChanged)app.getSharedPreferences(PREFS,0).edit().putLong("revision",revision(app)+1).apply();
                }else retry=enabled(app);
                }
            }catch(Exception failure){synchronized(NcnProgressStore.class){if(app.getSharedPreferences(PREFS,0).getLong("epoch",0)!=epoch||failure instanceof java.util.concurrent.CancellationException)retry=enabled(app);else{failedRevision=JourneyStore.dataRevision(app);android.util.Log.w("RoadprintsTiming","NCN assessment failed: "+failure.getClass().getSimpleName());status="NCN catalogue or assessment could not finish. Tap Retry to resume.";}}}
            finally{synchronized(NcnProgressStore.class){running=false;}if(retry)ensure(app);}
        });
    }
    static void catalogueStatus(String value){if(snapshot==null)status=value;}
    static synchronized void catalogueChanged(Context c){
        c.getSharedPreferences(PREFS,0).edit().putLong("epoch",c.getSharedPreferences(PREFS,0).getLong("epoch",0)+1).commit();
        snapshot=null;failedRevision=Long.MIN_VALUE;ensure(c);
    }
    static synchronized void retry(Context c){failedRevision=Long.MIN_VALUE;ensure(c);}
    static void changed(Context context) {
        if(!enabled(context)||!changeQueued.compareAndSet(false,true))return;
        Context app=context.getApplicationContext();WORKER.execute(()->{changeQueued.set(false);ensure(app,"archive_changed");});
    }
    static Snapshot restore(Context app) {
        try {
            NcnCatalogue catalogue=NcnCatalogue.load(app);
            JSONObject root=new JSONObject(new String(NcnCatalogue.read(new AtomicFile(new File(app.getFilesDir(),"ncn-derived-snapshot.json")).openRead()),StandardCharsets.UTF_8));
            long revision=JourneyStore.dataRevision(app);
            if(root.getInt("assessment_version")!=ASSESSMENT_VERSION||!root.getString("catalogue").equals(catalogue.version)||root.getLong("revision")!=revision)return null;
            JSONObject payload=root.getJSONObject("payload");
            if(!NcnJson.hash(payload.toString().getBytes(StandardCharsets.UTF_8)).equals(root.getString("sha256")))return null;
            NcnLedger ledger=new NcnLedger(catalogue);java.util.Map<String,JSONObject> metadata=new java.util.LinkedHashMap<>();
            JSONArray rows=payload.getJSONArray("rows");
            for(int i=0;i<rows.length();i++){JSONObject row=rows.getJSONObject(i);String id=row.getString("id");ledger.put(id,row.getJSONObject("assessment"));metadata.put(id,row.getJSONObject("metadata"));}
            ledger.networkMetres("combined");
            return new Snapshot(catalogue,ledger,revision,metadata);
        }catch(Exception unavailable){return null;}
    }
    static void checkpoint(Context app,Snapshot value)throws Exception{
        JSONArray rows=new JSONArray();for(String id:value.ledger.activities.keySet())rows.put(new JSONObject().put("id",id).put("assessment",value.ledger.activities.get(id)).put("metadata",value.metadata.get(id)));
        JSONObject payload=new JSONObject().put("rows",rows);byte[] bytes=payload.toString().getBytes(StandardCharsets.UTF_8);
        if(bytes.length>32_000_000)return; // Optional acceleration only; retain the complete in-memory ledger.
        write(new AtomicFile(new File(app.getFilesDir(),"ncn-derived-snapshot.json")),new JSONObject().put("assessment_version",ASSESSMENT_VERSION).put("catalogue",value.catalogue.version).put("revision",value.archiveRevision).put("payload",payload).put("sha256",NcnJson.hash(bytes)));
    }
    private static Snapshot assess(Context app) throws Exception {return assess(app,()->!enabled(app));}
    private static Snapshot assess(Context app,java.util.function.BooleanSupplier cancelled) throws Exception {
        NcnCatalogue catalogue=NcnCatalogue.load(app);long revision=JourneyStore.dataRevision(app);
        NcnMatcher matcher=new NcnMatcher(catalogue);NcnLedger ledger=new NcnLedger(catalogue);
        java.util.Map<String,JSONObject> metadata=new java.util.LinkedHashMap<>();
        File directory=new File(app.getFilesDir(),"ncn-national-assessments/"+catalogue.version);if(!directory.isDirectory()&&!directory.mkdirs())throw new java.io.IOException("Cannot create assessment cache");
        Set<String> live=new HashSet<>();
        int checked=0, decoded=0, reused=0, matched=0;
        long scanStarted=android.os.SystemClock.elapsedRealtime();
        // File metadata, not archive-wide revision, decides which evidence must be decoded.
        for (File source : JourneyStore.archiveFiles(app)) {
            if(cancelled.getAsBoolean() || Thread.currentThread().isInterrupted())
                throw new java.util.concurrent.CancellationException();
            String token=JourneyStore.fileToken(source);
            String id=source.getName().substring("journey_".length(),source.getName().length()-".json".length());
            String filename=NcnJson.hash(id.getBytes(StandardCharsets.UTF_8))+".json";
            live.add(filename);
            AtomicFile file=new AtomicFile(new File(directory,filename));
            JSONObject cached=null, assessment=null, rowMetadata=null;
            try {
                cached=new JSONObject(new String(NcnCatalogue.read(file.openRead()),StandardCharsets.UTF_8));
                if(cached.optInt("assessment_version")==ASSESSMENT_VERSION
                        && catalogue.version.equals(cached.optString("catalogue"))
                        && token.equals(cached.optString("source_token"))
                        && NcnJson.hash(cached.getJSONObject("assessment").toString().getBytes(StandardCharsets.UTF_8)).equals(cached.optString("assessment_sha256"))
                        && NcnJson.hash(cached.getJSONObject("metadata").toString().getBytes(StandardCharsets.UTF_8)).equals(cached.optString("metadata_sha256"))) {
                    assessment=cached.getJSONObject("assessment");
                    rowMetadata=cached.getJSONObject("metadata");
                    reused++;
                }
            } catch(Exception absent) { /* Missing, legacy or corrupt derived row: read this source only. */ }
            if(assessment==null) {
                JSONObject journey=JourneyStore.ncnEvidence(source);decoded++;
                if (!id.equals(journey.optString("journey_id"))) throw new java.io.IOException("Journey identity mismatch");
                String fingerprint=NcnJson.hash(journey.toString().getBytes(StandardCharsets.UTF_8));
                // Upgrade legacy cache rows without rerunning the matcher when evidence is identical.
                try {
                    if(cached!=null && cached.optInt("assessment_version")==ASSESSMENT_VERSION
                            && catalogue.version.equals(cached.optString("catalogue"))
                            && fingerprint.equals(cached.optString("fingerprint"))
                            && NcnJson.hash(cached.getJSONObject("assessment").toString().getBytes(StandardCharsets.UTF_8)).equals(cached.optString("assessment_sha256")))
                        assessment=cached.getJSONObject("assessment");
                } catch(Exception invalid) { assessment=null; }
                if(assessment==null){assessment=matcher.assess(journey);matched++;}
                rowMetadata=new RoadDetailModel.Entry(journey).summary;
                // A save racing with this read must never label older evidence with a newer token.
                if(!token.equals(JourneyStore.fileToken(source)) || revision!=JourneyStore.dataRevision(app))
                    throw new java.util.concurrent.CancellationException();
                write(file,new JSONObject().put("assessment_version",ASSESSMENT_VERSION).put("catalogue",catalogue.version)
                        .put("source_token",token).put("fingerprint",fingerprint).put("assessment",assessment)
                        .put("metadata",rowMetadata)
                        .put("assessment_sha256",NcnJson.hash(assessment.toString().getBytes(StandardCharsets.UTF_8)))
                        .put("metadata_sha256",NcnJson.hash(rowMetadata.toString().getBytes(StandardCharsets.UTF_8))));
            }
            ledger.put(id,assessment);metadata.put(id,rowMetadata);
            if(++checked%25==0)status="Assessing NCN history · "+checked+" journeys checked";
        }
        if(cancelled.getAsBoolean())throw new java.util.concurrent.CancellationException();
        PerformanceDiagnostics.timing("ncn_evidence",android.os.SystemClock.elapsedRealtime()-scanStarted,
                "type="+(reused>0?"incremental":"full")+" checked="+checked+" decoded="+decoded+" reused="+reused+" matched="+matched+" invalidation=source_or_rules");
        // Deleted journeys never enter the fresh ledger.
        if(revision==JourneyStore.dataRevision(app)) {File[] files=directory.listFiles();if(files!=null)for(File file:files)if(file.getName().endsWith(".json")&&!live.contains(file.getName()))new AtomicFile(file).delete();}
        // Build interval unions on this worker before a UI ever receives the snapshot.
        for(String route:catalogue.routes.keySet()){if(cancelled.getAsBoolean())throw new java.util.concurrent.CancellationException();ledger.metres(route,"combined");}
        return new Snapshot(catalogue,ledger,revision,metadata,decoded);
    }
    private static void write(AtomicFile file,JSONObject value) throws Exception {
        FileOutputStream out=null;try{out=file.startWrite();out.write(value.toString().getBytes(StandardCharsets.UTF_8));file.finishWrite(out);}
        catch(Exception failure){if(out!=null)file.failWrite(out);throw failure;}
    }
    static String mode(Context c){return c.getSharedPreferences(PREFS,0).getString("activity","combined");}
    static void mode(Context c,String value){if(value.equals("combined")||value.equals("cycling")||value.equals("on_foot"))c.getSharedPreferences(PREFS,0).edit().putString("activity",value).apply();}
    static JSONArray targetSlice(JSONArray c,double a,double z)throws Exception{JSONArray first=c.getJSONArray(0),last=c.getJSONArray(c.length()-1);return new JSONArray().put(new JSONArray().put(first.getDouble(0)+(last.getDouble(0)-first.getDouble(0))*a).put(first.getDouble(1)+(last.getDouble(1)-first.getDouble(1))*a)).put(new JSONArray().put(first.getDouble(0)+(last.getDouble(0)-first.getDouble(0))*z).put(first.getDouble(1)+(last.getDouble(1)-first.getDouble(1))*z));}
    static RoadDetailModel detail(Context context,String route,String mode) throws Exception {return detail(context,route,mode,null);}
    static RoadDetailModel detail(Context context,String route,String mode,java.util.function.Consumer<RoadDetailModel> geometryReady) throws Exception {
        if(!enabled(context))throw new IllegalStateException("Enable the NCN collection first");
        ensure(context);Snapshot s=snapshot;
        if(s==null||s.archiveRevision!=JourneyStore.dataRevision(context))throw new IllegalStateException("NCN assessment still running");
        if(!s.catalogue.routes.containsKey(route))throw new IllegalArgumentException("Unknown NCN route");
        RoadDetailModel result=new RoadDetailModel();result.ncn=true;result.fullReference=s.catalogue.routes.get(route).optBoolean("measurable",true);
        result.referenceMetres=s.catalogue.routes.get(route).getDouble("length_m");result.uniqueMetres=s.ledger.metres(route,mode);result.percent=result.fullReference?100*result.uniqueMetres/result.referenceMetres:Double.NaN;
        s.catalogue.targets(route,(key,length,coords)->{
            if(length<=0){result.remaining.add(coords);return;}double cursor=0;
            for(double[] span:s.ledger.covered(route,key,mode)){if(span[0]>cursor)result.remaining.add(targetSlice(coords,cursor/length,span[0]/length));cursor=Math.max(cursor,span[1]);}
            if(cursor<length)result.remaining.add(targetSlice(coords,cursor/length,1));
        });
        java.util.List<JSONArray> joined=NcnGeometry.join(result.remaining);result.remaining.clear();result.remaining.addAll(joined);
        if(!result.fullReference){result.uniqueMetres=0;if(geometryReady!=null)geometryReady.accept(result.geometryPreview());return result;}
        java.util.Set<String> activeWays=new java.util.HashSet<>();for(JSONObject activity:s.ledger.activities.values()){JSONArray spans=activity.getJSONArray("spans");for(int i=0;i<spans.length();i++){JSONArray span=spans.getJSONArray(i);if(span.getString(0).equals(route))activeWays.add(span.getString(1));}}
        java.util.List<NcnCatalogue.Path> paths=new java.util.ArrayList<>();for(String way:activeWays)for(NcnCatalogue.Path p:s.catalogue.way(way))if(p.route.equals(route))paths.add(p);
        for(NcnCatalogue.Path path:paths)for(double[] span:s.ledger.source(path,mode,null))result.covered.add(NcnLedger.slice(path,span[0],span[1]));
        if(geometryReady!=null)geometryReady.accept(result.geometryPreview());
        for(String id:s.ledger.activities.keySet()) {
            RoadDetailModel.Entry entry=new RoadDetailModel.Entry(s.metadata.get(id));
            JSONObject activity=s.ledger.activities.get(id);JSONArray spans=activity.getJSONArray("spans");java.util.Set<String> ways=new java.util.HashSet<>();
            for(int i=0;i<spans.length();i++){JSONArray span=spans.getJSONArray(i);if(span.getString(0).equals(route))ways.add(span.getString(1));}
            for(String way:ways)for(NcnCatalogue.Path path:s.catalogue.way(way))if(path.route.equals(route))for(double[] span:s.ledger.source(path,mode,activity))entry.lines.add(NcnLedger.slice(path,span[0],span[1]));
            if(!entry.lines.isEmpty())result.journeys.add(entry);
        }
        result.journeys.sort((a,b)->b.date.compareTo(a.date));return result;
    }
}
