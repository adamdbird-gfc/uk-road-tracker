package com.roadprints.capture;

import java.util.List;

/** Standalone Java regression harness, no Android runtime required. */
public final class CanonicalRoadAttributionTest {
    private static CanonicalRoadAttribution.Point p(double x, double y) {
        return new CanonicalRoadAttribution.Point(x, y);
    }
    private static CanonicalRoadAttribution.Fix f(double x, double y, long t, double acc) {
        return new CanonicalRoadAttribution.Fix(p(x, y), t, acc);
    }
    private static void check(boolean result, String label) {
        if (!result) throw new AssertionError(label);
    }
    public static void main(String[] args) {
        var one = new CanonicalRoadAttribution.Edge("osm:1", p(0, 0), p(100, 0));
        var other = new CanonicalRoadAttribution.Edge("osm:2", p(100, 0), p(100, 100));
        var mapper = new CanonicalRoadAttribution(List.of(one, other));
        var outbound = mapper.attributeCaptured(List.of(f(10, 1, 1000, 4), f(40, 2, 5000, 4)));
        check(outbound.acceptedPairs() == 1 && outbound.supported().get(0).startMm() == 10000,
                "observed section");
        var matched = mapper.attributeAcceptedRoute(List.of(p(10, 1), p(40, 2)));
        check(matched.acceptedPairs() == 1 && matched.supported().get(0).endMm() == 40000,
                "accepted saved matched route maps without timestamps or raw GPS");
        var matchedReverse = mapper.attributeAcceptedRoute(List.of(p(40, 2), p(10, 1)));
        check(matchedReverse.acceptedPairs() == 1 && matchedReverse.supported().get(0).startMm() == 10000,
                "accepted reverse route same interval");
        var inbound = mapper.attributeCaptured(List.of(f(40, 2, 1000, 4), f(10, 1, 5000, 4)));
        check(inbound.acceptedPairs() == 1 && inbound.supported().get(0).startMm() == 10000,
                "opposite direction shares physical section");
        check(mapper.attributeCaptured(List.of(f(10, 1, 1000, 25), f(40, 2, 5000, 4))).acceptedPairs() == 0,
                "bad accuracy rejected");
        check(mapper.attributeCaptured(List.of(f(10, 1, 1000, 4), f(40, 2, 60000, 4))).acceptedPairs() == 0,
                "long gap rejected");
        check(mapper.attributeCaptured(List.of(f(90, 0, 1000, 4), f(100, 40, 6000, 4))).acceptedPairs() == 0,
                "unsupported junction crossing rejected");
        var parallel = new CanonicalRoadAttribution(List.of(one,
                new CanonicalRoadAttribution.Edge("osm:near", p(0, 4), p(100, 4))));
        var acceptedAmbiguous = parallel.attributeAcceptedRoute(List.of(p(10, 2), p(40, 2)));
        check(acceptedAmbiguous.skippedAmbiguous() == 1 && acceptedAmbiguous.acceptedPairs() == 0,
                "matched route still needs unambiguous canonical identity");
        var ambiguous = parallel.attributeCaptured(List.of(f(10, 2, 1000, 4), f(40, 2, 5000, 4)));
        check(ambiguous.acceptedPairs() == 0 && ambiguous.skippedAmbiguous() == 1,
                "parallel carriageways unresolved");
        check(mapper.attributeCaptured(List.of()).examinedPairs() == 0, "empty capture");
        System.out.println("PASS: canonical matched-route and captured evidence, reverse, accuracy, timing, junction, ambiguity");
    }
}
