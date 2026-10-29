package com.roadprints.capture;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Set;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class RoutePreviewEmptyGeometryTest {
    private JSONArray pins() throws Exception {
        return new JSONArray().put(new JSONObject().put("id","pub")
                .put("name","Pub").put("lat",51).put("lng",0));
    }
    private JSONArray line() throws Exception {
        return new JSONArray("[[0,51],[0.001,51.001]]");
    }
    private void drawWithoutTileRequests(RoutePreviewView view) throws Exception {
        view.layout(0,0,256,256);
        view.restoreCameraState(0,51,14);
        // Tile networking is unrelated to this regression. Mark every visible
        // tile as loading so the actual drawing path runs entirely offline.
        Field field=RoutePreviewView.class.getDeclaredField("loadingTiles");
        field.setAccessible(true);
        @SuppressWarnings("unchecked") Set<String> loading=(Set<String>)field.get(view);
        int count=1<<14, x=count/2;
        double radians=Math.toRadians(51);
        int y=(int)Math.floor((1-Math.log(Math.tan(radians)+1/Math.cos(radians))/Math.PI)/2*count);
        for(int dx=-4;dx<=4;dx++)for(int dy=-4;dy<=4;dy++)loading.add("14/"+(x+dx)+"/"+(y+dy));
        view.draw(new Canvas(Bitmap.createBitmap(256,256,Bitmap.Config.ARGB_8888)));
        assertArrayEquals(new double[]{0,51,14},view.cameraState(),1e-9);
    }
    private Object endpointRoute(RoutePreviewView view,String name) throws Exception {
        Method method=RoutePreviewView.class.getDeclaredMethod(name);
        method.setAccessible(true);return method.invoke(view);
    }
    @Test public void overviewDrawsPubOrServicesPinsWithoutAnyRouteAndCanRemoveOverlay() throws Exception {
        RoutePreviewView view=RoutePreviewView.overview(RuntimeEnvironment.getApplication());
        for(String collection:new String[]{CollectionAccessStore.SPOONS,CollectionAccessStore.SERVICES}) {
            view.setRouteData(null,Collections.emptyList());
            view.setCollectionVenues(collection,pins(),Collections.emptySet());
            drawWithoutTileRequests(view);
        }
        view.setCollectionVenues(CollectionAccessStore.NONE,new JSONArray(),Collections.emptySet());
        drawWithoutTileRequests(view);
    }
    @Test public void endpointEnabledPreviewWithOnlyPinsHasNoEndpointRoutes() throws Exception {
        RoutePreviewView view=new RoutePreviewView(RuntimeEnvironment.getApplication());
        view.setCollectionVenues(CollectionAccessStore.SPOONS,pins(),Collections.emptySet());
        assertNull(endpointRoute(view,"firstMotorwayRoute"));
        assertNull(endpointRoute(view,"lastMotorwayRoute"));
        drawWithoutTileRequests(view);
    }
    @Test public void clearingLastRoadOverlayDoesNotLeaveADrawTimeRouteAssumption() throws Exception {
        RoutePreviewView view=new RoutePreviewView(RuntimeEnvironment.getApplication());
        view.setARoadCoverageSegments(Collections.emptyList(),Collections.singletonList(line()));
        assertSame(endpointRoute(view,"firstMotorwayRoute"),endpointRoute(view,"lastMotorwayRoute"));
        drawWithoutTileRequests(view);
        view.setARoadCoverageSegments(Collections.emptyList(),Collections.emptyList());
        drawWithoutTileRequests(view);
    }
    @Test public void boundaryOnlyViewDoesNotInventRoadEndpoints() throws Exception {
        RoutePreviewView view=new RoutePreviewView(RuntimeEnvironment.getApplication());
        view.setSettlementBoundary(new JSONObject("{\"type\":\"Polygon\",\"coordinates\":[[[0,51],[0.001,51],[0.001,51.001],[0,51]]]}"));
        drawWithoutTileRequests(view);
    }
}
