package com.roadprints.capture;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Converts measured GPS pairs into intervals on a supplied versioned road reference.
 * This is deliberately conservative: inferred/matched lines must never be passed in
 * place of raw fixes. It does not mutate historical completion or Android Map state.
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
     * Requires measured fixes from a real capture. Reconstructed route vertices
     * and sparse Timeline control points are not eligible for automatic credit.
     * A separate reference-aware continuity solver is needed across junctions.
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
