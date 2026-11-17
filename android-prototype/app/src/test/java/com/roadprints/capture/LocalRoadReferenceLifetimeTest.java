package com.roadprints.capture;

import static org.junit.Assert.*;

import android.content.Context;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** Exercise the asset channel after finalization opportunities on a newer Android runtime. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class LocalRoadReferenceLifetimeTest {
    @Test public void collectionBetweenJourneyReadsKeepsThePackagedReferenceOpen() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        byte[] evidence = """
                {"mode":"driving","processing_status":"complete","processing_result":{
                  "geojson":{"type":"FeatureCollection","features":[{"type":"Feature",
                    "geometry":{"type":"LineString","coordinates":[
                      [0.3753447,51.44112821],[0.3753871,51.44128429]]}}]}}}
                """.getBytes(StandardCharsets.UTF_8);
        List<File> sources = new ArrayList<>();
        try (MapJourneyStore records = new MapJourneyStore(context);
             LocalRoadCoverage coverage = LocalRoadCoverage.open(context)) {
            assertNotNull(coverage);
            for (int i = 0; i < 12; i++) {
                File source = File.createTempFile("reference-lifetime-", ".json", context.getCacheDir());
                sources.add(source);
                Files.write(source.toPath(), evidence);
                System.gc();
                System.runFinalization();
                var record = records.new Record(source, JourneyStore.fileToken(source),
                        new JSONObject().put("mode", "driving"), null, true);
                LocalRoadCoverage.Prepared prepared = coverage.contribution(record);
                assertNotNull(coverage.failureBreakdown(), prepared);
                assertFalse(prepared.spans().isEmpty());
                assertArrayEquals(evidence, Files.readAllBytes(source.toPath()));
            }
            assertEquals(12, coverage.calculated);
            assertEquals(0, coverage.held);
        } finally {
            for (File source : sources) Files.deleteIfExists(source.toPath());
        }
    }
}
