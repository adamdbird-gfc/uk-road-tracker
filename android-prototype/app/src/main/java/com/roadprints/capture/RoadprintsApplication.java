package com.roadprints.capture;

import android.app.Application;

/** Installs the local crash recorder before any Roadprints screen is created. */
public class RoadprintsApplication extends Application {
    @Override public void onCreate() {
        super.onCreate();
        CrashReporter.install(this);
    }
}
