package com.roadprints.capture;

import java.util.*;
import java.util.function.BooleanSupplier;

/** Regional rail matcher. The graph uses the catalogue's EPSG:32630 coordinates. */
final class RailwayMatcher {
    static final String VERSION = "railway-android-1";
    static final double RADIUS = 40;
    static final int MAX_FIXES = 2048;

    record Fix(double x, double y, double accuracy, long time) {}
    record Edge(long id, long a, long b, double ax, double ay, double bx, double by,
                double length, String ref, boolean eligible) {
        Hit hit(Fix p) {
            double dx = bx-ax, dy = by-ay;
            double t = Math.max(0, Math.min(1, ((p.x-ax)*dx+(p.y-ay)*dy)/(dx*dx+dy*dy)));
            return new Hit(this, t, Math.hypot(p.x-ax-t*dx, p.y-ay-t*dy));
        }
    }
    record Hit(Edge edge, double fraction, double residual) {
        double x() { return edge.ax+fraction*(edge.bx-edge.ax); }
        double y() { return edge.ay+fraction*(edge.by-edge.ay); }
        boolean heading(double dx, double dy, double chord) {
            return Math.abs(dx*(edge.bx-edge.ax)+dy*(edge.by-edge.ay)) >= .75*chord*edge.length;
        }
    }
    record Axis(double offset, boolean assessable) {}
    record Candidates(List<Hit> hits, double road) {}
    record Pair(Hit start, Hit end, Axis from, Axis to, boolean witness, double metres) {}
    record Result(String confidence, String reason, double supportedMetres, int witnesses,
                  int supportedPairs) {
        boolean complete() { return "full".equals(confidence); }
    }
    interface Graph {
        List<Edge> nearby(Fix fix, double radius, boolean road);
        List<Edge> adjacent(String ref, long node);
        Axis axis(String ref, Hit hit);
        boolean held(String ref, double lo, double hi);
    }

    private final Graph graph;
    private final BooleanSupplier cancelled;
    RailwayMatcher(Graph graph, BooleanSupplier cancelled) {
        this.graph = graph;
        this.cancelled = cancelled;
    }

    Result match(List<Fix> fixes, long start, long end) {
        if (fixes.size() > MAX_FIXES) return new Result("unresolved","input_limit",0,0,0);
        if (fixes.size() < 4) return new Result("unresolved","insufficient_fixes",0,0,0);
        List<Pair> run = new ArrayList<>();
        double metres = 0;
        int witnesses = 0, pairs = 0, runs = 0;
        long firstSupported = -1, lastSupported = -1;
        Fix previous = fixes.get(0);
        Candidates first = candidates(previous);
        for (int i = 1; i <= fixes.size(); i++) {
            check();
            Fix next = i < fixes.size() ? fixes.get(i) : null;
            Candidates last = next == null ? null : candidates(next);
            Pair pair = next == null ? null : pair(previous,next,first,last,run.isEmpty()?null:run.get(run.size()-1));
            if (pair == null || !run.isEmpty() && !connected(run.get(run.size()-1), pair)) {
                int left = -1, right = -1, count = 0;
                double witnessMetres = 0;
                for (int j = 0; j < run.size(); j++) {
                    Pair p = run.get(j);
                    if (p.witness) {
                        if (left < 0) left = j;
                        right = j;
                        count++;
                        witnessMetres += Math.abs(p.to.offset-p.from.offset);
                    }
                }
                if (count >= 3 && witnessMetres >= 100) {
                    int runStart = i - 1 - run.size();
                    firstSupported = firstSupported < 0 ? fixes.get(runStart+left).time : firstSupported;
                    lastSupported = fixes.get(runStart+right+1).time;
                    for (int j = left; j <= right; j++) metres += run.get(j).metres;
                    witnesses += count;
                    pairs += right-left+1;
                    runs++;
                }
                run.clear();
            }
            if (pair != null) run.add(pair);
            previous = next;
            first = last;
        }
        // Whole-journey replacement requires a witnessed route between the actual
        // saved boundaries. Incomplete or mixed traces keep the existing distance.
        boolean full = runs == 1 && pairs == fixes.size()-1
                && firstSupported == start && lastSupported == end && end > start;
        return new Result(full?"full":pairs>0?"partial":"unresolved",
                full?"connected_full_route":pairs>0?"incomplete_route":"no_supported_route",metres,witnesses,pairs);
    }

    private Candidates candidates(Fix p) {
        if (!(p.accuracy > 0 && p.accuracy <= RADIUS)) return new Candidates(List.of(),0);
        List<Hit> all = hits(graph.nearby(p,RADIUS,false),p,RADIUS);
        Hit best = all.stream().filter(h -> h.edge.eligible).findFirst().orElse(null);
        if (best == null) return new Candidates(List.of(),0);
        for (Hit other : all) {
            if (!other.edge.ref.equals(best.edge.ref) && !"OTHER".equals(other.edge.ref)
                    && other.residual < best.residual+Math.max(6,Math.min(14,p.accuracy*.45))) {
                return new Candidates(List.of(),0);
            }
        }
        List<Hit> roads = hits(graph.nearby(p,65,true),p,65);
        double road = roads.isEmpty()?65:roads.get(0).residual;
        if (road+Math.max(3,Math.min(8,p.accuracy*.4)) < best.residual)
            return new Candidates(List.of(),road);
        List<Hit> selected = new ArrayList<>(4);
        for (Hit h : all) {
            if (h.edge.eligible && h.edge.ref.equals(best.edge.ref)) selected.add(h);
            if (selected.size() == 4) break;
        }
        return new Candidates(selected,road);
    }

    private static List<Hit> hits(List<Edge> edges, Fix p, double radius) {
        List<Hit> out = new ArrayList<>();
        for (Edge edge : edges) {
            Hit hit = edge.hit(p);
            if (hit.residual <= radius) out.add(hit);
        }
        out.sort(Comparator.comparingDouble(Hit::residual).thenComparingLong(h -> h.edge.id));
        return out;
    }

    private Pair pair(Fix a, Fix b, Candidates first, Candidates last, Pair previous) {
        double dx = b.x-a.x, dy = b.y-a.y, chord = Math.hypot(dx,dy);
        double seconds = (b.time-a.time)/1000.0;
        if (seconds <= 0 || seconds > 18 || chord < 5 || chord/seconds > 65) return null;
        Pair best = null, continuous = null;
        double min = chord*.62, max = chord*1.35+15;
        for (Hit start : first.hits) {
            if (!start.heading(dx,dy,chord)) continue;
            for (Hit end : last.hits) {
                check();
                if (!start.edge.ref.equals(end.edge.ref) || !end.heading(dx,dy,chord)) continue;
                double length = path(start,end,max);
                if (!(length >= min && length <= max)) continue;
                Axis s = graph.axis(start.edge.ref,start), t = graph.axis(start.edge.ref,end);
                if (s == null || t == null || !s.assessable || !t.assessable) continue;
                double lo = Math.min(s.offset,t.offset), hi = Math.max(s.offset,t.offset);
                if (hi-lo < Math.max(3,min) || hi-lo > max || graph.held(start.edge.ref,lo,hi)) continue;
                Pair pair = new Pair(start,end,s,t,chord/seconds >= 7.5
                        && witness(start,a,first.road) && witness(end,b,last.road),length);
                if (preferred(pair,best)) best = pair;
                if (previous != null && connected(previous,pair) && preferred(pair,continuous)) continuous = pair;
            }
        }
        return continuous == null ? best : continuous;
    }

    private static boolean preferred(Pair p, Pair other) {
        if (other == null) return true;
        int order = Double.compare(p.start.residual+p.end.residual,other.start.residual+other.end.residual);
        if (order != 0) return order < 0;
        if (p.start.edge.id != other.start.edge.id) return p.start.edge.id < other.start.edge.id;
        return p.end.edge.id < other.end.edge.id;
    }

    private static boolean connected(Pair a, Pair b) {
        return a.end.edge.ref.equals(b.start.edge.ref)
                && Math.signum(a.to.offset-a.from.offset) == Math.signum(b.to.offset-b.from.offset)
                && (a.end.edge.id == b.start.edge.id
                    && Math.abs(a.end.fraction-b.start.fraction) < 1e-8
                    || sameNode(a.end,b.start));
    }

    private static boolean sameNode(Hit a, Hit b) {
        long node = a.fraction == 0 ? a.edge.a : a.fraction == 1 ? a.edge.b : -1;
        return node != -1 && (b.fraction == 0 && b.edge.a == node
                || b.fraction == 1 && b.edge.b == node);
    }

    private static boolean witness(Hit h, Fix p, double road) {
        return h.residual <= Math.max(3,Math.min(12,p.accuracy*.85))
                && road-h.residual >= Math.max(5,Math.min(12,p.accuracy/2));
    }

    private record Node(long id, double distance) {}
    private double path(Hit start, Hit end, double limit) {
        if (start.edge.id == end.edge.id) return Math.abs(start.fraction-end.fraction)*start.edge.length;
        PriorityQueue<Node> queue = new PriorityQueue<>(Comparator.comparingDouble(Node::distance));
        queue.add(new Node(start.edge.a,start.fraction*start.edge.length));
        queue.add(new Node(start.edge.b,(1-start.fraction)*start.edge.length));
        Set<Long> seen = new HashSet<>();
        double best = Double.POSITIVE_INFINITY;
        while (!queue.isEmpty() && seen.size() < 55) {
            check();
            Node n = queue.remove();
            if (n.distance > limit || n.distance >= best) break;
            if (!seen.add(n.id)) continue;
            if (n.id == end.edge.a) best = Math.min(best,n.distance+end.fraction*end.edge.length);
            if (n.id == end.edge.b) best = Math.min(best,n.distance+(1-end.fraction)*end.edge.length);
            for (Edge edge : graph.adjacent(start.edge.ref,n.id)) {
                long target = edge.a == n.id ? edge.b : edge.a;
                if (!seen.contains(target) && n.distance+edge.length <= limit)
                    queue.add(new Node(target,n.distance+edge.length));
            }
        }
        return best;
    }

    private void check() {
        if (cancelled.getAsBoolean()) throw new java.util.concurrent.CancellationException();
    }
}
