package com.roadprints.capture;

import android.app.Activity;
import android.content.Context;
import android.view.*;
import android.widget.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28,manifest=Config.NONE)
public class MilepostPresentationTest {
    Context context;String user;MilepostPresentationStore store;
    @Before public void setup(){context=RuntimeEnvironment.getApplication();user=UUID.randomUUID().toString();store=new MilepostPresentationStore(context,user);MilepostPresentationStore.State initial=store.read();initial.staticMode=true;store.save(initial);
        context.getSharedPreferences("roadprints_capture_state",0).edit().clear().commit();}
    static MilepostPresentation.Snapshot snapshot(int count,int earned,String receipt){
        List<MilepostPresentation.Award> awards=new ArrayList<>();for(int i=0;i<7;i++)awards.add(new MilepostPresentation.Award(i<earned,i<earned?i+1:0,i==4||i==6?null:"2026-11-01T00:00:00Z",i==4||i==6?"unknown":"journey"));
        return new MilepostPresentation.Snapshot(true,count,count,2,3,count,earned,receipt,"2026-10-01","2026-11-01",awards);
    }
    static String contents(View view){
        StringBuilder text=new StringBuilder();if(view instanceof TextView)text.append(((TextView)view).getText()).append(' ');
        if(view.getContentDescription()!=null)text.append(view.getContentDescription()).append(' ');
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)text.append(contents(((ViewGroup)view).getChildAt(i)));
        return text.toString();
    }
    @Test public void unknownDesignsUnlockChaptersWithoutRevealingPersonalities(){
        for(int count:new int[]{0,1,6}){
            MilepostPresentation.Snapshot s=snapshot(count,0,"a");assertFalse(s.introduced());assertEquals(0,s.individuals());
            for(int i=0;i<6;i++){MilepostPresentation.Card c=s.card(i);assertEquals(i==5?"The seven-chapter mystery":"Hidden personality",c.title);assertEquals("",c.biography);assertEquals(i==5?count:0,c.stage);assertFalse(c.accessible().contains("Mills"));}
        }
    }
    @Test public void firstIndividualRevealsOnlyThatNameAndCollectiveProgress(){
        MilepostPresentation.Snapshot s=snapshot(1,1,"a");assertTrue(s.introduced());assertEquals(1,s.individuals());
        assertEquals("Jon Mills",s.card(0).title);assertFalse(s.card(0).biography.isEmpty());
        for(int i=1;i<6;i++){assertEquals(i==5?"The seven-chapter mystery":"Hidden personality",s.card(i).title);assertEquals("",s.card(i).biography);}
        assertEquals(1,s.card(5).stage);
    }
    @Test public void eachIndividualCanBeFirstWithoutSpoilingOtherIdentities(){
        String[] names={"Jon Mills","Andrew Rowe","Iain McColl","David Dudgeon","John Grimshaw","Charlie Harrow"};
        for(int first=0;first<6;first++){
            List<MilepostPresentation.Award> awards=new ArrayList<>();
            for(int i=0;i<7;i++)awards.add(new MilepostPresentation.Award(i==first,i==first?1:0,null,"unknown"));
            MilepostPresentation.Snapshot s=new MilepostPresentation.Snapshot(true,first==5?7:1,1,0,0,1,1,"first",null,null,awards);
            assertTrue(s.introduced());assertEquals(1,s.individuals());assertEquals(names[first],s.card(first).title);
            for(int i=0;i<6;i++)if(i!=first){assertEquals(i==5?"The seven-chapter mystery":"Hidden personality",s.card(i).title);assertEquals("",s.card(i).biography);}
        }
    }
    @Test public void grimshawCanIntroduceCollectionWithoutAnyPosts(){
        List<MilepostPresentation.Award> awards=new ArrayList<>();for(int i=0;i<7;i++)awards.add(new MilepostPresentation.Award(i==4,i==4?1:0,null,"unknown"));
        MilepostPresentation.Snapshot s=new MilepostPresentation.Snapshot(true,0,0,0,0,0,1,"g",null,null,awards);
        assertTrue(s.introduced());assertEquals("John Grimshaw",s.card(4).title);assertEquals("Hidden personality",snapshot(1,0,"u").card(4).title);
        assertEquals(0,s.card(5).stage);assertEquals("Historical dates unavailable",s.dates());
    }
    @Test public void everyCipherPositionAndLayerPrefixIsDeterministic(){
        String target="CHARLIEHARROW";int[][] positions={{},{0,7},{1,8},{2,9},{3,10},{4,11},{5,12},{6}};
        Set<Integer> resolved=new HashSet<>();assertEquals("",MilepostPresentation.cipher(0));
        String[] themes={"Hidden","Seasons","Continents","Ages of Man","Zodiac","Centuries","Final Mystery","Revealed"};
        for(int n=0;n<=7;n++){
            assertEquals(themes[n],MilepostPresentation.THEMES[n]);for(int p:positions[n])resolved.add(p);
            if(n>0){String cipher=MilepostPresentation.cipher(n);assertEquals(' ',cipher.charAt(7));String letters=cipher.replace(" ","");
                for(int p=0;p<13;p++){char expected=resolved.contains(p)?target.charAt(p):(char)('A'+(target.charAt(p)-'A'+7)%26);
                    assertEquals("Stage "+n+" position "+p,expected,letters.charAt(p));assertEquals(resolved.contains(p),MilepostPresentation.resolved(n,p));}
                assertEquals(cipher,MilepostPresentation.cipher(n));
            }
            assertEquals(n==7?0:n,MilepostPresentation.layers(n).size());
            if(n>1&&n<7)assertEquals(MilepostPresentation.layers(n-1),MilepostPresentation.layers(n).subList(0,n-1));
            if(n<7)assertFalse(MilepostPresentation.announcement(n).contains("Harrow"));
        }
        assertEquals("CHARLIE HARROW",MilepostPresentation.cipher(7));assertEquals(7,MilepostPresentation.stage(100));
        assertFalse(snapshot(6,1,"a").card(5).accessible().contains("Harrow"));
    }
    @Test public void allSixIndividualsAndParentCanArriveInOneSummary(){
        MilepostPresentation.Snapshot s=snapshot(4096,7,"bulk");assertEquals(6,s.individuals());assertEquals(7,s.newAwards(0).size());
        assertEquals("In Honour of Time",s.newAwards(0).get(6));assertEquals(1,s.newAwards(6).size());
        assertTrue(s.awards.get(6).earned);assertTrue(s.dates().contains("Journey precision"));
    }
    @Test public void acknowledgementIsDurableBatchBoundedAndUserScoped(){
        MilepostPresentationStore.State state=store.read();MilepostPresentation.Snapshot first=snapshot(7,7,"first"),later=snapshot(8,7,"later");
        assertTrue(store.pending(state,first));store.acknowledge(state,first);
        MilepostPresentationStore replacement=new MilepostPresentationStore(context,user);state=replacement.read();
        assertFalse(replacement.pending(state,first));assertTrue(replacement.pending(state,later));assertEquals(7,state.acknowledgedPosts);
        assertTrue(new MilepostPresentationStore(context,"other").pending(new MilepostPresentationStore.State(),first));
    }
    @Test public void pauseCursorArtworkAndStaticPreferenceSurviveRestart(){
        MilepostPresentationStore.State state=store.read();state.cursor=4;state.completedStage=4;state.replaying=true;state.paused=true;state.dismissed=true;state.dismissedReceipt="a";state.artworkVersion=1;store.save(state);
        state=new MilepostPresentationStore(context,user).read();assertEquals(4,state.cursor);assertEquals(4,state.completedStage);assertTrue(state.paused);assertTrue(state.dismissed);assertTrue(state.staticMode);assertEquals(1,state.artworkVersion);
    }
    @Test public void corruptOrFuturePresentationDoesNotOverwriteAcknowledgement(){
        context.getSharedPreferences("roadprints_milepost_presentation_v1",0).edit().putString("user:"+user,"{\"schema\":99}").commit();
        assertThrows(IllegalStateException.class,()->store.read());
        assertEquals("{\"schema\":99}",context.getSharedPreferences("roadprints_milepost_presentation_v1",0).getString("user:"+user,""));
    }
    ActivityController<Activity> controller;MilepostPresentationView view;
    void host(MilepostPresentation.Snapshot s){controller=Robolectric.buildActivity(Activity.class).setup().visible();
        view=new MilepostPresentationView(controller.get(),store,s,(after,done)->done.accept(new MilepostPresentationReader.Page(Collections.emptyList(),false,true)));controller.get().setContentView(view);view.onHostResume();}
    @After public void closeHost(){if(controller!=null)controller.pause().stop().destroy();}
    @Test public void lockedPersonalitiesAreGroupedWithoutLeakingNames(){
        host(snapshot(0,0,"a"));String all=contents(view);
        for(String name:new String[]{"Mills","Rowe","McColl","Dudgeon","Grimshaw","Harrow","Honour","Seasons","Fossil"})assertFalse(name,all.contains(name));
        assertNull(view.findViewWithTag("milepost_replay"));
        assertNull(view.findViewWithTag("milepost_summary_replay"));
        for(int i=0;i<5;i++)assertNull(view.findViewWithTag("milepost_card_"+i));
        assertTrue(contents(view.findViewWithTag("milepost_hidden_personalities")).contains("5 hidden personalities"));
        assertNotNull(view.findViewWithTag("milepost_card_5"));
        assertNotNull(view.findViewWithTag("milepost_card_6"));
    }
    @Test public void unknownPostUnlocksFirstChapterAndFinalIdentityRequiresTheAward(){
        host(snapshot(1,0,"unknown-post"));
        assertTrue(contents(view.findViewWithTag("milepost_card_5")).contains("1 of 7 chapters unlocked"));
        view.findViewWithTag("milepost_replay").performClick();
        view.next();
        assertEquals(1,view.displayStage());
        assertFalse(view.findViewWithTag("milepost_next").isEnabled());

        view.update(snapshot(7,4,"without-final-award"));
        view.skip();
        assertEquals(6,view.displayStage());
        assertFalse(contents(view).contains("Charlie Harrow"));
        assertTrue(contents(view.findViewWithTag("milepost_card_5")).contains("6 of 7 chapters unlocked"));
    }
    @Test public void staticRevealPersistsCompletedStagesAndRestartsPaused(){
        host(snapshot(6,1,"a"));view.replay(true);view.next();view.next();assertEquals(2,store.read().cursor);
        view.onHostPause();view=new MilepostPresentationView(controller.get(),store,snapshot(6,1,"a"),(after,done)->done.accept(new MilepostPresentationReader.Page(Collections.emptyList(),false,true)));controller.get().setContentView(view);view.onHostResume();
        assertTrue(store.read().paused);assertEquals(2,view.displayStage());view.next();assertEquals(2,view.displayStage());
        view.replay(false);view.next();assertEquals(3,store.read().cursor);assertEquals(3,store.read().completedStage);
        TextView cipher=view.findViewWithTag("milepost_replay_cipher");assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO,cipher.getImportantForAccessibility());assertFalse(cipher.isFocusable());
    }
    @Test public void revisitingChaptersKeepsCompletionAndDoesNotUnlockFutureChapters(){
        host(snapshot(3,1,"chapter-history"));
        view.findViewWithTag("milepost_replay").performClick();
        for(int stage=1;stage<=3;stage++)view.next();
        View previous=view.findViewWithTag("milepost_previous"),next=view.findViewWithTag("milepost_next");
        assertFalse(next.isEnabled());
        assertTrue(previous.performClick());
        assertEquals(2,store.read().cursor);
        assertEquals(3,store.read().completedStage);
        assertTrue(previous.performClick());
        assertFalse(previous.isEnabled());
        assertEquals(1,view.displayStage());
        assertTrue(next.performClick());
        assertEquals(2,view.displayStage());
        view.later();
        view.findViewWithTag("milepost_replay").performClick();
        assertEquals(2,view.displayStage());
        view.next();
        assertFalse(view.findViewWithTag("milepost_next").isEnabled());
        assertFalse(contents(view).contains("Charlie Harrow"));
        assertTrue(contents(view).contains("Find another Milepost"));
    }
    @Test public void captureStartPausesImmediatelyAndPreventsEveryTransitionAndResume(){
        host(snapshot(7,7,"a"));view.replay(true);view.next();assertEquals(1,store.read().cursor);
        context.getSharedPreferences("roadprints_capture_state",0).edit().putBoolean("active",true).commit();
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        assertTrue(store.read().paused);assertNull(view.findViewWithTag("milepost_next"));
        view.next();view.replay(false);view.skip();view.onHostResume();assertEquals(1,store.read().cursor);assertTrue(store.read().paused);
        context.getSharedPreferences("roadprints_capture_state",0).edit().putBoolean("active",false).commit();
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();view.replay(false);view.next();assertEquals(2,store.read().cursor);
    }
    @Test public void laterSkipAndFutureDiscoveriesDoNotCreateAwardsOrLoseCursor(){
        host(snapshot(3,1,"a"));view.replay(true);view.next();view.later();assertTrue(store.read().dismissed);assertEquals(1,store.read().cursor);assertEquals(0,store.read().acknowledgedPosts);
        view.update(snapshot(4,1,"b"));assertNotNull(view.findViewWithTag("milepost_replay"));assertEquals(1,store.read().cursor);
        view.replay(false);view.skip();assertEquals(4,store.read().cursor);assertEquals(4,store.read().acknowledgedPosts);
        view.update(snapshot(5,1,"c"));assertNotNull(view.findViewWithTag("milepost_replay"));assertEquals(4,store.read().cursor);
        assertEquals(1,snapshot(5,1,"c").individuals());
    }
    @Test public void withdrawalsClampVisibleProgressWithoutErasingReplayHistoryAndReinstatementRestores(){
        host(snapshot(7,7,"a"));view.replay(true);for(int i=0;i<7;i++)view.next();assertEquals(7,store.read().cursor);
        List<MilepostPresentation.Award> withdrawn=new ArrayList<>();for(int i=0;i<7;i++)withdrawn.add(new MilepostPresentation.Award(false,i+1,null,"unknown"));
        view.update(new MilepostPresentation.Snapshot(true,2,0,0,0,2,0,"withdrawn",null,null,withdrawn));
        assertEquals(2,view.displayStage());assertEquals(7,store.read().cursor);assertFalse(contents(view).contains("Charlie Harrow"));
        view.update(snapshot(7,7,"restored"));assertEquals(7,view.displayStage());assertTrue(contents(view).contains("Charlie Harrow"));
    }
    @Test public void staleReceiptCannotOpenRevealOrAcknowledgeSummary(){
        MilepostPresentation.Snapshot s=snapshot(7,7,"a");host(new MilepostPresentation.Snapshot(false,7,0,1,0,7,7,"stale",null,null,s.awards));
        view.replay(true);assertFalse(store.read().replaying);assertEquals(0,store.read().acknowledgedPosts);
        store.acknowledge(store.read(),new MilepostPresentation.Snapshot(false,7,0,1,0,7,7,"stale",null,null,s.awards));assertEquals(0,store.read().acknowledgedPosts);
    }
    @Test public void nativeControlsAndLargeTextHaveNoFixedTextHeightOrMotionDependency(){
        android.content.res.Configuration config=context.getResources().getConfiguration();config.fontScale=2f;
        context.getResources().updateConfiguration(config,context.getResources().getDisplayMetrics());
        host(snapshot(7,7,"a"));view.replay(true);View next=view.findViewWithTag("milepost_next");assertNotNull(next);
        assertTrue(((Button)next).getMinHeight()>=48*context.getResources().getDisplayMetrics().density);
        assertEquals(-2,next.getLayoutParams().height);assertTrue(store.read().staticMode);
        view.measure(View.MeasureSpec.makeMeasureSpec(320,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(640,View.MeasureSpec.EXACTLY));view.layout(0,0,320,640);
        assertTrue(((android.view.ViewGroup)view.getChildAt(0)).getMeasuredHeight()>640);
        for(int i=0;i<7;i++)view.next();assertTrue(contents(view).contains("Charlie Harrow"));
        assertTrue(((TextView)view.findViewWithTag("milepost_reveal_progress")).getText().toString().contains("7 of 7"));
    }
    @Test public void discoveryBrowsingAcknowledgesOnlyDeliveredDetailsAndKeepsAnonymousPostsAnonymous(){
        host(snapshot(1,0,"unknown"));
        view.findViewWithTag("milepost_browse_discoveries").performClick();
        assertTrue(contents(view).contains("Discovery record"));assertEquals(1,store.read().acknowledgedPosts);
        assertNotNull(view.findViewWithTag("milepost_replay"));assertNotNull(view.findViewWithTag("milepost_browse_discoveries"));
    }
    @Test public void replayDoesNotChangeAnyExistingEntitlementOrCelebrationPreferences(){
        String awards=context.getSharedPreferences("roadprints_achievements_v1",0).getAll().toString();
        String celebrations=context.getSharedPreferences("roadprints_achievement_celebrations_v1",0).getAll().toString();
        host(snapshot(7,7,"a"));view.replay(true);for(int i=0;i<7;i++)view.next();view.skip();view.replay(true);view.next();view.later();
        assertEquals(awards,context.getSharedPreferences("roadprints_achievements_v1",0).getAll().toString());
        assertEquals(celebrations,context.getSharedPreferences("roadprints_achievement_celebrations_v1",0).getAll().toString());
    }
    @Test public void nextRetainsCardsAndRevealControlsInsteadOfRebuildingScreen(){
        host(snapshot(7,7,"a"));view.replay(true);
        View card=view.findViewWithTag("milepost_card_0"),next=view.findViewWithTag("milepost_next");
        View progress=view.findViewWithTag("milepost_reveal_progress");assertTrue(next.hasFocus());
        view.next();assertSame(card,view.findViewWithTag("milepost_card_0"));assertSame(next,view.findViewWithTag("milepost_next"));assertSame(progress,view.findViewWithTag("milepost_reveal_progress"));
    }
    static long heap(){Runtime r=Runtime.getRuntime();return r.totalMemory()-r.freeMemory();}
    void fullReplay(){view.replay(true);for(int n=0;n<7;n++)view.next();view.skip();}
    @Test public void warmedNativePresentationMeasuresIncrementalHeap()throws Exception{
        host(snapshot(7,7,"a"));for(int i=0;i<2;i++)fullReplay();
        System.gc();Thread.sleep(50);long before=heap();java.util.concurrent.atomic.AtomicLong peak=new java.util.concurrent.atomic.AtomicLong(before);
        java.util.concurrent.atomic.AtomicBoolean stop=new java.util.concurrent.atomic.AtomicBoolean();
        Thread sampler=new Thread(()->{while(!stop.get()){peak.accumulateAndGet(heap(),Math::max);try{Thread.sleep(2);}catch(InterruptedException e){return;}}});sampler.start();
        long started=System.nanoTime();try{for(int i=0;i<3;i++)fullReplay();}finally{stop.set(true);sampler.join();}
        long elapsed=(System.nanoTime()-started)/1000000;System.gc();Thread.sleep(50);long retained=heap();
        System.out.println("MILEPOST_PRESENTATION_VIEW_PROBE cycles=3 stages_per_cycle=8 before="+before+" peak="+peak.get()+" incremental_peak="+(peak.get()-before)+" retained="+retained+" retained_delta="+(retained-before)+" elapsed_ms="+elapsed+" decoded_bitmaps=0");
        assertTrue("Native presentation peak exceeds 32MiB",peak.get()-before<32L*1024*1024);
        assertTrue("Native presentation retained growth exceeds 8MiB",retained-before<8L*1024*1024);
    }

}
