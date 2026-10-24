package com.roadprints.capture;

import org.json.JSONArray;
import org.json.JSONObject;

/** Capture evidence is independent of whether a router can connect two fixes. */
final class CaptureQualityValidator {
    static boolean fresh(long now,long fixTime,long receivedAge) {
        return fixTime>0&&Math.abs(now-fixTime)<=60000&&receivedAge>=-1&&receivedAge<=60000;
    }
    static boolean reliable(FootTraceValidator.Sample a, FootTraceValidator.Sample b, boolean foot) {
        if (!Double.isFinite(b.lon) || !Double.isFinite(b.lat) || Math.abs(b.lon)>180
                || Math.abs(b.lat)>90 || !Double.isFinite(b.accuracy)
                || b.accuracy<=0 || b.accuracy>(foot?50:100) || b.time<=0) return false;
        if (a==null) return true;
        if (b.time<=a.time) return false;
        return foot ? FootTraceValidator.plausible(a,b)
                : FootTraceValidator.metres(a,b)<=70*(b.time-a.time)/1000.0+Math.min(50,a.accuracy+b.accuracy);
    }

    static JSONObject inspect(JSONArray samples, boolean foot) throws Exception {
        JSONObject out=new JSONObject().put("version",1).put("evidence_available",samples!=null);
        JSONArray issues=new JSONArray();
        if(samples!=null) for(int i=0;i<samples.length();i++) {
            FootTraceValidator.Sample b=sample(samples.getJSONArray(i),i);
            FootTraceValidator.Sample a=i==0?null:sample(samples.getJSONArray(i-1),i-1);
            if(!reliable(a,b,foot)) issues.put(new JSONObject().put("point_index",i).put("reason","unreliable_fix"));
            if(a!=null && b.time-a.time>120000 && FootTraceValidator.metres(a,b)>
                    Math.max(foot?75:300, a.accuracy+b.accuracy))
                issues.put(new JSONObject().put("point_index",i).put("reason","moving_gps_gap")
                        .put("duration_ms",b.time-a.time).put("displacement_m",Math.round(FootTraceValidator.metres(a,b))));
        }
        return out.put("resolved",issues.length()==0).put("issues",issues);
    }
    /** Display-only segmentation. Raw observation arrays and historical records stay intact.
     * Null means no aligned timing evidence or no break; empty means no supported edge. */
    static java.util.List<JSONArray> displaySections(JSONObject journey) {
        JSONArray samples=journey.optJSONArray("capture_route_samples");
        JSONObject geometry=journey.optJSONObject("route_geometry");
        JSONArray coords=geometry==null?null:geometry.optJSONArray("coordinates");
        if(samples==null||coords==null||samples.length()!=coords.length()||samples.length()<2)return null;
        boolean foot=MatchingCoordinator.isFoot(journey.optString("mode"));
        java.util.List<JSONArray> sections=new java.util.ArrayList<>();
        JSONArray current=null;boolean broken=false;
        for(int i=0;i<samples.length();i++) {
            JSONArray p=samples.optJSONArray(i),c=coords.optJSONArray(i);
            if(p==null||c==null||p.optDouble(0)!=c.optDouble(0)||p.optDouble(1)!=c.optDouble(1))return null;
        }
        for(int i=1;i<samples.length();i++) {
            FootTraceValidator.Sample a=sample(samples.optJSONArray(i-1),i-1),b=sample(samples.optJSONArray(i),i);
            boolean edge=reliable(null,a,foot)&&reliable(a,b,foot)&&b.time-a.time<=120000;
            if(!edge) {broken=true;if(current!=null)sections.add(current);current=null;continue;}
            if(current==null){current=new JSONArray();current.put(coords.optJSONArray(i-1));}
            current.put(coords.optJSONArray(i));
        }
        if(current!=null)sections.add(current);
        return broken?sections:null;
    }
    private static FootTraceValidator.Sample sample(JSONArray p,int index) {
        return new FootTraceValidator.Sample(p.optDouble(0),p.optDouble(1),p.optDouble(2,-1),p.optLong(3),p.optDouble(4,-1),index);
    }
}
