package com.roadprints.capture;

import static org.junit.Assert.*;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.CancellationException;
import org.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35)
public class RailwayTrialWorkerTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();

    @Test public void interruptionAndRestartLeaveArchiveAndReceiptConsistent() throws Exception {
        File source = temp.newFile("journey.json");
        File output = temp.newFolder();
        String original = """
                {"journey_id":"test","mode":"train","title":"Original","distance_meters":42,
                 "started_at":"2026-11-12T09:00:00Z","ended_at":"2026-11-12T09:00:15Z",
                 "raw_capture_samples":[[0.3,51.4,8,1794474000000,15],[0.301,51.4,8,1794474005000,15],
                 [0.302,51.4,8,1794474010000,15],[0.303,51.4,8,1794474015000,15]]}
                """;
        Files.write(source.toPath(), original.getBytes(StandardCharsets.UTF_8));
        var matcher = new RailwayMatcher(RailwayStage4Test.graph(false,false),()->false);
        assertThrows(CancellationException.class,()->RailwayTrialWorker.evaluate(source,output,matcher,()->true));
        assertEquals(0,output.list().length);
        var interruptedMatcher = new RailwayMatcher(RailwayStage4Test.graph(false,false),()->true);
        assertThrows(CancellationException.class,
                ()->RailwayTrialWorker.evaluate(source,output,interruptedMatcher,()->false));
        assertEquals(0,output.list().length);
        assertTrue(RailwayTrialWorker.evaluate(source,output,matcher,()->false));
        assertEquals(original,new String(Files.readAllBytes(source.toPath()), StandardCharsets.UTF_8));
        assertEquals(1,output.list().length);
        File receipt = output.listFiles()[0];
        String result = new String(Files.readAllBytes(receipt.toPath()), StandardCharsets.UTF_8);
        assertEquals(42,new JSONObject(result).getDouble("proposed_m"),0);
        assertFalse(RailwayTrialWorker.evaluate(source,output,matcher,()->false));
        Files.write(source.toPath(), original.replace("Original", "Edited").getBytes(StandardCharsets.UTF_8));
        assertFalse(RailwayTrialWorker.evaluate(source,output,matcher,()->false));
        assertEquals(result,new String(Files.readAllBytes(receipt.toPath()), StandardCharsets.UTF_8));
    }
}
