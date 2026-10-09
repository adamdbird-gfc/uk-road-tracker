package com.roadprints.capture;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class ContextMapCacheTest {
    @Test public void onlineLookupCannotBeQueuedWithoutExplicitChoice() throws Exception {
        android.content.Context app=RuntimeEnvironment.getApplication();
        app.getSharedPreferences("roadprints_capture_state",0).edit().clear().apply();
        ContextMapCache cache=new ContextMapCache(app);
        try {
            assertFalse(ContextMapCache.enabled(app));
            cache.request(51.5,-.1,()->fail("No callback without lookup"));
            java.lang.reflect.Field attempted=ContextMapCache.class.getDeclaredField("attemptedAt");attempted.setAccessible(true);
            assertEquals(0,attempted.getLong(cache));
        } finally {cache.close();}
    }
}
