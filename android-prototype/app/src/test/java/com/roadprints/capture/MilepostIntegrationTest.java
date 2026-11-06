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
    MilepostIntegration isolated(String fingerprint)throws Exception{
        java.lang.reflect.Constructor<MilepostIntegration> ctor=MilepostIntegration.class.getDeclaredConstructor(boolean.class,String.class,String.class,int.class,String.class,String.class);
        ctor.setAccessible(true);return ctor.newInstance(true,f.name,MilepostAchievementsTest.USER,1,MilepostDiscoveryLedgerTest.HASH,fingerprint);
    }
    void prepare(int posts,double ncn)throws Exception{f.work(()->{
        f.posts(posts);f.save("historical-a");MilepostAchievementsTest.setNcn(f.ncn(ncn));
        gate=isolated(MilepostAchievements.scalar(f.ledger.db(),"SELECT fingerprint FROM catalogue"));
    });}
    void process()throws Exception{f.work(()->assertFalse(gate.process(f.c,new AtomicBoolean(),Long.MAX_VALUE)));}
    MilepostPresentation.Snapshot read(long acknowledged)throws Exception{
        MilepostPresentation.Snapshot[] s={null};f.work(()->s[0]=gate.read(f.c,f.ledger,acknowledged,()->false));return s[0];
    }
    @Test public void productionDisabledDespiteImportedPreferencesAndStaleJob()throws Exception{
        f.c.getSharedPreferences("roadprints_mileposts",0).edit().putBoolean("enabled",true).commit();
        JobScheduler scheduler=f.c.getSystemService(JobScheduler.class);scheduler.cancelAll();
        assertFalse(MilepostIntegration.PRODUCTION.enabled);
        assertFalse(MilepostIntegration.PRODUCTION.enterPresentation());assertFalse(MilepostIntegration.PRODUCTION.enterProcessing());
        assertEquals(JobScheduler.RESULT_FAILURE,MilepostReconciliationJob.schedule(f.c));assertNull(scheduler.getPendingJob(MilepostReconciliationJob.JOB_ID));
        assertFalse(Robolectric.buildService(MilepostReconciliationJob.class).create().get().onStartJob(null));
        f.work(()->assertFalse(MilepostIntegration.PRODUCTION.process(f.c,new AtomicBoolean(),Long.MAX_VALUE)));
        Activity a=Robolectric.buildActivity(Activity.class).setup().get();LinearLayout section=new LinearLayout(a);
        MilepostScreenIntegration screen=new MilepostScreenIntegration(a,MilepostIntegration.PRODUCTION);screen.mount(section);screen.resume();screen.refresh();screen.pause();
        assertEquals(0,section.getChildCount());assertFalse(f.c.getDatabasePath(MilepostReconciliationJob.DATABASE).exists());
    }
    @Test public void validatedArchiveThroughAllSevenAwardsSummaryAndPersistentReplay()throws Exception{
        prepare(7,100);String original=f.c.getSharedPreferences("roadprints_achievements_v1",0).getAll().toString();process();
        MilepostPresentation.Snapshot s=read(0);assertNotNull(s);assertEquals(7,s.count);assertEquals(6,s.individuals());assertTrue(s.awards.get(6).earned);
        for(int i=0;i<4;i++)assertTrue(s.awards.get(i).earned);assertEquals(7,s.target());assertEquals(7,s.newPosts);
        Activity a=Robolectric.buildActivity(Activity.class).setup().get();MilepostPresentationStore store=new MilepostPresentationStore(a,gate.user);
        MilepostPresentationView view=new MilepostPresentationView(a,store,s,(after,done)->{});a.setContentView(view);view.onHostResume();
        assertNotNull(view.findViewWithTag("milepost_summary_replay"));view.replay(true);for(int i=0;i<7;i++)view.next();
        assertEquals(7,store.read().cursor);view.skip();assertEquals(s.postOrdinal,store.read().acknowledgedPosts);
        view.onHostPause();MilepostPresentationView restarted=new MilepostPresentationView(a,store,s,(after,done)->{});a.setContentView(restarted);restarted.onHostResume();
        assertTrue(store.read().paused);assertEquals(7,store.read().completedStage);restarted.replay(true);restarted.next();assertEquals(1,store.read().cursor);
        assertEquals(original,f.c.getSharedPreferences("roadprints_achievements_v1",0).getAll().toString());assertEquals(0,read(s.postOrdinal).newPosts);
    }
    @Test public void sixPhysicalPostsDoNotCompleteHarrowAndUnknownsDoNotRevealDesigners()throws Exception{
        prepare(6,0);process();MilepostPresentation.Snapshot s=read(0);assertEquals(6,s.count);assertFalse(s.awards.get(5).earned);assertFalse(s.awards.get(6).earned);
    }
    @Test public void missingUnverifiedAndIncompatibleCatalogueFailClosed()throws Exception{
        gate=isolated("0".repeat(64));assertNull(read(0));process();
        prepare(7,100);gate=isolated("0".repeat(64));process();assertNull(read(0));
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
    @Test public void presentationAndProcessingAreMutuallyExclusiveAndCancellationReleasesLease()throws Exception{
        prepare(7,100);assertTrue(gate.enterPresentation());assertFalse(gate.enterProcessing());
        f.work(()->assertTrue(gate.process(f.c,new AtomicBoolean(),Long.MAX_VALUE)));gate.leavePresentation();
        assertTrue(gate.enterProcessing());assertFalse(gate.enterPresentation());gate.leaveProcessing();
        f.work(()->assertThrows(java.util.concurrent.CancellationException.class,()->gate.process(f.c,new AtomicBoolean(true),Long.MAX_VALUE)));
        assertTrue(gate.enterPresentation());gate.leavePresentation();process();assertNotNull(read(0));
    }
    @Test public void captureStartAndFreshnessChangeBlockPresentationTransitions()throws Exception{
        prepare(7,100);process();MilepostPresentation.Snapshot s=read(0);Activity a=Robolectric.buildActivity(Activity.class).setup().get();
        MilepostPresentationStore store=new MilepostPresentationStore(a,gate.user);MilepostPresentationView view=new MilepostPresentationView(a,store,s,(after,done)->{});a.setContentView(view);view.onHostResume();view.replay(true);view.next();
        AtomicBoolean fresh=new AtomicBoolean(true);view.freshness=fresh::get;fresh.set(false);view.next();view.skip();assertEquals(1,store.read().cursor);assertEquals(0,store.read().acknowledgedPosts);
        fresh.set(true);view.replay(false);a.getSharedPreferences("roadprints_capture_state",0).edit().putBoolean("active",true).commit();Shadows.shadowOf(Looper.getMainLooper()).idle();view.next();assertEquals(1,store.read().cursor);assertTrue(store.read().paused);
        f.work(()->assertThrows(java.util.concurrent.CancellationException.class,()->gate.process(f.c,new AtomicBoolean(),Long.MAX_VALUE)));
        a.getSharedPreferences("roadprints_capture_state",0).edit().putBoolean("active",false).commit();
    }
    @Test public void integratedScreenMountResumePauseAndHistoryUseExistingView()throws Exception{
        prepare(7,100);process();Activity a=Robolectric.buildActivity(Activity.class).setup().get();LinearLayout section=new LinearLayout(a);a.setContentView(section);
        MilepostScreenIntegration screen=new MilepostScreenIntegration(a,gate);screen.mount(section);screen.resume();drain();
        assertEquals(1,section.getChildCount());assertTrue(section.getChildAt(0) instanceof MilepostPresentationView);assertFalse(gate.enterProcessing());
        screen.pause();drain();assertEquals(0,section.getChildCount());assertTrue(gate.enterProcessing());gate.leaveProcessing();screen.resume();drain();assertEquals(1,section.getChildCount());screen.pause();drain();
    }
    static void drain()throws Exception{ScreenDataLoader.submit(()->{}).get();Shadows.shadowOf(Looper.getMainLooper()).idle();}
}
