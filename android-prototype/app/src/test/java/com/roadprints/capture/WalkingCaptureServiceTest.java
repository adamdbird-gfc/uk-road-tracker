package com.roadprints.capture;

import android.location.Location;
import java.lang.reflect.*;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import com.google.android.gms.location.DetectedActivity;
import com.google.android.gms.location.ActivityTransition;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class WalkingCaptureServiceTest {
    private Field field(String name) throws Exception {
        Field f=CaptureService.class.getDeclaredField(name);f.setAccessible(true);return f;
    }
    private Location p(double metres,long time) {
        Location p=new Location("gps");p.setLongitude(metres/111195);p.setLatitude(0);
        p.setAccuracy(5);p.setTime(time);p.setSpeed(0);return p;
    }
    @Test public void gpsStartsTheStopTimerAndLateWalkingCallbacksDoNotClearIt() throws Exception {
        CaptureService service=Robolectric.buildService(CaptureService.class).get();
        service.getSharedPreferences("roadprints_capture_state",0).edit()
                .putBoolean("armed",true).putBoolean("active",true).apply();
        field("mode").set(service,"walking");field("automaticCapture").set(service,true);
        @SuppressWarnings("unchecked") List<Location> points=(List<Location>)field("points").get(service);
        Method observe=CaptureService.class.getDeclaredMethod("observeWalkingStillness",Location.class);observe.setAccessible(true);
        long now=System.currentTimeMillis();
        for(int i=0;i<=12;i++) { Location p=p(i%3,now-360000+i*30000);points.add(p);observe.invoke(service,p); }
        long began=field("stationarySince").getLong(service);
        assertTrue(began>0);assertTrue(field("gpsStillness").getBoolean(service));
        Method transition=CaptureService.class.getDeclaredMethod("handleTransition",int.class,int.class);transition.setAccessible(true);
        transition.invoke(service,DetectedActivity.WALKING,ActivityTransition.ACTIVITY_TRANSITION_ENTER);
        assertEquals(began,field("stationarySince").getLong(service));
        Location resumed=p(90,now+30000);points.add(resumed);observe.invoke(service,resumed);
        assertEquals(0,field("stationarySince").getLong(service));
        service.getSharedPreferences("roadprints_capture_state",0).edit().clear().apply();
    }
}
