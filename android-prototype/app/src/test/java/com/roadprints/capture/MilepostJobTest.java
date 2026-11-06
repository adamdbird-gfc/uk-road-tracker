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
    @Test public void explicitScheduleHasIdleChargingConstraintsAndNoPeriodicService() {
        Context c=RuntimeEnvironment.getApplication();JobScheduler scheduler=c.getSystemService(JobScheduler.class);scheduler.cancelAll();
        assertNull(scheduler.getPendingJob(MilepostReconciliationJob.JOB_ID));
        assertEquals(JobScheduler.RESULT_SUCCESS,MilepostReconciliationJob.schedule(c));
        JobInfo job=scheduler.getPendingJob(MilepostReconciliationJob.JOB_ID);
        assertTrue(job.isRequireCharging());assertTrue(job.isRequireDeviceIdle());assertFalse(job.isPeriodic());
        assertFalse(job.isPersisted());scheduler.cancelAll();
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
