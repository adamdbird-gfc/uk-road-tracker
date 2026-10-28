package com.roadprints.capture;

import java.util.*;
import org.json.*;

/** Actual retained route edges, never the complete geometry of a touched road. */
final class TravelAchievementRoutes {
    static boolean foot(String mode) { return Arrays.asList("walking","running","pedestrian").contains(mode); }
    static boolean eligible(String mode) { return foot(mode)||Arrays.asList("driving","bus","cycling","bicycle").contains(mode); }
    static double metres(double[] a,double[] b) {
        double x=(a[0]-b[0])*111320*Math.cos(Math.toRadians((a[1]+b[1])/2));
        return Math.hypot(x,(a[1]-b[1])*111320);
    }
    static double[] point(JSONArray p) {
        if(p==null||p.length()<2)return null;
        double x=p.optDouble(0,Double.NaN),y=p.optDouble(1,Double.NaN);
        return Double.isFinite(x)&&Double.isFinite(y)&&Math.abs(x)<=180&&Math.abs(y)<=85?new double[]{x,y}:null;
    }
    static List<List<double[]>> matched(JSONObject journey) {
        return matched(journey,false);
    }
    static List<List<double[]>> matched(JSONObject journey,boolean surfaceOnly) {
        JSONObject result=journey.optJSONObject("processing_result");
        JSONObject collection=result==null?null:result.optJSONObject("geojson");
        JSONArray features=collection==null?null:collection.optJSONArray("features");
        List<List<double[]>> parts=new ArrayList<>();if(features==null)return parts;
        JSONObject edits=journey.optJSONObject("journey_corrections");
        JSONArray removed=edits==null?null:edits.optJSONArray("removed_matched_segments");
        Set<Integer> removedEdges=new HashSet<>();if(removed!=null)for(int i=0;i<removed.length();i++)removedEdges.add(removed.optInt(i,-1));
        List<JSONObject> deletedRoads=new ArrayList<>();
        if(!JourneyCorrectionUtils.removedRoadIds(journey).isEmpty())
            for(String field:new String[]{"road_geojson","motorway_geojson","a_road_geojson"}) {
                JSONObject roads=result.optJSONObject(field);JSONArray rows=roads==null?null:roads.optJSONArray("features");
                if(rows!=null)for(int i=0;i<rows.length();i++) {
                    JSONObject row=rows.optJSONObject(i);if(row!=null&&JourneyCorrectionUtils.excludesRoadFeature(journey,row))deletedRoads.add(row);
                }
            }
        int edge=0;
        for(int f=0;f<features.length();f++) {
            JSONObject feature=features.optJSONObject(f);
            JSONObject properties=feature==null?null:feature.optJSONObject("properties");
            boolean underground=surfaceOnly&&properties!=null&&(properties.optInt("layer",0)<0
                    ||(!properties.optString("tunnel","").isEmpty()&&!"no".equals(properties.optString("tunnel"))));
            for(JSONArray line:CoreAchievementEvidence.lines(feature==null?null:feature.optJSONObject("geometry"))) {
                List<double[]> part=new ArrayList<>();
                for(int p=1;p<line.length();p++,edge++) {
                    double[] a=point(line.optJSONArray(p-1)),b=point(line.optJSONArray(p));
                    boolean deleted=underground||removedEdges.contains(edge)||JourneyCorrectionUtils.excludesRoadFeature(journey,feature);
                    if(a!=null&&b!=null&&!deleted)for(JSONObject road:deletedRoads)
                        if(JourneyCorrectionUtils.distanceToGeometryMetres((a[0]+b[0])/2,(a[1]+b[1])/2,
                                road.optJSONObject("geometry"))<=35){deleted=true;break;}
                    if(a==null||b==null||deleted) {if(part.size()>1)parts.add(part);part=new ArrayList<>();continue;}
                    if(part.isEmpty())part.add(a);
                    if(metres(a,b)>.01)part.add(b);
                }
                if(part.size()>1)parts.add(part);
            }
        }
        return parts;
    }
    static boolean hasRoad(JSONObject journey,String ref) {
        if(JourneyCorrectionUtils.removedRoadIds(journey).contains("ref:"+ref.toUpperCase(Locale.ROOT)))return false;
        JSONObject result=journey.optJSONObject("processing_result");if(result==null)return false;
        for(String field:new String[]{"road_geojson","motorway_geojson","a_road_geojson"}) {
            JSONObject roads=result.optJSONObject(field);JSONArray features=roads==null?null:roads.optJSONArray("features");
            if(features==null)continue;
            for(int i=0;i<features.length();i++) {
                JSONObject f=features.optJSONObject(i),p=f==null?null:f.optJSONObject("properties");
                if(p==null||JourneyCorrectionUtils.excludesRoadFeature(journey,f))continue;
                String value=p.optString("road_ref",p.optString("ref",""));
                for(String r:value.split("[;,]"))if(ref.equalsIgnoreCase(r.trim().replace("GB:","")))return true;
            }
        }
        return false;
    }
}
