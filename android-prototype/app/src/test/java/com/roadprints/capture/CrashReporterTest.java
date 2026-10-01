package com.roadprints.capture;

import android.content.Context;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class CrashReporterTest {
    private Context app;

    @Before public void setUp() {
        app = RuntimeEnvironment.getApplication();
        CrashReporter.clear(app);
    }

    @Test public void storesRecentCrashDetailsLocallyAndCanClearThem() {
        for (int index = 0; index < 6; index++) {
            CrashReporter.record(app, new Thread("matcher-worker"),
                    new IllegalStateException("matcher failure " + index));
        }

        String reports = CrashReporter.getReports(app);
        assertTrue(reports.contains("Roadprints crash report"));
        assertTrue(reports.contains("App version: " + BuildConfig.VERSION_NAME));
        assertTrue(reports.contains("Device:"));
        assertTrue(reports.contains("Thread: matcher-worker"));
        assertTrue(reports.contains("matcher failure 5"));
        assertTrue(reports.contains("matcher failure 1"));
        assertFalse(reports.contains("matcher failure 0"));
        assertTrue(CrashReporter.hasReports(app));

        CrashReporter.clear(app);
        assertFalse(CrashReporter.hasReports(app));
        assertEquals("", CrashReporter.getReports(app));
    }
}
