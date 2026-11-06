package com.roadprints.capture;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28,manifest=Config.NONE)
public class MilepostReconcilerTest {
    Context c;String name;MilepostDiscoveryLedger ledger;MilepostReconciler worker;
    void work(MilepostDiscoveryLedgerTest.Work work)throws Exception {
        ExecutorService e=Executors.newSingleThreadExecutor();
        try{e.submit(()->{try{work.run();}catch(Exception ex){throw new RuntimeException(ex);}}).get();}
        catch(ExecutionException ex){Throwable cause=ex.getCause();if(cause instanceof RuntimeException&&cause.getCause()!=null)cause=cause.getCause();
            if(cause instanceof Exception)throw (Exception)cause;if(cause instanceof Error)throw (Error)cause;throw ex;}
        finally{e.shutdownNow();}
    }
    @Before public void setup()throws Exception {
        c=RuntimeEnvironment.getApplication();name="reconcile-"+UUID.randomUUID()+".db";
        JourneyStore.deleteAll(c);
        work(()->{ledger=new MilepostDiscoveryLedger(c,name);worker=new MilepostReconciler(c,ledger,"u");});
    }
    @After public void cleanup()throws Exception {work(()->ledger.close());c.deleteDatabase(name);JourneyStore.deleteAll(c);}
    JSONObject row(String id)throws Exception {return MilepostJourneyEvidenceTest.journey("walking").put("journey_id",id);}
    void save(JSONObject j){JourneyStore.save(c,j);}
    void catalogue() {ledger.install(1,MilepostDiscoveryLedgerTest.HASH,Collections.singletonList(MilepostDiscoveryLedgerTest.marker("p",50,true)));}
    void drain()throws Exception {int slices=0;while(worker.runSlice(()->false,1000)){if(++slices>3000)fail("Worker failed to converge");}}
    List<MilepostDiscoveryLedger.Discovery> found(){return ledger.discoveries("u","",16);}
    @Test public void realSaveCorrectionDeletionRestoreAndUnchangedRetry()throws Exception {work(()->{
        catalogue();JSONObject a=row("a"),b=row("b");save(a);save(b);long revision=JourneyStore.dataRevision(c),ncn=JourneyStore.ncnDataRevision(c);
        String original=new String(Files.readAllBytes(MilepostArchiveReader.file(c,"a").toPath()),StandardCharsets.UTF_8);drain();assertEquals(1,found().size());assertEquals(2,ledger.proofCount("u"));
        int reads=worker.readSources;drain();assertEquals(reads,worker.readSources);assertEquals(2,ledger.proofCount("u"));
        assertEquals(original,new String(Files.readAllBytes(MilepostArchiveReader.file(c,"a").toPath()),StandardCharsets.UTF_8));
        assertEquals(revision,JourneyStore.dataRevision(c));assertEquals(ncn,JourneyStore.ncnDataRevision(c));
        JourneyStore.delete(c,"a");drain();assertEquals("b",found().get(0).journey);
        b.put("journey_corrections",new JSONObject().put("removed_matched_segments",new JSONArray().put(0)));save(b);drain();assertTrue(found().isEmpty());
        assertEquals(MilepostDiscoveryLedger.State.PENDING_EVIDENCE,ledger.state("u","b"));
        b.remove("journey_corrections");save(b);drain();assertEquals(1,found().get(0).ordinal);
        JourneyStore.delete(c,"b");drain();assertTrue(found().isEmpty());assertEquals(MilepostDiscoveryLedger.State.DELETED,ledger.state("u","b"));
        save(a);drain();assertEquals(1,found().get(0).ordinal);assertEquals(1000,found().get(0).firstRecognised);
    });}
    @Test public void validationStatusAndHistoricalPendingAreRetryable()throws Exception {work(()->{
        catalogue();JSONObject a=row("a");a.put("processing_status","failed");save(a);drain();assertTrue(found().isEmpty());
        assertEquals(MilepostDiscoveryLedger.State.PENDING_EVIDENCE,ledger.state("u","a"));
        a.put("processing_status","complete");save(a);drain();assertEquals(1,found().size());
        JSONObject old=row("old");old.getJSONObject("source").put("type","timeline_import");save(old);drain();
        assertEquals(MilepostDiscoveryLedger.State.PENDING_EVIDENCE,ledger.state("u","old"));assertEquals(1,found().size());
    });}
    @Test public void projectedAdapterAgreesWithFullReaderAndSkipsLargeRoadTrees()throws Exception {work(()->{
        for(String mode:new String[]{"walking","running","cycling"}) {
            JSONObject row=MilepostJourneyEvidenceTest.journey(mode).put("journey_id",mode);
            row.getJSONObject("processing_result").put("road_geojson",new JSONObject().put("ignored",new JSONArray(Collections.nCopies(20000,new JSONArray().put(0).put(1)))));
            save(row);MilepostProximity.Evidence full=MilepostJourneyEvidence.nativeArchive(row);
            MilepostProximity.Evidence projected=MilepostArchiveReader.read(c,mode,()->false).evidence;
            assertEquals(full.problem(),projected.problem());
            assertEquals(MilepostProximityTest.assess(MilepostProximityTest.marker(50),full).outcome,
                    MilepostProximityTest.assess(MilepostProximityTest.marker(50),projected).outcome);
        }
    });}
    @Test public void oversizedAndMalformedSourcesStayPendingWithoutBlockingLaterJourney()throws Exception {work(()->{
        catalogue();JSONObject big=row("a");big.put("capture_route_samples",new JSONArray(Collections.nCopies(10001,new JSONArray().put(1))));
        Files.write(MilepostArchiveReader.file(c,"a").toPath(),big.toString().getBytes(StandardCharsets.UTF_8));
        Files.write(MilepostArchiveReader.file(c,"b").toPath(),"{broken".getBytes(StandardCharsets.UTF_8));save(row("z"));drain();
        assertEquals(MilepostDiscoveryLedger.State.PENDING_EVIDENCE,ledger.state("u","a"));
        assertEquals(MilepostDiscoveryLedger.State.PENDING_EVIDENCE,ledger.state("u","b"));assertEquals("z",found().get(0).journey);
    });}
    @Test public void deletionAllAndAtomicSameSizeReplacementAreDetected()throws Exception {work(()->{
        catalogue();JSONObject a=row("a");save(a);drain();String before=ledger.archiveStamp("u","a");
        a.put("mode","driving");save(a);drain();assertNotEquals(before,ledger.archiveStamp("u","a"));assertTrue(found().isEmpty());
        save(row("a"));save(row("b"));drain();JourneyStore.deleteAll(c);drain();assertTrue(found().isEmpty());
        assertEquals(MilepostDiscoveryLedger.State.DELETED,ledger.state("u","a"));assertEquals(MilepostDiscoveryLedger.State.DELETED,ledger.state("u","b"));
    });}
    @Test public void restartResumesArchiveCursorAndPartialMarkerPages()throws Exception {work(()->{
        List<MilepostDiscoveryLedger.Marker> markers=new ArrayList<>();for(int i=0;i<70;i++)markers.add(MilepostDiscoveryLedgerTest.marker(String.format("p%03d",i),50,true));
        ledger.install(1,MilepostDiscoveryLedgerTest.HASH,markers);
        for(int i=0;i<35;i++)save(row(String.format("j%03d",i)));
        assertTrue(worker.runSlice(()->false,1000));assertEquals("j031",ledger.scanCursor("u","live"));
        assertEquals(MilepostDiscoveryLedger.State.QUEUED,ledger.state("u","j000"));assertTrue(found().isEmpty());
        ledger.close();ledger=new MilepostDiscoveryLedger(c,name);worker=new MilepostReconciler(c,ledger,"u");drain();
        assertEquals(35*70,ledger.proofCount("u"));assertEquals(16,found().size());
        assertEquals(MilepostDiscoveryLedger.State.DISCOVERED,ledger.state("u","j034"));
    });}
    @Test public void sourceAndCatalogueChangesBeforeCommitRejectStalePages()throws Exception {work(()->{
        catalogue();save(row("a"));MilepostArchiveReader.Snapshot s=MilepostArchiveReader.read(c,"a",()->false);
        ledger.archiveChanged("u","a",s.stamp,s.evidence.token(),false);
        try {
            ledger.processNext("u",(u,id)->s.evidence,()->false,1000,true,(id,transaction)->{
                ledger.install(2,String.join("",Collections.nCopies(64,"b")),Collections.singletonList(MilepostDiscoveryLedgerTest.marker("p",200,true)));
                transaction.run();
            });fail("Stale catalogue page accepted");
        }catch(ConcurrentModificationException expected){}assertTrue(found().isEmpty());drain();assertTrue(found().isEmpty());
        save(row("a").put("processing_status","failed"));drain();assertTrue(found().isEmpty());
    });}
    @Test public void captureCancellationCommitsNoPageAndResumeHasNoDuplicates()throws Exception {work(()->{
        catalogue();save(row("a"));
        try{worker.runSlice(()->true,1000);fail();}catch(CancellationException expected){}
        assertNull(ledger.state("u","a"));
        final int[] polls={0};
        try{worker.runSlice(()->++polls[0]>50,1000);}catch(CancellationException expected){}
        drain();assertEquals(1,ledger.proofCount("u"));assertEquals(1,found().get(0).ordinal);
    });}
    @Test public void projectedPointCeilingIsRetainedAndLargerArraysRemainPending()throws Exception {work(()->{
        JSONObject j=row("long");JSONArray samples=new JSONArray(),coords=new JSONArray();
        long start=java.time.Instant.parse(j.getString("started_at")).toEpochMilli();
        for(int i=0;i<10000;i++) {
            net.sf.geographiclib.GeodesicData p=net.sf.geographiclib.Geodesic.WGS84.Direct(51.4,-1,90,i*2);
            coords.put(new JSONArray().put(p.lon2).put(p.lat2));samples.put(new JSONArray().put(p.lon2).put(p.lat2).put(3).put(start+i*2000).put(1));
        }
        j.put("capture_route_samples",samples).put("route_geometry",new JSONObject().put("type","LineString").put("coordinates",coords))
                .put("ended_at",java.time.Instant.ofEpochMilli(start+20000000).toString());
        Files.write(MilepostArchiveReader.file(c,"long").toPath(),j.toString().getBytes(StandardCharsets.UTF_8));
        MilepostProximity.Evidence full=MilepostJourneyEvidence.nativeArchive(j),projected=MilepostArchiveReader.read(c,"long",()->false).evidence;
        assertEquals("android_activity_capture",projected.source());assertEquals(full.problem(),projected.problem());
        samples.put(samples.get(9999));Files.write(MilepostArchiveReader.file(c,"long").toPath(),j.toString().getBytes(StandardCharsets.UTF_8));
        assertEquals("projection_limit",MilepostArchiveReader.read(c,"long",()->false).evidence.source());
    });}
    @Test public void spatialSqlPreservesExactHundredMetreBoundary()throws Exception {work(()->{
        List<MilepostDiscoveryLedger.Marker> markers=new ArrayList<>();
        for(int distance:new int[]{0,75,99,100,101,125,200})markers.add(MilepostDiscoveryLedgerTest.marker("p"+distance,distance,true));
        ledger.install(1,MilepostDiscoveryLedgerTest.HASH,markers);save(row("a"));drain();assertEquals(4,found().size());
        assertEquals(MilepostDiscoveryLedger.State.DISCOVERED,ledger.assessmentState("u","a","p100"));
        assertNotEquals(MilepostDiscoveryLedger.State.DISCOVERED,ledger.assessmentState("u","a","p101"));
        assertNotEquals(MilepostDiscoveryLedger.State.DISCOVERED,ledger.assessmentState("u","a","p200"));
    });}
    @Test public void concurrentArchiveCorrectionDuringEvaluationDoesNotBlockSaveOrCommitOldProof()throws Exception {work(()->{
        catalogue();save(row("a"));
        java.util.concurrent.atomic.AtomicBoolean changed=new java.util.concurrent.atomic.AtomicBoolean();
        ExecutorService saveThread=Executors.newSingleThreadExecutor();
        try {
            worker.runSlice(()->{
                boolean evaluating=false;
                for(StackTraceElement frame:Thread.currentThread().getStackTrace())
                    evaluating|=frame.getClassName().equals(MilepostProximity.class.getName())&&frame.getMethodName().equals("assess");
                if(evaluating&&changed.compareAndSet(false,true)) {
                    assertFalse("Proximity must not hold archive monitor",Thread.holdsLock(JourneyStore.class));
                    try{saveThread.submit(()->{try{save(row("a").put("processing_status","failed"));}catch(Exception e){throw new RuntimeException(e);}}).get(5,TimeUnit.SECONDS);}
                    catch(Exception e){throw new RuntimeException(e);}
                }
                return false;
            },1000);
        } finally {saveThread.shutdownNow();}
        assertTrue("Correction must race actual proximity evaluation",changed.get());assertTrue(found().isEmpty());drain();
        assertEquals(MilepostDiscoveryLedger.State.PENDING_EVIDENCE,ledger.state("u","a"));assertTrue(found().isEmpty());
    });}
    @Test public void additiveStageThreeSchemaUpgradePreservesRecognition()throws Exception {work(()->{
        catalogue();save(row("a"));drain();ledger.close();
        try(SQLiteDatabase db=SQLiteDatabase.openDatabase(c.getDatabasePath(name).getPath(),null,SQLiteDatabase.OPEN_READWRITE)) {
            db.execSQL("DROP TABLE archive_source");db.execSQL("DROP TABLE archive_cursor");db.execSQL("DROP INDEX marker_bounds");db.setVersion(1);
        }
        ledger=new MilepostDiscoveryLedger(c,name);worker=new MilepostReconciler(c,ledger,"u");
        assertEquals(1,found().get(0).ordinal);drain();assertEquals(1,found().get(0).ordinal);assertEquals(1,ledger.proofCount("u"));
    });}
    @Test public void stageOneCandidatePositionsRemainIneligibleAndFullVolumeProbe()throws Exception {work(()->{
        String path=System.getenv("MILEPOST_CANDIDATE_CATALOGUE");assertNotNull("CI must supply pinned full inventory",path);
        List<String> lines=Files.readAllLines(Paths.get(path));assertEquals(889,lines.size());
        List<MilepostDiscoveryLedger.Marker> candidates=new ArrayList<>(),synthetic=new ArrayList<>();
        for(String line:lines){JSONObject p=new JSONObject(line);assertFalse(p.getBoolean("eligible"));
            String id="fixture:"+p.getString("osm_id");
            candidates.add(new MilepostDiscoveryLedger.Marker(id,p.getDouble("longitude"),p.getDouble("latitude"),false,0,4102444800000L,Collections.emptyList()));
            synthetic.add(new MilepostDiscoveryLedger.Marker(id,p.getDouble("longitude"),p.getDouble("latitude"),true,0,4102444800000L,Collections.emptyList()));
        }
        ledger.install(1,MilepostDiscoveryLedgerTest.HASH,candidates);save(row("probe"));drain();assertTrue(found().isEmpty());
        // Synthetic approval is local to this isolated test DB; inventory bytes are never changed.
        ledger.install(2,String.join("",Collections.nCopies(64,"b")),synthetic);
        for(int i=0;i<1605;i++) {
            JSONObject j=row(String.format("v%04d",i));
            MilepostDiscoveryLedger.Marker target=synthetic.get(i%synthetic.size());
            for(int point=0;point<5;point++) {
                net.sf.geographiclib.GeodesicData position=net.sf.geographiclib.Geodesic.WGS84.Direct(target.lat,target.lon,90,(point-2)*40);
                j.getJSONArray("capture_route_samples").getJSONArray(point).put(0,position.lon2).put(1,position.lat2);
                j.getJSONObject("route_geometry").getJSONArray("coordinates").getJSONArray(point).put(0,position.lon2).put(1,position.lat2);
            }
            Files.write(MilepostArchiveReader.file(c,j.getString("journey_id")).toPath(),j.toString().getBytes(StandardCharsets.UTF_8));
        }
        Runtime rt=Runtime.getRuntime();System.gc();long beforeHeap=rt.totalMemory()-rt.freeMemory();
        java.util.concurrent.atomic.AtomicBoolean sampling=new java.util.concurrent.atomic.AtomicBoolean(true);
        java.util.concurrent.atomic.AtomicLong peak=new java.util.concurrent.atomic.AtomicLong(beforeHeap);
        Thread sampler=new Thread(()->{while(sampling.get()){peak.accumulateAndGet(rt.totalMemory()-rt.freeMemory(),Math::max);
            try{Thread.sleep(2);}catch(InterruptedException stop){return;}}});sampler.start();
        long start=System.nanoTime();try{drain();}finally{sampling.set(false);sampler.join();}long elapsed=System.nanoTime()-start;
        int reads=worker.readSources,pages=worker.processedPages;long size=c.getDatabasePath(name).length();
        System.gc();long retained=rt.totalMemory()-rt.freeMemory();
        long retry=System.nanoTime();drain();retry=System.nanoTime()-retry;assertEquals(reads,worker.readSources);
        long assessments;
        try(SQLiteDatabase db=SQLiteDatabase.openDatabase(c.getDatabasePath(name).getPath(),null,SQLiteDatabase.OPEN_READONLY);
            android.database.Cursor cursor=db.rawQuery("SELECT COUNT(*) FROM assessment",null)){assertTrue(cursor.moveToFirst());assessments=cursor.getLong(0);}
        assertTrue("Spatial selection must reduce cross product",assessments<1606L*889/100);
        System.out.println("MILEPOST_RECONCILE_PROBE journeys=1606 markers=889 elapsed_ms="+elapsed/1000000+" source_reads="+reads+" pages="+pages
                +" assessments="+assessments+" cartesian="+(1606L*889)+" database_bytes="+size+" retained_process_heap="+retained
                +" sampled_peak_heap="+peak.get()+" before_heap="+beforeHeap+" retry_ms="+retry/1000000+" max_commit_monitor_us="+worker.maxCommitNanos/1000);
    });}
}
