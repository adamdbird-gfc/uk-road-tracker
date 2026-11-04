package com.roadprints.capture;

import android.location.Location;
import android.location.LocationListener;
import android.content.Context;
import java.lang.reflect.*;
import java.time.*;
import java.util.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import com.google.android.gms.location.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class DrivingTransitionServiceTest {
    private CaptureService service;
    private ContextMapCache cache;
    private long start;
    private static Field field(Class<?> type,String name)throws Exception {Field f=type.getDeclaredField(name);f.setAccessible(true);return f;}
    private Object get(String name)throws Exception{return field(CaptureService.class,name).get(service);}
    private void set(String name,Object value)throws Exception{field(CaptureService.class,name).set(service,value);}
    @Before public void setup()throws Exception {
        service=Robolectric.buildService(CaptureService.class).get();
        service.getSharedPreferences("roadprints_capture_state",0).edit().clear().putBoolean("armed",true).putBoolean("active",true).putBoolean("public_map_context",true).commit();
        set("mode","walking");set("automaticCapture",true);start=System.currentTimeMillis();
        cache=new ContextMapCache(service);set("contextCache",cache);
        JSONArray geometry=new JSONArray().put(new JSONObject().put("lat",51.49).put("lon",-.1)).put(new JSONObject().put("lat",51.54).put("lon",-.1));
        field(ContextMapCache.class,"features").set(cache,new JSONArray().put(new JSONObject().put("tags",new JSONObject().put("highway","primary")).put("geometry",geometry)));
        field(ContextMapCache.class,"south").setDouble(cache,51.5);field(ContextMapCache.class,"west").setDouble(cache,-.1);
        field(ContextMapCache.class,"validUntil").setLong(cache,System.currentTimeMillis()+3600000);
    }
    @After public void cleanup(){cache.close();service.getSharedPreferences("roadprints_capture_state",0).edit().clear().commit();}
    private void fix(int i,double metres,float speed,float accuracy,long age)throws Exception {
        long time=start+i*10000L;set("wallClock",Clock.fixed(Instant.ofEpochMilli(time+age),ZoneOffset.UTC));
        Location p=new Location("gps");p.setLatitude(51.5+metres/111195);p.setLongitude(-.1);p.setAccuracy(accuracy);p.setSpeed(speed);p.setTime(time);
        ((LocationListener)get("locationListener")).onLocationChanged(p);
    }
    private void drive()throws Exception{for(int i=0;i<10;i++)fix(i,i*150,15,5,0);}
    @Test public void provisionalWalkingAtVehicleSpeedReachesRoadContextAndDriving()throws Exception {
        drive();assertEquals("driving",get("mode"));assertFalse((Boolean)get("captureFinishing"));
        assertEquals(10,((List<?>)get("points")).size());
        assertTrue(service.getSharedPreferences("roadprints_capture_state",0).getBoolean("public_map_context",false));
    }
    @Test public void disabledContextPreservesUncertaintyAndConsent()throws Exception {
        service.getSharedPreferences("roadprints_capture_state",0).edit().putBoolean("public_map_context",false).commit();
        drive();assertEquals("unknown",get("mode"));
        assertFalse(service.getSharedPreferences("roadprints_capture_state",0).getBoolean("public_map_context",true));
    }
    @Test public void absenceOfActivityCallbackDoesNotBlockGpsContextCorrection()throws Exception {
        assertEquals("unknown",get("currentActivity"));drive();assertEquals("driving",get("mode"));
    }
    @Test public void poorOrStaleGpsDoesNotChangeProvisionalMode()throws Exception {
        for(int i=0;i<10;i++)fix(i,i*150,15,150,0);assertEquals("walking",get("mode"));
        for(int i=10;i<20;i++)fix(i,i*150,15,5,120000);assertEquals("walking",get("mode"));
    }
    @Test public void implausibleJumpDoesNotProduceDriving()throws Exception {
        fix(0,0,1,5,0);
        for(int i=1;i<10;i++)fix(i,i*1000,15,5,0);
        assertEquals("walking",get("mode"));
    }
    @Test public void oneVehicleSpeedSpikeDoesNotChangeWalking()throws Exception {
        fix(0,0,1,5,0);fix(1,100,15,5,0);
        for(int i=2;i<8;i++)fix(i,100+i*10,1,5,0);
        assertEquals("walking",get("mode"));
    }
    @Test public void briefSlowTrafficAndStoppedFixesKeepRecognisedDriving()throws Exception {
        drive();for(int i=10;i<13;i++)fix(i,1350+(i-9)*15,1.5f,5,0);
        assertEquals("driving",get("mode"));
        for(int i=13;i<18;i++)fix(i,1395,0,5,0);
        assertEquals("driving",get("mode"));assertFalse((Boolean)get("captureFinishing"));
    }
    @Test public void existingCyclingAndBusLabelsAreNotOverwrittenBySpeed()throws Exception {
        for(String mode:new String[]{"cycling","bus"}) {
            set("mode",mode);set("lastPoint",null);set("contextModel",new ContextJourneyModel());
            ((List<?>)get("points")).clear();drive();assertEquals(mode,get("mode"));
        }
    }
    @Test public void vehicleActivityAloneStillCannotAssignDriving()throws Exception {
        Method classify=CaptureService.class.getDeclaredMethod("modeForActivity",int.class);classify.setAccessible(true);
        assertEquals("unknown",classify.invoke(service,DetectedActivity.IN_VEHICLE));
        Method transition=CaptureService.class.getDeclaredMethod("handleTransition",int.class,int.class);transition.setAccessible(true);
        transition.invoke(service,DetectedActivity.IN_VEHICLE,ActivityTransition.ACTIVITY_TRANSITION_ENTER);
        assertEquals("walking",get("mode"));assertEquals("unknown",get("pendingActivityMode"));
    }
}
