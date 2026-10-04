package com.roadprints.capture;

import android.location.Location;

import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class CaptureStartGateTest {
    @Test public void activityTransitionAloneDoesNotConfirmJourney() {
        assertFalse(CaptureStartGate.shouldConfirmStart("walking", 0, 0, false, 0));
        assertFalse(CaptureStartGate.shouldConfirmStart("walking", 25_000, 12, false, 0));
    }

    @Test public void walkingStartNeedsStableMovementButKeepsShortTripsPossible() {
        assertFalse(CaptureStartGate.shouldConfirmStart("walking", 10_000, 60, false, 0));
        assertTrue(CaptureStartGate.shouldConfirmStart("walking", 20_000, 50, false, 0));
    }

    @Test public void vehicleStartUsesLargerMovementThreshold() {
        assertFalse(CaptureStartGate.shouldConfirmStart("unknown", 30_000, 100, false, 0));
        assertTrue(CaptureStartGate.shouldConfirmStart("unknown", 30_000, 120, false, 0));
    }

    @Test public void newJourneyMustLeaveThePreviousStopArea() {
        assertFalse(CaptureStartGate.shouldConfirmStart("walking", 60_000, 80, true, 74));
        assertTrue(CaptureStartGate.shouldConfirmStart("walking", 60_000, 80, true, 75));
    }

    @Test public void briefTrafficPauseDoesNotCancelMovementCandidate() {
        assertFalse(CaptureStartGate.shouldEndAfterStillness(4 * 60_000L));
        assertTrue(CaptureStartGate.shouldEndAfterStillness(CaptureStartGate.CANDIDATE_STILL_CANCEL_MS));
    }

    @Test public void confirmedArrivalRequiresFiveMinutesOfStillness() {
        assertFalse(CaptureStartGate.shouldEndAfterStillness(299_999));
        assertTrue(CaptureStartGate.shouldEndAfterStillness(300_000));
    }

    @Test public void collapsesOnlyAStationaryEndpointCluster() {
        Location anchor = location(0.0007, 51.5);
        anchor.setAccuracy(8f);
        List<Location> points = new ArrayList<>();
        points.add(location(0.0, 51.5));
        points.add(location(0.0005, 51.5));
        points.add(location(0.00069, 51.5));
        points.add(location(0.00071, 51.5));
        points.add(location(0.00068, 51.5));

        List<Location> cleaned = CaptureService.collapseStationaryEndpoint(points, anchor);

        assertEquals(2, cleaned.size());
        assertEquals(anchor.getLongitude(), cleaned.get(1).getLongitude(), 0.0);
    }

    @Test public void keepsAShortTailWithoutAStationaryCluster() {
        Location anchor = location(0.0007, 51.5);
        List<Location> points = new ArrayList<>();
        points.add(location(0.0, 51.5));
        points.add(location(0.0005, 51.5));
        points.add(location(0.00069, 51.5));
        points.add(location(0.00071, 51.5));

        assertEquals(points.size(), CaptureService.collapseStationaryEndpoint(points, anchor).size());
    }

    private static Location location(double longitude, double latitude) {
        Location point = new Location("test");
        point.setLongitude(longitude);
        point.setLatitude(latitude);
        return point;
    }
}
