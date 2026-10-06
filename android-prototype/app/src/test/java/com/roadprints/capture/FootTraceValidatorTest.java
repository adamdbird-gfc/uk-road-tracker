package com.roadprints.capture;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class FootTraceValidatorTest {
    @Test public void unrecoverableGapPreservesEvidenceAndDoesNotClaimValidation() {
        List<FootTraceValidator.Sample> points=Arrays.asList(p(0,0,0,5,0),p(10000,0,1,5,1),
                p(10010,0,2,5,2),p(10020,0,3,5,3),p(10030,0,4,5,4));
        FootTraceValidator.Result result=FootTraceValidator.validate(points);
        assertFalse(result.resolved); assertEquals(points,result.samples);
    }
    private FootTraceValidator.Sample p(double east,double north,long seconds,double accuracy,int index) {
        return new FootTraceValidator.Sample(east/111195,north/111195,accuracy,100000+seconds*1000,1,index);
    }
    @Test public void rejectsFastJumpEvenWithClaimedGoodAccuracy() {
        List<FootTraceValidator.Sample> points=Arrays.asList(p(0,0,0,15,0),p(10,0,10,15,1),
                p(250,0,20,15,2),p(30,0,30,15,3),p(40,0,40,15,4));
        FootTraceValidator.Result result=FootTraceValidator.validate(points);
        assertTrue(result.timed); assertTrue(result.removed>=1);
        assertEquals(40,result.distance,.1);
        assertFalse(result.samples.stream().anyMatch(point->point.index==2));
        assertFalse(FootTraceValidator.plausible(points.get(1),points.get(2)));
    }
    @Test public void preservesPreciseRightAngleAndOutAndBack() {
        List<FootTraceValidator.Sample> turn=Arrays.asList(p(0,0,0,3,0),p(40,0,40,3,1),p(40,40,80,3,2));
        assertEquals(3,FootTraceValidator.validate(turn).samples.size());
        List<FootTraceValidator.Sample> returned=Arrays.asList(p(0,0,0,3,0),p(60,0,60,3,1),p(0,0,120,3,2));
        assertEquals(120,FootTraceValidator.validate(returned).distance,.1);
    }
    @Test public void sparseUntimedRecordingIsNotShortened() {
        List<FootTraceValidator.Sample> points=Arrays.asList(new FootTraceValidator.Sample(0,0,0,0,-1,0),
                new FootTraceValidator.Sample(.01,.01,0,0,-1,1),new FootTraceValidator.Sample(.02,0,0,0,-1,2));
        FootTraceValidator.Result result=FootTraceValidator.validate(points);
        assertFalse(result.timed); assertEquals(0,result.removed); assertEquals(points,result.samples);
    }
    @Test public void sameTimestampJumpDoesNotAddDistanceAndLongRealGapRemains() {
        FootTraceValidator.Sample a=p(0,0,0,5,0),b=p(500,0,0,5,1),c=p(500,0,300,5,2);
        assertFalse(FootTraceValidator.plausible(a,b)); assertTrue(FootTraceValidator.plausible(a,c));
    }
    @Test public void reportedRunningSpeedAllowsGenuineFastFootMovement() {
        FootTraceValidator.Sample a=new FootTraceValidator.Sample(0,0,3,100000,6,0);
        FootTraceValidator.Sample b=new FootTraceValidator.Sample(60/111195.0,0,3,110000,6,1);
        assertTrue(FootTraceValidator.plausible(a,b));
    }
}
