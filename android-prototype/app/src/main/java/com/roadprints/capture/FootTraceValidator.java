package com.roadprints.capture;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Bounded, evidence-based cleanup. Original observations are never changed.
 * Missing timing evidence leaves a trace untouched. */
final class FootTraceValidator {
    static final class Sample {
        final double lon, lat, accuracy, speed;
        final long time;
        final int index;
        Sample(double lon, double lat, double accuracy, long time, double speed, int index) {
            this.lon=lon; this.lat=lat; this.accuracy=accuracy; this.time=time;
            this.speed=speed; this.index=index;
        }
    }
    static final class Result {
        final List<Sample> samples;
        final int removed;
        final boolean timed;
        final boolean resolved;
        final double distance;
        Result(List<Sample> samples, int original, boolean timed) {
            this(samples, original, timed, true);
        }
        Result(List<Sample> samples, int original, boolean timed, boolean resolved) {
            this.samples=samples; this.removed=original-samples.size(); this.timed=timed;
            this.resolved=resolved;
            double metres=0;
            for(int i=1;i<samples.size();i++) metres+=metres(samples.get(i-1),samples.get(i));
            distance=metres;
        }
    }

    static Result validate(List<Sample> input) {
        int n=input.size(), timed=0;
        for(Sample point:input) if(point.time>0) timed++;
        if(n<2 || timed==0 || timed<n-1 || (n==2 && timed<2))
            return new Result(new ArrayList<>(input),n,false);
        boolean[] suspect=new boolean[n];
        for(int i=1;i<n-1;i++) suspect[i]=!plausible(input.get(i-1),input.get(i))
                || !plausible(input.get(i),input.get(i+1));
        double[] costs=new double[n]; int[] previous=new int[n];
        java.util.Arrays.fill(costs,Double.POSITIVE_INFINITY);
        java.util.Arrays.fill(previous,-1); costs[0]=0;
        for(int i=1;i<n;i++) {
            for(int before=Math.max(0,i-12);before<i;before++) {
                if(!Double.isFinite(costs[before]) || !plausible(input.get(before),input.get(i))) continue;
                double discard=0; boolean allowed=true;
                if(i-before>1 && (input.get(before).time<=0
                        || (input.get(i).time-input.get(before).time>120000
                        && !recoverableSamplingGap(input,suspect,before,i)))) continue;
                for(int skipped=before+1;skipped<i;skipped++) {
                    Sample point=input.get(skipped);
                    double uncertainty=Math.min(30,Math.max(5,point.accuracy*.6));
                    if(!suspect[skipped] && segmentGap(point,input.get(before),input.get(i))>uncertainty) {
                        allowed=false; break;
                    }
                    // Accurate fixes are expensive to discard; noisy fixes can
                    // be bypassed only with continuity or uncertainty evidence.
                    discard+=20*Math.max(.5,Math.min(4,20/Math.max(5,point.accuracy)));
                }
                if(!allowed) continue;
                double cost=costs[before]+metres(input.get(before),input.get(i))+discard;
                if(cost<costs[i]) { costs[i]=cost; previous[i]=before; }
            }
        }
        int end=n-1;
        while(end>0 && !Double.isFinite(costs[end])) end--;
        if(end==0 || n-1-end>2) return new Result(new ArrayList<>(input),n,true,false);
        List<Sample> selected=new ArrayList<>();
        for(int at=end;at>=0;at=previous[at]) selected.add(input.get(at));
        Collections.reverse(selected);
        return new Result(selected,n,true);
    }

    static boolean plausible(Sample a, Sample b) {
        if(!Double.isFinite(a.lon) || !Double.isFinite(a.lat) || !Double.isFinite(b.lon)
                || !Double.isFinite(b.lat) || Math.abs(b.lon)>180 || Math.abs(b.lat)>90) return false;
        if(a.time<=0 || b.time<=0) return true;
        double seconds=(b.time-a.time)/1000.0;
        if(seconds<=0) return false;
        double reported=Math.max(a.speed,b.speed);
        double limit=Math.max(4.5,Math.min(10,reported*1.5));
        double allowance=Math.min(20,(Math.max(0,a.accuracy)+Math.max(0,b.accuracy))*.3);
        return metres(a,b)<=limit*seconds+allowance;
    }

    private static boolean recoverableSamplingGap(List<Sample> input, boolean[] suspect,
                                                  int before, int after) {
        // A delayed/inconsistent fix after a real sampling pause must not
        // strand the rest of a walk. Bypass at most two suspect observations;
        // ordinary recorded movement and reliable turns remain protected.
        if(after-before>3) return false;
        for(int at=before+1;at<after;at++) if(!suspect[at]) return false;
        long largestGap=0;
        for(int at=before+1;at<=after;at++)
            largestGap=Math.max(largestGap,input.get(at).time-input.get(at-1).time);
        long span=input.get(after).time-input.get(before).time;
        return largestGap>=120000 && largestGap>=span*.8;
    }

    static double metres(Sample a, Sample b) {
        double x=Math.toRadians(a.lat),y=Math.toRadians(b.lat),dl=Math.toRadians(b.lon-a.lon);
        double h=Math.pow(Math.sin((y-x)/2),2)+Math.cos(x)*Math.cos(y)*Math.pow(Math.sin(dl/2),2);
        return 12742000*Math.asin(Math.sqrt(Math.min(1,h)));
    }
    static double segmentGap(Sample point, Sample a, Sample b) {
        double scale=Math.cos(Math.toRadians(point.lat));
        double dx=(b.lon-a.lon)*scale,dy=b.lat-a.lat;
        double px=(point.lon-a.lon)*scale,py=point.lat-a.lat,norm=dx*dx+dy*dy;
        double fraction=norm==0?0:Math.max(0,Math.min(1,(px*dx+py*dy)/norm));
        return Math.hypot(px-fraction*dx,py-fraction*dy)*111195;
    }
}
