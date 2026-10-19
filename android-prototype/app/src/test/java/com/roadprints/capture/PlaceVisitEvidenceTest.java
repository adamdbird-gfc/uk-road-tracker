package com.roadprints.capture;

import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class PlaceVisitEvidenceTest {
    private FootTraceValidator.Sample p(double x,double y,long t,double speed,int i) {
        return new FootTraceValidator.Sample(x/111195,y/111195,3,t,speed,i);
    }
    private JSONArray ring(double radius) throws Exception {
        JSONArray ring=new JSONArray();
        for(double[] xy:new double[][]{{-radius,-radius},{radius,-radius},{radius,radius},{-radius,radius},{-radius,-radius}})
            ring.put(new JSONObject().put("lat",xy[1]/111195).put("lon",xy[0]/111195));
        return ring;
    }
    private JSONObject stop(boolean mapped) throws Exception {
        JSONObject stop=new JSONObject().put("id","test-stop").put("latitude",0).put("longitude",0)
                .put("accuracy_m",5).put("kind",mapped?"school":"estimated").put("classification",mapped?"supported":"estimated");
        if(mapped)stop.put("site_geometry",ring(300));
        return stop;
    }
    @Test public void movementBetweenSchoolBuildingsRemainsAVisitDespiteRunningHint() throws Exception {
        List<FootTraceValidator.Sample> points=new ArrayList<>();
        for(int i=0;i<180;i++)points.add(p(i%20*10,0,1000+i*10000L,i%20<8?1.3:0,i));
        assertNull(PlaceVisitEvidence.inspect(stop(true),points,"running"));
    }
    @Test public void parkrunLoopInsideCollegeIsPreservedWithoutCrossingSiteBoundary() throws Exception {
        List<FootTraceValidator.Sample> points=new ArrayList<>();
        for(int i=0;i<=24;i++)points.add(p(100*Math.cos(i*Math.PI/12),100*Math.sin(i*Math.PI/12),1000+i*10000L,2.7,i));
        assertTrue(FootTraceValidator.metres(points.get(0),points.get(24))<1);
        PlaceVisitEvidence.Decision d=PlaceVisitEvidence.inspect(stop(true),points);
        assertNotNull(d);assertEquals("running",d.mode);assertEquals(0,d.startIndex);
        assertEquals("sustained_activity_within_place",d.reason);
    }
    @Test public void shortRunAtSchoolIsNotASeparateExerciseSession() throws Exception {
        List<FootTraceValidator.Sample> points=new ArrayList<>();
        for(int i=0;i<7;i++)points.add(p(i*10,0,1000+i*10000L,i<4?2.7:0,i));
        assertNull(PlaceVisitEvidence.inspect(stop(true),points));
    }
    @Test public void departureRequiresFreshSustainedEvidenceBeyondSiteEdge() throws Exception {
        List<FootTraceValidator.Sample> points=new ArrayList<>();
        points.add(p(320,0,1000,1.3,0));points.add(p(340,0,16000,1.3,1));
        assertNull(PlaceVisitEvidence.inspect(stop(true),points));
        points.add(p(360,0,31000,1.3,2));
        PlaceVisitEvidence.Decision d=PlaceVisitEvidence.inspect(stop(true),points);
        assertNotNull(d);assertEquals("departure_from_visit",d.reason);
        assertEquals("walking",d.mode);assertEquals(0,d.startIndex);
    }
    @Test public void boundaryJitterAndSingleJumpDoNotBecomeDeparture() throws Exception {
        List<FootTraceValidator.Sample> points=new ArrayList<>();
        for(int i=0;i<10;i++)points.add(p(i%2==0?298:310,0,1000+i*10000L,1,i));
        assertNull(PlaceVisitEvidence.inspect(stop(true),points));
        points.clear();
        for(int i=0;i<5;i++)points.add(p(500,0,1000+i*10000L,0,i));
        assertNull(PlaceVisitEvidence.inspect(stop(true),points));
    }
    @Test public void offlineStayEstimateAllowsGenuineDepartureWithoutInventingASite() throws Exception {
        JSONObject estimated=stop(false);
        assertFalse(PlaceVisitEvidence.outside(estimated,p(40,0,1000,1,0)));
        List<FootTraceValidator.Sample> points=new ArrayList<>();
        for(int i=0;i<4;i++)points.add(p(100+i*20,0,1000+i*15000L,1.3,i));
        assertNotNull(PlaceVisitEvidence.inspect(estimated,points));
    }
    @Test public void cyclingLoopIsNotMistakenForIncidentalSchoolWalking() throws Exception {
        List<FootTraceValidator.Sample> points=new ArrayList<>();
        for(int i=0;i<18;i++)points.add(p(140*Math.cos(i*.3),140*Math.sin(i*.3),1000+i*10000L,4.2,i));
        assertEquals("cycling",PlaceVisitEvidence.inspect(stop(true),points,"cycling").mode);
    }
    @Test public void sustainedWalkingSessionWithinLargeSiteAlsoQualifies() throws Exception {
        JSONObject large=stop(true).put("site_geometry",ring(500));
        List<FootTraceValidator.Sample> points=new ArrayList<>();
        for(int i=0;i<=40;i++)points.add(p(250*Math.cos(i*.1),250*Math.sin(i*.1),1000+i*10000L,1.5,i));
        assertEquals("walking",PlaceVisitEvidence.inspect(large,points).mode);
    }
    @Test public void rejectedStopsAndStationsKeepTheirExistingModels() throws Exception {
        assertFalse(PlaceVisitEvidence.eligible(stop(true).put("classification","rejected")));
        assertFalse(PlaceVisitEvidence.eligible(stop(true).put("kind","station")));
    }
    @Test public void mappedBoundarySurvivesSavingAndReloadingStop() throws Exception {
        JSONObject loaded=new JSONObject(stop(true).toString());
        assertFalse(PlaceVisitEvidence.outside(loaded,p(200,0,1000,1,0)));
        assertTrue(PlaceVisitEvidence.outside(loaded,p(400,0,1000,1,0)));
    }
    @Test public void schoolDwellSurvivesMixedProviderSpeedSpikesButNotRealDeparture() {
        WalkingStillnessDetector dwell=new WalkingStillnessDetector();
        for(int i=0;i<70;i++)dwell.accept(p(i%2*3,0,1000+i*5000L,i%3==2?1.3:0,i));
        assertTrue(dwell.canFinish(346000));
        dwell.accept(p(60,0,366000,1.3,70));
        assertFalse(dwell.quiet(366000));
    }
    @Test public void runningModeNeedsRunningSpeedsRatherThanAnAndroidLabel() {
        List<FootTraceValidator.Sample> points=new ArrayList<>();
        for(int i=0;i<8;i++)points.add(p(i*15,0,1000+i*10000L,1.3,i));
        assertFalse(CaptureMovementEvidence.confirmsMode(points,"running",1000,71000));
        points.clear();
        for(int i=0;i<8;i++)points.add(p(i*30,0,1000+i*10000L,3,i));
        assertTrue(CaptureMovementEvidence.confirmsMode(points,"running",1000,71000));
    }
}
