package com.roadprints.capture;

import java.util.List;

/** A conservative fallback when Android has not yet delivered a pedestrian transition. */
final class WalkingStartEvidence {
    static boolean confirmed(List<FootTraceValidator.Sample> samples,long now) {
        if(samples.size()<3) return false;
        FootTraceValidator.Sample first=samples.get(0),last=samples.get(samples.size()-1);
        if(last.time-first.time>120_000L || Math.abs(now-last.time)>60_000L) return false;
        double movement=0;
        for(int i=0;i<samples.size();i++) {
            FootTraceValidator.Sample p=samples.get(i);
            if(!WalkingStillnessDetector.usable(p) || p.accuracy>25 || p.speed<0.5 || p.speed>3.5) return false;
            if(i>0) {
                FootTraceValidator.Sample previous=samples.get(i-1);
                if(p.time<=previous.time || p.time-previous.time>60_000L
                        || !FootTraceValidator.plausible(previous,p)) return false;
                movement+=Math.max(0,FootTraceValidator.metres(previous,p)-previous.accuracy-p.accuracy);
            }
        }
        return FootTraceValidator.metres(first,last)-first.accuracy-last.accuracy>=50
                && CaptureStartGate.shouldConfirmStart("walking",last.time-first.time,(float)movement,false,0);
    }
}
