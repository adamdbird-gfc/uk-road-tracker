package com.roadprints.capture;

import android.app.*;
import android.os.*;
import android.view.*;
import org.json.*;
import java.io.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Sampled process evidence, not allocation attribution or a hard peak guarantee. Acceptance only. */
final class MilepostRuntimeProbe implements AutoCloseable {
    private final Activity host;
    private final ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private final HandlerThread frames=new HandlerThread("acceptance-frames");
    private final AtomicBoolean closed=new AtomicBoolean();
    private final AtomicLong maxStall=new AtomicLong(),peakJava=new AtomicLong(),peakNative=new AtomicLong(),peakPss=new AtomicLong();
    private long frameCount,maxFrame,slowFrames;
    private final Window.OnFrameMetricsAvailableListener listener=(window,metrics,dropped)->{
        long ns=metrics.getMetric(FrameMetrics.TOTAL_DURATION);
        synchronized(this){frameCount++;maxFrame=Math.max(maxFrame,ns);if(ns>16666667)slowFrames++;}
        if(dropped>0)mark("frame_callback_drops",dropped);
    };
    MilepostRuntimeProbe(Activity host){
        this.host=host;frames.start();host.getWindow().addOnFrameMetricsAvailableListener(listener,new Handler(frames.getLooper()));
        try{
            ActivityManager.MemoryInfo ram=new ActivityManager.MemoryInfo();host.getSystemService(ActivityManager.class).getMemoryInfo(ram);
            write(new JSONObject().put("event","environment").put("model",Build.MODEL).put("manufacturer",Build.MANUFACTURER)
                .put("android",Build.VERSION.RELEASE).put("sdk",Build.VERSION.SDK_INT).put("ram_bytes",ram.totalMem)
                .put("package",BuildConfig.APPLICATION_ID).put("variant",BuildConfig.BUILD_TYPE)
                .put("workload","synthetic; operator records source SHA, archive size and runtime kind separately")
                .put("evidence_kind","Android runtime; physical/emulator classification requires operator receipt"));
        }catch(Exception e){throw new IllegalStateException(e);}
        worker.scheduleWithFixedDelay(this::sample,0,250,TimeUnit.MILLISECONDS);
        main.post(heartbeat);
    }
    private final Runnable heartbeat=new Runnable(){
        long due=SystemClock.uptimeMillis();
        public void run(){if(closed.get())return;long now=SystemClock.uptimeMillis();maxStall.accumulateAndGet(Math.max(0,now-due),Math::max);due=now+50;main.postDelayed(this,50);}
    };
    private void sample(){
        try{
            Runtime r=Runtime.getRuntime();long java=r.totalMemory()-r.freeMemory(),nativeBytes=Debug.getNativeHeapAllocatedSize();
            Debug.MemoryInfo info=new Debug.MemoryInfo();Debug.getMemoryInfo(info);long pss=info.getTotalPss()*1024L;
            peakJava.accumulateAndGet(java,Math::max);peakNative.accumulateAndGet(nativeBytes,Math::max);peakPss.accumulateAndGet(pss,Math::max);
            write(new JSONObject().put("event","sample").put("java_used_bytes",java).put("native_allocated_bytes",nativeBytes)
                .put("total_pss_bytes",pss).put("runtime_stats",new JSONObject(Debug.getRuntimeStats())));
        }catch(Exception e){android.util.Log.e("MilepostAcceptance","Probe sample failed",e);}
    }
    void mark(String name,long ms){try{write(new JSONObject().put("event",name).put("value",ms));}catch(Exception e){throw new IllegalStateException(e);}}
    private synchronized void write(JSONObject row)throws Exception {
        row.put("elapsed_ms",SystemClock.elapsedRealtime());
        try(FileWriter out=new FileWriter(new File(host.getFilesDir(),"milepost-acceptance-runtime.jsonl"),true)){out.write(row.toString());out.write('\n');}
    }
    public void close(){
        if(!closed.compareAndSet(false,true))return;
        main.removeCallbacks(heartbeat);host.getWindow().removeOnFrameMetricsAvailableListener(listener);
        worker.shutdownNow();frames.quitSafely();
        try{write(new JSONObject().put("event","session_summary").put("sampled_peak_java_bytes",peakJava.get())
            .put("sampled_peak_native_bytes",peakNative.get()).put("sampled_peak_pss_bytes",peakPss.get())
            .put("max_main_heartbeat_delay_ms",maxStall.get()).put("frames",frameCount)
            .put("frames_over_16_67ms",slowFrames).put("max_frame_ns",maxFrame));
        }catch(Exception e){android.util.Log.e("MilepostAcceptance","Probe summary failed",e);}
    }
}
