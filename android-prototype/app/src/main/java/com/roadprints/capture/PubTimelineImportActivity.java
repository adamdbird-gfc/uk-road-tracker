package com.roadprints.capture;

import android.os.Bundle;
import android.view.ViewGroup;

/** Floating, styled place-only Timeline importer for the pub progress card. */
public class PubTimelineImportActivity extends TimelineImportActivity {
    @Override protected void onCreate(Bundle state){
        getIntent().putExtra("pub_visits_only",true).putExtra("service_only",true);
        super.onCreate(state);
        if(getWindow()!=null)getWindow().setLayout(
                Math.min(Math.round(560*getResources().getDisplayMetrics().density),getResources().getDisplayMetrics().widthPixels-Math.round(32*getResources().getDisplayMetrics().density)),
                Math.round(getResources().getDisplayMetrics().heightPixels*.8f));
    }
}
