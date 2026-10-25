package com.roadprints.capture;
import android.app.Activity;
import android.view.View;
import android.view.ViewTreeObserver;

/** Navigation-to-draw milestones, separate from data preparation and skeleton containers. */
final class NavigationTrace {
    static final String EXTRA="roadprints_navigation_started";
    private final String screen;private long started;private String mode="cold";private boolean resumed;
    private final java.util.Set<String> drawn=new java.util.HashSet<>();
    private static final java.util.Set<String> seen=new java.util.HashSet<>();
    NavigationTrace(String screen){this.screen=screen;started=android.os.SystemClock.elapsedRealtime();}
    void resume(Activity a){
        boolean navigation=a.getIntent().hasExtra(EXTRA);
        started=a.getIntent().getLongExtra(EXTRA,resumed?android.os.SystemClock.elapsedRealtime():started);a.getIntent().removeExtra(EXTRA);
        mode=resumed?(navigation?"warm":"back"):seen.contains(screen)?"warm":"cold";
        resumed=true;seen.add(screen);drawn.clear();event("open","");
    }
    void event(String phase,String counts){PerformanceDiagnostics.timing("nav_"+screen+"_"+phase,android.os.SystemClock.elapsedRealtime()-started,"load="+mode+" "+counts);}
    void frame(View view,String phase,String counts){
        if(view==null||!drawn.add(phase))return;
        final long request=started;
        ViewTreeObserver.OnDrawListener listener=new ViewTreeObserver.OnDrawListener(){boolean done;
            public void onDraw(){if(done)return;done=true;if(request==started)event(phase,counts);view.post(()->{if(view.getViewTreeObserver().isAlive())view.getViewTreeObserver().removeOnDrawListener(this);});}};
        view.getViewTreeObserver().addOnDrawListener(listener);view.invalidate();
    }
}
