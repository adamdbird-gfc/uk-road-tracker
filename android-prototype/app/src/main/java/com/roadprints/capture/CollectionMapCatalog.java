package com.roadprints.capture;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;

/** Pub pins cannot consume the reference-only candidate catalogue. */
final class CollectionMapCatalog {
    private CollectionMapCatalog() {}
    static JSONArray approvedVenues(JSONObject catalogue) {
        if(catalogue==null||!"wetherspoons".equals(catalogue.optString("collection_id"))
                ||!"UK".equals(catalogue.optString("scope"))
                ||!catalogue.optBoolean("ready_for_app",false)
                ||!"approved".equals(catalogue.optString("release_status")))return new JSONArray();
        JSONArray rows=catalogue.optJSONArray("venues"),result=new JSONArray();
        if(rows==null)return result;
        for(int i=0;i<rows.length();i++) {
            JSONObject row=rows.optJSONObject(i);
            if(row==null||!"active".equals(row.optString("status"))||row.optString("id").isEmpty())continue;
            double lat=row.optDouble("lat",Double.NaN),lng=row.optDouble("lon",Double.NaN);
            if(!Double.isFinite(lat)||!Double.isFinite(lng)||Math.abs(lat)>90||Math.abs(lng)>180)continue;
            try {JSONObject pin=new JSONObject();pin.put("id",row.getString("id"));
                pin.put("name",row.optString("name"));pin.put("lat",lat);pin.put("lng",lng);result.put(pin);}
            catch(Exception error){throw new IllegalStateException("Invalid approved venue",error);}
        }
        return result;
    }
    static JSONArray spoons(Context context) {
        // Slice 1 remains audit-only. No candidate asset is packaged or activated.
        return approvedVenues(null);
    }
}
