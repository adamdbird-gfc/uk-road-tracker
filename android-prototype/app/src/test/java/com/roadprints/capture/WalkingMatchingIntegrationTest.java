package com.roadprints.capture;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class WalkingMatchingIntegrationTest {
    private Context app;
    private MatchingCoordinator coordinator;
    @Before public void setUp() { app=RuntimeEnvironment.getApplication();JourneyStore.deleteAll(app); }
    @After public void tearDown() { if(coordinator!=null) coordinator.shutdownForTest();JourneyStore.deleteAll(app); }
    private JSONObject walk() throws Exception {
        JSONArray coords=new JSONArray("[[0,0],[0.00009,0],[0.00225,0],[0.00027,0],[0.00036,0]]");
        JSONArray evidence=new JSONArray();
        for(int i=0;i<coords.length();i++) evidence.put(new JSONArray().put(coords.getJSONArray(i).getDouble(0))
                .put(0).put(15).put(100000+i*10000).put(1));
        return new JSONObject().put("journey_id","walk").put("mode","walking").put("processing_status","pending")
                .put("source",new JSONObject().put("type","android_activity_capture"))
                .put("distance_meters",500).put("raw_capture_samples",evidence)
                .put("route_geometry",new JSONObject().put("type","LineString").put("coordinates",coords));
    }
    private JSONObject result(double distance) throws Exception {
        return new JSONObject("{\"geojson\":{\"features\":[{\"geometry\":{\"type\":\"LineString\",\"coordinates\":[[0,0],[0.00036,0]]}}]}}")
                .put("matched_distance_m",distance);
    }
    private void await() throws Exception {
        long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
        while(coordinator.snapshot().state!=MatchingCoordinator.State.COMPLETE && System.nanoTime()<end) Thread.sleep(10);
        assertEquals(MatchingCoordinator.State.COMPLETE,coordinator.snapshot().state);
    }
    @Test public void failedCaptureQualityIsReevaluatedBeforeTheMatcherOnRepeatedRetries() throws Exception {
        JSONArray coords=new JSONArray(),samples=new JSONArray();
        for(int i=0;i<22;i++) {
            double metres=i==0?-20:i<7?i*40:240+i%3;
            coords.put(new JSONArray().put(metres/111195).put(0));
            samples.put(new JSONArray().put(metres/111195).put(0).put(i==0?178:10)
                    .put(i==0?0:100000+i*30000).put(1));
        }
        JSONObject j=walk().put("raw_capture_samples",samples).put("capture_route_samples",samples)
                .put("recording_quality",new JSONObject().put("resolved",false))
                .put("route_geometry",new JSONObject().put("type","LineString").put("coordinates",coords))
                .put("distance_meters",0).put("processing_status","failed");
        JourneyStore.save(app,j);AtomicInteger calls=new AtomicInteger();
        coordinator=new MatchingCoordinator(app,(foot,payload)->{
            calls.incrementAndGet(); int count=payload.getJSONArray("points").length();
            assertTrue(count<22); return result(210).put("input_points",count).put("matched_tracepoints",count);
        });
        coordinator.rematch("walk");await();
        assertEquals(1,coordinator.snapshot().matched);
        JSONObject stored=JourneyStore.get(app,"walk");
        assertTrue(stored.getJSONObject("recording_quality").getBoolean("resolved"));
        assertEquals(samples.toString(),stored.getJSONArray("raw_capture_samples").toString());
        assertEquals(coords.toString(),stored.getJSONObject("route_geometry").getJSONArray("coordinates").toString());
        coordinator.rematch("walk");await();assertEquals(2,calls.get());
    }
    @Test public void successfulRematchCorrectsDistancePreservesRawTraceAndIsRepeatable() throws Exception {
        JSONObject walk=walk();JourneyStore.save(app,walk);AtomicInteger submitted=new AtomicInteger();
        coordinator=new MatchingCoordinator(app,(foot,payload)->{submitted.set(payload.getJSONArray("points").length());return result(44);});
        coordinator.start("walk");await();
        assertEquals(1,coordinator.snapshot().matched);assertTrue(submitted.get()<5);
        JSONObject stored=JourneyStore.get(app,"walk");
        assertEquals(44,stored.getDouble("distance_meters"),.01);assertEquals(500,stored.getDouble("original_distance_meters"),.01);
        assertEquals(walk.getJSONObject("route_geometry").toString(),stored.getJSONObject("route_geometry").toString());
        assertEquals(5,stored.getJSONArray("raw_capture_samples").length());
        int first=submitted.get();coordinator.rematch("walk");await();assertEquals(first,submitted.get());
        assertEquals(500,JourneyStore.get(app,"walk").getDouble("original_distance_meters"),.01);
    }
    @Test public void inflatedMatchDoesNotOverwriteOriginalDistanceOrPreviousMatch() throws Exception {
        JSONObject walk=walk().put("processing_result",result(45));JourneyStore.save(app,walk);
        coordinator=new MatchingCoordinator(app,(foot,payload)->result(1000));coordinator.start("walk");await();
        assertEquals(1,coordinator.snapshot().failed);
        JSONObject stored=JourneyStore.get(app,"walk");assertEquals(500,stored.getDouble("distance_meters"),.01);
        assertEquals(45,stored.getJSONObject("processing_result").getDouble("matched_distance_m"),.01);
        assertTrue(stored.getString("error_summary").contains("substantially longer"));
    }

    @Test public void movingSamplingGapRemainsBlockedAfterPreparationWithoutDiscardingRawData() throws Exception {
        JSONObject walk=walk();
        JSONArray coordinates=new JSONArray(),samples=new JSONArray();
        int[] metres={0,10,15,150,160},seconds={0,10,190,200,210};
        for(int i=0;i<metres.length;i++) {
            double longitude=metres[i]/111195.0;
            coordinates.put(new JSONArray().put(longitude).put(0));
            samples.put(new JSONArray().put(longitude).put(0).put(5).put(100000+seconds[i]*1000).put(1));
        }
        walk.put("route_geometry",new JSONObject().put("type","LineString").put("coordinates",coordinates))
                .put("raw_capture_samples",samples).put("distance_meters",0).put("original_distance_meters",2000)
                .put("capture_validation",new JSONObject().put("version",1).put("timing_available",true).put("resolved",false))
                .put("processing_status","failed");
        JourneyStore.save(app,walk);AtomicInteger submitted=new AtomicInteger();
        coordinator=new MatchingCoordinator(app,(foot,payload)->{
            submitted.set(payload.getJSONArray("points").length());
            return result(165).put("input_points",4).put("matched_tracepoints",4);
        });
        coordinator.start("walk");await();
        assertEquals(1,coordinator.snapshot().failed);assertEquals(0,submitted.get());
        JSONObject stored=JourneyStore.get(app,"walk");
        assertEquals(0,stored.getDouble("distance_meters"),.01);
        assertEquals(2000,stored.getDouble("original_distance_meters"),.01);
        assertEquals(5,stored.getJSONArray("raw_capture_samples").length());
        assertEquals(coordinates.toString(),stored.getJSONObject("route_geometry").getJSONArray("coordinates").toString());
        assertTrue(stored.getString("error_summary").contains("moving GPS gap"));
    }
}
