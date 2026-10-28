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
    @Test public void savedAccuracyIsSentAndAnUnmappableManualRemovalKeepsTheOldMatch() throws Exception {
        JSONObject old=result(44);JSONObject j=walk().put("processing_status","complete").put("processing_result",old)
                .put("journey_corrections",new JSONObject().put("removed_matched_segments",new JSONArray("[0]")));
        String priorCoverage=JourneyStore.ncnEvidenceHash(j);
        JourneyStore.save(app,j);
        coordinator=new MatchingCoordinator(app,(endpoint,payload)->{
            JSONArray points=payload.getJSONArray("points");
            for(int i=0;i<points.length();i++)assertEquals(15,points.getJSONObject(i).getDouble("accuracy_m"),0);
            JSONObject changed=result(40);changed.getJSONObject("geojson").getJSONArray("features").getJSONObject(0)
                    .getJSONObject("geometry").put("coordinates",new JSONArray("[[0,0],[0.00035,0]]"));
            return changed;
        });
        coordinator.rematch("walk");await();assertEquals(1,coordinator.snapshot().failed);
        JSONObject saved=JourneyStore.get(app,"walk");
        assertEquals("complete",saved.getString("processing_status"));
        assertEquals(priorCoverage,JourneyStore.ncnEvidenceHash(saved));
        assertEquals("retained_manual_edits",saved.getJSONObject("last_match_attempt").getString("outcome"));
        assertEquals(old.toString(),saved.getJSONObject("processing_result").toString());
        assertEquals("[0]",saved.getJSONObject("journey_corrections").getJSONArray("removed_matched_segments").toString());
        assertTrue(saved.getString("error_summary").contains("preserved"));
        assertEquals(j.getJSONArray("raw_capture_samples").toString(),saved.getJSONArray("raw_capture_samples").toString());
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
    private JSONObject longIndoorStay()throws Exception{
        JSONArray samples=new JSONArray(),coords=new JSONArray();
        for(int i=0;i<189;i++){
            double metres=i<9?i*20:160+i%3*3;long time=i<9?100000+i*20000:280000+(i-9)*10000L;
            coords.put(new JSONArray().put(metres/111195).put(0));
            samples.put(new JSONArray().put(metres/111195).put(0).put(20).put(time).put(i<9?1:i%7==0?2:.2));
        }
        return walk().put("route_geometry",new JSONObject().put("type","LineString").put("coordinates",coords))
                .put("raw_capture_samples",samples).put("capture_route_samples",samples)
                .put("started_at",java.time.Instant.ofEpochMilli(100000).toString())
                .put("ended_at",java.time.Instant.ofEpochMilli(samples.getJSONArray(188).getLong(3)).toString());
    }
    @Test public void successfulRematchRepairsOldArrivalTimeAndRepeatedRematchesKeepTheOriginal()throws Exception{
        JSONObject j=longIndoorStay();String original=j.getString("ended_at"),raw=j.getJSONArray("raw_capture_samples").toString();
        JourneyStore.save(app,j);
        coordinator=new MatchingCoordinator(app,(foot,payload)->result(180));coordinator.rematch("walk");await();
        assertEquals(1,coordinator.snapshot().matched);
        JSONObject stored=JourneyStore.get(app,"walk");String corrected=stored.getString("ended_at");
        assertTrue(java.time.Instant.parse(corrected).toEpochMilli()<700000);
        assertEquals(original,stored.getString("original_ended_at"));
        assertEquals(raw,stored.getJSONArray("raw_capture_samples").toString());
        coordinator.rematch("walk");await();
        stored=JourneyStore.get(app,"walk");assertEquals(corrected,stored.getString("ended_at"));
        assertEquals(original,stored.getString("original_ended_at"));
    }
    @Test public void failedRematchCannotChangeOldArrivalTime()throws Exception{
        JSONObject j=longIndoorStay();String original=j.getString("ended_at");JourneyStore.save(app,j);
        coordinator=new MatchingCoordinator(app,(foot,payload)->{throw new IllegalStateException("HTTP 503: unavailable");});
        coordinator.rematch("walk");await();assertEquals(1,coordinator.snapshot().failed);
        JSONObject stored=JourneyStore.get(app,"walk");assertEquals(original,stored.getString("ended_at"));
        assertFalse(stored.has("original_ended_at"));
    }

    @Test public void completedOldMatchIsRepreparedAndDriftRemovedUsingCurrentLogic()throws Exception{
        JSONObject j=longIndoorStay().put("processing_status","complete")
                .put("processing_result",result(500))
                .put("walking_validation",new JSONObject().put("version",1)
                        .put("capture_preparation_version",1).put("stationary_points",0));
        String geometry=j.getJSONObject("route_geometry").toString();
        String raw=j.getJSONArray("raw_capture_samples").toString();
        String originalEnd=j.getString("ended_at");
        // The historical displayed match itself contains the stationary tail.
        j.getJSONObject("processing_result").getJSONObject("geojson").getJSONArray("features")
                .getJSONObject(0).getJSONObject("geometry")
                .put("coordinates",j.getJSONObject("route_geometry").getJSONArray("coordinates"));
        String oldMap=j.getJSONObject("processing_result").getJSONObject("geojson").toString();
        JourneyStore.save(app,j);AtomicInteger calls=new AtomicInteger();
        coordinator=new MatchingCoordinator(app,(foot,payload)->{
            assertEquals("/match-walking",foot);calls.incrementAndGet();
            assertTrue(payload.getJSONArray("points").length()<20);
            return result(180);
        });
        coordinator.rematch("walk");await();
        assertEquals(1,calls.get());assertEquals(1,coordinator.snapshot().matched);
        JSONObject stored=JourneyStore.get(app,"walk");
        assertEquals("complete",stored.getString("processing_status"));
        assertEquals(3,stored.getJSONObject("walking_validation").getInt("capture_preparation_version"));
        assertTrue(stored.getJSONObject("walking_validation").getInt("stationary_points")>100);
        assertEquals(180,stored.getDouble("distance_meters"),0d);
        assertEquals(180,stored.getJSONObject("processing_result").getDouble("matched_distance_m"),0d);
        assertNotEquals(oldMap,stored.getJSONObject("processing_result").getJSONObject("geojson").toString());
        assertEquals(result(180).getJSONObject("geojson").toString(),
                stored.getJSONObject("processing_result").getJSONObject("geojson").toString());
        assertEquals(originalEnd,stored.getString("original_ended_at"));
        assertEquals(raw,stored.getJSONArray("raw_capture_samples").toString());
        assertEquals(geometry,stored.getJSONObject("route_geometry").toString());
        coordinator.rematch("walk");await();
        assertEquals(2,calls.get());
        assertEquals(raw,JourneyStore.get(app,"walk").getJSONArray("raw_capture_samples").toString());
    }

    @Test public void failedUpgradeRematchRetainsPreviousMapDistanceAndRawRecording()throws Exception{
        JSONObject j=longIndoorStay().put("processing_status","complete").put("processing_result",result(190));
        String previous=j.getJSONObject("processing_result").toString();
        String geometry=j.getJSONObject("route_geometry").toString();
        String raw=j.getJSONArray("raw_capture_samples").toString();
        String ended=j.getString("ended_at");double distance=j.getDouble("distance_meters");
        JourneyStore.save(app,j);
        coordinator=new MatchingCoordinator(app,(foot,payload)->{throw new IllegalStateException("HTTP 503: unavailable");});
        coordinator.rematch("walk");await();
        assertEquals(1,coordinator.snapshot().failed);
        JSONObject stored=JourneyStore.get(app,"walk");
        assertEquals("failed",stored.getString("processing_status"));
        assertEquals(previous,stored.getJSONObject("processing_result").toString());
        assertEquals(distance,stored.getDouble("distance_meters"),0d);
        assertEquals(ended,stored.getString("ended_at"));
        assertEquals(geometry,stored.getJSONObject("route_geometry").toString());
        assertEquals(raw,stored.getJSONArray("raw_capture_samples").toString());
    }

    @Test public void completedCapturedDriveUsesCurrentPreparationAndFreshMatcherResult()throws Exception{
        JSONArray coords=new JSONArray("[[0,0],[0.001,0],[0.002,0],[0.003,0]]"),samples=new JSONArray();
        for(int i=0;i<coords.length();i++)samples.put(new JSONArray().put(coords.getJSONArray(i).getDouble(0))
                .put(0).put(5).put(100000+i*10000).put(11));
        JSONObject j=walk().put("mode","driving").put("processing_status","complete")
                .put("processing_result",result(500)).put("raw_capture_samples",samples)
                .put("capture_route_samples",samples).put("road_preparation",new JSONObject().put("version",1))
                .put("route_geometry",new JSONObject().put("type","LineString").put("coordinates",coords));
        JourneyStore.save(app,j);AtomicInteger calls=new AtomicInteger();
        coordinator=new MatchingCoordinator(app,(endpoint,payload)->{
            assertEquals("/match",endpoint);assertTrue(payload.getBoolean("road_recovery"));
            assertEquals(4,payload.getJSONArray("points").length());calls.incrementAndGet();
            JSONObject matched=result(330).put("input_points",4).put("matched_tracepoints",4)
                    .put("matched_distance_is_deduplicated",true).put("failed_sections",new JSONArray());
            matched.getJSONObject("geojson").getJSONArray("features").getJSONObject(0)
                    .getJSONObject("geometry").put("coordinates",coords);
            return matched;
        });
        coordinator.rematch("walk");await();
        assertEquals(1,calls.get());assertEquals(1,coordinator.snapshot().matched);
        JSONObject stored=JourneyStore.get(app,"walk");
        assertEquals(2,stored.getJSONObject("road_preparation").getInt("version"));
        assertEquals(330,stored.getJSONObject("processing_result").getDouble("matched_distance_m"),0d);
        assertEquals(coords.toString(),stored.getJSONObject("route_geometry").getJSONArray("coordinates").toString());
        assertEquals(samples.toString(),stored.getJSONArray("raw_capture_samples").toString());
    }

}
