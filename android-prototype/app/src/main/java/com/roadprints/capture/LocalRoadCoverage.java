package com.roadprints.capture;

import static com.roadprints.capture.LocalRoadNetwork.*;

import android.content.Context;
import android.util.JsonReader;
import android.util.JsonToken;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;

/** Read-only journey attribution and disposable projection for the overview Map. */
final class LocalRoadCoverage implements AutoCloseable {
    private static final int MAX_INTERVALS_PER_JOURNEY = 4096;
    private static final int MAX_CONTRIBUTION_INTERVALS = 600_000;

    private final RoadReference reference;
    // Android's asset stream can delegate getChannel() to a different stream.
    // Retain the outer owner so its finalizer cannot close an active reference.
    private final java.io.FileInputStream referenceStream;
    private final CompactJourneyCache cache;
    private final Map<Edge, Map<String, int[]>> contributors = new LinkedHashMap<>();
    // Network-identified change journal: one pending entry per physical edge,
    // never one retained polyline per historical journey.
    private final java.util.LinkedHashSet<Edge> changedEdges = new java.util.LinkedHashSet<>();
    private static final int MAX_CHANGED_EDGES = 4_096;
    private boolean incrementalTracking;
    private boolean deltaOverflow;
    private long changeRevision;

    /** Opt-in only: ordinary map loads must not retain another edge index. */
    void enableIncrementalTracking() {
        incrementalTracking = true;
        deltaOverflow = false;
        changedEdges.clear();
    }

    boolean deltaOverflowed() { return deltaOverflow; }
    private int intervalCount;
    int reused, calculated, held;
    private final Map<String, Integer> failureCounts = new LinkedHashMap<>();

    private LocalRoadCoverage(RoadReference reference, CompactJourneyCache cache,
                              java.io.FileInputStream referenceStream) {
        this.reference = reference;
        this.referenceStream = referenceStream;
        this.cache = cache;
    }

    static LocalRoadCoverage open(Context context) {
        try {
            android.content.res.AssetFileDescriptor asset = context.getAssets().openFd(RoadReference.ASSET);
            java.io.FileInputStream stream = asset.createInputStream();
            try {
                RoadReference reference = new RoadReference(stream.getChannel(), asset.getStartOffset(),
                        asset.getDeclaredLength());
                return new LocalRoadCoverage(reference,
                        new CompactJourneyCache(context, RoadReference.ALGORITHM + "-" + reference.version), stream);
            } catch (Exception unavailable) {
                stream.close();
                throw unavailable;
            }
        } catch (Exception unavailable) {
            PerformanceDiagnostics.timing("map_local_road_reference_unavailable", 0,
                    "reason=" + failureSignature(unavailable));
            return null;
        }
    }

    record Prepared(Map<Edge, List<Interval>> spans, Map<Edge, List<Interval>> majorSpans,
                    JSONArray residual) {}
    int overBudget;
    long unresolvedMm, excludedMm, majorOrOtherMm;
    final Map<String, Integer> unresolvedLegReasons = new LinkedHashMap<>();
    final Map<String, Integer> footUnresolvedLegReasons = new LinkedHashMap<>();
    long footUnresolvedMm, footMajorOrOtherMm;
    int footMajorIntervals;
    int footResidualLines, footResidualHeldNear, footResidualHeldLong, footResidualTruncated;

    /** Reuse the already-open UK index; never decode a tile to classify a journey. */
    boolean hasUkReferenceTile(double longitude, double latitude) {
        return reference.hasIndexedTileAt(longitude, latitude);
    }

    RoadReference.Statistics referenceStatistics() {
        return reference.statistics();
    }

    Prepared contribution(MapJourneyStore.Record record) {
        if (!AcceptedRoadRoute.eligibleMode(record.header.optString("mode"))) return null;
        String phase = "read_or_compute";
        try {
            int hits = cache.hits;
            boolean onFoot = JourneyFilters.isFootMode(record.header.optString("mode"));
            JSONObject row = cache.getRoad(record.source, file -> readAcceptedContribution(reference, file), onFoot);
            if (row == null || !record.token.equals(JourneyStore.fileToken(record.source))) return null;
            boolean refreshedLegacyFootRow = false;
            // Older disposable rows contain covered intervals but no reason
            // breakdown. Reassess only on-foot rows to learn why saved paths
            // remain invisible; do not invalidate the 660 driving contributions.
            if (JourneyFilters.isFootMode(record.header.optString("mode"))
                    && (!row.has("unresolvedLegs") || !row.has("majorSpans")
                            || row.optInt("footResidualVersion", 0) < FootpathResidualGeometry.VERSION)
                    && row.has("spans")) {
                cache.invalidate(record.source);
                refreshedLegacyFootRow = true;
                row = cache.getRoad(record.source, file -> readAcceptedContribution(reference, file), onFoot);
                if (row == null) return null;
            }
            // Earlier builds incorrectly stored {} for a non-projectable route.
            // Reassess that legacy row once, then persist a typed hold marker.
            if (row.length() == 0) {
                cache.invalidate(record.source);
                refreshedLegacyFootRow = true;
                row = cache.getRoad(record.source, file -> readAcceptedContribution(reference, file), onFoot);
                if (row == null) return null;
            }
            boolean reusedRow = cache.hits != hits && !refreshedLegacyFootRow;
            // A known, deterministic projection hold is itself a valid derived
            // result. Reuse it instead of repeatedly decoding thousands of
            // matched legs, but never grant road coverage for this source.
            String holdReason = row.optString("heldReason", "");
            if (!holdReason.isEmpty()) {
                if (!"no_accepted_projection".equals(holdReason)
                        && !"interval_limit".equals(holdReason))
                    throw new IllegalArgumentException("Unsupported derived hold reason");
                held++;
                failureCounts.merge("cached:" + holdReason, 1, Integer::sum);
                if (reusedRow) reused++; else calculated++;
                return null;
            }
            phase = "decode_contribution";
            JSONArray residual = row.getJSONArray("residual");
            Map<Edge, List<Interval>> result = new LinkedHashMap<>();
            JSONArray spans = row.getJSONArray("spans");
            int count = row.getInt("intervals");
            if (count < 0 || count > MAX_INTERVALS_PER_JOURNEY)
                throw new IllegalArgumentException("Invalid local-road interval count");
            if (intervalCount + count > MAX_CONTRIBUTION_INTERVALS) {
                overBudget++;
                return null;
            }
            int decodedCount = 0;
            for (int i = 0; i < spans.length(); i++) {
                JSONArray span = spans.getJSONArray(i);
                long way = span.getLong(0), from = span.getLong(1), to = span.getLong(2);
                Point a = RoadReference.point(span.getDouble(4), span.getDouble(5));
                Point b = RoadReference.point(span.getDouble(6), span.getDouble(7));
                Edge edge = new Edge(way, from, to, span.getString(3), a, b, 0, true,
                        span.getInt(8), -1, cachedRoadRef(span), cachedFootOnly(span));
                List<Interval> intervals = decodeCachedIntervals(span, edge.mm);
                if (result.put(edge, intervals) != null)
                    throw new IllegalArgumentException("Duplicate cached local-road section");
                decodedCount += intervals.size();
            }
            if (decodedCount != count)
                throw new IllegalArgumentException("Invalid cached local-road interval total");
            long unresolved = row.getLong("unresolvedMm"), excluded = row.getLong("excludedMm");
            if (unresolved < 0 || excluded < 0)
                throw new IllegalArgumentException("Invalid cached local-road distance");
            unresolvedMm += unresolved;
            excludedMm += excluded;
            long major = row.optLong("majorOrOtherMm", 0);
            majorOrOtherMm += major;
            JSONArray retainedMajor = onFoot ? row.optJSONArray("majorSpans") : null;
            int majorCount = onFoot ? row.optInt("majorIntervals", 0) : 0;
            if (majorCount < 0 || majorCount > MAX_INTERVALS_PER_JOURNEY
                    || (retainedMajor == null && majorCount != 0))
                throw new IllegalArgumentException("Invalid major-road attribution count");
            Map<Edge, List<Interval>> majorResult = new LinkedHashMap<>();
            if (retainedMajor != null) {
                int validated = 0;
                for (int i = 0; i < retainedMajor.length(); i++) {
                    JSONArray span = retainedMajor.getJSONArray(i);
                    int sectionMm = span.getInt(8);
                    long way = span.getLong(0), from = span.getLong(1), to = span.getLong(2);
                    Edge edge = new Edge(way, from, to, span.getString(3),
                            RoadReference.point(span.getDouble(4), span.getDouble(5)),
                            RoadReference.point(span.getDouble(6), span.getDouble(7)),
                            0, false, sectionMm, -1, cachedRoadRef(span), cachedFootOnly(span));
                    List<Interval> intervals = decodeCachedIntervals(span, sectionMm);
                    if (majorResult.put(edge, intervals) != null)
                        throw new IllegalArgumentException("Duplicate accepted major-road section");
                    validated += intervals.size();
                }
                if (validated != majorCount)
                    throw new IllegalArgumentException("Invalid major-road interval total");
            }
            if (onFoot) {
                footUnresolvedMm += unresolved;
                footMajorOrOtherMm += major;
                footMajorIntervals += majorCount;
                footResidualLines += residual.length();
                footResidualHeldNear += row.optInt("footResidualHeldNear", 0);
                footResidualHeldLong += row.optInt("footResidualHeldLong", 0);
                if (row.optBoolean("footResidualTruncated", false)) footResidualTruncated++;
            }
            JSONObject reasons = row.optJSONObject("unresolvedLegs");
            if (reasons != null) {
                java.util.Iterator<String> keys = reasons.keys();
                while (keys.hasNext()) {
                    String reason = keys.next();
                    int occurrences = reasons.optInt(reason, 0);
                    if (occurrences < 0) throw new IllegalArgumentException("Negative unresolved reason count");
                    unresolvedLegReasons.merge(reason, occurrences, Integer::sum);
                    if (onFoot) footUnresolvedLegReasons.merge(reason, occurrences, Integer::sum);
                }
            }
            if (reusedRow) reused++;
            else calculated++;
            return new Prepared(result, majorResult, residual);
        } catch (CancellationException cancelled) {
            throw cancelled;
        } catch (Exception unavailable) {
            if (Thread.currentThread().isInterrupted()) throw new CancellationException();
            // A malformed derived row should never poison the next Map refresh.
            // The original journey remains authoritative and is never modified.
            if ("decode_contribution".equals(phase)) {
                try { cache.invalidate(record.source); }
                catch (android.database.sqlite.SQLiteException ignored) {
                    // Continue reporting the original failure, not a cache-cleanup failure.
                }
            }
            held++;
            String signature = phase + ":" + failureSignature(unavailable);
            if (!failureCounts.containsKey(signature) && failureCounts.size() >= 8) signature = "other";
            failureCounts.merge(signature, 1, Integer::sum);
            return null;
        }
    }

    /** Validates disposable cache rows before they may suppress legacy route geometry. */
    static List<Interval> decodeCachedIntervals(JSONArray span, int sectionMm)
            throws org.json.JSONException {
        int first = span != null && span.opt(9) instanceof String ? 11 : 9;
        if (sectionMm <= 0 || span == null || span.length() < first + 2
                || (span.length() - first) % 2 != 0)
            throw new IllegalArgumentException("Invalid cached local-road interval format");
        List<Interval> intervals = new ArrayList<>();
        for (int offset = first; offset < span.length(); offset += 2) {
            int start = span.getInt(offset), end = span.getInt(offset + 1);
            if (start < 0 || end <= start || end > sectionMm)
                throw new IllegalArgumentException("Invalid cached local-road interval bounds");
            intervals.add(new Interval(start, end));
        }
        return intervals;
    }

    private static String cachedRoadRef(JSONArray row) {
        return row.opt(9) instanceof String ? row.optString(9, "") : "";
    }

    private static boolean cachedFootOnly(JSONArray row) {
        return row.opt(9) instanceof String && row.optBoolean(10, false);
    }

    String failureBreakdown() {
        return failureCounts.toString();
    }

    private static String failureSignature(Exception failure) {
        String origin = "unknown";
        for (StackTraceElement frame : failure.getStackTrace()) {
            String owner = frame.getClassName();
            if (!owner.startsWith("com.roadprints.capture.")) continue;
            origin = owner.substring(owner.lastIndexOf('.') + 1) + "." + frame.getMethodName();
            break;
        }
        // Exception messages can contain journey paths or coordinate data.
        return failure.getClass().getSimpleName() + "@" + origin;
    }

    static JSONObject readAcceptedContribution(RoadReference reference, File source) throws Exception {
        JSONObject journey = new JSONObject();
        try (JsonReader reader = journeyReader(source)) {
            reader.beginObject();
            while (reader.hasNext()) {
                String field = reader.nextName();
                if ("processing_result".equals(field) && reader.peek() == JsonToken.BEGIN_OBJECT) {
                    JSONObject result = new JSONObject();
                    reader.beginObject();
                    while (reader.hasNext()) {
                        String key = reader.nextName();
                        if ("geojson".equals(key) || "status".equals(key))
                            result.put(key, JourneyStore.readEvidenceValue(reader));
                        else reader.skipValue();
                    }
                    reader.endObject();
                    journey.put(field, result);
                } else if ("mode".equals(field) || "processing_status".equals(field)
                        || "journey_corrections".equals(field)) {
                    journey.put(field, JourneyStore.readEvidenceValue(reader));
                } else reader.skipValue();
            }
            reader.endObject();
        }
        AcceptedRoadRoute.Projection projection;
        try {
            projection = AcceptedRoadRoute.project(reference, journey);
        } catch (IllegalArgumentException limit) {
            if ("Matched route exceeds per-journey interval limit".equals(limit.getMessage())
                    || "Accepted route exceeds interval limit".equals(limit.getMessage()))
                return heldContribution("interval_limit");
            throw limit;
        }
        if (projection == null) return heldContribution("no_accepted_projection");
        try {
        int count = 0;
        JSONArray spans = new JSONArray();
        for (Map.Entry<Edge, List<Interval>> section : projection.sections().entrySet()) {
            Edge edge = section.getKey();
            count += section.getValue().size();
            if (count > MAX_INTERVALS_PER_JOURNEY)
                throw new IllegalArgumentException("Matched route exceeds per-journey interval limit");
            JSONArray row = new JSONArray().put(edge.way).put(edge.from).put(edge.to).put(edge.label)
                    .put(ukLongitude(edge.a)).put(ukLatitude(edge.a)).put(ukLongitude(edge.b))
                    .put(ukLatitude(edge.b)).put(edge.mm)
                    .put(edge.roadRef).put(edge.footOnly);
            for (Interval interval : section.getValue()) row.put(interval.start()).put(interval.end());
            spans.put(row);
        }
        // Non-local canonical sections retain their physical OSM identity and
        // clipped intervals for reconciliation. They are NOT drawn as local roads.
        JSONArray majorSpans = new JSONArray();
        int majorCount = 0;
        for (Map.Entry<Edge, List<Interval>> section : projection.majorSections().entrySet()) {
            Edge edge = section.getKey();
            majorCount += section.getValue().size();
            if (count + majorCount > MAX_INTERVALS_PER_JOURNEY)
                throw new IllegalArgumentException("Accepted route exceeds interval limit");
            JSONArray row = new JSONArray().put(edge.way).put(edge.from).put(edge.to).put(edge.label)
                    .put(ukLongitude(edge.a)).put(ukLatitude(edge.a)).put(ukLongitude(edge.b))
                    .put(ukLatitude(edge.b)).put(edge.mm)
                    .put(edge.roadRef).put(edge.footOnly);
            for (Interval interval : section.getValue()) row.put(interval.start()).put(interval.end());
            majorSpans.put(row);
        }
        // Walking/running unresolved fragments are display-only evidence. The
        // canonical road/NCN ledgers receive ONLY the proven spans above.
        // Hold ambiguous and adjoining road fragments instead of drawing a
        // second physical road or claiming unverified completion.
        boolean onFoot = JourneyFilters.isFootMode(journey.optString("mode", ""));
        FootpathResidualGeometry.Projection footResidual = null;
        if (onFoot && !projection.residual().isEmpty()) {
            Set<Edge> nearby = new java.util.HashSet<>();
            footResidual = FootpathResidualGeometry.extract(projection.residual(),
                    (ax, ay, bx, by) -> {
                        double longitude = (ax + bx) / 2, latitude = (ay + by) / 2;
                        // No UK tile: this belongs to the existing international
                        // fallback instead. Do not double-render it as a UK path.
                        if (!reference.hasIndexedTileAt(longitude, latitude)) return true;
                        reference.collectNearby(ax, ay, bx, by, nearby);
                        return !nearby.isEmpty();
                    });
        }
        JSONObject cached = new JSONObject().put("spans", spans).put("intervals", count)
                .put("majorSpans", majorSpans).put("majorIntervals", majorCount)
                .put("residual", footResidual == null ? new JSONArray() : footResidual.lines())
                .put("unresolvedMm", projection.unresolvedMm()).put("excludedMm", projection.excludedMm())
                .put("majorOrOtherMm", projection.majorOrOtherMm())
                .put("unresolvedLegs", new JSONObject(projection.unresolvedLegs()));
        if (onFoot) {
            cached.put("footResidualVersion", FootpathResidualGeometry.VERSION);
            cached.put("footResidualTruncated", footResidual != null && footResidual.truncated());
            cached.put("footResidualHeldNear", footResidual == null ? 0 : footResidual.heldNearby());
            cached.put("footResidualHeldLong", footResidual == null ? 0 : footResidual.heldLong());
        }
        return cached;
        } catch (IllegalArgumentException limit) {
            if ("Matched route exceeds per-journey interval limit".equals(limit.getMessage())
                    || "Accepted route exceeds interval limit".equals(limit.getMessage()))
                return heldContribution("interval_limit");
            throw limit;
        }
    }

    /** Cached negative assessment is not successful road coverage. */
    private static JSONObject heldContribution(String reason) throws org.json.JSONException {
        return new JSONObject().put("heldReason", reason);
    }

    static JSONObject readContribution(LocalRoadNetwork network, File source, String started, String ended)
            throws Exception {
        long start = Instant.parse(started).toEpochMilli(), end = Instant.parse(ended).toEpochMilli();
        Map<Edge, List<Interval>> spans = new LinkedHashMap<>();
        JSONObject corrections = null;
        JSONArray output = new JSONArray();
        int stored = 0;
        try (JsonReader reader = journeyReader(source)) {
            reader.beginObject();
            while (reader.hasNext()) {
                String field = reader.nextName();
                if ("journey_corrections".equals(field) && reader.peek() == JsonToken.BEGIN_OBJECT) {
                    corrections = (JSONObject) JourneyStore.readEvidenceValue(reader);
                } else if ("raw_capture_samples".equals(field) && reader.peek() == JsonToken.BEGIN_ARRAY) {
                    reader.beginArray();
                    Fix previous = null;
                    while (reader.hasNext()) {
                        if (Thread.currentThread().isInterrupted()) throw new CancellationException();
                        Fix fix = readFix(reader, network, start, end);
                        for (Span span : network.observe(previous, fix)) {
                            spans.computeIfAbsent(span.edge(), ignored -> new ArrayList<>()).add(span.interval());
                            if (++stored > MAX_INTERVALS_PER_JOURNEY * 8)
                                return new JSONObject().put("spans", output);
                        }
                        previous = fix;
                    }
                    reader.endArray();
                } else reader.skipValue();
            }
            reader.endObject();
        }

        // Original observations keep their own evidence gates. The matched route
        // supplies the location of explicit exclusions only.
        if (!spans.isEmpty() && corrections != null) applyRemovals(network, source, corrections, spans);
        for (Map.Entry<Edge, List<Interval>> section : spans.entrySet()) {
            for (Interval range : union(section.getValue())) {
                if (output.length() == MAX_INTERVALS_PER_JOURNEY)
                    return new JSONObject().put("spans", new JSONArray());
                output.put(new JSONArray().put(section.getKey().id()).put(range.start()).put(range.end()));
            }
        }
        return new JSONObject().put("spans", output);
    }

    private static JsonReader journeyReader(File source) throws Exception {
        return new JsonReader(new InputStreamReader(new java.io.FileInputStream(source), StandardCharsets.UTF_8));
    }

    private static void applyRemovals(LocalRoadNetwork network, File source, JSONObject corrections,
                                      Map<Edge, List<Interval>> spans) throws Exception {
        Set<Integer> indices = JourneyCorrectionUtils.removedEdgeIndices(corrections);
        Set<String> legacyIds = JourneyCorrectionUtils.savedRoadIds(corrections);
        JSONArray savedIndices = corrections.optJSONArray("removed_matched_segments");
        boolean hasIndices = savedIndices != null && savedIndices.length() > 0;
        if (!hasIndices && legacyIds.isEmpty()) return;
        List<RouteRemoval.Segment> selected = hasIndices ? RouteRemoval.selected(
                JourneyMapEditorActivity.matchedRouteSegments(JourneyStore.matchedResult(source)), indices) : Collections.emptyList();
        if (!hasIndices || selected.size() != indices.size() || indices.isEmpty()) {
            // ID-only legacy records and corrupt indices have no physical scope.
            // Keep other named roads when legacy identifiers can bound the uncertainty.
            spans.keySet().removeIf(edge -> legacyIds.isEmpty() || edge.label.isBlank()
                    || legacyIds.contains("name:" + edge.label.trim().toLowerCase(java.util.Locale.ROOT).replaceAll("\\s+", " "))
                    || legacyIds.contains("ref:" + edge.label.replaceAll("\\s+", "").toUpperCase(java.util.Locale.ROOT)));
        }
        for (RouteRemoval.Segment geometry : selected) {
            if (Thread.currentThread().isInterrupted()) throw new CancellationException();
            network.applyRemoval(new RouteRemoval.Segment(network.point(geometry.a().x(), geometry.a().y()),
                    network.point(geometry.b().x(), geometry.b().y())), spans);
        }
    }

    private static Fix readFix(JsonReader reader, LocalRoadNetwork network, long start, long end) throws Exception {
        if (reader.peek() != JsonToken.BEGIN_ARRAY) {
            reader.skipValue();
            return null;
        }
        reader.beginArray();
        double lon = Double.NaN, lat = Double.NaN, accuracy = Double.NaN;
        long time = 0;
        boolean provider = false;
        int column = 0;
        while (reader.hasNext()) {
            JsonToken token = reader.peek();
            if (column == 0 && token == JsonToken.NUMBER) lon = reader.nextDouble();
            else if (column == 1 && token == JsonToken.NUMBER) lat = reader.nextDouble();
            else if (column == 2 && token == JsonToken.NUMBER) accuracy = reader.nextDouble();
            else if (column == 3 && token == JsonToken.NUMBER) {
                try { time = Long.parseLong(reader.nextString()); }
                catch (NumberFormatException invalid) { time = 0; }
            } else if (column == 5 && token == JsonToken.STRING) {
                String value = reader.nextString();
                provider = value.equals("gps") || value.equals("fused");
            } else reader.skipValue();
            column++;
        }
        reader.endArray();
        if (!(lon >= -180 && lon <= 180 && lat >= -90 && lat <= 90 && provider
                && time > 0 && time >= start && time <= end)) return null;
        return new Fix(network.point(lon, lat), time, accuracy);
    }

    void add(String journeyId, Map<Edge, List<Interval>> contribution) {
        for (Map.Entry<Edge, List<Interval>> section : contribution.entrySet()) {
            Map<String, int[]> history = contributors.computeIfAbsent(section.getKey(),
                    ignored -> new LinkedHashMap<>());
            int[] next = endpoints(section.getValue());
            int[] previous = history.put(journeyId, next);
            intervalCount += section.getValue().size() - (previous == null ? 0 : previous.length / 2);
            if (!java.util.Arrays.equals(previous, next)) {
                changeRevision++;
                if (incrementalTracking && !deltaOverflow) {
                    changedEdges.add(section.getKey());
                    if (changedEdges.size() > MAX_CHANGED_EDGES) {
                        // Safety fallback: never let preview bookkeeping compete
                        // with authoritative journey/road accumulation.
                        deltaOverflow = true;
                        changedEdges.clear();
                    }
                }
            }
        }
    }

    /** Monotonic revision of accepted canonical-edge contribution changes. */
    long changeRevision() { return changeRevision; }

    int pendingChangedEdges() { return changedEdges.size(); }

    /**
     * Read at most max distinct changed canonical edges. Each immutable
     * section is reconstructed from the CURRENT accumulated contribution
     * history, never from raw GPS/individual journey polylines. Revisiting a
     * physical edge coalesces its pending change instead of appending a
     * duplicate. The caller must replace an earlier preview for the same
     * section id; these deltas are not append-only.
     *
     * Only call from the serial archive worker; this journal is not thread-safe.
     */
    List<Section> drainChangedSections(int max) throws Exception {
        if (!incrementalTracking || deltaOverflow || max <= 0 || changedEdges.isEmpty())
            return java.util.Collections.emptyList();
        List<Section> result = new ArrayList<>(Math.min(max, changedEdges.size()));
        java.util.Iterator<Edge> cursor = changedEdges.iterator();
        while (cursor.hasNext() && result.size() < max) {
            if (Thread.currentThread().isInterrupted()) throw new CancellationException();
            Edge edge = cursor.next();
            Map<String, int[]> current = contributors.get(edge);
            if (current == null) { cursor.remove(); continue; }
            // Copy mutable ledger arrays so the published Section cannot acquire
            // later contributions via a reference to the working accumulator.
            Map<String, int[]> snapshot = new LinkedHashMap<>();
            for (Map.Entry<String, int[]> row : current.entrySet())
                snapshot.put(row.getKey(), row.getValue().clone());
            Section section = new Section(edge.id(), edge.label, edge.mm,
                    new JSONArray().put(ukLongitude(edge.a)).put(ukLatitude(edge.a)),
                    new JSONArray().put(ukLongitude(edge.b)).put(ukLatitude(edge.b)), snapshot);
            result.add(section);
            cursor.remove();
        }
        return result;
    }

    /**
     * Drain compact, immutable draw-only canonical coverage updates.
     * Unlike drainChangedSections(), this does NOT copy journey IDs, JSON,
     * Section instances or per-journey interval arrays into the UI pipeline.
     * Serial archive worker only; no concurrent contributor mutations.
     */
    List<CanonicalPreviewGeometry> drainChangedPreview(int max) {
        if (!incrementalTracking || deltaOverflow || max <= 0 || changedEdges.isEmpty())
            return java.util.Collections.emptyList();
        List<CanonicalPreviewGeometry> result =
                new ArrayList<>(Math.min(max, changedEdges.size()));
        java.util.Iterator<Edge> cursor = changedEdges.iterator();
        while (cursor.hasNext() && result.size() < max) {
            if (Thread.currentThread().isInterrupted()) throw new CancellationException();
            Edge edge = cursor.next();
            Map<String, int[]> current = contributors.get(edge);
            if (current != null)
                result.add(CanonicalPreviewGeometry.from(edge, current));
            cursor.remove();
        }
        return result;
    }

    static int[] endpoints(List<Interval> intervals) {
        int[] result = new int[intervals.size() * 2];
        int i = 0;
        for (Interval interval : intervals) {
            result[i++] = interval.start();
            result[i++] = interval.end();
        }
        return result;
    }

    /** Exact count before draining, without materialising another collection. */
    int pendingSectionCount() { return contributors.size(); }

    /** Emits sections directly into the cumulative Map. Avoid a second 44k-item
     * temporary list of already retained Section objects during peak heap use. */
    void forEachSection(java.util.function.Consumer<Section> accept) throws Exception {
        for (Map.Entry<Edge, Map<String, int[]>> entry : contributors.entrySet()) {
            if (Thread.currentThread().isInterrupted()) throw new CancellationException();
            Edge edge = entry.getKey();
            accept.accept(new Section(edge.id(), edge.label, edge.mm,
                    new JSONArray().put(ukLongitude(edge.a)).put(ukLatitude(edge.a)),
                    new JSONArray().put(ukLongitude(edge.b)).put(ukLatitude(edge.b)),
                    entry.getValue()));
        }
    }

    /** End-of-scan ownership transfer: retain history once, release working edges and tiles. */
    void drainSections(java.util.function.Consumer<Section> accept) throws Exception {
        incrementalTracking = false;
        changedEdges.clear();
        reference.releaseDecodedTiles();
        var entries = contributors.entrySet().iterator();
        while (entries.hasNext()) {
            if (Thread.currentThread().isInterrupted()) throw new CancellationException();
            var entry = entries.next();
            Edge edge = entry.getKey();
            accept.accept(new Section(edge.id(), edge.label, edge.mm,
                    new JSONArray().put(ukLongitude(edge.a)).put(ukLatitude(edge.a)),
                    new JSONArray().put(ukLongitude(edge.b)).put(ukLatitude(edge.b)),
                    entry.getValue()));
            entries.remove();
        }
    }

    /** Retained for callers that explicitly need a standalone list. */
    List<Section> sections() throws Exception {
        List<Section> sections = new ArrayList<>();
        forEachSection(sections::add);
        return sections;
    }

    /** Compact physical-road interval. Geometry is materialised as JSON only for
     * the rare compatibility caller (e.g. replay), never for normal Map display.
     * The Section endpoints remain authoritative and are not rounded to floats. */
    static final class Line {
        private final Section section;
        private final Interval interval;

        Line(Section section, Interval interval) {
            this.section = section;
            this.interval = interval;
        }

        Section section() { return section; }
        Interval interval() { return interval; }

        /** Exact historical double-precision interpolation; no retained JSON. */
        JSONArray points() {
            double fractionStart = interval.start() / (double) section.length;
            double fractionEnd = interval.end() / (double) section.length;
            double lon = section.first.optDouble(0), lat = section.first.optDouble(1);
            double dx = section.last.optDouble(0) - lon;
            double dy = section.last.optDouble(1) - lat;
            try {
                return new JSONArray()
                        .put(new JSONArray().put(lon + dx * fractionStart).put(lat + dy * fractionStart))
                        .put(new JSONArray().put(lon + dx * fractionEnd).put(lat + dy * fractionEnd));
            } catch (org.json.JSONException invalid) {
                throw new IllegalStateException("Invalid derived road coordinate", invalid);
            }
        }

        double longitude(boolean last) {
            double fraction = (last ? interval.end() : interval.start()) / (double) section.length;
            double first = section.first.optDouble(0);
            return first + (section.last.optDouble(0) - first) * fraction;
        }

        double latitude(boolean last) {
            double fraction = (last ? interval.end() : interval.start()) / (double) section.length;
            double first = section.first.optDouble(1);
            return first + (section.last.optDouble(1) - first) * fraction;
        }

        /**
         * Total accepted traversal distance across the displayed physical
         * interval for journeys contributing at this precise tap location.
         * Repeated walks count towards travel distance, never extra unique
         * physical coverage. No original journey geometry is consulted.
         */
        long contributorMillimetresAt(double fraction) {
            int offset = interval.start() + (int) Math.round(fraction * (interval.end() - interval.start()));
            long total = 0;
            for (int[] endpoints : section.history.values()) {
                boolean contributesHere = false;
                for (int i = 0; i < endpoints.length; i += 2)
                    if (endpoints[i] <= offset && offset <= endpoints[i + 1]) {
                        contributesHere = true;
                        break;
                    }
                if (!contributesHere) continue;
                for (int i = 0; i < endpoints.length; i += 2)
                    total += Math.max(0, Math.min(interval.end(), endpoints[i + 1])
                            - Math.max(interval.start(), endpoints[i]));
            }
            return total;
        }

        List<String> contributors(double fraction) {
            int offset = interval.start() + (int) Math.round(fraction * (interval.end() - interval.start()));
            List<String> ids = new ArrayList<>();
            for (Map.Entry<String, int[]> journey : section.history.entrySet()) {
                int[] endpoints = journey.getValue();
                for (int i = 0; i < endpoints.length; i += 2) {
                    if (endpoints[i] <= offset && offset <= endpoints[i + 1]) {
                        ids.add(journey.getKey());
                        break;
                    }
                }
            }
            return ids;
        }
    }

    static final class Section {
        final String id, label;
        final int length;
        final JSONArray first, last;
        final Map<String, int[]> history;
        final List<Line> lines;
        final long uniqueMillimetres;

        Section(String id, String label, int length, JSONArray first, JSONArray last,
                Map<String, int[]> history) throws Exception {
            this.id = id;
            this.label = label;
            this.length = length;
            this.first = first;
            this.last = last;
            this.history = history;
            // The old union allocated one Interval object for every recorded
            // contributor span plus another list of merged Intervals. This runs
            // tens of thousands of times on the overview Map. Encode each
            // validated pair in one primitive long; signed sort orders start,
            // then end, exactly as LocalRoadNetwork.union() does.
            int count = 0;
            for (int[] values : history.values()) count += values.length / 2;
            long[] endpoints = new long[count];
            int cursor = 0;
            for (int[] values : history.values())
                for (int i = 0; i < values.length; i += 2)
                    endpoints[cursor++] = ((long) values[i] << 32) | (values[i + 1] & 0xffffffffL);
            java.util.Arrays.sort(endpoints);
            lines = new ArrayList<>();
            long unique = 0;
            int start = -1, end = -1;
            for (long encoded : endpoints) {
                int nextStart = (int) (encoded >>> 32), nextEnd = (int) encoded;
                if (start >= 0 && nextStart > end) {
                    lines.add(coveredLine(start, end));
                    unique += end - start;
                    start = -1;
                }
                if (start < 0) { start = nextStart; end = nextEnd; }
                else end = Math.max(end, nextEnd);
            }
            if (start >= 0) {
                lines.add(coveredLine(start, end));
                unique += end - start;
            }
            uniqueMillimetres = unique;
        }

        private Line coveredLine(int start, int end) {
            return new Line(this, new Interval(start, end));
        }

        JSONObject encode() throws Exception {
            JSONArray journeys = new JSONArray();
            for (Map.Entry<String, int[]> journey : history.entrySet()) {
                JSONArray row = new JSONArray().put(journey.getKey());
                for (int endpoint : journey.getValue()) row.put(endpoint);
                journeys.put(row);
            }
            return new JSONObject().put("id", id).put("label", label).put("length", length)
                    .put("first", first).put("last", last).put("journeys", journeys);
        }

        static Section decode(JSONObject data) throws Exception {
            Map<String, int[]> history = new LinkedHashMap<>();
            int length = data.getInt("length");
            JSONArray journeys = data.getJSONArray("journeys");
            for (int i = 0; i < journeys.length(); i++) {
                JSONArray row = journeys.getJSONArray(i);
                if (row.length() < 3 || row.length() % 2 != 1)
                    throw new IllegalArgumentException("Invalid contributor intervals");
                int[] intervals = new int[row.length() - 1];
                for (int j = 1; j < row.length(); j += 2) {
                    int start = row.getInt(j), end = row.getInt(j + 1);
                    if (start < 0 || start >= end || end > length) throw new IllegalArgumentException("Invalid coverage");
                    intervals[j - 1] = start;
                    intervals[j] = end;
                }
                history.put(row.getString(0), intervals);
            }
            return new Section(data.getString("id"), data.getString("label"), length,
                    data.getJSONArray("first"), data.getJSONArray("last"), history);
        }
    }

    static List<JSONArray> remainingFragments(LocalRoadNetwork network, JSONArray points,
                                               Map<Edge, List<Interval>> accepted) throws Exception {
        List<JSONArray> fragments = new ArrayList<>();
        JSONArray path = new JSONArray().put(points.getJSONArray(0));
        boolean changed = false;
        for (int i = 1; i < points.length(); i++) {
            if (Thread.currentThread().isInterrupted()) throw new CancellationException();
            JSONArray a = points.getJSONArray(i - 1), b = points.getJSONArray(i);
            List<double[]> clips = network.displayClips(network.point(a.getDouble(0), a.getDouble(1)),
                    network.point(b.getDouble(0), b.getDouble(1)), accepted);
            double cursor = 0;
            for (double[] clip : clips) {
                if (clip[1] <= cursor) continue;
                if (clip[0] > cursor + 1e-8) {
                    if (path == null) path = new JSONArray().put(interpolate(a, b, cursor));
                    path.put(interpolate(a, b, clip[0]));
                }
                if (path != null && path.length() >= 2) fragments.add(path);
                path = null;
                cursor = clip[1];
                changed = true;
            }
            if (cursor < 1 - 1e-8) {
                if (path == null) path = new JSONArray().put(interpolate(a, b, cursor));
                path.put(b);
            }
        }
        if (!changed) return Collections.singletonList(points);
        if (path != null && path.length() >= 2) fragments.add(path);
        return fragments;
    }

    private static JSONArray interpolate(JSONArray a, JSONArray b, double fraction) throws Exception {
        double x = a.getDouble(0), y = a.getDouble(1);
        return new JSONArray().put(x + (b.getDouble(0) - x) * fraction)
                .put(y + (b.getDouble(1) - y) * fraction);
    }

    @Override public void close() {
        try {
            reference.close();
        } catch (java.io.IOException optional) {
            android.util.Log.w("RoadprintsTiming", "local_road_reference_close=failed");
        }
        try { referenceStream.close(); }
        catch (java.io.IOException optional) {
            android.util.Log.w("RoadprintsTiming", "local_road_stream_close=failed");
        }
        try { cache.close(); }
        catch (android.database.sqlite.SQLiteException optional) {
            android.util.Log.w("RoadprintsTiming", "local_road_cache_close=failed");
        }
    }
}

