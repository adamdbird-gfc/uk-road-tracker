package com.roadprints.capture;
import org.json.*;
/** A supported site plus no confirmed outside fix; boundary uncertainty is not a departure. */
final class ResortJourneyPolicy {
    static boolean internal(DestinationCatalog catalog,JSONArray samples) throws JSONException {
        if(samples==null||samples.length()<2)return false;
        ContextMap.Evidence site=null;
        for(int i=0;i<samples.length();i++) {
            JSONArray p=samples.optJSONArray(i);if(p==null)return false;
            ContextMap.Evidence e=catalog.evidence(p.optDouble(1),p.optDouble(0),(float)p.optDouble(2));
            if(DestinationCatalog.suppresses(e)){site=e;break;}
        }
        if(site==null)return false;
        JSONObject stop=new JSONObject().put("site_geometry",site.geometry);
        if(site.holes!=null)stop.put("site_holes",site.holes);
        for(int i=0;i<samples.length();i++) {
            JSONArray p=samples.getJSONArray(i);
            FootTraceValidator.Sample fix=new FootTraceValidator.Sample(p.optDouble(0),p.optDouble(1),p.optDouble(2),p.optLong(3),p.optDouble(4,-1),i);
            if(!WalkingStillnessDetector.usable(fix)||fix.accuracy<=0||fix.accuracy>50||PlaceVisitEvidence.outside(stop,fix))return false;
        }
        return true;
    }
}
