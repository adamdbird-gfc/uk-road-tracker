package com.roadprints.capture;

import android.app.Activity;
import android.app.AlertDialog;
import android.view.View;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class RoadprintsDialogTest {
    @Test public void existingDialogsStillDismissBeforeAction() {
        Activity activity=Robolectric.buildActivity(Activity.class).setup().get();
        boolean[] ran={false};
        AlertDialog dialog=RoadprintsDialog.show(activity,"Title",null,null,"SAVE",()->ran[0]=true,null,null);
        dialog.getWindow().getDecorView().findViewWithTag("roadprints_dialog_primary").performClick();
        assertTrue(ran[0]);assertFalse(dialog.isShowing());
    }
    @Test public void reviewCanValidateAndSaveWithoutPrematureDismissal() {
        Activity activity=Robolectric.buildActivity(Activity.class).setup().get();
        boolean[] ran={false};
        AlertDialog dialog=RoadprintsDialog.show(activity,"Review",null,null,"CONFIRM",()->ran[0]=true,"NOT A STOP",null,false);
        View primary=dialog.getWindow().getDecorView().findViewWithTag("roadprints_dialog_primary");
        primary.performClick();assertTrue(ran[0]);assertTrue(dialog.isShowing());
        dialog.dismiss();
    }
}
