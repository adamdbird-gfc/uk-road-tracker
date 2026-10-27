package com.roadprints.capture;

import android.content.Context;
import android.util.AtomicFile;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.*;
import java.util.zip.GZIPInputStream;

/** Firebase reference delivery. Only complete, verified generations become active. */
final class NcnCatalogueFiles {
    static final String MANIFEST="collections/published/ncn/manifest-v2.json";
    private static final ExecutorService WORKER=Executors.newSingleThreadExecutor();
    private static boolean updating;
    private static final ThreadLocal<String> cachedVersion=new ThreadLocal<>();
    // Download/verification may take minutes. UI scheduling must never acquire this lock.
    static final Object INSTALL_LOCK=new Object();
    private static final java.util.Set<String> verified=java.util.concurrent.ConcurrentHashMap.newKeySet();
    static final class Installed {final File file;final JSONObject manifest;Installed(File f,JSONObject m){file=f;manifest=m;}}
    static File directory(Context c){ReferenceDataDiagnostics.initialize(c.getFilesDir());File d=new File(c.getFilesDir(),"ncn-reference-v2");if(!d.isDirectory()&&!d.mkdirs())throw new IllegalStateException("NCN cache unavailable");return d;}
    static void validate(JSONObject m)throws Exception{
        String hash=m.getString("sha256");long bytes=m.getLong("database_bytes"),packed=m.getLong("compressed_bytes");
        if(m.getInt("schema_version")!=2||!m.getString("scope").equals("national-verified-open-checkpoint")||m.getBoolean("whole_published_route_ready")||!hash.matches("[a-f0-9]{64}")||!m.getString("compressed_sha256").matches("[a-f0-9]{64}")||!m.getString("filename").equals("collections/published/ncn/"+hash+"/catalogue.db.gz")||bytes<4096||bytes>512L*1024*1024||packed<100||packed>256L*1024*1024||m.getInt("supported_identities")<1||m.getInt("supported_identities")>2000||m.getLong("revision")<1)throw new IllegalArgumentException("Unsupported NCN release manifest");
    }
    static JSONObject readManifest(InputStream source)throws Exception{try(InputStream in=source;ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1){if(out.size()+n>32768)throw new IOException("NCN manifest too large");out.write(b,0,n);}return new JSONObject(new String(out.toByteArray(),StandardCharsets.UTF_8));}}
    static JSONObject readManifest(File f)throws Exception{return readManifest(new AtomicFile(f).openRead());}
    static JSONObject remoteManifest()throws Exception{HttpURLConnection http=connection(MANIFEST);try{if(http.getResponseCode()!=200)throw new IOException("NCN manifest unavailable");JSONObject m=readManifest(http.getInputStream());validate(m);return m;}finally{http.disconnect();}}
    static Installed cached(Context c)throws Exception{
        File dir=directory(c);Exception failure=null;
        for(String name:new String[]{"active.json","previous.json"})try{JSONObject m=readManifest(new File(dir,name));validate(m);File file=new File(dir,m.getString("sha256")+".db");if(file.length()!=m.getLong("database_bytes"))throw new IOException("Cached NCN length differs");String key=file.getPath()+":"+file.lastModified();if(!verified.contains(key)){if(!digest(file).equals(m.getString("sha256")))throw new IOException("Cached NCN checksum differs");verified.add(key);}return new Installed(file,m);}catch(Exception absent){failure=absent;}
        throw new IOException("No verified NCN catalogue cached",failure);
    }
    static Installed open(Context c)throws Exception{
        synchronized(INSTALL_LOCK) {
        try{Installed result=cached(c);ReferenceDataDiagnostics.cache("ncn",result.manifest.getString("sha256"),result.manifest.getString("sha256"),true,"valid");return result;}catch(IOException absent){
            android.content.SharedPreferences retry=c.getSharedPreferences("ncn_reference_updates",0);
            long now=System.currentTimeMillis(), next=retry.getLong("install_retry_after",0);
            if(now<next && next-now<=60*60_000)throw new IOException("NCN retry deferred; use explicit Retry");
            retry.edit().putLong("install_retry_after",now+60*60_000).apply();
            ReferenceDataDiagnostics.cache("ncn","seed_or_latest","none",false,"missing_or_corrupt");cachedVersion.set("none");
            JSONObject seed=readManifest(c.getAssets().open("ncn-national/manifest.json"));validate(seed);
            JSONObject selected=seed;try{JSONObject latest=remoteManifest();if(latest.getLong("revision")>=seed.getLong("revision"))selected=latest;}catch(Exception unavailable){/* Immutable seed is a fallback when discovery is unavailable. */}
            NcnProgressStore.catalogueStatus("Downloading verified-open NCN catalogue…");install(c,selected);retry.edit().remove("install_retry_after").putLong("next",System.currentTimeMillis()+7L*24*60*60_000).apply();return cached(c);
        }
        }
    }
    static HttpURLConnection connection(String path)throws Exception{
        if(path.contains("..")||path.startsWith("/"))throw new IllegalArgumentException("Invalid reference path");
        HttpURLConnection http=new ReferenceDataConnection(new URL(PublicReferenceData.BASE_URL+path),path.equals(MANIFEST)?"ncn-manifest":"ncn",path.equals(MANIFEST)?"manifest":path.split("/")[3],path.equals(MANIFEST)?"ncn_manifest_discovery":"ncn_install_or_update",cachedVersion.get()==null?"unknown":cachedVersion.get());http.setInstanceFollowRedirects(false);http.setConnectTimeout(30_000);http.setReadTimeout(45_000);http.setRequestProperty("Accept-Encoding","identity");return http;
    }
    static String digest(File file)throws Exception{MessageDigest hash=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(file)){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)hash.update(b,0,n);}StringBuilder out=new StringBuilder();for(byte b:hash.digest())out.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return out.toString();}
    static void install(Context c,JSONObject manifest)throws Exception{
        long started=android.os.SystemClock.elapsedRealtime();
        synchronized(INSTALL_LOCK){installLocked(c,manifest);}
        PerformanceDiagnostics.timing("ncn_install",android.os.SystemClock.elapsedRealtime()-started,"");
    }
    private static void installLocked(Context c,JSONObject manifest)throws Exception{
        validate(manifest);File dir=directory(c),database=new File(dir,manifest.getString("sha256")+".db");
        if(!database.exists()||database.length()!=manifest.getLong("database_bytes")||!digest(database).equals(manifest.getString("sha256"))){
            File download=new File(dir,manifest.getString("compressed_sha256")+".part");long offset=download.exists()?download.length():0;if(offset>manifest.getLong("compressed_bytes")){download.delete();offset=0;}
            if(offset<manifest.getLong("compressed_bytes")){
                HttpURLConnection http=connection(manifest.getString("filename"));if(offset>0)http.setRequestProperty("Range","bytes="+offset+"-");
                try{int code=http.getResponseCode();boolean append=code==206&&offset>0&&http.getHeaderField("Content-Range")!=null&&http.getHeaderField("Content-Range").startsWith("bytes "+offset+"-");if(code!=200&&!append)throw new IOException("NCN download HTTP "+code);if(!append)offset=0;
                    try(InputStream in=http.getInputStream();FileOutputStream out=new FileOutputStream(download,append)){byte[] b=new byte[65536];int n;long total=offset,deadline=System.currentTimeMillis()+10*60_000;while((n=in.read(b))!=-1){if(Thread.currentThread().isInterrupted()||System.currentTimeMillis()>deadline)throw new IOException("NCN download interrupted; Retry resumes it");total+=n;if(total>manifest.getLong("compressed_bytes"))throw new IOException("NCN download exceeds manifest");out.write(b,0,n);NcnProgressStore.catalogueStatus("Downloading NCN catalogue · "+(100*total/manifest.getLong("compressed_bytes"))+"%");}out.getFD().sync();}
                }finally{http.disconnect();}
            }
            if(download.length()!=manifest.getLong("compressed_bytes"))throw new IOException("NCN download incomplete; Retry resumes it");
            if(!digest(download).equals(manifest.getString("compressed_sha256"))){download.delete();throw new IOException("NCN download integrity failure");}
            File temporary=new File(dir,manifest.getString("sha256")+".tmp");
            try(InputStream in=new GZIPInputStream(new FileInputStream(download));FileOutputStream out=new FileOutputStream(temporary)){byte[] b=new byte[65536];int n;long total=0;while((n=in.read(b))!=-1){total+=n;if(total>manifest.getLong("database_bytes"))throw new IOException("NCN decoded limit exceeded");out.write(b,0,n);}out.getFD().sync();}
            if(temporary.length()!=manifest.getLong("database_bytes")||!digest(temporary).equals(manifest.getString("sha256"))){temporary.delete();throw new IOException("NCN decoded integrity failure");}
            try(android.database.sqlite.SQLiteDatabase db=android.database.sqlite.SQLiteDatabase.openDatabase(temporary.getPath(),null,android.database.sqlite.SQLiteDatabase.OPEN_READONLY);android.database.Cursor check=db.rawQuery("PRAGMA quick_check",null);android.database.Cursor routes=db.rawQuery("SELECT COUNT(*) FROM routes",null)){
                if(!check.moveToFirst()||!check.getString(0).equals("ok")||!routes.moveToFirst()||routes.getInt(0)!=manifest.getInt("supported_identities"))throw new IOException("NCN database validation failed");
            }
            if(!temporary.renameTo(database))throw new IOException("Cannot commit NCN database");download.delete();
        }
        File active=new File(dir,"active.json");if(active.exists()){JSONObject previous=null;try{previous=readManifest(active);validate(previous);}catch(Exception corrupt){/* Do not preserve a broken pointer over the last good rollback. */}if(previous!=null&&!previous.optString("sha256").equals(manifest.getString("sha256")))write(new File(dir,"previous.json"),previous);}
        write(active,manifest);
        java.util.Set<String> keep=new java.util.HashSet<>();keep.add(manifest.getString("sha256")+".db");
        try{keep.add(readManifest(new File(dir,"previous.json")).getString("sha256")+".db");}catch(Exception absent){/* First installation has no prior generation. */}
        File[] generations=dir.listFiles();if(generations!=null)for(File generation:generations)if(generation.getName().matches("[a-f0-9]{64}\\.db")&&!keep.contains(generation.getName()))generation.delete();
    }
    static void write(File target,JSONObject m)throws Exception{AtomicFile file=new AtomicFile(target);FileOutputStream out=null;try{out=file.startWrite();out.write(m.toString().getBytes(StandardCharsets.UTF_8));file.finishWrite(out);}catch(Exception e){if(out!=null)file.failWrite(out);throw e;}}
    static synchronized void refreshIfDue(Context context){
        Context c=context.getApplicationContext();ReferenceDataDiagnostics.initialize(c.getFilesDir());android.content.SharedPreferences prefs=c.getSharedPreferences("ncn_reference_updates",0);long now=System.currentTimeMillis();if(updating||now<prefs.getLong("next",0))return;updating=true;prefs.edit().putLong("next",now+60*60_000).apply();
        WORKER.execute(()->{try{Installed previous=cached(c);cachedVersion.set(previous.manifest.getString("sha256"));JSONObject incoming=remoteManifest();validate(incoming);if(incoming.getLong("revision")>previous.manifest.getLong("revision")){install(c,incoming);NcnCatalogue.invalidate();NcnProgressStore.catalogueChanged(c);}prefs.edit().putLong("next",System.currentTimeMillis()+7L*24*60*60_000).apply();}catch(Exception unavailable){/* Keep the last fully validated offline catalogue. */}finally{cachedVersion.remove();synchronized(NcnCatalogueFiles.class){updating=false;}}});
    }
}
