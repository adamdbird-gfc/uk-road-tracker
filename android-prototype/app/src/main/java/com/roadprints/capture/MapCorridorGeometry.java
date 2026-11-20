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
                    || west == east || south >= north
                    || west < -180 || west > 180 || east < -180 || east > 180
                    || south < -90 || north > 90
                    || (west == -180 && east == 180))
                throw new IllegalArgumentException("Invalid geographic bounds");
        }
    }

    static Bounds around(List<JSONArray> accepted, double marginMetres) {
        if (accepted == null || accepted.isEmpty() || !Double.isFinite(marginMetres)
                || marginMetres < 0 || marginMetres > 500)
            throw new IllegalArgumentException("Missing route or unsafe margin");
        double south = Double.POSITIVE_INFINITY, north = Double.NEGATIVE_INFINITY;
        List<Double> longitudes = new ArrayList<>();
        for (JSONArray line : accepted) {
            if (line == null || line.length() < 2)
                throw new IllegalArgumentException("Invalid route geometry");
            for (int i = 0; i < line.length(); i++) {
                JSONArray point = line.optJSONArray(i);
                if (point == null || point.length() < 2)
                    throw new IllegalArgumentException("Invalid point");
                double x = point.optDouble(0, Double.NaN), y = point.optDouble(1, Double.NaN);
                if (!valid(x, y)) throw new IllegalArgumentException("Invalid coordinate");
                longitudes.add(x < 0 ? x + 360 : x);
                south = Math.min(south, y);
                north = Math.max(north, y);
            }
        }
        // The smallest circular interval containing the accepted longitudes
        // is the complement of their largest gap. Unlike naive min/max, this
        // keeps a Fiji-style route near ±180 rather than spanning the globe.
        java.util.Collections.sort(longitudes);
        double gap = -1;
        int gapAfter = -1;
        for (int i = 0; i < longitudes.size(); i++) {
            double start = longitudes.get(i);
            double next = i + 1 < longitudes.size()
                    ? longitudes.get(i + 1) : longitudes.get(0) + 360;
            if (next - start > gap) { gap = next - start; gapAfter = i; }
        }
        double extent = 360 - gap;
        if (extent > 180)
            throw new IllegalArgumentException(
                    "Global-spanning journey requires separate local validation windows");
        double start = longitudes.get((gapAfter + 1) % longitudes.size());
        double end = start + extent;
        double latMargin = marginMetres / 111_195.08;
        double lonMargin = latMargin / Math.max(0.01,
                Math.cos(Math.toRadians((south + north) / 2)));
        if (extent + 2 * lonMargin >= 360)
            throw new IllegalArgumentException("Window spans all longitudes");
        double west = wrapLongitude(start - lonMargin - 1e-8);
        double east = wrapLongitude(end + lonMargin + 1e-8);
        return new Bounds(west,
                Math.max(-90, south - latMargin - 1e-8),
                east, Math.min(90, north + latMargin + 1e-8));
    }

    private static double wrapLongitude(double x) {
        double wrapped = ((x + 180) % 360 + 360) % 360 - 180;
        return Math.abs(wrapped + 180) < 1e-10 && x > 0 ? 180 : wrapped;
    }

    /** Returns all clipped in-window pieces in input order without joining
     * across disjoint gaps. A wrapped west>east viewport is valid, and segments
     * follow the shortest longitude displacement across the antimeridian.
     */
    static List<JSONArray> clip(JSONArray route, Bounds box) {
        if (route == null || route.length() < 2 || box == null)
            throw new IllegalArgumentException("Missing route or bounds");
        List<JSONArray> output = new ArrayList<>();
        JSONArray current = null;
        // Unwrap the corridor into one continuous longitude interval.
        double west = box.west();
        double east = box.east() <= west ? box.east() + 360 : box.east();
        for (int i = 1; i < route.length(); i++) {
            JSONArray a = route.optJSONArray(i-1), b = route.optJSONArray(i);
            if (a == null || b == null || a.length() < 2 || b.length() < 2)
                throw new IllegalArgumentException("Invalid segment");
            double ax = a.optDouble(0, Double.NaN), ay = a.optDouble(1, Double.NaN);
            double bx = b.optDouble(0, Double.NaN), by = b.optDouble(1, Double.NaN);
            if (!valid(ax, ay) || !valid(bx, by))
                throw new IllegalArgumentException("Invalid segment coordinate");
            // Unwrap the segment to its shortest representation, then pick
            // whichever 360-degree copy is nearest the window centre.
            double delta = bx - ax;
            if (delta > 180) delta -= 360;
            if (delta < -180) delta += 360;
            bx = ax + delta;
            double centre = (west + east) / 2;
            double shift = 360 * Math.rint((centre - (ax + bx) / 2) / 360);
            ax += shift; bx += shift;
            double[] interval = segmentClip(ax, ay, bx, by, west, box.south(), east, box.north());
            if (interval == null || interval[1]-interval[0] <= 1e-12) {
                if (current != null && current.length() >= 2) output.add(current);
                current = null;
                continue;
            }
            double begin = interval[0], finish = interval[1];
            double sx = ax + (bx-ax)*begin, sy = ay+(by-ay)*begin;
            double ex = ax + (bx-ax)*finish, ey = ay+(by-ay)*finish;
            if (current == null) current = new JSONArray().put(point(wrapLongitude(sx),sy));
            else {
                JSONArray last = current.optJSONArray(current.length()-1);
                if (Math.abs(shortestDifference(last.optDouble(0), wrapLongitude(sx))) > 1e-10
                        || Math.abs(last.optDouble(1)-sy) > 1e-10) {
                    if (current.length() >= 2) output.add(current);
                    current = new JSONArray().put(point(wrapLongitude(sx),sy));
                }
            }
            current.put(point(wrapLongitude(ex),ey));
            if (finish < 1 - 1e-12) {
                if (current.length() >= 2) output.add(current);
                current = null;
            }
        }
        if (current != null && current.length() >= 2) output.add(current);
        return output;
    }

    private static double shortestDifference(double a, double b) {
        double diff = b - a;
        if (diff > 180) diff -= 360;
        if (diff < -180) diff += 360;
        return diff;
    }

    private static boolean valid(double lon,double lat) {
        return Double.isFinite(lon) && Double.isFinite(lat)
                && Math.abs(lon)<=180 && Math.abs(lat)<=90;
    }

    private static JSONArray point(double lon,double lat) {
        return new JSONArray().put(lon).put(lat);
    }

    private static double[] segmentClip(double ax,double ay,double bx,double by,
                                        double west,double south,double east,double north) {
        double dx=bx-ax,dy=by-ay;
        double[] p={-dx,dx,-dy,dy};
        double[] q={ax-west,east-ax,ay-south,north-ay};
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
