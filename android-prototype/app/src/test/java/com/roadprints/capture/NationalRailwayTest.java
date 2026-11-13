package com.roadprints.capture;

import static org.junit.Assert.*;

import java.util.*;
import java.util.concurrent.CancellationException;
import org.junit.Test;

public class NationalRailwayTest {
    private static final long START = 1_790_000_000_000L;
    private static final long END = START+600_000;

    @Test public void connectedRouteCrossesTunnelAndWorksInBothDirections() throws Exception {
        var graph = new Fixture(false, false);
        var matcher = new NationalRailway(graph);
        for (boolean reverse : new boolean[]{false,true}) {
            var result = matcher.infer(fixes(reverse,false),START,END,()->false);
            assertEquals("inferred",result.confidence());
            assertEquals(6000,result.metres(),0);
            assertEquals(reverse ? "East → West" : "West → East",result.endpoints());
        }
        assertThrows(CancellationException.class,()->matcher.infer(fixes(false,false),START,END,()->true));
    }

    @Test public void gapAcrossBranchKeepsMileageAmbiguous() throws Exception {
        var matcher = new NationalRailway(new Fixture(true,false));
        var result = matcher.infer(fixes(false,false),START,END,()->false);
        assertEquals("ambiguous",result.confidence());
        assertEquals(0,result.metres(),0);
    }

    @Test public void parallelRoadCannotSupplyRailWitnesses() throws Exception {
        var matcher = new NationalRailway(new Fixture(false,true));
        var result = matcher.infer(fixes(false,true),START,END,()->false);
        assertEquals("partial",result.confidence());
        assertEquals(0,result.metres(),0);
    }

    private static List<RailwayMatcher.Fix> fixes(boolean reverse,boolean road) {
        List<RailwayMatcher.Fix> out = new ArrayList<>();
        for (int i=0;i<=60;i++) {
            int x = (reverse ? 60-i : i)*100;
            if (x>2000 && x<4000) continue;
            out.add(new RailwayMatcher.Fix(x,road?4:0,8,START+i*10_000));
        }
        return out;
    }

    private static final class Fixture implements NationalRailway.Graph {
        private final Map<Long,List<NationalRailway.Track>> graph = new HashMap<>();
        private final boolean road;

        Fixture(boolean alternative,boolean road) {
            this.road = road;
            for (int i=0;i<60;i++) edge(i,i,i+1,i*100,0,(i+1)*100,0,i>=20&&i<40);
            if (alternative) {
                edge(100,20,100,2000,0,2400,400,false);
                edge(101,100,101,2400,400,3600,400,false);
                edge(102,101,40,3600,400,4000,0,false);
            }
        }

        private void edge(long id,long from,long to,double ax,double ay,double bx,double by,boolean tunnel) {
            var edge = new RailwayMatcher.Edge(id,from,to,ax,ay,bx,by,Math.hypot(bx-ax,by-ay),"",true);
            var track = new NationalRailway.Track(edge,tunnel);
            graph.computeIfAbsent(from,ignored->new ArrayList<>()).add(track);
            graph.computeIfAbsent(to,ignored->new ArrayList<>()).add(track);
        }

        @Override public List<NationalRailway.Station> stations(RailwayMatcher.Fix fix,double radius) {
            if (fix.x()==0) return List.of(new NationalRailway.Station(1,0,"West"));
            if (fix.x()==6000) return List.of(new NationalRailway.Station(2,60,"East"));
            return List.of();
        }

        @Override public List<NationalRailway.Track> adjacent(long node) {
            return graph.getOrDefault(node,List.of());
        }

        @Override public double roadResidual(RailwayMatcher.Fix fix) { return road?0:24; }
    }
}
