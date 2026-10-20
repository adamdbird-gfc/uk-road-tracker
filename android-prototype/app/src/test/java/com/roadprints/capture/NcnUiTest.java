package com.roadprints.capture;
import android.app.Activity;
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import java.util.*;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class NcnUiTest {
 @org.junit.Before public void installExternalTestReference()throws Exception{new NcnNationalTest().installExternalTestReference();}
 private <T extends View>T find(View root,Class<T> type){if(type.isInstance(root))return type.cast(root);if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++){T found=find(((ViewGroup)root).getChildAt(i),type);if(found!=null)return found;}return null;}
 private TextView text(View root,String prefix){if(root instanceof TextView&&((TextView)root).getText().toString().startsWith(prefix))return(TextView)root;if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++){TextView found=text(((ViewGroup)root).getChildAt(i),prefix);if(found!=null)return found;}return null;}
 @Test public void groupedSearchModeNavigationAndOverlayPreserveCamera()throws Exception{
  Activity a=Robolectric.buildActivity(Activity.class).setup().get();NcnCatalogue c=NcnCatalogue.load(a);NcnLedger ledger=new NcnLedger(c);ledger.networkMetres("combined");
  java.lang.reflect.Field field=NcnProgressStore.class.getDeclaredField("snapshot");field.setAccessible(true);Object before=field.get(null);
  a.getSharedPreferences("roadprints_ncn_national_v2",0).edit().putBoolean("enabled",true).commit();field.set(null,new NcnProgressStore.Snapshot(c,ledger,JourneyStore.dataRevision(a),new LinkedHashMap<>()));
  NcnProgressStore.mode(a,"combined");try{
   LinearLayout root=new LinearLayout(a);root.setOrientation(1);a.setContentView(root);NcnProgressUi.add(a,root);assertNotNull(text(root,"▾ National routes"));assertNotNull(text(root,"▸ Regional routes"));assertNotNull(text(root,"▸ Named routes"));
   text(root,"Cycling").performClick();assertEquals("cycling",NcnProgressStore.mode(a));EditText search=find(root,EditText.class);assertNotNull(search);search.setText("344");TextView route=text(root,"NCN 344");assertNotNull(route);assertEquals(View.VISIBLE,((View)route.getParent()).getVisibility());assertEquals(View.GONE,((View)text(root,"NCN 1 ·").getParent()).getVisibility());((View)route.getParent()).performClick();Intent opened=Shadows.shadowOf(a).getNextStartedActivity();assertEquals("NCN:344",opened.getStringExtra("road_label"));assertEquals("cycling",opened.getStringExtra("ncn_mode"));
   RoutePreviewView map=RoutePreviewView.overview(a);map.layout(0,0,700,700);map.restoreCameraState(51.5,.1,10);double[] camera=map.cameraState();map.setNcnOverlay(Collections.emptyList(),ids->{});assertArrayEquals(camera,map.cameraState(),0);map.setNcnOverlay(null,null);assertArrayEquals(camera,map.cameraState(),0);
  }finally{field.set(null,before);a.getSharedPreferences("roadprints_ncn_national_v2",0).edit().putBoolean("enabled",false).commit();a.finish();}
 }
}
