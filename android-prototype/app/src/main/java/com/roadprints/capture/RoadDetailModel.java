package com.roadprints.capture;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** One streamed archive assessment shared by the map and contributing-journey view. */
final class RoadDetailModel {
    static final class Entry {
        final String id,title,date;
        final JSONObject summary=new JSONObject();
        final List<JSONArray> lines=new ArrayList<>();
        boolean contributes;
        Entry(JSONObject journey) {
            id=journey.optString("journey_id");title=journey.optString("title",journey.optString("name","Journey"));
            date=journey.optString("started_at");
            for(String key:new String[]{"journey_id","title","name","mode","started_at","ended_at","distance_meters","gps_point_count","_route_point_count","source"})
                if(journey.has(key))try{summary.put(key,journey.opt(key));}catch(org.json.JSONException invalid){throw new IllegalArgumentException(invalid);}
        }
        boolean near(double latitude,double longitude) {
            for(JSONArray line:lines) try {
                JSONObject g=new JSONObject().put("type","LineString").put("coordinates",line);
                if(JourneyCorrectionUtils.distanceToGeometryMetres(longitude,latitude,g)<=120)return true;
            } catch(org.json.JSONException ignored) { }
            return false;
        }
    }
    final List<JSONArray> covered=new ArrayList<>(), remaining=new ArrayList<>();
    final List<Entry> journeys=new ArrayList<>();
    boolean fullReference, mapLimited, ncn;
    private JSONObject boundary;
    double referenceMetres, uniqueMetres, percent=Double.NaN;
    private static final int MAX_MAP_EDGES=60_000;

    static RoadDetailModel load(Context context,RoadDetailSelection selection,boolean cumulative) throws Exception {
        return load(context,selection,cumulative,null);
    }
    static RoadDetailModel load(Context context,RoadDetailSelection selection,boolean cumulative,java.util.function.Consumer<RoadDetailModel> geometryReady) throws Exception {
        if(RoadDetailSelection.NCN.equals(selection.kind))return NcnProgressStore.detail(context,selection.label,selection.ncnMode,geometryReady);
        JSONObject boundary=RoadDetailSelection.LOCAL.equals(selection.kind)?LocalRoadSettlementMatcher.boundary(selection.settlementCode):null;
        if(RoadDetailSelection.LOCAL.equals(selection.kind)&&boundary==null)throw new IllegalStateException("Town boundary unavailable");
        RoadDetailModel seed=cumulative?null:ProgressActivity.focusedPreview(context,selection);
        RoadDetailModel output=seed==null?new RoadDetailModel():seed;output.boundary=boundary;
        if(seed!=null&&geometryReady!=null)geometryReady.accept(seed.geometryPreview());
        MotorwayProgressCalculator motorway=seed==null&&!cumulative&&RoadDetailSelection.MOTORWAY.equals(selection.kind)?new MotorwayProgressCalculator(context):null;
        ARoadProgressCalculator aRoad=seed==null&&!cumulative&&RoadDetailSelection.A_ROAD.equals(selection.kind)?new ARoadProgressCalculator(context):null;
        Set<String> refs=Collections.singleton(RoadDetailSelection.normalizeRef(selection.label));
        cachedRoadEvidence(context,journey->{
            if(!"complete".equals(journey.optString("processing_status")))return;
            if(motorway!=null)motorway.addJourney(journey,refs);
            if(aRoad!=null)aRoad.addJourney(journey,refs);
            Entry entry=collect(journey,selection,boundary,cumulative);
            if(entry.contributes||!entry.lines.isEmpty()) {
                output.journeys.add(entry);
                if(seed==null)for(JSONArray line:entry.lines) {
                    if(output.covered.size()<MAX_MAP_EDGES)output.covered.add(line);else output.mapLimited=true;
                }
                entry.lines.clear(); // retain metadata, not the whole journey archive
            }
        });
        if(motorway!=null)for(MotorwayProgressCalculator.Road road:motorway.finish().roads) {
            if(!RoadDetailSelection.normalizeRef(road.ref).equals(RoadDetailSelection.normalizeRef(selection.label))
                    ||(!selection.region.isEmpty()&&!selection.region.equals(road.region)))continue;
            if(road.referenceAvailable) {
                output.setReference(road.referenceKm*1000,road.estimatedUniqueKm()*1000,road.percent(),road.coveredMapSections,road.incompleteMapSections);
            }
        }
        if(aRoad!=null)for(ARoadProgressCalculator.Road road:aRoad.finish().roads) {
            if(!road.ref.equals(selection.label)||(!selection.region.isEmpty()&&!selection.region.equals(road.region)))continue;
            if(road.referenceAvailable)output.setReference(road.totalKm*1000,road.uniqueKm()*1000,road.percent(),road.coveredMapSections,road.incompleteMapSections);
        }
        if(geometryReady!=null&&!cumulative)geometryReady.accept(output.geometryPreview());
        output.journeys.sort((a,b)->b.date.compareTo(a.date));
        return output;
    }
    static void cachedRoadEvidence(Context context,JourneyStore.JourneyVisitor visitor)throws Exception {
        JourneyStore.prepareArchive(context);try(CompactJourneyCache cache=new CompactJourneyCache(context,"road-evidence-v1")){
            for(java.io.File source:JourneyStore.archiveFiles(context)){if(Thread.currentThread().isInterrupted())throw new java.util.concurrent.CancellationException();visitor.visit(cache.get(source,file->JourneyStore.mapEvidence(context,file,false)));}
            PerformanceDiagnostics.timing("focused_archive",0,"reads="+cache.reads+" reused="+cache.hits);
        }
    }
    List<Entry> journeysAt(Context context,RoadDetailSelection selection,double latitude,double longitude) throws Exception {
        if(ncn){List<Entry> related=new ArrayList<>();Section selected=sectionAt(latitude,longitude);if(selected==null||!selected.travelled)return related;for(Entry e:journeys)for(JSONArray line:e.lines)if(NcnGeometry.overlaps(line,selected.line)){related.add(e);break;}return related;}
        Set<String> ids=new HashSet<>();for(Entry e:journeys)ids.add(e.id);
        List<Entry> related=new ArrayList<>();
        cachedRoadEvidence(context,journey->{
            if(!ids.contains(journey.optString("journey_id")))return;
            Entry entry=collect(journey,selection,boundary,false);
            if(entry.near(latitude,longitude)){entry.lines.clear();related.add(entry);}
        });
        related.sort((a,b)->b.date.compareTo(a.date));return related;
    }
    RoadDetailModel geometryPreview() {
        RoadDetailModel preview=new RoadDetailModel();preview.covered.addAll(covered);preview.remaining.addAll(remaining);
        preview.ncn=ncn;preview.fullReference=fullReference;preview.mapLimited=mapLimited;preview.referenceMetres=referenceMetres;
        preview.uniqueMetres=uniqueMetres;preview.percent=percent;return preview;
    }
    static final class Section {
        final double latitude,longitude,metres;final boolean travelled;final JSONArray line;
        Section(double lat,double lon,double metres,boolean travelled){this(lat,lon,metres,travelled,null);}
        Section(double lat,double lon,double metres,boolean travelled,JSONArray line){latitude=lat;longitude=lon;this.metres=metres;this.travelled=travelled;this.line=line;}
    }
    /** Display statistics for the nearest rendered edge, never a new coverage calculation. */
    Section sectionAt(double latitude,double longitude) {
        Section best=null;double nearest=Double.POSITIVE_INFINITY;
        for(boolean travelled:new boolean[]{true,false})for(JSONArray line:travelled?covered:remaining)for(int i=1;i<line.length();i++) {
            JSONArray a=line.optJSONArray(i-1),b=line.optJSONArray(i);if(!RoadDetailGeometry.valid(a)||!RoadDetailGeometry.valid(b))continue;
            double scale=Math.cos(Math.toRadians(latitude))*111195,sy=111195;
            double ax=(a.optDouble(0)-longitude)*scale,ay=(a.optDouble(1)-latitude)*sy;
            double bx=(b.optDouble(0)-longitude)*scale,by=(b.optDouble(1)-latitude)*sy,dx=bx-ax,dy=by-ay;
            double t=dx*dx+dy*dy==0?0:Math.max(0,Math.min(1,-(ax*dx+ay*dy)/(dx*dx+dy*dy)));
            double distance=Math.hypot(ax+t*dx,ay+t*dy);
            if(distance<nearest){nearest=distance;best=new Section((a.optDouble(1)+b.optDouble(1))/2,(a.optDouble(0)+b.optDouble(0))/2,Math.hypot(dx,dy),travelled,new JSONArray().put(a).put(b));}
        }
        return best;
    }
    void setReference(double total,double travelled,double completion,List<JSONArray> yes,List<JSONArray> no) {
        // The selection always carries a region for numbered roads. Never merge GB/NI totals.
        fullReference=true;referenceMetres=total;uniqueMetres=travelled;percent=completion;
        covered.clear();covered.addAll(yes);remaining.clear();remaining.addAll(no);
    }
    static Entry collect(JSONObject journey,RoadDetailSelection selection,JSONObject boundary,boolean cumulative) {
        Entry entry=new Entry(journey);
        String mode=journey.optString("mode","").toLowerCase(java.util.Locale.ROOT);
        boolean driven="driving".equals(mode)||"bus".equals(mode);
        boolean foot="walking".equals(mode)||"running".equals(mode)||"pedestrian".equals(mode);
        if(!"complete".equals(journey.optString("processing_status"))||(!driven&&!foot&&!MatchingCoordinator.isCycle(mode)))return entry;
        JSONObject result=journey.optJSONObject("processing_result");if(result==null)return entry;
        String canonical=RoadDetailSelection.MOTORWAY.equals(selection.kind)?"motorway_geojson":RoadDetailSelection.A_ROAD.equals(selection.kind)?"a_road_geojson":null;
        if(cumulative&&canonical!=null&&!driven)return entry;
        String[] fields=cumulative&&canonical!=null?new String[]{canonical}:new String[]{"road_geojson","motorway_geojson","a_road_geojson","cycling_geojson"};
        Set<String> seen=new HashSet<>();
        for(String field:fields) {
            JSONObject collection=result.optJSONObject(field);JSONArray features=collection==null?null:collection.optJSONArray("features");
            if(features==null)continue;
            for(int i=0;i<features.length();i++) {
                JSONObject feature=features.optJSONObject(i);
                if(!selection.matches(feature)||JourneyCorrectionUtils.excludesRoadFeature(journey,feature))continue;
                // Cumulative calculators count retained feature contributions even if
                // their geometry cannot be displayed. Do not lose those journeys.
                if(cumulative&&canonical!=null){entry.contributes=true;return entry;}
                JSONObject geometry=feature.optJSONObject("geometry");
                if(geometry==null||!seen.add(geometry.toString()))continue;
                entry.lines.addAll(RoadDetailGeometry.lines(geometry,boundary));
            }
        }
        return entry;
    }
}
