package com.roadprints.capture;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.SystemClock;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.crashlytics.FirebaseCrashlytics;
import com.google.firebase.perf.FirebasePerformance;
import com.google.firebase.perf.metrics.Trace;
import org.json.JSONObject;
import java.util.Arrays;
import java.util.HashSet;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Set;

/** Deliberately small telemetry contract. Never pass journeys, free text or GPS to an SDK. */
final class BetaMeasurement {
    private static final String PREFS = "roadprints_measurement";
    private static final Set<String> EVENTS = new HashSet<>(Arrays.asList(
            "capture_started", "capture_saved", "capture_save_failed", "capture_start_failed",
            "match_started", "match_completed", "match_failed", "import_started", "import_completed",
            "import_failed", "screen_view", "measurement_enabled", "diagnostic_test"));
    private static final Set<String> MODES = new HashSet<>(Arrays.asList(
            "driving", "walking", "running", "cycling", "bus", "train", "flight", "ferry", "unknown"));
    private static final Set<String> SCREENS = new HashSet<>(Arrays.asList(
            "Onboarding", "Main", "Map", "Progress", "Achievements", "Collections", "JourneyList",
            "JourneyReplay", "JourneyMapEditor", "Utilities", "Settings", "Permissions",
            "DataManagement", "TrackingSettings", "Debug", "TimelineImport", "Growing", "MeasurementSettings"));
    private BetaMeasurement() {}

    interface Backend {
        void configure(boolean usage, boolean diagnostics, boolean internal);
        void event(String name, Bundle parameters);
        void failure(String category);
        void reset();
    }
    static volatile Backend testBackend;
    private static volatile Backend backend;
    private static Backend backend(Context context) {
        if (testBackend != null) return testBackend;
        if (backend == null) backend = new FirebaseBackend(context.getApplicationContext());
        return backend;
    }
    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
    static boolean usageEnabled(Context context) { return prefs(context).getBoolean("usage", false); }
    static boolean diagnosticsEnabled(Context context) { return prefs(context).getBoolean("diagnostics", false); }
    static boolean internalDevice(Context context) { return prefs(context).getBoolean("internal", false); }

    static synchronized void saveChoices(Context context, boolean usage, boolean diagnostics, boolean internal) {
        boolean wasEnabled = usageEnabled(context);
        prefs(context).edit().putBoolean("usage", usage).putBoolean("diagnostics", diagnostics)
                .putBoolean("internal", internal).putBoolean("prompt_seen", true).commit();
        try {
            backend(context).configure(usage, diagnostics, internal);
            if (wasEnabled && !usage) backend(context).reset();
        } catch (RuntimeException ignored) { /* Measurement must never block journeys. */ }
        if (usage && !wasEnabled) event(context, "measurement_enabled", null);
    }

    static void install(Application application) {
        // Firebase SDK collection starts disabled in the manifest, before Application.onCreate.
        if (usageEnabled(application) || diagnosticsEnabled(application)) {
            try { backend(application).configure(usageEnabled(application), diagnosticsEnabled(application),
                    internalDevice(application)); } catch (RuntimeException ignored) { }
        }
        application.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            public void onActivityResumed(Activity activity) {
                String screen = activity.getClass().getSimpleName().replace("Activity", "");
                if (SCREENS.contains(screen)) {
                    Bundle params = new Bundle(); params.putString("screen_name", screen);
                    event(activity, "screen_view", params);
                }
                if (activity instanceof OnboardingActivity && !prefs(activity).getBoolean("prompt_seen", false)) {
                    prefs(activity).edit().putBoolean("prompt_seen", true).apply();
                    activity.startActivity(new Intent(activity, MeasurementSettingsActivity.class));
                }
            }
            public void onActivityCreated(Activity a, Bundle b) { }
            public void onActivityStarted(Activity a) { }
            public void onActivityPaused(Activity a) { }
            public void onActivityStopped(Activity a) { }
            public void onActivitySaveInstanceState(Activity a, Bundle b) { }
            public void onActivityDestroyed(Activity a) { }
        });
    }

    static synchronized void event(Context context, String name, Bundle parameters) {
        if (!usageEnabled(context) || !EVENTS.contains(name)) return;
        Bundle safe = new Bundle();
        if (parameters != null) {
            // Fixed numeric fields and bounded enums only; unknown fields are dropped.
            for (String key : Arrays.asList("duration_ms", "gps_points", "added", "skipped", "invalid")) {
                Object value = parameters.get(key);
                if (value instanceof Number) safe.putLong(key, Math.max(0, ((Number) value).longValue()));
            }
            copyEnum(parameters, safe, "mode", MODES);
            copyEnum(parameters, safe, "source", new HashSet<>(Arrays.asList("capture", "timeline", "other")));
            copyEnum(parameters, safe, "capture_type", new HashSet<>(Arrays.asList("automatic", "manual")));
            copyEnum(parameters, safe, "attempt_type", new HashSet<>(Arrays.asList("match", "rematch")));
            copyEnum(parameters, safe, "error_category", new HashSet<>(Arrays.asList(
                    "timeout", "rate_limit", "server", "validation", "network", "permission", "other")));
            copyEnum(parameters, safe, "screen_name", SCREENS);
        }
        safe.putString("test_cohort", internalDevice(context) ? "internal" : "beta");
        safe.putString("event_schema", "1");
        try { backend(context).event(name, safe); } catch (RuntimeException ignored) { }
    }
    private static void copyEnum(Bundle from, Bundle to, String key, Set<String> values) {
        Object value = from.get(key);
        if (value instanceof String && values.contains(value)) to.putString(key, (String) value);
    }
    static Bundle journeyParameters(JSONObject journey) {
        Bundle params = new Bundle();
        params.putString("mode", normalizedMode(journey == null ? null : journey.optString("mode")));
        JSONObject source = journey == null ? null : journey.optJSONObject("source");
        String type = source == null ? "" : source.optString("type");
        params.putString("source", "android_activity_capture".equals(type) ? "capture"
                : "timeline_import".equals(type) ? "timeline" : "other");
        return params;
    }
    static String normalizedMode(String mode) {
        if ("bicycle".equals(mode)) return "cycling";
        if ("transit".equals(mode)) return "bus";
        if ("flying".equals(mode)) return "flight";
        return MODES.contains(mode) ? mode : "unknown";
    }
    static String errorCategory(Throwable error) {
        if (error instanceof SecurityException) return "permission";
        String message = error == null || error.getMessage() == null ? "" : error.getMessage().toLowerCase(Locale.ROOT);
        if (error instanceof java.net.SocketTimeoutException || message.contains("timeout") || message.contains("time limit")) return "timeout";
        if (message.contains("http 429") || message.contains("rate-limit")) return "rate_limit";
        if (message.matches("(?s).*http 5[0-9][0-9].*")) return "server";
        if (message.contains("http 422") || message.contains("gps") || message.contains("evidence")) return "validation";
        if (error instanceof java.io.IOException) return "network";
        return "other";
    }
    static void reportFailure(Context context, Throwable error) {
        if (!diagnosticsEnabled(context)) return;
        try { backend(context).failure(errorCategory(error)); } catch (RuntimeException ignored) { }
    }
    static void sendDiagnosticTest(Context context) {
        if (!diagnosticsEnabled(context)) return;
        try { backend(context).failure("diagnostic_test"); } catch (RuntimeException ignored) { }
        event(context, "diagnostic_test", null);
    }
    static Operation operation(Context context, String name) { return new Operation(context, name); }
    static final class Operation implements AutoCloseable {
        final long started = SystemClock.elapsedRealtime();
        private Trace trace;
        private final Context context;
        Operation(Context context, String name) {
            this.context = context.getApplicationContext();
            if (testBackend != null || !diagnosticsEnabled(context)
                    || !Arrays.asList("journey_match", "timeline_import", "map_data_load", "progress_data_load").contains(name)) return;
            try {
                trace = FirebasePerformance.getInstance().newTrace(name);
                trace.putAttribute("test_cohort", internalDevice(context) ? "internal" : "beta");
                trace.start();
            } catch (RuntimeException ignored) { trace = null; }
        }
        long duration() { return Math.max(0, SystemClock.elapsedRealtime() - started); }
        public void close() {
            if (trace != null) {
                try { trace.stop(); } catch (RuntimeException ignored) { }
                trace = null;
            }
        }
    }
    private static final class FirebaseBackend implements Backend {
        private final FirebaseAnalytics analytics;
        FirebaseBackend(Context context) { analytics = FirebaseAnalytics.getInstance(context); }
        public void configure(boolean usage, boolean diagnostics, boolean internal) {
            EnumMap<FirebaseAnalytics.ConsentType, FirebaseAnalytics.ConsentStatus> consent =
                    new EnumMap<>(FirebaseAnalytics.ConsentType.class);
            for (FirebaseAnalytics.ConsentType type : FirebaseAnalytics.ConsentType.values())
                consent.put(type, FirebaseAnalytics.ConsentStatus.DENIED);
            consent.put(FirebaseAnalytics.ConsentType.ANALYTICS_STORAGE,
                    usage ? FirebaseAnalytics.ConsentStatus.GRANTED : FirebaseAnalytics.ConsentStatus.DENIED);
            analytics.setConsent(consent);
            analytics.setAnalyticsCollectionEnabled(usage);
            if (usage) analytics.setUserProperty("test_cohort", internal ? "internal" : "beta");
            FirebaseCrashlytics crash = FirebaseCrashlytics.getInstance();
            crash.setCrashlyticsCollectionEnabled(diagnostics);
            if (!diagnostics) crash.deleteUnsentReports();
            else crash.setCustomKey("test_cohort", internal ? "internal" : "beta");
            FirebasePerformance.getInstance().setPerformanceCollectionEnabled(diagnostics);
        }
        public void event(String name, Bundle params) { analytics.logEvent(name, params); }
        public void failure(String category) {
            // Original messages and causes can contain request bodies or personal journey text.
            FirebaseCrashlytics.getInstance().recordException(new IllegalStateException("Roadprints operation failed: " + category));
        }
        public void reset() { analytics.resetAnalyticsData(); }
    }
}
