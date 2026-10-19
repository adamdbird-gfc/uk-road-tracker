package com.roadprints.capture;

import java.util.*;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

/** Invented relative geometry; exercise the combined visit/continuity policies. */
@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class PlaceVisitReliabilityTest {
    private FootTraceValidator.Sample p(double x,double y,long seconds,double speed,int index) {
        return new FootTraceValidator.Sample(x/111195,y/111195,5,1000+seconds*1000,speed,index);
    }
    private JSONObject stop(double radius) throws Exception {
        JSONArray ring=new JSONArray();
        for(double[] xy:new double[][]{{-radius,-radius},{radius,-radius},{radius,radius},{-radius,radius},{-radius,-radius}})
            ring.put(new JSONObject().put("lon",xy[0]/111195).put("lat",xy[1]/111195));
        return new JSONObject().put("id","site").put("latitude",0).put("longitude",0)
                .put("accuracy_m",5).put("kind","school").put("classification","supported").put("site_geometry",ring);
    }
    @Test public void sparseMorningWalkKeepsMovementBeforeLeavingTheBoundary() throws Exception {
        List<FootTraceValidator.Sample> points=new ArrayList<>();
        for(int i=0;i<7;i++)points.add(p(i*20,0,i*60,1.3,i));
        PlaceVisitEvidence.Decision d=PlaceVisitEvidence.inspect(stop(60),points);
        assertNotNull(d);assertEquals("walking",d.mode);assertEquals(0,d.startIndex);
    }
    @Test public void fastVehicleDepartureUsesVehicleEvidenceNotAWalkingGate() throws Exception {
        List<FootTraceValidator.Sample> points=Arrays.asList(p(100,0,0,20,0),p(500,0,30,20,1),p(900,0,60,20,2));
        PlaceVisitEvidence.Decision d=PlaceVisitEvidence.inspect(stop(1000),points,"unknown");
        assertNotNull(d);assertEquals("unknown",d.mode);assertEquals(0,d.startIndex);
    }
    @Test public void recentWalkToCarInsideCampusGetsItsOwnContinuousLeg() throws Exception {
        List<FootTraceValidator.Sample> points=Arrays.asList(p(0,0,0,1.3,0),p(20,0,15,1.3,1),p(40,0,30,1.3,2),
                p(40,0,45,0,3),p(70,0,60,12,4),p(190,0,70,12,5),p(310,0,80,12,6));
        PlaceVisitEvidence.Decision d=PlaceVisitEvidence.inspect(stop(1000),points,"unknown");
        assertNotNull(d);assertEquals("walking",d.mode);assertEquals(0,d.startIndex);
        assertEquals(4,d.transitionIndex);assertEquals("unknown",d.nextMode);
        assertEquals(points.size()-1,d.transitionIndex+(points.size()-d.transitionIndex)-1);
    }
    @Test public void oldOutsideMovementFollowedByAStopCannotRestartTheVisit() throws Exception {
        List<FootTraceValidator.Sample> points=new ArrayList<>();
        for(int i=0;i<5;i++)points.add(p(320+i*20,0,i*15,1.3,i));
        for(int i=5;i<15;i++)points.add(p(400,0,i*15,0,i));
        assertNull(PlaceVisitEvidence.inspect(stop(300),points));
        assertNull(PlaceVisitEvidence.inspect(stop(300),points.subList(0,5),null,600000));
    }
    @Test public void sixtySecondSamplesStillAllowClosedRunningLaps() throws Exception {
        List<FootTraceValidator.Sample> points=new ArrayList<>();
        for(int i=0;i<=12;i++)points.add(p(100*Math.cos(i*Math.PI/6),100*Math.sin(i*Math.PI/6),i*60,2.7,i));
        PlaceVisitEvidence.Decision d=PlaceVisitEvidence.inspect(stop(500),points,"running");
        assertNotNull(d);assertEquals("running",d.mode);assertEquals(0,d.startIndex);
        assertTrue(FootTraceValidator.metres(points.get(0),points.get(12))<1);
    }
    @Test public void samplingGapCannotCombineSeparateIncidentalActivityIntoExercise() throws Exception {
        List<FootTraceValidator.Sample> points=new ArrayList<>();
        for(int i=0;i<4;i++)points.add(p(i*60,0,i*30,2.7,i));
        for(int i=4;i<8;i++)points.add(p((i-4)*60,0,600+(i-4)*30,2.7,i));
        assertNull(PlaceVisitEvidence.inspect(stop(1000),points,"running"));
    }
    @Test public void campusArrivalUsesDwellOnsetAndSurvivesCheckpoint() {
        ContextMap.Evidence site=new ContextMap.Evidence();site.available=true;site.destination=true;site.site=true;site.kind="school";
        ContextJourneyModel model=new ContextJourneyModel();
        assertNull(model.sample("walking",0,1000,0,-.0018,5,1,site,false));
        assertNull(model.sample("walking",1,61000,0,0,5,0,site,false));
        assertNull(model.sample("walking",2,121000,0,0,5,0,site,false));
        String[] checkpoint=model.checkpoint();assertEquals("4",checkpoint[0]);
        ContextJourneyModel restored=new ContextJourneyModel();restored.restore(checkpoint,3);
        // An exit before a campus's two-minute dwell cannot be promoted to arrival.
        assertNull(restored.sample("walking",3,151000,0,.0006,5,1,site,false));
        ContextJourneyModel.Decision arrival=model.sample("walking",3,181000,0,0,5,0,site,false);
        assertNotNull(arrival);assertEquals(1,arrival.boundary);assertNull(arrival.nextMode);
        assertEquals("arrival_school",arrival.reason);
        // Previous checkpoints are still accepted.
        String[] v3=Arrays.copyOf(checkpoint,23);v3[0]="3";
        restored.restore(v3,3);assertEquals("4",restored.checkpoint()[0]);
    }
}
