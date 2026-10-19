package com.roadprints.capture;

import android.location.Location;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
public class StationCaptureHandoffTest {
    private Object field(CaptureService service,String name) throws Exception {
        Field f=CaptureService.class.getDeclaredField(name); f.setAccessible(true); return f.get(service);
    }
    private Location point(int i) {
        Location p=new Location("gps"); p.setTime(100000L+i*10000L);
        p.setLatitude(51.5+i*.0001); p.setLongitude(-.1); p.setAccuracy(10); p.setSpeed(i<4?1:12); return p;
    }
    @Test public void boardingHandoffKeepsOriginalCaptureAndSeedsDeparturePoints() throws Exception {
        CaptureService service=Robolectric.buildService(CaptureService.class).get();
        @SuppressWarnings("unchecked") List<Location> points=(List<Location>)field(service,"points");
        for(int i=0;i<8;i++)points.add(point(i));
        Method split=CaptureService.class.getDeclaredMethod("prepareStationSplit",int.class,int.class,String.class);
        split.setAccessible(true); split.invoke(service,2,4,"train");
        @SuppressWarnings("unchecked") List<Location> next=(List<Location>)field(service,"nextCapturePoints");
        assertEquals(8,points.size()); assertEquals(4,next.size());
        assertEquals(2,((Integer)field(service,"stationEndIndex")).intValue());
        assertEquals(Instant.ofEpochMilli(point(4).getTime()).toString(),field(service,"nextCaptureStartedAt"));
        assertNotNull(field(service,"nextCaptureJourneyId"));
        assertNotSame(points.get(4),next.get(0));
    }
    @Test public void recoverySnapshotPreservesFixTimeAccuracyAndDepartureSpeed() throws Exception {
        CaptureService service=Robolectric.buildService(CaptureService.class).get();
        Method snapshot=CaptureService.class.getDeclaredMethod("checkpointSnapshot",String.class,String.class,
                String.class,double.class,long.class,List.class); snapshot.setAccessible(true);
        JSONObject saved=(JSONObject)snapshot.invoke(service,"train-leg","2026-10-05T17:20:00Z",
                "train",100d,0L,java.util.Arrays.asList(point(4),point(5)));
        assertEquals("train",saved.getString("mode"));
        assertEquals(point(4).getTime(),saved.getJSONArray("points").getJSONArray(0).getLong(3));
        assertEquals(12d,saved.getJSONArray("points").getJSONArray(0).getDouble(4),0d);
    }

    @Test public void measuredModeBoundaryKeepsEveryEdgeWithOneSharedEndpoint() throws Exception {
        CaptureService service=Robolectric.buildService(CaptureService.class).get();
        @SuppressWarnings("unchecked") List<Location> points=(List<Location>)field(service,"points");
        java.util.List<FootTraceValidator.Sample> samples=new java.util.ArrayList<>();
        for(int i=0;i<8;i++) {
            Location p=point(i);p.setLatitude(0);
            p.setLongitude((i<4?i*15:60+(i-4)*100)/111195d);points.add(p);
            samples.add(new FootTraceValidator.Sample(p.getLongitude(),p.getLatitude(),p.getAccuracy(),
                    p.getTime(),p.getSpeed(),i));
        }
        int boundary=CaptureMovementEvidence.modeChangeStart(samples,"unknown",100000,170000);
        // Vehicle speed starts at index 4, after an early activity callback.
        assertEquals(4,boundary);
        Method split=CaptureService.class.getDeclaredMethod("prepareStationSplit",int.class,int.class,String.class);
        split.setAccessible(true);split.invoke(service,boundary,boundary,"unknown");
        @SuppressWarnings("unchecked") List<Location> next=(List<Location>)field(service,"nextCapturePoints");
        assertEquals(4,((Integer)field(service,"stationEndIndex")).intValue());
        assertEquals(4,next.size());
        assertEquals(points.get(boundary).getTime(),next.get(0).getTime());
        for(int i=0;i<next.size();i++) {
            assertEquals(points.get(boundary+i).getLongitude(),next.get(i).getLongitude(),0d);
            assertEquals(points.get(boundary+i).getLatitude(),next.get(i).getLatitude(),0d);
            assertEquals(points.get(boundary+i).getTime(),next.get(i).getTime());
            assertNotSame(points.get(boundary+i),next.get(i));
        }
        assertEquals(points.size()-1,boundary+next.size()-1);
        assertEquals(8,points.size());
    }
}
