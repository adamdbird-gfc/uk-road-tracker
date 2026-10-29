package com.roadprints.capture;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** Synthetic observations only; callback time must not reassign earlier travel. */
public class CaptureModeBoundaryTest {
    private final List<FootTraceValidator.Sample> samples = new ArrayList<>();
    private void add(double metres, long seconds, double speed) {
        samples.add(new FootTraceValidator.Sample(metres/111195, 0, 5,
                1000+seconds*1000, speed, samples.size()));
    }
    private int boundary(String mode, long seconds) {
        return CaptureMovementEvidence.modeChangeStart(samples, mode, 1000, 1000+seconds*1000);
    }
    @Test public void earlyVehicleLabelDoesNotTakeThePrecedingWalk() {
        add(0,0,1); add(20,20,1); add(40,40,1);
        add(100,50,9); add(200,60,9); add(300,70,9);
        assertEquals(3,boundary("unknown",70));
        assertEquals(6,samples.size());
    }
    @Test public void earlyWalkingLabelCanConfirmAfterTheVehicleActuallyStops() {
        add(0,0,9); add(90,10,9); add(90,20,0);
        add(110,30,1); add(130,40,1); add(150,50,1);
        assertEquals(3,boundary("walking",50));
    }
    @Test public void oldMovementFollowedByAStopCannotConfirmAModeChange() {
        add(0,0,9); add(100,10,9); add(200,20,9); add(200,30,0);
        assertEquals(-1,boundary("unknown",30));
    }
    @Test public void alternatingLabelsDoNotPoolDisconnectedMovementVotes() {
        add(0,0,9); add(100,10,1); add(200,20,9); add(300,30,1); add(400,40,9);
        assertEquals(-1,boundary("unknown",40));
    }
    @Test public void newRunAfterSamplingGapUsesItsOwnBoundary() {
        add(0,0,9); add(100,10,9); add(200,20,9);
        add(300,100,9); add(400,110,9); add(500,120,9);
        assertEquals(3,boundary("unknown",120));
    }
    @Test public void oneNewFixAfterGapDoesNotReuseOlderConfirmation() {
        add(0,0,9); add(100,10,9); add(200,20,9); add(300,100,9);
        assertEquals(-1,boundary("unknown",100));
    }
    @Test public void duplicateCallbacksCannotSupplyThreeObservations() {
        add(0,0,9); add(100,20,9); add(100,20,9);
        assertEquals(-1,boundary("unknown",20));
    }
    @Test public void futureAndStaleObservationsCannotConfirm() {
        add(0,0,9); add(100,10,9); add(200,20,9);
        assertEquals(-1,boundary("unknown",10));
        assertEquals(-1,boundary("unknown",81));
    }
    @Test public void walkingJumpCannotCorroborateAChangeDespiteSlowSpeedField() {
        add(0,0,1); add(20,10,1); add(1000,20,1);
        assertEquals(-1,boundary("walking",20));
    }
    @Test public void cyclingKeepsItsMeasuredBoundaryAndOriginalIndices() {
        add(0,0,0); add(30,10,3); add(70,20,3); add(110,30,3);
        assertEquals(1,boundary("cycling",30));
        assertEquals(-1,CaptureMovementEvidence.modeChangeStart(samples,"cycling",21000,31000));
    }
    @Test public void missingSpeedCannotSupportAChange() {
        add(0,0,Double.NaN); add(100,10,Double.NaN); add(200,20,Double.NaN);
        assertEquals(-1,boundary("unknown",20));
    }
}
