package com.roadprints.capture;

import android.content.Context;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class MatchRetryLifecycleTest {
    Context app;MatchingCoordinator worker;AtomicInteger calls;
    @Before public void setup(){app=RuntimeEnvironment.getApplication();JourneyStore.deleteAll(app);CrashReporter.clear(app);calls=new AtomicInteger();}
    @After public void close(){if(worker!=null)worker.shutdownForTest();JourneyStore.deleteAll(app);}
    JSONObject walk()throws Exception {
        JSONArray samples=new JSONArray("[[0,0,5,1000000,1],[0.00009,0,5,1010000,1],[0.00344,0,5,1898224,1],[0.00353,0,5,1908224,1]]");
        return MultimodalFerryTest.journey("idle-walk",samples).put("mode","walking").put("processing_status","pending");
    }
    void freshWorker(){if(worker!=null)worker.shutdownForTest();worker=new MatchingCoordinator(app,(endpoint,payload)->{calls.incrementAndGet();return new JSONObject("{\"geojson\":{\"features\":[{\"geometry\":{\"type\":\"LineString\",\"coordinates\":[[0,0],[0.00027,0]]}}]},\"matched_distance_m\":30}");});}
    void await()throws Exception{long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);while(worker.snapshot().state!=MatchingCoordinator.State.COMPLETE&&System.nanoTime()<end)Thread.sleep(10);assertEquals(worker.snapshot().message,MatchingCoordinator.State.COMPLETE,worker.snapshot().state);}
    @Test public void repeatedIdleScansRestartsManualRetryAndArchiveLabelsDoNotRetryGpsGap()throws Exception {
        JSONObject original=walk();JourneyStore.save(app,original);freshWorker();worker.startCaptured("idle-walk");await();
        JSONObject saved=JourneyStore.get(app,"idle-walk");assertTrue(MatchRetryPolicy.qualityBlocked(saved));assertEquals(0,calls.get());
        String attempt=saved.getJSONObject("last_match_attempt").toString(),raw=saved.getJSONArray("raw_capture_samples").toString();
        for(int i=0;i<4;i++) {JourneyStore.updateTitle(app,"idle-walk","label "+i);freshWorker();worker.start();await();assertEquals(0,worker.snapshot().total);worker.rematch("idle-walk");await();assertEquals(0,worker.snapshot().total);}
        saved=JourneyStore.get(app,"idle-walk");assertEquals(attempt,saved.getJSONObject("last_match_attempt").toString());assertEquals(raw,saved.getJSONArray("raw_capture_samples").toString());
    }
    @Test public void materialEvidenceRepairReopensMatchingAndPreservesRawArrays()throws Exception {
        JourneyStore.save(app,walk());freshWorker();worker.start();await();JSONObject saved=JourneyStore.get(app,"idle-walk");
        JSONArray repaired=new JSONArray("[[0,0,5,1000000,1],[0.00009,0,5,1010000,1],[0.00018,0,5,1020000,1],[0.00027,0,5,1030000,1]]");
        JSONObject fixed=MultimodalFerryTest.journey("idle-walk",repaired).put("mode","walking").put("processing_status","failed").put("match_retry_state",saved.getJSONObject("match_retry_state"));
        JourneyStore.save(app,fixed);assertFalse(MatchRetryPolicy.qualityBlocked(fixed));freshWorker();worker.start();await();assertEquals(1,calls.get());assertEquals(1,worker.snapshot().matched);
        assertEquals(repaired.toString(),JourneyStore.get(app,"idle-walk").getJSONArray("raw_capture_samples").toString());
    }
    @Test public void changedRulesReopenEvidenceButUiRevisionDoesNot()throws Exception {
        JSONObject saved=walk();MatchRetryPolicy.reject(saved,"2026-10-24T04:23:56Z");assertTrue(MatchRetryPolicy.qualityBlocked(saved));
        saved.put("revision",200).put("title","renamed");assertTrue(MatchRetryPolicy.qualityBlocked(saved));
        saved.getJSONObject("match_retry_state").put("rules_revision",0);assertFalse(MatchRetryPolicy.qualityBlocked(saved));
    }
    @Test public void legacyKnownGapIsAdoptedWithoutManufacturingAnAttemptOrAlert()throws Exception {
        JSONObject saved=walk().put("processing_status","failed").put("error_summary","This walking recording has a moving GPS gap or unreliable route evidence. Your original recording is preserved.");
        JourneyStore.save(app,saved);freshWorker();worker.start();await();assertEquals(0,worker.snapshot().total);assertEquals(0,calls.get());
        assertFalse(JourneyStore.get(app,"idle-walk").has("last_match_attempt"));assertEquals("",CrashReporter.getMatchFailureReports(app));
    }
    @Test public void transientCooldownIsSkippedWithoutNewFailureAndExpiresRecoverably()throws Exception {
        JSONObject saved=walk().put("source",new JSONObject().put("type","timeline_import"))
            .put("last_match_attempt",new JSONObject().put("next_retry_at_utc",java.time.Instant.now().plusSeconds(600).toString()));
        JourneyStore.save(app,saved);freshWorker();worker.start();await();assertEquals(0,worker.snapshot().total);assertEquals(0,worker.snapshot().failed);
        assertEquals("",CrashReporter.getMatchFailureReports(app));
        saved.getJSONObject("last_match_attempt").put("next_retry_at_utc","2026-01-01T00:00:00Z");assertFalse(MatchRetryPolicy.coolingDown(saved));
        JourneyStore.save(app,saved);freshWorker();worker.start();await();assertEquals(1,calls.get());assertEquals(1,worker.snapshot().matched);
        assertTrue(MatchFailure.retryable(new MatchFailure(504,"timeout")));assertFalse(MatchFailure.retryNow(new MatchFailure(504,"timeout"),0));
        assertTrue(MatchFailure.retryNow(new java.net.UnknownHostException(),0));assertFalse(MatchFailure.retryNow(new java.net.UnknownHostException(),1));
    }
}
