package com.roadprints.capture;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class PubAchievementsTest {
static final String[][] NAMED={
        {"spoons-rochester-castle","(Not) The OG","spoons-osm-n256404255","The Rochester Castle","Stoke Newington","London"},
        {"spoons-hawkshaw","Nice to meet you Sir","spoons-osm-n5141625722","The Sir John Hawkshaw","Cannon Street, London","London"},
        {"spoons-pavilion","It’s echoey in here","spoons-osm-w103966079","Royal Victoria Pavilion","Ramsgate","South East"},
        {"spoons-opera","Nessun dorma","spoons-osm-n1973967757","Opera House","Royal Tunbridge Wells","South East"},
        {"spoons-justice","Guilty as charged","spoons-osm-w277707569","The Chief Justice of the Common Pleas","Keswick","North West"},
        {"spoons-winter","It’s a kinda chilly","spoons-osm-n462615653","The Winter Gardens","Harrogate","Yorkshire and the Humber"},
        {"spoons-palladium","Sunday night at the Palladium","spoons-osm-w223309617","The Palladium","Llandudno","Wales"},
        {"spoons-wallaw","No way, downstairs loos!","spoons-osm-w134968549","The Wallaw","Blyth","North East"},
        {"spoons-kettleby","Pie & a Pint","spoons-osm-n1059478029","The Kettleby Cross","Melton Mowbray","East Midlands"},
        {"spoons-square-peg","Round Holes","spoons-osm-w94961209","The Square Peg","Birmingham","West Midlands"},
        {"spoons-corn-exchange","Buried in opulence","spoons-osm-n2291314726","The Corn Exchange","Bury St Edmunds","East of England"},
        {"spoons-imperial","Anyone for drinks in the orangery?","spoons-osm-w175308977","The Imperial","Exeter","South West"},
        {"spoons-counting-house","1, 2, 3, 4, 5","spoons-osm-n247937950","The Counting House","Glasgow","Scotland"},
        {"spoons-spirit-merchant","Single or double?","spoons-osm-n2964369609","The Spirit Merchant","Newtownards","Northern Ireland"}
    };
    private Context app;private JSONArray pubs,categories;
    @Before public void reset(){app=RuntimeEnvironment.getApplication();app.getSharedPreferences("roadprints_achievements_v1",0).edit().clear().commit();pubs=CollectionMapCatalog.spoons(app);categories=PubAchievements.categories(app);}
    private JSONObject proof(String id) throws Exception {return new JSONObject().put("pub_id",id).put("source","confirmed_timeline_visit").put("visited_at","2026-09-01T12:00:00Z");}
    private PubAchievements.Snapshot calculate(JSONArray records){return PubAchievements.calculate(pubs,records,true,categories);}
    @Test public void allNamedAndCategoryIdentitiesResolveToReviewedCurrentPubs() throws Exception {
        Set<String> areas=new HashSet<>();
        for(String[] row:NAMED){JSONObject pub=PubVisitStore.find(pubs,row[2]);assertNotNull(row[3],pub);areas.add(row[5]);}
        assertEquals(12,areas.size());assertEquals(14,NAMED.length);
        for(int i=0;i<categories.length();i++)assertNotNull(PubVisitStore.find(pubs,categories.getJSONObject(i).getString("id")));
        assertEquals("Glasgow",PubVisitStore.find(pubs,"spoons-osm-n247937950").getString("town"));
    }
    @Test public void onePubCanEarnNamedTrainAndRegionalProgressButRepeatsCountOnce() throws Exception {
        JSONArray records=new JSONArray().put(proof("spoons-osm-n5141625722")).put(proof("spoons-osm-n5141625722"));
        PubAchievements.Snapshot result=calculate(records);
        assertEquals(1,result.values.get("spoons-hawkshaw"),0);assertEquals(1,result.values.get("spoons-train"),0);
        assertEquals(1,result.values.get("spoons-grand-tour"),0);assertEquals(1,result.values.get("spoons-collector"),0);
        assertEquals(0,result.values.get("spoons-plane"),0);
    }
    @Test public void eitherLondonAndSouthEastNomineeCompletesTheSameTourAreas() throws Exception {
        JSONArray records=new JSONArray();
        for(String[] row:NAMED)if(!"spoons-hawkshaw".equals(row[0])&&!"spoons-opera".equals(row[0]))records.put(proof(row[2]));
        assertEquals(12,calculate(records).values.get("spoons-grand-tour"),0);
        JSONArray alternatives=new JSONArray();
        for(String[] row:NAMED)if(!"spoons-rochester-castle".equals(row[0])&&!"spoons-pavilion".equals(row[0]))alternatives.put(proof(row[2]));
        assertEquals(12,calculate(alternatives).values.get("spoons-grand-tour"),0);
    }
    @Test public void allFourNationsAndFourStageVenuesAreRequired() throws Exception {
        JSONArray records=new JSONArray().put(proof("spoons-osm-w103966079")).put(proof("spoons-osm-n1973967757")).put(proof("spoons-osm-w134968549"));
        assertEquals(3,calculate(records).values.get("spoons-stage"),0);assertEquals(1,calculate(records).values.get("spoons-nations"),0);
        records.put(proof("spoons-osm-w223309617"));assertEquals(4,calculate(records).values.get("spoons-stage"),0);
        records.put(proof("spoons-osm-n247937950")).put(proof("spoons-osm-n2964369609"));
        assertEquals(4,calculate(records).values.get("spoons-nations"),0);
    }
    @Test public void rawNearbyPlacesAndUninstalledCollectionNeverAward() throws Exception {
        JSONArray raw=new JSONArray().put(new JSONObject().put("pub_id","spoons-osm-n5141625722").put("source","google_timeline_placeVisit"));
        assertEquals(0,calculate(raw).values.get("spoons-train"),0);
        PubAchievements.Snapshot locked=PubAchievements.calculate(pubs,new JSONArray().put(proof("spoons-osm-n5141625722")),false,categories);
        assertEquals(0,locked.values.get("spoons-collector"),0);
        assertEquals(0,locked.values.get("spoons-grand-tour"),0);
    }
    @Test public void stationRoadNameDoesNotQualifyAndHavenNeedsPubProof() throws Exception {
        assertEquals(0,calculate(new JSONArray().put(proof("spoons-osm-w1323980995"))).values.get("spoons-train"),0);
        assertEquals(1,calculate(new JSONArray().put(proof("spoons-curated-springfield-cala-gran"))).values.get("spoons-holiday"),0);
        assertEquals(1,calculate(new JSONArray().put(proof("spoons-curated-belle-vue-manchester-airport"))).values.get("spoons-plane"),0);
    }
    @Test public void retainedHistoricalProofCountsWhenPinDisappears() throws Exception {
        JSONObject record=proof("retired-pub").put("nation","Scotland");
        PubAchievements.Snapshot result=PubAchievements.calculate(new JSONArray(),new JSONArray().put(record),true,categories);
        assertEquals(1,result.values.get("spoons-collector"),0);assertEquals(1,result.values.get("spoons-nations"),0);
    }
    @Test public void retrospectiveMilestonesRecalculateAfterRemovingProofWithoutRepeatedCelebrations() throws Exception {
        JSONArray records=new JSONArray();for(int i=0;i<10;i++)records.put(proof("historic-"+i));
        PubAchievements.Snapshot goals=calculate(records);
        AchievementStore.Snapshot first=AchievementStore.evaluate(app,goals.values,goals.lists,new boolean[7],0);
        AchievementStore.Progress collector=null;for(AchievementStore.Progress row:first.achievements)if("spoons-collector".equals(row.definition.id))collector=row;
        assertNotNull(collector);assertEquals(2,collector.level);assertTrue(collector.unlocked);
        assertFalse(first.newlyUnlocked.isEmpty());
        assertTrue(AchievementStore.evaluate(app,goals.values,goals.lists,new boolean[7],0).newlyUnlocked.isEmpty());
        PubAchievements.Snapshot empty=calculate(new JSONArray());
        AchievementStore.Snapshot corrected=AchievementStore.evaluate(app,empty.values,empty.lists,new boolean[7],1);
        for(AchievementStore.Progress row:corrected.achievements)if("spoons-collector".equals(row.definition.id))assertFalse(row.unlocked);
    }
}
