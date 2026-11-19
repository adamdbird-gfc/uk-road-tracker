package com.roadprints.capture;

import static org.junit.Assert.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.json.JSONArray;
import org.junit.Test;

public final class MatchedCorridorIntervalUnionTest {
    private static JSONArray line(double ax, double ay, double bx, double by) {
        try {
            return new JSONArray().put(new JSONArray().put(ax).put(ay))
                    .put(new JSONArray().put(bx).put(by));
        } catch (org.json.JSONException invalid) {
            throw new AssertionError(invalid);
        }
    }
    private static final double LON = 0.375, LAT = 51.44, STEP = 0.00144;
    private static final class Input {
        final List<JSONArray> lines = new ArrayList<>();
        final List<Set<String>> contributors = new ArrayList<>();
        final Set<Integer> foot = new HashSet<>();
        void add(double start, double end, double latitudeDrift, String journey, boolean onFoot) {
            int idx=lines.size();
            lines.add(line(LON+STEP*start,LAT+latitudeDrift,LON+STEP*end,LAT+latitudeDrift));
            contributors.add(new HashSet<>(Set.of(journey)));
            if (onFoot) foot.add(idx);
        }
        MatchedCorridorIntervalUnion.Result union() {
            return MatchedCorridorIntervalUnion.reconcile(lines, contributors, foot, 32, 100);
        }
    }
    @Test public void wholeStreetAndHalfStreetShareOnlyTheirTravelledSubinterval()
            throws Exception {
        Input input=new Input();
        input.add(0,1,0,"full-drive",false);
        input.add(0.2,0.6,0,"partial-walk",true);
        var result=input.union();
        assertFalse(result.budgetHeld());
        assertEquals(1,result.mergedInputs());
        assertEquals(3,result.lines().size());
        assertEquals(Set.of("full-drive"),result.contributors().get(0));
        assertEquals(Set.of("full-drive","partial-walk"),result.contributors().get(1));
        assertEquals(Set.of("full-drive"),result.contributors().get(2));
        assertEquals(Set.of(1),result.footIndices());
    }
    @Test public void reversedProcessingOrderProducesTheSameContributorIntervals()
            throws Exception {
        Input input=new Input();
        input.add(.6,.2,0,"partial-run",true);
        input.add(1,0,0,"full-cycle",false);
        var result=input.union();
        assertEquals(3,result.lines().size());
        assertEquals(Set.of("full-cycle"),result.contributors().get(0));
        assertEquals(Set.of("partial-run","full-cycle"),result.contributors().get(1));
        assertEquals(Set.of("full-cycle"),result.contributors().get(2));
        assertEquals(Set.of(1),result.footIndices());
    }
    @Test public void repeatedOverlappingWalksPreserveThreeSourceAttributions()
            throws Exception {
        Input input=new Input();
        input.add(0,1,0,"full",false);
        input.add(.2,.7,0,"lunch-walk",true);
        input.add(.4,.8,0,"evening-run",true);
        var result=input.union();
        assertEquals(5,result.lines().size());
        assertEquals(Set.of("full","lunch-walk","evening-run"),result.contributors().get(2));
        assertTrue(result.footIndices().contains(2));
    }
    @Test public void adjacentFootwayAndParallelCarriagewayNeverJoin() throws Exception {
        Input input=new Input();
        input.add(0,1,0,"driving",false);
        input.add(.2,.7,0.00003,"distinct-path",true); // ~3.3m away
        var result=input.union();
        assertEquals(2,result.lines().size());
        assertEquals(Set.of("driving"),result.contributors().get(0));
        assertEquals(Set.of("distinct-path"),result.contributors().get(1));
    }
    @Test public void touchingEndpointsWithoutOverlapCreateNoSharedCorridor()
            throws Exception {
        Input input=new Input();
        input.add(0,.5,0,"first",true);
        input.add(.5,1,0,"second",true);
        var result=input.union();
        assertEquals(2,result.lines().size());
        assertEquals(Set.of("first"),result.contributors().get(0));
        assertEquals(Set.of("second"),result.contributors().get(1));
    }
    @Test public void unknownCurvedPathIsUnmodifiedWithoutSpeculativeJoins()
            throws Exception {
        Input input=new Input();
        input.add(0,1,0,"road",false);
        int idx=input.lines.size();
        input.lines.add(new JSONArray().put(new JSONArray().put(LON).put(LAT))
                .put(new JSONArray().put(LON+.0004).put(LAT+.0002))
                .put(new JSONArray().put(LON+STEP).put(LAT)));
        input.contributors.add(Set.of("curved"));
        input.foot.add(idx);
        var result=input.union();
        assertEquals(2,result.lines().size());
        assertEquals(3,result.lines().get(1).length());
    }
    @Test public void ambiguousMiddleAlignmentRemainsSeparate() throws Exception {
        Input input=new Input();
        input.add(0,1,0,"south",false);
        input.add(0,1,0.00001,"north",true); // 1.1m separate, outside 0.6m tolerance
        input.add(.2,.8,0.000005,"middle-unknown",true);
        var result=input.union();
        assertEquals(3,result.lines().size());
        assertEquals(1,result.ambiguous());
        assertEquals(Set.of("middle-unknown"),result.contributors().get(2));
    }
    @Test public void overBudgetKeepsOriginalSnapshotAndContributors()
            throws Exception {
        Input input=new Input();
        input.add(0,1,0,"full",false);
        input.add(.2,.6,0,"partial",true);
        var result=MatchedCorridorIntervalUnion.reconcile(input.lines,input.contributors,
                input.foot,2,100);
        assertTrue(result.budgetHeld());
        assertEquals(2,result.lines().size());
        assertEquals(Set.of("full"),result.contributors().get(0));
        assertEquals(Set.of("partial"),result.contributors().get(1));
    }
}
