package com.roadprints.capture;

import static org.junit.Assert.*;
import org.junit.Test;

public final class CanonicalPreviewIndexTest {
    @Test public void replacementDoesNotDuplicatePhysicalRoads() {
        CanonicalPreviewIndex<String> index = new CanonicalPreviewIndex<>(2, 4);
        assertTrue(index.replace("osm:1:2:3", "first", 2));
        assertTrue(index.replace("osm:1:2:3", "merged", 3));
        assertEquals(1, index.sectionCount());
        assertEquals(3, index.lineCount());
        assertEquals("merged", index.layers().iterator().next());
        assertTrue(index.replace("osm:2:3:4", "other", 1));
        assertEquals(2, index.sectionCount());
        assertEquals(4, index.lineCount());
        assertFalse(index.replace("osm:3:4:5", "overflow", 1));
        assertFalse(index.replace("osm:1:2:3", "too large", 4));
        assertEquals(4, index.lineCount());
        assertTrue(index.replace("osm:1:2:3", "removed", 0));
        assertEquals(1, index.sectionCount());
        assertEquals(1, index.lineCount());
        index.clear();
        assertEquals(0, index.sectionCount());
        assertEquals(0, index.lineCount());
    }

    @Test public void boundedMemorySupportsMoreThanLegacy256Roads() {
        CanonicalPreviewIndex<String> index =
                new CanonicalPreviewIndex<>(8192, 16384, 2 * 1024 * 1024);
        for (int i = 0; i < 1500; i++)
            assertTrue("section " + i,
                    index.replace("osm:way:" + i, "line", 1));
        assertEquals(1500, index.sectionCount());
        assertEquals(1500, index.lineCount());
        assertTrue(index.estimatedBytes() <= 2 * 1024 * 1024);
        assertEquals(0, index.rejectedUpdates());
        assertTrue(index.replace("osm:way:0", "replaced", 2));
        assertEquals(1500, index.sectionCount());
        assertEquals(1501, index.lineCount());
        assertTrue(index.replace("osm:way:0", "deleted", 0));
        assertEquals(1499, index.sectionCount());
    }

    @Test public void estimatedBytesBudgetFailsClosedWithoutDroppingExistingRoads() {
        CanonicalPreviewIndex<String> index = new CanonicalPreviewIndex<>(100, 100, 400);
        assertTrue(index.replace("a", "geometry", 1));
        assertTrue(index.replace("b", "geometry", 1));
        assertFalse(index.replace("c", "overflow", 1));
        assertEquals(2, index.sectionCount());
        assertEquals(1, index.rejectedUpdates());
        assertEquals(388, index.estimatedBytes());
        assertTrue(index.replace("a", "removed", 0));
        assertTrue(index.replace("c", "fits", 1));
        assertEquals(2, index.sectionCount());
        assertEquals(388, index.estimatedBytes());
    }

    @Test public void invalidUpdatesCannotChangeIndex() {
        CanonicalPreviewIndex<String> index = new CanonicalPreviewIndex<>(1, 1);
        assertFalse(index.replace("", "layer", 1));
        assertFalse(index.replace("key", null, 1));
        assertFalse(index.replace("key", "layer", -1));
        assertTrue(index.layers().isEmpty());
    }
}
