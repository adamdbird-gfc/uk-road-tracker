package com.roadprints.capture;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;
import net.sf.geographiclib.Geodesic;
import net.sf.geographiclib.GeodesicData;
import org.json.*;

public class MilepostProximityTest {
    static final MilepostProximity.Point ORIGIN=new MilepostProximity.Point(-1,51.4);
    static MilepostProximity.Point offset(MilepostProximity.Point p,double heading,double metres){
        GeodesicData d=Geodesic.WGS84.Direct(p.lat,p.lon,heading,metres);return new MilepostProximity.Point(d.lon2,d.lat2);
    }
    static MilepostProximity.Marker marker(double distance){return new MilepostProximity.Marker("test-only:physical:1",offset(ORIGIN,0,distance),true,"test-only-2");}
    static MilepostProximity.Evidence proof(String mode,Iterable<MilepostProximity.Segment> segments){
        return proof("synthetic-journey",mode,segments);
    }
    static MilepostProximity.Evidence proof(String id,String mode,Iterable<MilepostProximity.Segment> segments){
        return new MilepostProximity.Evidence(){
            public String journeyId(){return id;}public String journeyDate(){return "2026-11-05T00:00:00Z";}
            public String mode(){return mode;}public String source(){return "test-only-independently-validated-timeline-section";}
            public String token(){return "fixture-token";}public String validationRevision(){return "test-validator-1";}
            public MilepostProximity.Outcome problem(){return null;}public Iterable<MilepostProximity.Segment> segments(){return segments;}
        };
    }
    static MilepostProximity.Segment segment(MilepostProximity.Point a,MilepostProximity.Point b,int index){return new MilepostProximity.Segment(a,b,0,index,index+1);}
    static MilepostProximity.Evidence pass(String mode){return proof(mode,Arrays.asList(segment(offset(ORIGIN,270,150),ORIGIN,0),segment(ORIGIN,offset(ORIGIN,90,150),1)));}
    static MilepostProximity.Result assess(MilepostProximity.Marker m,MilepostProximity.Evidence e){return MilepostProximity.assess(m,e,MilepostProximity.Policy.V1,()->false);}
    @Test public void allNineBoundaryDistances(){
        for(double d:new double[]{0,25,50,75,99,100,101,125,200}){
            MilepostProximity.Result r=assess(marker(d),pass("walking"));
            assertEquals("distance "+d,d<=100?MilepostProximity.Outcome.QUALIFIED:MilepostProximity.Outcome.OUTSIDE_RADIUS,r.outcome);
            assertEquals(d,r.closestMetres,.00001);
        }
    }
    @Test public void finiteSegmentInteriorBetweenSparseSamples(){
        assertEquals(MilepostProximity.Outcome.QUALIFIED,assess(marker(50),proof("cycling",Collections.singletonList(
                segment(offset(ORIGIN,270,150),offset(ORIGIN,90,150),0)))).outcome);
    }
    @Test public void neverExtrapolatesEndpoints(){
        assertEquals(MilepostProximity.Outcome.OUTSIDE_RADIUS,assess(marker(0),proof("walking",Collections.singletonList(
                segment(offset(ORIGIN,90,101),offset(ORIGIN,90,200),0)))).outcome);
    }
    @Test public void disconnectedSectionsNeverCreateConnector(){
        assertEquals(MilepostProximity.Outcome.OUTSIDE_RADIUS,assess(marker(0),proof("walking",Arrays.asList(
                segment(offset(ORIGIN,270,300),offset(ORIGIN,270,200),0),
                segment(offset(ORIGIN,90,200),offset(ORIGIN,90,300),2)))).outcome);
    }
    @Test public void independentPythonGeographicLibUkLatitudeOracles(){
        // Fixed oracle coordinates produced independently with Python GeographicLib 2.1, WGS84 Direct north 100m.
        double[][] oracles={{49.9,49.90089906110796},{51.4,51.400898828463575},
                {58.7,58.70089774727368},{60.85,60.85089745164139}};
        for(double[] o:oracles){
            MilepostProximity.Point a=new MilepostProximity.Point(-1,o[0]),b=new MilepostProximity.Point(-1,o[1]);
            assertEquals(100,MilepostProximity.distance(a,b),.001);
        }
    }
    @Test public void geographicPrecisionAndReversedSegmentsAtUkExtremes(){
        for(double lat:new double[]{49.9,51.4,58.7,60.85}){
            MilepostProximity.Point centre=new MilepostProximity.Point(-1,lat);
            MilepostProximity.Marker m=new MilepostProximity.Marker("test-only:uk",offset(centre,0,100),true,"test-only-2");
            MilepostProximity.Point a=offset(centre,270,75),b=offset(centre,90,75);
            for(MilepostProximity.Segment edge:new MilepostProximity.Segment[]{segment(a,b,0),segment(b,a,0)}){
                MilepostProximity.Result r=assess(m,proof("walking",Collections.singletonList(edge)));
                assertEquals(MilepostProximity.Outcome.QUALIFIED,r.outcome);assertEquals(100,r.closestMetres,.00001);
            }
        }
    }
    @Test public void toleranceIsOnlyTenMicrometres(){
        assertEquals(MilepostProximity.Outcome.QUALIFIED,assess(marker(100.000005),pass("running")).outcome);
        assertEquals(MilepostProximity.Outcome.OUTSIDE_RADIUS,assess(marker(100.0001),pass("running")).outcome);
    }
    @Test public void modesAndNoStopOrVisibilityInputs(){
        // Vegetation/no stop/parallel path are deliberately the same geometry-only input contract.
        for(String mode:new String[]{"walking","running","cycling"})assertEquals(MilepostProximity.Outcome.QUALIFIED,assess(marker(75),pass(mode)).outcome);
        for(String mode:new String[]{"car","driving","bus","train","ferry","plane","unknown"})assertEquals(MilepostProximity.Outcome.INELIGIBLE_MODE,assess(marker(0),pass(mode)).outcome);
    }
    @Test public void metadataAndRepeatedEvaluation(){
        MilepostProximity.Result a=assess(marker(25),pass("walking")),b=assess(marker(25),pass("walking"));
        assertEquals(a.closestMetres,b.closestMetres,0);assertEquals(a.sourceStart,b.sourceStart);
        assertEquals("synthetic-journey",a.journeyId);assertEquals("test-only:physical:1",a.markerId);
        assertEquals("journey",a.datePrecision);assertEquals("test-only-2",a.catalogueRevision);
        assertEquals("milepost-proximity-1",a.policyRevision);assertNotNull(a.evidenceToken);assertNotNull(a.validationRevision);
    }
    @Test public void multipleJourneysAndDistinctMarkersAreIndependent(){
        for(int i=0;i<5;i++){
            MilepostProximity.Marker m=new MilepostProximity.Marker("test-only:physical:"+i,marker(i*20).point,true,"test-only-2");
            MilepostProximity.Result r=assess(m,pass("cycling"));assertEquals(MilepostProximity.Outcome.QUALIFIED,r.outcome);
            assertEquals(m.physicalId,r.markerId);
        }
        for(String id:new String[]{"journey-a","journey-b"}){
            MilepostProximity.Result r=assess(marker(25),proof(id,"walking",pass("walking").segments()));
            assertEquals(MilepostProximity.Outcome.QUALIFIED,r.outcome);assertEquals(id,r.journeyId);
        }
        // No uniqueness counter, ledger or award operation exists here; Stage 3 owns deduplication.
    }
    @Test public void cancellationReturnsNoPartialProof(){assertEquals(MilepostProximity.Outcome.CANCELLED,
            MilepostProximity.assess(marker(0),pass("walking"),MilepostProximity.Policy.V1,()->true).outcome);}
    @Test public void emptyAndMalformedGeometryFailClosed(){
        assertEquals(MilepostProximity.Outcome.INSUFFICIENT_GEOMETRY,assess(marker(0),proof("walking",Collections.emptyList())).outcome);
        assertEquals(MilepostProximity.Outcome.UNRELIABLE_LOCATION,assess(marker(0),proof("walking",Collections.singletonList(segment(new MilepostProximity.Point(Double.NaN,0),ORIGIN,0)))).outcome);
        assertEquals(MilepostProximity.Outcome.INVALID_MARKER,assess(new MilepostProximity.Marker("x",new MilepostProximity.Point(0,91),true,"2"),pass("walking")).outcome);
    }
    @Test public void everyStageOneCandidateIsIneligible()throws Exception{
        java.nio.file.Path path=java.nio.file.Paths.get("reference-data/millennium-mileposts/revision-1/catalogue.jsonl");
        if(!java.nio.file.Files.exists(path))path=java.nio.file.Paths.get("../..").resolve(path);
        int count=0;
        try(java.io.BufferedReader reader=java.nio.file.Files.newBufferedReader(path)){
            String line;while((line=reader.readLine())!=null){JSONObject row=new JSONObject(line);
                assertFalse(row.getBoolean("eligible"));
                assertEquals(MilepostProximity.Outcome.MARKER_INELIGIBLE,assess(MilepostJourneyEvidence.candidate(row),pass("walking")).outcome);
                row.put("eligible",true); // A tampered research row cannot bypass the explicit schema boundary.
                assertEquals(MilepostProximity.Outcome.MARKER_INELIGIBLE,assess(MilepostJourneyEvidence.candidate(row),pass("walking")).outcome);count++;
            }
        }
        assertEquals(889,count);
    }
}
