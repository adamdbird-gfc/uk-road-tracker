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
    private static final java.util.concurrent.ThreadPoolExecutor MAP =
            new java.util.concurrent.ThreadPoolExecutor(
                    1, 1, 0L, java.util.concurrent.TimeUnit.MILLISECONDS,
                    new java.util.concurrent.ArrayBlockingQueue<>(1),
                    task -> {
                        Thread thread = new Thread(task, "roadprints-map-data");
                        thread.setPriority(Thread.NORM_PRIORITY - 1);
                        return thread;
                    });
    /** Only the newest not-yet-started Map request is relevant to the visible screen. */
    static java.util.concurrent.Future<?> map(Runnable task) {
        java.util.concurrent.FutureTask<Void> submitted = new java.util.concurrent.FutureTask<>(task, null);
        synchronized (MAP) {
            Runnable queued = MAP.getQueue().poll();
            if (queued instanceof java.util.concurrent.Future<?> previous) previous.cancel(false);
            MAP.execute(submitted);
        }
        return submitted;
    }

    private static final java.util.concurrent.ThreadPoolExecutor CACHE=new java.util.concurrent.ThreadPoolExecutor(
            1,1,0L,java.util.concurrent.TimeUnit.MILLISECONDS,new java.util.concurrent.ArrayBlockingQueue<>(1),
            task->new Thread(()->{android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);task.run();},"roadprints-map-cache"),
            new java.util.concurrent.ThreadPoolExecutor.DiscardOldestPolicy());
    static void cache(Runnable task) { CACHE.execute(task); }

    private static final ExecutorService SUMMARIES=Executors.newSingleThreadExecutor(task->new Thread(task,"roadprints-journey-summaries"));
    static void summaries(Runnable task){SUMMARIES.execute(task);}
    private static final ExecutorService VENUES = Executors.newSingleThreadExecutor(
            task -> new Thread(task, "roadprints-venue-ui"));
    static void venues(Runnable task) { VENUES.execute(task); }

    private static final java.util.concurrent.locks.ReentrantLock ARCHIVE_PREPARATION =
            new java.util.concurrent.locks.ReentrantLock(true);
    private static volatile boolean preparingArchive;

    static boolean preparingArchive() { return preparingArchive; }

    static <T> T prepareArchive(java.util.concurrent.Callable<T> prepare) throws Exception {
        long waitedAt = android.os.SystemClock.elapsedRealtime();
        ARCHIVE_PREPARATION.lockInterruptibly();
        long acquiredAt = android.os.SystemClock.elapsedRealtime();
        preparingArchive = true;
        PerformanceDiagnostics.timing("archive_prepare_lock_wait", acquiredAt - waitedAt,
                "thread=" + Thread.currentThread().getName());
        try {
            long railwayAt = android.os.SystemClock.elapsedRealtime();
            RailwayTrialWorker.releaseCatalogueForScreen();
            PerformanceDiagnostics.timing("archive_prepare_railway_release",
                    android.os.SystemClock.elapsedRealtime() - railwayAt,
                    "thread=" + Thread.currentThread().getName());
            return prepare.call();
        } finally {
            preparingArchive = false;
            ARCHIVE_PREPARATION.unlock();
        }
    }

    private ScreenDataLoader() {}

    static java.util.concurrent.Future<?> submit(Runnable task) { return WORKER.submit(task); }

    static void execute(Runnable task) {
        WORKER.execute(task);
    }
}
