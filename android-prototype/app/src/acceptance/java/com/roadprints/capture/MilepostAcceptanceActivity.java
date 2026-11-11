package com.roadprints.capture;

import android.app.Activity;
import android.content.Intent;
import android.os.*;
import android.widget.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Non-exported acceptance-only host. No Intent extras, imported state or production override. */
public final class MilepostAcceptanceActivity extends Activity {
    LinearLayout section;
    MilepostScreenIntegration screen;
    MilepostAcceptanceFixture fixture;
    MilepostRuntimeProbe probe;
    TextView status;
    private final AtomicBoolean stopped=new AtomicBoolean(true);
    private boolean resumed;
    private long entryAt;
    private int generation;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);setContentView(root);
        status=new TextView(this);status.setText("Roadprints Acceptance · Sample discoveries");root.addView(status);
        LinearLayout controls=new LinearLayout(this);controls.setOrientation(LinearLayout.VERTICAL);
        ScrollView setupScroll=new ScrollView(this);setupScroll.addView(controls);root.addView(setupScroll,new LinearLayout.LayoutParams(-1,Math.round(250*getResources().getDisplayMetrics().density)));
        button(controls,"Open live map and Locate Me",()->startActivity(new Intent(this,MapActivity.class)
                .putExtra("collection_id",CollectionAccessStore.NCN)));
        button(controls,"Undiscovered Milepost dialog",()->sampleDialog(false));
        button(controls,"Discovered Milepost dialog",()->sampleDialog(true));
        button(controls,"Load three sample chapters",()->seed(3));
        button(controls,"Load seven sample chapters",()->seed(7));
        button(controls,"Seed one synthetic post",()->seed(1));
        button(controls,"Seed 1,605 synthetic journeys",()->runWork(true,7,1605));
        button(controls,"Resume reconciliation",()->runWork(false,0));
        button(controls,"Normal sandbox navigation / capture",()->startActivity(new Intent(this,MainActivity.class)));
        button(controls,"Request GC observation (not guaranteed)",()->{System.gc();if(probe!=null)probe.mark("gc_requested",0);});
        section=new LinearLayout(this);section.setOrientation(LinearLayout.VERTICAL);root.addView(section,new LinearLayout.LayoutParams(-1,0,1));
    }
    private void sampleDialog(boolean discovered){
        MilepostPresentationReader.Discovery sample=discovered?new MilepostPresentationReader.Discovery(
                "synthetic:dialog","2026-11-11",1,1,1<<1):null;
        MilepostDetailsDialog.show(this,sample,"Millennium Milepost Holes Bay Roundabout",
                "rowe",50.7315787,-1.990668);
    }
    void button(LinearLayout parent,String text,Runnable action){Button b=new Button(this);b.setText(text);b.setMinHeight(Math.round(48*getResources().getDisplayMetrics().density));b.setOnClickListener(v->action.run());parent.addView(b);}
    void seed(int posts){runWork(true,posts);}
    void runWork(boolean seed,int posts){runWork(seed,posts,2);}
    void runWork(boolean seed,int posts,int archiveCount){
        int token=++generation;
        if(screen!=null)screen.pause();
        status.setText(seed?"Loading sample journeys…":"Roadprints Acceptance · Sample discoveries");
        ScreenDataLoader.execute(()->{
            long start=SystemClock.elapsedRealtime();
            try{
                if(fixture==null)fixture=new MilepostAcceptanceFixture(this);
                if(seed){fixture.prepare(posts);fixture.populateArchive(archiveCount);}else fixture.restore();
                if(fixture.gate==null){runOnUiThread(()->{if(resumed&&token==generation)status.setText("Roadprints Acceptance · Open a sample dialog, load chapters or test the live map.");});return;}
                // Same cooperative two-second deadline as the real job; repeat via explicit Resume.
                boolean retry=fixture.process(stopped,SystemClock.elapsedRealtime()+2000);
                long ms=SystemClock.elapsedRealtime()-start;
                PerformanceDiagnostics.timing("synthetic_milepost_slice",ms,"retry="+retry+" archive_setup="+seed);
                runOnUiThread(()->{if(!resumed||token!=generation)return;if(probe!=null)probe.mark("background_slice_ms",ms);
                    status.setText(retry?"Sample loading paused · tap Resume reconciliation":"Roadprints Acceptance · Sample discoveries ready");
                    screen=new MilepostScreenIntegration(this,fixture.gate);screen.mount(section);screen.resume();awaitView(token);});
            }catch(Exception e){runOnUiThread(()->{if(resumed&&token==generation)status.setText("Stopped / unavailable: "+e.getClass().getSimpleName());});}
        });
    }
    private void awaitView(int token){
        if(!resumed||screen==null||token!=generation)return;
        if(section.getChildCount()>0){if(probe!=null)probe.mark("entry_to_view_ms",SystemClock.elapsedRealtime()-entryAt);return;}
        section.postDelayed(()->{if(resumed&&token==generation){screen.refresh();awaitView(token);}},100);
    }
    @Override protected void onResume(){super.onResume();resumed=true;stopped.set(false);entryAt=SystemClock.elapsedRealtime();probe=new MilepostRuntimeProbe(this);runWork(false,0);}
    @Override protected void onPause(){generation++;resumed=false;stopped.set(true);if(screen!=null)screen.pause();if(probe!=null){probe.close();probe=null;}super.onPause();}
    @Override protected void onDestroy(){ScreenDataLoader.execute(()->{if(fixture!=null){fixture.close();fixture=null;}});super.onDestroy();}
}
