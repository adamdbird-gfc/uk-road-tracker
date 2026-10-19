package com.roadprints.capture;
import android.app.*;
import android.content.*;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.android.controller.ActivityController;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28) @LooperMode(LooperMode.Mode.PAUSED)
public class BetaUiRefinementTest {
    Context app;RoadDetailActivity.Loader previous;
    List<ActivityController<?>> screens=new ArrayList<>();List<CountDownLatch> releases=new ArrayList<>();
    @Before public void setup(){app=RuntimeEnvironment.getApplication();JourneyStore.deleteAll(app);RoadDetailCache.clear();previous=RoadDetailActivity.loader;}
    @After public void cleanup(){for(CountDownLatch release:releases)release.countDown();for(ActivityController<?> c:screens)c.destroy();RoadDetailActivity.loader=previous;RoadDetailCache.clear();}
    private static Object get(Object o,String name)throws Exception{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
    private static void set(Object o,String name,Object value)throws Exception{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);f.set(o,value);}
    private static Object call(Object o,String name,Class<?>[] types,Object...values)throws Exception{Method m=o.getClass().getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(o,values);}
    private View text(View v,String label){if(v instanceof TextView&&((TextView)v).getText().toString().equals(label))return v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){View found=text(((ViewGroup)v).getChildAt(i),label);if(found!=null)return found;}return null;}
    private JSONObject trip(String id)throws Exception{return new JSONObject().put("journey_id",id).put("title","Drive "+id).put("mode","driving").put("processing_status","complete")
        .put("started_at","2026-10-19T10:00:00Z").put("ended_at","2026-10-19T10:15:00Z").put("distance_meters",5000)
        .put("source",new JSONObject().put("type","android_activity_capture"));}
    private MapActivity recap()throws Exception{
        ActivityController<MapActivity> c=Robolectric.buildActivity(MapActivity.class).create();screens.add(c);MapActivity screen=c.get();
        List<JSONObject> rows=new ArrayList<>(Arrays.asList(trip("out"),trip("back")));
        ReturnRecapStore.collect(app,Collections.emptyList(),1);ReturnRecapStore.collect(app,rows,Long.MAX_VALUE);
        set(screen,"recapJourneys",rows);call(screen,"setMapReady",new Class[]{boolean.class},true);call(screen,"renderRecap",new Class[]{});return screen;
    }
    @Test public void dismissOverlayRestoresControlsAndPreparedMapWithoutLoadingAgain()throws Exception{
        MapActivity screen=recap();View root=screen.getWindow().getDecorView();Object map=get(screen,"mapView");Object generation=get(screen,"mapLoadGeneration");
        assertNotNull(root.findViewWithTag("welcome_back_overlay"));assertEquals(View.GONE,root.findViewWithTag("map_legend_button").getVisibility());
        assertEquals(View.GONE,((View)get(screen,"mapFilterControls")).getVisibility());
        text(root,"DISMISS").performClick();assertNull(root.findViewWithTag("welcome_back_overlay"));
        assertEquals(View.VISIBLE,root.findViewWithTag("map_legend_button").getVisibility());assertSame(map,get(screen,"mapView"));assertEquals(generation,get(screen,"mapLoadGeneration"));
        call(screen,"renderRecap",new Class[]{});assertNull(root.findViewWithTag("welcome_back_overlay"));
    }
    @Test public void recapNavigationCapturesIdsAndConsumesOnlyPresentedSummary()throws Exception{
        MapActivity screen=recap();text(screen.getWindow().getDecorView(),"VIEW NEW JOURNEYS").performClick();
        Intent next=Shadows.shadowOf(screen).getNextStartedActivity();assertEquals(JourneyListActivity.class.getName(),next.getComponent().getClassName());
        assertEquals(2,next.getStringArrayListExtra("recap_journey_ids").size());assertFalse((Boolean)get(screen,"recapVisible"));
        assertTrue(ReturnRecapStore.collect(app,Arrays.asList(trip("out"),trip("back")),Long.MAX_VALUE).isEmpty());
    }
    @Test public void backDismissesOverlayWithoutClosingMap()throws Exception{MapActivity screen=recap();screen.onBackPressed();assertFalse(screen.isFinishing());assertFalse((Boolean)get(screen,"recapVisible"));}
    @Test public void closeTextAlignsToContentEndAndRetainsTouchTarget()throws Exception{
        ActivityController<Activity> c=Robolectric.buildActivity(Activity.class).setup();screens.add(c);
        TextView close=RoadprintsDialog.closeControl(c.get(),null);assertEquals(Gravity.END,close.getGravity()&Gravity.RELATIVE_HORIZONTAL_GRAVITY_MASK);
        assertEquals(0,close.getPaddingEnd());assertTrue(close.getMinHeight()>=48);
        AlertDialog dialog=RoadprintsDialog.show(c.get(),"Road",null,null,"CLOSE",null,null,null);
        dialog.getWindow().getDecorView().findViewWithTag("roadprints_dialog_close").performClick();assertFalse(dialog.isShowing());
    }
    @Test public void recapReplayUsesPresentedIdsAndRevealsPreparedMap()throws Exception{
        MapActivity screen=recap();Object map=get(screen,"mapView");text(screen.getWindow().getDecorView(),"SEE WHAT’S NEW").performClick();
        Intent next=Shadows.shadowOf(screen).getNextStartedActivity();assertEquals(JourneyReplayActivity.class.getName(),next.getComponent().getClassName());
        assertEquals(2,next.getStringArrayListExtra("journey_ids").size());assertFalse((Boolean)get(screen,"recapVisible"));assertSame(map,get(screen,"mapView"));
    }
    private RoadDetailSelection selection(){return new RoadDetailSelection(RoadDetailSelection.MOTORWAY,"M2","GB","","");}
    private RoadDetailModel model()throws Exception{
        RoadDetailModel m=new RoadDetailModel();m.fullReference=true;m.percent=37.6;m.uniqueMetres=3760;m.referenceMetres=10000;
        m.covered.add(new JSONArray("[[0.483,51.37],[0.484,51.37]]"));m.remaining.add(new JSONArray("[[0.484,51.37],[0.485,51.37]]"));m.journeys.add(new RoadDetailModel.Entry(trip("one")));return m;
    }
    private ActivityController<RoadDetailActivity> road(boolean resume){ActivityController<RoadDetailActivity> c=Robolectric.buildActivity(RoadDetailActivity.class,selection().intent(app,false)).create();screens.add(c);if(resume)c.start().resume();return c;}
    private void drain()throws Exception{ScreenDataLoader.interactive(()->{}).get(4,TimeUnit.SECONDS);Shadows.shadowOf(Looper.getMainLooper()).idle();}
    @Test public void mapContainerSpinnerAndStatisticsSkeletonExistBeforeBackgroundWork()throws Exception{
        RoadDetailActivity screen=road(false).get();View root=screen.getWindow().getDecorView();assertNotNull(root.findViewWithTag("road_map_container"));
        assertEquals(View.VISIBLE,root.findViewWithTag("road_stats_skeleton").getVisibility());assertEquals(View.VISIBLE,root.findViewWithTag("road_loading_spinner").getVisibility());
        assertEquals(View.GONE,((View)get(screen,"zoom")).getVisibility());
    }
    @Test public void geometryCanRenderBeforeJourneyPreparationCompletes()throws Exception{
        CountDownLatch geometry=new CountDownLatch(1),release=new CountDownLatch(1);releases.add(release);RoadDetailModel ready=model();
        RoadDetailActivity.loader=new RoadDetailActivity.Loader(){public RoadDetailModel load(Context c,RoadDetailSelection s,boolean all){return ready;}
            public RoadDetailModel progressive(Context c,RoadDetailSelection s,boolean all,java.util.function.Consumer<RoadDetailModel> publish)throws Exception{
                publish.accept(ready.geometryPreview());geometry.countDown();release.await();return ready;}};
        RoadDetailActivity screen=road(true).get();assertTrue(geometry.await(3,TimeUnit.SECONDS));Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertTrue((Boolean)get(screen,"geometryShown"));assertEquals(View.GONE,((View)get(screen,"loadingPanel")).getVisibility());
        assertEquals(View.GONE,((View)get(screen,"skeleton")).getVisibility());assertFalse(((View)get(screen,"journeyAction")).isEnabled());
        assertEquals(376,((ProgressBar)get(screen,"progress")).getProgress());
        release.countDown();drain();assertTrue(((View)get(screen,"journeyAction")).isEnabled());assertTrue(((TextView)get(screen,"status")).getText().toString().contains("WHOLE ROAD"));
    }
    @Test public void contributionCardsUseSharedMetadataAndOpenSavedJourney()throws Exception{
        RoadDetailModel ready=model();RoadDetailActivity.loader=(c,s,all)->ready;RoadDetailActivity screen=road(true).get();drain();
        ((View)get(screen,"journeyAction")).performClick();AlertDialog dialog=(AlertDialog)get(screen,"journeyDialog");
        View row=dialog.getWindow().getDecorView().findViewWithTag("road_journey_one");assertNotNull(row);
        assertNotNull(text(row,"Drive one"));assertNotNull(text(row,"VIEW JOURNEY"));row.performClick();
        Intent intent=Shadows.shadowOf(screen).getNextStartedActivity();assertEquals("one",intent.getStringExtra("journey_id"));assertFalse(dialog.isShowing());
    }
    @Test public void focusedMapSharesWorkingZoomButtonsWithoutReplacingGeometry()throws Exception{
        RoadDetailActivity.loader=(c,s,all)->model();RoadDetailActivity screen=road(true).get();drain();
        RoutePreviewView map=(RoutePreviewView)get(screen,"map");map.layout(0,0,320,450);
        assertEquals(.484,map.cameraState()[0],.001);map.restoreCameraState(.484,51.37,12);double before=map.cameraState()[2];
        View controls=(View)get(screen,"zoom");text(controls,"+").performClick();assertTrue(map.cameraState()[2]>before);
        text(controls,"−").performClick();assertEquals(before,map.cameraState()[2],.001);assertSame(map,get(screen,"map"));
    }
    @Test public void longContributionListsBuildInBoundedPages()throws Exception{
        RoadDetailModel ready=model();ready.journeys.clear();for(int i=0;i<65;i++)ready.journeys.add(new RoadDetailModel.Entry(trip("id"+i)));
        RoadDetailActivity.loader=(c,s,all)->ready;RoadDetailActivity screen=road(true).get();drain();((View)get(screen,"journeyAction")).performClick();
        View root=((AlertDialog)get(screen,"journeyDialog")).getWindow().getDecorView();assertNotNull(root.findViewWithTag("road_journey_id29"));assertNull(root.findViewWithTag("road_journey_id30"));
        text(root,"SHOW MORE JOURNEYS").performClick();assertNotNull(root.findViewWithTag("road_journey_id59"));assertNull(root.findViewWithTag("road_journey_id60"));
    }
    @Test public void sectionDistanceAndWholeRoadPercentageRemainDistinct()throws Exception{
        RoadDetailModel ready=model();RoadDetailModel.Section section=ready.sectionAt(51.37,.4835);assertNotNull(section);assertTrue(section.travelled);assertTrue(section.metres>60&&section.metres<80);
        RoadDetailActivity.loader=(c,s,all)->ready;RoadDetailActivity screen=road(true).get();drain();String before=((TextView)get(screen,"status")).getText().toString();
        call(screen,"showJourneys",new Class[]{List.class,RoadDetailModel.Section.class},ready.journeys,section);
        View root=((AlertDialog)get(screen,"journeyDialog")).getWindow().getDecorView();assertNotNull(findContaining(root,"SELECTED MAPPED SECTION"));assertNotNull(findContaining(root,"Travel frequency: 1"));
        assertEquals(before,((TextView)get(screen,"status")).getText().toString());
    }
    private View findContaining(View v,String part){if(v instanceof TextView&&((TextView)v).getText().toString().contains(part))return v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){View found=findContaining(((ViewGroup)v).getChildAt(i),part);if(found!=null)return found;}return null;}
    @Test public void roadCacheKeepsDesignationsAndRevisionsSeparate()throws Exception{
        RoadDetailSelection a1=new RoadDetailSelection(RoadDetailSelection.A_ROAD,"A1","GB","","");RoadDetailSelection a1m=new RoadDetailSelection(RoadDetailSelection.MOTORWAY,"A1(M)","GB","","");
        String one=RoadDetailCache.key(app,a1,false,1),two=RoadDetailCache.key(app,a1m,false,1);assertNotEquals(one,two);assertNotEquals(one,RoadDetailCache.key(app,a1,false,2));
        RoadDetailCache.put(one,model());RoadDetailCache.put(two,model());RoadDetailCache.put("third",model());assertNull(RoadDetailCache.get(one));assertNotNull(RoadDetailCache.get(two));
        JSONObject feature=new JSONObject().put("properties",new JSONObject().put("road_ref","A1").put("road_region","GB"));assertTrue(a1.matches(feature));assertFalse(a1m.matches(feature));
        feature.getJSONObject("properties").put("road_ref","A1(M)");assertTrue(a1m.matches(feature));assertFalse(a1.matches(feature));
    }
    @Test public void reopeningUnchangedRoadUsesBoundedSnapshot()throws Exception{
        Field defaults=RoadDetailActivity.class.getDeclaredField("DEFAULT_LOADER");defaults.setAccessible(true);RoadDetailActivity.loader=(RoadDetailActivity.Loader)defaults.get(null);
        RoadDetailModel ready=model();RoadDetailCache.put(RoadDetailCache.key(app,selection(),false,JourneyStore.dataRevision(app)),ready);
        RoadDetailActivity first=road(true).get();assertSame(ready,get(first,"model"));assertFalse((Boolean)get(first,"loading"));
        RoadDetailActivity second=road(true).get();assertSame(ready,get(second,"model"));assertEquals(View.VISIBLE,((View)get(second,"zoom")).getVisibility());
    }
    @Test public void focusedCompletionTrackIsWhiteAndCoverageValuesAreUnchanged()throws Exception{
        RoadDetailModel ready=model();RoadDetailActivity.loader=(c,s,all)->ready;RoadDetailActivity screen=road(true).get();drain();
        ProgressBar graph=(ProgressBar)get(screen,"progress");assertEquals(0xFFFFFFFF,graph.getProgressBackgroundTintList().getDefaultColor());
        assertEquals(376,graph.getProgress());assertEquals(3760,ready.uniqueMetres,.001);assertEquals(10000,ready.referenceMetres,.001);
    }
    @Test public void bAndLocalMapsHideUnsupportedStatisticsAndKeepJourneyAction()throws Exception{
        for(String kind:new String[]{RoadDetailSelection.B_ROAD,RoadDetailSelection.LOCAL}){
            RoadDetailModel ready=model();ready.fullReference=false;RoadDetailActivity.loader=(c,s,all)->ready;
            RoadDetailSelection selected=new RoadDetailSelection(kind,"Test road","GB","town","");
            ActivityController<RoadDetailActivity> c=Robolectric.buildActivity(RoadDetailActivity.class,selected.intent(app,false)).create().start().resume();screens.add(c);drain();
            RoadDetailActivity screen=c.get();assertEquals(View.GONE,((View)get(screen,"stats")).getVisibility());
            assertEquals(View.VISIBLE,((View)get(screen,"zoom")).getVisibility());assertTrue(((View)get(screen,"journeyAction")).isEnabled());
            assertNull(get(get(screen,"map"),"mapRoadTapListener"));assertFalse(((View)get(screen,"map")).getContentDescription().toString().contains("Tap a section"));
            ((View)get(screen,"journeyAction")).performClick();assertNotNull(((android.app.AlertDialog)get(screen,"journeyDialog")).getWindow().getDecorView().findViewWithTag("road_journey_one"));
        }
    }
    @Test public void aRoadUsesGreenGraphWithWhiteTrackAndSectionInteraction()throws Exception{
        RoadDetailModel ready=model();RoadDetailActivity.loader=(c,s,all)->ready;
        RoadDetailSelection selected=new RoadDetailSelection(RoadDetailSelection.A_ROAD,"A1","GB","","");
        ActivityController<RoadDetailActivity> c=Robolectric.buildActivity(RoadDetailActivity.class,selected.intent(app,false)).create().start().resume();screens.add(c);drain();
        ProgressBar graph=(ProgressBar)get(c.get(),"progress");assertEquals(MapLayerPreferences.Layer.A_ROADS.color,graph.getProgressTintList().getDefaultColor());
        assertEquals(0xFFFFFFFF,graph.getProgressBackgroundTintList().getDefaultColor());assertNotNull(get(get(c.get(),"map"),"mapRoadTapListener"));
    }
}
