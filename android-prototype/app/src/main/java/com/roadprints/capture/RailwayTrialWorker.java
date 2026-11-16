package com.roadprints.capture;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
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
import org.json.JSONObject;

/** Controlled foreground-triggered reassessment. Reads staged copies and writes only its sidecar. */
final class RailwayTrialWorker {
    private static final String TAG = "RoadprintsRailway";
    private static final String SWITCH = "enabled";
    private static final int BATCH = 8;
    private static final int MAX_INPUTS = 2048;
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
    private static boolean queued;
    private static volatile long foregroundGeneration;
    private static volatile boolean foreground;
    private static int nextIndex;
    private static boolean finished;
    private static final String KEY = RegionalRailwayCatalogue.SHA256+":"+RailwayMatcher.VERSION;

    private RailwayTrialWorker() {}

    static void resume(Context activity) {
        if (!BuildConfig.DEBUG) return;
        foreground = true;
        foregroundGeneration++;
        nextIndex = 0;
        finished = false;
        schedule(activity.getApplicationContext());
    }

    static void pause() {
        if (!BuildConfig.DEBUG) return;
        foreground = false;
        foregroundGeneration++;
    }

    private static void schedule(Context app) {
        if (queued || !foreground || finished) return;
        queued = true;
        long generation = foregroundGeneration;
        int index = nextIndex;
        WORK.execute(() -> {
            File root = new File(app.getFilesDir(),"railway-trial");
            File marker = new File(root,SWITCH);
            boolean enabled = marker.isFile();
            BooleanSupplier stop = () -> !foreground || generation != foregroundGeneration
                    || CaptureService.isActive(app);
            int next = -1;
            try {
                if (enabled && !stop.getAsBoolean()) next = batch(app,root,index,stop);
            } catch (CancellationException ignored) {
                // A resumed foreground session skips already committed evidence.
            } catch (Exception error) {
                Log.w(TAG,"Trial batch stopped: "+error.getClass().getSimpleName());
            }
            int resumeAt = next;
            MainThread.HANDLER.post(() -> {
                queued = false;
                if (!foreground) return;
                if (generation != foregroundGeneration) {
                    schedule(app);
                } else if (resumeAt >= 0) {
                    nextIndex = resumeAt;
                    MainThread.HANDLER.postDelayed(() -> schedule(app),100);
                } else {
                    finished = true;
                }
            });
        });
    }

    private static int batch(Context app, File root, int index, BooleanSupplier stop) throws Exception {
        File[] files = new File(root,"input").listFiles(f -> f.isFile() && f.getName().endsWith(".json"));
        if (files == null || files.length == 0 || index >= files.length) return -1;
        if (files.length > MAX_INPUTS) throw new IOException("Trial input count");
        Arrays.sort(files,Comparator.comparing(File::getName));
        File output = new File(root,"results");
        if (!output.isDirectory() && !output.mkdir()) throw new IOException("Trial output directory");
        long opened = TimeUnit.NANOSECONDS.toMillis(System.nanoTime());
        try (RegionalRailwayCatalogue catalogue = RegionalRailwayCatalogue.open(app,root,stop)) {
            Log.i(TAG,"catalogue_open_or_install_ms="+(TimeUnit.NANOSECONDS.toMillis(System.nanoTime())-opened));
            RailwayMatcher matcher = new RailwayMatcher(catalogue,stop);
            int end = Math.min(index+BATCH,files.length);
            for (int i = index; i < end; i++) {
                check(stop);
                if (!new File(root,SWITCH).isFile()) return -1;
                evaluate(files[i],output,matcher,stop);
            }
            return end == files.length?-1:end;
        }
    }

    static boolean evaluate(File source, File output, RailwayMatcher matcher, BooleanSupplier stop) throws Exception {
        String token = JourneyStore.fileToken(source);
        Evidence evidence;
        try {
            evidence = read(source,stop);
        } catch (CancellationException cancelled) {
            throw cancelled;
        } catch (IOException | IllegalStateException | IllegalArgumentException error) {
            Log.i(TAG,"held_input="+error.getClass().getSimpleName());
            // Oversized inputs have a durable receipt and are cheap to skip on retry.
            return write(output,source.getName(),KEY+":"+token,
                    new JSONObject().put("confidence","unresolved").put("reason",
                            error instanceof Limit?"input_limit":"malformed_input"),source,token,stop);
        }
        String fingerprint = KEY+":"+evidence.fingerprint;
        AtomicFile result = resultFile(output,evidence.id);
        JSONObject previous = previous(result);
        boolean unchanged = previous != null && fingerprint.equals(previous.optString("fingerprint"));
        if (unchanged && (evidence.baseline == JSONObject.NULL
                ? previous.isNull("baseline_m")
                : Double.compare((double)evidence.baseline,previous.optDouble("baseline_m")) == 0)) return false;
        long started = TimeUnit.NANOSECONDS.toMillis(System.nanoTime());
        RailwayMatcher.Result match = null;
        if (!unchanged) {
            try {
                match = matcher.match(evidence.fixes,evidence.start,evidence.end);
            } catch (RegionalRailwayCatalogue.QueryLimit limit) {
                match = new RailwayMatcher.Result("unresolved","catalogue_query_limit",0,0,0);
            }
        }
        long matchMs = unchanged?0:TimeUnit.NANOSECONDS.toMillis(System.nanoTime())-started;
        JSONObject row = unchanged?previous:new JSONObject()
                .put("journey_id",evidence.id)
                .put("confidence",match.confidence())
                .put("reason",match.reason())
                .put("witnesses",match.witnesses())
                .put("supported_pairs",match.supportedPairs())
                .put("supported_rail_m",match.supportedMetres())
                .put("source",evidence.provider)
                .put("input_fixes",evidence.fixes.size())
                .put("window_started_ms",evidence.start)
                .put("window_ended_ms",evidence.end)
                .put("distance_provenance",match.complete()?"connected_physical_rail_path":"saved_fallback")
                .put("match_ms",matchMs);
        row.put("baseline_m",evidence.baseline);
        boolean complete = "full".equals(row.optString("confidence"));
        row.put("proposed_m",complete?row.get("supported_rail_m"):evidence.baseline);
        if (!unchanged) Log.i(TAG,"match_ms="+matchMs+" fixes="+evidence.fixes.size()
                +" confidence="+match.confidence());
        return write(output,evidence.id,fingerprint,row,source,token,stop);
    }

    private static JSONObject previous(AtomicFile file) {
        try { return new JSONObject(new String(file.readFully(),StandardCharsets.UTF_8)); }
        catch (Exception missing) { return null; }
    }

    private static AtomicFile resultFile(File dir, String id) throws Exception {
        return new AtomicFile(new File(dir,RegionalRailwayCatalogue.hex(MessageDigest.getInstance("SHA-256")
                .digest(id.getBytes(StandardCharsets.UTF_8)))+".json"));
    }

    private static boolean write(File dir,String id,String fingerprint,JSONObject row,File source,
                                 String token,BooleanSupplier stop) throws Exception {
        AtomicFile file = resultFile(dir,id);
        JSONObject old = previous(file);
        row.put("fingerprint",fingerprint).put("matcher",RailwayMatcher.VERSION)
                .put("catalogue",RegionalRailwayCatalogue.VERSION)
                .put("catalogue_sha256",RegionalRailwayCatalogue.SHA256);
        if (old != null && row.toString().equals(old.toString())) return false;
        long started = TimeUnit.NANOSECONDS.toMillis(System.nanoTime());
        FileOutputStream output = file.startWrite();
        try {
            output.write(row.toString().getBytes(StandardCharsets.UTF_8));
            check(stop);
            if (!source.isFile() || !token.equals(JourneyStore.fileToken(source)))
                throw new CancellationException();
            file.finishWrite(output);
            Log.i(TAG,"receipt_ms="+(TimeUnit.NANOSECONDS.toMillis(System.nanoTime())-started));
            return true;
        } catch (Exception failure) {
            file.failWrite(output);
            throw failure;
        }
    }

    private record Raw(double lon,double lat,double accuracy,long time,String provider) {}
    private record Evidence(String id,long start,long end,Object baseline,String provider,
                            List<RailwayMatcher.Fix> fixes,String fingerprint) {}
    private static final class Limit extends IOException {}

    private static Evidence read(File file,BooleanSupplier stop) throws Exception {
        if (file.length() > MAX_BYTES) throw new Limit();
        List<Raw> raw = new ArrayList<>();
        String id = "", mode = "", start = "", end = "";
        Object baseline = JSONObject.NULL;
        try (JsonReader json = new JsonReader(new InputStreamReader(new FilterInputStream(new FileInputStream(file)) {
            private int bytes;
            @Override public int read(byte[] b,int off,int len) throws IOException {
                check(stop);
                int n = super.read(b,off,Math.min(8192,len));
                if (n > 0 && (bytes += n) > MAX_BYTES) throw new Limit();
                return n;
            }
        },StandardCharsets.UTF_8))) {
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
                    case "raw_capture_samples": readSamples(json,raw,stop); break;
                    default: json.skipValue();
                }
            }
            json.endObject();
        }
        if (id.isEmpty() || id.length() > 512) throw new IOException("Journey identity");
        long from, to;
        try { from = Instant.parse(start).toEpochMilli(); to = Instant.parse(end).toEpochMilli(); }
        catch (java.time.DateTimeException e) { throw new IOException("Journey time",e); }
        String provider = raw.stream().anyMatch(p -> "gps".equals(p.provider))?"gps":"legacy";
        TreeMap<Long,Raw> ordered = new TreeMap<>();
        Set<Long> conflicts = new HashSet<>();
        if ("train".equals(mode) || "unknown".equals(mode)) {
            for (Raw p : raw) {
                if (p.time < from || p.time > to || !provider.equals(p.provider)) continue;
                Raw old = ordered.putIfAbsent(p.time,p);
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
        return new Evidence(id,from,to,baseline,provider,fixes,RegionalRailwayCatalogue.hex(digest.digest()));
    }

    private static void readSamples(JsonReader json,List<Raw> raw,BooleanSupplier stop) throws IOException {
        json.beginArray();
        while (json.hasNext()) {
            check(stop);
            if (raw.size() >= RailwayMatcher.MAX_FIXES) throw new Limit();
            json.beginArray();
            double lon = json.nextDouble(), lat = json.nextDouble(), accuracy = json.nextDouble();
            long time = json.nextLong();
            if (json.hasNext()) json.skipValue(); // recorded speed; the matcher uses displacement and time
            String provider = "legacy";
            if (json.hasNext()) {
                if (json.peek() == android.util.JsonToken.STRING) provider = json.nextString();
                else { json.skipValue(); provider = "unknown"; }
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
