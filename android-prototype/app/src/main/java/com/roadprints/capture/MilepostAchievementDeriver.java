package com.roadprints.capture;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import org.json.JSONObject;
import java.util.ConcurrentModificationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

/** Explicit inactive worker only. No job/lifecycle/UI caller. A new instance begins a full
 * metadata-only archive pass; interrupted proof pages remain durable. Geometry is loaded
 * only by Stage 4 for changed sources. Never trusts a previous worker's partial scan. */
final class MilepostAchievementDeriver implements AutoCloseable {
    final Context context;
    final MilepostDiscoveryLedger ledger;
    final String user;
    final MilepostReconciler reconciler;
    final MilepostReconciliationJob.CaptureGuard capture;
    private long archive=-1;
    private boolean reconciled;
    MilepostAchievementDeriver(Context context,MilepostDiscoveryLedger ledger,String user) {
        this.context=context.getApplicationContext();this.ledger=ledger;this.user=user;
        ledger.db(); // Reject UI thread before registering anything.
        reconciler=new MilepostReconciler(context,ledger,user);
        capture=new MilepostReconciliationJob.CaptureGuard(context,new AtomicBoolean(),Long.MAX_VALUE);
    }
    private void start() {
        archive=JourneyStore.dataRevision(context);reconciled=false;
        ledger.scanCursor(user,"live","");ledger.scanCursor(user,"known","");
    }
    /** true: another bounded slice needed; false: committed or waiting for fresh NCN.
     * Incomplete/stale inputs retain the previous complete seven-row result. */
    boolean runSlice(BooleanSupplier cancelled,long recognisedAt)throws Exception {
        if(recognisedAt<0)throw new IllegalArgumentException("Invalid recognition time");
        ledger.db();android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
        BooleanSupplier yield=()->capture.getAsBoolean()||cancelled.getAsBoolean();
        MilepostArchiveReader.check(yield);
        if(archive<0||archive!=JourneyStore.dataRevision(context))start();
        if(!reconciled) {
            if(reconciler.runSlice(yield,recognisedAt))return true;
            if(archive!=JourneyStore.dataRevision(context)){start();return true;}
            reconciled=true;
        }
        NcnProgressStore.Snapshot ncn;long ncnRevision;
        synchronized(NcnProgressStore.class) {
            ncn=NcnProgressStore.current();ncnRevision=NcnProgressStore.revision(context);
            if(!freshNcn(context,ncn,archive))return false;
        }
        String ncnToken=token(ncn,ncnRevision);
        try {
        MilepostAchievements.Prepared prepared=MilepostAchievements.prepare(ledger,user,NcnAchievements.firstCompletedRoute(ncn),yield);
        MilepostArchiveReader.check(yield);
        SQLiteDatabase d=ledger.db();d.beginTransaction();boolean[] ended={false};
        try {
            // Same order as Stage 4: writer before archive monitor. NCN publication
            // is excluded for this short seven-row commit; no geometry under locks.
            synchronized(NcnProgressStore.class) {
                synchronized(JourneyStore.class) {
                    if(archive!=JourneyStore.dataRevision(context)||ncn!=NcnProgressStore.current()
                            ||ncnRevision!=NcnProgressStore.revision(context)||!freshNcn(context,ncn,archive))
                        throw new ConcurrentModificationException("Archive/NCN changed before achievement commit");
                    MilepostAchievements.publish(d,user,prepared,archive,ncnToken,recognisedAt,yield);
                    try {d.setTransactionSuccessful();} finally {ended[0]=true;d.endTransaction();}
                }
            }
        }finally {if(!ended[0])d.endTransaction();}
        // Reusing a coordinator still performs a full freshness pass before every commit.
        archive=-1;reconciled=false;
        return false;
        }catch(Exception failure){archive=-1;reconciled=false;throw failure;}
    }
    private static boolean freshNcn(Context c,NcnProgressStore.Snapshot ncn,long archive) {
        return NcnProgressStore.enabled(c)&&ncn!=null&&ncn.archiveRevision==archive
                &&ncn.evidenceRevision==JourneyStore.ncnDataRevision(c);
    }
    private static String token(NcnProgressStore.Snapshot ncn,long revision) {
        return ncn.catalogue.version+":"+ncn.archiveRevision+":"+ncn.evidenceRevision+":"+revision;
    }
    static JSONObject state(Context context,MilepostDiscoveryLedger ledger,String user)throws Exception {
        SQLiteDatabase d=ledger.db();d.beginTransaction();
        try {
        JSONObject result=MilepostAchievements.state(ledger,user);
        synchronized(NcnProgressStore.class) {
            synchronized(JourneyStore.class) {
                NcnProgressStore.Snapshot n=NcnProgressStore.current();long revision=JourneyStore.dataRevision(context);
                boolean current=freshNcn(context,n,revision)
                        &&MilepostAchievements.number(d,"SELECT COUNT(*) FROM achievement_snapshot WHERE user=? AND archive_revision=? AND ledger_generation=? AND ncn_token=?",user,
                            Long.toString(revision),Long.toString(MilepostAchievements.generation(d)),n==null?"":token(n,NcnProgressStore.revision(context)))==1;
                return result.put("current",current).put("status",current?"complete":"updating");
            }
        }
        }finally {d.endTransaction();}
    }
    @Override public void close(){capture.close();}
}
