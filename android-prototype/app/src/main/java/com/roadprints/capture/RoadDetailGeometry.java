package com.roadprints.capture;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Clip at actual polygon crossings; retain holes and disconnected pieces. */
final class RoadDetailGeometry {
    static List<JSONArray> lines(JSONObject geometry, JSONObject boundary) {
        List<JSONArray> source=new ArrayList<>(), output=new ArrayList<>();
        if(geometry==null) return output;
        JSONArray c=geometry.optJSONArray("coordinates");
        if("LineString".equals(geometry.optString("type"))) { if(c!=null)source.add(c); }
        else if("MultiLineString".equals(geometry.optString("type"))&&c!=null)
            for(int i=0;i<c.length();i++) if(c.optJSONArray(i)!=null) source.add(c.optJSONArray(i));
        List<JSONArray> rings=new ArrayList<>(); if(boundary!=null) rings(boundary,rings);
        for(JSONArray line:source) for(int i=1;i<line.length();i++) {
            JSONArray a=line.optJSONArray(i-1), b=line.optJSONArray(i);
            if(!valid(a)||!valid(b)) continue;
            if(boundary==null) { output.add(new JSONArray().put(a).put(b));continue; }
            List<Double> cuts=new ArrayList<>();cuts.add(0d);cuts.add(1d);
            double ax=a.optDouble(0),ay=a.optDouble(1),dx=b.optDouble(0)-ax,dy=b.optDouble(1)-ay;
            for(JSONArray ring:rings) for(int j=1;j<ring.length();j++) {
                JSONArray p=ring.optJSONArray(j-1),q=ring.optJSONArray(j);if(!valid(p)||!valid(q))continue;
                double ex=q.optDouble(0)-p.optDouble(0),ey=q.optDouble(1)-p.optDouble(1);
                double cross=dx*ey-dy*ex;if(Math.abs(cross)<1e-18)continue;
                double px=p.optDouble(0)-ax,py=p.optDouble(1)-ay;
                double t=(px*ey-py*ex)/cross,u=(px*dy-py*dx)/cross;
                if(t>0&&t<1&&u>=0&&u<=1)cuts.add(t);
            }
            Collections.sort(cuts);
            for(int j=1;j<cuts.size();j++) {
                double low=cuts.get(j-1),high=cuts.get(j);if(high-low<1e-12)continue;
                double mid=(low+high)/2;
                if(contains(boundary,ax+dx*mid,ay+dy*mid)) output.add(new JSONArray()
                        .put(point(ax+dx*low,ay+dy*low)).put(point(ax+dx*high,ay+dy*high)));
            }
        }
        return output;
    }
    static boolean valid(JSONArray p) {
        return p!=null&&p.length()>=2&&Double.isFinite(p.optDouble(0,Double.NaN))
                &&Double.isFinite(p.optDouble(1,Double.NaN))&&Math.abs(p.optDouble(0))<=180&&Math.abs(p.optDouble(1))<=90;
    }
    private static JSONArray point(double x,double y) {
        JSONArray p=new JSONArray();try{p.put(x);p.put(y);}catch(org.json.JSONException e){throw new IllegalArgumentException(e);}return p;
    }
    private static void rings(JSONObject g,List<JSONArray> output) {
        String type=g.optString("type");JSONArray c=g.optJSONArray("coordinates");
        if("Feature".equals(type)) { JSONObject geometry=g.optJSONObject("geometry");if(geometry!=null)rings(geometry,output); }
        else if("FeatureCollection".equals(type)) { JSONArray f=g.optJSONArray("features");if(f!=null)for(int i=0;i<f.length();i++)if(f.optJSONObject(i)!=null)rings(f.optJSONObject(i),output); }
        else if("Polygon".equals(type)&&c!=null) { for(int i=0;i<c.length();i++)if(c.optJSONArray(i)!=null)output.add(c.optJSONArray(i)); }
        else if("MultiPolygon".equals(type)&&c!=null) for(int i=0;i<c.length();i++) { JSONArray p=c.optJSONArray(i);if(p!=null)for(int j=0;j<p.length();j++)if(p.optJSONArray(j)!=null)output.add(p.optJSONArray(j)); }
    }
    private static boolean contains(JSONObject g,double x,double y) {
        String type=g.optString("type");JSONArray c=g.optJSONArray("coordinates");
        if("Feature".equals(type)) { JSONObject p=g.optJSONObject("geometry");return p!=null&&contains(p,x,y); }
        if("FeatureCollection".equals(type)) { JSONArray f=g.optJSONArray("features");if(f!=null)for(int i=0;i<f.length();i++)if(f.optJSONObject(i)!=null&&contains(f.optJSONObject(i),x,y))return true; }
        if("Polygon".equals(type))return polygon(c,x,y);
        if("MultiPolygon".equals(type)&&c!=null)for(int i=0;i<c.length();i++)if(polygon(c.optJSONArray(i),x,y))return true;
        return false;
    }
    private static boolean polygon(JSONArray rings,double x,double y) {
        if(rings==null||!inside(rings.optJSONArray(0),x,y))return false;
        for(int i=1;i<rings.length();i++)if(inside(rings.optJSONArray(i),x,y))return false;
        return true;
    }
    private static boolean inside(JSONArray ring,double x,double y) {
        if(ring==null||ring.length()<4)return false;boolean inside=false;
        for(int i=0,j=ring.length()-1;i<ring.length();j=i++) {
            JSONArray a=ring.optJSONArray(i),b=ring.optJSONArray(j);if(!valid(a)||!valid(b))continue;
            double ax=a.optDouble(0),ay=a.optDouble(1),bx=b.optDouble(0),by=b.optDouble(1);
            if((ay>y)!=(by>y)&&x<(bx-ax)*(y-ay)/(by-ay)+ax)inside=!inside;
        }
        return inside;
    }
}
