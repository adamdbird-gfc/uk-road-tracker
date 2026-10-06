package com.roadprints.capture;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class WalkingJourneyPreparationTest {
    @Test public void nativeWalkUsesSavedEvidenceWithoutChangingOriginalGeometry() throws Exception {
        Context app=RuntimeEnvironment.getApplication();
        JSONObject journey=journey("android_activity_capture");
        JSONArray coords=new JSONArray("[[0,0],[0.00009,0],[0.00225,0],[0.00027,0],[0.00036,0]]");
        journey.put("route_geometry",new JSONObject().put("type","LineString").put("coordinates",coords));
        JSONArray evidence=new JSONArray();
        for(int i=0;i<coords.length();i++) evidence.put(new JSONArray().put(coords.getJSONArray(i).getDouble(0))
                .put(0).put(15).put(100000+i*10000).put(1));
        journey.put("raw_capture_samples",evidence);
        String original=journey.toString();
        WalkingJourneyPreparation.Prepared result=WalkingJourneyPreparation.prepare(app,journey);
        assertTrue(result.validated);assertTrue(result.points.length()<5);assertEquals(40,result.distance,1);
        assertEquals(original,journey.toString());assertEquals("saved_capture_samples",result.details.getString("evidence_source"));
    }
    @Test public void importedTimelineAndUntimedNativeGeometryRemainIntact() throws Exception {
        Context app=RuntimeEnvironment.getApplication();MovementDiagnostics.clear(app);
        for(String source:Arrays.asList("timeline_import","android_activity_capture")) {
            JSONObject journey=journey(source).put("route_geometry",new JSONObject().put("type","LineString")
                    .put("coordinates",new JSONArray("[[0,0],[0.01,0.01],[0.02,0]]")));
            WalkingJourneyPreparation.Prepared result=WalkingJourneyPreparation.prepare(app,journey);
            assertFalse(result.validated);assertEquals(3,result.points.length());
        }
    }
    @Test public void stationTailRequiresDwellAndNoDepartureAndDoesNotAffectPassThrough() throws Exception {
        RailStationCatalog catalog=new RailStationCatalog();
        catalog.stations.add(new RailStationCatalog.Station("TEST","Test",51.5,0));
        long time=java.time.Instant.parse("2026-10-05T17:00:00Z").toEpochMilli();
        List<FootTraceValidator.Sample> points=Arrays.asList(
                new FootTraceValidator.Sample(-.01,51.5,15,time-100000,1,0),
                new FootTraceValidator.Sample(0,51.5,15,time-1000,1,1),
                new FootTraceValidator.Sample(.0002,51.5,15,time+360000,0,2));
        JSONObject still=new JSONObject().put("activity","still").put("transition","entered")
                .put("timestamp_utc","2026-10-05T17:00:00Z");
        assertEquals(1,WalkingJourneyPreparation.stationWaitingEnd(points,Arrays.asList(still),catalog,time+400000));
        assertEquals(-1,WalkingJourneyPreparation.stationWaitingEnd(points,Arrays.asList(still),catalog,time+100000));
        List<FootTraceValidator.Sample> departed=new ArrayList<>(points);
        departed.add(new FootTraceValidator.Sample(.02,51.5,15,time+390000,1,3));
        assertEquals(-1,WalkingJourneyPreparation.stationWaitingEnd(departed,Arrays.asList(still),catalog,time+400000));
    }
    private JSONObject journey(String source) throws Exception {
        return new JSONObject().put("mode","walking").put("source",new JSONObject().put("type",source))
                .put("started_at","2026-10-05T17:00:00Z").put("ended_at","2026-10-05T17:10:00Z");
    }
}
