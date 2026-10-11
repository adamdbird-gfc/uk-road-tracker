package com.roadprints.capture;

import android.content.Context;
import android.content.SharedPreferences;

final class ContextRequestBudget {
    private static final Object LOCK=new Object();
    private final SharedPreferences prefs;
    ContextRequestBudget(Context context) {prefs=context.getSharedPreferences("context_request_budget_v1",Context.MODE_PRIVATE);}
    boolean reserve(long now) {
        synchronized(LOCK) {
            long day=now/86400000L;
            if(!ContextRequestPolicy.allowed(now,prefs.getLong("day",-1),prefs.getInt("requests",0),prefs.getLong("next_allowed",0)))return false;
            int count=prefs.getLong("day",-1)==day?prefs.getInt("requests",0):0;
            return prefs.edit().putLong("day",day).putInt("requests",count+1).putLong("next_allowed",now+60000L).commit();
        }
    }
    void success() {synchronized(LOCK){prefs.edit().putInt("failures",0).commit();}}
    void failure(long now,long retryAfter) {
        synchronized(LOCK) {
            int count=prefs.getInt("failures",0);
            prefs.edit().putInt("failures",Math.min(6,count+1)).putLong("next_allowed",
                    now+ContextRequestPolicy.failureDelay(count,retryAfter)).commit();
        }
    }
}
