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
    private boolean leased,loading;
    private int request,lifecycle;
    private long archive,ncn,ncnEvidence;
    private String receipt="";
    MilepostScreenIntegration(Activity host,MilepostIntegration gate){this.host=host;this.gate=gate;}
    void mount(LinearLayout container){
        this.container=container;
        if(view!=null){if(view.getParent() instanceof android.view.ViewGroup)((android.view.ViewGroup)view.getParent()).removeView(view);container.addView(view);view.onHostResume();}
    }
    void resume(){if(!gate.enabled)return;stopped.set(false);refresh();}
    void refresh(){
        if(!gate.enabled||stopped.get()||container==null||loading)return;
        if(!leased){leased=gate.enterPresentation();if(!leased)return;}
        if(view!=null&&!fresh())clearView();
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
                if(ready==null||a!=JourneyStore.dataRevision(host)||n!=NcnProgressStore.revision(host)||e!=JourneyStore.ncnDataRevision(host)||!NcnProgressStore.enabled(host)||CaptureService.isActive(host)){clearView();return;}
                archive=a;ncn=n;ncnEvidence=e;
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
    private boolean fresh(){return gate.enabled&&!stopped.get()&&archive==JourneyStore.dataRevision(host)&&ncn==NcnProgressStore.revision(host)&&ncnEvidence==JourneyStore.ncnDataRevision(host)&&NcnProgressStore.enabled(host);}
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
        // The serial screen worker releases the lease after any cancelled read/page has exited.
        if(leased){leased=false;ScreenDataLoader.execute(gate::leavePresentation);}
    }
    private void clearView(){lifecycle++;if(view!=null){view.onHostPause();if(view.getParent() instanceof android.view.ViewGroup)((android.view.ViewGroup)view.getParent()).removeView(view);view=null;}receipt="";}
}
