package com.roadprints.capture;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import java.util.Arrays;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class PlaceBoundaryEvidenceTest {
    private FootTraceValidator.Sample p(double metres,long time,double speed) {
        return new FootTraceValidator.Sample(51+metres/111195,0,5,time,speed,0);
    }
    private JSONObject stop() throws Exception {
        return PlaceBoundaryEvidence.arrival("a",p(0,100000,.1),400000,new ContextMap.Evidence(),null,"arrival_gps_dwell");
    }
    private JSONObject journey(String id,JSONObject stop) throws Exception {
        JSONObject j=new JSONObject().put("journey_id",id).put("mode","unknown").put("started_at","2026-10-11T12:00:00Z")
                .put("ended_at","2026-10-11T12:01:00Z").put("distance_meters",100);
        PlaceBoundaryEvidence.attach(j,null,stop);return j;
    }
    @Test public void gpsArrivalRemainsAnEstimateAndUsesArrivalTime() throws Exception {
        JSONObject s=stop();assertEquals("estimated",s.getString("classification"));
        assertEquals("gps_dwell",s.getString("source"));assertEquals(300,s.getInt("dwell_seconds"));
        assertEquals("1970-01-01T00:01:40Z",s.getString("boundary_at"));
        assertEquals("stopped",s.getString("visit_status"));
    }
    @Test public void footprintAndStationEvidenceAreSupportedNotUserConfirmed() throws Exception {
        ContextMap.Evidence map=new ContextMap.Evidence();map.destination=true;map.kind="residential";map.name="residential";map.featureId="osm:way:123";
        JSONObject s=PlaceBoundaryEvidence.arrival("a",p(0,100000,.1),400000,map,null,"arrival_residential");
        assertEquals("supported",s.getString("classification"));assertEquals("osm:way:123",s.getString("place_id"));
        s=PlaceBoundaryEvidence.arrival("a",p(0,100000,.1),400000,new ContextMap.Evidence(),"ABC","station_arrival_waiting");
        assertEquals("offline_station_catalog",s.getString("source"));assertEquals("supported",s.getString("classification"));
    }
    @Test public void departureUsesFreshObservedLastStillPointWithoutRemovingRealWalk() throws Exception {
        assertEquals(1,PlaceBoundaryEvidence.departureIndex(stop(),Arrays.asList(p(0,500000,0),p(2,510000,.1),p(20,530000,1),p(80,590000,1))));
        JSONObject d=PlaceBoundaryEvidence.departure(stop(),p(2,510000,.1));
        assertEquals("1970-01-01T00:08:30Z",d.getString("boundary_at"));
        assertEquals("1970-01-01T00:01:40Z",d.getString("arrived_at"));
    }
    @Test public void unrelatedOriginOrRejectedStopDoesNotInventDeparture() throws Exception {
        assertEquals(-1,PlaceBoundaryEvidence.departureIndex(stop(),Arrays.asList(p(500,500000,1),p(600,510000,1))));
        JSONObject rejected=PlaceBoundaryEvidence.review(stop(),"",false,900000);
        assertEquals(-1,PlaceBoundaryEvidence.departureIndex(rejected,Arrays.asList(p(0,500000,0),p(100,510000,8))));
    }
    @Test public void confirmationPreservesOriginalProvenanceAndCoordinates() throws Exception {
        JSONObject s=PlaceBoundaryEvidence.review(stop(),"Corrected place",true,900000);
        assertEquals("confirmed",s.getString("classification"));assertEquals("Corrected place",s.getString("name"));
        assertEquals("gps_dwell",s.getJSONObject("original_evidence").getString("source"));
        assertEquals(stop().getDouble("latitude"),s.getDouble("latitude"),0);
    }
    @Test public void backgroundSaveCannotOverwriteNewerUserReview() throws Exception {
        JSONObject stale=journey("a",stop()),reviewed=journey("a",PlaceBoundaryEvidence.review(stop(),"Home",true,900000));
        PlaceBoundaryEvidence.preserveReviews(stale,reviewed);
        assertEquals("Home",stale.getJSONArray("stops").getJSONObject(0).getString("name"));
        assertEquals("Home",stale.getJSONArray("places").getJSONObject(0).getString("name"));
    }
    @Test public void storedReviewSurvivesRematchAndRejectionInvalidatesOrigin() throws Exception {
        Context app=RuntimeEnvironment.getApplication();PlaceAnchorStore.clear(app);
        JSONObject j=journey("a",stop());JourneyStore.save(app,j);
        assertNotNull(PlaceAnchorStore.last(app));
        JSONObject stale=new JSONObject(j.toString());
        JourneyStore.reviewStop(app,"a","a:arrival","Home",true);
        JourneyStore.save(app,stale);
        assertEquals("Home",PlaceAnchorStore.last(app).getString("name"));
        JourneyStore.reviewStop(app,"a","a:arrival","",false);
        assertNull(PlaceAnchorStore.last(app));
        assertEquals(0,JourneyStore.get(app,"a").getJSONArray("places").length());
    }
    @Test public void olderArrivalCannotReplaceLatestOriginAndDeletionClearsIt() throws Exception {
        Context app=RuntimeEnvironment.getApplication();PlaceAnchorStore.clear(app);
        JSONObject latest=PlaceBoundaryEvidence.arrival("new",p(0,500000,.1),800000,new ContextMap.Evidence(),null,"arrival_gps_dwell");
        JourneyStore.save(app,journey("new",latest));JourneyStore.save(app,journey("a",stop()));
        assertEquals("new:arrival",PlaceAnchorStore.last(app).getString("id"));
        JourneyStore.delete(app,"new");assertNull(PlaceAnchorStore.last(app));
    }
}
