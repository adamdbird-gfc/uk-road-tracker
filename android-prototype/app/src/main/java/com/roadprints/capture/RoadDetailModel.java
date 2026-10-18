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
        final List<JSONArray> lines=new ArrayList<>();
        boolean contributes;
        Entry(JSONObject journey) {
            id=journey.optString("journey_id");title=journey.optString("title",journey.optString("name","Journey"));
            date=journey.optString("started_at");
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
        if(RoadDetailSelection.NCN.equals(selection.kind))return NcnProgressStore.detail(context,selection.label,selection.ncnMode);
        JSONObject boundary=RoadDetailSelection.LOCAL.equals(selection.kind)?LocalRoadSettlementMatcher.boundary(selection.settlementCode):null;
        if(RoadDetailSelection.LOCAL.equals(selection.kind)&&boundary==null)throw new IllegalStateException("Town boundary unavailable");
        RoadDetailModel output=new RoadDetailModel();output.boundary=boundary;
        MotorwayProgressCalculator motorway=!cumulative&&RoadDetailSelection.MOTORWAY.equals(selection.kind)?new MotorwayProgressCalculator(context):null;
        ARoadProgressCalculator aRoad=!cumulative&&RoadDetailSelection.A_ROAD.equals(selection.kind)?new ARoadProgressCalculator(context):null;
        Set<String> refs=Collections.singleton(RoadDetailSelection.normalizeRef(selection.label));
        JourneyStore.forEach(context,journey->{
            if(!"complete".equals(journey.optString("processing_status")))return;
            if(motorway!=null)motorway.addJourney(journey,refs);
            if(aRoad!=null)aRoad.addJourney(journey,refs);
            Entry entry=collect(journey,selection,boundary,cumulative);
            if(entry.contributes||!entry.lines.isEmpty()) {
                output.journeys.add(entry);
                for(JSONArray line:entry.lines) {
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
        output.journeys.sort((a,b)->b.date.compareTo(a.date));
        return output;
    }
    List<Entry> journeysAt(Context context,RoadDetailSelection selection,double latitude,double longitude) {
        if(ncn){List<Entry> related=new ArrayList<>();for(Entry e:journeys)if(e.near(latitude,longitude))related.add(e);return related;}
        Set<String> ids=new HashSet<>();for(Entry e:journeys)ids.add(e.id);
        List<Entry> related=new ArrayList<>();
        JourneyStore.forEach(context,journey->{
            if(!ids.contains(journey.optString("journey_id")))return;
            Entry entry=collect(journey,selection,boundary,false);
            if(entry.near(latitude,longitude)){entry.lines.clear();related.add(entry);}
        });
        related.sort((a,b)->b.date.compareTo(a.date));return related;
    }
    private void setReference(double total,double travelled,double completion,List<JSONArray> yes,List<JSONArray> no) {
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
