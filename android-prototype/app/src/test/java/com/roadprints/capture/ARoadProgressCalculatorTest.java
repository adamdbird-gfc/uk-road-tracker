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
}
