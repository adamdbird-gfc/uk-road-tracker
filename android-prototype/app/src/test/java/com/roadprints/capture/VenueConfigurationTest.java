package com.roadprints.capture;
import android.content.Context;
import android.util.AtomicFile;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class VenueConfigurationTest {
    private Context app;
    @Before public void reset(){app=RuntimeEnvironment.getApplication();for(VenueCollection.Definition d:VenueCollection.all()){new AtomicFile(new File(app.getFilesDir(),d.id+"-map-cache-v1.json")).delete();VenueCatalogueUpdates.forCollection(d).resetForTests();}}
    @After public void clean(){reset();}
    @Test public void configuredGoalsKeepUniqueStableIdsAndAllExistingEligibility()throws Exception{
        Set<String> ids=new HashSet<>();int services=0,pubs=0;
        for(AchievementStore.Definition d:AchievementStore.definitions()){
            assertTrue("Duplicate achievement "+d.id,ids.add(d.id));
            if("services".equals(d.collectionId))services++;
            if("wetherspoons".equals(d.collectionId))pubs++;
        }
        assertEquals(48,services);assertEquals(21,pubs);
        JSONArray catalogue=ServiceStationStore.stations(app);Set<String> all=new HashSet<>();for(int i=0;i<catalogue.length();i++)all.add(catalogue.getJSONObject(i).getString("id"));
        VenueAchievements.Snapshot goals=VenueAchievements.calculate(VenueCollection.definition("services"),catalogue,all,true,Collections.emptyMap());
        for(int i=0;i<VenueCollection.definition("services").goals.length();i++){JSONObject g=VenueCollection.definition("services").goals.getJSONObject(i);assertEquals(g.getString("id"),g.getDouble("target"),goals.values.get(g.getString("id")),0);}
    }
    @Test public void newRestaurantConfigurationUsesExistingGoalAndCatalogueProjection()throws Exception{
        JSONObject row=new JSONObject(VenueCollectionConfig.read().getJSONArray("collections").getJSONObject(1).toString());
        row.put("id","fixture").put("name","Fixture restaurants").put("singular","restaurant").put("plural","restaurants").put("identity_pattern","fixture-[a-z]+").put("achievement_type","venue-visit").put("pin_label","F");
        row.put("achievements",new JSONArray().put(new JSONObject().put("id","fixture-first").put("title","First restaurant").put("description","Visit a restaurant.").put("detail","Visit a restaurant.").put("measure","distinct_history").put("target",1)));
        VenueCollection.Definition definition=new VenueCollection.Definition(row);
        JSONObject root=new JSONObject().put("collection_id","fixture").put("scope","UK").put("ready_for_app",true).put("release_status","approved").put("approval_scope","map_only_partial_beta").put("visit_enabled",false).put("completion_target",JSONObject.NULL);
        root.put("venues",new JSONArray().put(new JSONObject().put("id","fixture-one").put("name","Fixture restaurant").put("town","Town").put("status","active").put("operating_status","reviewed_current").put("visit_enabled",false).put("map_reviewed_at","2026-10-13").put("lat",51.4).put("lon",.3)));
        JSONArray pins=CollectionMapCatalog.approvedVenues(root,definition);assertEquals(1,pins.length());
        VenueAchievements.Snapshot goals=VenueAchievements.calculate(definition,pins,Collections.singleton("fixture-one"),true,Collections.emptyMap());assertEquals(1,goals.values.get("fixture-first"),0);
        List<AchievementStore.Definition> cards=new ArrayList<>();VenueAchievements.definitions(definition,cards);assertEquals("fixture",cards.get(0).collectionId);assertEquals("F",definition.pinLabel);
    }
    private JSONObject manifest(VenueCollection.Definition d,JSONObject root,byte[] bytes,long revision)throws Exception{return new JSONObject().put("schema_version",1).put("collection_id",d.id).put("revision",revision).put("filename","catalogue-"+revision+".json").put("bytes",bytes.length).put("sha256",VenueCatalogueUpdates.sha256(bytes)).put("catalogue_version",root.getString("catalogue_version"));}
    @Test public void serviceUpdateIsIsolatedFromPubCacheAndNeverAddsVisitEvidence()throws Exception{
        VenueCollection.Definition service=VenueCollection.definition("services");VenueCatalogueUpdates store=VenueCatalogueUpdates.forCollection(service);
        int pubs=CollectionMapCatalog.spoons(app).length();long pubRevision=CollectionCatalogueUpdates.revision();
        JSONObject root=store.bundled(app).put("schema_version",1).put("collection_id","services").put("scope","UK").put("release_status","approved").put("attribution","© OpenStreetMap contributors · ODbL 1.0").put("catalogue_version","fixture-update");
        String id=root.getJSONArray("services").getJSONObject(0).getString("id");root.getJSONArray("services").getJSONObject(0).put("name","Renamed service area");
        byte[] payload=root.toString().getBytes(StandardCharsets.UTF_8);JSONObject m=manifest(service,root,payload,service.bundledRevision+1);
        assertTrue(store.refreshNow(app,(name,limit)->name.equals("manifest-v1.json")?m.toString().getBytes(StandardCharsets.UTF_8):payload));
        assertEquals(id,ServiceStationStore.stations(app).getJSONObject(0).getString("id"));assertEquals("Renamed service area",ServiceStationStore.stations(app).getJSONObject(0).getString("name"));
        store.resetForTests();assertEquals("Renamed service area",ServiceStationStore.stations(app).getJSONObject(0).getString("name"));
        assertEquals(pubs,CollectionMapCatalog.spoons(app).length());assertEquals(pubRevision,CollectionCatalogueUpdates.revision());assertTrue(VenueVisitStore.completed(app,"services").isEmpty());
        m.put("collection_id","wetherspoons");try{store.validateManifest(m);fail("Wrong collection must fail");}catch(IllegalArgumentException expected){}
    }
    @Test public void reopeningReviewsRetainIdentityWhileClosedAndFuturePubsStayHeld()throws Exception{
        JSONArray pubs=CollectionMapCatalog.spoons(app);
        assertNotNull(PubVisitStore.find(pubs,"spoons-osm-w1465394526"));assertNotNull(PubVisitStore.find(pubs,"spoons-osm-w98286602"));
        assertNull(PubVisitStore.find(pubs,"spoons-osm-w507289751"));assertNull(PubVisitStore.find(pubs,"spoons-osm-w38325241"));
    }
}
