package com.roadprints.capture;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import java.lang.reflect.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28) @LooperMode(LooperMode.Mode.PAUSED)
public class Beta201RegressionTest {
    private Context app;
    private final List<ActivityController<?>> screens=new ArrayList<>();
    private final List<CountDownLatch> releases=new ArrayList<>();
    @Before public void reset(){app=RuntimeEnvironment.getApplication();JourneyStore.deleteAll(app);}
    @After public void cleanup(){
        for(CountDownLatch release:releases)release.countDown();
        for(ActivityController<?> screen:screens)screen.destroy();
        RoadDetailActivity.loader=RoadDetailModel::load;
    }
    private static Object field(Object object,String name)throws Exception {
        Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);
    }
    private static void call(Object object,String name,Class<?>[] types,Object...args)throws Exception {
        Method m=object.getClass().getDeclaredMethod(name,types);m.setAccessible(true);m.invoke(object,args);
    }
    @Test public void filterTapOpensImmediatelyAndControlsFollowReadiness()throws Exception {
        ActivityController<MapActivity> c=Robolectric.buildActivity(MapActivity.class).create();screens.add(c);
        MapActivity screen=c.get();
        Field active=MapActivity.class.getDeclaredField("active");active.setAccessible(true);active.setBoolean(screen,true);
        View filter=screen.getWindow().getDecorView().findViewWithTag("map_filters_open");
        assertEquals(View.GONE,((View)field(screen,"mapFilterControls")).getVisibility());
        filter.performClick();assertNull(field(screen,"filterDialog"));
        call(screen,"setMapReady",new Class<?>[]{boolean.class},true);
        filter.performClick();AlertDialog dialog=(AlertDialog)field(screen,"filterDialog");assertTrue(dialog.isShowing());
        filter.performClick();assertSame(dialog,field(screen,"filterDialog"));
        dialog.dismiss();call(screen,"showMapLoading",new Class<?>[]{boolean.class},true);
        assertEquals(View.GONE,((View)field(screen,"mapFilterControls")).getVisibility());
        assertEquals(View.GONE,((View)field(screen,"zoomControls")).getVisibility());
        assertEquals(View.GONE,((View)field(screen,"legendButton")).getVisibility());
        filter.performClick();assertFalse(dialog.isShowing());
    }
    @Test public void mapFilterWithoutArchiveOffersTransportAndAppliesSelection()throws Exception {
        ActivityController<Activity> c=Robolectric.buildActivity(Activity.class).setup();screens.add(c);
        final JourneyFilters[] result={null};
        AlertDialog dialog=new JourneyFilterPanel(c.get(),null,new JourneyFilters(5,"matched",JourneyDateFilter.all()),true,v->result[0]=v).show();
        dialog.getWindow().getDecorView().findViewWithTag("journey_filter_apply").performClick();
        assertNotNull(result[0]);assertEquals(5,result[0].mode);assertEquals("matched",result[0].status);
    }
    @Test public void tabsReorderExistingInstancesWithoutFinishingOrClearingOthers(){
        ActivityController<ProgressActivity> c=Robolectric.buildActivity(ProgressActivity.class).create();screens.add(c);
        RoadprintsNavigation.openDestination(c.get(),AchievementsActivity.class);
        Intent intent=Shadows.shadowOf(c.get()).getNextStartedActivity();
        assertTrue((intent.getFlags()&Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)!=0);
        assertEquals(0,intent.getFlags()&Intent.FLAG_ACTIVITY_CLEAR_TOP);assertFalse(c.get().isFinishing());
        RoadprintsNavigation.openDestination(c.get(),MapActivity.class);
        assertTrue((Shadows.shadowOf(c.get()).getNextStartedActivity().getFlags()&Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)!=0);
    }
    @Test public void interactiveWorkIsNotBlockedByArchiveScreenQueue()throws Exception {
        CountDownLatch release=new CountDownLatch(1),started=new CountDownLatch(1),finished=new CountDownLatch(1);releases.add(release);
        ScreenDataLoader.execute(()->{started.countDown();try{release.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}});
        assertTrue(started.await(3,TimeUnit.SECONDS));
        ScreenDataLoader.interactive(finished::countDown);
        assertTrue("Road detail must not wait for background history",finished.await(3,TimeUnit.SECONDS));
    }
    private ActivityController<RoadDetailActivity> road() {
        Intent intent=new RoadDetailSelection(RoadDetailSelection.MOTORWAY,"M2","GB","","").intent(app,false);
        ActivityController<RoadDetailActivity> c=Robolectric.buildActivity(RoadDetailActivity.class,intent).create().start().resume();screens.add(c);return c;
    }
    @Test public void stalledRoadHasFiniteRetryAndLateCompletionCannotReplaceFailure()throws Exception {
        CountDownLatch release=new CountDownLatch(1);releases.add(release);
        RoadDetailActivity.loader=(context,selection,cumulative)->{release.await();return new RoadDetailModel();};
        ActivityController<RoadDetailActivity> c=road();
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(RoadDetailActivity.LOAD_TIMEOUT_MS+1));
        TextView status=(TextView)field(c.get(),"status");assertTrue(status.getText().toString().contains("Tap to retry"));
        assertFalse((Boolean)field(c.get(),"loading"));
        release.countDown();ScreenDataLoader.interactive(()->{}).get(3,TimeUnit.SECONDS);
        Shadows.shadowOf(Looper.getMainLooper()).idle();assertTrue(status.getText().toString().contains("Tap to retry"));
        RoadDetailActivity.loader=(context,selection,cumulative)->new RoadDetailModel();
        status.performClick();ScreenDataLoader.interactive(()->{}).get(3,TimeUnit.SECONDS);
        Shadows.shadowOf(Looper.getMainLooper()).idle();assertFalse(status.getText().toString().contains("Loading"));
    }
    @Test public void roadFailureIsVisibleAndCloseReturnsWithoutChangingArchive()throws Exception {
        long revision=JourneyStore.dataRevision(app);
        RoadDetailActivity.loader=(context,selection,cumulative)->{throw new IllegalStateException("test failure");};
        ActivityController<RoadDetailActivity> c=road();ScreenDataLoader.interactive(()->{}).get(3,TimeUnit.SECONDS);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertTrue(((TextView)field(c.get(),"status")).getText().toString().contains("could not be loaded"));
        c.get().onBackPressed();assertTrue(c.get().isFinishing());assertEquals(revision,JourneyStore.dataRevision(app));
    }
    @Test public void actualM2ReferenceLoadsCoverageAndContributingJourneys()throws Exception {
        JSONArray line=new JSONArray("[[0.4831509,51.3704781],[0.4844129,51.3698968],[0.4852193,51.3694555]]");
        JSONObject feature=new JSONObject().put("type","Feature").put("properties",new JSONObject()
                .put("road_ref","M2").put("road_region","GB").put("distance_m",180))
                .put("geometry",new JSONObject().put("type","LineString").put("coordinates",line));
        JSONObject fc=new JSONObject().put("type","FeatureCollection").put("features",new JSONArray().put(feature));
        JSONObject trip=new JSONObject().put("journey_id","m2-drive").put("mode","driving").put("processing_status","complete")
                .put("title","M2 drive").put("started_at","2026-10-19T10:00:00Z")
                .put("processing_result",new JSONObject().put("motorway_geojson",fc).put("road_geojson",fc));
        JourneyStore.save(app,trip);long revision=JourneyStore.dataRevision(app);
        RoadDetailSelection selection=new RoadDetailSelection(RoadDetailSelection.MOTORWAY,"M2","GB","","");
        RoadDetailModel model=RoadDetailModel.load(app,selection,false);
        assertTrue(model.fullReference);assertFalse(model.covered.isEmpty());assertFalse(model.remaining.isEmpty());
        assertEquals(41210,model.referenceMetres,.01);assertTrue(model.percent>0);assertTrue(model.percent<100);
        assertEquals(1,model.journeys.size());assertEquals("M2 drive",model.journeys.get(0).title);
        assertEquals(1,model.journeysAt(app,selection,51.3704781,0.4831509).size());
        assertEquals(revision,JourneyStore.dataRevision(app));
        RoadDetailModel cumulative=RoadDetailModel.load(app,selection,true);assertEquals(1,cumulative.journeys.size());
    }
    @Test public void streamedMapEvidenceRetainsAllDisplayedGeometryAndFiltersWithoutRawArrays()throws Exception {
        JSONArray line=new JSONArray("[[0.2,51.4],[0.21,51.41]]");
        JSONObject fc=new JSONObject().put("type","FeatureCollection").put("features",new JSONArray().put(new JSONObject()
                .put("type","Feature").put("properties",new JSONObject().put("road_ref","M2").put("road_region","GB").put("distance_m",100))
                .put("geometry",new JSONObject().put("type","LineString").put("coordinates",line))));
        JSONObject result=new JSONObject();for(String key:new String[]{"geojson","road_geojson","motorway_geojson","a_road_geojson","cycling_geojson"})result.put(key,fc);
        JSONObject journey=new JSONObject().put("journey_id","map-evidence").put("title","Retained title")
                .put("mode","driving").put("processing_status","complete").put("started_at","2026-10-19T10:00:00Z")
                .put("route_geometry",new JSONObject().put("type","LineString").put("coordinates",line))
                .put("gps_samples",new JSONArray().put(new JSONObject().put("private_raw",true))).put("processing_result",result)
                .put("journey_corrections",new JSONObject().put("removed_road_ids",new JSONArray().put("ref:A2")));
        JourneyStore.save(app,journey);long revision=JourneyStore.dataRevision(app);
        List<JSONObject> read=new ArrayList<>();JourneyStore.forEachMapEvidence(app,()->false,read::add);
        assertEquals(1,read.size());JSONObject compact=read.get(0);
        assertFalse(compact.has("gps_samples"));assertFalse(compact.has("route_geometry"));assertEquals(2,JourneyFilters.pointCount(compact));
        assertEquals(result.toString(),compact.getJSONObject("processing_result").toString());
        assertEquals("Retained title",compact.getString("title"));assertTrue(JourneyFilters.matchesJourneyStatusFilter(compact,"matched"));
        assertEquals(journey.getJSONObject("journey_corrections").toString(),compact.getJSONObject("journey_corrections").toString());
        assertEquals(revision,JourneyStore.dataRevision(app));
        assertTrue(JourneyStore.get(app,"map-evidence").has("gps_samples"));
    }
}
