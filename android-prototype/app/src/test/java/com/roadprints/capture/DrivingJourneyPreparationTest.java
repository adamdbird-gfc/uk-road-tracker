package com.roadprints.capture;

import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class DrivingJourneyPreparationTest {
    private JSONObject point(double x,double y) throws Exception {
        return new JSONObject().put("lng",x/111195).put("lat",y/111195);
    }
    private JSONObject journey(JSONArray points) throws Exception {
        JSONArray samples=new JSONArray();
        for(int i=0;i<points.length();i++) samples.put(new JSONArray()
                .put(points.getJSONObject(i).getDouble("lng")).put(points.getJSONObject(i).getDouble("lat"))
                .put(5).put(100000+i*9000).put(0));
        return new JSONObject().put("capture_route_samples",samples);
    }
    private JSONObject partial(int count,int... omitted) throws Exception {
        JSONArray kept=new JSONArray(), dropped=new JSONArray();
        for(int i=0;i<count;i++) {
            boolean remove=false; for(int index:omitted) if(i==index) remove=true;
            (remove?dropped:kept).put(i);
        }
        return new JSONObject().put("input_points",count).put("matched_tracepoints",kept.length())
                .put("matched_point_indices",kept).put("unmatched_point_indices",dropped).put("failed_sections",new JSONArray());
    }
    @Test public void keepsBothEndpointsAndSharpTurnsWhileCollapsingStoppedFixes() throws Exception {
        JSONArray points=new JSONArray().put(point(0,0)).put(point(100,0)).put(point(100,0))
                .put(point(100,0)).put(point(110,0)).put(point(110,100));
        String original=points.toString();
        TimelineRoadPreparation.Prepared prepared=DrivingJourneyPreparation.prepare(journey(points),points);
        JSONArray kept=prepared.sections.get(0);
        assertEquals(4,kept.length()); assertEquals(points.getJSONObject(0).toString(),kept.getJSONObject(0).toString());
        assertEquals(points.getJSONObject(4).toString(),kept.getJSONObject(2).toString());
        assertEquals(points.getJSONObject(5).toString(),kept.getJSONObject(3).toString());
        assertEquals(original,points.toString());assertEquals("android_capture_available",prepared.details.getString("evidence_status"));
    }
    @Test public void misalignedEvidenceCannotRemoveMovement() throws Exception {
        JSONArray points=new JSONArray().put(point(0,0)).put(point(100,0));JSONObject j=journey(points);
        j.getJSONArray("capture_route_samples").getJSONArray(1).put(0,1);
        try { DrivingJourneyPreparation.prepare(j,points);fail(); } catch(IllegalStateException expected) { }
    }
    @Test public void retryRequiresNearbyMatchedInteriorEvidenceAndNoLostEndpoints() throws Exception {
        JSONArray points=new JSONArray().put(point(0,0)).put(point(100,0)).put(point(100,0)).put(point(200,0));
        assertEquals(3,DrivingJourneyPreparation.retryPoints(partial(4,2),points).length());
        assertNull(DrivingJourneyPreparation.retryPoints(partial(4,0),points));
        assertNull(DrivingJourneyPreparation.retryPoints(partial(4,3),points));
        points.put(2,point(100,40)); assertNull(DrivingJourneyPreparation.retryPoints(partial(4,2),points));
    }
    @Test public void retryAllowsSmallGpsUncertaintyButStillProtectsRecordedTurns() throws Exception {
        JSONArray points=new JSONArray().put(point(0,0)).put(point(10,7)).put(point(100,0));
        JSONObject j=journey(points);j.getJSONArray("capture_route_samples").getJSONArray(1).put(2,18);
        assertNull(DrivingJourneyPreparation.retryPoints(partial(3,1),points));
        assertNotNull(DrivingJourneyPreparation.retryPoints(partial(3,1),points,j));
        points.put(1,point(10,20));assertNull(DrivingJourneyPreparation.retryPoints(partial(3,1),points,j));
    }
    @Test public void correctedGeometryIsNotRebuiltFromOriginalCapture() throws Exception {
        JSONArray points=new JSONArray().put(point(0,0)).put(point(100,0));JSONObject j=journey(points);
        assertTrue(DrivingJourneyPreparation.aligned(j,points));points.put(1,point(200,0));
        assertFalse(DrivingJourneyPreparation.aligned(j,points));
    }
    @Test public void realInteriorMovementAndFailedSectionsAreNeverSilentlyAccepted() throws Exception {
        JSONArray points=new JSONArray().put(point(0,0)).put(point(100,0)).put(point(200,0));
        assertNull(DrivingJourneyPreparation.retryPoints(partial(3,1),points));
        points.put(1,point(1,0));JSONObject result=partial(3,1);
        result.getJSONArray("failed_sections").put(new JSONObject());
        assertNull(DrivingJourneyPreparation.retryPoints(result,points));
    }
    @Test public void excessiveMatchedDistanceIsBlocked() throws Exception {
        JSONArray points=new JSONArray().put(point(0,0)).put(point(1000,0));
        DrivingJourneyPreparation.validateDistance(new JSONObject().put("matched_distance_m",1200),points);
        try { DrivingJourneyPreparation.validateDistance(new JSONObject().put("matched_distance_m",2000),points);fail(); }
        catch(IllegalStateException expected) { }
    }
    @Test public void movingVehicleResetsStopEvidenceAndGpsSilenceCannotConfirmArrival() {
        WalkingStillnessDetector detector=new WalkingStillnessDetector();
        for(int i=0;i<=10;i++) detector.accept(new FootTraceValidator.Sample(0,0,5,100000+i*30000,0,i),true);
        assertTrue(detector.canFinish(400000));assertFalse(detector.canFinish(600001));
        detector.accept(new FootTraceValidator.Sample(.001,0,5,409000,13,11),true);
        assertFalse(detector.quiet(409000));
    }
}
