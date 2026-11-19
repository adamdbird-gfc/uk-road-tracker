package com.roadprints.capture;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Resolves saved matched-route vertices OR measured GPS pairs into intervals on a
 * supplied versioned road reference. Saved matched routes are accepted by default;
 * raw fixes provide independent quality checks and help investigate anomalies. It does not mutate historical completion or Android Map state.
 * Callers must pass coordinates in the SAME locally projected metre frame as edges.
 */
final class CanonicalRoadAttribution {
    record Point(double x, double y) {
        double distance(Point other) { return Math.hypot(x - other.x, y - other.y); }
    }

    record Edge(String sectionId, Point start, Point end) {
        Edge {
            if (sectionId == null || sectionId.isBlank() || start.distance(end) < .001)
                throw new IllegalArgumentException("Invalid canonical section");
        }
        double length() { return start.distance(end); }
        int millimetres() { return (int) Math.round(length() * 1000); }
    }

    record Fix(Point point, long timeMillis, double accuracyMetres) {}
    record Match(String sectionId, int startMm, int endMm) {}
    record Report(List<Match> supported, int examinedPairs, int acceptedPairs,
                  int skippedQuality, int skippedAmbiguous, int skippedNoEdge) {
        Report { supported = List.copyOf(supported); }
    }

    private static final double MAX_ACCURACY_M = 12;
    private static final long MAX_INTERVAL_MS = 12000;
    private static final double MAX_STEP_M = 130;
    private static final double MAX_OFF_EDGE_M = 7;
    private static final double MIN_STEP_M = 4;
    private static final double MIN_HEADING_COS = .8;
    private final List<Edge> edges;

    CanonicalRoadAttribution(List<Edge> referenceSections) {
        if (referenceSections == null) throw new IllegalArgumentException("Reference required");
        Set<String> identities = new HashSet<>();
        for (Edge edge : referenceSections)
            if (!identities.add(edge.sectionId()))
                throw new IllegalArgumentException("Duplicate canonical section identity: " + edge.sectionId());
        this.edges = List.copyOf(referenceSections);
    }

    /**
     * Secondary evidence pathway using actual, accuracy-bearing capture fixes.
     * This is NOT a prerequisite for accepted matched-route coverage.
     */
    Report attributeCaptured(List<Fix> fixes) {
        if (fixes == null) throw new IllegalArgumentException("Fixes required");
        List<Match> accepted = new ArrayList<>();
        int quality = 0, ambiguous = 0, missing = 0, passed = 0;
        for (int i = 1; i < fixes.size(); i++) {
            Fix first = fixes.get(i - 1), last = fixes.get(i);
            if (!credible(first, last)) { quality++; continue; }
            List<Match> alternatives = new ArrayList<>();
            for (Edge edge : edges) {
                Match match = match(first.point(), last.point(), edge);
                if (match != null) alternatives.add(match);
            }
            if (alternatives.size() != 1) {
                if (alternatives.isEmpty()) missing++; else ambiguous++;
                continue;
            }
            accepted.add(alternatives.get(0));
            passed++;
        }
        return new Report(accepted, Math.max(0, fixes.size() - 1), passed,
                quality, ambiguous, missing);
    }

    /**
     * Primary coverage pathway: the journey's final saved map-matched geometry,
     * AFTER owner corrections have been applied. The caller must establish that
     * matching succeeded; never pass original sparse Timeline control points,
     * an unprocessed straight-line fallback or a known failed reconstruction.
     * The resulting intervals use accepted-match evidence in CumulativeRoadUnion.
     * This method does not itself infer roads across gaps or missing network tiles.
     */
    Report attributeAcceptedRoute(List<Point> correctedMatchedVertices) {
        if (correctedMatchedVertices == null)
            throw new IllegalArgumentException("Matched route required");
        List<Match> accepted = new ArrayList<>();
        int quality = 0, ambiguous = 0, missing = 0;
        for (int i = 1; i < correctedMatchedVertices.size(); i++) {
            Point first = correctedMatchedVertices.get(i - 1);
            Point last = correctedMatchedVertices.get(i);
            if (first == null || last == null || first.distance(last) < .001) {
                quality++;
                continue;
            }
            List<Match> alternatives = new ArrayList<>();
            for (Edge edge : edges) {
                Match match = match(first, last, edge);
                if (match != null) alternatives.add(match);
            }
            if (alternatives.size() != 1) {
                if (alternatives.isEmpty()) missing++; else ambiguous++;
                continue;
            }
            accepted.add(alternatives.get(0));
        }
        return new Report(accepted, Math.max(0, correctedMatchedVertices.size() - 1),
                accepted.size(), quality, ambiguous, missing);
    }

    record Alignment(LocalRoadNetwork.Span span, double start, double end) {}
    record Leg(List<Alignment> alignments, String unresolvedReason) {}

    private static final double MAX_MATCHED_LEG = 2_000;
    private static final double MATCH_RADIUS = 5;
    private static final double JOIN_GAP = 1.25;
    private static final int MAX_CHAIN_EDGES = 128;

    /** Resolve a matched chord against a uniquely supported connected chain. */
    static Leg attributeAcceptedLeg(RoadReference reference, double ax, double ay, double bx, double by)
            throws java.io.IOException {
        return attributeAcceptedLeg(reference, ax, ay, bx, by, false);
    }

    /** A vehicle must not attribute a nearby pedestrian-only OSM section. */
    static Leg attributeAcceptedLeg(RoadReference reference, double ax, double ay, double bx, double by,
                                    boolean onFoot) throws java.io.IOException {
        return attributeAcceptedLeg(reference, ax, ay, bx, by, onFoot,
                new HashSet<>());
    }

    /**
     * The accepted-route loop passes one reusable scratch set for all its legs.
     * The result only retains immutable Spans, never this collection.
     */
    static Leg attributeAcceptedLeg(RoadReference reference, double ax, double ay, double bx, double by,
                                    boolean onFoot, Set<LocalRoadNetwork.Edge> scratch) throws java.io.IOException {
        LocalRoadNetwork.Point first = RoadReference.point(ax, ay), last = RoadReference.point(bx, by);
        LocalRoadNetwork.Point step = last.minus(first);
        double length = step.length();
        if (!Double.isFinite(length) || length > MAX_MATCHED_LEG)
            return new Leg(List.of(), "invalid_or_long_leg");
        if (length < .001) return new Leg(List.of(), null);
        reference.collectNearby(ax, ay, bx, by, scratch);
        if (!onFoot) scratch.removeIf(edge -> edge.footOnly);
        return attributeAcceptedLeg(first, step, length, scratch);
    }

    static Leg attributeAcceptedLeg(LocalRoadNetwork.Point first, LocalRoadNetwork.Point step,
                                    double length, Set<LocalRoadNetwork.Edge> nearby) {
        List<LocalRoadNetwork.Candidate> candidates = new ArrayList<>();
        for (LocalRoadNetwork.Edge edge : nearby) {
            LocalRoadNetwork.Candidate candidate = LocalRoadNetwork.project(edge, first, step, length, MATCH_RADIUS);
            if (candidate == null || !candidate.hasPositiveInterval())
                continue;
            // Matcher coordinates can round a junction vertex a few centimetres
            // over its node. Treat these terminal slivers as seam tolerance.
            boolean terminalSliver = length > .5 && (candidate.end() - candidate.start()) * length < .25
                    && (candidate.start() * length < .25 || (1 - candidate.end()) * length < .25);
            if (!terminalSliver) candidates.add(candidate);
        }
        if (candidates.size() > MAX_CHAIN_EDGES) return new Leg(List.of(), "complex_reference");
        candidates.sort(java.util.Comparator.comparingDouble(LocalRoadNetwork.Candidate::end)
                .thenComparingDouble(LocalRoadNetwork.Candidate::start)
                .thenComparing(candidate -> candidate.edge().id()));
        Chain[] best = new Chain[candidates.size()], second = new Chain[candidates.size()];
        Chain winner = null, alternative = null;
        for (int i = 0; i < candidates.size(); i++) {
            LocalRoadNetwork.Candidate next = candidates.get(i);
            double cost = offsetCost(next, first, step);
            if (next.start() * length <= JOIN_GAP)
                best[i] = new Chain(next, null, cost + next.start() * 5);
            for (int j = 0; j < i; j++) {
                LocalRoadNetwork.Candidate previous = candidates.get(j);
                if (best[j] == null || previous.start() >= next.start() || previous.end() >= next.end()
                        || Math.abs(previous.end() - next.start()) * length > JOIN_GAP
                        || !previous.joins(next)) continue;
                Chain option = new Chain(next, best[j], best[j].cost + cost
                        + Math.abs(previous.end() - next.start()) * 5);
                if (best[i] == null || option.cost < best[i].cost) {
                    second[i] = best[i];
                    best[i] = option;
                } else if (second[i] == null || option.cost < second[i].cost) {
                    second[i] = option;
                }
                if (second[j] != null) {
                    Chain runnerUp = new Chain(next, second[j], option.cost + second[j].cost - best[j].cost);
                    if (second[i] == null || runnerUp.cost < second[i].cost) second[i] = runnerUp;
                }
            }
            if (best[i] == null || (1 - next.end()) * length > JOIN_GAP) continue;
            for (Chain chain : new Chain[]{best[i], second[i]}) {
                if (chain == null) continue;
                Chain finished = new Chain(chain.candidate, chain.previous, chain.cost + (1 - next.end()) * 5);
                if (winner == null || finished.cost < winner.cost) {
                    alternative = winner;
                    winner = finished;
                } else if (alternative == null || finished.cost < alternative.cost) {
                    alternative = finished;
                }
            }
        }
        if (winner == null) return new Leg(List.of(), "no_connected_alignment");
        if (alternative != null && alternative.cost - winner.cost < .75
                && materiallyDifferent(winner, alternative, length))
            return new Leg(List.of(), "competing_alignment");
        List<Alignment> result = new ArrayList<>();
        for (Chain chain = winner; chain != null; chain = chain.previous) {
            LocalRoadNetwork.Candidate match = chain.candidate;
            result.add(new Alignment(match.span(), match.start(), match.end()));
        }
        java.util.Collections.reverse(result);
        return new Leg(result, null);
    }

    private record Chain(LocalRoadNetwork.Candidate candidate, Chain previous, double cost) {}

    private static boolean materiallyDifferent(Chain first, Chain second, double length) {
        Set<LocalRoadNetwork.Edge> firstEdges = new HashSet<>(), secondEdges = new HashSet<>();
        for (Chain c = first; c != null; c = c.previous) firstEdges.add(c.candidate.edge());
        for (Chain c = second; c != null; c = c.previous) secondEdges.add(c.candidate.edge());
        double unmatched = 0;
        for (Chain c = first; c != null; c = c.previous)
            if (!secondEdges.contains(c.candidate.edge())) unmatched += c.candidate.end() - c.candidate.start();
        for (Chain c = second; c != null; c = c.previous)
            if (!firstEdges.contains(c.candidate.edge())) unmatched += c.candidate.end() - c.candidate.start();
        return unmatched * length > Math.min(.5, length * .5);
    }

    private static double offsetCost(LocalRoadNetwork.Candidate match, LocalRoadNetwork.Point origin,
                                     LocalRoadNetwork.Point step) {
        LocalRoadNetwork.Edge edge = match.edge();
        double t = (match.start() + match.end()) * .5;
        LocalRoadNetwork.Point midpoint = new LocalRoadNetwork.Point(origin.x() + t * step.x(),
                origin.y() + t * step.y());
        double offset = Math.abs(edge.axis.cross(midpoint.minus(edge.a))) / edge.length;
        return offset * (match.end() - match.start());
    }

    private static boolean credible(Fix a, Fix b) {
        if (a == null || b == null || a.point() == null || b.point() == null) return false;
        if (!(a.accuracyMetres() > 0 && a.accuracyMetres() <= MAX_ACCURACY_M
                && b.accuracyMetres() > 0 && b.accuracyMetres() <= MAX_ACCURACY_M)) return false;
        long duration = b.timeMillis() - a.timeMillis();
        double distance = a.point().distance(b.point());
        return duration > 0 && duration <= MAX_INTERVAL_MS
                && distance >= MIN_STEP_M && distance <= MAX_STEP_M
                && distance / (duration / 1000.0) <= 45;
    }

    private static Match match(Point observedStart, Point observedEnd, Edge edge) {
        double dx = edge.end().x() - edge.start().x();
        double dy = edge.end().y() - edge.start().y();
        double len2 = dx * dx + dy * dy;
        double movementX = observedEnd.x() - observedStart.x();
        double movementY = observedEnd.y() - observedStart.y();
        double movement = Math.hypot(movementX, movementY);
        double alignment = Math.abs((dx * movementX + dy * movementY) / Math.sqrt(len2) / movement);
        if (alignment < MIN_HEADING_COS) return null;
        double u = ((observedStart.x() - edge.start().x()) * dx
                + (observedStart.y() - edge.start().y()) * dy) / len2;
        double v = ((observedEnd.x() - edge.start().x()) * dx
                + (observedEnd.y() - edge.start().y()) * dy) / len2;
        if (u < 0 || u > 1 || v < 0 || v > 1) return null;
        double offA = Math.abs((observedStart.x() - edge.start().x()) * dy
                - (observedStart.y() - edge.start().y()) * dx) / Math.sqrt(len2);
        double offB = Math.abs((observedEnd.x() - edge.start().x()) * dy
                - (observedEnd.y() - edge.start().y()) * dx) / Math.sqrt(len2);
        if (offA > MAX_OFF_EDGE_M || offB > MAX_OFF_EDGE_M) return null;
        int a = (int) Math.round(Math.min(u, v) * edge.millimetres());
        int b = (int) Math.round(Math.max(u, v) * edge.millimetres());
        return b <= a ? null : new Match(edge.sectionId(), a, b);
    }
}
