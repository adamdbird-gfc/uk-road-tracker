package com.roadprints.capture;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import org.json.JSONArray;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import java.lang.reflect.Method;
import java.util.Collections;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class MapRenderIdentityTest {
    static final class UnhashableCoordinates extends JSONArray {
        @Override public int hashCode(){throw new AssertionError("Structural coordinate hashing on rendering path");}
        @Override public boolean equals(Object other){throw new AssertionError("Structural coordinate equality on rendering path");}
    }
    private JSONArray row(int size) throws Exception {
        JSONArray row=new UnhashableCoordinates();
        for(int i=0;i<size;i++)row.put(new JSONArray().put(i*.0000001).put(0));
        return row;
    }
    private Canvas canvas(){return new Canvas(Bitmap.createBitmap(300,300,Bitmap.Config.ARGB_8888));}
    @Test public void largePreparedAndWarmFramesNeverHashNestedCoordinates() throws Exception {
        MapRenderGeometry.clearPrepared();JSONArray row=row(25000);
        assertThrows(AssertionError.class,()->new java.util.WeakHashMap<JSONArray,Boolean>().put(row,true));
        Paint paint=new Paint();paint.setStrokeWidth(3);
        MapRenderGeometry.prepare(Collections.singletonList(row));MapRenderGeometry cache=new MapRenderGeometry();Canvas canvas=canvas();
        long started=System.nanoTime();
        for(int frame=0;frame<30;frame++)assertTrue(cache.draw(canvas,row,paint,null,.1,130,0,300,300));
        assertEquals(0,cache.builds);assertEquals(29,cache.hits);assertEquals(25000,cache.vertices);
        assertFalse(cache.draw(canvas,row,paint,null,.1,1000000,0,300,300));
        System.out.println("RENDER_IDENTITY_BENCHMARK points=25000 frames=30 structural_hash_calls=0 warm_frames_ms="+(System.nanoTime()-started)/1e6+" environment=Robolectric_not_phone");
        cache.clear();assertEquals(0,cache.vertices);MapRenderGeometry.clearPrepared();
    }
    @Test public void republishingMutatedSameLengthGeometryRetiresThePreparedPath() throws Exception {
        JSONArray row=row(2);RoutePreviewView view=new RoutePreviewView(RuntimeEnvironment.getApplication(),row);
        MapRenderGeometry.prepare(Collections.singletonList(row));
        Method prepared=MapRenderGeometry.class.getDeclaredMethod("prepared",JSONArray.class);prepared.setAccessible(true);
        assertNotNull(prepared.invoke(null,row));row.getJSONArray(1).put(1,.001);
        view.setRouteData(row,Collections.emptyList());assertNull(prepared.invoke(null,row));
        MapRenderGeometry cache=new MapRenderGeometry();assertTrue(cache.draw(canvas(),row,new Paint(),null,.1,0,0,300,300));
        assertEquals(1,cache.builds);MapRenderGeometry.clearPrepared();
    }
    @Test public void oversizedRoutesAreNotStronglyRetainedAndEditingAlwaysRebuilds() throws Exception {
        MapRenderGeometry.clearPrepared();MapRenderGeometry cache=new MapRenderGeometry();JSONArray row=row(100001);
        cache.draw(canvas(),row,new Paint(),null,.1,100,0,300,300);assertEquals(0,cache.vertices);
        JSONArray edit=row(2);cache.draw(canvas(),edit,new Paint(),null,.1,0,0,300,300,false);
        int builds=cache.builds;edit.getJSONArray(1).put(1,.001);
        cache.draw(canvas(),edit,new Paint(),null,.1,0,0,300,300,false);
        assertEquals(builds+1,cache.builds);assertEquals(0,cache.vertices);cache.clear();
    }
}
