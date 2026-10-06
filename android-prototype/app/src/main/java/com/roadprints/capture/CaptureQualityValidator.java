package com.roadprints.capture;

import org.json.JSONArray;
import org.json.JSONObject;

/** Capture evidence is independent of whether a router can connect two fixes. */
final class CaptureQualityValidator {
    static boolean reliable(FootTraceValidator.Sample a, FootTraceValidator.Sample b, boolean foot) {
        if (!Double.isFinite(b.lon) || !Double.isFinite(b.lat) || Math.abs(b.lon)>180
                || Math.abs(b.lat)>90 || b.accuracy<0 || b.accuracy>100 || b.time<=0) return false;
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
    private static FootTraceValidator.Sample sample(JSONArray p,int index) {
        return new FootTraceValidator.Sample(p.optDouble(0),p.optDouble(1),p.optDouble(2,-1),p.optLong(3),p.optDouble(4,-1),index);
    }
}
