package com.roadprints.capture;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Bounded, asynchronous public map tiles. Requests contain no user or journey identifiers. */
final class ContextMapCache {
    private final File directory;
    private final Context app;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private JSONArray features;
    private double south,west;
    private long validUntil;
    private boolean busy,closed;
    private long attemptedAt;
    private final ContextRequestBudget budget;
    private static final String SERVICE="https://uk-road-tracker-api.onrender.com/map-context/v1/";
    static boolean enabled(Context context) {
        return context.getSharedPreferences("roadprints_capture_state",Context.MODE_PRIVATE).getBoolean("public_map_context",false);
    }
    ContextMapCache(Context context) {app=context.getApplicationContext();directory=new File(app.getCacheDir(),"capture-context-v3");budget=new ContextRequestBudget(app);}
    ContextMap.Evidence evidence(double lat,double lon,float accuracy) {
        return ContextMap.at(enabled(app)&&covers(lat,lon)?features:null,lat,lon,accuracy);
    }
    private boolean covers(double lat,double lon) {
        return features!=null&&System.currentTimeMillis()<validUntil&&lat>=south-.003&&lat<=south+.013&&lon>=west-.006&&lon<=west+.026;
    }
    void request(double lat,double lon,Runnable callback) {
        if(!enabled(app)||closed||busy||covers(lat,lon)||System.currentTimeMillis()-attemptedAt<60_000L)return;
        busy=true; attemptedAt=System.currentTimeMillis();
        final double s=Math.floor(lat*100)/100,w=Math.floor(lon*50)/50;
        final String key=String.format(Locale.ROOT,"%.2f_%.2f.json",s,w);
        worker.execute(()->{
            JSONArray loaded=null; HttpURLConnection connection=null; boolean reserved=false; long retryAfter=0,expires=0;
            try {
                directory.mkdirs(); File file=new File(directory,key);
                JSONObject cached=null;
                if(file.isFile())try {cached=new JSONObject(new String(Files.readAllBytes(file.toPath()),StandardCharsets.UTF_8));}
                catch(Exception invalid){file.delete();}
                if(cached!=null && cached.optInt("schema_version")==1
                        && cached.optLong("expires_at")*1000L>System.currentTimeMillis()) {
                    loaded=cached.getJSONArray("elements");expires=cached.getLong("expires_at")*1000L;
                } else {
                    if(!enabled(app))throw new java.io.IOException("Online context disabled");
                    if(!budget.reserve(System.currentTimeMillis()))throw new java.io.IOException("Context request budget paused");
                    reserved=true;
                    connection=(HttpURLConnection)new URL(SERVICE+(int)Math.floor(lat*100)+"/"+(int)Math.floor(lon*50)).openConnection();
                    connection.setConnectTimeout(6000); connection.setReadTimeout(25000);
                    connection.setRequestMethod("GET");
                    connection.setRequestProperty("User-Agent","Roadprints/"+BuildConfig.VERSION_NAME);
                    int response=connection.getResponseCode();
                    try {retryAfter=Long.parseLong(connection.getHeaderField("Retry-After"));}catch(Exception ignored){}
                    if(response!=200)throw new java.io.IOException("Context unavailable");
                    java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();byte[] buffer=new byte[8192];
                    try(java.io.InputStream input=connection.getInputStream()) {
                        int n;while((n=input.read(buffer))!=-1){out.write(buffer,0,n);if(out.size()>4_000_000)throw new java.io.IOException("Context tile too large");}
                    }
                    JSONObject document=new JSONObject(out.toString("UTF-8"));
                    if(document.optInt("schema_version")!=1 || !document.optString("area_key").equals("v1:"+(int)Math.floor(lat*100)+":"+(int)Math.floor(lon*50)))
                        throw new java.io.IOException("Invalid map context area");
                    expires=document.getLong("expires_at")*1000L;
                    if(expires<=System.currentTimeMillis())throw new java.io.IOException("Expired context area");
                    loaded=document.getJSONArray("elements");
                    for(int i=0;i<loaded.length();i++) {
                        JSONObject f=loaded.optJSONObject(i);if(f==null)continue;
                        JSONArray g=f.optJSONArray("geometry");if(g==null)continue;
                        double minLat=90,maxLat=-90,minLon=180,maxLon=-180;
                        for(int j=0;j<g.length();j++) {
                            JSONObject p=g.optJSONObject(j);if(p==null)continue;
                            double a=p.optDouble("lat"),b=p.optDouble("lon");
                            minLat=Math.min(minLat,a);maxLat=Math.max(maxLat,a);minLon=Math.min(minLon,b);maxLon=Math.max(maxLon,b);
                        }
                        f.put("bounds",new JSONObject().put("minlat",minLat).put("maxlat",maxLat).put("minlon",minLon).put("maxlon",maxLon));
                    }
                    File[] files=directory.listFiles();
                    if(files!=null&&files.length>=64) {
                        java.util.Arrays.sort(files,java.util.Comparator.comparingLong(File::lastModified));
                        for(int i=0;i<=files.length-64;i++)files[i].delete();
                    }
                    Files.write(file.toPath(),document.toString().getBytes(StandardCharsets.UTF_8));
                }
                if(reserved)budget.success();
            } catch(Exception ignored) {
                loaded=null;
                if(reserved)budget.failure(System.currentTimeMillis(),retryAfter);
                // Keep recording; unavailable context is not arrival evidence.
            }
            finally {if(connection!=null)connection.disconnect();}
            final JSONArray result=loaded; final long resultExpiry=expires;
            main.post(()->{busy=false;if(closed)return;if(result!=null){features=result;south=s;west=w;validUntil=resultExpiry;}callback.run();});
        });
    }
    void close(){closed=true;worker.shutdownNow();}
}
