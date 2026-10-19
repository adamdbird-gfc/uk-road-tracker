package com.roadprints.capture;

import java.time.Instant;
import java.util.*;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class WalkingArrivalTimingTest {
    private FootTraceValidator.Sample point(double x,long time,double speed,int index){
        return new FootTraceValidator.Sample(x/111195,0,20,time,speed,index);
    }
    @Test public void ninetyMinuteIndoorStayHasAnEarlyArrivalAndKeepsOriginalTime()throws Exception{
        List<FootTraceValidator.Sample> samples=new ArrayList<>();
        for(int i=0;i<9;i++)samples.add(point(i*20,100000+i*20000,1,i));
        for(int i=0;i<540;i++)samples.add(point(160+i%3*3,280000+i*10000L,i%7==0&&i<539?2:.2,samples.size()));
        WalkingArrivalTiming.Candidate candidate=WalkingArrivalTiming.candidate(samples,WalkingCaptureEvidence.prepare(samples));
        assertNotNull(candidate);assertTrue(candidate.arrival<700000);
        String original=Instant.ofEpochMilli(samples.get(samples.size()-1).time).toString();
        JSONObject stop=new JSONObject().put("role","arrival").put("source","gps_dwell").put("classification","estimated")
                .put("arrived_at",original).put("boundary_at",original).put("confirmed_at",original);
        JSONObject journey=new JSONObject().put("mode","walking").put("source",new JSONObject().put("type","android_activity_capture"))
                .put("started_at",Instant.ofEpochMilli(100000).toString()).put("ended_at",original).put("stops",new JSONArray().put(stop));
        assertTrue(WalkingArrivalTiming.apply(journey,candidate));
        assertEquals(Instant.ofEpochMilli(candidate.arrival).toString(),journey.getString("ended_at"));
        assertEquals(original,journey.getString("original_ended_at"));
        assertEquals(journey.getString("ended_at"),stop.getString("arrived_at"));
        assertEquals(original,stop.getString("original_arrived_at"));
        assertFalse(WalkingArrivalTiming.apply(journey,candidate));
        assertEquals(original,journey.getString("original_ended_at"));
        assertEquals(549,samples.size());
    }
    @Test public void movingWalkAndUncorroboratedOrImportedTimesAreNotShortened()throws Exception{
        List<FootTraceValidator.Sample> moving=new ArrayList<>();
        for(int i=0;i<90;i++)moving.add(point(i*15,100000+i*10000,1.5,i));
        assertNull(WalkingArrivalTiming.candidate(moving,WalkingCaptureEvidence.prepare(moving)));
        for(String source:new String[]{"timeline_import","android_activity_capture"}){
            JSONObject j=new JSONObject().put("mode","walking").put("source",new JSONObject().put("type",source))
                    .put("started_at",Instant.ofEpochMilli(100000).toString()).put("ended_at",Instant.ofEpochMilli(800000).toString());
            String original=j.toString();
            assertFalse(WalkingArrivalTiming.apply(j,new WalkingArrivalTiming.Candidate(200000,400000)));
            assertEquals(original,j.toString());
            if(source.equals("timeline_import"))assertFalse(WalkingArrivalTiming.apply(j,new WalkingArrivalTiming.Candidate(200000,700000)));
        }
    }
    @Test public void fullRawConfirmationCanRecoverAnArrivalLostFromAnOlderFilteredRoute()throws Exception{
        List<FootTraceValidator.Sample> samples=new ArrayList<>();JSONArray raw=new JSONArray();
        for(int i=0;i<120;i++){
            double x=i<9?i*20:160+i%3*3;long t=i<9?100000+i*20000:280000+(i-9)*10000L;
            FootTraceValidator.Sample p=point(x,t,i<9?1:i==100?2:.2,i);samples.add(p);
            raw.put(new JSONArray().put(p.lon).put(p.lat).put(p.accuracy).put(p.time).put(p.speed));
        }
        List<FootTraceValidator.Sample> old=new ArrayList<>(samples.subList(0,101));
        assertNull(WalkingArrivalTiming.candidate(old,WalkingCaptureEvidence.prepare(old)));
        assertNotNull(WalkingArrivalTiming.fromRaw(raw,old));
        assertNull(WalkingArrivalTiming.fromRaw(raw,old.subList(0,5))); // A trimmed walk cannot acquire a later endpoint.
    }

}
