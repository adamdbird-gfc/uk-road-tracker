package com.roadprints.capture;

import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.Build;
import org.json.*;
import java.time.Instant;
import java.util.UUID;

/** Durable failure episodes and at-most-once alert delivery, independent of report visibility. */
final class DiagnosticNotifications {
    static final String PREFS="roadprints_diagnostic_delivery_v1";
    static final int ID=44;
    private static final String CHANNEL="roadprints_debug_reports",LAST="last_event";
    private static android.content.SharedPreferences prefs(Context c){return c.getSharedPreferences(PREFS,0);}
    private static JSONObject read(String value){try{return new JSONObject(value);}catch(Exception e){return new JSONObject();}}
    static synchronized JSONObject event(Context c,String scope,String signature,String original,String trigger,String worker)throws Exception {
        android.content.SharedPreferences p=prefs(c);
        String key="episode:"+NcnJson.digest(scope);
        JSONObject prior=read(p.getString(key,"{}"));
        if(signature.equals(prior.optString("signature"))) {
            JSONObject receipt=read(p.getString(LAST,"{}"));
            // Rediscovery is diagnostic evidence, never a new delivery event.
            receipt.put("rediscovered_at_utc",Instant.now().toString()).put("rediscovery_trigger",trigger)
                    .put("rediscovery_worker",worker).put("rediscovered_event_id",prior.optString("event_id"));
            p.edit().putString(LAST,receipt.toString()).commit();return null;
        }
        JSONObject event=new JSONObject().put("event_id",UUID.randomUUID().toString()).put("scope_sha256",NcnJson.digest(scope))
                .put("signature",signature).put("notification_id",ID).put("recorded_at_utc",Instant.now().toString())
                .put("original_failure_at_utc",original).put("trigger",trigger).put("worker",worker)
                .put("thread",Thread.currentThread().getName()).put("delivery_state","recording");
        // Persist the new event before report/notification work; startup never promotes legacy reports.
        if(!p.edit().putString(key,event.toString()).putString(LAST,event.toString()).commit())return null;
        return event;
    }
    static synchronized void ready(Context c,JSONObject expected)throws Exception {
        android.content.SharedPreferences p=prefs(c);JSONObject event=read(p.getString(LAST,"{}"));
        if(expected!=null&&expected.optString("event_id").equals(event.optString("event_id"))) {
            event.put("delivery_state","pending");p.edit().putString(LAST,event.toString()).commit();
        }
    }
    static synchronized void resolved(Context c,String journeyId) {
        prefs(c).edit().remove("episode:"+NcnJson.digest("journey:"+journeyId)).commit();
    }
    static synchronized void deliver(Context c,String trigger,String worker) {
        android.content.SharedPreferences p=prefs(c);JSONObject event=read(p.getString(LAST,"{}"));
        if(!"pending".equals(event.optString("delivery_state")))return;
        NotificationManager manager=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);
        if(manager==null||!manager.areNotificationsEnabled())return;
        if(Build.VERSION.SDK_INT>=33&&c.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return;
        try {
            if(Build.VERSION.SDK_INT>=26)manager.createNotificationChannel(new NotificationChannel(CHANNEL,"Roadprints debug reports",NotificationManager.IMPORTANCE_DEFAULT));
            String created=Instant.now().toString();
            event.put("notification_created_at_utc",created).put("delivery_trigger",trigger).put("delivery_worker",worker)
                    .put("delivery_state","claimed");
            // Crash between claim and notify may lose this alert, but cannot announce an old failure again.
            if(!p.edit().putString(LAST,event.toString()).commit())return;
            Intent open=new Intent(c,DebugActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent content=PendingIntent.getActivity(c,ID,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            Intent dismissed=new Intent(c,DiagnosticNotificationReceiver.class).putExtra("event_id",event.getString("event_id"));
            PendingIntent delete=PendingIntent.getBroadcast(c,ID,dismissed,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            android.os.Bundle details=new android.os.Bundle();
            for(String key:new String[]{"event_id","notification_created_at_utc","original_failure_at_utc","trigger","worker","delivery_trigger","delivery_worker"})details.putString("roadprints_"+key,event.optString(key));
            Notification.Builder builder=Build.VERSION.SDK_INT>=26?new Notification.Builder(c,CHANNEL):new Notification.Builder(c);
            Notification notification=builder.setSmallIcon(android.R.drawable.stat_notify_error)
                    .setContentTitle("Roadprints debug report ready").setContentText("Tap to review the new report. It stays on this device.")
                    .setContentIntent(content).setDeleteIntent(delete).setAutoCancel(true)
                    .setWhen(Instant.parse(created).toEpochMilli()).addExtras(details).build();
            manager.notify(ID,notification);
            event.put("delivery_state","posted").put("posted_at_utc",Instant.now().toString());
            p.edit().putString(LAST,event.toString()).commit();
        }catch(Exception failure){
            // Never loop on an OS delivery failure. Reports remain reviewable in Debug.
            try{event.put("delivery_state","delivery_failed").put("delivery_error",failure.getClass().getSimpleName());p.edit().putString(LAST,event.toString()).commit();}
            catch(Exception ignored){}
        }
    }
    static synchronized void acknowledged(Context c,String state,String id) {
        android.content.SharedPreferences p=prefs(c);JSONObject event=read(p.getString(LAST,"{}"));
        if(id!=null&&!id.equals(event.optString("event_id")))return;
        try{if(event.length()>0){event.put("delivery_state",state).put("acknowledged_at_utc",Instant.now().toString());p.edit().putString(LAST,event.toString()).commit();}}
        catch(Exception ignored){}
    }
    static String report(Context c){return prefs(c).getString(LAST,"");}
}
