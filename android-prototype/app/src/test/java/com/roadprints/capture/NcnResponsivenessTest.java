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
    private NcnCatalogue trial() throws Exception {
        byte[] raw=NcnCatalogue.read(RuntimeEnvironment.getApplication().getAssets().open("ncn-trial/ncn-trial.bin"));
        return new NcnCatalogue(raw,new JSONObject().put("sha256",NcnJson.hash(raw)));
    }
}
