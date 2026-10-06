package com.roadprints.capture;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class CaptureQualityValidatorTest {
    @Test public void movingGapsAreReportedButStationaryPausesAreAllowed() throws Exception {
        assertFalse(CaptureQualityValidator.inspect(new JSONArray("[[0,0,10,100000,1],[0.01,0,10,300000,1]]"),false).getBoolean("resolved"));
        assertTrue(CaptureQualityValidator.inspect(new JSONArray("[[0,0,10,100000,0],[0.00001,0,10,300000,0]]"),true).getBoolean("resolved"));
    }
    @Test public void regularRoadFixesPassAndTeleportOrPoorAccuracyFails() throws Exception {
        assertTrue(CaptureQualityValidator.inspect(new JSONArray("[[0,0,10,100000,10],[0.001,0,10,108000,10]]"),false).getBoolean("resolved"));
        assertFalse(CaptureQualityValidator.inspect(new JSONArray("[[0,0,10,100000,10],[0.1,0,10,108000,10]]"),false).getBoolean("resolved"));
        assertFalse(CaptureQualityValidator.inspect(new JSONArray("[[0,0,150,100000,1],[0.0001,0,10,108000,1]]"),true).getBoolean("resolved"));
    }
    @Test public void oldRecordingsDoNotClaimToHaveCaptureEvidence() throws Exception {
        assertFalse(CaptureQualityValidator.inspect(null,false).getBoolean("evidence_available"));
    }
}
