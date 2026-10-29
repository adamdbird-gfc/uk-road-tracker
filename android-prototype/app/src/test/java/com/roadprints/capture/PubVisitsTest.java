package com.roadprints.capture;

import android.content.Context;
import android.location.Location;
import android.os.SystemClock;
import androidx.core.location.LocationCompat;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import java.time.Instant;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class PubVisitsTest {
    private Context app;
    private JSONObject pub;
    @Before public void reset() throws Exception {
        app=RuntimeEnvironment.getApplication();
        TimelineVisitStore.clear(app);PubVisitStore.clear(app);
        app.getSharedPreferences(CollectionAccessStore.PREFS,0).edit().clear().commit();
        app.getSharedPreferences("roadprints_collections_v1",0).edit().clear().commit();
        CollectionAccessStore.installForTesting(app,CollectionAccessStore.SPOONS);
        JSONArray pubs=CollectionMapCatalog.spoons(app);
        for(int i=0;i<pubs.length();i++)if("The Robert Pocock".equals(pubs.getJSONObject(i).optString("name")))pub=pubs.getJSONObject(i);
        assertNotNull(pub);
    }
    @After public void clean(){TimelineVisitStore.clear(app);PubVisitStore.clear(app);app.getSharedPreferences(CollectionAccessStore.PREFS,0).edit().clear().commit();}
    private JSONObject visit(String id,String name) throws Exception {
        return new JSONObject().put("id",id).put("source","google_timeline_placeVisit")
                .put("start",Instant.now().minusSeconds(3600).toString())
                .put("end",Instant.now().minusSeconds(3000).toString())
                .put("place_name",name).put("lat",pub.getDouble("lat")).put("lng",pub.getDouble("lng"));
    }
    private Location fix() throws Exception {
        Location result=new Location("gps");result.setLatitude(pub.getDouble("lat"));result.setLongitude(pub.getDouble("lng"));
        result.setAccuracy(5);result.setTime(System.currentTimeMillis());result.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
        return result;
    }
    @Test public void namedTimelineVisitIsRecognisedOnceAndImportDeduplicates() throws Exception {
        JSONObject visit=visit("same",pub.getString("name")+" - Wetherspoon");
        TimelineVisitStore.merge(app,new JSONArray().put(visit));
        assertEquals(1,PubVisitStore.reconcile(app,CollectionMapCatalog.spoons(app)));
        TimelineVisitStore.merge(app,new JSONArray().put(visit));
        assertEquals(0,PubVisitStore.reconcile(app,CollectionMapCatalog.spoons(app)));
        assertEquals(1,PubVisitStore.completed(app).size());assertEquals(1,PubVisitStore.records(app).length());
        assertEquals("named_timeline_visit",PubVisitStore.records(app).getJSONObject(0).getString("source"));
    }
    @Test public void genericBuildingRequiresConfirmationAndUnrelatedPubIsRejected() throws Exception {
        JSONObject visit=visit("building","Airport Terminal 2");
        TimelineVisitStore.merge(app,new JSONArray().put(visit));
        assertEquals(0,PubVisitStore.reconcile(app,CollectionMapCatalog.spoons(app)));
        assertTrue(PubVisitStore.completed(app).isEmpty());
        assertTrue(PubVisitStore.pending(app,CollectionMapCatalog.spoons(app)).length()>0);
        try{PubVisitStore.confirmTimeline(app,"building","spoons-missing");fail();}
        catch(IllegalArgumentException expected){}
        PubVisitStore.confirmTimeline(app,"building",pub.getString("id"));
        assertEquals(1,PubVisitStore.completed(app).size());
        assertEquals("confirmed_timeline_visit",PubVisitStore.records(app).getJSONObject(0).getString("source"));
    }
    @Test public void passingCoordinatesOrFutureVisitsDoNotProducePubVisits() throws Exception {
        JSONObject route=visit("route",pub.getString("name")).put("source","android_activity_capture");
        JSONObject future=visit("future",pub.getString("name")).put("end",Instant.now().plusSeconds(3600).toString());
        JSONObject shortVisit=visit("short",pub.getString("name"));
        shortVisit.put("end",shortVisit.getString("start"));
        TimelineVisitStore.merge(app,new JSONArray().put(route).put(future).put(shortVisit));
        assertEquals(0,PubVisitStore.reconcile(app,CollectionMapCatalog.spoons(app)));
        assertEquals(0,PubVisitStore.pending(app,CollectionMapCatalog.spoons(app)).length());
    }
    @Test public void duplicateNearbyNamesAreAmbiguous() throws Exception {
        JSONObject other=new JSONObject(pub.toString()).put("id","spoons-osm-n999999999");
        JSONArray nearby=new JSONArray().put(pub).put(other);
        assertEquals("",PubVisitEvidence.namedMatch(visit("ambiguous",pub.getString("name")),nearby));
    }
    @Test public void curatedNamedVisitStillNeedsReview() throws Exception {
        JSONObject curated=new JSONObject(pub.toString()).put("id","spoons-curated-building");
        assertEquals("",PubVisitEvidence.namedMatch(visit("building",pub.getString("name")),new JSONArray().put(curated)));
    }
    @Test public void removingWrongTimelineProofPreventsReimportAward() throws Exception {
        TimelineVisitStore.merge(app,new JSONArray().put(visit("remove",pub.getString("name"))));
        PubVisitStore.reconcile(app,CollectionMapCatalog.spoons(app));
        String key=PubVisitStore.records(app).getJSONObject(0).getString("record_key");
        PubVisitStore.removeProof(app,key);
        assertEquals(0,PubVisitStore.reconcile(app,CollectionMapCatalog.spoons(app)));
        assertTrue(PubVisitStore.completed(app).isEmpty());
    }
    @Test public void dismissingVisitDoesNotAwardOrReturnAfterReimport() throws Exception {
        JSONObject visit=visit("rejected","Shared holiday park");
        TimelineVisitStore.merge(app,new JSONArray().put(visit));
        PubVisitStore.dismissTimeline(app,"rejected");
        TimelineVisitStore.merge(app,new JSONArray().put(visit));
        assertEquals(0,PubVisitStore.reconcile(app,CollectionMapCatalog.spoons(app)));
        assertEquals(0,PubVisitStore.pending(app,CollectionMapCatalog.spoons(app)).length());
        assertTrue(PubVisitStore.completed(app).isEmpty());
    }
    @Test public void freshLiveFixRequiresNamedConfirmation() throws Exception {
        try{PubVisitStore.confirmLive(app,pub.getString("id"),fix(),false);fail();}
        catch(IllegalArgumentException expected){}
        assertTrue(PubVisitStore.completed(app).isEmpty());
        PubVisitStore.confirmLive(app,pub.getString("id"),fix(),true);
        assertTrue(PubVisitStore.completed(app).contains(pub.getString("id")));
        PubVisitStore.confirmLive(app,pub.getString("id"),fix(),true);
        assertEquals(1,PubVisitStore.completed(app).size());
        assertEquals("confirmed_live_checkin",PubVisitStore.records(app).getJSONObject(0).getString("source"));
    }
    @Test public void staleInaccurateMockAndRemoteFixesAreRejected() throws Exception {
        for(int scenario=0;scenario<4;scenario++){
            Location fix=fix();
            if(scenario==0)fix.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos()-61_000_000_000L);
            if(scenario==1)fix.setAccuracy(100);
            if(scenario==2)LocationCompat.setMock(fix,true);
            if(scenario==3)fix.setLatitude(fix.getLatitude()+.1);
            assertFalse(PubVisitEvidence.liveProblem(pub,fix,SystemClock.elapsedRealtimeNanos()).isEmpty());
            try{PubVisitStore.confirmLive(app,pub.getString("id"),fix,true);fail();}
            catch(IllegalArgumentException expected){}
        }
        assertTrue(PubVisitStore.completed(app).isEmpty());
    }
    @Test public void removedCollectionCannotRecordAndReinstallRetainsAcceptedProofs() throws Exception {
        PubVisitStore.confirmLive(app,pub.getString("id"),fix(),true);
        CollectionAccessStore.remove(app,CollectionAccessStore.SPOONS);
        try{PubVisitStore.confirmLive(app,pub.getString("id"),fix(),true);fail();}
        catch(IllegalStateException expected){}
        CollectionAccessStore.installForTesting(app,CollectionAccessStore.SPOONS);
        assertEquals(1,PubVisitStore.completed(app).size());
        assertEquals(0,PubVisitStore.reconcile(app,new JSONArray()));
        assertEquals(1,PubVisitStore.completed(app).size());
    }
    @Test public void deletingAllHistoryClearsPubProofsButKeepsEntitlement() throws Exception {
        TimelineVisitStore.merge(app,new JSONArray().put(visit("delete",pub.getString("name"))));
        PubVisitStore.reconcile(app,CollectionMapCatalog.spoons(app));
        ServiceStationStore.clearOnDeleteAll(app);
        assertTrue(PubVisitStore.completed(app).isEmpty());
        assertTrue(CollectionAccessStore.entitled(app,CollectionAccessStore.SPOONS));
        assertEquals(0,TimelineVisitStore.all(app).length());
    }
    @Test public void backupRestoresProofsAndReconciliationWaitsForHistoryReplacement() throws Exception {
        PubVisitStore.confirmLive(app,pub.getString("id"),fix(),true);
        java.io.ByteArrayOutputStream archive=new java.io.ByteArrayOutputStream();
        RoadprintsBackup.export(app,archive);
        PubVisitStore.clear(app);
        assertTrue(PubVisitStore.completed(app).isEmpty());
        RoadprintsBackup.restore(app,new java.io.ByteArrayInputStream(archive.toByteArray()));
        assertEquals(1,PubVisitStore.completed(app).size());
        TimelineVisitStore.merge(app,new JSONArray().put(visit("restore",pub.getString("name"))));
        PubVisitStore.beginHistoryReplacement();
        try{assertEquals(0,PubVisitStore.reconcile(app,CollectionMapCatalog.spoons(app)));}
        finally{PubVisitStore.endHistoryReplacement();}
        assertEquals(1,PubVisitStore.reconcile(app,CollectionMapCatalog.spoons(app)));
    }

    @Test public void oldProofReceivesReviewedTownAndNationWithoutChangingItsVisit() throws Exception {
        TimelineVisitStore.merge(app,new JSONArray().put(visit("metadata",pub.getString("name"))));
        PubVisitStore.reconcile(app,CollectionMapCatalog.spoons(app));
        String raw=app.getSharedPreferences(VenueVisitStore.PREFS,0).getString(CollectionAccessStore.SPOONS+":records","{}");
        JSONObject saved=new JSONObject(raw),proof=saved.getJSONObject("timeline:metadata");
        String when=proof.getString("visited_at");proof.remove("nation");proof.put("town","");
        app.getSharedPreferences(VenueVisitStore.PREFS,0).edit().putString(CollectionAccessStore.SPOONS+":records",saved.toString()).commit();
        assertEquals(0,PubVisitStore.reconcile(app,CollectionMapCatalog.spoons(app)));
        JSONObject corrected=PubVisitStore.records(app).getJSONObject(0);
        assertEquals(when,corrected.getString("visited_at"));assertEquals("England",corrected.getString("nation"));
        assertEquals(pub.getString("town"),corrected.getString("town"));assertEquals(1,PubVisitStore.completed(app).size());
    }
}
