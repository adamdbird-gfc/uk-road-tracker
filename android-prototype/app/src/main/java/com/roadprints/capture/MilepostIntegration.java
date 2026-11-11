package com.roadprints.capture;

import android.content.Context;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

/** The sole application feature boundary. No preference, Intent, backup or BuildConfig override.
 * Enabling production requires a reviewed source change and an independently approved pin.
 * Tests construct a separate instance by reflection; they never replace PRODUCTION. */
final class MilepostIntegration {
    static final MilepostIntegration PRODUCTION=new MilepostIntegration(MilepostCatalogue.READY,
        MilepostReconciliationJob.DATABASE,MilepostReconciliationJob.USER,MilepostCatalogue.REVISION,
        MilepostCatalogue.SHA,MilepostCatalogue.fingerprint(),MilepostDiscoveryLedger.EVALUATOR);
    final boolean enabled;
    final String database,user;
    private final int revision;
    private final String sha,fingerprint,evaluator;
    private boolean processing;
    private MilepostIntegration(boolean enabled,String database,String user,int revision,String sha,String fingerprint,String evaluator){
        this.enabled=enabled;this.database=database;this.user=user;this.revision=revision;this.sha=sha;this.fingerprint=fingerprint;this.evaluator=evaluator;
    }
    synchronized boolean enterProcessing(){if(!enabled||processing)return false;processing=true;return true;}
    synchronized void leaveProcessing(){processing=false;}
    /** Worker only. The reviewed envelope includes physical eligibility, attribution and aliases.
     * Existing ledger install is the only writer; candidates cannot be promoted by this boundary. */
    boolean verified(MilepostDiscoveryLedger ledger){
        if(!enabled||!MilepostDiscoveryLedger.EVALUATOR.equals(evaluator)||revision<1||!sha.matches("[0-9a-f]{64}")||!fingerprint.matches("[0-9a-f]{64}"))return false;
        return MilepostAchievements.number(ledger.db(),
            "SELECT COUNT(*) FROM catalogue WHERE singleton=1 AND revision=? AND sha=? AND fingerprint=?",
            Integer.toString(revision),sha,fingerprint)==1;
    }
    boolean allowed(Context context){return enabled&&(this!=PRODUCTION||"com.roadprints.capture".equals(context.getPackageName()));}
    boolean process(Context context,AtomicBoolean stopped,long deadline)throws Exception {
        if(!allowed(context))return false;
        if(!enterProcessing())return enabled;
        try(MilepostDiscoveryLedger ledger=new MilepostDiscoveryLedger(context,database);
            MilepostReconciliationJob.CaptureGuard guard=new MilepostReconciliationJob.CaptureGuard(context,stopped,deadline)){
            MilepostArchiveReader.check(guard);
            if(this==PRODUCTION&&!verified(ledger))MilepostCatalogue.install(context,ledger);
            if(!verified(ledger)){
                if(this==PRODUCTION)throw new java.io.IOException(MilepostCatalogue.BLOCKER);
                return false;
            }
            if(MilepostAchievementDeriver.state(context,ledger,user).getBoolean("current"))return false;
            JourneyStore.prepareArchive(context);
            try(MilepostAchievementDeriver worker=new MilepostAchievementDeriver(context,ledger,user,()->verified(ledger))){
                BooleanSupplier yield=()->guard.getAsBoolean()||!enabled;
                while(worker.runSlice(yield,System.currentTimeMillis())){
                    MilepostArchiveReader.check(yield);
                    if(!verified(ledger))throw new java.io.IOException(MilepostCatalogue.BLOCKER);
                }
                return !MilepostAchievementDeriver.state(context,ledger,user).getBoolean("current");
            }
        }finally{leaveProcessing();}
    }
    MilepostPresentation.Snapshot read(Context context,MilepostDiscoveryLedger ledger,long acknowledged,BooleanSupplier cancelled)throws Exception {
        if(!allowed(context)||!verified(ledger))return null;
        MilepostPresentation.Snapshot snapshot=MilepostPresentationReader.read(context,ledger,user,acknowledged,cancelled);
        return snapshot.current&&verified(ledger)?snapshot:null;
    }
}
