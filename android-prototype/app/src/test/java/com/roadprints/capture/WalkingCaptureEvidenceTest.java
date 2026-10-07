package com.roadprints.capture;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class WalkingCaptureEvidenceTest {
    private FootTraceValidator.Sample p(double metres,double accuracy,long time,int index) {
        return new FootTraceValidator.Sample(metres/111195,0,accuracy,time,1,index);
    }
    @Test public void stationaryIndoorDriftDoesNotBecomeTravelOrPreventAStop() {
        WalkingStillnessDetector d=new WalkingStillnessDetector();
        d.accept(p(0,10,100000,0)); d.accept(p(12,10,130000,1));
        d.accept(p(60,120,160000,2)); d.accept(p(8,20,180000,3));
        d.accept(p(4,25,400000,4));
        // A long loss of reliable evidence cannot corroborate a stop.
        assertFalse(d.canFinish(400000));
        for(int i=0;i<11;i++)d.accept(p(5+i%3,20,430000+i*30000,5+i));
        assertTrue(d.canFinish(730000));
        assertFalse(d.canFinish(900000));
    }
    @Test public void movingAgainClearsConfirmedStillness() {
        WalkingStillnessDetector d=new WalkingStillnessDetector();
        for(int i=0;i<12;i++)d.accept(p(i%3,5,100000+i*30000,i));
        assertTrue(d.canFinish(430000));
        d.accept(p(80,5,460000,12));
        assertFalse(d.canFinish(460000)); assertEquals(-1,d.confirmedEndpoint());
    }
    @Test public void continuousWalkingAndMissingGpsDoNotCountAsStillness() {
        WalkingStillnessDetector d=new WalkingStillnessDetector();
        for(int i=0;i<20;i++)d.accept(p(i*35,5,100000+i*30000,i));
        assertFalse(d.canFinish(670000)); assertEquals(-1,d.confirmedEndpoint());
        WalkingStillnessDetector missing=new WalkingStillnessDetector();
        missing.accept(p(0,5,100000,0));
        assertFalse(missing.canFinish(500000));
    }
    @Test public void cleanupDropsInvalidAnchorAndOnlyCorroboratedStationaryTail() {
        List<FootTraceValidator.Sample> input=new ArrayList<>();
        input.add(p(-20,178,0,0));
        for(int i=1;i<=6;i++)input.add(p(i*40,5,100000+i*30000,i));
        for(int i=7;i<=19;i++)input.add(p(240+i%3,15,100000+i*30000,i));
        WalkingCaptureEvidence.Result result=WalkingCaptureEvidence.prepare(input);
        assertTrue(result.route.resolved); assertEquals(1,result.rejectedFixes);
        assertTrue(result.stationaryTail>0); assertTrue(result.route.distance<250);
        assertEquals(0,input.get(0).time); assertEquals(20,input.size());
    }
    @Test public void badMiddleFixesDoNotEraseGoodTurnsOrUntimedDrawnGeometry() {
        List<FootTraceValidator.Sample> input=Arrays.asList(p(0,5,100000,0),p(500,150,110000,1),
                p(20,5,120000,2),p(40,5,140000,3));
        WalkingCaptureEvidence.Result result=WalkingCaptureEvidence.prepare(input);
        assertEquals(1,result.rejectedFixes);assertEquals(40,result.route.distance,1);
        List<FootTraceValidator.Sample> drawn=Arrays.asList(p(0,5,100000,0),p(10,0,0,1),p(20,5,120000,2));
        assertEquals(3,WalkingCaptureEvidence.prepare(drawn).route.samples.size());
    }
    @Test public void shortRepeatedWalkingLoopsAreMovementEvenInsideTheStopRadius() {
        WalkingStillnessDetector d=new WalkingStillnessDetector();
        for(int i=0;i<30;i++)d.accept(p(i%2==0?0:30,3,100000+i*15000,i));
        assertFalse(d.canFinish(535000)); assertEquals(-1,d.confirmedEndpoint());
    }
}
