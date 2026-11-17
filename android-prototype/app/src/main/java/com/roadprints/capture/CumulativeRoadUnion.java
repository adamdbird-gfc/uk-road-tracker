package com.roadprints.capture;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reference-network interval aggregation only. Callers must resolve every input
 * onto the SAME versioned canonical network first; proximity is not proof.
 * No archive, Map, motorway or A-road ledger is modified by this class.
 */
final class CumulativeRoadUnion {
    enum Evidence { SUPPORTED, ACCEPTED_MATCH, INFERRED }

    record Interval(int startMm, int endMm) {
        Interval {
            if (startMm < 0 || endMm <= startMm)
                throw new IllegalArgumentException("Invalid positive millimetre interval");
        }
    }

    record Contribution(String journeyId, String sectionId, Interval interval, Evidence evidence) {
        Contribution {
            if (journeyId == null || journeyId.isBlank() || sectionId == null || sectionId.isBlank()
                    || interval == null || evidence == null)
                throw new IllegalArgumentException("Missing contribution identity or evidence");
        }
    }

    record VisibleSpan(String sectionId, Interval interval, Set<String> contributors) {
        VisibleSpan { contributors = Collections.unmodifiableSet(new HashSet<>(contributors)); }
    }

    private final Map<String, List<Contribution>> byJourney = new HashMap<>();
    private final Map<String, List<Contribution>> rejectionsByJourney = new HashMap<>();

    /** Replace one journey atomically, including its owner-rejected sections. */
    void replaceJourney(String journeyId, List<Contribution> sections, List<Contribution> rejected) {
        if (journeyId == null || journeyId.isBlank() || sections == null || rejected == null)
            throw new IllegalArgumentException("Journey id and collections required");
        for (Contribution c : sections)
            if (!journeyId.equals(c.journeyId())) throw new IllegalArgumentException("Cross-journey evidence");
        for (Contribution c : rejected)
            if (!journeyId.equals(c.journeyId())) throw new IllegalArgumentException("Cross-journey rejection");
        byJourney.put(journeyId, List.copyOf(sections));
        rejectionsByJourney.put(journeyId, List.copyOf(rejected));
    }

    void removeJourney(String journeyId) {
        byJourney.remove(journeyId);
        rejectionsByJourney.remove(journeyId);
    }

    /** Disjoint accepted intervals (observed or saved matched), retaining contributing journeys. */
    List<VisibleSpan> supported() {
        Map<String, List<Contribution>> bySection = new HashMap<>();
        for (Map.Entry<String, List<Contribution>> entry : byJourney.entrySet()) {
            List<Contribution> rejected = rejectionsByJourney.getOrDefault(entry.getKey(), List.of());
            for (Contribution candidate : entry.getValue()) {
                if (candidate.evidence() == Evidence.INFERRED) continue;
                List<Interval> remaining = new ArrayList<>(List.of(candidate.interval()));
                for (Contribution r : rejected)
                    if (r.sectionId().equals(candidate.sectionId()))
                        remaining = subtract(remaining, r.interval());
                for (Interval interval : remaining)
                    bySection.computeIfAbsent(candidate.sectionId(), ignored -> new ArrayList<>())
                        .add(new Contribution(candidate.journeyId(), candidate.sectionId(),
                            interval, candidate.evidence()));
            }
        }
        List<VisibleSpan> result = new ArrayList<>();
        for (Map.Entry<String, List<Contribution>> entry : bySection.entrySet()) {
            Set<Integer> boundary = new HashSet<>();
            for (Contribution c : entry.getValue()) {
                boundary.add(c.interval().startMm());
                boundary.add(c.interval().endMm());
            }
            List<Integer> cuts = new ArrayList<>(boundary);
            Collections.sort(cuts);
            List<VisibleSpan> spans = new ArrayList<>();
            for (int i = 1; i < cuts.size(); i++) {
                int start = cuts.get(i - 1), end = cuts.get(i);
                Set<String> owners = new HashSet<>();
                for (Contribution c : entry.getValue())
                    if (c.interval().startMm() <= start && c.interval().endMm() >= end)
                        owners.add(c.journeyId());
                if (!owners.isEmpty())
                    spans.add(new VisibleSpan(entry.getKey(), new Interval(start, end), owners));
            }
            // Merge contiguous intervals only if their provenance agrees.
            List<VisibleSpan> merged = new ArrayList<>();
            for (VisibleSpan span : spans) {
                int n = merged.size();
                if (n > 0) {
                    VisibleSpan last = merged.get(n - 1);
                    if (last.interval().endMm() == span.interval().startMm()
                            && last.contributors().equals(span.contributors())) {
                        merged.set(n - 1, new VisibleSpan(span.sectionId(),
                            new Interval(last.interval().startMm(), span.interval().endMm()),
                            span.contributors()));
                        continue;
                    }
                }
                merged.add(span);
            }
            result.addAll(merged);
        }
        result.sort(Comparator.comparing(VisibleSpan::sectionId)
                .thenComparingInt(span -> span.interval().startMm()));
        return List.copyOf(result);
    }

    long uniqueMillimetres() {
        long total = 0;
        for (VisibleSpan span : supported())
            total += span.interval().endMm() - span.interval().startMm();
        return total;
    }

    private static List<Interval> subtract(List<Interval> intervals, Interval denied) {
        List<Interval> result = new ArrayList<>();
        for (Interval interval : intervals) {
            if (denied.endMm() <= interval.startMm() || denied.startMm() >= interval.endMm()) {
                result.add(interval);
            } else {
                if (denied.startMm() > interval.startMm())
                    result.add(new Interval(interval.startMm(), denied.startMm()));
                if (denied.endMm() < interval.endMm())
                    result.add(new Interval(denied.endMm(), interval.endMm()));
            }
        }
        return result;
    }
}
