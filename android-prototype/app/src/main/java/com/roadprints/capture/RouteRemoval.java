package com.roadprints.capture;

import static com.roadprints.capture.LocalRoadNetwork.Point;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Physical segments selected in the saved matched route, in editor edge order. */
final class RouteRemoval {
    record Segment(Point a, Point b) {
        double length() { return b.minus(a).length(); }
    }

    static List<Segment> selected(List<JSONArray> routes, Set<Integer> indices) {
        List<Segment> selected = new ArrayList<>();
        int index = 0;
        for (JSONArray route : routes) {
            for (int i = 1; i < route.length(); i++, index++) {
                if (!indices.contains(index)) continue;
                Segment segment = segment(route.optJSONArray(i - 1), route.optJSONArray(i));
                if (segment != null) selected.add(segment);
            }
        }
        return selected;
    }

    static Segment segment(JSONArray first, JSONArray last) {
        Point a = point(first), b = point(last);
        return a == null || b == null ? null : new Segment(a, b);
    }

    static JSONArray coordinate(Segment segment, double fraction) {
        Point delta = segment.b.minus(segment.a);
        JSONArray point = new JSONArray();
        // The coordinates are finite because the original segment was validated.
        point.put(Double.valueOf(segment.a.x() + fraction * delta.x()));
        point.put(Double.valueOf(segment.a.y() + fraction * delta.y()));
        return point;
    }

    private static Point point(JSONArray value) {
        if (value == null) return null;
        double lon = value.optDouble(0), lat = value.optDouble(1);
        if (!(Math.abs(lon) <= 180 && Math.abs(lat) <= 90)) return null;
        return new Point(lon, lat);
    }

    static Segment metres(Segment segment, double scaleX, double scaleY) {
        return new Segment(new Point(segment.a.x() * scaleX, segment.a.y() * scaleY),
                new Point(segment.b.x() * scaleX, segment.b.y() * scaleY));
    }

    /**
     * Return fractions along the subject that coincide with the removal. Require
     * alignment and endpoint agreement so crossings and parallel roads keep their credit.
     */
    static double[] overlap(Segment subject, Segment removal) {
        Point axis = subject.b.minus(subject.a), direction = removal.b.minus(removal.a);
        double length = axis.length(), removedLength = direction.length();
        if (length == 0 || removedLength == 0
                || Math.abs(axis.dot(direction)) < .995 * length * removedLength) return null;
        Point a = removal.a.minus(subject.a), b = removal.b.minus(subject.a);
        double t0 = a.dot(axis) / (length * length), t1 = b.dot(axis) / (length * length);
        double start = Math.max(0, Math.min(t0, t1)), end = Math.min(1, Math.max(t0, t1));
        if ((end - start) * length <= .05) return null;
        double side0 = axis.cross(a) / length, side1 = axis.cross(b) / length;
        double slope = (side1 - side0) / (t1 - t0);
        if (Math.abs(side0 + (start - t0) * slope) > .75
                || Math.abs(side0 + (end - t0) * slope) > .75) return null;
        return new double[]{start, end};
    }

    static boolean sameSection(Segment first, Segment second) {
        return first.a.minus(second.a).length() <= .25 && first.b.minus(second.b).length() <= .25
                || first.a.minus(second.b).length() <= .25 && first.b.minus(second.a).length() <= .25;
    }

    static boolean near(Segment a, Segment b, double radius) {
        if (Math.max(a.a.x(), a.b.x()) + radius < Math.min(b.a.x(), b.b.x())
                || Math.max(b.a.x(), b.b.x()) + radius < Math.min(a.a.x(), a.b.x())
                || Math.max(a.a.y(), a.b.y()) + radius < Math.min(b.a.y(), b.b.y())
                || Math.max(b.a.y(), b.b.y()) + radius < Math.min(a.a.y(), a.b.y())) return false;
        Point u = a.b.minus(a.a), v = b.b.minus(b.a), offset = b.a.minus(a.a);
        double cross = u.cross(v);
        if (cross != 0) {
            double t = offset.cross(v) / cross, s = offset.cross(u) / cross;
            if (t >= 0 && t <= 1 && s >= 0 && s <= 1) return true;
        }
        return distance(a.a, b) <= radius || distance(a.b, b) <= radius
                || distance(b.a, a) <= radius || distance(b.b, a) <= radius;
    }

    private static double distance(Point point, Segment segment) {
        Point axis = segment.b.minus(segment.a), offset = point.minus(segment.a);
        double squared = axis.dot(axis);
        double t = squared == 0 ? 0 : Math.max(0, Math.min(1, offset.dot(axis) / squared));
        return Math.hypot(offset.x() - t * axis.x(), offset.y() - t * axis.y());
    }
}
