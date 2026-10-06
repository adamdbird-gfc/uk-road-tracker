package com.roadprints.capture;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.android.controller.ActivityController;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class ProgressAvailabilityTest {
    private final List<Runnable> jobs=new ArrayList<>();
    private final List<ActivityController<ProgressActivity>> controllers=new ArrayList<>();
    private Object oldExecutor;
    private Context app;
    private static java.lang.reflect.Field field(String name) throws Exception {java.lang.reflect.Field f=ProgressActivity.class.getDeclaredField(name);f.setAccessible(true);return f;}
    private Object get(ProgressActivity screen,String name) throws Exception{return field(name).get(screen);}
    private void invoke(ProgressActivity screen,String name) throws Exception {java.lang.reflect.Method m=ProgressActivity.class.getDeclaredMethod(name);m.setAccessible(true);m.invoke(screen);}
    @Before public void setup() throws Exception {
        app=RuntimeEnvironment.getApplication();Shadows.shadowOf(RuntimeEnvironment.getApplication()).grantPermissions("com.roadprints.capture.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION");
        oldExecutor=field("statisticsExecutor").get(null);field("statisticsExecutor").set(null,(java.util.concurrent.Executor)jobs::add);
        field("statsLoadInFlight").setBoolean(null,false);JourneyStore.deleteAll(app);
    }
    @After public void cleanup() throws Exception {
        for(ActivityController<ProgressActivity> c:controllers)c.pause().stop().destroy();
        field("statsLoadInFlight").setBoolean(null,false);ProgressActivity.clearCachedStatistics(app);field("statisticsExecutor").set(null,oldExecutor);
    }
    private ProgressActivity screen(){ActivityController<ProgressActivity> c=Robolectric.buildActivity(ProgressActivity.class).create();controllers.add(c);return c.get();}
    private Object stats() throws Exception {
        Class<?> type=Class.forName("com.roadprints.capture.ProgressActivity$DistanceStats");java.lang.reflect.Constructor<?> ctor=type.getDeclaredConstructor();ctor.setAccessible(true);Object value=ctor.newInstance();
        java.lang.reflect.Field distance=type.getDeclaredField("drivingMetres");distance.setAccessible(true);distance.setDouble(value,1609.344);
        java.lang.reflect.Field done=type.getDeclaredField("localRoadEnrichmentComplete");done.setAccessible(true);done.setBoolean(value,true);return value;
    }
    private void cache(Object stats) throws Exception {field("processCachedStats").set(null,stats);field("processCachedRevision").setLong(null,JourneyStore.dataRevision(app));}
    private boolean hasText(View root,String text){if(root instanceof TextView&&((TextView)root).getText().toString().contains(text))return true;if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++)if(hasText(((ViewGroup)root).getChildAt(i),text))return true;return false;}
    @Test public void firstOpenShowsRealScreenScaffoldAndCoalescesTabReopens() throws Exception {
        ProgressActivity first=screen(),second=screen();
        assertTrue(hasText((View)get(first,"statisticsContent"),"Statistics summary"));
        assertTrue(hasText((View)get(first,"statisticsContent"),"—"));
        assertTrue(hasText((View)get(second,"statisticsContent"),"Motorways"));
        assertEquals(1,jobs.size());
    }
    @Test public void unchangedArchiveKeepsExistingViewsAndDoesNotRescan() throws Exception {
        cache(stats());ProgressActivity screen=screen();ViewGroup content=(ViewGroup)get(screen,"statisticsContent");View summary=content.getChildAt(0);
        invoke(screen,"loadStatistics");invoke(screen,"loadStatistics");assertSame(summary,content.getChildAt(0));assertEquals(0,jobs.size());
    }
    @Test public void changedArchiveKeepsPreviousFiguresWhileRefreshing() throws Exception {
        cache(stats());ProgressActivity screen=screen();ViewGroup content=(ViewGroup)get(screen,"statisticsContent");View summary=content.getChildAt(0);
        field("processCachedRevision").setLong(null,Long.MIN_VALUE);invoke(screen,"loadStatistics");invoke(screen,"loadStatistics");
        assertSame(summary,content.getChildAt(0));assertTrue(hasText(content,"1.0 mi"));assertEquals(1,jobs.size());
    }
    @Test public void settlementUpdatesOnlyReplaceRoadDiscovery() throws Exception {
        Object stats=stats();cache(stats);ProgressActivity screen=screen();ViewGroup content=(ViewGroup)get(screen,"statisticsContent");View summary=content.getChildAt(0);
        java.lang.reflect.Method method=ProgressActivity.class.getDeclaredMethod("postSettlementProgress",stats.getClass(),int.class);method.setAccessible(true);method.invoke(screen,stats,0);
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();assertSame(summary,content.getChildAt(0));
    }
    @Test public void compactSummarySurvivesProcessCacheLossAndDeleteClearsIt() throws Exception {
        Object stats=stats();java.lang.reflect.Method save=ProgressActivity.class.getDeclaredMethod("writeStatisticsSnapshot",Context.class,stats.getClass(),long.class);save.setAccessible(true);save.invoke(null,app,stats,JourneyStore.dataRevision(app));
        ProgressActivity screen=screen();assertTrue(hasText((View)get(screen,"statisticsContent"),"1.0 mi"));assertEquals(1,jobs.size());
        JourneyStore.deleteAll(app);assertFalse(app.getSharedPreferences("roadprints_progress_snapshot",Context.MODE_PRIVATE).contains("summary"));
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();assertFalse(hasText((View)get(screen,"statisticsContent"),"1.0 mi"));
    }
}
