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
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);setContentView(root);
        status=new TextView(this);status.setText("SYNTHETIC acceptance sandbox — production gate remains disabled");root.addView(status);
        LinearLayout controls=new LinearLayout(this);controls.setOrientation(LinearLayout.VERTICAL);root.addView(controls);
        button(controls,"Seed one synthetic post",()->seed(1));
        button(controls,"Seed seven synthetic posts",()->seed(7));
        button(controls,"Resume reconciliation",()->runWork(false,0));
        button(controls,"Normal sandbox navigation / capture",()->startActivity(new Intent(this,MainActivity.class)));
        button(controls,"Request GC observation (not guaranteed)",()->{System.gc();if(probe!=null)probe.mark("gc_requested",0);});
        section=new LinearLayout(this);section.setOrientation(LinearLayout.VERTICAL);root.addView(section,new LinearLayout.LayoutParams(-1,0,1));
    }
    void button(LinearLayout parent,String text,Runnable action){Button b=new Button(this);b.setText(text);b.setMinHeight(Math.round(48*getResources().getDisplayMetrics().density));b.setOnClickListener(v->action.run());parent.addView(b);}
    void seed(int posts){runWork(true,posts);}
    void runWork(boolean seed,int posts){
        if(screen!=null)screen.pause();
        status.setText("Processing isolated synthetic fixtures…");
        ScreenDataLoader.execute(()->{
            long start=SystemClock.elapsedRealtime();
            try{
                if(fixture==null)fixture=new MilepostAcceptanceFixture(this);
                if(seed)fixture.prepare(posts);else fixture.restore();
                if(fixture.gate==null){runOnUiThread(()->status.setText("Seed fixtures to begin"));return;}
                // Same cooperative two-second deadline as the real job; repeat via explicit Resume.
                boolean retry=fixture.process(stopped,SystemClock.elapsedRealtime()+2000);
                long ms=SystemClock.elapsedRealtime()-start;
                runOnUiThread(()->{if(!resumed)return;if(probe!=null)probe.mark("background_slice_ms",ms);
                    status.setText(retry?"Checkpoint saved; Resume reconciliation":"Synthetic receipt ready");
                    screen=new MilepostScreenIntegration(this,fixture.gate);screen.mount(section);screen.resume();awaitView();});
            }catch(Exception e){runOnUiThread(()->{if(resumed)status.setText("Stopped / unavailable: "+e.getClass().getSimpleName());});}
        });
    }
    private void awaitView(){
        if(!resumed||screen==null)return;
        if(section.getChildCount()>0){if(probe!=null)probe.mark("entry_to_view_ms",SystemClock.elapsedRealtime()-entryAt);return;}
        section.postDelayed(()->{if(resumed){screen.refresh();awaitView();}},100);
    }
    @Override protected void onResume(){super.onResume();resumed=true;stopped.set(false);entryAt=SystemClock.elapsedRealtime();probe=new MilepostRuntimeProbe(this);runWork(false,0);}
    @Override protected void onPause(){resumed=false;stopped.set(true);if(screen!=null)screen.pause();if(probe!=null){probe.close();probe=null;}super.onPause();}
    @Override protected void onDestroy(){ScreenDataLoader.execute(()->{if(fixture!=null){fixture.close();fixture=null;}});super.onDestroy();}
}
