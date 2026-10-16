package com.roadprints.capture;

import org.json.JSONArray;
import org.json.JSONObject;

/** Offline service grounds, including holes cut for adjacent main carriageways. */
final class ServiceSiteBoundary {
    private ServiceSiteBoundary() {}
    static boolean present(JSONObject venue) { return venue.has("site_boundary"); }

    static boolean supports(JSONObject venue,double lat,double lng,double accuracy) {
        if(!Double.isFinite(lat)||!Double.isFinite(lng)||!Double.isFinite(accuracy)||accuracy<0
                ||Math.abs(lat)>90||Math.abs(lng)>180)return false;
        JSONObject geometry=venue.optJSONObject("site_boundary");
        if(!valid(geometry))return false; // A malformed footprint must never fall back to a larger circle.
        JSONArray polygons=geometry.optJSONArray("coordinates");
        for(int i=0;i<polygons.length();i++) {
            JSONArray polygon=polygons.optJSONArray(i);
            if(!inside(polygon.optJSONArray(0),lat,lng))continue;
            boolean supported=true;
            for(int r=0;r<polygon.length();r++) {
                JSONArray ring=polygon.optJSONArray(r);
                if((r>0&&inside(ring,lat,lng))||clearance(ring,lat,lng)+0.001<accuracy){supported=false;break;}
            }
            if(supported)return true;
        }
        return false;
    }

    static boolean valid(JSONObject geometry) {
        if(geometry==null||!"MultiPolygon".equals(geometry.optString("type")))return false;
        JSONArray polygons=geometry.optJSONArray("coordinates");
        if(polygons==null||polygons.length()==0||polygons.length()>100)return false;
        int vertices=0;
        for(int i=0;i<polygons.length();i++) {
            JSONArray polygon=polygons.optJSONArray(i);
            if(polygon==null||polygon.length()==0||polygon.length()>100)return false;
            for(int r=0;r<polygon.length();r++) {
                JSONArray ring=polygon.optJSONArray(r);
                if(ring==null||ring.length()<4||(vertices+=ring.length())>20000)return false;
                for(int p=0;p<ring.length();p++) {
                    JSONArray point=ring.optJSONArray(p);
                    if(point==null||point.length()!=2)return false;
                    double x=point.optDouble(0,Double.NaN),y=point.optDouble(1,Double.NaN);
                    if(!Double.isFinite(x)||!Double.isFinite(y)||x< -9||x>2||y<49||y>61)return false;
                }
                JSONArray a=ring.optJSONArray(0),b=ring.optJSONArray(ring.length()-1);
                if(a.optDouble(0)!=b.optDouble(0)||a.optDouble(1)!=b.optDouble(1))return false;
            }
        }
        return true;
    }

    private static boolean inside(JSONArray ring,double lat,double lng) {
        boolean in=false;
        for(int i=0,j=ring.length()-1;i<ring.length();j=i++) {
            JSONArray a=ring.optJSONArray(i),b=ring.optJSONArray(j);
            double ax=a.optDouble(0),ay=a.optDouble(1),bx=b.optDouble(0),by=b.optDouble(1);
            if((ay>lat)!=(by>lat)&&lng<(bx-ax)*(lat-ay)/(by-ay)+ax)in=!in;
        }
        return in;
    }

    private static double clearance(JSONArray ring,double lat,double lng) {
        double nearest=Double.MAX_VALUE,sx=111195*Math.cos(Math.toRadians(lat)),sy=111195;
        for(int i=1;i<ring.length();i++) {
            JSONArray a=ring.optJSONArray(i-1),b=ring.optJSONArray(i);
            double ax=(a.optDouble(0)-lng)*sx,ay=(a.optDouble(1)-lat)*sy;
            double bx=(b.optDouble(0)-lng)*sx,by=(b.optDouble(1)-lat)*sy;
            double dx=bx-ax,dy=by-ay,length=dx*dx+dy*dy;
            double t=length==0?0:Math.max(0,Math.min(1,-(ax*dx+ay*dy)/length));
            nearest=Math.min(nearest,Math.hypot(ax+t*dx,ay+t*dy));
        }
        return nearest;
    }
}
