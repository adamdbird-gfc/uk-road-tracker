package com.roadprints.capture;

import org.json.JSONArray;
import org.json.JSONObject;
import java.time.Instant;
import java.util.List;

/** Local place evidence: a supported stop is not a confirmed venue visit. */
final class PlaceBoundaryEvidence {
    static JSONObject arrival(String journeyId, FootTraceValidator.Sample anchor, long confirmedAt,
                              ContextMap.Evidence map, String stationCode, String reason) throws org.json.JSONException {
        if(!WalkingStillnessDetector.usable(anchor)||confirmedAt<anchor.time)return null;
        boolean station=stationCode!=null&&!stationCode.isEmpty();
        boolean mapped=map!=null&&map.destination;
        String id=journeyId+":arrival";
        return new JSONObject().put("id",id).put("journey_id",journeyId).put("role","arrival")
                .put("place_id",station?"rail:"+stationCode:mapped&&!map.featureId.isEmpty()?map.featureId:id)
                .put("name",station?"Station "+stationCode:mapped?map.name:"Estimated stop")
                .put("kind",station?"station":mapped?map.kind:"estimated")
                .put("latitude",anchor.lat).put("longitude",anchor.lon).put("accuracy_m",anchor.accuracy)
                .put("arrived_at",Instant.ofEpochMilli(anchor.time).toString())
                .put("confirmed_at",Instant.ofEpochMilli(confirmedAt).toString())
                .put("dwell_seconds",(confirmedAt-anchor.time)/1000)
                .put("classification",mapped||station?"supported":"estimated")
                .put("confidence",mapped||station?"medium":"low").put("visit_status","stopped")
                .put("source",station?"offline_station_catalog":mapped?"map_footprint_and_dwell":"gps_dwell")
                .put("reason",reason==null?"arrival_dwell":reason).put("boundary_at",Instant.ofEpochMilli(anchor.time).toString());
    }

    /** Locate departure in new observations; never prepend the old arrival fix. */
    static int departureIndex(JSONObject stop,List<FootTraceValidator.Sample> samples) {
        if(stop==null||"rejected".equals(stop.optString("classification"))||samples.isEmpty())return -1;
        try {if(Instant.parse(stop.getString("arrived_at")).toEpochMilli()>samples.get(0).time)return -1;}
        catch(Exception invalid){return -1;}
        double lat=stop.optDouble("latitude"),lon=stop.optDouble("longitude"),accuracy=stop.optDouble("accuracy_m");
        if(!Double.isFinite(lat)||!Double.isFinite(lon)||Math.abs(lat)>90||Math.abs(lon)>180
                ||!Double.isFinite(accuracy)||accuracy<=0||accuracy>50)return -1;
        int lastInside=-1;
        for(int i=0;i<samples.size();i++) {
            FootTraceValidator.Sample p=samples.get(i);
            if(!WalkingStillnessDetector.usable(p))continue;
            boolean inside=RailStationCatalog.metres(lat,lon,p.lat,p.lon)<=35+accuracy+p.accuracy;
            if(inside) {if(lastInside<0||p.speed>=0&&p.speed<=.8)lastInside=i;}
            else if(lastInside>=0)return lastInside;
            else return -1;
        }
        return -1;
    }
    static JSONObject departure(JSONObject stop,FootTraceValidator.Sample point) throws org.json.JSONException {
        JSONObject result=new JSONObject(stop.toString());
        result.put("role","departure").put("departed_at",Instant.ofEpochMilli(point.time).toString())
                .put("boundary_at",Instant.ofEpochMilli(point.time).toString());
        return result;
    }
    static void attach(JSONObject journey,JSONObject departure,JSONObject arrival) throws org.json.JSONException {
        JSONArray stops=new JSONArray(),places=new JSONArray();
        for(JSONObject stop:new JSONObject[]{departure,arrival}) {
            if(stop==null)continue;
            stops.put(new JSONObject(stop.toString()));
            places.put(new JSONObject().put("id",stop.optString("place_id"))
                    .put("name",stop.optString("name")).put("kind",stop.optString("kind"))
                    .put("latitude",stop.optDouble("latitude")).put("longitude",stop.optDouble("longitude"))
                    .put("classification",stop.optString("classification")).put("source",stop.optString("source")));
        }
        journey.put("stops",stops).put("places",places).put("place_evidence_version",1);
    }
    static JSONObject review(JSONObject stop,String name,boolean confirmed,long now) throws org.json.JSONException {
        JSONObject result=new JSONObject(stop.toString());
        if(!result.has("original_evidence"))result.put("original_evidence",new JSONObject(stop.toString()));
        String label=name==null?"":name.trim();
        if(label.length()>120)throw new IllegalArgumentException("Place name is too long");
        if(confirmed&&label.isEmpty())throw new IllegalArgumentException("Enter a place name");
        result.put("classification",confirmed?"confirmed":"rejected")
                .put("confidence",confirmed?"high":"low").put("visit_status",confirmed?"confirmed":"rejected")
                .put("reviewed_at_ms",now).put("review_source","user_confirmation");
        if(confirmed)result.put("name",label);
        return result;
    }
    static void preserveReviews(JSONObject incoming,JSONObject stored) throws org.json.JSONException {
        JSONArray old=stored==null?null:stored.optJSONArray("stops"),stops=incoming.optJSONArray("stops");
        if(old==null||stops==null)return;
        for(int i=0;i<old.length();i++) {
            JSONObject previous=old.optJSONObject(i);if(previous==null||!previous.has("reviewed_at_ms"))continue;
            for(int j=0;j<stops.length();j++) {
                JSONObject next=stops.optJSONObject(j);
                if(next!=null&&previous.optString("id").equals(next.optString("id"))
                        &&previous.optLong("reviewed_at_ms")>next.optLong("reviewed_at_ms"))
                    stops.put(j,new JSONObject(previous.toString()));
            }
        }
        rebuildPlaces(incoming);
    }
    static void rebuildPlaces(JSONObject journey) throws org.json.JSONException {
        // Only this version's generated place list belongs to this model.
        if(journey.optInt("place_evidence_version")!=1)return;
        JSONArray stops=journey.optJSONArray("stops"),places=new JSONArray();
        if(stops!=null)for(int i=0;i<stops.length();i++) {
            JSONObject stop=stops.optJSONObject(i);if(stop==null||"rejected".equals(stop.optString("classification")))continue;
            places.put(new JSONObject().put("id",stop.optString("place_id")).put("name",stop.optString("name"))
                    .put("kind",stop.optString("kind")).put("latitude",stop.optDouble("latitude"))
                    .put("longitude",stop.optDouble("longitude")).put("classification",stop.optString("classification"))
                    .put("source",stop.optString("source")));
        }
        journey.put("places",places);
    }
}
