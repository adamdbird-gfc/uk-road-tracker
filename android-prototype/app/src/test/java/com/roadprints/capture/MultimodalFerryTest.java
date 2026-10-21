package com.roadprints.capture;

import android.content.Context;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class MultimodalFerryTest {
    Context app;
    @Before public void setup(){app=RuntimeEnvironment.getApplication();JourneyStore.deleteAll(app);}
    @After public void cleanup(){JourneyStore.deleteAll(app);}
    static JSONArray row(double lon,double lat,long seconds,double speed) throws Exception {
        return new JSONArray().put(lon).put(lat).put(8).put(1_000_000L+seconds*1000).put(speed);
    }
    static JSONArray crossing() throws Exception {
        JSONArray rows=new JSONArray().put(row(1.30,51.12,0,8)).put(row(1.333,51.126,300,8));
        for(int i=0;i<9;i++)rows.put(row(1.48+i*.025,51.12-i*.008,900+i*180,10));
        rows.put(row(1.875,50.981,3300,8)).put(row(1.877,50.98,3360,8));
        rows.put(row(1.92,50.945,3660,16)).put(row(1.94,50.93,3780,20));return rows;
    }
    static JSONObject journey(String id,JSONArray rows) throws Exception {
        JSONArray coords=new JSONArray();for(int i=0;i<rows.length();i++)coords.put(new JSONArray().put(rows.getJSONArray(i).get(0)).put(rows.getJSONArray(i).get(1)));
        return new JSONObject().put("journey_id",id).put("revision",1).put("mode","driving").put("transport_confirmation","confirmed")
                .put("source",new JSONObject().put("type","android_activity_capture"))
                .put("capture_route_samples",rows).put("raw_capture_samples",rows)
                .put("raw_capture_geometry",new JSONObject().put("type","LineString").put("coordinates",coords))
                .put("route_geometry",new JSONObject().put("type","LineString").put("coordinates",coords));
    }
    JSONArray fixture(String name) throws Exception {
        try(java.io.InputStream input=getClass().getResourceAsStream("/multimodal/"+name+".json")) {
            return new JSONObject(new String(input.readAllBytes(),StandardCharsets.UTF_8)).getJSONArray("capture_route_samples");
        }
    }
    @Test public void supportedCorridorFindsTerminalTransitions() throws Exception {
        FerryJourneyEvidence.Review r=FerryJourneyEvidence.inspect(crossing());assertTrue(r.canSplit());assertEquals(1,r.start);assertEquals(12,r.end);
    }
    @Test public void recordedCalaisRoadHasNoFerryToRecover() throws Exception {
        assertFalse(FerryJourneyEvidence.inspect(fixture("calais-road")).hasMarineEvidence());
        JourneyStore.save(app,journey("calais",fixture("calais-road")));
        try{FerryJourneyRecovery.split(app,"calais",1);fail();}catch(IllegalStateException expected){}
        assertEquals(1,JourneyStore.count(app));
    }
    @Test public void doverCoachQueueIsNotFerry() throws Exception {assertFalse(FerryJourneyEvidence.inspect(fixture("dover-coach")).hasMarineEvidence());}
    @Test public void singleOffshoreFixAndTerminalJumpNeverQualify() throws Exception {
        JSONArray rows=new JSONArray().put(row(1.333,51.126,0,8)).put(row(1.6,51.08,30,10)).put(row(1.875,50.981,3000,8));
        assertFalse(FerryJourneyEvidence.inspect(rows).hasMarineEvidence());
    }
    @Test public void tunnelAndCoastalRoadDoNotQualify() throws Exception {
        JSONArray rows=new JSONArray();for(int i=0;i<20;i++)rows.put(row(1.15+i*.035,51.09-i*.009,180*i,18));
        assertFalse(FerryJourneyEvidence.inspect(rows).hasMarineEvidence());
        rows=new JSONArray();for(int i=0;i<20;i++)rows.put(row(1.31+i*.001,51.12,30*i,8));
        assertFalse(FerryJourneyEvidence.inspect(rows).hasMarineEvidence());
    }
    @Test public void longGapsDoNotPoolUnrelatedOffshoreFixes() throws Exception {
        JSONArray rows=new JSONArray();for(int i=0;i<7;i++)rows.put(row(1.49+i*.025,51.10-i*.005,1200*i,8));
        assertFalse(FerryJourneyEvidence.inspect(rows).hasMarineEvidence());
    }
    @Test public void intermittentReceptionStillRequiresMultipleMeasuredMarineFixes() throws Exception {
        JSONArray rows=crossing();rows.put(5,row(1.56,51.096,1500,8));assertTrue(FerryJourneyEvidence.inspect(rows).canSplit());
    }
    @Test public void missingTerminalIsReviewOnly() throws Exception {
        JSONArray rows=crossing();JSONArray partial=new JSONArray();for(int i=2;i<rows.length();i++)partial.put(rows.get(i));
        FerryJourneyEvidence.Review r=FerryJourneyEvidence.inspect(partial);assertTrue(r.hasMarineEvidence());assertFalse(r.canSplit());
    }
    @Test public void splitIsIdempotentAndRestoreRetainsOriginalEvidence() throws Exception {
        JSONObject source=journey("recorded",crossing());String raw=source.getJSONArray("raw_capture_samples").toString();JourneyStore.save(app,source);
        long ncn=JourneyStore.ncnDataRevision(app);
        assertEquals(3,FerryJourneyRecovery.split(app,"recorded",1));assertEquals(3,FerryJourneyRecovery.split(app,"recorded",1));assertEquals(3,JourneyStore.count(app));
        assertEquals("ferry",JourneyStore.get(app,"recorded-ferry-v1-1").getString("mode"));assertTrue(JourneyStore.ncnDataRevision(app)>ncn);
        FerryJourneyRecovery.restore(app,"recorded-ferry-v1-1");assertEquals(1,JourneyStore.count(app));
        assertEquals(raw,JourneyStore.get(app,"recorded").getJSONArray("raw_capture_samples").toString());
        assertEquals("confirmed",JourneyStore.get(app,"recorded").getString("transport_confirmation"));
    }
    @Test public void changedRevisionCannotBeSplitUsingStaleReview() throws Exception {
        JourneyStore.save(app,journey("recorded",crossing()).put("revision",2));
        try{FerryJourneyRecovery.split(app,"recorded",1);fail();}catch(IllegalStateException expected){}
        assertEquals(1,JourneyStore.count(app));
    }
    @Test public void confirmedModeAndBoundedCorrectionHistorySurviveSaves() throws Exception {
        JourneyStore.save(app,journey("recorded",crossing()));JourneyStore.updateMode(app,"recorded","bus");
        JSONObject saved=JourneyStore.get(app,"recorded");assertEquals("bus",saved.getString("mode"));assertEquals("confirmed",saved.getString("transport_confirmation"));
        assertEquals("manual_confirmation",saved.getJSONArray("transport_changes").getJSONObject(0).getString("source"));
        JourneyStore.save(app,saved);assertEquals("bus",JourneyStore.get(app,"recorded").getString("mode"));
    }
    @Test public void slowCoachWithoutBicycleHintStaysUnknown() throws Exception {
        JSONObject stop=new JSONObject().put("latitude",0).put("longitude",0).put("accuracy_m",5).put("kind","estimated");
        List<FootTraceValidator.Sample> points=new ArrayList<>();for(int i=0;i<20;i++)points.add(new FootTraceValidator.Sample(i*50/111195.0,0,5,1000000L+i*10000,5, i));
        PlaceVisitEvidence.Decision result=PlaceVisitEvidence.inspect(stop,points,null);assertNotNull(result);assertEquals("unknown",result.mode);
        assertEquals("cycling",PlaceVisitEvidence.inspect(stop,points,"cycling").mode);
    }
    @Test public void stationaryVehicleDoesNotBecomeJourney() throws Exception {
        JSONObject stop=new JSONObject().put("latitude",0).put("longitude",0).put("accuracy_m",5);
        List<FootTraceValidator.Sample> points=new ArrayList<>();for(int i=0;i<30;i++)points.add(new FootTraceValidator.Sample(i%2/111195.0,0,8,1000000+i*10000,0,i));
        assertNull(PlaceVisitEvidence.inspect(stop,points,null));
    }
    @Test public void failureCategoriesAndRetriesAreBounded() {
        assertEquals("dns",MatchFailure.category(new java.net.UnknownHostException()));assertTrue(MatchFailure.retryNow(new java.net.UnknownHostException(),0));
        assertFalse(MatchFailure.retryNow(new java.net.UnknownHostException(),1));
        assertEquals("backend_timeout",MatchFailure.category(new MatchFailure(504,"timeout")));assertTrue(MatchFailure.retryable(new MatchFailure(504,"timeout")));
        assertFalse(MatchFailure.retryNow(new MatchFailure(504,"timeout"),0));assertFalse(MatchFailure.retryable(new MatchFailure(422,"bad points")));
        assertFalse(MatchFailure.retryable(new IllegalStateException("Matched drive exceeds its GPS distance evidence")));
    }
    @Test public void mainMapShowsFerryWithoutRoadCoverageAndReusesCache() throws Exception {
        app.getSharedPreferences("roadprints_ncn_national_v2",0).edit().putBoolean("enabled",false).commit();
        JourneyStore.save(app,journey("crossing",crossing()).put("mode","ferry").put("processing_status","pending"));
        org.robolectric.android.controller.ActivityController<MapActivity> controller=Robolectric.buildActivity(MapActivity.class);
        MapActivity screen=controller.get();
        java.lang.reflect.Method method=MapActivity.class.getDeclaredMethod("readMapRoutes",JourneyFilters.class,java.time.LocalDate.class,java.time.ZoneId.class);method.setAccessible(true);
        MapActivity.MapRoutes routes=(MapActivity.MapRoutes)method.invoke(screen,JourneyFilters.all(),java.time.LocalDate.now(),java.time.ZoneOffset.UTC);
        assertEquals(1,routes.ferrySections.size());assertTrue(routes.sections.isEmpty());assertTrue(routes.motorwaySections.isEmpty());
        routes=(MapActivity.MapRoutes)method.invoke(screen,JourneyFilters.all(),java.time.LocalDate.now(),java.time.ZoneOffset.UTC);
        assertEquals(1,routes.ferrySections.size());
        assertTrue(JourneyFilters.matchesFilter(JourneyStore.get(app,"crossing"),7));
        java.lang.reflect.Method encode=MapActivity.class.getDeclaredMethod("mapRoutesToCache",MapActivity.MapRoutes.class);encode.setAccessible(true);
        assertEquals(1,((JSONObject)encode.invoke(screen,routes)).getJSONArray("ferries").length());
    }
    @Test public void nonEligibleMatchingStatusDoesNotInvalidateNcn() throws Exception {
        JSONObject j=journey("road",crossing()).put("processing_status","pending");String before=JourneyStore.ncnEvidenceHash(j);
        j.put("processing_status","failed").put("last_match_attempt",new JSONObject().put("failure_category","dns"));
        assertEquals(before,JourneyStore.ncnEvidenceHash(j));
        j.put("mode","walking");assertNotEquals(before,JourneyStore.ncnEvidenceHash(j));
    }
    @Test public void overseasRoadIdentitiesNeverEnterUkContributions() throws Exception {
        JSONArray line=new JSONArray().put(new JSONArray().put(2.55).put(49.07)).put(new JSONArray().put(2.56).put(49.08));
        JSONObject feature=new JSONObject().put("type","Feature").put("properties",new JSONObject().put("road_ref","A1").put("distance_m",10000))
                .put("geometry",new JSONObject().put("type","LineString").put("coordinates",line));
        JSONObject collection=new JSONObject().put("type","FeatureCollection").put("features",new JSONArray().put(feature));
        JSONObject j=journey("france",fixture("calais-road")).put("processing_status","complete")
                .put("processing_result",new JSONObject().put("a_road_geojson",collection).put("motorway_geojson",collection));
        assertFalse(UkRoadScope.candidateFeature(feature));
        assertEquals(0,new ARoadProgressCalculator(app).contribution(j).length());
        assertEquals(0,new MotorwayProgressCalculator(app).contribution(j).length());
        assertTrue(UkRoadScope.candidate(1.333,51.126));assertFalse(UkRoadScope.candidate(1.875,50.981));
        JourneyStore.save(app,j);assertNotNull(JourneyStore.get(app,"france"));
    }
    @Test public void interruptedCorrectionJournalReplaysBeforeArchiveRead() throws Exception {
        JSONObject j=journey("replay",crossing());
        FerryJourneyRecovery.write(new java.io.File(app.getFilesDir(),"ferry_recovery_pending.json"),new JSONObject().put("records",new JSONArray().put(j)));
        assertNotNull(JourneyStore.get(app,"replay"));assertEquals(1,JourneyStore.count(app));assertNotNull(JourneyStore.get(app,"replay"));
    }
    @Test public void rejectedDistanceRetainsReturnedEvidenceAndThreshold() throws Exception {
        JSONArray points=new JSONArray().put(new JSONObject().put("lat",51).put("lng",1)).put(new JSONObject().put("lat",51.001).put("lng",1));
        JSONObject result=new JSONObject().put("matched_distance_m",5000),details=DrivingJourneyPreparation.distanceIntegrity(result,points);
        assertFalse(details.getBoolean("accepted"));assertEquals(5000,details.getDouble("matched_distance_m"),0);assertTrue(details.getDouble("matched_to_evidence_ratio")>40);
        try{DrivingJourneyPreparation.validateDistance(result,points);fail();}catch(IllegalStateException expected){}
    }
}
