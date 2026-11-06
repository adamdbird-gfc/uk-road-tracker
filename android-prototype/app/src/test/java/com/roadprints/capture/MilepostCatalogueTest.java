package com.roadprints.capture;

import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

/** Validation fixtures remain in test sources; none is a production approved post. */
@RunWith(RobolectricTestRunner.class) @Config(sdk=28,manifest=Config.NONE)
public class MilepostCatalogueTest {
    MilepostAchievementsTest f=new MilepostAchievementsTest();
    @Before public void setup()throws Exception{f.setup();}
    @After public void cleanup()throws Exception{f.cleanup();}
    JSONObject envelope()throws Exception{return new JSONObject().put("schema_version",1).put("revision",2)
        .put("scope","approved-physical-mileposts").put("evaluator",MilepostDiscoveryLedger.EVALUATOR)
        .put("licence","ODbL-1.0").put("attribution","synthetic fixture only").put("source_url","https://example.invalid/test")
        .put("records",new JSONArray().put(record()));}
    JSONObject record()throws Exception{
        MilepostDiscoveryLedger.Marker marker=f.marker("fixture","mills",true,true);
        return new JSONObject().put("status","approved").put("identity_status","verified").put("location_status","verified").put("scope","UK")
            .put("physical_marker_id","roadprints:millennium:test-only").put("longitude",marker.lon).put("latitude",marker.lat)
            .put("designer","mills").put("designer_verified",true).put("designer_evidence","test-only")
            .put("aliases",new JSONArray().put("osm:node:123")).put("coordinate_evidence","test-only")
            .put("precision_resolution","test-only").put("validity_evidence","test-only").put("reviewed_at","2026-11-06T00:00:00Z")
            .put("valid_from","2000-01-01T00:00:00Z").put("valid_until","2030-01-01T00:00:00Z")
            .put("evidence",new JSONArray().put(new JSONObject().put("url","https://example.invalid/test")
                .put("licence","test-only").put("attribution","test-only").put("observed_at","2026-11-06T00:00:00Z")
                .put("receipt_sha256","a".repeat(64))));
    }
    byte[] bytes(JSONObject root){return root.toString().getBytes(StandardCharsets.UTF_8);}
    List<MilepostDiscoveryLedger.Marker> parse(JSONObject root,int count)throws Exception{
        byte[] data=bytes(root);return MilepostCatalogue.parse(data,NcnJson.hash(data),2,count);
    }
    @Test public void bundledApprovalCheckpointFailsClosedWithoutTouchingLedger()throws Exception{f.work(()->{
        assertFalse(MilepostCatalogue.READY);assertFalse(MilepostIntegration.PRODUCTION.enabled);
        assertEquals(0,MilepostCatalogue.load(f.c).size());
        assertEquals(MilepostCatalogue.fingerprint(),MilepostDiscoveryLedger.markerFingerprint(Collections.emptyList()));
        f.posts(1);String old=MilepostAchievements.catalogue(f.ledger.db());
        assertThrows(java.io.IOException.class,()->MilepostCatalogue.install(f.c,f.ledger));
        assertEquals(old,MilepostAchievements.catalogue(f.ledger.db()));
    });}
    @Test public void checksumAndCountMismatchRejectBeforeInstallation()throws Exception{
        JSONObject root=envelope();assertThrows(java.io.IOException.class,()->MilepostCatalogue.parse(bytes(root),"0".repeat(64),2,1));
        assertThrows(java.io.IOException.class,()->parse(root,2));
        assertThrows(java.io.IOException.class,()->MilepostCatalogue.parse(new byte[MilepostCatalogue.MAX_BYTES+1],"0".repeat(64),2,1));
    }
    @Test public void incompatibleSchemaScopeRevisionEvaluatorAndLicenceReject()throws Exception{
        for(String key:new String[]{"schema_version","revision","scope","evaluator","licence"}){
            JSONObject root=envelope();root.put(key,key.equals("schema_version")||key.equals("revision")?99:"unsupported");
            assertThrows(Exception.class,()->parse(root,1));
        }
    }
    @Test public void unapprovedMissingProvenanceUnknownScopeAndInvalidDatesReject()throws Exception{
        for(String key:new String[]{"status","identity_status","location_status","scope","coordinate_evidence","precision_resolution","validity_evidence","reviewed_at","valid_from","valid_until","designer_evidence"}){
            JSONObject root=envelope();root.getJSONArray("records").getJSONObject(0).put(key,"");assertThrows(Exception.class,()->parse(root,1));
        }
        JSONObject root=envelope();root.getJSONArray("records").getJSONObject(0).put("evidence",new JSONArray());assertThrows(Exception.class,()->parse(root,1));
    }
    @Test public void duplicatePhysicalIdentityAndAliasesReject()throws Exception{
        JSONObject root=envelope();root.getJSONArray("records").put(record());assertThrows(Exception.class,()->parse(root,2));
        root.getJSONArray("records").getJSONObject(1).put("physical_marker_id","roadprints:millennium:second");assertThrows(Exception.class,()->parse(root,2));
    }
    @Test public void invalidGeometryAttributionAndIntervalReject()throws Exception{
        JSONObject root=envelope();JSONObject r=root.getJSONArray("records").getJSONObject(0);
        r.put("latitude",100);assertThrows(Exception.class,()->parse(root,1));r.put("latitude",51);
        r.put("designer","unknown");assertThrows(Exception.class,()->parse(root,1));r.put("designer","mills");
        r.put("valid_until",r.getString("valid_from"));assertThrows(Exception.class,()->parse(root,1));
    }
    @Test public void validatedLoaderOutputUsesExistingArchiveDerivationAndPersistentState()throws Exception{f.work(()->{
        JSONObject root=envelope();List<MilepostDiscoveryLedger.Marker> markers=parse(root,1);
        f.ledger.install(2,NcnJson.hash(bytes(root)),markers);f.save("loader-history");MilepostAchievementsTest.setNcn(f.ncn(0));f.derive(1000);
        assertEquals(1,f.earned());assertTrue(f.award(0).getBoolean("earned"));
        f.ledger.close();f.ledger=new MilepostDiscoveryLedger(f.c,f.name);assertTrue(f.award(0).getBoolean("earned"));
        long ordinal=f.award(0).getLong("ordinal");f.derive(2000);assertEquals(ordinal,f.award(0).getLong("ordinal"));
        JourneyStore.delete(f.c,"loader-history");MilepostAchievementsTest.setNcn(f.ncn(0));f.derive(3000);assertFalse(f.award(0).getBoolean("earned"));
    });}
}
