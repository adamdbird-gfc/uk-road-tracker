package com.roadprints.capture;

import android.content.Context;
import android.location.*;
import android.os.Looper;
import android.util.AtomicFile;
import java.io.File;
import java.lang.reflect.*;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28) @LooperMode(LooperMode.Mode.PAUSED)
public class CaptureRecoveryIntegrationTest {
    private CaptureService service;
    private long start;
    @Before public void setup()throws Exception {
        service=Robolectric.buildService(CaptureService.class).get();start=System.currentTimeMillis()-900000;
        service.getSharedPreferences("roadprints_capture_state",0).edit().clear().putBoolean("active",true).commit();
        Shadows.shadowOf(RuntimeEnvironment.getApplication()).grantPermissions("android.permission.ACCESS_FINE_LOCATION");
        field("locationManager").set(service,service.getSystemService(Context.LOCATION_SERVICE));
        field("checkpointFile").set(service,new AtomicFile(new File(service.getFilesDir(),"recovery-test.json")));
        invoke("createNotificationChannel",new Class<?>[0]);JourneyStore.deleteAll(service);
    }
    @After public void cleanup()throws Exception {
        service.getSharedPreferences("roadprints_capture_state",0).edit().clear().commit();
        ((LocationManager)field("locationManager").get(service)).removeUpdates((LocationListener)field("locationListener").get(service));
        ExecutorService io=io();io.shutdown();io.awaitTermination(5,TimeUnit.SECONDS);
        ((AtomicFile)field("checkpointFile").get(service)).delete();JourneyStore.deleteAll(service);
    }
    private Field field(String name)throws Exception {Field f=CaptureService.class.getDeclaredField(name);f.setAccessible(true);return f;}
    private Object invoke(String name,Class<?>[] types,Object... args)throws Exception {
        Method m=CaptureService.class.getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(service,args);
    }
    private ExecutorService io()throws Exception{return (ExecutorService)field("captureIo").get(service);}
    private JSONArray rows(int first,int last)throws Exception {
        JSONArray rows=new JSONArray();for(int i=first;i<=last;i++)
            rows.put(new JSONArray().put(i*.001).put(0).put(5).put(start+i*10000).put(11));return rows;
    }
    private JSONObject snapshot(String id,int first,int last)throws Exception {
        return new JSONObject().put("journey_id",id).put("started_at",Instant.ofEpochMilli(start+first*10000).toString())
                .put("mode","unknown").put("automatic_capture",false).put("points",rows(first,last));
    }
    private void restore(JSONObject snapshot)throws Exception {
        invoke("writeCaptureCheckpoint",new Class<?>[]{JSONObject.class},snapshot);
        invoke("restoreActiveCapture",new Class<?>[0]);
    }
    @SuppressWarnings("unchecked") private List<Location> points()throws Exception{return (List<Location>)field("points").get(service);}
    @Test public void processDeathRestoresIdentityModeAndEveryObservation()throws Exception {
        restore(snapshot("original",0,5));assertTrue(CaptureService.isActive(service));
        assertEquals("original",field("journeyId").get(service));assertEquals("unknown",field("mode").get(service));
        assertEquals(6,points().size());for(int i=0;i<6;i++)assertEquals(start+i*10000,points().get(i).getTime());
        assertEquals(android.app.Service.START_STICKY,service.onStartCommand(null,0,1));
        assertTrue(CaptureService.isActive(service));
    }
    @Test public void samplingGapIsPreservedAsUnresolvedEvidenceRatherThanInventingGeometry()throws Exception {
        JSONObject state=snapshot("gap",0,3);state.getJSONArray("points").getJSONArray(3).put(0,.02).put(3,start+600000);
        restore(state);assertEquals(4,points().size());assertEquals(start+600000,points().get(3).getTime());
        JSONObject quality=CaptureQualityValidator.inspect(state.getJSONArray("points"),false);
        assertFalse(quality.optBoolean("resolved"));
    }
    @Test public void interruptedSaveHandoffIsReplayableAndDoesNotOverwriteCommittedMatch()throws Exception {
        JSONObject archived=new JSONObject().put("journey_id","old").put("mode","unknown")
                .put("source",new JSONObject().put("type","android_activity_capture"))
                .put("started_at",Instant.ofEpochMilli(start).toString()).put("ended_at",Instant.ofEpochMilli(start+20000).toString())
                .put("route_geometry",new JSONObject().put("type","LineString").put("coordinates",new JSONArray("[[0,0],[0.002,0]]")))
                .put("processing_result",new JSONObject().put("committed_marker",true));
        JourneyStore.save(service,archived);
        JSONObject pending=snapshot("old",0,5).put("finish_pending",true).put("finish_boundary_index",2)
                .put("finish_ended_at",Instant.ofEpochMilli(start+20000).toString()).put("finish_next",snapshot("next",2,5));
        restore(pending);io().submit(()->{}).get(5,TimeUnit.SECONDS);
        Shadows.shadowOf(Looper.getMainLooper()).idle();io().submit(()->{}).get(5,TimeUnit.SECONDS);
        assertEquals("next",field("journeyId").get(service));assertEquals(4,points().size());
        assertEquals(start+20000,points().get(0).getTime());
        assertTrue(JourneyStore.get(service,"old").getJSONObject("processing_result").getBoolean("committed_marker"));
        assertEquals(1,JourneyStore.count(service));
    }
}
