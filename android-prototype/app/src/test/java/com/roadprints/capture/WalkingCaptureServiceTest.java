package com.roadprints.capture;

import android.location.Location;
import android.location.LocationManager;
import android.content.Context;
import java.lang.reflect.*;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.RuntimeEnvironment;
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
    @Test public void lateAndroidStartKeepsBufferedFixesAndCancelledCandidatesResumeSampling() throws Exception {
        CaptureService service=Robolectric.buildService(CaptureService.class).get();
        Shadows.shadowOf(RuntimeEnvironment.getApplication()).grantPermissions("android.permission.ACCESS_FINE_LOCATION");
        field("locationManager").set(service,service.getSystemService(Context.LOCATION_SERVICE));
        service.getSharedPreferences("roadprints_capture_state",0).edit().clear().putBoolean("armed",true).apply();
        @SuppressWarnings("unchecked") List<Location> buffered=(List<Location>)field("armedWalkingPoints").get(service);
        long first=System.currentTimeMillis()-30000;
        Location a=p(0,first),b=p(20,first+30000);a.setSpeed(1);b.setSpeed(1);
        buffered.add(a);buffered.add(b);
        Method begin=CaptureService.class.getDeclaredMethod("beginStartCandidate",String.class);begin.setAccessible(true);
        begin.invoke(service,"walking");
        assertEquals(first,field("candidateStartedAtMs").getLong(service));
        @SuppressWarnings("unchecked") List<Location> candidate=(List<Location>)field("candidatePoints").get(service);
        assertEquals(2,candidate.size());assertEquals(first,candidate.get(0).getTime());
        Method cancel=CaptureService.class.getDeclaredMethod("cancelStartCandidate",String.class);cancel.setAccessible(true);
        cancel.invoke(service,new Object[]{null});
        assertTrue(field("diagnosticUpdatesRegistered").getBoolean(service));
        ((LocationManager)field("locationManager").get(service)).removeUpdates((android.location.LocationListener)field("locationListener").get(service));
        service.getSharedPreferences("roadprints_capture_state",0).edit().clear().apply();
    }
    @Test public void gpsStartsTheStopTimerAndLateWalkingCallbacksDoNotClearIt() throws Exception {
        gpsStop("walking",DetectedActivity.WALKING);
    }
    @Test public void drivingGpsArrivalSurvivesLateVehicleCallbacksAndResetsOnDeparture() throws Exception {
        gpsStop("driving",DetectedActivity.IN_VEHICLE);
    }
    private void gpsStop(String mode,int activity) throws Exception {
        CaptureService service=Robolectric.buildService(CaptureService.class).get();
        service.getSharedPreferences("roadprints_capture_state",0).edit()
                .putBoolean("armed",true).putBoolean("active",true).apply();
        field("mode").set(service,mode);field("automaticCapture").set(service,true);
        @SuppressWarnings("unchecked") List<Location> points=(List<Location>)field("points").get(service);
        Method observe=CaptureService.class.getDeclaredMethod("observeWalkingStillness",Location.class);observe.setAccessible(true);
        long now=System.currentTimeMillis();
        for(int i=0;i<=12;i++) { Location p=p(i%3,now-360000+i*30000);points.add(p);observe.invoke(service,p); }
        long began=field("stationarySince").getLong(service);
        assertTrue(began>0);assertTrue(field("gpsStillness").getBoolean(service));
        Method transition=CaptureService.class.getDeclaredMethod("handleTransition",int.class,int.class);transition.setAccessible(true);
        transition.invoke(service,activity,ActivityTransition.ACTIVITY_TRANSITION_ENTER);
        assertEquals(began,field("stationarySince").getLong(service));
        Location resumed=p(90,now+30000);points.add(resumed);observe.invoke(service,resumed);
        assertEquals(0,field("stationarySince").getLong(service));
        service.getSharedPreferences("roadprints_capture_state",0).edit().clear().apply();
    }
}
