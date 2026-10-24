package com.roadprints.capture;
import org.json.*;
/** Bounded union of mapped components; no hull, radius growth or invented connector. */
final class PlaceGeometry {
    static boolean present(JSONObject stop) {
        JSONArray parts=stop.optJSONArray("site_areas");
        if(parts!=null)for(int i=0;i<parts.length();i++)if(valid(parts.optJSONObject(i)))return true;
        return valid(newPart(stop.optJSONArray("site_geometry"),stop.optJSONArray("site_holes")));
    }
    private static JSONObject newPart(JSONArray ring,JSONArray holes) {
        try{return new JSONObject().put("geometry",ring).put("holes",holes);}catch(Exception ignored){return new JSONObject();}
    }
    private static boolean valid(JSONObject part) {
        JSONArray ring=part==null?null:part.optJSONArray("geometry");
        if(ring==null||ring.length()<4||ring.length()>2000)return false;
        for(int i=0;i<ring.length();i++) {
            JSONObject p=ring.optJSONObject(i);if(p==null||!Double.isFinite(p.optDouble("lat"))||!Double.isFinite(p.optDouble("lon"))
                ||Math.abs(p.optDouble("lat"))>90||Math.abs(p.optDouble("lon"))>180)return false;
        }
        JSONObject a=ring.optJSONObject(0),b=ring.optJSONObject(ring.length()-1);
        return a.optDouble("lat")==b.optDouble("lat")&&a.optDouble("lon")==b.optDouble("lon");
    }
    static boolean outside(JSONObject stop,FootTraceValidator.Sample p) {
        JSONArray parts=stop.optJSONArray("site_areas");
        if(parts!=null)for(int i=0;i<parts.length();i++)if(containsOrUncertain(parts.optJSONObject(i),p))return false;
        JSONObject primary=newPart(stop.optJSONArray("site_geometry"),stop.optJSONArray("site_holes"));
        return !containsOrUncertain(primary,p);
    }
    private static boolean containsOrUncertain(JSONObject part,FootTraceValidator.Sample p) {
        if(!valid(part))return false;
        JSONArray ring=part.optJSONArray("geometry"),holes=part.optJSONArray("holes");
        boolean inside=ContextMap.inside(ring,p.lat,p.lon);double edge=ContextMap.distance(ring,p.lat,p.lon);
        if(holes!=null)for(int i=0;i<holes.length();i++) {
            JSONArray h=holes.optJSONArray(i);if(ContextMap.inside(h,p.lat,p.lon))inside=false;
            edge=Math.min(edge,ContextMap.distance(h,p.lat,p.lon));
        }
        return inside||edge<=p.accuracy+10;
    }
    static void attach(JSONObject stop,ContextMap.Evidence map) throws JSONException {
        if(map==null||map.geometry==null)return;
        JSONObject snapshot=newPart(map.geometry,map.holes);
        if(!valid(snapshot))return;
        JSONArray areas=map.areas;
        if(areas!=null)snapshot.put("areas",areas);
        if(snapshot.toString().length()>100000){areas=null;snapshot.remove("areas");}
        if(snapshot.toString().length()>100000)return;
        stop.put("site_geometry",new JSONArray(map.geometry.toString()));
        if(map.holes!=null)stop.put("site_holes",new JSONArray(map.holes.toString()));
        if(areas!=null&&areas.length()<=32)stop.put("site_areas",new JSONArray(areas.toString()));
    }
}
