package com.roadprints.capture;

import android.content.Context;
import org.json.*;
import java.nio.charset.StandardCharsets;

/** Small pinned public resort reference. Independent of optional UK online context. */
final class DestinationCatalog {
    private final JSONArray features;
    DestinationCatalog(Context app) {
        JSONArray loaded=null;
        try(java.io.InputStream in=app.getAssets().open("destinations/disneyland-paris-v1.json")) {
            java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();
            byte[] buffer=new byte[4096];int n;
            while((n=in.read(buffer))!=-1)out.write(buffer,0,n);
            loaded=new JSONObject(new String(out.toByteArray(),StandardCharsets.UTF_8)).getJSONArray("elements");
        }catch(Exception unavailable){ /* No guessed boundary if the reference is unavailable. */ }
        features=loaded;
    }
    ContextMap.Evidence evidence(double lat,double lon,float accuracy) {
        if(lat<48.85||lat>48.89||lon<2.75||lon>2.81)return new ContextMap.Evidence();
        return ContextMap.at(features,lat,lon,accuracy);
    }
    static boolean suppresses(ContextMap.Evidence evidence) {
        return evidence!=null&&evidence.destination&&"theme_park".equals(evidence.kind);
    }
}
