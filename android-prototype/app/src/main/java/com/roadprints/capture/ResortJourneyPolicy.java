package com.roadprints.capture;
import org.json.*;
/** Requires every retained fix to support the same resort; never hides an approach. */
final class ResortJourneyPolicy {
    static boolean internal(DestinationCatalog catalog,JSONArray samples) {
        if(samples==null||samples.length()<2)return false;
        String id="";
        for(int i=0;i<samples.length();i++) {
            JSONArray p=samples.optJSONArray(i);if(p==null)return false;
            ContextMap.Evidence e=catalog.evidence(p.optDouble(1),p.optDouble(0),(float)p.optDouble(2));
            if(!DestinationCatalog.suppresses(e)||(!id.isEmpty()&&!id.equals(e.featureId)))return false;
            id=e.featureId;
        }
        return true;
    }
}
