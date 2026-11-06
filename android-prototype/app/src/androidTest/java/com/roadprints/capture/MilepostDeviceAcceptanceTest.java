package com.roadprints.capture;

import android.content.*;
import android.os.SystemClock;
import android.view.*;
import android.widget.Button;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.*;
import org.junit.runner.RunWith;
import java.util.concurrent.*;
import static org.junit.Assert.*;

/** ART tests. A passing simulator run is not physical GPS/TalkBack acceptance. */
@RunWith(AndroidJUnit4.class)
public class MilepostDeviceAcceptanceTest {
    Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();
    void worker(Callable<Void> task)throws Exception {ScreenDataLoader.submit(()->{try{task.call();}catch(Exception e){throw new RuntimeException(e);}}).get(120,TimeUnit.SECONDS);}
    void seed()throws Exception{worker(()->{MilepostAcceptanceChecks.reset(c);try(MilepostAcceptanceFixture f=new MilepostAcceptanceFixture(c)){f.prepare(7);MilepostAcceptanceChecks.converge(f);}return null;});}
    @Test public void onePhysicalPostAndDesigner()throws Exception{worker(()->{MilepostAcceptanceChecks.onePostAndDesigner(c);return null;});}
    @Test public void sixPostsRemainAnonymous()throws Exception{worker(()->{MilepostAcceptanceChecks.sixPostsRemainMystery(c);return null;});}
    @Test public void allAwardsAndDuplicateSummary()throws Exception{worker(()->{MilepostAcceptanceChecks.allAwardsAndDuplicates(c);return null;});}
    @Test public void correctionFallbackWithdrawalAndReopen()throws Exception{worker(()->{MilepostAcceptanceChecks.correctionFallbackAndRestart(c);return null;});}
    @Test public void interruptedWorkerAndSimulatedCaptureState()throws Exception{worker(()->{MilepostAcceptanceChecks.interruptionAndCaptureState(c);return null;});}
    static MilepostPresentationView view(MilepostAcceptanceActivity a){assertEquals(1,a.section.getChildCount());return (MilepostPresentationView)a.section.getChildAt(0);}
    void ready(ActivityScenario<MilepostAcceptanceActivity> scenario)throws Exception{
        long deadline=SystemClock.elapsedRealtime()+15000;
        while(SystemClock.elapsedRealtime()<deadline){boolean[] found={false};scenario.onActivity(a->found[0]=a.section.getChildCount()==1);if(found[0])return;Thread.sleep(50);}
        fail("Integrated presentation not ready in 15 seconds (test timeout, not validated latency budget)");
    }
    @Test public void repeatedEntryReplayRecreationAndRuntimeProfile()throws Exception{
        seed();for(int cycle=0;cycle<10;cycle++){
            try(ActivityScenario<MilepostAcceptanceActivity> scenario=ActivityScenario.launch(MilepostAcceptanceActivity.class)){
                ready(scenario);scenario.onActivity(a->{MilepostPresentationView v=view(a);v.replay(true);for(int n=0;n<7;n++)v.next();assertEquals(7,new MilepostPresentationStore(a,MilepostAcceptanceFixture.USER).read().cursor);v.later();});
                scenario.recreate();ready(scenario);scenario.onActivity(a->{assertTrue(new MilepostPresentationStore(a,MilepostAcceptanceFixture.USER).read().paused);assertEquals(7,new MilepostPresentationStore(a,MilepostAcceptanceFixture.USER).read().cursor);});
            }
        }
        worker(()->{MilepostAcceptanceChecks.productionIsolated(c);return null;});
    }
    @Test public void replayCapturePauseAccessibilityStructureAndFocus()throws Exception{
        seed();try(ActivityScenario<MilepostAcceptanceActivity> scenario=ActivityScenario.launch(MilepostAcceptanceActivity.class)){
            ready(scenario);scenario.onActivity(a->{MilepostPresentationView v=view(a);v.replay(true);v.next();
                View cipher=v.findViewWithTag("milepost_replay_cipher");assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO,cipher.getImportantForAccessibility());
                View next=v.findViewWithTag("milepost_next");assertTrue(next.requestFocus());assertTrue(next.isFocusable());
                inspectTargets(v,a.getResources().getDisplayMetrics().density);
                a.getSharedPreferences("roadprints_capture_state",0).edit().putBoolean("active",true).commit();
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(a->{MilepostPresentationStore.State state=new MilepostPresentationStore(a,MilepostAcceptanceFixture.USER).read();assertTrue(state.paused);assertEquals(1,state.cursor);
                view(a).next();assertEquals(1,new MilepostPresentationStore(a,MilepostAcceptanceFixture.USER).read().cursor);
                a.getSharedPreferences("roadprints_capture_state",0).edit().putBoolean("active",false).commit();});
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED);scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED);ready(scenario);
        }finally{c.getSharedPreferences("roadprints_capture_state",0).edit().putBoolean("active",false).commit();}
    }
    static void inspectTargets(View view,float density){
        if(view instanceof Button)assertTrue("Button minimum 48dp",view.getMinimumHeight()>=Math.round(48*density));
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)inspectTargets(group.getChildAt(i),density);}
    }
    @Test public void manualAcceptanceWindow()throws Exception {
        int seconds=Math.max(1,Math.min(600,Integer.parseInt(InstrumentationRegistry.getArguments().getString("manualSeconds","1"))));
        if(!"false".equals(InstrumentationRegistry.getArguments().getString("manualSeed","true")))seed();
        try(ActivityScenario<MilepostAcceptanceActivity> scenario=ActivityScenario.launch(MilepostAcceptanceActivity.class)){
            ready(scenario);Thread.sleep(seconds*1000L); // Bounded tester interaction window, not an acceptance assertion.
        }
    }
    // Two separate instrumentation invocations with an intervening force-stop prove process persistence.
    @Test public void processRestartReceipt()throws Exception {
        String phase=InstrumentationRegistry.getArguments().getString("restartPhase","prepare");
        if("prepare".equals(phase))seed();else assertEquals("verify",phase);
        try(ActivityScenario<MilepostAcceptanceActivity> scenario=ActivityScenario.launch(MilepostAcceptanceActivity.class)){
            ready(scenario);scenario.onActivity(a->{
                if("prepare".equals(phase)){view(a).replay(true);view(a).next();view(a).next();view(a).later();}
                MilepostPresentationStore.State state=new MilepostPresentationStore(a,MilepostAcceptanceFixture.USER).read();
                assertEquals(2,state.cursor);assertTrue(state.paused);
            });
        }
    }
}
