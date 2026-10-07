package com.roadprints.capture;

import java.util.ArrayList;
import java.util.List;

/** One conservative preparation rule for saving and retrying native pedestrian captures. */
final class WalkingCaptureEvidence {
    static final class Result {
        final FootTraceValidator.Result route;
        final int rejectedFixes, stationaryTail;
        Result(FootTraceValidator.Result route,int rejectedFixes,int stationaryTail) {
            this.route=route; this.rejectedFixes=rejectedFixes; this.stationaryTail=stationaryTail;
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
        for(FootTraceValidator.Sample p:input) detector.accept(p);
        int endpoint=detector.confirmedEndpoint(), tail=0, rejected=0;
        List<FootTraceValidator.Sample> retained=new ArrayList<>();
        for(FootTraceValidator.Sample p:input) {
            if(endpoint>=0 && p.index>endpoint) { tail++; continue; }
            if(!WalkingStillnessDetector.usable(p)) { rejected++; continue; }
            retained.add(p);
        }
        FootTraceValidator.Result checked=FootTraceValidator.validate(retained);
        return new Result(new FootTraceValidator.Result(checked.samples,input.size(),true,
                checked.resolved && checked.samples.size()>=2),rejected,tail);
    }
}
