package com.roadprints.capture;

import android.content.Context;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.json.JSONObject;
import org.json.JSONArray;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
@LooperMode(LooperMode.Mode.PAUSED)
public class LateJourneyRecapTest {
    private Context app;
    @Before public void setup() {
        app=RuntimeEnvironment.getApplication();JourneyStore.deleteAll(app);ReturnRecapStore.clear(app);
        Shadows.shadowOf(RuntimeEnvironment.getApplication()).grantPermissions("com.roadprints.capture.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION");
    }
    @After public void cleanup(){JourneyStore.deleteAll(app);ReturnRecapStore.clear(app);}
    private JSONObject row(String id,long ended) throws Exception {
        return new JSONObject().put("journey_id",id).put("mode","walking")
            .put("source",new JSONObject().put("type","android_activity_capture"))
            .put("started_at",Instant.ofEpochMilli(ended-1000).toString())
            .put("ended_at",Instant.ofEpochMilli(ended).toString()).put("processing_status","pending")
            .put("route_geometry",new JSONObject().put("type","LineString")
                .put("coordinates",new JSONArray("[[0.37,51.44],[0.371,51.441]]")));
    }
    private Object get(Object target,String name)throws Exception{java.lang.reflect.Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}
    private void set(Object target,String name,Object value)throws Exception{java.lang.reflect.Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);f.set(target,value);}
    private void call(Object target,String name)throws Exception{java.lang.reflect.Method m=target.getClass().getDeclaredMethod(name);m.setAccessible(true);m.invoke(target);}
    @Test public void completedBeforeEarlyVisitButSavedAfterwardsStillAppears() throws Exception {
        ReturnRecapStore.collect(app,Collections.emptyList(),1000);
        ReturnRecapStore.collect(app,Collections.emptyList(),5000); // App opened while disk save was pending.
        JSONObject late=row("return",3000);
        assertEquals("return",ReturnRecapStore.collect(app,Arrays.asList(late),6000).get(0).getString("journey_id"));
    }
    @Test public void dismissingOutboundDoesNotConsumeLaterReturnOrDeleteArchive() throws Exception {
        ReturnRecapStore.collect(app,Collections.emptyList(),1000);
        JSONObject out=row("out",2000),back=row("back",3000);
        JourneyStore.save(app,out);JourneyStore.save(app,back);
        ReturnRecapStore.collect(app,Arrays.asList(out),4000);
        ReturnRecapStore.dismiss(app,new HashSet<>(Arrays.asList("out")));
        List<JSONObject> pending=ReturnRecapStore.collect(app,Arrays.asList(out,back),5000);
        assertEquals(1,pending.size());assertEquals("back",pending.get(0).getString("journey_id"));
        assertNotNull(JourneyStore.get(app,"out"));assertNotNull(JourneyStore.get(app,"back"));
    }
    @Test public void oldCardDismissAcknowledgesOnlyIdsItActuallyDisplayed() throws Exception {
        ReturnRecapStore.collect(app,Collections.emptyList(),1000);
        JSONObject out=row("out",2000),back=row("back",3000);
        ReturnRecapStore.collect(app,Arrays.asList(out,back),4000);
        org.robolectric.android.controller.ActivityController<MapActivity> controller=Robolectric.buildActivity(MapActivity.class).create();
        MapActivity screen=controller.get();set(screen,"mapReady",true);set(screen,"recapJourneys",new ArrayList<>(Arrays.asList(out)));call(screen,"renderRecap");
        View dismiss=find(screen.getWindow().getDecorView(),"DISMISS");assertNotNull(dismiss);
        set(screen,"recapJourneys",new ArrayList<>(Arrays.asList(out,back))); // New data arrived after card was made.
        dismiss.performClick();
        List<JSONObject> pending=ReturnRecapStore.collect(app,Arrays.asList(out,back),5000);
        assertEquals(1,pending.size());assertEquals("back",pending.get(0).getString("journey_id"));controller.destroy();
    }
    @Test public void legacyMigrationPreservesPendingWithoutRecappingWholeArchive() throws Exception {
        app.getSharedPreferences(ReturnRecapStore.PREFS,0).edit().putLong("visited_at",4000)
            .putStringSet("pending",new HashSet<>(Arrays.asList("pending"))).commit();
        List<JSONObject> pending=ReturnRecapStore.collect(app,Arrays.asList(row("old",1000),row("pending",2000)),5000);
        assertEquals(1,pending.size());assertEquals("pending",pending.get(0).getString("journey_id"));
    }
    @Test public void listObservesRevisionWhileOpenAndStopsObservingWhenPaused() throws Exception {
        org.robolectric.android.controller.ActivityController<JourneyListActivity> controller=Robolectric.buildActivity(JourneyListActivity.class).create().start().resume();
        JourneyListActivity screen=controller.get();drainLoads();
        JourneyStore.save(app,row("late",3000));
        ((Runnable)get(screen,"observeJourneyChanges")).run();drainLoads();
        List<?> loaded=(List<?>)get(screen,"journeys");assertEquals(1,loaded.size());
        assertEquals("late",((JSONObject)loaded.get(0)).getString("journey_id"));
        controller.pause();long shown=(Long)get(screen,"displayedJourneyRevision");
        JourneyStore.save(app,row("later",4000));((Runnable)get(screen,"observeJourneyChanges")).run();
        assertEquals(shown,(long)(Long)get(screen,"displayedJourneyRevision"));controller.stop().destroy();
    }
    private void drainLoads() throws Exception {
        for(int i=0;i<2;i++) {CountDownLatch done=new CountDownLatch(1);ScreenDataLoader.execute(done::countDown);
            assertTrue(done.await(15,TimeUnit.SECONDS));Shadows.shadowOf(Looper.getMainLooper()).idle();}
    }
    private View find(View view,String text){if(view instanceof TextView&&text.contentEquals(((TextView)view).getText()))return view;if(view instanceof ViewGroup){ViewGroup g=(ViewGroup)view;for(int i=0;i<g.getChildCount();i++){View found=find(g.getChildAt(i),text);if(found!=null)return found;}}return null;}
}
