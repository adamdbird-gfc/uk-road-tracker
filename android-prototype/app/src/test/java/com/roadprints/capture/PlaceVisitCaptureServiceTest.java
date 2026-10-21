package com.roadprints.capture;

import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import java.lang.reflect.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28) @LooperMode(LooperMode.Mode.PAUSED)
public class PlaceVisitCaptureServiceTest {
    private CaptureService service;
    private long time;
    @Before public void setup() throws Exception {
        service=Robolectric.buildService(CaptureService.class).get();
        time=System.currentTimeMillis();
        field("wallClock").set(service,new java.time.Clock() {
            public java.time.ZoneId getZone(){return java.time.ZoneOffset.UTC;}
            public java.time.Clock withZone(java.time.ZoneId zone){return this;}
            public Instant instant(){return Instant.ofEpochMilli(time);}
            public long millis(){return time;}
        });
        service.getSharedPreferences("roadprints_capture_state",0).edit().clear().putBoolean("armed",true).commit();
        PlaceAnchorStore.clear(service);JourneyStore.deleteAll(service);
        Shadows.shadowOf(RuntimeEnvironment.getApplication()).grantPermissions("android.permission.ACCESS_FINE_LOCATION");
        field("locationManager").set(service,service.getSystemService(Context.LOCATION_SERVICE));
        invoke("createNotificationChannel",new Class<?>[0]);
    }
    @After public void cleanup() throws Exception {
        service.getSharedPreferences("roadprints_capture_state",0).edit().clear().commit();
        ((LocationManager)field("locationManager").get(service)).removeUpdates((LocationListener)field("locationListener").get(service));
        ExecutorService io=(ExecutorService)field("captureIo").get(service);io.shutdown();io.awaitTermination(5,TimeUnit.SECONDS);
        PlaceAnchorStore.clear(service);JourneyStore.deleteAll(service);
    }
    private Field field(String name) throws Exception {Field f=CaptureService.class.getDeclaredField(name);f.setAccessible(true);return f;}
    private Object invoke(String name,Class<?>[] types,Object... args)throws Exception {
        Method m=CaptureService.class.getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(service,args);
    }
    private Location fix(double x,double y,float speed,int seconds) {
        time+=seconds*1000L;
        Location p=new Location("gps");p.setLatitude(y/111195);p.setLongitude(x/111195);
        p.setTime(time);p.setAccuracy(5);p.setSpeed(speed);return p;
    }
    private void establish(boolean campus)throws Exception {
        JSONObject stop=new JSONObject().put("id","stay").put("journey_id","past")
                .put("role","arrival").put("latitude",0).put("longitude",0).put("accuracy_m",5)
                .put("kind",campus?"school":"estimated").put("classification",campus?"supported":"estimated")
                .put("arrived_at",Instant.ofEpochMilli(time).toString());
        if(campus){JSONArray ring=new JSONArray();for(double[] xy:new double[][]{{-500,-500},{500,-500},{500,500},{-500,500},{-500,-500}})
            ring.put(new JSONObject().put("lon",xy[0]/111195).put("lat",xy[1]/111195));stop.put("site_geometry",ring);}
        PlaceAnchorStore.record(service,new JSONObject().put("stops",new JSONArray().put(stop)));
    }
    private void observe(Location p)throws Exception {invoke("observePlaceVisit",new Class<?>[]{Location.class},p);}
    @SuppressWarnings("unchecked") private List<Location> points(String name)throws Exception{return (List<Location>)field(name).get(service);}
    @Test public void overnightDriftAndFalseActivityHintsStayIdleThenMorningWalkKeepsItsStart()throws Exception {
        establish(false);
        for(int i=0;i<720;i++) {
            field("visitActivityHint").set(service,i%11==0?"running":"walking");
            Location p=fix(i%3*4,0,i%11==0?2.4f:0,60);p.setAccuracy(25);observe(p);
            assertFalse(CaptureService.isActive(service));
        }
        List<Location> morning=new ArrayList<>();
        for(int i=0;i<8&&!CaptureService.isActive(service);i++){Location p=fix(i*20,0,1.3f,60);morning.add(p);observe(p);}
        assertTrue(CaptureService.isActive(service));assertTrue(CaptureService.isArmed(service));
        assertEquals("walking",field("mode").get(service));
        assertEquals(Instant.ofEpochMilli(morning.get(0).getTime()).toString(),field("startedAt").get(service));
        assertEquals(morning.size(),points("points").size());
        for(int i=0;i<morning.size();i++)assertEquals(morning.get(i).getTime(),points("points").get(i).getTime());
    }
    @Test public void prolongedSchoolStayDoesNotBecomeAJourney()throws Exception {
        establish(true);
        for(int i=0;i<480;i++){Location p=fix(i%5*5,0,i%13==0?1.3f:0,60);p.setAccuracy(20);observe(p);}
        assertFalse(CaptureService.isActive(service));assertNull(field("candidateMode").get(service));
        assertTrue(points("visitPoints").size()<=21);
    }
    @Test public void idleDwellCanEstablishAStayWithoutCreatingASavedJourney()throws Exception {
        for(int i=0;i<=6;i++)invoke("observeIdlePlace",new Class<?>[]{Location.class},fix(i%2*2,0,0,60));
        JSONObject stop=PlaceAnchorStore.last(service);assertNotNull(stop);
        assertEquals("idle_gps_dwell",stop.getString("reason"));assertFalse(CaptureService.isActive(service));
        assertNull(JourneyStore.get(service,stop.getString("journey_id")));
    }
    @Test public void staleOrInaccurateSamplesCannotEstablishIdleDwell()throws Exception {
        for(int i=0;i<10;i++) {
            Location p=fix(0,0,0,60);p.setTime(p.getTime()-300000);
            assertEquals(false,invoke("observeIdlePlace",new Class<?>[]{Location.class},p));
            p.setTime(time);p.setAccuracy(150);
            assertEquals(false,invoke("observeIdlePlace",new Class<?>[]{Location.class},p));
        }
        assertNull(PlaceAnchorStore.last(service));assertFalse(CaptureService.isActive(service));
    }
    @Test public void sparseRunningLapsWithinCampusStartAtFirstMeasuredMovement()throws Exception {
        establish(true);List<Location> run=new ArrayList<>();
        for(int i=0;i<13&&!CaptureService.isActive(service);i++) {
            Location p=fix(100*Math.cos(i*Math.PI/6),100*Math.sin(i*Math.PI/6),2.7f,60);run.add(p);observe(p);
        }
        assertTrue(CaptureService.isActive(service));assertEquals("running",field("mode").get(service));
        assertEquals(run.get(0).getTime(),points("points").get(0).getTime());
        assertEquals(run.size(),points("points").size());
    }
    @Test public void cyclingDepartureRetainsModeAndInitialPoints()throws Exception {
        establish(false);field("visitActivityHint").set(service,"cycling");field("visitActivityHintAt").setLong(service,time);
        Location first=null;
        for(int i=0;i<8&&!CaptureService.isActive(service);i++){Location p=fix(i*120,0,4,30);if(first==null)first=p;observe(p);}
        assertTrue(CaptureService.isActive(service));assertEquals("cycling",field("mode").get(service));
        assertEquals(first.getTime(),points("points").get(0).getTime());
    }
    @Test public void restoredVisitBufferKeepsTheEarliestMeasuredDeparture()throws Exception {
        establish(false);JSONArray rows=new JSONArray();long first=0;
        for(int i=0;i<2;i++) {
            Location p=fix(i*20,0,1.3f,60);if(i==0)first=p.getTime();
            rows.put(new JSONArray().put(p.getLongitude()).put(0).put(5).put(p.getTime()).put(1.3));
        }
        service.getSharedPreferences("roadprints_capture_state",0).edit().putString("visit_buffer",
                new JSONObject().put("stop_id","stay").put("saved_at",time).put("samples",rows).toString()).commit();
        for(int i=2;i<8&&!CaptureService.isActive(service);i++)observe(fix(i*20,0,1.3f,60));
        assertTrue(CaptureService.isActive(service));assertEquals(first,points("points").get(0).getTime());
    }
    @Test public void futureOrExpiredVisitCheckpointsCannotBlockFreshSamples()throws Exception {
        establish(false);
        for(long offset:new long[]{3600000,-3600000}) {
            long now=time;
            JSONObject saved=new JSONObject().put("stop_id","stay").put("saved_at",now+offset)
                    .put("samples",new JSONArray().put(new JSONArray().put(0).put(0).put(5).put(now+offset).put(1)));
            service.getSharedPreferences("roadprints_capture_state",0).edit().putString("visit_buffer",saved.toString()).commit();
            invoke("restoreVisitBuffer",new Class<?>[]{String.class},"stay");assertEquals(0,points("visitPoints").size());
        }
    }
    @Test public void vehicleDepartureWithinLargeVenueRemainsUnknownUntilTransportIsConfirmed()throws Exception {
        establish(true);field("visitActivityHint").set(service,"unknown");
        Location first=null;
        for(int i=0;i<3;i++){Location p=fix(i*200,0,20,30);if(first==null)first=p;observe(p);}
        assertTrue(CaptureService.isActive(service));assertEquals("unknown",field("mode").get(service));
        assertEquals(first.getTime(),points("points").get(0).getTime());
    }
    @Test public void measuredArrivalKeepsTheDwellOnsetOutsideTheConfirmationTail()throws Exception {
        field("automaticCapture").setBoolean(service,true);field("mode").set(service,"walking");
        List<Location> original=points("points");
        original.add(fix(-100,0,1.3f,60));long arrival=0;
        for(int i=0;i<=6;i++) {
            Location p=fix(i%2*2,0,0,60);if(i==0)arrival=p.getTime();original.add(p);
            invoke("observeWalkingStillness",new Class<?>[]{Location.class},p);
        }
        invoke("resolveGpsArrivalBoundary",new Class<?>[]{long.class},time);
        assertEquals(1,field("stationEndIndex").getInt(service));
        assertEquals(arrival,field("stationarySince").getLong(service));
        assertEquals(8,original.size()); // Confirmation observations remain raw evidence.
    }
    @Test public void latestGpsModeBoundariesRetainEveryEdgeInBothDirections()throws Exception {
        for(boolean vehicle:new boolean[]{true,false}) {
            List<Location> original=points("points");original.clear();List<FootTraceValidator.Sample> samples=new ArrayList<>();
            long start=time-90000;
            for(int i=0;i<7;i++) {
                boolean next=i>=3;double speed=next==(vehicle)?12:1.3;
                Location p=new Location("gps");p.setLatitude(0);p.setLongitude((i<3?i*20:60+(i-3)*(vehicle?120:20))/111195d);
                p.setAccuracy(5);p.setSpeed((float)speed);p.setTime(start+i*10000);original.add(p);
                samples.add(new FootTraceValidator.Sample(p.getLongitude(),0,5,p.getTime(),speed,i));
            }
            String next=vehicle?"unknown":"walking";
            int boundary=CaptureMovementEvidence.modeChangeStart(samples,next,start,time);assertEquals(3,boundary);
            invoke("prepareStationSplit",new Class<?>[]{int.class,int.class,String.class},boundary,boundary,next);
            List<Location> continuation=points("nextCapturePoints");assertEquals(4,continuation.size());
            for(int i=0;i<continuation.size();i++) {
                assertEquals(original.get(boundary+i).getTime(),continuation.get(i).getTime());
                assertEquals(original.get(boundary+i).getLongitude(),continuation.get(i).getLongitude(),0d);
            }
            assertEquals(original.size()-1,boundary+continuation.size()-1);
        }
    }
}
