package com.roadprints.capture;

import java.util.ArrayList;
import java.util.List;

/** Bounded recent trajectory evidence; path length from GPS jitter is not movement. */
final class PedestrianMotionWindow {
    private final List<FootTraceValidator.Sample> recent=new ArrayList<>();
    void reset(){recent.clear();}
    void accept(FootTraceValidator.Sample p){
        if(!WalkingStillnessDetector.usable(p)||p.accuracy>50)return;
        if(!recent.isEmpty()){
            FootTraceValidator.Sample last=recent.get(recent.size()-1);
            if(p.time<=last.time)return;
            if(p.time-last.time>60_000L)recent.clear();
        }
        recent.add(p);
        while(recent.size()>1&&(p.time-recent.get(0).time>90_000L||recent.size()>96))recent.remove(0);
    }
    boolean moving(){return startIndex(recent)>=0;}
    int movementStart(){return startIndex(recent);}
    static boolean confirmed(List<FootTraceValidator.Sample> samples){return startIndex(samples)>=0;}
    static boolean sustainedDeparture(List<FootTraceValidator.Sample> samples){
        PedestrianMotionWindow window=new PedestrianMotionWindow();long since=0,lastVote=0,lastTime=0;
        for(FootTraceValidator.Sample p:samples){
            if(!WalkingStillnessDetector.usable(p)||p.accuracy>50||p.time<=lastTime)continue;
            lastTime=p.time;window.accept(p);
            if(window.moving()){
                if(since==0||p.time-lastVote>15_000L)since=p.time;
                lastVote=p.time;
            }
        }
        return since>0&&lastTime-lastVote<=15_000L&&lastVote-since>=40_000L;
    }
    private static int startIndex(List<FootTraceValidator.Sample> samples){
        // Examine short suffixes, so loops need no overall departure radius.
        // Fixes less than ten seconds apart do not multiply the movement vote.
        if(samples.size()<3)return -1;
        FootTraceValidator.Sample end=samples.get(samples.size()-1);
        if(!WalkingStillnessDetector.usable(end)||end.accuracy>50)return -1;
        for(int start=samples.size()-3;start>=0;start--){
            FootTraceValidator.Sample first=samples.get(start);
            long span=end.time-first.time;
            if(span>90_000L)break;
            if(span<20_000L||!WalkingStillnessDetector.usable(first)||first.accuracy>50)continue;
            FootTraceValidator.Sample previous=null;double path=0;int fixes=0,moving=0;boolean precise=true,valid=true;
            for(int i=start;i<samples.size();i++){
                FootTraceValidator.Sample p=samples.get(i);
                if(!WalkingStillnessDetector.usable(p)||p.accuracy>50)continue;
                if(previous!=null&&p.time-previous.time<10_000L&&i<samples.size()-1)continue;
                if(previous!=null){
                    if(p.time<=previous.time||p.time-previous.time>60_000L||!FootTraceValidator.plausible(previous,p)){valid=false;break;}
                    path+=FootTraceValidator.metres(previous,p);
                }
                precise &= p.accuracy<=10;
                if(Double.isFinite(p.speed)&&p.speed>=.5)moving++;
                fixes++;previous=p;
            }
            double extent=FootTraceValidator.metres(first,end);
            if(valid&&fixes>=3&&(moving*3>=fixes*2||precise)
                    &&extent-first.accuracy-end.accuracy>=20&&path>0&&extent/path>=.65)return first.index;
        }
        return -1;
    }
}
