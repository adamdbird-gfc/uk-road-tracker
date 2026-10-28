package com.roadprints.capture;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.location.Location;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.widget.TextView;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowAlertDialog;
import org.robolectric.annotation.Config;
import java.time.Duration;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class PubLocationFeedbackTest {
    private Activity activity;private JSONObject pub;
    @Before public void setup() throws Exception {
        activity=Robolectric.buildActivity(Activity.class).setup().get();
        Shadows.shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION);
        PubVisitStore.clear(activity);pub=CollectionMapCatalog.spoons(activity).getJSONObject(0);
    }
    @After public void cleanup(){PubVisitUi.locationRequester=PubVisitUi.DEVICE_LOCATION;PubVisitStore.clear(activity);activity.finish();}
    private TextView status(AlertDialog dialog){return dialog.getWindow().getDecorView().findViewWithTag("pub_location_status");}
    private View button(AlertDialog dialog){return dialog.getWindow().getDecorView().findViewWithTag("roadprints_dialog_primary");}
    @Test public void pendingRequestShowsSpinnerAndTimeoutAllowsRetry(){
        TaskCompletionSource<Location> pending=new TaskCompletionSource<>();PubVisitUi.locationRequester=(a,r)->pending.getTask();
        AlertDialog dialog=PubVisitUi.live(activity,pub,()->{},null);button(dialog).performClick();
        assertFalse(button(dialog).isEnabled());assertTrue(status(dialog).getText().toString().contains("Checking"));
        assertEquals(View.VISIBLE,dialog.getWindow().getDecorView().findViewWithTag("pub_location_spinner").getVisibility());
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(21));
        assertTrue(button(dialog).isEnabled());assertTrue(status(dialog).getText().toString().contains("No fresh location"));
        assertTrue(PubVisitStore.completed(activity).isEmpty());dialog.dismiss();
    }
    @Test public void unavailableLocationReportsFailureAndAllowsAnotherCheck(){
        PubVisitUi.locationRequester=(a,r)->Tasks.forResult(null);
        AlertDialog dialog=PubVisitUi.live(activity,pub,()->{},null);button(dialog).performClick();Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertTrue(status(dialog).getText().toString().contains("could not be obtained"));assertTrue(button(dialog).isEnabled());dialog.dismiss();
    }
    @Test public void freshRemoteFixReportsDistanceWithoutAwarding() throws Exception {
        Location fix=new Location("gps");fix.setLatitude(pub.getDouble("lat")+.1);fix.setLongitude(pub.getDouble("lng"));fix.setAccuracy(5);fix.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
        PubVisitUi.locationRequester=(a,r)->Tasks.forResult(fix);
        AlertDialog dialog=PubVisitUi.live(activity,pub,()->{},null);button(dialog).performClick();Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertTrue(status(dialog).getText().toString().contains("Reported distance"));assertTrue(PubVisitStore.completed(activity).isEmpty());dialog.dismiss();
    }
    @Test public void accurateFixStillRequiresExplicitNamedConfirmation() throws Exception {
        Location fix=new Location("gps");fix.setLatitude(pub.getDouble("lat"));fix.setLongitude(pub.getDouble("lng"));fix.setAccuracy(5);fix.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
        PubVisitUi.locationRequester=(a,r)->Tasks.forResult(fix);
        AlertDialog dialog=PubVisitUi.live(activity,pub,()->{},null);button(dialog).performClick();Shadows.shadowOf(Looper.getMainLooper()).idle();
        AlertDialog confirmation=ShadowAlertDialog.getLatestAlertDialog();assertNotSame(dialog,confirmation);
        assertTrue(PubVisitStore.completed(activity).isEmpty());confirmation.dismiss();dialog.dismiss();
    }
}
