package com.roadprints.capture;

import android.content.Context;
import java.time.Instant;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class ArrivalDwellIntegrationTest {
    private final long at=1_800_000_000_000L;
    private JSONArray rows(int first,int last,int step,float speed)throws Exception {
        JSONArray rows=new JSONArray();
        for(int second=first;second<=last;second+=step)
            rows.put(new JSONArray().put(-2).put(52).put(5).put(at+second*1000L).put(speed));
        return rows;
    }
    private JSONObject stop()throws Exception {
        return new JSONObject().put("id","one:arrival").put("journey_id","one").put("role","arrival")
                .put("classification","supported").put("arrived_at",Instant.ofEpochMilli(at).toString());
    }
    private JSONObject journey()throws Exception {
        return new JSONObject().put("journey_id","one").put("mode","driving")
                .put("source",new JSONObject().put("type","android_activity_capture"))
                .put("started_at",Instant.ofEpochMilli(at-60000).toString()).put("ended_at",Instant.ofEpochMilli(at).toString())
                .put("route_geometry",new JSONObject().put("type","LineString").put("coordinates",new JSONArray("[[-2.01,52],[-2,52]]")))
                .put("distance_meters",685).put("raw_capture_samples",rows(0,60,30,0))
                .put("stops",new JSONArray().put(stop()));
    }
    private JSONArray venues()throws Exception {
        return new JSONArray().put(new JSONObject().put("id","services").put("lat",52).put("lng",-2));
    }
    @Test public void earlyArrivalCollectsGenuineDwellWithoutExtendingJourneyOrChangingRawEvidence()throws Exception {
        JSONObject journey=journey();String raw=journey.getJSONArray("raw_capture_samples").toString();
        String route=journey.getJSONObject("route_geometry").toString(),end=journey.getString("ended_at");
        assertTrue(ServiceStationEvidence.visits(journey,venues()).isEmpty());
        assertTrue(ArrivalDwellEvidence.append(journey,stop(),rows(60,240,60,0)));
        assertTrue(ServiceStationEvidence.visits(journey,venues()).contains("services"));
        assertEquals(raw,journey.getJSONArray("raw_capture_samples").toString());
        assertEquals(route,journey.getJSONObject("route_geometry").toString());
        assertEquals(end,journey.getString("ended_at"));assertEquals(685,journey.getInt("distance_meters"));
        assertFalse(ArrivalDwellEvidence.append(journey,stop(),rows(60,240,60,0)));
    }
    @Test public void venueExercisePassingTrafficAndSamplingGapsDoNotManufactureDwell()throws Exception {
        for(JSONArray observations:new JSONArray[]{rows(90,300,30,3),rows(90,300,30,20),rows(180,540,120,0)}) {
            JSONObject journey=journey();ArrivalDwellEvidence.append(journey,stop(),observations);
            assertTrue(ServiceStationEvidence.visits(journey,venues()).isEmpty());
        }
    }
    @Test public void tailsAreBoundedAndUnrelatedOrRejectedArrivalsCannotAccrueEvidence()throws Exception {
        JSONObject journey=journey();assertFalse(ArrivalDwellEvidence.append(journey,stop().put("journey_id","other"),rows(90,240,30,0)));
        journey.getJSONArray("stops").getJSONObject(0).put("classification","rejected");
        assertFalse(ArrivalDwellEvidence.append(journey,stop(),rows(90,240,30,0)));
        journey=journey();assertFalse(ArrivalDwellEvidence.append(journey,stop(),rows(660,1200,30,0)));
    }
    @Test public void storedTailSurvivesRematchingAndDeletionIsNeverUndone()throws Exception {
        Context app=RuntimeEnvironment.getApplication();JourneyStore.deleteAll(app);
        JSONObject journey=journey();JourneyStore.save(app,journey);
        JourneyStore.appendArrivalDwell(app,stop(),rows(90,240,30,0));
        JSONObject stored=JourneyStore.get(app,"one");assertNotNull(stored.optJSONArray("arrival_dwell_samples"));
        stored.put("processing_status","complete").put("processing_result",new JSONObject());JourneyStore.save(app,stored);
        assertTrue(ServiceStationEvidence.visits(JourneyStore.get(app,"one"),venues()).contains("services"));
        final boolean[] visited={false};JourneyStore.forEachServiceStationEvidence(app,evidence->{
            visited[0]=ServiceStationEvidence.visits(evidence,venuesUnchecked()).contains("services");
        });assertTrue(visited[0]);
        JourneyStore.deleteAll(app);JourneyStore.appendArrivalDwell(app,stop(),rows(90,240,30,0));
        assertNull(JourneyStore.get(app,"one"));
    }
    private JSONArray venuesUnchecked(){try{return venues();}catch(Exception error){throw new AssertionError(error);}}
}
