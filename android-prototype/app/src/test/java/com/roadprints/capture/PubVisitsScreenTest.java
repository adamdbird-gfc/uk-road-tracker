package com.roadprints.capture;

import android.content.Context;
import android.content.Intent;
import android.app.AlertDialog;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import org.json.JSONArray;
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
import java.lang.reflect.Method;
import java.time.Instant;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class PubVisitsScreenTest {
    private Context app;
    @Before public void reset(){
        app=RuntimeEnvironment.getApplication();PubVisitStore.clear(app);TimelineVisitStore.clear(app);
        app.getSharedPreferences(CollectionAccessStore.PREFS,0).edit().clear().commit();
        CollectionAccessStore.installForTesting(app,CollectionAccessStore.SPOONS);
    }
    @After public void clean(){
        PubVisitStore.clear(app);TimelineVisitStore.clear(app);
        app.getSharedPreferences(CollectionAccessStore.PREFS,0).edit().clear().commit();
    }
    private TextView find(View view,String label){
        if(view instanceof TextView&&label.contentEquals(((TextView)view).getText()))return (TextView)view;
        if(view instanceof ViewGroup){
            ViewGroup group=(ViewGroup)view;
            for(int i=0;i<group.getChildCount();i++){TextView found=find(group.getChildAt(i),label);if(found!=null)return found;}
        }
        return null;
    }
    @Test public void timelineButtonOpensPlaceOnlyImportAndReviewCannotAwardOnOpening() throws Exception {
        JSONArray pubs=CollectionMapCatalog.spoons(app);
        JSONObject pub=pubs.getJSONObject(0);
        JSONObject visit=new JSONObject().put("id","ui-review").put("source","google_timeline_placeVisit")
                .put("start",Instant.now().minusSeconds(3600).toString()).put("end",Instant.now().minusSeconds(3000).toString())
                .put("lat",pub.getDouble("lat")).put("lng",pub.getDouble("lng")).put("place_name","Shared building");
        TimelineVisitStore.merge(app,new JSONArray().put(visit));
        org.robolectric.android.controller.ActivityController<PubVisitsActivity> controller=
                Robolectric.buildActivity(PubVisitsActivity.class).create();
        PubVisitsActivity activity=controller.get();
        Method render=PubVisitsActivity.class.getDeclaredMethod("render",JSONArray.class,JSONArray.class,JSONArray.class,String.class);
        render.setAccessible(true);render.invoke(activity,pubs,new JSONArray(),PubVisitStore.pending(app,pubs),null);
        View root=activity.getWindow().getDecorView();
        assertNotNull(find(root,"0 pubs visited"));
        assertNotNull(find(root,"IMPORT TIMELINE PLACE VISITS"));
        find(root,"IMPORT TIMELINE PLACE VISITS").performClick();
        Intent started=Shadows.shadowOf(activity).getNextStartedActivity();
        assertTrue(started.getBooleanExtra("service_only",false));
        assertTrue(started.getBooleanExtra("pub_visits_only",false));
        find(root,"REVIEW: "+pub.getString("name")).performClick();
        AlertDialog dialog=ShadowAlertDialog.getLatestAlertDialog();
        assertTrue(dialog.isShowing());
        assertTrue(PubVisitStore.completed(app).isEmpty());
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        assertTrue(PubVisitStore.completed(app).isEmpty());
        controller.destroy();
    }
}
