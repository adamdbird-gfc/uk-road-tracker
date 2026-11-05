package com.roadprints.capture;

import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import java.lang.reflect.*;
import java.util.*;
import static org.junit.Assert.*;

/** Pins the real importer schema; no private journey archive is copied into fixtures. */
@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class MilepostTimelineImportTest {
    @Test public void realWalkRunAndCycleImportsLackSufficientLocalValidation()throws Exception{
        TimelineImportActivity activity=Robolectric.buildActivity(TimelineImportActivity.class).get();
        try{
            Class<?> type=Class.forName("com.roadprints.capture.TimelineImportActivity$TimedPoint");
            Constructor<?> ctor=type.getDeclaredConstructor(long.class,double[].class);ctor.setAccessible(true);
            long first=java.time.Instant.parse("2026-11-05T00:00:00Z").toEpochMilli();
            List<Object> points=Arrays.asList(ctor.newInstance(first,new double[]{51.4,-1}),
                    ctor.newInstance(first+30000,new double[]{51.4,-.999}),ctor.newInstance(first+60000,new double[]{51.4,-.998}));
            Method method=TimelineImportActivity.class.getDeclaredMethod("journeyFromSegment",JSONObject.class,String.class,List.class);
            method.setAccessible(true);
            for(String mode:new String[]{"WALKING","RUNNING","CYCLING"}){
                JSONObject segment=new JSONObject().put("startTime","2026-11-05T00:00:00Z").put("endTime","2026-11-05T00:02:00Z")
                        .put("activity",new JSONObject().put("start",new JSONObject().put("latLng","51.4°, -1°"))
                        .put("end",new JSONObject().put("latLng","51.4°, -0.998°"))
                        .put("topCandidate",new JSONObject().put("type",mode).put("probability",.99)).put("distanceMeters",150));
                JSONObject journey=(JSONObject)method.invoke(activity,segment,"test-only-file",points);
                assertNotNull(journey);assertFalse(journey.has("timeline_match_evidence"));
                JSONObject preserved=journey.getJSONObject("timeline_source_evidence");
                assertEquals(mode,preserved.getString("original_activity_type"));
                assertEquals("RUNNING".equals(mode)?"walking":mode.toLowerCase(java.util.Locale.UK),journey.getString("mode"));
                assertEquals("unvalidated",preserved.getString("validation_status"));
                assertEquals(journey.getJSONObject("route_geometry").getJSONArray("coordinates").length(),
                        preserved.getJSONArray("route_point_times_ms").length());
                assertFalse(journey.has("capture_route_samples"));
                assertEquals("timeline_import",journey.getJSONObject("source").getString("type"));
                journey.put("processing_status","complete");
                assertEquals(MilepostProximity.Outcome.INSUFFICIENT_GEOMETRY,MilepostJourneyEvidence.nativeArchive(journey).problem());
            }
        }finally{activity.finish();}
    }
}
