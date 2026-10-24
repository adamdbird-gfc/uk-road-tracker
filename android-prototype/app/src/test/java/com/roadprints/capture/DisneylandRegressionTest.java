package com.roadprints.capture;
import android.content.Context;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import java.util.*;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class DisneylandRegressionTest {
    private Context app=RuntimeEnvironment.getApplication();
    private FootTraceValidator.Sample p(double lon,double lat,long time,double accuracy,int index) {
        return new FootTraceValidator.Sample(lon,lat,accuracy,time,1.3,index);
    }
    private JSONArray fixtures() throws Exception {
        try(java.io.InputStream in=new java.util.zip.GZIPInputStream(getClass().getResourceAsStream("/disneyland-traces.json.gz"))) {
            java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();byte[] b=new byte[4096];int n;
            while((n=in.read(b))!=-1)out.write(b,0,n);
            return new JSONArray(new String(out.toByteArray(),StandardCharsets.UTF_8));
        }
    }
    private List<FootTraceValidator.Sample> samples(JSONArray rows) throws Exception {
        List<FootTraceValidator.Sample> samples=new ArrayList<>();
        for(int i=0;i<rows.length();i++){JSONArray p=rows.getJSONArray(i);samples.add(new FootTraceValidator.Sample(p.getDouble(0),p.getDouble(1),p.getDouble(2),p.getLong(3),p.getDouble(4),i));}
        return samples;
    }
    private JSONObject stop() throws Exception {
        ContextMap.Evidence e=new DestinationCatalog(app).evidence(48.872,2.774,5);
        assertTrue(DestinationCatalog.suppresses(e));assertEquals("osm:way:205732748",e.featureId);
        return PlaceBoundaryEvidence.arrival("resort",p(2.774,48.872,1000,5,0),31000,e,null,"arrival_theme_park");
    }
    @Test public void pinnedBoundaryRecognisesEightInternalRecordingsAndPreservesOutsideLeg() throws Exception {
        JSONArray all=fixtures();assertEquals(9,all.length());int internal=0;
        for(int i=0;i<all.length();i++) {
            JSONObject f=all.getJSONObject(i);boolean value=ResortJourneyPolicy.internal(new DestinationCatalog(app),f.getJSONArray("route"));
            if(value)internal++;
            if(f.getString("fixture").equals("015845"))assertFalse(value);
        }
        assertEquals(8,internal);
    }
    @Test public void originalNoiseIsRemovedWithoutMutatingRawFixtures() throws Exception {
        JSONArray all=fixtures();
        for(int i=0;i<all.length();i++) {
            JSONObject f=all.getJSONObject(i);JSONArray raw=f.getJSONArray("raw");String original=raw.toString();
            WalkingCaptureEvidence.Result clean=WalkingCaptureEvidence.prepare(samples(raw));
            assertEquals(original,raw.toString());assertTrue(f.getString("fixture"),clean.route.resolved);
            long time=0;
            for(FootTraceValidator.Sample p:clean.route.samples){assertTrue(p.accuracy<=50);assertTrue(p.time>time);time=p.time;}
            assertTrue(clean.route.distance<f.getDouble("original_m")*1.2);
            if(f.getString("fixture").equals("015845"))assertTrue(clean.route.distance<4000);
            if(f.getString("fixture").equals("015837"))assertTrue(clean.route.distance<1600);
        }
    }
    @Test public void severalHoursOfAttractionMovementAndRideHintsRemainInside() throws Exception {
        JSONObject stop=stop();List<FootTraceValidator.Sample> loop=new ArrayList<>();
        for(int i=0;i<360;i++) {
            loop.add(p(2.774+Math.sin(i*.1)*.001,48.872+Math.cos(i*.1)*.001,1000+i*30000L,5,i));
            if(loop.size()>40)loop.remove(0);
            for(String mode:new String[]{null,"walking","running","cycling","unknown"})
                assertNull(PlaceVisitEvidence.inspect(stop,loop,mode));
        }
        assertNull(PlaceVisitEvidence.inspect(new JSONObject(stop.toString()),loop,"running"));
    }
    @Test public void walkingEntryDoesNotNeedAStationaryDwell() {
        ContextJourneyModel model=new ContextJourneyModel();DestinationCatalog catalog=new DestinationCatalog(app);
        assertNull(model.sample("walking",1,1000,48.872,2.774,5,1.3f,catalog.evidence(48.872,2.774,5),false));
        assertNull(model.sample("walking",2,16000,48.8721,2.7741,5,1.3f,catalog.evidence(48.8721,2.7741,5),false));
        ContextJourneyModel.Decision d=model.sample("walking",3,31000,48.8722,2.7742,5,1.3f,catalog.evidence(48.8722,2.7742,5),false);
        assertNotNull(d);assertEquals(1,d.boundary);assertEquals("arrival_theme_park",d.reason);assertNull(d.nextMode);
    }
    @Test public void accurateConfirmedDepartureExcludesTheInternalWalk() throws Exception {
        JSONObject stop=stop();List<FootTraceValidator.Sample> points=new ArrayList<>();
        points.add(p(2.774,48.872,1000,5,0));
        for(int i=1;i<=5;i++)points.add(p(2.762-(i-1)*.00025,48.875,1000+i*20000L,5,i));
        // Avoid counting the unsupported jump as a departure; use fresh outside evidence only.
        points.remove(0);
        PlaceVisitEvidence.Decision d=PlaceVisitEvidence.inspect(stop,points,"walking");
        assertNotNull(d);assertEquals("departure_from_visit",d.reason);assertEquals(1,d.startIndex);
    }
    @Test public void staleAndPoorFixesDoNotPreventRecoveringSlowWalking() {
        List<FootTraceValidator.Sample> points=new ArrayList<>();
        for(int i=0;i<10;i++)points.add(p(.1+i*.00005,51,1000+i*10000L,5,i));
        points.add(5,p(.3,51,1000,100,99));points.add(6,p(.4,51,500,5,100));
        WalkingCaptureEvidence.Result clean=WalkingCaptureEvidence.prepare(points);
        assertTrue(clean.route.resolved);assertEquals(10,clean.route.samples.size());assertEquals(2,clean.rejectedFixes);
    }
    @Test public void movingGapHasNoDistanceAndRequiresASessionBoundary() {
        FootTraceValidator.Sample a=p(.1,51,1000,5,0),b=p(.12,51,301000,5,1);
        assertTrue(CaptureMovementEvidence.footSamplingGap(a,b));
        assertEquals(0,new FootTraceValidator.Result(Arrays.asList(a,b),2,true).distance,0);
    }
    @Test public void simplificationPreservesBendsLoopsAndBoundedStraightChords() throws Exception {
        JSONArray points=new JSONArray();
        for(int i=0;i<60;i++)points.put(new JSONObject().put("lng",.1+Math.sin(i*.1)*.001).put("lat",51+Math.cos(i*.1)*.001));
        JSONArray reduced=WalkingTraceSimplifier.simplify(points);assertTrue(reduced.length()<points.length());assertTrue(reduced.length()>10);
        assertEquals(points.get(0),reduced.get(0));assertEquals(points.get(59),reduced.get(reduced.length()-1));
        for(int i=1;i<points.length()-1;i++) {
            JSONObject p=points.getJSONObject(i);double best=Double.POSITIVE_INFINITY;
            for(int k=1;k<reduced.length();k++) {
                JSONObject a=reduced.getJSONObject(k-1),b=reduced.getJSONObject(k);
                best=Math.min(best,FootTraceValidator.segmentGap(p(p.getDouble("lng"),p.getDouble("lat"),0,0,0),p(a.getDouble("lng"),a.getDouble("lat"),0,0,0),p(b.getDouble("lng"),b.getDouble("lat"),0,0,0)));
            }
            assertTrue(best<=3.01);
        }
    }
    @Test public void internalEvidenceWritesAreIdempotentAndNeverCreateAJourney() throws Exception {
        JSONObject raw=new JSONObject().put("journey_id","internal-regression").put("raw_capture_samples",fixtures().getJSONObject(1).getJSONArray("raw"));
        InternalMovementStore.save(app,raw);InternalMovementStore.save(app,raw);
        java.io.File file=new java.io.File(app.getFilesDir(),"destination-internal-evidence/internal-regression.json");
        assertEquals(raw.toString(),new String(java.nio.file.Files.readAllBytes(file.toPath()),StandardCharsets.UTF_8));
        assertNull(JourneyStore.get(app,"internal-regression"));assertTrue(file.delete());
    }
    @Test public void inheritedStopIsExplicitlyNotContinuousGpsCoverage() throws Exception {
        JSONObject departure=PlaceBoundaryEvidence.departure(stop(),p(2.762,48.875,36000000,5,0));
        assertFalse(departure.getBoolean("gps_continuity_from_arrival"));assertEquals("1970-01-01T10:00:00Z",departure.getString("departed_at"));
    }
}
