package com.roadprints.capture;

import java.util.Locale;

/**
 * Main Map cumulative corridor-coverage eligibility, distinct from the archive
 * and from mode-specific collections. This does not determine whether a
 * journey has sufficient accepted geometry to receive distance or coverage.
 *
 * No detail-screen point-to-point train/plane/ferry lines belong on the
 * cumulative road/path map. Ferry measurement and independent railway/flight
 * collection assessment must not be gated by this policy.
 */
final class OverviewJourneyScope {
    private OverviewJourneyScope() {}

    static boolean contributesCorridorCoverage(String rawMode) {
        if (rawMode == null) return false;
        String mode = rawMode.trim().toLowerCase(Locale.ROOT);
        return JourneyFilters.isRoadMode(mode)
                || JourneyFilters.isFootMode(mode)
                || MatchingCoordinator.isCycle(mode);
    }

    static String exclusionReason(String rawMode) {
        if (contributesCorridorCoverage(rawMode)) return "";
        String mode = rawMode == null ? "" : rawMode.trim().toLowerCase(Locale.ROOT);
        if ("train".equals(mode)) return "collection_railway";
        if ("plane".equals(mode) || "flight".equals(mode)) return "collection_flight";
        if ("ferry".equals(mode)) return "ferry_measurement_only";
        return "unsupported_mode";
    }
}
