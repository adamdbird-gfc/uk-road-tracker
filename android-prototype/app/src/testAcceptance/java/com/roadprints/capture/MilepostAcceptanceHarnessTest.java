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
    @Before public void setup(){c=RuntimeEnvironment.getApplication();}
    @Test public void onePhysicalPostAndDesigner()throws Exception{MilepostAcceptanceChecks.onePostAndDesigner(c);}
    @Test public void sevenPostsAllAwardsAndDuplicateSummary()throws Exception{MilepostAcceptanceChecks.allAwardsAndDuplicates(c);}
    @Test public void correctionFallbackWithdrawalAndReopen()throws Exception{MilepostAcceptanceChecks.correctionFallbackAndRestart(c);}
    @Test public void interruptedWorkerAndSimulatedCaptureState()throws Exception{MilepostAcceptanceChecks.interruptionAndCaptureState(c);}
    @Test public void wrongContextRejectedBeforeAnyFixtureWrite()throws Exception{
        android.content.ContextWrapper wrong=new android.content.ContextWrapper(c){@Override public String getPackageName(){return "com.roadprints.capture";}@Override public Context getApplicationContext(){return this;}};
        Assert.assertThrows(SecurityException.class,()->new MilepostAcceptanceFixture(wrong));
    }
}
