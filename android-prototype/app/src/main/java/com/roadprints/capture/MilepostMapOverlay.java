package com.roadprints.capture;

import android.graphics.Canvas;
import android.graphics.Paint;
import java.util.Arrays;

/** One canvas layer. Projection/density work only when the camera changes; no Android marker Views. */
final class MilepostMapOverlay {
    interface Projection {float x(double lon);float y(double lat);}
    final MilepostCatalogue.MapLocations markers;
    final float[] x,y;final int[] indices,counts;
    private final int[] cells=new int[2048],slots=new int[2048];
    private final String[] labels;
    int size;private double lon=Double.NaN,lat,zoom;private int width,height;
    private final Paint fill=new Paint(3),outline=new Paint(3),text=new Paint(3);
    MilepostMapOverlay(MilepostCatalogue.MapLocations markers,float density){
        this.markers=markers;
        int n=markers.size();x=new float[n];y=new float[n];indices=new int[n];counts=new int[n];labels=new String[n+1];
        for(int i=1;i<=n;i++)labels[i]=Integer.toString(i);
        fill.setColor(0xFF465A83);outline.setColor(0xFFFFFFFF);text.setColor(0xFFFFFFFF);
        text.setTextSize(11*density);text.setTextAlign(Paint.Align.CENTER);
    }
    void project(double lon,double lat,double zoom,int width,int height,float cell,Projection p){
        if(this.lon==lon&&this.lat==lat&&this.zoom==zoom&&this.width==width&&this.height==height)return;
        this.lon=lon;this.lat=lat;this.zoom=zoom;this.width=width;this.height=height;
        size=0;Arrays.fill(slots,-1);int columns=(int)Math.ceil(width/cell)+2;
        for(int i=0;i<markers.size();i++){
            float px=p.x(markers.longitude[i]),py=p.y(markers.latitude[i]);if(px<0||px>width||py<0||py>height)continue;
            int key=(int)(py/cell)*columns+(int)(px/cell),slot=(key*0x9e3779b9)>>>21;
            while(slots[slot]>=0&&cells[slot]!=key)slot=(slot+1)&2047;
            if(slots[slot]>=0){counts[slots[slot]]++;continue;}
            cells[slot]=key;slots[slot]=size;x[size]=px;y[size]=py;indices[size]=i;counts[size]=1;size++;
        }
    }
    void draw(Canvas canvas,float density){
        float r=11*density;
        for(int i=0;i<size;i++){
            canvas.drawCircle(x[i],y[i],r+2*density,outline);canvas.drawCircle(x[i],y[i],r,fill);
            canvas.drawText(counts[i]>1?labels[counts[i]]:"?",x[i],y[i]-(text.ascent()+text.descent())/2,text);
        }
    }
    int hit(float px,float py,float radius){
        int best=-1;float d=radius*radius;
        for(int i=0;i<size;i++){float dx=x[i]-px,dy=y[i]-py,dd=dx*dx+dy*dy;if(dd<d){d=dd;best=i;}}
        return best;
    }
}
