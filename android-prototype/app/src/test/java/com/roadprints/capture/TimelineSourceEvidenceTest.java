package com.roadprints.capture;

import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class TimelineSourceEvidenceTest {
    private JSONObject source(String type) throws Exception {
        return new JSONObject().put("startTime", "2020-01-01T00:00:00.123456Z")
                .put("endTime", "2020-01-01T01:00:00Z")
                .put("activity", new JSONObject().put("topCandidate",
                        new JSONObject().put("type", type).put("probability", .81)));
    }
    @Test public void modesAndLiteralBoundaryPrecisionSurviveSerialization() throws Exception {
        for (String mode : new String[]{"WALKING", "RUNNING", "CYCLING", "UNKNOWN"}) {
            JSONObject e = TimelineSourceEvidence.preserve(source(mode), "walking",
                    new JSONArray("[1000,2000,3000]"), 3, 3, false);
            JSONObject read = new JSONObject(e.toString());
            assertEquals(mode, read.getString("original_activity_type"));
            assertEquals("2020-01-01T00:00:00.123456Z", read.getString("source_started_at"));
            assertEquals(.81, read.getDouble("candidate_probability"), 0);
            assertEquals("unvalidated", read.getString("validation_status"));
            assertEquals(2000, read.getJSONArray("route_point_times_ms").getLong(1));
            assertFalse(read.has("accuracy")); assertFalse(read.has("validated"));
        }
    }
    @Test public void missingAndMalformedTimesNeverBecomeObservations() throws Exception {
        for (JSONArray times : new JSONArray[]{null, new JSONArray("[1000]"),
                new JSONArray("[1000,-1]"), new JSONArray("[1000,null]"), new JSONArray("[1000,1.5]")}) {
            JSONObject e = TimelineSourceEvidence.preserve(source("WALKING"), "walking", times, 2, 2, true);
            assertFalse(e.has("route_point_times_ms"));
            assertNotEquals("preserved", e.getString("route_timing_status"));
        }
    }
    @Test public void gapAndEqualTimesRemainUnmodifiedAndUnvalidated() throws Exception {
        JSONArray times = new JSONArray("[1000,1000,9999999]");
        JSONObject e = TimelineSourceEvidence.preserve(source("CYCLING"), "cycling", times, 3, 3, false);
        assertEquals(times.toString(), e.getJSONArray("route_point_times_ms").toString());
        times.put(0, 0);
        assertEquals(1000, e.getJSONArray("route_point_times_ms").getLong(0));
        assertEquals("unvalidated", e.getString("validation_status"));
    }
    @Test public void legacyClassificationIsRetainedWithoutInventingWaypointTiming() throws Exception {
        JSONObject old = new JSONObject().put("activityType", "RUNNING").put("confidence", "HIGH")
                .put("duration", new JSONObject().put("startTimestamp", "2019-01-01T00:00:00Z")
                        .put("endTimestamp", "2019-01-01T00:03:00Z"));
        JSONObject e = TimelineSourceEvidence.preserve(old, "walking", new JSONArray("[1000,3000]"), 2, 0, true);
        assertEquals("RUNNING", e.getString("original_activity_type"));
        assertEquals("HIGH", e.getString("source_confidence"));
        assertEquals("timelineObjects", e.getString("format"));
        assertFalse(e.has("route_point_times_ms"));
    }
    @Test public void boundsOmitWholeTimingArrayAndOversizedMetadata() throws Exception {
        JSONArray times = new JSONArray();
        for (int i=0; i<=TimelineSourceEvidence.MAX_POINTS; i++) times.put(i+1000L);
        JSONObject s = source("X".repeat(TimelineSourceEvidence.MAX_TEXT+1));
        JSONObject e = TimelineSourceEvidence.preserve(s, "unknown", times, times.length(), times.length(), false);
        assertFalse(e.has("original_activity_type"));
        assertFalse(e.has("route_point_times_ms"));
        assertEquals("point_limit_exceeded", e.getString("route_timing_status"));
        assertTrue(e.toString().length() < 1000);
        times.remove(times.length()-1);
        e = TimelineSourceEvidence.preserve(source("CYCLING"), "cycling", times, times.length(), times.length(), false);
        assertEquals(TimelineSourceEvidence.MAX_POINTS, e.getJSONArray("route_point_times_ms").length());
    }
    @Test public void unsupportedCandidateAndAbsentMetadataAreExplicit() throws Exception {
        JSONObject s = source("WALKING");
        s.getJSONObject("activity").put("topCandidate", new JSONArray("[1,2]"));
        JSONObject e = TimelineSourceEvidence.preserve(s, "unknown", null, 0, 0, false);
        assertFalse(e.has("original_activity_type"));
        assertTrue(e.has("original_activity_type_status"));
        e = TimelineSourceEvidence.preserve(new JSONObject(), "unknown", null, 0, 0, false);
        assertFalse(e.has("original_activity_type"));
        assertFalse(e.has("source_started_at"));
    }
    public static void main(String[] args) throws Exception {
        TimelineSourceEvidenceTest probe = new TimelineSourceEvidenceTest();
        JSONArray times = new JSONArray();
        for (int i=0; i<TimelineSourceEvidence.MAX_POINTS; i++) times.put(1577836800000L + i*1000L);
        long start = System.nanoTime(); int bytes = 0;
        for (int i=0; i<100; i++) bytes = TimelineSourceEvidence.preserve(probe.source("RUNNING"),
                "walking", times, times.length(), times.length(), true).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        System.out.println("100 x 10000-point source envelopes: seconds=" + (System.nanoTime()-start)/1e9
                + " last_encoded_bytes=" + bytes);
    }
}
