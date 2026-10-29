package com.roadprints.capture;

import android.app.Application;
import android.content.Context;
import android.os.Bundle;
import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28, application=Application.class)
public class BetaMeasurementTest {
    private Context app;
    private RecordingBackend backend;
    static class RecordingBackend implements BetaMeasurement.Backend {
        boolean usage, diagnostics, internal, reset;
        final List<String> events = new ArrayList<>();
        final List<Bundle> parameters = new ArrayList<>();
        final List<String> failures = new ArrayList<>();
        public void configure(boolean u, boolean d, boolean i) { usage=u; diagnostics=d; internal=i; }
        public void event(String name, Bundle values) { events.add(name); parameters.add(new Bundle(values)); }
        public void failure(String category) { failures.add(category); }
        public void reset() { reset=true; }
    }
    @Before public void setup() {
        app=RuntimeEnvironment.getApplication();
        app.getSharedPreferences("roadprints_measurement",Context.MODE_PRIVATE).edit().clear().commit();
        backend=new RecordingBackend(); BetaMeasurement.testBackend=backend;
    }
    @After public void cleanup() { BetaMeasurement.testBackend=null; }
    @Test public void defaultDenialDoesNotSendEventsOrFailures() {
        BetaMeasurement.event(app,"capture_started",null);
        BetaMeasurement.reportFailure(app,new RuntimeException("private route"));
        assertTrue(backend.events.isEmpty()); assertTrue(backend.failures.isEmpty());
        assertFalse(BetaMeasurement.usageEnabled(app)); assertFalse(BetaMeasurement.diagnosticsEnabled(app));
    }
    @Test public void diagnosticsCanBeEnabledWithoutUsageAnalytics() {
        BetaMeasurement.saveChoices(app,false,true,true);
        BetaMeasurement.event(app,"capture_saved",null);
        BetaMeasurement.reportFailure(app,new java.net.SocketTimeoutException("private route"));
        assertFalse(backend.usage); assertTrue(backend.diagnostics); assertTrue(backend.internal);
        assertTrue(backend.events.isEmpty()); assertEquals("timeout",backend.failures.get(0));
    }
    @Test public void customPayloadDropsPrivateTextCoordinatesAndInvalidEnums() throws Exception {
        BetaMeasurement.saveChoices(app,true,false,true);
        JSONObject journey=new JSONObject().put("mode","driving")
                .put("title","Home to school").put("journey_id","personal-id")
                .put("source",new JSONObject().put("type","android_activity_capture").put("filename","personal.json"));
        Bundle input=BetaMeasurement.journeyParameters(journey);
        input.putString("title","Home to school"); input.putDouble("lat",51.4);
        input.putString("error_category","GPS 51.4,0.3"); input.putLong("duration_ms",250);
        BetaMeasurement.event(app,"match_completed",input);
        Bundle sent=backend.parameters.get(1);
        assertEquals("driving",sent.getString("mode")); assertEquals("capture",sent.getString("source"));
        assertEquals("internal",sent.getString("test_cohort")); assertEquals(250,sent.getLong("duration_ms"));
        assertFalse(sent.containsKey("title")); assertFalse(sent.containsKey("lat"));
        assertFalse(sent.containsKey("journey_id")); assertFalse(sent.containsKey("error_category"));
        BetaMeasurement.event(app,"Home to school",input); assertEquals(2,backend.events.size());
    }
    @Test public void revokingConsentResetsUsageAndBlocksLaterSending() {
        BetaMeasurement.saveChoices(app,true,true,false);
        BetaMeasurement.saveChoices(app,false,false,false);
        BetaMeasurement.event(app,"match_failed",null); BetaMeasurement.reportFailure(app,new RuntimeException());
        assertTrue(backend.reset); assertFalse(backend.usage); assertFalse(backend.diagnostics);
        assertEquals(1,backend.events.size()); assertTrue(backend.failures.isEmpty());
    }
    @Test public void sdkFailureCannotBreakSavingConsentOrRecordingJourneys() {
        BetaMeasurement.testBackend=new RecordingBackend() {
            public void configure(boolean u, boolean d, boolean i) { throw new IllegalStateException(); }
            public void event(String name, Bundle values) { throw new IllegalStateException(); }
            public void failure(String category) { throw new IllegalStateException(); }
        };
        BetaMeasurement.saveChoices(app,true,true,false);
        BetaMeasurement.event(app,"capture_saved",null);
        BetaMeasurement.reportFailure(app,new RuntimeException());
        assertTrue(BetaMeasurement.usageEnabled(app));
    }
    @Test public void privacyChoiceAppearsOnceWithoutEnablingCollection() {
        BetaMeasurement.install((Application)app);
        org.robolectric.android.controller.ActivityController<OnboardingActivity> activity=
                Robolectric.buildActivity(OnboardingActivity.class).setup();
        assertEquals(MeasurementSettingsActivity.class.getName(),Shadows.shadowOf(activity.get()).getNextStartedActivity().getComponent().getClassName());
        activity.pause().resume(); assertNull(Shadows.shadowOf(activity.get()).getNextStartedActivity());
        assertTrue(backend.events.isEmpty()); activity.pause().stop().destroy();
    }
}
