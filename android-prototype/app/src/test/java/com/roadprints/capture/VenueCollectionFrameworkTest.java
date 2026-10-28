package com.roadprints.capture;
import android.content.Context;
import android.location.Location;
import android.os.SystemClock;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import java.time.Instant;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class VenueCollectionFrameworkTest {
    private Context app;private JSONObject pub,service;
    @Before public void reset()throws Exception{
        app=RuntimeEnvironment.getApplication();JourneyStore.deleteAll(app);TimelineVisitStore.clear(app);
        app.getSharedPreferences(VenueVisitStore.PREFS,0).edit().clear().commit();
        app.getSharedPreferences(PubVisitStore.PREFS,0).edit().clear().commit();
        app.getSharedPreferences(CollectionAccessStore.PREFS,0).edit().clear().commit();
        app.getSharedPreferences("roadprints_collections_v1",0).edit().clear().commit();
        CollectionAccessStore.installForTesting(app,CollectionAccessStore.SPOONS);
        app.getSharedPreferences("roadprints_collections_v1",0).edit().putBoolean("service_stations_unlocked",true).putBoolean("automatic_visits_v2_migration",true).putBoolean("confirmed_timeline_only_v1",true).commit();
        CollectionAccessStore.installForTesting(app,CollectionAccessStore.SERVICES);
        JSONArray pubs=CollectionMapCatalog.spoons(app);
        for(int i=0;i<pubs.length();i++)if("The Robert Pocock".equals(pubs.getJSONObject(i).optString("name")))pub=pubs.getJSONObject(i);
        assertNotNull(pub);service=ServiceStationStore.stations(app).getJSONObject(0);
    }
    private Location fix(JSONObject row)throws Exception{
        JSONObject point=row.optJSONArray("points")==null?row:row.getJSONArray("points").getJSONObject(0);
        Location fix=new Location("gps");fix.setLatitude(point.getDouble("lat"));fix.setLongitude(point.getDouble("lng"));
        fix.setAccuracy(5);fix.setTime(System.currentTimeMillis());fix.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());return fix;
    }
    private JSONObject capture(JSONObject row,boolean named,double speed)throws Exception{
        Location location=fix(row);long at=System.currentTimeMillis()-600000;JSONArray samples=new JSONArray();
        for(int i=0;i<5;i++)samples.put(new JSONArray().put(location.getLongitude()).put(location.getLatitude()).put(5).put(at+i*60000).put(speed));
        JSONObject arrival=new JSONObject().put("role","arrival").put("name",named?row.getString("name"):"Building")
                .put("classification","supported").put("source","map_footprint_and_dwell").put("dwell_seconds",240)
                .put("latitude",location.getLatitude()).put("longitude",location.getLongitude()).put("accuracy_m",5);
        return new JSONObject().put("journey_id","framework-test").put("source",new JSONObject().put("type","android_activity_capture"))
                .put("raw_capture_samples",samples).put("stops",new JSONArray().put(arrival));
    }
    @Test public void manualCheckinUsesIdenticalFreshLocationPolicyForBothCollections()throws Exception{
        for(String id:new String[]{"services","wetherspoons"}){
            JSONObject row="services".equals(id)?service:pub;Location location=fix(row);
            VenueVisitStore.confirmLive(app,id,row.getString("id"),location,true);
            assertTrue(VenueVisitStore.completed(app,id).contains(row.getString("id")));
            location.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos()-61000000000L);
            try{VenueVisitStore.confirmLive(app,id,row.getString("id"),location,true);fail();}catch(IllegalArgumentException expected){}
        }
    }
    @Test public void pubProximityAloneCreatesReviewWhileNamedSupportedStopCanBeAutomatic()throws Exception{
        JSONArray rows=new JSONArray().put(pub);
        assertFalse(VenueEvidence.nativeVisits("wetherspoons",capture(pub,false,0),rows).getJSONObject(0).getBoolean("automatic"));
        assertTrue(VenueEvidence.nativeVisits("wetherspoons",capture(pub,true,0),rows).getJSONObject(0).getBoolean("automatic"));
        assertEquals(0,VenueEvidence.nativeVisits("wetherspoons",capture(pub,true,12),rows).length());
    }
    @Test public void sharedBuildingPubAlwaysNeedsConfirmation()throws Exception{
        JSONObject row=new JSONObject(pub.toString()).put("id","spoons-curated-fixture").put("location_label","Approximate building location");
        assertFalse(VenueEvidence.nativeVisits("wetherspoons",capture(row,true,0),new JSONArray().put(row)).getJSONObject(0).getBoolean("automatic"));
    }
    @Test public void servicesStopAndNamedPubUseSameDetectorWithoutCountingPassing()throws Exception{
        assertTrue(VenueEvidence.nativeVisits("services",capture(service,false,0),new JSONArray().put(service)).getJSONObject(0).getBoolean("automatic"));
        assertEquals(0,VenueEvidence.nativeVisits("services",capture(service,false,20),new JSONArray().put(service)).length());
    }
    @Test public void legacyPubProofMigratesOnceWithDateAndRejectionIntact()throws Exception{
        app.getSharedPreferences(VenueVisitStore.PREFS,0).edit().clear().commit();
        JSONObject proof=new JSONObject().put("pub_id",pub.getString("id")).put("name",pub.getString("name")).put("source","confirmed_live_checkin").put("visited_at","2026-09-01T12:00:00Z");
        JSONObject saved=new JSONObject().put("live:old",proof).put("timeline:rejected",new JSONObject().put("dismissed",true));
        app.getSharedPreferences(PubVisitStore.PREFS,0).edit().putString("records",saved.toString()).commit();
        assertEquals(1,PubVisitStore.records(app).length());assertEquals("2026-09-01T12:00:00Z",PubVisitStore.records(app).getJSONObject(0).getString("visited_at"));
        assertEquals(1,PubVisitStore.records(app).length());
        PubVisitStore.clear(app);assertEquals(0,PubVisitStore.records(app).length());
    }
    @Test public void legacyServiceJourneyMigratesWithoutInventingDate()throws Exception{
        app.getSharedPreferences(VenueVisitStore.PREFS,0).edit().clear().commit();
        JSONObject links=new JSONObject().put("old-drive",new JSONArray().put(service.getString("id")));
        app.getSharedPreferences("roadprints_collections_v1",0).edit().putString("service_station_detected_journey_visits_v2",links.toString()).commit();
        assertTrue(VenueVisitStore.completed(app,"services").contains(service.getString("id")));
        JSONObject proof=VenueVisitStore.records(app,"services").getJSONObject(0);assertEquals("",proof.getString("visited_at"));
        assertTrue(ServiceStationVisitStore.journeyIdsForStation(app,service.getString("id")).contains("old-drive"));
        VenueVisitStore.removeProof(app,"services",proof.getString("record_key"));
        assertFalse(ServiceStationStore.completed(app).contains(service.getString("id")));
    }
    @Test public void nativeReplayDeduplicatesAndDeleteDoesNotResurrectVisit()throws Exception{
        JSONObject journey=capture(pub,true,0);
        VenueVisitStore.recordJourney(app,"wetherspoons",journey);VenueVisitStore.recordJourney(app,"wetherspoons",journey);
        assertEquals(1,PubVisitStore.records(app).length());
        PubVisitStore.removeProof(app,PubVisitStore.records(app).getJSONObject(0).getString("record_key"));
        VenueVisitStore.recordJourney(app,"wetherspoons",journey);assertEquals(0,PubVisitStore.records(app).length());
        VenueVisitStore.forgetJourney(app,"framework-test");assertEquals(0,PubVisitStore.records(app).length());
    }
    @Test public void automaticAndManualSameVisitHaveOneHistoryEntryWithBothProofs()throws Exception{
        VenueVisitStore.recordJourney(app,"wetherspoons",capture(pub,true,0));
        PubVisitStore.confirmLive(app,pub.getString("id"),fix(pub),true);
        assertEquals(1,PubVisitStore.records(app).length());
        assertEquals(2,PubVisitStore.records(app).getJSONObject(0).getJSONArray("evidence_records").length());
        assertEquals(1,PubVisitStore.completed(app).size());
        PubVisitStore.removeProof(app,PubVisitStore.records(app).getJSONObject(0).getString("record_key"));
        assertEquals(0,PubVisitStore.records(app).length());
    }
    @Test public void definitionFixtureUsesSharedQualificationWithoutCopiedUi()throws Exception{
        JSONObject fixture=new JSONObject().put("id","fixture-restaurant").put("name","Fixture restaurant").put("lat",51.4).put("lng",.3);
        VenueCollection.Definition definition=new VenueCollection.Definition("fixture","Fixture restaurants","restaurant","restaurants",125,false,"",context->new JSONArray().put(fixture));
        assertTrue(VenueEvidence.nativeVisits(definition,capture(fixture,true,0),definition.source.load(app)).getJSONObject(0).getBoolean("automatic"));
        assertEquals(0,VenueStopDetector.stops(capture(fixture,false,10).getJSONArray("raw_capture_samples"),new JSONArray().put(fixture)).size());
    }
    @Test public void warwickFootprintSupportsLiveCheckinAndNativeReplayBeyondOldRadius()throws Exception{
        JSONObject north=VenueVisitStore.find(ServiceStationStore.stations(app),"msa:m40 warwick:52.2174:-1.504");
        JSONObject south=VenueVisitStore.find(ServiceStationStore.stations(app),"msa:warwick m40:52.2188:-1.5033");
        Location location=new Location("gps");location.setLatitude(52.2165186);location.setLongitude(-1.5053546);
        location.setAccuracy(3);location.setTime(System.currentTimeMillis());location.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
        assertTrue(ServiceStationEvidence.distance(north,location.getLatitude(),location.getLongitude())>125);
        assertEquals("",VenueEvidence.liveProblem("services",north,location,SystemClock.elapsedRealtimeNanos()));
        assertFalse(VenueEvidence.liveProblem("services",south,location,SystemClock.elapsedRealtimeNanos()).isEmpty());
        VenueVisitStore.confirmLive(app,"services",north.getString("id"),location,true);
        java.io.InputStream input=getClass().getResourceAsStream("/warwick-services-synthetic-stop.json");assertNotNull(input);
        JSONArray samples=new JSONArray(new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));
        JSONObject journey=new JSONObject().put("journey_id","warwick-regression").put("source",new JSONObject().put("type","android_activity_capture"))
                .put("raw_capture_samples",samples);
        JSONArray outcomes=VenueEvidence.nativeVisits("services",journey,ServiceStationStore.stations(app));
        assertEquals(1,outcomes.length());assertTrue(outcomes.getJSONObject(0).getBoolean("automatic"));
        assertEquals(north.getString("id"),outcomes.getJSONObject(0).getJSONObject("venue").getString("id"));
        VenueVisitStore.recordJourney(app,"services",journey);VenueVisitStore.recordJourney(app,"services",journey);
        assertEquals(1,VenueVisitStore.completed(app,"services").size());
    }
    @Test public void changedEvidencePolicyRescansSavedJourneyWithoutJourneyEdits()throws Exception{
        JSONObject journey=capture(service,false,0).put("mode","driving").put("started_at","2026-10-16T10:00:00Z").put("ended_at","2026-10-16T10:10:00Z");
        JourneyStore.save(app,journey);
        app.getSharedPreferences(VenueVisitStore.PREFS,0).edit().putString("services:records","{}")
                .putLong("services:native_scan_revision",JourneyStore.dataRevision(app)).putInt("services:native_evidence_version",1).commit();
        java.lang.reflect.Method scan=VenueVisitStore.class.getDeclaredMethod("ensureNativeHistory",Context.class,String.class);scan.setAccessible(true);
        scan.invoke(null,app,"services");
        assertTrue(VenueVisitStore.completed(app,"services").contains(service.getString("id")));
        assertEquals(2,app.getSharedPreferences(VenueVisitStore.PREFS,0).getInt("services:native_evidence_version",0));
    }

}
