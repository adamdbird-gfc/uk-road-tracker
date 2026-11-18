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
        assertTrue("A fully scanned archive with held roads may be cached if still labelled incomplete",
                routes.canCacheSnapshot());
    }

    @Test public void deadlineGraceRequiresActiveScanAndIsGivenOnlyOnce() {
        assertTrue(MapActivity.allowMapProgressGrace("journey_records_scanning", 2400, false));
        assertFalse(MapActivity.allowMapProgressGrace("journey_records_scanning", 16000, false));
        assertFalse(MapActivity.allowMapProgressGrace("journey_records_scanning", -1, false));
        assertFalse(MapActivity.allowMapProgressGrace("render_path_preparation", 100, false));
        assertFalse(MapActivity.allowMapProgressGrace("journey_records_scanning", 100, true));
        assertEquals(120000L, MapActivity.MAP_TIMEOUT_MS);
        assertEquals(60000L, MapActivity.MAP_PROGRESS_GRACE_MS);
    }

    @Test public void budgetDegradationCannotBeCached() {
        MapActivity.MapRoutes routes = new MapActivity.MapRoutes();
        routes.localReferenceAvailable = true;
        routes.localCoverageDegraded = true;
        assertTrue("Known held journeys are not a resource skip", routes.canCacheSnapshot());
        routes.localCoverageResourceDegraded = true;
        assertFalse("Resource-budget exclusions cannot become reusable coverage", routes.canCacheSnapshot());
        routes.localCoverageResourceDegraded = false;
        assertTrue(routes.canCacheSnapshot());
        routes.localReferenceAvailable = false;
        assertFalse(routes.canCacheSnapshot());
    }
}
