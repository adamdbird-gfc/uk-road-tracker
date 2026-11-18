package com.roadprints.capture;

import static org.junit.Assert.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

/** New Map requests cannot accumulate behind an expensive running archive scan. */
public final class MapWorkerQueueTest {
    @Test public void supersededQueuedMapRequestIsCancelledAndOnlyNewestRuns() throws Exception {
        CountDownLatch running = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger oldQueuedCalls = new AtomicInteger();
        AtomicInteger latestCalls = new AtomicInteger();
        Future<?> first = ScreenDataLoader.map(() -> {
            running.countDown();
            try {
                if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Map worker not released");
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        });
        try {
            assertTrue("Map worker did not start", running.await(10, TimeUnit.SECONDS));
            Future<?> old = ScreenDataLoader.map(oldQueuedCalls::incrementAndGet);
            Future<?> latest = ScreenDataLoader.map(latestCalls::incrementAndGet);
            assertTrue("Superseded Map request must be cancelled", old.isCancelled());
            release.countDown();
            first.get(10, TimeUnit.SECONDS);
            latest.get(10, TimeUnit.SECONDS);
            assertEquals(0, oldQueuedCalls.get());
            assertEquals(1, latestCalls.get());
        } finally {
            release.countDown();
            first.cancel(true);
        }
    }
}
