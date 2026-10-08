package com.roadprints.capture;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Button;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class OnboardingTrackingTest {
    private Context app;

    @Before public void setup() {
        app = RuntimeEnvironment.getApplication();
        app.getSharedPreferences("roadprints_capture_state", Context.MODE_PRIVATE).edit().clear().commit();
        Shadows.shadowOf(RuntimeEnvironment.getApplication()).denyPermissions(
                Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACTIVITY_RECOGNITION, Manifest.permission.POST_NOTIFICATIONS);
    }

    private ActivityController<MainActivity> onboarding() {
        return Robolectric.buildActivity(MainActivity.class, new Intent(app, MainActivity.class)
                .putExtra("tracking_settings_screen", true)
                .putExtra("onboarding_enable_tracking", true)).create();
    }

    private void grantRequired() {
        Shadows.shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(
                Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACTIVITY_RECOGNITION);
    }

    private void permissionResult(MainActivity activity) {
        activity.onRequestPermissionsResult(42, new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACTIVITY_RECOGNITION, Manifest.permission.POST_NOTIFICATIONS},
                new int[]{PackageManager.PERMISSION_GRANTED, PackageManager.PERMISSION_GRANTED,
                        PackageManager.PERMISSION_DENIED});
    }

    private Intent nextService() {
        return Shadows.shadowOf(RuntimeEnvironment.getApplication()).getNextStartedService();
    }

    @Test public void permissionApprovalStartsTrackingWithoutAnotherTapEvenWithoutNotifications() {
        ActivityController<MainActivity> controller = onboarding();
        assertNull(nextService());
        grantRequired();
        permissionResult(controller.get());
        assertEquals(CaptureService.ACTION_ARM, nextService().getAction());
        assertNull(nextService());
        controller.destroy();
    }

    @Test public void alreadyGrantedPermissionsStartTrackingImmediately() {
        grantRequired();
        ActivityController<MainActivity> controller = onboarding();
        assertEquals(CaptureService.ACTION_ARM, nextService().getAction());
        controller.destroy();
    }

    @Test public void deniedActivityPermissionLeavesTrackingOff() {
        ActivityController<MainActivity> controller = onboarding();
        Shadows.shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION);
        permissionResult(controller.get());
        assertNull(nextService());
        assertFalse(CaptureService.isArmed(app));
        controller.destroy();
    }

    @Test public void approximateLocationOnlyLeavesTrackingOff() {
        ActivityController<MainActivity> controller = onboarding();
        Shadows.shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(
                Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACTIVITY_RECOGNITION);
        permissionResult(controller.get());
        assertNull(nextService());
        controller.destroy();
    }

    @Test public void permissionContinuationSurvivesActivityRecreation() {
        ActivityController<MainActivity> controller = onboarding();
        Bundle saved = new Bundle();
        controller.saveInstanceState(saved).destroy();
        controller = Robolectric.buildActivity(MainActivity.class,
                new Intent(app, MainActivity.class).putExtra("tracking_settings_screen", true)).create(saved);
        grantRequired();
        permissionResult(controller.get());
        assertEquals(CaptureService.ACTION_ARM, nextService().getAction());
        controller.destroy();
    }

    @Test public void repeatedCallbackDoesNotDisableTrackingAndUtilitiesCanStillDisableIt() throws Exception {
        ActivityController<MainActivity> controller = onboarding();
        grantRequired();
        permissionResult(controller.get());
        assertEquals(CaptureService.ACTION_ARM, nextService().getAction());
        permissionResult(controller.get());
        assertNull(nextService());
        java.lang.reflect.Field field = MainActivity.class.getDeclaredField("trackingButton");
        field.setAccessible(true);
        ((Button) field.get(controller.get())).performClick();
        assertEquals(CaptureService.ACTION_DISARM, nextService().getAction());
        controller.destroy();
    }

    @Test public void openingUtilitiesDoesNotEnableTrackingWithoutUserAction() {
        grantRequired();
        ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class,
                new Intent(app, MainActivity.class).putExtra("tracking_settings_screen", true)).create();
        assertNull(nextService());
        controller.destroy();
    }
}
