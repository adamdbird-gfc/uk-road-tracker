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
    private View find(View v,String text) {
        if(v instanceof TextView && text.contentEquals(((TextView)v).getText()))return v;
        if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){View found=find(((ViewGroup)v).getChildAt(i),text);if(found!=null)return found;}
        return null;
    }
}
