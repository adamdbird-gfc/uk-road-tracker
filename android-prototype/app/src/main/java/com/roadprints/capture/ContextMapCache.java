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
    private boolean busy,closed;
    private long attemptedAt;
    static boolean enabled(Context context) {
        return context.getSharedPreferences("roadprints_capture_state",Context.MODE_PRIVATE).getBoolean("public_map_context",false);
    }
    ContextMapCache(Context context) {app=context.getApplicationContext();directory=new File(app.getCacheDir(),"capture-context-v1");}
    ContextMap.Evidence evidence(double lat,double lon,float accuracy) {
        return ContextMap.at(covers(lat,lon)?features:null,lat,lon,accuracy);
    }
    private boolean covers(double lat,double lon) {
        return features!=null&&lat>=south-.003&&lat<=south+.013&&lon>=west-.006&&lon<=west+.026;
    }
    void request(double lat,double lon,Runnable callback) {
        if(!enabled(app)||closed||busy||covers(lat,lon)||System.currentTimeMillis()-attemptedAt<60_000L)return;
        busy=true; attemptedAt=System.currentTimeMillis();
        final double s=Math.floor(lat*100)/100,w=Math.floor(lon*50)/50;
        final String key=String.format(Locale.ROOT,"%.2f_%.2f.json",s,w);
        worker.execute(()->{
            JSONArray loaded=null; HttpURLConnection connection=null;
            try {
                directory.mkdirs(); File file=new File(directory,key);
                if(file.isFile()&&System.currentTimeMillis()-file.lastModified()<7*86400000L)
                    loaded=new JSONObject(new String(Files.readAllBytes(file.toPath()),StandardCharsets.UTF_8)).getJSONArray("elements");
                else {
                    if(!enabled(app))throw new java.io.IOException("Online context disabled");
                    String bbox=String.format(Locale.ROOT,"%.3f,%.3f,%.3f,%.3f",s-.003,w-.006,s+.013,w+.026);
                    String query="[out:json][timeout:12];(way[highway]("+bbox+");way[railway=rail]("+bbox+");"
                            +"way[building]("+bbox+");way[amenity=parking]("+bbox+");way[leisure]("+bbox+");way[natural]("+bbox+"));out geom;";
                    connection=(HttpURLConnection)new URL("https://overpass-api.de/api/interpreter").openConnection();
                    connection.setConnectTimeout(6000); connection.setReadTimeout(16000);
                    connection.setRequestMethod("POST"); connection.setDoOutput(true);
                    connection.setRequestProperty("User-Agent","Roadprints/0.25.175");
                    connection.setRequestProperty("Content-Type","application/x-www-form-urlencoded");
                    byte[] body=("data="+java.net.URLEncoder.encode(query,"UTF-8")).getBytes(StandardCharsets.UTF_8);
                    try(java.io.OutputStream out=connection.getOutputStream()){out.write(body);}
                    if(connection.getResponseCode()!=200)throw new java.io.IOException("Context unavailable");
                    java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();byte[] buffer=new byte[8192];
                    try(java.io.InputStream input=connection.getInputStream()) {
                        int n;while((n=input.read(buffer))!=-1){out.write(buffer,0,n);if(out.size()>4_000_000)throw new java.io.IOException("Context tile too large");}
                    }
                    JSONObject document=new JSONObject(out.toString("UTF-8"));
                    if(document.has("remark"))throw new java.io.IOException("Incomplete map context");
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
            } catch(Exception ignored) { /* Keep recording; unavailable context is not arrival evidence. */ }
            finally {if(connection!=null)connection.disconnect();}
            final JSONArray result=loaded;
            main.post(()->{busy=false;if(closed)return;if(result!=null){features=result;south=s;west=w;}callback.run();});
        });
    }
    void close(){closed=true;worker.shutdownNow();}
}
