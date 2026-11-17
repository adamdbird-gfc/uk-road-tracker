package com.roadprints.capture;

import static org.junit.Assert.*;

import org.junit.Test;

/** Coverage-only overview must never describe failed attribution as an empty travel history. */
public final class MapActivityOverviewCoverageTest {
    @Test public void missingCumulativeRoadsDoesNotClaimJourneysAreUntravelled() {
        MapActivity.MapRoutes routes = new MapActivity.MapRoutes();
        routes.matchedJourneys = 650;
        assertTrue(MapActivity.overviewCoverageMessage(routes, true)
                .contains("No cumulative minor-road coverage available yet"));
        assertTrue(MapActivity.overviewCoverageMessage(routes, true)
                .contains("saved journeys"));
    }

    @Test public void failureOverridesVisibleMajorRoadReferences() {
        MapActivity.MapRoutes routes = new MapActivity.MapRoutes();
        routes.matchedJourneys = 650;
        routes.localCoverageDegraded = true;
        routes.coveredARoadSections.add(new org.json.JSONArray());
        assertTrue(MapActivity.overviewCoverageMessage(routes, true)
                .contains("coverage incomplete"));
        assertFalse(routes.canCacheSnapshot());
    }

    @Test public void budgetDegradationCannotBeCached() {
        MapActivity.MapRoutes routes = new MapActivity.MapRoutes();
        routes.localReferenceAvailable = true;
        routes.localCoverageDegraded = true;
        assertFalse(routes.canCacheSnapshot());
        routes.localCoverageDegraded = false;
        assertTrue(routes.canCacheSnapshot());
        routes.localReferenceAvailable = false;
        assertFalse(routes.canCacheSnapshot());
    }
}
