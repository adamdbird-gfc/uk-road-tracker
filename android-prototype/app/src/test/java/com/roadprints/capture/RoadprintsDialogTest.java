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

    @Test public void sharedDialogHasOneHeaderCloseAndRemovalLast(){
        Activity activity=Robolectric.buildActivity(Activity.class).setup().get();
        android.widget.LinearLayout body=new android.widget.LinearLayout(activity);
        android.widget.TextView remove=new android.widget.TextView(activity);remove.setText("Remove incorrect visit");remove.setTag("roadprints_destructive");body.addView(remove);
        AlertDialog dialog=RoadprintsDialog.show(activity,"Pub",null,body,"Check in now",null,"Close",null);
        View root=dialog.getWindow().getDecorView();assertNotNull(root.findViewWithTag("roadprints_dialog_close"));
        assertNull(root.findViewWithTag("roadprints_dialog_secondary"));
        android.view.ViewGroup panel=(android.view.ViewGroup)remove.getParent();assertSame(remove,panel.getChildAt(panel.getChildCount()-1));
        root.findViewWithTag("roadprints_dialog_close").performClick();assertFalse(dialog.isShowing());
    }
    @Test public void nativeConfirmationKeepsCallbacksAndPutsDestructionLast(){
        Activity activity=Robolectric.buildActivity(Activity.class).setup().get();boolean[] removed={false};
        AlertDialog dialog=new RoadprintsDialog.Builder(activity).setTitle("Delete journey?")
                .setMessage("Confirm removal").setPositiveButton("Delete",(d,w)->removed[0]=true)
                .setNegativeButton("Cancel",null).show();
        View root=dialog.getWindow().getDecorView();assertNotNull(root.findViewWithTag("roadprints_dialog_close"));
        android.widget.Button delete=dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        assertEquals(0xFFFF8A8A,delete.getCurrentTextColor());
        android.view.ViewGroup parent=(android.view.ViewGroup)delete.getParent();assertSame(delete,parent.getChildAt(parent.getChildCount()-1));
        delete.performClick();org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();assertTrue(removed[0]);
    }
}
