package com.roadprints.capture;

import java.io.*;
import java.util.*;

/** Local bounded bandwidth ledger. Dataset categories only: no URLs, codes or coordinates. */
final class ReferenceDataDiagnostics {
    private static File file;
    private static boolean pending;
    private static final java.util.concurrent.ScheduledExecutorService WRITER =
            java.util.concurrent.Executors.newSingleThreadScheduledExecutor(task -> {
                Thread thread = new Thread(task, "reference-diagnostics"); thread.setDaemon(true); return thread;
            });
    private static final Properties totals = new Properties();
    private static final ArrayDeque<String> recent = new ArrayDeque<>();
    static synchronized void initialize(File directory) {
        if (file != null) return;
        file = new File(directory, "reference-bandwidth.properties");
        try (InputStream in = new FileInputStream(file)) {
            if (file.length() > 64 * 1024) return;
            totals.load(in);
            for (String row : totals.getProperty("recent", "").split("\n"))
                if (!row.isEmpty() && recent.size() < 64) recent.addLast(row);
        }
        catch (IOException | IllegalArgumentException ignored) { totals.clear(); }
    }
    private static void add(String key, long count) {
        long old; try { old = Long.parseLong(totals.getProperty(key, "0")); } catch (NumberFormatException e) { old = 0; }
        totals.setProperty(key, Long.toString(old + count));
    }
    static synchronized void cache(String dataset, String version, String existing, boolean hit, String reason) {
        add("cache_checks", 1); add(hit ? "cache_hits" : "cache_misses", 1);
        event(dataset + " requested=" + version + " cached=" + existing + " hit=" + hit + " network=false reason=" + reason);
    }
    static synchronized void network(String dataset, String version, String cachedVersion, String trigger, long bytes, long duration, boolean success) {
        add("requests", 1); add("bytes", bytes); add(dataset + ".requests", 1); add(dataset + ".bytes", bytes);
        add("trigger." + trigger + ".requests", 1);
        boolean repeated = version.equals(totals.getProperty(dataset + ".last_requested_version"));
        long retry = repeated ? counter(dataset + ".consecutive_failures") : 0;
        if (repeated) add(dataset + ".repeat_requests", 1);
        totals.setProperty(dataset + ".last_requested_version", version);
        totals.setProperty(dataset + ".consecutive_failures", Long.toString(success ? 0 : retry + 1));
        if (!success) add("failures", 1);
        event(dataset + " requested=" + version + " cached=" + cachedVersion + " trigger=" + trigger + " network=true bytes=" + bytes
                + " ms=" + duration + " success=" + success + " retry=" + retry);
    }
    private static void event(String value) {
        recent.addFirst(System.currentTimeMillis() + " " + value);
        while (recent.size() > 64) recent.removeLast();
        totals.setProperty("recent", String.join("\n", recent));
        if (file != null && !pending) {
            pending = true;
            WRITER.schedule(ReferenceDataDiagnostics::flush, 1, java.util.concurrent.TimeUnit.SECONDS);
        }
    }
    // Coalesced disk work never runs on a screen/cache caller.
    static void flush() {
        Properties snapshot = new Properties(); File target;
        synchronized (ReferenceDataDiagnostics.class) {
            pending = false; target = file; if (target == null) return; snapshot.putAll(totals);
        }
        synchronized (WRITER) {
            File temp = new File(target.getPath() + ".tmp");
            try (FileOutputStream out = new FileOutputStream(temp)) {
                snapshot.store(out, "Local reference data counters; body bytes exclude headers/TLS");
            } catch (IOException ignored) { return; }
            try { java.nio.file.Files.move(temp.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE); } catch (IOException ignored) { }
        }
    }
    private static long counter(String key) {
        try { return Math.max(0, Long.parseLong(totals.getProperty(key, "0"))); }
        catch (NumberFormatException invalid) { return 0; }
    }
    static synchronized String report() {
        StringBuilder out = new StringBuilder("Reference data bandwidth (response-body bytes; not Firebase billing)\n");
        for (String key : new TreeSet<>(totals.stringPropertyNames()))
            if (!key.equals("recent")) out.append(key).append('=').append(totals.getProperty(key)).append('\n');
        long checks = counter("cache_checks"), hits = counter("cache_hits");
        out.append("instrumented_cache_hit_percent=").append(checks == 0 ? 0 : 100 * hits / checks).append('\n');
        return out.append(totals.getProperty("recent", "")).toString();
    }
}
