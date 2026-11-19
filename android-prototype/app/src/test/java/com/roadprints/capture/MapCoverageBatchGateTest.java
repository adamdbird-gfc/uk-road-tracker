package com.roadprints.capture;

import static org.junit.Assert.*;
import org.junit.Test;

public final class MapCoverageBatchGateTest {
    @Test public void boundariesAreStrictlyBoundedAndDoNotRetainRecords() {
        MapCoverageBatchGate gate = new MapCoverageBatchGate(100L);
        for (int i = 0; i < 63; i++) assertFalse(gate.record(101L));
        assertTrue(gate.record(101L));
        assertEquals(64, gate.finishBoundary(101L));
        assertEquals(1, gate.boundaries());
        assertFalse(gate.hasPending());
        assertFalse(gate.record(102L));
        assertEquals(1, gate.finishBoundary(102L));
        assertEquals(65, gate.scanned());
        assertEquals(2, gate.boundaries());
    }

    @Test public void slowRecordsForceEarlierBoundary() {
        MapCoverageBatchGate gate = new MapCoverageBatchGate(100L);
        assertTrue(gate.record(351L));
        assertEquals(1, gate.finishBoundary(351L));
        assertFalse(gate.record(352L));
    }

    @Test public void noEmptyBatchAndEmptyArchive() {
        MapCoverageBatchGate gate = new MapCoverageBatchGate(0L);
        assertEquals(0, gate.finishBoundary(10L));
        assertEquals(0, gate.scanned());
        assertEquals(0, gate.boundaries());
    }

    @Test public void publicationRejectsStaleOrLowMemoryWork() {
        long safe = 64L * 1024L * 1024L;
        assertTrue(MapCoverageBatchGate.canPublish(safe, false, true, true));
        assertFalse(MapCoverageBatchGate.canPublish(safe, true, true, true));
        assertFalse(MapCoverageBatchGate.canPublish(safe, false, false, true));
        assertFalse(MapCoverageBatchGate.canPublish(safe, false, true, false));
        assertFalse(MapCoverageBatchGate.canPublish(31L * 1024L * 1024L, false, true, true));
    }
}
