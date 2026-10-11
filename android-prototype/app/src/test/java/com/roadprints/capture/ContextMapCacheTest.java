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
    @Test public void recreatedCacheBudgetRetainsCooldownAndEscalates() {
        android.content.Context app=RuntimeEnvironment.getApplication();
        app.getSharedPreferences("context_request_budget_v1",0).edit().clear().commit();
        long now=100000000L;
        ContextRequestBudget first=new ContextRequestBudget(app);
        assertTrue(first.reserve(now));first.failure(now,0);
        ContextRequestBudget restarted=new ContextRequestBudget(app);
        assertFalse(restarted.reserve(now+599999));
        assertTrue(restarted.reserve(now+600000));restarted.failure(now+600000,0);
        assertFalse(new ContextRequestBudget(app).reserve(now+1799999));
        assertTrue(new ContextRequestBudget(app).reserve(now+1800000));
    }
    @Test public void dailyLimitSurvivesRestartAndNewDayRestoresBudget() {
        android.content.Context app=RuntimeEnvironment.getApplication();
        app.getSharedPreferences("context_request_budget_v1",0).edit().clear().commit();
        long day=86400000L;
        for(int i=0;i<48;i++)assertTrue(new ContextRequestBudget(app).reserve(day+i*60000L));
        assertFalse(new ContextRequestBudget(app).reserve(day+48*60000L));
        assertTrue(new ContextRequestBudget(app).reserve(day*2));
    }
}
