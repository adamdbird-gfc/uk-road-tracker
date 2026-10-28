package com.roadprints.capture;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/** Compatibility route for saved links from the initial pub-visits screen. */
public class PubVisitsActivity extends Activity {
    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        Intent progress=new Intent(this,ProgressActivity.class).putExtra("progress_section","wetherspoons");
        String id=getIntent().getStringExtra("pub_id");
        if(id!=null)progress.putExtra("pub_checkin_id",id);
        startActivity(progress);finish();
    }
}
