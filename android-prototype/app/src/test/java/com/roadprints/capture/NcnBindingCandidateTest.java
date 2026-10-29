package com.roadprints.capture;

import android.content.Context;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import java.nio.file.*;
import java.io.File;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;

/** Opt-in integration of the exact offline Stage 1.1 candidate, never phone data. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class NcnBindingCandidateTest {
    private NcnCatalogue catalogue;
    private JSONObject report;
    @Before public void candidate() throws Exception {
        String receipt=System.getenv("NCN_ATTAINABILITY_REPORT");
        Assume.assumeNotNull("External candidate receipt is required for this integration suite",receipt);
        report=new JSONObject(new String(Files.readAllBytes(Paths.get(receipt)),StandardCharsets.UTF_8));
        JSONObject manifest=report.getJSONObject("candidate_manifest");
        File input=new File(System.getenv("NCN_TEST_DATABASE"));
        assertEquals(report.getString("candidate_sha256"),NcnCatalogueFiles.digest(input));
        Context app=RuntimeEnvironment.getApplication();File dir=NcnCatalogueFiles.directory(app);
        Files.copy(input.toPath(),new File(dir,manifest.getString("sha256")+".db").toPath(),StandardCopyOption.REPLACE_EXISTING);
        NcnCatalogueFiles.write(new File(dir,"active.json"),manifest);NcnCatalogue.invalidate();
        catalogue=NcnCatalogue.load(app);assertEquals(manifest.getString("sha256"),catalogue.version);
    }
    private NcnCatalogue.Path source()throws Exception {
        return catalogue.way("1050757473").stream().filter(p->p.route.equals("NCN:1")).findFirst().get();
    }
    private JSONObject journey(NcnCatalogue.Path path,int a,int z,String mode)throws Exception {
        JSONArray coords=new JSONArray(),nodes=new JSONArray();
        for(int i=a;i<=z;i++){coords.put(path.coordinates.getJSONArray(i));nodes.put(path.nodes.get(i));}
        JSONObject geometry=new JSONObject().put("type","LineString").put("coordinates",coords);
        JSONObject data=new JSONObject().put("geojson",new JSONObject().put("features",new JSONArray().put(new JSONObject().put("geometry",geometry))));
        if(mode.equals("cycling")) {
            JSONObject feature=new JSONObject().put("geometry_feature_index",0).put("geometry_sha256",NcnJson.geometry(coords))
                .put("legs",new JSONArray().put(new JSONObject().put("from_vertex",0).put("to_vertex",coords.length()-1).put("osm_nodes",nodes)));
            feature.put("content_sha256",NcnJson.digest(feature));
            data.put("matcher","osrm_bicycle_v1").put("matching_mode","cycling").put("cycling_path_evidence",new JSONObject()
                .put("schema_version",1).put("provider","osrm").put("router_graph","bicycle").put("status","available").put("features",new JSONArray().put(feature)));
        } else {
            data.put("matcher","preloaded_pedestrian_network_v4").put("road_geojson",new JSONObject().put("features",new JSONArray().put(
                new JSONObject().put("geometry",new JSONObject().put("type","LineString").put("coordinates",path.coordinates)))));
        }
        return new JSONObject().put("mode",mode).put("processing_status","complete").put("processing_result",data);
    }
    @Test public void exactSupportedSourceCreditsAndUnadmittedPrefixDoesNot()throws Exception {
        NcnCatalogue.Path path=source();assertTrue(path.bindings.length()>0);
        for(int i=0;i<path.bindings.length();i++)assertTrue(path.bindings.getJSONObject(i).getDouble("start")>=path.positions[1]);
        NcnMatcher matcher=new NcnMatcher(catalogue);NcnLedger ledger=new NcnLedger(catalogue);
        for(String mode:new String[]{"walking","running","cycling"}) {
            JSONObject prefix=matcher.assess(journey(path,0,1,mode));
            assertTrue("Real source identity may match, but unadmitted prefix must not earn target credit",prefix.getJSONArray("spans").length()>0);
            ledger.put("prefix",prefix);assertEquals(0,ledger.metres("NCN:1","combined"),1e-7);ledger.remove("prefix");
            JSONObject supported=matcher.assess(journey(path,1,2,mode));
            ledger.put("proof",supported);assertTrue(mode,ledger.metres("NCN:1","combined")>0);ledger.remove("proof");
        }
        JSONObject walking=matcher.assess(journey(path,1,2,"walking"));ledger.put("walking",walking);
        double first=ledger.metres("NCN:1","combined");ledger.put("repeat",walking);
        ledger.put("running",matcher.assess(journey(path,1,2,"running")));ledger.put("cycling",matcher.assess(journey(path,1,2,"cycling")));
        assertEquals(first,ledger.metres("NCN:1","combined"),1e-6);
        assertEquals(first,ledger.metres("NCN:1","on_foot"),1e-6);assertEquals(first,ledger.metres("NCN:1","cycling"),1e-6);
        for(String mode:new String[]{"driving","bus","unknown","train"})assertEquals(0,matcher.assess(journey(path,1,2,mode)).getJSONArray("spans").length());
        JSONObject removed=journey(path,1,2,"walking").put("journey_corrections",new JSONObject().put("removed_matched_segments",new JSONArray().put(0)));
        assertEquals(0,matcher.assess(removed).getJSONArray("spans").length());
    }
    @Test public void syntheticSourceCapacityAgreesWithReceiptAndCannotClaimCompleteRoutes()throws Exception {
        NcnLedger ledger=new NcnLedger(catalogue);
        for(String mode:new String[]{"on_foot","cycling"}) {
            JSONArray spans=new JSONArray();
            for(String route:new String[]{"RCN:90","NCN:1"})for(NcnCatalogue.Path p:catalogue.route(route))if(p.modes.contains(mode))
                spans.put(new JSONArray().put(p.route).put(p.way).put(0).put(p.positions[p.positions.length-1]));
            ledger.put(mode,new JSONObject().put("mode",mode).put("spans",spans));
        }
        JSONArray routes=report.getJSONArray("routes");
        for(int i=0;i<routes.length();i++) {
            JSONObject row=routes.getJSONObject(i);String key=row.getString("route_key");
            if(!key.equals("RCN:90")&&!key.equals("NCN:1"))continue;
            JSONObject expected=row.getJSONObject("after");
            for(String mode:new String[]{"on_foot","cycling","combined"})assertEquals(key+mode,expected.getDouble(mode+"_m"),ledger.metres(key,mode),1e-4);
            assertTrue(NcnAchievements.percent(ledger.metres(key,"combined"),catalogue.routes.get(key).getDouble("length_m"))<100);
        }
        double union=ledger.networkMetres("combined");
        assertTrue(union>0);assertTrue(union<=ledger.networkMetres("on_foot")+ledger.networkMetres("cycling")+1e-6);
        NcnProgressStore.Snapshot snapshot=new NcnProgressStore.Snapshot(catalogue,ledger,1,new java.util.LinkedHashMap<>());
        JSONArray awards=NcnAchievements.prepare(snapshot,new JSONObject()).getJSONArray("rows");
        for(int i=0;i<awards.length();i++) {
            JSONObject award=awards.getJSONObject(i);
            if(award.getString("id").equals("ncn-route:NCN:1"))assertEquals(3,award.getInt("level"));
            if(award.getString("id").equals("ncn-route:RCN:90"))assertEquals(0,award.getInt("level"));
        }
    }
}
