package com.roadprints.capture;

import android.app.job.*;
import android.content.*;
import android.os.Process;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Local reconciliation after archive writes, with durable checkpoints and capture priority. */
public final class MilepostReconciliationJob extends JobService {
    static final int JOB_ID=74104;
    static final String DATABASE="milepost-discoveries.db", USER="local";
    private static final ExecutorService WORKER=Executors.newSingleThreadExecutor();
    private static final long SLICE_MS=15_000;
    private static final AtomicBoolean REQUESTED=new AtomicBoolean();
    private static final AtomicBoolean FOREGROUND_QUEUED=new AtomicBoolean();
    private static final android.os.Handler MAIN=new android.os.Handler(android.os.Looper.getMainLooper());
    private static volatile String status="Waiting to check your saved journeys.";
    private static volatile long resultRevision;
    private Run current;

    static String status(Context context) {
        return CaptureService.isActive(context)?"Checking for Mileposts will resume after your journey.":status;
    }
    static long resultRevision() {return resultRevision;}

    /** Visible entrypoints can make progress even when the OS defers scheduled jobs. */
    static void assessWhileOpen(Context context) {
        Context app=context.getApplicationContext();
        if(!MilepostIntegration.PRODUCTION.allowed(app)||CaptureService.isActive(app)
                ||!FOREGROUND_QUEUED.compareAndSet(false,true))return;
        WORKER.execute(()->{
            try {
                if(assess(app,new AtomicBoolean()))MAIN.post(()->schedule(app));
            } finally {FOREGROUND_QUEUED.set(false);}
        });
    }

    private static boolean assess(Context context,AtomicBoolean cancelled) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
        try {
            status="Looking for Mileposts in your saved journeys…";
            boolean retry=MilepostIntegration.PRODUCTION.process(context,cancelled,
                    android.os.SystemClock.elapsedRealtime()+SLICE_MS);
            status=retry?"Looking for Mileposts in your saved journeys…":"Updating your Milepost progress…";
            if(!retry)resultRevision++;
            return retry;
        } catch(CancellationException|java.util.ConcurrentModificationException interrupted) {
            status="Looking for Mileposts in your saved journeys…";
            return true;
        } catch(Exception error) {
            status="Couldn’t update Milepost progress. Roadprints will try again.";
            android.util.Log.w("Roadprints","Milepost reconciliation failed",error);
            return true;
        }
    }

    static void archiveChanged(Context context) {
        Context app=context.getApplicationContext();
        if(!MilepostIntegration.PRODUCTION.allowed(app)||!REQUESTED.compareAndSet(false,true))return;
        MAIN.post(()->{
            REQUESTED.set(false);
            if(!CaptureService.isActive(app))schedule(app);
        });
    }
    private static final class Run {
        final AtomicBoolean cancelled=new AtomicBoolean(), stopped=new AtomicBoolean();
        Future<?> task;
    }
    static synchronized int schedule(Context context) {
        if(!MilepostIntegration.PRODUCTION.allowed(context))return JobScheduler.RESULT_FAILURE;
        JobScheduler scheduler=context.getSystemService(JobScheduler.class);
        JobInfo pending=scheduler.getPendingJob(JOB_ID);
        if(pending!=null&&!pending.isRequireCharging()&&!pending.isRequireDeviceIdle())return JobScheduler.RESULT_SUCCESS;
        int result=scheduler.schedule(new JobInfo.Builder(JOB_ID,new ComponentName(context,MilepostReconciliationJob.class))
                .setMinimumLatency(1)
                .setBackoffCriteria(10_000,JobInfo.BACKOFF_POLICY_LINEAR)
                .build());
        if(result!=JobScheduler.RESULT_SUCCESS)status="Couldn’t check for Mileposts right now.";
        return result;
    }
    @Override public boolean onStartJob(JobParameters parameters) {
        if(!MilepostIntegration.PRODUCTION.allowed(this))return false;
        if(current!=null)return false;
        Run run=new Run();current=run;
        run.task=WORKER.submit(()->{
            long archive=JourneyStore.dataRevision(this);
            long ncn=NcnProgressStore.revision(this);
            boolean needsRetry=assess(this,run.cancelled);
            MAIN.post(()->{
                if(run.stopped.get())return;
                current=null;
                jobFinished(parameters,!CaptureService.isActive(this)&&(needsRetry
                        ||archive!=JourneyStore.dataRevision(this)||ncn!=NcnProgressStore.revision(this)));
            });
        });
        return true;
    }
    /** Latches capture starts even if capture finishes before the next evaluation poll. */
    static final class CaptureGuard implements java.util.function.BooleanSupplier,AutoCloseable {
        final SharedPreferences state;
        final AtomicBoolean cancelled;
        final long deadline;
        final SharedPreferences.OnSharedPreferenceChangeListener listener;
        CaptureGuard(Context context,AtomicBoolean cancelled,long deadline) {
            this.cancelled=cancelled;this.deadline=deadline;
            state=context.getSharedPreferences("roadprints_capture_state",MODE_PRIVATE);
            listener=(p,key)->{if(p.getBoolean("active",false))cancelled.set(true);};
            state.registerOnSharedPreferenceChangeListener(listener);
        }
        public boolean getAsBoolean(){return cancelled.get()||state.getBoolean("active",false)
                ||android.os.SystemClock.elapsedRealtime()>=deadline;}
        public void close(){state.unregisterOnSharedPreferenceChangeListener(listener);}
    }
    @Override public boolean onStopJob(JobParameters parameters) {
        Run run=current;current=null;
        if(run!=null) {
            run.stopped.set(true);run.cancelled.set(true);
            if(run.task!=null)run.task.cancel(true);
        }
        return true;
    }
}
