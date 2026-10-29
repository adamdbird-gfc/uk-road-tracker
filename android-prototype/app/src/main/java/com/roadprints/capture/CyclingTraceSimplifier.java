package com.roadprints.capture;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/** Reduces dense bicycle fixes while retaining bends and intermediate evidence. */
final class CyclingTraceSimplifier {
    static int[] retainedIndices(double[][] points) {
        int size = points.length;
        if (size < 3) { int[] all = new int[size]; for(int i=0;i<size;i++) all[i]=i; return all; }
        boolean[] keep = new boolean[size]; keep[0]=true; keep[size-1]=true;
        ArrayDeque<int[]> pending=new ArrayDeque<>(); pending.push(new int[]{0,size-1});
        while(!pending.isEmpty()) {
            int[] range=pending.pop(); int first=range[0],last=range[1];
            if(last-first<2) continue;
            double largest=5; int selected=-1;
            for(int i=first+1;i<last;i++) {
                double deviation=distanceToSegment(points[i],points[first],points[last]);
                if(deviation>largest) { largest=deviation; selected=i; }
            }
            // Do not reduce a long straight ride to two distant endpoints.
            if(selected<0 && distance(points[first],points[last])>100) selected=(first+last)/2;
            if(selected>=0) { keep[selected]=true; pending.push(new int[]{first,selected}); pending.push(new int[]{selected,last}); }
        }
        List<Integer> indices=new ArrayList<>(); for(int i=0;i<size;i++) if(keep[i]) indices.add(i);
        int[] result=new int[indices.size()]; for(int i=0;i<result.length;i++) result[i]=indices.get(i);
        return result;
    }
    private static double distance(double[] a,double[] b) {
        double x=(b[0]-a[0])*111320*Math.cos(Math.toRadians((a[1]+b[1])/2));
        double y=(b[1]-a[1])*111320; return Math.hypot(x,y);
    }
    private static double distanceToSegment(double[] p,double[] a,double[] b) {
        double scale=111320*Math.cos(Math.toRadians((a[1]+b[1])/2));
        double x=(b[0]-a[0])*scale,y=(b[1]-a[1])*111320;
        double px=(p[0]-a[0])*scale,py=(p[1]-a[1])*111320;
        double length=x*x+y*y; double t=length==0?0:Math.max(0,Math.min(1,(px*x+py*y)/length));
        return Math.hypot(px-t*x,py-t*y);
    }
}
