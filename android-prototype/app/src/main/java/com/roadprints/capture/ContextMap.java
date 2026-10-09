package com.roadprints.capture;

import org.json.JSONArray;
import org.json.JSONObject;

/** Public map evidence. A POI name alone never establishes arrival. */
final class ContextMap {
    static final class Evidence {
        boolean available, rail, road, outdoor, destination, parking;
        String name = "", kind = "unknown";
    }
    static Evidence at(JSONArray features, double lat, double lon, float accuracy) {
        Evidence result = new Evidence();
        if (features == null || !Float.isFinite(accuracy) || accuracy <= 0 || accuracy > 50) return result;
        result.available = true;
        double roadDistance = Double.POSITIVE_INFINITY;
        for (int i=0;i<features.length();i++) {
            JSONObject f=features.optJSONObject(i); if(f==null)continue;
            JSONObject tags=f.optJSONObject("tags"); if(tags==null)continue;
            JSONObject bounds=f.optJSONObject("bounds");
            if(bounds!=null && (lat<bounds.optDouble("minlat")-.001 || lat>bounds.optDouble("maxlat")+.001
                    ||lon<bounds.optDouble("minlon")-.002 ||lon>bounds.optDouble("maxlon")+.002))continue;
            JSONArray geometry=f.optJSONArray("geometry");
            double distance=distance(geometry,lat,lon);
            String highway=tags.optString("highway"), railway=tags.optString("railway");
            if ("rail".equals(railway) && distance<=Math.max(15,accuracy)) result.rail=true;
            if (!highway.isEmpty() && !"footway".equals(highway) && !"path".equals(highway)
                    && !"steps".equals(highway) && !"pedestrian".equals(highway)
                    && !"cycleway".equals(highway)) roadDistance=Math.min(roadDistance,distance);
            if (inside(geometry,lat,lon)) {
                if (tags.has("leisure") || tags.has("natural")) result.outdoor=true;
                boolean parking="parking".equals(tags.optString("amenity"));
                boolean building=tags.has("building") && !"no".equals(tags.optString("building"));
                // The entire accuracy circle must fit in the mapped footprint.
                if ((building || parking) && distance>accuracy) {
                    result.destination=true; result.parking=parking;
                    result.kind=parking?"parking":("house".equals(tags.optString("building"))
                            || "residential".equals(tags.optString("building"))?"residential":"building");
                    result.name=tags.optString("name",result.kind);
                }
            }
        }
        result.road=roadDistance<=Math.max(15,accuracy);
        if(result.road)result.destination=false;
        return result;
    }
    static double distance(JSONArray geometry,double lat,double lon) {
        double best=Double.POSITIVE_INFINITY;
        if(geometry==null)return best;
        double scale=Math.cos(Math.toRadians(lat));
        for(int i=1;i<geometry.length();i++) {
            JSONObject a=geometry.optJSONObject(i-1),b=geometry.optJSONObject(i);
            if(a==null||b==null)continue;
            double ax=(a.optDouble("lon")-lon)*111195*scale,ay=(a.optDouble("lat")-lat)*111195;
            double bx=(b.optDouble("lon")-lon)*111195*scale,by=(b.optDouble("lat")-lat)*111195;
            double dx=bx-ax,dy=by-ay,den=dx*dx+dy*dy;
            double t=den==0?0:Math.max(0,Math.min(1,-(ax*dx+ay*dy)/den));
            best=Math.min(best,Math.hypot(ax+t*dx,ay+t*dy));
        }
        return best;
    }
    static boolean inside(JSONArray geometry,double lat,double lon) {
        if(geometry==null||geometry.length()<4)return false;
        JSONObject first=geometry.optJSONObject(0),last=geometry.optJSONObject(geometry.length()-1);
        if(first==null||last==null||first.optDouble("lat")!=last.optDouble("lat")
                ||first.optDouble("lon")!=last.optDouble("lon"))return false;
        boolean inside=false;
        for(int i=0,j=geometry.length()-1;i<geometry.length();j=i++) {
            JSONObject a=geometry.optJSONObject(i),b=geometry.optJSONObject(j);
            if(a==null||b==null)return false;
            double y=a.optDouble("lat"),z=b.optDouble("lat");
            if((y>lat)!=(z>lat) && lon<(b.optDouble("lon")-a.optDouble("lon"))*(lat-y)/(z-y)+a.optDouble("lon"))inside=!inside;
        }
        return inside;
    }
}
