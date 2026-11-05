package com.roadprints.capture;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.Iterator;

/** Pure JVM streaming budget; deliberately not a device/ART heap claim. */
public class MilepostEvidenceVolumeTest {
    @Test public void millionEdgesAreConsumedOnceWithCancellationAndNoRetainedArchive(){
        final int[] visited={0};
        Iterable<MilepostProximity.Segment> stream=()->new Iterator<MilepostProximity.Segment>(){
            public boolean hasNext(){return visited[0]<1000000;}
            public MilepostProximity.Segment next(){int i=visited[0]++;
                return MilepostProximityTest.segment(MilepostProximityTest.ORIGIN,MilepostProximityTest.offset(MilepostProximityTest.ORIGIN,90,50),i);}
        };
        MilepostProximity.Result r=MilepostProximity.assess(MilepostProximityTest.marker(0),MilepostProximityTest.proof("walking",stream),
                MilepostProximity.Policy.V1,()->visited[0]>=1000);
        assertEquals(MilepostProximity.Outcome.CANCELLED,r.outcome);assertEquals(1000,visited[0]);
        assertTrue(Double.isNaN(r.closestMetres));
    }
    public static void main(String[] args)throws Exception{
        int journeys=1605,edges=500;
        long start=System.nanoTime();int[] visited={0};
        MilepostProximity.Point far=MilepostProximityTest.offset(MilepostProximityTest.ORIGIN,0,1000);
        MilepostProximity.Point farEnd=MilepostProximityTest.offset(far,90,30);
        for(int j=0;j<journeys;j++){
            Iterable<MilepostProximity.Segment> stream=()->new Iterator<MilepostProximity.Segment>(){
                int i;
                public boolean hasNext(){return i<edges;}
                public MilepostProximity.Segment next(){visited[0]++;return i++==0?MilepostProximityTest.segment(
                        MilepostProximityTest.ORIGIN,MilepostProximityTest.offset(MilepostProximityTest.ORIGIN,90,50),0)
                        :MilepostProximityTest.segment(far,farEnd,i);}
            };
            assertEquals(MilepostProximity.Outcome.QUALIFIED,MilepostProximityTest.assess(MilepostProximityTest.marker(25),MilepostProximityTest.proof("walking",stream)).outcome);
        }
        double elapsed=(System.nanoTime()-start)/1e9;
        System.gc();Thread.sleep(50);
        long used=Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory();
        assertEquals(802500,visited[0]);assertTrue("retained JVM heap "+used,used<16*1024*1024);
        System.out.printf(java.util.Locale.ROOT,"journeys=%d edges=%d seconds=%.3f retained_heap_bytes=%d max_heap_bytes=%d%n",journeys,visited[0],elapsed,used,Runtime.getRuntime().maxMemory());
        start=System.nanoTime();
        for(int i=0;i<100;i++)assertEquals(MilepostProximity.Outcome.QUALIFIED,MilepostJourneyEvidenceTest.assess(MilepostJourneyEvidenceTest.journey("walking")).outcome);
        System.out.printf(java.util.Locale.ROOT,"native_adapter_repeats=100 seconds=%.3f%n",(System.nanoTime()-start)/1e9);
    }
}
