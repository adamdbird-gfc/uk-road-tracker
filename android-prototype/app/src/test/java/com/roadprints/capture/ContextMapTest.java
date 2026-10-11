package com.roadprints.capture;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class ContextMapTest {
    private JSONArray rectangle(double radius) throws Exception {
        JSONArray ring=new JSONArray();
        for(double[] p:new double[][]{{-radius,-radius},{radius,-radius},{radius,radius},{-radius,radius},{-radius,-radius}})
            ring.put(new JSONObject().put("lat",51.5+p[0]/111195).put("lon",-.1+p[1]/(111195*Math.cos(Math.toRadians(51.5)))));
        return ring;
    }
    private JSONObject building(double radius) throws Exception {
        return new JSONObject().put("tags",new JSONObject().put("building","house")).put("geometry",rectangle(radius));
    }
    @Test public void destinationNeedsAccuracyCircleInsideFootprint() throws Exception {
        JSONArray f=new JSONArray().put(building(30));
        assertTrue(ContextMap.at(f,51.5,-.1,5).destination);
        assertFalse(ContextMap.at(f,51.5,-.1,40).destination);
        assertFalse(ContextMap.at(f,51.501,-.1,5).destination);
    }
    @Test public void roadOutsideBusinessCannotBeAnArrival() throws Exception {
        JSONArray g=new JSONArray().put(new JSONObject().put("lat",51.499).put("lon",-.1))
                .put(new JSONObject().put("lat",51.501).put("lon",-.1));
        JSONArray f=new JSONArray().put(building(30)).put(new JSONObject().put("tags",new JSONObject().put("highway","primary")).put("geometry",g));
        ContextMap.Evidence e=ContextMap.at(f,51.5,-.1,5);assertTrue(e.road);assertFalse(e.destination);
    }
    @Test public void absentMapIsUncertainRatherThanOffRoad() {
        ContextMap.Evidence e=ContextMap.at(null,51.5,-.1,5);assertFalse(e.available);assertFalse(e.destination);
    }
    @Test public void parkingAndOutdoorAreasProvideDifferentContext() throws Exception {
        JSONArray f=new JSONArray().put(new JSONObject().put("tags",new JSONObject().put("amenity","parking")).put("geometry",rectangle(100)));
        assertTrue(ContextMap.at(f,51.5,-.1,5).parking);
        f=new JSONArray().put(new JSONObject().put("tags",new JSONObject().put("leisure","park")).put("geometry",rectangle(100)));
        ContextMap.Evidence e=ContextMap.at(f,51.5,-.1,5);assertTrue(e.outdoor);assertFalse(e.destination);
    }
    @Test public void courtyardHoleIsNotInsideBuildingAndAccuracyMustClearHoleEdge() throws Exception {
        JSONObject f=building(100).put("holes",new JSONArray().put(rectangle(20)));
        assertFalse(ContextMap.at(new JSONArray().put(f),51.5,-.1,5).destination);
        assertFalse(ContextMap.at(new JSONArray().put(f),51.5+25.0/111195,-.1,10).destination);
        assertTrue(ContextMap.at(new JSONArray().put(f),51.5+40.0/111195,-.1,5).destination);
    }
    @Test public void nearbyRoadDoesNotOverrideAContainedBuildingFix() throws Exception {
        double scale=111195*Math.cos(Math.toRadians(51.5));
        JSONArray road=new JSONArray().put(new JSONObject().put("lat",51.499).put("lon",-.1+38/scale))
                .put(new JSONObject().put("lat",51.501).put("lon",-.1+38/scale));
        JSONArray features=new JSONArray().put(building(30)).put(new JSONObject().put("tags",new JSONObject().put("highway","residential")).put("geometry",road));
        ContextMap.Evidence e=ContextMap.at(features,51.5,-.1+25/scale,3);
        assertTrue(e.destination);assertTrue(e.routeAmbiguous);
    }
}
