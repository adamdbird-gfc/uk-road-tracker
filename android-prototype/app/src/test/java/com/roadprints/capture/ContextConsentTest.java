package com.roadprints.capture;

import android.app.Activity;
import android.app.AlertDialog;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowAlertDialog;
import org.robolectric.annotation.Config;
import org.robolectric.RobolectricTestRunner;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class ContextConsentTest {
    private Button find(View view,String text) {
        if(view instanceof Button && text.equals(((Button)view).getText().toString()))return (Button)view;
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){
            Button result=find(((ViewGroup)view).getChildAt(i),text);if(result!=null)return result;
        }
        return null;
    }
    @Test public void upgradeExplainsOnceWithoutSilentlyEnablingRequests() {
        Activity activity=Robolectric.buildActivity(Activity.class).setup().get();
        activity.getSharedPreferences("roadprints_onboarding",0).edit().putBoolean("complete",true).commit();
        ContextConsent.preferences(activity).edit().clear().commit();
        ContextConsent.maybeExplain(activity);AlertDialog dialog=ShadowAlertDialog.getLatestAlertDialog();
        assertTrue(dialog.isShowing());assertFalse(ContextMapCache.enabled(activity));
        find(dialog.getWindow().getDecorView(),"CLOSE").performClick();
        ContextConsent.maybeExplain(activity);assertFalse(dialog.isShowing());
        assertFalse(ContextMapCache.enabled(activity));
    }
    @Test public void explicitEnableAndDisablePersistChoice() {
        Activity activity=Robolectric.buildActivity(Activity.class).setup().get();
        activity.getSharedPreferences("roadprints_onboarding",0).edit().putBoolean("complete",true).commit();
        ContextConsent.preferences(activity).edit().clear().commit();
        ContextConsent.maybeExplain(activity);AlertDialog dialog=ShadowAlertDialog.getLatestAlertDialog();
        find(dialog.getWindow().getDecorView(),"ENABLE MAP CONTEXT").performClick();
        assertTrue(ContextMapCache.enabled(activity));ContextConsent.choose(activity,false);
        assertFalse(ContextMapCache.enabled(activity));assertTrue(ContextConsent.preferences(activity).getBoolean("context_explained_v1",false));
    }
}
