package com.roadprints.capture;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Android implementation of the Stage 1.2D tagged observed-pair policy. */
final class LocalRoadNetwork {
    static final String ASSET = "gravesend-tagged-v1.json";
    static final String SHA256 = "9c3ce6b58789df13f2019b69be50cb95aa56eacdf612b1606973cfaf08fe4aba";
    static final String CACHE_VERSION = "local-roads-v1-" + SHA256.substring(0, 16);
    private static final double LAT_SCALE = 111_195.08;
    private static final double HEADING = Math.cos(Math.toRadians(20));
    private static final double GRID = 40;
    private static final double RADIUS = 7;
    private static final double SEAM = .5;

    record Point(double x, double y) {
        Point minus(Point other) { return new Point(x - other.x, y - other.y); }
        double dot(Point other) { return x * other.x + y * other.y; }
        double cross(Point other) { return x * other.y - y * other.x; }
        double length() { return Math.hypot(x, y); }
    }

    record Fix(Point point, long time, double accuracy) {}
    record Interval(int start, int end) {}
    record Span(Edge edge, Interval interval) {}

    static final class Edge {
        final long way, from, to;
        final String label;
        final Point a, b, axis;
        final double length;
        final int mm;
        final Integer direction;
        final boolean eligible;

        Edge(long way, long from, long to, String label, Point a, Point b,
             Integer direction, boolean eligible) {
            this.way = way;
            this.from = from;
            this.to = to;
            this.label = label;
            this.a = a;
            this.b = b;
            axis = b.minus(a);
            length = axis.length();
            mm = (int) Math.round(length * 1000);
            this.direction = direction;
            this.eligible = eligible;
        }

        String id() { return "osm:" + way + ":" + Math.min(from, to) + ":" + Math.max(from, to); }

        Point at(double fraction) {
            return new Point(a.x + fraction * axis.x, a.y + fraction * axis.y);
        }

        long endpoint(double fraction) { return fraction < .5 ? from : to; }
    }

    private record Candidate(Edge edge, double start, double end, double entry, double exit) {
        boolean joins(Candidate next) {
            return edge.endpoint(exit) == next.edge.endpoint(next.entry)
                    && Math.abs(exit - Math.round(exit)) * edge.length <= SEAM
                    && Math.abs(next.entry - Math.round(next.entry)) * next.edge.length <= SEAM;
        }

        Span span() {
            int a = (int) Math.round(entry * edge.mm);
            int b = (int) Math.round(exit * edge.mm);
            return new Span(edge, new Interval(Math.min(a, b), Math.max(a, b)));
        }
    }

    private final double west, south, lonScale, width, height;
    private final Map<Long, List<Edge>> grid = new HashMap<>();
    final Map<String, Edge> edges = new HashMap<>();

    LocalRoadNetwork(double west, double south, double east, double north) {
        this.west = west;
        this.south = south;
        lonScale = LAT_SCALE * Math.cos(Math.toRadians((south + north) / 2));
        Point extent = point(east, north);
        width = extent.x;
        height = extent.y;
    }

    static LocalRoadNetwork load(InputStream asset) throws Exception {
        // Android's asset merger unpacks the repository's .gz input.
        byte[] json = readBounded(asset, 512 * 1024);
        if (!SHA256.equals(NcnJson.hash(json))) throw new IOException("Road reference checksum mismatch");
        JSONObject source = new JSONObject(new String(json, StandardCharsets.UTF_8));
        if (!"roadprints-gravesend-tagged-v1".equals(source.getString("format")))
            throw new IOException("Unsupported road reference");
        JSONObject box = source.getJSONObject("bounds");
        LocalRoadNetwork network = new LocalRoadNetwork(box.getDouble("minlon"), box.getDouble("minlat"),
                box.getDouble("maxlon"), box.getDouble("maxlat"));
        JSONObject nodes = source.getJSONObject("nodes");
        Map<Long, Point> points = new HashMap<>();
        for (java.util.Iterator<String> keys = nodes.keys(); keys.hasNext();) {
            String key = keys.next();
            JSONArray xy = nodes.getJSONArray(key);
            points.put(Long.parseLong(key), network.point(xy.getDouble(0) / 1e7, xy.getDouble(1) / 1e7));
        }
        JSONArray edges = source.getJSONArray("edges");
        for (int i = 0; i < edges.length(); i++) {
            JSONArray row = edges.getJSONArray(i);
            long from = row.getLong(1), to = row.getLong(2);
            String highway = row.getString(4);
            // Major-road coverage retains its established reference and selection.
            boolean local = !highway.equals("primary") && !highway.equals("primary_link");
            network.add(new Edge(row.getLong(0), from, to, row.isNull(3) ? "" : row.getString(3),
                    points.get(from), points.get(to), row.isNull(5) ? null : row.getInt(5),
                    local && row.getBoolean(6)));
        }
        return network;
    }

    private static byte[] readBounded(InputStream input, int limit) throws IOException {
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) {
            if (data.size() + count > limit) throw new IOException("Road reference exceeds its size limit");
            data.write(buffer, 0, count);
        }
        return data.toByteArray();
    }

    void add(Edge edge) {
        if (edge.mm <= 0 || edges.putIfAbsent(edge.id(), edge) != null)
            throw new IllegalArgumentException("Invalid section " + edge.id());
        int pieces = (int) Math.ceil(edge.length / GRID);
        Set<Long> occupied = new HashSet<>();
        for (int i = 0; i < pieces; i++) {
            Point a = edge.at(i / (double) pieces), b = edge.at((i + 1) / (double) pieces);
            for (int x = cell(Math.min(a.x, b.x)); x <= cell(Math.max(a.x, b.x)); x++)
                for (int y = cell(Math.min(a.y, b.y)); y <= cell(Math.max(a.y, b.y)); y++)
                    occupied.add(key(x, y));
        }
        for (long cell : occupied) grid.computeIfAbsent(cell, ignored -> new ArrayList<>()).add(edge);
    }

    Point point(double lon, double lat) { return new Point((lon - west) * lonScale, (lat - south) * LAT_SCALE); }

    JSONArray coordinates(Point point) throws org.json.JSONException {
        return new JSONArray().put(west + point.x / lonScale).put(south + point.y / LAT_SCALE);
    }

    private boolean inside(Point point, double margin) {
        return point.x >= margin && point.x <= width - margin
                && point.y >= margin && point.y <= height - margin;
    }

    List<Span> observe(Fix first, Fix last) {
        if (first == null || last == null
                || !(first.accuracy > 0 && first.accuracy <= 5 && last.accuracy > 0 && last.accuracy <= 5))
            return Collections.emptyList();
        long elapsed = last.time - first.time;
        if (elapsed <= 0 || elapsed > 8000) return Collections.emptyList();
        double uncertainty = Math.max(first.accuracy, last.accuracy);
        Point a = first.point, b = last.point;
        if (!inside(a, RADIUS + uncertainty) || !inside(b, RADIUS + uncertainty)) return Collections.emptyList();
        Point step = b.minus(a);
        double length = step.length();
        if (length < 2 || length > 35 || length <= first.accuracy + last.accuracy
                || length / elapsed > .06) return Collections.emptyList();
        List<Candidate> matches = new ArrayList<>();
        for (Edge edge : nearby(a, b, RADIUS + uncertainty)) {
            Candidate match = project(edge, a, step, length, RADIUS);
            if (match != null) matches.add(match);
            else {
                Candidate competitor = project(edge, a, step, length, RADIUS + uncertainty);
                if (competitor != null && (competitor.end - competitor.start) * length > SEAM)
                    return Collections.emptyList();
            }
        }
        if (matches.isEmpty()) return Collections.emptyList();
        matches.sort(Comparator.comparingDouble(Candidate::start).thenComparingDouble(Candidate::end));
        List<Span> spans = new ArrayList<>();
        Candidate previous = null;
        for (Candidate match : matches) {
            if (!match.edge.eligible || match.edge.direction == null
                    || (match.edge.direction != 0 && match.edge.direction != (match.entry < match.exit ? 1 : -1)))
                return Collections.emptyList();
            if (previous != null && (Math.abs(match.start - previous.end) * length > SEAM
                    || !previous.joins(match))) return Collections.emptyList();
            Span span = match.span();
            if (span.interval.start < span.interval.end) spans.add(span);
            previous = match;
        }
        if (Math.max(matches.get(0).start, 1 - previous.end) * length > SEAM) return Collections.emptyList();
        return spans;
    }

    /**
     * Find portions of a legacy line that the same journey already confirms.
     * Competing centrelines keep the legacy line visible. These clips grant no credit.
     */
    List<double[]> displayClips(Point a, Point b, Map<Edge, List<Interval>> accepted) {
        if (!intersectsBounds(a, b, 7)) return Collections.emptyList();
        Point step = b.minus(a);
        double length = step.length();
        if (!(length > 0)) return Collections.emptyList();
        List<Candidate> candidates = new ArrayList<>();
        for (Edge edge : nearby(a, b, 7)) {
            Candidate match = project(edge, a, step, length, 7);
            if (match != null) candidates.add(match);
        }
        List<double[]> clips = new ArrayList<>();
        for (Candidate match : candidates) {
            List<Interval> intervals = accepted.get(match.edge);
            if (intervals == null) continue;
            double atStart = match.entry * match.edge.mm, atEnd = match.exit * match.edge.mm;
            for (Interval interval : intervals) {
                double t0 = (interval.start - atStart) / (atEnd - atStart);
                double t1 = (interval.end - atStart) / (atEnd - atStart);
                double start = Math.max(match.start, match.start + Math.min(t0, t1) * (match.end - match.start));
                double end = Math.min(match.end, match.start + Math.max(t0, t1) * (match.end - match.start));
                if (start >= end) continue;
                boolean ambiguous = false;
                for (Candidate other : candidates) {
                    if (other == match || Math.min(end, other.end) - Math.max(start, other.start) <= SEAM / length)
                        continue;
                    ambiguous = true;
                    break;
                }
                if (!ambiguous) {
                    // Match the precision of the stored millimetre offsets at a legacy vertex.
                    clips.add(new double[]{start * length <= .001 ? 0 : start,
                            (1 - end) * length <= .001 ? 1 : end});
                }
            }
        }
        clips.sort(Comparator.comparingDouble(range -> range[0]));
        return clips;
    }

    private boolean intersectsBounds(Point a, Point b, double margin) {
        return Math.max(a.x, b.x) >= -margin && Math.min(a.x, b.x) <= width + margin
                && Math.max(a.y, b.y) >= -margin && Math.min(a.y, b.y) <= height + margin;
    }

    private Set<Edge> nearby(Point a, Point b, double margin) {
        Set<Edge> result = new HashSet<>();
        int left = cell(Math.max(-margin, Math.min(a.x, b.x) - margin));
        int right = cell(Math.min(width + margin, Math.max(a.x, b.x) + margin));
        int bottom = cell(Math.max(-margin, Math.min(a.y, b.y) - margin));
        int top = cell(Math.min(height + margin, Math.max(a.y, b.y) + margin));
        for (int x = left; x <= right; x++)
            for (int y = bottom; y <= top; y++) {
                List<Edge> edges = grid.get(key(x, y));
                if (edges != null) result.addAll(edges);
            }
        return result;
    }

    private static Candidate project(Edge edge, Point a, Point step, double length, double radius) {
        double dot = edge.axis.dot(step);
        if (Math.abs(dot) < HEADING * edge.length * length) return null;
        Point relative = a.minus(edge.a);
        double denominator = edge.length * edge.length;
        double first = relative.dot(edge.axis) / denominator, change = dot / denominator;
        double lower = -first / change, upper = (1 - first) / change;
        double start = Math.max(0, Math.min(lower, upper)), end = Math.min(1, Math.max(lower, upper));
        double lateral = edge.axis.cross(relative) / edge.length;
        double lateralChange = edge.axis.cross(step) / edge.length;
        if (lateralChange != 0) {
            lower = (-radius - lateral) / lateralChange;
            upper = (radius - lateral) / lateralChange;
            start = Math.max(start, Math.min(lower, upper));
            end = Math.min(end, Math.max(lower, upper));
        } else if (Math.abs(lateral) > radius) return null;
        return start < end ? new Candidate(edge, start, end, clamp(first + start * change),
                clamp(first + end * change)) : null;
    }

    static List<Interval> union(List<Interval> intervals) {
        intervals.sort(Comparator.comparingInt(Interval::start).thenComparingInt(Interval::end));
        List<Interval> result = new ArrayList<>();
        for (Interval interval : intervals) {
            int last = result.size() - 1;
            if (last >= 0 && interval.start <= result.get(last).end) {
                Interval earlier = result.get(last);
                result.set(last, new Interval(earlier.start, Math.max(earlier.end, interval.end)));
            } else result.add(interval);
        }
        return result;
    }

    private static double clamp(double value) { return Math.min(1, Math.max(0, value)); }
    private static int cell(double value) { return (int) Math.floor(value / GRID); }
    private static long key(int x, int y) { return ((long) x << 32) | (y & 0xffffffffL); }
}
