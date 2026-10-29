package com.roadprints.capture;

import org.junit.Test;
import static org.junit.Assert.*;

public class VehicleArrivalEvidenceTest {
    private FootTraceValidator.Sample p(double metres,double accuracy,long time,double speed,int index) {
        return new FootTraceValidator.Sample(metres/111195,0,accuracy,time,speed,index);
    }
    @Test public void clusteredStopSurvivesRecurringIsolatedSpeedSpikes() {
        WalkingStillnessDetector d=new WalkingStillnessDetector();
        for(int i=0;i<=90;i++) {
            double speed=i>3&&i%7==0?3.8:.15;
            d.accept(p((i%5-2)*6,25,100000+i*7000,speed,i),true);
        }
        assertTrue(d.canFinish(730000));assertEquals(0,d.confirmedEndpoint());
        assertEquals(100000,d.anchor().time);
    }
    @Test public void sustainedReportedMotionAndSilenceAreNotArrival() {
        WalkingStillnessDetector d=new WalkingStillnessDetector();
        for(int i=0;i<12;i++)d.accept(p(i%3,10,100000+i*10000,.1,i),true);
        for(int i=12;i<=70;i++)d.accept(p(i%3,10,100000+i*10000,3,i),true);
        assertFalse(d.canFinish(800000));
        d=new WalkingStillnessDetector();
        for(int i=0;i<12;i++)d.accept(p(i%3,10,100000+i*10000,.1,i),true);
        assertFalse(d.canFinish(800000));
    }
    @Test public void actualVehicleDepartureClearsTheStopEvenInsideTheRadius() {
        WalkingStillnessDetector d=new WalkingStillnessDetector();
        for(int i=0;i<=10;i++)d.accept(p(i%3,5,100000+i*30000,.1,i),true);
        assertTrue(d.canFinish(400000));
        d.accept(p(10,5,410000,8,11),true);
        assertFalse(d.quiet(410000));assertEquals(-1,d.confirmedEndpoint());
    }
    @Test public void genuineWalkingAwayIsNotAbsorbedIntoTheVehicleStop() {
        WalkingStillnessDetector d=new WalkingStillnessDetector();
        for(int i=0;i<=10;i++)d.accept(p(i%3,5,100000+i*30000,.1,i),true);
        for(int i=1;i<=8;i++)d.accept(p(i*15,5,400000+i*10000,1.5,10+i),true);
        assertFalse(d.quiet(480000));assertEquals(-1,d.confirmedEndpoint());
    }
    @Test public void pedestrianLoopsStillCountAsMovement() {
        WalkingStillnessDetector d=new WalkingStillnessDetector();
        for(int i=0;i<40;i++)d.accept(p(i%2==0?0:20,5,100000+i*10000,1.5,i),false);
        assertFalse(d.canFinish(490000));
    }
    @Test public void transientWalkingLabelBeforeVehicleMovementHasNoFootLeg() {
        java.util.List<FootTraceValidator.Sample> samples=new java.util.ArrayList<>();
        samples.add(p(0,5,100000,1,0));
        samples.add(p(3,5,110000,1,1));
        for(int i=0;i<4;i++)samples.add(p(100+i*100,5,120000+i*10000,10,2+i));
        assertFalse(CaptureMovementEvidence.hasInitialFootLeg(samples));
        assertTrue(CaptureMovementEvidence.hasVehicleMovement(samples));
    }
    @Test public void supportedWalkBeforeVehicleMovementRetainsItsFootLeg() {
        java.util.List<FootTraceValidator.Sample> samples=new java.util.ArrayList<>();
        for(int i=0;i<4;i++)samples.add(p(i*20,3,100000+i*15000,1.3,i));
        for(int i=0;i<4;i++)samples.add(p(160+i*100,5,155000+i*10000,10,4+i));
        assertTrue(CaptureMovementEvidence.hasInitialFootLeg(samples));
        assertTrue(CaptureMovementEvidence.hasVehicleMovement(samples));
    }
}
