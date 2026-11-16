package com.roadprints.capture;

import java.io.*;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import java.util.zip.GZIPInputStream;

/** Scores the pinned conventional passenger paths prepared from the regional railway catalogue. */
final class NetworkFirstRailway {
    static final String VERSION = "north-kent-passenger-r1";
    static final String SHA256 = "80f4486253e94658b7128411b9c52b29e07e7dddd04c4f8392ae8af6afc5d340";
    private static final int CELL = 128;
    private static final double SEARCH = 124;
    private static final int MAX_ASSET_BYTES = 225006;
    private static final String[] BRANCHES = {"Sidcup", "Bexleyheath", "Woolwich and Greenwich"};
    private static final RailwayMatcher.Fix GRAVESEND = RailwayProjection.fix(.366990, 51.441029, 0, 0);
    private static final RailwayMatcher.Fix CHARING_CROSS = RailwayProjection.fix(-.123689, 51.507497, 0, 0);

    record Outcome(String confidence, String reason, String branch, String alternatives,
                   double metres, int witnesses, String endpoints, String pathId,
                   long candidateMs, long evidenceMs, int paths) {}
    private record Stop(long id, double x, double y) {}
    private record Route(int branch, boolean qualified, int start, int end, short[] order,
                         double[] offset, double metres, String pathId) {}
    private record Score(Route route, int contradictions, int progression, int witnesses,
                         double witnessMetres, int thirds, double median) {
        boolean plausible() {
            return contradictions <= 2 && progression <= 8 && witnesses >= 3 && witnessMetres >= 100 && thirds >= 2;
        }
    }
    private static final Comparator<Score> QUALITY = Comparator.comparingInt(Score::contradictions)
            .thenComparingInt(Score::progression)
            .thenComparing(Comparator.comparingDouble(Score::witnessMetres).reversed())
            .thenComparingDouble(Score::median);
    private final RailwayMatcher.Edge[] edges;
    private final boolean[] tunnels;
    private final Stop[] stops;
    private final Route[] routes;
    private final Map<Long, int[]> grid;

    private NetworkFirstRailway(DataInputStream input, BooleanSupplier stop) throws Exception {
        if (input.readInt() != 0x52504e31) throw new IOException("Railway paths format");
        int edgeCount = input.readUnsignedShort();
        int routeCount = input.readUnsignedShort();
        int stopCount = input.readUnsignedByte();
        if (edgeCount != 3165 || routeCount != 72 || stopCount != 5) throw new IOException("Railway paths size");
        stops = new Stop[stopCount];
        for (int i = 0; i < stopCount; i++) {
            stops[i] = new Stop(input.readLong(), input.readDouble(), input.readDouble());
        }
        edges = new RailwayMatcher.Edge[edgeCount];
        tunnels = new boolean[edgeCount];
        Map<Long, List<Integer>> cells = new HashMap<>();
        for (int i = 0; i < edgeCount; i++) {
            check(stop);
            long id = input.readInt();
            int flags = input.readUnsignedByte();
            double x = 700000 + input.readInt() / 100.0;
            double y = 5700000 + input.readInt() / 100.0;
            double dx = input.readInt() / 100.0, dy = input.readInt() / 100.0;
            double length = input.readInt() / 1000.0;
            if (!(length > 0) || Math.hypot(dx, dy) <= 0 || length > 1300) throw new IOException("Railway path edge");
            tunnels[i] = (flags & 2) != 0;
            edges[i] = new RailwayMatcher.Edge(id, 0, 0, x, y, x+dx, y+dy, length, "", true);
            for (int a = cell(Math.min(x, x+dx)-SEARCH); a <= cell(Math.max(x, x+dx)+SEARCH); a++) {
                for (int b = cell(Math.min(y, y+dy)-SEARCH); b <= cell(Math.max(y, y+dy)+SEARCH); b++) {
                    cells.computeIfAbsent(key(a, b), ignored -> new ArrayList<>()).add(i);
                }
            }
        }
        grid = new HashMap<>();
        cells.forEach((key, values) -> grid.put(key, values.stream().mapToInt(Integer::intValue).toArray()));
        routes = new Route[routeCount];
        for (int i = 0; i < routeCount; i++) {
            check(stop);
            int branch = input.readUnsignedByte();
            boolean qualified = input.readBoolean();
            int first = input.readUnsignedByte(), last = input.readUnsignedByte();
            int count = input.readUnsignedShort();
            if (branch >= BRANCHES.length || first >= stops.length || last >= stops.length || count < 1 || count > 1600)
                throw new IOException("Railway path route");
            short[] order = new short[edges.length];
            Arrays.fill(order, (short) -1);
            double[] offset = new double[count];
            double metres = 0;
            StringJoiner ids = new StringJoiner(",");
            for (int j = 0; j < count; j++) {
                int index = input.readUnsignedShort();
                if (index >= edges.length || order[index] != -1) throw new IOException("Railway path topology");
                order[index] = (short) j;
                offset[j] = metres;
                metres += edges[index].length();
                ids.add(Long.toString(edges[index].id()));
            }
            if (metres > 55_000) throw new IOException("Railway path length");
            String id = RegionalRailwayCatalogue.hex(MessageDigest.getInstance("SHA-256")
                    .digest(ids.toString().getBytes(java.nio.charset.StandardCharsets.US_ASCII)));
            routes[i] = new Route(branch, qualified, first, last, order, offset, metres, id);
        }
        if (input.read() != -1) throw new IOException("Railway paths trailing data");
    }

    static NetworkFirstRailway open(InputStream source, BooleanSupplier stop) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (var compressed = new DigestInputStream(source, digest);
             var gzip = new GZIPInputStream(compressed)) {
            byte[] bytes = new byte[MAX_ASSET_BYTES];
            int offset = 0;
            while (offset < bytes.length) {
                check(stop);
                int count = gzip.read(bytes, offset, Math.min(4096, bytes.length-offset));
                if (count < 0) break;
                offset += count;
            }
            if (offset != bytes.length || gzip.read() != -1
                    || !SHA256.equals(RegionalRailwayCatalogue.hex(digest.digest())))
                throw new IOException("Railway paths checksum");
            try (var input = new DataInputStream(new ByteArrayInputStream(bytes))) {
                return new NetworkFirstRailway(input, stop);
            }
        }
    }

    static boolean supports(List<RailwayMatcher.Fix> fixes, long start, long end) {
        if (fixes.size() < 4 || end-start < 35*60_000L || end-start > 110*60_000L) return false;
        var first = endpointFix(fixes, start, end, false);
        var last = endpointFix(fixes, start, end, true);
        return near(first, GRAVESEND) && near(last, CHARING_CROSS)
                || near(first, CHARING_CROSS) && near(last, GRAVESEND);
    }

    private static RailwayMatcher.Fix endpointFix(List<RailwayMatcher.Fix> fixes, long start, long end, boolean last) {
        for (int i = 0; i < fixes.size(); i++) {
            var fix = fixes.get(last ? fixes.size()-1-i : i);
            if (usable(fix, start, end) && (last ? end-fix.time() : fix.time()-start) <= 120_000) return fix;
        }
        return null;
    }

    private static boolean near(RailwayMatcher.Fix fix, RailwayMatcher.Fix station) {
        return fix != null && Math.hypot(fix.x()-station.x(), fix.y()-station.y()) <= 550;
    }

    private static boolean usable(RailwayMatcher.Fix fix, long start, long end) {
        return fix.time() >= start && fix.time() <= end && fix.accuracy() > 0 && fix.accuracy() <= 40;
    }

    private static int cell(double metres) { return (int) Math.floor(metres / CELL); }
    private static long key(int x, int y) { return ((long) x << 32) | (y & 0xffffffffL); }
    private static void check(BooleanSupplier stop) {
        if (stop.getAsBoolean()) throw new CancellationException();
    }

    Outcome infer(List<RailwayMatcher.Fix> fixes, long start, long end, RailwayMatcher.Graph graph,
                  BooleanSupplier stop) {
        if (!supports(fixes, start, end)) return null;
        long started = System.nanoTime();
        boolean reverse = near(endpointFix(fixes, start, end, false), CHARING_CROSS);
        var observations = new ArrayList<Observation>();
        var first = endpointFix(fixes, start, end, false);
        var last = endpointFix(fixes, start, end, true);
        Route[] hypotheses = Arrays.stream(routes).filter(route ->
                nearStop(first, stops[reverse ? route.end : route.start])
                && nearStop(last, stops[reverse ? route.start : route.end])).toArray(Route[]::new);
        long candidateMs = elapsed(started);
        if (hypotheses.length == 0) return null;
        for (int i = 0; i < fixes.size(); i++) {
            check(stop);
            var fix = fixes.get(i);
            if (usable(fix, start, end)) observations.add(observe(graph, fix, i));
        }
        Score[] plausible = new Score[BRANCHES.length];
        Score[] qualified = new Score[BRANCHES.length];
        int routeFamilies = 0;
        for (Route route : hypotheses) {
            check(stop);
            routeFamilies |= 1 << route.branch;
            Score score = score(route, observations, start, end, reverse, stop);
            if (!score.plausible()) continue;
            if (better(score, plausible[route.branch])) plausible[route.branch] = score;
            if (route.qualified && better(score, qualified[route.branch])) qualified[route.branch] = score;
        }
        if (Integer.bitCount(routeFamilies) != BRANCHES.length) return null;
        StringJoiner alternatives = new StringJoiner(", ");
        int branches = 0, winner = -1;
        for (int i = 0; i < plausible.length; i++) {
            if (plausible[i] == null) continue;
            alternatives.add(BRANCHES[i]);
            branches++;
            winner = i;
        }
        String endpoints = reverse ? "London Charing Cross → Gravesend" : "Gravesend → London Charing Cross";
        long evidenceMs = elapsed(started)-candidateMs;
        if (branches > 1) return new Outcome("ambiguous", "multiple_rail_routes", "", alternatives.toString(),
                0, 0, endpoints, "", candidateMs, evidenceMs, hypotheses.length);
        if (branches == 0) return new Outcome("unresolved", "network_evidence_insufficient", "", "",
                0, 0, endpoints, "", candidateMs, evidenceMs, hypotheses.length);
        // The reviewed running moves are westbound. The reverse candidates can
        // identify uncertainty, but cannot issue a distance on this data revision.
        Score selected = reverse ? null : qualified[winner];
        if (selected == null) return new Outcome("partial", "network_catalogue_held", BRANCHES[winner], "",
                0, plausible[winner].witnesses, endpoints, "", candidateMs, evidenceMs, hypotheses.length);
        return new Outcome("inferred", "network_route_inferred", BRANCHES[winner], "",
                selected.route.metres, selected.witnesses, endpoints, selected.route.pathId,
                candidateMs, evidenceMs, hypotheses.length);
    }

    private static long elapsed(long start) { return (System.nanoTime()-start)/1_000_000; }
    private static boolean nearStop(RailwayMatcher.Fix fix, Stop stop) {
        return Math.hypot(fix.x()-stop.x, fix.y()-stop.y) <= 600;
    }
    private static boolean better(Score score, Score old) {
        return old == null || QUALITY.compare(score, old) < 0;
    }

    private record Observation(RailwayMatcher.Fix fix, int sequence, int[] candidates, double[] residual,
                               double[] fraction, double road) {}

    private Observation observe(RailwayMatcher.Graph graph, RailwayMatcher.Fix fix, int sequence) {
        int[] candidates = grid.getOrDefault(key(cell(fix.x()), cell(fix.y())), EMPTY);
        double[] residual = new double[candidates.length], fraction = new double[candidates.length];
        boolean roadEvidenceNeeded = false;
        for (int i = 0; i < candidates.length; i++) {
            int index = candidates[i];
            var hit = edges[index].hit(fix);
            residual[i] = hit.residual();
            fraction[i] = hit.fraction();
            roadEvidenceNeeded |= !tunnels[index] && residual[i] <= 12;
        }
        // A rail witness needs at most 12 m residual plus 12 m separation.
        double road = roadEvidenceNeeded ? 24 : 0;
        if (roadEvidenceNeeded) {
            for (var edge : graph.nearby(fix, 24, true)) road = Math.min(road, edge.hit(fix).residual());
        }
        return new Observation(fix, sequence, candidates, residual, fraction, road);
    }
    private static final int[] EMPTY = {};

    private static final class Witness {
        double residual = SEARCH, offset;
        int order = Integer.MAX_VALUE;
        boolean tunnel;
    }

    private Score score(Route route, List<Observation> observations, long start, long end, boolean reverse,
                        BooleanSupplier stop) {
        int minuteCount = (int) ((end-start)/60_000)+1;
        int[] minutes = new int[minuteCount], bad = new int[minuteCount];
        double[] residuals = new double[observations.size()];
        double witnessMetres = 0, previousOffset = 0;
        int witnesses = 0, thirds = 0, conflicts = 0;
        boolean previousWitness = false, previousClose = false;
        Observation previous = null;
        Witness nearest = new Witness();
        for (int i = 0; i < observations.size(); i++) {
            if ((i & 15) == 0) check(stop);
            Observation o = observations.get(i);
            nearest.residual = SEARCH;
            nearest.order = Integer.MAX_VALUE;
            nearest.offset = 0;
            nearest.tunnel = true;
            for (int c = 0; c < o.candidates.length; c++) {
                int edge = o.candidates[c];
                int order = route.order[edge];
                if (order < 0 || o.residual[c] > nearest.residual
                        || (o.residual[c] == nearest.residual && order >= nearest.order)) continue;
                nearest.residual = o.residual[c];
                nearest.offset = route.offset[order]+o.fraction[c]*edges[edge].length();
                nearest.order = order;
                nearest.tunnel = tunnels[edge];
            }
            double residual = nearest.residual;
            double offset = reverse ? route.metres-nearest.offset : nearest.offset;
            double accuracy = o.fix.accuracy();
            residuals[i] = residual;
            int minute = (int) ((o.fix.time()-start)/60_000);
            minutes[minute]++;
            if (o.fix.time() > start+120_000 && o.fix.time() < end-120_000
                    && residual > Math.max(100, 3*accuracy)) bad[minute]++;
            boolean close = residual <= Math.max(35, 2*accuracy);
            boolean witness = !nearest.tunnel && residual <= clamp(accuracy*.85, 3, 12)
                    && o.road-residual >= clamp(accuracy/2, 5, 12);
            if (previous != null) {
                double seconds = (o.fix.time()-previous.fix.time())/1000.0;
                double advance = offset-previousOffset;
                boolean progresses = advance >= -25 && advance <= 65*seconds+50;
                if (close && previousClose && !progresses) conflicts++;
                if (witness && previousWitness && o.sequence == previous.sequence+1
                        && progresses && advance > 0 && seconds > 0 && seconds <= 18) {
                    double chord = Math.hypot(o.fix.x()-previous.fix.x(), o.fix.y()-previous.fix.y());
                    if (chord >= 7.5*seconds && chord <= 65*seconds) {
                        witnesses++;
                        witnessMetres += chord;
                        thirds |= 1 << Math.min(2, (int) (3*(previous.fix.time()-start)/(end-start)));
                    }
                }
            }
            previous = o;
            previousOffset = offset;
            previousClose = close;
            previousWitness = witness;
        }
        int contradictions = 0;
        for (int i = 0; i < minutes.length; i++) {
            if (minutes[i] > 0 && 2*bad[i] >= minutes[i]) contradictions++;
        }
        Arrays.sort(residuals);
        int middle = residuals.length/2;
        double median = residuals.length % 2 == 0 ? (residuals[middle-1]+residuals[middle])/2 : residuals[middle];
        return new Score(route, contradictions, conflicts, witnesses, witnessMetres, Integer.bitCount(thirds), median);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
