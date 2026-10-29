package com.roadprints.capture;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class WalkingStartEvidenceTest {
    private FootTraceValidator.Sample p(double metres,double accuracy,long time,double speed) {
        return new FootTraceValidator.Sample(metres/111195,0,accuracy,time,speed,0);
    }
    @Test public void sustainedWalkingConfirmsFromBufferedEvidence() {
        assertTrue(WalkingStartEvidence.confirmed(Arrays.asList(p(0,5,100000,1),
                p(40,5,130000,1),p(80,5,160000,1)),160000));
    }
    @Test public void IndoorDriftPoorAccuracyAndAStaleFixCannotStartWalking() {
        assertFalse(WalkingStartEvidence.confirmed(Arrays.asList(p(0,25,100000,1),
                p(40,25,130000,1),p(0,25,160000,1)),160000));
        assertFalse(WalkingStartEvidence.confirmed(Arrays.asList(p(0,150,100000,1),
                p(40,5,130000,1),p(80,5,160000,1)),160000));
        assertFalse(WalkingStartEvidence.confirmed(Arrays.asList(p(0,5,100000,1),
                p(40,5,130000,1),p(80,5,160000,1)),300000));
    }
    @Test public void VehicleSpeedsAndImpossibleJumpsDoNotStartWalking() {
        assertFalse(WalkingStartEvidence.confirmed(Arrays.asList(p(0,5,100000,10),
                p(300,5,130000,10),p(600,5,160000,10)),160000));
        assertFalse(WalkingStartEvidence.confirmed(Arrays.asList(p(0,5,100000,1),
                p(500,5,105000,1),p(1000,5,110000,1)),110000));
    }
}
