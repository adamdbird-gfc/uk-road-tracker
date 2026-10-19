package com.roadprints.capture;
import android.content.Context;
import org.json.JSONArray;
import java.util.LinkedHashMap;

/** Two bounded derived snapshots; key lookup never performs disk or network I/O. */
final class RoadDetailCache {
    private static final class Cached {
        final RoadDetailModel model;final long expires;
        Cached(String key,RoadDetailModel model){this.model=model;expires=key.startsWith(RoadDetailSelection.LOCAL+"|")?android.os.SystemClock.elapsedRealtime()+300000:Long.MAX_VALUE;}
    }
    private static final LinkedHashMap<String,Cached> models=new LinkedHashMap<>(4,.75f,true);
    static String key(Context context,RoadDetailSelection s,boolean cumulative,long revision) {
        // Bundled numbered-road references are immutable within a build. Public
        // town boundaries have no revision token, so local snapshots expire after five minutes.
        return (RoadDetailSelection.NCN.equals(s.kind)?"ncn:"+s.ncnMode+":"+NcnProgressStore.revision(context):s.kind)+"|"+s.label+"|"+s.region+"|"+s.settlementCode+"|"+cumulative+"|"+revision;
    }
    static synchronized RoadDetailModel get(String key){
        Cached saved=models.get(key);if(saved==null)return null;
        if(android.os.SystemClock.elapsedRealtime()>=saved.expires){models.remove(key);return null;}return saved.model;
    }
    static synchronized void put(String key,RoadDetailModel model){
        int points=0;for(JSONArray line:model.covered)points+=line.length();for(JSONArray line:model.remaining)points+=line.length();
        if(points>50000||model.journeys.size()>5000)return;
        models.put(key,new Cached(key,model));while(models.size()>2)models.remove(models.keySet().iterator().next());
    }
    static synchronized void clear(){models.clear();}
}
