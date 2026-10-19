package com.roadprints.capture;
import org.json.JSONArray;
import java.util.*;
/** Join only identical endpoint evidence; branch nodes and gaps remain separate. */
final class NcnGeometry {
    static boolean overlaps(JSONArray first,JSONArray selected){
        if(selected==null||selected.length()!=2)return false;JSONArray a=selected.optJSONArray(0),z=selected.optJSONArray(1);if(a==null||z==null)return false;
        double sy=111195,sx=Math.cos(Math.toRadians(a.optDouble(1)))*sy,dx=(z.optDouble(0)-a.optDouble(0))*sx,dy=(z.optDouble(1)-a.optDouble(1))*sy,length=Math.hypot(dx,dy);if(length<=.01)return false;dx/=length;dy/=length;
        for(int i=1;i<first.length();i++){JSONArray p=first.optJSONArray(i-1),q=first.optJSONArray(i);if(p==null||q==null)continue;double px=(p.optDouble(0)-a.optDouble(0))*sx,py=(p.optDouble(1)-a.optDouble(1))*sy,qx=(q.optDouble(0)-a.optDouble(0))*sx,qy=(q.optDouble(1)-a.optDouble(1))*sy;
            if(Math.abs(px*dy-py*dx)>.05||Math.abs(qx*dy-qy*dx)>.05)continue;double low=px*dx+py*dy,high=qx*dx+qy*dy;if(Math.min(length,Math.max(low,high))-Math.max(0,Math.min(low,high))>.01)return true;
        }return false;
    }
    static String key(JSONArray point){return point.toString();}
    static List<JSONArray> join(List<JSONArray> lines)throws Exception{
        Map<String,List<Integer>> ends=new HashMap<>();
        for(int i=0;i<lines.size();i++){JSONArray line=lines.get(i);if(line.length()<2)continue;ends.computeIfAbsent(key(line.getJSONArray(0)),k->new ArrayList<>()).add(i);ends.computeIfAbsent(key(line.getJSONArray(line.length()-1)),k->new ArrayList<>()).add(i);}
        boolean[] used=new boolean[lines.size()];List<JSONArray> result=new ArrayList<>();
        // Begin chains at terminals/branches, then consume isolated closed rings.
        for(int pass=0;pass<2;pass++)for(int i=0;i<lines.size();i++){
            if(used[i]||lines.get(i).length()<2)continue;JSONArray seed=lines.get(i);String first=key(seed.getJSONArray(0)),last=key(seed.getJSONArray(seed.length()-1));
            boolean reverse=ends.get(first).size()==2&&ends.get(last).size()!=2;if(pass==0&&ends.get(first).size()==2&&ends.get(last).size()==2)continue;
            JSONArray output=new JSONArray();int current=i;String at=reverse?last:first;
            while(current>=0&&!used[current]){JSONArray line=lines.get(current);used[current]=true;boolean backwards=!key(line.getJSONArray(0)).equals(at);
                for(int j=output.length()==0?0:1;j<line.length();j++)output.put(line.getJSONArray(backwards?line.length()-1-j:j));at=key(output.getJSONArray(output.length()-1));List<Integer> next=ends.get(at);current=-1;if(next!=null&&next.size()==2)for(int n:next)if(!used[n])current=n;
            }result.add(output);
        }return result;
    }
}
