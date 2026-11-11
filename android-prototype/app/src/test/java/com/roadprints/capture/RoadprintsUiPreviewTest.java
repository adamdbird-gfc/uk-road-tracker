package com.roadprints.capture;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.location.Location;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import org.json.JSONArray;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,application=Application.class,qualifiers="en-rGB-w412dp-h892dp-normal-port-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class RoadprintsUiPreviewTest {
    private static final double LAT=50.7315787,LON=-1.990668;
    private static final String NAME="Millennium Milepost Holes Bay Roundabout";
    private ActivityController<Activity> controller;
    private Activity host;

    @Before public void setUp(){
        controller=Robolectric.buildActivity(Activity.class).setup().visible();
        host=controller.get();
        host.setTheme(R.style.AppTheme);
    }

    @After public void tearDown(){controller.pause().stop().destroy();}

    @Test public void undiscoveredDialogDoesNotBindIdentityOrTechnicalDetails()throws IOException{
        AlertDialog dialog=MilepostDetailsDialog.show(host,null,NAME,"rowe",LAT,LON);
        try{
            View decor=dialog.getWindow().getDecorView();
            String text=MilepostPresentationTest.contents(decor);
            assertTrue(text.contains("Undiscovered"));
            assertTrue(text.contains("recorded walk, run or cycle"));
            for(String hidden:new String[]{"Holes Bay","Rowe","OpenStreetMap",String.valueOf(LAT)})
                assertFalse(text.contains(hidden));
            assertNull(decor.findViewWithTag("roadprints_dialog_primary"));
            saveDialog("01-milepost-undiscovered",dialog);
        }finally{dialog.dismiss();}
    }

    @Test public void discoveredDialogShowsOnlySupportedAchievementAndAnOptionalMapLink()throws IOException{
        MilepostPresentationReader.Discovery discovery=new MilepostPresentationReader.Discovery(
                "fixture","2026-11-11",1,1,1<<1);
        AlertDialog dialog=MilepostDetailsDialog.show(host,discovery,NAME,"rowe",LAT,LON);
        try{
            String text=MilepostPresentationTest.contents(dialog.getWindow().getDecorView());
            assertTrue(text.contains(NAME));
            assertTrue(text.contains("11 November 2026"));
            assertTrue(text.contains("Related achievement · Andrew Rowe"));
            assertTrue(text.contains("View location on OpenStreetMap"));
            assertFalse(text.contains("Charlie Harrow"));
            assertFalse(text.contains(String.valueOf(LAT)));
            saveDialog("02-milepost-discovered",dialog);
        }finally{dialog.dismiss();}
    }

    @Test public void achievementCardsAndMysteryRenderWithSampleProgress()throws IOException{
        MilepostPresentationStore store=new MilepostPresentationStore(host,"ui-preview");
        MilepostPresentationStore.State state=store.read();
        state.staticMode=true;
        store.save(state);
        MilepostPresentation.Snapshot sample=new MilepostPresentation.Snapshot(true,3,3,0,0,3,2,
                "preview",null,null,MilepostPresentationTest.snapshot(3,2,"preview").awards);
        MilepostPresentationView view=new MilepostPresentationView(host,store,sample,(after,complete)->{});
        FrameLayout root=new FrameLayout(host);
        root.setBackgroundColor(0xFF0B1C50);
        root.setPadding(dp(16),dp(12),dp(16),0);
        root.addView(view);
        host.setContentView(root);
        view.onHostResume();
        assertNull(view.findViewWithTag("milepost_summary_replay"));
        assertNotNull(view.findViewWithTag("milepost_card_0"));
        assertNotNull(view.findViewWithTag("milepost_card_5"));
        saveScreen("03-achievement-cards",root);
        view.findViewWithTag("milepost_replay").performClick();
        view.next();
        view.next();
        view.next();
        assertFalse(view.findViewWithTag("milepost_next").isEnabled());
        assertTrue(view.findViewWithTag("milepost_previous").isEnabled());
        layout(root,dp(412),dp(760));
        if(System.getProperty("roadprints.previewDir")!=null)
            save("04-mystery-chapter",(View)view.findViewWithTag("milepost_reveal_progress").getParent());
    }

    @Test public void mapControlsAndPinKeyUseProductionRendering()throws Exception{
        host.getSharedPreferences("roadprints_ncn_national_v2",0).edit().putBoolean("enabled",true).commit();
        CollectionAccessStore.select(host,CollectionAccessStore.NCN);
        FrameLayout root=new FrameLayout(host);
        RoutePreviewView map=RoutePreviewView.overview(host);
        map.setRenderingActive(false);
        root.addView(map,new FrameLayout.LayoutParams(-1,-1));
        MapZoomControls.add(root,map);
        Location location=new Location("fixture");
        location.setLatitude(LAT);
        location.setLongitude(LON);
        location.setAccuracy(105);
        View locate=MapLocationControl.add(root,()->map.showMapLocation(location));
        assertEquals("Locate me",locate.getContentDescription());
        host.setContentView(root);
        layout(root,dp(412),dp(760));
        assertTrue(locate.performClick());
        assertArrayEquals(new double[]{LON,LAT},Arrays.copyOf(map.cameraState(),2),1e-7);
        map.setMapLayersFromPreferences();
        map.setNcnOverlay(Arrays.asList(
                new NcnMapOverlay.Line("25",new JSONArray("[[-1.9938,50.7322],[-1.9922,50.7329],[-1.9905,50.7323]]"),true),
                new NcnMapOverlay.Line("25",new JSONArray("[[-1.9905,50.7323],[-1.9888,50.7318],[-1.987,50.7306]]"),false)),null);
        MilepostCatalogue.MapLocations pins=new MilepostCatalogue.MapLocations(Arrays.asList(
                marker("first",LAT+.0011,LON-.0016),marker("second",LAT+.00025,LON+.00185)),Collections.emptyMap());
        map.setMileposts(pins,Collections.singletonMap("first",new MilepostPresentationReader.Discovery(
                "first","2026-11-11",1)));
        saveScreen("05-map-location-and-pins",root);
        AlertDialog dialog=MapLegendPanel.show(host,false,()->{});
        try{
            View decor=dialog.getWindow().getDecorView();
            View controls=decor.findViewWithTag("ncn_map_controls");
            assertNotNull(controls);
            android.widget.RadioGroup modes=(android.widget.RadioGroup)((ViewGroup)controls).getChildAt(1);
            assertEquals(modes.getChildAt(0).getId(),modes.getCheckedRadioButtonId());
            android.widget.Switch toggle=decor.findViewWithTag("ncn_map_mileposts");
            assertNotNull(toggle);
            toggle.setChecked(false);
            assertFalse(MapLayerPreferences.milepostsVisible(host));
            toggle.setChecked(true);
            assertTrue(MapLayerPreferences.milepostsVisible(host));
            if(System.getProperty("roadprints.previewDir")!=null){
                ViewGroup decorGroup=(ViewGroup)decor;
                layout(decorGroup,dp(380),-1);
                android.widget.ScrollView scroll=scrollParent(controls);
                scroll.scrollTo(0,((View)controls.getParent()).getTop()+controls.getTop());
                save("06-ncn-layer-controls",decorGroup);
            }
        }finally{dialog.dismiss();}
    }

    private static android.widget.ScrollView scrollParent(View child){
        for(android.view.ViewParent parent=child.getParent();parent!=null;parent=parent.getParent())
            if(parent instanceof android.widget.ScrollView)return (android.widget.ScrollView)parent;
        throw new AssertionError("Expected dialog scroll content");
    }

    private static MilepostDiscoveryLedger.Marker marker(String id,double lat,double lon){
        return new MilepostDiscoveryLedger.Marker(id,lon,lat,true,0,Long.MAX_VALUE,
                Collections.emptyList(),"rowe",true);
    }

    private void saveDialog(String name,AlertDialog dialog)throws IOException{
        if(System.getProperty("roadprints.previewDir")==null)return;
        View decor=dialog.getWindow().getDecorView();
        layout(decor,dp(380),-1);
        save(name,decor);
    }

    private void saveScreen(String name,View root)throws IOException{
        if(System.getProperty("roadprints.previewDir")==null)return;
        layout(root,dp(412),dp(760));
        save(name,root);
    }

    private void layout(View view,int width,int height){
        view.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height<0?dp(840):height,
                        height<0?View.MeasureSpec.AT_MOST:View.MeasureSpec.EXACTLY));
        view.layout(0,0,width,view.getMeasuredHeight());
    }

    private void save(String name,View view)throws IOException{
        view.jumpDrawablesToCurrentState();
        Bitmap bitmap=Bitmap.createBitmap(view.getWidth(),view.getHeight(),Bitmap.Config.ARGB_8888);
        try{
            view.draw(new Canvas(bitmap));
            File directory=new File(System.getProperty("roadprints.previewDir"));
            if(!directory.isDirectory()&&!directory.mkdirs())throw new IOException("Could not create "+directory);
            try(FileOutputStream out=new FileOutputStream(new File(directory,name+".png"))){
                if(!bitmap.compress(Bitmap.CompressFormat.PNG,100,out))throw new IOException("Could not save "+name);
            }
        }finally{bitmap.recycle();}
    }

    private int dp(int size){return Math.round(size*host.getResources().getDisplayMetrics().density);}
}
