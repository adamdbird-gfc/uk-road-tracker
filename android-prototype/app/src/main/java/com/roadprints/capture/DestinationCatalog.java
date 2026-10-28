package com.roadprints.capture;

import android.content.Context;
import org.json.*;
import java.nio.charset.StandardCharsets;

/** Small pinned public resort reference. Independent of optional UK online context. */
final class DestinationCatalog {
    private final JSONArray features;
    private double south=90,north=-90,west=180,east=-180;
    DestinationCatalog(Context app) {
        JSONArray loaded=null;
        try(java.io.InputStream in=app.getAssets().open("destinations/disneyland-paris-v1.json")) {
            java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();
            byte[] buffer=new byte[4096];int n;
            while((n=in.read(buffer))!=-1)out.write(buffer,0,n);
            loaded=new JSONObject(new String(out.toByteArray(),StandardCharsets.UTF_8)).getJSONArray("elements");
        }catch(Exception unavailable){ /* No guessed boundary if the reference is unavailable. */ }
        features=loaded;
        if(features!=null)for(int i=0;i<features.length();i++) {
            JSONArray ring=features.optJSONObject(i).optJSONArray("geometry");if(ring==null)continue;
            for(int n=0;n<ring.length();n++){JSONObject p=ring.optJSONObject(n);if(p==null)continue;
                south=Math.min(south,p.optDouble("lat"));north=Math.max(north,p.optDouble("lat"));
                west=Math.min(west,p.optDouble("lon"));east=Math.max(east,p.optDouble("lon"));}
        }
    }
    ContextMap.Evidence evidence(double lat,double lon,float accuracy) {
        if(lat<south||lat>north||lon<west||lon>east)return new ContextMap.Evidence();
        return ContextMap.at(features,lat,lon,accuracy);
    }
    static boolean suppresses(ContextMap.Evidence evidence) {
        return evidence!=null&&evidence.destination&&evidence.site&&DestinationPolicy.complex(evidence.kind);
    }
}
