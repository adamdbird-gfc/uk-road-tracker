package com.roadprints.capture;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import org.json.JSONArray;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28) @LooperMode(LooperMode.Mode.PAUSED)
public class Beta206PresentationTest {
    private Context app;
    @Before public void setup(){app=RuntimeEnvironment.getApplication();}
    private void size(View v){v.measure(View.MeasureSpec.makeMeasureSpec(320,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(480,View.MeasureSpec.EXACTLY));v.layout(0,0,320,480);}
    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE) public void sharedProgressComponentActuallyDrawsWhiteTrackAndCorrectFill(){
        for(int colour:new int[]{RoadCompletionBar.MOTORWAY,RoadCompletionBar.A_ROAD}){
            RoadCompletionBar bar=new RoadCompletionBar(app,.376,colour);
            bar.measure(View.MeasureSpec.makeMeasureSpec(200,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(12,View.MeasureSpec.EXACTLY));bar.layout(0,0,200,12);
            Bitmap image=Bitmap.createBitmap(200,12,Bitmap.Config.ARGB_8888);bar.draw(new Canvas(image));
            assertEquals(0xFFFFFFFF,image.getPixel(180,6));assertEquals(colour,image.getPixel(25,6));assertEquals(.376,bar.fraction(),.000001);
        }
    }
    @Test public void b2008RecordedGeometryArrivingAfterLayoutFitsInsteadOfUkView()throws Exception{
        RoutePreviewView map=RoutePreviewView.overview(app);size(map);
        JSONArray line=new JSONArray("[[0.59,51.32],[0.595,51.33]]");map.setRouteData(null,Arrays.asList(line));map.fitRoadGeometry(Arrays.asList(line));
        assertEquals(.5925,map.cameraState()[0],.001);assertEquals(51.325,map.cameraState()[1],.002);assertTrue(map.cameraState()[2]>12);
        map.restoreCameraState(.593,51.326,13);size(map);assertEquals(13,map.cameraState()[2],.0001);
    }
    @Test public void localGeometryBeforeLayoutFitsWhenViewportBecomesReady()throws Exception{
        RoutePreviewView map=RoutePreviewView.overview(app);JSONArray line=new JSONArray("[[0.37,51.44],[0.371,51.441]]");
        map.setRouteData(null,Arrays.asList(line));map.fitRoadGeometry(Arrays.asList(line));size(map);
        assertEquals(.3705,map.cameraState()[0],.001);assertTrue(map.cameraState()[2]>14);
    }
    @Test public void veryShortSinglePointAndMalformedResultsHaveFiniteUsefulCamera()throws Exception{
        RoutePreviewView map=RoutePreviewView.overview(app);size(map);
        map.fitRoadGeometry(Arrays.asList(new JSONArray("[[0.37,51.44]]")));assertEquals(.37,map.cameraState()[0],.00001);assertTrue(map.cameraState()[2]>=16);assertTrue(Double.isFinite(map.cameraState()[1]));
        map.fitRoadGeometry(Arrays.asList(new JSONArray("[[null,null],[],[0.37,51.44],[0.370001,51.440001]]")));assertTrue(map.cameraState()[2]>=16);
        double[] before=map.cameraState();map.fitRoadGeometry(Collections.emptyList());assertArrayEquals(before,map.cameraState(),.000001);
    }
    @Test public void m20AndA12ActualFullReferencesFitBothRemainingAndCoveredGeometry()throws Exception{
        JourneyStore.deleteAll(app);
        for(RoadDetailSelection selection:Arrays.asList(new RoadDetailSelection(RoadDetailSelection.MOTORWAY,"M20","GB","",""),new RoadDetailSelection(RoadDetailSelection.A_ROAD,"A12","GB","",""))){
            RoadDetailModel model=RoadDetailModel.load(app,selection,false);assertTrue(selection.label,model.fullReference);
            List<JSONArray> geometry=new ArrayList<>(model.covered);geometry.addAll(model.remaining);assertFalse(geometry.isEmpty());
            RoutePreviewView map=RoutePreviewView.overview(app);size(map);map.fitRoadGeometry(geometry);
            assertTrue(selection.label,map.cameraState()[2]>6);assertTrue(map.cameraState()[0]>-1&&map.cameraState()[0]<3);assertTrue(map.cameraState()[1]>50&&map.cameraState()[1]<54);
            double[] fitted=map.cameraState();size(map);assertArrayEquals(fitted,map.cameraState(),.000001);
        }
    }
}
