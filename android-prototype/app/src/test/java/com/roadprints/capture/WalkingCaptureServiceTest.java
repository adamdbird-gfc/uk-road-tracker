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
    @Test public void runningActivityIsNotTranslatedBackToWalking() throws Exception {
        CaptureService service=Robolectric.buildService(CaptureService.class).get();
        Method classify=CaptureService.class.getDeclaredMethod("modeForActivity",int.class);classify.setAccessible(true);
        assertEquals("running",classify.invoke(service,DetectedActivity.RUNNING));
        assertEquals("walking",classify.invoke(service,DetectedActivity.WALKING));
    }
    private Field field(String name) throws Exception {
        Field f=CaptureService.class.getDeclaredField(name);f.setAccessible(true);return f;
    }
    private Location p(double metres,long time) {
        Location p=new Location("gps");p.setLongitude(metres/111195);p.setLatitude(0);
        p.setAccuracy(5);p.setTime(time);p.setSpeed(0);return p;
    }
    @Test public void activityAfterCompletedCaptureCannotDereferenceNullMode() throws Exception {
        CaptureService service=Robolectric.buildService(CaptureService.class).get();
        service.getSharedPreferences("roadprints_capture_state",0).edit().clear()
                .putBoolean("armed",true).putBoolean("active",false).apply();
        field("mode").set(service,null);
        field("automaticCapture").set(service,true);
        field("gpsStillness").set(service,true);
        Method transition=CaptureService.class.getDeclaredMethod("handleTransition",int.class,int.class);
        transition.setAccessible(true);
        transition.invoke(service,DetectedActivity.UNKNOWN,ActivityTransition.ACTIVITY_TRANSITION_ENTER);
        assertFalse(field("gpsStillness").getBoolean(service));
        service.getSharedPreferences("roadprints_capture_state",0).edit().clear().apply();
    }
    @Test public void repeatedStillCallbacksKeepOriginalCandidateDeadline() throws Exception {
        CaptureService service=Robolectric.buildService(CaptureService.class).get();
        service.getSharedPreferences("roadprints_capture_state",0).edit().clear().putBoolean("armed",true).apply();
        field("candidateMode").set(service,"walking");
        long began=System.currentTimeMillis()-240000;
        field("candidateStationarySince").setLong(service,began);
        Method transition=CaptureService.class.getDeclaredMethod("handleTransition",int.class,int.class);
        transition.setAccessible(true);
        transition.invoke(service,DetectedActivity.STILL,ActivityTransition.ACTIVITY_TRANSITION_ENTER);
        assertEquals(began,field("candidateStationarySince").getLong(service));
        service.getSharedPreferences("roadprints_capture_state",0).edit().clear().apply();
    }
    @Test public void stationaryVehicleActivityCannotSplitIntoWalking() throws Exception {
        CaptureService service=Robolectric.buildService(CaptureService.class).get();
        service.getSharedPreferences("roadprints_capture_state",0).edit().clear()
                .putBoolean("armed",true).putBoolean("active",true).apply();
        field("mode").set(service,"unknown");field("automaticCapture").setBoolean(service,true);
        field("pendingActivityMode").set(service,"walking");
        field("pendingActivitySince").setLong(service,System.currentTimeMillis()-30000);
        @SuppressWarnings("unchecked") List<Location> points=(List<Location>)field("points").get(service);
        for(int i=0;i<4;i++) points.add(p(i%2,System.currentTimeMillis()-30000+i*10000));
        Method commit=CaptureService.class.getDeclaredMethod("commitPendingActivityMode");commit.setAccessible(true);
        commit.invoke(service);
        assertNull(field("nextCaptureMode").get(service));
        assertFalse(field("captureFinishing").getBoolean(service));
        service.getSharedPreferences("roadprints_capture_state",0).edit().clear().apply();
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
    @Test public void unconfirmedVehicleCaptureUsesGpsArrivalEvidence() throws Exception {
        gpsStop("unknown",DetectedActivity.IN_VEHICLE);
    }
    @Test public void walkingLabelsDuringGpsDwellDoNotQueueANewLeg() throws Exception {
        CaptureService service=Robolectric.buildService(CaptureService.class).get();
        service.getSharedPreferences("roadprints_capture_state",0).edit().clear()
                .putBoolean("armed",true).putBoolean("active",true).apply();
        field("mode").set(service,"driving");field("automaticCapture").setBoolean(service,true);
        field("gpsStillness").setBoolean(service,true);
        long began=System.currentTimeMillis()-120000;
        field("stationarySince").setLong(service,began);
        Method transition=CaptureService.class.getDeclaredMethod("handleTransition",int.class,int.class);
        transition.setAccessible(true);
        transition.invoke(service,DetectedActivity.WALKING,ActivityTransition.ACTIVITY_TRANSITION_ENTER);
        assertNull(field("pendingActivityMode").get(service));
        assertEquals(began,field("stationarySince").getLong(service));
        service.getSharedPreferences("roadprints_capture_state",0).edit().clear().apply();
    }
    @Test public void corroboratedArrivalResolvesBoundaryBeforeConfirmationTail() throws Exception {
        CaptureService service=Robolectric.buildService(CaptureService.class).get();
        field("mode").set(service,"driving");field("automaticCapture").setBoolean(service,true);
        @SuppressWarnings("unchecked") List<Location> points=(List<Location>)field("points").get(service);
        Method observe=CaptureService.class.getDeclaredMethod("observeWalkingStillness",Location.class);observe.setAccessible(true);
        long now=System.currentTimeMillis();
        points.add(p(-300,now-400000));
        for(int i=0;i<=50;i++) {
            Location fix=p(i%3,now-350000+i*7000);fix.setAccuracy(25);
            fix.setSpeed(i>3&&i%7==0?3.8f:.15f);points.add(fix);observe.invoke(service,fix);
        }
        Method resolve=CaptureService.class.getDeclaredMethod("resolveGpsArrivalBoundary",long.class);resolve.setAccessible(true);
        resolve.invoke(service,now);
        assertEquals(1,field("stationEndIndex").getInt(service));
        assertEquals("arrival_gps_dwell",field("contextReason").get(service));
        assertEquals(now-350000,field("stationarySince").getLong(service));
        assertEquals(52,points.size()); // Raw confirmation evidence is retained.
    }
    @Test public void briefFalseWalkingStartBecomesOneVehicleLegButRealWalkIsPreserved() throws Exception {
        for(boolean realWalk:new boolean[]{false,true}) {
            CaptureService service=Robolectric.buildService(CaptureService.class).get();
            field("mode").set(service,"walking");field("automaticCapture").setBoolean(service,true);
            @SuppressWarnings("unchecked") List<Location> points=(List<Location>)field("points").get(service);
            long now=System.currentTimeMillis();
            for(int i=0;i<3;i++) {
                Location fix=p(realWalk?i*20:i,now-70000+i*10000);
                fix.setSpeed(realWalk?1.5f:.1f);points.add(fix);
            }
            for(int i=0;i<4;i++) {
                Location fix=p(100+i*100,now-40000+i*10000);fix.setSpeed(10);points.add(fix);
            }
            Method promote=CaptureService.class.getDeclaredMethod("promoteProvisionalDepartureMode",String.class);
            promote.setAccessible(true);
            assertEquals(!realWalk,promote.invoke(service,"unknown"));
            assertEquals(realWalk?"walking":"unknown",field("mode").get(service));
            assertNull(field("nextCaptureMode").get(service));assertEquals(7,points.size());
            service.getSharedPreferences("roadprints_capture_state",0).edit().clear().apply();
        }
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
