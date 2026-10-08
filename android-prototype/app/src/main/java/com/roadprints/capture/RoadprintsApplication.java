package com.roadprints.capture;

import android.app.Application;

/** Installs the local crash recorder before any Roadprints screen is created. */
public class RoadprintsApplication extends Application {
    @Override public void onCreate() {
        super.onCreate();
        BetaMeasurement.install(this);
        CrashReporter.install(this);
    }

    @Override public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level >= TRIM_MEMORY_RUNNING_LOW) MapActivity.clearProcessMapCache();
    }
}
