package com.roadprints.capture;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.google.android.gms.location.ActivityTransitionEvent;
import com.google.android.gms.location.ActivityTransitionResult;
import com.google.android.gms.location.DetectedActivity;

import java.util.List;

public class ActivityRecognitionReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!ActivityTransitionResult.hasResult(intent)) {
            sendDiagnostic(context, "Activity transition callback received without a transition result.");
            return;
        }

        ActivityTransitionResult result = ActivityTransitionResult.extractResult(intent);
        if (result == null || result.getTransitionEvents() == null
                || result.getTransitionEvents().isEmpty()) {
            sendDiagnostic(context, "Activity transition callback contained no events.");
            return;
        }

        for (ActivityTransitionEvent event : result.getTransitionEvents()) {
            Intent update = new Intent(context, CaptureService.class)
                    .setAction(CaptureService.ACTION_ACTIVITY)
                    .putExtra(CaptureService.EXTRA_ACTIVITY_TYPE, event.getActivityType())
                    .putExtra(CaptureService.EXTRA_TRANSITION, event.getTransitionType());
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(update);
            } else {
                context.startService(update);
            }
        }
    }

    private void sendDiagnostic(Context context, String message) {
        Intent update = new Intent(CaptureService.ACTION_UPDATE);
        update.setPackage(context.getPackageName());
        update.putExtra(CaptureService.EXTRA_ARMED, true);
        update.putExtra(CaptureService.EXTRA_MESSAGE, message);
        context.sendBroadcast(update);
    }
}
