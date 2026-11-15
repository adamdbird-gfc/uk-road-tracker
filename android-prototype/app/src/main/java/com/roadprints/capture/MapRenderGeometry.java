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
        int size(){prune();return values.size();}
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
        void clear(){values.clear();}
    }
    private static final WeakIdentityLines prepared=new WeakIdentityLines();
    // Keep the worker's paths bounded, and never keep a JSON geometry alive via its cache key.
    private static final int MAX_PREPARED_POINTS = 120_000;
    private static int preparedPoints;
    private static int cost(Line line){return line.path==null?0:line.count;}
    private static void prunePrepared() {
        WeakKey key;
        while ((key = (WeakKey) prepared.queue.poll()) != null) {
            Line removed = prepared.values.remove(key);
            if (removed != null) preparedPoints -= cost(removed);
        }
    }
    private static Line prepared(JSONArray points) {
        synchronized (prepared) {
            prunePrepared();
            return prepared.values.get(new WeakKey(points, null));
        }
    }
    static void prepare(java.util.List<JSONArray> rows) { prepare(rows, true); }
    static void prepareBounds(java.util.List<JSONArray> rows) { prepare(rows, false); }
    private static void prepare(java.util.List<JSONArray> rows, boolean allowPaths) {
        for (JSONArray points : rows) {
            if (Thread.currentThread().isInterrupted()) return;
            if (prepared(points) != null) continue;
            boolean build;
            synchronized (prepared) {
                build = allowPaths && preparedPoints + points.length() <= MAX_PREPARED_POINTS;
            }
            Line line = new Line(points, build);
            synchronized (prepared) {
                prunePrepared();
                if (prepared.values.containsKey(new WeakKey(points, null))) continue;
                if (prepared.values.size() >= 8192) {
                    var oldest = prepared.values.entrySet().iterator();
                    preparedPoints -= cost(oldest.next().getValue());
                    oldest.remove();
                }
                if (preparedPoints + cost(line) > MAX_PREPARED_POINTS) line = new Line(line);
                prepared.values.put(new WeakKey(points, prepared.queue), line);
                preparedPoints += cost(line);
            }
        }
    }
    static void invalidatePrepared(JSONArray points) {
        synchronized (prepared) {
            prunePrepared();
            Line removed = prepared.values.remove(new WeakKey(points, null));
            if (removed != null) preparedPoints -= cost(removed);
        }
    }
    static void clearPrepared() {
        synchronized (prepared) {
            prepared.clear();
            preparedPoints = 0;
        }
    }
    private final WeakIdentityLines lines=new WeakIdentityLines();
    private final RetainedLines retained=new RetainedLines();
    // Seven independently capped overview layers can exceed the worker's
    // 8,192 bounds entries. Keep enough lightweight bounds for all of them.
    private static final int MAX_VIEW_BOUNDS = 40_000;
    private static final int MAX_VIEW_PATH_POINTS = 100_000;
    private record Viewport(double scale, double x, double y, int width, int height) {}
    private record VisibleLayer(float padding, List<JSONArray> rows) {}
    private Viewport viewport;
    private boolean prunedPaths;
    private final Map<List<JSONArray>, Map<Paint, VisibleLayer>> visibleLayers = new IdentityHashMap<>();
    int vertices,builds,hits,culled;
    static double x(double lon){return lon*111319.49079327358;}
    static double y(double lat){return 6378137*Math.log(Math.tan(Math.PI/4+Math.toRadians(Math.max(-85.05112878,Math.min(85.05112878,lat)))/2));}
    private final Path screen=new Path();private final Matrix transform=new Matrix();private final RectF visible=new RectF();
    private final float[] transformValues = new float[]{1,0,0,0,-1,0,0,0,1};
    private void put(JSONArray points, Line line) {
        Line previous = retained.remove(points);
        if (previous != null) vertices -= cost(previous);
        if (line.path != null && vertices + cost(line) > MAX_VIEW_PATH_POINTS) {
            pruneOffscreenPaths();
            // Keep resident visible paths when the viewport itself exceeds
            // the budget. Sequential drawing must not evict the next line.
            if (vertices + cost(line) > MAX_VIEW_PATH_POINTS) line = new Line(line);
        }
        if (lines.size() >= MAX_VIEW_BOUNDS && lines.get(points) == null) return;
        lines.put(points, line);
        if (line.path != null) {
            retained.put(points, line);
            vertices += cost(line);
        }
    }

    void beginViewport(double scale, double x, double y, int width, int height) {
        Viewport next = new Viewport(scale, x, y, width, height);
        if (next.equals(viewport)) return;
        viewport = next;
        prunedPaths = false;
        visibleLayers.clear();
    }

    void invalidateLayer(List<JSONArray> rows) {
        visibleLayers.remove(rows);
    }

    void drawLayer(Canvas canvas, List<JSONArray> rows, Paint paint) {
        if (rows.isEmpty()) return;
        Map<Paint, VisibleLayer> byPaint = visibleLayers.computeIfAbsent(rows, unused -> new IdentityHashMap<>());
        float padding = Math.max(16, paint.getStrokeWidth());
        VisibleLayer cached = byPaint.get(paint);
        boolean reuse = cached != null && cached.padding == padding;
        List<JSONArray> visibleRows = reuse ? cached.rows : new ArrayList<>();
        for (JSONArray points : reuse ? visibleRows : rows) {
            boolean drawn = draw(canvas, points, paint, null,
                    viewport.scale, viewport.x, viewport.y, viewport.width, viewport.height, true, !reuse);
            if (drawn && !reuse) visibleRows.add(points);
        }
        if (!reuse) byPaint.put(paint, new VisibleLayer(padding, visibleRows));
    }

    private void pruneOffscreenPaths() {
        if (viewport == null || prunedPaths) return;
        prunedPaths = true;
        var entries = retained.values.entrySet().iterator();
        while (entries.hasNext()) {
            var entry = entries.next();
            Line line = entry.getValue();
            // Keep a small margin around the screen for nearby roads during a pan.
            if (offscreen(line, viewport.scale, viewport.x, viewport.y, viewport.width, viewport.height, 64)) {
                vertices -= cost(line);
                lines.put(entry.getKey().points, new Line(line));
                entries.remove();
            }
        }
    }

    private boolean offscreen(Line line, double scale, double cx, double cy, int width, int height, float padding) {
        float tx = (float)(width / 2.0 + (line.originX - cx) * scale);
        float ty = (float)(height / 2.0 - (line.originY - cy) * scale);
        visible.set((float)(line.bounds.left * scale + tx), (float)(-line.bounds.bottom * scale + ty),
                (float)(line.bounds.right * scale + tx), (float)(-line.bounds.top * scale + ty));
        return visible.right < -padding || visible.left > width + padding
                || visible.bottom < -padding || visible.top > height + padding;
    }

    boolean draw(Canvas canvas,JSONArray points,Paint paint,Paint halo,double scale,double centreX,double centreY,int width,int height){return draw(canvas,points,paint,halo,scale,centreX,centreY,width,height,true);}
    boolean draw(Canvas canvas,JSONArray points,Paint paint,Paint halo,double scale,double centreX,double centreY,int width,int height,boolean immutable){
        return draw(canvas, points, paint, halo, scale, centreX, centreY, width, height, immutable, true);
    }

    private boolean draw(Canvas canvas, JSONArray points, Paint paint, Paint halo, double scale,
                         double centreX, double centreY, int width, int height, boolean immutable, boolean cull) {
        Line previous = immutable ? lines.get(points) : null;
        Line line = previous;
        if (line == null || line.count != points.length()) {
            line = immutable ? prepared(points) : null;
            if (line == null || line.count != points.length()) line = new Line(points, false);
        }
        if (cull && offscreen(line, scale, centreX, centreY, width, height,
                Math.max(16, halo == null ? paint.getStrokeWidth() : halo.getStrokeWidth()))) {
            culled++;
            if (immutable && previous != line) put(points, line);
            return false;
        }
        if (line.path == null) {
            line = new Line(points, true);
            builds++;
        } else if (line == previous) {
            hits++;
        }
        if (immutable && previous != line) put(points, line);
        float tx = (float)(width / 2.0 + (line.originX - centreX) * scale);
        float ty = (float)(height / 2.0 - (line.originY - centreY) * scale);
        transformValues[0]=(float)scale;transformValues[2]=tx;
        transformValues[4]=(float)-scale;transformValues[5]=ty;
        transform.setValues(transformValues);line.path.transform(transform,screen);
        if(halo!=null)canvas.drawPath(screen,halo);canvas.drawPath(screen,paint);return true;
    }
    void clear(){lines.clear();retained.clear();visibleLayers.clear();viewport=null;vertices=0;}
}
