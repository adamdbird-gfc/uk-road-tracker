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
public class TownAchievementsTest {
    private Context app;
    @Before public void setup() {
        app=RuntimeEnvironment.getApplication();JourneyStore.deleteAll(app);
        for(String name:new String[]{"roadprints_achievements_v1","roadprints_town_achievement_evidence_v1",
                "roadprints_local_settlement_matches_v1","roadprints_local_settlement_inventories_v1"})
            app.getSharedPreferences(name,0).edit().clear().commit();
    }
    private TownAchievementEvidence.Town town(TownAchievementEvidence e,String code,int total,int discovered) {
        TownAchievementEvidence.Town t=new TownAchievementEvidence.Town(code,code);t.inventory=total;
        for(int i=0;i<discovered;i++)t.roads.add("road"+i);e.towns.put(code,t);return t;
    }
    private AchievementStore.Definition definition(String id) {
        return AchievementStore.definitions().stream().filter(d->d.id.equals(id)).findFirst().get();
    }
    private AchievementStore.Progress progress(AchievementStore.Snapshot s,String id) {
        return s.achievements.stream().filter(p->p.definition.id.equals(id)).findFirst().get();
    }
    private JSONObject geometry(double x) throws Exception {
        return new JSONObject().put("type","LineString").put("coordinates",new JSONArray()
                .put(new JSONArray().put(x).put(51.5)).put(new JSONArray().put(x+.001).put(51.5)));
    }
    private JSONObject journey(String id,String mode,String name,JSONObject geom) throws Exception {
        JSONObject feature=new JSONObject().put("properties",new JSONObject().put("name",name).put("highway","residential"))
                .put("geometry",geom);
        return new JSONObject().put("journey_id",id).put("mode",mode).put("processing_status","complete")
                .put("processing_result",new JSONObject().put("road_geojson",new JSONObject().put("features",new JSONArray().put(feature))));
    }
    private void cache(String name,List<JSONObject> geometry,String code,String town,int inventory) {
        AchievementStore.recordRoadSettlements(app,"name:"+name,geometry,
                Collections.singletonList(new LocalRoadSettlementMatcher.Settlement(code,town)));
        app.getSharedPreferences("roadprints_local_settlement_inventories_v1",0).edit().putString(code,""+inventory).commit();
    }
    @Test public void exactly400IsExcludedAnd401QualifiesWithoutRounding() {
        TownAchievementEvidence e=new TownAchievementEvidence();town(e,"small",400,400);town(e,"pending",-1,400);
        TownAchievementEvidence.Town eligible=town(e,"eligible",401,100);
        assertEquals(0,AchievementStore.levelFor(definition("town-exploration"),e.value(false)));
        eligible.roads.add("oneMore");assertEquals(1,AchievementStore.levelFor(definition("town-exploration"),e.value(false)));
        assertTrue(e.contributions(25).stream().anyMatch(s->s.contains("inventory pending")));
        assertTrue(e.contributions(25).stream().anyMatch(s->s.contains("needs more than 400")));
    }
    @Test public void bothLaddersUseAllFourExactThresholds() {
        for(int level=0;level<4;level++) {
            TownAchievementEvidence e=new TownAchievementEvidence();int count=(level+1)*200;
            town(e,"a",800,count);town(e,"b",800,count);TownAchievementEvidence.Town c=town(e,"c",800,count-1);
            assertEquals(level+1,AchievementStore.levelFor(definition("town-exploration"),e.value(false)));
            assertEquals(level,AchievementStore.levelFor(definition("roaming-exploration"),e.value(true)));
            c.roads.add("last");assertEquals(level+1,AchievementStore.levelFor(definition("roaming-exploration"),e.value(true)));
        }
    }
    @Test public void roamingUsesThirdTownNotAverageAndRequiresThreeCodes() {
        TownAchievementEvidence e=new TownAchievementEvidence();town(e,"a",800,800);town(e,"b",800,800);
        assertEquals(0,e.value(true),0);town(e,"c",800,199);
        assertEquals(24.875,e.value(true),0);assertEquals(0,AchievementStore.levelFor(definition("roaming-exploration"),e.value(true)));
        town(e,"small",400,400);assertEquals(24.875,e.value(true),0);
    }
    @Test public void combinedModesRepeatsAndFragmentsCountRoadOnceAndExcludeTrain() throws Exception {
        JSONObject first=geometry(0),second=geometry(.002);
        CoreAchievementEvidence core=new CoreAchievementEvidence();
        core.add(journey("a","walking","High Street",first));core.add(journey("b","bus","High Street",first));
        core.add(journey("c","driving","High Street",second));core.add(journey("d","train","Other Road",first));
        cache("high street",Arrays.asList(first,second),"town","Town",800);
        TownAchievementEvidence e=new TownAchievementEvidence();e.collect(app,core);
        assertEquals(1,e.towns.size());assertEquals(1,e.towns.get("town").roads.size());
    }
    @Test public void sameNameTownsStayDistinctByCodeAndDuplicateMetadataDoesNotInflate() throws Exception {
        JSONObject g=geometry(0);CoreAchievementEvidence c=new CoreAchievementEvidence();c.add(journey("a","walking","High Street",g));
        AchievementStore.recordRoadSettlements(app,"name:high street",Collections.singletonList(g),Arrays.asList(
                new LocalRoadSettlementMatcher.Settlement("a","Newtown"),new LocalRoadSettlementMatcher.Settlement("b","Newtown"),
                new LocalRoadSettlementMatcher.Settlement("a","Newtown")));
        TownAchievementEvidence e=new TownAchievementEvidence();e.collect(app,c);
        assertEquals(2,e.towns.size());assertEquals(1,e.towns.get("a").roads.size());assertEquals(1,e.towns.get("b").roads.size());
    }
    @Test public void changedGeometryCannotReusePreviousTownEvidence() throws Exception {
        JSONObject old=geometry(0),changed=geometry(.02);cache("high street",Collections.singletonList(old),"a","A",800);
        CoreAchievementEvidence c=new CoreAchievementEvidence();c.add(journey("a","walking","High Street",changed));
        TownAchievementEvidence e=new TownAchievementEvidence();e.collect(app,c);assertTrue(e.towns.isEmpty());assertEquals(1,e.unresolvedRoads);
    }
    @Test public void removedRoadAndDeletionRemoveTravelEvidenceWithoutReusingCache() throws Exception {
        JSONObject g=geometry(0);cache("high street",Collections.singletonList(g),"a","A",800);
        JSONObject j=journey("a","walking","High Street",g).put("journey_corrections",
                new JSONObject().put("removed_road_ids",new JSONArray().put("name:high street")));
        CoreAchievementEvidence c=new CoreAchievementEvidence();c.add(j);TownAchievementEvidence e=new TownAchievementEvidence();e.collect(app,c);
        assertTrue(e.towns.isEmpty());
        e=new TownAchievementEvidence();e.collect(app,new CoreAchievementEvidence());assertTrue(e.towns.isEmpty());
    }
    @Test public void oldSettlementCacheMigratesOfflineAndMalformedInventoryStaysPending() throws Exception {
        JSONObject g=geometry(0);LocalRoadSettlementMatcher.saveCached(app,"name:high street",Collections.singletonList(g),
                Collections.singletonList(new LocalRoadSettlementMatcher.Settlement("a","Town")));
        app.getSharedPreferences("roadprints_local_settlement_inventories_v1",0).edit().putString("a","broken").commit();
        CoreAchievementEvidence c=new CoreAchievementEvidence();c.add(journey("a","walking","High Street",g));
        TownAchievementEvidence e=new TownAchievementEvidence();e.collect(app,c);assertEquals(-1,e.towns.get("a").inventory);
        assertEquals(0,e.value(false),0);assertTrue(e.contributions(25).get(0).contains("pending"));
    }
    @Test public void titlesUpgradeAndDowngradeWithoutRepeatCelebration() {
        Map<String,Double> values=new HashMap<>();values.put("town-exploration",75d);values.put("roaming-exploration",50d);
        AchievementStore.Snapshot first=AchievementStore.evaluate(app,values,Collections.emptyMap(),null,0);
        assertEquals("Town Mayor",AchievementStore.titleFor(definition("town-exploration"),progress(first,"town-exploration").level));
        assertEquals("Roaming Legend",AchievementStore.titleFor(definition("roaming-exploration"),progress(first,"roaming-exploration").level));
        assertEquals(2,first.newlyUnlocked.size());
        AchievementStore.evaluate(app,Collections.emptyMap(),Collections.emptyMap(),null,1);
        assertTrue(AchievementStore.evaluate(app,values,Collections.emptyMap(),null,2).newlyUnlocked.isEmpty());
        assertEquals("Town Explorer",AchievementStore.titleFor(definition("town-exploration"),0));
    }
    @Test public void calculateConnectsTownEvidenceToCurrentJourneys() throws Exception {
        JSONObject g=geometry(0);JSONArray features=new JSONArray();
        for(int i=0;i<101;i++) {
            String name="street "+i;cache(name,Collections.singletonList(g),"a","A",401);
            features.put(new JSONObject().put("geometry",g).put("properties",new JSONObject().put("name",name).put("highway","residential")));
        }
        JSONObject j=new JSONObject().put("journey_id","import").put("mode","walking").put("processing_status","complete")
                .put("source",new JSONObject().put("type","timeline_import"))
                .put("processing_result",new JSONObject().put("road_geojson",new JSONObject().put("features",features)));
        JourneyStore.save(app,j);AchievementStore.Snapshot result=AchievementStore.calculate(app);
        assertEquals(1,progress(result,"town-exploration").level);assertEquals(0,progress(result,"roaming-exploration").level);
        JourneyStore.delete(app,"import");assertEquals(0,progress(AchievementStore.calculate(app),"town-exploration").level);
    }
}
