package com.roadprints.capture;

import android.content.Context;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28,manifest=Config.NONE,packageName="com.roadprints.capture.milepostacceptance")
public class MilepostAcceptanceHarnessTest {
    Context c;
    interface Work {void run()throws Exception;}
    void work(Work task)throws Exception {
        java.util.concurrent.ExecutorService worker=java.util.concurrent.Executors.newSingleThreadExecutor();
        try{worker.submit(()->{try{task.run();}catch(Exception e){throw new RuntimeException(e);}}).get();}
        finally{worker.shutdownNow();}
    }
    @Before public void setup(){c=RuntimeEnvironment.getApplication();}
    @Test public void onePhysicalPostAndDesigner()throws Exception{work(()->MilepostAcceptanceChecks.onePostAndDesigner(c));}
    @Test public void sixPostsRemainAnonymous()throws Exception{work(()->MilepostAcceptanceChecks.sixPostsRemainMystery(c));}
    @Test public void sevenPostsAllAwardsAndDuplicateSummary()throws Exception{work(()->MilepostAcceptanceChecks.allAwardsAndDuplicates(c));}
    @Test public void correctionFallbackWithdrawalAndReopen()throws Exception{work(()->MilepostAcceptanceChecks.correctionFallbackAndRestart(c));}
    @Test public void interruptedWorkerAndSimulatedCaptureState()throws Exception{work(()->MilepostAcceptanceChecks.interruptionAndCaptureState(c));}
    @Test public void wrongContextRejectedBeforeAnyFixtureWrite()throws Exception{
        android.content.ContextWrapper wrong=new android.content.ContextWrapper(c){@Override public String getPackageName(){return "com.roadprints.capture";}@Override public Context getApplicationContext(){return this;}};
        Assert.assertThrows(SecurityException.class,()->new MilepostAcceptanceFixture(wrong));
    }
}
