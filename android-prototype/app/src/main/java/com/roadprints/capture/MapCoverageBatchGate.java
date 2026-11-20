package com.roadprints.capture;

/**
 * Bounded archive-scan publication cadence for progressive overview construction.
 *
 * This class deliberately owns no journey objects, geometry or Android views.
 * A batch boundary is not itself permission to publish incomplete coverage:
 * renderers must use canonical, immutable road-network deltas.
 */
final class MapCoverageBatchGate {
    static final int MAX_RECORDS_PER_BATCH = 64;
    static final long MAX_BATCH_AGE_MS = 250L;
    static final long MIN_HEADROOM_BYTES = 32L * 1024L * 1024L;

    private int scanned;
    private int sinceBoundary;
    private int boundaries;
    private long beganAt;

    MapCoverageBatchGate(long startedAt) {
        beganAt = startedAt;
    }

    boolean record(long now) {
        scanned++;
        sinceBoundary++;
        return sinceBoundary >= MAX_RECORDS_PER_BATCH
                || (sinceBoundary > 0 && now - beganAt >= MAX_BATCH_AGE_MS);
    }

    boolean hasPending() {
        return sinceBoundary > 0;
    }

    int finishBoundary(long now) {
        if (!hasPending()) return 0;
        int count = sinceBoundary;
        sinceBoundary = 0;
        beganAt = now;
        boundaries++;
        return count;
    }

    int scanned() { return scanned; }
    int boundaries() { return boundaries; }

    static boolean canPublish(long freeHeadroomBytes, boolean cancelled,
                              boolean sameRevision, boolean sameGeneration) {
        return !cancelled && sameRevision && sameGeneration
                && freeHeadroomBytes >= MIN_HEADROOM_BYTES;
    }
}
