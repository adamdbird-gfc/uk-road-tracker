package com.roadprints.capture;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import java.util.*;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class PortableJourneyIntelligenceTest {
    private static final String[] KINDS={"theme_park","shopping_centre","airport","hospital","university","event_venue"};
    private static final double[][] CENTRES={{.37,51.44},{-3.2,55.95},{-5.9,54.6}};
    private JSONArray ring(double lon,double lat,double left,double right,double bottom,double top)throws Exception {
        JSONArray r=new JSONArray();for(double[] xy:new double[][]{{left,bottom},{right,bottom},{right,top},{left,top},{left,bottom}})
            r.put(new JSONObject().put("lon",lon+xy[0]/(111195*Math.cos(Math.toRadians(lat)))).put("lat",lat+xy[1]/111195));return r;
    }
    private FootTraceValidator.Sample p(double lon,double lat,double x,double y,long time,double speed,int i) {
        return new FootTraceValidator.Sample(lon+x/(111195*Math.cos(Math.toRadians(lat))),lat+y/111195,5,time,speed,i);
    }
    private JSONObject stop(String kind,double lon,double lat)throws Exception {
        return new JSONObject().put("id","portable").put("role","arrival").put("journey_id","portable")
            .put("place_id","osm:relation:42").put("kind",kind).put("name","A label unrelated to its location")
            .put("latitude",lat).put("longitude",lon).put("accuracy_m",5).put("classification","supported")
            .put("arrived_at","1970-01-01T00:00:01Z").put("site_geometry",ring(lon,lat,-300,300,-300,300));
    }
    @Test public void destinationStaysAndBoundaryDriftArePortableAcrossTypesAndRegions()throws Exception {
        for(double[] centre:CENTRES)for(String kind:KINDS) {
            JSONObject place=stop(kind,centre[0],centre[1]);List<FootTraceValidator.Sample> observations=new ArrayList<>();
            for(int i=0;i<240;i++) {
                observations.add(p(centre[0],centre[1],i%10==0?310:80*Math.sin(i*.2),80*Math.cos(i*.2),1000+i*30000L,i%4==0?0:1.3,i));
                if(observations.size()>40)observations.remove(0);
                List<FootTraceValidator.Sample> indexed=new ArrayList<>();for(int n=0;n<observations.size();n++) {
                    FootTraceValidator.Sample v=observations.get(n);indexed.add(new FootTraceValidator.Sample(v.lon,v.lat,v.accuracy,v.time,v.speed,n));}
                assertNull(kind,PlaceVisitEvidence.inspect(place,indexed,"walking"));
            }
            assertTrue(DestinationPolicy.bounded(new JSONObject(place.toString())));
        }
    }
    @Test public void confirmedFootAndVehicleDepartureRecoverTheBoundaryFixWithoutInventingOldMovement()throws Exception {
        for(double[] centre:CENTRES)for(String kind:KINDS)for(boolean vehicle:new boolean[]{false,true}) {
            JSONObject place=stop(kind,centre[0],centre[1]);List<FootTraceValidator.Sample> samples=new ArrayList<>();
            samples.add(p(centre[0],centre[1],290,0,1000,vehicle?8:1.3,0));
            for(int i=1;i<6;i++)samples.add(p(centre[0],centre[1],320+(i-1)*(vehicle?160:25),0,1000+i*20000L,vehicle?8:1.3,i));
            PlaceVisitEvidence.Decision d=PlaceVisitEvidence.inspect(place,samples,"walking");
            assertNotNull(kind,d);assertEquals("departure_from_visit",d.reason);assertEquals(vehicle?"unknown":"walking",d.mode);
            assertEquals(0,d.startIndex); // Last supported inside boundary observation, not the old arrival.
        }
    }
    @Test public void connectedComponentsShareIdentityButNeverInventCoverageAcrossTheirGap()throws Exception {
        double lon=-3.2,lat=55.95;JSONArray a=ring(lon,lat,-300,-20,-100,100),b=ring(lon,lat,20,300,-100,100);
        JSONObject tags=new JSONObject().put("aeroway","terminal").put("name","Public terminal");
        JSONArray features=new JSONArray().put(new JSONObject().put("type","relation").put("id",42).put("tags",tags).put("geometry",a))
            .put(new JSONObject().put("type","relation").put("id",42).put("tags",tags).put("geometry",b));
        ContextMap.Evidence evidence=ContextMap.at(features,lat,lon-.002,5);
        assertTrue(evidence.destination);assertEquals("terminal",evidence.kind);assertEquals(2,evidence.areas.length());
        JSONObject place=PlaceBoundaryEvidence.arrival("components",p(lon,lat,-100,0,1000,0,0),31000,evidence,null,"arrival_destination_entry");
        assertFalse(PlaceVisitEvidence.outside(place,p(lon,lat,100,0,41000,1.3,1)));
        assertTrue(PlaceVisitEvidence.outside(place,p(lon,lat,0,0,51000,1.3,2)));
        PlaceAnchorStore.record(RuntimeEnvironment.getApplication(),new JSONObject().put("stops",new JSONArray().put(place)));
        JSONObject restored=PlaceAnchorStore.last(RuntimeEnvironment.getApplication());assertEquals(2,restored.getJSONArray("site_areas").length());
        List<FootTraceValidator.Sample> crossing=Arrays.asList(p(lon,lat,-40,0,1000,1.3,0),p(lon,lat,0,0,11000,1.3,1),p(lon,lat,40,0,21000,1.3,2));
        assertNull(PlaceVisitEvidence.inspect(restored,crossing));PlaceAnchorStore.clear(RuntimeEnvironment.getApplication());
    }
    @Test public void sustainedPurposefulExerciseRemainsRecordableInsideAndOutsideDestinations()throws Exception {
        for(String kind:KINDS) {
            JSONObject place=stop(kind,.37,51.44);List<FootTraceValidator.Sample> run=new ArrayList<>();
            for(int i=0;i<25;i++)run.add(p(.37,51.44,100*Math.cos(i*.25),100*Math.sin(i*.25),1000+i*10000L,2.7,i));
            PlaceVisitEvidence.Decision d=PlaceVisitEvidence.inspect(place,run,"running");assertNotNull(kind,d);assertEquals("running",d.mode);
            assertTrue(CaptureMovementEvidence.hasJourneyMovement(run,"running"));
        }
        JSONObject large=stop("hospital",.37,51.44).put("site_geometry",ring(.37,51.44,-1500,1500,-100,100));
        List<FootTraceValidator.Sample> walk=new ArrayList<>();for(int i=0;i<=60;i++)walk.add(p(.37,51.44,i*20-600,0,1000+i*10000L,1.5,i));
        assertEquals("walking",PlaceVisitEvidence.inspect(large,walk).mode);
    }
    @Test public void missingAndIncorrectZeroSpeedsCanUsePreciseContinuousWalkingEvidence() {
        for(double speed:new double[]{-1,0}) {
            List<FootTraceValidator.Sample> samples=new ArrayList<>();for(int i=0;i<6;i++)samples.add(p(.37,51.44,i*30,0,1000+i*20000L,speed,i));
            assertTrue(WalkingStartEvidence.confirmed(samples,101000));
            assertEquals(0,CaptureMovementEvidence.modeChangeStart(samples,"walking",0,101000));
        }
        List<FootTraceValidator.Sample> repeated=new ArrayList<>();for(int i=0;i<12;i++)repeated.add(p(.37,51.44,0,0,1000+i*10000L,1.3,i));
        assertFalse(WalkingStartEvidence.confirmed(repeated,111000));assertFalse(CaptureMovementEvidence.hasJourneyMovement(repeated,"walking"));
    }
    @Test public void labelsNeverBecomeAuthoritativeGeographicIdentities()throws Exception {
        JSONObject estimated=stop("estimated",.37,51.44);estimated.remove("site_geometry");estimated.put("place_id","local-stop");
        JSONObject reviewed=PlaceBoundaryEvidence.review(estimated,"Heathrow Airport",true,50000);
        assertEquals("user_confirmed",reviewed.getJSONObject("identity").getString("type"));
        assertFalse(reviewed.getJSONObject("identity").has("geographic_id"));assertEquals(.37,reviewed.getDouble("longitude"),0);
        assertEquals("Heathrow Airport",reviewed.getString("display_label"));
        JSONObject unsupported=new JSONObject().put("id","label").put("display_label","Disneyland");PlaceIdentity.apply(unsupported);
        assertEquals("display_label_only",unsupported.getJSONObject("identity").getString("type"));
        JSONObject mapped=stop("hospital",.37,51.44);PlaceIdentity.apply(mapped);
        assertEquals("recognised",mapped.getJSONObject("identity").getString("type"));
        assertEquals("osm:relation:42",mapped.getJSONObject("identity").getString("geographic_id"));
    }
    @Test public void uncertaintyWithoutBoundariesNeverExpandsToCoverAGenuineJourney()throws Exception {
        JSONObject place=stop("airport",.37,51.44);place.remove("site_geometry");
        assertFalse(DestinationPolicy.bounded(place));List<FootTraceValidator.Sample> samples=new ArrayList<>();
        for(int i=0;i<6;i++)samples.add(p(.37,51.44,100+i*25,0,1000+i*20000L,1.3,i));
        assertNotNull(PlaceVisitEvidence.inspect(place,samples));assertFalse(place.has("site_geometry"));
    }
    @Test public void originalFixtureMovementIsInvariantWhenTranslatedToUnrelatedRegions()throws Exception {
        JSONArray fixtures;
        try(java.io.InputStream in=new java.util.zip.GZIPInputStream(getClass().getResourceAsStream("/disneyland-traces.json.gz"))) {
            java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)out.write(b,0,n);
            fixtures=new JSONArray(new String(out.toByteArray(),StandardCharsets.UTF_8));
        }
        assertEquals(9,fixtures.length());
        for(int f=0;f<fixtures.length();f++) {
            JSONArray raw=fixtures.getJSONObject(f).getJSONArray("raw");double firstLon=raw.getJSONArray(0).getDouble(0),firstLat=raw.getJSONArray(0).getDouble(1);
            for(double[] centre:CENTRES) {
                List<FootTraceValidator.Sample> moved=new ArrayList<>();
                for(int i=0;i<raw.length();i++) {
                    JSONArray row=raw.getJSONArray(i);double x=(row.getDouble(0)-firstLon)*111195*Math.cos(Math.toRadians(firstLat));double y=(row.getDouble(1)-firstLat)*111195;
                    FootTraceValidator.Sample p=p(centre[0],centre[1],x,y,row.getLong(3),row.getDouble(4),i);
                    moved.add(new FootTraceValidator.Sample(p.lon,p.lat,row.getDouble(2),p.time,p.speed,i));
                }
                WalkingCaptureEvidence.Result prepared=WalkingCaptureEvidence.prepare(moved);
                assertTrue(prepared.route.resolved);assertTrue(prepared.route.distance<fixtures.getJSONObject(f).getDouble("original_m")*1.2);
                for(FootTraceValidator.Sample p:prepared.route.samples)assertTrue(p.accuracy<=50);
            }
        }
    }
    @Test public void serverOverloadAndRateLimitingAreRecoverableWithoutImmediateRetries() {
        for(MatchFailure error:new MatchFailure[]{new MatchFailure(504,"timeout"),new MatchFailure(503,"The pedestrian router is rate-limiting requests"),new MatchFailure(503,"Walking matcher is busy"),new MatchFailure(503,"Walking search work limit reached")}) {
            assertTrue(MatchFailure.retryable(error));assertFalse(MatchFailure.retryNow(error,0));
        }
        assertTrue(MatchFailure.retryNow(new java.net.ConnectException(),0));assertFalse(MatchFailure.retryNow(new java.net.ConnectException(),1));
    }
    @Test public void explicitManualAndPurposefulIntentRemainDistinctFromInternalWandering()throws Exception {
        JSONObject manual=new JSONObject().put("capture_intent","manual");assertTrue(DestinationJourneyEvidence.purposeful(manual));
        JSONObject departure=stop("airport",.37,51.44).put("departure_reason","purposeful_activity_within_place");
        assertTrue(DestinationJourneyEvidence.purposeful(new JSONObject().put("stops",new JSONArray().put(departure))));
        assertFalse(DestinationJourneyEvidence.purposeful(new JSONObject().put("stops",new JSONArray().put(stop("airport",.37,51.44)))));
        MatchFailure pause=new MatchFailure(503,"temporary failure",120);
        assertTrue(MatchFailure.retryable(pause));assertFalse(MatchFailure.retryNow(pause,0));
    }
    @Test public void sparseCoherentDepartureKeepsItsSupportedOriginAcrossDestinationTypes()throws Exception {
        for(String kind:KINDS) {
            JSONObject place=stop(kind,.37,51.44).put("site_geometry",ring(.37,51.44,-60,60,-60,60));
            List<FootTraceValidator.Sample> samples=new ArrayList<>();for(int n=0;n<7;n++)samples.add(p(.37,51.44,n*20,0,1000+n*60000L,1.3,n));
            assertEquals(kind,0,PlaceVisitEvidence.inspect(place,samples).startIndex);
        }
    }
    @Test public void monotonicReceiptAgeRejectsCachedFixesEvenWhenWallTimestampLooksFresh() {
        assertFalse(CaptureQualityValidator.fresh(200000,199000,120000));
        assertFalse(CaptureQualityValidator.fresh(200000,100000,0));
        assertTrue(CaptureQualityValidator.fresh(200000,199000,1000));
        assertTrue(CaptureQualityValidator.fresh(200000,199000,-1)); // Legacy provider lacks monotonic evidence.
    }
}
