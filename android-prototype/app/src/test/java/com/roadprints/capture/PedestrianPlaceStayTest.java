package com.roadprints.capture;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** Synthetic relative geometry only: no user locations or journey exports. */
public class PedestrianPlaceStayTest {
    private FootTraceValidator.Sample point(double x,double y,double accuracy,long time,double speed,int index){
        return new FootTraceValidator.Sample(x/111195.0,y/111195.0,accuracy,time,speed,index);
    }
    private List<FootTraceValidator.Sample> drift(int count){
        List<FootTraceValidator.Sample> out=new ArrayList<>();
        for(int i=0;i<count;i++){
            double angle=i*2.4;
            out.add(point(30*Math.cos(angle),30*Math.sin(angle),25,100000+i*10000L,i%7==0?2.4:.2,i));
        }
        return out;
    }
    @Test public void venueDriftAndIsolatedSpeedSpikesConfirmAStopWithoutAFalseDeparture(){
        List<FootTraceValidator.Sample> samples=drift(180);WalkingStillnessDetector detector=new WalkingStillnessDetector();
        long first=0;
        for(FootTraceValidator.Sample p:samples){detector.accept(p);if(first==0&&detector.canFinish(p.time))first=p.time;}
        assertTrue(first>0&&first<=460000);assertTrue(detector.canFinish(samples.get(samples.size()-1).time));
        assertFalse(CaptureMovementEvidence.hasJourneyMovement(samples,"walking"));
        assertFalse(PedestrianMotionWindow.sustainedDeparture(samples));
        assertTrue(WalkingCaptureEvidence.prepare(samples).route.distance<80);
    }
    @Test public void genuineSlowWalkAndRunningLapsRemainTravel(){
        for(boolean running:new boolean[]{false,true}){
            List<FootTraceValidator.Sample> samples=new ArrayList<>();WalkingStillnessDetector detector=new WalkingStillnessDetector();
            for(int i=0;i<120;i++){
                double x=running?60*Math.cos(i*.4):i*4,y=running?60*Math.sin(i*.4):0;
                FootTraceValidator.Sample p=point(x,y,5,100000+i*10000L,running?2.4:.4,i);
                samples.add(p);detector.accept(p);assertFalse(detector.canFinish(p.time));
            }
            assertTrue(CaptureMovementEvidence.hasJourneyMovement(samples,running?"running":"walking"));
            assertTrue(PedestrianMotionWindow.sustainedDeparture(samples));
            assertEquals(0,WalkingCaptureEvidence.prepare(samples).stationaryPoints);
        }
    }
    @Test public void returningNearTheStartDoesNotEraseALunchWalk(){
        List<FootTraceValidator.Sample> points=new ArrayList<>();
        for(int i=0;i<=40;i++)points.add(point(i<20?i*10:(40-i)*10,0,8,100000+i*10000L,1,i));
        assertTrue(CaptureMovementEvidence.hasJourneyMovement(points,"walking"));
        assertEquals(0,WalkingCaptureEvidence.prepare(points).stationaryPoints);
    }
    @Test public void stayBetweenTwoWalksLosesDriftAndKeepsDepartureEvidence(){
        List<FootTraceValidator.Sample> points=new ArrayList<>();
        for(int i=0;i<8;i++)points.add(point(-140+i*20,0,5,100000+i*20000L,1,points.size()));
        for(int i=0;i<90;i++)points.add(point(i%3*7,0,25,260000+i*10000L,i%7==0?2:.1,points.size()));
        int departure=points.size();
        for(int i=0;i<15;i++)points.add(point(i*12,0,5,1160000+i*10000L,1.2,points.size()));
        WalkingCaptureEvidence.Result result=WalkingCaptureEvidence.prepare(points);
        assertTrue(result.stationaryPoints>70);assertEquals(0,result.stationaryTail);
        assertTrue(result.route.resolved);assertTrue(result.route.distance<400);
        assertTrue(result.route.samples.stream().anyMatch(p->p.index==departure));
        assertEquals(points.size()-1,result.route.samples.get(result.route.samples.size()-1).index);
        assertEquals(113,points.size()); // Input is untouched.
    }
    @Test public void staleInaccurateAndDuplicatedFixesCannotSupplyDwellOrDeparture(){
        WalkingStillnessDetector detector=new WalkingStillnessDetector();
        detector.accept(point(0,0,5,100000,0,0));
        for(int i=1;i<50;i++)detector.accept(point(i,0,150,100000+i*10000L,0,i));
        assertFalse(detector.canFinish(600000));
        List<FootTraceValidator.Sample> duplicates=new ArrayList<>();
        for(int i=0;i<30;i++)duplicates.add(point(i*20,0,5,100000,1,i));
        assertFalse(PedestrianMotionWindow.sustainedDeparture(duplicates));
        assertFalse(CaptureMovementEvidence.hasJourneyMovement(duplicates,"walking"));
    }
    @Test public void longSamplingGapDoesNotJoinTwoShortMovementVotes(){
        List<FootTraceValidator.Sample> points=new ArrayList<>();
        for(int i=0;i<3;i++)points.add(point(i*20,0,5,100000+i*10000L,1,i));
        for(int i=0;i<3;i++)points.add(point(100+i*20,0,5,500000+i*10000L,1,i+3));
        assertFalse(PedestrianMotionWindow.sustainedDeparture(points));
    }
}
