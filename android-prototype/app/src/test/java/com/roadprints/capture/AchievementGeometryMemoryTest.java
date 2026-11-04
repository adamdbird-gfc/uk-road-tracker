package com.roadprints.capture;

import java.util.*;
import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Lossless evidence checks plus a standalone -Xmx256m retention probe. */
public class AchievementGeometryMemoryTest {
    static JSONObject journey(int id,int vertices) throws Exception {
        JSONArray line=new JSONArray();
        for(int p=0;p<vertices;p++)line.put(new JSONArray().put(-.1+p*.00001+id*.00000001).put(51.5+p*.000001));
        JSONObject geometry=new JSONObject().put("type","LineString").put("coordinates",line);
        JSONObject road=new JSONObject().put("geometry",geometry).put("properties",new JSONObject().put("highway","residential").put("name","Test Lane "+id));
        JSONObject matched=new JSONObject().put("features",new JSONArray().put(new JSONObject().put("geometry",new JSONObject().put("type","LineString").put("coordinates",new JSONArray().put(new JSONArray().put(-.1).put(51.5)).put(new JSONArray().put(-.099).put(51.5))))));
        return new JSONObject().put("journey_id","j"+id).put("mode","driving").put("distance_meters",100).put("processing_status","complete")
                .put("processing_result",new JSONObject().put("road_geojson",new JSONObject().put("features",new JSONArray().put(road))).put("geojson",matched));
    }
    static JSONObject geometry(JSONObject journey) throws Exception {
        return journey.getJSONObject("processing_result").getJSONObject("road_geojson").getJSONArray("features").getJSONObject(0).getJSONObject("geometry");
    }
    @Test public void retainedGeometryIsLosslessIndependentAndDeduplicated() throws Exception {
        JSONObject j=journey(1,500),original=geometry(j);String expected=original.toString();
        CoreAchievementEvidence core=new CoreAchievementEvidence();core.add(j);core.add(j);
        Set<String> retained=core.named.get("test lane 1");assertEquals(1,retained.size());
        List<JSONObject> restored=CoreAchievementEvidence.geometries(retained);
        assertEquals(expected,restored.get(0).toString());assertNotSame(original,restored.get(0));
        // Releasing/mutating the parsed archive cannot change the scan's evidence.
        original.getJSONArray("coordinates").getJSONArray(0).put(0,20);
        assertEquals(expected,CoreAchievementEvidence.geometries(retained).get(0).toString());
        assertEquals(expected,CoreAchievementEvidence.geometries(core.townRoads.get("ref:Test Lane 1")).get(0).toString());
    }
    @Test public void multiLineGeometryAndNonCoordinateFieldsKeepEvidenceIdentity() throws Exception {
        JSONObject j=journey(2,5),g=geometry(j);
        g.put("type","MultiLineString").put("bbox",new JSONArray().put(-1).put(51).put(1).put(52))
                .put("coordinates",new JSONArray().put(g.getJSONArray("coordinates")).put(new JSONArray().put(new JSONArray().put(0).put(51)).put(new JSONArray().put(.01).put(51.1))));
        CoreAchievementEvidence core=new CoreAchievementEvidence();core.add(j);
        assertEquals(g.toString(),CoreAchievementEvidence.geometries(core.named.get("test lane 2")).get(0).toString());
    }
    @Test public void correctionsStillExcludeRoadsAndMatchedEdges() throws Exception {
        JSONObject j=journey(3,5).put("journey_corrections",new JSONObject().put("removed_road_ids",new JSONArray().put("name:test lane 3")).put("removed_matched_segments",new JSONArray().put(0)));
        CoreAchievementEvidence core=new CoreAchievementEvidence();core.add(j);
        assertTrue(core.named.isEmpty());assertTrue(core.townRoads.isEmpty());assertEquals(0,core.roadCoverage.metres(),0);
    }
    @Test public void repeatsRetainOneGeometryAndUniqueCoverage() throws Exception {
        CoreAchievementEvidence core=new CoreAchievementEvidence();JSONObject j=journey(4,500);
        core.add(j);double unique=core.roadCoverage.metres();
        for(int i=1;i<1605;i++)core.add(j);
        assertEquals(160500,core.roadMetres,0);assertEquals(unique,core.roadCoverage.metres(),0);
        assertEquals(1,core.named.get("test lane 4").size());assertEquals(1,core.townRoads.size());
    }
    public static void main(String[] args) throws Exception {
        // Exercise exactly the same production CoreAchievementEvidence on before/after refs.
        // 802,500 full-road vertices; matched route is intentionally separate and short.
        for(int scan=0;scan<3;scan++) {
            CoreAchievementEvidence core=new CoreAchievementEvidence();
            for(int i=0;i<1605;i++)core.add(journey(i,500));
            System.gc();Thread.sleep(100);
            long heap=Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory();
            if(core.roadMetres!=160500||core.townRoads.size()!=1605)throw new AssertionError("Evidence changed");
            System.out.println("scan="+scan+" journeys=1605 vertices=802500 retained_heap_bytes="+heap+" roads="+core.townRoads.size()+" unique_metres="+core.roadCoverage.metres());
        }
    }
}
