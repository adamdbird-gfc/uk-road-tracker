package com.roadprints.capture;
import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.LinkedHashMap;

/** Two bounded, derived road snapshots; never stores an Activity or raw journey. */
final class RoadDetailCache {
    private static final LinkedHashMap<String,RoadDetailModel> models=new LinkedHashMap<>(4,.75f,true);
    static String key(Context context,RoadDetailSelection s,boolean cumulative,long revision) {
        int boundary=0;
        if(RoadDetailSelection.LOCAL.equals(s.kind)) {JSONObject b=LocalRoadSettlementMatcher.boundary(s.settlementCode);boundary=b==null?0:b.toString().hashCode();}
        return s.kind+"|"+s.label+"|"+s.region+"|"+s.settlementCode+"|"+cumulative+"|"+revision+"|"+boundary;
    }
    static synchronized RoadDetailModel get(String key){return models.get(key);}
    static synchronized void put(String key,RoadDetailModel model){
        int points=0;for(JSONArray line:model.covered)points+=line.length();for(JSONArray line:model.remaining)points+=line.length();
        if(points>50000||model.journeys.size()>5000)return;
        models.put(key,model);while(models.size()>2)models.remove(models.keySet().iterator().next());
    }
    static synchronized void clear(){models.clear();}
}
