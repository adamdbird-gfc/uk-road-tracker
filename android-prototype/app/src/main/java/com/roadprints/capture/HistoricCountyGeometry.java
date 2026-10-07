package com.roadprints.capture;

import java.io.*;
import java.util.*;
import java.util.zip.GZIPInputStream;

/** Full-resolution polygon rings, including holes and islands. Coordinates are sub-metre integers. */
final class HistoricCountyGeometry {
    static final double SCALE=1_000_000.0;
    final List<int[]> rings;
    final List<double[]> bounds=new ArrayList<>();
    final int bytes;
    HistoricCountyGeometry(List<int[]> rings) {
        this.rings=rings;int size=0;
        for(int[] ring:rings) {
            double[] b={Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY};
            for(int i=0;i<ring.length;i+=2){b[0]=Math.min(b[0],ring[i]/SCALE);b[1]=Math.min(b[1],ring[i+1]/SCALE);b[2]=Math.max(b[2],ring[i]/SCALE);b[3]=Math.max(b[3],ring[i+1]/SCALE);}
            bounds.add(b);size+=ring.length*4;
        }
        bytes=size;
    }
    static HistoricCountyGeometry read(byte[] compressed) throws IOException {
        List<int[]> rings=new ArrayList<>();int total=0;
        try(InputStream in=new BufferedInputStream(new GZIPInputStream(new ByteArrayInputStream(compressed)))) {
            int count=uint(in);if(count<1||count>20000)throw new IOException("Invalid county rings");
            for(int r=0;r<count;r++) {
                int n=uint(in);total+=n;if(n<3||total>2_000_000)throw new IOException("Invalid county vertices");
                int[] points=new int[n*2];int x=0,y=0;
                for(int p=0;p<n;p++){x+=signed(in);y+=signed(in);points[p*2]=x;points[p*2+1]=y;}
                rings.add(points);
            }
            if(in.read()!=-1)throw new IOException("Unexpected county data");
        }
        return new HistoricCountyGeometry(rings);
    }
    private static int uint(InputStream in) throws IOException {
        int value=0;
        for(int shift=0;shift<=28;shift+=7){int b=in.read();if(b<0)throw new EOFException();value|=(b&127)<<shift;if((b&128)==0)return value;}
        throw new IOException("Invalid integer");
    }
    private static int signed(InputStream in) throws IOException {int v=uint(in);return (v>>>1)^-(v&1);}
    static boolean overlaps(double[] b,double ax,double ay,double bx,double by) {
        return Math.max(ax,bx)>=b[0]&&Math.min(ax,bx)<=b[2]&&Math.max(ay,by)>=b[1]&&Math.min(ay,by)<=b[3];
    }
    boolean contains(double x,double y) {
        boolean inside=false;
        for(int r=0;r<rings.size();r++) {
            if(!overlaps(bounds.get(r),x,y,x,y))continue;
            int[] ring=rings.get(r);
            for(int i=0,j=ring.length-2;i<ring.length;j=i,i+=2) {
                double ax=ring[j]/SCALE,ay=ring[j+1]/SCALE,bx=ring[i]/SCALE,by=ring[i+1]/SCALE;
                double cross=(x-ax)*(by-ay)-(y-ay)*(bx-ax);
                if(Math.abs(cross)<1e-14 && x>=Math.min(ax,bx)-1e-10&&x<=Math.max(ax,bx)+1e-10
                        &&y>=Math.min(ay,by)-1e-10&&y<=Math.max(ay,by)+1e-10)return false;
                if((ay>y)!=(by>y)&&x<(bx-ax)*(y-ay)/(by-ay)+ax)inside=!inside;
            }
        }
        return inside;
    }
    /** Require a positive stretch inside: touching a border or running along it does not qualify. */
    boolean intersects(double ax,double ay,double bx,double by) {
        if(!Double.isFinite(ax)||!Double.isFinite(ay)||!Double.isFinite(bx)||!Double.isFinite(by)
                ||(ax==bx&&ay==by))return false;
        if(contains(ax,ay)||contains(bx,by))return true;
        double dx=bx-ax,dy=by-ay;
        List<Double> cuts=new ArrayList<>();cuts.add(0.0);cuts.add(1.0);
        for(int r=0;r<rings.size();r++) {
            if(!overlaps(bounds.get(r),ax,ay,bx,by))continue;
            int[] ring=rings.get(r);
            for(int i=0,j=ring.length-2;i<ring.length;j=i,i+=2) {
                double cx=ring[j]/SCALE,cy=ring[j+1]/SCALE,ex=ring[i]/SCALE-cx,ey=ring[i+1]/SCALE-cy;
                double denominator=dx*ey-dy*ex;if(Math.abs(denominator)<1e-18)continue;
                double t=((cx-ax)*ey-(cy-ay)*ex)/denominator,u=((cx-ax)*dy-(cy-ay)*dx)/denominator;
                if(t>=0&&t<=1&&u>=0&&u<=1)cuts.add(t);
            }
        }
        Collections.sort(cuts);
        for(int i=1;i<cuts.size();i++)if(cuts.get(i)-cuts.get(i-1)>1e-9) {
            double t=(cuts.get(i)+cuts.get(i-1))/2;if(contains(ax+dx*t,ay+dy*t))return true;
        }
        return false;
    }
}
