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
        JSONArray filtered=new JSONArray(),uncorroborated=new JSONArray();
        java.util.List<Integer> originalIndices=new java.util.ArrayList<>();
        for(int i=0;i<points.length();i++){
            if(unsupportedInteriorFix(samples,i))uncorroborated.put(i);
            else {filtered.put(points.get(i));originalIndices.add(i);}
        }
        JSONArray retained=new JSONArray(), omitted=new JSONArray();
        retained.put(filtered.get(0));
        for(int i=1;i<filtered.length()-1;i++) {
            JSONObject before=retained.getJSONObject(retained.length()-1), point=filtered.getJSONObject(i), after=filtered.getJSONObject(i+1);
            if (redundant(before,point,after)) omitted.put(originalIndices.get(i));
            else retained.put(point);
        }
        retained.put(filtered.get(filtered.length()-1));
        out.sections.add(retained); out.changed=omitted.length()>0||uncorroborated.length()>0;
        out.details.put("version",2).put("evidence_status","android_capture_available")
                .put("uncorroborated_fix_indices",uncorroborated)
                .put("redundant_point_indices",omitted).put("original_geometry_preserved",true);
        return out;
    }

    private static boolean unsupportedInteriorFix(JSONArray samples,int index){
        if(index<=0||index>=samples.length()-1)return false;
        JSONArray p=samples.optJSONArray(index);
        if(p==null||p.optDouble(4,-1)>=0)return false;
        int before=index-1,after=index+1;
        while(before>=0&&!measuredVehicleFix(samples.optJSONArray(before)))before--;
        while(after<samples.length()&&!measuredVehicleFix(samples.optJSONArray(after)))after++;
        if(before<0||after>=samples.length())return false;
        JSONArray a=samples.optJSONArray(before),b=samples.optJSONArray(after);
        long time=p.optLong(3),start=a.optLong(3),end=b.optLong(3);
        double accuracy=p.optDouble(2,Double.NaN),nearbyAccuracy=Math.max(a.optDouble(2),b.optDouble(2));
        if(time<=start||time>=end||end-start>30_000L||!Double.isFinite(accuracy)||accuracy<=nearbyAccuracy*2)return false;
        FootTraceValidator.Sample fix=timed(p),from=timed(a),to=timed(b);
        double seconds=(end-start)/1000.0;
        double plausibleDistance=Math.max(15,Math.max(from.speed,to.speed)*1.5)*seconds
                +from.accuracy+to.accuracy;
        return FootTraceValidator.metres(from,to)<=plausibleDistance
                &&FootTraceValidator.segmentGap(fix,from,to)>accuracy+nearbyAccuracy;
    }
    private static boolean measuredVehicleFix(JSONArray p){
        return p!=null&&p.optDouble(2,-1)>0&&p.optDouble(2,100)<=25
                &&Double.isFinite(p.optDouble(4,Double.NaN))&&p.optDouble(4,-1)>=3;
    }
    private static FootTraceValidator.Sample timed(JSONArray p){
        return new FootTraceValidator.Sample(p.optDouble(0),p.optDouble(1),p.optDouble(2),p.optLong(3),p.optDouble(4,-1),0);
    }
    /** Keep all prepared captured-road observations across bounded requests. */
    static java.util.List<JSONArray> requests(JSONArray points,int limit)throws Exception{
        if(limit<2)throw new IllegalArgumentException("At least two points per request");
        java.util.List<JSONArray> out=new java.util.ArrayList<>();
        for(int start=0;start<points.length()-1;start+=limit-1){
            JSONArray section=new JSONArray();
            for(int i=start;i<Math.min(points.length(),start+limit);i++)section.put(points.get(i));
            out.add(section);
            if(start+limit>=points.length())break;
        }
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

    static boolean supportedPartial(JSONObject result, JSONArray points) throws Exception {
        if (!TimelineRoadPreparation.importPartial(result, points)) return false;
        double recorded=0;
        for(int i=1;i<points.length();i++) recorded+=TimelineRoadPreparation.distance(points.getJSONObject(i-1),points.getJSONObject(i));
        // Verified, separate router sections may count; an inflated or
        // unverified aggregate must never be admitted by percentage alone.
        return result.optDouble("matched_distance_m",Double.POSITIVE_INFINITY)
                <=Math.max(recorded*1.2,recorded+300);
    }
}
