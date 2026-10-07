package com.roadprints.capture;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class HistoricCountyGeometryTest {
    private int[] square(double x,double y,double size) {
        return new int[]{(int)(x*1e6),(int)(y*1e6),(int)((x+size)*1e6),(int)(y*1e6),
                (int)((x+size)*1e6),(int)((y+size)*1e6),(int)(x*1e6),(int)((y+size)*1e6)};
    }
    @Test public void holesAndSeparateIslandsPreservePolygonMembership() {
        HistoricCountyGeometry g=new HistoricCountyGeometry(Arrays.asList(square(0,0,4),square(1,1,2),square(5,0,1)));
        assertTrue(g.contains(.5,.5));assertFalse(g.contains(2,2));assertTrue(g.contains(5.5,.5));assertFalse(g.contains(4.5,.5));
        assertFalse(g.intersects(1.5,2,2.5,2));assertTrue(g.intersects(.5,2,3.5,2));
    }
    @Test public void roadCrossingWithoutAVertexInsideCountsButBoundaryTouchesDoNot() {
        HistoricCountyGeometry g=new HistoricCountyGeometry(Collections.singletonList(square(0,0,1)));
        assertTrue(g.intersects(-1,.5,2,.5));assertFalse(g.intersects(-1,0,0,0));
        assertFalse(g.intersects(0,0,1,0));assertFalse(g.intersects(.5,.5,.5,.5));
        assertFalse(g.intersects(Double.NaN,0,1,1));
    }
}
