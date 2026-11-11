package com.roadprints.capture;

import android.app.Activity;
import android.app.job.JobScheduler;
import android.os.Looper;
import android.widget.LinearLayout;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28,manifest=Config.NONE)
public class MilepostIntegrationTest {
    MilepostAchievementsTest f=new MilepostAchievementsTest();MilepostIntegration gate;
    @Before public void setup()throws Exception{f.setup();f.c.getSharedPreferences("roadprints_capture_state",0).edit().clear().commit();}
    @After public void cleanup()throws Exception{f.cleanup();}
    // No injectable global, production setter, Intent extra or preference. Constructor access is test-only reflection.
    MilepostIntegration isolated(String fingerprint)throws Exception{return isolated(fingerprint,MilepostDiscoveryLedger.EVALUATOR);}
    MilepostIntegration isolated(String fingerprint,String evaluator)throws Exception{
        java.lang.reflect.Constructor<MilepostIntegration> ctor=MilepostIntegration.class.getDeclaredConstructor(boolean.class,String.class,String.class,int.class,String.class,String.class,String.class);
        ctor.setAccessible(true);return ctor.newInstance(true,f.name,MilepostAchievementsTest.USER,1,MilepostDiscoveryLedgerTest.HASH,fingerprint,evaluator);
    }
    void prepare(int posts,double ncn)throws Exception{f.work(()->{
        f.posts(posts);f.save("historical-a");MilepostAchievementsTest.setNcn(f.ncn(ncn));
        gate=isolated(MilepostAchievements.scalar(f.ledger.db(),"SELECT fingerprint FROM catalogue"));
    });}
    void process()throws Exception{f.work(()->assertFalse(gate.process(f.c,new AtomicBoolean(),Long.MAX_VALUE)));}
    MilepostPresentation.Snapshot read(long acknowledged)throws Exception{
        MilepostPresentation.Snapshot[] s={null};f.work(()->s[0]=gate.read(f.c,f.ledger,acknowledged,()->false));return s[0];
    }
    @Test public void productionRequiresCorrectPackageAndPinnedCatalogueDespitePreferences()throws Exception{
        f.c.getSharedPreferences("roadprints_mileposts",0).edit().putBoolean("enabled",false).commit();
        assertTrue(MilepostIntegration.PRODUCTION.enabled);
        f.work(()->assertFalse(MilepostIntegration.PRODUCTION.verified(f.ledger)));
        android.content.Context sandbox=new android.content.ContextWrapper(f.c){
            @Override public String getPackageName(){return "com.roadprints.capture.milepostacceptance";}
        };
        assertFalse(MilepostIntegration.PRODUCTION.allowed(sandbox));
        assertEquals(JobScheduler.RESULT_FAILURE,MilepostReconciliationJob.schedule(sandbox));
        f.work(()->assertFalse(MilepostIntegration.PRODUCTION.process(sandbox,new AtomicBoolean(),Long.MAX_VALUE)));
        assertFalse(f.c.getDatabasePath(MilepostReconciliationJob.DATABASE).exists());
    }
    @Test public void validatedArchiveThroughAllSevenAwardsSummaryAndPersistentReplay()throws Exception{
        prepare(7,100);String original=f.c.getSharedPreferences("roadprints_achievements_v1",0).getAll().toString();process();
        MilepostPresentation.Snapshot s=read(0);assertNotNull(s);assertEquals(7,s.count);assertEquals(6,s.individuals());assertTrue(s.awards.get(6).earned);
        for(int i=0;i<4;i++)assertTrue(s.awards.get(i).earned);assertEquals(7,s.target());assertEquals(7,s.newPosts);
        Activity a=Robolectric.buildActivity(Activity.class).setup().get();MilepostPresentationStore store=new MilepostPresentationStore(a,gate.user);
        MilepostPresentationStore.State staticState=store.read();staticState.staticMode=true;store.save(staticState);MilepostPresentationView view=new MilepostPresentationView(a,store,s,(after,done)->{});a.setContentView(view);view.onHostResume();
        assertNull(view.findViewWithTag("milepost_summary_replay"));assertNotNull(view.findViewWithTag("milepost_replay"));view.replay(true);for(int i=0;i<7;i++)view.next();
        assertEquals(7,store.read().cursor);view.skip();assertEquals(s.postOrdinal,store.read().acknowledgedPosts);
        view.onHostPause();MilepostPresentationView restarted=new MilepostPresentationView(a,store,s,(after,done)->{});a.setContentView(restarted);restarted.onHostResume();
        assertTrue(store.read().paused);assertEquals(7,store.read().completedStage);restarted.replay(true);restarted.next();assertEquals(1,store.read().cursor);
        assertEquals(original,f.c.getSharedPreferences("roadprints_achievements_v1",0).getAll().toString());assertEquals(0,read(s.postOrdinal).newPosts);
    }
    @Test public void sixPhysicalPostsDoNotCompleteHarrowAndUnknownsDoNotRevealDesigners()throws Exception{
        prepare(6,0);process();MilepostPresentation.Snapshot s=read(0);assertEquals(6,s.count);assertFalse(s.awards.get(5).earned);assertFalse(s.awards.get(6).earned);
    }
    @Test public void offlineRunDiscoversTwoRealPostsWithoutNcnOrDuplicateRecognition()throws Exception{
        f.work(()->{
            f.c.getSharedPreferences("roadprints_ncn_national_v2",0).edit().putBoolean("enabled",false).commit();
            MilepostAchievementsTest.setNcn(null);
            List<MilepostDiscoveryLedger.Marker> markers=MilepostCatalogue.load(f.c);
            List<MilepostDiscoveryLedger.Marker> pair=new ArrayList<>();
            for(MilepostDiscoveryLedger.Marker marker:markers)
                if(marker.id.endsWith("-11250895145")||marker.id.endsWith("-6924174900"))pair.add(marker);
            assertEquals(2,pair.size());
            f.install(1,pair);
            gate=isolated(MilepostAchievements.scalar(f.ledger.db(),"SELECT fingerprint FROM catalogue"));
            JourneyStore.save(f.c,realRoute(pair,"lunchtime-run","running",0));
            JourneyStore.save(f.c,realRoute(pair,"outside-100-metres","running",.002));
            JourneyStore.save(f.c,realRoute(pair,"vehicle","driving",0));
        });
        process();
        MilepostPresentation.Snapshot first=read(0);
        assertNotNull(first);assertEquals(2,first.count);assertEquals(2,first.newPosts);
        f.work(()->{
            assertEquals(2,f.ledger.proofCount(gate.user));
            assertEquals(MilepostDiscoveryLedger.State.DISCOVERED,f.ledger.state(gate.user,"lunchtime-run"));
            assertEquals(MilepostDiscoveryLedger.State.ASSESSED_NO_DISCOVERY,f.ledger.state(gate.user,"outside-100-metres"));
            assertEquals(MilepostDiscoveryLedger.State.PENDING_EVIDENCE,f.ledger.state(gate.user,"vehicle"));
            f.ledger.close();f.ledger=new MilepostDiscoveryLedger(f.c,f.name);
            JourneyStore.save(f.c,realRoute(MilepostCatalogue.load(f.c).stream()
                    .filter(m->m.id.endsWith("-11250895145")||m.id.endsWith("-6924174900"))
                    .collect(java.util.stream.Collectors.toList()),"repeat","running",0));
        });
        process();
        assertEquals(2,read(first.postOrdinal).count);assertEquals(0,read(first.postOrdinal).newPosts);
    }
    private static org.json.JSONObject realRoute(List<MilepostDiscoveryLedger.Marker> pair,String id,String mode,double east)throws Exception{
        MilepostDiscoveryLedger.Marker a=pair.get(0),b=pair.get(1);
        org.json.JSONObject row=MilepostJourneyEvidenceTest.journey(mode).put("journey_id",id)
                .put("capture_intent","automatic").put("processing_status","pending");
        row.remove("processing_result");
        long start=java.time.Instant.parse(row.getString("started_at")).toEpochMilli();
        org.json.JSONArray samples=new org.json.JSONArray(),coordinates=new org.json.JSONArray();
        for(int i=0;i<=96;i++){
            double lon=a.lon+(b.lon-a.lon)*i/96+east,lat=a.lat+(b.lat-a.lat)*i/96;
            coordinates.put(new org.json.JSONArray().put(lon).put(lat));
            samples.put(new org.json.JSONArray().put(lon).put(lat).put(3).put(start+i*1000).put(2.4));
        }
        return row.put("capture_route_samples",samples).put("route_geometry",
                new org.json.JSONObject().put("type","LineString").put("coordinates",coordinates));
    }
    @Test public void missingUnverifiedAndIncompatibleCatalogueFailClosed()throws Exception{
        gate=isolated("0".repeat(64));assertNull(read(0));process();
        prepare(7,100);String pin[]=new String[1];f.work(()->pin[0]=MilepostAchievements.scalar(f.ledger.db(),"SELECT fingerprint FROM catalogue"));
        gate=isolated(pin[0],"unsupported-evaluator");process();assertNull(read(0));
        gate=isolated("0".repeat(64));process();assertNull(read(0));
        f.work(()->assertEquals(0,MilepostAchievements.number(f.ledger.db(),"SELECT COUNT(*) FROM achievement_snapshot")));
    }
    @Test public void unapprovedPostsAndIncompleteEvidenceCannotBecomeDiscoveries()throws Exception{
        f.work(()->{f.install(1,Collections.singletonList(f.marker("candidate","mills",true,false)));f.save("a");MilepostAchievementsTest.setNcn(f.ncn(0));gate=isolated(MilepostAchievements.scalar(f.ledger.db(),"SELECT fingerprint FROM catalogue"));});
        process();MilepostPresentation.Snapshot s=read(0);assertEquals(0,s.count);assertEquals(0,s.individuals());assertFalse(s.introduced());assertTrue(s.incomplete>0);
    }
    @Test public void staleReceiptDeletionAndReinstatementNeverDuplicateHistory()throws Exception{
        prepare(7,100);process();MilepostPresentation.Snapshot first=read(0);
        f.work(()->{f.save("later-batch");});assertNull(read(0));
        f.work(()->{MilepostAchievementsTest.setNcn(f.ncn(100));});process();assertEquals(0,read(first.postOrdinal).newPosts);
        f.work(()->{JourneyStore.delete(f.c,"historical-a");JourneyStore.delete(f.c,"later-batch");MilepostAchievementsTest.setNcn(f.ncn(0));});process();
        assertEquals(0,read(first.postOrdinal).count);assertFalse(read(0).awards.get(0).earned);
        f.work(()->{f.save("historical-a");MilepostAchievementsTest.setNcn(f.ncn(100));});process();
        MilepostPresentation.Snapshot restored=read(first.postOrdinal);assertEquals(7,restored.count);assertEquals(0,restored.newPosts);assertEquals(first.awards.get(0).ordinal,restored.awards.get(0).ordinal);
    }
    @Test public void processingRemainsSingleWriterAndCancellationReleasesLease()throws Exception{
        prepare(7,100);assertTrue(gate.enterProcessing());
        try{f.work(()->assertTrue(gate.process(f.c,new AtomicBoolean(),Long.MAX_VALUE)));}
        finally{gate.leaveProcessing();}
        f.work(()->assertThrows(java.util.concurrent.CancellationException.class,()->gate.process(f.c,new AtomicBoolean(true),Long.MAX_VALUE)));
        process();assertNotNull(read(0));
    }
    @Test public void withdrawnReferenceAtCommitRollsBackAllAchievementRows()throws Exception{
        prepare(7,100);f.work(()->{
            try(MilepostAchievementDeriver worker=new MilepostAchievementDeriver(f.c,f.ledger,gate.user,()->false)){
                assertThrows(java.util.ConcurrentModificationException.class,()->{while(worker.runSlice(()->false,1000)){};});
            }
            assertEquals(0,MilepostAchievements.number(f.ledger.db(),"SELECT COUNT(*) FROM achievement_snapshot"));
            assertEquals(0,MilepostAchievements.number(f.ledger.db(),"SELECT COUNT(*) FROM achievement_state"));
        });process();assertNotNull(read(0));
    }
    @Test public void captureStartAndFreshnessChangeBlockPresentationTransitions()throws Exception{
        prepare(7,100);process();MilepostPresentation.Snapshot s=read(0);Activity a=Robolectric.buildActivity(Activity.class).setup().get();
        MilepostPresentationStore store=new MilepostPresentationStore(a,gate.user);MilepostPresentationStore.State staticState=store.read();staticState.staticMode=true;store.save(staticState);MilepostPresentationView view=new MilepostPresentationView(a,store,s,(after,done)->{});a.setContentView(view);view.onHostResume();view.replay(true);view.next();
        AtomicBoolean fresh=new AtomicBoolean(true);view.freshness=fresh::get;fresh.set(false);view.next();view.skip();assertEquals(1,store.read().cursor);assertEquals(0,store.read().acknowledgedPosts);
        fresh.set(true);view.replay(false);a.getSharedPreferences("roadprints_capture_state",0).edit().putBoolean("active",true).commit();Shadows.shadowOf(Looper.getMainLooper()).idle();view.next();assertEquals(1,store.read().cursor);assertTrue(store.read().paused);
        f.work(()->assertThrows(java.util.concurrent.CancellationException.class,()->gate.process(f.c,new AtomicBoolean(),Long.MAX_VALUE)));
        a.getSharedPreferences("roadprints_capture_state",0).edit().putBoolean("active",false).commit();
    }
    @Test public void integratedScreenMountResumePauseAndHistoryUseExistingView()throws Exception{
        prepare(7,100);process();Activity a=Robolectric.buildActivity(Activity.class).setup().get();LinearLayout section=new LinearLayout(a);a.setContentView(section);
        MilepostScreenIntegration screen=new MilepostScreenIntegration(a,gate);screen.mount(section);screen.resume();drain();
        assertEquals(1,section.getChildCount());assertTrue(section.getChildAt(0) instanceof MilepostPresentationView);assertTrue(gate.enterProcessing());gate.leaveProcessing();
        // A normal periodic compact refresh must not discard an in-flight history page callback.
        section.getChildAt(0).findViewWithTag("milepost_browse_discoveries").performClick();screen.refresh();drain();
        assertEquals(7,new MilepostPresentationStore(a,gate.user).read().acknowledgedPosts);
        screen.pause();drain();assertEquals(0,section.getChildCount());assertTrue(gate.enterProcessing());gate.leaveProcessing();screen.resume();drain();assertEquals(1,section.getChildCount());screen.pause();drain();
    }
    @Test public void integratedRepeatedEntryDismissalAndReplayMeasureHeap()throws Exception{
        prepare(7,100);process();Activity a=Robolectric.buildActivity(Activity.class).setup().get();
        LinearLayout section=new LinearLayout(a);a.setContentView(section);MilepostScreenIntegration screen=new MilepostScreenIntegration(a,gate);screen.mount(section);
        MilepostPresentationStore presentation=new MilepostPresentationStore(a,gate.user);MilepostPresentationStore.State staticReplay=presentation.read();staticReplay.staticMode=true;presentation.save(staticReplay);
        for(int i=0;i<2;i++)cycle(screen,section);
        System.gc();Thread.sleep(50);long before=MilepostPresentationTest.heap();java.util.concurrent.atomic.AtomicLong peak=new java.util.concurrent.atomic.AtomicLong(before);
        AtomicBoolean stop=new AtomicBoolean();Thread sampler=new Thread(()->{while(!stop.get()){peak.accumulateAndGet(MilepostPresentationTest.heap(),Math::max);try{Thread.sleep(2);}catch(InterruptedException e){return;}}});sampler.start();
        long started=System.nanoTime();try{for(int i=0;i<3;i++)cycle(screen,section);}finally{stop.set(true);sampler.join();}
        long elapsed=(System.nanoTime()-started)/1000000;System.gc();Thread.sleep(50);long retained=MilepostPresentationTest.heap();
        System.out.println("MILEPOST_INTEGRATION_PROBE cycles=3 posts=7 source_journeys=1 before="+before+" peak="+peak.get()+" incremental_peak="+(peak.get()-before)+" retained="+retained+" retained_delta="+(retained-before)+" elapsed_ms="+elapsed+" overlap=false decoded_bitmaps=0");
        assertEquals(0,section.getChildCount());assertTrue("Integration peak exceeds 64MiB",peak.get()-before<64L*1024*1024);assertTrue("Integration retained growth exceeds 8MiB",retained-before<8L*1024*1024);
    }
    void cycle(MilepostScreenIntegration screen,LinearLayout section)throws Exception{
        process();screen.resume();drain();assertEquals(1,section.getChildCount());MilepostPresentationView view=(MilepostPresentationView)section.getChildAt(0);
        view.replay(true);for(int n=0;n<7;n++)view.next();view.later();screen.pause();drain();
    }
    static void drain()throws Exception{ScreenDataLoader.submit(()->{}).get();Shadows.shadowOf(Looper.getMainLooper()).idle();}
}
