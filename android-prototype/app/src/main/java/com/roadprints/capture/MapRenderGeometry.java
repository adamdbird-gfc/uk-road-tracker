package com.roadprints.capture;
import android.graphics.*;
import org.json.JSONArray;
import java.util.*;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;

/** Bounded world paths plus cheap spatial records; offscreen lines need no UI Path construction. */
final class MapRenderGeometry {
    static final class Line {
        final Path path;final RectF bounds=new RectF();final int count;final double originX,originY;
        Line(Line previous){count=previous.count;originX=previous.originX;originY=previous.originY;bounds.set(previous.bounds);path=null;}
        Line(JSONArray points,boolean build){count=points.length();JSONArray first=points.optJSONArray(0);
            originX=first==null?0:x(first.optDouble(0));originY=first==null?0:y(first.optDouble(1));path=build?new Path():null;
            boolean started=false;for(int i=0;i<points.length();i++){JSONArray p=points.optJSONArray(i);if(p==null||!Double.isFinite(p.optDouble(0))||!Double.isFinite(p.optDouble(1)))continue;
                float px=(float)(x(p.optDouble(0))-originX),py=(float)(y(p.optDouble(1))-originY);
                if(!started){if(path!=null)path.moveTo(px,py);bounds.set(px,py,px,py);started=true;}
                else{if(path!=null)path.lineTo(px,py);bounds.left=Math.min(bounds.left,px);bounds.right=Math.max(bounds.right,px);bounds.top=Math.min(bounds.top,py);bounds.bottom=Math.max(bounds.bottom,py);}
            }
        }
    }
    /** Never call JSONArray.hashCode/equals: nested coordinate arrays can be huge. */
    private static final class WeakKey extends WeakReference<JSONArray> {
        final int hash;
        WeakKey(JSONArray points, ReferenceQueue<JSONArray> queue) { super(points,queue);hash=System.identityHashCode(points); }
        @Override public int hashCode(){return hash;}
        @Override public boolean equals(Object value){if(this==value)return true;if(!(value instanceof WeakKey))return false;JSONArray points=get();return points!=null&&points==((WeakKey)value).get();}
    }
    private static final class WeakIdentityLines {
        final ReferenceQueue<JSONArray> queue=new ReferenceQueue<>();
        final LinkedHashMap<WeakKey,Line> values=new LinkedHashMap<>();
        void prune(){WeakKey key;while((key=(WeakKey)queue.poll())!=null)values.remove(key);}
        Line get(JSONArray points){prune();return values.get(new WeakKey(points,null));}
        void put(JSONArray points,Line line){prune();values.put(new WeakKey(points,queue),line);}
        Line remove(JSONArray points){prune();return values.remove(new WeakKey(points,null));}
        int size(){prune();return values.size();}
        boolean isEmpty(){return size()==0;}
        JSONArray oldest(){prune();Iterator<WeakKey> keys=values.keySet().iterator();while(keys.hasNext()){JSONArray points=keys.next().get();if(points!=null)return points;keys.remove();}return null;}
        void clear(){values.clear();while(queue.poll()!=null){}}
    }
    private static final class StrongKey {
        final JSONArray points;final int hash;
        StrongKey(JSONArray points){this.points=points;hash=System.identityHashCode(points);}
        @Override public int hashCode(){return hash;}
        @Override public boolean equals(Object value){return value instanceof StrongKey && points==((StrongKey)value).points;}
    }
    private static final class RetainedLines {
        final LinkedHashMap<StrongKey,Line> values=new LinkedHashMap<>(64,.75f,true);
        Line put(JSONArray points,Line line){return values.put(new StrongKey(points),line);}
        Line remove(JSONArray points){return values.remove(new StrongKey(points));}
        boolean isEmpty(){return values.isEmpty();}
        JSONArray oldest(){return values.keySet().iterator().next().points;}
        void clear(){values.clear();}
    }
    private static final WeakIdentityLines prepared=new WeakIdentityLines();
    private static final RetainedLines preparedPaths=new RetainedLines();
    private static int preparedPoints;
    private static int cost(Line line){return line.path==null?0:line.count;}
    private static Line prepared(JSONArray points){synchronized(prepared){return prepared.get(points);}}
    static void prepare(java.util.List<JSONArray> rows){
        for(JSONArray points:rows){if(Thread.currentThread().isInterrupted())return;if(prepared(points)!=null)continue;
            boolean build;synchronized(prepared){build=preparedPoints+points.length()<=200000;}
            Line line=new Line(points,build);
            synchronized(prepared){
                if(prepared.get(points)!=null)continue;
                while(prepared.size()>=8192){JSONArray oldest=prepared.oldest();if(oldest==null)break;prepared.remove(oldest);Line removed=preparedPaths.remove(oldest);if(removed!=null)preparedPoints-=cost(removed);}
                if(line.path!=null&&preparedPoints+cost(line)>200000)line=new Line(line);
                prepared.put(points,line);if(line.path!=null){preparedPaths.put(points,line);preparedPoints+=cost(line);}
            }
        }
    }
    static void invalidatePrepared(JSONArray points){synchronized(prepared){prepared.remove(points);Line removed=preparedPaths.remove(points);if(removed!=null)preparedPoints-=cost(removed);}}
    static void clearPrepared(){synchronized(prepared){prepared.clear();preparedPaths.clear();preparedPoints=0;}}
    private final WeakIdentityLines lines=new WeakIdentityLines();
    private final RetainedLines retained=new RetainedLines();
    int vertices,builds,hits,culled;
    static double x(double lon){return lon*111319.49079327358;}
    static double y(double lat){return 6378137*Math.log(Math.tan(Math.PI/4+Math.toRadians(Math.max(-85.05112878,Math.min(85.05112878,lat)))/2));}
    private final Path screen=new Path();private final Matrix transform=new Matrix();private final RectF visible=new RectF();
    private void put(JSONArray points,Line line){
        if(lines.size()>=8192&&lines.get(points)==null){JSONArray oldest=lines.oldest();if(oldest!=null){lines.remove(oldest);Line discarded=retained.remove(oldest);if(discarded!=null)vertices-=cost(discarded);}}lines.put(points,line);
        Line previous=retained.remove(points);if(previous!=null)vertices-=cost(previous);
        if(line.path==null)return;
        if(cost(line)>100000){lines.put(points,new Line(line));return;}
        while(!retained.isEmpty()&&vertices+cost(line)>100000){JSONArray oldest=retained.oldest();Line discarded=retained.remove(oldest);vertices-=cost(discarded);lines.put(oldest,new Line(discarded));}
        retained.put(points,line);vertices+=cost(line);
    }
    boolean draw(Canvas canvas,JSONArray points,Paint paint,Paint halo,double scale,double centreX,double centreY,int width,int height){return draw(canvas,points,paint,halo,scale,centreX,centreY,width,height,true);}
    boolean draw(Canvas canvas,JSONArray points,Paint paint,Paint halo,double scale,double centreX,double centreY,int width,int height,boolean immutable){
        Line line=immutable?lines.get(points):null;
        if(line==null||line.count!=points.length()){line=immutable?prepared(points):null;if(line==null||line.count!=points.length())line=new Line(points,false);}else hits++;
        float tx=(float)(width/2.0+(line.originX-centreX)*scale),ty=(float)(height/2.0-(line.originY-centreY)*scale);
        visible.set((float)(line.bounds.left*scale+tx),(float)(-line.bounds.bottom*scale+ty),(float)(line.bounds.right*scale+tx),(float)(-line.bounds.top*scale+ty));
        float padding=Math.max(16,halo==null?paint.getStrokeWidth():halo.getStrokeWidth());
        if(visible.right< -padding||visible.left>width+padding||visible.bottom< -padding||visible.top>height+padding){culled++;if(immutable)put(points,line);return false;}
        if(line.path==null){line=new Line(points,true);builds++;}
        if(immutable)put(points,line);
        transform.reset();transform.setValues(new float[]{(float)scale,0,tx,0,(float)-scale,ty,0,0,1});line.path.transform(transform,screen);
        if(halo!=null)canvas.drawPath(screen,halo);canvas.drawPath(screen,paint);return true;
    }
    void clear(){lines.clear();retained.clear();vertices=0;}
}
