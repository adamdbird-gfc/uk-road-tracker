package com.roadprints.capture;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28,manifest=Config.NONE)
public class MilepostAchievementsTest {
    Context c;String name;MilepostDiscoveryLedger ledger;
    static final String USER="stage5-fixture";
    interface Work {void run()throws Exception;}
    void work(Work work)throws Exception {
        ExecutorService e=Executors.newSingleThreadExecutor();
        try{e.submit(()->{try{work.run();}catch(Exception ex){throw new RuntimeException(ex);}}).get();}
        catch(ExecutionException ex){Throwable t=ex.getCause();if(t instanceof RuntimeException&&t.getCause()!=null)t=t.getCause();
            if(t instanceof Exception)throw (Exception)t;if(t instanceof Error)throw (Error)t;throw ex;}
        finally{e.shutdownNow();}
    }
    @Before public void setup()throws Exception {
        c=RuntimeEnvironment.getApplication();name="stage5-"+UUID.randomUUID()+".db";JourneyStore.deleteAll(c);
        c.getSharedPreferences("roadprints_ncn_national_v2",0).edit().putBoolean("enabled",true).commit();
        work(()->ledger=new MilepostDiscoveryLedger(c,name));
    }
    @After public void cleanup()throws Exception {
        setNcn(null);c.getSharedPreferences("roadprints_ncn_national_v2",0).edit().clear().commit();
        work(()->ledger.close());c.deleteDatabase(name);JourneyStore.deleteAll(c);
    }
    static void setNcn(NcnProgressStore.Snapshot n)throws Exception {
        // Isolated published-snapshot fixture; no production test hook or NCN activation/job.
        java.lang.reflect.Field f=NcnProgressStore.class.getDeclaredField("snapshot");f.setAccessible(true);f.set(null,n);
    }
    static NcnCatalogue catalogue()throws Exception {
        JSONArray routes=new JSONArray(),paths=new JSONArray();
        for(int i=0;i<NcnCatalogue.KEYS.length;i++) {
            String key=NcnCatalogue.KEYS[i];
            routes.put(new JSONObject().put("key",key).put("length_m",100).put("logical",new JSONObject().put("t",100)));
            paths.put(new JSONObject().put("way",""+(100+i)).put("route",key).put("source_geometry_sha256",MilepostDiscoveryLedgerTest.HASH)
                    .put("origin",new JSONArray("[0,0]")).put("coordinates",new JSONArray("[[0,0],[0.001,0]]"))
                    .put("nodes",new JSONArray("[1,2]")).put("positions",new JSONArray().put(0).put(6371008.8*Math.toRadians(.001)))
                    .put("modes",new JSONArray("[\"on_foot\",\"cycling\"]"))
                    .put("bindings",new JSONArray().put(new JSONObject().put("start",0).put("end",100).put("low",0).put("high",100)
                            .put("target","t").put("modes",new JSONArray("[\"on_foot\",\"cycling\"]")))));
        }
        byte[] raw=new JSONObject().put("schema_version",1).put("scope",NcnCatalogue.SCOPE).put("whole_published_route_ready",false)
                .put("physical_equivalence_verified",true).put("attribution","synthetic test only").put("routes",routes).put("paths",paths).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return new NcnCatalogue(raw,new JSONObject().put("sha256",NcnJson.hash(raw)));
    }
    NcnProgressStore.Snapshot ncn(double done)throws Exception {
        NcnCatalogue cat=catalogue();NcnLedger l=new NcnLedger(cat);
        l.put("synthetic",new JSONObject().put("mode","on_foot").put("spans",new JSONArray().put(new JSONArray().put("NCN:167").put("100").put(0).put(done))));
        l.networkMetres("combined");return new NcnProgressStore.Snapshot(cat,l,JourneyStore.dataRevision(c),Collections.emptyMap(),0,JourneyStore.ncnDataRevision(c));
    }
    MilepostDiscoveryLedger.Marker marker(String id,String designer,boolean verified,boolean eligible) {
        MilepostDiscoveryLedger.Marker m=MilepostDiscoveryLedgerTest.marker("fixture:"+id,50,eligible);
        return new MilepostDiscoveryLedger.Marker(m.id,m.lon,m.lat,eligible,m.from,m.until,Arrays.asList("fixture:alias:"+id,"fixture:second:"+id),designer,verified);
    }
    void install(int revision,List<MilepostDiscoveryLedger.Marker> markers){ledger.install(revision,MilepostDiscoveryLedgerTest.HASH,markers);}
    void posts(int count)throws Exception {
        List<MilepostDiscoveryLedger.Marker> rows=new ArrayList<>();String[] ds={"mills","rowe","mccoll","dudgeon","unknown"};
        for(int i=0;i<count;i++)rows.add(marker("p"+i,ds[Math.min(i,4)],i<4,true));install(1,rows);
    }
    JSONObject journey(String id)throws Exception{return MilepostJourneyEvidenceTest.journey("walking").put("journey_id",id);}
    void save(String id)throws Exception{JourneyStore.save(c,journey(id));}
    void derive(long now)throws Exception {
        try(MilepostAchievementDeriver w=new MilepostAchievementDeriver(c,ledger,USER)) {
            int slices=0;while(w.runSlice(()->false,now))if(++slices>10000)fail("Did not converge");
        }
    }
    JSONObject state()throws Exception{return MilepostAchievementDeriver.state(c,ledger,USER);}
    JSONObject award(int i)throws Exception {
        JSONArray rows=state().getJSONArray("rows");for(int j=0;j<rows.length();j++)if(rows.getJSONObject(j).getString("id").equals(MilepostAchievements.IDS[i]))return rows.getJSONObject(j);
        throw new AssertionError("Missing "+i);
    }
    int earned()throws Exception {int n=0;JSONArray rows=state().getJSONArray("rows");for(int i=0;i<rows.length();i++)if(rows.getJSONObject(i).getBoolean("earned"))n++;return n;}
    @Test public void allSevenRulesAtomicAndInactive()throws Exception {work(()->{
        posts(7);save("a");setNcn(ncn(100));String oldNcn=NcnAchievements.state(c).toString();
        String oldAchievements=c.getSharedPreferences("roadprints_achievements",0).getAll().toString();derive(1000);
        assertEquals(7,earned());assertTrue(state().getBoolean("current"));
        assertEquals(7,award(5).getJSONArray("support").length());assertEquals(6,award(6).getJSONArray("support").length());
        assertEquals("unknown",award(4).getString("precision"));assertTrue(award(4).isNull("event_date"));
        assertEquals("unknown",award(6).getString("precision"));
        for(int i=0;i<7;i++)assertEquals(1000,award(i).getLong("first_recognised"));
        assertEquals(oldNcn,NcnAchievements.state(c).toString());assertEquals(oldAchievements,c.getSharedPreferences("roadprints_achievements",0).getAll().toString());
        for(AchievementStore.Definition d:AchievementStore.definitions())assertFalse(Arrays.asList(MilepostAchievements.IDS).contains(d.id));
        String before=state().toString();derive(2000);assertEquals(before,state().toString());
    });}
    @Test public void sixThenSevenDistinctPostsAliasesAndDuplicates()throws Exception {work(()->{
        posts(6);save("a");save("b");setNcn(ncn(0));derive(1000);
        assertFalse(award(5).getBoolean("earned"));assertEquals(4,earned());
        assertEquals(6,MilepostAchievements.number(ledger.db(),"SELECT discovery_count FROM achievement_snapshot WHERE user=?",USER));
        List<MilepostDiscoveryLedger.Marker> rows=new ArrayList<>();for(int i=0;i<7;i++)rows.add(marker("p"+i,"unknown",false,true));
        install(2,rows);derive(2000);assertTrue(award(5).getBoolean("earned"));assertEquals(1,earned());
        assertEquals("fixture:p0",ledger.canonical("fixture:second:p0"));
        assertEquals(7,MilepostAchievements.number(ledger.db(),"SELECT discovery_count FROM achievement_snapshot WHERE user=?",USER));
        assertEquals(1000,award(0).getLong("first_recognised"));assertFalse(award(6).getBoolean("earned"));
    });}
    @Test public void unknownAndUnverifiedDesignsDoNotAwardDesignerAndCandidatesDoNotCount()throws Exception {work(()->{
        List<MilepostDiscoveryLedger.Marker> rows=new ArrayList<>();
        for(int i=0;i<7;i++)rows.add(marker("p"+i,i==0?"unknown":"mills",false,true));
        rows.add(marker("candidate","rowe",true,false));install(1,rows);save("a");setNcn(ncn(0));derive(1000);
        assertEquals(1,earned());assertTrue(award(5).getBoolean("earned"));
        assertEquals(7,MilepostAchievements.number(ledger.db(),"SELECT discovery_count FROM achievement_snapshot WHERE user=?",USER));
    });}
    @Test public void sevenAcrossArbitraryUkRegionsHaveNoGeographicQuota()throws Exception {work(()->{
        double[][] locations={{-.1,51.5},{-3.2,51.5},{-5.9,54.6},{-4.3,55.9},{-1.1,53.9},{-5.5,50.1},{-1.1,60.2}};
        List<MilepostDiscoveryLedger.Marker> rows=new ArrayList<>();
        for(int i=0;i<7;i++)rows.add(new MilepostDiscoveryLedger.Marker("fixture:uk"+i,locations[i][0],locations[i][1],true,0,4102444800000L,Collections.emptyList()));
        install(1,rows);
        // Seed already-validated compact ledger facts only, independently of proximity.
        seedFacts(7,1,"fixture:uk");setNcn(ncn(0));publishPrepared(1000);
        assertTrue(award(5).getBoolean("earned"));assertEquals(7,award(5).getJSONArray("support").length());
    });}
    @Test public void trueCombinedNcnCompletionOnlyAndGrimshawIndependentOfPosts()throws Exception {work(()->{
        install(1,Collections.emptyList());setNcn(ncn(99.96));derive(1000);assertEquals(0,earned());
        setNcn(ncn(100-1e-11));derive(2000);assertEquals(1,earned());assertTrue(award(4).getBoolean("earned"));
        NcnProgressStore.Snapshot s=ncn(100);s.catalogue.routes.get("NCN:167").put("measurable",false);assertNull(NcnAchievements.firstCompletedRoute(s));
        s=ncn(100);s.catalogue.routes.put("RCN:test",s.catalogue.routes.remove("NCN:167"));assertNull(NcnAchievements.firstCompletedRoute(s));
        s=ncn(0);NcnCatalogue cat=s.catalogue;NcnLedger l=new NcnLedger(cat);
        l.put("foot",new JSONObject().put("mode","on_foot").put("spans",new JSONArray("[[\"NCN:167\",\"100\",0,60]]")));
        l.put("cycle",new JSONObject().put("mode","cycling").put("spans",new JSONArray("[[\"NCN:167\",\"100\",60,100]]")));
        assertNotNull(NcnAchievements.firstCompletedRoute(new NcnProgressStore.Snapshot(cat,l,0,Collections.emptyMap())));
    });}
    @Test public void correctionDeletionFallbackReinstatementAndHistoricalBackfill()throws Exception {work(()->{
        posts(7);save("a");save("b");setNcn(ncn(100));derive(1000);long ordinal=award(0).getLong("ordinal");
        JourneyStore.delete(c,"a");setNcn(ncn(100));derive(2000);assertEquals(7,earned());assertEquals("b",award(0).getJSONArray("support").getJSONObject(0).getString("journey"));
        JSONObject bad=journey("b").put("processing_status","failed");JourneyStore.save(c,bad);setNcn(ncn(0));derive(3000);
        assertEquals(0,earned());assertEquals(1000,award(0).getLong("first_recognised"));assertEquals(ordinal,award(0).getLong("ordinal"));
        save("b");setNcn(ncn(100));derive(4000);assertEquals(7,earned());assertEquals(1000,award(0).getLong("first_recognised"));
        JSONObject early=journey("early");long delta=86400000L;early.put("started_at","2026-11-04T00:00:00Z").put("ended_at","2026-11-04T00:02:00Z");
        for(int i=0;i<5;i++){JSONArray p=early.getJSONArray("capture_route_samples").getJSONArray(i);p.put(3,p.getLong(3)-delta);}
        JourneyStore.save(c,early);setNcn(ncn(100));derive(5000);
        assertEquals("2026-11-04T00:00:00Z",award(0).getString("event_date"));assertEquals("journey",award(0).getString("precision"));
        assertEquals("2026-11-05T00:00:00Z",award(0).getString("first_event"));assertEquals(ordinal,award(0).getLong("ordinal"));
        assertEquals("2026-11-04T00:00:00Z",award(5).getString("event_date"));
    });}
    @Test public void incompleteAndStaleNcnRetainPreviousResult()throws Exception {work(()->{
        posts(7);save("a");setNcn(ncn(100));derive(1000);String previous=MilepostAchievements.state(ledger,USER).toString();
        save("b");derive(2000);assertFalse(state().getBoolean("current"));assertEquals(previous,MilepostAchievements.state(ledger,USER).toString());
        setNcn(null);derive(3000);assertEquals(previous,MilepostAchievements.state(ledger,USER).toString());
        setNcn(ncn(0));derive(4000);assertFalse(award(4).getBoolean("earned"));assertFalse(award(6).getBoolean("earned"));
        ledger.reassess(USER,"a");assertThrows(ConcurrentModificationException.class,()->MilepostAchievements.prepare(ledger,USER,null,()->false));
    });}
    @Test public void interruptedPublicationRollsBackAllSevenAndRestartIsIdempotent()throws Exception {work(()->{
        posts(7);save("a");setNcn(ncn(100));derive(1000);String before=MilepostAchievements.state(ledger,USER).toString();
        MilepostAchievements.Prepared p=MilepostAchievements.prepare(ledger,USER,null,()->false);AtomicInteger polls=new AtomicInteger();
        SQLiteDatabase d=ledger.db();d.beginTransaction();try {
            assertThrows(CancellationException.class,()->MilepostAchievements.publish(d,USER,p,JourneyStore.dataRevision(c),"fixture",2000,()->polls.incrementAndGet()>3));
        }finally{d.endTransaction();}
        assertEquals(before,MilepostAchievements.state(ledger,USER).toString());ledger.close();ledger=new MilepostDiscoveryLedger(c,name);
        derive(3000);assertEquals(before,MilepostAchievements.state(ledger,USER).toString());
    });}
    @Test public void changingSourceCatalogueAndEvaluatorRejectPreparedResults()throws Exception {work(()->{
        posts(7);save("a");setNcn(ncn(100));derive(1000);
        MilepostAchievements.Prepared p=MilepostAchievements.prepare(ledger,USER,null,()->false);
        ledger.sourceChanged(USER,"a",2,"corrected",false);assertRejected(p);
        ledger.sourceChanged(USER,"a",3,MilepostArchiveReader.read(c,"a",()->false).evidence.token(),false);
        derive(2000);p=MilepostAchievements.prepare(ledger,USER,null,()->false);
        install(2,Collections.singletonList(marker("p0","mills",true,true)));assertRejected(p);
        derive(3000);p=MilepostAchievements.prepare(ledger,USER,null,()->false);
        ledger.db().execSQL("UPDATE runtime SET evaluator='incompatible'");assertRejected(p);
        assertThrows(ConcurrentModificationException.class,()->MilepostAchievements.prepare(ledger,USER,null,()->false));
    });}
    void assertRejected(MilepostAchievements.Prepared p)throws Exception {
        SQLiteDatabase d=ledger.db();d.beginTransaction();try{assertThrows(ConcurrentModificationException.class,()->MilepostAchievements.publish(d,USER,p,0,"fixture",0,()->false));}finally{d.endTransaction();}
    }
    @Test public void activeCaptureAndMainThreadCannotCompute()throws Exception {
        assertThrows(IllegalStateException.class,()->new MilepostAchievementDeriver(c,ledger,USER));
        work(()->{posts(1);save("a");setNcn(ncn(100));
            try(MilepostAchievementDeriver w=new MilepostAchievementDeriver(c,ledger,USER)) {
                c.getSharedPreferences("roadprints_capture_state",0).edit().putBoolean("active",true).commit();
                assertThrows(CancellationException.class,()->w.runSlice(()->false,1000));assertEquals(0,state().getJSONArray("rows").length());
            }finally{c.getSharedPreferences("roadprints_capture_state",0).edit().clear().commit();}
        });
    }
    @Test public void duplicateIdsAreNotRegisteredInExistingDefinitions() {
        Set<String> ids=new HashSet<>(Arrays.asList(MilepostAchievements.IDS));assertEquals(7,ids.size());
        for(AchievementStore.Definition d:AchievementStore.definitions())assertFalse(ids.contains(d.id));
    }
    // Compact synthetic facts for derivation-only tests. They do not claim evaluator approval
    // for any real post or actual archive. Integration tests above use Stage 4 instead.
    void seedFacts(int markers,int journeys,String prefix)throws Exception {
        SQLiteDatabase d=ledger.db();d.beginTransaction();try {
            for(int j=0;j<journeys;j++)d.execSQL("INSERT INTO journey VALUES(?,?,1,'fixture-token',0,'','DISCOVERED')",new Object[]{USER,"fixture:j"+j});
            for(int i=0;i<markers;i++)for(int j=0;j<2;j++) {
                String journey="fixture:j"+((i+j)%journeys);
                d.execSQL("INSERT OR IGNORE INTO assessment(user,journey,marker,state,reason,token,source_version,catalogue,evaluator,validator,source,mode,event_date,event_ms,precision,recognised,section) VALUES(?,?,?,'DISCOVERED','QUALIFIED','fixture-token',1,1,?,'fixture-validator','fixture-native','walking','2026-11-01T00:00:00Z',1793491200000,'journey',1000,0)",
                        new Object[]{USER,journey,prefix+i,MilepostDiscoveryLedger.EVALUATOR});
            }
            d.setTransactionSuccessful();
        }finally{d.endTransaction();}
    }
    void publishPrepared(long now)throws Exception {
        MilepostAchievements.Prepared p=MilepostAchievements.prepare(ledger,USER,null,()->false);SQLiteDatabase d=ledger.db();d.beginTransaction();
        try{MilepostAchievements.publish(d,USER,p,JourneyStore.dataRevision(c),"fixture",now,()->false);d.setTransactionSuccessful();}finally{d.endTransaction();}
    }
    static long heap(){Runtime r=Runtime.getRuntime();return r.totalMemory()-r.freeMemory();}
    @Test public void boundedLargeWorkloadMeasuresPeakAndRetainedHeap()throws Exception {work(()->{
        List<MilepostDiscoveryLedger.Marker> rows=new ArrayList<>();for(int i=0;i<4096;i++)rows.add(marker("bulk"+i,"mills",true,true));
        install(1,rows);rows=null;seedFacts(4096,1605,"fixture:bulk");
        publishPrepared(1000);System.gc();Thread.sleep(50);long before=heap();AtomicLong peak=new AtomicLong(before);AtomicBoolean stop=new AtomicBoolean();
        Thread sampler=new Thread(()->{while(!stop.get()){peak.accumulateAndGet(heap(),Math::max);try{Thread.sleep(2);}catch(InterruptedException e){return;}}});sampler.start();
        long start=System.nanoTime();try{for(int i=0;i<5;i++)publishPrepared(2000+i);}finally{stop.set(true);sampler.join();}
        long elapsed=(System.nanoTime()-start)/1000000;System.gc();Thread.sleep(50);long retained=heap();
        assertEquals(4096,MilepostAchievements.number(ledger.db(),"SELECT discovery_count FROM achievement_snapshot WHERE user=?",USER));
        assertTrue(award(5).getBoolean("earned"));assertEquals(7,award(5).getJSONArray("support").length());
        assertTrue("Incremental peak exceeds 64MiB",peak.get()-before<64L*1024*1024);
        assertTrue("Retained growth exceeds 8MiB",retained-before<8L*1024*1024);
        System.out.println("MILEPOST_ACHIEVEMENT_PROBE markers=4096 journeys=1605 proofs=8192 repeats=5 before="+before+" peak="+peak.get()+" incremental_peak="+(peak.get()-before)+" retained="+retained+" retained_delta="+(retained-before)+" elapsed_ms="+elapsed+" db_bytes="+c.getDatabasePath(name).length());
    });}
}
