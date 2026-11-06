package com.roadprints.capture;

import android.content.Context;
import java.io.File;
import java.nio.file.*;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Inactive, explicit background slices over the existing archive and derived ledger.
 * No writes to source files, awards, NCN, export/import or backend. */
final class MilepostReconciler {
    static final int SCAN_PAGE=32, WORK_PAGES=4;
    final Context context;
    final MilepostDiscoveryLedger ledger;
    final String user;
    int readSources,processedPages;
    long maxCommitNanos;
    MilepostReconciler(Context context,MilepostDiscoveryLedger ledger,String user) {
        this.context=context.getApplicationContext();this.ledger=ledger;this.user=user;
    }
    /** A bounded keyset over directory names: O(page) retained names, no all-files array.
     * Atomic replacements share the JourneyStore monitor; reading/decoding occurs outside it.
     * Explicit invocations do not migrate legacy preference journeys into files. */
    private List<String> filesAfter(String after,BooleanSupplier cancelled)throws Exception {
        TreeSet<String> page=new TreeSet<>();
        try(DirectoryStream<Path> directory=Files.newDirectoryStream(context.getFilesDir().toPath(),"journey_*.json")) {
            for(Path path:directory) {
                MilepostArchiveReader.check(cancelled);
                String name=path.getFileName().toString();
                String id=name.substring(8,name.length()-5);
                if(id.compareTo(after)<=0||id.length()>512||id.isEmpty()||!Files.isRegularFile(path))continue;
                page.add(id);if(page.size()>SCAN_PAGE)page.pollLast();
            }
        }
        return new ArrayList<>(page);
    }
    private void sync(String id,BooleanSupplier cancelled)throws Exception {
        MilepostArchiveReader.check(cancelled);
        String stamp=MilepostArchiveReader.token(context,id);
        if(stamp.equals(ledger.archiveStamp(user,id)))return;
        if("deleted".equals(stamp)) {
            ledger.archiveChanged(user,id,"deleted","deleted",true,(journey,transaction)->commit(journey,"deleted",cancelled,transaction));
            return;
        }
        MilepostArchiveReader.Snapshot snapshot=MilepostArchiveReader.read(context,id,cancelled);readSources++;
        ledger.archiveChanged(user,id,snapshot.stamp,snapshot.evidence.token(),false,
                (journey,transaction)->commit(journey,snapshot.stamp,cancelled,transaction));
    }
    private void commit(String id,String stamp,BooleanSupplier cancelled,Runnable transaction) {
        long start=System.nanoTime();
        synchronized(JourneyStore.class) {
            MilepostArchiveReader.check(cancelled);
            if(!stamp.equals(MilepostArchiveReader.token(context,id)))throw new ConcurrentModificationException("Archive changed before commit");
            transaction.run();
        }
        maxCommitNanos=Math.max(maxCommitNanos,System.nanoTime()-start);
    }
    /** Returns whether another slice is needed. Caller owns time/capture cancellation.
     * Cursor advances only after a durable feed update; a cancelled item is retried.
     * At most 32 live records, 32 tombstone checks and 4 x 16 exact assessments per slice. */
    boolean runSlice(BooleanSupplier cancelled,long recognisedAt)throws Exception {
        MilepostArchiveReader.check(cancelled);
        List<String> live=filesAfter(ledger.scanCursor(user,"live"),cancelled);
        for(String id:live) {
            try{sync(id,cancelled);}catch(java.io.FileNotFoundException|ConcurrentModificationException raced){return true;}
            ledger.scanCursor(user,"live",id);
        }
        if(live.size()<SCAN_PAGE)ledger.scanCursor(user,"live","");
        List<MilepostDiscoveryLedger.ArchiveSource> known=ledger.archivePage(user,ledger.scanCursor(user,"known"),SCAN_PAGE);
        for(MilepostDiscoveryLedger.ArchiveSource source:known) {
            try{sync(source.id,cancelled);}catch(java.io.FileNotFoundException|ConcurrentModificationException raced){return true;}
            ledger.scanCursor(user,"known",source.id);
        }
        if(known.size()<SCAN_PAGE)ledger.scanCursor(user,"known","");
        for(int i=0;i<WORK_PAGES;i++) {
            MilepostArchiveReader.check(cancelled);
            final MilepostArchiveReader.Snapshot[] loaded={null};
            try {
                boolean more=ledger.processNext(user,(u,id)->{
                    sync(id,cancelled);
                    loaded[0]=MilepostArchiveReader.read(context,id,cancelled);readSources++;
                    return loaded[0].evidence;
                },cancelled,recognisedAt,true,(id,transaction)->commit(id,loaded[0].stamp,cancelled,transaction));
                if(!more)return live.size()==SCAN_PAGE||known.size()==SCAN_PAGE;
                processedPages++;
            }catch(java.io.FileNotFoundException|ConcurrentModificationException raced){return true;}
        }
        return true;
    }
}
