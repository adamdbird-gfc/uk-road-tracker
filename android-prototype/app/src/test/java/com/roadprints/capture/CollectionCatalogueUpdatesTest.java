package com.roadprints.capture;

import android.content.Context;
import android.util.AtomicFile;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class CollectionCatalogueUpdatesTest {
    private Context app;
    private File cache;
    @Before public void reset(){
        app=RuntimeEnvironment.getApplication();
        cache=new File(app.getFilesDir(),"wetherspoons-map-cache-v1.json");
        new AtomicFile(cache).delete();
        CollectionCatalogueUpdates.resetForTests();
        app.getSharedPreferences(CollectionAccessStore.PREFS,0).edit().clear().commit();
        app.getSharedPreferences("roadprints_collections_v1",0).edit().clear().commit();
        app.getSharedPreferences("roadprints_spoons_updates_v1",0).edit().clear().commit();
    }
    @After public void clean(){new AtomicFile(cache).delete();CollectionCatalogueUpdates.resetForTests();}
    private JSONObject catalogue() throws Exception {
        try(java.io.InputStream input=app.getAssets().open("collections/uk-wetherspoons-map-v1.json")){
            JSONObject root=CollectionMapCatalog.readCatalogue(input);
            root.put("catalogue_version","2026-10-13.test");
            root.getJSONArray("venues").getJSONObject(0).put("name","Updated pub");
            return root;
        }
    }
    private JSONObject manifest(byte[] bytes,long revision,String version) throws Exception {
        return new JSONObject().put("schema_version",1).put("collection_id","wetherspoons")
                .put("revision",revision).put("filename","catalogue-"+revision+".json")
                .put("bytes",bytes.length).put("sha256",CollectionCatalogueUpdates.sha256(bytes))
                .put("catalogue_version",version);
    }
    private boolean update(JSONObject data,long revision) throws Exception {
        byte[] bytes=data.toString().getBytes(StandardCharsets.UTF_8);
        JSONObject m=manifest(bytes,revision,data.getString("catalogue_version"));
        return CollectionCatalogueUpdates.refreshNow(app,(path,limit)->
                path.equals("manifest-v1.json")?m.toString().getBytes(StandardCharsets.UTF_8):bytes);
    }
    @Test public void updateSurvivesRestartOfflineAndDoesNotAwardVisits() throws Exception {
        JSONArray before=CollectionMapCatalog.spoons(app);
        String id=before.getJSONObject(0).getString("id");
        assertEquals(811,before.length());
        assertTrue(update(catalogue(),(CollectionCatalogueUpdates.BUNDLED_REVISION+1)));
        assertEquals("Updated pub",CollectionMapCatalog.spoons(app).getJSONObject(0).getString("name"));
        assertEquals(id,CollectionMapCatalog.spoons(app).getJSONObject(0).getString("id"));
        CollectionCatalogueUpdates.resetForTests();
        assertEquals("Updated pub",CollectionMapCatalog.spoons(app).getJSONObject(0).getString("name"));
        assertTrue(app.getSharedPreferences("roadprints_collections_v1",0).getAll().isEmpty());
        assertEquals(CollectionAccessStore.NONE,CollectionAccessStore.selected(app));
    }
    @Test public void unchangedManifestDoesNotDownloadPayload() throws Exception {
        JSONObject data=catalogue();
        byte[] bytes=data.toString().getBytes(StandardCharsets.UTF_8);
        JSONObject m=manifest(bytes,CollectionCatalogueUpdates.BUNDLED_REVISION,data.getString("catalogue_version"));
        int[] requests={0};
        assertFalse(CollectionCatalogueUpdates.refreshNow(app,(path,limit)->{
            requests[0]++;
            assertEquals("manifest-v1.json",path);
            return m.toString().getBytes(StandardCharsets.UTF_8);
        }));
        assertEquals(1,requests[0]);assertFalse(cache.exists());
    }
    @Test public void interruptedDownloadKeepsLastGoodCache() throws Exception {
        assertTrue(update(catalogue(),(CollectionCatalogueUpdates.BUNDLED_REVISION+1)));
        byte[] saved=Files.readAllBytes(cache.toPath());
        try{
            CollectionCatalogueUpdates.refreshNow(app,(path,limit)->{throw new java.io.IOException("offline");});
            fail("Offline transport must fail");
        }catch(java.io.IOException expected){}
        assertArrayEquals(saved,Files.readAllBytes(cache.toPath()));
        assertEquals("Updated pub",CollectionMapCatalog.spoons(app).getJSONObject(0).getString("name"));
    }
    @Test public void payloadHashAndLengthFailuresNeverReplaceGoodCache() throws Exception {
        assertTrue(update(catalogue(),(CollectionCatalogueUpdates.BUNDLED_REVISION+1)));
        byte[] saved=Files.readAllBytes(cache.toPath());
        JSONObject data=catalogue();
        byte[] bytes=data.toString().getBytes(StandardCharsets.UTF_8);
        JSONObject m=manifest(bytes,(CollectionCatalogueUpdates.BUNDLED_REVISION+2),data.getString("catalogue_version"));
        for(boolean corruptHash:new boolean[]{true,false}){
            m.put("sha256",CollectionCatalogueUpdates.sha256(bytes));
            m.put("bytes",bytes.length);
            if(corruptHash)m.put("sha256",new String(new char[64]).replace('\0','0'));
            else m.put("bytes",bytes.length+1);
            try{
                CollectionCatalogueUpdates.refreshNow(app,(path,limit)->
                        path.equals("manifest-v1.json")?m.toString().getBytes(StandardCharsets.UTF_8):bytes);
                fail("Corrupt download must fail");
            }catch(IllegalArgumentException expected){}
            assertArrayEquals(saved,Files.readAllBytes(cache.toPath()));
        }
    }
    @Test public void auditVisitEnabledDuplicateAndWrongSchemaAreRejected() throws Exception {
        for(int scenario=0;scenario<6;scenario++){
            JSONObject data=catalogue();
            if(scenario==0)data.put("release_status","audit_only");
            if(scenario==1)data.put("visit_enabled",true);
            if(scenario==2)data.getJSONArray("venues").getJSONObject(0).put("visit_enabled",true);
            if(scenario==3)data.put("schema_version",2);
            if(scenario==4)data.getJSONArray("venues").getJSONObject(1)
                    .put("id",data.getJSONArray("venues").getJSONObject(0).getString("id"));
            if(scenario==5)data.getJSONArray("venues").getJSONObject(0).put("lat",0);
            try{update(data,(CollectionCatalogueUpdates.BUNDLED_REVISION+1));fail("Invalid catalogue must fail");}
            catch(IllegalArgumentException expected){}
            assertFalse(cache.exists());assertEquals(811,CollectionMapCatalog.spoons(app).length());
        }
    }
    @Test public void cachedDowngradeAndTamperingFallBackToBundledMap() throws Exception {
        assertTrue(update(catalogue(),(CollectionCatalogueUpdates.BUNDLED_REVISION+2)));
        JSONObject older=catalogue();older.getJSONArray("venues").getJSONObject(0).put("name","Old pub");
        assertFalse(update(older,(CollectionCatalogueUpdates.BUNDLED_REVISION+1)));
        assertEquals("Updated pub",CollectionMapCatalog.spoons(app).getJSONObject(0).getString("name"));
        JSONObject stored=new JSONObject(new String(Files.readAllBytes(cache.toPath()),StandardCharsets.UTF_8));
        stored.put("payload",stored.getString("payload").replace("Updated pub","Tampered pub"));
        Files.write(cache.toPath(),stored.toString().getBytes(StandardCharsets.UTF_8));
        CollectionCatalogueUpdates.resetForTests();
        assertNotEquals("Tampered pub",CollectionMapCatalog.spoons(app).getJSONObject(0).getString("name"));
        assertEquals(811,CollectionMapCatalog.spoons(app).length());
    }
    @Test public void manifestCannotRedirectToOtherData() throws Exception {
        JSONObject data=catalogue();
        byte[] bytes=data.toString().getBytes(StandardCharsets.UTF_8);
        JSONObject m=manifest(bytes,(CollectionCatalogueUpdates.BUNDLED_REVISION+1),data.getString("catalogue_version"));
        m.put("filename","https://example.com/pub-data");
        try{CollectionCatalogueUpdates.validateManifest(m);fail("Unexpected filename must fail");}
        catch(IllegalArgumentException expected){}
    }
    @Test public void sizeBoundAndDailyCooldownAreEnforced() throws Exception {
        try{
            CollectionCatalogueUpdates.readBounded(new ByteArrayInputStream(new byte[32769]),32768);
            fail("Oversized manifest must fail");
        }catch(java.io.IOException expected){}
        long now=12345;
        assertTrue(CollectionCatalogueUpdates.due(now,0));
        assertFalse(CollectionCatalogueUpdates.due(now,now+CollectionCatalogueUpdates.DAY_MS));
        assertTrue(CollectionCatalogueUpdates.due(now,now+CollectionCatalogueUpdates.DAY_MS+1));
        CollectionCatalogueUpdates.refreshIfDue(app,()->fail("Uninstalled collection must not fetch"));
        assertTrue(app.getSharedPreferences("roadprints_spoons_updates_v1",0).getAll().isEmpty());
    }
}
