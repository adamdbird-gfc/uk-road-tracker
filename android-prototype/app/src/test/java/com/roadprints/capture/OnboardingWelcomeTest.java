package com.roadprints.capture;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class OnboardingWelcomeTest {
    private org.robolectric.android.controller.ActivityController<OnboardingActivity> controller;
    private OnboardingActivity activity;
    @Before public void setup(){
        RuntimeEnvironment.getApplication().getSharedPreferences("roadprints_measurement",Context.MODE_PRIVATE).edit().putBoolean("prompt_seen",true).commit();
        RuntimeEnvironment.getApplication().getSharedPreferences("roadprints_onboarding",Context.MODE_PRIVATE).edit().clear().commit();
        controller=Robolectric.buildActivity(OnboardingActivity.class).setup();activity=controller.get();
    }
    @After public void cleanup(){controller.pause().stop().destroy();}
    private TextView find(View view,String label){
        if(view instanceof TextView && label.contentEquals(((TextView)view).getText()))return (TextView)view;
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){
            TextView found=find(((ViewGroup)view).getChildAt(i),label);if(found!=null)return found;
        }
        return null;
    }
    private void tap(String label){TextView view=find(activity.getWindow().getDecorView(),label);assertNotNull(label,view);
        if(view.isClickable())view.performClick();else ((View)view.getParent()).performClick();}
    private void choices(){tap("LET’S BEGIN");}
    @Test public void welcomeIsSkippableImmediatelyAndChoicesReplaceTravellerQuestion(){
        assertNotNull(find(activity.getWindow().getDecorView(),"Every journey grows your map."));
        assertNull(find(activity.getWindow().getDecorView(),"How much of a traveller are you?"));
        choices();assertNotNull(find(activity.getWindow().getDecorView(),"IMPORT GOOGLE TIMELINE"));
        assertNotNull(find(activity.getWindow().getDecorView(),"START TRACKING JOURNEYS"));
        assertNotNull(find(activity.getWindow().getDecorView(),"RESTORE A ROADPRINTS BACKUP"));
    }
    @Test public void timelineChoiceOpensImportDirectly(){choices();tap("IMPORT GOOGLE TIMELINE");
        Intent next=Shadows.shadowOf(activity).getNextStartedActivity();assertEquals(TimelineImportActivity.class.getName(),next.getComponent().getClassName());}
    @Test public void trackingChoiceExplainsPermissionsBeforeStartingSetup(){choices();tap("START TRACKING JOURNEYS");
        assertNull(Shadows.shadowOf(activity).getNextStartedActivity());tap("SET UP TRACKING");
        Intent next=Shadows.shadowOf(activity).getNextStartedActivity();assertEquals(MainActivity.class.getName(),next.getComponent().getClassName());
        assertTrue(next.getBooleanExtra("tracking_settings_screen",false));assertTrue(next.getBooleanExtra("onboarding_enable_tracking",false));}
    @Test public void restoreChoiceDoesNotCompleteOnboardingBeforeBackupSucceeds(){choices();tap("RESTORE A ROADPRINTS BACKUP");
        Intent next=Shadows.shadowOf(activity).getNextStartedActivity();assertEquals(DataManagementActivity.class.getName(),next.getComponent().getClassName());
        assertTrue(next.getBooleanExtra("onboarding_restore",false));
        assertFalse(activity.getSharedPreferences("roadprints_onboarding",Context.MODE_PRIVATE).getBoolean("complete",false));}
    @Test public void finishedIllustrationDrawsWithoutNetworkOrLocation(){
        WelcomeDiscoveryView view=new WelcomeDiscoveryView(activity,false);view.finishAnimation();view.layout(0,0,360,240);
        Bitmap image=Bitmap.createBitmap(360,240,Bitmap.Config.ARGB_8888);view.draw(new Canvas(image));
        assertTrue(view.getContentDescription().toString().startsWith("Example discovery:"));image.recycle();
    }
}
