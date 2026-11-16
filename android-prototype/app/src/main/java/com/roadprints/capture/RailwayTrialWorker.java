package com.roadprints.capture;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.AtomicFile;
import android.util.JsonReader;
import android.util.Log;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.json.JSONObject;

/** Foreground-triggered comparison of committed journeys. Only comparison receipts are written. */
final class RailwayTrialWorker {
    private static final String TAG = "RoadprintsRailway";
    private static final int BATCH = 8;
    private static final long BATCH_MS = 180;
    private static final long BATCH_DELAY_MS = 150;
    // Bound mixed-provider parsing separately from the number of fixes sent to the graph.
    private static final int MAX_RAW_SAMPLES = 8192;
    private static final int MAX_BYTES = 8*1024*1024;
    private static final ExecutorService WORK = Executors.newSingleThreadExecutor(task -> {
        Thread t = new Thread(() -> {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
            task.run();
        },"roadprints-railway-trial");
        return t;
    });
    private static final class MainThread {
        static final Handler HANDLER = new Handler(Looper.getMainLooper());
    }
    // Scheduling and the detail observer live on the main thread. The archive
    // cursor and SQLite connection belong exclusively to the worker.
    private static boolean queued;
    private static volatile long foregroundGeneration;
    private static volatile boolean foreground;
    private static String observedId;
    private static Consumer<JSONObject> observer;
    private static volatile boolean detailPending;
    private static long completedRevision = -1;
    private static long scanRevision = -1;
    private static List<File> pending = Collections.emptyList();
    private static int nextIndex;
    private static RegionalRailwayCatalogue catalogue;
    private static NetworkFirstRailway network;
    private static final String KEY = RegionalRailwayCatalogue.SHA256+":"+NetworkFirstRailway.SHA256+":"+RailwayMatcher.VERSION;

    private RailwayTrialWorker() {}

    static boolean eligible(String mode) { return "train".equals(mode); }

    static void resume(Context activity) {
        if (!BuildConfig.DEBUG) return;
        foreground = true;
        detailPending = observedId != null;
        schedule(activity.getApplicationContext());
    }

    static void pause() {
        if (!BuildConfig.DEBUG) return;
        foreground = false;
        foregroundGeneration++;
        // Release the SQLite cache when leaving the assessment screens.
        WORK.execute(RailwayTrialWorker::closeCatalogue);
    }

    static void archiveChanged(Context context) {
        if (!BuildConfig.DEBUG) return;
        Context app = context.getApplicationContext();
        MainThread.HANDLER.post(() -> {
            detailPending = observedId != null;
            schedule(app);
        });
    }

    static void captureEnded(Context context) {
        if (BuildConfig.DEBUG) schedule(context.getApplicationContext());
    }

    static void observe(Context context, String id, Consumer<JSONObject> callback) {
        observedId = id;
        observer = callback;
        detailPending = true;
        schedule(context.getApplicationContext());
    }

    static void removeObserver(Consumer<JSONObject> callback) {
        if (observer != callback) return;
        observedId = null;
        observer = null;
        detailPending = false;
    }

    private static void schedule(Context app) {
        if (queued || !foreground || CaptureService.isActive(app)) return;
        long revision = JourneyStore.dataRevision(app);
        if (!detailPending && completedRevision == revision) return;
        queued = true;
        String priority = detailPending ? observedId : null;
        detailPending = false;
        long generation = foregroundGeneration;
        long queuedAt = SystemClock.elapsedRealtime();
        WORK.execute(() -> {
            long started = SystemClock.elapsedRealtime();
            boolean ncnAtStart = NcnProgressStore.isAssessing();
            BooleanSupplier stop = () -> !foreground || generation != foregroundGeneration
                    || CaptureService.isActive(app);
            boolean more = false;
            boolean failed = false;
            boolean cancelled = false;
            try {
                check(stop);
                if (priority != null) {
                    File source = JourneyStore.archiveFile(app, priority);
                    JSONObject row = source.isFile() ? assess(app, source, priority, stop, true) : null;
                    MainThread.HANDLER.post(() -> {
                        if (observer != null && priority.equals(observedId) && foreground
                                && generation == foregroundGeneration) observer.accept(row);
                    });
                }
                more = batch(app, revision, stop);
            } catch (CancellationException paused) {
                cancelled = true;
            } catch (Exception error) {
                Log.w(TAG, "Railway assessment paused", error);
                closeCatalogue();
                failed = true;
                if (priority != null) MainThread.HANDLER.post(() -> {
                    if (observer != null && priority.equals(observedId) && foreground
                            && generation == foregroundGeneration) observer.accept(new JSONObject(
                            Collections.singletonMap("reason", "assessment_unavailable")));
                });
            }
            if (!more) closeCatalogue();
            long elapsed = SystemClock.elapsedRealtime() - started;
            if (priority != null || !more || elapsed >= BATCH_MS) {
                PerformanceDiagnostics.timing("railway_worker", elapsed,
                        "queue_ms="+(started-queuedAt)+" detail="+(priority!=null)
                                +" more="+more+" interrupted="+cancelled+" failed="+failed
                                +" ncn_at_start="+ncnAtStart+" ncn_at_end="+NcnProgressStore.isAssessing()
                                +" heap_mib="+((Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory())/1048576));
            }
            boolean remaining = more;
            boolean retryOnNextVisit = failed;
            boolean interrupted = cancelled;
            MainThread.HANDLER.post(() -> {
                queued = false;
                if (interrupted && priority != null && priority.equals(observedId)) detailPending = true;
                if (generation != foregroundGeneration || interrupted) {
                    schedule(app);
                } else if (retryOnNextVisit) {
                    completedRevision = revision;
                } else if (remaining) {
                    if (detailPending) schedule(app);
                    else MainThread.HANDLER.postDelayed(() -> schedule(app), BATCH_DELAY_MS);
                } else {
                    completedRevision = revision;
                    schedule(app);
                }
            });
        });
    }

    private static boolean batch(Context app, long revision, BooleanSupplier stop) throws Exception {
        long started = SystemClock.elapsedRealtime();
        if (scanRevision != revision) {
            List<File> archive = JourneyStore.archiveFiles(app);
            Map<File, Long> modified = new HashMap<>();
            for (File source : archive) {
                check(stop);
                modified.put(source, source.lastModified());
            }
            archive.sort(Comparator.comparingLong((File source) -> modified.get(source)).reversed());
            pending = archive;
            nextIndex = 0;
            scanRevision = revision;
        }
        int count = 0;
        try (CompactJourneyCache modes = new CompactJourneyCache(app, "railway-modes-v1")) {
            while (nextIndex < pending.size() && count < BATCH && !detailPending) {
                check(stop);
                File source = pending.get(nextIndex);
                JSONObject metadata = modes.get(source, file -> readMode(file, stop));
                if (metadata != null && eligible(metadata.optString("mode"))) {
                    assess(app, source, JourneyStore.archiveId(source), stop, false);
                }
                nextIndex++;
                count++;
                if (SystemClock.elapsedRealtime() - started >= BATCH_MS) break;
            }
        }
        Log.i(TAG, "archive_batch_ms=" + (SystemClock.elapsedRealtime() - started) + " files=" + count);
        if (nextIndex < pending.size()) return true;
        pending = Collections.emptyList();
        nextIndex = 0;
        scanRevision = -1;
        return false;
    }

    private static JSONObject readMode(File source, BooleanSupplier stop) throws IOException {
        try (JsonReader json = reader(source, stop)) {
            json.beginObject();
            while (json.hasNext()) {
                if ("mode".equals(json.nextName())) {
                    return new JSONObject(Collections.singletonMap("mode", json.nextString()));
                }
                json.skipValue();
            }
        } catch (IOException | IllegalStateException malformed) {
            // A truncated or oversized non-journey must not block the archive.
        }
        return new JSONObject();
    }

    private static File results(Context context) {
        return new File(context.getFilesDir(), "railway-comparisons");
    }

    private static JSONObject assess(Context app, File source, String id, BooleanSupplier stop, boolean report) throws Exception {
        File output = results(app);
        if (!output.isDirectory() && !output.mkdir()) throw new IOException("Railway comparison directory");
        JSONObject row = assess(source, output, id, () -> {
            if (catalogue == null) {
                long opened = SystemClock.elapsedRealtime();
                catalogue = RegionalRailwayCatalogue.open(app, app.getFilesDir(), stop);
                PerformanceDiagnostics.timing("railway_catalogue", SystemClock.elapsedRealtime()-opened,
                        "version="+RegionalRailwayCatalogue.VERSION+" bounds="+RegionalRailwayCatalogue.COVERAGE);
            }
            return new RailwayMatcher(catalogue, stop);
        }, stop, (fixes, start, end) -> {
            if (!NetworkFirstRailway.supports(fixes, start, end)) return null;
            if (network == null) {
                long opened = SystemClock.elapsedRealtime();
                network = NetworkFirstRailway.open(app.getAssets().open("railway/network-first-r1.bin.gzip"), stop);
                PerformanceDiagnostics.timing("railway_network_catalogue", SystemClock.elapsedRealtime()-opened,
                        "version="+NetworkFirstRailway.VERSION);
            }
            return network;
        });
        if (report && row != null) {
            PerformanceDiagnostics.timing("railway_comparison", row.optLong("match_ms"),
                    "reason="+row.optString("reason")+" limit="+row.optString("limit","none")
                            +" confidence="+row.optString("confidence")+" raw="+row.optInt("raw_samples")
                            +" fixes="+row.optInt("input_fixes")+" read_ms="+row.optLong("read_ms")
                            +" cpu_ms="+row.optLong("match_cpu_ms")+" window_ms="
                            +(row.optLong("window_ended_ms")-row.optLong("window_started_ms"))
                            +" supported_m="+Math.round(row.optDouble("supported_rail_m",0))
                            +" network_candidates="+row.optInt("network_candidates")
                            +" candidate_ms="+row.optLong("candidate_ms")
                            +" network_evidence_ms="+row.optLong("network_evidence_ms")
                            +" "+row.optString("work"));
        }
        return row;
    }

    private static void closeCatalogue() {
        network = null;
        if (catalogue != null) {
            catalogue.close();
            catalogue = null;
        }
    }

    interface MatcherProvider { RailwayMatcher open() throws Exception; }
    interface NetworkProvider {
        NetworkFirstRailway open(List<RailwayMatcher.Fix> fixes, long start, long end) throws Exception;
    }

    static JSONObject assess(File source, File output, String id, MatcherProvider provider,
                             BooleanSupplier stop) throws Exception {
        return assess(source, output, id, provider, stop, (fixes, start, end) -> null);
    }

    static JSONObject assess(File source, File output, String id, MatcherProvider provider,
                             BooleanSupplier stop, NetworkProvider networkProvider) throws Exception {
        check(stop);
        if (!source.isFile()) return null;
        String token = JourneyStore.fileToken(source);
        AtomicFile result = resultFile(output, id);
        JSONObject previous = previous(result);
        if (previous != null && token.equals(previous.optString("archive_token"))
                && previous.optString("fingerprint").startsWith(KEY + ":")) {
            return token.equals(JourneyStore.fileToken(source)) ? previous : null;
        }
        evaluate(source, output, id, token, previous, provider, stop, networkProvider);
        JSONObject row = previous(result);
        return source.isFile() && token.equals(JourneyStore.fileToken(source))
                && row != null && token.equals(row.optString("archive_token")) ? row : null;
    }

    static void deleteResult(Context context, String id) {
        File output = results(context);
        WORK.execute(() -> {
            try { resultFile(output, id).delete(); }
            catch (Exception error) { Log.w(TAG, "Could not remove railway comparison", error); }
        });
    }

    static void clearResults(Context context) {
        File output = results(context);
        WORK.execute(() -> {
            File[] files = output.listFiles();
            if (files != null) for (File file : files) file.delete();
        });
    }

    private static void evaluate(File source, File output, String id, String token, JSONObject previous,
                                 MatcherProvider provider, BooleanSupplier stop, NetworkProvider networkProvider) throws Exception {
        long readStarted = SystemClock.elapsedRealtime();
        Evidence evidence;
        try {
            evidence = read(source, stop);
        } catch (CancellationException cancelled) {
            throw cancelled;
        } catch (IOException | IllegalStateException | IllegalArgumentException error) {
            write(output, id, KEY + ":" + token, new JSONObject()
                    .put("journey_id", id).put("confidence", "unresolved")
                    .put("reason", error instanceof Limit ? "input_limit" : "malformed_input")
                    .put("limit", error instanceof Limit limit ? limit.kind : "none")
                    .put("raw_samples", error instanceof Limit limit ? limit.rawSamples : 0)
                    .put("input_fixes", error instanceof Limit limit ? limit.fixes : 0)
                    .put("read_ms", SystemClock.elapsedRealtime()-readStarted), source, token, stop);
            return;
        }
        if (!eligible(evidence.mode) || !id.equals(evidence.id)) return;
        long readMs = SystemClock.elapsedRealtime() - readStarted;
        String fingerprint = KEY + ":" + evidence.fingerprint;
        boolean unchanged = previous != null && fingerprint.equals(previous.optString("fingerprint"));
        long started = SystemClock.elapsedRealtime();
        long cpuStarted = SystemClock.currentThreadTimeMillis();
        RailwayMatcher.Result match = null;
        RailwayMatcher matcher = null;
        String limitName = "none";
        if (!unchanged) {
            try {
                if (evidence.fixes.size() < 4) {
                    match = new RailwayMatcher.Result("unresolved", "insufficient_fixes", 0, 0, 0);
                } else {
                    matcher = provider.open();
                    started = SystemClock.elapsedRealtime();
                    cpuStarted = SystemClock.currentThreadTimeMillis();
                    NetworkFirstRailway routes = networkProvider.open(evidence.fixes, evidence.start, evidence.end);
                    match = matcher.compare(evidence.fixes, evidence.start, evidence.end, routes);
                }
            } catch (RegionalRailwayCatalogue.QueryLimit limit) {
                match = new RailwayMatcher.Result("unresolved","catalogue_query_limit",0,0,0);
                limitName = limit.query+"_rows_256";
            }
        }
        long matchCpuMs = SystemClock.currentThreadTimeMillis() - cpuStarted;
        long matchMs = unchanged ? 0 : SystemClock.elapsedRealtime() - started;
        JSONObject row = unchanged?previous:new JSONObject()
                .put("journey_id",evidence.id)
                .put("confidence",match.confidence())
                .put("reason",match.reason())
                .put("witnesses",match.witnesses())
                .put("supported_pairs",match.supportedPairs())
                .put("supported_rail_m",match.supportedMetres())
                .put("source",evidence.provider)
                .put("raw_samples",evidence.rawSamples)
                .put("input_fixes",evidence.fixes.size())
                .put("read_ms",readMs)
                .put("match_cpu_ms",matchCpuMs)
                .put("limit",limitName)
                .put("work",matcher==null?"":matcher.metrics.summary())
                .put("window_started_ms",evidence.start)
                .put("window_ended_ms",evidence.end)
                .put("distance_provenance",match.complete()?"connected_physical_rail_path":"saved_fallback")
                .put("match_ms",matchMs);
        NetworkFirstRailway.Outcome inference = matcher == null ? null : matcher.inference;
        if (!unchanged && inference != null) {
            row.put("network_catalogue", NetworkFirstRailway.VERSION)
                    .put("network_catalogue_sha256", NetworkFirstRailway.SHA256)
                    .put("network_branch", inference.branch())
                    .put("network_alternatives", inference.alternatives())
                    .put("inferred_endpoints", inference.endpoints())
                    .put("network_candidates", inference.paths())
                    .put("network_witness_pairs", inference.witnesses())
                    .put("candidate_ms", inference.candidateMs())
                    .put("network_evidence_ms", inference.evidenceMs());
            if ("inferred".equals(inference.confidence())) {
                row.put("inferred_m", inference.metres())
                        .put("network_path_sha256", inference.pathId())
                        .put("distance_provenance", "inferred_physical_rail_path");
            }
        }
        row.put("baseline_m",evidence.baseline).put("raw_samples",evidence.rawSamples);
        Object proposal = switch (row.optString("confidence")) {
            case "inferred" -> row.get("inferred_m");
            case "full" -> row.get("supported_rail_m");
            default -> evidence.baseline;
        };
        row.put("proposed_m", proposal);
        if (!unchanged) Log.i(TAG, "match_ms=" + matchMs + " cpu_ms="
                + matchCpuMs + " fixes=" + evidence.fixes.size()
                + " confidence=" + match.confidence());
        write(output, id, fingerprint, row, source, token, stop);
    }

    private static JSONObject previous(AtomicFile file) {
        try { return new JSONObject(new String(file.readFully(),StandardCharsets.UTF_8)); }
        catch (Exception missing) { return null; }
    }

    private static AtomicFile resultFile(File dir, String id) throws Exception {
        return new AtomicFile(new File(dir,RegionalRailwayCatalogue.hex(MessageDigest.getInstance("SHA-256")
                .digest(id.getBytes(StandardCharsets.UTF_8)))+".json"));
    }

    private static void write(File dir, String id, String fingerprint, JSONObject row, File source,
                              String token, BooleanSupplier stop) throws Exception {
        AtomicFile file = resultFile(dir,id);
        row.put("fingerprint", fingerprint).put("matcher", RailwayMatcher.VERSION)
                .put("catalogue", RegionalRailwayCatalogue.VERSION)
                .put("catalogue_sha256", RegionalRailwayCatalogue.SHA256)
                .put("archive_token", token);
        long started = TimeUnit.NANOSECONDS.toMillis(System.nanoTime());
        FileOutputStream output = file.startWrite();
        try {
            output.write(row.toString().getBytes(StandardCharsets.UTF_8));
            check(stop);
            if (!source.isFile() || !token.equals(JourneyStore.fileToken(source)))
                throw new CancellationException();
            file.finishWrite(output);
            // The receipt carries the archive token and readers ignore it if a save
            // or deletion won the race. Cleanup is serialised with receipt writes.
            Log.i(TAG,"receipt_ms="+(TimeUnit.NANOSECONDS.toMillis(System.nanoTime())-started));
        } catch (Exception failure) {
            file.failWrite(output);
            throw failure;
        }
    }

    private record Raw(double lon,double lat,double accuracy,long time,String provider) {}
    private record Evidence(String id,String mode,long start,long end,Object baseline,String provider,
                            List<RailwayMatcher.Fix> fixes,int rawSamples,String fingerprint) {}
    private static final class Limit extends IOException {
        final String kind;
        final int rawSamples, fixes;
        Limit(String kind, int rawSamples, int fixes) {
            this.kind = kind;
            this.rawSamples = rawSamples;
            this.fixes = fixes;
        }
    }

    private static Evidence read(File file,BooleanSupplier stop) throws Exception {
        if (file.length() > MAX_BYTES) throw new Limit("input_bytes_8388608",0,0);
        List<Raw> raw = new ArrayList<>();
        String id = "", mode = "", start = "", end = "";
        Object baseline = JSONObject.NULL;
        try (JsonReader json = reader(file, stop)) {
            json.beginObject();
            while (json.hasNext()) {
                check(stop);
                switch (json.nextName()) {
                    case "journey_id": id = json.nextString(); break;
                    case "mode": mode = json.nextString(); break;
                    case "started_at": start = json.nextString(); break;
                    case "ended_at": end = json.nextString(); break;
                    case "distance_meters":
                        if (json.peek() == android.util.JsonToken.NUMBER) baseline = json.nextDouble();
                        else json.skipValue();
                        break;
                    case "raw_capture_samples":
                        if (json.peek() == android.util.JsonToken.BEGIN_ARRAY) readSamples(json, raw, stop);
                        else json.skipValue();
                        break;
                    default: json.skipValue();
                }
            }
            json.endObject();
        }
        if (id.isEmpty() || id.length() > 512) throw new IOException("Journey identity");
        long from, to;
        try { from = Instant.parse(start).toEpochMilli(); to = Instant.parse(end).toEpochMilli(); }
        catch (java.time.DateTimeException e) { throw new IOException("Journey time",e); }
        String provider = "legacy";
        for (Raw point : raw) {
            if (point.time < from - RailwayMatcher.BOUNDARY_MS || point.time > to + RailwayMatcher.BOUNDARY_MS) continue;
            if ("gps".equals(point.provider)) {
                provider = "gps";
                break;
            }
            if ("fused".equals(point.provider)) provider = "fused";
        }
        TreeMap<Long,Raw> ordered = new TreeMap<>();
        Set<Long> conflicts = new HashSet<>();
        if (eligible(mode)) {
            for (Raw p : raw) {
                if (p.time < from - RailwayMatcher.BOUNDARY_MS || p.time > to + RailwayMatcher.BOUNDARY_MS
                        || !provider.equals(p.provider)) continue;
                Raw old = ordered.putIfAbsent(p.time,p);
                if (ordered.size() > RailwayMatcher.MAX_FIXES)
                    throw new Limit("selected_fixes_2048",raw.size(),ordered.size());
                if (old != null && !p.equals(old)) conflicts.add(p.time);
            }
        }
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update((mode+":"+from+":"+to+":"+provider).getBytes(StandardCharsets.UTF_8));
        List<RailwayMatcher.Fix> fixes = new ArrayList<>();
        for (Raw p : ordered.values()) {
            boolean conflict = conflicts.contains(p.time);
            digest.update(ByteBuffer.allocate(33).putLong(p.time).putDouble(p.lon)
                    .putDouble(p.lat).putDouble(p.accuracy).put((byte)(conflict?1:0)).array());
            // Conflicting observations remain a barrier, even if adjacent times fit.
            fixes.add(RailwayProjection.fix(p.lon,p.lat,conflict?0:p.accuracy,p.time));
        }
        return new Evidence(id,mode,from,to,baseline,provider,fixes,raw.size(),RegionalRailwayCatalogue.hex(digest.digest()));
    }

    private static JsonReader reader(File file, BooleanSupplier stop) throws IOException {
        return new JsonReader(new InputStreamReader(new FilterInputStream(new FileInputStream(file)) {
            private int bytes;
            @Override public int read(byte[] b, int off, int len) throws IOException {
                check(stop);
                int n = super.read(b, off, Math.min(8192, len));
                if (n > 0 && (bytes += n) > MAX_BYTES) throw new Limit("input_bytes_8388608",0,0);
                return n;
            }
        }, StandardCharsets.UTF_8));
    }

    private static void readSamples(JsonReader json,List<Raw> raw,BooleanSupplier stop) throws IOException {
        json.beginArray();
        while (json.hasNext()) {
            check(stop);
            if (raw.size() >= MAX_RAW_SAMPLES) throw new Limit("raw_samples_8192",raw.size()+1,0);
            json.beginArray();
            double lon = json.nextDouble(), lat = json.nextDouble(), accuracy = json.nextDouble();
            long time = json.nextLong();
            if (json.hasNext()) json.skipValue(); // recorded speed; the matcher uses displacement and time
            String provider = "legacy";
            if (json.hasNext()) {
                if (json.peek() == android.util.JsonToken.STRING) provider = json.nextString();
                else { json.skipValue(); provider = "unknown"; }
            }
            if (json.hasNext()) json.skipValue(); // elapsed realtime
            if (json.hasNext() && json.peek() == android.util.JsonToken.NUMBER) {
                if (json.nextLong() > 60_000) accuracy = 0; // Retain stale fixes as barriers.
            }
            while (json.hasNext()) json.skipValue();
            json.endArray();
            if (Math.abs(lon)>180 || Math.abs(lat)>90 || !Double.isFinite(accuracy))
                throw new IOException("Invalid location");
            raw.add(new Raw(lon,lat,accuracy,time,provider));
        }
        json.endArray();
    }

    private static void check(BooleanSupplier stop) {
        if (stop.getAsBoolean()) throw new CancellationException();
    }
}
