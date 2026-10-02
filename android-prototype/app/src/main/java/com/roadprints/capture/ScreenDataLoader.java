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

    private ScreenDataLoader() {}

    static void execute(Runnable task) {
        WORKER.execute(task);
    }
}
