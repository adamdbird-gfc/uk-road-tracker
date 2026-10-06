package com.roadprints.capture;

import android.content.Context;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import java.util.concurrent.Executor;
import java.util.function.Function;
import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.android.controller.ActivityController;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28) @LooperMode(LooperMode.Mode.PAUSED)
public class AchievementsAvailabilityTest {
    private Context app;
    private Object oldExecutor,oldCalculator;
    private final List<Runnable> jobs=new ArrayList<>();
    private final List<ActivityController<AchievementsActivity>> controllers=new ArrayList<>();
    private static Field field(String name) throws Exception {
        Field f=AchievementsActivity.class.getDeclaredField(name);f.setAccessible(true);return f;
    }
    @Before public void setup() throws Exception {
        app=RuntimeEnvironment.getApplication();
        JourneyStore.deleteAll(app);
        app.getSharedPreferences("roadprints_achievements_v1",Context.MODE_PRIVATE).edit().clear().commit();
        app.getSharedPreferences("roadprints_service_stations_v1",Context.MODE_PRIVATE).edit().clear().commit();
        oldExecutor=field("achievementExecutor").get(null);
        oldCalculator=field("achievementCalculator").get(null);
        field("achievementExecutor").set(null,(Executor)jobs::add);
        field("achievementCalculator").set(null,(Function<Context,AchievementStore.Snapshot>)AchievementStore::catalogueSnapshot);
        field("loadInFlight").setBoolean(null,false);
        ((List<?>)field("waitingScreens").get(null)).clear();
    }
    @After public void cleanup() throws Exception {
        for(ActivityController<AchievementsActivity> c:controllers)c.destroy();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        field("loadInFlight").setBoolean(null,false);
        ((List<?>)field("waitingScreens").get(null)).clear();
        AchievementsActivity.clearCachedAchievements(app);
        field("achievementExecutor").set(null,oldExecutor);
        field("achievementCalculator").set(null,oldCalculator);
    }
    private AchievementsActivity screen() {
        ActivityController<AchievementsActivity> c=Robolectric.buildActivity(AchievementsActivity.class).create();
        controllers.add(c);return c.get();
    }
    private ViewGroup content(AchievementsActivity screen) throws Exception {return (ViewGroup)field("content").get(screen);}
    private void load(AchievementsActivity screen) throws Exception {
        java.lang.reflect.Method m=AchievementsActivity.class.getDeclaredMethod("loadAchievements");m.setAccessible(true);m.invoke(screen);
    }
    private boolean contains(View view,String text) {
        if(view instanceof TextView && ((TextView)view).getText().toString().contains(text))return true;
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)if(contains(((ViewGroup)view).getChildAt(i),text))return true;
        return false;
    }
    private AchievementStore.Snapshot progress() {
        AchievementStore.Definition d=AchievementStore.definitions().get(0);
        List<AchievementStore.Progress> rows=new ArrayList<>(AchievementStore.catalogueSnapshot(app).achievements);
        rows.set(0,new AchievementStore.Progress(d,12.5,12,25,false,"12.5% saved coverage",null));
        return new AchievementStore.Snapshot(rows,Collections.emptyList(),JourneyStore.dataRevision(app));
    }
    private void cache() throws Exception {
        field("processSnapshot").set(null,progress());
        field("processSnapshotRevision").setLong(null,JourneyStore.dataRevision(app));
        field("processHighStreetRevision").setInt(null,AchievementStore.highStreetEvidenceRevision(app));
        field("processStationRevision").setLong(null,ServiceStationStore.revision(app));
    }
    @Test public void firstLaunchShowsFixedCatalogueBeforeAnyWorkRuns() throws Exception {
        AchievementsActivity screen=screen();
        assertTrue(contains(content(screen),"Quarter Marker"));
        assertTrue(contains(content(screen),"Enjoying the Solstice"));
        assertTrue(contains(content(screen),"Spanning the Nation"));
        assertFalse(contains(content(screen),"Preparing achievements"));
        assertEquals(1,jobs.size());
    }
    @Test public void rapidReopeningSharesOneRefresh() throws Exception {
        screen();screen();assertEquals(1,jobs.size());
    }
    @Test public void unchangedDataKeepsViewsAndDoesNotScan() throws Exception {
        cache();AchievementsActivity screen=screen();View first=content(screen).getChildAt(0);
        load(screen);load(screen);
        assertSame(first,content(screen).getChildAt(0));assertTrue(jobs.isEmpty());
    }
    @Test public void changedDataKeepsSavedProgressDuringRefresh() throws Exception {
        cache();field("processSnapshotRevision").setLong(null,Long.MIN_VALUE);
        AchievementsActivity screen=screen();View first=content(screen).getChildAt(0);
        load(screen);assertSame(first,content(screen).getChildAt(0));
        assertTrue(contains(content(screen),"12.5% saved coverage"));assertEquals(1,jobs.size());
    }
    @Test public void snapshotSurvivesProcessLossEvenWhenJourneyRevisionChanges() throws Exception {
        JSONObject saved=(JSONObject)invokeStatic("snapshotToJson",new Class<?>[]{AchievementStore.Snapshot.class},progress());
        saved.put("format",1).put("journeys",-99).put("highStreets",0).put("stations",0);
        app.getSharedPreferences("roadprints_achievement_snapshot",Context.MODE_PRIVATE).edit().putString("snapshot",saved.toString()).commit();
        AchievementsActivity screen=screen();assertTrue(contains(content(screen),"12.5% saved coverage"));assertEquals(1,jobs.size());
    }
    @Test public void failedRefreshKeepsCardsAndCanRetry() throws Exception {
        cache();field("processSnapshotRevision").setLong(null,Long.MIN_VALUE);
        field("achievementCalculator").set(null,(Function<Context,AchievementStore.Snapshot>)c->{throw new IllegalStateException("test failure");});
        AchievementsActivity screen=screen();View first=content(screen).getChildAt(0);
        jobs.remove(0).run();Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertSame(first,content(screen).getChildAt(0));assertTrue(contains(content(screen),"12.5% saved coverage"));
        ((TextView)field("summary").get(screen)).performClick();assertEquals(1,jobs.size());
    }
    @Test public void resetClearsPersistedAndProcessProgress() throws Exception {
        cache();app.getSharedPreferences("roadprints_achievement_snapshot",Context.MODE_PRIVATE).edit().putString("snapshot","{}").commit();
        JourneyStore.deleteAll(app);
        assertNull(field("processSnapshot").get(null));
        assertFalse(app.getSharedPreferences("roadprints_achievement_snapshot",Context.MODE_PRIVATE).contains("snapshot"));
        assertFalse(contains(content(screen()),"12.5% saved coverage"));
    }
    @Test public void resetDuringRefreshCannotRepublishOldSnapshot() throws Exception {
        screen();JourneyStore.deleteAll(app);jobs.remove(0).run();Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertNull(field("processSnapshot").get(null));
        assertFalse(app.getSharedPreferences("roadprints_achievement_snapshot",Context.MODE_PRIVATE).contains("snapshot"));
        assertEquals(1,jobs.size());
    }
    @Test public void missingCachedDefinitionStillShowsFullCatalogue() throws Exception {
        JSONObject partial=new JSONObject().put("items",new org.json.JSONArray().put(new JSONObject().put("id","motorway-quarter")));
        AchievementStore.Snapshot snapshot=(AchievementStore.Snapshot)invokeStatic("snapshotFromJson",new Class<?>[]{JSONObject.class},partial);
        assertEquals(AchievementStore.definitions().size(),snapshot.achievements.size());
    }
    private Object invokeStatic(String name,Class<?>[] types,Object value) throws Exception {
        java.lang.reflect.Method m=AchievementsActivity.class.getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(null,value);
    }
}
