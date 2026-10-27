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
        WORKER.scheduleWithFixedDelay(()->{
            try {
                if(foreground==0){pendingAt=0;return;}
                long now=SystemClock.uptimeMillis();
                if(pendingAt==0){reported=false;pendingAt=now;main.post(()->pendingAt=0);}
                else if(!reported&&now-pendingAt>=2000){reported=true;
                    StringBuilder stack=new StringBuilder("foreground_ui_stall_ms=").append(now-pendingAt);
                    for(StackTraceElement frame:Looper.getMainLooper().getThread().getStackTrace()){
                        if(stack.length()>5000)break;stack.append('\n').append(frame);
                    }
                    append(stack.toString());
                }
            }catch(Exception ignored){/* Diagnostics cannot interrupt the app. */}
        },1,1,TimeUnit.SECONDS);
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
