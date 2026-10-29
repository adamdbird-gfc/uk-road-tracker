package com.roadprints.capture;

import android.content.Context;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;

/** Offline, lazy reference: never allocate the complete UK boundary geometry at once. */
final class HistoricCountyCatalog {
    static final String ASSETS="historic-counties/";
    static final int COUNTY_COUNT=92;
    static final class County {
        final String code,name,nation;
        final double[] bbox;
        final long offset;
        final int length;
        final double interiorLon,interiorLat;
        County(JSONObject row) throws JSONException {
            code=row.getString("code");name=row.getString("name");nation=row.getString("nation");
            JSONArray b=row.getJSONArray("bbox"),p=row.getJSONArray("interior");bbox=new double[]{b.getDouble(0),b.getDouble(1),b.getDouble(2),b.getDouble(3)};
            interiorLon=p.getDouble(0);interiorLat=p.getDouble(1);offset=row.getLong("offset");length=row.getInt("length");
        }
    }
    final List<County> counties=new ArrayList<>();
    final String version;
    private final Context app;
    private final LinkedHashMap<String,HistoricCountyGeometry> cache=new LinkedHashMap<>(16,.75f,true);
    private int cacheBytes;
    HistoricCountyCatalog(Context context) throws IOException {
        app=context.getApplicationContext();
        try(InputStream input=app.getAssets().open(ASSETS+"catalogue.json")) {
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int n;
            while((n=input.read(buffer))!=-1)bytes.write(buffer,0,n);
            JSONObject root=new JSONObject(bytes.toString(StandardCharsets.UTF_8.name()));
            if(root.getInt("format")!=1||root.getInt("coordinate_scale")!=1_000_000)throw new IOException("Unsupported county reference");
            version=root.getString("version");JSONArray rows=root.getJSONArray("counties");Set<String> codes=new HashSet<>();
            for(int i=0;i<rows.length();i++) {
                County county=new County(rows.getJSONObject(i));
                if(!codes.add(county.code)||county.offset<0||county.length<1||county.length>5_000_000)throw new IOException("Invalid county catalogue");
                counties.add(county);
            }
            if(counties.size()!=COUNTY_COUNT)throw new IOException("Incomplete historic county reference");
            counties.sort(Comparator.comparing(c->c.name));
        }catch(JSONException error){throw new IOException("Invalid county reference",error);}
    }
    HistoricCountyGeometry geometry(County county) throws IOException {
        HistoricCountyGeometry result=cache.get(county.code);if(result!=null)return result;
        byte[] compressed=new byte[county.length];
        try(InputStream input=app.getAssets().open(ASSETS+"boundaries.bin")) {
            long left=county.offset;
            while(left>0){long skipped=input.skip(left);if(skipped>0)left-=skipped;else if(input.read()!=-1)left--;else throw new EOFException();}
            int at=0;while(at<compressed.length){int n=input.read(compressed,at,compressed.length-at);if(n<0)throw new EOFException();at+=n;}
        }
        result=HistoricCountyGeometry.read(compressed);
        // A large individual county may exceed the budget, but does not remain cached.
        if(result.bytes<=4_000_000) {
            while(cacheBytes+result.bytes>4_000_000&&!cache.isEmpty()) {
                Iterator<HistoricCountyGeometry> old=cache.values().iterator();cacheBytes-=old.next().bytes;old.remove();
            }
            cache.put(county.code,result);cacheBytes+=result.bytes;
        }
        return result;
    }
}
