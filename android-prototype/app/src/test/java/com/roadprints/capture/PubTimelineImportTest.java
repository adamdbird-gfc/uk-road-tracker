package com.roadprints.capture;

import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class PubTimelineImportTest {
    private TimelineImportActivity activity;
    private JSONObject pub;
    @Before public void reset() throws Exception {
        activity=Robolectric.buildActivity(TimelineImportActivity.class).get();
        JourneyStore.deleteAll(activity);TimelineVisitStore.clear(activity);PubVisitStore.clear(activity);
        activity.getSharedPreferences(CollectionAccessStore.PREFS,0).edit().clear().commit();
        CollectionAccessStore.installForTesting(activity,CollectionAccessStore.SPOONS);
        JSONArray pubs=CollectionMapCatalog.spoons(activity);
        for(int i=0;i<pubs.length();i++)if("The Robert Pocock".equals(pubs.getJSONObject(i).optString("name")))pub=pubs.getJSONObject(i);
        assertNotNull(pub);
        Field field=TimelineImportActivity.class.getDeclaredField("pubVisitsOnly");field.setAccessible(true);field.setBoolean(activity,true);
    }
    @After public void clean(){
        JourneyStore.deleteAll(activity);TimelineVisitStore.clear(activity);PubVisitStore.clear(activity);
        activity.getSharedPreferences(CollectionAccessStore.PREFS,0).edit().clear().commit();
    }
    private String importFile(JSONObject document) throws Exception {
        File file=File.createTempFile("pub-timeline-test",".json",activity.getCacheDir());
        Files.write(file.toPath(),document.toString().getBytes(StandardCharsets.UTF_8));
        Method method=TimelineImportActivity.class.getDeclaredMethod("importFile",Uri.class,boolean.class);method.setAccessible(true);
        Object result=method.invoke(activity,Uri.fromFile(file),true);
        Field message=result.getClass().getDeclaredField("message");message.setAccessible(true);
        return (String)message.get(result);
    }
    @Test public void semanticPubImportRetainsNameAndDateWithoutImportingRoutes() throws Exception {
        JSONObject candidate=new JSONObject().put("placeName",pub.getString("name")).put("placeId","test-place")
                .put("placeLocation",new JSONObject().put("latLng",pub.getDouble("lat")+"°, "+pub.getDouble("lng")+"°"));
        JSONObject segment=new JSONObject().put("startTime","2026-01-01T12:00:00Z").put("endTime","2026-01-01T12:30:00Z")
                .put("visit",new JSONObject().put("topCandidate",candidate));
        JSONObject document=new JSONObject().put("semanticSegments",new JSONArray().put(segment));
        assertTrue(importFile(document).startsWith("Pub import complete:"));
        assertEquals(1,PubVisitStore.completed(activity).size());
        assertEquals("2026-01-01T12:00:00Z",PubVisitStore.records(activity).getJSONObject(0).getString("visited_at"));
        assertEquals("test-place",TimelineVisitStore.all(activity).getJSONObject(0).getString("place_id"));
        long revision=JourneyStore.dataRevision(activity);
        importFile(document);
        assertEquals(revision,JourneyStore.dataRevision(activity));
        assertEquals(1,PubVisitStore.records(activity).length());
    }
    @Test public void legacyGenericPlaceNeedsReviewAndRetainsExistingJourney() throws Exception {
        JSONObject kept=new JSONObject().put("journey_id","unchanged").put("mode","train")
                .put("title","Keep my journey").put("source",new JSONObject().put("type","test"));
        JourneyStore.save(activity,kept);
        long revision=JourneyStore.dataRevision(activity);
        JSONObject location=new JSONObject().put("name","Shared building")
                .put("latitudeE7",Math.round(pub.getDouble("lat")*1e7)).put("longitudeE7",Math.round(pub.getDouble("lng")*1e7));
        JSONObject visit=new JSONObject().put("location",location).put("duration",new JSONObject()
                .put("startTimestamp","2026-01-02T12:00:00Z").put("endTimestamp","2026-01-02T12:30:00Z"));
        importFile(new JSONObject().put("timelineObjects",new JSONArray().put(new JSONObject().put("placeVisit",visit))));
        assertEquals(revision,JourneyStore.dataRevision(activity));
        assertEquals("Keep my journey",JourneyStore.get(activity,"unchanged").getString("title"));
        assertTrue(PubVisitStore.completed(activity).isEmpty());
        assertTrue(PubVisitStore.pending(activity,CollectionMapCatalog.spoons(activity)).length()>0);
    }
}
