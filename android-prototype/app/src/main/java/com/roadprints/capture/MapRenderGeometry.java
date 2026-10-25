package com.roadprints.capture;
import android.graphics.*;
import org.json.JSONArray;
import java.util.*;

/** Bounded immutable world-path cache; camera changes transform rather than rebuild every vertex. */
final class MapRenderGeometry {
    static final class Line {
        final Path path=new Path();final RectF bounds=new RectF();final int count;final double originX,originY;
        Line(JSONArray points){count=points.length();JSONArray first=points.optJSONArray(0);
            originX=first==null?0:x(first.optDouble(0));originY=first==null?0:y(first.optDouble(1));
            boolean started=false;for(int i=0;i<points.length();i++){JSONArray p=points.optJSONArray(i);if(p==null||!Double.isFinite(p.optDouble(0))||!Double.isFinite(p.optDouble(1)))continue;
                float px=(float)(x(p.optDouble(0))-originX),py=(float)(y(p.optDouble(1))-originY);if(!started){path.moveTo(px,py);bounds.set(px,py,px,py);started=true;}else {path.lineTo(px,py);bounds.left=Math.min(bounds.left,px);bounds.right=Math.max(bounds.right,px);bounds.top=Math.min(bounds.top,py);bounds.bottom=Math.max(bounds.bottom,py);}}

        }
    }
    private static final LinkedHashMap<JSONArray,Line> prepared=new LinkedHashMap<>(64,.75f,true);
    private static int preparedPoints;
    private static Line prepared(JSONArray points){synchronized(prepared){return prepared.get(points);}}
    static void prepare(java.util.List<JSONArray> rows){
        for(JSONArray points:rows){if(Thread.currentThread().isInterrupted())return;if(points.length()>200000||prepared(points)!=null)continue;
            Line line=new Line(points);synchronized(prepared){while(!prepared.isEmpty()&&(prepared.size()>=4096||preparedPoints+line.count>200000)){JSONArray key=prepared.keySet().iterator().next();preparedPoints-=prepared.remove(key).count;}prepared.put(points,line);preparedPoints+=line.count;}
        }
    }
    private final LinkedHashMap<JSONArray,Line> lines=new LinkedHashMap<>(64,.75f,true);
    int vertices,builds,hits,culled;
    static double x(double lon){return lon*111319.49079327358;}
    static double y(double lat){return 6378137*Math.log(Math.tan(Math.PI/4+Math.toRadians(Math.max(-85.05112878,Math.min(85.05112878,lat)))/2));}
    private final Path screen=new Path();private final Matrix transform=new Matrix();private final RectF visible=new RectF();
    boolean draw(Canvas canvas,JSONArray points,Paint paint,Paint halo,double scale,double centreX,double centreY,int width,int height){
        Line line=lines.get(points);
        if(line==null||line.count!=points.length()){
            if(line!=null){vertices-=line.count;lines.remove(points);}line=prepared(points);if(line==null||line.count!=points.length()){line=new Line(points);builds++;}else hits++;
            if(line.count<=200000){while(!lines.isEmpty()&&(lines.size()>=4096||vertices+line.count>200000)){JSONArray oldest=lines.keySet().iterator().next();vertices-=lines.remove(oldest).count;}lines.put(points,line);vertices+=line.count;}
        }else hits++;
        float tx=(float)(width/2.0+(line.originX-centreX)*scale),ty=(float)(height/2.0-(line.originY-centreY)*scale);
        visible.set((float)(line.bounds.left*scale+tx),(float)(-line.bounds.bottom*scale+ty),(float)(line.bounds.right*scale+tx),(float)(-line.bounds.top*scale+ty));
        float padding=Math.max(16,halo==null?paint.getStrokeWidth():halo.getStrokeWidth());
        if(visible.right< -padding||visible.left>width+padding||visible.bottom< -padding||visible.top>height+padding){culled++;return false;}
        transform.reset();transform.setValues(new float[]{(float)scale,0,tx,0,(float)-scale,ty,0,0,1});line.path.transform(transform,screen);
        if(halo!=null)canvas.drawPath(screen,halo);canvas.drawPath(screen,paint);return true;
    }
    void clear(){lines.clear();vertices=0;}
}
