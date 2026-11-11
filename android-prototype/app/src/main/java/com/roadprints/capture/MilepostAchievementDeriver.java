package com.roadprints.capture;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import org.json.JSONObject;
import java.util.ConcurrentModificationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

/** Derives awards from the reconciled archive and a fresh NCN result when available. */
final class MilepostAchievementDeriver implements AutoCloseable {
    final Context context;
    final MilepostDiscoveryLedger ledger;
    final String user;
    final MilepostReconciler reconciler;
    final MilepostReconciliationJob.CaptureGuard capture;
    private long archive=-1, reconciledGeneration=-1;
    private boolean reconciled;
    private final BooleanSupplier referenceApproved;
    MilepostAchievementDeriver(Context context,MilepostDiscoveryLedger ledger,String user) {
        this(context,ledger,user,()->true);
    }
    MilepostAchievementDeriver(Context context,MilepostDiscoveryLedger ledger,String user,BooleanSupplier referenceApproved) {
        this.referenceApproved=referenceApproved;
        this.context=context.getApplicationContext();this.ledger=ledger;this.user=user;
        ledger.db(); // Reject UI thread before registering anything.
        reconciler=new MilepostReconciler(context,ledger,user);
        capture=new MilepostReconciliationJob.CaptureGuard(context,new AtomicBoolean(),Long.MAX_VALUE);
    }
    private void start() {
        archive=JourneyStore.dataRevision(context);reconciled=false;
        String pass=MilepostArchiveReader.REVISION+":"+archive;
        if(!pass.equals(ledger.scanCursor(user,"pass"))) {
            ledger.scanCursor(user,"live","");ledger.scanCursor(user,"known","");
            ledger.scanCursor(user,"pass",pass);
        }
    }
    /** True while another bounded archive slice is needed. */
    boolean runSlice(BooleanSupplier cancelled,long recognisedAt)throws Exception {
        if(recognisedAt<0)throw new IllegalArgumentException("Invalid recognition time");
        ledger.db();android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
        BooleanSupplier yield=()->capture.getAsBoolean()||cancelled.getAsBoolean();
        MilepostArchiveReader.check(yield);
        if(archive<0||archive!=JourneyStore.dataRevision(context)
                ||(reconciled&&reconciledGeneration!=MilepostAchievements.generation(ledger.db())))start();
        if(!reconciled) {
            if(reconciler.runSlice(yield,recognisedAt))return true;
            if(archive!=JourneyStore.dataRevision(context)){start();return true;}
            reconciled=true;reconciledGeneration=MilepostAchievements.generation(ledger.db());
        }
        NcnProgressStore.Snapshot ncn;long ncnRevision;
        synchronized(NcnProgressStore.class) {
            ncn=freshNcn(context,archive);ncnRevision=NcnProgressStore.revision(context);
        }
        String ncnToken=token(ncn,ncnRevision);
        try {
        JSONObject route=ncn==null?previousRouteAward(ledger.db(),user):NcnAchievements.firstCompletedRoute(ncn);
        MilepostAchievements.Prepared prepared=MilepostAchievements.prepare(ledger,user,route,yield);
        MilepostArchiveReader.check(yield);
        SQLiteDatabase d=ledger.db();d.beginTransaction();boolean[] ended={false};
        try {
            // Same order as Stage 4: writer before archive monitor. NCN publication
            // is excluded for this short seven-row commit; no geometry under locks.
            synchronized(NcnProgressStore.class) {
                synchronized(JourneyStore.class) {
                    if(archive!=JourneyStore.dataRevision(context)||ncn!=freshNcn(context,archive)
                            ||ncnRevision!=NcnProgressStore.revision(context))
                        throw new ConcurrentModificationException("Archive/NCN changed before achievement commit");
                    // The integration's reviewed reference pin is checked under the same writer
                    // transaction as publication, rather than querying SQLite at every GPS poll.
                    if(!referenceApproved.getAsBoolean())throw new ConcurrentModificationException("Unapproved integration reference");
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
    private static JSONObject previousRouteAward(SQLiteDatabase d,String user)throws Exception {
        String support=MilepostAchievements.scalar(d,"SELECT support FROM achievement_state WHERE user=? AND id=? AND earned=1",
                user,MilepostAchievements.IDS[4]);
        return support==null?null:new org.json.JSONArray(support).optJSONObject(0);
    }
    private static NcnProgressStore.Snapshot freshNcn(Context c,long archive) {
        NcnProgressStore.Snapshot ncn=NcnProgressStore.current();
        return NcnProgressStore.enabled(c)&&ncn!=null&&ncn.archiveRevision==archive
                &&ncn.evidenceRevision==JourneyStore.ncnDataRevision(c)?ncn:null;
    }
    private static String token(NcnProgressStore.Snapshot ncn,long revision) {
        return MilepostArchiveReader.REVISION+":"+(ncn==null?"pending":ncn.catalogue.version+":"+ncn.archiveRevision+":"+ncn.evidenceRevision+":"+revision);
    }
    static JSONObject state(Context context,MilepostDiscoveryLedger ledger,String user)throws Exception {
        SQLiteDatabase d=ledger.db();d.beginTransaction();
        try {
        JSONObject result=MilepostAchievements.state(ledger,user);
        synchronized(NcnProgressStore.class) {
            synchronized(JourneyStore.class) {
                long revision=JourneyStore.dataRevision(context);
                NcnProgressStore.Snapshot n=freshNcn(context,revision);
                String pin=token(n,NcnProgressStore.revision(context));
                boolean current=MilepostAchievements.number(d,"SELECT COUNT(*) FROM achievement_snapshot WHERE user=? AND archive_revision=? AND ledger_generation=? AND (ncn_token=? OR (?='pending' AND ncn_token LIKE ?))",user,
                            Long.toString(revision),Long.toString(MilepostAchievements.generation(d)),pin,n==null?"pending":"ready",MilepostArchiveReader.REVISION+":%")==1;
                return result.put("current",current).put("status",current?"complete":"updating");
            }
        }
        }finally {d.endTransaction();}
    }
    @Override public void close(){capture.close();}
}
