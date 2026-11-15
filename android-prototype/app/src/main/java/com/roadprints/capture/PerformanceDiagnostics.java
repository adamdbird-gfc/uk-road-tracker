package com.roadprints.capture;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.content.Context;
import java.util.concurrent.*;

/** Bounded local timings and foreground stalls; no journey identifiers or coordinates. */
final class PerformanceDiagnostics {
    private static final String PREFS="roadprints_performance_diagnostics";
    private static final ScheduledExecutorService WORKER=Executors.newSingleThreadScheduledExecutor(task->{Thread t=new Thread(task,"roadprints-ui-diagnostics");t.setDaemon(true);return t;});
    private static volatile Context app;
    private static volatile int foreground;
    private static volatile long pendingAt;
    private static volatile boolean reported;
    private static boolean installed;
    static synchronized void install(Application application){
        if(installed)return;installed=true;app=application;
        Handler main=new Handler(Looper.getMainLooper());
        application.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks(){
            public void onActivityResumed(Activity a){foreground++;}
            public void onActivityPaused(Activity a){foreground=Math.max(0,foreground-1);pendingAt=0;}
            public void onActivityCreated(Activity a,Bundle b){} public void onActivityStarted(Activity a){}
            public void onActivityStopped(Activity a){} public void onActivitySaveInstanceState(Activity a,Bundle b){} public void onActivityDestroyed(Activity a){}
        });
        if (android.os.Build.VERSION.SDK_INT >= 30) WORKER.execute(() -> recordPreviousExit(application));
        WORKER.scheduleWithFixedDelay(()->{
            try {
                if(foreground==0){pendingAt=0;return;}
                long now=SystemClock.uptimeMillis();
                if(pendingAt==0){reported=false;pendingAt=now;main.post(()->pendingAt=0);}
                else if(!reported&&now-pendingAt>=2000){reported=true;
                    StringBuilder stack=new StringBuilder("foreground_ui_stall_ms=").append(now-pendingAt)
                            .append(" railway_worker_active=").append(RailwayTrialWorker.isChecking());
                    for(StackTraceElement frame:Looper.getMainLooper().getThread().getStackTrace()){
                        if(stack.length()>5000)break;stack.append('\n').append(frame);
                    }
                    append(stack.toString());
                }
            }catch(Exception ignored){/* Diagnostics cannot interrupt the app. */}
        },1,1,TimeUnit.SECONDS);
    }
    private static void recordPreviousExit(Application application) {
        try {
            var manager = application.getSystemService(android.app.ActivityManager.class);
            var exits = manager.getHistoricalProcessExitReasons(application.getPackageName(), 0, 1);
            if (exits.isEmpty()) return;
            var exit = exits.get(0);
            var preferences = application.getSharedPreferences(PREFS, 0);
            if (exit.getTimestamp() <= preferences.getLong("last_exit", 0)) return;
            String reason = switch (exit.getReason()) {
                case android.app.ApplicationExitInfo.REASON_ANR -> "anr";
                case android.app.ApplicationExitInfo.REASON_CRASH -> "java_crash";
                case android.app.ApplicationExitInfo.REASON_CRASH_NATIVE -> "native_crash";
                case android.app.ApplicationExitInfo.REASON_LOW_MEMORY -> "low_memory";
                default -> "other_" + exit.getReason();
            };
            append("previous_process_exit reason=" + reason + " at=" +
                    java.time.Instant.ofEpochMilli(exit.getTimestamp()) + " pss_kib=" + exit.getPss());
            preferences.edit().putLong("last_exit", exit.getTimestamp()).apply();
        } catch (Exception ignored) {
            // Exit history may be unavailable on a managed or test device.
        }
    }

    /** Sampled Java heap usage, including concurrent work in the app process. */
    static final class HeapSample {
        private long peak;
        HeapSample() { sample(); }
        void sample() {
            Runtime runtime = Runtime.getRuntime();
            peak = Math.max(peak, runtime.totalMemory() - runtime.freeMemory());
        }
        String description() {
            sample();
            return "sampled_heap_peak_mib=" + peak / (1024 * 1024)
                    + " heap_limit_mib=" + Runtime.getRuntime().maxMemory() / (1024 * 1024)
                    + " railway_worker_active=" + RailwayTrialWorker.isChecking();
        }
    }

    /** Process-wide occupancy and natural ART GC deltas for a single preparation phase. */
    static final class ArchiveHeapSample {
        private final long start = usedHeap();
        private final long gcStart = runtimeStat("art.gc.gc-count");
        private final long freedStart = runtimeStat("art.gc.bytes-freed");
        private long peak = start;

        void sample() { peak = Math.max(peak, usedHeap()); }

        String description() {
            long end = usedHeap();
            peak = Math.max(peak, end);
            long gcEnd = runtimeStat("art.gc.gc-count");
            long freedEnd = runtimeStat("art.gc.bytes-freed");
            return "sampled_heap_peak_mib=" + peak / 1_048_576
                    + " heap_limit_mib=" + Runtime.getRuntime().maxMemory() / 1_048_576
                    + " heap_start_kib=" + start / 1024 + " heap_end_kib=" + end / 1024
                    + " gc_runs=" + delta(gcStart, gcEnd, 1)
                    + " gc_freed_kib=" + delta(freedStart, freedEnd, 1024)
                    + " railway_worker_active=" + RailwayTrialWorker.isChecking();
        }

        private static long usedHeap() {
            Runtime runtime = Runtime.getRuntime();
            return runtime.totalMemory() - runtime.freeMemory();
        }

        private static long runtimeStat(String key) {
            String value = android.os.Debug.getRuntimeStat(key);
            try { return value == null ? -1 : Long.parseLong(value); }
            catch (NumberFormatException unavailable) { return -1; }
        }

        private static String delta(long start, long end, int unit) {
            return start < 0 || end < start ? "unavailable" : Long.toString((end - start) / unit);
        }
    }

    static void timing(String operation,long ms,String counts){
        String value=operation+" ms="+ms+(counts.isEmpty()?"":" "+counts);
        android.util.Log.i("RoadprintsTiming",value);
        if(app!=null)WORKER.execute(()->append(value));
    }
    private static void append(String value){
        Context c=app;if(c==null)return;
        android.content.SharedPreferences p=c.getSharedPreferences(PREFS,0);
        String entry=java.time.Instant.now()+" · "+value+"\n";
        String previous=p.getString("recent","");
        String combined=entry+previous;
        if(combined.length()>16000)combined=combined.substring(0,16000);
        p.edit().putString("recent",combined).apply();
    }
    static void clear(Context context){
        context.getSharedPreferences(PREFS,0).edit().clear().apply();
        ReferenceDataDiagnostics.initialize(context.getFilesDir());
        ReferenceDataDiagnostics.clear();
    }
    static String report(Context context){
        ReferenceDataDiagnostics.initialize(context.getFilesDir());
        String bandwidth=ReferenceDataDiagnostics.report();
        String timings=context.getSharedPreferences(PREFS,0).getString("recent","");
        return bandwidth.isEmpty()?timings:timings.isEmpty()?bandwidth:bandwidth+"\n"+timings;
    }
}
