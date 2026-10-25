package com.roadprints.capture;
import android.content.*;
import android.graphics.*;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28) @LooperMode(LooperMode.Mode.PAUSED)
public class NavigationPerformanceTest {
    Context app;
    @Before public void setup(){app=RuntimeEnvironment.getApplication();JourneyStore.deleteAll(app);app.getSharedPreferences("roadprints_ncn_national_v2",0).edit().putBoolean("enabled",false).commit();Shadows.shadowOf(RuntimeEnvironment.getApplication()).grantPermissions("com.roadprints.capture.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION");}
    @After public void cleanup(){JourneyStore.deleteAll(app);RoadDetailCache.clear();}
    Object call(Object obj,String name,Class<?>[] args,Object... values)throws Exception{Method m=obj.getClass().getDeclaredMethod(name,args);m.setAccessible(true);return m.invoke(obj,values);}
    Field field(Class<?> type,String name)throws Exception{Field f=type.getDeclaredField(name);f.setAccessible(true);return f;}
    void set(Object obj,String name,Object value)throws Exception{field(obj.getClass(),name).set(obj,value);}
    View find(View view,String prefix){if(view instanceof TextView&&((TextView)view).getText().toString().startsWith(prefix))return view;if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){View found=find(((ViewGroup)view).getChildAt(i),prefix);if(found!=null)return found;}return null;}
    void draw(View view){view.measure(View.MeasureSpec.makeMeasureSpec(800,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(900,View.MeasureSpec.EXACTLY));view.layout(0,0,800,900);view.draw(new Canvas(Bitmap.createBitmap(800,900,Bitmap.Config.ARGB_8888)));}
    JSONObject trip(String id)throws Exception{
        MapPerformanceTest helper=new MapPerformanceTest();helper.app=app;JSONObject row=helper.trip(id);
        JSONArray coords=row.getJSONObject("processing_result").getJSONObject("geojson").getJSONArray("features").getJSONObject(0).getJSONObject("geometry").getJSONArray("coordinates");
        row.put("route_geometry",new JSONObject().put("type","LineString").put("coordinates",coords));
        JSONObject road=helper.feature("B"+(1000+Integer.parseInt(id.substring(id.lastIndexOf('-')+1))),coords);
        road.getJSONObject("properties").put("highway","secondary");row.getJSONObject("processing_result").put("road_geojson",helper.collection(road));
        JSONArray raw=new JSONArray();for(int i=0;i<256;i++)raw.put(new JSONArray().put(.37).put(51.44).put(i));row.put("raw_capture_samples",raw);return row;
    }
    @Test public void representative1577ArchiveMeasuresActualBindingAndIncrementalProgress()throws Exception {
        JSONObject template=trip("nav-0");String raw=template.getJSONArray("raw_capture_samples").toString();
        for(int i=0;i<1577;i++){
            template.put("journey_id","nav-"+i);template.getJSONObject("processing_result").getJSONObject("road_geojson").getJSONArray("features").getJSONObject(0).getJSONObject("properties").put("road_ref","B"+(1000+i));
            java.nio.file.Files.write(new File(app.getFilesDir(),"journey_nav-"+i+".json").toPath(),template.toString().getBytes(StandardCharsets.UTF_8));
        }
        long start=System.nanoTime();List<JSONObject> first=JourneyStore.firstSummaries(app,12);long firstRead=System.nanoTime()-start;
        assertEquals(12,first.size());start=System.nanoTime();List<JSONObject> all=JourneyStore.allSummaries(app);long fullRead=System.nanoTime()-start;assertEquals(1577,all.size());
        try(CompactJourneyCache cache=new CompactJourneyCache(app,"summaries-v1")){for(File file:JourneyStore.archiveFiles(app))cache.get(file,JourneyStore::readSummary);assertEquals(0,cache.reads);assertEquals(1577,cache.hits);}
        JourneyListActivity list=Robolectric.buildActivity(JourneyListActivity.class).get();call(list,"buildScreen",new Class<?>[]{});set(list,"journeys",new ArrayList<>(all));set(list,"journeyCardLimit",40);
        start=System.nanoTime();call(list,"render",new Class<?>[]{});draw((View)field(JourneyListActivity.class,"journeyList").get(list));long fullBind=System.nanoTime()-start;
        JourneyListActivity paged=Robolectric.buildActivity(JourneyListActivity.class).get();call(paged,"buildScreen",new Class<?>[]{});set(paged,"journeys",new ArrayList<>(first));set(paged,"summariesPartial",true);set(paged,"journeyCardLimit",12);
        start=System.nanoTime();call(paged,"render",new Class<?>[]{});View rows=(View)field(JourneyListActivity.class,"journeyList").get(paged);draw(rows);long pageBind=System.nanoTime()-start;
        View firstCard=((ViewGroup)rows).getChildAt(0);call(paged,"render",new Class<?>[]{});assertSame(firstCard,((ViewGroup)rows).getChildAt(0));
        ProgressActivity progress=Robolectric.buildActivity(ProgressActivity.class).get();start=System.nanoTime();Object stats=call(progress,"summarizeSavedJourneys",new Class<?>[]{Context.class,int.class},app,0);long coldProgress=System.nanoTime()-start;
        start=System.nanoTime();Object warm=call(progress,"summarizeSavedJourneys",new Class<?>[]{Context.class,int.class},app,0);long warmProgress=System.nanoTime()-start;
        assertEquals(1577,field(stats.getClass(),"activities").getInt(stats));assertEquals(field(stats.getClass(),"uniqueDrivingMetres").getDouble(stats),field(warm.getClass(),"uniqueDrivingMetres").getDouble(warm),.001);
        List<?> roads=new ArrayList<>(((Map<?,?>)field(stats.getClass(),"discoveredRoads").get(stats)).values());assertEquals(1577,roads.size());
        LinearLayout full=new LinearLayout(progress);full.setOrientation(1);start=System.nanoTime();call(progress,"addRoadDiscoveryRows",new Class<?>[]{LinearLayout.class,List.class},full,roads);
        while(find(full,"SHOW MORE ROADS")!=null)find(full,"SHOW MORE ROADS").performClick();draw(full);long fullRoadBind=System.nanoTime()-start;
        LinearLayout page=new LinearLayout(progress);page.setOrientation(1);start=System.nanoTime();call(progress,"addRoadDiscoveryRows",new Class<?>[]{LinearLayout.class,List.class},page,roads);draw(page);long pageRoadBind=System.nanoTime()-start;assertEquals(61,page.getChildCount());
        Field cached=field(ProgressActivity.class,"processCachedStats"),revision=field(ProgressActivity.class,"processCachedRevision");Object old=cached.get(null);long oldRevision=revision.getLong(null);
        try {
            cached.set(null,stats);revision.setLong(null,JourneyStore.dataRevision(app));RoadDetailSelection selection=new RoadDetailSelection(RoadDetailSelection.MOTORWAY,"M2","GB","","");
            start=System.nanoTime();RoadDetailModel model=RoadDetailModel.load(app,selection,false);long fullFocused=System.nanoTime()-start;
            start=System.nanoTime();RoadDetailModel preview=ProgressActivity.focusedPreview(app,selection);assertNotNull(preview);
            RoutePreviewView map=RoutePreviewView.overview(app);map.setMotorwayCoverageSegments(preview.remaining,preview.covered);List<JSONArray> fit=new ArrayList<>(preview.covered);fit.addAll(preview.remaining);map.fitRoadGeometry(fit);draw(map);long firstFocused=System.nanoTime()-start;
            assertEquals(model.uniqueMetres,preview.uniqueMetres,.001);assertFalse(preview.covered.isEmpty());
            System.out.println("NAV_BENCHMARK archive=1577 journeys_recent12_read_ms="+firstRead/1e6+" all_summaries_ms="+fullRead/1e6+" journeys_full40_bind_draw_ms="+fullBind/1e6+" journeys_first12_bind_draw_ms="+pageBind/1e6+" road_all1577_bind_draw_ms="+fullRoadBind/1e6+" road_first30_bind_draw_ms="+pageRoadBind/1e6+" focused_full_ready_ms="+fullFocused/1e6+" focused_first_useful_draw_ms="+firstFocused/1e6+" progress_cold_ms="+coldProgress/1e6+" progress_warm_ms="+warmProgress/1e6+" environment=Robolectric_not_phone");
        }finally{cached.set(null,old);revision.setLong(null,oldRevision);}
        JourneyStore.updateTitle(app,"nav-0","Renamed");start=System.nanoTime();call(progress,"summarizeSavedJourneys",new Class<?>[]{Context.class,int.class},app,0);long changed=System.nanoTime()-start;
        String last="";for(org.robolectric.shadows.ShadowLog.LogItem item:org.robolectric.shadows.ShadowLog.getLogsForTag("RoadprintsTiming"))if(item.msg.startsWith("progress_archive"))last=item.msg;
        assertTrue(last,last.contains("reads=1 reused=1576"));
        try(CompactJourneyCache cache=new CompactJourneyCache(app,"progress-v1")){for(File file:JourneyStore.archiveFiles(app))cache.get(file,f->{throw new AssertionError("Unchanged neighbour decoded");});assertEquals(1577,cache.hits);}
        assertEquals(raw,JourneyStore.get(app,"nav-0").getJSONArray("raw_capture_samples").toString());System.out.println("NAV_BENCHMARK changed_one_progress_ms="+changed/1e6+" full_history_invalidations=0");
        list.finish();paged.finish();progress.finish();
    }
    @Test public void compactCacheInvalidatesOnlyChangedSourcesAndIgnoresDeletedRows()throws Exception {
        JourneyStore.save(app,trip("small-0"));JourneyStore.save(app,trip("small-1"));
        try(CompactJourneyCache cache=new CompactJourneyCache(app,"test")){for(File file:JourneyStore.archiveFiles(app))cache.get(file,JourneyStore::readSummary);assertEquals(2,cache.reads);}
        JourneyStore.updateTitle(app,"small-1","updated");
        try(CompactJourneyCache cache=new CompactJourneyCache(app,"test")){for(File file:JourneyStore.archiveFiles(app))cache.get(file,JourneyStore::readSummary);assertEquals(1,cache.reads);assertEquals(1,cache.hits);}
        JourneyStore.delete(app,"small-1");assertEquals(1,JourneyStore.allSummaries(app).size());
    }
    @Test public void worldPathsAreReusedCulledAndRemainBoundedAcrossCameraChanges()throws Exception {
        JSONArray line=new JSONArray("[[0,51],[0.001,51.001],[0.002,51]]");MapRenderGeometry cache=new MapRenderGeometry();Paint paint=new Paint();paint.setStrokeWidth(4);Canvas canvas=new Canvas(Bitmap.createBitmap(800,800,Bitmap.Config.ARGB_8888));
        double x=MapRenderGeometry.x(0),y=MapRenderGeometry.y(51);assertTrue(cache.draw(canvas,line,paint,null,.1,x,y,800,800));int builds=cache.builds;
        assertTrue(cache.draw(canvas,line,paint,null,.2,x,y,800,800));assertEquals(builds,cache.builds);assertEquals(1,cache.hits);
        assertFalse(cache.draw(canvas,line,paint,null,1,MapRenderGeometry.x(40),y,800,800));assertEquals(1,cache.culled);
        for(int i=0;i<4200;i++)cache.draw(canvas,new JSONArray("[[1,51],[1.0001,51]]"),paint,null,1,x,y,800,800);assertTrue(cache.vertices<=200000);
        cache.clear();assertEquals(0,cache.vertices);
    }
    @Test public void hiddenNcnDoesNotScheduleAssessmentWhenSpoonsPinsAreSelected()throws Exception {
        android.app.Activity activity=Robolectric.buildActivity(android.app.Activity.class).setup().get();
        CollectionAccessStore.installForTesting(app,CollectionAccessStore.SPOONS);assertTrue(CollectionAccessStore.select(app,CollectionAccessStore.SPOONS));
        Field running=field(NcnProgressStore.class,"running");boolean previous=running.getBoolean(null);
        try {
            running.setBoolean(null,false);app.getSharedPreferences("roadprints_ncn_national_v2",0).edit().putBoolean("enabled",true).commit();
            RoutePreviewView map=RoutePreviewView.overview(activity);map.layout(0,0,800,800);map.restoreCameraState(.37,51.44,12);double[] camera=map.cameraState();
            map.setCollectionVenues(CollectionAccessStore.SPOONS,new JSONArray(),Collections.emptySet());NcnMapOverlay.apply(activity,map);
            assertFalse(running.getBoolean(null));assertNull(field(RoutePreviewView.class,"ncnOverlay").get(map));assertArrayEquals(camera,map.cameraState(),0);
            assertEquals(CollectionAccessStore.SPOONS,field(RoutePreviewView.class,"collectionKind").get(map));
            assertTrue(CollectionAccessStore.select(app,CollectionAccessStore.NONE));NcnMapOverlay.apply(activity,map);assertFalse(running.getBoolean(null));assertArrayEquals(camera,map.cameraState(),0);
        }finally{running.setBoolean(null,previous);app.getSharedPreferences("roadprints_ncn_national_v2",0).edit().putBoolean("enabled",false).commit();activity.finish();}
    }
    @Test public void focusedCacheSharesGeometryBudgetAcrossEightSmallScreens()throws Exception {
        RoadDetailCache.clear();JSONArray line=new JSONArray("[[0,0],[1,1]]");
        for(int i=0;i<3;i++){RoadDetailModel model=new RoadDetailModel();for(int n=0;n<20000;n++)model.covered.add(line);RoadDetailCache.put("large"+i,model);}
        assertNull(RoadDetailCache.get("large0"));assertNotNull(RoadDetailCache.get("large2"));
    }
    @Test public void summaryWorkDoesNotQueueBehindProgressPreparation()throws Exception {
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1),summary=new CountDownLatch(1);
        ScreenDataLoader.execute(()->{entered.countDown();try{release.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}});
        try{assertTrue(entered.await(2,TimeUnit.SECONDS));ScreenDataLoader.summaries(summary::countDown);assertTrue("Visible summaries are independent of heavy Progress work",summary.await(2,TimeUnit.SECONDS));}
        finally{release.countDown();}
    }
    @Test public void repeatedRoadFramesReuseWorkerPathsAndCullOffscreenSections()throws Exception {
        List<JSONArray> rows=new ArrayList<>();for(int i=0;i<3565;i++){
            JSONArray line=new JSONArray();for(int p=0;p<20;p++)line.put(new JSONArray().put((i<60?.37:-6)+(i%10)*.001+p*.00001).put(i<60?51.44:55.0));rows.add(line);
        }
        Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(4);Canvas canvas=new Canvas(Bitmap.createBitmap(800,800,Bitmap.Config.ARGB_8888));
        double scale=256*Math.pow(2,10)/(2*Math.PI*6378137),cx=MapRenderGeometry.x(.37),cy=MapRenderGeometry.y(51.44);
        long start=System.nanoTime();for(JSONArray row:rows){Path path=new Path();for(int i=0;i<row.length();i++){JSONArray p=row.getJSONArray(i);float x=(float)(400+(MapRenderGeometry.x(p.getDouble(0))-MapRenderGeometry.x(.37))*scale),y=(float)(400-(MapRenderGeometry.y(p.getDouble(1))-MapRenderGeometry.y(51.44))*scale);if(i==0)path.moveTo(x,y);else path.lineTo(x,y);}canvas.drawPath(path,paint);}long legacy=System.nanoTime()-start;
        MapRenderGeometry.clearPrepared();start=System.nanoTime();MapRenderGeometry.prepare(rows);long preparation=System.nanoTime()-start;
        MapRenderGeometry cache=new MapRenderGeometry();start=System.nanoTime();for(JSONArray line:rows)cache.draw(canvas,line,paint,null,scale,cx,cy,800,800);long current=System.nanoTime()-start;
        assertEquals(0,cache.builds);assertTrue(cache.culled>=3500);start=System.nanoTime();for(JSONArray line:rows)cache.draw(canvas,line,paint,null,scale,cx,cy,800,800);long warm=System.nanoTime()-start;
        assertEquals(0,cache.builds);assertTrue(cache.vertices<=100000);
        System.out.println("NAV_BENCHMARK road_sections=3565 legacy_project_draw_ms="+legacy/1e6+" worker_path_preparation_ms="+preparation/1e6+" first_cached_draw_ms="+current/1e6+" warm_cached_draw_ms="+warm/1e6+" ui_path_builds="+cache.builds+" offscreen_culled="+cache.culled+" environment=Robolectric_not_phone");MapRenderGeometry.clearPrepared();
    }
    @Test public void mutableEditingDoesNotReuseImmutableGeometry()throws Exception {
        MapRenderGeometry cache=new MapRenderGeometry();JSONArray row=new JSONArray("[[0,0],[0.0001,0]]");Paint paint=new Paint();Canvas canvas=new Canvas(Bitmap.createBitmap(100,100,Bitmap.Config.ARGB_8888));
        cache.draw(canvas,row,paint,null,1,0,0,100,100,false);row.getJSONArray(1).put(0,.0002);cache.draw(canvas,row,paint,null,1,0,0,100,100,false);
        assertEquals(2,cache.builds);assertEquals(0,cache.vertices);
    }
    @Test public void compactCoverageUnionEqualsRawCoverageWithOverlapAndRemovedEdges()throws Exception {
        JSONObject a=trip("union-0"),b=trip("union-1");b.put("journey_corrections",new JSONObject().put("removed_matched_segments",new JSONArray("[0,1]")));
        CoreAchievementEvidence.Coverage raw=new CoreAchievementEvidence.Coverage(),compact=new CoreAchievementEvidence.Coverage();
        for(JSONObject row:Arrays.asList(a,b,a)){raw.addJourney(row);CoreAchievementEvidence.Coverage one=new CoreAchievementEvidence.Coverage();one.addJourney(row);compact.addCompact(one.compact());}
        assertEquals(raw.metres(),compact.metres(),.001);
    }
}
