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
public class CountyCollectorTest {
    private Context app;
    private HistoricCountyCatalog catalogue;
    @Before public void setup() throws Exception {
        app=RuntimeEnvironment.getApplication();JourneyStore.deleteAll(app);
        app.getSharedPreferences("roadprints_achievements_v1",0).edit().clear().commit();
        catalogue=new HistoricCountyCatalog(app);
    }
    private HistoricCountyCatalog.County county(String code){return catalogue.counties.stream().filter(c->c.code.equals(code)).findFirst().get();}
    private JSONObject journey(String code,String mode) throws Exception {
        HistoricCountyCatalog.County c=county(code);
        JSONArray line=new JSONArray().put(new JSONArray().put(c.interiorLon).put(c.interiorLat))
                .put(new JSONArray().put(c.interiorLon+.0000001).put(c.interiorLat));
        JSONObject feature=new JSONObject().put("properties",new JSONObject().put("name","Test Road").put("highway","residential"))
                .put("geometry",new JSONObject().put("type","LineString").put("coordinates",line));
        return new JSONObject().put("journey_id",code).put("mode",mode).put("processing_status","complete")
                .put("processing_result",new JSONObject().put("road_geojson",new JSONObject().put("features",new JSONArray().put(feature))));
    }
    private CountyCollectorEvidence scan() throws Exception {
        CountyCollectorEvidence e=new CountyCollectorEvidence(catalogue);
        JourneyStore.forEachAchievementEvidence(app,()->false,e::add);return e;
    }
    @Test public void all92OfflinePolygonsContainTheirIndependentInteriorFixture() throws Exception {
        assertEquals(92,catalogue.counties.size());Map<String,Integer> counts=new HashMap<>();
        for(HistoricCountyCatalog.County c:catalogue.counties) {
            assertTrue(c.code,catalogue.geometry(c).contains(c.interiorLon,c.interiorLat));
            counts.put(c.nation,counts.getOrDefault(c.nation,0)+1);
        }
        assertEquals(Integer.valueOf(39),counts.get("England"));assertEquals(Integer.valueOf(34),counts.get("Scotland"));
        assertEquals(Integer.valueOf(13),counts.get("Wales"));assertEquals(Integer.valueOf(6),counts.get("Northern Ireland"));
    }
    @Test public void historicLocationsAreIndependentOfModernProgressCountyLabels() throws Exception {
        assertTrue(catalogue.geometry(county("KNT")).contains(.37,51.44));
        assertTrue(catalogue.geometry(county("MSX")).contains(-.141,51.515));
        assertTrue(catalogue.geometry(county("LCS")).contains(-3.05,53.81));
        assertTrue(catalogue.geometry(county("GLM")).contains(-3.18,51.48));
        assertTrue(catalogue.geometry(county("ANM")).contains(-5.93,54.61));
        assertFalse(catalogue.geometry(county("KNT")).contains(.37,51.50));
    }
    @Test public void repeatedRoadsAndModesCountOnceButRailFailuresAndPathsDoNot() throws Exception {
        CountyCollectorEvidence e=new CountyCollectorEvidence(catalogue);
        e.add(journey("KNT","walking"));e.add(journey("KNT","bus"));e.add(journey("ANM","driving"));
        e.add(journey("GLM","train"));e.add(journey("MSX","plane"));e.add(journey("LCS","running").put("processing_status","failed"));
        JSONObject path=journey("GLM","walking");path.getJSONObject("processing_result").getJSONObject("road_geojson").getJSONArray("features").getJSONObject(0).getJSONObject("properties").put("highway","footway");e.add(path);
        assertEquals(new HashSet<>(Arrays.asList("KNT","ANM")),e.completed);assertEquals(92,e.checklist().size());
        assertTrue(e.checklist().stream().anyMatch(s->s.startsWith("✓ Kent")));
        assertTrue(e.checklist().stream().anyMatch(s->s.startsWith("○ Glamorgan")));
    }
    @Test public void deletingOrRemovingTheOnlyUnlockedRoadRevokesItsCountyEvidence() throws Exception {
        JSONObject walk=journey("KNT","walking");JourneyStore.save(app,walk);assertTrue(scan().completed.contains("KNT"));
        walk.put("journey_corrections",new JSONObject().put("removed_road_ids",new JSONArray().put("name:test road")));
        JourneyStore.save(app,walk);assertFalse(scan().completed.contains("KNT"));
        walk.remove("journey_corrections");JourneyStore.save(app,walk);assertTrue(scan().completed.contains("KNT"));
        JourneyStore.delete(app,"KNT");assertTrue(scan().completed.isEmpty());
    }
    @Test public void exactPercentagesUnlockAt10_23_46_92AndDowngradesKeepMilestoneHistory() {
        AchievementStore.Definition d=AchievementStore.definitions().stream().filter(x->x.id.equals("county-collector")).findFirst().get();
        int[] thresholds={10,23,46,92};
        for(int i=0;i<4;i++) {
            assertEquals(i,AchievementStore.levelFor(d,100.0*(thresholds[i]-1)/92));
            assertEquals(i+1,AchievementStore.levelFor(d,100.0*thresholds[i]/92));
        }
        AchievementStore.Snapshot high=AchievementStore.evaluate(app,Collections.singletonMap(d.id,50.0),Collections.emptyMap(),new boolean[7],0);
        AchievementStore.Progress p=high.achievements.stream().filter(x->x.definition==d).findFirst().get();assertEquals(3,p.level);
        AchievementStore.Snapshot lower=AchievementStore.evaluate(app,Collections.emptyMap(),Collections.emptyMap(),new boolean[7],1);
        p=lower.achievements.stream().filter(x->x.definition==d).findFirst().get();assertFalse(p.unlocked);assertEquals(3,p.milestones.size());
        assertTrue(AchievementStore.evaluate(app,Collections.singletonMap(d.id,50.0),Collections.emptyMap(),new boolean[7],2).newlyUnlocked.isEmpty());
    }
}
