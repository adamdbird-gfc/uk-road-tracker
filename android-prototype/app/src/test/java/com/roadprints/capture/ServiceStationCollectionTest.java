package com.roadprints.capture;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class ServiceStationCollectionTest {
    private Context app;
    @Before public void reset() throws Exception {
        app=RuntimeEnvironment.getApplication();drain();JourneyStore.deleteAll(app);
        app.getSharedPreferences("roadprints_collections_v1",0).edit().clear().commit();
        app.getSharedPreferences("roadprints_achievements_v1",0).edit().clear().commit();
    }
    private void drain() throws Exception {
        java.lang.reflect.Field f=ServiceStationStore.class.getDeclaredField("BACKFILL_EXECUTOR");f.setAccessible(true);
        ((ExecutorService)f.get(null)).submit(()->{}).get(20,java.util.concurrent.TimeUnit.SECONDS);
    }
    private JSONObject station(String id,double lng,double lat) throws Exception {
        return new JSONObject().put("id",id).put("name",id).put("road","M1").put("operator_group","Moto")
                .put("country","England").put("lat",lat).put("lng",lng)
                .put("points",new JSONArray().put(new JSONObject().put("lng",lng).put("lat",lat)));
    }
    private JSONArray samples(double lng,double lat,double accuracy,long gap,double speed) throws Exception {
        JSONArray result=new JSONArray();
        for(int i=0;i<5;i++)result.put(new JSONArray().put(lng).put(lat).put(accuracy).put(1_000_000L+i*gap).put(speed));
        return result;
    }
    private JSONObject capture(String id,JSONArray samples) throws Exception {
        return new JSONObject().put("journey_id",id).put("source",new JSONObject().put("type","android_activity_capture"))
                .put("started_at","2026-10-07T10:00:00Z").put("ended_at","2026-10-07T10:10:00Z")
                .put("mode","driving").put("raw_capture_samples",samples)
                .put("route_geometry",new JSONObject().put("type","LineString").put("coordinates",new JSONArray("[[-2,52],[-2.01,52.01]]")));
    }
    private JSONObject gloucester() throws Exception {
        JSONArray all=ServiceStationStore.stations(app);
        for(int i=0;i<all.length();i++)if(all.getJSONObject(i).getString("id").startsWith("msa:westmorland gloucester:"))return all.getJSONObject(i);
        throw new AssertionError("Gloucester missing");
    }
    private JSONObject gloucesterCapture(String id) throws Exception {
        JSONObject point=gloucester().getJSONArray("points").getJSONObject(0);
        return capture(id,samples(point.getDouble("lng"),point.getDouble("lat"),10,60_000,0));
    }
    @Test public void sustainedAccurateStopCountsButDrivingPastDoesNot() throws Exception {
        JSONArray stations=new JSONArray().put(station("one",-2,52));
        assertEquals(Collections.singleton("one"),ServiceStationEvidence.stops(samples(-2,52,10,60_000,0),stations));
        assertTrue(ServiceStationEvidence.stops(samples(-2,52,10,20_000,0),stations).isEmpty());
        assertTrue(ServiceStationEvidence.stops(samples(-2,52,10,60_000,20),stations).isEmpty());
        JSONArray badSpeed=samples(-2,52,10,60_000,0);
        for(int i=0;i<badSpeed.length();i++)badSpeed.getJSONArray(i).put(4,"NaN");
        assertTrue(ServiceStationEvidence.stops(badSpeed,stations).isEmpty());
    }
    @Test public void poorMissingAccuracyAndSamplingGapsNeverManufactureDwell() throws Exception {
        JSONArray stations=new JSONArray().put(station("one",-2,52));
        assertTrue(ServiceStationEvidence.stops(samples(-2,52,80,60_000,0),stations).isEmpty());
        assertTrue(ServiceStationEvidence.stops(samples(-2,52,0,60_000,0),stations).isEmpty());
        assertTrue(ServiceStationEvidence.stops(samples(-2,52,10,180_000,0),stations).isEmpty());
        assertTrue(ServiceStationEvidence.stops(samples(-2,52,10,0,0),stations).isEmpty());
        JSONArray reversed=samples(-2,52,10,60_000,0);reversed.getJSONArray(2).put(3,1L);
        assertTrue(ServiceStationEvidence.stops(reversed,stations).isEmpty());
    }
    @Test public void visitsUseSitePointsOnEitherCarriagewayAndCountTheNamedSiteOnce() throws Exception {
        JSONObject s=station("one",-2,52);
        s.getJSONArray("points").put(new JSONObject().put("lng",-2.02).put("lat",52));
        JSONArray stations=new JSONArray().put(s),both=samples(-2,52,10,60_000,0);
        JSONArray second=samples(-2.02,52,10,60_000,0);
        for(int i=0;i<second.length();i++){second.getJSONArray(i).put(3,2_000_000L+i*60_000);both.put(second.getJSONArray(i));}
        assertEquals(Collections.singleton("one"),ServiceStationEvidence.stops(both,stations));
        assertSame(s,ServiceStationEvidence.nearest(stations,52,-2.02,350));
    }
    @Test public void nearbyMainRoadAndContinuousMovementDoNotCount() throws Exception {
        JSONArray stations=new JSONArray().put(station("one",-2,52));
        assertTrue(ServiceStationEvidence.stops(samples(-2.004,52,10,60_000,0),stations).isEmpty());
        JSONArray moving=new JSONArray();
        for(int i=0;i<5;i++)moving.put(new JSONArray().put(-2+0.001*i).put(52).put(10).put(1_000_000L+i*60_000).put(-1));
        assertTrue(ServiceStationEvidence.stops(moving,stations).isEmpty());
    }
    @Test public void routeGeometryAndOldUserConfirmationsCannotEarnVisits() throws Exception {
        JSONObject journey=capture("unproved",new JSONArray()).put("service_station_candidates",new JSONArray().put(new JSONObject().put("id","one")));
        assertTrue(ServiceStationEvidence.visits(journey,new JSONArray().put(station("one",-2,52))).isEmpty());
        journey=capture("timeline",samples(-2,52,10,60_000,0));journey.getJSONObject("source").put("type","timeline_import");
        assertTrue(ServiceStationEvidence.visits(journey,new JSONArray().put(station("one",-2,52))).isEmpty());
        app.getSharedPreferences("roadprints_collections_v1",0).edit()
                .putStringSet("service_station_manual",Collections.singleton("one"))
                .putString("service_station_confirmed_journey_visits_v1","[{\"station_id\":\"one\",\"journey_id\":\"unproved\"}]").commit();
        assertTrue(ServiceStationStore.completed(app).isEmpty());
    }
    @Test public void captureBeforePurchaseIsRetainedAndUnlockRecognisesItRetrospectively() throws Exception {
        JSONObject journey=gloucesterCapture("pre-purchase");JourneyStore.save(app,journey);
        assertFalse(ServiceStationStore.unlocked(app));assertTrue(ServiceStationStore.completed(app).contains(gloucester().getString("id")));
        assertEquals(0,ServiceStationStore.achievementValues(app).get("service-farmers-friend"),0);
        ServiceStationStore.unlockForTesting(app);drain();
        assertTrue(ServiceStationStore.historicalBackfillComplete(app));
        assertEquals(1,ServiceStationStore.achievementValues(app).get("service-farmers-friend"),0);
        JSONObject earned=new JSONObject(app.getSharedPreferences("roadprints_achievements_v1",0).getString("unlocked","{}"));
        assertTrue(earned.has("service-farmers-friend"));assertTrue(earned.has("service-first-stop"));assertFalse(earned.has("service-ten-stops"));
        assertEquals(Collections.singleton("pre-purchase"),ServiceStationVisitStore.journeyIdsForStation(app,gloucester().getString("id")));
    }
    @Test public void oldCaptureArchiveBackfillsAndIgnoresLargeMatchedGeometry() throws Exception {
        JSONObject journey=gloucesterCapture("old-archive");
        journey.put("processing_result",new JSONObject().put("geojson",new JSONObject().put("irrelevant","matching does not prove a visit")));
        java.nio.file.Files.write(new java.io.File(app.getFilesDir(),"journey_old-archive.json").toPath(),journey.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        ServiceStationStore.unlockForTesting(app);drain();
        assertTrue(ServiceStationStore.completed(app).contains(gloucester().getString("id")));
        java.util.List<JSONObject> evidence=new java.util.ArrayList<>();JourneyStore.forEachServiceStationEvidence(app,evidence::add);
        assertFalse(evidence.get(0).has("processing_result"));assertFalse(evidence.get(0).has("route_geometry"));assertTrue(evidence.get(0).has("raw_capture_samples"));
    }
    @Test public void timelineImportsCountWhileLockedAndEitherSitePointMatches() throws Exception {
        JSONObject s=gloucester(),point=s.getJSONArray("points").getJSONObject(1);
        JSONArray visits=new JSONArray().put(new JSONObject().put("id","confirmed-place")
                .put("lat",point.getDouble("lat")).put("lng",point.getDouble("lng")));
        ServiceStationStore.recordConfirmedTimelineVisits(app,visits);ServiceStationStore.recordConfirmedTimelineVisits(app,visits);
        assertEquals(1,ServiceStationStore.completed(app).size());assertEquals(1,TimelineVisitStore.all(app).length());
        ServiceStationStore.unlockForTesting(app);drain();
        assertEquals(1,ServiceStationStore.achievementValues(app).get("service-farmers-friend"),0);
    }
    @Test public void repeatCapturesDoNotInflateCountsAndDeletionRemovesOnlyUnsupportedVisits() throws Exception {
        JourneyStore.save(app,gloucesterCapture("a"));JourneyStore.save(app,gloucesterCapture("b"));
        assertEquals(1,ServiceStationStore.completed(app).size());JourneyStore.delete(app,"a");
        assertEquals(1,ServiceStationStore.completed(app).size());JourneyStore.delete(app,"b");assertTrue(ServiceStationStore.completed(app).isEmpty());
        ServiceStationStore.unlockForTesting(app);drain();JourneyStore.deleteAll(app);assertTrue(ServiceStationStore.unlocked(app));
    }
    @Test public void milestoneBoundariesAndEmptyGroupsCannotUnlock() throws Exception {
        JSONArray catalogue=new JSONArray();Set<String> visited=new HashSet<>();
        for(int i=0;i<4;i++)catalogue.put(station("s"+i,-2,52));visited.add("s0");
        ServiceStationAchievements.Snapshot goals=ServiceStationAchievements.calculate(catalogue,visited,true);
        assertEquals(25,goals.values.get("service-percent-25"),0);
        assertNull(goals.values.get(ServiceStationAchievements.roadId("M99")));
        assertEquals(0,goals.values.get("service-extra-mile"),0);
        assertEquals(4,goals.lists.get("service-loyalty-points").size());
        visited.addAll(Arrays.asList("s1","s2","s3"));goals=ServiceStationAchievements.calculate(catalogue,visited,true);
        assertEquals(100,goals.values.get("service-percent-100"),0);
        assertEquals(100,goals.values.get("service-loyalty-points"),0);
        assertEquals(0,ServiceStationAchievements.calculate(catalogue,visited,false).values.get("service-percent-100"),0);
    }
    @Test public void catalogueExpansionPreservesEarnedMilestonesWhileEvidenceDeletionRevokesThem() throws Exception {
        java.lang.reflect.Field field=ServiceStationStore.class.getDeclaredField("catalogue");field.setAccessible(true);
        Object original=field.get(null);JSONArray sites=new JSONArray();
        for(int i=0;i<4;i++)sites.put(station("old"+i,-2+i*.1,52));
        field.set(null,sites);
        try {
            ServiceStationStore.recordConfirmedTimelineVisits(app,new JSONArray().put(new JSONObject().put("id","old-visit").put("lat",52).put("lng",-2)));
            ServiceStationStore.unlockForTesting(app);drain();
            assertEquals(25,ServiceStationStore.achievementSnapshot(app).values.get("service-percent-25"),0);
            sites.put(station("new-site",-3,52));
            ServiceStationAchievements.Snapshot goals=ServiceStationStore.achievementSnapshot(app);
            assertEquals(25,goals.values.get("service-percent-25"),0);
            assertTrue(goals.display.get("service-percent-25").contains("retained"));
            TimelineVisitStore.clear(app);ServiceStationStore.recordConfirmedTimelineVisits(app,new JSONArray());drain();
            assertEquals(0,ServiceStationStore.achievementSnapshot(app).values.get("service-percent-25"),0);
        } finally {field.set(null,original);}
    }
    @Test public void catalogueHasGoalsForEveryRoadAndAllNamedStops() throws Exception {
        JSONArray catalogue=ServiceStationStore.stations(app);Set<String> roads=new HashSet<>(),all=new HashSet<>();
        for(int i=0;i<catalogue.length();i++){JSONObject s=catalogue.getJSONObject(i);roads.add(ServiceStationAchievements.groupRoad(s));all.add(s.getString("id"));assertFalse(s.optString("country").isEmpty());}
        assertEquals(roads,new HashSet<>(Arrays.asList(ServiceStationAchievements.ROADS)));
        ServiceStationAchievements.Snapshot goals=ServiceStationAchievements.calculate(catalogue,all,true);
        for(String[] special:ServiceStationAchievements.SPECIAL)assertEquals(special[1],1,goals.values.get(special[0]),0);
        assertEquals(3,goals.values.get("service-farm-to-motorway"),0);assertEquals(3,goals.values.get("service-three-nations"),0);
        for(AchievementStore.Definition d:AchievementStore.definitions())assertNotEquals("service-ten-stops",d.id);
    }
}
