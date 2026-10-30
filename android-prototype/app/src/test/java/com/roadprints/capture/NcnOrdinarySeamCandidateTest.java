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

/** Public reference witnesses only; no production changes or personal journeys. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class NcnOrdinarySeamCandidateTest {
    private NcnCatalogue catalogue;
    private JSONArray witnesses;

    @Before public void selectCandidate() throws Exception {
        String receipt=System.getenv("NCN_ATTAINABILITY_REPORT");
        Assume.assumeNotNull("Seam candidate receipt required",receipt);
        JSONObject report=new JSONObject(new String(Files.readAllBytes(Paths.get(receipt)),StandardCharsets.UTF_8));
        // The ordinary suite's separate offset candidate receipt has no seam witnesses.
        Assume.assumeTrue("Ordinary seam candidate required",report.has("consumer_witnesses"));
        witnesses=report.getJSONArray("consumer_witnesses");assertTrue(witnesses.length()>=2);
        JSONObject manifest=report.getJSONObject("candidate_manifest");
        File input=new File(System.getenv("NCN_TEST_DATABASE"));
        assertEquals(report.getString("candidate_sha256"),NcnCatalogueFiles.digest(input));
        Context app=RuntimeEnvironment.getApplication();File dir=NcnCatalogueFiles.directory(app);
        Files.copy(input.toPath(),new File(dir,manifest.getString("sha256")+".db").toPath(),StandardCopyOption.REPLACE_EXISTING);
        NcnCatalogueFiles.write(new File(dir,"active.json"),manifest);NcnCatalogue.invalidate();
        catalogue=NcnCatalogue.load(app);assertEquals(manifest.getString("sha256"),catalogue.version);
    }

    private JSONObject evidence(String route,String way,double a,double z,String mode)throws Exception {
        return new JSONObject().put("mode",mode).put("spans",new JSONArray().put(new JSONArray().put(route).put(way).put(a).put(z)));
    }

    @Test public void partialSeamEvidenceCannotAwardTheOtherHalf()throws Exception {
        for(int i=0;i<witnesses.length();i++) {
            JSONObject w=witnesses.getJSONObject(i);String route=w.getString("route"),way=w.getString("way");
            JSONArray bindings=w.getJSONArray("bindings");assertEquals(2,bindings.length());
            for(int j=0;j<bindings.length();j++)for(String mode:new String[]{"on_foot","cycling"}) {
                JSONObject b=bindings.getJSONObject(j);double a=b.getDouble("start"),z=b.getDouble("end");
                double expected=Math.abs(b.getDouble("high")-b.getDouble("low"))*.5;
                NcnLedger ledger=new NcnLedger(catalogue);
                JSONObject half=evidence(route,way,a+(z-a)*.25,a+(z-a)*.75,mode);
                ledger.put("partial",half);assertEquals(expected,ledger.metres(route,"combined"),1e-5);
                ledger.put("repeat",half);assertEquals(expected,ledger.metres(route,"combined"),1e-5);
                ledger.remove("repeat");ledger.put("partial",evidence(route,way,a,a+(z-a)*.25,mode));
                assertEquals(expected*.5,ledger.metres(route,"combined"),1e-5);
                ledger.remove("partial");assertEquals(0,ledger.metres(route,"combined"),1e-7);
            }
        }
    }

    private JSONObject journey(NcnCatalogue.Path path,int vertex,String mode)throws Exception {
        JSONArray coords=new JSONArray().put(path.coordinates.getJSONArray(vertex)).put(path.coordinates.getJSONArray(vertex+1));
        JSONObject geometry=new JSONObject().put("type","LineString").put("coordinates",coords);
        JSONObject data=new JSONObject().put("geojson",new JSONObject().put("features",new JSONArray().put(new JSONObject().put("geometry",geometry))));
        if(mode.equals("cycling")) {
            JSONArray nodes=new JSONArray().put(path.nodes.get(vertex)).put(path.nodes.get(vertex+1));
            JSONObject feature=new JSONObject().put("geometry_feature_index",0).put("geometry_sha256",NcnJson.geometry(coords))
                .put("legs",new JSONArray().put(new JSONObject().put("from_vertex",0).put("to_vertex",1).put("osm_nodes",nodes)));
            feature.put("content_sha256",NcnJson.digest(feature));
            data.put("matcher","osrm_bicycle_v1").put("matching_mode","cycling").put("cycling_path_evidence",new JSONObject()
                .put("schema_version",1).put("provider","osrm").put("router_graph","bicycle").put("status","available").put("features",new JSONArray().put(feature)));
        } else {
            data.put("matcher","preloaded_pedestrian_network_v4").put("road_geojson",new JSONObject().put("features",new JSONArray().put(
                new JSONObject().put("geometry",new JSONObject().put("type","LineString").put("coordinates",path.coordinates)))));
        }
        return new JSONObject().put("mode",mode).put("processing_status","complete").put("processing_result",data);
    }

    @Test public void existingMatcherConsumesRealSourceEdgeAcrossEachSeam()throws Exception {
        for(int i=0;i<witnesses.length();i++) {
            JSONObject w=witnesses.getJSONObject(i);String route=w.getString("route"),way=w.getString("way");int vertex=w.getInt("source_vertex");
            NcnCatalogue.Path path=catalogue.way(way).stream().filter(p->p.route.equals(route)).findFirst().get();
            NcnMatcher matcher=new NcnMatcher(catalogue);
            for(String mode:new String[]{"walking","running","cycling"}) {
                NcnLedger ledger=new NcnLedger(catalogue);
                ledger.put("edge",matcher.assess(journey(path,vertex,mode)));
                assertEquals(route+mode,w.getDouble("source_edge_target_m"),ledger.metres(route,"combined"),1e-4);
                assertTrue(ledger.metres(route,"combined")>0);
            }
            assertEquals(0,matcher.assess(journey(path,vertex,"driving")).getJSONArray("spans").length());
        }
    }
}
