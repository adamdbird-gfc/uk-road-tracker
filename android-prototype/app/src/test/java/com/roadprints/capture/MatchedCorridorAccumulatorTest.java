package com.roadprints.capture;

import static org.junit.Assert.*;
import org.json.JSONArray;
import org.junit.Test;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** One worldwide accepted matched-corridor display ledger, regardless of mode. */
public final class MatchedCorridorAccumulatorTest {
    private static JSONArray line(String json) throws Exception { return new JSONArray(json); }

    private static final class Ledger {
        final List<JSONArray> lines = new ArrayList<>();
        final List<Set<String>> contributors = new ArrayList<>();
        final Set<Integer> walkingFallback = new HashSet<>();
        final Map<String, Integer> identities = new HashMap<>();
        MatchedCorridorAccumulator.Result add(String geometry, String id, boolean foot)
                throws Exception {
            return MatchedCorridorAccumulator.add(line(geometry), id, foot,
                    lines, contributors, walkingFallback, identities,
                    16, 100, lines.size(), totalPoints());
        }
        int totalPoints() { return lines.stream().mapToInt(JSONArray::length).sum(); }
    }

    @Test public void walkingAndDrivingOnSameAcceptedGeometryAreOneCorridor()
            throws Exception {
        Ledger ledger = new Ledger();
        String forward = "[[1.83,50.95],[1.831,50.951],[1.832,50.952]]";
        String reverse = "[[1.832,50.952],[1.831,50.951],[1.83,50.95]]";
        assertEquals(MatchedCorridorAccumulator.Result.ADDED,
                ledger.add(forward, "walk-calais", true));
        assertEquals(MatchedCorridorAccumulator.Result.REUSED,
                ledger.add(reverse, "drive-calais", false));
        assertEquals(1, ledger.lines.size());
        assertEquals(Set.of("walk-calais", "drive-calais"), ledger.contributors.get(0));
        assertEquals(Set.of(0), ledger.walkingFallback);
        assertTrue(MatchedCorridorAccumulator.coherent(
                ledger.lines, ledger.contributors, ledger.walkingFallback));
    }

    @Test public void drivingThenRunningSharesOneLineAndRetainsFootpathProvenance()
            throws Exception {
        Ledger ledger = new Ledger();
        String geometry = "[[0.375,51.44],[0.376,51.4401]]";
        assertEquals(MatchedCorridorAccumulator.Result.ADDED,
                ledger.add(geometry, "drive-gravesend", false));
        assertTrue(ledger.walkingFallback.isEmpty());
        assertEquals(MatchedCorridorAccumulator.Result.REUSED,
                ledger.add(geometry, "run-gravesend", true));
        assertEquals(Set.of("drive-gravesend", "run-gravesend"), ledger.contributors.get(0));
        assertEquals(Set.of(0), ledger.walkingFallback);
    }

    @Test public void adjacentPhysicalPathStaysDistinctEvenWithSameStartAndFinish()
            throws Exception {
        Ledger ledger = new Ledger();
        ledger.add("[[0.375,51.44],[0.376,51.4401]]", "walk-road", true);
        ledger.add("[[0.375,51.4401],[0.376,51.4402]]", "walk-path", true);
        assertEquals(2, ledger.lines.size());
        assertEquals(1, ledger.contributors.get(0).size());
        assertEquals(1, ledger.contributors.get(1).size());
    }

    @Test public void budgetHoldsWithoutLosingEarlierCorridorsOrAddingIncompleteHistory()
            throws Exception {
        Ledger ledger = new Ledger();
        String route = "[[2.33,48.85],[2.331,48.851]]";
        assertEquals(MatchedCorridorAccumulator.Result.ADDED,
                ledger.add(route, "paris-one", false));
        assertEquals(MatchedCorridorAccumulator.Result.HELD,
                MatchedCorridorAccumulator.add(line("[[2.332,48.852],[2.333,48.853]]"),
                        "paris-two",false,ledger.lines,ledger.contributors,
                        ledger.walkingFallback,ledger.identities,1,100,1,2));
        assertEquals(MatchedCorridorAccumulator.Result.REUSED,
                MatchedCorridorAccumulator.add(line(route), "paris-repeated", true,
                        ledger.lines,ledger.contributors,ledger.walkingFallback,
                        ledger.identities,1,100,1,2));
        assertEquals(1, ledger.lines.size());
        assertEquals(2, ledger.contributors.get(0).size());
    }

    @Test public void snapshotMustNotAcceptOrphanedLineOrBrokenContributorHistory()
            throws Exception {
        Ledger ledger = new Ledger();
        ledger.lines.add(line("[[0.375,51.44],[0.376,51.44]]"));
        assertFalse(MatchedCorridorAccumulator.coherent(
                ledger.lines, ledger.contributors, ledger.walkingFallback));
        ledger.contributors.add(Set.of());
        assertFalse(MatchedCorridorAccumulator.coherent(
                ledger.lines, ledger.contributors, ledger.walkingFallback));
    }

    @Test public void slightlyShiftedAcceptedWalkAndDriveSharePhysicalCorridor()
            throws Exception {
        Ledger ledger = new Ledger();
        assertEquals(MatchedCorridorAccumulator.Result.ADDED,
                ledger.add("[[0.375,51.44],[0.3755,51.44],[0.376,51.44]]",
                        "walk-shared", true));
        // The saved driving match is ~0.9m north and has different vertices.
        assertEquals(MatchedCorridorAccumulator.Result.REUSED,
                ledger.add("[[0.376,51.440008],[0.37575,51.440008],"
                        + "[0.37525,51.440008],[0.375,51.440008]]",
                        "drive-shared", false));
        assertEquals(1, ledger.lines.size());
        assertEquals(Set.of("walk-shared","drive-shared"), ledger.contributors.get(0));
    }

    @Test public void nearbyParallelFootpathRemainsDistinctFromDrivenRoad()
            throws Exception {
        Ledger ledger = new Ledger();
        ledger.add("[[0.375,51.44],[0.376,51.44]]", "drive", false);
        // ~3.0m north: two physical pavements/carriageway alignments may
        // be separate; display must not silently credit either as the other.
        assertEquals(MatchedCorridorAccumulator.Result.ADDED,
                ledger.add("[[0.375,51.440027],[0.376,51.440027]]",
                        "separate-footway", true));
        assertEquals(2, ledger.lines.size());
    }

    @Test public void crossingAndSharedEndpointsDoNotConflateDifferentPaths()
            throws Exception {
        Ledger ledger = new Ledger();
        ledger.add("[[0.375,51.44],[0.3755,51.44],[0.376,51.44]]",
                "road", false);
        assertEquals(MatchedCorridorAccumulator.Result.ADDED,
                ledger.add("[[0.375,51.44],[0.3755,51.44008],[0.376,51.44]]",
                        "different-arc", true));
        assertEquals(2, ledger.lines.size());
    }

    @Test public void shortFragmentsAreNotFuzzyMergedWithoutStrongEvidence()
            throws Exception {
        Ledger ledger = new Ledger();
        ledger.add("[[0.375,51.44],[0.3751,51.44]]", "short-walk", true);
        assertEquals(MatchedCorridorAccumulator.Result.ADDED,
                ledger.add("[[0.375,51.440005],[0.3751,51.440005]]",
                        "short-drive", false));
        assertEquals(2, ledger.lines.size());
    }

    @Test public void partialOverlapDoesNotEraseTheUnsharedCorridor()
            throws Exception {
        Ledger ledger = new Ledger();
        ledger.add("[[2.33,48.85],[2.331,48.85]]", "long-route", false);
        assertEquals(MatchedCorridorAccumulator.Result.ADDED,
                ledger.add("[[2.33,48.85],[2.3305,48.85]]", "half-route", true));
        assertEquals(2, ledger.lines.size());
    }

    @Test public void fuzzyReusePersistsDistinctContributorIdsWithoutAddingPoints()
            throws Exception {
        Ledger ledger = new Ledger();
        String a = "[[1.43,38.91],[1.431,38.91]]";
        String b = "[[1.43,38.910005],[1.431,38.910005]]";
        assertEquals(MatchedCorridorAccumulator.Result.ADDED,
                ledger.add(a,"ibiza-walk",true));
        assertEquals(MatchedCorridorAccumulator.Result.REUSED,
                ledger.add(b,"ibiza-cycle",false));
        assertEquals(MatchedCorridorAccumulator.Result.REUSED,
                ledger.add(b,"ibiza-cycle",false));
        assertEquals(1,ledger.lines.size());
        assertEquals(Set.of("ibiza-walk","ibiza-cycle"),ledger.contributors.get(0));
        assertEquals(2,ledger.totalPoints());
        assertTrue(MatchedCorridorAccumulator.coherent(
                ledger.lines,ledger.contributors,ledger.walkingFallback));
    }
}
