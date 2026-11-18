package com.roadprints.capture;

import static org.junit.Assert.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.lang.reflect.Field;
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
        // Hold the real archive preparation lock on this test thread. Using a
        // second screen executor here could queue behind unrelated test work.
        Field field = ScreenDataLoader.class.getDeclaredField("ARCHIVE_PREPARATION");
        field.setAccessible(true);
        ReentrantLock lock = (ReentrantLock) field.get(null);
        CountDownLatch mapStarted = new CountDownLatch(1);
        AtomicInteger unexpectedPreparation = new AtomicInteger();
        Future<?> cancelled = null;
        lock.lock();
        try {
            cancelled = ScreenDataLoader.map(() -> {
                mapStarted.countDown();
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
            assertTrue("Map should start while archive preparation is locked",
                    mapStarted.await(10, TimeUnit.SECONDS));
            assertTrue("Cancelled Map must accept an interrupt", cancelled.cancel(true));
        } finally {
            lock.unlock();
        }
        try {
            // This runs on the same Map executor after the cancelled scan.
            Future<?> next = ScreenDataLoader.map(() -> {});
            next.get(10, TimeUnit.SECONDS);
            assertEquals("Cancelled Map must not enter archive preparation",
                    0, unexpectedPreparation.get());
        } finally {
            if (cancelled != null) cancelled.cancel(true);
        }
    }

}
