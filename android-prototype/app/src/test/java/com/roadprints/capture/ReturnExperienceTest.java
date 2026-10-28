package com.roadprints.capture;

import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class ReturnExperienceTest {
    private Context app;
    private java.util.concurrent.Executor previousPreparer;
    private final List<Runnable> replayJobs=new ArrayList<>();
    @Before public void deferReplayPreparation() throws Exception {
        java.lang.reflect.Field field=DiscoveryReplayCache.class.getDeclaredField("preparer");field.setAccessible(true);
        previousPreparer=(java.util.concurrent.Executor)field.get(null);
        field.set(null,(java.util.concurrent.Executor)replayJobs::add);
    }
    @After public void restoreReplayPreparation() throws Exception {
        java.lang.reflect.Field field=DiscoveryReplayCache.class.getDeclaredField("preparer");field.setAccessible(true);field.set(null,previousPreparer);
    }
    @Before public void setup() { app=RuntimeEnvironment.getApplication(); JourneyStore.deleteAll(app);
        Shadows.shadowOf((android.app.Application)app).grantPermissions("com.roadprints.capture.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"); }
    @After public void cleanup() { JourneyStore.deleteAll(app); }
    private JSONObject row(String id, String source, long ended) throws Exception {
        return new JSONObject().put("journey_id",id).put("source",new JSONObject().put("type",source))
                .put("started_at",java.time.Instant.ofEpochMilli(ended-1000).toString())
                .put("ended_at",java.time.Instant.ofEpochMilli(ended).toString());
    }
    @Test public void upgradeEstablishesBaselineAndOnlyNewRecordingsEnterRecap() throws Exception {
        JSONObject old=row("old","android_activity_capture",100), imported=row("import","timeline_import",300);
        assertTrue(ReturnRecapStore.collect(app,Arrays.asList(old),200).isEmpty());
        JSONObject fresh=row("new","android_activity_capture",300);
        List<JSONObject> recap=ReturnRecapStore.collect(app,Arrays.asList(old,imported,fresh),400);
        assertEquals(1,recap.size());assertEquals("new",recap.get(0).getString("journey_id"));
    }
    @Test public void pendingRecapSurvivesMatchingAndDismissalDoesNotReannounce() throws Exception {
        ReturnRecapStore.collect(app,Collections.emptyList(),200);
        JSONObject fresh=row("new","android_activity_capture",300).put("processing_status","pending");
        ReturnRecapStore.collect(app,Arrays.asList(fresh),400);
        fresh.put("processing_status","complete");
        assertEquals(1,ReturnRecapStore.collect(app,Arrays.asList(fresh),500).size());
        ReturnRecapStore.dismiss(app,new HashSet<>(Arrays.asList("new")));
        assertTrue(ReturnRecapStore.collect(app,Arrays.asList(fresh),600).isEmpty());
        JourneyStore.deleteAll(app);
        assertFalse(app.getSharedPreferences(ReturnRecapStore.PREFS,Context.MODE_PRIVATE).contains("visited_at"));
    }
    @Test public void malformedFutureAndDeletedRowsDoNotRemainInRecap() throws Exception {
        ReturnRecapStore.collect(app,Collections.emptyList(),200);
        JSONObject bad=row("bad","android_activity_capture",300).put("ended_at","broken");
        assertTrue(ReturnRecapStore.collect(app,Arrays.asList(bad,row("future","android_activity_capture",900)),400).isEmpty());
        ReturnRecapStore.collect(app,Arrays.asList(row("new","android_activity_capture",500)),600);
        assertTrue(ReturnRecapStore.collect(app,Collections.emptyList(),700).isEmpty());
    }
    @Test public void returningLauncherRoutesToMapAndTrackingSettingsStillOpens() {
        org.robolectric.android.controller.ActivityController<MainActivity> home=Robolectric.buildActivity(MainActivity.class).create();
        Intent next=Shadows.shadowOf(home.get()).getNextStartedActivity();
        assertEquals(MapActivity.class.getName(),next.getComponent().getClassName());
        home.destroy();
        org.robolectric.android.controller.ActivityController<MainActivity> settings=Robolectric.buildActivity(MainActivity.class,
                new Intent(app,MainActivity.class).putExtra("tracking_settings_screen",true)).create();
        assertNull(Shadows.shadowOf(settings.get()).getNextStartedActivity());settings.destroy();
    }
    private boolean containsMap(View view) {
        if(view instanceof RoutePreviewView)return true;
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)
            if(containsMap(((ViewGroup)view).getChildAt(i)))return true;
        return false;
    }
    @Test public void mapExistsBeforeAnyArchiveLoadingOrActivityResume() {
        org.robolectric.android.controller.ActivityController<MapActivity> map=Robolectric.buildActivity(MapActivity.class).create();
        assertTrue(containsMap(map.get().getWindow().getDecorView()));map.destroy();
    }
    @Test public void canonicalReplaySuppressesRepeatedSectionsButIncludesNewStretch() throws Exception {
        JSONArray anchors=new JSONArray();
        for(int i=0;i<8;i++)anchors.put(new JSONArray().put(-.25-i*.003).put(51.7));
        JSONObject cache=new JSONObject().put("roads",new JSONObject().put("M25",new JSONObject().put("anchors",anchors)));
        JSONObject old=motorway("old",new JSONArray().put(anchors.get(0)).put(anchors.get(1)));
        MotorwayProgressCalculator baseline=new MotorwayProgressCalculator(app,cache,false,false);baseline.addJourney(old);
        Map<String,Set<Integer>> previous=new HashMap<>();
        previous.put("M25",new HashSet<>(baseline.finish().roads.get(0).coveredSections));
        MotorwayProgressCalculator repeat=new MotorwayProgressCalculator(app,cache,false,false);repeat.addJourney(old);
        assertTrue(repeat.newlyCoveredSections(previous).isEmpty());repeat.finish();
        MotorwayProgressCalculator fresh=new MotorwayProgressCalculator(app,cache,false,false);
        fresh.addJourney(motorway("new",new JSONArray().put(anchors.get(5)).put(anchors.get(6))));
        assertFalse(fresh.newlyCoveredSections(previous).get("M25").isEmpty());fresh.finish();
    }
    @Test public void replayKeepsMatchedGapsSeparateAndDoesNotCelebrateNamedRoadTwice() throws Exception {
        JSONObject old = row("older", "android_activity_capture", 2000);
        JSONObject fresh = row("fresh", "android_activity_capture", 4000);
        JSONArray first = new JSONArray("[[-0.1,51.5],[-0.11,51.51]]");
        JSONArray second = new JSONArray("[[-0.2,51.6],[-0.21,51.61]]");
        JSONObject known = roadFeature("Known Road", first), added = roadFeature("Fresh Road", second);
        finishJourney(old, new JSONArray().put(known), new JSONArray().put(known));
        finishJourney(fresh, new JSONArray().put(known).put(added), new JSONArray().put(known).put(added));
        JourneyStore.save(app,old); JourneyStore.save(app,fresh);
        DiscoveryReplay replay = DiscoveryReplay.calculate(app,new HashSet<>(Arrays.asList("fresh")));
        assertEquals(2,replay.routes.size());
        assertEquals(new HashSet<>(Arrays.asList("Fresh Road")),replay.labels);
        assertEquals(1,replay.discoveries.size());
        assertEquals(second.toString(),replay.discoveries.get(0).points.toString());
    }
    @Test public void removedRoadEvidenceIsExcludedFromDiscoveryReplay() throws Exception {
        JSONObject journey = row("removed", "android_activity_capture", 4000);
        JSONObject road = roadFeature("Removed Road",new JSONArray("[[-0.1,51.5],[-0.11,51.51]]"));
        finishJourney(journey,new JSONArray().put(road),new JSONArray().put(road));
        journey.put("journey_corrections",new JSONObject().put("removed_road_ids",new JSONArray().put("name:removed road")));
        JourneyStore.save(app,journey);
        assertTrue(DiscoveryReplay.calculate(app,new HashSet<>(Arrays.asList("removed"))).labels.isEmpty());
    }
    private JSONObject roadFeature(String name,JSONArray coordinates) throws Exception {
        return new JSONObject().put("properties",new JSONObject().put("name",name))
                .put("geometry",new JSONObject().put("type","LineString").put("coordinates",coordinates));
    }
    private void finishJourney(JSONObject journey,JSONArray roads,JSONArray routes) throws Exception {
        journey.put("mode","walking").put("processing_status","complete")
                .put("route_geometry",new JSONObject().put("type","LineString")
                        .put("coordinates",routes.getJSONObject(0).getJSONObject("geometry").getJSONArray("coordinates")))
                .put("processing_result",new JSONObject().put("road_geojson",new JSONObject().put("features",roads))
                        .put("geojson",new JSONObject().put("features",routes)));
    }
    @Test public void finishingReplayRetainsJourneyCameraAndOverlayUntilExplicitStop() throws Exception {
        RoutePreviewView view = RoutePreviewView.overview(app);
        view.layout(0,0,600,800);
        DiscoveryReplay replay = new DiscoveryReplay();
        replay.routes.add(new DiscoveryReplay.Section(new JSONArray("[[-0.1,51.5],[-0.11,51.51]]"),0xFF101820));
        int[] completed = {0};
        view.startDiscoveryReplay(replay, () -> completed[0]++);
        double[] framed = view.cameraState();
        view.finishDiscoveryReplay();
        assertArrayEquals(framed,view.cameraState(),0.00001);
        java.lang.reflect.Field retained = RoutePreviewView.class.getDeclaredField("discoveryReplay");
        retained.setAccessible(true);assertSame(replay,retained.get(view));
        java.lang.reflect.Field fraction = RoutePreviewView.class.getDeclaredField("discoveryFraction");
        fraction.setAccessible(true);assertEquals(1f,fraction.getFloat(view),0f);
        view.finishDiscoveryReplay();assertEquals(1,completed[0]);
        view.stopDiscoveryReplay();assertNull(retained.get(view));
    }

    @Test public void replayEvidenceSkipsGpsAndKeepsCorrections() throws Exception {
        JSONObject journey=row("streamed","android_activity_capture",4000);
        JSONObject road=roadFeature("Stream Road",new JSONArray("[[-0.1,51.5],[-0.11,51.51]]"));
        finishJourney(journey,new JSONArray().put(road),new JSONArray().put(road));
        journey.put("raw_capture_samples",new JSONArray().put(new JSONObject().put("large","ignored")));
        journey.put("journey_corrections",new JSONObject().put("removed_road_ids",new JSONArray().put("name:stream road")));
        JourneyStore.save(app,journey);
        List<JSONObject> evidence=new ArrayList<>();
        JourneyStore.forEachRoadEvidence(app,()->false,evidence::add);
        assertEquals(1,evidence.size());assertFalse(evidence.get(0).has("raw_capture_samples"));
        assertFalse(evidence.get(0).has("route_geometry"));
        assertFalse(evidence.get(0).getJSONObject("processing_result").has("geojson"));
        assertTrue(DiscoveryReplay.localFeatures(evidence.get(0)).isEmpty());
        try { JourneyStore.forEachRoadEvidence(app,()->true,evidence::add);fail("Expected cancellation"); }
        catch(java.util.concurrent.CancellationException expected) { }
    }
    @Test public void surroundingContextDoesNotChangeFinishedJourneyCamera() throws Exception {
        RoutePreviewView view=RoutePreviewView.overview(app);view.layout(0,0,600,800);
        DiscoveryReplay replay=new DiscoveryReplay();
        replay.routes.add(new DiscoveryReplay.Section(new JSONArray("[[-0.1,51.5],[-0.11,51.51]]"),0xFF101820));
        view.startDiscoveryReplay(replay,()->{});view.finishDiscoveryReplay();double[] camera=view.cameraState();
        view.setReplayContext(Arrays.asList(new JSONArray("[[-4,55],[-3,56]]")),Collections.emptyList(),Collections.emptyList());
        assertArrayEquals(camera,view.cameraState(),.00001);
    }

    @Test public void cardReplayStartsWhileDiscoveryPreparationIsBlocked() throws Exception {
        JSONObject journey=row("instant","android_activity_capture",4000);
        JSONObject road=roadFeature("Instant Road",new JSONArray("[[-0.1,51.5],[-0.11,51.51]]"));
        finishJourney(journey,new JSONArray().put(road),new JSONArray().put(road));JourneyStore.save(app,journey);
        Intent intent=JourneyReplayActivity.replayIntent(app,journey);
        org.robolectric.android.controller.ActivityController<JourneyReplayActivity> screen=
                Robolectric.buildActivity(JourneyReplayActivity.class,intent).create();
        java.lang.reflect.Field field=JourneyReplayActivity.class.getDeclaredField("action");field.setAccessible(true);
        android.widget.TextView action=(android.widget.TextView)field.get(screen.get());
        assertTrue(action.isEnabled());assertFalse(action.getText().toString().contains("PREPARING"));
        field=JourneyReplayActivity.class.getDeclaredField("prepared");field.setAccessible(true);
        assertEquals(1,((DiscoveryReplay)field.get(screen.get())).routes.size());
        assertEquals(1,replayJobs.size());
        field=JourneyReplayActivity.class.getDeclaredField("status");field.setAccessible(true);
        assertFalse(((android.widget.TextView)field.get(screen.get())).getText().toString().contains("no new"));
        screen.destroy();
    }
    @Test public void replayCacheCoalescesPreparationPersistsAndInvalidatesAfterEdits() throws Exception {
        JSONObject journey=row("cached","android_activity_capture",4000);
        JSONObject road=roadFeature("Cached Road",new JSONArray("[[-0.1,51.5],[-0.11,51.51]]"));
        finishJourney(journey,new JSONArray().put(road),new JSONArray().put(road));JourneyStore.save(app,journey);
        Set<String> ids=Collections.singleton("cached");List<DiscoveryReplay> answers=new ArrayList<>();
        DiscoveryReplayCache.request(app,ids,answers::add);DiscoveryReplayCache.request(app,ids,answers::add);
        assertEquals(1,replayJobs.size());assertNull(DiscoveryReplayCache.peek(app,ids));
        replayJobs.remove(0).run();assertEquals(2,answers.size());assertSame(answers.get(0),answers.get(1));
        DiscoveryReplay result=DiscoveryReplayCache.peek(app,ids);assertNotNull(result);
        assertEquals(Collections.singleton("Cached Road"),result.labels);
        assertEquals(result.toJson().toString(),DiscoveryReplay.fromJson(result.toJson()).toJson().toString());
        java.io.File[] files=new java.io.File(app.getFilesDir(),"screen-cache").listFiles((dir,name)->name.startsWith("journey-replay-"));
        assertNotNull(files);assertEquals(1,files.length);
        java.lang.reflect.Field memory=DiscoveryReplayCache.class.getDeclaredField("READY");memory.setAccessible(true);
        ((Map<?,?>)memory.get(null)).clear();
        DiscoveryReplayCache.request(app,ids,answers::add);replayJobs.remove(0).run();
        assertEquals(Collections.singleton("Cached Road"),DiscoveryReplayCache.peek(app,ids).labels);
        journey.put("journey_corrections",new JSONObject().put("removed_road_ids",new JSONArray().put("name:cached road")));
        JourneyStore.save(app,journey);assertNull(DiscoveryReplayCache.peek(app,ids));
        DiscoveryReplayCache.request(app,ids,answers::add);replayJobs.remove(0).run();
        assertTrue(DiscoveryReplayCache.peek(app,ids).labels.isEmpty());
    }
    @Test public void deletingArchiveCancelsQueuedReplayAndRemovesDerivedData() throws Exception {
        JSONObject journey=row("deleted","android_activity_capture",4000);
        JSONObject road=roadFeature("Deleted Road",new JSONArray("[[-0.1,51.5],[-0.11,51.51]]"));
        finishJourney(journey,new JSONArray().put(road),new JSONArray().put(road));JourneyStore.save(app,journey);
        Set<String> ids=Collections.singleton("deleted");DiscoveryReplayCache.request(app,ids,null);
        JourneyStore.deleteAll(app);replayJobs.remove(0).run();
        assertNull(DiscoveryReplayCache.peek(app,ids));
        java.io.File[] files=new java.io.File(app.getFilesDir(),"screen-cache").listFiles((dir,name)->name.startsWith("journey-replay-"));
        assertTrue(files==null||files.length==0);
    }
    @Test public void lateDiscoveryHighlightsDoNotRestartFinishedReplayOrMoveCamera() throws Exception {
        RoutePreviewView map=RoutePreviewView.overview(app);map.layout(0,0,600,800);
        DiscoveryReplay initial=new DiscoveryReplay();
        initial.routes.add(new DiscoveryReplay.Section(new JSONArray("[[-0.1,51.5],[-0.11,51.51]]"),0xFF101820));
        int[] complete={0};map.startDiscoveryReplay(initial,()->complete[0]++);map.finishDiscoveryReplay();
        double[] camera=map.cameraState();DiscoveryReplay enriched=new DiscoveryReplay();enriched.routes.addAll(initial.routes);
        enriched.discoveries.add(new DiscoveryReplay.Section(initial.routes.get(0).points,0xFF008755));
        map.updateDiscoveryReplay(enriched);
        assertArrayEquals(camera,map.cameraState(),.00001);assertEquals(1,complete[0]);
        java.lang.reflect.Field fraction=RoutePreviewView.class.getDeclaredField("discoveryFraction");fraction.setAccessible(true);
        assertEquals(1f,fraction.getFloat(map),0f);
        java.lang.reflect.Field replay=RoutePreviewView.class.getDeclaredField("discoveryReplay");replay.setAccessible(true);assertSame(enriched,replay.get(map));
    }

    @Test public void splitNamedRoadHighlightsAllFeaturesAndSeparateMultilineParts() throws Exception {
        JSONObject journey=row("split-road","android_activity_capture",4000);
        JSONArray first=new JSONArray("[[-0.1,51.5],[-0.11,51.51]]");
        JSONArray second=new JSONArray("[[-0.11,51.51],[-0.12,51.52],[-0.13,51.53]]");
        JSONArray third=new JSONArray("[[-0.2,51.6],[-0.21,51.61]]");
        JSONArray fourth=new JSONArray("[[-0.3,51.7],[-0.31,51.71]]");
        JSONObject multi=roadFeature("MISKIN WAY",third);
        multi.put("geometry",new JSONObject().put("type","MultiLineString")
                .put("coordinates",new JSONArray().put(third).put(fourth)));
        JSONArray features=new JSONArray().put(roadFeature("Miskin Way",first))
                .put(roadFeature("  Miskin   Way  ",second)).put(multi);
        finishJourney(journey,features,new JSONArray().put(roadFeature("Miskin Way",first)));
        JourneyStore.save(app,journey);
        DiscoveryReplay replay=DiscoveryReplay.calculate(app,Collections.singleton("split-road"));
        assertEquals(Collections.singleton("Miskin Way"),replay.labels);
        assertEquals(4,replay.discoveries.size());
        assertEquals(first.toString(),replay.discoveries.get(0).points.toString());
        assertEquals(second.toString(),replay.discoveries.get(1).points.toString());
        assertEquals(third.toString(),replay.discoveries.get(2).points.toString());
        assertEquals(fourth.toString(),replay.discoveries.get(3).points.toString());
    }
    @Test public void newRoadAcrossSelectedJourneysKeepsEveryPieceAndIsAnnouncedOnce() throws Exception {
        JSONObject a=row("piece-a","android_activity_capture",4000),b=row("piece-b","android_activity_capture",6000);
        JSONObject first=roadFeature("Miskin Way",new JSONArray("[[-0.1,51.5],[-0.11,51.51]]"));
        JSONObject second=roadFeature("Miskin Way",new JSONArray("[[-0.11,51.51],[-0.12,51.52]]"));
        finishJourney(a,new JSONArray().put(first),new JSONArray().put(first));
        finishJourney(b,new JSONArray().put(second),new JSONArray().put(second));
        JourneyStore.save(app,a);JourneyStore.save(app,b);
        Set<String> ids=new HashSet<>(Arrays.asList("piece-a","piece-b"));
        DiscoveryReplay replay=DiscoveryReplay.calculate(app,ids);
        assertEquals(Collections.singleton("Miskin Way"),replay.labels);assertEquals(2,replay.discoveries.size());
        JSONObject old=row("previous-discovery","android_activity_capture",2000);
        finishJourney(old,new JSONArray().put(first),new JSONArray().put(first));JourneyStore.save(app,old);
        replay=DiscoveryReplay.calculate(app,ids);assertTrue(replay.labels.isEmpty());assertTrue(replay.discoveries.isEmpty());
    }
    @Test public void removedSplitRoadIsExcludedAndOldIncompleteHighlightCachesAreRejected() throws Exception {
        JSONObject journey=row("split-removed","android_activity_capture",4000);
        JSONObject first=roadFeature("Miskin Way",new JSONArray("[[-0.1,51.5],[-0.11,51.51]]"));
        JSONObject second=roadFeature("Miskin Way",new JSONArray("[[-0.11,51.51],[-0.12,51.52]]"));
        finishJourney(journey,new JSONArray().put(first).put(second),new JSONArray().put(first).put(second));
        journey.put("journey_corrections",new JSONObject().put("removed_road_ids",new JSONArray().put("name:miskin way")));
        JourneyStore.save(app,journey);
        DiscoveryReplay replay=DiscoveryReplay.calculate(app,Collections.singleton("split-removed"));
        assertTrue(replay.labels.isEmpty());assertTrue(replay.discoveries.isEmpty());
        assertNull(DiscoveryReplay.fromJson(replay.toJson().put("format",1)));
    }

    @Test public void verifiedCachedDiscoveryExpandsWithoutScanningEarlierJourneys() throws Exception {
        JSONObject journey=row("upgrade-split","android_activity_capture",4000);
        JSONArray first=new JSONArray("[[-0.1,51.5],[-0.11,51.51]]");
        JSONArray second=new JSONArray("[[-0.11,51.51],[-0.12,51.52]]");
        JSONObject a=roadFeature("Miskin Way",first),b=roadFeature("Miskin Way",second);
        finishJourney(journey,new JSONArray().put(a).put(b),new JSONArray().put(a).put(b));JourneyStore.save(app,journey);
        // An unrelated corrupt archive entry proves migration uses only the selected match.
        java.nio.file.Files.write(new java.io.File(app.getFilesDir(),"journey_unrelated.json").toPath(),"invalid".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        DiscoveryReplay old=DiscoveryReplay.fromJourney(journey);old.labels.add("Miskin Way");
        old.discoveries.add(new DiscoveryReplay.Section(first,0xFF101820));
        DiscoveryReplay updated=DiscoveryReplay.upgradeCached(app,Collections.singleton("upgrade-split"),old.toJson().put("format",1));
        assertNotNull(updated);assertEquals(Collections.singleton("Miskin Way"),updated.labels);
        assertEquals(2,updated.discoveries.size());assertEquals(second.toString(),updated.discoveries.get(1).points.toString());
    }

    private JSONObject motorway(String id,JSONArray coordinates) throws Exception {
        JSONObject feature=new JSONObject().put("properties",new JSONObject().put("road_ref","M25"))
                .put("geometry",new JSONObject().put("type","LineString").put("coordinates",coordinates));
        return new JSONObject().put("journey_id",id).put("processing_status","complete").put("mode","driving")
                .put("processing_result",new JSONObject().put("motorway_geojson",new JSONObject().put("features",new JSONArray().put(feature))));
    }
}
