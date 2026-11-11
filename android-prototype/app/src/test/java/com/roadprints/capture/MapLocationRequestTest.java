package com.roadprints.capture;

import android.location.Location;
import android.os.Looper;
import com.google.android.gms.tasks.TaskCompletionSource;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28,application=android.app.Application.class)
public class MapLocationRequestTest {
    @Test public void cancellationIgnoresOldFixAndAllowsOneNewRequest(){
        MapLocationRequest request=new MapLocationRequest();
        TaskCompletionSource<Location> first=new TaskCompletionSource<>(), second=new TaskCompletionSource<>();
        List<Location> delivered=new ArrayList<>();
        request.start(token->first.getTask(),delivered::add);
        request.start(token->{throw new AssertionError("Second request started");},delivered::add);
        request.cancel();
        request.start(token->second.getTask(),delivered::add);
        first.setResult(new Location("old"));
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertTrue(delivered.isEmpty());
        Location fix=new Location("current");
        second.setResult(fix);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals(java.util.Collections.singletonList(fix),delivered);
    }

    @Test public void deniedProviderAndFailedFixLeaveTheControlRetryable(){
        MapLocationRequest request=new MapLocationRequest();
        List<Location> delivered=new ArrayList<>();
        request.start(token->{throw new SecurityException();},delivered::add);
        TaskCompletionSource<Location> failed=new TaskCompletionSource<>();
        request.start(token->failed.getTask(),delivered::add);
        failed.setException(new IllegalStateException());
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals(java.util.Arrays.asList(null,null),delivered);
    }

    @Test public void accuracyRadiusScalesWithZoomAndLatitude(){
        float equator=MapLocationControl.Indicator.radiusPixels(0,1_000_000,400);
        assertEquals(10,equator,.05);
        assertEquals(equator*2,MapLocationControl.Indicator.radiusPixels(60,1_000_000,400),.01);
        assertEquals(equator*2,MapLocationControl.Indicator.radiusPixels(0,2_000_000,400),.01);
    }
}
