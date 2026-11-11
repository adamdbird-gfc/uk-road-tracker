package com.roadprints.capture;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Canvas pins share a viewport grid for drawing, hit testing and cluster membership. */
final class MilepostMapOverlay {
    interface Projection {float x(double lon);float y(double lat);}
    private static final int UNFOUND=0xFFE8F1F8,FOUND=0xFFD6B9FF,NAVY=0xFF192C51;
    final MilepostCatalogue.MapLocations markers;
    final float[] x,y;
    final int[] indices,counts,discoveredCounts;
    private final int[] cells=new int[2048],slots=new int[2048],next;
    private final String[] labels;
    private final String[] mixedLabels;
    private final MilepostPresentationReader.Discovery[] discoveries;
    int size;
    private double lon=Double.NaN,lat,zoom;
    private int width,height;
    private final Paint fill=new Paint(3),text=new Paint(3);
    private final MarkerArt art;

    MilepostMapOverlay(MilepostCatalogue.MapLocations markers,float density){
        this.markers=markers;
        int n=markers.size();
        x=new float[n];y=new float[n];indices=new int[n];counts=new int[n];
        discoveredCounts=new int[n];next=new int[n];labels=new String[n+1];mixedLabels=new String[n];
        discoveries=new MilepostPresentationReader.Discovery[n];
        for(int i=1;i<=n;i++)labels[i]=Integer.toString(i);
        text.setColor(NAVY);text.setTextSize(11*density);text.setTextAlign(Paint.Align.CENTER);
        text.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        art=new MarkerArt(density);
    }

    void setDiscoveries(Map<String,MilepostPresentationReader.Discovery> states){
        for(int i=0;i<markers.size();i++)discoveries[i]=states.get(markers.identity[i]);
        lon=Double.NaN;
    }

    MilepostPresentationReader.Discovery discovery(int marker){return discoveries[marker];}
    boolean discovered(int marker){return discovery(marker)!=null;}
    List<Integer> members(int slot){
        List<Integer> result=new ArrayList<>(counts[slot]);
        for(int i=indices[slot];i>=0;i=next[i])result.add(i);
        Collections.reverse(result);
        return result;
    }

    void project(double lon,double lat,double zoom,int width,int height,float cell,Projection p){
        if(this.lon==lon&&this.lat==lat&&this.zoom==zoom&&this.width==width&&this.height==height)return;
        this.lon=lon;this.lat=lat;this.zoom=zoom;this.width=width;this.height=height;
        size=0;Arrays.fill(slots,-1);int columns=(int)Math.ceil(width/cell)+2;
        for(int i=0;i<markers.size();i++){
            float px=p.x(markers.longitude[i]),py=p.y(markers.latitude[i]);
            if(px<0||px>width||py<0||py>height)continue;
            int key=(int)(py/cell)*columns+(int)(px/cell),slot=(key*0x9e3779b9)>>>21;
            while(slots[slot]>=0&&cells[slot]!=key)slot=(slot+1)&2047;
            int index=slots[slot];
            if(index>=0){
                next[i]=indices[index];indices[index]=i;counts[index]++;
                if(discovered(i))discoveredCounts[index]++;
                continue;
            }
            cells[slot]=key;slots[slot]=size;x[size]=px;y[size]=py;indices[size]=i;
            next[i]=-1;counts[size]=1;discoveredCounts[size]=discovered(i)?1:0;size++;
        }
        for(int i=0;i<size;i++){
            mixedLabels[i]=discoveredCounts[i]>0&&discoveredCounts[i]<counts[i]
                    ?discoveredCounts[i]+"/"+counts[i]:null;
        }
    }

    void draw(Canvas canvas,float density){
        for(int i=0;i<size;i++){
            if(counts[i]==1){art.draw(canvas,x[i],y[i],discoveredCounts[i]==1);continue;}
            boolean mixed=discoveredCounts[i]>0&&discoveredCounts[i]<counts[i];
            float radius=(mixed?17:13)*density;
            fill.setColor(NAVY);canvas.drawCircle(x[i],y[i],radius+2*density,fill);
            fill.setColor(discoveredCounts[i]==counts[i]?FOUND:UNFOUND);
            canvas.drawCircle(x[i],y[i],radius,fill);
            float baseline=y[i]-(text.ascent()+text.descent())/2;
            if(mixed){
                fill.setColor(FOUND);
                canvas.drawArc(x[i]-radius,y[i]-radius,x[i]+radius,y[i]+radius,0,180,true,fill);
                text.setTextSize(9*density);
                canvas.drawText(mixedLabels[i],x[i],y[i]-(text.ascent()+text.descent())/2,text);
                text.setTextSize(11*density);
            }else canvas.drawText(labels[counts[i]],x[i],baseline,text);
        }
    }

    int hit(float px,float py,float radius){
        int best=-1;float d=radius*radius;
        for(int i=0;i<size;i++){float dx=x[i]-px,dy=y[i]-py,dd=dx*dx+dy*dy;if(dd<d){d=dd;best=i;}}
        return best;
    }

    /** A cast fingerpost silhouette shared by both states and the NCN map key. */
    static final class MarkerArt {
        private final Path post=new Path(),check=new Path();
        private final Paint paint=new Paint(3);
        private final float density;
        MarkerArt(float density){
            this.density=density;
            post.moveTo(-3,-13);post.lineTo(3,-13);post.lineTo(3,-10);
            post.lineTo(10,-10);post.lineTo(14,-6);post.lineTo(10,-2);post.lineTo(3,-2);
            post.lineTo(3,1);post.lineTo(8,1);post.lineTo(8,5);post.lineTo(3,5);
            post.lineTo(3,11);post.lineTo(6,11);post.lineTo(6,14);post.lineTo(-6,14);
            post.lineTo(-6,11);post.lineTo(-3,11);post.lineTo(-3,5);post.lineTo(-10,5);
            post.lineTo(-14,1);post.lineTo(-10,-3);post.lineTo(-3,-3);post.close();
            check.moveTo(4,-6);check.lineTo(6,-4);check.lineTo(10,-8);
            paint.setStrokeJoin(Paint.Join.ROUND);paint.setStrokeCap(Paint.Cap.ROUND);
        }
        void draw(Canvas canvas,float x,float y,boolean discovered){
            canvas.save();canvas.translate(x,y);canvas.scale(density,density);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xFFFFFFFF);canvas.drawCircle(0,0,17,paint);
            paint.setColor(NAVY);canvas.drawCircle(0,0,15.5f,paint);
            paint.setColor(discovered?FOUND:UNFOUND);canvas.drawCircle(0,0,14,paint);
            canvas.save();canvas.scale(.70f,.70f);
            paint.setColor(NAVY);canvas.drawPath(post,paint);
            if(discovered){
                paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2);paint.setColor(FOUND);
                canvas.drawPath(check,paint);
            }
            canvas.restore();
            canvas.restore();
        }
    }
}
