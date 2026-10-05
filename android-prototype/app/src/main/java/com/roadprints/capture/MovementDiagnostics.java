package com.roadprints.capture;

import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.Build;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** Opt-in, local-only movement diagnostics with a bounded collection window. */
final class MovementDiagnostics {
    private static final String PREFS = "roadprints_movement_diagnostics";
    private static final String ENABLED = "enabled";
    private static final String UNTIL = "until";
    private static final String RETAIN_UNTIL = "retain_until";
    private static final String TRUNCATED = "truncated";
    private static final long COLLECTION_WINDOW_MS = 24L * 60L * 60L * 1000L;
    private static final long RETENTION_AFTER_WINDOW_MS = 7L * 24L * 60L * 60L * 1000L;
    private static final long MAX_LOG_BYTES = 4L * 1024L * 1024L;
    private static final Object LOCK = new Object();
    private static final DateTimeFormatter LOCAL_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss XXX");

    private MovementDiagnostics() {}

    static boolean start(Context context) {
        Context app = context.getApplicationContext();
        long now = System.currentTimeMillis();
        SharedPreferences prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        synchronized (LOCK) {
            File file = logFile(app);
            if (file.exists()) file.delete();
            prefs.edit().putBoolean(ENABLED, true)
                    .putLong(UNTIL, now + COLLECTION_WINDOW_MS)
                    .putLong(RETAIN_UNTIL, now + COLLECTION_WINDOW_MS + RETENTION_AFTER_WINDOW_MS)
                    .putBoolean(TRUNCATED, false).apply();
            appendRaw(app, event("diagnostics_started",
                    "Collecting movement data for up to 24 hours. Stored locally on this device."));
        }
        return true;
    }

    static void stop(Context context, String reason) {
        Context app = context.getApplicationContext();
        synchronized (LOCK) {
            SharedPreferences prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            boolean wasEnabled = prefs.getBoolean(ENABLED, false);
            prefs.edit().putBoolean(ENABLED, false)
                    .putLong(RETAIN_UNTIL, System.currentTimeMillis() + RETENTION_AFTER_WINDOW_MS)
                    .apply();
            if (wasEnabled) appendRaw(app, event("diagnostics_stopped", reason));
        }
    }

    static boolean isRunning(Context context) {
        Context app = context.getApplicationContext();
        SharedPreferences prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        synchronized (LOCK) {
            if (!prefs.getBoolean(ENABLED, false)) return false;
            long until = prefs.getLong(UNTIL, 0L);
            if (System.currentTimeMillis() < until) return true;
            prefs.edit().putBoolean(ENABLED, false)
                    .putLong(RETAIN_UNTIL, until + RETENTION_AFTER_WINDOW_MS)
                    .apply();
            appendRaw(app, event("diagnostics_stopped", "The 24-hour collection window ended."));
            return false;
        }
    }

    static long until(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getLong(UNTIL, 0L);
    }

    static String status(Context context) {
        if (isRunning(context)) {
            return "Recording locally until " + localTime(until(context)) + ".";
        }
        return logFile(context.getApplicationContext()).exists()
                ? "Not recording. A recent movement log is available to review and copy."
                : "Not recording. No movement log is available.";
    }

    static void recordEvent(Context context, String name, String detail) {
        Context app = context.getApplicationContext();
        if (!isRunning(app)) return;
        synchronized (LOCK) {
            appendRaw(app, event(name, detail));
        }
    }

    static void recordActivity(Context context, String activity, String transition, boolean armed,
                               boolean active, String mode) {
        try {
            JSONObject item = event("activity_transition", activity + " " + transition);
            item.put("activity", activity);
            item.put("transition", transition);
            item.put("tracking_armed", armed);
            item.put("journey_active", active);
            if (mode != null) item.put("journey_mode", mode);
            appendIfRunning(context, item);
        } catch (Exception ignored) {}
    }

    static void recordLocation(Context context, Location location, String state) {
        if (location == null || !isRunning(context)) return;
        try {
            JSONObject item = event("location_sample", state);
            item.put("latitude", location.getLatitude());
            item.put("longitude", location.getLongitude());
            item.put("accuracy_m", location.hasAccuracy() ? location.getAccuracy() : JSONObject.NULL);
            item.put("provider", location.getProvider() == null ? JSONObject.NULL : location.getProvider());
            item.put("location_time_utc", Instant.ofEpochMilli(location.getTime()).toString());
            if (location.hasSpeed()) item.put("speed_mps", location.getSpeed());
            if (location.hasBearing()) item.put("bearing_degrees", location.getBearing());
            appendIfRunning(context, item);
        } catch (Exception ignored) {}
    }

    static String getReport(Context context) {
        Context app = context.getApplicationContext();
        SharedPreferences prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!isRunning(app)) {
            long retainUntil = prefs.getLong(RETAIN_UNTIL, 0L);
            if (retainUntil > 0 && System.currentTimeMillis() >= retainUntil) clear(app);
        }
        File file = logFile(app);
        if (!file.exists()) return "";
        StringBuilder report = new StringBuilder(
                "Roadprints movement diagnostics (precise location; stored locally only)\n");
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) report.append(line).append('\n');
        } catch (Exception error) {
            return "Could not read movement diagnostics: " + error.getClass().getSimpleName();
        }
        if (prefs.getBoolean(TRUNCATED, false)) {
            report.append("\nLog reached its 4 MiB safety limit; later samples were omitted.\n");
        }
        return report.toString();
    }

    static void clear(Context context) {
        Context app = context.getApplicationContext();
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean(ENABLED, false).remove(UNTIL).remove(RETAIN_UNTIL)
                .putBoolean(TRUNCATED, false).apply();
        File file = logFile(app);
        if (file.exists()) file.delete();
    }

    /** Stream a complete snapshot without clipboard limits or a large String. */
    static void writeReport(Context context, OutputStream output) throws IOException {
        Context app = context.getApplicationContext();
        File snapshot = File.createTempFile("movement-export-", ".jsonl", app.getCacheDir());
        boolean truncated;
        try {
          synchronized (LOCK) {
            SharedPreferences prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            if (!isRunning(app)) {
                long retainUntil = prefs.getLong(RETAIN_UNTIL, 0L);
                if (retainUntil > 0 && System.currentTimeMillis() >= retainUntil) clear(app);
            }
            File file = logFile(app);
            if (!file.exists()) throw new IOException("No movement log is available.");
            truncated = prefs.getBoolean(TRUNCATED, false);
            try (FileInputStream input = new FileInputStream(file);
                 FileOutputStream copy = new FileOutputStream(snapshot)) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) copy.write(buffer, 0, count);
            }
          }
            // A document provider can write to remote storage. Release the log
            // lock before writing there so capture can continue uninterrupted.
            output.write(("Roadprints movement diagnostics (precise location; stored locally only)\n")
                    .getBytes(StandardCharsets.UTF_8));
            long copiedBytes = 0;
            try (FileInputStream input = new FileInputStream(snapshot)) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    output.write(buffer, 0, count);
                    copiedBytes += count;
                }
            }
            if (truncated) {
                output.write("\nLog reached its 4 MiB safety limit; later samples were omitted.\n"
                        .getBytes(StandardCharsets.UTF_8));
            }
            output.write(("\nEnd of movement diagnostics export. Log bytes: " + copiedBytes
                    + ". Exported at: " + Instant.now() + "\n").getBytes(StandardCharsets.UTF_8));
            output.flush();
        } finally {
            snapshot.delete();
        }
    }

    private static void appendIfRunning(Context context, JSONObject item) {
        Context app = context.getApplicationContext();
        if (!isRunning(app)) return;
        synchronized (LOCK) {
            appendRaw(app, item);
        }
    }

    private static JSONObject event(String name, String detail) {
        JSONObject item = new JSONObject();
        try {
            long now = System.currentTimeMillis();
            item.put("timestamp_utc", Instant.ofEpochMilli(now).toString());
            item.put("timestamp_local", localTime(now));
            item.put("event", name);
            if (detail != null) item.put("detail", detail);
            item.put("device", Build.MANUFACTURER + " " + Build.MODEL);
        } catch (Exception ignored) {}
        return item;
    }

    private static String localTime(long epochMillis) {
        return LOCAL_TIME.withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(epochMillis));
    }

    private static File logFile(Context context) {
        return new File(context.getFilesDir(), "roadprints_movement_diagnostics.jsonl");
    }

    private static void appendRaw(Context context, JSONObject item) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (prefs.getBoolean(TRUNCATED, false)) return;
        File file = logFile(context);
        byte[] bytes = (item.toString() + "\n").getBytes(StandardCharsets.UTF_8);
        if (file.length() + bytes.length > MAX_LOG_BYTES) {
            prefs.edit().putBoolean(TRUNCATED, true).apply();
            return;
        }
        try (FileOutputStream output = new FileOutputStream(file, true)) {
            output.write(bytes);
        } catch (Exception ignored) {}
    }
}
