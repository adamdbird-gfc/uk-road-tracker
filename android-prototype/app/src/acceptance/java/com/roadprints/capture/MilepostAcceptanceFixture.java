package com.roadprints.capture;

import android.content.Context;
import org.json.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Only compiled into acceptance; all data belongs to its distinct Android UID. */
final class MilepostAcceptanceFixture implements AutoCloseable {
    static final String PACKAGE="com.roadprints.capture.milepostacceptance";
    static final String HASH="aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    static final String DATABASE="synthetic-acceptance.db", USER="synthetic-acceptance";
    final Context c;
    final MilepostDiscoveryLedger ledger;
    MilepostIntegration gate;
    MilepostAcceptanceFixture(Context context)throws Exception {
        c=context.getApplicationContext();
        if(!PACKAGE.equals(BuildConfig.APPLICATION_ID)||!PACKAGE.equals(c.getPackageName()))
            throw new SecurityException("Synthetic fixtures require the acceptance package");
        setField("running",true); // Suppress real NCN refresh; never install real catalogue data.
        c.getSharedPreferences("roadprints_ncn_national_v2",0).edit().putBoolean("enabled",true).commit();
        ledger=new MilepostDiscoveryLedger(c,DATABASE);
    }
    static void setField(String name,Object value)throws Exception {
        java.lang.reflect.Field f=NcnProgressStore.class.getDeclaredField(name);f.setAccessible(true);f.set(null,value);
    }
    static void setNcn(NcnProgressStore.Snapshot value)throws Exception {setField("snapshot",value);}
    static MilepostProximity.Point offset(double heading,double metres) {
        net.sf.geographiclib.GeodesicData p=net.sf.geographiclib.Geodesic.WGS84.Direct(51.4,-1,heading,metres);
        return new MilepostProximity.Point(p.lon2,p.lat2);
    }
    void prepare(int count)throws Exception {
        if(CaptureService.isActive(c))throw new IllegalStateException("Stop sandbox capture before replacing fixtures");
        JourneyStore.deleteAll(c);
        java.util.List<MilepostDiscoveryLedger.Marker> rows=new ArrayList<>();
        String[] designers={"mills","rowe","mccoll","dudgeon","unknown"};
        for(int i=0;i<count;i++){
            // Distinct synthetic physical posts, each in range of the same validated passage.
            MilepostProximity.Point p=offset(0,40+i);
            rows.add(new MilepostDiscoveryLedger.Marker("synthetic:p"+i,p.lon,p.lat,true,0,4102444800000L,
                Collections.singletonList("synthetic:alias:"+i),designers[Math.min(i,4)],i<4));
        }
        int revision=(int)MilepostAchievements.number(ledger.db(),"SELECT COALESCE(MAX(revision),0) FROM catalogue")+1;
        ledger.install(revision,HASH,rows);
        JourneyStore.save(c,journey("walking").put("journey_id","synthetic:a"));
        JourneyStore.save(c,journey("walking").put("journey_id","synthetic:b"));
        restore();
    }
    void restore()throws Exception {
        setNcn(ncn(100));
        int revision=(int)MilepostAchievements.number(ledger.db(),"SELECT COALESCE(MAX(revision),0) FROM catalogue");
        if(revision==0)return;
        String fingerprint=MilepostAchievements.scalar(ledger.db(),"SELECT fingerprint FROM catalogue");
        java.lang.reflect.Constructor<MilepostIntegration> ctor=MilepostIntegration.class.getDeclaredConstructor(
            boolean.class,String.class,String.class,int.class,String.class,String.class,String.class);
        ctor.setAccessible(true);gate=ctor.newInstance(true,DATABASE,USER,revision,HASH,fingerprint,MilepostDiscoveryLedger.EVALUATOR);
    }
    boolean process(AtomicBoolean stopped,long deadline)throws Exception {
        if(gate==null)throw new IllegalStateException("Seed synthetic fixtures first");
        return gate.process(c,stopped,deadline);
    }
    MilepostPresentation.Snapshot read()throws Exception {return gate.read(c,ledger,0,()->false);}
    public void close(){ledger.close();}
    static NcnCatalogue catalogue()throws Exception {
        JSONArray routes=new JSONArray(),paths=new JSONArray();
        for(int i=0;i<NcnCatalogue.KEYS.length;i++) {
            String key=NcnCatalogue.KEYS[i];
            routes.put(new JSONObject().put("key",key).put("length_m",100).put("logical",new JSONObject().put("t",100)));
            paths.put(new JSONObject().put("way",""+(100+i)).put("route",key).put("source_geometry_sha256",HASH)
                    .put("origin",new JSONArray("[0,0]")).put("coordinates",new JSONArray("[[0,0],[0.001,0]]"))
                    .put("nodes",new JSONArray("[1,2]")).put("positions",new JSONArray().put(0).put(6371008.8*Math.toRadians(.001)))
                    .put("modes",new JSONArray("[\"on_foot\",\"cycling\"]"))
                    .put("bindings",new JSONArray().put(new JSONObject().put("start",0).put("end",100).put("low",0).put("high",100)
                            .put("target","t").put("modes",new JSONArray("[\"on_foot\",\"cycling\"]")))));
        }
        byte[] raw=new JSONObject().put("schema_version",1).put("scope",NcnCatalogue.SCOPE).put("whole_published_route_ready",false)
                .put("physical_equivalence_verified",true).put("attribution","synthetic test only").put("routes",routes).put("paths",paths).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return new NcnCatalogue(raw,new JSONObject().put("sha256",NcnJson.hash(raw)));
    }
    NcnProgressStore.Snapshot ncn(double done)throws Exception {
        NcnCatalogue cat=catalogue();NcnLedger l=new NcnLedger(cat);
        l.put("synthetic",new JSONObject().put("mode","on_foot").put("spans",new JSONArray().put(new JSONArray().put("NCN:167").put("100").put(0).put(done))));
        l.networkMetres("combined");return new NcnProgressStore.Snapshot(cat,l,JourneyStore.dataRevision(c),Collections.emptyMap(),0,JourneyStore.ncnDataRevision(c));
    }
    static JSONObject journey(String mode)throws Exception{
        long start=Instant.parse("2026-11-05T00:00:00Z").toEpochMilli();
        JSONArray samples=new JSONArray(),coords=new JSONArray();
        for(int i=0;i<5;i++){
            MilepostProximity.Point p=offset(90,(i-2)*40);
            coords.put(new JSONArray().put(p.lon).put(p.lat));
            samples.put(new JSONArray().put(p.lon).put(p.lat).put(3).put(start+i*20000).put("cycling".equals(mode)?3:2));
        }
        JSONObject result=new JSONObject().put("status","ok").put("input_points",5).put("matched_tracepoints",5)
                .put("matching_mode",mode).put("matched_distance_is_deduplicated",true).put("matched_distance_m",160)
                .put("geojson",new JSONObject().put("type","FeatureCollection").put("features",new JSONArray().put(
                        new JSONObject().put("type","Feature").put("geometry",new JSONObject().put("type","LineString").put("coordinates",coords)))));
        return new JSONObject().put("journey_id","native-test").put("started_at","2026-11-05T00:00:00Z")
                .put("ended_at","2026-11-05T00:02:00Z").put("mode",mode).put("processing_status","complete")
                .put("source",new JSONObject().put("type","android_activity_capture"))
                .put("capture_decisions",new JSONObject().put("mode_at_save",mode).put("mode_source","capture_activity_or_gps_context"))
                .put("activity_recognition_history",new JSONArray().put(new JSONObject().put("activity",mode).put("entering",true).put("at_utc","2026-11-05T00:00:00Z")))
                .put("capture_route_samples",samples).put("route_geometry",new JSONObject().put("type","LineString").put("coordinates",coords))
                .put("processing_result",result);
    }
}
