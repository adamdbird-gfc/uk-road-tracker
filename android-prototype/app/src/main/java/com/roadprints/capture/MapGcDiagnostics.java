package com.roadprints.capture;

/**
 * Passive ART collection/heap checkpoints used for Map archive diagnostics.
 * Never requests GC, holds no Map/journey objects, and reports only counts.
 */
final class MapGcDiagnostics {
    private final long startCollections;
    private final long startFreedBytes;
    private final long startGcTimeMs;

    MapGcDiagnostics() {
        startCollections = stat("art.gc.gc-count");
        startFreedBytes = stat("art.gc.bytes-freed");
        startGcTimeMs = stat("art.gc.gc-time");
    }

    void checkpoint(String phase, int scanned, int previewSections, int pendingEdges) {
        Runtime runtime = Runtime.getRuntime();
        long used = runtime.totalMemory() - runtime.freeMemory();
        PerformanceDiagnostics.timing("map_gc_checkpoint", 0,
                "phase=" + phase
                        + " scanned=" + scanned
                        + " used_kib=" + used / 1024
                        + " committed_kib=" + runtime.totalMemory() / 1024
                        + " free_headroom_kib=" + (runtime.maxMemory() - used) / 1024
                        + " gc_runs=" + delta(startCollections, stat("art.gc.gc-count"), 1)
                        + " gc_freed_kib=" + delta(startFreedBytes, stat("art.gc.bytes-freed"), 1024)
                        + " gc_time_ms=" + delta(startGcTimeMs, stat("art.gc.gc-time"), 1)
                        + " preview_sections=" + previewSections
                        + " pending_edges=" + pendingEdges);
    }

    private static long stat(String name) {
        try {
            String value = android.os.Debug.getRuntimeStat(name);
            return value == null ? -1 : Long.parseLong(value);
        } catch (RuntimeException unavailable) {
            return -1;
        }
    }

    private static String delta(long start, long end, long unit) {
        return start < 0 || end < start ? "unavailable"
                : Long.toString((end - start) / unit);
    }
}
