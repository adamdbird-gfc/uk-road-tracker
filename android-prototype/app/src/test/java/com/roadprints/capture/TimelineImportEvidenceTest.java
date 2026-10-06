package com.roadprints.capture;

import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import java.lang.reflect.*;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class TimelineImportEvidenceTest {
    private TimelineImportActivity activity;
    @Before public void setup(){activity=Robolectric.buildActivity(TimelineImportActivity.class).get();JourneyStore.deleteAll(activity);}
    @After public void cleanup(){JourneyStore.deleteAll(activity);}
    private JSONObject segment() throws Exception {
        return new JSONObject("{\"startTime\":\"2026-01-01T00:00:00Z\",\"endTime\":\"2026-01-01T00:03:00Z\",\"activity\":{\"start\":{\"latLng\":\"0°, 0°\"},\"end\":{\"latLng\":\"0°, 0.03°\"},\"topCandidate\":{\"type\":\"IN_PASSENGER_VEHICLE\",\"probability\":0.9},\"distanceMeters\":3000,\"parking\":{\"location\":{\"latLng\":\"0°, 0.03°\"},\"startTime\":\"2026-01-01T00:03:00Z\"}}}");
    }
    private List<Object> samples() throws Exception {
        Class<?> type=Class.forName("com.roadprints.capture.TimelineImportActivity$TimedPoint");
        Constructor<?> ctor=type.getDeclaredConstructor(long.class,double[].class);ctor.setAccessible(true);
        long start=java.time.Instant.parse("2026-01-01T00:00:00Z").toEpochMilli();
        return Arrays.asList(ctor.newInstance(start,new double[]{0,0}),
                ctor.newInstance(start+60000,new double[]{0,.01}),
                ctor.newInstance(start+120000,new double[]{0,.02}));
    }
    private JSONObject incoming() throws Exception {
        Method m=TimelineImportActivity.class.getDeclaredMethod("journeyFromSegment",JSONObject.class,String.class,List.class);
        m.setAccessible(true);return (JSONObject)m.invoke(activity,segment(),"synthetic",samples());
    }
    @Test public void timestampsStayAlignedWithDeduplicatedPointsAndOriginalModeIsRetained() throws Exception {
        JSONObject j=incoming();JSONArray times=j.getJSONObject("timeline_match_evidence").getJSONArray("point_times_ms");
        assertEquals(4,j.getJSONObject("route_geometry").getJSONArray("coordinates").length());assertEquals(4,times.length());
        assertTrue(j.getJSONObject("timeline_match_evidence").getBoolean("end_appended"));
        assertEquals("IN_PASSENGER_VEHICLE",j.getJSONObject("timeline_match_evidence").getJSONObject("source_transport").getString("type"));
        assertEquals(60000,times.getLong(1)-times.getLong(0));
    }
    @Test public void reimportAddsEvidenceWithoutUndoingTrainCorrectionTitleOrPreviousMatch() throws Exception {
        JSONObject old=incoming();old.remove("timeline_match_evidence");
        old.put("mode","train").put("revision",2).put("title","My corrected journey")
                .put("processing_result",new JSONObject().put("marker","preserved"));JourneyStore.save(activity,old);
        Method m=TimelineImportActivity.class.getDeclaredMethod("importSegment",JSONObject.class,String.class,List.class);
        m.setAccessible(true);m.invoke(activity,segment(),"synthetic",samples());
        JSONObject stored=JourneyStore.get(activity,old.getString("journey_id"));
        assertEquals("train",stored.getString("mode"));assertEquals("My corrected journey",stored.getString("title"));
        assertEquals("preserved",stored.getJSONObject("processing_result").getString("marker"));
        assertTrue(stored.has("timeline_match_evidence"));assertEquals(old.getString("journey_id"),stored.getString("journey_id"));
    }
}
