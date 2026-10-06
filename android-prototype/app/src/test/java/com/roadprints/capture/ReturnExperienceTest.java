package com.roadprints.capture;

import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class ReturnExperienceTest {
    private Context app;
    @Before public void setup() { app=RuntimeEnvironment.getApplication(); JourneyStore.deleteAll(app); }
    @After public void cleanup() { JourneyStore.deleteAll(app); }
    private JSONObject row(String id, String source, long ended) throws Exception {
        return new JSONObject().put("journey_id",id).put("source",new JSONObject().put("type",source))
                .put("started_at",java.time.Instant.ofEpochMilli(ended-1000).toString())
                .put("ended_at",java.time.Instant.ofEpochMilli(ended).toString());
    }
    @Test public void upgradeEstablishesBaselineAndOnlyNewRecordingsEnterRecap() throws Exception {
        JSONObject old=row("old","android_activity_capture",100), imported=row("import","timeline_import",300);
        assertTrue(ReturnRecapStore.collect(app,Arrays.asList(old),200).isEmpty());
        JSONObject fresh=row("new","android_activity_capture",300);
        List<JSONObject> recap=ReturnRecapStore.collect(app,Arrays.asList(old,imported,fresh),400);
        assertEquals(1,recap.size());assertEquals("new",recap.get(0).getString("journey_id"));
    }
    @Test public void pendingRecapSurvivesMatchingAndDismissalDoesNotReannounce() throws Exception {
        ReturnRecapStore.collect(app,Collections.emptyList(),200);
        JSONObject fresh=row("new","android_activity_capture",300).put("processing_status","pending");
        ReturnRecapStore.collect(app,Arrays.asList(fresh),400);
        fresh.put("processing_status","complete");
        assertEquals(1,ReturnRecapStore.collect(app,Arrays.asList(fresh),500).size());
        ReturnRecapStore.dismiss(app,new HashSet<>(Arrays.asList("new")));
        assertTrue(ReturnRecapStore.collect(app,Arrays.asList(fresh),600).isEmpty());
        JourneyStore.deleteAll(app);
        assertFalse(app.getSharedPreferences(ReturnRecapStore.PREFS,Context.MODE_PRIVATE).contains("visited_at"));
    }
    @Test public void malformedFutureAndDeletedRowsDoNotRemainInRecap() throws Exception {
        ReturnRecapStore.collect(app,Collections.emptyList(),200);
        JSONObject bad=row("bad","android_activity_capture",300).put("ended_at","broken");
        assertTrue(ReturnRecapStore.collect(app,Arrays.asList(bad,row("future","android_activity_capture",900)),400).isEmpty());
        ReturnRecapStore.collect(app,Arrays.asList(row("new","android_activity_capture",500)),600);
        assertTrue(ReturnRecapStore.collect(app,Collections.emptyList(),700).isEmpty());
    }
    @Test public void returningLauncherRoutesToMapAndTrackingSettingsStillOpens() {
        org.robolectric.android.controller.ActivityController<MainActivity> home=Robolectric.buildActivity(MainActivity.class).create();
        Intent next=Shadows.shadowOf(home.get()).getNextStartedActivity();
        assertEquals(MapActivity.class.getName(),next.getComponent().getClassName());
        home.destroy();
        org.robolectric.android.controller.ActivityController<MainActivity> settings=Robolectric.buildActivity(MainActivity.class,
                new Intent(app,MainActivity.class).putExtra("tracking_settings_screen",true)).create();
        assertNull(Shadows.shadowOf(settings.get()).getNextStartedActivity());settings.destroy();
    }
    private boolean containsMap(View view) {
        if(view instanceof RoutePreviewView)return true;
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)
            if(containsMap(((ViewGroup)view).getChildAt(i)))return true;
        return false;
    }
    @Test public void mapExistsBeforeAnyArchiveLoadingOrActivityResume() {
        org.robolectric.android.controller.ActivityController<MapActivity> map=Robolectric.buildActivity(MapActivity.class).create();
        assertTrue(containsMap(map.get().getWindow().getDecorView()));map.destroy();
    }
    @Test public void canonicalReplaySuppressesRepeatedSectionsButIncludesNewStretch() throws Exception {
        JSONArray anchors=new JSONArray();
        for(int i=0;i<8;i++)anchors.put(new JSONArray().put(-.25-i*.003).put(51.7));
        JSONObject cache=new JSONObject().put("roads",new JSONObject().put("M25",new JSONObject().put("anchors",anchors)));
        JSONObject old=motorway("old",new JSONArray().put(anchors.get(0)).put(anchors.get(1)));
        MotorwayProgressCalculator baseline=new MotorwayProgressCalculator(app,cache,false,false);baseline.addJourney(old);
        Map<String,Set<Integer>> previous=new HashMap<>();
        previous.put("M25",new HashSet<>(baseline.finish().roads.get(0).coveredSections));
        MotorwayProgressCalculator repeat=new MotorwayProgressCalculator(app,cache,false,false);repeat.addJourney(old);
        assertTrue(repeat.newlyCoveredSections(previous).isEmpty());repeat.finish();
        MotorwayProgressCalculator fresh=new MotorwayProgressCalculator(app,cache,false,false);
        fresh.addJourney(motorway("new",new JSONArray().put(anchors.get(5)).put(anchors.get(6))));
        assertFalse(fresh.newlyCoveredSections(previous).get("M25").isEmpty());fresh.finish();
    }
    private JSONObject motorway(String id,JSONArray coordinates) throws Exception {
        JSONObject feature=new JSONObject().put("properties",new JSONObject().put("road_ref","M25"))
                .put("geometry",new JSONObject().put("type","LineString").put("coordinates",coordinates));
        return new JSONObject().put("journey_id",id).put("processing_status","complete").put("mode","driving")
                .put("processing_result",new JSONObject().put("motorway_geojson",new JSONObject().put("features",new JSONArray().put(feature))));
    }
}
