package com.roadprints.capture;

import android.app.Application;
import android.content.SharedPreferences;

/** Installs the local crash recorder before any Roadprints screen is created. */
public class RoadprintsApplication extends Application {
    private final SharedPreferences.OnSharedPreferenceChangeListener captureState=(preferences,key)->{
        if("active".equals(key)&&!preferences.getBoolean(key,false))MilepostReconciliationJob.archiveChanged(this);
    };
    @Override public void onCreate() {
        super.onCreate();
        BetaMeasurement.install(this);
        CrashReporter.install(this);
        PerformanceDiagnostics.install(this);
        if(MilepostIntegration.PRODUCTION.allowed(this)) {
            getSharedPreferences("roadprints_capture_state",MODE_PRIVATE).registerOnSharedPreferenceChangeListener(captureState);
            MilepostReconciliationJob.archiveChanged(this);
        }
    }

    @Override public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level >= TRIM_MEMORY_RUNNING_LOW) MapActivity.clearProcessMapCache();
    }
}
