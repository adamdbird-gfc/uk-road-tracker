package com.roadprints.capture;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;

/** Stores a short, device-local history of uncaught app crashes for user review. */
public final class CrashReporter {
    private static final String PREFS = "roadprints_debug_reports";
    private static final String REPORTS = "reports";
    private static final String MATCH_REPORTS = "match_failure_reports";
    private static final int MAX_REPORTS = 5;
    private static final int MAX_REPORT_CHARS = 12_000;
    private static final int MAX_MATCH_REPORTS = 30;
    private static final int MAX_MATCH_REPORT_CHARS = 2_400;
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

    /** Records a failed route match without retaining or exporting any route coordinates. */
    public static void recordMatchFailure(Context context, JSONObject journey, Throwable error) {
        try {
            String report = formatMatchFailure(journey, error);
            synchronized (LOCK) {
                SharedPreferences preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
                JSONArray oldReports;
                try {
                    oldReports = new JSONArray(preferences.getString(MATCH_REPORTS, "[]"));
                } catch (Exception ignored) {
                    oldReports = new JSONArray();
                }
                JSONArray updated = new JSONArray().put(report);
                for (int index = 0; index < oldReports.length()
                        && updated.length() < MAX_MATCH_REPORTS; index++) {
                    String previous = sanitizeSavedMatchReport(oldReports.optString(index, ""));
                    if (!previous.isEmpty() && !previous.equals(report)) updated.put(previous);
                }
                preferences.edit().putString(MATCH_REPORTS, updated.toString()).apply();
            }
        } catch (Exception ignored) {
            // Diagnostic recording must never interrupt journey matching or recovery.
        }
    }

    public static String getMatchFailureReports(Context context) {
        synchronized (LOCK) {
            SharedPreferences preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            String encoded = preferences.getString(MATCH_REPORTS, "[]");
            JSONArray reports;
            try {
                reports = new JSONArray(encoded);
            } catch (Exception ignored) {
                return "";
            }
            StringBuilder output = new StringBuilder();
            JSONArray sanitized = new JSONArray();
            boolean changed = false;
            for (int index = 0; index < reports.length(); index++) {
                String original = reports.optString(index, "");
                String report = sanitizeSavedMatchReport(original);
                sanitized.put(report);
                if (!report.equals(original)) changed = true;
                if (index > 0) output.append("\n\n========== Previous journey match failure ==========" + "\n\n");
                output.append(report);
            }
            if (changed) preferences.edit().putString(MATCH_REPORTS, sanitized.toString()).apply();
            return output.toString();
        }
    }

    /** Summarizes matcher errors without retaining or displaying echoed route coordinates. */
    public static String summarizeMatcherError(int status, String body) {
        String prefix = "HTTP " + status + ": ";
        if (body == null || body.trim().isEmpty()) return prefix + "API request failed";
        String trimmed = body.trim();
        try {
            if (trimmed.startsWith("[")) {
                return prefix + summarizeValidationErrors(new JSONArray(trimmed));
            }
            JSONObject response = new JSONObject(trimmed);
            Object detail = response.opt("detail");
            if (detail instanceof JSONArray) {
                return prefix + summarizeValidationErrors((JSONArray) detail);
            }
            if (detail instanceof JSONObject) {
                JSONArray errors = ((JSONObject) detail).optJSONArray("errors");
                if (errors != null) return prefix + summarizeValidationErrors(errors);
                return prefix + "Matcher returned a structured error response";
            }
            if (detail instanceof String) {
                String message = ((String) detail).trim();
                if (message.startsWith("[") || message.startsWith("{")) {
                    return summarizeMatcherError(status, message);
                }
                if (containsCoordinateFields(message)) {
                    return prefix + "Matcher error detail omitted because it contained submitted point data";
                }
                return prefix + limit(message, 500);
            }
            return prefix + "Matcher returned an error response";
        } catch (Exception ignored) {
            // Raw server bodies can echo GPS points, so never save a body we cannot parse safely.
            return prefix + "Non-JSON error response (" + body.length() + " characters)";
        }
    }

    private static String summarizeValidationErrors(JSONArray errors) {
        StringBuilder summary = new StringBuilder();
        int included = 0;
        for (int index = 0; index < errors.length() && included < 3; index++) {
            JSONObject error = errors.optJSONObject(index);
            if (error == null) continue;
            String location = formatErrorLocation(error.optJSONArray("loc"));
            String type = error.optString("type", "");
            String message = error.optString("msg", "Request validation failed");
            if (summary.length() > 0) summary.append("; ");
            if (!location.isEmpty()) summary.append(location).append(": ");
            if (!type.isEmpty()) summary.append(type).append(" — ");
            summary.append(limit(message, 240));
            included++;
        }
        return summary.length() == 0
                ? "Matcher rejected the request (validation details omitted)" : summary.toString();
    }

    private static String formatErrorLocation(JSONArray location) {
        if (location == null || location.length() == 0) return "";
        StringBuilder path = new StringBuilder();
        for (int index = 0; index < location.length(); index++) {
            String part = String.valueOf(location.opt(index));
            if (path.length() > 0) path.append('.');
            path.append(part);
        }
        return path.toString();
    }

    private static String sanitizeDiagnosticError(String value) {
        if (value == null || value.isEmpty()) return "";
        int http = value.indexOf("HTTP ");
        if (http >= 0 && value.length() >= http + 8) {
            try {
                int status = Integer.parseInt(value.substring(http + 5, http + 8));
                int colon = value.indexOf(':', http + 8);
                if (colon >= 0) {
                    String body = value.substring(colon + 1).trim();
                    if (body.startsWith("[") || body.startsWith("{")) {
                        return value.substring(0, http) + summarizeMatcherError(status, body);
                    }
                }
            } catch (NumberFormatException ignored) { }
        }
        if (containsCoordinateFields(value)) {
            return "Error detail omitted because it contained submitted point data";
        }
        return limit(value, MAX_MATCH_REPORT_CHARS / 2);
    }

    private static boolean containsCoordinateFields(String value) {
        String lower = value.toLowerCase();
        return lower.contains("\"lat\"") || lower.contains("\"lng\"")
                || lower.contains("\"latitude\"") || lower.contains("\"longitude\"");
    }

    private static String sanitizeSavedMatchReport(String report) {
        if (report == null || report.isEmpty()) return "";
        String[] lines = report.split("\\n", -1);
        StringBuilder sanitized = new StringBuilder();
        for (String line : lines) {
            if (line.startsWith("Stored error: ")) {
                line = "Stored error: " + sanitizeDiagnosticError(
                        line.substring("Stored error: ".length()));
            } else if (line.startsWith("Detail: ")) {
                line = "Detail: " + sanitizeDiagnosticError(line.substring("Detail: ".length()));
            } else if (line.startsWith("Cause: ")) {
                line = "Cause: " + sanitizeDiagnosticError(line.substring("Cause: ".length()));
            } else if (containsCoordinateFields(line)) {
                line = "[Error detail omitted because it contained submitted point data.]";
            }
            if (sanitized.length() > 0) sanitized.append('\n');
            sanitized.append(line);
        }
        return sanitized.toString();
    }

    public static String getDiagnosticReports(Context context) {
        String crashes = getReports(context);
        String matches = getMatchFailureReports(context);
        if (crashes.isEmpty()) return matches;
        if (matches.isEmpty()) return crashes;
        return crashes + "\n\n========== Journey match failures ==========" + "\n\n" + matches;
    }

    private static String formatMatchFailure(JSONObject journey, Throwable error) {
        JSONObject attempt = journey == null ? null : journey.optJSONObject("last_match_attempt");
        JSONObject source = journey == null ? null : journey.optJSONObject("source");
        StringBuilder output = new StringBuilder();
        output.append("Roadprints journey match failure\n");
        output.append("Time (UTC): ").append(attempt == null
                ? Instant.now() : attempt.optString("started_at_utc", Instant.now().toString())).append('\n');
        output.append("App version: ").append(BuildConfig.VERSION_NAME)
                .append(" (").append(BuildConfig.VERSION_CODE).append(")\n");
        output.append("Device: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
                .append("\nAndroid: ").append(Build.VERSION.RELEASE)
                .append(" (API ").append(Build.VERSION.SDK_INT).append(")\n");
        if (journey != null) {
            output.append("Journey ID: ").append(journey.optString("journey_id", "unknown")).append('\n');
            String title = journey.optString("title", "").trim();
            if (!title.isEmpty()) output.append("Journey title: ").append(title).append('\n');
            output.append("Mode: ").append(journey.optString("mode", "unknown")).append('\n');
            if (source != null) output.append("Source: ").append(source.optString("type", "unknown")).append('\n');
            String summary = sanitizeDiagnosticError(journey.optString("error_summary", ""));
            if (!summary.isEmpty()) output.append("Stored error: ").append(limit(summary, MAX_MATCH_REPORT_CHARS / 2)).append('\n');
        }
        if (attempt != null) {
            output.append("Matcher endpoint: ").append(attempt.optString("endpoint", "unknown")).append('\n');
            output.append("GPS points: ").append(attempt.optInt("source_points", -1)).append('\n');
            output.append("Points submitted: ").append(attempt.optInt("submitted_points", -1)).append('\n');
            output.append("Points reduced: ").append(attempt.optBoolean("points_reduced", false)).append('\n');
        }
        if (error != null) {
            output.append("Exception: ").append(error.getClass().getSimpleName()).append('\n');
            String message = sanitizeDiagnosticError(error.getMessage());
            if (message != null && !message.isEmpty()) output.append("Detail: ").append(limit(message, MAX_MATCH_REPORT_CHARS / 2)).append('\n');
            Throwable cause = error.getCause();
            if (cause != null) output.append("Cause: ").append(cause.getClass().getSimpleName())
                    .append(": ").append(sanitizeDiagnosticError(String.valueOf(cause.getMessage()))).append('\n');
        }
        String report = output.toString();
        return report.length() <= MAX_MATCH_REPORT_CHARS ? report
                : report.substring(0, MAX_MATCH_REPORT_CHARS) + "\n[Match failure report shortened.]";
    }

    private static String limit(String value, int maximum) {
        if (value == null || value.length() <= maximum) return value == null ? "" : value;
        return value.substring(0, maximum) + "…";
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
        return !getDiagnosticReports(context).isEmpty();
    }

    public static void clear(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .remove(REPORTS).remove(MATCH_REPORTS).commit();
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
