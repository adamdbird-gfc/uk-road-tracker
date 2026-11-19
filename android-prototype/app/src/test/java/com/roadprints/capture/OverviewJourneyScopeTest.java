package com.roadprints.capture;

import static org.junit.Assert.*;
import org.junit.Test;

/** Owner-approved distinction between worldwide corridor coverage and collections. */
public final class OverviewJourneyScopeTest {
    @Test public void drivingWalkingRunningAndCyclingAreWorldwideMapEligible() {
        for (String mode : new String[]{"driving", "bus", "walking", "running",
                "pedestrian", "cycling", "bicycle", " DRIVING "}) {
            assertTrue(mode, OverviewJourneyScope.contributesCorridorCoverage(mode));
            assertEquals(mode, "", OverviewJourneyScope.exclusionReason(mode));
        }
    }

    @Test public void railwayAndFlightAreHiddenButFerryUsesSeparateLayer() {
        assertFalse(OverviewJourneyScope.contributesCorridorCoverage("train"));
        assertEquals("collection_railway", OverviewJourneyScope.exclusionReason("train"));
        for (String mode : new String[]{"plane", "flight", " FLIGHT "}) {
            assertFalse(mode, OverviewJourneyScope.contributesCorridorCoverage(mode));
            assertEquals("collection_flight", OverviewJourneyScope.exclusionReason(mode));
        }
        assertFalse(OverviewJourneyScope.contributesCorridorCoverage("ferry"));
        assertEquals("separate_ferry_layer", OverviewJourneyScope.exclusionReason("ferry"));
    }

    @Test public void unknownIsNotMistakenForAcceptedCorridorCoverage() {
        for (String mode : new String[]{null, "", "unknown", "other", "transit", "airship"}) {
            assertFalse(String.valueOf(mode), OverviewJourneyScope.contributesCorridorCoverage(mode));
            assertEquals("unsupported_mode", OverviewJourneyScope.exclusionReason(mode));
        }
    }

    @Test public void scopeDoesNotRequireUkLocationOrMatchingStatus() {
        // Scope is geographical-mode eligibility, not evidence acceptance.
        // France, Ibiza, and UK routes are all eligible when mode is driving,
        // walking, running or cycling; geometry quality is verified separately.
        assertTrue(OverviewJourneyScope.contributesCorridorCoverage("driving"));
        assertTrue(OverviewJourneyScope.contributesCorridorCoverage("walking"));
    }
}
