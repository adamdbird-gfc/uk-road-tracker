package com.roadprints.capture;

import static org.junit.Assert.*;

import java.util.HashSet;
import java.util.Set;
import org.junit.Test;

/** Shared per-leg candidate collection must not change accepted OSM identities. */
public final class RoadCandidateCollectionTest {
    @Test public void collectingAcrossAdjacentReferenceTilesEqualsUnionOfOriginalCandidateSets() {
        LocalRoadNetwork first = new LocalRoadNetwork(0, 0, .02, .02, 51);
        LocalRoadNetwork second = new LocalRoadNetwork(0, 0, .02, .02, 51);
        LocalRoadNetwork.Point a = first.point(.002, .005);
        LocalRoadNetwork.Point b = first.point(.008, .005);
        LocalRoadNetwork.Point c = first.point(.002, .0051);
        LocalRoadNetwork.Point d = first.point(.008, .0051);
        LocalRoadNetwork.Edge sharedA = new LocalRoadNetwork.Edge(1, 1, 2, "Shared", a, b, 0, true);
        LocalRoadNetwork.Edge sharedB = new LocalRoadNetwork.Edge(1, 1, 2, "Shared", a, b, 0, true);
        LocalRoadNetwork.Edge parallel = new LocalRoadNetwork.Edge(2, 3, 4, "Parallel", c, d, 0, true);
        first.add(sharedA);
        second.add(sharedB);
        second.add(parallel);

        LocalRoadNetwork.Point searchA = first.point(.003, .005);
        LocalRoadNetwork.Point searchB = first.point(.006, .005);
        Set<LocalRoadNetwork.Edge> expected = new HashSet<>(first.nearby(searchA, searchB, 8));
        expected.addAll(second.nearby(searchA, searchB, 8));

        Set<LocalRoadNetwork.Edge> actual = new HashSet<>();
        first.collectNearby(searchA, searchB, 8, actual);
        second.collectNearby(searchA, searchB, 8, actual);
        assertEquals(expected, actual);
        assertEquals(2, actual.size());
        assertTrue(actual.contains(sharedA));
        assertTrue(actual.contains(parallel));
        // A repeated tile visit must never duplicate credited physical sections.
        second.collectNearby(searchA, searchB, 8, actual);
        assertEquals(expected, actual);
    }

    @Test public void emptyNeighbourhoodProducesNoCandidate() {
        LocalRoadNetwork network = new LocalRoadNetwork(0, 0, .02, .02, 51);
        LocalRoadNetwork.Point a = network.point(.003, .005);
        LocalRoadNetwork.Point b = network.point(.004, .005);
        Set<LocalRoadNetwork.Edge> candidates = new HashSet<>();
        network.collectNearby(a, b, 8, candidates);
        assertTrue(candidates.isEmpty());
    }
}
