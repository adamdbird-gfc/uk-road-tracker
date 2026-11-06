package com.roadprints.capture;

import android.app.job.*;
import android.content.*;
import android.os.Process;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Explicitly scheduled only: no lifecycle caller, boot receiver or live entitlement reader.
 * OS retries a requested run. No foreground/permanent background service. */
public final class MilepostReconciliationJob extends JobService {
    static final int JOB_ID=74104;
    static final String DATABASE="milepost-discoveries.db", USER="local";
    private Run current;
    private static final class Run {
        final AtomicBoolean cancelled=new AtomicBoolean(), stopped=new AtomicBoolean();
        final ExecutorService executor=Executors.newSingleThreadExecutor(r->new Thread(r,"roadprints-milepost-reconciliation"));
        Future<?> task;
    }
    static int schedule(Context context) {
        JobScheduler scheduler=context.getSystemService(JobScheduler.class);
        return scheduler.schedule(new JobInfo.Builder(JOB_ID,new ComponentName(context,MilepostReconciliationJob.class))
                .setRequiresCharging(true).setRequiresDeviceIdle(true)
                .setBackoffCriteria(30_000,JobInfo.BACKOFF_POLICY_EXPONENTIAL).build());
    }
    @Override public boolean onStartJob(JobParameters parameters) {
        if(current!=null&&current.task!=null&&!current.task.isDone())return false;
        Run run=new Run();current=run;
        run.task=run.executor.submit(()->{
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
            CaptureGuard guard=new CaptureGuard(this,run.cancelled,android.os.SystemClock.elapsedRealtime()+2000);
            boolean retry=true;
            try {
                java.util.function.BooleanSupplier yield=guard;
                MilepostArchiveReader.check(yield);
                try(MilepostDiscoveryLedger ledger=new MilepostDiscoveryLedger(this,DATABASE)) {
                    retry=new MilepostReconciler(this,ledger,USER).runSlice(yield,System.currentTimeMillis());
                }
            }catch(java.util.concurrent.CancellationException|java.util.ConcurrentModificationException interrupted){
                // Retain checkpoints and request an OS retry under idle/charging constraints.
            }catch(Exception error){android.util.Log.w("Roadprints","Inactive milepost reconciliation needs retry",error);}
            finally {
                guard.close();
                if(!run.stopped.get())jobFinished(parameters,retry);
                run.executor.shutdown();
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
            run.executor.shutdownNow();
        }
        return true;
    }
}
