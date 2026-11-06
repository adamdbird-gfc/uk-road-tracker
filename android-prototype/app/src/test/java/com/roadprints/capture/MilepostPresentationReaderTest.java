package com.roadprints.capture;

import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import java.util.*;
import java.util.concurrent.atomic.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28,manifest=Config.NONE)
public class MilepostPresentationReaderTest {
    MilepostAchievementsTest fixture;
    @Before public void setup()throws Exception{fixture=new MilepostAchievementsTest();fixture.setup();}
    @After public void close()throws Exception{fixture.cleanup();}
    MilepostPresentation.Snapshot read(long acknowledged)throws Exception{
        return MilepostPresentationReader.read(fixture.c,fixture.ledger,MilepostAchievementsTest.USER,acknowledged,()->false);
    }
    @Test public void readsStableReceiptUniquePostsSupportedDatesAndAllSevenAwards()throws Exception{fixture.work(()->{
        fixture.posts(7);fixture.save("a");fixture.save("duplicate");MilepostAchievementsTest.setNcn(fixture.ncn(100));fixture.derive(1000);
        String original=fixture.state().toString();MilepostPresentation.Snapshot s=read(0);
        assertTrue(s.current);assertEquals(7,s.count);assertEquals(7,s.newPosts);assertEquals(7,s.postOrdinal);assertEquals(7,s.newAwards(0).size());
        assertEquals("2026-11-05",s.from);assertEquals(s.from,s.to);assertNull(s.awards.get(4).date);assertNull(s.awards.get(6).date);
        MilepostPresentationReader.Page page=MilepostPresentationReader.page(fixture.c,fixture.ledger,MilepostAchievementsTest.USER,0,()->false);
        assertTrue(page.current);assertFalse(page.more);assertEquals(7,page.discoveries.size());
        for(int i=0;i<7;i++)assertEquals(i+1,page.discoveries.get(i).ordinal);
        assertEquals(0,read(s.postOrdinal).newPosts);assertEquals(original,fixture.state().toString());
    });}
    @Test public void freshUnknownOnlyIsSpoilerSafeAndStaleLedgerCannotAnnounceNewDiscoveries()throws Exception{fixture.work(()->{
        fixture.install(1,Collections.singletonList(fixture.marker("unknown","unknown",false,true)));fixture.save("a");
        MilepostAchievementsTest.setNcn(fixture.ncn(0));fixture.derive(1000);MilepostPresentation.Snapshot s=read(0);
        assertTrue(s.current);assertEquals(1,s.newPosts);assertFalse(s.introduced());
        fixture.save("new-unassessed");s=read(0);assertFalse(s.current);assertEquals(0,s.newPosts);assertTrue(s.newAwards(0).isEmpty());
    });}
    @Test public void deletionWithdrawalReinstatementAndBackfillKeepRecognitionOrdering()throws Exception{fixture.work(()->{
        fixture.posts(7);fixture.save("a");MilepostAchievementsTest.setNcn(fixture.ncn(100));fixture.derive(1000);
        MilepostPresentation.Snapshot first=read(0);long ordinal=first.postOrdinal;
        JourneyStore.delete(fixture.c,"a");MilepostAchievementsTest.setNcn(fixture.ncn(0));fixture.derive(2000);
        MilepostPresentation.Snapshot withdrawn=read(ordinal);assertEquals(0,withdrawn.count);assertEquals(0,withdrawn.individuals());assertTrue(withdrawn.introduced());
        assertFalse(withdrawn.card(0).earned);assertTrue(withdrawn.card(0).status.contains("withdrawn"));
        fixture.save("a");MilepostAchievementsTest.setNcn(fixture.ncn(100));fixture.derive(3000);
        MilepostPresentation.Snapshot restored=read(ordinal);assertEquals(7,restored.count);assertEquals(0,restored.newPosts);assertEquals(ordinal,restored.postOrdinal);
        assertEquals(first.awards.get(0).ordinal,restored.awards.get(0).ordinal);
    });}
    @Test public void pendingAndIncompleteAreReportedWithoutFabricatingDate()throws Exception{fixture.work(()->{
        fixture.install(1,Collections.singletonList(fixture.marker("unapproved","unknown",false,false)));fixture.save("a");
        MilepostAchievementsTest.setNcn(fixture.ncn(0));fixture.derive(1000);
        MilepostPresentation.Snapshot s=read(0);assertTrue(s.current);assertEquals(0,s.count);assertEquals(1,s.incomplete);assertNull(s.from);assertNull(s.to);
    });}
    @Test public void readerRejectsMainThreadAndCooperativelyCancels()throws Exception{
        assertThrows(IllegalStateException.class,()->read(0));
        fixture.work(()->assertThrows(java.util.concurrent.CancellationException.class,()->MilepostPresentationReader.read(fixture.c,fixture.ledger,MilepostAchievementsTest.USER,0,()->true)));
    }
    static long heap(){Runtime r=Runtime.getRuntime();return r.totalMemory()-r.freeMemory();}
    @Test public void compactPresentationMemoryIsIndependentOfArchiveGeometry()throws Exception{fixture.work(()->{
        List<MilepostDiscoveryLedger.Marker> markers=new ArrayList<>();for(int i=0;i<4096;i++)markers.add(fixture.marker("bulk"+i,"mills",true,true));
        fixture.install(1,markers);markers=null;fixture.seedFacts(4096,1605,"fixture:bulk");
        fixture.ledger.db().execSQL("INSERT INTO recognition(user,marker,ordinal,first_recognised,first_journey,first_token,first_event,first_catalogue) SELECT ?,id,rowid,1000,'fixture:j0','fixture-token','2026-11-01T00:00:00Z',1 FROM marker",new Object[]{MilepostAchievementsTest.USER});
        MilepostAchievementsTest.setNcn(fixture.ncn(0));
        // Publish the existing compact Stage 5 result; do not run reconciliation/geometry benchmarks.
        fixture.publishPrepared(1000);
        // Synthetic prepared publication uses a fixture NCN token. Align it with the actual read fence.
        NcnProgressStore.Snapshot n=NcnProgressStore.current();String token=n.catalogue.version+":"+n.archiveRevision+":"+n.evidenceRevision+":"+NcnProgressStore.revision(fixture.c);
        fixture.ledger.db().execSQL("UPDATE achievement_snapshot SET ncn_token=?",new Object[]{token});
        assertTrue(read(0).current);
        MilepostPresentationReader.Page first=MilepostPresentationReader.page(fixture.c,fixture.ledger,MilepostAchievementsTest.USER,0,()->false);
        assertEquals(16,first.discoveries.size());assertTrue(first.more);
        MilepostPresentationReader.Page second=MilepostPresentationReader.page(fixture.c,fixture.ledger,MilepostAchievementsTest.USER,first.discoveries.get(15).ordinal,()->false);
        assertTrue(second.discoveries.get(0).ordinal>first.discoveries.get(15).ordinal);
        read(0);System.gc();Thread.sleep(50);long before=heap();AtomicLong peak=new AtomicLong(before);AtomicBoolean stop=new AtomicBoolean();
        Thread sampler=new Thread(()->{while(!stop.get()){peak.accumulateAndGet(heap(),Math::max);try{Thread.sleep(2);}catch(InterruptedException e){return;}}});sampler.start();
        long start=System.nanoTime();MilepostPresentation.Snapshot retained=null;
        try{for(int i=0;i<3;i++)retained=read(0);}finally{stop.set(true);sampler.join();}
        long elapsed=(System.nanoTime()-start)/1000000;System.gc();Thread.sleep(50);long after=heap();
        assertEquals(4096,retained.newPosts);assertEquals(7,retained.awards.size());
        System.out.println("MILEPOST_PRESENTATION_PROBE markers=4096 sources=1605 proofs=8192 repeats=3 before="+before+" peak="+peak.get()+" incremental_peak="+(peak.get()-before)+" retained="+after+" retained_delta="+(after-before)+" elapsed_ms="+elapsed+" decoded_bitmaps=0");
        assertTrue("Presentation peak exceeds 16MiB",peak.get()-before<16L*1024*1024);
        assertTrue("Presentation retained growth exceeds 4MiB",after-before<4L*1024*1024);
    });}
}
