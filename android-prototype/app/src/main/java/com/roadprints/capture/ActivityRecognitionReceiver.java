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
        if (!ActivityRecognitionResult.hasResult(intent)) return;

        ActivityRecognitionResult result = ActivityRecognitionResult.extractResult(intent);
        if (result == null) return;

        List<DetectedActivity> activities = result.getProbableActivities();
        if (activities == null || activities.isEmpty()) return;

        DetectedActivity best = activities.get(0);
        Intent update = new Intent(context, CaptureService.class)
                .setAction(CaptureService.ACTION_ACTIVITY)
                .putExtra(CaptureService.EXTRA_ACTIVITY_TYPE, best.getType())
                .putExtra(CaptureService.EXTRA_CONFIDENCE, best.getConfidence());
        context.startService(update);
    }
}
