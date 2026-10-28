package com.roadprints.capture;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Serializes archive-heavy screen preparation so multiple tabs cannot scan the archive at once. */
final class ScreenDataLoader {
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "roadprints-screen-data");
        thread.setPriority(Thread.NORM_PRIORITY - 1);
        return thread;
    });

    private static final ExecutorService INTERACTIVE = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "roadprints-road-detail");
        thread.setPriority(Thread.NORM_PRIORITY - 1);
        return thread;
    });
    static java.util.concurrent.Future<?> interactive(Runnable task) { return INTERACTIVE.submit(task); }

    // User map refreshes must not queue behind history, recap or Progress scans.
    private static final ExecutorService MAP = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "roadprints-map-data");
        thread.setPriority(Thread.NORM_PRIORITY - 1);return thread;
    });
    static java.util.concurrent.Future<?> map(Runnable task) { return MAP.submit(task); }

    private static final java.util.concurrent.ThreadPoolExecutor CACHE=new java.util.concurrent.ThreadPoolExecutor(
            1,1,0L,java.util.concurrent.TimeUnit.MILLISECONDS,new java.util.concurrent.ArrayBlockingQueue<>(1),
            task->new Thread(()->{android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);task.run();},"roadprints-map-cache"),
            new java.util.concurrent.ThreadPoolExecutor.DiscardOldestPolicy());
    static void cache(Runnable task) { CACHE.execute(task); }

    private static final ExecutorService SUMMARIES=Executors.newSingleThreadExecutor(task->new Thread(task,"roadprints-journey-summaries"));
    static void summaries(Runnable task){SUMMARIES.execute(task);}
    private ScreenDataLoader() {}

    static void execute(Runnable task) {
        WORKER.execute(task);
    }
}
