package com.roadprints.capture;

import java.util.List;
import java.util.Set;

/** Standalone JVM regression checks for the canonical road-union contract. */
public final class CumulativeRoadUnionTest {
    private static final CumulativeRoadUnion.Evidence OK = CumulativeRoadUnion.Evidence.SUPPORTED;
    private static final CumulativeRoadUnion.Evidence GUESS = CumulativeRoadUnion.Evidence.INFERRED;
    private static final CumulativeRoadUnion.Evidence MATCH = CumulativeRoadUnion.Evidence.ACCEPTED_MATCH;

    private static CumulativeRoadUnion.Contribution section(String trip, String edge,
            int start, int end, CumulativeRoadUnion.Evidence evidence) {
        return new CumulativeRoadUnion.Contribution(trip, edge,
                new CumulativeRoadUnion.Interval(start, end), evidence);
    }

    private static void check(boolean result, String reason) {
        if (!result) throw new AssertionError(reason);
    }

    public static void main(String[] args) {
        CumulativeRoadUnion union = new CumulativeRoadUnion();
        union.replaceJourney("out", List.of(section("out", "way/101", 0, 100_000, OK)), List.of());
        check(union.uniqueMillimetres() == 100_000, "initial journey");

        union.replaceJourney("back", List.of(section("back", "way/101", 0, 100_000, OK)), List.of());
        check(union.uniqueMillimetres() == 100_000, "reverse route must not double count");
        check(union.supported().get(0).contributors().equals(Set.of("out", "back")), "contributors");

        union.replaceJourney("different", List.of(
                section("different", "way/101", 30_000, 70_000, OK),
                section("different", "way/102", 0, 35_000, OK)), List.of());
        check(union.uniqueMillimetres() == 135_000, "alternative road adds unique distance");

        union.replaceJourney("accepted-timeline", List.of(
                section("accepted-timeline", "way/104", 0, 75_000, MATCH)), List.of());
        check(union.uniqueMillimetres() == 210_000,
                "accepted matched Timeline journey adds coverage without dense raw GPS");
        union.replaceJourney("duplicate-timeline", List.of(
                section("duplicate-timeline", "way/104", 0, 75_000, MATCH)), List.of());
        check(union.uniqueMillimetres() == 210_000, "repeat matched journeys do not double credit");
        union.replaceJourney("accepted-timeline", List.of(
                section("accepted-timeline", "way/104", 0, 75_000, MATCH)), List.of(
                section("accepted-timeline", "way/104", 10_000, 30_000, MATCH)));
        check(union.uniqueMillimetres() == 210_000,
                "one owner's rejection must not remove independently supported road");
        union.removeJourney("duplicate-timeline");
        check(union.uniqueMillimetres() == 190_000,
                "rejected interval withheld once no other journey supports it");
        union.removeJourney("accepted-timeline");
        check(union.uniqueMillimetres() == 135_000,
                "removing a contributing matched journey withdraws only its own coverage");

        union.replaceJourney("timeline", List.of(
                section("timeline", "way/103", 0, 250_000, GUESS)), List.of());
        check(union.uniqueMillimetres() == 135_000, "inferred geometry is not confirmed coverage");

        union.replaceJourney("out", List.of(
                section("out", "way/101", 0, 100_000, OK)), List.of(
                section("out", "way/101", 40_000, 50_000, OK)));
        check(union.uniqueMillimetres() == 135_000, "another journey still proves edited interval");

        union.removeJourney("back");
        check(union.uniqueMillimetres() == 135_000, "third journey still proves edited interval");
        union.removeJourney("different");
        check(union.uniqueMillimetres() == 90_000, "rejection is scoped to affected interval");

        union.replaceJourney("out", List.of(
                section("out", "way/101", 0, 100_000, OK)), List.of());
        check(union.uniqueMillimetres() == 100_000, "restoring an edit restores the span");

        union.replaceJourney("lane1", List.of(
                section("lane1", "way/201", 0, 25_000, OK)), List.of());
        union.replaceJourney("lane2", List.of(
                section("lane2", "way/202", 0, 25_000, OK)), List.of());
        check(union.uniqueMillimetres() == 150_000,
                "parallel carriageways with distinct IDs stay distinct");

        System.out.println("PASS: repeated and reverse journeys, new sections, inferred evidence, "
                + "corrections, contributor provenance, restoration and parallel carriageways");
    }
}
