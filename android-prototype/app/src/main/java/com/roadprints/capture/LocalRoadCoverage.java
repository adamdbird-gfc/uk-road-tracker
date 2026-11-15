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
import java.util.concurrent.CancellationException;

/** Read-only journey attribution and disposable projection for the overview Map. */
final class LocalRoadCoverage implements AutoCloseable {
    private static final int MAX_INTERVALS_PER_JOURNEY = 4096;
    private static final int MAX_CONTRIBUTION_INTERVALS = 48_000;

    private final LocalRoadNetwork network;
    private final CompactJourneyCache cache;
    private final Map<Edge, Map<String, List<Interval>>> contributors = new LinkedHashMap<>();
    private int intervalCount;
    int reused, calculated, held;

    private LocalRoadCoverage(LocalRoadNetwork network, CompactJourneyCache cache) {
        this.network = network;
        this.cache = cache;
    }

    static LocalRoadCoverage open(Context context) {
        try (InputStream asset = context.getAssets().open(ASSET)) {
            LocalRoadNetwork network = LocalRoadNetwork.load(asset);
            try {
                return new LocalRoadCoverage(network, new CompactJourneyCache(context, CACHE_VERSION));
            } catch (RuntimeException invalidCache) {
                // Keep the legacy view usable and try opening the cache on a later preparation.
                return null;
            }
        } catch (Exception unavailable) {
            return null;
        }
    }

    static boolean eligible(JSONObject header) {
        JSONObject source = header.optJSONObject("source");
        return "driving".equals(header.optString("mode"))
                && source != null && "android_activity_capture".equals(source.optString("type"));
    }

    Map<Edge, List<Interval>> contribution(MapJourneyStore.Record record) {
        if (!eligible(record.header)) return Collections.emptyMap();
        try {
            int hits = cache.hits;
            JSONObject row = cache.get(record.source, file -> readContribution(network, file,
                    record.header.optString("started_at"), record.header.optString("ended_at")));
            if (!record.token.equals(JourneyStore.fileToken(record.source))) return Collections.emptyMap();
            if (cache.hits != hits) reused++;
            else calculated++;
            Map<Edge, List<Interval>> result = new LinkedHashMap<>();
            JSONArray spans = row.getJSONArray("spans");
            if (spans.length() > MAX_INTERVALS_PER_JOURNEY
                    || intervalCount + spans.length() > MAX_CONTRIBUTION_INTERVALS) return Collections.emptyMap();
            for (int i = 0; i < spans.length(); i++) {
                JSONArray span = spans.getJSONArray(i);
                Edge edge = network.edges.get(span.getString(0));
                int start = span.getInt(1), end = span.getInt(2);
                if (edge == null || !edge.eligible || start < 0 || end <= start || end > edge.mm) return Collections.emptyMap();
                result.computeIfAbsent(edge, ignored -> new ArrayList<>()).add(new Interval(start, end));
            }
            return result;
        } catch (CancellationException cancelled) {
            throw cancelled;
        } catch (Exception unavailable) {
            held++;
            return Collections.emptyMap();
        }
    }

    static JSONObject readContribution(LocalRoadNetwork network, File source, String started, String ended)
            throws Exception {
        long start = Instant.parse(started).toEpochMilli(), end = Instant.parse(ended).toEpochMilli();
        Map<Edge, List<Interval>> spans = new LinkedHashMap<>();
        JSONArray empty = new JSONArray();
        int stored = 0;
        try (JsonReader reader = new JsonReader(new InputStreamReader(
                new java.io.FileInputStream(source), StandardCharsets.UTF_8))) {
            reader.beginObject();
            while (reader.hasNext()) {
                String field = reader.nextName();
                if ("journey_corrections".equals(field) && reader.peek() == JsonToken.BEGIN_OBJECT) {
                    // Edits are authoritative. The original samples have no mapping to removed edit indices.
                    if (hasRemovedRoadEvidence(reader)) return new JSONObject().put("spans", empty);
                } else if ("raw_capture_samples".equals(field) && reader.peek() == JsonToken.BEGIN_ARRAY) {
                    reader.beginArray();
                    Fix previous = null;
                    while (reader.hasNext()) {
                        if (Thread.currentThread().isInterrupted()) throw new CancellationException();
                        Fix fix = readFix(reader, network, start, end);
                        for (Span span : network.observe(previous, fix)) {
                            spans.computeIfAbsent(span.edge(), ignored -> new ArrayList<>()).add(span.interval());
                            if (++stored > MAX_INTERVALS_PER_JOURNEY * 8) return new JSONObject().put("spans", empty);
                        }
                        previous = fix;
                    }
                    reader.endArray();
                } else reader.skipValue();
            }
            reader.endObject();
        }
        JSONArray output = new JSONArray();
        for (Map.Entry<Edge, List<Interval>> section : spans.entrySet()) {
            for (Interval range : union(section.getValue())) {
                if (output.length() == MAX_INTERVALS_PER_JOURNEY) return new JSONObject().put("spans", empty);
                output.put(new JSONArray().put(section.getKey().id()).put(range.start()).put(range.end()));
            }
        }
        return new JSONObject().put("spans", output);
    }

    private static boolean hasRemovedRoadEvidence(JsonReader reader) throws Exception {
        reader.beginObject();
        while (reader.hasNext()) {
            String name = reader.nextName();
            if ((name.equals("removed_matched_segments") || name.equals("removed_road_ids"))
                    && reader.peek() == JsonToken.BEGIN_ARRAY) {
                reader.beginArray();
                if (reader.hasNext()) return true;
                reader.endArray();
            } else reader.skipValue();
        }
        reader.endObject();
        return false;
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
            Map<String, List<Interval>> history = contributors.computeIfAbsent(section.getKey(),
                    ignored -> new LinkedHashMap<>());
            List<Interval> previous = history.put(journeyId, section.getValue());
            intervalCount += section.getValue().size() - (previous == null ? 0 : previous.size());
        }
    }

    List<Section> sections() throws Exception {
        List<Section> sections = new ArrayList<>();
        for (Map.Entry<Edge, Map<String, List<Interval>>> entry : contributors.entrySet()) {
            Edge edge = entry.getKey();
            sections.add(new Section(edge.id(), edge.label, edge.mm, network.coordinates(edge.a),
                    network.coordinates(edge.b), entry.getValue()));
        }
        return sections;
    }

    record Line(Section section, Interval interval, JSONArray points) {
        List<String> contributors(double fraction) {
            int offset = interval.start() + (int) Math.round(fraction * (interval.end() - interval.start()));
            List<String> ids = new ArrayList<>();
            for (Map.Entry<String, List<Interval>> journey : section.history.entrySet()) {
                if (journey.getValue().stream().anyMatch(span -> span.start() <= offset && offset <= span.end()))
                    ids.add(journey.getKey());
            }
            return ids;
        }
    }

    static final class Section {
        final String id, label;
        final int length;
        final JSONArray first, last;
        final Map<String, List<Interval>> history;
        final List<Line> lines;
        final long uniqueMillimetres;

        Section(String id, String label, int length, JSONArray first, JSONArray last,
                Map<String, List<Interval>> history) throws Exception {
            this.id = id;
            this.label = label;
            this.length = length;
            this.first = first;
            this.last = last;
            this.history = history;
            List<Interval> intervals = new ArrayList<>();
            for (List<Interval> values : history.values()) intervals.addAll(values);
            lines = new ArrayList<>();
            long unique = 0;
            for (Interval interval : union(intervals)) {
                lines.add(new Line(this, interval, new JSONArray()
                        .put(interpolate(first, last, interval.start() / (double) length))
                        .put(interpolate(first, last, interval.end() / (double) length))));
                unique += interval.end() - interval.start();
            }
            uniqueMillimetres = unique;
        }

        JSONObject encode() throws Exception {
            JSONArray journeys = new JSONArray();
            for (Map.Entry<String, List<Interval>> journey : history.entrySet()) {
                JSONArray row = new JSONArray().put(journey.getKey());
                for (Interval interval : journey.getValue()) row.put(interval.start()).put(interval.end());
                journeys.put(row);
            }
            return new JSONObject().put("id", id).put("label", label).put("length", length)
                    .put("first", first).put("last", last).put("journeys", journeys);
        }

        static Section decode(JSONObject data) throws Exception {
            Map<String, List<Interval>> history = new LinkedHashMap<>();
            int length = data.getInt("length");
            JSONArray journeys = data.getJSONArray("journeys");
            for (int i = 0; i < journeys.length(); i++) {
                JSONArray row = journeys.getJSONArray(i);
                List<Interval> intervals = new ArrayList<>();
                for (int j = 1; j < row.length(); j += 2) {
                    int start = row.getInt(j), end = row.getInt(j + 1);
                    if (start < 0 || start >= end || end > length) throw new IllegalArgumentException("Invalid coverage");
                    intervals.add(new Interval(start, end));
                }
                history.put(row.getString(0), intervals);
            }
            return new Section(data.getString("id"), data.getString("label"), length,
                    data.getJSONArray("first"), data.getJSONArray("last"), history);
        }
    }

    JSONArray remainingLines(JSONArray rows, Map<Edge, List<Interval>> accepted) throws Exception {
        if (accepted.isEmpty()) return rows;
        JSONArray remaining = new JSONArray();
        for (int row = 0; row < rows.length(); row++) {
            JSONObject original = rows.getJSONObject(row);
            JSONArray points = original.getJSONArray("points");
            List<JSONArray> fragments = remainingFragments(network, points, accepted);
            for (JSONArray fragment : fragments) {
                remaining.put(fragment == points ? original
                        : new JSONObject().put("points", fragment).put("original", fragment.length()));
            }
        }
        return remaining;
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
        try { cache.close(); }
        catch (android.database.sqlite.SQLiteException optional) {
            android.util.Log.w("RoadprintsTiming", "local_road_cache_close=failed");
        }
    }
}
