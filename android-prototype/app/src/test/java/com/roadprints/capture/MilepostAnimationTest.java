package com.roadprints.capture;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Looper;
import android.widget.TextView;
import java.time.Duration;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28,manifest=Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
public class MilepostAnimationTest {
    Activity host;MilepostPresentationView view;MilepostPresentationStore store;
    @Before public void setup(){
        host=Robolectric.buildActivity(Activity.class).setup().get();
        host.getSharedPreferences("roadprints_capture_state",0).edit().clear().commit();
        store=new MilepostPresentationStore(host,UUID.randomUUID().toString());
        durationScale(1f);
        view=new MilepostPresentationView(host,store,MilepostPresentationTest.snapshot(7,7,"motion"),(after,done)->{});
        host.setContentView(view);view.onHostResume();
    }
    @After public void cleanup(){view.onHostPause();host.finish();}
    void durationScale(float value){org.robolectric.util.ReflectionHelpers.callStaticMethod(android.animation.ValueAnimator.class,"setDurationScale",org.robolectric.util.ReflectionHelpers.ClassParameter.from(float.class,value));}
    void settle(){Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1400));}
    @Test public void animatedNextCompletesOnlyAfterSettlingAndCanReplay(){
        assertTrue(view.motionAllowed());view.replay(true);view.next();
        assertEquals(0,store.read().cursor);settle();assertEquals(1,store.read().cursor);
        for(int n=2;n<=7;n++){view.next();settle();assertEquals(n,store.read().cursor);}
        assertEquals("CHARLIE HARROW",((TextView)view.findViewWithTag("milepost_replay_cipher")).getText().toString());
        view.replay(true);assertEquals(0,store.read().cursor);view.next();settle();assertEquals(1,store.read().cursor);
    }
    @Test public void interruptionReturnsToPreviousCompletedStateAcrossRestart(){
        view.replay(true);view.next();settle();view.next();view.onHostPause();settle();
        assertEquals(1,store.read().cursor);assertTrue(store.read().paused);
        view=new MilepostPresentationView(host,store,MilepostPresentationTest.snapshot(7,7,"motion"),(after,done)->{});
        host.setContentView(view);view.onHostResume();assertEquals(1,view.displayStage());
        view.replay(false);view.next();settle();assertEquals(2,store.read().cursor);
    }
    @Test public void captureStartCancelsImmediatelyWithoutCompletingAStage(){
        view.replay(true);view.next();
        host.getSharedPreferences("roadprints_capture_state",0).edit().putBoolean("active",true).commit();settle();
        assertEquals(0,store.read().cursor);assertTrue(store.read().paused);
        assertFalse(view.findViewWithTag("milepost_next").isShown());
    }
    @Test public void staticPreferenceAndSystemMotionOffHaveIdenticalFinalState(){
        view.replay(true);view.findViewWithTag("milepost_motion").performClick();
        assertTrue(store.read().staticMode);view.replay(false);view.next();assertEquals(1,store.read().cursor);
        view.findViewWithTag("milepost_motion").performClick();durationScale(0f);
        view.replay(false);assertFalse(view.motionAllowed());view.next();assertEquals(2,store.read().cursor);
        durationScale(1f);
    }
    @Test public void everyUnresolvedFramePreservesPreviouslyResolvedPositionsAndFinalRule(){
        String target="CHARLIEHARROW";
        for(int stage=1;stage<=7;stage++){
            for(float t:new float[]{0,.2f,.5f,.77f}){
                String frame=MilepostPresentation.animatedCipher(stage-1,stage,t).replace(" ","");
                for(int p=0;p<13;p++)if(MilepostPresentation.resolved(stage-1,p))assertEquals(target.charAt(p),frame.charAt(p));
                if(stage<7)assertNotEquals(target,frame);
            }
            assertEquals(MilepostPresentation.cipher(stage),MilepostPresentation.animatedCipher(stage-1,stage,1));
        }
    }
    @Test public void originalThematicResourcesAreDistinctAndVectorsInflateAtIconSizes(){
        Set<Integer> ids=new HashSet<>();
        for(int n=1;n<=7;n++){assertTrue(ids.add(MilepostArtworkView.THEMES[n]));assertEquals(n==7?0:n,MilepostArtworkView.stackDepth(n));}
        // Inflate and draw every production icon at 24/32/48dp: tests resource/path validity, not visual approval.
        List<Integer> resources=new ArrayList<>(ids);for(int id:MilepostArtworkView.MEDALLIONS)resources.add(id);
        for(int id:MilepostArtworkView.DESIGNS)resources.add(id);resources.add(R.drawable.milepost_collective);
        for(int size:new int[]{24,32,48}){
            Bitmap bitmap=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(bitmap);
            for(int id:resources){android.graphics.drawable.Drawable d=host.getDrawable(id);assertNotNull(d);d.setBounds(0,0,size,size);d.draw(canvas);}
            bitmap.recycle();
        }
    }
    @Test public void lockedCardsRemainAnonymousEvenAfterCollectionIntroduced(){
        view.update(MilepostPresentationTest.snapshot(6,1,"locked"));
        StringBuilder concealed=new StringBuilder();
        for(int i=1;i<=5;i++)concealed.append(MilepostPresentationTest.contents(view.findViewWithTag("milepost_card_"+i)));
        String text=concealed.toString();
        for(String spoiler:new String[]{"Rowe","McColl","Dudgeon","Grimshaw","Harrow"})assertFalse(text,text.contains(spoiler));
    }
}
