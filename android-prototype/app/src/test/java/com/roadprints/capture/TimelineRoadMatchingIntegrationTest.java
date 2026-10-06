package com.roadprints.capture;

import android.content.Context;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class TimelineRoadMatchingIntegrationTest {
    private Context app; private MatchingCoordinator coordinator;
    @Before public void setup(){app=RuntimeEnvironment.getApplication();JourneyStore.deleteAll(app);}
    @After public void teardown(){if(coordinator!=null)coordinator.shutdownForTest();JourneyStore.deleteAll(app);}
    private JSONObject journey() throws Exception {
        return new JSONObject("{\"journey_id\":\"road\",\"mode\":\"driving\",\"distance_meters\":590,\"processing_status\":\"pending\",\"capture_quality\":{\"gps_points\":4,\"source_route_points\":4},\"source\":{\"type\":\"timeline_import\"},\"route_geometry\":{\"type\":\"LineString\",\"coordinates\":[[0,0],[0.001,0],[0.1,0],[0.101,0]]},\"timeline_match_evidence\":{\"point_times_ms\":[0,60000,9300000,9360000],\"end_appended\":false}}");
    }
    private JSONObject result() throws Exception {
        return new JSONObject("{\"input_points\":2,\"matched_tracepoints\":2,\"matched_distance_m\":110,\"matched_distance_is_deduplicated\":true,\"failed_sections\":[],\"geojson\":{\"features\":[{\"geometry\":{\"type\":\"LineString\",\"coordinates\":[[0,0],[0.001,0]]}}]}}");
    }
    private void await() throws Exception {
        long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
        while(coordinator.snapshot().state!=MatchingCoordinator.State.COMPLETE&&System.nanoTime()<end)Thread.sleep(10);
        assertEquals(MatchingCoordinator.State.COMPLETE,coordinator.snapshot().state);
    }
    @Test public void gapsAreMatchedSeparatelyAndDistanceDoesNotIncludeAnInventedConnector() throws Exception {
        JSONObject j=journey();String original=j.getJSONObject("route_geometry").toString();JourneyStore.save(app,j);
        AtomicInteger calls=new AtomicInteger();coordinator=new MatchingCoordinator(app,(foot,payload)->{
            assertFalse(foot);assertTrue(payload.getBoolean("road_recovery"));assertEquals(2,payload.getJSONArray("points").length());
            calls.incrementAndGet();return result();
        });coordinator.start("road");await();
        assertEquals(2,calls.get());assertEquals(1,coordinator.snapshot().matched);
        JSONObject stored=JourneyStore.get(app,"road");assertEquals(220,stored.getDouble("distance_meters"),.01);
        assertEquals(590,stored.getDouble("original_distance_meters"),.01);
        assertEquals(original,stored.getJSONObject("route_geometry").toString());
        assertEquals(2,stored.getJSONObject("processing_result").getJSONObject("geojson").getJSONArray("features").length());
    }
    @Test public void failedSectionDoesNotReplacePreviousMatchOrDistanceAndExportsFailureIndices() throws Exception {
        JSONObject j=journey().put("processing_result",result());JourneyStore.save(app,j);
        AtomicInteger calls=new AtomicInteger();coordinator=new MatchingCoordinator(app,(foot,payload)->{
            if(calls.incrementAndGet()==1)return result();
            return result().put("matched_tracepoints",1).put("unmatched_point_indices",new JSONArray("[1]"))
                    .put("failed_sections",new JSONArray("[{\"points\":2,\"start_point_index\":0,\"end_point_index\":1,\"detail\":\"NoMatch\"}]"));
        });coordinator.start("road");await();assertEquals(1,coordinator.snapshot().failed);
        JSONObject stored=JourneyStore.get(app,"road");assertEquals(590,stored.getDouble("distance_meters"),.01);
        assertEquals(110,stored.getJSONObject("processing_result").getDouble("matched_distance_m"),.01);
        JSONObject d=stored.getJSONObject("last_match_attempt").getJSONArray("section_diagnostics").getJSONObject(1);
        assertEquals(1,d.getJSONArray("unmatched_point_indices").getInt(0));assertEquals(2,d.getJSONArray("submitted_points").length());
    }
    @Test public void unverifiedDistanceCannotReplaceTheOriginalTimelineTotal() throws Exception {
        JourneyStore.save(app,journey());
        coordinator=new MatchingCoordinator(app,(foot,payload)->result().put("matched_distance_is_deduplicated",false));
        coordinator.start("road");await();assertEquals(1,coordinator.snapshot().failed);
        JSONObject stored=JourneyStore.get(app,"road");assertEquals(590,stored.getDouble("distance_meters"),.01);
        assertFalse(stored.has("processing_result"));
    }
    private JSONObject endpointJourney() throws Exception {
        JSONObject j=journey();
        j.getJSONObject("route_geometry").put("coordinates",new JSONArray("[[0,0],[0.001,0],[0.002,0],[0.003,0],[0.004,0]]"));
        j.getJSONObject("timeline_match_evidence").put("point_times_ms",new JSONArray("[0,60000,120000,180000,240000]"));
        j.getJSONObject("capture_quality").put("gps_points",5).put("source_route_points",5);
        return j;
    }
    private JSONObject endpointResult() throws Exception {
        return result().put("input_points",5).put("matched_tracepoints",4).put("matched_distance_m",400)
                .put("matched_point_indices",new JSONArray("[0,1,2,3]")).put("unmatched_point_indices",new JSONArray("[4]"))
                .put("failed_sections",new JSONArray("[{\"start_point_index\":3,\"end_point_index\":4,\"points\":2,\"detail\":\"NoMatch\"}]"));
    }
    @Test public void endpointOnlyFailureIsSavedAsClearlyLabelledPartialRoadMatch() throws Exception {
        JSONObject j=endpointJourney();String original=j.getJSONObject("route_geometry").toString();JourneyStore.save(app,j);
        coordinator=new MatchingCoordinator(app,(foot,payload)->endpointResult());coordinator.start("road");await();
        assertEquals(1,coordinator.snapshot().matched);
        JSONObject stored=JourneyStore.get(app,"road");
        assertTrue(stored.getJSONObject("processing_result").getBoolean("endpoint_partial_match"));
        assertEquals(1,stored.getJSONObject("processing_result").getJSONArray("failed_sections").length());
        assertEquals(400,stored.getDouble("distance_meters"),.01);assertEquals(590,stored.getDouble("original_distance_meters"),.01);
        assertEquals(original,stored.getJSONObject("route_geometry").toString());
    }
    @Test public void partialMatchWithoutTimelineTimingIsRejectedWithReloadInstruction() throws Exception {
        JSONObject j=endpointJourney();j.remove("timeline_match_evidence");JourneyStore.save(app,j);
        coordinator=new MatchingCoordinator(app,(foot,payload)->endpointResult());coordinator.start("road");await();
        assertEquals(1,coordinator.snapshot().failed);assertTrue(JourneyStore.get(app,"road").getString("error_summary").contains("Load Timeline Data"));
    }
    @Test public void interiorUnmatchedPointStillFailsWithOriginalDistancePreserved() throws Exception {
        JourneyStore.save(app,endpointJourney());
        coordinator=new MatchingCoordinator(app,(foot,payload)->endpointResult()
                .put("matched_point_indices",new JSONArray("[0,1,3,4]")).put("unmatched_point_indices",new JSONArray("[2]"))
                .put("failed_sections",new JSONArray("[{\"start_point_index\":1,\"end_point_index\":2,\"points\":2}]")));
        coordinator.start("road");await();assertEquals(1,coordinator.snapshot().failed);
        assertEquals(590,JourneyStore.get(app,"road").getDouble("distance_meters"),.01);
    }
    @Test public void aLaterInteriorFailureCannotBeHiddenByAnEarlierAcceptedEndpointPartial() throws Exception {
        JSONObject j=endpointJourney();JSONArray coords=j.getJSONObject("route_geometry").getJSONArray("coordinates");
        JSONArray times=j.getJSONObject("timeline_match_evidence").getJSONArray("point_times_ms");
        for(int i=0;i<5;i++){coords.put(new JSONArray().put(.1+i*.001).put(0));times.put(3000000+i*60000);}
        j.getJSONObject("capture_quality").put("gps_points",10).put("source_route_points",10);JourneyStore.save(app,j);
        AtomicInteger calls=new AtomicInteger();
        coordinator=new MatchingCoordinator(app,(foot,payload)->{
            if(calls.incrementAndGet()==1)return endpointResult();
            return endpointResult().put("matched_point_indices",new JSONArray("[0,1,3,4]"))
                .put("unmatched_point_indices",new JSONArray("[2]"))
                .put("failed_sections",new JSONArray("[{\"start_point_index\":1,\"end_point_index\":2,\"points\":2}]"));
        });coordinator.start("road");await();assertEquals(1,coordinator.snapshot().failed);
        assertFalse(JourneyStore.get(app,"road").has("processing_result"));
    }
}
