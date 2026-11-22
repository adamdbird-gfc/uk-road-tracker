package com.roadprints.capture;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.*;

/** Bounded real-geometry Map corridor comparison, no production renderer change. */
public final class MapCorridorGeometryTest {
    private static JSONArray line(String p) throws Exception { return new JSONArray(p); }
    private static final MapCorridorGeometry.Bounds WINDOW =
            new MapCorridorGeometry.Bounds(-0.120,51.510,-0.110,51.515);

    @Test public void antimeridianWindowIncludesShortPacificCrossingNotEntireWorld()
            throws Exception {
        JSONArray crossing=line("[[179.8,-17.8],[-179.8,-17.8]]");
        var window=MapCorridorGeometry.around(List.of(crossing), 100);
        assertTrue("Dateline window wraps west>east", window.west()>window.east());
        List<JSONArray> visible=MapCorridorGeometry.clip(crossing, window);
        assertEquals(1,visible.size());
        assertEquals(2,visible.get(0).length());
        assertEquals(179.8,visible.get(0).getJSONArray(0).getDouble(0),1e-6);
        assertEquals(-179.8,visible.get(0).getJSONArray(1).getDouble(0),1e-6);
        assertTrue("London cannot intersect a Fiji antimeridian window",
                MapCorridorGeometry.clip(line("[[-0.12,51.51],[-0.11,51.51]]"),
                        window).isEmpty());
        assertEquals(0, CumulativeMapShapeAudit.acceptedToCumulativeDeviationMetres(
                crossing,visible),0.001);
    }

    @Test public void antimeridianBoundsClipSegmentWithBothEndpointsOutside()
            throws Exception {
        var pacific=new MapCorridorGeometry.Bounds(179.85,-18.0,-179.85,-17.0);
        var pieces=MapCorridorGeometry.clip(
                line("[[179.7,-17.8],[-179.7,-17.8]]"), pacific);
        assertEquals(1,pieces.size());
        assertEquals(179.85,pieces.get(0).getJSONArray(0).getDouble(0),1e-6);
        assertEquals(-179.85,pieces.get(0).getJSONArray(1).getDouble(0),1e-6);
    }

    @Test public void southernHemisphereHighLatitudeAndEquatorialWindows()
            throws Exception {
        JSONArray southern=line("[[166.9,-45.1],[167.0,-45.15]]");
        JSONArray equator=line("[[32.0,0.01],[32.02,0.01]]");
        for(JSONArray route:List.of(southern,equator)) {
            var bounds=MapCorridorGeometry.around(List.of(route),100);
            var pieces=MapCorridorGeometry.clip(route,bounds);
            assertEquals(1,pieces.size());
            assertEquals(0,CumulativeMapShapeAudit.acceptedToCumulativeDeviationMetres(
                    route,pieces),0.001);
        }
    }

    @Test public void globalSpanningSourceRequiresRegionalWindows()
            throws Exception {
        try {
            MapCorridorGeometry.around(List.of(
                    line("[[-100,0],[20,0],[140,0]]")),100);
            fail("A worldwide archive cannot be silently treated as one local viewport");
        } catch(IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("Global-spanning"));
        }
    }

    @Test public void segmentCrossingCorridorWithBothEndpointsOutsideIsIncluded() throws Exception {
        List<JSONArray> parts=MapCorridorGeometry.clip(
                line("[[-0.13,51.512],[-0.10,51.512]]"),WINDOW);
        assertEquals(1,parts.size());
        assertEquals(-0.120,parts.get(0).getJSONArray(0).getDouble(0),1e-9);
        assertEquals(-0.110,parts.get(0).getJSONArray(1).getDouble(0),1e-9);
    }

    @Test public void disconnectedIntersectionsRemainTwoLines() throws Exception {
        JSONArray backAndForth=line("[[-0.121,51.512],[-0.115,51.512],"
                + "[-0.121,51.512],[-0.121,51.514],[-0.115,51.514]]");
        List<JSONArray> parts=MapCorridorGeometry.clip(backAndForth,WINDOW);
        assertEquals(2,parts.size());
        assertEquals(3,parts.get(0).length());
        assertEquals(2,parts.get(1).length());
    }

    @Test public void actualPreparedMapCorridorCanRepresentAcceptedSplitStreet()
            throws Exception {
        JSONArray accepted=line("[[-0.119,51.512],[-0.115,51.5122],[-0.111,51.512]]");
        MapActivity.MapRoutes prepared=new MapActivity.MapRoutes();
        prepared.addLocalRoad(new LocalRoadCoverage.Section("west","street-west",100000,
                line("[-0.119,51.512]"),line("[-0.115,51.5122]"),
                Map.of("source-one",new int[]{0,100000})));
        prepared.addLocalRoad(new LocalRoadCoverage.Section("east","street-east",100000,
                line("[-0.115,51.5122]"),line("[-0.111,51.512]"),
                Map.of("source-one",new int[]{0,100000})));
        prepared.sections.add(line("[[-0.119,51.514],[-0.111,51.514]]")); // other legitimate journey
        var report=CumulativeMapSnapshotComparison.comparePreparedCorridor(
                "fleet-evidence",List.of(accepted),prepared,WINDOW);
        assertEquals(0,report.maxUnrepresentedMetres(),0.001);
        assertEquals(3,report.displayedPieces());
        assertEquals("unassessed_other_journeys_may_contribute",
                report.json().getString("extra_lines_attribution"));
    }

    @Test public void actualSerializedMapCorridorEqualsPreparedOutput() throws Exception {
        JSONArray accepted=line("[[-0.119,51.512],[-0.115,51.5122],[-0.111,51.512]]");
        LocalRoadCoverage.Section west=new LocalRoadCoverage.Section("west","street-west",100000,
                line("[-0.119,51.512]"),line("[-0.115,51.5122]"),
                Map.of("source-one",new int[]{0,100000}));
        LocalRoadCoverage.Section east=new LocalRoadCoverage.Section("east","street-east",100000,
                line("[-0.115,51.5122]"),line("[-0.111,51.512]"),
                Map.of("source-one",new int[]{0,100000}));
        MapActivity.MapRoutes prepared=new MapActivity.MapRoutes();
        prepared.addLocalRoad(west);prepared.addLocalRoad(east);
        JSONObject persisted=new JSONObject()
                .put("sections",new JSONArray())
                .put("coveredMotorways",new JSONArray())
                .put("coveredARoads",new JSONArray())
                .put("ferries",new JSONArray())
                .put("localRoads",new JSONArray().put(west.encode()).put(east.encode()));
        var live=CumulativeMapSnapshotComparison.comparePreparedCorridor(
                "fleet-evidence",List.of(accepted),prepared,WINDOW);
        var saved=CumulativeMapSnapshotComparison.compareSerializedCorridor(
                "fleet-evidence",List.of(accepted),persisted,WINDOW);
        assertEquals(live.maxUnrepresentedMetres(),saved.maxUnrepresentedMetres(),0.0001);
        assertEquals(live.displayFingerprint(),saved.displayFingerprint());
    }

    @Test public void changingAcceptedBendToCanonicalChordReportsDeviation() throws Exception {
        JSONArray accepted=line("[[-0.119,51.512],[-0.115,51.5122],[-0.111,51.512]]");
        MapActivity.MapRoutes prepared=new MapActivity.MapRoutes();
        prepared.addLocalRoad(new LocalRoadCoverage.Section("chord","bad approximation",100000,
                line("[-0.119,51.512]"),line("[-0.111,51.512]"),
                Map.of("source-one",new int[]{0,100000})));
        var report=CumulativeMapSnapshotComparison.comparePreparedCorridor(
                "fleet-evidence",List.of(accepted),prepared,WINDOW);
        assertTrue(report.maxUnrepresentedMetres()>15);
    }

    @Test public void outsideViewportRoadsNeverCountAsMatchingJourney() throws Exception {
        JSONArray accepted=line("[[-0.119,51.512],[-0.111,51.512]]");
        MapActivity.MapRoutes prepared=new MapActivity.MapRoutes();
        prepared.sections.add(line("[[-0.119,51.516],[-0.111,51.516]]"));
        var report=CumulativeMapSnapshotComparison.comparePreparedCorridor(
                "fleet-evidence",List.of(accepted),prepared,WINDOW);
        assertEquals(Double.POSITIVE_INFINITY,report.maxUnrepresentedMetres(),0);
    }
}
