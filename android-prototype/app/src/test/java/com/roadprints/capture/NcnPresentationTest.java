package com.roadprints.capture;
import android.app.Activity;
import android.content.Context;
import android.graphics.Paint;
import android.graphics.Color;
import android.view.*;
import android.widget.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class NcnPresentationTest {
 private Object field(Object o,String name)throws Exception{java.lang.reflect.Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
 private TextView text(View v,String value){if(v instanceof TextView&&((TextView)v).getText().toString().equals(value))return(TextView)v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){TextView t=text(((ViewGroup)v).getChildAt(i),value);if(t!=null)return t;}return null;}
 @Test public void sharedSummaryPreservesAccurateFractionAndReference(){
  Context c=RuntimeEnvironment.getApplication();for(int color:new int[]{RoadCompletionBar.MOTORWAY,RoadCompletionBar.A_ROAD,NcnProgressUi.PURPLE}){
   CoverageSummary summary=new CoverageSummary(c,color);summary.bind("A1(M)",250,1000,"Verified reference");
   assertEquals(.25,summary.bar.fraction(),0);assertEquals(color,summary.bar.fillColor());assertNotNull(text(summary,"25.0%"));assertNotNull(text(summary,"A1(M)"));
  }
 }
 @Test public void overlayAndFocusedMapUseSolidOrangeAndPreserveCamera()throws Exception{
  Context c=RuntimeEnvironment.getApplication();RoutePreviewView map=RoutePreviewView.overview(c);map.layout(0,0,700,700);map.restoreCameraState(51.5,.1,10);double[] camera=map.cameraState();
  map.setNcnOverlay(Collections.emptyList(),ids->{});Paint remaining=(Paint)field(map,"ncnRemaining");assertEquals(NcnProgressUi.ORANGE,remaining.getColor());assertNull(remaining.getPathEffect());assertArrayEquals(camera,map.cameraState(),0);
  Paint completed=(Paint)field(map,"ncnCompleted");assertEquals(NcnProgressUi.PURPLE,completed.getColor());
  map.setNcnCoverageSegments(Collections.emptyList(),Collections.emptyList());Paint focused=(Paint)field(map,"motorwayIncompletePaint");assertEquals(NcnProgressUi.ORANGE,focused.getColor());assertNull(focused.getPathEffect());
 }
 @Test public void collectionEnableIsSingleAndRemovalRequiresConfirmationKeepsCatalogue()throws Exception{
  Context c=RuntimeEnvironment.getApplication();c.getSharedPreferences("roadprints_ncn_national_v2",0).edit().clear().commit();
  org.robolectric.android.controller.ActivityController<CollectionsActivity> controller=Robolectric.buildActivity(CollectionsActivity.class).create();CollectionsActivity a=controller.get();
  View root=a.findViewById(android.R.id.content);assertNotNull(text(root,"Enable NCN collection"));assertNotNull(root.findViewWithTag("ncn_collection_card"));
  // Install state directly, without a network activation worker, to exercise the actual card.
  c.getSharedPreferences("roadprints_ncn_national_v2",0).edit().putBoolean("enabled",true).commit();java.lang.reflect.Method render=CollectionsActivity.class.getDeclaredMethod("render");render.setAccessible(true);render.invoke(a);
  assertNotNull(text(root,"Installed · beta access"));assertNotNull(text(root,"Show on map"));TextView remove=text(root,"Remove collection");assertNotNull(remove);remove.performClick();assertTrue(NcnProgressStore.enabled(c));
  android.app.AlertDialog dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();assertNotNull(dialog);
  dialog.dismiss();assertTrue(NcnProgressStore.enabled(c));
  c.getSharedPreferences("roadprints_ncn_national_v2",0).edit().putBoolean("enabled",false).commit();controller.destroy();
 }
}
