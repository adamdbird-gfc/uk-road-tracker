package com.roadprints.capture;

import android.content.Context;
import android.content.Intent;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import java.util.List;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class RoadDetailTest {
    @Test public void allSupportedRoadKindsOpenAndCloseTheirProgressChild() {
        Context app=RuntimeEnvironment.getApplication();
        for(String kind:new String[]{RoadDetailSelection.MOTORWAY,RoadDetailSelection.A_ROAD,
                RoadDetailSelection.B_ROAD,RoadDetailSelection.LOCAL}) {
            RoadDetailSelection road=new RoadDetailSelection(kind,"Example","GB","town-1","Town");
            org.robolectric.android.controller.ActivityController<RoadDetailActivity> controller=
                    org.robolectric.Robolectric.buildActivity(RoadDetailActivity.class,road.intent(app,true)).create();
            RoadDetailActivity activity=controller.get();
            assertFalse(activity.isFinishing());
            assertEquals(kind,RoadDetailSelection.from(activity.getIntent()).kind);
            assertTrue(activity.getIntent().getBooleanExtra("road_journeys",false));
            android.view.ViewGroup root=(android.view.ViewGroup)((android.view.ViewGroup)
                    activity.findViewById(android.R.id.content)).getChildAt(0);
            android.view.ViewGroup header=(android.view.ViewGroup)root.getChildAt(0);
            assertEquals("CLOSE",((android.widget.TextView)header.getChildAt(1)).getText().toString());
            header.getChildAt(1).performClick();assertTrue(activity.isFinishing());controller.destroy();
        }
    }
    private JSONObject line(double a,double b) throws Exception {
        return new JSONObject().put("type","LineString").put("coordinates",new JSONArray()
                .put(new JSONArray().put(a).put(0)).put(new JSONArray().put(b).put(0)));
    }
    private JSONObject polygon(double left,double right) throws Exception {
        return new JSONObject().put("type","Polygon").put("coordinates",new JSONArray().put(new JSONArray()
                .put(new JSONArray().put(left).put(-1)).put(new JSONArray().put(right).put(-1))
                .put(new JSONArray().put(right).put(1)).put(new JSONArray().put(left).put(1))
                .put(new JSONArray().put(left).put(-1))));
    }
    private JSONObject feature(String ref,String name,String region) throws Exception {
        return new JSONObject().put("type","Feature").put("geometry",line(-2,2)).put("properties",new JSONObject()
                .put("road_ref",ref).put("name",name).put("road_region",region).put("highway","residential"));
    }
    private JSONObject journey(JSONObject feature,String mode) throws Exception {
        JSONObject collection=new JSONObject().put("features",new JSONArray().put(feature));
        return new JSONObject().put("journey_id","one").put("title","One journey").put("started_at","2026-10-18T07:00:00Z")
                .put("mode",mode).put("processing_status","complete").put("processing_result",new JSONObject().put("road_geojson",collection));
    }
    @Test public void navigationCarriesOnlyRoadIdentityAndRoundTrips() {
        Context app=RuntimeEnvironment.getApplication();
        RoadDetailSelection road=new RoadDetailSelection(RoadDetailSelection.LOCAL,"High Street","","town-1","My town");
        Intent intent=road.intent(app,false);RoadDetailSelection restored=RoadDetailSelection.from(intent);
        assertEquals("town-1",restored.settlementCode);assertEquals("High Street · My town",restored.title());
        for(String key:intent.getExtras().keySet())assertFalse(intent.getExtras().get(key) instanceof JSONArray);
        assertFalse(intent.hasExtra("journey_ids"));
    }
    @Test public void localRoadWithoutSettlementIsRejected() {
        try {new RoadDetailSelection(RoadDetailSelection.LOCAL,"High Street","","","");fail();}
        catch(IllegalArgumentException expected) { }
    }
    @Test public void referencesSplitAliasesAndRegionsRemainSeparate() throws Exception {
        RoadDetailSelection m6=new RoadDetailSelection(RoadDetailSelection.MOTORWAY,"M6 Toll","GB","","");
        assertTrue(m6.matches(feature("M6T; A5","","GB")));
        assertFalse(m6.matches(feature("M6T","","NI")));
        RoadDetailSelection a1=new RoadDetailSelection(RoadDetailSelection.A_ROAD,"A1","GB","","");
        assertFalse(a1.matches(feature("A10","","GB")));
    }
    @Test public void localNamesDoNotSelectNumberedRoadsOrServicePaths() throws Exception {
        RoadDetailSelection road=new RoadDetailSelection(RoadDetailSelection.LOCAL,"High Street","","town","");
        assertTrue(road.matches(feature("","High Street","GB")));
        assertFalse(road.matches(feature("B123","High Street","GB")));
        JSONObject path=feature("","High Street","GB");path.getJSONObject("properties").put("highway","path");
        assertFalse(road.matches(path));
    }
    @Test public void crossingsAreClippedAtTownBoundaryAndOtherTownIsExcluded() throws Exception {
        List<JSONArray> inside=RoadDetailGeometry.lines(line(-2,2),polygon(-1,1));
        assertEquals(1,inside.size());assertEquals(-1,inside.get(0).getJSONArray(0).getDouble(0),1e-9);
        assertEquals(1,inside.get(0).getJSONArray(1).getDouble(0),1e-9);
        assertTrue(RoadDetailGeometry.lines(line(2,3),polygon(-1,1)).isEmpty());
    }
    @Test public void polygonHolesDoNotCreateAConnectingLine() throws Exception {
        JSONObject boundary=polygon(-2,2);
        boundary.getJSONArray("coordinates").put(polygon(-.5,.5).getJSONArray("coordinates").getJSONArray(0));
        List<JSONArray> sections=RoadDetailGeometry.lines(line(-3,3),boundary);
        assertEquals(2,sections.size());
        assertEquals(-.5,sections.get(0).getJSONArray(1).getDouble(0),1e-9);
        assertEquals(.5,sections.get(1).getJSONArray(0).getDouble(0),1e-9);
    }
    @Test public void correctionsAndUnfinishedJourneysCannotContribute() throws Exception {
        RoadDetailSelection road=new RoadDetailSelection(RoadDetailSelection.B_ROAD,"B123","GB","","");
        JSONObject trip=journey(feature("B123","","GB"),"driving");
        assertFalse(RoadDetailModel.collect(trip,road,null,false).lines.isEmpty());
        trip.put("processing_status","pending");assertTrue(RoadDetailModel.collect(trip,road,null,false).lines.isEmpty());
        trip.put("processing_status","complete").put("journey_corrections",new JSONObject()
                .put("removed_road_ids",new JSONArray().put("ref:B123")));
        assertTrue(RoadDetailModel.collect(trip,road,null,false).lines.isEmpty());
    }
    @Test public void cumulativeJourneysRequireTheSameCanonicalEvidenceAndMode() throws Exception {
        RoadDetailSelection road=new RoadDetailSelection(RoadDetailSelection.A_ROAD,"A1","GB","","");
        JSONObject trip=journey(feature("A1","","GB"),"driving");
        assertFalse(RoadDetailModel.collect(trip,road,null,true).contributes);
        JSONObject result=trip.getJSONObject("processing_result");result.put("a_road_geojson",result.getJSONObject("road_geojson"));
        assertTrue(RoadDetailModel.collect(trip,road,null,true).contributes);
        trip.getJSONObject("processing_result").getJSONObject("a_road_geojson").getJSONArray("features").getJSONObject(0).remove("geometry");
        assertTrue(RoadDetailModel.collect(trip,road,null,true).contributes);
        trip.getJSONObject("processing_result").getJSONObject("a_road_geojson").getJSONArray("features").getJSONObject(0).put("geometry",line(-2,2));
        trip.put("mode","walking");assertFalse(RoadDetailModel.collect(trip,road,null,true).contributes);
        assertFalse(RoadDetailModel.collect(trip,road,null,false).lines.isEmpty());
    }
    @Test public void duplicateFeatureCollectionsProduceOneSetOfSegments() throws Exception {
        RoadDetailSelection road=new RoadDetailSelection(RoadDetailSelection.A_ROAD,"A1","GB","","");
        JSONObject trip=journey(feature("A1","","GB"),"driving");JSONObject result=trip.getJSONObject("processing_result");
        result.put("a_road_geojson",result.getJSONObject("road_geojson"));
        assertEquals(1,RoadDetailModel.collect(trip,road,null,false).lines.size());
    }
    @Test public void sameNamedRoadInDifferentTownsCannotLeakJourneys() throws Exception {
        RoadDetailSelection road=new RoadDetailSelection(RoadDetailSelection.LOCAL,"High Street","","town","");
        JSONObject trip=journey(feature("","High Street","GB"),"walking");
        assertTrue(RoadDetailModel.collect(trip,road,polygon(5,6),false).lines.isEmpty());
        assertFalse(RoadDetailModel.collect(trip,road,polygon(-1,1),false).lines.isEmpty());
    }
}
