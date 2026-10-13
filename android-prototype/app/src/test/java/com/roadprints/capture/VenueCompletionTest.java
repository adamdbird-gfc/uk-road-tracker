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
    @Before public void setup()throws Exception{
        app=RuntimeEnvironment.getApplication();app.getSharedPreferences("roadprints_achievements_v1",0).edit().clear().commit();definition=VenueCollection.definition("wetherspoons");
        root=new JSONObject(VenueCatalogueUpdates.forCollection(definition).root(app).toString());venues=new JSONArray();
        venues.put(venue("fixture-kent-a","KNT","Kent")).put(venue("fixture-kent-b","KNT","Kent")).put(venue("fixture-surrey","SUR","Surrey"));root.put("venues",venues);certificate();
    }
    private JSONObject venue(String id,String code,String name)throws Exception{return new JSONObject().put("id",id).put("name",id).put("town","Town").put("lat",51.4).put("lon",.3).put("county",new JSONObject().put("code",code).put("name",name).put("nation","England"));}
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
    @Test public void approvalRejectsWrongCountyDenominatorAndFutureOrActiveClosure()throws Exception{
        VenueCatalogueApproval.validate(root);root.getJSONObject("coverage_review").getJSONObject("county_counts").put("KNT",99);assertFalse(VenueCatalogueApproval.valid(root));certificate();
        JSONObject c=closure().put("id","fixture-kent-a");root.put("lifecycle",new JSONArray().put(c));assertFalse(VenueCatalogueApproval.valid(root));
        c.put("id","fixture-closed").put("permanently_closed_at",Instant.now().plusSeconds(86400).toString());assertFalse(VenueCatalogueApproval.valid(root));
    }
}
