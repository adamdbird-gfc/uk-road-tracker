package com.roadprints.capture;

import android.location.Location;
import androidx.core.location.LocationCompat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/** Extends an already confirmed automatic start with measured, continuous departure evidence. */
final class JourneyOriginRecovery {
    private static final long LOOKBACK_MS = 180_000L;
    private static final long MAX_FIX_GAP_MS = 35_000L;
    private static final int MAX_FIXES = 120;
    private static final float MAX_ACCURACY_METRES = 25f;
    private final Deque<Location> recent = new ArrayDeque<>();
    private long firstObservedAt;

    static final class Result {
        final List<Location> prefix;
        final String status;

        Result(List<Location> prefix, String status) {
            this.prefix = prefix;
            this.status = status;
        }

        static Result unchanged(String reason) {
            return new Result(Collections.emptyList(), reason);
        }
    }

    void observe(Location fix) {
        if (firstObservedAt == 0 || fix.getTime() < firstObservedAt) firstObservedAt = fix.getTime();
        if (!usable(fix)) return;
        Location last = recent.peekLast();
        if (last != null && fix.getTime() <= last.getTime()) return;
        recent.addLast(new Location(fix));
        while (recent.size() > MAX_FIXES || fix.getTime() - recent.getFirst().getTime() > LOOKBACK_MS) {
            recent.removeFirst();
        }
    }

    void clear() {
        recent.clear();
        firstObservedAt = 0;
    }

    Result recover(List<Location> seed, String mode, long now) {
        if (seed.size() < 2) return Result.unchanged("insufficient_seed");
        Location first = seed.get(0);
        List<Location> trace = new ArrayList<>();
        for (Location fix : recent) {
            if (fix.getTime() >= first.getTime()) break;
            if (now - fix.getTime() <= LOOKBACK_MS) trace.add(fix);
        }
        if (trace.isEmpty()) return Result.unchanged(firstObservedAt != 0 && firstObservedAt < first.getTime()
                ? "no_earlier_usable_fix" : "no_earlier_location_callback");
        int firstSeed = trace.size();
        trace.addAll(seed);
        boolean foot = "walking".equals(mode) || "running".equals(mode);
        // Search the continuous suffix only. The newest stable pair is the last
        // observed stay before this movement, including a genuine brief drop-off.
        for (int i = firstSeed; i > 0; i--) {
            Location before = trace.get(i - 1);
            Location after = trace.get(i);
            if (!continuous(before, after, foot, now)) return Result.unchanged("broken_approach");
            if (i < 2 || !stablePair(trace.get(i - 2), before)) continue;
            if (!departureSupported(trace, i - 1, firstSeed, foot, mode, now)) {
                return Result.unchanged("departure_inconclusive");
            }
            return new Result(new ArrayList<>(trace.subList(i - 1, firstSeed)), "recovered");
        }
        return Result.unchanged("no_recent_stable_origin");
    }

    private static boolean departureSupported(List<Location> trace, int originIndex, int firstSeed,
                                              boolean foot, String mode, long now) {
        Location origin = trace.get(originIndex);
        Location previous = origin;
        int outside = 0;
        double greatestExtent = 0;
        List<FootTraceValidator.Sample> approach = new ArrayList<>();
        // Validate the entire recovered prefix and enough original observations to
        // corroborate the departure. Stop before unrelated later journey events.
        for (int i = originIndex + 1; i < trace.size(); i++) {
            Location fix = trace.get(i);
            if (!continuous(previous, fix, foot, now)) return false;
            double extent = origin.distanceTo(fix) - origin.getAccuracy() - fix.getAccuracy();
            if (extent < greatestExtent - 15 || stablePair(previous, fix)) return false;
            greatestExtent = Math.max(extent, greatestExtent);
            if (extent > 10) outside++;
            if ("cycling".equals(mode) && fix.hasSpeed() && fix.getSpeed() > 12f) return false;
            approach.add(CaptureService.footSample(fix, i));
            if (i >= firstSeed && outside >= 2 && greatestExtent >= (foot ? 20 : 35)) {
                // A confirmed walk to the car/platform belongs to the existing
                // multimodal logic, not the later vehicle leg's recovered prefix.
                return foot || !CaptureMovementEvidence.hasInitialFootLeg(approach);
            }
            previous = fix;
        }
        return false;
    }

    private static boolean stablePair(Location earlier, Location later) {
        long gap = later.getTime() - earlier.getTime();
        return usable(earlier) && usable(later) && gap >= 5_000L && gap <= MAX_FIX_GAP_MS
                && later.hasSpeed() && later.getSpeed() >= 0f && later.getSpeed() <= .5f
                && (!earlier.hasSpeed() || earlier.getSpeed() >= 0f && earlier.getSpeed() <= .5f)
                && earlier.distanceTo(later) <= Math.min(12f, earlier.getAccuracy() + later.getAccuracy());
    }

    private static boolean continuous(Location before, Location after, boolean foot, long now) {
        long gap = after.getTime() - before.getTime();
        return usable(before) && usable(after) && after.getTime() <= now
                && gap > 0 && gap <= MAX_FIX_GAP_MS
                && CaptureQualityValidator.reliable(CaptureService.footSample(before, 0),
                        CaptureService.footSample(after, 1), foot);
    }

    private static boolean usable(Location fix) {
        return !LocationCompat.isMock(fix) && fix.hasAccuracy() && fix.getAccuracy() <= MAX_ACCURACY_METRES
                && CaptureQualityValidator.reliable(null, CaptureService.footSample(fix, 0), true);
    }
}
