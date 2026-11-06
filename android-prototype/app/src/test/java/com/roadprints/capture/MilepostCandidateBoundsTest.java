package com.roadprints.capture;

import java.util.*;
import net.sf.geographiclib.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class MilepostCandidateBoundsTest {
    static MilepostProximity.Evidence evidence(MilepostProximity.Segment s) {
        return new MilepostProximity.Evidence(){
            public String journeyId(){return "j";}public String journeyDate(){return "2026-11-05T00:00:00Z";}
            public String mode(){return "walking";}public String source(){return "synthetic";}public String token(){return "token";}
            public String validationRevision(){return "v";}public MilepostProximity.Outcome problem(){return null;}
            public Iterable<MilepostProximity.Segment> segments(){return Collections.singletonList(s);}
        };
    }
    static boolean contains(MilepostCandidateBounds b,MilepostProximity.Point p) {
        if(b.global)return true;if(!b.any||p.lat<b.south||p.lat>b.north)return false;
        if(b.east-b.west>=360)return true;
        double w=MilepostCandidateBounds.wrap(b.west),e=MilepostCandidateBounds.wrap(b.east);
        return w<=e?p.lon>=w&&p.lon<=e:p.lon>=w||p.lon<=e;
    }
    @Test public void randomWgs84BuffersDateLinePolesAndLatitudeExtremesNeverExcluded() {
        Random random=new Random(74104);
        for(double lat:new double[]{-90,-89.999,-80,-51,0,51,80,89.999,90})
        for(double lon:new double[]{-180,-179.999,0,179.999,180})
        for(int n=0;n<5;n++) {
            double bearing=random.nextDouble()*360,length=1+random.nextDouble()*9998;
            GeodesicData end=Geodesic.WGS84.Direct(lat,lon,bearing,length);
            MilepostProximity.Segment edge=new MilepostProximity.Segment(new MilepostProximity.Point(lon,lat),new MilepostProximity.Point(end.lon2,end.lat2),0,0,1);
            MilepostCandidateBounds b=MilepostCandidateBounds.of(evidence(edge),()->false);
            GeodesicLine line=Geodesic.WGS84.InverseLine(lat,lon,end.lat2,end.lon2);
            for(double fraction:new double[]{0,.1,.5,.9,1}) {
                GeodesicData point=line.Position(length*fraction);
                for(double az:new double[]{0,45,90,135,180,225,270,315}) {
                    GeodesicData marker=Geodesic.WGS84.Direct(point.lat2,point.lon2,az,100.00001);
                    assertTrue("Lost boundary at "+lat+","+lon,contains(b,new MilepostProximity.Point(marker.lon2,marker.lat2)));
                }
            }
        }
    }
    @Test public void malformedAndLongUnsupportedEdgesFallBackToExactValidator() {
        for(MilepostProximity.Segment edge:Arrays.asList(null,new MilepostProximity.Segment(new MilepostProximity.Point(0,0),new MilepostProximity.Point(10,10),0,0,1)))
            assertTrue(MilepostCandidateBounds.of(evidence(edge),()->false).global);
    }
    @Test public void disconnectedSectionsOnlyExpandSelectionNeverCreateProximity() {
        MilepostProximity.Segment edge=new MilepostProximity.Segment(new MilepostProximity.Point(179.999,0),new MilepostProximity.Point(-179.999,0),0,0,1);
        MilepostCandidateBounds b=MilepostCandidateBounds.of(evidence(edge),()->false);
        assertTrue(contains(b,new MilepostProximity.Point(180,0)));assertTrue(contains(b,new MilepostProximity.Point(-180,0)));
        assertFalse(contains(b,new MilepostProximity.Point(0,0)));assertTrue(b.sql().contains(" OR "));
    }
}
