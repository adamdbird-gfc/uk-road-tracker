package com.roadprints.capture;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
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
import org.robolectric.annotation.Config;
import org.robolectric.android.controller.ActivityController;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
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
    private JSONObject visit(JSONObject pub,String id) throws Exception {
        return new JSONObject().put("id",id).put("source","google_timeline_placeVisit")
                .put("start",Instant.now().minusSeconds(3600).toString()).put("end",Instant.now().minusSeconds(3000).toString())
                .put("lat",pub.getDouble("lat")).put("lng",pub.getDouble("lng")).put("place_name","Shared building");
    }
    @Test public void reviewSliderCanNavigateAndCloseWithoutAwarding() throws Exception {
        JSONObject pub=CollectionMapCatalog.spoons(app).getJSONObject(0);
        JSONObject first=visit(pub,"first"),second=visit(pub,"second");
        TimelineVisitStore.merge(app,new JSONArray().put(first).put(second));
        JSONArray pending=new JSONArray().put(new JSONObject().put("visit",first).put("pubs",new JSONArray().put(pub)))
                .put(new JSONObject().put("visit",second).put("pubs",new JSONArray().put(pub)));
        ActivityController<Activity> controller=Robolectric.buildActivity(Activity.class).create();Activity activity=controller.get();
        AlertDialog dialog=PubVisitUi.showSlider(activity,pending,()->{});
        View root=dialog.getWindow().getDecorView();
        TextView counter=root.findViewWithTag("pub_review_counter");
        assertEquals("1 of 2",counter.getText().toString());
        assertFalse(root.findViewWithTag("pub_review_previous").isEnabled());
        root.findViewWithTag("pub_review_next").performClick();
        assertEquals("2 of 2",counter.getText().toString());
        root.findViewWithTag("pub_review_previous").performClick();
        assertEquals("1 of 2",counter.getText().toString());
        assertTrue(PubVisitStore.completed(app).isEmpty());
        find(root,"CLOSE").performClick();
        assertFalse(dialog.isShowing());assertTrue(PubVisitStore.completed(app).isEmpty());
        assertEquals(2,TimelineVisitStore.all(app).length());controller.destroy();
    }
    @Test public void countyAndTownListsAreLazyAndRetainExpansion() throws Exception {
        JSONObject pub=CollectionMapCatalog.spoons(app).getJSONObject(0);
        String county=pub.getJSONObject("county").getString("name"),town=pub.getString("town");
        ActivityController<Activity> controller=Robolectric.buildActivity(Activity.class).create();Activity activity=controller.get();
        LinearLayout panel=new LinearLayout(activity);Set<String> expanded=new HashSet<>();
        PubProgressPanel.populate(activity,panel,expanded,()->{});
        String countyKey="pub-county:"+county,townKey=countyKey+":town:"+town,rowKey="pub-row:"+pub.getString("id");
        assertNull(panel.findViewWithTag(rowKey));
        panel.findViewWithTag(countyKey+":header").performClick();
        assertNull(panel.findViewWithTag(rowKey));
        panel.findViewWithTag(townKey+":header").performClick();
        assertNotNull(panel.findViewWithTag(rowKey));
        LinearLayout rebuilt=new LinearLayout(activity);PubProgressPanel.populate(activity,rebuilt,expanded,()->{});
        assertNotNull(rebuilt.findViewWithTag(rowKey));assertTrue(expanded.contains(townKey));
        rebuilt.findViewWithTag("pub_timeline_import").performClick();
        Intent started=Shadows.shadowOf(activity).getNextStartedActivity();
        assertEquals(PubTimelineImportActivity.class.getName(),started.getComponent().getClassName());
        controller.destroy();
    }
    @Test public void legacyPubLinkRoutesToProgressAndPreservesCheckinTarget(){
        Intent intent=new Intent(app,PubVisitsActivity.class).putExtra("pub_id","selected-pub");
        ActivityController<PubVisitsActivity> controller=Robolectric.buildActivity(PubVisitsActivity.class,intent).create();
        Intent started=Shadows.shadowOf(controller.get()).getNextStartedActivity();
        assertEquals(ProgressActivity.class.getName(),started.getComponent().getClassName());
        assertEquals("wetherspoons",started.getStringExtra("progress_section"));
        assertEquals("selected-pub",started.getStringExtra("pub_checkin_id"));controller.destroy();
    }
    @Test public void popupImporterIsPlaceOnlyAndCloseDoesNotCreateVisits(){
        ActivityController<PubTimelineImportActivity> controller=Robolectric.buildActivity(PubTimelineImportActivity.class).create();
        PubTimelineImportActivity activity=controller.get();
        assertTrue(activity.getIntent().getBooleanExtra("pub_visits_only",false));
        assertTrue(activity.getIntent().getBooleanExtra("service_only",false));
        View root=activity.getWindow().getDecorView();assertNotNull(find(root,"Import from Timeline"));
        find(root,"CLOSE").performClick();assertTrue(activity.isFinishing());
        assertTrue(PubVisitStore.completed(app).isEmpty());controller.destroy();
    }
    @Test public void progressJumpMenuShowsInstalledCollectionOnly() throws Exception {
        ActivityController<ProgressActivity> controller=Robolectric.buildActivity(ProgressActivity.class);
        ProgressActivity activity=controller.get();
        java.lang.reflect.Method build=ProgressActivity.class.getDeclaredMethod("buildProgressJumpMenu");
        build.setAccessible(true);
        assertNotNull(find((View)build.invoke(activity),"Wetherspoons"));
        CollectionAccessStore.remove(app,CollectionAccessStore.SPOONS);
        assertNull(find((View)build.invoke(activity),"Wetherspoons"));
    }
    @Test public void collectionActionsOpenMapBeforeProgressAndHidePubCount(){
        ActivityController<CollectionsActivity> controller=Robolectric.buildActivity(CollectionsActivity.class).create();
        CollectionsActivity activity=controller.get();View root=activity.getWindow().getDecorView();
        TextView heading=find(root,CollectionAccessStore.name(CollectionAccessStore.SPOONS));
        ViewGroup card=(ViewGroup)heading.getParent().getParent();
        TextView map=find(card,"Show on map"),progress=find(card,"View progress");
        assertTrue(card.indexOfChild(map)<card.indexOfChild(progress));
        assertNull(find(card,"0 pubs visited"));
        TextView remove=find(card,"Remove collection");assertEquals(0xFFFF8A8A,remove.getCurrentTextColor());
        progress.performClick();Intent started=Shadows.shadowOf(activity).getNextStartedActivity();
        assertEquals(ProgressActivity.class.getName(),started.getComponent().getClassName());
        assertEquals("wetherspoons",started.getStringExtra("progress_section"));controller.destroy();
    }

    @Test public void repeatedPubVisitsHaveOneFinalRemovalActionAndCancellationKeepsBoth() throws Exception {
        JSONObject pub=CollectionMapCatalog.spoons(app).getJSONObject(0);
        TimelineVisitStore.merge(app,new JSONArray().put(visit(pub,"history-one")).put(visit(pub,"history-two")));
        PubVisitStore.confirmTimeline(app,"history-one",pub.getString("id"));PubVisitStore.confirmTimeline(app,"history-two",pub.getString("id"));
        ActivityController<Activity> controller=Robolectric.buildActivity(Activity.class).create();Activity activity=controller.get();
        AlertDialog history=PubVisitUi.details(activity,pub,()->{});View root=history.getWindow().getDecorView();
        TextView remove=find(root,"Remove incorrect visit");assertNotNull(remove);
        ViewGroup panel=(ViewGroup)remove.getParent();assertSame(remove,panel.getChildAt(panel.getChildCount()-1));
        assertNull(root.findViewWithTag("roadprints_dialog_secondary"));remove.performClick();
        AlertDialog chooser=ShadowAlertDialog.getLatestAlertDialog();assertNotSame(history,chooser);
        find(chooser.getWindow().getDecorView(),PubVisitUi.date(visit(pub,"x").getString("start"))+" · Confirmed Timeline visit").performClick();
        AlertDialog confirmation=ShadowAlertDialog.getLatestAlertDialog();
        confirmation.getWindow().getDecorView().findViewWithTag("roadprints_dialog_secondary").performClick();
        assertEquals(2,PubVisitStore.records(app).length());history.dismiss();controller.destroy();
    }
}
