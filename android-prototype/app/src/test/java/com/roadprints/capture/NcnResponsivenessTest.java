package com.roadprints.capture;

import android.content.Context;
import java.lang.reflect.Modifier;
import java.util.concurrent.*;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class NcnResponsivenessTest {
    @Test public void installerCannotOwnUiSchedulingMonitor() throws Exception {
        // In 207–209 both methods synchronized on NcnCatalogueFiles.class.
        // A slow install therefore blocked ensure() from Progress's one-second UI ticker.
        assertFalse(Modifier.isSynchronized(NcnCatalogueFiles.class.getDeclaredMethod("install",Context.class,JSONObject.class).getModifiers()));
        Context app=RuntimeEnvironment.getApplication();
        app.getSharedPreferences("ncn_reference_updates",0).edit().putLong("next",Long.MAX_VALUE).commit();
        ExecutorService workers=Executors.newFixedThreadPool(2);
        CountDownLatch locked=new CountDownLatch(1),release=new CountDownLatch(1);
        Future<?> installer=workers.submit(()->{synchronized(NcnCatalogueFiles.INSTALL_LOCK){locked.countDown();try{release.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}}});
        try {
            assertTrue(locked.await(2,TimeUnit.SECONDS));
            long start=System.nanoTime();
            workers.submit(()->NcnCatalogueFiles.refreshIfDue(app)).get(1,TimeUnit.SECONDS);
            System.out.println("NCN timing: refresh while installation blocked ms="+(System.nanoTime()-start)/1_000_000.0);
            assertFalse("Installer still stalled; UI scheduler completed independently",installer.isDone());
        } finally {release.countDown();workers.shutdownNow();}
    }

    @Test public void progressPollingCannotWaitForSlowInstall() throws Exception {
        Context app=RuntimeEnvironment.getApplication();
        app.getSharedPreferences("ncn_reference_updates",0).edit().putLong("next",Long.MAX_VALUE).commit();
        app.getSharedPreferences("roadprints_ncn_national_v2",0).edit().putBoolean("enabled",true).commit();
        java.lang.reflect.Field running=NcnProgressStore.class.getDeclaredField("running");running.setAccessible(true);
        java.lang.reflect.Field snapshot=NcnProgressStore.class.getDeclaredField("snapshot");snapshot.setAccessible(true);
        java.lang.reflect.Field failure=NcnProgressStore.class.getDeclaredField("failedRevision");failure.setAccessible(true);
        Object previous=snapshot.get(null);long previousFailure=failure.getLong(null);boolean previousRunning=running.getBoolean(null);
        ExecutorService worker=Executors.newSingleThreadExecutor();
        try {
            // A published empty reference is enough to exercise the exact warm polling path.
            NcnCatalogue c=trial();snapshot.set(null,new NcnProgressStore.Snapshot(c,new NcnLedger(c),JourneyStore.dataRevision(app),new java.util.LinkedHashMap<>()));
            running.setBoolean(null,false);failure.setLong(null,Long.MIN_VALUE);
            synchronized(NcnCatalogueFiles.INSTALL_LOCK){worker.submit(()->NcnProgressStore.ensure(app)).get(1,TimeUnit.SECONDS);}
        } finally {snapshot.set(null,previous);failure.setLong(null,previousFailure);running.setBoolean(null,previousRunning);app.getSharedPreferences("roadprints_ncn_national_v2",0).edit().putBoolean("enabled",false).commit();worker.shutdownNow();}
    }
    @Test public void repeatedProgressTotalsMatchFormerFullScan()throws Exception{
        NcnCatalogue c=trial();NcnLedger ledger=new NcnLedger(c);
        JSONObject golden=new JSONObject(new String(NcnCatalogue.read(getClass().getResourceAsStream("/ncn-android-golden.json")),java.nio.charset.StandardCharsets.UTF_8));
        for(int i=0;i<2;i++)ledger.put("trip"+i,new NcnMatcher(c).assess(golden.getJSONArray("cases").getJSONObject(i).getJSONObject("journey")));
        ledger.metres("NCN:344","combined");
        java.lang.reflect.Field field=NcnLedger.class.getDeclaredField("index");field.setAccessible(true);
        @SuppressWarnings("unchecked") java.util.Map<String,java.util.Map<String,java.util.List<double[]>>> index=(java.util.Map<String,java.util.Map<String,java.util.List<double[]>>>)field.get(ledger);
        double before=0,after=0;long started=System.nanoTime();
        for(int repeat=0;repeat<1000;repeat++)for(String mode:new String[]{"combined","on_foot","cycling"})for(String route:c.routes.keySet())
            for(java.util.Map.Entry<String,java.util.List<double[]>> entry:index.getOrDefault(mode,java.util.Collections.emptyMap()).entrySet())if(entry.getKey().startsWith(route+"/"))for(double[] span:entry.getValue())before+=span[1]-span[0];
        long scan=System.nanoTime()-started;started=System.nanoTime();
        for(int repeat=0;repeat<1000;repeat++)for(String mode:new String[]{"combined","on_foot","cycling"})for(String route:c.routes.keySet())after+=ledger.metres(route,mode);
        long cached=System.nanoTime()-started;
        assertEquals(before,after,.001);
        System.out.println("NCN repeated totals benchmark: routes="+c.routes.size()+" repeats=1000 former_scan_ms="+scan/1_000_000.0+" cached_ms="+cached/1_000_000.0);
    }
    private NcnCatalogue trial() throws Exception {
        byte[] raw=NcnCatalogue.read(new java.util.zip.GZIPInputStream(RuntimeEnvironment.getApplication().getAssets().open("ncn-trial/ncn-trial.bin")));
        return new NcnCatalogue(raw,new JSONObject().put("sha256",NcnJson.hash(raw)));
    }
}
