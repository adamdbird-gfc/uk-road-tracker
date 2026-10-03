package com.roadprints.capture;

import android.content.Context;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.json.JSONObject;
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
    @Test public void storesJourneyMatchFailureWithoutRouteCoordinatesAndCanClearIt() throws Exception {
        JSONObject journey = new JSONObject()
                .put("journey_id", "journey-114")
                .put("title", "Gravesend Station - Home")
                .put("mode", "driving")
                .put("source", new JSONObject().put("type", "timeline_import"))
                .put("error_summary", "HTTP 422: NoMatch")
                .put("last_match_attempt", new JSONObject()
                        .put("started_at_utc", "2026-10-02T18:46:00Z")
                        .put("endpoint", "/match")
                        .put("source_points", 114)
                        .put("submitted_points", 114)
                        .put("points_reduced", false))
                .put("route_geometry", new JSONObject().put("coordinates", "must not be copied"));
        CrashReporter.recordMatchFailure(app, journey, new IllegalStateException("HTTP 422: NoMatch"));

        String reports = CrashReporter.getDiagnosticReports(app);
        assertTrue(reports.contains("Roadprints journey match failure"));
        assertTrue(reports.contains("Journey title: Gravesend Station - Home"));
        assertTrue(reports.contains("Matcher endpoint: /match"));
        assertTrue(reports.contains("GPS points: 114"));
        assertTrue(reports.contains("HTTP 422: NoMatch"));
        assertFalse(reports.contains("must not be copied"));
        assertTrue(CrashReporter.hasReports(app));

        CrashReporter.clear(app);
        assertFalse(CrashReporter.hasReports(app));
        assertEquals("", CrashReporter.getDiagnosticReports(app));
    }

}
