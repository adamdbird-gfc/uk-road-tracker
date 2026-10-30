package com.roadprints.capture;
import android.location.Location;import android.location.LocationListener;
import java.lang.reflect.*;import java.time.*;import java.util.*;
import org.junit.*;import org.junit.runner.RunWith;import org.robolectric.*;import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class PhantomTrainServiceTest {
 private Field field(String n)throws Exception{Field f=CaptureService.class.getDeclaredField(n);f.setAccessible(true);return f;}
 @Test public void automaticTrainLocationCallbackCollectsDwellWithoutStillActivity()throws Exception{
  CaptureService service=Robolectric.buildService(CaptureService.class).get();
  service.getSharedPreferences("roadprints_capture_state",0).edit().clear().putBoolean("armed",true).putBoolean("active",true).commit();
  field("mode").set(service,"train");field("automaticCapture").setBoolean(service,true);
  // Keep the station model null: away from stations, a train label must not disable the detector.
  long start=System.currentTimeMillis();LocationListener listener=(LocationListener)field("locationListener").get(service);
  try{for(int i=0;i<=10;i++){
   long time=start+i*30000L;field("wallClock").set(service,Clock.fixed(Instant.ofEpochMilli(time),ZoneOffset.UTC));
   Location p=new Location("gps");p.setLatitude(0);p.setLongitude(0);p.setAccuracy(5);p.setSpeed(0);p.setTime(time);
   listener.onLocationChanged(p);
  }
  assertTrue(field("gpsStillness").getBoolean(service));assertEquals(start,field("stationarySince").getLong(service));
  @SuppressWarnings("unchecked")List<Location> points=(List<Location>)field("points").get(service);assertEquals(11,points.size());
  }finally{service.getSharedPreferences("roadprints_capture_state",0).edit().clear().commit();}
 }
}
