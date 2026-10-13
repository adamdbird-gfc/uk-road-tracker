package com.roadprints.capture;

import android.content.Context;
import android.location.Location;
import android.location.LocationManager;
import android.os.Looper;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class SamplingRecoveryTest {
    private Field field(String name) throws Exception {
        Field f=CaptureService.class.getDeclaredField(name); f.setAccessible(true); return f;
    }
    private CaptureService service() throws Exception {
        Shadows.shadowOf(RuntimeEnvironment.getApplication()).grantPermissions("android.permission.ACCESS_FINE_LOCATION");
        CaptureService service=Robolectric.buildService(CaptureService.class).get();
        field("locationManager").set(service,service.getSystemService(Context.LOCATION_SERVICE));
        service.getSharedPreferences("roadprints_capture_state",0).edit().clear().putBoolean("armed",true).apply();
        return service;
    }
    private void refresh(CaptureService service) throws Exception {
        Method refresh=CaptureService.class.getDeclaredMethod("refreshSamplingSubscriptions");
        refresh.setAccessible(true); refresh.invoke(service);
    }
    @Test public void watchdogRestoresCandidateSubscriptionWithoutOpeningApp() throws Exception {
        CaptureService service=service();
        field("candidateMode").set(service,"walking");
        field("candidateStartedAtMs").setLong(service,System.currentTimeMillis());
        refresh(service);
        assertFalse(field("diagnosticUpdatesRegistered").getBoolean(service));
        Location location=new Location("gps");location.setLatitude(51);location.setLongitude(0);
        location.setTime(System.currentTimeMillis());location.setAccuracy(5);
        LocationManager manager=(LocationManager)field("locationManager").get(service);
        Shadows.shadowOf(manager).simulateLocation(location);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertFalse(((List<?>)field("candidatePoints").get(service)).isEmpty());
        assertEquals("walking",field("candidateMode").get(service));
        manager.removeUpdates((android.location.LocationListener)field("locationListener").get(service));
        service.getSharedPreferences("roadprints_capture_state",0).edit().clear().apply();
    }
    @Test public void watchdogRestoresArmedSamplingWhenNoCandidateExists() throws Exception {
        CaptureService service=service();refresh(service);
        assertTrue(field("diagnosticUpdatesRegistered").getBoolean(service));
        ((LocationManager)field("locationManager").get(service)).removeUpdates(
                (android.location.LocationListener)field("locationListener").get(service));
        service.getSharedPreferences("roadprints_capture_state",0).edit().clear().apply();
    }
    @Test public void watchdogKeepsActiveJourneyInCaptureSampling() throws Exception {
        CaptureService service=service();
        service.getSharedPreferences("roadprints_capture_state",0).edit().putBoolean("active",true).apply();
        field("mode").set(service,"walking");refresh(service);
        assertFalse(field("diagnosticUpdatesRegistered").getBoolean(service));
        assertTrue(CaptureService.isActive(service));
        ((LocationManager)field("locationManager").get(service)).removeUpdates(
                (android.location.LocationListener)field("locationListener").get(service));
        service.getSharedPreferences("roadprints_capture_state",0).edit().clear().apply();
    }
}
