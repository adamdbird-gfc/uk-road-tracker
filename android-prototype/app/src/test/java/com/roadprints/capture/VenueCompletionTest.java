package com.roadprints.capture;
import android.content.Context;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import java.time.Instant;
import java.util.*;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class VenueCompletionTest {
    private Context app;private VenueCollection.Definition definition;private JSONObject root;private JSONArray venues;
    private VenueCatalogueUpdates swapped;private Object originalRoot,originalPins;
    @Before public void setup()throws Exception{
        app=RuntimeEnvironment.getApplication();app.getSharedPreferences("roadprints_achievements_v1",0).edit().clear().commit();definition=VenueCollection.definition("wetherspoons");
        root=new JSONObject(VenueCatalogueUpdates.forCollection(definition).root(app).toString());venues=new JSONArray();
        venues.put(venue("fixture-kent-a","KNT","Kent")).put(venue("fixture-kent-b","KNT","Kent")).put(venue("fixture-surrey","SUR","Surrey"));root.put("venues",venues);certificate();
    }
    private JSONObject venue(String id,String code,String name)throws Exception{return new JSONObject().put("id",id).put("name",id).put("town","Town").put("lat",51.4).put("lon",.3).put("status","active").put("operating_status","reviewed_current").put("visit_enabled",false).put("map_reviewed_at",java.time.LocalDate.now(java.time.ZoneOffset.UTC).toString()).put("county",new JSONObject().put("code",code).put("name",name).put("nation","England"));}
    private void certificate()throws Exception{
        JSONObject counts=new JSONObject();for(int i=0;i<venues.length();i++){String code=venues.getJSONObject(i).getJSONObject("county").getString("code");counts.put(code,counts.optInt(code)+1);}
        root.put("completion_target",venues.length());JSONObject r=root.getJSONObject("coverage_review");r.put("county_counts",counts).put("active_members",venues.length()).put("current_members",venues.length()).put("temporarily_closed_members",0).put("source_listings",venues.length()+r.getInt("outside_uk")+r.getInt("future_openings")+r.getInt("former_members"));
    }
    private JSONObject proof(String id,String at)throws Exception{return new JSONObject().put("venue_id",id).put("source","confirmed_live_checkin").put("visited_at",at);}
    private VenueAchievements.Snapshot calculate(JSONArray records){return VenueCompletion.calculate(app,definition,root,records,true);}
    @Test public void pubCrawlNeedsOnePubPerEligibleCountyAndRepeatsDoNotCount()throws Exception{
        JSONArray p=new JSONArray().put(proof("fixture-kent-a",Instant.now().toString())).put(proof("fixture-kent-a",Instant.now().toString()));
        assertEquals(50,calculate(p).values.get("spoons-pub-crawl"),0);assertEquals(50,calculate(p).values.get("spoons-county-knt"),0);
        p.put(proof("fixture-surrey",Instant.now().toString()));assertEquals(100,calculate(p).values.get("spoons-pub-crawl"),0);assertEquals(50,calculate(p).values.get("spoons-county-knt"),0);
    }
    @Test public void countyCompletionNeedsEveryActivePubAndEmptyCountiesNeverAward()throws Exception{
        JSONArray p=new JSONArray().put(proof("fixture-kent-a",Instant.now().toString())).put(proof("fixture-kent-b",Instant.now().toString()));
        assertEquals(100,calculate(p).values.get("spoons-county-knt"),0);assertEquals(0,calculate(p).values.get("spoons-county-brn"),0);
        assertEquals(100.0*2/3,calculate(p).values.get("spoons-uk-complete"),1e-8);
    }
    @Test public void earnedCompletionSurvivesNewPubButRemovingSupportingVisitRevokesIt()throws Exception{
        JSONArray p=new JSONArray().put(proof("fixture-kent-a",Instant.now().toString())).put(proof("fixture-kent-b",Instant.now().toString()));assertEquals(100,calculate(p).values.get("spoons-county-knt"),0);
        venues.put(venue("fixture-kent-c","KNT","Kent"));certificate();assertEquals(100,calculate(p).values.get("spoons-county-knt"),0);assertTrue(calculate(p).display.get("spoons-county-knt").contains("retained"));
        assertEquals(100.0/3,calculate(new JSONArray().put(p.getJSONObject(0))).values.get("spoons-county-knt"),1e-8);
    }
    @Test public void correctedCountyAttributionRevokesUnsupportedRetainedPubCrawl()throws Exception{
        JSONArray p=new JSONArray().put(proof("fixture-kent-a",Instant.now().toString()).put("county_code","KNT")).put(proof("fixture-surrey",Instant.now().toString()));
        assertEquals(100,calculate(p).values.get("spoons-pub-crawl"),0);
        venues.getJSONObject(0).put("county",new JSONObject().put("code","SUR").put("name","Surrey").put("nation","England"));certificate();
        assertEquals(50,calculate(p).values.get("spoons-pub-crawl"),0);
    }
    @Test public void partialCatalogueCannotAwardCompletionAndAccessDoesNotManufactureCredit()throws Exception{
        JSONArray p=new JSONArray();for(int i=0;i<venues.length();i++)p.put(proof(venues.getJSONObject(i).getString("id"),Instant.now().toString()));
        root.put("approval_scope","map_only_partial_beta").put("coverage_complete",false).put("completion_target",JSONObject.NULL);
        assertEquals(0,calculate(p).values.get("spoons-uk-complete"),0);assertEquals(0,calculate(p).values.get("spoons-pub-crawl"),0);
        assertEquals(0,VenueCompletion.calculate(app,definition,root,p,false).values.get("spoons-county-knt"),0);
    }
    private JSONObject closure()throws Exception{
        root.put("lifecycle_started_at",Instant.now().minusSeconds(864000).toString());
        return venue("fixture-closed","KNT","Kent").put("status","permanently_closed").put("known_active_from",Instant.now().minusSeconds(864000).toString()).put("permanently_closed_at",Instant.now().minusSeconds(86400).toString()).put("evidence_url","https://example.org/verified-closure").put("reviewed_at",java.time.LocalDate.now(java.time.ZoneOffset.UTC).toString());
    }
    @Test public void lastOrdersOnlyCountsSupportedVisitBeforeVerifiedFuturePermanentClosure()throws Exception{
        JSONObject c=closure();root.put("lifecycle",new JSONArray().put(c));
        JSONArray p=new JSONArray().put(proof("fixture-closed",Instant.now().minusSeconds(172800).toString()));assertEquals(1,calculate(p).values.get("spoons-last-orders"),0);
        assertEquals(0,calculate(new JSONArray().put(proof("fixture-closed",Instant.now().toString()))).values.get("spoons-last-orders"),0);
        assertEquals(0,calculate(new JSONArray().put(proof("fixture-closed",Instant.now().minusSeconds(172800).toString()).put("pending",true))).values.get("spoons-last-orders"),0);
        c.put("status","temporarily_closed");assertEquals(0,calculate(p).values.get("spoons-last-orders"),0);
        c.put("status","permanently_closed").put("permanently_closed_at",Instant.now().minusSeconds(1728000).toString());assertEquals(0,calculate(p).values.get("spoons-last-orders"),0);
    }
    @Test public void lastOrdersHistoryCountsCountyAndRemovingProofRevokesBadge()throws Exception{
        JSONObject c=closure();root.put("lifecycle",new JSONArray().put(c));JSONArray p=new JSONArray().put(proof("fixture-closed",Instant.now().minusSeconds(172800).toString()));
        assertEquals(50,calculate(p).values.get("spoons-pub-crawl"),0);assertEquals(1,calculate(p).values.get("spoons-last-orders"),0);assertEquals(0,calculate(new JSONArray()).values.get("spoons-last-orders"),0);
    }
    private void swapCatalogue()throws Exception{
        swapped=VenueCatalogueUpdates.forCollection(definition);java.lang.reflect.Field current=VenueCatalogueUpdates.class.getDeclaredField("current"),pins=VenueCatalogueUpdates.class.getDeclaredField("pins");current.setAccessible(true);pins.setAccessible(true);originalRoot=current.get(swapped);originalPins=pins.get(swapped);current.set(swapped,root);pins.set(swapped,CollectionMapCatalog.approvedVenues(root,definition));
        app.getSharedPreferences(CollectionAccessStore.PREFS,0).edit().putBoolean("wetherspoons_test_access",true).putBoolean("wetherspoons_installed",true).commit();
        app.getSharedPreferences(VenueVisitStore.PREFS,0).edit().putBoolean("wetherspoons:migrated",true).commit();
    }
    @After public void restore()throws Exception{
        if(swapped!=null){java.lang.reflect.Field current=VenueCatalogueUpdates.class.getDeclaredField("current"),pins=VenueCatalogueUpdates.class.getDeclaredField("pins");current.setAccessible(true);pins.setAccessible(true);current.set(swapped,originalRoot);pins.set(swapped,originalPins);}
        TimelineVisitStore.clear(app);
    }
    @Test public void archivedTimelineProofSurvivesClosureAndIsRevokedWhenSourceMoves()throws Exception{
        JSONObject c=closure().put("lat",51.5).put("lon",.4);root.put("lifecycle",new JSONArray().put(c));swapCatalogue();
        String at=Instant.now().minusSeconds(172800).toString();JSONObject visit=new JSONObject().put("id","closure-source").put("source","google_timeline_placeVisit").put("start",at).put("end",Instant.parse(at).plusSeconds(120).toString()).put("lat",51.5).put("lng",.4).put("place_name","fixture-closed");
        TimelineVisitStore.merge(app,new JSONArray().put(visit));JSONObject accepted=proof("fixture-closed",at).put("source","named_timeline_visit").put("source_visit_id","closure-source").put("county_code","KNT");
        app.getSharedPreferences(VenueVisitStore.PREFS,0).edit().putString("wetherspoons:records",new JSONObject().put("timeline:closure-source",accepted).toString()).commit();
        VenueVisitStore.reconcile(app,"wetherspoons",CollectionMapCatalog.spoons(app));assertTrue(VenueVisitStore.completed(app,"wetherspoons").contains("fixture-closed"));
        visit.put("lat",52.5);java.nio.file.Files.write(new java.io.File(app.getFilesDir(),"timeline-place-visits-v1.json").toPath(),new JSONObject().put("schema_version",1).put("visits",new JSONArray().put(visit)).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        VenueVisitStore.reconcile(app,"wetherspoons",CollectionMapCatalog.spoons(app));assertFalse(VenueVisitStore.completed(app,"wetherspoons").contains("fixture-closed"));
    }
    @Test public void archivedNativeProofSurvivesUnchangedJourneyAndRejectsMovingSamples()throws Exception{
        JSONObject c=closure().put("lat",51.5).put("lon",.4);root.put("lifecycle",new JSONArray().put(c));swapCatalogue();
        long at=Instant.now().minusSeconds(172800).toEpochMilli();JSONArray samples=new JSONArray();for(int i=0;i<5;i++)samples.put(new JSONArray().put(.4).put(51.5).put(5).put(at+i*60000).put(0));
        JSONObject arrival=new JSONObject().put("role","arrival").put("name","fixture-closed").put("classification","supported").put("source","map_footprint_and_dwell").put("dwell_seconds",240).put("latitude",51.5).put("longitude",.4).put("accuracy_m",5);
        JSONObject journey=new JSONObject().put("journey_id","closure-journey").put("source",new JSONObject().put("type","android_activity_capture")).put("raw_capture_samples",samples).put("stops",new JSONArray().put(arrival));
        String stop="fixture-closed:"+at,key="native:closure-journey:"+stop;JSONObject accepted=proof("fixture-closed",Instant.ofEpochMilli(at).toString()).put("source","native_stop").put("journey_id","closure-journey").put("stop_key",stop).put("county_code","KNT");
        app.getSharedPreferences(VenueVisitStore.PREFS,0).edit().putString("wetherspoons:records",new JSONObject().put(key,accepted).toString()).commit();
        VenueVisitStore.recordJourney(app,"wetherspoons",journey);assertTrue(VenueVisitStore.completed(app,"wetherspoons").contains("fixture-closed"));
        for(int i=0;i<samples.length();i++)samples.getJSONArray(i).put(4,20);VenueVisitStore.recordJourney(app,"wetherspoons",journey);assertFalse(VenueVisitStore.completed(app,"wetherspoons").contains("fixture-closed"));
    }
    @Test public void approvalRejectsWrongCountyDenominatorAndFutureOrActiveClosure()throws Exception{
        VenueCatalogueApproval.validate(root);root.getJSONObject("coverage_review").getJSONObject("county_counts").put("KNT",99);assertFalse(VenueCatalogueApproval.valid(root));certificate();
        JSONObject c=closure().put("id","fixture-kent-a");root.put("lifecycle",new JSONArray().put(c));assertFalse(VenueCatalogueApproval.valid(root));
        c.put("id","fixture-closed").put("permanently_closed_at",Instant.now().plusSeconds(86400).toString());assertFalse(VenueCatalogueApproval.valid(root));
    }
}
