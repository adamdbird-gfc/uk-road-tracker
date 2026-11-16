package com.roadprints.capture;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

/** Explicit, private dry runs. JourneyStore remains the only authority for journeys and credit. */
final class JourneyReconciliation implements AutoCloseable {
    static final int MAX_BATCH = 16;
    static final int MAX_BASELINE_BYTES = 2 * 1024 * 1024;
    private static final String DATABASE = "journey-interpretation-v1.db";
    private static final String STORE = "journey-interpretation-v1";
    private static final int MAX_INTERPRETATION_BYTES = 256 * 1024;

    enum Outcome {
        UNCHANGED, SAFE_CANDIDATE, REVIEW_REQUIRED, EVIDENCE_UNAVAILABLE, TOO_LARGE
    }

    interface Rule {
        /** Include the rule and reference-data revision in this version. */
        String version();
        Comparison compare(JSONObject source, JSONObject userDecisions) throws Exception;
    }

    static final class Comparison {
        final Outcome outcome;
        final JSONObject interpretation;

        Comparison(Outcome outcome, JSONObject interpretation) {
            this.outcome = outcome;
            this.interpretation = interpretation;
        }
    }

    // The observed record is a baseline. Raw fields, when present, keep their own
    // provenance and may cover just the observations retained by capture.
    static final Rule BASELINE = baselineRule("baseline-v2");
    private static final Rule LEGACY_BASELINE = baselineRule("baseline-v1");

    private static Rule baselineRule(String version) {
        return new Rule() {
        public String version() { return version; }

        public Comparison compare(JSONObject source, JSONObject userDecisions) throws Exception {
            JSONObject current = new JSONObject();
            for (String key : new String[]{"journey_id", "mode", "started_at", "ended_at",
                    "processing_status", "distance_meters", "route_geometry"}) {
                if (source.has(key)) current.put(key, source.get(key));
            }
            // Legacy distance was stored under one field with several meanings.
            // This baseline makes no new confidence, distance or collection claim.
            current.put("distance_kind", "stored");
            current.put("match_confidence", JSONObject.NULL);
            current.put("accepted_distance_m", JSONObject.NULL);
            current.put("unresolved_sections", JSONObject.NULL);
            JSONObject origin = source.optJSONObject("source");
            current.put("provenance", origin == null ? JSONObject.NULL : origin)
                    .put("has_raw_samples", source.optJSONArray("raw_capture_samples") != null)
                    .put("has_raw_geometry", source.optJSONObject("raw_capture_geometry") != null);
            JSONObject ferry = source.optJSONObject("ferry_recovery");
            if (ferry != null) current.put("existing_lineage", ferry);
            current.put("source_journeys", new JSONArray().put(
                    ferry == null ? source.getString("journey_id")
                            : ferry.optString("source_id", source.getString("journey_id"))));
            JSONArray samples = source.optJSONArray("raw_capture_samples");
            int timed = 0;
            for (int i = 0; samples != null && i < samples.length(); i++) {
                JSONArray fix = samples.optJSONArray(i);
                if (fix != null && fix.optLong(3, 0) > 0) timed++;
            }
            current.put("raw_timing", timed == 0 ? "missing" : timed == samples.length() ? "present" : "partial");
            JSONObject rawGeometry = source.optJSONObject("raw_capture_geometry");
            JSONObject route = source.optJSONObject("route_geometry");
            if ((samples == null || samples.length() < 2) && !hasRoute(rawGeometry) && !hasRoute(route)) {
                return new Comparison(Outcome.EVIDENCE_UNAVAILABLE, current);
            }
            return new Comparison(Outcome.UNCHANGED, current);
        }
        };
    }

    private static boolean hasRoute(JSONObject geometry) {
        JSONArray points = geometry == null ? null : geometry.optJSONArray("coordinates");
        return points != null && points.length() >= 2;
    }

    private static final java.util.concurrent.atomic.AtomicBoolean cleanupNeeded =
            new java.util.concurrent.atomic.AtomicBoolean(true);
    private final Context context;
    private final File baselines;
    private final SQLiteDatabase db;

    JourneyReconciliation(Context context) throws IOException {
        this.context = context.getApplicationContext();
        baselines = new File(this.context.getFilesDir(), STORE);
        if (!baselines.isDirectory() && !baselines.mkdirs() && !baselines.isDirectory()) {
            throw new IOException("Cannot open journey interpretation storage");
        }
        if (cleanupNeeded.compareAndSet(true, false)) {
            File[] abandoned = baselines.listFiles((dir, name) -> name.endsWith(".tmp"));
            if (abandoned != null) for (File file : abandoned) file.delete();
        }
        db = this.context.openOrCreateDatabase(DATABASE, Context.MODE_PRIVATE, null);
        db.beginTransaction();
        try {
            db.execSQL("CREATE TABLE IF NOT EXISTS pending (version TEXT NOT NULL, journey TEXT NOT NULL,"
                    + " PRIMARY KEY (version, journey))");
            db.execSQL("CREATE TABLE IF NOT EXISTS receipts (version TEXT NOT NULL, journey TEXT NOT NULL,"
                    + " token TEXT NOT NULL, source_revision INTEGER, source_sha TEXT, decisions_sha TEXT,"
                    + " interpretation_sha TEXT, outcome TEXT NOT NULL, interpretation TEXT, decisions TEXT,"
                    + " PRIMARY KEY (version, journey, token))");
            db.setVersion(1);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    /** Queue only the requested IDs. A caller can enqueue revised files or a new rule version. */
    void enqueue(Rule rule, Collection<String> ids) {
        if (ids.size() > MAX_BATCH) throw new IllegalArgumentException("Submit at most " + MAX_BATCH + " journeys");
        db.beginTransaction();
        try {
            for (String id : ids) {
                if (id == null || id.isEmpty() || id.contains("/") || id.contains("\\")) {
                    throw new IllegalArgumentException("Invalid journey ID");
                }
                ContentValues row = new ContentValues();
                row.put("version", rule.version());
                row.put("journey", id);
                db.insertWithOnConflict("pending", null, row, SQLiteDatabase.CONFLICT_IGNORE);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    /** Call on the archive worker. The caller controls scheduling and may cancel between reads. */
    synchronized int runBatch(Rule rule, BooleanSupplier cancelled) throws Exception {
        List<String> ids = new ArrayList<>();
        try (Cursor cursor = db.rawQuery(
                "SELECT journey FROM pending WHERE version=? ORDER BY journey LIMIT " + MAX_BATCH,
                new String[]{rule.version()})) {
            while (cursor.moveToNext()) ids.add(cursor.getString(0));
        }
        int done = 0;
        for (String id : ids) {
            if (shouldStop(cancelled)) break;
            try {
                if (reconcile(rule, id, cancelled)) done++;
            } catch (CancellationException stopped) {
                break;
            }
        }
        return done;
    }

    JSONObject latestVerification(String id) throws Exception {
        JSONObject result = receipt(BASELINE, id);
        return result == null ? receipt(LEGACY_BASELINE, id) : result;
    }

    /** Prioritize the explicitly selected record over queued historical work. */
    synchronized JSONObject verifySelected(String id, BooleanSupplier cancelled) throws Exception {
        JSONObject existing = receipt(BASELINE, id);
        if (existing != null) return existing;
        enqueue(BASELINE, java.util.Collections.singletonList(id));
        checkRunning(cancelled);
        return reconcile(BASELINE, id, cancelled) ? receipt(BASELINE, id) : null;
    }

    private boolean reconcile(Rule rule, String id, BooleanSupplier cancelled) throws Exception {
        File source = JourneyStore.archiveFile(context, id);
        String token = JourneyStore.fileToken(source);
        String[] key = {rule.version(), id, token};
        if (hasReceipt(key)) {
            synchronized (JourneyStore.class) {
                if (!token.equals(JourneyStore.fileToken(source))) return false;
                db.delete("pending", "version=? AND journey=?", new String[]{rule.version(), id});
                return true;
            }
        }

        ContentValues row = new ContentValues();
        row.put("version", rule.version());
        row.put("journey", id);
        row.put("token", token);
        Outcome outcome;
        byte[] baselineBytes = null;
        String baselineHash = null;
        if (!source.isFile()) {
            outcome = Outcome.EVIDENCE_UNAVAILABLE;
        } else if (source.length() > MAX_BASELINE_BYTES) {
            outcome = Outcome.TOO_LARGE;
        } else {
            byte[] bytes = readBaseline(source, cancelled);
            JSONObject baseline;
            try {
                baseline = new JSONObject(new String(bytes, StandardCharsets.UTF_8));
                if (!id.equals(baseline.optString("journey_id"))) baseline = null;
            } catch (org.json.JSONException invalid) {
                baseline = null;
            }
            if (baseline == null) {
                outcome = Outcome.EVIDENCE_UNAVAILABLE;
            } else {
                String sourceHash = NcnJson.hash(bytes);
                JSONObject decisions = new JSONObject();
                for (String field : new String[]{"title", "name", "journey_corrections",
                        "transport_confirmation", "transport_changes", "places", "stops", "ferry_recovery"}) {
                    if (baseline.has(field)) decisions.put(field, baseline.get(field));
                }
                String decisionsText = decisions.toString();
                int revision = baseline.optInt("revision", -1);
                checkRunning(cancelled);
                Comparison result = rule.compare(baseline, decisions);
                if (result.outcome == Outcome.SAFE_CANDIDATE && (decisions.has("journey_corrections")
                        || "confirmed".equals(decisions.optString("transport_confirmation"))
                        || decisions.has("ferry_recovery"))) {
                    result = new Comparison(Outcome.REVIEW_REQUIRED, result.interpretation);
                }
                checkRunning(cancelled);
                row.put("source_sha", sourceHash);
                if (revision >= 0) row.put("source_revision", revision);
                row.put("decisions_sha", NcnJson.hash(decisionsText.getBytes(StandardCharsets.UTF_8)));
                baselineHash = sourceHash;
                baselineBytes = bytes;
                String interpretation = result.interpretation == null ? null : result.interpretation.toString();
                outcome = result.outcome;
                if (interpretation != null) {
                    byte[] encoded = interpretation.getBytes(StandardCharsets.UTF_8);
                    if (encoded.length <= MAX_INTERPRETATION_BYTES) {
                        row.put("interpretation_sha", NcnJson.hash(encoded));
                        row.put("interpretation", interpretation);
                    } else outcome = Outcome.TOO_LARGE;
                }
                if (decisionsText.getBytes(StandardCharsets.UTF_8).length <= MAX_INTERPRETATION_BYTES) {
                    row.put("decisions", decisionsText);
                }
            }
        }
        row.put("outcome", outcome.name());
        checkRunning(cancelled);
        File staged = stageBaseline(baselineBytes, id, baselineHash);
        try {
            synchronized (JourneyStore.class) {
                checkRunning(cancelled);
                if (!token.equals(JourneyStore.fileToken(source))) return false;
                // A crash after the rename and before the SQLite commit leaves a
                // content-addressed copy that the next run can reuse.
                if (staged != null) {
                    java.nio.file.Files.move(staged.toPath(), baselineFile(id, baselineHash).toPath(),
                            java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
                db.beginTransaction();
                try {
                    db.insertWithOnConflict("receipts", null, row, SQLiteDatabase.CONFLICT_IGNORE);
                    if (!hasReceipt(key)) throw new IOException("Could not persist journey interpretation receipt");
                    db.delete("pending", "version=? AND journey=?", new String[]{rule.version(), id});
                    db.setTransactionSuccessful();
                } finally {
                    db.endTransaction();
                }
            }
        } finally {
            if (staged != null) staged.delete();
        }
        return true;
    }

    private boolean hasReceipt(String[] key) {
        try (Cursor cursor = db.rawQuery("SELECT 1 FROM receipts WHERE version=? AND journey=? AND token=?", key)) {
            return cursor.moveToFirst();
        }
    }

    JSONObject receipt(Rule rule, String id) throws Exception {
        try (Cursor cursor = db.rawQuery("SELECT source_revision,source_sha,decisions_sha,"
                + "interpretation_sha,outcome,interpretation,decisions FROM receipts"
                + " WHERE version=? AND journey=? AND token=?", new String[]{rule.version(), id,
                JourneyStore.fileToken(JourneyStore.archiveFile(context, id))})) {
            if (!cursor.moveToFirst()) return null;
            JSONObject receipt = new JSONObject().put("version", rule.version()).put("journey", id);
            for (int i = 0; i < cursor.getColumnCount(); i++) {
                if (!cursor.isNull(i)) receipt.put(cursor.getColumnName(i), cursor.getString(i));
            }
            return receipt;
        }
    }

    private byte[] readBaseline(File source, BooleanSupplier cancelled) throws IOException {
        try (FileInputStream input = new FileInputStream(source);
                ByteArrayOutputStream output = new ByteArrayOutputStream((int)Math.min(source.length(), 64 * 1024))) {
            byte[] block = new byte[16 * 1024];
            int n;
            while ((n = input.read(block)) != -1) {
                checkRunning(cancelled);
                if (output.size() + n > MAX_BASELINE_BYTES) throw new CancellationException("Source changed");
                output.write(block, 0, n);
            }
            return output.toByteArray();
        }
    }

    private static String baselinePrefix(String id) {
        return NcnJson.hash(id.getBytes(StandardCharsets.UTF_8)) + "-";
    }

    private File baselineFile(String id, String sha) {
        return new File(baselines, baselinePrefix(id) + sha + ".json");
    }

    private File stageBaseline(byte[] bytes, String id, String sha) throws IOException {
        if (bytes == null || baselineFile(id, sha).isFile()) return null;
        File staged = File.createTempFile(baselinePrefix(id), ".tmp", baselines);
        try (FileOutputStream output = new FileOutputStream(staged)) {
            output.write(bytes);
            output.getFD().sync();
            return staged;
        } catch (IOException failure) {
            staged.delete();
            throw failure;
        }
    }

    /** Keep copies under the existing journey deletion lifecycle. Caller holds JourneyStore's lock. */
    static void forget(Context context, String id) {
        File directory = new File(context.getFilesDir(), STORE);
        String prefix = id == null ? null : baselinePrefix(id);
        File[] copies = directory.listFiles((dir, name) -> prefix == null || name.startsWith(prefix));
        if (copies != null) for (File copy : copies) {
            if (!copy.delete() && copy.exists()) throw new IllegalStateException("Could not delete journey baseline");
        }
        if (!context.getDatabasePath(DATABASE).isFile()) return;
        try (SQLiteDatabase database = context.openOrCreateDatabase(DATABASE, Context.MODE_PRIVATE, null)) {
            if (database.getVersion() == 0) return; // Interrupted before any receipt could be written.
            database.beginTransaction();
            try {
                String where = id == null ? null : "journey=?";
                String[] args = id == null ? null : new String[]{id};
                database.delete("pending", where, args);
                database.delete("receipts", where, args);
                database.setTransactionSuccessful();
            } finally {
                database.endTransaction();
            }
        }
    }

    private boolean shouldStop(BooleanSupplier cancelled) {
        return Thread.currentThread().isInterrupted() || cancelled.getAsBoolean()
                || CaptureService.isActive(context);
    }

    private void checkRunning(BooleanSupplier cancelled) {
        if (shouldStop(cancelled)) throw new CancellationException();
    }

    @Override public void close() { db.close(); }
}
