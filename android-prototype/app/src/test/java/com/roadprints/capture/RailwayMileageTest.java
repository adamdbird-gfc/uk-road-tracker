package com.roadprints.capture;

import static org.junit.Assert.*;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.CancellationException;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class RailwayMileageTest {
    @Test public void atomicArchiveUpdatePreservesEditsAndCanRestoreMissingDistance() throws Exception {
        var app = RuntimeEnvironment.getApplication();
        String id = "mileage";
        File source = JourneyStore.archiveFile(app, id);
        JSONObject original = new JSONObject().put("journey_id", id).put("mode", "train")
                .put("title", "Home").put("revision", 5)
                .put("journey_corrections", new JSONObject().put("note", "Owner edit"))
                .put("original_distance_meters", 35000)
                .put("raw_capture_samples", new JSONArray().put(new JSONArray().put(1)));
        Files.write(source.toPath(), original.toString().getBytes(StandardCharsets.UTF_8));
        long modified = source.lastModified();
        long revision = JourneyStore.dataRevision(app);
        long inputRevision = JourneyStore.geometryRevision(app);
        byte[] untouched = Files.readAllBytes(source.toPath());
        JSONObject result = completeReceipt(source, id);

        assertThrows(CancellationException.class,
                () -> JourneyStore.applyRailwayDistance(app, id, result, () -> true));
        assertArrayEquals(untouched, Files.readAllBytes(source.toPath()));
        assertEquals(revision, JourneyStore.dataRevision(app));
        assertTrue(JourneyStore.applyRailwayDistance(app, id, result, () -> false));
        assertEquals(modified, source.lastModified());
        assertEquals(revision + 1, JourneyStore.dataRevision(app));
        assertEquals(inputRevision, JourneyStore.geometryRevision(app));
        assertEquals(39125.162, JourneyStore.readSummary(source).getDouble("distance_meters"), 0);
        assertEquals(39125.162, JourneyStore.mapEvidence(app, source, true).getDouble("distance_meters"), 0);
        JSONObject saved = new JSONObject(new String(Files.readAllBytes(source.toPath()), StandardCharsets.UTF_8));
        assertEquals(5, saved.getInt("revision"));
        assertEquals("Home", saved.getString("title"));
        assertEquals("Owner edit", saved.getJSONObject("journey_corrections").getString("note"));
        assertEquals(35000, saved.getDouble("original_distance_meters"), 0);
        assertEquals(original.getJSONArray("raw_capture_samples").toString(), saved.getJSONArray("raw_capture_samples").toString());

        assertThrows(CancellationException.class,
                () -> JourneyStore.applyRailwayDistance(app, id, result, () -> false));
        result.put("archive_token", JourneyStore.fileToken(source));
        assertFalse(JourneyStore.applyRailwayDistance(app, id, result, () -> false));
        assertEquals(revision + 1, JourneyStore.dataRevision(app));
        assertTrue(JourneyStore.restoreRailwayDistance(app, id));
        assertFalse(JourneyStore.restoreRailwayDistance(app, id));
        assertEquals(original.toString(), new JSONObject(
                new String(Files.readAllBytes(source.toPath()), StandardCharsets.UTF_8)).toString());
        assertEquals(1, JourneyStore.archiveFiles(app).size());
    }

    @Test public void partialAmbiguousAndLegacyComparisonsPreserveTheSavedValue() throws Exception {
        JSONObject journey = new JSONObject().put("journey_id", "legacy")
                .put("mode", "train").put("distance_meters", 19000);
        JSONObject receipt = completeReceipt(new File("not-used"), "legacy");
        for (String confidence : new String[]{"partial", "ambiguous", "unresolved", "full"}) {
            assertFalse(RailwayMileage.apply(journey, receipt.put("confidence", confidence)));
            assertEquals(19000, journey.getDouble("distance_meters"), 0);
        }
        journey.put("mode", "driving");
        assertFalse(RailwayMileage.apply(journey, receipt.put("confidence", "inferred")));
        assertEquals(19000, journey.getDouble("distance_meters"), 0);
    }

    private static JSONObject completeReceipt(File source, String id) throws Exception {
        return new JSONObject().put("journey_id", id).put("archive_token", JourneyStore.fileToken(source))
                .put("confidence", "inferred").put("reason", "network_route_inferred")
                .put("matcher", RailwayMatcher.VERSION).put("catalogue_sha256", RegionalRailwayCatalogue.SHA256)
                .put("network_catalogue_sha256", NetworkFirstRailway.SHA256)
                .put("network_path_sha256", "a".repeat(64)).put("fingerprint", "fixture")
                .put("inferred_m", 39125.162);
    }
}
