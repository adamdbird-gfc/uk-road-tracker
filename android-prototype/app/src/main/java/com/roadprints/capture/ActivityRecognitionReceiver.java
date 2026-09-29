package com.roadprints.capture;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.google.android.gms.location.ActivityRecognitionResult;
import com.google.android.gms.location.DetectedActivity;

import java.util.List;

public class ActivityRecognitionReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!ActivityRecognitionResult.hasResult(intent)) {
            sendDiagnostic(context, "Activity callback received without a recognition result.");
            return;
        }

        ActivityRecognitionResult result = ActivityRecognitionResult.extractResult(intent);
        if (result == null) {
            sendDiagnostic(context, "Activity callback could not be decoded.");
            return;
        }

        List<DetectedActivity> activities = result.getProbableActivities();
        if (activities == null || activities.isEmpty()) {
            sendDiagnostic(context, "Activity callback contained no probable activities.");
            return;
        }

        DetectedActivity best = activities.get(0);
        Intent update = new Intent(context, CaptureService.class)
                .setAction(CaptureService.ACTION_ACTIVITY)
                .putExtra(CaptureService.EXTRA_ACTIVITY_TYPE, best.getType())
                .putExtra(CaptureService.EXTRA_CONFIDENCE, best.getConfidence());
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(update);
        } else {
            context.startService(update);
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
