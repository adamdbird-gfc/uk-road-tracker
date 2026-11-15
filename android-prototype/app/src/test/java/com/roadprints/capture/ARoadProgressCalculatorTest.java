package com.roadprints.capture;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class ARoadProgressCalculatorTest {
    @Test public void bundledCanonicalARoadProducesAggregateCoverageAndMapSections() throws Exception {
        Context app=RuntimeEnvironment.getApplication();
        JSONObject reference;
        try(BufferedReader reader=new BufferedReader(new InputStreamReader(
                app.getAssets().open("GB-A1.json"),StandardCharsets.UTF_8))){
            StringBuilder text=new StringBuilder();String line;while((line=reader.readLine())!=null)text.append(line);
            reference=new JSONObject(text.toString());
        }
        JSONArray path=reference.getJSONArray("paths").getJSONArray(0);
        JSONArray route=new JSONArray();
        for(int i=0;i<path.length();i++){
            JSONArray p=path.getJSONArray(i);
            route.put(new JSONArray().put(p.getDouble(0)).put(p.getDouble(1)));
        }
        JSONObject feature=new JSONObject().put("type","Feature")
                .put("properties",new JSONObject().put("road_ref","A1")
                        .put("road_region","GB").put("distance_m",1000))
                .put("geometry",new JSONObject().put("type","LineString").put("coordinates",route));
        JSONObject journey=new JSONObject().put("journey_id","a1-1").put("mode","driving")
                .put("processing_status","complete")
                .put("processing_result",new JSONObject().put("a_road_geojson",
                        new JSONObject().put("type","FeatureCollection")
                                .put("features",new JSONArray().put(feature))));
        ARoadProgressCalculator calculator=new ARoadProgressCalculator(app);
        calculator.addJourney(journey);
        ARoadProgressCalculator.Summary summary=calculator.finish();
        assertEquals(1,summary.roads.size());
        ARoadProgressCalculator.Road road=summary.roads.get(0);
        assertTrue(road.referenceAvailable);
        assertTrue(road.percent()>0);
        assertTrue(road.percent()<=100);
        assertFalse(road.coveredMapSections.isEmpty());
        assertFalse(road.incompleteMapSections.isEmpty());
        assertEquals(1000,road.matchedMetres,0.001);
    }

    @Test public void statisticsCoverageMatchesMapWithoutRetainingGeometryOrLookupData() throws Exception {
        Context app=RuntimeEnvironment.getApplication();
        JSONObject reference;
        try(BufferedReader reader=new BufferedReader(new InputStreamReader(
                app.getAssets().open("GB-A1.json"),StandardCharsets.UTF_8))){
            StringBuilder text=new StringBuilder();String line;while((line=reader.readLine())!=null)text.append(line);
            reference=new JSONObject(text.toString());
        }
        JSONArray path=reference.getJSONArray("paths").getJSONArray(0);
        JSONArray route=new JSONArray();
        for(int i=0;i<path.length();i++){
            JSONArray p=path.getJSONArray(i);
            route.put(new JSONArray().put(p.getDouble(0)).put(p.getDouble(1)));
        }
        JSONObject feature=new JSONObject().put("type","Feature")
                .put("properties",new JSONObject().put("road_ref","A1")
                        .put("road_region","GB").put("distance_m",1000))
                .put("geometry",new JSONObject().put("type","LineString").put("coordinates",route));
        JSONObject journey=new JSONObject().put("journey_id","a1-1").put("mode","driving")
                .put("processing_status","complete")
                .put("processing_result",new JSONObject().put("a_road_geojson",
                        new JSONObject().put("type","FeatureCollection")
                                .put("features",new JSONArray().put(feature))));

        ARoadProgressCalculator map=new ARoadProgressCalculator(app);
        ARoadProgressCalculator stats=new ARoadProgressCalculator(app,false);
        map.addJourney(journey);stats.addJourney(journey);
        JSONObject repeated=new JSONObject(journey.toString()).put("journey_id","a1-repeat");
        map.addJourney(repeated);stats.addJourney(repeated);
        ARoadProgressCalculator.Summary mapped=map.finish(), counted=stats.finish();
        ARoadProgressCalculator.Road detail=counted.roads.get(0), visible=mapped.roads.get(0);
        assertEquals(visible.percent(),detail.percent(),0.000001);
        assertEquals(mapped.totalKm(),counted.totalKm(),0.000001);
        assertEquals(2000,detail.matchedMetres,0.001);
        assertEquals(2,detail.journeyIds.size());
        assertTrue(detail.referenceSections>0);
        assertTrue(detail.anchors.isEmpty());assertTrue(detail.grid.size()==0);
        assertTrue(visible.anchors.isEmpty());assertTrue(visible.grid.size()==0);
        assertTrue(detail.coveredMapSections.isEmpty());assertTrue(detail.incompleteMapSections.isEmpty());
        assertFalse(visible.coveredMapSections.isEmpty());
        JSONArray contribution = new ARoadProgressCalculator(app, false).contribution(journey);
        ARoadProgressCalculator cached = new ARoadProgressCalculator(app, false);
        assertTrue(cached.accepts(contribution));
        assertEquals(0, cached.referenceReads());
        JSONObject row = contribution.getJSONObject(0);
        String signature = row.getString("reference");
        row.put("reference", "changed");
        assertFalse(cached.accepts(contribution));
        row.put("reference", signature);
        cached.addContribution("a1-1", contribution);
        cached.addContribution("a1-repeat", contribution);
        ARoadProgressCalculator.Road restored = cached.finish().roads.get(0);
        assertEquals(detail.percent(), restored.percent(), 0.000001);
        assertEquals(0, cached.referenceReads());
        assertTrue(cached.populateMapSections(restored));
        assertEquals(1, cached.referenceReads());
        assertEquals(visible.coveredMapSections.toString(), restored.coveredMapSections.toString());
        assertEquals(visible.incompleteMapSections.toString(), restored.incompleteMapSections.toString());
        assertEquals(0, cached.peakRetainedAnchors());
        assertSame(counted,stats.finish());assertSame(mapped,map.finish());
    }
}

