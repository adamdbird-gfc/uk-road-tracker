package com.roadprints.capture;

import android.content.Context;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.BooleanSupplier;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
@LooperMode(LooperMode.Mode.PAUSED)
public class HistoryStabilisationTest {
    Context app; Object oldCatalogue;
    @Before public void setup() throws Exception {
        app=RuntimeEnvironment.getApplication();
        app.getSharedPreferences("roadprints_ncn_national_v2",0).edit().putBoolean("enabled",false).commit();
        JourneyStore.deleteAll(app);ReturnRecapStore.clear(app);
        Field field=NcnCatalogue.class.getDeclaredField("loaded");field.setAccessible(true);oldCatalogue=field.get(null);
        byte[] raw=NcnCatalogue.read(new java.util.zip.GZIPInputStream(app.getAssets().open("ncn-trial/ncn-trial.bin")));
        field.set(null,new NcnCatalogue(raw,new JSONObject().put("sha256",NcnJson.hash(raw))));
        Shadows.shadowOf(RuntimeEnvironment.getApplication()).grantPermissions("com.roadprints.capture.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION");
    }
    @After public void cleanup() throws Exception {
        JourneyStore.deleteAll(app);ReturnRecapStore.clear(app);
        Field field=NcnCatalogue.class.getDeclaredField("loaded");field.setAccessible(true);field.set(null,oldCatalogue);
    }
    JSONObject journey(String id) throws Exception {
        JSONObject golden=new JSONObject(new String(NcnCatalogue.read(getClass().getResourceAsStream("/ncn-android-golden.json")),StandardCharsets.UTF_8));
        JSONObject trip=golden.getJSONArray("cases").getJSONObject(0).getJSONObject("journey");
        return trip.put("journey_id",id).put("started_at","2026-10-20T18:00:00Z")
            .put("ended_at","2026-10-20T18:10:00Z")
            .put("source",new JSONObject().put("type","android_activity_capture"));
    }
    NcnProgressStore.Snapshot assess() throws Exception {
        Method method=NcnProgressStore.class.getDeclaredMethod("assess",Context.class,BooleanSupplier.class);
        method.setAccessible(true);
        return (NcnProgressStore.Snapshot)method.invoke(null,app,(BooleanSupplier)()->false);
    }
    @Test public void newEditedAndDeletedJourneysReuseUnchangedEvidence() throws Exception {
        JourneyStore.save(app,journey("first"));JourneyStore.save(app,journey("second"));
        NcnProgressStore.Snapshot first=assess();assertEquals(2,first.evidenceReads);
        double original=first.ledger.metres("NCN:344","combined");
        assertTrue(original>0);assertEquals(0,assess().evidenceReads);
        JourneyStore.save(app,journey("third"));
        NcnProgressStore.Snapshot added=assess();assertEquals(1,added.evidenceReads);
        assertEquals(original,added.ledger.metres("NCN:344","combined"),.001);
        JourneyStore.updateMode(app,"first","driving");
        NcnProgressStore.Snapshot edited=assess();assertEquals(1,edited.evidenceReads);
        assertEquals(original,edited.ledger.metres("NCN:344","combined"),.001);
        JourneyStore.delete(app,"second");JourneyStore.delete(app,"third");
        NcnProgressStore.Snapshot deleted=assess();assertEquals(0,deleted.evidenceReads);
        assertEquals(0,deleted.ledger.metres("NCN:344","combined"),.001);
        NcnProgressStore.checkpoint(app,deleted);
        NcnProgressStore.Snapshot restored=NcnProgressStore.restore(app);
        assertNotNull(restored);assertEquals(deleted.archiveRevision,restored.archiveRevision);
        assertEquals(1,restored.metadata.size());
    }
    @Test public void corruptRowAndRuleVersionAreRebuiltWithoutTrustingOldCredit() throws Exception {
        JourneyStore.save(app,journey("one"));NcnProgressStore.Snapshot first=assess();
        File row=new File(app.getFilesDir(),"ncn-national-assessments/"+first.catalogue.version+"/"+NcnJson.hash("one".getBytes(StandardCharsets.UTF_8))+".json");
        JSONObject cached=new JSONObject(new String(java.nio.file.Files.readAllBytes(row.toPath()),StandardCharsets.UTF_8));
        cached.put("assessment_version",-1);
        java.nio.file.Files.write(row.toPath(),cached.toString().getBytes(StandardCharsets.UTF_8));
        assertEquals(1,assess().evidenceReads);
        java.nio.file.Files.write(row.toPath(),"{broken".getBytes(StandardCharsets.UTF_8));
        assertEquals(1,assess().evidenceReads);
        NcnProgressStore.checkpoint(app,first);
        JourneyStore.save(app,journey("two"));assertNull(NcnProgressStore.restore(app));
    }
    @Test public void captureInboxSurvivesUnpresentedRestartAndMatchingUpdates() throws Exception {
        JourneyStore.save(app,journey("one"));JourneyStore.save(app,journey("two"));
        assertEquals(2,ReturnRecapStore.pending(app).size()); // No collect/map/NCN startup needed.
        JourneyStore.save(app,journey("one").put("title","updated"));
        assertEquals(2,ReturnRecapStore.pending(app).size());
        ReturnRecapStore.dismiss(app,new HashSet<>(Arrays.asList("one")));
        assertEquals("two",ReturnRecapStore.pending(app).get(0).getString("journey_id"));
        JourneyStore.save(app,journey("one"));assertEquals(1,ReturnRecapStore.pending(app).size());
        ReturnRecapStore.dismiss(app,new HashSet<>(Arrays.asList("two")));
        assertTrue(ReturnRecapStore.collect(app,JourneyStore.allSummaries(app),Long.MAX_VALUE).isEmpty());
        assertTrue(ReturnRecapStore.pending(app).isEmpty());
        assertEquals(2,JourneyStore.count(app));
    }
    @Test public void recapPresentationDoesNotWaitForMapAndAttemptDoesNotAcknowledge() throws Exception {
        JSONObject row=journey("one");JourneyStore.save(app,row);
        org.robolectric.android.controller.ActivityController<MapActivity> controller=Robolectric.buildActivity(MapActivity.class).create();
        try {
            MapActivity screen=controller.get();
            Field rows=MapActivity.class.getDeclaredField("recapJourneys");rows.setAccessible(true);
            rows.set(screen,new ArrayList<>(Arrays.asList(row)));
            Method render=MapActivity.class.getDeclaredMethod("renderRecap");render.setAccessible(true);render.invoke(screen);
            View action=find(screen.getWindow().getDecorView(),"VIEW NEW JOURNEYS");assertNotNull(action);
            assertEquals(1,ReturnRecapStore.pending(app).size());
            Field visible=MapActivity.class.getDeclaredField("recapVisible");visible.setAccessible(true);
            assertTrue(visible.getBoolean(screen)); // mapReady remains false.
        } finally {controller.destroy();}
        assertEquals(1,ReturnRecapStore.pending(app).size());
    }
    @Test public void equalSizeRapidSavesHaveDifferentTokensAndInvalidateProjections() throws Exception {
        JourneyStore.save(app,journey("token").put("title","aaaa"));
        File source=new File(app.getFilesDir(),"journey_token.json");
        String first=JourneyStore.fileToken(source);
        JSONObject payload=new JSONObject().put("title","aaaa");
        JourneyProjectionCache.write(app,source,first,"test-v1",payload);
        assertNotNull(JourneyProjectionCache.read(app,source,"test-v1"));
        JourneyStore.save(app,journey("token").put("title","bbbb"));
        assertNotEquals(first,JourneyStore.fileToken(source));
        assertNull(JourneyProjectionCache.read(app,source,"test-v1"));
    }
    @Test public void compressedMapSnapshotRetainsLargeMapsAndRejectsWrongRevision() throws Exception {
        char[] large=new char[13*1024*1024];Arrays.fill(large,'x');
        JSONObject data=new JSONObject().put("fixture",new String(large));
        MapSnapshotCache.write(app,123,data);
        assertEquals(large.length,MapSnapshotCache.read(app,123).getString("fixture").length());
        assertNull(MapSnapshotCache.read(app,124));
        File file=new File(app.getFilesDir(),"screen-cache/map-routes.gz");
        java.nio.file.Files.write(file.toPath(),new byte[]{1,2,3});
        assertNull(MapSnapshotCache.read(app,123));
    }
    @Test public void revisionReadsCannotBlockBehindAnArchiveSaveMonitor() throws Exception {
        java.util.concurrent.ExecutorService worker=java.util.concurrent.Executors.newSingleThreadExecutor();
        try {synchronized(JourneyStore.class) {
            worker.submit(()->JourneyStore.dataRevision(app)).get(1,java.util.concurrent.TimeUnit.SECONDS);
        }} finally {worker.shutdownNow();}
    }
    @Test public void foregroundReviewIsIndependentOfBlockedMapAndScreenQueue() throws Exception {
        java.util.concurrent.CountDownLatch release=new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch sharedBlocked=new java.util.concurrent.CountDownLatch(1);
        ScreenDataLoader.execute(()->{sharedBlocked.countDown();try{release.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}});
        assertTrue(sharedBlocked.await(3,java.util.concurrent.TimeUnit.SECONDS));
        JourneyStore.save(app,journey("foreground"));
        MapActivity.MapRouteLoader previous=MapActivity.mapRouteLoader;
        MapActivity.mapRouteLoader=(s,f,d,z)->{release.await();return new MapActivity.MapRoutes();};
        org.robolectric.android.controller.ActivityController<MapActivity> controller=Robolectric.buildActivity(MapActivity.class);
        try {
            controller.create().start().resume();
            Field io=MapActivity.class.getDeclaredField("recapIo");io.setAccessible(true);
            ((java.util.concurrent.ExecutorService)io.get(controller.get())).submit(()->{}).get(10,java.util.concurrent.TimeUnit.SECONDS);
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertNotNull(find(controller.get().getWindow().getDecorView(),"VIEW NEW JOURNEYS"));
            assertEquals(1,ReturnRecapStore.pending(app).size());
        } finally {
            release.countDown();controller.pause().stop().destroy();MapActivity.mapRouteLoader=previous;
        }
    }

    @Test public void representativeHistoryReportsColdWarmAndSingleAdditionWork() throws Exception {
        JSONObject template=journey("benchmark");
        JSONArray raw=new JSONArray();
        for(int p=0;p<256;p++)raw.put(new JSONArray().put(0.37).put(51.44).put(p));
        template.put("raw_capture_samples",raw);
        long seed=System.nanoTime();
        for(int i=0;i<1546;i++) {
            template.put("journey_id","bench-"+i);
            java.nio.file.Files.write(new File(app.getFilesDir(),"journey_bench-"+i+".json").toPath(),
                    template.toString().getBytes(StandardCharsets.UTF_8));
        }
        long start=System.nanoTime();int[] legacyReads={0};
        JourneyStore.forEachNcnEvidence(app,()->false,row->legacyReads[0]++);
        long legacyScan=System.nanoTime()-start;
        start=System.nanoTime();NcnProgressStore.Snapshot cold=assess();long coldNcn=System.nanoTime()-start;
        start=System.nanoTime();NcnProgressStore.Snapshot warm=assess();long warmNcn=System.nanoTime()-start;
        assertEquals(1546,legacyReads[0]);assertEquals(1546,cold.evidenceReads);assertEquals(0,warm.evidenceReads);
        assertEquals(cold.ledger.metres("NCN:344","combined"),warm.ledger.metres("NCN:344","combined"),.001);
        JourneyStore.save(app,journey("bench-added"));
        start=System.nanoTime();NcnProgressStore.Snapshot added=assess();long addition=System.nanoTime()-start;
        assertEquals(1,added.evidenceReads);assertEquals(1547,added.metadata.size());
        org.robolectric.android.controller.ActivityController<MapActivity> controller=Robolectric.buildActivity(MapActivity.class).create();
        try {
            Method read=MapActivity.class.getDeclaredMethod("readMapRoutes",JourneyFilters.class,java.time.LocalDate.class,java.time.ZoneId.class);
            read.setAccessible(true);
            start=System.nanoTime();Object coldMap=read.invoke(controller.get(),JourneyFilters.all(),java.time.LocalDate.now(),java.time.ZoneId.systemDefault());long coldMapTime=System.nanoTime()-start;
            start=System.nanoTime();Object warmMap=read.invoke(controller.get(),JourneyFilters.all(),java.time.LocalDate.now(),java.time.ZoneId.systemDefault());long warmMapTime=System.nanoTime()-start;
            Field count=coldMap.getClass().getDeclaredField("matchedJourneys");count.setAccessible(true);
            assertEquals(1547,count.getInt(coldMap));assertEquals(count.getInt(coldMap),count.getInt(warmMap));
            System.out.println("HISTORY_BENCHMARK synthetic journeys=1546 legacy_full_evidence_ms="+legacyScan/1e6
                +" cold_ncn_ms="+coldNcn/1e6+" warm_ncn_ms="+warmNcn/1e6+" add_one_ms="+addition/1e6
                +" cold_map_preparation_ms="+coldMapTime/1e6+" warm_map_preparation_ms="+warmMapTime/1e6
                +" source_decodes=1546,0,1 (JVM fixture; not Pixel startup)");
        } finally {controller.destroy();}
    }

    private View find(View v,String text) {
        if(v instanceof TextView && text.contentEquals(((TextView)v).getText()))return v;
        if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){View found=find(((ViewGroup)v).getChildAt(i),text);if(found!=null)return found;}
        return null;
    }
}
