package com.roadprints.capture;
import java.util.*;
import org.json.*;
final class WalkingTraceSimplifier {
    static JSONArray simplify(JSONArray points) throws Exception {
        if(points.length()<3)return points;
        boolean[] keep=new boolean[points.length()];keep[0]=true;keep[points.length()-1]=true;
        ArrayDeque<int[]> ranges=new ArrayDeque<>();ranges.push(new int[]{0,points.length()-1});
        while(!ranges.isEmpty()) {
            int[] range=ranges.pop();int a=range[0],z=range[1];if(z-a<2)continue;
            FootTraceValidator.Sample first=sample(points,a),last=sample(points,z);
            double maximum=3;int split=-1;
            for(int i=a+1;i<z;i++) {
                double gap=FootTraceValidator.segmentGap(sample(points,i),first,last);
                if(gap>maximum){maximum=gap;split=i;}
            }
            if(split<0&&(z-a>8||FootTraceValidator.metres(first,last)>60))split=(a+z)/2;
            if(split>=0){keep[split]=true;ranges.push(new int[]{a,split});ranges.push(new int[]{split,z});}
        }
        int count=0;for(boolean retained:keep)if(retained)count++;
        if(count<3)keep[points.length()/2]=true; // The walking matcher needs at least three fixes.
        JSONArray result=new JSONArray();for(int i=0;i<keep.length;i++)if(keep[i])result.put(points.get(i));return result;
    }
    private static FootTraceValidator.Sample sample(JSONArray points,int i) throws Exception {
        JSONObject p=points.getJSONObject(i);return new FootTraceValidator.Sample(p.getDouble("lng"),p.getDouble("lat"),0,0,-1,i);
    }
}
