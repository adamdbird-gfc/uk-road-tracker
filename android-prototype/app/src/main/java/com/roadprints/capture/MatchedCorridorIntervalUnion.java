package com.roadprints.capture;

import org.json.JSONArray;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Bounded, presentation-only interval reconciliation for SHORT, straight,
 * already-accepted matched segments without a canonical OSM edge ID.
 *
 * Two journeys may share only part of a street. Split their common straight
 * physical corridor at accepted fragment endpoints, preserving each source ID
 * exclusively on the intervals it actually traversed. Unknown curving routes,
 * nearby separate footpaths and ambiguous multiple alignments remain untouched.
 * Never awards road or NCN completion; never connects unobserved gaps.
 */
final class MatchedCorridorIntervalUnion {
    private static final double METRES = 111_195.08;
    private static final double MAX_CROSS_TRACK_METRES = 0.6;
    private static final double MIN_MATCHED_OVERLAP_METRES = 20;
    private static final double MIN_SEGMENT_METRES = 20;
    private static final double MAX_SEGMENT_METRES = 500;
    private static final double BOUNDARY_EPSILON_METRES = 0.02;

    record Result(List<JSONArray> lines, List<Set<String>> contributors,
                  Set<Integer> footIndices, int mergedInputs, int splitLines,
                  int ambiguous, boolean budgetHeld) {}

    private record Member(int index, double start, double end) {}
    private static final class Group {
        final double longitude, latitude, cosLat, ux, uy;
        final JSONArray original;
        final List<Member> members = new ArrayList<>();
        double min, max;
        Group(int index, JSONArray line, double metres) {
            JSONArray start = line.optJSONArray(0), end = line.optJSONArray(1);
            longitude = start.optDouble(0);
            latitude = start.optDouble(1);
            cosLat = Math.cos(Math.toRadians(latitude));
            ux = (end.optDouble(0) - longitude) * cosLat * METRES / metres;
            uy = (end.optDouble(1) - latitude) * METRES / metres;
            original = line;
            members.add(new Member(index, 0, metres));
            min = 0; max = metres;
        }
        Member project(int index, JSONArray line) {
            JSONArray a = line.optJSONArray(0), b = line.optJSONArray(1);
            double ax = (a.optDouble(0) - longitude) * cosLat * METRES;
            double ay = (a.optDouble(1) - latitude) * METRES;
            double bx = (b.optDouble(0) - longitude) * cosLat * METRES;
            double by = (b.optDouble(1) - latitude) * METRES;
            if (Math.abs(ux * ay - uy * ax) > MAX_CROSS_TRACK_METRES
                    || Math.abs(ux * by - uy * bx) > MAX_CROSS_TRACK_METRES) return null;
            double t1 = ax * ux + ay * uy, t2 = bx * ux + by * uy;
            double start = Math.min(t1, t2), end = Math.max(t1, t2);
            for (Member accepted : members)
                if (Math.min(end, accepted.end) - Math.max(start, accepted.start)
                        >= MIN_MATCHED_OVERLAP_METRES)
                    return new Member(index, start, end);
            return null;
        }
        JSONArray subline(double start, double end) {
            return new JSONArray().put(point(start)).put(point(end));
        }
        private JSONArray point(double metres) {
            try {
                return new JSONArray().put(longitude + metres * ux / (cosLat * METRES))
                        .put(latitude + metres * uy / METRES);
            } catch (org.json.JSONException invalid) {
                throw new IllegalStateException("Accepted corridor projection is non-finite", invalid);
            }
        }
    }
    private MatchedCorridorIntervalUnion() {}

    static Result reconcile(List<JSONArray> lines, List<Set<String>> contributors,
                            Set<Integer> footIndices, int maxLines, int maxPoints) {
        if (!MatchedCorridorAccumulator.coherent(lines, contributors, footIndices))
            return new Result(lines, contributors, footIndices, 0, 0, 0, true);
        if (lines.isEmpty() || lines.size() > maxLines || maxLines < 1 || maxPoints < 2)
            return new Result(lines, contributors, footIndices, 0, 0, 0, false);

        List<Group> groups = new ArrayList<>();
        int ambiguous = 0;
        for (int i = 0; i < lines.size(); i++) {
            JSONArray line = lines.get(i);
            double metres = twoPointMetres(line);
            if (metres < MIN_SEGMENT_METRES || metres > MAX_SEGMENT_METRES) {
                groups.add(new Group(i, line, 1)); // Invalid/curved retained unchanged below.
                continue;
            }
            Group chosen = null;
            Member member = null;
            for (Group group : groups) {
                if (group.members.get(0).index == i) continue;
                if (twoPointMetres(group.original) < MIN_SEGMENT_METRES) continue;
                Member overlap = group.project(i, line);
                if (overlap == null) continue;
                if (chosen != null) { chosen = null; ambiguous++; break; }
                chosen = group; member = overlap;
            }
            if (chosen == null) {
                groups.add(new Group(i, line, metres));
            } else {
                chosen.members.add(member);
                chosen.min = Math.min(chosen.min, member.start);
                chosen.max = Math.max(chosen.max, member.end);
            }
        }

        List<JSONArray> resultLines = new ArrayList<>();
        List<Set<String>> resultIds = new ArrayList<>();
        Set<Integer> resultFoot = new HashSet<>();
        int merged = 0, split = 0, pointCount = 0;
        for (Group group : groups) {
            if (group.members.size() == 1) {
                int original = group.members.get(0).index;
                resultLines.add(lines.get(original));
                resultIds.add(new HashSet<>(contributors.get(original)));
                if (footIndices.contains(original)) resultFoot.add(resultLines.size()-1);
                pointCount += lines.get(original).length();
                continue;
            }
            merged += group.members.size()-1;
            List<Double> boundaries = new ArrayList<>();
            for (Member m : group.members) { boundaries.add(m.start); boundaries.add(m.end); }
            Collections.sort(boundaries);
            List<Double> unique = new ArrayList<>();
            for (double t : boundaries)
                if (unique.isEmpty() || t - unique.get(unique.size()-1) > BOUNDARY_EPSILON_METRES)
                    unique.add(t);
            int produced = 0;
            for (int j = 1; j < unique.size(); j++) {
                double start = unique.get(j-1), end = unique.get(j);
                if (end - start <= BOUNDARY_EPSILON_METRES) continue;
                double mid = (start + end) / 2;
                Set<String> ids = new HashSet<>();
                boolean foot = false;
                for (Member m : group.members)
                    if (m.start <= mid && mid <= m.end) {
                        ids.addAll(contributors.get(m.index));
                        foot |= footIndices.contains(m.index);
                    }
                if (ids.isEmpty()) continue; // Unobserved gap: never create a connector.
                resultLines.add(group.subline(start, end));
                resultIds.add(ids);
                if (foot) resultFoot.add(resultLines.size()-1);
                pointCount += 2; produced++;
            }
            split += produced;
        }
        if (resultLines.size() > maxLines || pointCount > maxPoints
                || !MatchedCorridorAccumulator.coherent(resultLines, resultIds, resultFoot))
            return new Result(lines, contributors, footIndices, 0, 0, ambiguous, true);
        return new Result(resultLines, resultIds, resultFoot, merged, split, ambiguous, false);
    }

    private static double twoPointMetres(JSONArray line) {
        if (line == null || line.length() != 2) return -1;
        JSONArray a = line.optJSONArray(0), b = line.optJSONArray(1);
        if (a == null || b == null || a.length() < 2 || b.length() < 2) return -1;
        double ax = a.optDouble(0, Double.NaN), ay = a.optDouble(1, Double.NaN);
        double bx = b.optDouble(0, Double.NaN), by = b.optDouble(1, Double.NaN);
        if (!Double.isFinite(ax) || !Double.isFinite(ay)
                || !Double.isFinite(bx) || !Double.isFinite(by)
                || Math.abs(ax)>180 || Math.abs(bx)>180 || Math.abs(ay)>90 || Math.abs(by)>90)
            return -1;
        return METRES * Math.hypot((bx-ax)*Math.cos(Math.toRadians((ay+by)/2)),by-ay);
    }
}
