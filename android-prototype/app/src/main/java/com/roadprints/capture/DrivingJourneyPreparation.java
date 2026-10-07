package com.roadprints.capture;

import org.json.JSONArray;
import org.json.JSONObject;

/** Prepare captured drives from aligned evidence; never edit the saved trace. */
final class DrivingJourneyPreparation {
    static boolean aligned(JSONObject journey, JSONArray points) {
        JSONArray samples=journey.optJSONArray("capture_route_samples");
        if(samples==null || samples.length()!=points.length()) return false;
        for(int i=0;i<points.length();i++) {
            JSONArray sample=samples.optJSONArray(i); JSONObject point=points.optJSONObject(i);
            if(sample==null || point==null || !Double.isFinite(sample.optDouble(0)) || !Double.isFinite(sample.optDouble(1))
                    || Math.abs(sample.optDouble(0)-point.optDouble("lng"))>1e-7
                    || Math.abs(sample.optDouble(1)-point.optDouble("lat"))>1e-7) return false;
        }
        return true;
    }
    static TimelineRoadPreparation.Prepared prepare(JSONObject journey, JSONArray points) throws Exception {
        TimelineRoadPreparation.Prepared out = new TimelineRoadPreparation.Prepared();
        JSONArray samples = journey.optJSONArray("capture_route_samples");
        if (samples == null || samples.length() != points.length())
            throw new IllegalStateException("Driving capture timing does not align with its route. The original recording is preserved.");
        long previous = 0;
        for (int i=0;i<points.length();i++) {
            JSONArray sample=samples.getJSONArray(i); JSONObject point=points.getJSONObject(i);
            long time=sample.optLong(3,0);
            if (time<=previous || Math.abs(sample.optDouble(0)-point.getDouble("lng"))>1e-7
                    || Math.abs(sample.optDouble(1)-point.getDouble("lat"))>1e-7)
                throw new IllegalStateException("Driving capture timing does not align with its route. The original recording is preserved.");
            previous=time;
        }
        JSONArray retained=new JSONArray(), omitted=new JSONArray();
        retained.put(points.get(0));
        for(int i=1;i<points.length()-1;i++) {
            JSONObject before=retained.getJSONObject(retained.length()-1), point=points.getJSONObject(i), after=points.getJSONObject(i+1);
            if (redundant(before,point,after)) omitted.put(i);
            else retained.put(point);
        }
        retained.put(points.get(points.length()-1));
        out.sections.add(retained); out.changed=omitted.length()>0;
        out.details.put("version",1).put("evidence_status","android_capture_available")
                .put("redundant_point_indices",omitted).put("original_geometry_preserved",true);
        return out;
    }

    static boolean redundant(JSONObject before, JSONObject point, JSONObject after) {
        return TimelineRoadPreparation.distance(before,point)<=15
                && FootTraceValidator.segmentGap(sample(point),sample(before),sample(after))<=5;
    }
    private static FootTraceValidator.Sample sample(JSONObject p) {
        return new FootTraceValidator.Sample(p.optDouble("lng"),p.optDouble("lat"),0,0,0,0);
    }

    /** Retry only tidy-discarded interior fixes that lie within nearby matched evidence. */
    static JSONArray retryPoints(JSONObject result, JSONArray points) throws Exception {
        return retryPoints(result,points,null);
    }
    static JSONArray retryPoints(JSONObject result, JSONArray points, JSONObject journey) throws Exception {
        JSONArray matched=result.optJSONArray("matched_point_indices"), omitted=result.optJSONArray("unmatched_point_indices");
        JSONArray failures=result.optJSONArray("failed_sections");
        int count=points.length();
        if(matched==null || omitted==null || omitted.length()==0 || (failures!=null && failures.length()>0)
                || result.optInt("input_points",-1)!=count || result.optInt("matched_tracepoints",-1)!=matched.length()
                || matched.length()+omitted.length()!=count) return null;
        boolean[] kept=new boolean[count], removed=new boolean[count];
        for(int i=0;i<matched.length();i++) {
            int index=matched.optInt(i,-1); if(index<0 || index>=count || kept[index]) return null; kept[index]=true;
        }
        for(int i=0;i<omitted.length();i++) {
            int index=omitted.optInt(i,-1);
            if(index<=0 || index>=count-1 || kept[index] || removed[index]) return null;
            removed[index]=true;
        }
        if(!kept[0] || !kept[count-1]) return null;
        for(int i=1;i<count-1;i++) if(removed[i]) {
            int before=i-1, after=i+1;
            while(before>0 && !kept[before]) before--;
            while(after<count-1 && !kept[after]) after++;
            JSONObject p=points.getJSONObject(i), a=points.getJSONObject(before), b=points.getJSONObject(after);
            if(Math.min(TimelineRoadPreparation.distance(a,p),TimelineRoadPreparation.distance(b,p))>20
                    || FootTraceValidator.segmentGap(sample(p),sample(a),sample(b))>uncertainty(journey,p)) return null;
        }
        JSONArray retry=new JSONArray();
        for(int i=0;i<count;i++) if(kept[i]) retry.put(points.get(i));
        return retry;
    }

    private static double uncertainty(JSONObject journey,JSONObject point) {
        JSONArray samples=journey==null?null:journey.optJSONArray("capture_route_samples");
        if(samples!=null) for(int i=0;i<samples.length();i++) {
            JSONArray sample=samples.optJSONArray(i);
            if(sample!=null && Math.abs(sample.optDouble(0)-point.optDouble("lng"))<1e-7
                    && Math.abs(sample.optDouble(1)-point.optDouble("lat"))<1e-7)
                return Math.max(5,Math.min(10,sample.optDouble(2,0)*.5));
        }
        return 5;
    }
    static void validateDistance(JSONObject result, JSONArray points) throws Exception {
        double recorded=0;
        for(int i=1;i<points.length();i++) recorded+=TimelineRoadPreparation.distance(points.getJSONObject(i-1),points.getJSONObject(i));
        double matched=result.optDouble("matched_distance_m",-1);
        if(!Double.isFinite(matched) || matched<=0 || matched>Math.max(recorded*1.4,recorded+300))
            throw new IllegalStateException("Matched drive exceeds its GPS distance evidence. The original recording is preserved.");
    }
}
