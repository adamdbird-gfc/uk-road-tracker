package com.roadprints.capture;

import android.content.Context;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

/** Exact finite Chapel Lane candidate, opt-in only; synthetic evidence, never user data. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class NcnFiveCaseCandidateTest {
    private NcnCatalogue catalogue;
    private JSONObject review;
    @Before public void selectCandidate() throws Exception {
        String reviewPath=System.getenv("NCN_FIVE_CASE_REVIEW");
        Assume.assumeNotNull("Five-case candidate review required",reviewPath);
        review=new JSONObject(new String(Files.readAllBytes(Paths.get(reviewPath)),StandardCharsets.UTF_8));
        String receipt=System.getenv("NCN_ATTAINABILITY_REPORT");assertNotNull(receipt);
        JSONObject report=new JSONObject(new String(Files.readAllBytes(Paths.get(receipt)),StandardCharsets.UTF_8));
        JSONObject manifest=report.getJSONObject("candidate_manifest");File input=new File(System.getenv("NCN_TEST_DATABASE"));
        assertEquals(report.getString("candidate_sha256"),NcnCatalogueFiles.digest(input));
        Context app=RuntimeEnvironment.getApplication();File dir=NcnCatalogueFiles.directory(app);
        Files.copy(input.toPath(),new File(dir,manifest.getString("sha256")+".db").toPath(),StandardCopyOption.REPLACE_EXISTING);
        NcnCatalogueFiles.write(new File(dir,"active.json"),manifest);NcnCatalogue.invalidate();catalogue=NcnCatalogue.load(app);
    }
    private JSONObject span(String way,double a,double z,String mode)throws Exception {
        return new JSONObject().put("mode",mode).put("spans",new JSONArray().put(new JSONArray().put("NCN:1").put(way).put(a).put(z)));
    }
    private JSONObject journey(NcnCatalogue.Path path,int i,String mode)throws Exception {
        JSONArray coords=new JSONArray().put(path.coordinates.getJSONArray(i)).put(path.coordinates.getJSONArray(i+1));
        JSONObject geometry=new JSONObject().put("type","LineString").put("coordinates",coords);
        JSONObject data=new JSONObject().put("geojson",new JSONObject().put("features",new JSONArray().put(new JSONObject().put("geometry",geometry))));
        if(mode.equals("cycling")) {
            JSONObject feature=new JSONObject().put("geometry_feature_index",0).put("geometry_sha256",NcnJson.geometry(coords))
                .put("legs",new JSONArray().put(new JSONObject().put("from_vertex",0).put("to_vertex",1).put("osm_nodes",new JSONArray().put(path.nodes.get(i)).put(path.nodes.get(i+1)))));
            feature.put("content_sha256",NcnJson.digest(feature));
            data.put("matcher","osrm_bicycle_v1").put("matching_mode","cycling").put("cycling_path_evidence",new JSONObject()
                .put("schema_version",1).put("provider","osrm").put("router_graph","bicycle").put("status","available").put("features",new JSONArray().put(feature)));
        } else {
            data.put("matcher","preloaded_pedestrian_network_v4").put("road_geojson",new JSONObject().put("features",new JSONArray().put(new JSONObject().put("geometry",new JSONObject().put("type","LineString").put("coordinates",path.coordinates)))));
        }
        return new JSONObject().put("mode",mode).put("processing_status","complete").put("processing_result",data);
    }
    @Test public void everySourceEdgeUsesExistingWalkingRunningCyclingMatcher()throws Exception {
        NcnMatcher matcher=new NcnMatcher(catalogue);
        JSONArray cases=review.getJSONArray("cases");
        for(int k=0;k<cases.length();k++) {
            JSONObject c=cases.getJSONObject(k);String route=c.getString("route");
            NcnCatalogue.Path path=catalogue.way(c.getString("way")).stream().filter(p->p.route.equals(route)).findFirst().get();
            JSONArray rows=c.getJSONObject("path").getJSONArray("bindings");rows=new JSONArray(rows.toString());
            JSONArray additions=c.getJSONArray("bindings");for(int j=0;j<additions.length();j++)rows.put(additions.getJSONObject(j));
            for(int i=0;i<path.positions.length-1;i++) {
                double expected=0;
                for(int j=0;j<rows.length();j++) {
                    JSONObject b=rows.getJSONObject(j);double a=b.getDouble("start"),z=b.getDouble("end");
                    double overlap=Math.min(z,path.positions[i+1])-Math.max(a,path.positions[i]);
                    if(overlap>0)expected+=overlap/(z-a)*Math.abs(b.getDouble("high")-b.getDouble("low"));
                }
                for(String mode:new String[]{"walking","running","cycling"}) {
                    NcnLedger ledger=new NcnLedger(catalogue);ledger.put("edge",matcher.assess(journey(path,i,mode)));
                    assertEquals(route+" edge "+i+" "+mode,expected,ledger.metres(route,"combined"),1e-5);
                }
                assertEquals(0,matcher.assess(journey(path,i,"driving")).getJSONArray("spans").length());
            }
        }
    }
    @Test public void partialRepeatEditDeleteAndUnapprovedRoadControls()throws Exception {
        JSONArray cases=review.getJSONArray("cases");
        for(int k=0;k<cases.length();k++) {
            JSONObject c=cases.getJSONObject(k),b=c.getJSONArray("bindings").getJSONObject(0);
            String route=c.getString("route"),way=c.getString("way");double a=b.getDouble("start"),z=b.getDouble("end");
            double expected=Math.abs(b.getDouble("high")-b.getDouble("low"))*.5;
            NcnLedger ledger=new NcnLedger(catalogue);
            JSONObject half=new JSONObject().put("mode","on_foot").put("spans",new JSONArray().put(new JSONArray().put(route).put(way).put(a).put((a+z)/2)));
            ledger.put("a",half);ledger.put("repeat",half);assertEquals(expected,ledger.metres(route,"combined"),1e-5);
            ledger.remove("repeat");ledger.put("a",new JSONObject().put("mode","cycling").put("spans",new JSONArray().put(new JSONArray().put(route).put(way).put(a).put(a+(z-a)/4))));
            assertEquals(expected/2,ledger.metres(route,"combined"),1e-5);
            ledger.remove("a");assertEquals(0,ledger.metres(route,"combined"),1e-7);
        }
        assertTrue(catalogue.way("77611523").isEmpty());assertTrue(catalogue.way("23385711").isEmpty());
        // The Highland road keeps its original two finite bindings only.
        NcnCatalogue.Path highland=catalogue.way("463641622").stream().filter(p->p.route.equals("NCN:7")).findFirst().get();
        NcnLedger ledger=new NcnLedger(catalogue);ledger.put("held-road",new NcnMatcher(catalogue).assess(journey(highland,8,"walking")));
        assertEquals(0,ledger.metres("NCN:7","combined"),1e-7);
    }
}
