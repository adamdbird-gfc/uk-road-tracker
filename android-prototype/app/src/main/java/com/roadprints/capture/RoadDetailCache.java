package com.roadprints.capture;
import android.content.Context;
import org.json.JSONArray;
import java.util.LinkedHashMap;

/** Eight snapshots sharing a bounded geometry budget; key lookup never performs disk or network I/O. */
final class RoadDetailCache {
    private static final class Cached {
        final RoadDetailModel model;final long expires;final int points;
        Cached(String key,RoadDetailModel model){this.model=model;points=count(model);expires=key.startsWith(RoadDetailSelection.LOCAL+"|")?android.os.SystemClock.elapsedRealtime()+300000:Long.MAX_VALUE;}
    }
    private static final LinkedHashMap<String,Cached> models=new LinkedHashMap<>(4,.75f,true);
    private static int retainedPoints,retainedJourneys;
    private static int count(RoadDetailModel model){int points=0;for(JSONArray line:model.covered)points+=line.length();for(JSONArray line:model.remaining)points+=line.length();return points;}
    static String key(Context context,RoadDetailSelection s,boolean cumulative,long revision) {
        // Bundled numbered-road references are immutable within a build. Public
        // town boundaries have no revision token, so local snapshots expire after five minutes.
        return (RoadDetailSelection.NCN.equals(s.kind)?"ncn:"+s.ncnMode+":"+NcnProgressStore.revision(context):s.kind)+"|"+s.label+"|"+s.region+"|"+s.settlementCode+"|"+cumulative+"|"+revision;
    }
    static synchronized RoadDetailModel get(String key){
        Cached saved=models.get(key);if(saved==null)return null;
        if(android.os.SystemClock.elapsedRealtime()>=saved.expires){Cached expired=models.remove(key);retainedPoints-=expired.points;retainedJourneys-=expired.model.journeys.size();return null;}return saved.model;
    }
    static synchronized void put(String key,RoadDetailModel model){
        int points=0;for(JSONArray line:model.covered)points+=line.length();for(JSONArray line:model.remaining)points+=line.length();
        if(points>50000||model.journeys.size()>5000)return;
        Cached previous=models.put(key,new Cached(key,model));retainedPoints+=points-(previous==null?0:previous.points);retainedJourneys+=model.journeys.size()-(previous==null?0:previous.model.journeys.size());
        while(models.size()>8||retainedPoints>100000||retainedJourneys>10000){Cached expired=models.remove(models.keySet().iterator().next());retainedPoints-=expired.points;retainedJourneys-=expired.model.journeys.size();}
    }
    static synchronized void clear(){models.clear();retainedPoints=0;retainedJourneys=0;}
}
