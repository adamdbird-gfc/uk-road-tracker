package com.roadprints.capture;

import android.content.Context;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import org.json.JSONArray;
import org.json.JSONObject;

/** Only individually reviewed map venues; never a visit evidence provider. */
final class CollectionMapCatalog {
    static final int MAX_CATALOGUE_BYTES = 2 * 1024 * 1024;
    private static JSONObject bundled;
    private CollectionMapCatalog() {}
    static JSONArray approvedVenues(JSONObject catalogue) {
        if(catalogue==null||!"wetherspoons".equals(catalogue.optString("collection_id"))
                ||!"UK".equals(catalogue.optString("scope"))
                ||!catalogue.optBoolean("ready_for_app",false)
                ||!"approved".equals(catalogue.optString("release_status"))
                ||!"map_only_partial_beta".equals(catalogue.optString("approval_scope"))
                ||catalogue.optBoolean("visit_enabled",true)
                ||!catalogue.isNull("completion_target"))return new JSONArray();
        JSONArray rows=catalogue.optJSONArray("venues"),result=new JSONArray();
        if(rows==null)return result;
        HashSet<String> ids=new HashSet<>();
        for(int i=0;i<rows.length();i++) {
            JSONObject row=rows.optJSONObject(i);
            if(row==null||!"active".equals(row.optString("status"))||row.optString("id").isEmpty()
                    ||!"reviewed_current".equals(row.optString("operating_status"))
                    ||row.optBoolean("visit_enabled",true)||row.optString("map_reviewed_at").isEmpty())continue;
            if(!ids.add(row.optString("id")))return new JSONArray();
            double lat=row.optDouble("lat",Double.NaN),lng=row.optDouble("lon",Double.NaN);
            if(!Double.isFinite(lat)||!Double.isFinite(lng)||Math.abs(lat)>90||Math.abs(lng)>180)continue;
            try {JSONObject pin=new JSONObject();pin.put("id",row.getString("id"));
                pin.put("name",row.optString("name"));pin.put("town",row.optString("town"));
                pin.put("lat",lat);pin.put("lng",lng);
                pin.put("location_label",row.optString("location_label","Reviewed map location"));
                pin.put("attribution",row.optString("attribution","© OpenStreetMap contributors · ODbL 1.0"));
                result.put(pin);}
            catch(Exception error){throw new IllegalStateException("Invalid approved venue",error);}
        }
        return result;
    }
    static JSONObject readCatalogue(InputStream input) throws Exception {
        try(ByteArrayOutputStream output=new ByteArrayOutputStream()) {
            byte[] buffer=new byte[4096];int n;
            while((n=input.read(buffer))!=-1) {
                if(output.size()+n>MAX_CATALOGUE_BYTES)return new JSONObject();
                output.write(buffer,0,n);
            }
            return new JSONObject(new String(output.toByteArray(),StandardCharsets.UTF_8));
        }
    }
    static synchronized JSONArray spoons(Context context) {
        if(bundled==null) {
            try(InputStream input=context.getAssets().open("collections/uk-wetherspoons-map-v1.json")) {
                bundled=readCatalogue(input);
            }catch(Exception unavailable){bundled=new JSONObject();}
        }
        return CollectionCatalogueUpdates.pins(context,bundled);
    }
}



