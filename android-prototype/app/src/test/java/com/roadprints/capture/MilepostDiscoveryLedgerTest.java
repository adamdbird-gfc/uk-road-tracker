package com.roadprints.capture;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import java.util.*;
import java.util.concurrent.*;
import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28,manifest=Config.NONE)
public class MilepostDiscoveryLedgerTest {
    static final String HASH=String.join("",Collections.nCopies(64,"a"));
    Context context;
    String name;
    MilepostDiscoveryLedger ledger;
    interface Work {void run() throws Exception;}
    void worker(Work work)throws Exception {
        ExecutorService executor=Executors.newSingleThreadExecutor();
        try {executor.submit(()->{try{work.run();}catch(Exception e){throw new RuntimeException(e);}}).get();}
        catch(ExecutionException e){Throwable t=e.getCause();if(t instanceof RuntimeException&&t.getCause()!=null)t=t.getCause();
            if(t instanceof Exception)throw (Exception)t;if(t instanceof Error)throw (Error)t;throw e;}
        finally {executor.shutdownNow();}
    }
    @Before public void setup()throws Exception {
        context=RuntimeEnvironment.getApplication();name="milepost-"+UUID.randomUUID()+".db";
        worker(()->ledger=new MilepostDiscoveryLedger(context,name));
    }
    @After public void cleanup()throws Exception {worker(()->ledger.close());context.deleteDatabase(name);}
    static MilepostDiscoveryLedger.Marker marker(String id,double metres,boolean eligible) {
        MilepostProximity.Point p=MilepostProximityTest.offset(MilepostProximityTest.ORIGIN,0,metres);
        return new MilepostDiscoveryLedger.Marker(id,p.lon,p.lat,eligible,0,4102444800000L,Arrays.asList("osm:node:"+id));
    }
    MilepostProximity.Evidence evidence(String id)throws Exception {
        return MilepostJourneyEvidence.nativeArchive(MilepostJourneyEvidenceTest.journey("walking").put("journey_id",id));
    }
    void register(MilepostProximity.Evidence e,long version){ledger.sourceChanged("u",e.journeyId(),version,e.token(),false);}
    void drain(MilepostProximity.Evidence e)throws Exception {while(ledger.processNext("u",(u,id)->e,()->false,1000)){};}
    List<MilepostDiscoveryLedger.Discovery> discoveries(){return ledger.discoveries("u","",16);}
    @Test public void thresholdBoundaryAndCompactProvenancePersist()throws Exception {worker(()->{
        List<MilepostDiscoveryLedger.Marker> markers=new ArrayList<>();
        for(double d:new double[]{0,25,50,75,99,100,101,125,200})markers.add(marker("p"+(int)d,d,true));
        ledger.install(1,HASH,markers);MilepostProximity.Evidence e=evidence("j");register(e,1);drain(e);
        assertEquals(6,discoveries().size());assertEquals(6,ledger.proofCount("u"));
        assertEquals(MilepostDiscoveryLedger.State.ASSESSED_NO_DISCOVERY,ledger.assessmentState("u","j","p101"));
        try(SQLiteDatabase db=SQLiteDatabase.openDatabase(context.getDatabasePath(name).getPath(),null,SQLiteDatabase.OPEN_READONLY);
            android.database.Cursor c=db.rawQuery("SELECT token,validator,source,mode,precision,section,source_end,method,post_lon FROM assessment WHERE state='DISCOVERED' LIMIT 1",null)) {
            assertTrue(c.moveToFirst());assertEquals(e.token(),c.getString(0));assertEquals(e.validationRevision(),c.getString(1));
            assertEquals("android_activity_capture",c.getString(2));assertEquals("walking",c.getString(3));assertEquals("journey",c.getString(4));
            assertTrue(c.getInt(5)>=0);assertTrue(c.getInt(6)>0);assertEquals("WGS84-geodesic-finite-segment",c.getString(7));
        }
    });}
    @Test public void retriesOverlapReimportAndUsersNeverDuplicatePhysicalDiscovery()throws Exception {worker(()->{
        ledger.install(1,HASH,Arrays.asList(marker("p",50,true)));
        MilepostProximity.Evidence a=evidence("a"),b=evidence("b");register(a,1);drain(a);register(a,1);drain(a);
        register(b,1);drain(b);assertEquals(2,ledger.proofCount("u"));assertEquals(1,discoveries().size());
        assertEquals("a",discoveries().get(0).journey);assertEquals("p",ledger.canonical("osm:node:p"));
        assertTrue(ledger.discoveries("other","",16).isEmpty());assertEquals(1,discoveries().get(0).ordinal);
    });}
    @Test public void correctionsRetractImmediatelyAndDeletionFallsBackThenWithdraws()throws Exception {worker(()->{
        ledger.install(1,HASH,Arrays.asList(marker("p",50,true)));
        MilepostProximity.Evidence a=evidence("a"),b=evidence("b");register(a,1);drain(a);register(b,1);drain(b);
        ledger.sourceChanged("u","a",2,"deletion",true);assertEquals("b",discoveries().get(0).journey);
        ledger.sourceChanged("u","b",2,"correction",false);assertTrue(discoveries().isEmpty());assertTrue(ledger.previouslyRecognised("u","p"));assertNotNull(ledger.withdrawalReason("u","p"));
        MilepostProximity.Evidence bad=MilepostJourneyEvidence.nativeArchive(MilepostJourneyEvidenceTest.journey("walking").put("journey_id","b").put("processing_status","failed"));
        ledger.sourceChanged("u","b",3,bad.token(),false);drain(bad);
        assertEquals(MilepostDiscoveryLedger.State.PENDING_EVIDENCE,ledger.state("u","b"));assertTrue(discoveries().isEmpty());
        ledger.sourceChanged("u","a",3,a.token(),false);drain(a);assertEquals(1,discoveries().get(0).ordinal);
        assertEquals(1000,discoveries().get(0).firstRecognised);
    });}
    @Test public void timelineGapsVehiclesAndUnsupportedDatesStayPending()throws Exception {worker(()->{
        ledger.install(1,HASH,Arrays.asList(marker("p",50,true)));
        List<JSONObject> rows=new ArrayList<>();
        JSONObject timeline=MilepostJourneyEvidenceTest.journey("walking");timeline.getJSONObject("source").put("type","timeline_import");rows.add(timeline);
        rows.add(MilepostJourneyEvidenceTest.journey("driving"));
        JSONObject gap=MilepostJourneyEvidenceTest.journey("walking");gap.getJSONArray("capture_route_samples").getJSONArray(2).put(3,1);rows.add(gap);
        rows.add(MilepostJourneyEvidenceTest.journey("walking").put("started_at","unknown"));
        for(int i=0;i<rows.size();i++) {
            MilepostProximity.Evidence e=MilepostJourneyEvidence.nativeArchive(rows.get(i).put("journey_id","j"+i));register(e,1);drain(e);
            assertEquals(MilepostDiscoveryLedger.State.PENDING_EVIDENCE,ledger.state("u",e.journeyId()));
            ledger.reassess("u",e.journeyId());assertEquals(MilepostDiscoveryLedger.State.QUEUED,ledger.state("u",e.journeyId()));drain(e);
        }
        assertTrue(discoveries().isEmpty());
    });}
    @Test public void emptyAndCandidateCataloguesCannotCreateNegativeHistory()throws Exception {worker(()->{
        MilepostProximity.Evidence e=evidence("j");register(e,1);drain(e);
        assertEquals(MilepostDiscoveryLedger.State.PENDING_CATALOGUE,ledger.state("u","j"));
        ledger.install(1,HASH,Arrays.asList(marker("p",50,false)));drain(e);
        assertEquals(MilepostDiscoveryLedger.State.PENDING_EVIDENCE,ledger.state("u","j"));assertTrue(discoveries().isEmpty());
    });}
    @Test public void syntheticValidatedBackfillCanUpgradePendingWithoutNewJourneyIdentity()throws Exception {worker(()->{
        ledger.install(1,HASH,Arrays.asList(marker("p",50,true)));
        JSONObject row=MilepostJourneyEvidenceTest.journey("walking").put("journey_id","same");
        row.getJSONObject("source").put("type","timeline_import");
        MilepostProximity.Evidence pending=MilepostJourneyEvidence.nativeArchive(row);register(pending,1);drain(pending);
        assertEquals(MilepostDiscoveryLedger.State.PENDING_EVIDENCE,ledger.state("u","same"));
        MilepostProximity.Evidence base=MilepostProximityTest.pass("walking");
        MilepostProximity.Evidence validated=new MilepostProximity.Evidence() {
            public String journeyId(){return "same";}public String journeyDate(){return base.journeyDate();}
            public String journeyEndDate(){return "2026-11-05T00:02:00Z";}public String mode(){return base.mode();}
            public String source(){return "synthetic-independent-validator";}public String token(){return "validated-backfill-token";}
            public String validationRevision(){return "synthetic-validator-2";}public MilepostProximity.Outcome problem(){return null;}
            public Iterable<MilepostProximity.Segment> segments(){return base.segments();}
        };
        register(validated,2);drain(validated);assertEquals("same",discoveries().get(0).journey);
        assertEquals(1,ledger.proofCount("u"));register(validated,2);drain(validated);assertEquals(1,discoveries().size());
    });}
    @Test public void catalogueChangeDuringEvaluationDiscardsThePage()throws Exception {worker(()->{
        ledger.install(1,HASH,Arrays.asList(marker("p",50,true)));MilepostProximity.Evidence e=evidence("j");register(e,1);
        assertThrows(ConcurrentModificationException.class,()->ledger.processNext("u",(u,id)->{
            ledger.install(2,HASH,Arrays.asList(marker("p",200,true)));return e;
        },()->false,1000));
        assertEquals(0,ledger.proofCount("u"));drain(e);
        assertEquals(MilepostDiscoveryLedger.State.ASSESSED_NO_DISCOVERY,ledger.state("u","j"));
    });}
    @Test public void catalogueChangeRemovalRelocationAndPinConflictsFailClosed()throws Exception {worker(()->{
        MilepostProximity.Evidence e=evidence("j");ledger.install(1,HASH,Arrays.asList(marker("p",50,true)));register(e,1);drain(e);
        assertThrows(IllegalArgumentException.class,()->ledger.install(1,HASH,Arrays.asList(marker("p",60,true))));
        ledger.install(2,HASH,Arrays.asList(marker("p",200,true)));assertTrue(discoveries().isEmpty());drain(e);
        assertEquals(MilepostDiscoveryLedger.State.ASSESSED_NO_DISCOVERY,ledger.state("u","j"));
        ledger.install(3,HASH,Collections.emptyList());drain(e);assertTrue(discoveries().isEmpty());
        assertThrows(IllegalArgumentException.class,()->ledger.install(1,HASH,Collections.emptyList()));
        assertThrows(IllegalArgumentException.class,()->ledger.install(4,"bad",Collections.emptyList()));
        assertThrows(android.database.sqlite.SQLiteConstraintException.class,()->ledger.install(4,HASH,Arrays.asList(marker("p",1,true),marker("p",2,true))));
        assertEquals(MilepostDiscoveryLedger.State.PENDING_CATALOGUE,ledger.state("u","j"));
    });}
    @Test public void retiredIdentityAndAliasesCannotBeReassigned()throws Exception {worker(()->{
        ledger.install(1,HASH,Arrays.asList(marker("p",50,true)));
        MilepostDiscoveryLedger.Marker other=marker("q",50,true);
        assertThrows(IllegalArgumentException.class,()->ledger.install(2,HASH,Arrays.asList(new MilepostDiscoveryLedger.Marker(other.id,other.lon,other.lat,true,0,4102444800000L,Arrays.asList("osm:node:p")))));
        assertEquals("p",ledger.canonical("osm:node:p"));
        ledger.install(2,HASH,Collections.emptyList());
        assertThrows(IllegalArgumentException.class,()->ledger.install(3,HASH,Arrays.asList(marker("p",50,true))));
        assertEquals("p",ledger.canonical("osm:node:p"));
    });}
    @Test public void datedLocationsRequireTheWholeJourneyInterval()throws Exception {worker(()->{
        MilepostProximity.Evidence e=evidence("j");MilepostDiscoveryLedger.Marker m=marker("p",50,true);
        ledger.install(1,HASH,Arrays.asList(new MilepostDiscoveryLedger.Marker(m.id,m.lon,m.lat,true,0,java.time.Instant.parse("2026-11-05T00:01:00Z").toEpochMilli(),m.aliases)));
        register(e,1);drain(e);assertTrue(discoveries().isEmpty());assertEquals(MilepostDiscoveryLedger.State.PENDING_EVIDENCE,ledger.state("u","j"));
    });}
    @Test public void cancellationAndStaleWorkersCommitNothing()throws Exception {worker(()->{
        ledger.install(1,HASH,Arrays.asList(marker("p",50,true)));MilepostProximity.Evidence e=evidence("j");register(e,1);
        assertThrows(CancellationException.class,()->ledger.processNext("u",(u,id)->e,()->true,1000));assertEquals(0,ledger.proofCount("u"));
        assertThrows(ConcurrentModificationException.class,()->ledger.processNext("u",(u,id)->{ledger.sourceChanged("u","j",2,"deleted",true);return e;},()->false,1000));
        assertEquals(0,ledger.proofCount("u"));assertThrows(ConcurrentModificationException.class,()->register(e,1));
        assertThrows(ConcurrentModificationException.class,()->ledger.sourceChanged("u","j",2,e.token(),false));
    });}
    @Test public void restartResumesPagesWithoutDuplicateRecognition()throws Exception {worker(()->{
        List<MilepostDiscoveryLedger.Marker> markers=new ArrayList<>();for(int i=0;i<35;i++)markers.add(marker(String.format(Locale.ROOT,"p%03d",i),50,true));
        ledger.install(1,HASH,markers);MilepostProximity.Evidence e=evidence("j");register(e,1);
        assertTrue(ledger.processNext("u",(u,id)->e,()->false,1000));assertEquals(16,ledger.proofCount("u"));assertTrue(discoveries().isEmpty());
        assertEquals(MilepostDiscoveryLedger.State.QUEUED,ledger.state("u","j"));ledger.close();ledger=new MilepostDiscoveryLedger(context,name);
        assertTrue(ledger.processNext("u",(u,id)->e,()->false,2000));assertEquals(32,ledger.proofCount("u"));drain(e);
        assertEquals(35,ledger.proofCount("u"));assertFalse(ledger.processNext("u",(u,id)->e,()->false,2000));
        String after="";int count=0;for(;;){List<MilepostDiscoveryLedger.Discovery> page=ledger.discoveries("u",after,16);if(page.isEmpty())break;count+=page.size();after=page.get(page.size()-1).marker;}
        assertEquals(35,count);assertThrows(IllegalArgumentException.class,()->ledger.discoveries("u","",1000));
    });}
    @Test public void earlierBackfillUpdatesProofWithoutReorderingRecognition()throws Exception {worker(()->{
        ledger.install(1,HASH,Arrays.asList(marker("p",50,true)));MilepostProximity.Evidence e=evidence("z");register(e,1);drain(e);
        JSONObject row=MilepostJourneyEvidenceTest.journey("walking").put("journey_id","earlier");
        long delta=86400000L;row.put("started_at","2026-11-04T00:00:00Z").put("ended_at","2026-11-04T00:02:00Z");
        for(int i=0;i<5;i++)row.getJSONArray("capture_route_samples").getJSONArray(i).put(3,row.getJSONArray("capture_route_samples").getJSONArray(i).getLong(3)-delta);
        MilepostProximity.Evidence earlier=MilepostJourneyEvidence.nativeArchive(row);register(earlier,1);drain(earlier);
        assertEquals("earlier",discoveries().get(0).journey);assertEquals(1,discoveries().get(0).ordinal);assertEquals(1000,discoveries().get(0).firstRecognised);
    });}
    @Test public void evaluatorUpgradeInvalidatesDerivedRowsOnOpen()throws Exception {worker(()->{
        ledger.install(1,HASH,Arrays.asList(marker("p",50,true)));MilepostProximity.Evidence e=evidence("j");register(e,1);drain(e);ledger.close();
        try(SQLiteDatabase db=SQLiteDatabase.openDatabase(context.getDatabasePath(name).getPath(),null,SQLiteDatabase.OPEN_READWRITE)){db.execSQL("UPDATE runtime SET evaluator='old-validator'");}
        ledger=new MilepostDiscoveryLedger(context,name);assertTrue(discoveries().isEmpty());assertEquals(MilepostDiscoveryLedger.State.QUEUED,ledger.state("u","j"));drain(e);assertEquals(1,discoveries().size());
    });}
    @Test public void futureSchemaIsPreservedAndRefused()throws Exception {worker(()->{
        ledger.close();try(SQLiteDatabase db=SQLiteDatabase.openDatabase(context.getDatabasePath(name).getPath(),null,SQLiteDatabase.OPEN_READWRITE)){db.setVersion(99);}
        assertThrows(IllegalStateException.class,()->new MilepostDiscoveryLedger(context,name));
        try(SQLiteDatabase db=SQLiteDatabase.openDatabase(context.getDatabasePath(name).getPath(),null,SQLiteDatabase.OPEN_READONLY)){assertEquals(99,db.getVersion());}
    });}
    @Test public void mainThreadEntryIsRejected() {
        assertThrows(IllegalStateException.class,()->new MilepostDiscoveryLedger(context,"forbidden.db"));
    }
    @Test public void boundedArchiveProbe()throws Exception {worker(()->{
        ledger.install(1,HASH,Arrays.asList(marker("p",50,true)));long start=System.nanoTime();
        for(int i=0;i<1605;i++) {
            MilepostProximity.Evidence e=evidence("j"+i);register(e,1);assertTrue(ledger.processNext("u",(u,id)->e,()->false,1000));
        }
        assertEquals(1605,ledger.proofCount("u"));assertEquals(1,discoveries().size());
        System.out.println("MILEPOST_LEDGER_PROBE journeys=1605 markers=1 max_page="+MilepostDiscoveryLedger.PAGE+" elapsed_ms="+(System.nanoTime()-start)/1000000+" db_bytes="+context.getDatabasePath(name).length());
    });}
}
