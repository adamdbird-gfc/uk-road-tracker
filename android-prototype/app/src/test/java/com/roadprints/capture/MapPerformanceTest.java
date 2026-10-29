package com.roadprints.capture;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.function.BooleanSupplier;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
@LooperMode(LooperMode.Mode.PAUSED)
public class MapPerformanceTest {
    Context app;
    @Before public void setup() throws Exception {
        app=RuntimeEnvironment.getApplication();
        app.getSharedPreferences("roadprints_ncn_national_v2",0).edit().putBoolean("enabled",false).commit();
        JourneyStore.deleteAll(app);
        SQLiteDatabase.deleteDatabase(new File(app.getCacheDir(),"map-journeys-v1.db"));
        Shadows.shadowOf(RuntimeEnvironment.getApplication()).grantPermissions("com.roadprints.capture.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION");
    }
    @After public void cleanup(){JourneyStore.deleteAll(app);}
    JSONObject asset(String file) throws Exception {
        try(InputStream in=app.getAssets().open(file);ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] buf=new byte[8192];int n;while((n=in.read(buf))!=-1)out.write(buf,0,n);
            return new JSONObject(out.toString("UTF-8"));
        }
    }
    JSONArray line(JSONArray source,int max) throws Exception {
        JSONArray result=new JSONArray();
        for(int i=0;i<Math.min(source.length(),max);i++)result.put(new JSONArray().put(source.getJSONArray(i).getDouble(0)).put(source.getJSONArray(i).getDouble(1)));
        return result;
    }
    JSONObject feature(String ref,JSONArray line) throws Exception {
        return new JSONObject().put("type","Feature").put("properties",new JSONObject().put("road_ref",ref).put("distance_m",1000))
                .put("geometry",new JSONObject().put("type","LineString").put("coordinates",line));
    }
    JSONObject collection(JSONObject feature) throws Exception {return new JSONObject().put("type","FeatureCollection").put("features",new JSONArray().put(feature));}
    JSONObject trip(String id) throws Exception {
        JSONArray motorway=line(asset("canonical-motorways-v1.json").getJSONObject("roads").getJSONObject("M2").getJSONArray("anchors"),24);
        JSONArray aRoad=line(asset("GB-A2.json").getJSONArray("paths").getJSONArray(0),24);
        return new JSONObject().put("journey_id",id).put("mode","driving").put("processing_status","complete")
            .put("started_at","2026-10-20T12:00:00Z").put("ended_at","2026-10-20T12:10:00Z").put("gps_point_count",2)
            .put("processing_result",new JSONObject().put("geojson",collection(feature("M2",motorway)))
                .put("motorway_geojson",collection(feature("M2",motorway))).put("a_road_geojson",collection(feature("A2",aRoad))));
    }
    @Test public void compactContributionsEqualUncachedRoadCreditAndAttribution() throws Exception {
        MotorwayProgressCalculator rawM=new MotorwayProgressCalculator(app),cachedM=new MotorwayProgressCalculator(app);
        ARoadProgressCalculator rawA=new ARoadProgressCalculator(app),cachedA=new ARoadProgressCalculator(app);
        JSONObject one=trip("one"),two=new JSONObject(one.toString()).put("journey_id","two").put("mode","bus");
        for(JSONObject trip:Arrays.asList(one,two)){
            rawM.addJourney(trip);rawA.addJourney(trip);
            JSONArray m=cachedM.contribution(trip),a=cachedA.contribution(trip);
            assertTrue(cachedM.accepts(m));assertTrue(cachedA.accepts(a));
            cachedM.addContribution(trip.getString("journey_id"),m);cachedA.addContribution(trip.getString("journey_id"),a);
        }
        MotorwayProgressCalculator.Road m1=rawM.finish().roads.get(0),m2=cachedM.finish().roads.get(0);
        ARoadProgressCalculator.Road a1=rawA.finish().roads.get(0),a2=cachedA.finish().roads.get(0);
        assertEquals(m1.coveredSections,m2.coveredSections);assertEquals(m1.matchedMetres,m2.matchedMetres,0);
        assertEquals(m1.journeyIds,m2.journeyIds);assertEquals(m1.percent(),m2.percent(),0);
        assertEquals(a1.covered,a2.covered);assertEquals(a1.matchedMetres,a2.matchedMetres,0);
        assertEquals(a1.journeyIds,a2.journeyIds);assertEquals(a1.percent(),a2.percent(),0);
        assertEquals(m1.coveredMapSections.toString(),m2.coveredMapSections.toString());
        assertEquals(a1.coveredMapSections.toString(),a2.coveredMapSections.toString());
    }
    @Test public void referenceChangeOrOutOfRangeIndexInvalidatesBeforeMerging() throws Exception {
        MotorwayProgressCalculator m=new MotorwayProgressCalculator(app);
        JSONArray rows=m.contribution(trip("one"));
        JSONObject first=rows.getJSONObject(0);String original=first.getString("reference");
        first.put("reference","changed");assertFalse(m.accepts(rows));
        first.put("reference",original).getJSONArray("indices").put(Integer.MAX_VALUE);assertFalse(m.accepts(rows));
        assertTrue(m.finish().roads.isEmpty());
        ARoadProgressCalculator a=new ARoadProgressCalculator(app);
        rows=a.contribution(trip("two"));rows.getJSONObject(0).put("reference","changed");assertFalse(a.accepts(rows));
        assertTrue(a.finish().roads.isEmpty());
    }
    JSONObject emptyPrepared() throws Exception {
        return new JSONObject().put("motorway",new JSONArray()).put("a_road",new JSONArray()).put("roads",new JSONArray()).put("motorways",new JSONArray());
    }
    @Test public void compactRowsSurviveReopenRebuildOnlyChangesAndExcludeDeletions() throws Exception {
        JourneyStore.save(app,trip("one"));JourneyStore.save(app,trip("two"));
        for(int pass=0;pass<2;pass++)try(MapJourneyStore store=new MapJourneyStore(app)){
            store.forEach(()->false,r->r.prepared(raw->emptyPrepared()));
            assertEquals(pass==0?2:0,store.rebuilt);assertEquals(pass==0?0:2,store.reused);
        }
        try(MapJourneyStore forced=new MapJourneyStore(app,true)){
            forced.forEach(()->false,r->r.prepared(raw->emptyPrepared()));
            assertEquals(2,forced.rebuilt);assertEquals(2,forced.sourceReads);
        }
        JourneyStore.updateTitle(app,"one","A title edit");
        try(MapJourneyStore store=new MapJourneyStore(app)){
            store.forEach(()->false,r->r.prepared(raw->emptyPrepared()));
            assertEquals(1,store.rebuilt);assertEquals(1,store.reused);
        }
        JourneyStore.delete(app,"two");
        int[] seen={0};try(MapJourneyStore store=new MapJourneyStore(app)){
            store.forEach(()->false,r->{seen[0]++;r.prepared(raw->emptyPrepared());});
            assertEquals(0,store.rebuilt);
        }assertEquals(1,seen[0]);assertEquals(1,JourneyStore.count(app));
    }
    @Test public void corruptPayloadRebuildsAndInterruptedScanResumesCommittedRows() throws Exception {
        JourneyStore.save(app,trip("one"));JourneyStore.save(app,trip("two"));
        try(MapJourneyStore store=new MapJourneyStore(app)){
            try{store.forEach(()->false,r->{r.prepared(raw->emptyPrepared());throw new java.util.concurrent.CancellationException();});fail();}
            catch(java.util.concurrent.CancellationException expected){}
        }
        try(MapJourneyStore store=new MapJourneyStore(app)){
            store.forEach(()->false,r->r.prepared(raw->emptyPrepared()));
            assertEquals(1,store.rebuilt);assertEquals(1,store.reused);
        }
        SQLiteDatabase db=SQLiteDatabase.openDatabase(new File(app.getCacheDir(),"map-journeys-v1.db").getPath(),null,SQLiteDatabase.OPEN_READWRITE);
        db.execSQL("UPDATE entries SET payload=x'00' WHERE source='journey_one.json'");db.close();
        try(MapJourneyStore store=new MapJourneyStore(app)){
            store.forEach(()->false,r->r.prepared(raw->emptyPrepared()));
            assertEquals(1,store.rebuilt);assertEquals(1,store.reused);
        }
    }
    @Test public void largePreparedRecordAvoidsCursorWindowOverflowWithoutDroppingOriginal() throws Exception {
        JourneyStore.save(app,trip("large"));char[] text=new char[1100*1024];Arrays.fill(text,'x');
        JSONObject prepared=emptyPrepared().put("roads",new JSONArray().put(new JSONObject().put("fixture",new String(text))));
        for(int pass=0;pass<2;pass++)try(MapJourneyStore store=new MapJourneyStore(app)){
            store.forEach(()->false,r->{assertEquals(text.length,r.prepared(raw->prepared).getJSONArray("roads").getJSONObject(0).getString("fixture").length());});
            assertEquals(1,store.rebuilt);assertEquals(1,store.oversized);
        }
        assertNotNull(JourneyStore.get(app,"large"));
    }
    @Test public void metadataAndDwellDoNotInvalidateNcnAndDuplicateSavesDoNotBumpArchive() throws Exception {
        JSONObject trip=trip("metadata");JourneyStore.save(app,trip);
        long archive=JourneyStore.dataRevision(app),ncn=JourneyStore.ncnDataRevision(app);
        JourneyStore.save(app,trip);assertEquals(archive,JourneyStore.dataRevision(app));
        JourneyStore.updateTitle(app,"metadata","Renamed");
        assertTrue(JourneyStore.dataRevision(app)>archive);assertEquals(ncn,JourneyStore.ncnDataRevision(app));
        JSONObject row=JourneyStore.get(app,"metadata");
        row.put("arrival_dwell_samples",new JSONArray().put(new JSONObject().put("duration",1)));
        JourneyStore.save(app,row);assertEquals(ncn,JourneyStore.ncnDataRevision(app));
        JourneyStore.updateMode(app,"metadata","walking");assertTrue(JourneyStore.ncnDataRevision(app)>ncn);
    }
    @Test public void ncnCheckpointRestoresAfterMetadataOnlySaveWithFreshTitle() throws Exception {
        Field field=NcnCatalogue.class.getDeclaredField("loaded");field.setAccessible(true);Object old=field.get(null);
        try {
            byte[] raw=NcnCatalogue.read(new java.util.zip.GZIPInputStream(app.getAssets().open("ncn-trial/ncn-trial.bin")));
            field.set(null,new NcnCatalogue(raw,new JSONObject().put("sha256",NcnJson.hash(raw))));
            JourneyStore.save(app,trip("metadata"));
            Method method=NcnProgressStore.class.getDeclaredMethod("assess",Context.class,BooleanSupplier.class);method.setAccessible(true);
            NcnProgressStore.Snapshot snapshot=(NcnProgressStore.Snapshot)method.invoke(null,app,(BooleanSupplier)()->false);
            NcnProgressStore.checkpoint(app,snapshot);
            JourneyStore.updateTitle(app,"metadata","New title");
            NcnProgressStore.Snapshot restored=NcnProgressStore.restore(app);assertNotNull(restored);
            assertEquals("New title",restored.metadata.get("metadata").getString("title"));
            JourneyStore.updateMode(app,"metadata","walking");assertNull(NcnProgressStore.restore(app));
        }finally{field.set(null,old);}
    }
    @Test public void missingInputHashDoesNotTurnMetadataSaveIntoNcnEvidenceChange()throws Exception {
        JSONObject original=trip("legacy");JourneyStore.save(app,original);long ncn=JourneyStore.ncnDataRevision(app);
        app.getSharedPreferences("roadprints_ncn_inputs",0).edit().clear().commit();
        JourneyStore.updateTitle(app,"legacy","New label after cache eviction");
        assertEquals(ncn,JourneyStore.ncnDataRevision(app));
        assertNotNull(app.getSharedPreferences("roadprints_ncn_inputs",0).getString("legacy",null));
        app.getSharedPreferences("roadprints_ncn_inputs",0).edit().clear().commit();
        JourneyStore.updateMode(app,"legacy","walking");assertTrue(JourneyStore.ncnDataRevision(app)>ncn);
    }
    @Test public void missingWalkingHashStillDetectsRealEvidenceChange()throws Exception {
        JSONObject original=trip("foot").put("mode","walking");JourneyStore.save(app,original);
        long ncn=JourneyStore.ncnDataRevision(app);
        app.getSharedPreferences("roadprints_ncn_inputs",0).edit().clear().commit();
        JourneyStore.updateTitle(app,"foot","Label only");assertEquals(ncn,JourneyStore.ncnDataRevision(app));
        app.getSharedPreferences("roadprints_ncn_inputs",0).edit().clear().commit();
        JSONObject changed=JourneyStore.get(app,"foot");changed.getJSONObject("processing_result").put("geojson",new JSONObject().put("features",new JSONArray()));
        JourneyStore.save(app,changed);assertTrue(JourneyStore.ncnDataRevision(app)>ncn);
    }
    @Test public void unfinishedFootRetriesDoNotInvalidateNcnUntilCoverageCanChange()throws Exception {
        for(String mode:new String[]{"walking","running","cycling"}) {
            JSONObject row=trip(mode).put("mode",mode).put("processing_status","pending");JourneyStore.save(app,row);
            long revision=JourneyStore.ncnDataRevision(app);
            for(String state:new String[]{"processing","failed","pending","processing","failed"}) {
                row.put("processing_status",state).put("last_match_attempt",new JSONObject().put("failure_category","backend_timeout"));
                row.put("journey_corrections",new JSONObject().put("removed_matched_segments",new JSONArray("[0]")));
                JourneyStore.save(app,row);assertEquals(mode+"/"+state,revision,JourneyStore.ncnDataRevision(app));
            }
            row.put("processing_status","complete");JourneyStore.save(app,row);assertTrue(JourneyStore.ncnDataRevision(app)>revision);
            revision=JourneyStore.ncnDataRevision(app);row.put("processing_status","processing");JourneyStore.save(app,row);
            assertTrue("Removing previously completed coverage still invalidates",JourneyStore.ncnDataRevision(app)>revision);
        }
    }
    @Test public void staleLargeSnapshotRejectsBeforeReadingItsBrokenCompressedBody() throws Exception {
        MapSnapshotCache.write(app,123,new JSONObject().put("marker","unchanged"));
        File file=new File(app.getFilesDir(),"screen-cache/map-routes.bin");
        try(RandomAccessFile raw=new RandomAccessFile(file,"rw")){raw.setLength(12);}
        assertNull(MapSnapshotCache.read(app,124));assertNull(MapSnapshotCache.read(app,123));
    }
    @Test public void unprocessedTrainAndPlaneKeepTheirExistingVisibility() throws Exception {
        JSONObject driving=trip("driving");JourneyStore.save(app,driving);
        JourneyStore.save(app,new JSONObject(driving.toString()).put("journey_id","train").put("mode","train").put("processing_status","not_required"));
        JourneyStore.save(app,new JSONObject(driving.toString()).put("journey_id","flight").put("mode","plane").put("processing_status","not_required"));
        org.robolectric.android.controller.ActivityController<MapActivity> controller=Robolectric.buildActivity(MapActivity.class).create();
        try {assertEquals(1,read(controller.get(),JourneyFilters.all()).matchedJourneys);assertEquals(3,JourneyStore.count(app));}
        finally {controller.destroy();}
    }
    private void stageLogs(String label) {
        for(org.robolectric.shadows.ShadowLog.LogItem item:org.robolectric.shadows.ShadowLog.getLogsForTag("RoadprintsTiming"))
            if(item.msg.startsWith("map_"))System.out.println("MAP_STAGE "+label+" "+item.msg);
        org.robolectric.shadows.ShadowLog.clear();
    }
    private MapActivity.MapRoutes read(MapActivity screen,JourneyFilters filter) throws Exception {
        Method method=MapActivity.class.getDeclaredMethod("readMapRoutes",JourneyFilters.class,LocalDate.class,ZoneId.class);method.setAccessible(true);
        return (MapActivity.MapRoutes)method.invoke(screen,filter,LocalDate.now(),ZoneId.systemDefault());
    }
    private JSONObject legacySnapshot(File file,long revision) throws Exception {
        try(InputStream in=new java.util.zip.GZIPInputStream(new FileInputStream(file));ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] buffer=new byte[8192];int count;while((count=in.read(buffer))!=-1)out.write(buffer,0,count);
            JSONObject root=new JSONObject(out.toString("UTF-8"));
            if(root.getLong("revision")!=revision)return null;
            JSONObject payload=root.getJSONObject("data");
            return NcnJson.hash(payload.toString().getBytes(StandardCharsets.UTF_8)).equals(root.getString("sha256"))?payload:null;
        }
    }
    private void snapshotBenchmark(MapActivity screen,MapActivity.MapRoutes routes) throws Exception {
        Method serialise=MapActivity.class.getDeclaredMethod("mapRoutesToCache",MapActivity.MapRoutes.class);serialise.setAccessible(true);
        JSONObject data=(JSONObject)serialise.invoke(screen,routes);
        byte[] bytes=data.toString().getBytes(StandardCharsets.UTF_8);
        File old=new File(app.getCacheDir(),"legacy-map-benchmark.gz");
        JSONObject wrapper=new JSONObject().put("format",1).put("revision",987).put("data",data).put("sha256",NcnJson.hash(bytes));
        try(OutputStream out=new java.util.zip.GZIPOutputStream(new FileOutputStream(old))){out.write(wrapper.toString().getBytes(StandardCharsets.UTF_8));}
        MapSnapshotCache.write(app,987,data);
        long start=System.nanoTime();JSONObject before=legacySnapshot(old,987);long oldHit=System.nanoTime()-start;
        start=System.nanoTime();JSONObject after=MapSnapshotCache.read(app,987);long newHit=System.nanoTime()-start;
        assertNotNull(after);assertEquals(before.toString(),after.toString());
        start=System.nanoTime();assertNull(legacySnapshot(old,988));long oldMiss=System.nanoTime()-start;
        start=System.nanoTime();assertNull(MapSnapshotCache.read(app,988));long newMiss=System.nanoTime()-start;
        System.out.println("MAP_CACHE_BENCHMARK identical_payload_bytes="+bytes.length+" legacy_hit_ms="+oldHit/1e6+" current_hit_ms="+newHit/1e6
            +" legacy_stale_ms="+oldMiss/1e6+" current_stale_ms="+newMiss/1e6);
    }
    @Test public void drivingHistory1547ColdWarmAdditionFiltersAndCoverageRemainConsistent() throws Exception {
        JSONObject template=trip("bench");
        JSONArray raw=new JSONArray();for(int i=0;i<512;i++)raw.put(new JSONArray().put(.37).put(51.44).put(i));
        template.put("raw_capture_samples",raw);
        for(int i=0;i<1547;i++){
            template.put("journey_id","driving-"+i);
            java.nio.file.Files.write(new File(app.getFilesDir(),"journey_driving-"+i+".json").toPath(),template.toString().getBytes(StandardCharsets.UTF_8));
        }
        org.robolectric.android.controller.ActivityController<MapActivity> controller=Robolectric.buildActivity(MapActivity.class).create();
        try {
            org.robolectric.shadows.ShadowLog.clear();
            long start=System.nanoTime();MapActivity.MapRoutes cold=read(controller.get(),JourneyFilters.all());long coldTime=System.nanoTime()-start;stageLogs("cold");
            start=System.nanoTime();MapActivity.MapRoutes warm=read(controller.get(),JourneyFilters.all());long warmTime=System.nanoTime()-start;stageLogs("warm");
            assertEquals(1547,cold.matchedJourneys);assertEquals(cold.matchedJourneys,warm.matchedJourneys);
            assertEquals(cold.roadPercentages,warm.roadPercentages);
            snapshotBenchmark(controller.get(),warm);
            assertEquals(cold.coveredMotorwaySections.toString(),warm.coveredMotorwaySections.toString());
            assertEquals(cold.coveredARoadSections.toString(),warm.coveredARoadSections.toString());
            MapActivity.MapRoutes filtered=read(controller.get(),new JourneyFilters(2,"all",JourneyDateFilter.all()));
            assertEquals(0,filtered.matchedJourneys);
            JourneyStore.save(app,new JSONObject(template.toString()).put("journey_id","new-driving"));
            start=System.nanoTime();MapActivity.MapRoutes added=read(controller.get(),JourneyFilters.all());long addTime=System.nanoTime()-start;stageLogs("add_one");
            assertEquals(1548,added.matchedJourneys);assertEquals(cold.roadPercentages,added.roadPercentages);
            System.out.println("MAP_DRIVING_BENCHMARK synthetic=1547 cold_prepare_ms="+coldTime/1e6+" warm_prepare_ms="+warmTime/1e6+" add_one_prepare_ms="+addTime/1e6+" completion=identical");
        } finally {controller.destroy();}
    }
}

