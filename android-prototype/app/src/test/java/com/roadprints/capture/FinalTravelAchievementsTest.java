package com.roadprints.capture;

import android.content.Context;
import java.util.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class FinalTravelAchievementsTest {
    Context app;
    @Before public void setup(){app=RuntimeEnvironment.getApplication();JourneyStore.deleteAll(app);app.getSharedPreferences("roadprints_achievements_v1",0).edit().clear().commit();}
    static JSONArray points(double[][] coordinates)throws Exception {JSONArray p=new JSONArray();for(double[] point:coordinates)p.put(new JSONArray(point));return p;}
    static JSONObject journey(String mode,String start,double[][] coordinates)throws Exception {
        JSONObject geometry=new JSONObject().put("type","LineString").put("coordinates",points(coordinates));
        JSONObject feature=new JSONObject().put("geometry",geometry).put("properties",new JSONObject());
        return new JSONObject().put("journey_id",UUID.randomUUID().toString()).put("mode",mode).put("processing_status","complete")
            .put("started_at",start).put("ended_at",java.time.OffsetDateTime.parse(start).plusHours(1).toString()).put("timezone","Europe/London")
            .put("source",new JSONObject().put("type","timeline_import"))
            .put("processing_result",new JSONObject().put("geojson",new JSONObject().put("features",new JSONArray().put(feature))));
    }
    static double[][] route(double offset,boolean reversed){double[][] p={{-.13+offset,51.5},{-.128+offset,51.5001},{-.126+offset,51.5003},{-.124+offset,51.5003},{-.122+offset,51.5004}};if(reversed)Collections.reverse(Arrays.asList(p));return p;}
    static JSONObject road(JSONObject journey,String ref)throws Exception {
        JSONObject f=new JSONObject().put("properties",new JSONObject().put("road_ref",ref)).put("geometry",journey.getJSONObject("processing_result").getJSONObject("geojson").getJSONArray("features").getJSONObject(0).getJSONObject("geometry"));
        journey.getJSONObject("processing_result").put("road_geojson",new JSONObject().put("features",new JSONArray().put(f)));return journey;
    }
    @Test public void fiveWeekdaysImportsAndSmallGpsVariationQualify()throws Exception {
        GroundhogDayEvidence evidence=new GroundhogDayEvidence();
        for(int day=5;day<=9;day++)evidence.add(journey("walking","2026-10-0"+day+"T08:00:00Z",route((day-5)*.00002,false)));
        assertEquals(5,evidence.value());assertEquals(6,evidence.contributions().size());
    }
    @Test public void duplicateDatesWeekendsDirectionsAndModesDoNotInflate()throws Exception {
        GroundhogDayEvidence evidence=new GroundhogDayEvidence();
        for(int i=0;i<7;i++)evidence.add(journey("walking","2026-10-05T08:00:00Z",route(0,false)));
        evidence.add(journey("walking","2026-10-10T08:00:00Z",route(0,false)));
        evidence.add(journey("walking","2026-10-06T08:00:00Z",route(0,true)));
        evidence.add(journey("bus","2026-10-06T08:00:00Z",route(0,false)));
        assertEquals(1,evidence.value());
    }
    @Test public void localDateUsesTimezoneAndInvalidEvidenceIsSkipped()throws Exception {
        GroundhogDayEvidence e=new GroundhogDayEvidence();e.add(journey("walking","2026-10-04T23:30:00Z",route(0,false)));
        assertEquals(1,e.value());assertTrue(e.contributions().get(1).contains("2026-10-05"));
        assertNull(GroundhogDayEvidence.read(journey("walking","2026-10-06T08:00:00Z",route(0,false)).put("timezone","invalid")));
        assertNull(GroundhogDayEvidence.read(journey("walking","2026-10-06T08:00:00Z",route(0,false)).put("processing_status","failed")));
    }
    @Test public void routeShapeAndLoopDirectionMatter()throws Exception {
        GroundhogDayEvidence.Trip a=GroundhogDayEvidence.read(journey("walking","2026-10-05T08:00:00Z",route(0,false)));
        double[][] detour=route(0,false);detour[2][1]+=.002;
        assertFalse(GroundhogDayEvidence.similar(a,GroundhogDayEvidence.read(journey("walking","2026-10-06T08:00:00Z",detour))));
        double[][] loop={{-.13,51.5},{-.13,51.502},{-.127,51.502},{-.127,51.5},{-.13,51.5}};
        JSONObject forward=journey("walking","2026-10-05T08:00:00Z",loop);Collections.reverse(Arrays.asList(loop));
        assertFalse(GroundhogDayEvidence.similar(GroundhogDayEvidence.read(forward),GroundhogDayEvidence.read(journey("walking","2026-10-06T08:00:00Z",loop))));
    }
    @Test public void shortOppositeRoutesAndRemovedSegmentsAreNotPatterns()throws Exception {
        double[][] shortRoute={{0,51.5},{.0003,51.5},{.0006,51.5},{.0009,51.5}};
        JSONObject a=journey("walking","2026-10-05T08:00:00Z",shortRoute);Collections.reverse(Arrays.asList(shortRoute));
        assertFalse(GroundhogDayEvidence.similar(GroundhogDayEvidence.read(a),GroundhogDayEvidence.read(journey("walking","2026-10-06T08:00:00Z",shortRoute))));
        a.put("journey_corrections",new JSONObject().put("removed_matched_segments",new JSONArray().put(1)));assertNull(GroundhogDayEvidence.read(a));
    }
    @Test public void trainUsesRawGeometryButPlaneAndSparseRoutesDoNot()throws Exception {
        JSONObject train=journey("train","2026-10-05T08:00:00Z",route(0,false));train.put("route_geometry",train.getJSONObject("processing_result").getJSONObject("geojson").getJSONArray("features").getJSONObject(0).getJSONObject("geometry"));train.remove("processing_result");train.put("processing_status","ready");
        assertNotNull(GroundhogDayEvidence.read(train));train.put("mode","plane");assertNull(GroundhogDayEvidence.read(train));
        assertNull(GroundhogDayEvidence.read(journey("walking","2026-10-05T08:00:00Z",new double[][]{{0,51.5},{.1,51.5}})));
    }
    static LandmarkEvidence.Reference reference(LandmarkEvidence e,String id){return e.references.stream().filter(r->r.id.equals(id)).findFirst().get();}
    static double[][] full(LandmarkEvidence.Reference r){LandmarkEvidence.Sample a=r.samples.get(0),b=r.samples.get(r.samples.size()-1);return new double[][]{{(a.x-a.dx*3)/r.scale,(a.y-a.dy*3)/LandmarkEvidence.METRES},{(b.x+b.dx*3)/r.scale,(b.y+b.dy*3)/LandmarkEvidence.METRES}};}
    @Test public void actualBridgeCrossingsBothDirectionsQualifyNearbyRoutesDoNot()throws Exception {
        for(String id:new String[]{"big-ben","humber-bridge-landmark","trent-bridge","conwy-castle"}) {
            LandmarkEvidence e=new LandmarkEvidence(app);double[][] crossing=full(reference(e,id));
            e.add(journey("walking","2026-10-05T08:00:00Z",crossing));assertTrue(id,e.completed.contains(id));
            e=new LandmarkEvidence(app);Collections.reverse(Arrays.asList(crossing));e.add(journey("driving","2026-10-05T08:00:00Z",crossing));assertTrue(id,e.completed.contains(id));
            e=new LandmarkEvidence(app);for(double[] p:crossing)p[1]+=.002;e.add(journey("walking","2026-10-05T08:00:00Z",crossing));assertFalse(id,e.completed.contains(id));
        }
    }
    @Test public void partialDeletedAndRailCrossingsCannotQualify()throws Exception {
        LandmarkEvidence e=new LandmarkEvidence(app);double[][] p=full(reference(e,"humber-bridge-landmark"));
        double[] mid={(p[0][0]+p[1][0])/2,(p[0][1]+p[1][1])/2};
        e.add(journey("walking","2026-10-05T08:00:00Z",new double[][]{p[0],mid}));assertFalse(e.completed.contains("humber-bridge-landmark"));
        JSONObject deleted=journey("walking","2026-10-05T08:00:00Z",new double[][]{p[0],mid,p[1]});deleted.put("journey_corrections",new JSONObject().put("removed_matched_segments",new JSONArray().put(0)));e.add(deleted);assertFalse(e.completed.contains("humber-bridge-landmark"));
        e.add(journey("train","2026-10-05T08:00:00Z",p));assertFalse(e.completed.contains("humber-bridge-landmark"));
    }
    @Test public void bullIsFootOnlyAndEstateEntranceDoesNotCount()throws Exception {
        double[][] bull={{-1.8954,52.4781259},{-1.8948,52.4781259}};LandmarkEvidence e=new LandmarkEvidence(app);
        e.add(journey("driving","2026-10-05T08:00:00Z",bull));assertFalse(e.completed.contains("bullring-bull"));
        e.add(journey("walking","2026-10-05T08:00:00Z",bull));assertTrue(e.completed.contains("bullring-bull"));
        e.add(journey("walking","2026-10-05T08:00:00Z",new double[][]{{-5.831,54.594},{-5.831,54.595}}));assertFalse(e.completed.contains("stormont"));
    }
    @Test public void catalogueHasTwelveDistinctTargetsAndRevocableFamilies()throws Exception {
        assertEquals(12,LandmarkDefinitions.ALL.length);Set<String> ids=new HashSet<>();for(String[] r:LandmarkDefinitions.ALL)assertTrue(ids.add(r[0]));
        LandmarkEvidence e=new LandmarkEvidence(app);assertEquals(12,e.checklist().size());e.legacy("angel-of-the-north",true);assertEquals(1,e.count());
        AchievementStore.Definition sightseer=AchievementStore.definitions().stream().filter(d->d.id.equals("sightseer")).findFirst().get();assertArrayEquals(new double[]{3,6,12},sightseer.levels,0);
        AchievementStore.Snapshot earned=AchievementStore.evaluate(app,Collections.singletonMap("groundhog-day",5.0),Collections.emptyMap(),new boolean[7],0);
        assertTrue(earned.achievements.stream().filter(p->p.definition.id.equals("groundhog-day")).findFirst().get().unlocked);
        AchievementStore.Snapshot deleted=AchievementStore.evaluate(app,Collections.singletonMap("groundhog-day",0.0),Collections.emptyMap(),new boolean[7],1);
        assertFalse(deleted.achievements.stream().filter(p->p.definition.id.equals("groundhog-day")).findFirst().get().unlocked);
        assertTrue(AchievementStore.evaluate(app,Collections.singletonMap("groundhog-day",5.0),Collections.emptyMap(),new boolean[7],2).newlyUnlocked.isEmpty());
    }
    @Test public void distinctLakeMileageDoesNotCountRepeatedOrSeparateJourneys()throws Exception {
        double lat=57.2,scale=111320*Math.cos(Math.toRadians(lat));double[][] path={{-4.5,lat},{-4.5+6000/scale,lat}};
        JSONObject r=new JSONObject().put("id","loch-ness").put("kind","distance").put("modes",new JSONArray().put("driving")).put("minimum",5000).put("tolerance",25)
            .put("roads",new JSONArray().put(new JSONObject().put("ref","A82").put("coordinates",points(path))));
        JSONObject doc=new JSONObject().put("version",1).put("landmarks",new JSONArray().put(r));LandmarkEvidence e=new LandmarkEvidence(doc);
        double[] midway={-4.5+2000/scale,lat};double[][] repeated={path[0],midway,path[0],midway,path[0],midway};
        e.add(road(journey("driving","2026-10-05T08:00:00Z",repeated),"A82"));assertFalse(e.completed.contains("loch-ness"));
        e.add(road(journey("driving","2026-10-06T08:00:00Z",new double[][]{midway,path[1]}),"A82"));assertFalse(e.completed.contains("loch-ness"));
        e.add(road(journey("driving","2026-10-07T08:00:00Z",path),"A82"));assertTrue(e.completed.contains("loch-ness"));
        LandmarkEvidence actual=new LandmarkEvidence(app);Set<String> refs=new HashSet<>();for(LandmarkEvidence.Sample sample:reference(actual,"loch-ness").samples)refs.add(sample.ref);
        assertEquals(new HashSet<>(Arrays.asList("A82","B852","B862")),refs);
    }
    @Test public void archiveScanBoundsTrainGeometryAndKeepsTimezone()throws Exception {
        JSONObject train=journey("train","2026-10-05T08:00:00Z",route(0,false));JSONArray raw=new JSONArray();
        for(int i=0;i<20000;i++)raw.put(new JSONArray().put(-.13+i*.000001).put(51.5));
        train.remove("processing_result");train.put("route_geometry",new JSONObject().put("type","LineString").put("coordinates",raw)).put("raw_capture_samples",raw);
        JourneyStore.save(app,train);List<JSONObject> read=new ArrayList<>();JourneyStore.forEachAchievementEvidence(app,()->false,read::add);
        assertEquals(1,read.size());JSONArray retained=read.get(0).getJSONObject("route_geometry").getJSONArray("coordinates");assertTrue(retained.length()<=258);
        for(int axis=0;axis<2;axis++){assertEquals(raw.getJSONArray(0).getDouble(axis),retained.getJSONArray(0).getDouble(axis),1e-12);assertEquals(raw.getJSONArray(19999).getDouble(axis),retained.getJSONArray(retained.length()-1).getDouble(axis),1e-12);}
        assertTrue(retained.getJSONArray(0).get(0) instanceof Number);
        assertEquals("Europe/London",read.get(0).getString("timezone"));assertFalse(read.get(0).has("raw_capture_samples"));assertNotNull(GroundhogDayEvidence.read(read.get(0)));
    }
    @Test public void motorwayPromenadeAndAreasUseTheirSpecificModesAndReferences()throws Exception {
        LandmarkEvidence e=new LandmarkEvidence(app);LandmarkEvidence.Reference m4=reference(e,"windsor-castle");double[][] path=new double[m4.samples.size()][2];
        for(int i=0;i<path.length;i++){LandmarkEvidence.Sample sample=m4.samples.get(i);path[i]=new double[]{sample.x/m4.scale,sample.y/LandmarkEvidence.METRES};}
        e.add(road(journey("walking","2026-10-05T08:00:00Z",path),"M4"));assertFalse(e.completed.contains("windsor-castle"));
        e.add(journey("driving","2026-10-05T08:00:00Z",path));assertFalse(e.completed.contains("windsor-castle"));
        e.add(road(journey("bus","2026-10-05T08:00:00Z",path),"M4"));assertTrue(e.completed.contains("windsor-castle"));
        double[][] tower=full(reference(e,"blackpool-tower"));e.add(journey("walking","2026-10-05T08:00:00Z",tower));assertTrue(e.completed.contains("blackpool-tower"));
        double[][] ness={{1.7625,52.4811973},{1.763,52.4811973}};e.add(journey("driving","2026-10-05T08:00:00Z",ness));assertFalse(e.completed.contains("ness-point"));
        e.add(journey("cycling","2026-10-05T08:00:00Z",ness));assertTrue(e.completed.contains("ness-point"));
        e.add(journey("walking","2026-10-05T08:00:00Z",new double[][]{{-3.8269,53.2801},{-3.8264,53.2801}}));assertTrue(e.completed.contains("conwy-castle"));
        e.add(journey("walking","2026-10-05T08:00:00Z",new double[][]{{-5.832,54.6042},{-5.832,54.6052}}));assertTrue(e.completed.contains("stormont"));
    }
    @Test public void undergroundAndDeletedRoadEvidenceCannotEarnLandmarks()throws Exception {
        LandmarkEvidence e=new LandmarkEvidence(app);double[][] bridge=full(reference(e,"big-ben"));JSONObject underground=journey("walking","2026-10-05T08:00:00Z",bridge);
        underground.getJSONObject("processing_result").getJSONObject("geojson").getJSONArray("features").getJSONObject(0).getJSONObject("properties").put("tunnel","yes");
        e.add(underground);assertFalse(e.completed.contains("big-ben"));
        JSONObject deleted=road(journey("walking","2026-10-05T08:00:00Z",bridge),"A302");deleted.put("journey_corrections",new JSONObject().put("removed_road_ids",new JSONArray().put("ref:A302")));
        e.add(deleted);assertFalse(e.completed.contains("big-ben"));
    }
    @Test public void sightseerDowngradesWithoutRepeatingMilestoneRecognition()throws Exception {
        AchievementStore.Snapshot first=AchievementStore.evaluate(app,Collections.singletonMap("sightseer",6.0),Collections.emptyMap(),new boolean[7],0);
        AchievementStore.Progress p=first.achievements.stream().filter(v->v.definition.id.equals("sightseer")).findFirst().get();assertEquals(2,p.level);
        AchievementStore.Snapshot lower=AchievementStore.evaluate(app,Collections.singletonMap("sightseer",2.0),Collections.emptyMap(),new boolean[7],1);
        p=lower.achievements.stream().filter(v->v.definition.id.equals("sightseer")).findFirst().get();assertFalse(p.unlocked);assertEquals(2,p.milestones.size());
        assertTrue(AchievementStore.evaluate(app,Collections.singletonMap("sightseer",6.0),Collections.emptyMap(),new boolean[7],2).newlyUnlocked.isEmpty());
    }
}
