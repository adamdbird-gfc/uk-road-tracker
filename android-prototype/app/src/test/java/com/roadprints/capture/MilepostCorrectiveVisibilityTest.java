package com.roadprints.capture;
import android.app.Activity;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.view.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class MilepostCorrectiveVisibilityTest {
    @Test public void lockedCatalogueVisibleBeforeAnyWorkerOrLedgerExists(){
        Activity a=Robolectric.buildActivity(Activity.class).setup().get();
        LinearLayout container=new LinearLayout(a);a.setContentView(container);
        MilepostScreenIntegration screen=new MilepostScreenIntegration(a,MilepostIntegration.PRODUCTION);
        screen.mount(container);
        assertEquals(1,container.getChildCount());
        String copy=copy(container);assertTrue(copy.contains("saved journeys"));assertFalse(copy.toLowerCase(java.util.Locale.ROOT).contains("assess"));
        assertFalse(copy.contains("Discoveries: 0"));
        for(String name:new String[]{"Jon Mills","Andrew Rowe","Iain McColl","David Dudgeon","John Grimshaw","Charlie Harrow"})assertFalse(copy.contains(name));
        assertNotNull(container.findViewWithTag("milepost_hidden_personalities"));
        assertNotNull(container.findViewWithTag("milepost_card_5"));
        assertNotNull(container.findViewWithTag("milepost_card_6"));
        screen.pause();a.finish();
    }
    @Test public void backgroundMapReleasesDecodedTilesWithoutChangingCamera()throws Exception{
        RoutePreviewView map=RoutePreviewView.overview(RuntimeEnvironment.getApplication());
        map.restoreCameraState(.36,51.44,14);
        java.lang.reflect.Field field=RoutePreviewView.class.getDeclaredField("tileBitmaps");field.setAccessible(true);
        @SuppressWarnings("unchecked") android.util.LruCache<String,android.graphics.Bitmap> tiles=(android.util.LruCache<String,android.graphics.Bitmap>)field.get(map);
        tiles.put("tile",android.graphics.Bitmap.createBitmap(256,256,android.graphics.Bitmap.Config.ARGB_8888));
        map.setRenderingActive(false);assertEquals(0,tiles.size());
        assertArrayEquals(new double[]{.36,51.44,14},map.cameraState(),1e-9);map.setRenderingActive(true);
    }
    private static String copy(View v){StringBuilder s=new StringBuilder();if(v instanceof TextView)s.append(((TextView)v).getText());if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)s.append(copy(((ViewGroup)v).getChildAt(i)));return s.toString();}
    @Test public void viewportDensityRetainsAllApprovedCatalogueWithoutHeldCandidates()throws Exception{
        java.util.List<MilepostDiscoveryLedger.Marker> markers=MilepostCatalogue.load(RuntimeEnvironment.getApplication());assertEquals(887,markers.size());
        MilepostMapOverlay layer=new MilepostMapOverlay(MilepostCatalogue.mapLocations(RuntimeEnvironment.getApplication()),1);
        MilepostMapOverlay.Projection projection=new MilepostMapOverlay.Projection(){public float x(double lon){return 100;}public float y(double lat){return 100;}};
        layer.project(0,0,5,800,600,48,projection);assertEquals(1,layer.size);assertEquals(887,layer.counts[0]);assertEquals(0,layer.hit(100,100,24));
        String first=layer.markers.identity[0];
        layer.setDiscoveries(java.util.Collections.singletonMap(first,new MilepostPresentationReader.Discovery(first,"2026-11-11",1)));
        layer.project(0,0,5,800,600,48,projection);
        assertEquals(1,layer.discoveredCounts[0]);
        assertEquals(887,layer.members(0).size());
        assertEquals(Integer.valueOf(0),layer.members(0).get(0));
        assertEquals(-1,layer.hit(300,300,24));
        layer.project(0,0,6,800,600,48,new MilepostMapOverlay.Projection(){public float x(double lon){return -1;}public float y(double lat){return 100;}});assertEquals(0,layer.size);
    }
}
