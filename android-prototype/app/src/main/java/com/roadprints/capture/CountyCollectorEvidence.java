package com.roadprints.capture;

import java.io.IOException;
import java.util.*;
import org.json.*;

/** A fresh scan of currently unlocked roads; no sticky county credit after an edit or deletion. */
final class CountyCollectorEvidence {
    private final HistoricCountyCatalog catalogue;
    final Set<String> completed=new HashSet<>();
    CountyCollectorEvidence(HistoricCountyCatalog catalogue){this.catalogue=catalogue;}
    void add(JSONObject journey) throws IOException {
        if(!"complete".equals(journey.optString("processing_status")))return;
        String mode=journey.optString("mode","").toLowerCase(Locale.ROOT);
        if(!Arrays.asList("driving","bus","walking","running","pedestrian","cycling","bicycle").contains(mode))return;
        JSONObject result=journey.optJSONObject("processing_result");if(result==null)return;
        for(String field:new String[]{"road_geojson","motorway_geojson","a_road_geojson"}) {
            JSONObject collection=result.optJSONObject(field);JSONArray features=collection==null?null:collection.optJSONArray("features");if(features==null)continue;
            for(int i=0;i<features.length();i++) {
                JSONObject feature=features.optJSONObject(i);if(feature==null||JourneyCorrectionUtils.excludesRoadFeature(journey,feature))continue;
                JSONObject properties=feature.optJSONObject("properties");if(properties==null)continue;
                String highway=properties.optString("highway").toLowerCase(Locale.ROOT);
                if(highway.endsWith("_link")||Arrays.asList("service","path","footway","cycleway","steps","track").contains(highway))continue;
                if(properties.optString("road_ref",properties.optString("ref","")).trim().isEmpty()
                        &&properties.optString("name",properties.optString("road_name","")).trim().isEmpty())continue;
                for(JSONArray line:CoreAchievementEvidence.lines(feature.optJSONObject("geometry"))) {
                    for(int p=1;p<line.length();p++) {
                        JSONArray a=line.optJSONArray(p-1),b=line.optJSONArray(p);if(a==null||b==null||a.length()<2||b.length()<2)continue;
                        double ax=a.optDouble(0,Double.NaN),ay=a.optDouble(1,Double.NaN),bx=b.optDouble(0,Double.NaN),by=b.optDouble(1,Double.NaN);
                        if(!Double.isFinite(ax)||!Double.isFinite(ay)||!Double.isFinite(bx)||!Double.isFinite(by)
                                ||Math.abs(ax)>180||Math.abs(bx)>180||Math.abs(ay)>90||Math.abs(by)>90)continue;
                        for(HistoricCountyCatalog.County county:catalogue.counties) {
                            if(completed.contains(county.code)||!HistoricCountyGeometry.overlaps(county.bbox,ax,ay,bx,by))continue;
                            if(catalogue.geometry(county).intersects(ax,ay,bx,by))completed.add(county.code);
                        }
                    }
                }
            }
        }
    }
    double percent(){return 100.0*completed.size()/HistoricCountyCatalog.COUNTY_COUNT;}
    List<String> checklist() {
        List<String> rows=new ArrayList<>();
        for(HistoricCountyCatalog.County c:catalogue.counties)rows.add((completed.contains(c.code)?"✓ ":"○ ")+c.name+" · "+c.nation);
        return rows;
    }
}
