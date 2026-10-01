package com.roadprints.capture;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import org.json.JSONArray;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;

/** Stores a short, device-local history of uncaught app crashes for user review. */
public final class CrashReporter {
    private static final String PREFS = "roadprints_debug_reports";
    private static final String REPORTS = "reports";
    private static final int MAX_REPORTS = 5;
    private static final int MAX_REPORT_CHARS = 12_000;
    private static final Object LOCK = new Object();
    private static boolean installed;

    private CrashReporter() {}

    public static void install(Context context) {
        synchronized (LOCK) {
            if (installed) return;
            installed = true;
            Context app = context.getApplicationContext();
            Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
            Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
                try {
                    record(app, thread, error);
                } catch (Throwable ignored) {
                    // Preserve Android's normal crash handling even if local storage fails.
                }
                if (previous != null) {
                    previous.uncaughtException(thread, error);
                } else {
                    android.os.Process.killProcess(android.os.Process.myPid());
                    System.exit(10);
                }
            });
        }
    }

    static void record(Context context, Thread thread, Throwable error) {
        String report = format(thread, error);
        synchronized (LOCK) {
            SharedPreferences preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            JSONArray oldReports;
            try {
                oldReports = new JSONArray(preferences.getString(REPORTS, "[]"));
            } catch (Exception ignored) {
                oldReports = new JSONArray();
            }
            JSONArray updated = new JSONArray().put(report);
            for (int index = 0; index < oldReports.length() && updated.length() < MAX_REPORTS; index++) {
                String previous = oldReports.optString(index, "");
                if (!previous.isEmpty() && !previous.equals(report)) updated.put(previous);
            }
            // commit() is intentional: this runs immediately before Android terminates the process.
            preferences.edit().putString(REPORTS, updated.toString()).commit();
        }
    }

    public static String getReports(Context context) {
        synchronized (LOCK) {
            String encoded = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getString(REPORTS, "[]");
            JSONArray reports;
            try {
                reports = new JSONArray(encoded);
            } catch (Exception ignored) {
                return "";
            }
            StringBuilder output = new StringBuilder();
            for (int index = 0; index < reports.length(); index++) {
                if (index > 0) output.append("\n\n========== Previous crash ==========\n\n");
                output.append(reports.optString(index, ""));
            }
            return output.toString();
        }
    }

    public static boolean hasReports(Context context) {
        return !getReports(context).isEmpty();
    }

    public static void clear(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(REPORTS).commit();
    }

    private static String format(Thread thread, Throwable error) {
        StringBuilder output = new StringBuilder();
        output.append("Roadprints crash report\n");
        output.append("Time (UTC): ").append(Instant.now()).append('\n');
        output.append("App version: ").append(BuildConfig.VERSION_NAME)
                .append(" (").append(BuildConfig.VERSION_CODE).append(")\n");
        output.append("Device: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
                .append("\nAndroid: ").append(Build.VERSION.RELEASE)
                .append(" (API ").append(Build.VERSION.SDK_INT).append(")\n");
        output.append("Thread: ").append(thread == null ? "unknown" : thread.getName())
                .append("\n\n");
        StringWriter stack = new StringWriter();
        if (error != null) error.printStackTrace(new PrintWriter(stack));
        else stack.append("No exception details were available.");
        output.append(stack);
        String report = output.toString();
        if (report.length() <= MAX_REPORT_CHARS) return report;
        return report.substring(0, MAX_REPORT_CHARS)
                + "\n\n[Crash report shortened to fit local storage.]";
    }
}
