package com.roadprints.capture;

import android.app.job.*;
import android.content.Context;
import android.os.Looper;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28,manifest=Config.NONE)
public class MilepostJobTest {
    @Test public void productionReplacesIdleChargingJobAndSandboxCannotSchedule() {
        Context c=RuntimeEnvironment.getApplication();JobScheduler scheduler=c.getSystemService(JobScheduler.class);scheduler.cancelAll();
        assertNull(scheduler.getPendingJob(MilepostReconciliationJob.JOB_ID));
        scheduler.schedule(new JobInfo.Builder(MilepostReconciliationJob.JOB_ID,
                new android.content.ComponentName(c,MilepostReconciliationJob.class))
                .setRequiresCharging(true).setRequiresDeviceIdle(true).build());
        assertEquals(JobScheduler.RESULT_SUCCESS,MilepostReconciliationJob.schedule(c));
        JobInfo job=scheduler.getPendingJob(MilepostReconciliationJob.JOB_ID);assertNotNull(job);
        assertFalse(job.isRequireCharging());assertFalse(job.isRequireDeviceIdle());assertEquals(0,job.getNetworkType());
        Context sandbox=new android.content.ContextWrapper(c){@Override public String getPackageName(){return "com.roadprints.capture.milepostacceptance";}};
        scheduler.cancelAll();assertEquals(JobScheduler.RESULT_FAILURE,MilepostReconciliationJob.schedule(sandbox));
        assertNull(scheduler.getPendingJob(MilepostReconciliationJob.JOB_ID));
    }
    @Test public void liveCaptureYieldsAndStartIsLatchedAcrossStop() {
        Context c=RuntimeEnvironment.getApplication();
        android.content.SharedPreferences state=c.getSharedPreferences("roadprints_capture_state",0);
        state.edit().putBoolean("active",false).commit();
        try(MilepostReconciliationJob.CaptureGuard guard=new MilepostReconciliationJob.CaptureGuard(c,new AtomicBoolean(),Long.MAX_VALUE)) {
            assertFalse(guard.getAsBoolean());state.edit().putBoolean("active",true).commit();
            assertTrue(CaptureService.isActive(c));assertTrue(guard.getAsBoolean());Shadows.shadowOf(Looper.getMainLooper()).idle();
            state.edit().putBoolean("active",false).commit();Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertTrue(guard.getAsBoolean());
        } finally {state.edit().putBoolean("active",false).commit();}
    }
    @Test public void deadlineAndOsCancellationYieldWithoutStartingCapture() {
        Context c=RuntimeEnvironment.getApplication();AtomicBoolean stopped=new AtomicBoolean();
        try(MilepostReconciliationJob.CaptureGuard guard=new MilepostReconciliationJob.CaptureGuard(c,stopped,Long.MAX_VALUE)) {
            stopped.set(true);assertTrue(guard.getAsBoolean());
        }
        try(MilepostReconciliationJob.CaptureGuard guard=new MilepostReconciliationJob.CaptureGuard(c,new AtomicBoolean(),0)) {
            assertTrue(guard.getAsBoolean());
        }
    }
}
