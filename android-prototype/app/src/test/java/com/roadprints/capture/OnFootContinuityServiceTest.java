package com.roadprints.capture;

import android.location.Location;
import android.location.LocationListener;
import com.google.android.gms.location.ActivityTransition;
import com.google.android.gms.location.DetectedActivity;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import static org.junit.Assert.*;

/** Replay real timing/speed/accuracy with location-free distance adaptations.
 * Invoke deadlines explicitly against the injected UTC clock, without sleeping. */
@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
@LooperMode(LooperMode.Mode.PAUSED)
public class OnFootContinuityServiceTest {
    private CaptureService service;
    private final long start=1_700_000_000_000L;
    private Field field(String name)throws Exception {Field f=CaptureService.class.getDeclaredField(name);f.setAccessible(true);return f;}
    private Object get(String name)throws Exception{return field(name).get(service);}
    private void set(String name,Object value)throws Exception{field(name).set(service,value);}
    private void call(String name)throws Exception{Method m=CaptureService.class.getDeclaredMethod(name);m.setAccessible(true);m.invoke(service);}
    private void clock(long time)throws Exception{set("wallClock",Clock.fixed(Instant.ofEpochMilli(time),ZoneOffset.UTC));}
    @Before public void setup()throws Exception {
        service=Robolectric.buildService(CaptureService.class).get();
        service.getSharedPreferences("roadprints_capture_state",0).edit().clear()
                .putBoolean("armed",true).putBoolean("active",true).commit();
        set("automaticCapture",true);set("mode","walking");clock(start);
    }
    @After public void cleanup(){service.getSharedPreferences("roadprints_capture_state",0).edit().clear().commit();}
    private void activity(int type,long time)throws Exception {
        clock(time);Method m=CaptureService.class.getDeclaredMethod("handleTransition",int.class,int.class);m.setAccessible(true);
        m.invoke(service,type,ActivityTransition.ACTIVITY_TRANSITION_ENTER);
    }
    private void fix(long time,double metres,float speed,float accuracy)throws Exception {
        clock(time);Location p=new Location("gps");p.setTime(time);p.setLatitude(0);p.setLongitude(metres/111195d);
        p.setAccuracy(accuracy);if(speed>=0)p.setSpeed(speed);
        ((LocationListener)get("locationListener")).onLocationChanged(p);
    }
    private void open()throws Exception {assertFalse((Boolean)get("captureFinishing"));assertNull(get("nextCaptureMode"));}
    private static int type(String s){return "running".equals(s)?DetectedActivity.RUNNING:"still".equals(s)?DetectedActivity.STILL:DetectedActivity.WALKING;}
    private JSONArray recordings()throws Exception {
        try(java.io.InputStream in=getClass().getResourceAsStream("/on-foot-fragmentation/replay.json")){
            assertNotNull(in);return new JSONObject(new String(in.readAllBytes(),StandardCharsets.UTF_8)).getJSONArray("recordings");
        }
    }
    @Test public void sixIncidentSnapshotsAndOctoberReferenceStayOnFoot()throws Exception {
        JSONArray recordings=recordings();assertEquals(7,recordings.length());
        for(int r=0;r<recordings.length();r++) {
            JSONObject recording=recordings.getJSONObject(r);String original=recording.toString();
            set("mode",recording.getString("initial_mode"));set("contextModel",new ContextJourneyModel());
            ((WalkingStillnessDetector)get("walkingStillness")).reset();set("lastPoint",null);
            set("stationarySince",0L);set("stillActivitySince",0L);set("gpsStillness",false);call("clearPendingActivityMode");
            @SuppressWarnings("unchecked") List<Location> points=(List<Location>)get("points");points.clear();
            JSONArray samples=recording.getJSONArray("samples"),events=recording.getJSONArray("activities");int event=0;long now=start;
            for(int i=0;i<samples.length();i++) {
                JSONArray p=samples.getJSONArray(i);long time=start+p.getLong(0);now=Math.max(now,time);
                while(event<events.length()&&start+events.getJSONArray(event).getLong(0)<=now){
                    JSONArray e=events.getJSONArray(event++);activity(type(e.getString(1)),start+e.getLong(0));
                }
                fix(time,p.getDouble(1),(float)p.getDouble(3),(float)p.getDouble(2));clock(now);
                if(get("pendingActivityMode")!=null&&now-field("pendingActivitySince").getLong(service)>=15000)call("commitPendingActivityMode");
                ((Runnable)get("finishIfStill")).run();open();
            }
            assertEquals(samples.length(),points.size());assertEquals(original,recording.toString());
            assertEquals(recording.getString("initial_mode"),get("mode"));
            if(r==0)assertEquals(281,recording.getInt("matched_points"));
        }
    }
    @Test public void runningWalkingRunningAndBriefStillNoiseKeepOneCapture()throws Exception {
        set("mode","running");
        for(int i=0;i<45;i++) {
            long t=start+i*10000L;
            activity(i%7==0?DetectedActivity.STILL:i>=15&&i<30?DetectedActivity.WALKING:DetectedActivity.RUNNING,t);
            fix(t,i*20,i>=15&&i<30?1.5f:3,5);call("commitPendingActivityMode");
            ((Runnable)get("finishIfStill")).run();open();
        }
        assertEquals("running",get("mode"));assertEquals(0L,field("stationarySince").getLong(service));
    }
    @Test public void queuedPaceChangeCannotSplitAfterRestartOrDelayedCallback()throws Exception {
        for(String mode:new String[]{"walking","running"}) {
            set("mode",mode);set("pendingActivityMode","walking".equals(mode)?"running":"walking");
            set("pendingActivitySince",start-100000);call("commitPendingActivityMode");open();assertNull(get("pendingActivityMode"));
        }
    }
    @Test public void genuineArrivalStillResolvesAtStartOfSustainedDwell()throws Exception {
        fix(start,-30,2,5);
        for(int i=0;i<=11;i++)fix(start+10000+i*30000,0,0,5);
        assertTrue((Boolean)get("gpsStillness"));
        Method resolve=CaptureService.class.getDeclaredMethod("resolveGpsArrivalBoundary",long.class);resolve.setAccessible(true);
        resolve.invoke(service,start+340000);
        assertEquals("arrival_gps_dwell",get("contextReason"));assertEquals(1,field("stationEndIndex").getInt(service));
        assertTrue(((WalkingStillnessDetector)get("walkingStillness")).canFinish(start+340000));
        assertFalse(new ContextJourneyModel().holdPause("walking",330000,new ContextMap.Evidence()));
    }
    @Test public void vehicleActivityStillQueuesAGenuineTransportChange()throws Exception {
        activity(DetectedActivity.IN_VEHICLE,start);assertEquals("unknown",get("pendingActivityMode"));open();
    }
}
