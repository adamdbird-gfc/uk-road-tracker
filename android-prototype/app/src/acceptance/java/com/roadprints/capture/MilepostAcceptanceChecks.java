package com.roadprints.capture;

import android.content.Context;
import android.os.SystemClock;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;

/** Runner-independent scenario assertions, reused on Robolectric and ART. No production caller. */
final class MilepostAcceptanceChecks {
    static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    static void converge(MilepostAcceptanceFixture f)throws Exception {
        int slices=0;
        while(f.process(new AtomicBoolean(),SystemClock.elapsedRealtime()+2000))
            if(++slices>100)throw new AssertionError("Synthetic reconciliation did not converge within 100 slices");
    }
    static void reset(Context c){
        require(MilepostAcceptanceFixture.PACKAGE.equals(c.getPackageName()),"Wrong sandbox");
        c.deleteDatabase(MilepostAcceptanceFixture.DATABASE);
        c.getSharedPreferences("roadprints_capture_state",0).edit().clear().commit();
    }
    static void productionIsolated(Context c)throws Exception {
        require(!MilepostIntegration.PRODUCTION.enabled,"Production gate changed");
        require(!MilepostIntegration.PRODUCTION.enterPresentation(),"Production presentation activated");
        require(!MilepostIntegration.PRODUCTION.enterProcessing(),"Production worker activated");
        require(MilepostReconciliationJob.schedule(c)==android.app.job.JobScheduler.RESULT_FAILURE,"Production job scheduled");
        require(!c.getDatabasePath(MilepostReconciliationJob.DATABASE).exists(),"Production ledger created");
        for(AchievementStore.Definition d:AchievementStore.definitions())
            require(!java.util.Arrays.asList(MilepostAchievements.IDS).contains(d.id),"Synthetic award in normal definitions");
    }
    static void onePostAndDesigner(Context c)throws Exception {
        reset(c);try(MilepostAcceptanceFixture f=new MilepostAcceptanceFixture(c)){
            f.prepare(1);converge(f);MilepostPresentation.Snapshot s=f.read();
            require(s.count==1,"One physical post");require(s.awards.get(0).earned,"Mills award");
            require(!s.awards.get(5).earned&&!s.awards.get(6).earned,"Premature completion");productionIsolated(c);
        }
    }
    static void sixPostsRemainMystery(Context c)throws Exception {
        reset(c);try(MilepostAcceptanceFixture f=new MilepostAcceptanceFixture(c)){
            f.prepare(6);converge(f);MilepostPresentation.Snapshot s=f.read();
            require(s.count==6&&!s.awards.get(5).earned&&!s.awards.get(6).earned,"Six posts prematurely completed Harrow/collective");
            require(!s.card(5).accessible().contains("Charlie"),"Locked identity leaked");productionIsolated(c);
        }
    }
    static void allAwardsAndDuplicates(Context c)throws Exception {
        reset(c);
        try(MilepostAcceptanceFixture f=new MilepostAcceptanceFixture(c)){
            f.prepare(7);
            java.util.Map<String,?> before=c.getSharedPreferences("roadprints_achievements_v1",0).getAll();
            converge(f);MilepostPresentation.Snapshot first=f.read();
            require(first.count==7&&first.newPosts==7,"Consolidated historical summary");
            require(first.individuals()==6&&first.awards.get(6).earned,"All six plus collective");
            require(first.target()==7,"Seven Harrow stages");
            converge(f);MilepostPresentation.Snapshot again=f.read();
            require(again.count==7&&first.postOrdinal==again.postOrdinal,"Duplicate discovery ordinal");
            require(before.equals(c.getSharedPreferences("roadprints_achievements_v1",0).getAll()),"Normal entitlement preferences changed");productionIsolated(c);
        }
    }
    static void correctionFallbackAndRestart(Context c)throws Exception {
        reset(c);long ordinal;
        try(MilepostAcceptanceFixture f=new MilepostAcceptanceFixture(c)){
            f.prepare(7);converge(f);ordinal=f.read().awards.get(0).ordinal;
            JourneyStore.delete(c,"synthetic:a");f.restore();converge(f);require(f.read().count==7,"Fallback evidence lost");
            JourneyStore.save(c,MilepostAcceptanceFixture.journey("walking").put("journey_id","synthetic:b").put("processing_status","failed"));
            f.restore();converge(f);require(f.read().count==0,"Correction not withdrawn");
        }
        try(MilepostAcceptanceFixture f=new MilepostAcceptanceFixture(c)){
            JourneyStore.save(c,MilepostAcceptanceFixture.journey("walking").put("journey_id","synthetic:b"));
            f.restore();converge(f);require(f.read().count==7,"Restart reinstatement");
            require(f.read().awards.get(0).ordinal==ordinal,"Reinstatement duplicated award");productionIsolated(c);
        }
    }
    static void interruptionAndCaptureState(Context c)throws Exception {
        reset(c);try(MilepostAcceptanceFixture f=new MilepostAcceptanceFixture(c)){
            f.prepare(7);AtomicBoolean stop=new AtomicBoolean(true);boolean cancelled=false;
            try{f.process(stop,Long.MAX_VALUE);}catch(CancellationException expected){cancelled=true;}
            require(cancelled,"Worker ignored interruption");
            require(f.gate.enterPresentation(),"Worker lease leaked");f.gate.leavePresentation();
            try(MilepostReconciliationJob.CaptureGuard guard=new MilepostReconciliationJob.CaptureGuard(c,new AtomicBoolean(),Long.MAX_VALUE)){
                require(!guard.getAsBoolean(),"Capture already active");
                c.getSharedPreferences("roadprints_capture_state",0).edit().putBoolean("active",true).commit();
                require(guard.getAsBoolean(),"Capture start not observed");
                cancelled=false;try{f.process(new AtomicBoolean(),Long.MAX_VALUE);}catch(CancellationException expected){cancelled=true;}
                require(cancelled,"Capture state did not yield worker");
            }finally{c.getSharedPreferences("roadprints_capture_state",0).edit().putBoolean("active",false).commit();}
            f.restore();converge(f);require(f.read().count==7,"Resume failed");productionIsolated(c);
        }
    }
}
