package com.roadprints.capture;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Robolectric;
import android.view.View;
import org.robolectric.annotation.Config;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class CollectionAccessAndMapTest {
    private Context app;
    @Before public void reset() {
        app=RuntimeEnvironment.getApplication();
        app.getSharedPreferences(CollectionAccessStore.PREFS,0).edit().clear().commit();
        app.getSharedPreferences("roadprints_collections_v1",0).edit().clear().commit();
    }
    private void servicesOwner() {
        app.getSharedPreferences("roadprints_collections_v1",0).edit().putBoolean("service_stations_unlocked",true).commit();
    }
    @Test public void servicesUpgradePreservesInstallationAndSingleCollectionHasNoChoice() {
        servicesOwner();assertTrue(CollectionAccessStore.installed(app,CollectionAccessStore.SERVICES));
        assertEquals(1,CollectionAccessStore.installed(app).size());
        assertEquals(CollectionAccessStore.SERVICES,CollectionAccessStore.selected(app));
    }
    @Test public void testInstallGrantsAccessButNoVisits() {
        CollectionAccessStore.installForTesting(app,CollectionAccessStore.SPOONS);
        assertTrue(CollectionAccessStore.entitled(app,CollectionAccessStore.SPOONS));
        assertTrue(CollectionAccessStore.installed(app,CollectionAccessStore.SPOONS));
        assertTrue(app.getSharedPreferences("roadprints_collections_v1",0).getAll().isEmpty());
        assertEquals(37,CollectionMapCatalog.spoons(app).length());
        assertEquals("UK Wetherspoons Collection",CollectionAccessStore.name(CollectionAccessStore.SPOONS));
    }
    @Test public void selectionPersistsAndUninstalledCollectionCannotBeSelected() {
        servicesOwner();CollectionAccessStore.installForTesting(app,CollectionAccessStore.SPOONS);
        assertEquals(2,CollectionAccessStore.installed(app).size());
        assertTrue(CollectionAccessStore.select(app,CollectionAccessStore.SPOONS));
        assertEquals(CollectionAccessStore.SPOONS,CollectionAccessStore.selected(app.getApplicationContext()));
        assertTrue(CollectionAccessStore.select(app,CollectionAccessStore.NONE));
        assertEquals(CollectionAccessStore.NONE,CollectionAccessStore.selected(app));
        CollectionAccessStore.remove(app,CollectionAccessStore.SPOONS);
        assertFalse(CollectionAccessStore.select(app,CollectionAccessStore.SPOONS));
        assertEquals(CollectionAccessStore.SERVICES,CollectionAccessStore.selected(app));
    }
    @Test public void removeAndReinstallPreserveServicesOwnershipAndEvidence() {
        servicesOwner();app.getSharedPreferences("roadprints_collections_v1",0).edit().putString("evidence","keep").commit();
        CollectionAccessStore.remove(app,CollectionAccessStore.SERVICES);
        assertEquals(CollectionAccessStore.NONE,CollectionAccessStore.selected(app));
        assertTrue(ServiceStationStore.unlocked(app));
        CollectionAccessStore.installForTesting(app,CollectionAccessStore.SERVICES);
        assertEquals("keep",app.getSharedPreferences("roadprints_collections_v1",0).getString("evidence",""));
        assertEquals(CollectionAccessStore.SERVICES,CollectionAccessStore.selected(app));
    }
    @Test public void auditCatalogueAndUnapprovedStatusesNeverProducePubPins() throws Exception {
        JSONObject venue=new JSONObject().put("id","spoons-example").put("status","active").put("name","Example").put("lat",51).put("lon",0).put("operating_status","reviewed_current")
                .put("visit_enabled",false).put("map_reviewed_at","2026-10-11");
        JSONObject root=new JSONObject().put("collection_id","wetherspoons").put("scope","UK")
                .put("ready_for_app",false).put("release_status","audit_only").put("venues",new JSONArray().put(venue))
                .put("approval_scope","map_only_partial_beta").put("visit_enabled",false).put("completion_target",JSONObject.NULL);
        assertEquals(0,CollectionMapCatalog.approvedVenues(root).length());
        root.put("ready_for_app",true);assertEquals(0,CollectionMapCatalog.approvedVenues(root).length());
        root.put("release_status","approved");assertEquals(1,CollectionMapCatalog.approvedVenues(root).length());
        venue.put("status","candidate");assertEquals(0,CollectionMapCatalog.approvedVenues(root).length());
    }
    @Test public void packagedMapContainsOnlyReviewedVenuesAndExcludesFormerPub() throws Exception {
        JSONArray pins=CollectionMapCatalog.spoons(app);
        java.util.Set<String> actual=new java.util.HashSet<>();
        for(int i=0;i<pins.length();i++)actual.add(pins.getJSONObject(i).getString("id"));
        assertEquals(37,actual.size());
        assertTrue(actual.containsAll(java.util.Arrays.asList("spoons-osm-n528965858","spoons-osm-w310891787",
                "spoons-osm-w103691610","spoons-osm-w30110330")));
        assertFalse(actual.contains("spoons-osm-w262652847"));
        assertFalse(actual.contains("spoons-osm-w965765986"));
        assertTrue(app.getSharedPreferences("roadprints_collections_v1",0).getAll().isEmpty());
    }
    @Test public void unreviewedRowsAndVisitEnabledCataloguesCannotProducePins() throws Exception {
        JSONObject row=new JSONObject().put("id","spoons-a").put("status","active").put("lat",51).put("lon",0);
        JSONObject root=new JSONObject().put("collection_id","wetherspoons").put("scope","UK")
                .put("ready_for_app",true).put("release_status","approved").put("approval_scope","map_only_partial_beta")
                .put("visit_enabled",false).put("completion_target",JSONObject.NULL).put("venues",new JSONArray().put(row));
        assertEquals(0,CollectionMapCatalog.approvedVenues(root).length());
        row.put("operating_status","reviewed_current").put("visit_enabled",false).put("map_reviewed_at","2026-10-11");
        assertEquals(1,CollectionMapCatalog.approvedVenues(root).length());
        root.put("visit_enabled",true);assertEquals(0,CollectionMapCatalog.approvedVenues(root).length());
        root.put("visit_enabled",false).put("completion_target",827);assertEquals(0,CollectionMapCatalog.approvedVenues(root).length());
    }

    private JSONObject pin(String id,double lng,double lat) throws Exception {
        return new JSONObject().put("id",id).put("name",id).put("lng",lng).put("lat",lat);
    }
    private List<CollectionMapClusters.Cluster> clusters(JSONArray pins) {
        return CollectionMapClusters.build(pins,Collections.singleton("a"),new CollectionMapClusters.Projection(){
            public float x(double lng){return (float)(lng*100);}
            public float y(double lat){return (float)(lat*100);}
        },500,500,54);
    }
    @Test public void clusterHitUsesRenderedCentreAndRetainsAllChoices() throws Exception {
        List<CollectionMapClusters.Cluster> result=clusters(new JSONArray().put(pin("a",1,1)).put(pin("b",1.02,1.02)).put(pin("c",3,3)));
        assertEquals(2,result.size());
        CollectionMapClusters.Cluster hit=CollectionMapClusters.hit(result,101,101,10);
        assertNotNull(hit);assertEquals(2,hit.venues.size());assertEquals(1,hit.visited);
        assertNull(CollectionMapClusters.hit(result,450,450,10));
    }
    @Test public void replacementOverlayDoesNotRetainOtherCollectionOrOffscreenPins() throws Exception {
        assertEquals(1,clusters(new JSONArray().put(pin("services",1,1))).size());
        List<CollectionMapClusters.Cluster> next=clusters(new JSONArray().put(pin("pub",2,2)).put(pin("offscreen",50,50)));
        assertEquals(1,next.size());assertEquals("pub",next.get(0).venues.get(0).getString("id"));
        assertTrue(clusters(new JSONArray()).isEmpty());
    }
    @Test public void coincidentVenuesStayAvailableAsSeparateChoices() throws Exception {
        List<CollectionMapClusters.Cluster> result=clusters(new JSONArray().put(pin("a",1,1)).put(pin("b",1,1)));
        assertEquals(1,result.size());assertEquals(2,result.get(0).venues.size());
    }
    @Test public void mapSelectorIsOnlyVisibleWithMultipleInstallationsAndSwapKeepsCamera() throws Exception {
        servicesOwner();
        org.robolectric.android.controller.ActivityController<MapActivity> controller=Robolectric.buildActivity(MapActivity.class).create();
        MapActivity activity=controller.get();
        java.lang.reflect.Field selectorField=MapActivity.class.getDeclaredField("collectionSelector");selectorField.setAccessible(true);
        java.lang.reflect.Field mapField=MapActivity.class.getDeclaredField("mapView");mapField.setAccessible(true);
        java.lang.reflect.Method apply=MapActivity.class.getDeclaredMethod("applyCollectionLayer");apply.setAccessible(true);
        View selector=(View)selectorField.get(activity);RoutePreviewView map=(RoutePreviewView)mapField.get(activity);
        map.restoreCameraState(.36,51.44,14);apply.invoke(activity);assertEquals(View.GONE,selector.getVisibility());
        CollectionAccessStore.installForTesting(app,CollectionAccessStore.SPOONS);
        CollectionAccessStore.select(app,CollectionAccessStore.SPOONS);apply.invoke(activity);
        assertEquals(View.VISIBLE,selector.getVisibility());assertArrayEquals(new double[]{.36,51.44,14},map.cameraState(),1e-9);
        CollectionAccessStore.select(app,CollectionAccessStore.NONE);apply.invoke(activity);
        assertArrayEquals(new double[]{.36,51.44,14},map.cameraState(),1e-9);
        CollectionAccessStore.remove(app,CollectionAccessStore.SPOONS);apply.invoke(activity);
        assertEquals(View.GONE,selector.getVisibility());controller.destroy();
    }
}
