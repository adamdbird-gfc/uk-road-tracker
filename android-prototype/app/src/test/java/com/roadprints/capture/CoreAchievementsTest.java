package com.roadprints.capture;

import android.content.Context;
import java.util.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class CoreAchievementsTest {
    private Context app;
    @Before public void setup(){app=RuntimeEnvironment.getApplication();JourneyStore.deleteAll(app);
        app.getSharedPreferences("roadprints_achievements_v1",0).edit().clear().commit();DistanceUnits.setKilometres(app,false);}
    private JSONObject journey(String id,String mode,double metres) throws Exception {
        return new JSONObject().put("journey_id",id).put("mode",mode).put("distance_meters",metres)
                .put("source",new JSONObject().put("type","timeline_import"))
                .put("capture_quality",new JSONObject().put("gps_points",2).put("source_route_points",2));
    }
    private JSONObject line(double... longs) throws Exception {
        JSONArray points=new JSONArray();for(double longitude:longs)points.put(new JSONArray().put(longitude).put(51.5));
        return new JSONObject().put("type","LineString").put("coordinates",points);
    }
    private JSONObject collection(JSONObject geometry) throws Exception {
        return new JSONObject().put("features",new JSONArray().put(new JSONObject().put("geometry",geometry)));
    }
    private AchievementStore.Definition definition(String id){return AchievementStore.definitions().stream().filter(d->id.equals(d.id)).findFirst().get();}
    private AchievementStore.Progress progress(AchievementStore.Snapshot s,String id){return s.achievements.stream().filter(p->id.equals(p.definition.id)).findFirst().get();}
    private AchievementStore.Snapshot evaluate(String id,double value){return AchievementStore.evaluate(app,Collections.singletonMap(id,value),Collections.emptyMap(),new boolean[7],0);}

    @Test public void totalsIncludeImportsBusAndFailedMatchesButExcludeRail() throws Exception {
        CoreAchievementEvidence e=new CoreAchievementEvidence();
        e.add(journey("a","driving",100));e.add(journey("b","bus",50));e.add(journey("c","running",20).put("processing_status","failed"));
        e.add(journey("d","train",500));e.add(journey("e","walking",30));
        assertEquals(150,e.roadMetres,0);assertEquals(50,e.footMetres,0);assertEquals(100,e.longestRoad,0);assertEquals(0,e.roadCoverage.metres(),0);
    }
    @Test public void repeatAndReverseRoutesAddMileageWithoutNewCoverage() throws Exception {
        CoreAchievementEvidence e=new CoreAchievementEvidence();
        e.add(journey("a","driving",100).put("processing_status","complete").put("processing_result",new JSONObject().put("geojson",collection(line(0,.001)))));
        double unique=e.roadCoverage.metres();
        e.add(journey("b","bus",100).put("processing_status","complete").put("processing_result",new JSONObject().put("geojson",collection(line(.001,0)))));
        assertEquals(200,e.roadMetres,0);assertEquals(unique,e.roadCoverage.metres(),.01);
    }
    @Test public void splitsAndPartialOverlapsCountOnlyUnion() throws Exception {
        CoreAchievementEvidence.Coverage e=new CoreAchievementEvidence.Coverage();e.add(collection(line(0,.002)),null);double length=e.metres();
        e.add(collection(line(0,.001,.002)),null);assertEquals(length,e.metres(),.01);
        e.add(collection(line(.001,.003)),null);assertEquals(length*1.5,e.metres(),.01);
    }
    @Test public void removedSectionsAreExcludedAcrossMultipleLines() throws Exception {
        JSONObject multi=new JSONObject().put("type","MultiLineString").put("coordinates",new JSONArray().put(line(0,.001).getJSONArray("coordinates")).put(line(.001,.002).getJSONArray("coordinates")));
        CoreAchievementEvidence.Coverage all=new CoreAchievementEvidence.Coverage(),part=new CoreAchievementEvidence.Coverage();
        all.add(collection(multi),null);part.add(collection(multi),new JSONArray().put(1));assertEquals(all.metres()/2,part.metres(),.01);
    }
    @Test public void separateModeCoverageDoesNotCrossCredit() throws Exception {
        CoreAchievementEvidence e=new CoreAchievementEvidence();e.add(journey("a","walking",100).put("processing_status","complete").put("processing_result",new JSONObject().put("geojson",collection(line(0,.001)))));
        assertTrue(e.footCoverage.metres()>0);assertEquals(0,e.roadCoverage.metres(),0);
    }
    @Test public void everyDistanceAndRoadCountBoundaryIsExact() {
        for(String id:new String[]{"foot-total","road-total","foot-unique","road-unique","the-knowledge","completion-motorway-M1","completion-aroad-GB:A2"}) {
            AchievementStore.Definition d=definition(id);
            for(int i=0;i<d.levels.length;i++){assertEquals(i,AchievementStore.levelFor(d,d.levels[i]-.000001));assertEquals(i+1,AchievementStore.levelFor(d,d.levels[i]));}
        }
    }
    @Test public void downgradesPreserveHistoryAndDoNotCelebrateAgain() {
        AchievementStore.Snapshot initial=evaluate("foot-total",100);
        assertEquals(3,progress(initial,"foot-total").level);assertEquals(1,initial.newlyUnlocked.size());
        AchievementStore.Snapshot lower=evaluate("foot-total",.5);assertFalse(progress(lower,"foot-total").unlocked);assertEquals(3,progress(lower,"foot-total").milestones.size());
        assertTrue(evaluate("foot-total",100).newlyUnlocked.isEmpty());
        assertEquals(1,evaluate("foot-total",500).newlyUnlocked.size());
    }
    @Test public void importedCaptureDoesNotUnlockSatNavButActualCaptureSurvivesDeletion() throws Exception {
        AchievementStore.recordCapture(app,journey("import","driving",100).put("ended_at","2026-10-06T10:00:00Z"));
        assertFalse(app.getSharedPreferences("roadprints_achievements_v1",0).getBoolean("capture_used",false));
        JourneyStore.save(app,journey("actual","driving",100).put("ended_at","2026-10-06T10:00:00Z").put("source",new JSONObject().put("type","android_activity_capture")));
        JourneyStore.delete(app,"actual");assertTrue(app.getSharedPreferences("roadprints_achievements_v1",0).getBoolean("capture_used",false));
    }
    @Test public void editsCountDistinctJourneysAndTraceIsPermanent() {
        for(int i=0;i<10;i++)AchievementStore.recordSavedEdit(app,"same",false);
        assertEquals(1,app.getSharedPreferences("roadprints_achievements_v1",0).getStringSet("edited_journeys",Collections.emptySet()).size());
        for(int i=0;i<4;i++)AchievementStore.recordSavedEdit(app,"extra"+i,false);
        AchievementStore.recordSavedEdit(app,"same",true);
        assertEquals(5,app.getSharedPreferences("roadprints_achievements_v1",0).getStringSet("edited_journeys",Collections.emptySet()).size());
        assertTrue(app.getSharedPreferences("roadprints_achievements_v1",0).getBoolean("trace_used",false));
        assertTrue(progress(evaluate("picasso",5),"picasso").unlocked);
        assertTrue(progress(evaluate("picasso",0),"picasso").unlocked);
    }
    @Test public void unitsChangePresentationWithoutChangingLevel() {
        AchievementStore.Progress p=progress(evaluate("foot-total",25),"foot-total");
        assertTrue(AchievementStore.progressText(app,p).contains("25.0 mi"));
        DistanceUnits.setKilometres(app,true);assertTrue(AchievementStore.progressText(app,p).contains("40.2 km"));assertEquals(2,p.level);
    }
    @Test public void roadNamesUseDistinctTownsAndCurrentGeometryOnly() throws Exception {
        CoreAchievementEvidence e=new CoreAchievementEvidence();JSONObject g=line(0,.001);
        JSONObject feature=new JSONObject().put("geometry",g).put("properties",new JSONObject().put("name","Station Road").put("highway","residential"));
        e.add(journey("a","walking",100).put("processing_status","complete").put("processing_result",new JSONObject().put("road_geojson",new JSONObject().put("features",new JSONArray().put(feature)))));
        AchievementStore.recordRoadSettlements(app,"name:station road",Collections.singletonList(g),Arrays.asList(new LocalRoadSettlementMatcher.Settlement("a","Town A"),new LocalRoadSettlementMatcher.Settlement("b","Town B")));
        assertEquals(2,e.roadLists(app).get("mastered-monopoly").size());
        assertTrue(AchievementStore.roadSettlements(app,"name:station road",Collections.singletonList(line(1,1.001))).isEmpty());
        e.add(journey("b","bus",100).put("processing_status","complete").put("processing_result",new JSONObject().put("road_geojson",new JSONObject().put("features",new JSONArray().put(feature)))));
        assertEquals(2,e.roadLists(app).get("mastered-monopoly").size());
    }
    @Test public void geometryEvidenceIdentityIgnoresOrderAndDuplicateFragments() throws Exception {
        JSONObject a=line(0,.001),b=line(.001,.002);
        assertEquals(AchievementStore.roadEvidenceKey("name:high street",Arrays.asList(a,b)),AchievementStore.roadEvidenceKey("name:high street",Arrays.asList(b,a,a)));
    }
    @Test public void oldUnlockMigrationDoesNotCelebrateExistingBadge() throws Exception {
        app.getSharedPreferences("roadprints_achievements_v1",0).edit().putString("unlocked",new JSONObject().put("mary-high-streets",new JSONObject().put("unlocked_at",1000)).toString()).commit();
        assertTrue(evaluate("mary-high-streets",10).newlyUnlocked.isEmpty());
    }
    @Test public void projectedScanExcludesRawCaptureButIncludesMilestoneEvidence() throws Exception {
        JourneyStore.save(app,journey("one","walking",42).put("ended_at","2026-10-06T10:00:00Z").put("raw_capture_samples",new JSONArray().put("unused")));
        List<JSONObject> read=new ArrayList<>();JourneyStore.forEachAchievementEvidence(app,()->false,read::add);
        assertEquals(1,read.size());assertEquals(42,read.get(0).getDouble("distance_meters"),0);assertFalse(read.get(0).has("raw_capture_samples"));assertTrue(read.get(0).has("source"));
    }
    @Test public void fullCalculationUsesExistingJourneysThenDowngradesAfterDeletion() throws Exception {
        JourneyStore.save(app,journey("one","walking",1609.344*100));
        assertEquals(3,progress(AchievementStore.calculate(app),"foot-total").level);
        JourneyStore.delete(app,"one");assertEquals(0,progress(AchievementStore.calculate(app),"foot-total").level);
    }
}
