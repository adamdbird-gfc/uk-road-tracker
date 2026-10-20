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
   text(root,"Cycling").performClick();assertEquals("cycling",NcnProgressStore.mode(a));EditText search=find(root,EditText.class);assertNotNull(search);search.setText("344");TextView route=text(root,"NCN 344");assertNotNull(route);assertEquals(View.VISIBLE,root.findViewWithTag("ncn_route:NCN:344").getVisibility());assertEquals(View.GONE,root.findViewWithTag("ncn_route:NCN:1").getVisibility());root.findViewWithTag("ncn_route:NCN:344").performClick();Intent opened=Shadows.shadowOf(a).getNextStartedActivity();assertEquals("NCN:344",opened.getStringExtra("road_label"));assertEquals("cycling",opened.getStringExtra("ncn_mode"));
   RoutePreviewView map=RoutePreviewView.overview(a);map.layout(0,0,700,700);map.restoreCameraState(51.5,.1,10);double[] camera=map.cameraState();map.setNcnOverlay(Collections.emptyList(),ids->{});assertArrayEquals(camera,map.cameraState(),0);map.setNcnOverlay(null,null);assertArrayEquals(camera,map.cameraState(),0);
  }finally{field.set(null,before);a.getSharedPreferences("roadprints_ncn_national_v2",0).edit().putBoolean("enabled",false).commit();a.finish();}
 }
 @Test public void combinedSearchModesGroupsEmptyClearAndKeyboardRetainState()throws Exception{
  Activity a=Robolectric.buildActivity(Activity.class).setup().get();NcnCatalogue c=NcnCatalogue.load(a);NcnLedger ledger=new NcnLedger(c);
  org.json.JSONObject golden=new org.json.JSONObject(new String(NcnCatalogue.read(getClass().getResourceAsStream("/ncn-national-golden.json")),java.nio.charset.StandardCharsets.UTF_8));
  NcnMatcher matcher=new NcnMatcher(c);for(int i=0;i<2;i++)ledger.put("mode"+i,matcher.assess(golden.getJSONArray("cases").getJSONObject(i).getJSONObject("journey")));
  java.lang.reflect.Field field=NcnProgressStore.class.getDeclaredField("snapshot");field.setAccessible(true);Object before=field.get(null);
  a.getSharedPreferences("roadprints_ncn_national_v2",0).edit().putBoolean("enabled",true).commit();field.set(null,new NcnProgressStore.Snapshot(c,ledger,JourneyStore.dataRevision(a),new LinkedHashMap<>()));
  android.content.SharedPreferences prefs=a.getSharedPreferences("ncn_ui",0);prefs.edit().clear().putBoolean("national",false).putBoolean("regional",true).commit();
  Set<String> catalogueKeys=new LinkedHashSet<>(c.routes.keySet());double network=c.networkLength;
  try{
   LinearLayout root=new LinearLayout(a);root.setOrientation(1);a.setContentView(root);NcnProgressUi.add(a,root);
   EditText search=(EditText)root.findViewWithTag("ncn_search");assertEquals(android.graphics.Color.WHITE,((android.graphics.drawable.GradientDrawable)search.getBackground()).getColor().getDefaultColor());assertNotNull(text(root,"FIND A ROUTE"));
   assertEquals(219,c.routes.size());assertNotNull(root.findViewWithTag("ncn_route:NCN:1"));assertNotNull(root.findViewWithTag("ncn_route:NCN:344"));
   TextView badge=(TextView)root.findViewWithTag("ncn_badge:NCN:344");assertEquals(android.graphics.Color.WHITE,badge.getCurrentTextColor());assertEquals(NcnProgressUi.PURPLE,((android.graphics.drawable.GradientDrawable)badge.getBackground()).getColor().getDefaultColor());
   float density=a.getResources().getDisplayMetrics().density;assertEquals(Math.round(78*density),badge.getLayoutParams().width);assertEquals(Math.round(32*density),badge.getLayoutParams().height);
   View compact=root.findViewWithTag("ncn_route:NCN:344");compact.measure(View.MeasureSpec.makeMeasureSpec(Math.round(320*density),View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));assertTrue("Compact row should fit within 104dp at standard text size",compact.getMeasuredHeight()<=Math.round(104*density));
   search.setText("NCN:344");assertEquals(View.VISIBLE,root.findViewWithTag("ncn_group:national").getVisibility());assertEquals(View.GONE,root.findViewWithTag("ncn_group:regional").getVisibility());
   assertTrue(((TextView)root.findViewWithTag("ncn_matches")).getText().toString().startsWith("1 routes shown"));
   for(String mode:new String[]{"cycling","on_foot","combined"}){
    root.findViewWithTag("ncn_mode:"+mode).performClick();assertEquals(mode,NcnProgressStore.mode(a));assertSame(search,root.findViewWithTag("ncn_search"));assertEquals("NCN:344",search.getText().toString());
    assertTrue(root.findViewWithTag("ncn_mode:"+mode).isSelected());
    assertEquals(ledger.metres("NCN:344",mode)/c.routes.get("NCN:344").getDouble("length_m"),((RoadCompletionBar)root.findViewWithTag("ncn_bar:NCN:344")).fraction(),.0000001);
    assertEquals(ledger.networkMetres(mode)/network,((CoverageSummary)root.findViewWithTag("ncn_network_summary")).bar.fraction(),.0000001);
   }
   String regional=c.routes.keySet().stream().filter(key->"regional".equals(c.routes.get(key).optString("group"))).findFirst().get();search.setText(c.routes.get(regional).optString("name"));assertEquals(View.VISIBLE,root.findViewWithTag("ncn_route:"+regional).getVisibility());assertEquals(View.VISIBLE,root.findViewWithTag("ncn_group:regional").getVisibility());
   search.setText("no-such-route-xyz");assertTrue(((TextView)root.findViewWithTag("ncn_matches")).getText().toString().startsWith("No matching routes"));for(String group:new String[]{"national","regional","named"})assertEquals(View.GONE,root.findViewWithTag("ncn_group:"+group).getVisibility());
   root.findViewWithTag("ncn_clear_search").performClick();assertEquals("",search.getText().toString());assertEquals(View.GONE,root.findViewWithTag("ncn_group:national").getVisibility());assertEquals(View.VISIBLE,root.findViewWithTag("ncn_group:regional").getVisibility());assertTrue(((TextView)root.findViewWithTag("ncn_matches")).getText().toString().startsWith("219 routes shown"));
   search.requestFocus();search.onEditorAction(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);assertFalse(search.hasFocus());
   search.setText("344");root.findViewWithTag("ncn_mode:cycling").performClick();root.findViewWithTag("ncn_route:NCN:344").performClick();Intent opened=Shadows.shadowOf(a).getNextStartedActivity();assertEquals("cycling",opened.getStringExtra("ncn_mode"));
   assertEquals("344",prefs.getString("search",""));assertFalse(prefs.getBoolean("national",true));assertTrue(prefs.getBoolean("regional",false));
   LinearLayout restored=new LinearLayout(a);restored.setOrientation(1);a.setContentView(restored);NcnProgressUi.add(a,restored);assertEquals("344",((EditText)restored.findViewWithTag("ncn_search")).getText().toString());assertTrue(restored.findViewWithTag("ncn_mode:cycling").isSelected());assertEquals(View.VISIBLE,restored.findViewWithTag("ncn_route:NCN:344").getVisibility());
   assertEquals(catalogueKeys,c.routes.keySet());assertEquals(network,c.networkLength,0);
  }finally{field.set(null,before);a.getSharedPreferences("roadprints_ncn_national_v2",0).edit().putBoolean("enabled",false).commit();a.finish();}
 }

}
