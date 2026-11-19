package com.roadprints.capture;

import static org.junit.Assert.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;

public final class FootpathResidualGeometryTest {
    private static JSONArray line(String pairs) throws Exception { return new JSONArray(pairs); }

    @Test public void acceptedUnmatchedWalkIsRetainedButNeverInventsConnections()
            throws Exception {
        var result = FootpathResidualGeometry.extract(java.util.List.of(
                line("[[0.37,51.44],[0.3704,51.4401],[0.3708,51.4402]]")),
                (ax, ay, bx, by) -> false);
        assertEquals(1, result.lines().length());
        assertEquals(3, result.lines().getJSONArray(0).length());
        assertFalse(result.truncated());
    }

    @Test public void neighbouringCanonicalRoadAndLongConnectorAreHeld()
            throws Exception {
        var adjacent = FootpathResidualGeometry.extract(java.util.List.of(
                line("[[0.37,51.44],[0.3704,51.4401]]")),
                (ax, ay, bx, by) -> true);
        assertEquals(0, adjacent.lines().length());
        assertEquals(1, adjacent.heldNearby());
        var longSeaLink = FootpathResidualGeometry.extract(java.util.List.of(
                line("[[1.312,51.127],[1.84,50.95]]")),
                (ax, ay, bx, by) -> false);
        assertEquals(0, longSeaLink.lines().length());
        assertEquals(1, longSeaLink.heldLong());
    }

    @Test public void excludedMiddleFragmentNeverCreatesAStraightLine() throws Exception {
        var result = FootpathResidualGeometry.extract(java.util.List.of(
                line("[[0.37,51.44],[0.3702,51.44],[0.3704,51.44],[0.3706,51.44],[0.3708,51.44]]")),
                (ax, ay, bx, by) -> ax >= 0.3702 && ax < 0.3706);
        assertEquals(2, result.lines().length());
        assertEquals(2, result.lines().getJSONArray(0).length());
        assertEquals(2, result.lines().getJSONArray(1).length());
    }

    @Test public void walkingResidualCacheContainsOnlyTrulyUnmatchedNearbyTileSegments()
            throws Exception {
        Path pack = Files.createTempFile("foot-residual-ref", ".bin");
        Path source = Files.createTempFile("foot-residual-journey", ".json");
        try {
            Files.write(pack, AcceptedRoadRouteTest.testPackV3());
            JSONObject walk = new JSONObject("""
                    {"mode":"walking","processing_status":"complete",
                     "processing_result":{"status":"ok","geojson":{"features":[{
                     "geometry":{"type":"LineString","coordinates":[
                     [0.001,51.0016],[0.0011,51.0016]]}}]}}}
                    """);
            Files.write(source, walk.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            try (RoadReference reference = new RoadReference(
                    FileChannel.open(pack), 0, Files.size(pack))) {
                JSONObject derived = LocalRoadCoverage.readAcceptedContribution(reference, source.toFile());
                assertEquals(1, derived.getInt("footResidualVersion"));
                assertEquals(0, derived.getInt("intervals"));
                assertEquals(1, derived.getJSONArray("residual").length());
                assertTrue(derived.getLong("unresolvedMm") > 0);

                walk.put("journey_corrections",
                        new JSONObject().put("removed_matched_segments", new JSONArray().put(0)));
                Files.write(source, walk.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                JSONObject corrected = LocalRoadCoverage.readAcceptedContribution(reference, source.toFile());
                assertEquals(0, corrected.getJSONArray("residual").length());
                assertEquals(0, corrected.getInt("intervals"));
                assertTrue(corrected.getLong("excludedMm") > 0);
            }
        } finally {
            Files.deleteIfExists(source);
            Files.deleteIfExists(pack);
        }
    }
}
