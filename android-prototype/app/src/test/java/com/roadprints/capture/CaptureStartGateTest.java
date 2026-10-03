package com.roadprints.capture;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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
}
