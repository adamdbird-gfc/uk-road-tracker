package com.roadprints.capture;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

/** One selected overlay; drawing and hit testing share the same screen clusters. */
final class CollectionMapClusters {
    interface Projection { float x(double longitude); float y(double latitude); }
    static final class Cluster {
        final List<JSONObject> venues=new ArrayList<>();
        float sumX,sumY;
        int visited;
        float x(){return sumX/venues.size();}
        float y(){return sumY/venues.size();}
    }
    static List<Cluster> build(JSONArray venues,Set<String> visited,Projection projection,
                               float width,float height,float cell) {
        if(!Float.isFinite(cell)||cell<=0)throw new IllegalArgumentException("Invalid cluster cell");
        Map<Long,Cluster> clusters=new LinkedHashMap<>();
        if(venues==null)return new ArrayList<>();
        for(int i=0;i<venues.length();i++) {
            JSONObject venue=venues.optJSONObject(i);if(venue==null)continue;
            double lat=venue.optDouble("lat",Double.NaN),lng=venue.optDouble("lng",Double.NaN);
            if(!Double.isFinite(lat)||!Double.isFinite(lng)||Math.abs(lat)>90||Math.abs(lng)>180)continue;
            float x=projection.x(lng),y=projection.y(lat);
            if(!Float.isFinite(x)||!Float.isFinite(y)||x<-cell||x>width+cell||y<-cell||y>height+cell)continue;
            int column=(int)Math.floor(x/cell),row=(int)Math.floor(y/cell);
            long key=(((long)column)<<32)^(row&0xffffffffL);
            Cluster cluster=clusters.get(key);
            if(cluster==null){cluster=new Cluster();clusters.put(key,cluster);}
            cluster.venues.add(venue);cluster.sumX+=x;cluster.sumY+=y;
            if(visited!=null&&visited.contains(venue.optString("id")))cluster.visited++;
        }
        return new ArrayList<>(clusters.values());
    }
    static Cluster hit(List<Cluster> clusters,float x,float y,float radius) {
        double nearest=radius*radius;Cluster result=null;
        for(Cluster cluster:clusters) {
            double dx=cluster.x()-x,dy=cluster.y()-y,distance=dx*dx+dy*dy;
            if(distance<=nearest){nearest=distance;result=cluster;}
        }
        return result;
    }
}
