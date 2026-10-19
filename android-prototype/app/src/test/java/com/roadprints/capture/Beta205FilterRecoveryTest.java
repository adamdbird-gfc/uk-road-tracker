package com.roadprints.capture;

import android.content.Context;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;
import org.json.JSONArray;
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
public class Beta205FilterRecoveryTest {
    Context app;MapActivity.MapRouteLoader previous;
    List<ActivityController<MapActivity>> screens=new ArrayList<>();List<CountDownLatch> releases=new ArrayList<>();
    @Before public void setup(){app=RuntimeEnvironment.getApplication();previous=MapActivity.mapRouteLoader;MapActivity.clearProcessMapCache();
        app.getSharedPreferences("roadprints_map_journey_filters",0).edit().clear().commit();
        new java.io.File(app.getFilesDir(),"screen-cache/map-routes.json").delete();}
    @After public void cleanup()throws Exception{for(CountDownLatch l:releases)l.countDown();for(ActivityController<MapActivity> c:screens)c.destroy();ScreenDataLoader.map(()->{}).get(5,TimeUnit.SECONDS);CountDownLatch settled=new CountDownLatch(1);ScreenDataLoader.execute(settled::countDown);assertTrue(settled.await(5,TimeUnit.SECONDS));MapActivity.mapRouteLoader=previous;MapActivity.clearProcessMapCache();}
    static Object get(Object o,String n)throws Exception{Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);}
    static void set(Object o,String n,Object v)throws Exception{Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);f.set(o,v);}
    static Object call(Object o,String n,Class<?>[] types,Object...args)throws Exception{Method m=o.getClass().getDeclaredMethod(n,types);m.setAccessible(true);return m.invoke(o,args);}
    MapActivity create() {ActivityController<MapActivity> c=Robolectric.buildActivity(MapActivity.class).create();screens.add(c);return c.get();}
    MapActivity ready()throws Exception{MapActivity s=create();call(s,"displayMapRoutes",new Class[]{MapActivity.MapRoutes.class,int.class},routes(1),get(s,"mapLoadGeneration"));idle();return s;}
    MapActivity.MapRoutes routes(int count)throws Exception{MapActivity.MapRoutes r=new MapActivity.MapRoutes();if(count>0)r.sections.add(new JSONArray("[[0.37,51.44],[0.371,51.441]]"));r.matchedJourneys=count;return r;}
    JourneyFilters selection(int mode,String date){return new JourneyFilters(mode,"all",new JourneyDateFilter(date,null,null));}
    void apply(MapActivity s,JourneyFilters f)throws Exception{call(s,"applyMapFilters",new Class[]{JourneyFilters.class},f);}
    void idle(){Shadows.shadowOf(Looper.getMainLooper()).idle();}
    void drain()throws Exception{ScreenDataLoader.map(()->{}).get(5,TimeUnit.SECONDS);idle();}
    View tagged(MapActivity s,String tag){return s.getWindow().getDecorView().findViewWithTag(tag);}
    CountDownLatch blockedLoader() {CountDownLatch started=new CountDownLatch(1),release=new CountDownLatch(1);releases.add(release);
        MapActivity.mapRouteLoader=(s,f,d,z)->{started.countDown();release.await();return routes(3);};return started;}

    @Test public void footDateCombinedAndEmptyResultsAllResolveAndRepeatedResetWorks()throws Exception{
        MapActivity s=ready();Object map=get(s,"mapView");MapActivity.mapRouteLoader=(a,f,d,z)->routes(f.mode==5?0:2);
        for(JourneyFilters f:Arrays.asList(selection(2,"all"),selection(0,"week"),selection(2,"week"),selection(5,"week"),JourneyFilters.all(),selection(2,"week"),JourneyFilters.all())){
            apply(s,f);drain();assertTrue((Boolean)get(s,"mapReady"));assertNull(get(s,"loadingCard"));assertNull(tagged(s,"map_load_recovery"));assertSame(map,get(s,"mapView"));
            assertEquals(f.mode,((JourneyFilters)get(s,"displayedFilters")).mode);
        }
    }
    @Test public void filteringDoesNotWaitForBlockedHistoryWorker()throws Exception{
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);releases.add(release);
        ScreenDataLoader.execute(()->{entered.countDown();try{release.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}});assertTrue(entered.await(3,TimeUnit.SECONDS));
        MapActivity s=ready();MapActivity.mapRouteLoader=(a,f,d,z)->routes(2);apply(s,selection(2,"week"));drain();assertTrue((Boolean)get(s,"mapReady"));
    }
    @Test public void cancelRetainsCameraMapAndRestoresSuccessfulFilters()throws Exception{
        MapActivity s=ready();RoutePreviewView map=(RoutePreviewView)get(s,"mapView");map.restoreCameraState(.37,51.44,12);
        CountDownLatch entered=blockedLoader();apply(s,selection(2,"week"));assertTrue(entered.await(3,TimeUnit.SECONDS));
        assertEquals(View.VISIBLE,tagged(s,"map_load_reset").getVisibility());assertFalse((Boolean)get(map,"overviewLoading"));
        tagged(s,"map_load_cancel").performClick();assertTrue((Boolean)get(s,"mapReady"));assertSame(map,get(s,"mapView"));assertEquals(12,map.cameraState()[2],.001);
        assertTrue(((JourneyFilters)get(s,"mapFilters")).isAll());assertEquals(0,app.getSharedPreferences("roadprints_map_journey_filters",0).getInt("mode",-1));
    }
    @Test public void resetCanEscapeActiveFilteringWithoutAnotherArchiveRead()throws Exception{
        MapActivity s=ready();CountDownLatch entered=blockedLoader();apply(s,selection(2,"week"));assertTrue(entered.await(3,TimeUnit.SECONDS));
        tagged(s,"map_load_reset").performClick();assertTrue((Boolean)get(s,"mapReady"));assertNull(get(s,"loadingCard"));assertNull(tagged(s,"map_load_recovery"));
    }
    @Test public void timeoutRetainsSelectionOffersRetryResetAndSuppressesAutoLoop()throws Exception{
        MapActivity s=ready();CountDownLatch entered=blockedLoader();apply(s,selection(2,"week"));assertTrue(entered.await(3,TimeUnit.SECONDS));
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(MapActivity.MAP_TIMEOUT_MS+1));
        assertTrue((Boolean)get(s,"mapLoadFailed"));assertNull(get(s,"loadingCard"));assertNotNull(tagged(s,"map_load_retry"));assertNotNull(tagged(s,"map_load_reset"));
        int generation=(Integer)get(s,"mapLoadGeneration");call(s,"refreshMap",new Class[]{});assertEquals(generation,get(s,"mapLoadGeneration"));
        assertEquals(2,((JourneyFilters)get(s,"mapFilters")).mode);MapActivity.mapRouteLoader=(a,f,d,z)->routes(2);drain();tagged(s,"map_load_retry").performClick();drain();assertTrue((Boolean)get(s,"mapReady"));
    }
    @Test public void failureAllowsRetryAndKeepsPreviousGeometry()throws Exception{
        MapActivity s=ready();Object map=get(s,"mapView");MapActivity.mapRouteLoader=(a,f,d,z)->{throw new java.io.IOException("test");};apply(s,selection(2,"all"));drain();
        assertSame(map,get(s,"mapView"));assertNotNull(tagged(s,"map_load_reset"));MapActivity.mapRouteLoader=(a,f,d,z)->routes(2);tagged(s,"map_load_retry").performClick();drain();assertTrue((Boolean)get(s,"mapReady"));
    }
    @Test public void initialLoadTimesOutWithRetryResetWithoutDependentControls()throws Exception{
        MapActivity s=create();set(s,"mapFilters",selection(2,"week"));CountDownLatch entered=blockedLoader();call(s,"refreshMap",new Class[]{});assertTrue(entered.await(3,TimeUnit.SECONDS));
        assertEquals(View.GONE,((View)get(s,"mapFilterControls")).getVisibility());Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(MapActivity.MAP_TIMEOUT_MS+1));assertNotNull(tagged(s,"map_load_reset"));assertNotNull(tagged(s,"map_load_retry"));
    }
    @Test public void lateCancelledOperationCannotOverwriteNewSelection()throws Exception{
        MapActivity s=ready();CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);releases.add(release);
        MapActivity.mapRouteLoader=(a,f,d,z)->{entered.countDown();while(release.getCount()>0)try{release.await();}catch(InterruptedException ignored){}return routes(99);};
        apply(s,selection(2,"week"));assertTrue(entered.await(3,TimeUnit.SECONDS));tagged(s,"map_load_cancel").performClick();release.countDown();drain();assertTrue(((JourneyFilters)get(s,"displayedFilters")).isAll());assertFalse(((TextView)get(s,"mapSubtitle")).getText().toString().contains("99"));
    }
    @Test public void pauseResumeDoesNotSupersedeCurrentFilterAndCompletedStateIsReused()throws Exception{
        MapActivity s=ready();CountDownLatch entered=blockedLoader();apply(s,selection(2,"week"));assertTrue(entered.await(3,TimeUnit.SECONDS));int generation=(Integer)get(s,"mapLoadGeneration");
        call(s,"onPause",new Class[]{});call(s,"onResume",new Class[]{});assertEquals(generation,get(s,"mapLoadGeneration"));releases.get(0).countDown();drain();call(s,"refreshMap",new Class[]{});assertEquals(generation,get(s,"mapLoadGeneration"));assertTrue((Boolean)get(s,"mapReady"));
    }
    @Test public void savedFilterRestoresAndCompletesAfterRestart()throws Exception{
        MapActivity first=ready();MapActivity.mapRouteLoader=(a,f,d,z)->routes(2);apply(first,selection(2,"week"));drain();screens.get(0).destroy();screens.clear();
        MapActivity restored=create();JourneyFilters saved=(JourneyFilters)get(restored,"mapFilters");assertEquals(2,saved.mode);assertEquals("week",saved.date.preset);call(restored,"refreshMap",new Class[]{});drain();assertTrue((Boolean)get(restored,"mapReady"));
    }
    @Test public void previousGeometryCanPanButCannotOpenSummaryForPendingFilters()throws Exception{
        MapActivity s=ready();CountDownLatch entered=blockedLoader();apply(s,selection(2,"week"));assertTrue(entered.await(3,TimeUnit.SECONDS));
        call(s,"loadRoadSummaryAt",new Class[]{double.class,double.class},51.44,.37);assertNull(get(s,"roadDialog"));
        assertFalse((Boolean)get(get(s,"mapView"),"overviewLoading"));
    }
    @Test public void validColdAllMapCanFinishAfterOldThirtySecondCutoff()throws Exception{
        MapActivity s=create();CountDownLatch entered=blockedLoader();call(s,"refreshMap",new Class[]{});assertTrue(entered.await(3,TimeUnit.SECONDS));
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(45));assertFalse((Boolean)get(s,"mapLoadFailed"));
        GrowingStatusControl status=(GrowingStatusControl)get(s,"mapStatus");status.render(MatchingCoordinator.get(app).snapshot());assertTrue(status.getText().toString().contains("Loading map"));
        releases.get(0).countDown();drain();assertTrue((Boolean)get(s,"mapReady"));assertTrue(status.getText().toString().contains("Map ready"));
    }
    @Test public void errorAndRetryFooterFollowTheActualMapState()throws Exception{
        MapActivity s=create();MapActivity.mapRouteLoader=(a,f,d,z)->{throw new java.io.IOException("test");};call(s,"refreshMap",new Class[]{});drain();
        GrowingStatusControl status=(GrowingStatusControl)get(s,"mapStatus");status.render(MatchingCoordinator.get(app).snapshot());assertTrue(status.getText().toString().contains("needs retry"));assertFalse(status.getText().toString().contains("Ready"));
        CountDownLatch entered=blockedLoader();tagged(s,"map_load_retry").performClick();assertTrue(entered.await(3,TimeUnit.SECONDS));assertTrue(status.getText().toString().contains("Loading map"));
        releases.get(0).countDown();drain();assertTrue(status.getText().toString().contains("Map ready"));
    }
    @Test public void failedResetClearsFiltersBypassesCachedDataAndLoadsDefaultMap()throws Exception{
        MapActivity s=create();MapActivity.mapRouteLoader=(a,f,d,z)->routes(1);apply(s,selection(2,"week"));drain();
        call(s,"mapFailure",new Class[]{int.class,String.class},get(s,"mapLoadGeneration"),"test failure");
        final java.util.concurrent.atomic.AtomicInteger calls=new java.util.concurrent.atomic.AtomicInteger();
        MapActivity.mapRouteLoader=(a,f,d,z)->{assertTrue(f.isAll());calls.incrementAndGet();return routes(4);};
        tagged(s,"map_load_reset").performClick();drain();assertEquals(1,calls.get());assertTrue(((JourneyFilters)get(s,"mapFilters")).isAll());assertTrue((Boolean)get(s,"mapReady"));
    }
    @Test public void mapPublishesEvenWhileOptionalDiskCacheWorkerIsBlocked()throws Exception{
        MapActivity s=create();CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);releases.add(release);
        ScreenDataLoader.execute(()->{entered.countDown();try{release.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}});assertTrue(entered.await(3,TimeUnit.SECONDS));
        MapActivity.mapRouteLoader=(a,f,d,z)->routes(2);call(s,"refreshMap",new Class[]{});drain();assertTrue((Boolean)get(s,"mapReady"));assertNull(get(s,"loadingCard"));
    }
    @Test public void retryForcesFreshPreparationEvenIfLastSuccessfulRevisionStillMatches()throws Exception{
        MapActivity s=ready();call(s,"mapFailure",new Class[]{int.class,String.class},get(s,"mapLoadGeneration"),"test failure");
        final java.util.concurrent.atomic.AtomicInteger calls=new java.util.concurrent.atomic.AtomicInteger();MapActivity.mapRouteLoader=(a,f,d,z)->{calls.incrementAndGet();return routes(5);};
        tagged(s,"map_load_retry").performClick();drain();assertEquals(1,calls.get());assertTrue((Boolean)get(s,"mapReady"));
    }
}
