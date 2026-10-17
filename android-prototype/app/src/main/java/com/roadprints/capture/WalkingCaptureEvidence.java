package com.roadprints.capture;

import java.util.ArrayList;
import java.util.List;

/** One conservative preparation rule for saving and retrying native pedestrian captures. */
final class WalkingCaptureEvidence {
    static final class Result {
        final FootTraceValidator.Result route;
        final int rejectedFixes, stationaryTail, stationaryPoints;
        Result(FootTraceValidator.Result route,int rejectedFixes,int stationaryTail) {
            this(route,rejectedFixes,stationaryTail,stationaryTail);
        }
        Result(FootTraceValidator.Result route,int rejectedFixes,int stationaryTail,int stationaryPoints) {
            this.route=route; this.rejectedFixes=rejectedFixes; this.stationaryTail=stationaryTail;
            this.stationaryPoints=stationaryPoints;
        }
    }
    static Result prepare(List<FootTraceValidator.Sample> input) {
        int timed=0;
        for(FootTraceValidator.Sample p:input) if(p.time>0) timed++;
        // Untimed and user-drawn geometry cannot be reconstructed from original GPS evidence.
        if(timed<2 || timed<input.size()-1)
            return new Result(FootTraceValidator.validate(input),0,0);
        for(int i=1;i<input.size()-1;i++) if(input.get(i).time<=0)
            return new Result(FootTraceValidator.validate(input),0,0);
        WalkingStillnessDetector detector=new WalkingStillnessDetector();
        PedestrianMotionWindow motion=new PedestrianMotionWindow();
        boolean[] stationary=new boolean[input.size()];
        int stay=-1,end=-1,departure=-1;long movingSince=0,lastVote=0,lastSupport=0;
        for(int i=0;i<input.size();i++){
            FootTraceValidator.Sample p=input.get(i);detector.accept(p);motion.accept(p);
            FootTraceValidator.Sample anchor=detector.anchor();
            if(stay>=0){
                boolean usable=WalkingStillnessDetector.usable(p)&&p.accuracy<=50;
                int moving=motion.movementStart();
                if(usable&&moving>=0){
                    if(movingSince==0||p.time-lastVote>15_000L){movingSince=p.time;departure=moving;}
                    lastVote=p.time;
                }
                boolean departed=movingSince>0&&lastVote-movingSince>=40_000L&&p.time-lastVote<=15_000L;
                if(departed||p.time-lastSupport>WalkingStillnessDetector.FRESH_MS){
                    int cutoff=end;
                    if(departed)while(cutoff>stay&&input.get(cutoff).index>=departure)cutoff--;
                    for(int k=stay+1;k<=cutoff;k++)stationary[k]=true;
                    stay=-1;movingSince=0;lastVote=0;departure=-1;
                }else if(usable&&p.time>lastSupport&&p.speed>=0&&p.speed<=.8
                        &&FootTraceValidator.metres(input.get(stay),p)<=35+input.get(stay).accuracy+p.accuracy){
                    lastSupport=p.time;end=i;
                }
            }
            if(stay<0&&detector.canFinish(p.time)){
                    stay=i;
                    while(stay>0&&input.get(stay).index!=anchor.index)stay--;
                    end=i;lastSupport=p.time;
            }
        }
        if(stay>=0)for(int k=stay+1;k<=end;k++)stationary[k]=true;
        int tail=0,rejected=0,stationaryCount=0;
        for(int i=input.size()-1;i>=0&&stationary[i];i--)tail++;
        List<FootTraceValidator.Sample> retained=new ArrayList<>();
        for(int i=0;i<input.size();i++) {
            FootTraceValidator.Sample p=input.get(i);
            if(stationary[i]) { stationaryCount++;continue; }
            if(!WalkingStillnessDetector.usable(p) || p.accuracy>50) { rejected++; continue; }
            retained.add(p);
        }
        FootTraceValidator.Result checked=FootTraceValidator.validate(retained);
        return new Result(new FootTraceValidator.Result(checked.samples,input.size(),true,
                checked.resolved && checked.samples.size()>=2),rejected,tail,stationaryCount);
    }
}
