package com.roadprints.capture;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.junit.Assert.*;

/** Pure JVM regressions: uses the same geometry and dwell code as the installed app. */
public class ServiceSiteBoundaryTest {
    private JSONObject site(String id,String coordinates)throws Exception {
        return new JSONObject().put("id",id).put("lat",52).put("lng",-2)
                .put("site_boundary",new JSONObject().put("type","MultiPolygon").put("coordinates",new JSONArray(coordinates)));
    }
    private JSONObject square()throws Exception {
        return site("one","[[[[-2.003,51.998],[-1.997,51.998],[-1.997,52.002],[-2.003,52.002],[-2.003,51.998]]]]");
    }
    private JSONArray samples(double lng,double lat)throws Exception {
        JSONArray s=new JSONArray();
        for(int i=0;i<25;i++)s.put(new JSONArray().put(lng).put(lat).put(10).put(1000000+i*10000).put(0));
        return s;
    }
    @Test public void groundsBeyondOldCircleQualifyButOutsideLocationsDoNot()throws Exception {
        JSONObject v=square();assertTrue(ServiceStationEvidence.distance(v,52,-2.002)>125);
        assertTrue(ServiceSiteBoundary.supports(v,52,-2.002,3));
        assertFalse(ServiceSiteBoundary.supports(v,52,-2.004,3));
        assertEquals("one",VenueStopDetector.stops(samples(-2.002,52),new JSONArray().put(v)).get(0).id);
    }
    @Test public void uncertaintyMustFitInsideGroundsAndNeverReachExcludedCarriageway()throws Exception {
        JSONObject v=square();assertTrue(ServiceSiteBoundary.supports(v,52,-2.0029,3));
        assertFalse(ServiceSiteBoundary.supports(v,52,-2.0029,20));
        v.getJSONObject("site_boundary").getJSONArray("coordinates").getJSONArray(0)
                .put(new JSONArray("[[-2.0002,51.9985],[-1.9998,51.9985],[-1.9998,52.0015],[-2.0002,52.0015],[-2.0002,51.9985]]"));
        assertFalse(ServiceSiteBoundary.supports(v,52,-2,0));
        assertFalse(ServiceSiteBoundary.supports(v,52,-2.00025,10));
        assertTrue(VenueStopDetector.stops(samples(-2,52),new JSONArray().put(v)).isEmpty());
    }
    @Test public void disconnectedSidesDoNotFillMotorwayGap()throws Exception {
        JSONObject v=site("both","[[[[-2.004,51.998],[-2.001,51.998],[-2.001,52.002],[-2.004,52.002],[-2.004,51.998]]],[[[-1.999,51.998],[-1.996,51.998],[-1.996,52.002],[-1.999,52.002],[-1.999,51.998]]]]");
        assertTrue(ServiceSiteBoundary.supports(v,52,-2.002,10));assertTrue(ServiceSiteBoundary.supports(v,52,-1.998,10));
        assertFalse(ServiceSiteBoundary.supports(v,52,-2,0));
    }
    @Test public void ambiguousSitesAndMalformedFootprintsCannotEarnVisits()throws Exception {
        JSONObject a=square(),b=new JSONObject(a.toString()).put("id","two");
        assertTrue(VenueStopDetector.stops(samples(-2,52),new JSONArray().put(a).put(b)).isEmpty());
        a.getJSONObject("site_boundary").put("coordinates",new JSONArray("[[[[-2,52],[-1,52],[-1,53]]]]"));
        assertFalse(ServiceSiteBoundary.valid(a.optJSONObject("site_boundary")));
        assertFalse(ServiceSiteBoundary.supports(a,52,-2,0));
        assertTrue(VenueStopDetector.stops(samples(-2,52),new JSONArray().put(a)).isEmpty());
    }
    @Test public void briefPoorFixDoesNotEraseDwellButLongGapOrMovementDoes()throws Exception {
        JSONArray s=samples(-2,52);s.getJSONArray(10).put(2,100);
        assertEquals(1,VenueStopDetector.stops(s,new JSONArray().put(square())).size());
        for(int i=4;i<17;i++)s.getJSONArray(i).put(2,100);
        assertTrue(VenueStopDetector.stops(s,new JSONArray().put(square())).isEmpty());
        s=samples(-2,52);s.getJSONArray(10).put(4,20);
        assertTrue(VenueStopDetector.stops(s,new JSONArray().put(square())).isEmpty());
    }
    @Test public void duplicateOrOutOfOrderFixDoesNotAddDwellAndPassThroughDoesNotQualify()throws Exception {
        JSONArray s=samples(-2,52);for(int i=0;i<s.length();i++)s.getJSONArray(i).put(4,20);
        assertTrue(VenueStopDetector.stops(s,new JSONArray().put(square())).isEmpty());
        s=samples(-2,52);for(int i=0;i<s.length();i++)s.getJSONArray(i).put(3,1000000);
        assertTrue(VenueStopDetector.stops(s,new JSONArray().put(square())).isEmpty());
    }
    @Test public void everyBundledSiteHasValidFootprint()throws Exception {
        JSONArray sites=catalogue();assertTrue(sites.length()>90);
        for(int i=0;i<sites.length();i++)assertTrue(sites.getJSONObject(i).optString("name"),ServiceSiteBoundary.valid(sites.getJSONObject(i).optJSONObject("site_boundary")));
    }
    static JSONArray catalogue()throws Exception {
        try(java.io.InputStream input=ServiceSiteBoundaryTest.class.getResourceAsStream("/uk-motorway-services-v1.json")) {
            if(input!=null)return new JSONObject(new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getJSONArray("services");
        }
        Path p=Path.of("android-prototype/app/src/main/assets/uk-motorway-services-v1.json");
        if(!Files.exists(p))p=Path.of("src/main/assets/uk-motorway-services-v1.json");
        return new JSONObject(new String(Files.readAllBytes(p),java.nio.charset.StandardCharsets.UTF_8)).getJSONArray("services");
    }
    @Test public void warwickMainBuildingQualifiesOnlyOnNorthboundSide()throws Exception {
        JSONArray sites=catalogue();JSONObject north=null,south=null;
        for(int i=0;i<sites.length();i++){
            JSONObject v=sites.getJSONObject(i);if(v.getString("id").equals("msa:m40 warwick:52.2174:-1.504"))north=v;
            if(v.getString("id").equals("msa:warwick m40:52.2188:-1.5033"))south=v;
        }
        assertNotNull(north);assertNotNull(south);
        // Synthetic stationary fixes at public OSM building 93201761 centroid. No user GPS data.
        java.io.InputStream input=getClass().getResourceAsStream("/warwick-services-synthetic-stop.json");assertNotNull(input);
        JSONArray s=new JSONArray(new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));
        List<VenueStopDetector.Stop> stops=VenueStopDetector.stops(s,sites);
        assertEquals(1,stops.size());assertEquals(north.getString("id"),stops.get(0).id);
        assertFalse(ServiceSiteBoundary.supports(south,52.2165186,-1.5053546,3));
        assertTrue(ServiceSiteBoundary.supports(north,52.2165186,-1.5053546,31.18));
    }
}
