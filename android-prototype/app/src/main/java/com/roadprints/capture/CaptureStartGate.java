package com.roadprints.capture;

/** Pure thresholds for deciding whether an activity transition represents a real trip. */
final class CaptureStartGate {
    static final long START_CONFIRMATION_MS = 20_000L;
    static final long START_CANDIDATE_TIMEOUT_MS = 480_000L;
    static final long CANDIDATE_STILL_CANCEL_MS = 300_000L;
    static final long STILLNESS_END_THRESHOLD_MS = 300_000L;

    private CaptureStartGate() {}

    static float minimumMovementMetres(String mode) {
        if ("walking".equals(mode) || "running".equals(mode)) return 50f;
        if ("cycling".equals(mode)) return 70f;
        return 120f;
    }

    static float departureRadiusMetres(String mode) {
        if ("walking".equals(mode) || "running".equals(mode)) return 75f;
        if ("cycling".equals(mode)) return 100f;
        return 150f;
    }

    static float reliableMovementIncrement(float stepMetres, float previousAccuracyMetres,
                                           float currentAccuracyMetres) {
        float uncertaintyAllowance = (Math.max(0f, previousAccuracyMetres)
                + Math.max(0f, currentAccuracyMetres)) * 0.25f;
        return Math.max(0f, stepMetres - Math.min(8f, uncertaintyAllowance));
    }

    static boolean shouldConfirmStart(String mode, long elapsedMs, float movementSinceCandidateMetres,
                                     boolean hasRecentStopAnchor, float fromStopAnchorMetres) {
        if (elapsedMs < START_CONFIRMATION_MS
                || movementSinceCandidateMetres < minimumMovementMetres(mode)) return false;
        // A real walking route may begin and end close to the same place, such as
        // an office lunch walk. Do not require a straight-line departure radius.
        return "walking".equals(mode) || "running".equals(mode)
                || !hasRecentStopAnchor
                || fromStopAnchorMetres >= departureRadiusMetres(mode);
    }

    static boolean shouldEndAfterStillness(long stillDurationMs) {
        return stillDurationMs >= STILLNESS_END_THRESHOLD_MS;
    }

    static boolean hasLeftStopArea(String mode, float distanceFromStopMetres) {
        return distanceFromStopMetres >= departureRadiusMetres(mode);
    }
}
