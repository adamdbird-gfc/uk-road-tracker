package com.roadprints.capture;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.widget.LinearLayout;
import java.util.concurrent.atomic.AtomicBoolean;

/** Existing Achievements screen section; no Activity, navigation stack or award store of its own. */
final class MilepostScreenIntegration {
    private final Activity host;
    private final MilepostIntegration gate;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final AtomicBoolean stopped=new AtomicBoolean(true);
    private LinearLayout container;
    private MilepostPresentationView view;
    private boolean loading;
    private int request,lifecycle;
    private long archive,ncn,ncnEvidence;
    private String receipt="";
    private MilepostPresentation.Snapshot lastReady;
    MilepostScreenIntegration(Activity host,MilepostIntegration gate){this.host=host;this.gate=gate;}
    void mount(LinearLayout container){
        this.container=container;
        if(view==null&&gate.allowed(host))showPending();
        if(view!=null){if(view.getParent() instanceof android.view.ViewGroup)((android.view.ViewGroup)view.getParent()).removeView(view);container.addView(view);view.onHostResume();}
    }
    void resume(){if(!gate.allowed(host))return;stopped.set(false);refresh();}
    void refresh(){
        if(!gate.allowed(host)||stopped.get()||container==null||loading)return;
        if(view!=null&&!receipt.equals("assessment-pending")&&!fresh()){clearView();showPending();}
        if(view==null)showPending();
        loading=true;int token=++request;
        ScreenDataLoader.execute(()->{
            MilepostPresentation.Snapshot result=null;long a=JourneyStore.dataRevision(host),n=NcnProgressStore.revision(host),e=JourneyStore.ncnDataRevision(host);
            try(MilepostDiscoveryLedger ledger=new MilepostDiscoveryLedger(host,gate.database);
                MilepostReconciliationJob.CaptureGuard guard=new MilepostReconciliationJob.CaptureGuard(host,stopped,Long.MAX_VALUE)){
                result=gate.read(host,ledger,new MilepostPresentationStore(host,gate.user).read().acknowledgedPosts,guard);
            }catch(Exception unavailable){android.util.Log.w("Roadprints","Milepost screen unavailable",unavailable);}
            MilepostPresentation.Snapshot ready=result;
            main.post(()->{
                if(token!=request||stopped.get()||host.isDestroyed()||host.isFinishing())return;
                loading=false;
                if(ready==null||a!=JourneyStore.dataRevision(host)||n!=NcnProgressStore.revision(host)||e!=JourneyStore.ncnDataRevision(host)||CaptureService.isActive(host)){if(!receipt.equals("assessment-pending")){clearView();showPending();}return;}
                archive=a;ncn=n;ncnEvidence=e;lastReady=ready;
                try{
                    if(view==null){
                        view=new MilepostPresentationView(host,new MilepostPresentationStore(host,gate.user),ready,(after,complete)->loadPage(after,complete));
                        view.freshness=this::fresh;container.addView(view);view.onHostResume();
                    }else if(!receipt.equals(ready.receipt)){lifecycle++;view.update(ready);}
                    receipt=ready.receipt;
                }catch(Exception unavailable){clearView();android.util.Log.w("Roadprints","Milepost presentation state unavailable",unavailable);}
            });
        });
    }
    private void showPending(){
        if(container==null||!gate.allowed(host))return;
        // Re-entry may reuse only the compact verified receipt with identical evidence revisions.
        // Avoid constructing the entire locked view immediately before replacing it with the same earned view.
        boolean reuse=lastReady!=null&&archive==JourneyStore.dataRevision(host)&&ncn==NcnProgressStore.revision(host)
            &&ncnEvidence==JourneyStore.ncnDataRevision(host)&&!CaptureService.isActive(host);
        MilepostPresentation.Snapshot initial=reuse?lastReady:MilepostPresentation.pending();
        view=new MilepostPresentationView(host,new MilepostPresentationStore(host,gate.user),initial,this::loadPage);
        view.freshness=this::fresh;receipt=initial.receipt;container.addView(view);
        if(!stopped.get())view.onHostResume();
    }
    private boolean fresh(){return gate.enabled&&!stopped.get()&&archive==JourneyStore.dataRevision(host)&&ncn==NcnProgressStore.revision(host)&&ncnEvidence==JourneyStore.ncnDataRevision(host);}
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
        stopped.set(true);request++;loading=false;clearView();
    }
    private void clearView(){lifecycle++;if(view!=null){view.onHostPause();if(view.getParent() instanceof android.view.ViewGroup)((android.view.ViewGroup)view.getParent()).removeView(view);view=null;}receipt="";}
}
