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
    @Test public void reported888224MillisecondGapKeepsRawEvidenceAndOmitsConnector() throws Exception {
        JSONArray samples=new JSONArray("[[0,51,8,1000000,10],[0.001,51,8,1010000,10],[0.1,51,8,1898224,10],[0.101,51,8,1908224,10]]");
        JSONObject journey=MultimodalFerryTest.journey("gap",samples);String raw=journey.toString();
        JSONObject quality=CaptureQualityValidator.inspect(samples,false);assertFalse(quality.getBoolean("resolved"));
        JSONObject gap=quality.getJSONArray("issues").getJSONObject(0);assertEquals("moving_gps_gap",gap.getString("reason"));
        assertEquals(888224,gap.getLong("duration_ms"));
        JourneyListActivity.JourneyPreview preview=JourneyListActivity.journeyPreview(journey);
        assertNull(preview.coordinates);assertEquals(2,preview.matches.size());
        assertEquals(.001,preview.matches.get(0).getJSONArray(1).getDouble(0),0);
        assertEquals(.1,preview.matches.get(1).getJSONArray(0).getDouble(0),0);
        assertEquals(raw,journey.toString());
    }
    @Test public void unreliableFixesAndReversedTimesNeverFormPreviewEdges()throws Exception {
        JSONArray samples=new JSONArray("[[0,51,8,1000000,1],[0.0001,51,200,1010000,1],[0.0002,51,8,1020000,1],[0.0003,51,8,1015000,1]]");
        assertTrue(CaptureQualityValidator.displaySections(MultimodalFerryTest.journey("bad",samples).put("mode","walking")).isEmpty());
    }
    @Test public void ordinaryContinuousEvidenceKeepsPreviewAndMismatchedEvidenceIsNotApplied()throws Exception {
        JSONArray samples=new JSONArray("[[0,51,8,1000000,1],[0.0001,51,8,1010000,1]]");
        JSONObject journey=MultimodalFerryTest.journey("good",samples).put("mode","walking");
        assertNull(CaptureQualityValidator.displaySections(journey));
        journey.getJSONObject("route_geometry").put("coordinates",new JSONArray("[[1,51],[1.0001,51]]"));
        assertNull(CaptureQualityValidator.displaySections(journey));
    }
    @Test public void oldRecordingsDoNotClaimToHaveCaptureEvidence() throws Exception {
        assertFalse(CaptureQualityValidator.inspect(null,false).getBoolean("evidence_available"));
    }
}
