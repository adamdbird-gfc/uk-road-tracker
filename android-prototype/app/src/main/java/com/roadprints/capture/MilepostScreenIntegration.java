package com.roadprints.capture;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.widget.LinearLayout;
import java.util.concurrent.atomic.AtomicBoolean;

/** Displays the last published ledger result while the existing background job reconciles changes. */
final class MilepostScreenIntegration {
    private static MilepostPresentation.Snapshot productionSnapshot;
    private final Activity host;
    private final MilepostIntegration gate;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final AtomicBoolean stopped=new AtomicBoolean(true);
    private LinearLayout container;
    private MilepostPresentationView view;
    private MilepostPresentation.Snapshot lastReady;
    private boolean loading;
    private int request,lifecycle;
    private java.util.List<Long> attempted;
    private java.util.List<Long> loaded;

    MilepostScreenIntegration(Activity host,MilepostIntegration gate){
        this.host=host;
        this.gate=gate;
        if(gate==MilepostIntegration.PRODUCTION)lastReady=productionSnapshot;
    }
    void mount(LinearLayout container){
        this.container=container;
        if(!gate.allowed(host))return;
        if(view==null)mountView();
        else if(view.getParent()!=container){
            if(view.getParent() instanceof android.view.ViewGroup)((android.view.ViewGroup)view.getParent()).removeView(view);
            container.addView(view);
        }
        if(!stopped.get())view.onHostResume();
    }
    void resume(){
        if(!gate.allowed(host))return;
        stopped.set(false);
        if(container!=null&&view==null)mountView();
        if(view!=null)view.onHostResume();
        if(loaded==null)attempted=null;
        refresh();
    }
    void refresh(){
        if(!gate.allowed(host)||stopped.get()||container==null)return;
        if(view!=null)view.updateAssessmentStatus();
        java.util.List<Long> revision=revision();
        if(loading||revision.equals(attempted))return;
        attempted=revision;
        loading=true;
        int token=++request;
        ScreenDataLoader.execute(()->{
            MilepostPresentation.Snapshot result=null;
            boolean failed=false;
            try(MilepostDiscoveryLedger ledger=new MilepostDiscoveryLedger(host,gate.database)){
                result=gate.readPublished(host,ledger,new MilepostPresentationStore(host,gate.user).read().acknowledgedPosts,stopped::get);
            }catch(java.util.concurrent.CancellationException interrupted){
                // An inactive screen will read the latest published result when reopened.
            }catch(Exception unavailable){
                failed=true;
                android.util.Log.w("Roadprints","Milepost screen unavailable",unavailable);
            }
            MilepostPresentation.Snapshot ready=result;
            boolean readFailed=failed;
            main.post(()->{
                if(token!=request||stopped.get()||host.isDestroyed()||host.isFinishing())return;
                loading=false;
                if(readFailed){if(view!=null)view.showAssessmentReadFailure();return;}
                if(!revision.equals(revision())||ready==null)return;
                loaded=revision;
                boolean changed=lastReady==null||!lastReady.receipt.equals(ready.receipt)||lastReady.current!=ready.current;
                lastReady=ready;
                if(gate==MilepostIntegration.PRODUCTION)productionSnapshot=ready;
                if(view==null)mountView();
                else if(changed){lifecycle++;view.update(ready);}
                else view.updateAssessmentStatus();
            });
        });
    }
    private java.util.List<Long> revision(){
        return java.util.Arrays.asList(MilepostReconciliationJob.resultRevision(),JourneyStore.dataRevision(host),
                NcnProgressStore.revision(host),JourneyStore.ncnDataRevision(host));
    }
    private boolean fresh(){return !stopped.get()&&revision().equals(loaded);}
    private void mountView(){
        MilepostPresentation.Snapshot initial=lastReady==null?MilepostPresentation.pending():lastReady;
        view=new MilepostPresentationView(host,new MilepostPresentationStore(host,gate.user),initial,this::loadPage,!stopped.get());
        view.freshness=this::fresh;
        container.addView(view);
    }
    private void loadPage(long after,java.util.function.Consumer<MilepostPresentationReader.Page> complete){
        int token=lifecycle;
        ScreenDataLoader.execute(()->{
            MilepostPresentationReader.Page page=new MilepostPresentationReader.Page(java.util.Collections.emptyList(),false,false);
            try(MilepostDiscoveryLedger ledger=new MilepostDiscoveryLedger(host,gate.database);
                MilepostReconciliationJob.CaptureGuard guard=new MilepostReconciliationJob.CaptureGuard(host,stopped,Long.MAX_VALUE)){
                if(gate.verified(ledger))page=MilepostPresentationReader.page(host,ledger,gate.user,after,guard);
            }catch(Exception unavailable){android.util.Log.w("Roadprints","Milepost history unavailable",unavailable);}
            MilepostPresentationReader.Page delivered=page;
            main.post(()->{if(token==lifecycle&&fresh()&&!CaptureService.isActive(host))complete.accept(delivered);});
        });
    }
    void pause(){
        if(!gate.enabled)return;
        stopped.set(true);
        request++;
        lifecycle++;
        loading=false;
        if(!java.util.Objects.equals(loaded,attempted))attempted=null;
        if(view!=null){
            view.onHostPause();
            if(view.getParent() instanceof android.view.ViewGroup)((android.view.ViewGroup)view.getParent()).removeView(view);
            view=null;
        }
    }
}
