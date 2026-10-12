package com.roadprints.capture;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.AtomicFile;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Reviewed map data only. No journey, visit or location information is sent. */
final class CollectionCatalogueUpdates {
    static final long BUNDLED_REVISION=2026101201L;
    static final long DAY_MS=24L*60*60*1000, RETRY_MS=60L*60*1000;
    static final int MANIFEST_LIMIT=32*1024;
    static final String BASE_URL="https://raw.githubusercontent.com/adamdbird-gfc/uk-road-tracker/refs/heads/fix/onboarding-start-tracking/collections/published/wetherspoons/";
    private static final String PREFS="roadprints_spoons_updates_v1", NEXT_CHECK="next_check";
    private static final Object LOCK=new Object(), UPDATE_LOCK=new Object();
    private static final ExecutorService WORKER=Executors.newSingleThreadExecutor();
    private static JSONObject current;
    private static JSONArray pins;
    private static long revision=BUNDLED_REVISION;
    private static boolean inFlight;
    interface Transport { byte[] get(String filename,int limit) throws Exception; }
    private CollectionCatalogueUpdates(){}

    static JSONArray pins(Context context,JSONObject bundled){
        synchronized(LOCK){
            if(current==null){
                current=bundled;revision=BUNDLED_REVISION;
                AtomicFile file=cache(context);
                try(InputStream input=file.openRead()){
                    JSONObject saved=new JSONObject(new String(readBounded(input,4*1024*1024),StandardCharsets.UTF_8));
                    JSONObject manifest=saved.getJSONObject("manifest");
                    byte[] bytes=saved.getString("payload").getBytes(StandardCharsets.UTF_8);
                    JSONObject candidate=validatePayload(manifest,bytes);
                    if(manifest.getLong("revision")>BUNDLED_REVISION){
                        current=candidate;revision=manifest.getLong("revision");
                    }
                }catch(Exception unavailable){ /* The bundled map is always available offline. */ }
                pins=CollectionMapCatalog.approvedVenues(current);
            }
            return pins;
        }
    }
    static void refreshIfDue(Context context,Runnable onChanged){
        Context app=context.getApplicationContext();
        if(!CollectionAccessStore.installed(app,CollectionAccessStore.SPOONS))return;
        long now=System.currentTimeMillis();
        synchronized(LOCK){
            long next=app.getSharedPreferences(PREFS,0).getLong(NEXT_CHECK,0);
            if(inFlight || !due(now,next))return;
            inFlight=true;
            app.getSharedPreferences(PREFS,0).edit().putLong(NEXT_CHECK,now+RETRY_MS).apply();
        }
        WORKER.execute(()->{
            boolean changed=false;
            try{
                changed=refreshNow(app,CollectionCatalogueUpdates::download);
                app.getSharedPreferences(PREFS,0).edit()
                        .putLong(NEXT_CHECK,System.currentTimeMillis()+DAY_MS).apply();
            }catch(Exception unavailable){ /* Keep the last validated map and retry after an hour. */ }
            finally { synchronized(LOCK){inFlight=false;} }
            if(changed && onChanged!=null)new Handler(Looper.getMainLooper()).post(onChanged);
        });
    }
    static boolean due(long now,long next){return next<=now || next>now+DAY_MS;}
    static boolean refreshNow(Context context,Transport transport) throws Exception {
        synchronized(UPDATE_LOCK){
            CollectionMapCatalog.spoons(context);
            JSONObject manifest=new JSONObject(new String(transport.get("manifest-v1.json",MANIFEST_LIMIT),StandardCharsets.UTF_8));
            validateManifest(manifest);
            long incoming=manifest.getLong("revision");
            synchronized(LOCK){if(incoming<=revision)return false;}
            byte[] bytes=transport.get(manifest.getString("filename"),CollectionMapCatalog.MAX_CATALOGUE_BYTES);
            JSONObject candidate=validatePayload(manifest,bytes);
            JSONObject saved=new JSONObject().put("manifest",manifest)
                    .put("payload",new String(bytes,StandardCharsets.UTF_8));
            AtomicFile file=cache(context);
            FileOutputStream output=null;
            synchronized(LOCK){
                if(incoming<=revision)return false;
                try{
                    output=file.startWrite();
                    output.write(saved.toString().getBytes(StandardCharsets.UTF_8));
                    file.finishWrite(output);output=null;
                }finally{if(output!=null)file.failWrite(output);}
                current=candidate;revision=incoming;pins=CollectionMapCatalog.approvedVenues(current);
            }
            return true;
        }
    }
    static void validateManifest(JSONObject m) throws Exception {
        long release=m.getLong("revision");
        if(m.getInt("schema_version")!=1 || !"wetherspoons".equals(m.getString("collection_id"))
                || release<BUNDLED_REVISION || release>9999999999L
                || !("catalogue-"+release+".json").equals(m.getString("filename"))
                || !m.getString("sha256").matches("[0-9a-f]{64}")
                || m.getInt("bytes")<2 || m.getInt("bytes")>CollectionMapCatalog.MAX_CATALOGUE_BYTES
                || m.getString("catalogue_version").isEmpty())
            throw new IllegalArgumentException("Unsupported pub manifest");
    }
    static JSONObject validatePayload(JSONObject manifest,byte[] bytes) throws Exception {
        validateManifest(manifest);
        if(bytes.length!=manifest.getInt("bytes") || !sha256(bytes).equals(manifest.getString("sha256")))
            throw new IllegalArgumentException("Pub catalogue integrity check failed");
        JSONObject root=new JSONObject(new String(bytes,StandardCharsets.UTF_8));
        if(root.getInt("schema_version")!=1 || !"wetherspoons".equals(root.getString("collection_id"))
                || !"UK".equals(root.getString("scope")) || !"approved".equals(root.getString("release_status"))
                || !"map_only_partial_beta".equals(root.getString("approval_scope"))
                || !Boolean.TRUE.equals(root.opt("ready_for_app"))
                || !Boolean.FALSE.equals(root.opt("visit_enabled"))
                || !Boolean.FALSE.equals(root.opt("coverage_complete"))
                || !root.has("completion_target") || root.opt("completion_target")!=JSONObject.NULL
                || !manifest.getString("catalogue_version").equals(root.getString("catalogue_version"))
                || root.getString("attribution").isEmpty())
            throw new IllegalArgumentException("Unsupported pub catalogue");
        JSONArray venues=root.getJSONArray("venues");
        if(venues.length()>5000 || root.getJSONObject("review_summary").getInt("map_venues")!=venues.length())
            throw new IllegalArgumentException("Pub count does not match review");
        HashSet<String> ids=new HashSet<>(),aliases=new HashSet<>();
        LocalDate today=LocalDate.now(ZoneOffset.UTC);
        for(int i=0;i<venues.length();i++){
            JSONObject v=venues.getJSONObject(i);
            String id=v.getString("id");
            if(!id.matches("spoons-(osm-[nwr][0-9]+|curated-[a-z0-9-]+)") || id.length()>200 || !ids.add(id)
                    || !"active".equals(v.getString("status")) || !"reviewed_current".equals(v.getString("operating_status"))
                    || !Boolean.FALSE.equals(v.opt("visit_enabled"))
                    || v.getString("name").trim().isEmpty() || v.getString("name").length()>200
                    || v.getString("town").length()>200
                    || LocalDate.parse(v.getString("map_reviewed_at")).isAfter(today))
                throw new IllegalArgumentException("Unreviewed or duplicate pub");
            double lat=v.getDouble("lat"),lon=v.getDouble("lon");
            if(!Double.isFinite(lat)||!Double.isFinite(lon)||lat<49||lat>61||lon< -9||lon>2)
                throw new IllegalArgumentException("Pub is outside UK coordinate bounds");
            if(id.startsWith("spoons-curated-") && (!Boolean.TRUE.equals(v.opt("confirmation_required"))
                    || v.getString("location_label").isEmpty() || v.getString("attribution").isEmpty()))
                throw new IllegalArgumentException("Curated pub labels are required");
            JSONArray names=v.getJSONArray("osm_aliases");
            for(int j=0;j<names.length();j++){
                String alias=names.getString(j);
                if(!alias.matches("(node|way|relation)/[0-9]+")||!aliases.add(alias))
                    throw new IllegalArgumentException("Duplicate pub source identity");
            }
        }
        if(CollectionMapCatalog.approvedVenues(root).length()!=venues.length())
            throw new IllegalArgumentException("Pub map projection rejected");
        return root;
    }
    static String sha256(byte[] bytes) throws Exception {
        byte[] digest=MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder hex=new StringBuilder();
        for(byte b:digest)hex.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
        return hex.toString();
    }
    private static byte[] download(String filename,int limit) throws Exception {
        if(!filename.equals("manifest-v1.json")&&!filename.matches("catalogue-[0-9]{10}\\.json"))
            throw new IllegalArgumentException("Unsupported pub file");
        HttpURLConnection connection=(HttpURLConnection)new URL(BASE_URL+filename).openConnection();
        connection.setConnectTimeout(5000);connection.setReadTimeout(10000);
        connection.setInstanceFollowRedirects(false);
        try{
            if(connection.getResponseCode()!=200 || connection.getContentLengthLong()>limit)
                throw new java.io.IOException("Pub release unavailable");
            try(InputStream input=connection.getInputStream()){return readBounded(input,limit);}
        }finally{connection.disconnect();}
    }
    static byte[] readBounded(InputStream input,int limit) throws Exception {
        ByteArrayOutputStream output=new ByteArrayOutputStream();
        byte[] buffer=new byte[8192];int count;
        while((count=input.read(buffer))!=-1){
            if(output.size()+count>limit)throw new java.io.IOException("Pub file exceeds limit");
            output.write(buffer,0,count);
        }
        return output.toByteArray();
    }
    private static AtomicFile cache(Context context){
        return new AtomicFile(new File(context.getFilesDir(),"wetherspoons-map-cache-v1.json"));
    }
    static void resetForTests(){synchronized(LOCK){current=null;pins=null;revision=BUNDLED_REVISION;}}
}
