package com.roadprints.capture;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import java.nio.charset.StandardCharsets;
import java.io.File;
import java.nio.file.Files;
import java.util.zip.GZIPInputStream;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class NcnAndroidTest {
    private Context app(){return RuntimeEnvironment.getApplication();}
    private JSONObject golden() throws Exception {return new JSONObject(new String(NcnCatalogue.read(getClass().getResourceAsStream("/ncn-android-golden.json")),StandardCharsets.UTF_8));}
    private JSONObject journey(int index) throws Exception {return golden().getJSONArray("cases").getJSONObject(index).getJSONObject("journey");}
    private double total(NcnCatalogue c,JSONObject assessment) throws Exception {NcnLedger ledger=new NcnLedger(c);ledger.put("one",assessment);return ledger.metres("NCN:344","combined");}
    @Test public void pinnedEightInventoriesLoadWithUnchangedDenominators() throws Exception {
        NcnCatalogue c=NcnCatalogue.load(app());assertEquals(8,c.routes.size());assertEquals(206,c.paths.size());
        NcnLedger ledger=new NcnLedger(c);for(String key:c.routes.keySet()){assertTrue(c.routes.get(key).getDouble("length_m")>0);assertEquals(0,ledger.metres(key,"combined"),0);}
    }
    @Test public void walkingCyclingAndLegacyMatchPythonTrialGoldenResults() throws Exception {
        NcnCatalogue c=NcnCatalogue.load(app());JSONArray cases=golden().getJSONArray("cases");
        for(int i=0;i<cases.length();i++){JSONObject row=cases.getJSONObject(i);JSONObject result=new NcnMatcher(c).assess(row.getJSONObject("journey"));
            assertEquals(row.getString("name")+": "+result.optString("reason"),row.getDouble("expected_metres"),total(c,result),.001);}
    }
    @Test public void canonicalGeometryHashesMatchPythonFloatAndUnicodeContracts() throws Exception {
        JSONArray vectors=golden().getJSONArray("geometry_hash_vectors");for(int i=0;i<vectors.length();i++){JSONObject v=vectors.getJSONObject(i);assertEquals(v.getString("sha256"),NcnJson.geometry(v.getJSONArray("coordinates")));}
        JSONObject reference=journey(0).getJSONObject("processing_result").getJSONObject("matched_path_evidence").getJSONArray("references").getJSONObject(0);
        assertTrue(NcnJson.signed(reference));
    }
    @Test public void drivingRelabelAndUnsupportedMatcherNeverGainCyclingCredit() throws Exception {
        NcnMatcher matcher=new NcnMatcher(NcnCatalogue.load(app()));JSONObject trip=journey(1);trip.put("mode","driving");assertEquals(0,matcher.assess(trip).getJSONArray("spans").length());
        trip=journey(0);trip.put("mode","cycling");assertEquals(0,matcher.assess(trip).getJSONArray("spans").length());
        trip=journey(1);trip.getJSONObject("processing_result").put("matcher","osrm_car_v1");assertEquals(0,matcher.assess(trip).getJSONArray("spans").length());
    }
    @Test public void editedGeometryOrSignedNodeMetadataFailsClosed() throws Exception {
        NcnMatcher matcher=new NcnMatcher(NcnCatalogue.load(app()));
        for(int i=0;i<2;i++){JSONObject trip=journey(i);trip.getJSONObject("processing_result").getJSONObject("geojson").getJSONArray("features").getJSONObject(0).getJSONObject("geometry").getJSONArray("coordinates").getJSONArray(0).put(0,0d);assertEquals(0,matcher.assess(trip).getJSONArray("spans").length());}
        JSONObject trip=journey(1);trip.getJSONObject("processing_result").getJSONObject("cycling_path_evidence").getJSONArray("features").getJSONObject(0).getJSONArray("legs").getJSONObject(0).getJSONArray("osm_nodes").put(0,12345);assertEquals(0,matcher.assess(trip).getJSONArray("spans").length());
    }
    @Test public void removedEdgesAndRoadCorrectionsDoNotContribute() throws Exception {
        NcnMatcher matcher=new NcnMatcher(NcnCatalogue.load(app()));JSONObject trip=journey(0);
        trip.put("journey_corrections",new JSONObject().put("removed_matched_segments",new JSONArray().put(0)));
        assertEquals(0,matcher.assess(trip).getJSONArray("spans").length());
        trip=journey(1);trip.put("journey_corrections",new JSONObject().put("removed_road_ids",new JSONArray().put("anything")));assertEquals(0,matcher.assess(trip).getJSONArray("spans").length());
    }
    @Test public void nearbyUnlistedSourceIdentityCannotGainMembership() throws Exception {
        NcnMatcher matcher=new NcnMatcher(NcnCatalogue.load(app()));JSONObject trip=journey(0);
        JSONObject ref=trip.getJSONObject("processing_result").getJSONObject("matched_path_evidence").getJSONArray("references").getJSONObject(0);
        ref.put("source_feature_id","999999999999");ref.remove("content_sha256");ref.put("content_sha256",NcnJson.digest(ref));
        assertEquals(0,matcher.assess(trip).getJSONArray("spans").length());
    }
    @Test public void repeatsModesCorrectionsAndDeletionUseIntervalUnions() throws Exception {
        NcnCatalogue c=NcnCatalogue.load(app());NcnMatcher matcher=new NcnMatcher(c);NcnLedger ledger=new NcnLedger(c);
        JSONObject foot=matcher.assess(journey(0)),cycle=matcher.assess(journey(1));double f=total(c,foot),b=total(c,cycle);
        ledger.put("one",foot);ledger.put("two",foot);assertEquals(f,ledger.metres("NCN:344","on_foot"),.001);
        ledger.put("cycle",cycle);assertEquals(b,ledger.metres("NCN:344","combined"),.001);
        ledger.put("cycle",foot);assertEquals(0,ledger.metres("NCN:344","cycling"),0);assertEquals(f,ledger.metres("NCN:344","combined"),.001);
        ledger.remove("one");ledger.remove("two");ledger.remove("cycle");assertEquals(0,ledger.metres("NCN:344","combined"),0);
    }
    @Test public void corruptedDownloadOrPublishedScopeCannotLoad() throws Exception {
        byte[] raw;try(GZIPInputStream in=new GZIPInputStream(app().getAssets().open("ncn-trial/ncn-trial.json.gz"))){raw=NcnCatalogue.read(in);}
        JSONObject manifest=new JSONObject(new String(NcnCatalogue.read(app().getAssets().open("ncn-trial/ncn-trial-manifest.json")),StandardCharsets.UTF_8));
        byte[] edited=raw.clone();edited[edited.length/2]^=1;try{new NcnCatalogue(edited,manifest);fail();}catch(IllegalArgumentException expected){}
        JSONObject root=new JSONObject(new String(raw,StandardCharsets.UTF_8));root.put("whole_published_route_ready",true);edited=root.toString().getBytes(StandardCharsets.UTF_8);manifest.put("sha256",NcnJson.hash(edited));try{new NcnCatalogue(edited,manifest);fail();}catch(IllegalArgumentException expected){}
    }
    @Test public void malformedEvidenceAfterValidSpanClearsAllPartialCredit() throws Exception {
        NcnCatalogue c=NcnCatalogue.load(app());JSONObject trip=journey(0);JSONObject evidence=trip.getJSONObject("processing_result").getJSONObject("matched_path_evidence");evidence.getJSONArray("spans").put(new JSONArray().put(0).put(1).put(0));
        assertEquals(0,new NcnMatcher(c).assess(trip).getJSONArray("spans").length());
    }
    @Test public void activationIsOffByDefaultAndNavigationCarriesModeIdentity() throws Exception {
        assertFalse(NcnProgressStore.enabled(app()));RoadDetailSelection road=new RoadDetailSelection(RoadDetailSelection.NCN,"NCN:344","","","");road.ncnMode="on_foot";
        RoadDetailSelection copy=RoadDetailSelection.from(road.intent(app(),false));assertEquals("on_foot",copy.ncnMode);assertEquals("National Cycle Route 344",copy.title());
        assertFalse(road.intent(app(),false).hasExtra("journey_ids"));
    }
    @Test public void checkpointSurvivesRestartAndDeletedHistoryCannotReturn() throws Exception {
        Context app=app();app.getSharedPreferences("roadprints_journeys_v1",0).edit().putBoolean("journeys_migrated_to_archive",true).commit();
        JSONObject trip=journey(0);trip.put("places",new JSONArray()).put("stops",new JSONArray()).put("capture_quality",new JSONObject());
        File archive=new File(app.getFilesDir(),"journey_foot.json");Files.write(archive.toPath(),trip.toString().getBytes(StandardCharsets.UTF_8));
        app.getSharedPreferences("roadprints_ncn_internal_trial_v1",0).edit().putBoolean("enabled",true).commit();
        java.lang.reflect.Method assess=NcnProgressStore.class.getDeclaredMethod("assess",Context.class);assess.setAccessible(true);
        NcnProgressStore.Snapshot first=(NcnProgressStore.Snapshot)assess.invoke(null,app);assertTrue(first.ledger.metres("NCN:344","combined")>0);
        File directory=new File(app.getFilesDir(),"ncn-trial/"+first.catalogue.version);File cache=new File(directory,NcnJson.hash("foot".getBytes(StandardCharsets.UTF_8))+".json");byte[] checkpoint=Files.readAllBytes(cache.toPath());
        NcnProgressStore.Snapshot resumed=(NcnProgressStore.Snapshot)assess.invoke(null,app);assertArrayEquals(checkpoint,Files.readAllBytes(cache.toPath()));assertEquals(first.ledger.metres("NCN:344","combined"),resumed.ledger.metres("NCN:344","combined"),.001);
        assertTrue(archive.delete());NcnProgressStore.Snapshot deleted=(NcnProgressStore.Snapshot)assess.invoke(null,app);assertEquals(0,deleted.ledger.metres("NCN:344","combined"),0);assertFalse(cache.exists());
    }
}
