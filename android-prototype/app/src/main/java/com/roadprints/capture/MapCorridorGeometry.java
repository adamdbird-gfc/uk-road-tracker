package com.roadprints.capture;

import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;

/**
 * Non-mutating bounded map-window geometry extraction for private A/B tests.
 * Geographic clipping is segment-based: a segment that crosses a viewport
 * is retained even if both its original vertices lie outside the viewport.
 *
 * This is not a map matching or road attribution implementation.
 */
final class MapCorridorGeometry {
    private MapCorridorGeometry() {}

    record Bounds(double west, double south, double east, double north) {
        Bounds {
            if (!Double.isFinite(west) || !Double.isFinite(east)
                    || !Double.isFinite(south) || !Double.isFinite(north)
                    || west >= east || south >= north
                    || west < -180 || east > 180 || south < -90 || north > 90)
                throw new IllegalArgumentException("Invalid geographic bounds");
        }
    }

    static Bounds around(List<JSONArray> accepted, double marginMetres) {
        if (accepted == null || accepted.isEmpty() || !Double.isFinite(marginMetres)
                || marginMetres < 0 || marginMetres > 500)
            throw new IllegalArgumentException("Missing route or unsafe margin");
        double west = Double.POSITIVE_INFINITY, east = Double.NEGATIVE_INFINITY;
        double south = Double.POSITIVE_INFINITY, north = Double.NEGATIVE_INFINITY;
        for (JSONArray line : accepted) {
            if (line == null || line.length() < 2)
                throw new IllegalArgumentException("Invalid route geometry");
            for (int i = 0; i < line.length(); i++) {
                JSONArray point = line.optJSONArray(i);
                if (point == null || point.length() < 2) throw new IllegalArgumentException("Invalid point");
                double x = point.optDouble(0, Double.NaN), y = point.optDouble(1, Double.NaN);
                if (!Double.isFinite(x) || !Double.isFinite(y)
                        || Math.abs(x) > 180 || Math.abs(y) > 90)
                    throw new IllegalArgumentException("Invalid coordinate");
                west = Math.min(west, x); east = Math.max(east, x);
                south = Math.min(south, y); north = Math.max(north, y);
            }
        }
        double latMargin = marginMetres / 111_195.08;
        double lonMargin = latMargin / Math.max(0.01, Math.cos(Math.toRadians((south+north)/2)));
        // Require a small nonzero window even for a straight horizontal street.
        return new Bounds(Math.max(-180, west-lonMargin-1e-8),
                Math.max(-90, south-latMargin-1e-8),
                Math.min(180, east+lonMargin+1e-8),
                Math.min(90, north+latMargin+1e-8));
    }

    /** Returns all clipped in-window pieces in input order without joining
     * across disjoint gaps; never inserts a speculative connector.
     */
    static List<JSONArray> clip(JSONArray route, Bounds box) {
        if (route == null || route.length() < 2 || box == null)
            throw new IllegalArgumentException("Missing route or bounds");
        List<JSONArray> output = new ArrayList<>();
        JSONArray current = null;
        for (int i = 1; i < route.length(); i++) {
            JSONArray a = route.optJSONArray(i-1), b = route.optJSONArray(i);
            if (a == null || b == null || a.length() < 2 || b.length() < 2)
                throw new IllegalArgumentException("Invalid segment");
            double ax=a.optDouble(0,Double.NaN), ay=a.optDouble(1,Double.NaN);
            double bx=b.optDouble(0,Double.NaN), by=b.optDouble(1,Double.NaN);
            if (!valid(ax,ay) || !valid(bx,by))
                throw new IllegalArgumentException("Invalid segment coordinate");
            double[] interval = segmentClip(ax,ay,bx,by,box);
            if (interval == null) {
                if (current != null && current.length() >= 2) output.add(current);
                current = null;
                continue;
            }
            double start=interval[0], end=interval[1];
            if (end-start <= 1e-12) continue; // no drawable length
            double sx=ax+(bx-ax)*start, sy=ay+(by-ay)*start;
            double ex=ax+(bx-ax)*end, ey=ay+(by-ay)*end;
            if (current == null) current=new JSONArray().put(point(sx,sy));
            else {
                JSONArray last=current.optJSONArray(current.length()-1);
                if (Math.abs(last.optDouble(0)-sx)>1e-10
                        || Math.abs(last.optDouble(1)-sy)>1e-10) {
                    if (current.length() >= 2) output.add(current);
                    current=new JSONArray().put(point(sx,sy));
                }
            }
            current.put(point(ex,ey));
            if (end < 1-1e-12) {
                if (current.length() >= 2) output.add(current);
                current=null;
            }
        }
        if (current != null && current.length() >= 2) output.add(current);
        return output;
    }

    private static boolean valid(double lon,double lat) {
        return Double.isFinite(lon) && Double.isFinite(lat)
                && Math.abs(lon)<=180 && Math.abs(lat)<=90;
    }

    private static JSONArray point(double lon,double lat) {
        return new JSONArray().put(lon).put(lat);
    }

    private static double[] segmentClip(double ax,double ay,double bx,double by,Bounds box) {
        double dx=bx-ax,dy=by-ay;
        double[] p={-dx,dx,-dy,dy};
        double[] q={ax-box.west(),box.east()-ax,ay-box.south(),box.north()-ay};
        double lo=0,hi=1;
        for (int i=0;i<4;i++) {
            if (Math.abs(p[i])<1e-15) {
                if(q[i]<0) return null;
            } else {
                double t=q[i]/p[i];
                if (p[i]<0) lo=Math.max(lo,t);
                else hi=Math.min(hi,t);
                if(lo>hi) return null;
            }
        }
        return new double[]{lo,hi};
    }
}
