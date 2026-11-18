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

    @Test public void cancellingActiveMapReleasesWorkerForNextRequest() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch interrupted = new CountDownLatch(1);
        AtomicInteger completedOldWork = new AtomicInteger();
        Future<?> active = ScreenDataLoader.map(() -> {
            started.countDown();
            try {
                new CountDownLatch(1).await();
                completedOldWork.incrementAndGet();
            } catch (InterruptedException expected) {
                interrupted.countDown();
                Thread.currentThread().interrupt();
            }
        });
        try {
            assertTrue("First Map should start", started.await(10, TimeUnit.SECONDS));
            assertTrue("An active Map Future must accept cancellation", active.cancel(true));
            assertTrue("Cancelled worker must observe its interrupt", interrupted.await(10, TimeUnit.SECONDS));
            CountDownLatch replacementRan = new CountDownLatch(1);
            Future<?> replacement = ScreenDataLoader.map(replacementRan::countDown);
            assertTrue("A replacement Map must not be blocked by stale work",
                    replacementRan.await(10, TimeUnit.SECONDS));
            replacement.get(10, TimeUnit.SECONDS);
            assertEquals(0, completedOldWork.get());
        } finally {
            active.cancel(true);
        }
    }

    @Test public void cancellingMapWaitingForArchiveLockDoesNotRunItsPreparation() throws Exception {
        CountDownLatch lockAcquired = new CountDownLatch(1);
        CountDownLatch releaseLock = new CountDownLatch(1);
        Future<?> otherScreen = ScreenDataLoader.submit(() -> {
            try {
                ScreenDataLoader.prepareArchive(() -> {
                    lockAcquired.countDown();
                    if (!releaseLock.await(10, TimeUnit.SECONDS))
                        throw new IllegalStateException("Screen lock was not released");
                    return null;
                });
            } catch (Exception failure) {
                throw new RuntimeException(failure);
            }
        });
        Future<?> cancelled = null;
        try {
            assertTrue("Other screen should acquire the archive lock",
                    lockAcquired.await(10, TimeUnit.SECONDS));
            CountDownLatch waiting = new CountDownLatch(1);
            AtomicInteger unexpectedPreparation = new AtomicInteger();
            cancelled = ScreenDataLoader.map(() -> {
                waiting.countDown();
                try {
                    ScreenDataLoader.prepareArchive(() -> {
                        unexpectedPreparation.incrementAndGet();
                        return null;
                    });
                } catch (InterruptedException expected) {
                    Thread.currentThread().interrupt();
                } catch (Exception failure) {
                    throw new RuntimeException(failure);
                }
            });
            assertTrue("Map must start waiting for archive preparation",
                    waiting.await(10, TimeUnit.SECONDS));
            assertTrue(cancelled.cancel(true));
            releaseLock.countDown();
            otherScreen.get(10, TimeUnit.SECONDS);
            Future<?> next = ScreenDataLoader.map(() -> {});
            next.get(10, TimeUnit.SECONDS);
            assertEquals("Cancelled Map must never enter archive preparation",
                    0, unexpectedPreparation.get());
        } finally {
            releaseLock.countDown();
            if (cancelled != null) cancelled.cancel(true);
            otherScreen.cancel(true);
        }
    }

}
