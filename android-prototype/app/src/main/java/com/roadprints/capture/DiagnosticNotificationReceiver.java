package com.roadprints.capture;

import android.content.*;

/** A shade dismissal acknowledges this event without deleting any report or journey. */
public final class DiagnosticNotificationReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent) {
        String id=intent.getStringExtra("event_id");
        if(id!=null)DiagnosticNotifications.acknowledged(context,"dismissed",id);
    }
}
