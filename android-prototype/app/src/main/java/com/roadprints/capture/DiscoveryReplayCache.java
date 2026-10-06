package com.roadprints.capture;

import android.content.Context;
import org.json.JSONObject;
import java.io.File;
import java.util.*;
import java.util.concurrent.Executor;

/** Bounded derived-data cache. Archive scans never gate replay playback. */
final class DiscoveryReplayCache {
    interface Callback { void ready(DiscoveryReplay replay); }
    private static final Map<String, DiscoveryReplay> READY = new LinkedHashMap<>();
    private static final Map<String, List<Callback>> PENDING = new HashMap<>();
    private static Executor preparer = ScreenDataLoader::execute;
    private static long revision = Long.MIN_VALUE;
    private static int generation;

    private static String key(Set<String> ids) {
        List<String> sorted = new ArrayList<>(ids); Collections.sort(sorted);
        try {
            java.security.MessageDigest digest=java.security.MessageDigest.getInstance("SHA-256");
            for(String id:sorted){digest.update(id.getBytes(java.nio.charset.StandardCharsets.UTF_8));digest.update((byte)0);}
            StringBuilder value=new StringBuilder("journey-replay-");
            for(byte b:digest.digest())value.append(String.format(Locale.ROOT,"%02x",b&255));
            return value.toString();
        } catch(Exception impossible){throw new IllegalStateException(impossible);}
    }
    private static void refresh(long current) {
        if(current!=revision){READY.clear();revision=current;}
    }
    static DiscoveryReplay peek(Context context,Set<String> ids) {
        long current=JourneyStore.dataRevision(context);
        synchronized(DiscoveryReplayCache.class){refresh(current);return READY.get(key(ids));}
    }
    static void request(Context context,Set<String> ids,Callback callback) {
        Context app=context.getApplicationContext();Set<String> selected=new HashSet<>(ids);
        final String diskKey=key(selected),job;final long version;final int epoch;
        long current=JourneyStore.dataRevision(app);
        synchronized(DiscoveryReplayCache.class) {
            refresh(current);DiscoveryReplay cached=READY.get(diskKey);
            if(cached!=null){if(callback!=null)callback.ready(cached);return;}
            version=revision;epoch=generation;job=version+":"+epoch+":"+diskKey;
            List<Callback> callbacks=PENDING.get(job);
            if(callbacks!=null){if(callback!=null)callbacks.add(callback);return;}
            if(PENDING.size()>=2){if(callback!=null)callback.ready(null);return;}
            callbacks=new ArrayList<>();if(callback!=null)callbacks.add(callback);PENDING.put(job,callbacks);
        }
        preparer.execute(()->{
            DiscoveryReplay result=null;
            try {
                if(valid(app,version,epoch)) {
                    JSONObject saved=PersistentScreenCache.read(app,diskKey,version);
                    result=saved==null?null:DiscoveryReplay.fromJson(saved);
                    if(result==null)result=DiscoveryReplay.calculate(app,selected,()->!valid(app,version,epoch));
                    if(valid(app,version,epoch)&&result!=null) {
                        synchronized(DiscoveryReplayCache.class) {
                            if(epoch==generation){PersistentScreenCache.write(app,diskKey,version,result.toJson());trim(app);}
                        }
                    }
                }
            } catch(Exception ignored) { result=null; }
            List<Callback> callbacks;
            if(!valid(app,version,epoch))result=null;
            synchronized(DiscoveryReplayCache.class) {
                if(epoch!=generation||revision!=version)result=null;
                if(result!=null){READY.put(diskKey,result);while(READY.size()>2)READY.remove(READY.keySet().iterator().next());}
                callbacks=PENDING.remove(job);
            }
            if(callbacks!=null)for(Callback listener:callbacks)listener.ready(result);
        });
    }
    private static boolean valid(Context context,long version,int epoch) {
        long current=JourneyStore.dataRevision(context);
        synchronized(DiscoveryReplayCache.class){return epoch==generation&&current==version;}
    }
    static synchronized void clear(Context context) {
        generation++;READY.clear();PENDING.clear();revision=Long.MIN_VALUE;
        File folder=new File(context.getFilesDir(),"screen-cache");
        File[] files=folder.listFiles((dir,name)->name.startsWith("journey-replay-"));
        if(files!=null)for(File file:files)file.delete();
    }
    private static void trim(Context context) {
        File[] files=new File(context.getFilesDir(),"screen-cache").listFiles((dir,name)->name.startsWith("journey-replay-")&&name.endsWith(".json"));
        if(files==null||files.length<=8)return;
        Arrays.sort(files,Comparator.comparingLong(File::lastModified));
        for(int i=0;i<files.length-8;i++)files[i].delete();
    }
}
