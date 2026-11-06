package com.roadprints.capture;

import android.app.job.*;
import android.content.*;
import android.os.Process;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Gated idle/charging work requested by the achievements host. No boot receiver or live entitlement writer.
 * OS retries a requested run. No foreground/permanent background service. */
public final class MilepostReconciliationJob extends JobService {
    static final int JOB_ID=74104;
    static final String DATABASE="milepost-discoveries.db", USER="local";
    private Run current;
    private static final class Run {
        final AtomicBoolean cancelled=new AtomicBoolean(), stopped=new AtomicBoolean();
        Future<?> task;
    }
    static int schedule(Context context) {
        if(!MilepostIntegration.PRODUCTION.allowed(context))return JobScheduler.RESULT_FAILURE;
        JobScheduler scheduler=context.getSystemService(JobScheduler.class);
        if(scheduler.getPendingJob(JOB_ID)!=null)return JobScheduler.RESULT_SUCCESS;
        return scheduler.schedule(new JobInfo.Builder(JOB_ID,new ComponentName(context,MilepostReconciliationJob.class))
                .setRequiresCharging(true).setRequiresDeviceIdle(true)
                .build());
    }
    @Override public boolean onStartJob(JobParameters parameters) {
        if(!MilepostIntegration.PRODUCTION.allowed(this))return false;
        if(current!=null&&current.task!=null&&!current.task.isDone())return false;
        Run run=new Run();current=run;
        run.task=ScreenDataLoader.submit(()->{
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
            boolean retry=true;
            try {
                retry=MilepostIntegration.PRODUCTION.process(this,run.cancelled,android.os.SystemClock.elapsedRealtime()+2000);
            }catch(java.util.concurrent.CancellationException|java.util.ConcurrentModificationException interrupted){
                // Retain checkpoints and request an OS retry under idle/charging constraints.
            }catch(Exception error){android.util.Log.w("Roadprints","Inactive milepost reconciliation needs retry",error);}
            finally {
                if(!run.stopped.get())jobFinished(parameters,retry);
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
        Run run=current;
        if(run!=null) {
            run.stopped.set(true);run.cancelled.set(true);
            if(run.task!=null)run.task.cancel(true);
        }
        return true;
    }
}
