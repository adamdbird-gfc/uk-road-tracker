package com.roadprints.capture;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import android.widget.Switch;
import org.json.JSONArray;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import java.util.Collections;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class MapLegendTest {
    private Context app;
    @Before public void reset(){
        app=RuntimeEnvironment.getApplication();app.getSharedPreferences(MapLayerPreferences.PREFS,0).edit().clear().commit();
        app.getSharedPreferences(CollectionAccessStore.PREFS,0).edit().clear().commit();
        app.getSharedPreferences("roadprints_collections_v1",0).edit().clear().commit();
    }
    @Test public void legendSwitchPersistsAndClosingDoesNotChangeCameraOrEvidence(){
        android.app.Activity activity=Robolectric.buildActivity(Activity.class).create().get();
        app.getSharedPreferences("roadprints_collections_v1",0).edit().putBoolean("service_stations_unlocked",true).putString("keep","evidence").commit();
        int[] changed={0};AlertDialog dialog=MapLegendPanel.show(activity,false,()->changed[0]++);
        Switch toggle=dialog.getWindow().getDecorView().findViewWithTag("map_layer:REMAINING");
        assertTrue(toggle.isChecked());toggle.setChecked(false);assertEquals(1,changed[0]);
        assertFalse(MapLayerPreferences.visible(app.getApplicationContext(),MapLayerPreferences.Layer.REMAINING));
        View none=dialog.getWindow().getDecorView().findViewWithTag("map_collection:none");none.performClick();
        assertEquals(CollectionAccessStore.NONE,CollectionAccessStore.selected(app));assertEquals("evidence",app.getSharedPreferences("roadprints_collections_v1",0).getString("keep",""));
        dialog.dismiss();AlertDialog reopened=MapLegendPanel.show(activity,false,()->{});
        assertFalse(((Switch)reopened.getWindow().getDecorView().findViewWithTag("map_layer:REMAINING")).isChecked());reopened.dismiss();activity.finish();
    }
    @Test public void hiddenMotorwayIsExcludedFromRoadTapGroupsAndDataIsRetained()throws Exception{
        RoutePreviewView map=RoutePreviewView.overview(app);map.layout(0,0,800,800);
        JSONArray road=new JSONArray("[[0.34,51.44],[0.38,51.44]]");
        map.setMotorwayCoverageSegments(Collections.emptyList(),Collections.singletonList(road));map.restoreCameraState(.36,51.44,14);
        int[] taps={0};map.setMapRoadTapListener((lat,lng)->taps[0]++);
        java.lang.reflect.Method tap=RoutePreviewView.class.getDeclaredMethod("tapMapRoad",float.class,float.class);tap.setAccessible(true);
        assertEquals(true,tap.invoke(map,400f,400f));assertEquals(1,taps[0]);
        MapLayerPreferences.set(app,MapLayerPreferences.Layer.MOTORWAYS,false);map.setMapLayersFromPreferences();
        assertEquals(false,tap.invoke(map,400f,400f));assertEquals(1,taps[0]);
        java.lang.reflect.Field data=RoutePreviewView.class.getDeclaredField("coveredMotorwaySegments");data.setAccessible(true);assertEquals(1,((java.util.List<?>)data.get(map)).size());
        assertArrayEquals(new double[]{.36,51.44,14},map.cameraState(),1e-9);
        MapLayerPreferences.set(app,MapLayerPreferences.Layer.MOTORWAYS,true);map.setMapLayersFromPreferences();assertEquals(true,tap.invoke(map,400f,400f));
    }
    @Test public void townLegendIncludesBoundaryKeyAndUsesScrollableSharedDialog(){
        Activity activity=Robolectric.buildActivity(Activity.class).create().get();AlertDialog dialog=MapLegendPanel.show(activity,true,()->{});
        assertNotNull(dialog.getWindow().getDecorView().findViewWithTag("map_layer:BOUNDARIES"));
        assertNull(dialog.getWindow().getDecorView().findViewWithTag("map_collection:none"));dialog.dismiss();activity.finish();
    }
    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE) public void lineReferenceUsesRendererColoursAndHasVisibleContrast(){
        for(MapLayerPreferences.Layer layer:MapLayerPreferences.Layer.values()){
            MapLegendPanel.Sample sample=new MapLegendPanel.Sample(app,layer.color,null,false);sample.layout(0,0,160,80);
            Bitmap bitmap=Bitmap.createBitmap(160,80,Bitmap.Config.ARGB_8888);sample.draw(new Canvas(bitmap));
            assertEquals(layer.color,bitmap.getPixel(80,40));assertNotEquals(layer.color,bitmap.getPixel(80,30));
        }
    }
}
