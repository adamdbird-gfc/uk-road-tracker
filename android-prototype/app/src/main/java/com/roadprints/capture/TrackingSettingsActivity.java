package com.roadprints.capture;

import android.os.Bundle;

/** Tracking controls presented as a focused utility screen. */
public class TrackingSettingsActivity extends MainActivity {
    @Override protected void onCreate(Bundle state) {
        getIntent().putExtra("tracking_settings_screen", true);
        super.onCreate(state);
    }
}
