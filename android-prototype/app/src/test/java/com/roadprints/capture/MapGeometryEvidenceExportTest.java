package com.roadprints.capture;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import java.util.Map;
import static org.junit.Assert.*;

/** Global, private-on-device visible Map export; no archive writes. */
public final class MapGeometryEvidenceExportTest {
    private static JSONArray json(String value) throws Exception {return new JSONArray(value);}
    private static final MapCorridorGeometry.Bounds LONDON =
            new MapCorridorGeometry.Bounds(-0.120,51.510,-0.110,51.515);
    private static final double[] CAMERA = {-0.115,51.512,16.0};

    @Test public void exportsOnlyTravelledVisibleSegmentsWithLayerIdentity()
            throws Exception {
        MapActivity.MapRoutes routes=new MapActivity.MapRoutes();
        routes.sections.add(json("[[-0.119,51.512],[-0.111,51.512]]"));
        routes.ferrySections.add(json("[[-0.119,51.513],[-0.111,51.513]]"));
        routes.incompleteMotorwaySections.add(json("[[-0.119,51.514],[-0.111,51.514]]"));
        routes.addLocalRoad(new LocalRoadCoverage.Section("local","sample",100000,
                json("[-0.119,51.511]"),json("[-0.111,51.511]"),
                Map.of("private-id",new int[]{0,100000})));
        JSONObject result=MapGeometryEvidenceExport.capture(
                routes,LONDON,CAMERA,313,"all");
        assertEquals("roadprints_map_geometry_evidence",result.getString("format"));
        assertEquals(3,result.getInt("display_sections"));
        assertEquals(6,result.getInt("display_points"));
        assertEquals("all",result.getString("filter"));
        String content=result.toString();
        assertFalse(content.contains("private-id"));
        assertFalse(content.contains("incomplete_motorways"));
        assertTrue(content.contains("matched_corridors"));
        assertTrue(content.contains("accepted_local_roads"));
        assertTrue(content.contains("ferries"));
        assertEquals(-0.120,result.getJSONObject("window").getDouble("west"),0.00001);
    }

    @Test public void noCoverageIsValidAndExplicit() throws Exception {
        JSONObject result=MapGeometryEvidenceExport.capture(
                new MapActivity.MapRoutes(),LONDON,CAMERA,313,"all");
        assertEquals(0,result.getInt("display_sections"));
        assertEquals(0,result.getJSONArray("segments").length());
    }

    @Test public void refusesCrowdedMapWithoutTruncating() throws Exception {
        MapActivity.MapRoutes routes=new MapActivity.MapRoutes();
        for(int i=0;i<513;i++)
            routes.sections.add(json("[[-0.119,51.512],[-0.111,51.512]]"));
        try {
            MapGeometryEvidenceExport.capture(routes,LONDON,CAMERA,313,"all");
            fail("Cannot misrepresent truncated view as complete");
        } catch(IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("zoom in"));
        }
    }

    @Test public void wrapsRealCameraBoundsAtInternationalDateLine() throws Exception {
        var bounds=MapCorridorGeometry.visibleWindow(179.999,-17.8,16,1080,1920,256*2.5);
        assertTrue(bounds.west()>bounds.east());
        var parts=MapCorridorGeometry.clip(json("[[179.90,-17.8],[-179.90,-17.8]]"),bounds);
        assertFalse(parts.isEmpty());
    }

    @Test public void rejectsOversizedWorldViewRatherThanApproximating() {
        try {
            MapCorridorGeometry.visibleWindow(0,0,2,8000,6000,256);
            fail("An all-world evidence export is not a bounded view");
        } catch(IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("wide"));
        }
    }

    @Test public void preservesCoverageIncompleteFlags() throws Exception {
        MapActivity.MapRoutes routes=new MapActivity.MapRoutes();
        routes.internationalCoverageIncomplete=true;
        routes.localCoverageResourceDegraded=true;
        JSONObject result=MapGeometryEvidenceExport.capture(
                routes,LONDON,CAMERA,313,"all");
        assertTrue(result.getBoolean("international_coverage_incomplete"));
        assertTrue(result.getBoolean("local_coverage_resource_degraded"));
    }
}
