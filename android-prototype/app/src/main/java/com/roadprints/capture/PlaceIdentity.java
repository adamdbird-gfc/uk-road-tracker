package com.roadprints.capture;
import org.json.*;
/** Display labels, geographic evidence and user confirmation are separate facts. */
final class PlaceIdentity {
    static void apply(JSONObject stop) throws JSONException {
        double lat=stop.optDouble("latitude"),lon=stop.optDouble("longitude");
        boolean positioned=Double.isFinite(lat)&&Double.isFinite(lon)&&Math.abs(lat)<=90&&Math.abs(lon)<=180
                &&stop.optDouble("accuracy_m",1000)>0&&stop.optDouble("accuracy_m",1000)<=100;
        JSONObject original=stop.optJSONObject("original_evidence");
        String id=original==null?stop.optString("place_id"):original.optString("place_id");
        boolean mapped=positioned&&(id.startsWith("osm:")&&PlaceGeometry.present(stop)&&!PlaceGeometry.outside(stop,new FootTraceValidator.Sample(lon,lat,stop.optDouble("accuracy_m"),1,-1,0))||id.startsWith("rail:"));
        boolean reviewed="user_confirmation".equals(stop.optString("review_source"))&&"confirmed".equals(stop.optString("classification"));
        String type=mapped?"recognised":positioned?(reviewed?"user_confirmed":"estimated"):
                (!stop.optString("display_label").isEmpty()?"display_label_only":"unknown");
        JSONObject identity=new JSONObject().put("version",1).put("type",type)
                .put("position_supported",positioned).put("confidence",mapped?"medium":positioned?"low":"unknown")
                .put("basis",mapped?"mapped_geography":positioned?"gps_endpoint":"unsupported");
        if(mapped)identity.put("geographic_id",id);
        if(positioned)identity.put("latitude",lat).put("longitude",lon);
        stop.put("identity",identity);
    }
}
