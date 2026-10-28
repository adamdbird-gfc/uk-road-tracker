package com.roadprints.capture;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class MatchedEditReconciliationTest {
    private JSONObject result(String coordinates)throws Exception{return new JSONObject("{\"geojson\":{\"features\":[{\"geometry\":{\"type\":\"LineString\",\"coordinates\":"+coordinates+"}}]}} ");}
    @Test public void exactRemovedSectionSurvivesNewLeadingEdges()throws Exception {
        JSONObject old=result("[[0,0],[1,0],[2,0]]"),replacement=result("[[-1,0],[0,0],[1,0],[2,0]]");
        JSONObject j=new JSONObject().put("processing_result",old).put("journey_corrections",new JSONObject().put("removed_matched_segments",new JSONArray("[1]")));
        JourneyCorrectionUtils.reconcileMatchedRemovals(j,replacement);
        assertEquals("[2]",j.getJSONObject("journey_corrections").getJSONArray("removed_matched_segments").toString());
    }
    @Test public void nearbyParallelSectionCannotInheritAnEdit()throws Exception {
        JSONObject j=new JSONObject().put("processing_result",result("[[0,0],[1,0]]")).put("journey_corrections",new JSONObject().put("removed_matched_segments",new JSONArray("[0]")));
        String original=j.toString();
        try{JourneyCorrectionUtils.reconcileMatchedRemovals(j,result("[[0,0.00001],[1,0.00001]]"));fail();}
        catch(IllegalStateException expected){assertTrue(expected.getMessage().contains("preserved"));}
        assertEquals(original,j.toString());
    }
}
