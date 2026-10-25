package com.roadprints.capture;
import android.graphics.*;
import org.json.JSONArray;
import java.util.*;

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
    private static final WeakHashMap<JSONArray,Line> prepared=new WeakHashMap<>();
    private static final LinkedHashMap<JSONArray,Line> preparedPaths=new LinkedHashMap<>();
    private static int preparedPoints;
    private static int cost(Line line){return line.path==null?0:line.count;}
    private static Line prepared(JSONArray points){synchronized(prepared){return prepared.get(points);}}
    static void prepare(java.util.List<JSONArray> rows){
        for(JSONArray points:rows){if(Thread.currentThread().isInterrupted())return;if(prepared(points)!=null)continue;
            boolean build;synchronized(prepared){build=preparedPoints+points.length()<=200000;}
            Line line=new Line(points,build);
            synchronized(prepared){while(!prepared.isEmpty()&&prepared.size()>=8192)prepared.remove(prepared.keySet().iterator().next());prepared.put(points,line);if(line.path!=null){preparedPaths.put(points,line);preparedPoints+=cost(line);}}
        }
    }
    static void clearPrepared(){synchronized(prepared){prepared.clear();preparedPaths.clear();preparedPoints=0;}}
    private final WeakHashMap<JSONArray,Line> lines=new WeakHashMap<>();
    private final LinkedHashMap<JSONArray,Line> retained=new LinkedHashMap<>(64,.75f,true);
    int vertices,builds,hits,culled;
    static double x(double lon){return lon*111319.49079327358;}
    static double y(double lat){return 6378137*Math.log(Math.tan(Math.PI/4+Math.toRadians(Math.max(-85.05112878,Math.min(85.05112878,lat)))/2));}
    private final Path screen=new Path();private final Matrix transform=new Matrix();private final RectF visible=new RectF();
    private void put(JSONArray points,Line line){
        if(lines.size()>=8192)lines.remove(lines.keySet().iterator().next());lines.put(points,line);
        Line previous=retained.remove(points);if(previous!=null)vertices-=cost(previous);
        if(line.path==null)return;
        if(cost(line)>100000){lines.put(points,new Line(line));return;}
        while(!retained.isEmpty()&&vertices+cost(line)>100000){JSONArray oldest=retained.keySet().iterator().next();Line discarded=retained.remove(oldest);vertices-=cost(discarded);lines.put(oldest,new Line(discarded));}
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
