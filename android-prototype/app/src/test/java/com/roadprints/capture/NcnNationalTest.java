package com.roadprints.capture;

import android.content.Context;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.io.File;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class NcnNationalTest {
    private Context app(){return RuntimeEnvironment.getApplication();}
    private JSONObject golden()throws Exception{return new JSONObject(new String(NcnCatalogue.read(getClass().getResourceAsStream("/ncn-national-golden.json")),StandardCharsets.UTF_8));}
    @org.junit.Before public void installExternalTestReference()throws Exception{
        String source=System.getenv("NCN_TEST_DATABASE");assertNotNull("Run scripts/prepare_ncn_reference.py before Gradle",source);
        Context context=app();JSONObject manifest=new JSONObject(new String(NcnCatalogue.read(context.getAssets().open("ncn-national/manifest.json")),StandardCharsets.UTF_8));
        File dir=NcnCatalogueFiles.directory(context),database=new File(dir,manifest.getString("sha256")+".db");
        Files.copy(new File(source).toPath(),database.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        NcnCatalogueFiles.write(new File(dir,"active.json"),manifest);NcnCatalogue.invalidate();
    }
    @Test public void nationalIndexedCatalogueGoldenModesUnionsAndEdits()throws Exception{
        NcnCatalogue c=NcnCatalogue.load(app());assertEquals(219,c.routes.size());assertTrue(c.paths.isEmpty());assertTrue(c.networkLength>1_000_000);assertFalse(c.routes.get("NCN:228").getBoolean("measurable"));
        NcnMatcher matcher=new NcnMatcher(c);JSONArray cases=golden().getJSONArray("cases");NcnLedger ledger=new NcnLedger(c);
        for(int i=0;i<cases.length();i++){JSONObject row=cases.getJSONObject(i);JSONObject trip=row.getJSONObject("journey");JSONObject evidence=matcher.assess(trip);NcnLedger one=new NcnLedger(c);one.put("one",evidence);assertEquals(row.getString("name")+evidence.toString(),row.getDouble("expected_metres"),one.metres("NCN:344","combined"),.001);assertTrue(one.networkMetres("combined")>0);}
        JSONObject foot=matcher.assess(cases.getJSONObject(0).getJSONObject("journey")),cycle=matcher.assess(cases.getJSONObject(1).getJSONObject("journey"));ledger.put("foot",foot);double f=ledger.networkMetres("combined");ledger.put("repeat",foot);assertEquals(f,ledger.networkMetres("combined"),.00001);ledger.put("cycle",cycle);double both=ledger.networkMetres("combined");assertTrue(both>=f);assertTrue(both<=ledger.networkMetres("on_foot")+ledger.networkMetres("cycling")+.00001);
        JSONObject drive=cases.getJSONObject(0).getJSONObject("journey");drive.put("mode","driving");ledger.put("foot",matcher.assess(drive));ledger.remove("repeat");assertEquals(0,ledger.networkMetres("on_foot"),0);ledger.remove("cycle");assertEquals(0,ledger.networkMetres("combined"),0);
        for(String mode:new String[]{"unknown","bus","train","flight"}){drive.put("mode",mode);assertEquals(0,matcher.assess(drive).getJSONArray("spans").length());}
        // Same verified physical way in two route projections must remain one network.
        java.util.List<NcnCatalogue.Path> shared=c.way("23384774");assertTrue(shared.size()>=2);JSONObject spans=new JSONObject().put("mode","cycling").put("spans",new JSONArray());
        for(NcnCatalogue.Path p:shared)spans.getJSONArray("spans").put(new JSONArray().put(p.route).put(p.way).put(0).put(p.positions[p.positions.length-1]));
        NcnLedger sharedLedger=new NcnLedger(c);sharedLedger.put("shared",spans);double network=sharedLedger.networkMetres("combined");assertTrue(network>0);sharedLedger.put("repeat",spans);assertEquals(network,sharedLedger.networkMetres("combined"),.00001);
        final int[] count={0};c.targets("NCN:344",(key,length,coords)->{assertTrue(length>0);assertEquals(2,coords.length());count[0]++;});assertTrue(count[0]>0);assertFalse(c.overview("NCN:344").isEmpty());
    }
    @Test public void nationalCheckpointsResumeDeleteAndKeepModeNavigation()throws Exception{
        Context app=app();app.getSharedPreferences("roadprints_journeys_v1",0).edit().putBoolean("journeys_migrated_to_archive",true).commit();app.getSharedPreferences("roadprints_ncn_national_v2",0).edit().putBoolean("enabled",true).commit();
        JSONObject trip=golden().getJSONArray("cases").getJSONObject(0).getJSONObject("journey");File archive=new File(app.getFilesDir(),"journey_foot.json");Files.write(archive.toPath(),trip.toString().getBytes(StandardCharsets.UTF_8));
        java.lang.reflect.Method assess=NcnProgressStore.class.getDeclaredMethod("assess",Context.class);assess.setAccessible(true);NcnProgressStore.Snapshot first=(NcnProgressStore.Snapshot)assess.invoke(null,app);assertTrue(first.ledger.metres("NCN:344","combined")>0);
        File cache=new File(app.getFilesDir(),"ncn-national-assessments/"+first.catalogue.version+"/"+NcnJson.hash("foot".getBytes(StandardCharsets.UTF_8))+".json");byte[] checkpoint=Files.readAllBytes(cache.toPath());cache.setLastModified(1000000);
        NcnProgressStore.Snapshot resumed=(NcnProgressStore.Snapshot)assess.invoke(null,app);assertArrayEquals(checkpoint,Files.readAllBytes(cache.toPath()));assertEquals(1000000,cache.lastModified());assertEquals(first.ledger.networkMetres("combined"),resumed.ledger.networkMetres("combined"),.00001);
        assertTrue(archive.delete());NcnProgressStore.Snapshot deleted=(NcnProgressStore.Snapshot)assess.invoke(null,app);assertEquals(0,deleted.ledger.networkMetres("combined"),0);assertFalse(cache.exists());
        NcnProgressStore.mode(app,"cycling");RoadDetailSelection selection=new RoadDetailSelection(RoadDetailSelection.NCN,"RCN:90","","","");selection.ncnMode=NcnProgressStore.mode(app);assertEquals("cycling",RoadDetailSelection.from(selection.intent(app,false)).ncnMode);assertFalse(selection.intent(app,false).hasExtra("journey_ids"));app.getSharedPreferences("roadprints_ncn_national_v2",0).edit().putBoolean("enabled",false).commit();
    }
}
