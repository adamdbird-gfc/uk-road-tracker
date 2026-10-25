package com.roadprints.capture;

import android.app.*;
import android.content.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import java.lang.reflect.Field;
import java.time.Instant;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class DiagnosticNotificationLifecycleTest {
    Context app;NotificationManager manager;
    @Before public void setup(){app=RuntimeEnvironment.getApplication();app.getSharedPreferences(DiagnosticNotifications.PREFS,0).edit().clear().commit();CrashReporter.clear(app);manager=(NotificationManager)app.getSystemService(Context.NOTIFICATION_SERVICE);manager.cancelAll();}
    JSONObject failure()throws Exception{return new JSONObject().put("journey_id","historical-walk").put("mode","walking")
        .put("last_match_attempt",new JSONObject().put("finished_at_utc","2026-10-24T04:23:56Z"))
        .put("route_geometry",new JSONObject().put("coordinates",new JSONArray("[[0,0],[0.001,0]]")));}
    void record(JSONObject journey){CrashReporter.recordMatchFailure(app,journey,new CaptureEvidenceFailure("This walking recording has a moving GPS gap or unreliable route evidence."),"capture_saved","foot_matching");}
    void restart()throws Exception {
        Field installed=CrashReporter.class.getDeclaredField("installed");installed.setAccessible(true);
        boolean old=installed.getBoolean(null);Thread.UncaughtExceptionHandler handler=Thread.getDefaultUncaughtExceptionHandler();
        try{installed.setBoolean(null,false);CrashReporter.install(app);}finally{installed.setBoolean(null,old);Thread.setDefaultUncaughtExceptionHandler(handler);}
    }
    JSONObject receipt()throws Exception{return new JSONObject(DiagnosticNotifications.report(app));}
    @Test public void viewedHistoricalFailureCannotReappearOnIdleBackgroundProcessRestarts()throws Exception {
        record(failure());assertEquals(1,Shadows.shadowOf(manager).size());String id=receipt().getString("event_id");
        CrashReporter.markDiagnosticReportsViewed(app);assertEquals(0,Shadows.shadowOf(manager).size());
        for(int i=0;i<5;i++){restart();record(failure().put("title","archive label "+i));assertEquals(0,Shadows.shadowOf(manager).size());}
        assertEquals(id,receipt().getString("event_id"));assertEquals("viewed",receipt().getString("delivery_state"));
        assertEquals(1,new JSONArray(app.getSharedPreferences("roadprints_debug_reports",0).getString("match_failure_reports","[]")).length());
    }
    @Test public void oldReportsAndLegacyBooleanNeverBecomeNewNotificationEvents()throws Exception {
        app.getSharedPreferences("roadprints_debug_reports",0).edit().putBoolean("debug_notification_active",false)
            .putString("match_failure_reports",new JSONArray().put("Historical match failure: 2026-10-24T04:23:56Z").toString()).commit();
        restart();assertEquals(0,Shadows.shadowOf(manager).size());assertTrue(CrashReporter.getMatchFailureReports(app).contains("2026-10-24"));
        assertEquals("",DiagnosticNotifications.report(app));
    }
    @Test public void newFailureCarriesCreatedTriggerWorkerAndOriginalTime()throws Exception {
        record(failure());Notification n=Shadows.shadowOf(manager).getNotification(44);assertNotNull(n);
        assertEquals("2026-10-24T04:23:56Z",n.extras.getString("roadprints_original_failure_at_utc"));
        assertEquals("capture_saved",n.extras.getString("roadprints_trigger"));assertEquals("foot_matching",n.extras.getString("roadprints_worker"));
        assertEquals(n.when,Instant.parse(n.extras.getString("roadprints_notification_created_at_utc")).toEpochMilli());
        assertTrue(CrashReporter.getDiagnosticReports(app).contains("notification_created_at_utc"));
        String id=receipt().getString("event_id");record(failure().put("journey_id","new-walk"));assertNotEquals(id,receipt().getString("event_id"));
    }
    @Test public void dismissalSurvivesRestartAndOldDismissalCannotAcknowledgeNewError()throws Exception {
        record(failure());String id=receipt().getString("event_id");manager.cancelAll();
        new DiagnosticNotificationReceiver().onReceive(app,new Intent().putExtra("event_id",id));
        restart();record(failure());assertEquals(0,Shadows.shadowOf(manager).size());assertEquals("dismissed",receipt().getString("delivery_state"));
        record(failure().put("journey_id","other"));String next=receipt().getString("event_id");
        new DiagnosticNotificationReceiver().onReceive(app,new Intent().putExtra("event_id",id));
        assertEquals(next,receipt().getString("event_id"));assertEquals("posted",receipt().getString("delivery_state"));
    }
    @Test public void deniedDeliveryWaitsForPermissionThenPostsOnlyOnce()throws Exception {
        Shadows.shadowOf(manager).setNotificationsEnabled(false);record(failure());assertEquals("pending",receipt().getString("delivery_state"));
        restart();assertEquals(0,Shadows.shadowOf(manager).size());Shadows.shadowOf(manager).setNotificationsEnabled(true);
        restart();assertEquals(1,Shadows.shadowOf(manager).size());assertEquals("application_startup_pending",receipt().getString("delivery_trigger"));
        manager.cancelAll();restart();assertEquals(0,Shadows.shadowOf(manager).size());
    }
    @Test public void deliveryClaimCrashWindowDoesNotRegenerateHistoricalAlert()throws Exception {
        JSONObject event=DiagnosticNotifications.event(app,"test","sig","2026-10-24T04:23:56Z","test","worker");
        event.put("delivery_state","claimed");app.getSharedPreferences(DiagnosticNotifications.PREFS,0).edit().putString("last_event",event.toString()).commit();
        restart();assertEquals(0,Shadows.shadowOf(manager).size());
    }
    @Test public void clearRetainsAcknowledgedIdentityButSuccessfulRecoveryAllowsNewFailure()throws Exception {
        record(failure());String id=receipt().getString("event_id");CrashReporter.clear(app);record(failure());
        assertEquals(0,Shadows.shadowOf(manager).size());assertEquals("",CrashReporter.getDiagnosticReports(app));
        DiagnosticNotifications.resolved(app,"historical-walk");record(failure());assertNotEquals(id,receipt().getString("event_id"));assertEquals(1,Shadows.shadowOf(manager).size());
    }
    @Test public void recoveryCancelsOnlyItsOwnAlertAndPersistsResolvedState()throws Exception {
        record(failure());DiagnosticNotifications.resolved(app,"different-journey");assertEquals(1,Shadows.shadowOf(manager).size());
        DiagnosticNotifications.resolved(app,"historical-walk");assertEquals(0,Shadows.shadowOf(manager).size());assertEquals("resolved",receipt().getString("delivery_state"));
        restart();assertEquals(0,Shadows.shadowOf(manager).size());assertTrue(CrashReporter.getMatchFailureReports(app).contains("historical-walk"));
    }
    @Test public void concurrentRediscoveryPostsOneEvent()throws Exception {
        JSONObject journey=failure();java.util.concurrent.ExecutorService worker=java.util.concurrent.Executors.newFixedThreadPool(4);
        try{java.util.List<java.util.concurrent.Future<?>> tasks=new java.util.ArrayList<>();for(int i=0;i<12;i++)tasks.add(worker.submit(()->record(journey)));for(java.util.concurrent.Future<?> f:tasks)f.get();}
        finally{worker.shutdownNow();}
        assertEquals(1,Shadows.shadowOf(manager).size());assertEquals(1,new JSONArray(app.getSharedPreferences("roadprints_debug_reports",0).getString("match_failure_reports","[]")).length());
    }
}
