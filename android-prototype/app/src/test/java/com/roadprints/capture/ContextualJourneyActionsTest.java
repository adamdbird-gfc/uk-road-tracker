package com.roadprints.capture;

import android.app.AlertDialog;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import android.widget.LinearLayout;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class ContextualJourneyActionsTest {
    JSONObject road(String mode)throws Exception {
        JSONArray samples=new JSONArray();
        for(int i=0;i<20;i++)samples.put(MultimodalFerryTest.row(.37+i*.0015,51.44,i*20,10));
        return MultimodalFerryTest.journey("ordinary",samples).put("mode",mode)
                .put("started_at","2026-10-24T12:00:00Z").put("ended_at","2026-10-24T12:07:00Z");
    }
    @Test public void ordinaryModesAndFerryLabelsDoNotOfferReview()throws Exception {
        for(String mode:new String[]{"driving","walking","running","cycling","bus","unknown","ferry"})
            assertFalse(mode,FerryJourneyEvidence.reviewAvailable(road(mode)));
    }
    @Test public void confirmedCrossingAndPartialMarineEvidenceStayReviewable()throws Exception {
        JSONObject journey=MultimodalFerryTest.journey("crossing",MultimodalFerryTest.crossing());
        for(String mode:new String[]{"driving","walking","cycling","ferry","unknown"})
            assertTrue(mode,FerryJourneyEvidence.reviewAvailable(journey.put("mode",mode)));
        JSONArray partial=new JSONArray();for(int i=2;i<12;i++)partial.put(MultimodalFerryTest.crossing().get(i));
        assertTrue(FerryJourneyEvidence.reviewAvailable(MultimodalFerryTest.journey("partial",partial)));
        assertFalse(FerryJourneyEvidence.inspect(partial).canSplit());
        journey.put("ferry_recovery",JSONObject.NULL);
        assertTrue(FerryJourneyEvidence.reviewAvailable(journey));assertFalse(FerryJourneyEvidence.restorable(journey));
    }
    @Test public void ambiguityPortProximityAndSavedFlagsAreInsufficient()throws Exception {
        JSONObject journey=road("unknown").put("context_evidence",new JSONObject().put("reason","mixed_mode_requires_review"))
                .put("ferry_evidence",new JSONObject().put("confidence","supported_terminal_transition"));
        assertFalse(FerryJourneyEvidence.reviewAvailable(journey));
        JSONArray rows=new JSONArray().put(MultimodalFerryTest.row(1.333,51.126,0,10))
                .put(MultimodalFerryTest.row(1.6,51.08,30,10)).put(MultimodalFerryTest.row(1.875,50.981,3300,10));
        assertFalse(FerryJourneyEvidence.reviewAvailable(journey.put("capture_route_samples",rows)));
        assertFalse(FerryJourneyEvidence.reviewAvailable(journey.put("ferry_recovery",JSONObject.NULL)));
    }
    @Test public void journalledDerivedJourneyRetainsRestoreEvenWithoutCrossingSamples()throws Exception {
        assertTrue(FerryJourneyEvidence.reviewAvailable(road("driving").put("ferry_recovery",
                new JSONObject().put("source_id","original").put("backup_key","original-r1"))));
    }
    @Test public void titleSaveDoesNotWaitForTheArchiveLockOnTheMainThread()throws Exception {
        android.content.Context app=RuntimeEnvironment.getApplication();
        Shadows.shadowOf(RuntimeEnvironment.getApplication()).grantPermissions("com.roadprints.capture.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION");
        JourneyListActivity screen=Robolectric.buildActivity(JourneyListActivity.class).create().get();
        JSONObject journey=road("unknown").put("transport_confirmation","required").put("revision",1);
        JourneyStore.save(app,journey);
        java.lang.reflect.Method show=JourneyListActivity.class.getDeclaredMethod("showDetails",JSONObject.class);
        show.setAccessible(true);
        show.invoke(screen,journey);
        AlertDialog dialog=ShadowAlertDialog.getLatestAlertDialog();
        View root=dialog.getWindow().getDecorView();
        EditText title=find(root,EditText.class,null);
        Button save=find(root,Button.class,"SAVE CHANGES");
        assertNotNull(title);
        assertNotNull(save);
        title.setText("New title");

        CountDownLatch locked=new CountDownLatch(1), release=new CountDownLatch(1), unlocked=new CountDownLatch(1);
        Thread archiveReader=new Thread(() -> {
            synchronized (JourneyStore.class) {
                locked.countDown();
                try { release.await(4,TimeUnit.SECONDS); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
                unlocked.countDown();
            }
        });
        archiveReader.start();
        try {
            assertTrue(locked.await(4,TimeUnit.SECONDS));
            assertTrue(save.performClick());
            assertEquals("Save returned while the archive was still busy",1,unlocked.getCount());
        } finally {
            release.countDown();
            archiveReader.join();
        }

        java.lang.reflect.Field edits=JourneyListActivity.class.getDeclaredField("journeyEdits");
        edits.setAccessible(true);
        ((ExecutorService)edits.get(screen)).submit(() -> {}).get(5,TimeUnit.SECONDS);
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        JSONObject saved=JourneyStore.get(app,"ordinary");
        assertEquals("New title",saved.getString("title"));
        assertEquals("required",saved.getString("transport_confirmation"));
        assertFalse(saved.has("transport_changes"));
        assertEquals(2,saved.getInt("revision"));
        dialog.dismiss();
        screen.finish();
    }

    private static <T extends View> T find(View view,Class<T> type,String buttonText) {
        if(type.isInstance(view)&&(buttonText==null||buttonText.contentEquals(((Button)view).getText())))
            return type.cast(view);
        if(view instanceof ViewGroup) {
            ViewGroup children=(ViewGroup)view;
            for(int i=0;i<children.getChildCount();i++) {
                T found=find(children.getChildAt(i),type,buttonText);
                if(found!=null)return found;
            }
        }
        return null;
    }

    @Test public void detailUsesEvidenceGateAndSpacesEveryAction()throws Exception {
        Shadows.shadowOf(RuntimeEnvironment.getApplication()).grantPermissions("com.roadprints.capture.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION");
        JourneyListActivity screen=Robolectric.buildActivity(JourneyListActivity.class).create().get();
        java.lang.reflect.Method show=JourneyListActivity.class.getDeclaredMethod("showDetails",JSONObject.class);show.setAccessible(true);
        try {
            show.invoke(screen,road("driving"));AlertDialog dialog=ShadowAlertDialog.getLatestAlertDialog();
            assertNull(dialog.getWindow().getDecorView().findViewWithTag("journey_ferry_review"));dialog.dismiss();
            JSONObject crossing=road("unknown").put("capture_route_samples",MultimodalFerryTest.crossing());
            show.invoke(screen,crossing);dialog=ShadowAlertDialog.getLatestAlertDialog();
            Button ferry=dialog.getWindow().getDecorView().findViewWithTag("journey_ferry_review");assertNotNull(ferry);
            ViewGroup parent=(ViewGroup)ferry.getParent();int buttons=0;
            for(int i=0;i<parent.getChildCount();i++)if(parent.getChildAt(i) instanceof Button){
                LinearLayout.LayoutParams params=(LinearLayout.LayoutParams)parent.getChildAt(i).getLayoutParams();
                assertTrue("Every action has a visible gap",params.topMargin>0);buttons++;
            }
            assertTrue(buttons>=2);dialog.dismiss();
        } finally {screen.finish();}
    }
}
