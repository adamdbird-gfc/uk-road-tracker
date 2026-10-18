package com.roadprints.capture;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import java.lang.reflect.Method;
import java.util.*;
import org.json.JSONArray;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class RouteStrokeContinuityTest {
    static class RecordedCanvas extends Canvas {
        final List<Integer> colors=new ArrayList<>();
        @Override public void drawPath(Path path,Paint paint){colors.add(paint.getColor());}
    }
    @Test public void editableSectionHalosNeverOverpaintAnyBlackCentreline()throws Exception{
        List<JSONArray> sections=Arrays.asList(new JSONArray("[[0,51],[0.001,51]]"),
                new JSONArray("[[0.001,51],[0.002,51]]"),new JSONArray("[[0.003,51],[0.004,51]]"));
        RoutePreviewView view=new RoutePreviewView(RuntimeEnvironment.getApplication(),null,sections,
                Collections.emptySet(),null);
        RecordedCanvas canvas=new RecordedCanvas();
        Method draw=RoutePreviewView.class.getDeclaredMethod("drawEditableRoutes",Canvas.class);draw.setAccessible(true);
        draw.invoke(view,canvas);
        assertEquals(6,canvas.colors.size());
        for(int i=0;i<3;i++)assertEquals(canvas.colors.get(0),canvas.colors.get(i));
        for(int i=3;i<6;i++)assertEquals(canvas.colors.get(3),canvas.colors.get(i));
        assertNotEquals(canvas.colors.get(0),canvas.colors.get(3));
        assertEquals(3,sections.size()); // Drawing does not join the real gap.
    }
    @Test public void aRemovedEdgeIsNotReconnectedToMakeTheLineLookContinuous()throws Exception{
        RoutePreviewView view=new RoutePreviewView(RuntimeEnvironment.getApplication(),null,
                Arrays.asList(new JSONArray("[[0,51],[0.001,51],[0.002,51],[0.003,51]]")),
                Collections.singleton(1),null);
        RecordedCanvas canvas=new RecordedCanvas();Method draw=RoutePreviewView.class.getDeclaredMethod("drawEditableRoutes",Canvas.class);
        draw.setAccessible(true);draw.invoke(view,canvas);
        assertEquals(4,canvas.colors.size()); // Two surviving paths, each outlined then filled.
    }
}
