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
    private Run current;

    static void archiveChanged(Context context) {
        Context app=context.getApplicationContext();
        if(!MilepostIntegration.PRODUCTION.allowed(app)||!REQUESTED.compareAndSet(false,true))return;
        new android.os.Handler(app.getMainLooper()).post(()->{
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
        return scheduler.schedule(new JobInfo.Builder(JOB_ID,new ComponentName(context,MilepostReconciliationJob.class))
                .setOverrideDeadline(5_000)
                .build());
    }
    @Override public boolean onStartJob(JobParameters parameters) {
        if(!MilepostIntegration.PRODUCTION.allowed(this))return false;
        if(current!=null)return false;
        Run run=new Run();current=run;
        run.task=WORKER.submit(()->{
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
            boolean retry=true;
            long archive=JourneyStore.dataRevision(this);
            long ncn=NcnProgressStore.revision(this);
            try {
                retry=MilepostIntegration.PRODUCTION.process(this,run.cancelled,android.os.SystemClock.elapsedRealtime()+SLICE_MS);
            }catch(java.util.concurrent.CancellationException|java.util.ConcurrentModificationException interrupted){
                // Retain checkpoints and request an OS retry after backoff.
            }catch(Exception error){android.util.Log.w("Roadprints","Milepost reconciliation needs retry",error);}
            finally {
                boolean needsRetry=retry;
                new android.os.Handler(getMainLooper()).post(()->{
                    synchronized(MilepostReconciliationJob.class) {
                        if(run.stopped.get())return;
                        current=null;
                        jobFinished(parameters,needsRetry||archive!=JourneyStore.dataRevision(this)
                                ||ncn!=NcnProgressStore.revision(this));
                    }
                });
            }
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
