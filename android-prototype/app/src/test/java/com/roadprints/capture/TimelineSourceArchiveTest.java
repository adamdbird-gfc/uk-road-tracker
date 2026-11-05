package com.roadprints.capture;

import android.net.Uri;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;

/** Synthetic exports exercise the real file reader and local archive, never private history. */
@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class TimelineSourceArchiveTest {
    private TimelineImportActivity activity;
    @Before public void setup() { activity=Robolectric.buildActivity(TimelineImportActivity.class).get(); JourneyStore.deleteAll(activity); }
    @After public void cleanup() { JourneyStore.deleteAll(activity); activity.finish(); }
    private JSONObject semantic(String mode) throws Exception {
        return new JSONObject("{\"semanticSegments\":[{\"startTime\":\"2020-01-01T00:00:00Z\",\"endTime\":\"2020-01-01T00:03:00Z\","
                +"\"activity\":{\"start\":{\"latLng\":\"51.4°, -1°\"},\"end\":{\"latLng\":\"51.4°, -0.998°\"},"
                +"\"topCandidate\":{\"type\":\""+mode+"\",\"probability\":0.9},\"distanceMeters\":150},"
                +"\"timelinePath\":[{\"point\":\"51.4°, -1°\",\"time\":\"2020-01-01T00:00:00Z\"},"
                +"{\"point\":\"51.4°, -0.999°\",\"time\":\"2020-01-01T00:01:00Z\"},"
                +"{\"point\":\"51.4°, -0.998°\",\"time\":\"2020-01-01T00:02:00Z\"}]}]}");
    }
    private void read(JSONObject root) throws Exception {
        java.io.File file=new java.io.File(activity.getCacheDir(),"synthetic-timeline.json");
        Files.write(file.toPath(),root.toString().getBytes(StandardCharsets.UTF_8));
        try {
            Method method=TimelineImportActivity.class.getDeclaredMethod("importFile",Uri.class,boolean.class);
            method.setAccessible(true); method.invoke(activity,Uri.fromFile(file),false);
        } finally { Files.deleteIfExists(file.toPath()); }
    }
    private JSONObject stored() { assertEquals(1,JourneyStore.count(activity)); return JourneyStore.get(activity,JourneyStore.allSummaries(activity).get(0).optString("journey_id")); }
    @Test public void allModesRoundTripThroughRealFileReaderAndArchive() throws Exception {
        for(String mode:new String[]{"WALKING","RUNNING","CYCLING","IN_BUS"}) {
            JourneyStore.deleteAll(activity); read(semantic(mode)); JSONObject j=stored();
            JSONObject e=j.getJSONObject("timeline_source_evidence");
            assertEquals(mode,e.getString("original_activity_type"));
            assertEquals(150,j.getDouble("distance_meters"),0);
            assertEquals(3,j.getJSONObject("route_geometry").getJSONArray("coordinates").length());
            assertEquals(3,e.getJSONArray("route_point_times_ms").length());
            assertEquals("2020-01-01T00:00:00Z",e.getString("source_started_at"));
            assertEquals("IN_BUS".equals(mode),j.has("timeline_match_evidence"));
            assertFalse(j.has("capture_route_samples"));
            if(!"IN_BUS".equals(mode))assertEquals(MilepostProximity.Outcome.INSUFFICIENT_GEOMETRY,
                    MilepostJourneyEvidence.nativeArchive(j).problem());
        }
    }
    @Test public void legacyObjectWaypointArrayRetainsClassificationButHasNoLocalTiming() throws Exception {
        read(new JSONObject("{\"timelineObjects\":[{\"activitySegment\":{\"activityType\":\"RUNNING\",\"confidence\":\"HIGH\","
                +"\"duration\":{\"startTimestamp\":\"2019-01-01T00:00:00Z\",\"endTimestamp\":\"2019-01-01T00:03:00Z\"},"
                +"\"startLocation\":{\"latitudeE7\":514000000,\"longitudeE7\":-10000000},"
                +"\"endLocation\":{\"latitudeE7\":514000000,\"longitudeE7\":-9980000},"
                +"\"waypointPath\":[\"51.4,-0.999\"],\"distance\":150}}]}"));
        JSONObject j=stored(),e=j.getJSONObject("timeline_source_evidence");
        assertEquals("walking",j.getString("mode")); assertEquals("RUNNING",e.getString("original_activity_type"));
        assertEquals("HIGH",e.getString("source_confidence")); assertFalse(e.has("route_point_times_ms"));
        assertEquals(3,j.getJSONObject("route_geometry").getJSONArray("coordinates").length());
    }
    @Test public void repeatedImportAndCompletedCorrectionAreNotRewritten() throws Exception {
        JSONObject input=semantic("RUNNING"); read(input); JSONObject j=stored();
        String id=j.getString("journey_id");
        j.remove("timeline_source_evidence"); // An already completed pre-change archive.
        j.put("mode","train").put("title","My correction").put("processing_status","complete")
                .put("journey_corrections",new JSONObject().put("removed_road_ids",new JSONArray("[1]")))
                .put("processing_result",new JSONObject().put("fixture","keep"));
        JourneyStore.save(activity,j); String before=JourneyStore.get(activity,id).toString();
        long revision=JourneyStore.dataRevision(activity);
        read(input); read(input);
        assertEquals(before,stored().toString()); assertEquals(revision,JourneyStore.dataRevision(activity));
        assertEquals(id,stored().getString("journey_id"));
    }
    @Test public void metadataDoesNotInvalidateNcnInputsAndIsDeletedWithJourney() throws Exception {
        read(semantic("CYCLING")); JSONObject j=stored(); String id=j.getString("journey_id");
        long before=JourneyStore.ncnDataRevision(activity);
        j.getJSONObject("timeline_source_evidence").put("source_confidence","test-only");
        JourneyStore.save(activity,j); assertEquals(before,JourneyStore.ncnDataRevision(activity));
        JourneyStore.delete(activity,id); assertNull(JourneyStore.get(activity,id)); assertEquals(0,JourneyStore.count(activity));
        assertFalse(new java.io.File(activity.getFilesDir(),"journey_"+id+".json").exists());
    }
    @Test public void unsupportedLegacyWaypointObjectIsNotMistakenForTimestampedEvidence() throws Exception {
        read(new JSONObject("{\"timelineObjects\":[{\"activitySegment\":{\"activityType\":\"CYCLING\","
                +"\"duration\":{\"startTimestamp\":\"2019-01-01T00:00:00Z\",\"endTimestamp\":\"2019-01-01T00:03:00Z\"},"
                +"\"startLocation\":{\"latitudeE7\":514000000,\"longitudeE7\":-10000000},"
                +"\"endLocation\":{\"latitudeE7\":514000000,\"longitudeE7\":-9980000},"
                +"\"waypointPath\":{\"waypoints\":[{\"latE7\":514000000,\"lngE7\":-9990000}]},\"distance\":150}}]}"));
        JSONObject j=stored(); assertEquals(0,j.getJSONObject("capture_quality").getInt("source_route_points"));
        assertFalse(j.getJSONObject("timeline_source_evidence").has("route_point_times_ms"));
    }
}
