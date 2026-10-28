package com.roadprints.capture;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class CaptureMovementEvidenceTest {
    private FootTraceValidator.Sample point(double metres, long time, double accuracy, double speed, int i) {
        return new FootTraceValidator.Sample(metres/111195, 0, accuracy, time, speed, i);
    }
    @Test public void indoorDriftDoesNotBecomeAWalk() {
        List<FootTraceValidator.Sample> samples = new ArrayList<>();
        for (int i=0; i<30; i++) samples.add(point(i%3*5, 1000+i*10000L, 40, 0, i));
        assertFalse(CaptureMovementEvidence.hasJourneyMovement(samples, "walking"));
        assertFalse(CaptureMovementEvidence.confirmsMode(samples, "walking", 1000, 291000));
    }
    @Test public void cleanedTenMetreTraceIsNotAJourney() {
        List<FootTraceValidator.Sample> samples = new ArrayList<>();
        for (int i=0; i<3; i++) samples.add(point(i*5, 1000+i*15000L, 5, .5, i));
        assertFalse(CaptureMovementEvidence.hasJourneyMovement(samples, "walking"));
    }
    @Test public void sustainedShortWalkAndLoopRemainJourneys() {
        List<FootTraceValidator.Sample> samples = new ArrayList<>();
        for (int i=0; i<7; i++) samples.add(point(i*16, 1000+i*15000L, 5, 1, i));
        assertTrue(CaptureMovementEvidence.hasJourneyMovement(samples, "walking"));
        assertTrue(CaptureMovementEvidence.confirmsMode(samples, "walking", 1000, 91000));
        for (int i=7; i<13; i++) samples.add(point((12-i)*16, 1000+i*15000L, 5, 1, i));
        assertTrue(CaptureMovementEvidence.hasJourneyMovement(samples, "walking"));
    }
    @Test public void vehicleSpeedVetoesWalkingDespiteAndroidLabel() {
        List<FootTraceValidator.Sample> samples = new ArrayList<>();
        for (int i=0; i<7; i++) samples.add(point(i*90, 1000+i*10000L, 10, 9, i));
        assertFalse(CaptureMovementEvidence.confirmsMode(samples, "walking", 1000, 61000));
        assertTrue(CaptureMovementEvidence.confirmsMode(samples, "unknown", 1000, 61000));
    }
    @Test public void stoppedCarDoesNotCorroborateWalking() {
        List<FootTraceValidator.Sample> samples = new ArrayList<>();
        for (int i=0; i<10; i++) samples.add(point(i%3, 1000+i*10000L, 5, 0, i));
        assertFalse(CaptureMovementEvidence.confirmsMode(samples, "walking", 1000, 91000));
    }
    @Test public void ordinaryCyclingCanCorroborateABicycleCallback() {
        List<FootTraceValidator.Sample> samples = new ArrayList<>();
        for (int i=0; i<7; i++) samples.add(point(i*30, 1000+i*10000L, 5, 3, i));
        assertTrue(CaptureMovementEvidence.confirmsMode(samples, "cycling", 1000, 61000));
    }
    @Test public void ordinaryPhoneAccuracyDoesNotHideAShortWalk() {
        List<FootTraceValidator.Sample> samples = new ArrayList<>();
        for (int i=0; i<5; i++) samples.add(point(i*20, 1000+i*20000L, 14, 1, i));
        assertTrue(CaptureMovementEvidence.hasJourneyMovement(samples, "walking"));
        assertTrue(CaptureMovementEvidence.confirmsMode(samples, "walking", 1000, 81000));
    }
    @Test public void longNearbyBreakHasNoRoutableConnectionButTunnelStaysOpen() {
        FootTraceValidator.Sample before=point(0, 1000, 2, 9, 0);
        assertTrue(CaptureMovementEvidence.separateSamplingSessions(before, point(337, 4060878, 9, 0, 1)));
        assertFalse(CaptureMovementEvidence.separateSamplingSessions(before, point(337, 61000, 9, 9, 1)));
        assertFalse(CaptureMovementEvidence.separateSamplingSessions(before, point(10000, 4060878, 9, 0, 1)));
    }
}
