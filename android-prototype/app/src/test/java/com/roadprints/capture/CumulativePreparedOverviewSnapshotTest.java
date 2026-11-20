package com.roadprints.capture;

import org.json.JSONArray;
import org.junit.Test;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.*;

/**
 * End-to-end MapRoutes display extraction using the real production union
 * Section/Line representation. Fixture-only: never reads private journeys or
 * renders an Android screen. Run in the normal JVM regression suite.
 */
public final class CumulativePreparedOverviewSnapshotTest {
    private static JSONArray line(String value) throws Exception { return new JSONArray(value); }

    private static LocalRoadCoverage.Section section(String id, String from, String to)
            throws Exception {
        return new LocalRoadCoverage.Section(id, "Synthetic accepted street", 80_000,
                line(from), line(to), Map.of("saved-a", new int[]{0,80_000},
                        "saved-b", new int[]{0,80_000}));
    }

    @Test public void baselineVersusCandidateUsesRealPreparedMapLocalSections()
            throws Exception {
        JSONArray accepted = line("[[0.374,51.44],[0.375,51.4402],[0.376,51.44]]");

        MapActivity.MapRoutes baseline = new MapActivity.MapRoutes();
        baseline.addLocalRoad(section("one-chord", "[0.374,51.44]", "[0.376,51.44]"));
        MapActivity.MapRoutes candidate = new MapActivity.MapRoutes();
        candidate.addLocalRoad(section("bend-west", "[0.374,51.44]", "[0.375,51.4402]"));
        candidate.addLocalRoad(section("bend-east", "[0.375,51.4402]", "[0.376,51.44]"));

        var a = CumulativeMapSnapshotComparison.snapshotPreparedOverview(
                "accepted-curved-road", List.of(accepted), baseline);
        var b = CumulativeMapSnapshotComparison.snapshotPreparedOverview(
                "accepted-curved-road", List.of(accepted), candidate);
        assertTrue("Actual canonical endpoint interpolation loses accepted curve",
                a.missingMetres() > 15);
        assertEquals(0, b.missingMetres(), 0.001);
        assertEquals(0, b.unsupportedMetres(), 0.001);
        assertEquals(1, a.displaySections());
        assertEquals(2, b.displaySections());
        assertEquals(a.acceptedFingerprint(), b.acceptedFingerprint());
        assertEquals(1, baseline.localRoadSegments.size());
        assertEquals(2, candidate.localRoadSegments.size());
        assertEquals(2, baseline.localRoadSections.get(0).history.size());
        System.out.println("prepared_map_ab="
                + CumulativeMapSnapshotComparison.compare(a, b));
    }

    @Test public void preparedMapIncludesTravelledCorridorsAndCanonicalLayersOnly()
            throws Exception {
        JSONArray accepted = line("[[0.37,51.44],[0.371,51.44]]");
        MapActivity.MapRoutes output = new MapActivity.MapRoutes();
        output.sections.add(accepted);
        output.incompleteMotorwaySections.add(
                line("[[1,52],[1.01,52]]")); // background reference, NOT travelled
        output.incompleteARoadSections.add(
                line("[[2,53],[2.01,53]]")); // background reference, NOT travelled
        var snapshot = CumulativeMapSnapshotComparison.snapshotPreparedOverview(
                "travelled-only", List.of(accepted), output);
        assertEquals(1, snapshot.displaySections());
        assertEquals(0, snapshot.missingMetres(), 0.0001);
        assertEquals(0, snapshot.unsupportedMetres(), 0.0001);
    }

    @Test public void preparedMapDetectsExtraneousTravelledCanonicalRoad()
            throws Exception {
        JSONArray accepted = line("[[0.37,51.44],[0.371,51.44]]");
        MapActivity.MapRoutes output = new MapActivity.MapRoutes();
        output.sections.add(accepted);
        output.addLocalRoad(section("unjustified-edge", "[0.38,51.45]", "[0.381,51.45]"));
        var snapshot = CumulativeMapSnapshotComparison.snapshotPreparedOverview(
                "extra-local-road", List.of(accepted), output);
        assertEquals(0, snapshot.missingMetres(), 0.0001);
        assertTrue(snapshot.unsupportedMetres() > 100);
    }

    @Test public void serializedRealMapSnapshotMatchesPreparedDisplay()
            throws Exception {
        JSONArray accepted = line("[[0.374,51.44],[0.375,51.4402],[0.376,51.44]]");
        MapActivity.MapRoutes routes = new MapActivity.MapRoutes();
        routes.addLocalRoad(section("west", "[0.374,51.44]", "[0.375,51.4402]"));
        routes.addLocalRoad(section("east", "[0.375,51.4402]", "[0.376,51.44]"));
        var prepared = CumulativeMapSnapshotComparison.snapshotPreparedOverview(
                "same-fixture", List.of(accepted), routes);
        org.json.JSONObject saved = new org.json.JSONObject()
                .put("sections", new JSONArray())
                .put("coveredMotorways", new JSONArray())
                .put("coveredARoads", new JSONArray())
                .put("ferries", new JSONArray())
                .put("localRoads", new JSONArray()
                        .put(routes.localRoadSections.get(0).encode())
                        .put(routes.localRoadSections.get(1).encode()));
        var restored = CumulativeMapSnapshotComparison.snapshotSerializedOverview(
                "same-fixture", List.of(accepted), saved);
        assertEquals(prepared.geometryFingerprint(), restored.geometryFingerprint());
        assertEquals(prepared.acceptedFingerprint(), restored.acceptedFingerprint());
        assertEquals(prepared.displaySections(), restored.displaySections());
        assertEquals(0, restored.missingMetres(), 0.001);
        assertEquals(0, restored.unsupportedMetres(), 0.001);
    }

    @Test public void diagnosticSourceUsesMatchedGeometryNotNoisyRawCapture()
            throws Exception {
        JSONArray accepted = line("[[0.374,51.44],[0.375,51.4402],[0.376,51.44]]");
        org.json.JSONObject geometry = new org.json.JSONObject()
                .put("type", "LineString").put("coordinates", accepted);
        org.json.JSONObject feature = new org.json.JSONObject()
                .put("geometry", geometry);
        org.json.JSONObject geojson = new org.json.JSONObject()
                .put("features", new JSONArray().put(feature));
        org.json.JSONObject processing = new org.json.JSONObject()
                .put("status", "ok").put("geojson", geojson);
        org.json.JSONObject journey = new org.json.JSONObject()
                .put("processing_status", "complete")
                .put("raw_capture_geometry", new org.json.JSONObject()
                        .put("coordinates", line("[[0,50],[10,60]]")))
                .put("processing_result", processing);
        org.json.JSONObject diagnostic = new org.json.JSONObject()
                .put("format", "roadprints_journey_diagnostics")
                .put("journey", journey);
        var lines = CumulativeMapSnapshotComparison.acceptedUncorrectedDiagnostic(diagnostic);
        assertEquals(1, lines.size());
        assertEquals(accepted.toString(), lines.get(0).toString());

        diagnostic.getJSONObject("journey").put("journey_corrections",
                new org.json.JSONObject());
        try {
            CumulativeMapSnapshotComparison.acceptedUncorrectedDiagnostic(diagnostic);
            fail("A diagnostic wrapper may not silently disregard saved corrections");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("correction"));
        }
    }

    @Test public void serializedSnapshotRejectsMissingTravelledCollections()
            throws Exception {
        try {
            CumulativeMapSnapshotComparison.snapshotSerializedOverview("invalid",
                    List.of(line("[[0,50],[0.001,50]]")),
                    new org.json.JSONObject().put("localRoads", new JSONArray()));
            fail("Incomplete cache snapshot must not be treated as authoritative");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("Missing"));
        }
    }

    @Test public void refusesOwnerSizedSnapshotRatherThanSilentlySamplingIt()
            throws Exception {
        JSONArray accepted = line("[[0.37,51.44],[0.371,51.44]]");
        MapActivity.MapRoutes output = new MapActivity.MapRoutes();
        for (int i = 0; i < 513; i++) output.sections.add(accepted);
        try {
            CumulativeMapSnapshotComparison.snapshotPreparedOverview(
                    "overbudget", List.of(accepted), output);
            fail("An incomplete sample cannot be labelled a complete A/B comparison");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("bounded"));
        }
    }
}
