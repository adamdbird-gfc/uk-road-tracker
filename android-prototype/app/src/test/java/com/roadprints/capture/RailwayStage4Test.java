package com.roadprints.capture;

import static org.junit.Assert.*;

import java.util.*;
import java.util.concurrent.CancellationException;
import org.junit.Test;

public class RailwayStage4Test {
    static RailwayMatcher.Graph graph(boolean competingRoad, boolean held) {
        return new RailwayMatcher.Graph() {
            public List<RailwayMatcher.Edge> nearby(RailwayMatcher.Fix p,double radius,boolean road) {
                if (road && !competingRoad) return List.of();
                double y = road?0:competingRoad?18:0;
                return List.of(new RailwayMatcher.Edge(1,10,11,0,y,500,y,500,"HDR",!road));
            }
            public List<RailwayMatcher.Edge> adjacent(String ref,long node) { return List.of(); }
            public RailwayMatcher.Axis axis(String ref,RailwayMatcher.Hit h) {
                return new RailwayMatcher.Axis(h.x(),true);
            }
            public boolean held(String ref,double lo,double hi) { return held; }
        };
    }

    @Test public void fullDistanceRequiresBothBoundariesAndSupportedPairs() {
        var fixes = List.of(new RailwayMatcher.Fix(0,0,8,0),
                new RailwayMatcher.Fix(100,0,8,5000),new RailwayMatcher.Fix(200,0,8,10000),
                new RailwayMatcher.Fix(300,0,8,15000),new RailwayMatcher.Fix(400,0,8,20000));
        var matcher = new RailwayMatcher(graph(false,false),()->false);
        var full = matcher.match(fixes,0,20000);
        assertTrue(full.complete());
        assertEquals(400,full.supportedMetres(),.001);
        assertEquals("partial",matcher.match(fixes,0,21000).confidence());
        var missing = new ArrayList<>(fixes);
        missing.add(new RailwayMatcher.Fix(500,0,8,40000));
        assertEquals("partial",matcher.match(missing,0,40000).confidence());
        assertEquals(0,new RailwayMatcher(graph(true,false),()->false).match(fixes,0,20000)
                .supportedMetres(),.001);
        assertEquals(0,new RailwayMatcher(graph(false,true),()->false).match(fixes,0,20000)
                .supportedMetres(),.001);
        assertThrows(CancellationException.class,
                () -> new RailwayMatcher(graph(false,false),()->true).match(fixes,0,20000));
    }
}
