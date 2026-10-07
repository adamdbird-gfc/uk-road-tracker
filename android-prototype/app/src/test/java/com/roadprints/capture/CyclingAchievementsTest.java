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
public class CyclingAchievementsTest {
    private static final double MILE=1609.344;
    private Context app;
    @Before public void setup() {
        app=RuntimeEnvironment.getApplication();JourneyStore.deleteAll(app);
        app.getSharedPreferences("roadprints_achievements_v1",0).edit().clear().commit();
        DistanceUnits.setKilometres(app,false);
    }
    private JSONObject ride(String id,String mode,double miles) throws Exception {
        return FinalTravelAchievementsTest.journey(mode,"2026-10-05T08:00:00Z",FinalTravelAchievementsTest.route(0,false))
                .put("journey_id",id).put("distance_meters",miles*MILE);
    }
    private AchievementStore.Progress progress(AchievementStore.Snapshot s,String id) {
        return s.achievements.stream().filter(p->id.equals(p.definition.id)).findFirst().get();
    }
    @Test public void importsAndCaptureUseCyclingTotalsWithoutFootOrRoadCredit() throws Exception {
        CoreAchievementEvidence e=new CoreAchievementEvidence();
        e.add(ride("import","cycling",10));
        e.add(ride("capture","bicycle",15).put("source",new JSONObject().put("type","android_activity_capture")));
        assertEquals(25*MILE,e.cycleMetres,.001);assertEquals(2,e.cycleJourneys.size());assertEquals(15*MILE,e.longestCycle,.001);
        assertEquals(0,e.roadMetres,0);assertEquals(0,e.footMetres,0);
        assertEquals(0,e.roadCoverage.metres(),0);assertEquals(0,e.footCoverage.metres(),0);
        assertTrue(e.captured);assertTrue(e.refs.isEmpty());assertTrue(e.named.isEmpty());assertTrue(e.townRoads.isEmpty());
    }
    @Test public void repeatedAndReverseRidesGrowTotalsButNotUniqueCoverage() throws Exception {
        CoreAchievementEvidence e=new CoreAchievementEvidence();JSONObject first=ride("a","cycling",1);e.add(first);
        double unique=e.cycleCoverage.metres();
        JSONObject reverse=FinalTravelAchievementsTest.journey("bicycle","2026-10-06T08:00:00Z",FinalTravelAchievementsTest.route(0,true)).put("journey_id","b").put("distance_meters",MILE);
        e.add(reverse);assertEquals(2*MILE,e.cycleMetres,.001);assertEquals(unique,e.cycleCoverage.metres(),.01);
        // Scanning one saved journey again must not inflate the completed ride count.
        e.add(first);assertEquals(2,e.cycleJourneys.size());
    }
    @Test public void failedPendingAndEmptyMatchesOnlyCountTravelDistance() throws Exception {
        CoreAchievementEvidence e=new CoreAchievementEvidence();
        e.add(ride("failed","cycling",100).put("processing_status","failed"));
        e.add(ride("pending","bicycle",100).put("processing_status","pending"));
        JSONObject empty=ride("empty","cycling",100);empty.remove("processing_result");e.add(empty);
        assertEquals(300*MILE,e.cycleMetres,.001);assertTrue(e.cycleJourneys.isEmpty());
        assertEquals(0,e.longestCycle,0);assertEquals(0,e.cycleCoverage.metres(),0);
    }
    @Test public void correctionsRemoveCoverageAndCannotLeaveADeletedLongRide() throws Exception {
        JSONObject edited=ride("edited","cycling",100).put("journey_corrections",new JSONObject().put("removed_matched_segments",new JSONArray().put(0)));
        CoreAchievementEvidence full=new CoreAchievementEvidence(),part=new CoreAchievementEvidence();
        full.add(ride("full","cycling",100));part.add(edited);
        assertTrue(part.cycleCoverage.metres()<full.cycleCoverage.metres());assertTrue(part.longestCycle<MILE);
        edited.getJSONObject("journey_corrections").put("removed_matched_segments",new JSONArray().put(0).put(1).put(2).put(3));
        CoreAchievementEvidence deleted=new CoreAchievementEvidence();deleted.add(edited);
        assertTrue(deleted.cycleJourneys.isEmpty());assertEquals(0,deleted.longestCycle,0);assertEquals(0,deleted.cycleCoverage.metres(),0);
    }
    @Test public void allCyclingTierBoundariesAndIconsAreExact() {
        for(String id:new String[]{"cycle-total","cycle-unique","cycle-rides","cycle-longest"}) {
            AchievementStore.Definition d=AchievementStore.definitions().stream().filter(v->id.equals(v.id)).findFirst().get();
            assertEquals(R.drawable.ic_roadprints_bicycle,RoadprintsIcons.achievement(id));
            for(int i=0;i<d.levels.length;i++) {
                assertEquals(i,AchievementStore.levelFor(d,d.levels[i]-.000001));assertEquals(i+1,AchievementStore.levelFor(d,d.levels[i]));
            }
        }
        assertEquals(R.drawable.ic_roadprints_bicycle,RoadprintsIcons.achievement("cycle-first"));
    }
    @Test public void existingSavedRidesUnlockFamiliesAndDeletionRevokesWithoutRecelebration() throws Exception {
        for(int i=0;i<5;i++)JourneyStore.save(app,ride("ride"+i,i%2==0?"cycling":"bicycle",i==0?25:1));
        AchievementStore.Snapshot first=AchievementStore.calculate(app);
        assertEquals(2,progress(first,"cycle-total").level);assertTrue(progress(first,"cycle-first").unlocked);
        assertEquals(1,progress(first,"cycle-rides").level);assertEquals(3,progress(first,"cycle-longest").level);
        assertTrue(progress(first,"long-way-home").unlocked);
        assertEquals(0,progress(first,"road-total").level);assertEquals(0,progress(first,"foot-total").level);
        JourneyStore.delete(app,"ride0");AchievementStore.Snapshot deleted=AchievementStore.calculate(app);
        assertEquals(0,progress(deleted,"cycle-rides").level);assertFalse(progress(deleted,"long-way-home").unlocked);
        assertEquals(3,progress(deleted,"cycle-longest").milestones.size());
        JourneyStore.save(app,ride("ride0","cycling",25));assertTrue(AchievementStore.calculate(app).newlyUnlocked.isEmpty());
    }
    @Test public void cyclingDistancePresentationHonoursUnitsAndRideCountIsClear() {
        Map<String,Double> values=new HashMap<>();values.put("cycle-total",25.0);values.put("cycle-longest",25.0);values.put("cycle-rides",5.0);
        AchievementStore.Snapshot snapshot=AchievementStore.evaluate(app,values,Collections.emptyMap(),new boolean[7],0);
        assertTrue(AchievementStore.progressText(app,progress(snapshot,"cycle-total")).startsWith("Cycling · 25.0 mi"));
        assertTrue(AchievementStore.progressText(app,progress(snapshot,"cycle-longest")).startsWith("Longest cycling journey · 25.0 mi"));
        assertEquals("5 / 25 completed cycling journeys",AchievementStore.progressText(app,progress(snapshot,"cycle-rides")));
        DistanceUnits.setKilometres(app,true);
        assertTrue(AchievementStore.progressText(app,progress(snapshot,"cycle-total")).contains("40.2 km"));
        assertTrue(AchievementStore.progressText(app,progress(snapshot,"long-way-home")).contains("40.2 km cycling"));
    }
    @Test public void cyclingAliasesShareGroundhogWeekdaysButDoNotMixWithOtherModes() throws Exception {
        GroundhogDayEvidence e=new GroundhogDayEvidence();
        for(int day=5;day<=9;day++)e.add(FinalTravelAchievementsTest.journey(day%2==0?"cycling":"bicycle","2026-10-0"+day+"T08:00:00Z",FinalTravelAchievementsTest.route(0,false)));
        assertEquals(5,e.value());
        GroundhogDayEvidence mixed=new GroundhogDayEvidence();mixed.add(ride("a","cycling",1));
        mixed.add(FinalTravelAchievementsTest.journey("walking","2026-10-06T08:00:00Z",FinalTravelAchievementsTest.route(0,false)));
        assertEquals(1,mixed.value());
    }
    @Test public void applicableCyclingLandmarksQualifyAndFootOnlyLandmarksStayFootOnly() throws Exception {
        for(String mode:new String[]{"cycling","bicycle"}) {
            LandmarkEvidence e=new LandmarkEvidence(app);
            for(String id:new String[]{"big-ben","humber-bridge-landmark","trent-bridge","conwy-castle"}) {
                double[][] crossing=FinalTravelAchievementsTest.full(FinalTravelAchievementsTest.reference(e,id));
                e.add(FinalTravelAchievementsTest.journey(mode,"2026-10-05T08:00:00Z",crossing));assertTrue(id,e.completed.contains(id));
            }
            e.add(FinalTravelAchievementsTest.journey(mode,"2026-10-05T08:00:00Z",new double[][]{{-1.8954,52.4781259},{-1.8948,52.4781259}}));
            assertFalse(e.completed.contains("bullring-bull"));assertEquals(4,e.count());
        }
    }
    @Test public void savedCyclingLandmarksFeedSightseerAndCaptureMilestone() throws Exception {
        LandmarkEvidence references=new LandmarkEvidence(app);int index=0;
        for(String id:new String[]{"big-ben","humber-bridge-landmark","trent-bridge"}) {
            JSONObject cycle=FinalTravelAchievementsTest.journey("cycling","2026-10-05T08:00:00Z",
                    FinalTravelAchievementsTest.full(FinalTravelAchievementsTest.reference(references,id)))
                    .put("journey_id","landmark"+index++).put("source",new JSONObject().put("type","android_activity_capture"));
            JourneyStore.save(app,cycle);
        }
        AchievementStore.Snapshot snapshot=AchievementStore.calculate(app);
        assertEquals(1,progress(snapshot,"sightseer").level);assertTrue(progress(snapshot,"sat-nav-on").unlocked);
        assertTrue(progress(snapshot,"humber-bridge-landmark").unlocked);
    }
    @Test public void provisionalCyclingRoadNamesCannotUnlockRoadDiscovery() throws Exception {
        JSONObject provisional=FinalTravelAchievementsTest.road(ride("a","cycling",1),"A1");
        JSONObject result=provisional.getJSONObject("processing_result");result.put("cycling_geojson",result.remove("road_geojson"));
        CoreAchievementEvidence e=new CoreAchievementEvidence();e.add(provisional);
        assertTrue(e.refs.isEmpty());assertTrue(e.named.isEmpty());assertTrue(e.townRoads.isEmpty());assertEquals(1,e.cycleJourneys.size());
    }
}
