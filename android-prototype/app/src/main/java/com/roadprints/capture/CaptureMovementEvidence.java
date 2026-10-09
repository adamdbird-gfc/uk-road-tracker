package com.roadprints.capture;

import java.util.List;

/** Movement corroboration independent of Android's sometimes late activity labels. */
final class CaptureMovementEvidence {
    static boolean hasVehicleMovement(List<FootTraceValidator.Sample> samples) {
        FootTraceValidator.Sample first=null,previous=null;int count=0;double path=0;
        for(FootTraceValidator.Sample p:samples) {
            if(!WalkingStillnessDetector.usable(p)||p.accuracy>50||!Double.isFinite(p.speed)||p.speed<6
                    ||previous!=null&&(!CaptureQualityValidator.reliable(previous,p,false)||p.time-previous.time>60000)) {
                first=null;previous=null;count=0;path=0;continue;
            }
            if(first==null)first=p;
            if(previous!=null)path+=CaptureStartGate.reliableMovementIncrement((float)FootTraceValidator.metres(previous,p),(float)previous.accuracy,(float)p.accuracy);
            previous=p;count++;
            if(count>=3&&p.time-first.time>=20000&&path>=100)return true;
        }
        return false;
    }
    static boolean hasJourneyMovement(List<FootTraceValidator.Sample> samples, String mode) {
        if (samples.size() < 3) return false;
        FootTraceValidator.Sample origin = null, previous = null;
        double path = 0, extent = 0;
        long first = 0, last = 0;
        for (FootTraceValidator.Sample p : samples) {
            if (!WalkingStillnessDetector.usable(p) || p.accuracy > 50) continue;
            if (origin == null) { origin = p; first = p.time; }
            if (previous != null && CaptureQualityValidator.reliable(previous, p, false)
                    && p.time - previous.time <= 120_000L) {
                path += CaptureStartGate.reliableMovementIncrement((float)FootTraceValidator.metres(previous, p),
                        (float)previous.accuracy, (float)p.accuracy);
            }
            extent = Math.max(extent, FootTraceValidator.metres(origin, p) - origin.accuracy - p.accuracy);
            previous = p; last = p.time;
        }
        double threshold = "walking".equals(mode) || "running".equals(mode) ? 25 : 50;
        return last - first >= 20_000L && extent >= threshold && path >= threshold;
    }

    static boolean confirmsMode(List<FootTraceValidator.Sample> samples, String proposed, long since, long now) {
        int fixes = 0, moving = 0;
        FootTraceValidator.Sample first = null, previous = null, last = null;
        double path = 0;
        boolean foot = "walking".equals(proposed) || "running".equals(proposed);
        boolean cycle = "cycling".equals(proposed);
        for (FootTraceValidator.Sample p : samples) {
            if (p.time < since || !WalkingStillnessDetector.usable(p) || p.accuracy > 25) continue;
            if (previous != null && (!CaptureQualityValidator.reliable(previous, p, false)
                    || p.time - previous.time > 60_000L)) {
                fixes = 0; moving = 0; path = 0; first = null;
            }
            if (foot && p.speed >= 5) return false;
            if (first == null) first = p;
            if (previous != null && fixes > 0)
                path += CaptureStartGate.reliableMovementIncrement((float)FootTraceValidator.metres(previous, p),
                        (float)previous.accuracy, (float)p.accuracy);
            if (foot ? p.speed >= .5 && p.speed <= 3.5
                    : cycle ? p.speed >= .8 && p.speed <= 12 : p.speed >= 6) moving++;
            fixes++; previous = p; last = p;
        }
        return first != null && last != null && now - last.time <= 60_000L
                && last.time - first.time >= 20_000L && fixes >= 3 && moving >= 3
                && path >= (foot ? 30 : 60);
    }

    /** A long break is a recording boundary, not evidence of a routable connection. */
    static boolean separateSamplingSessions(FootTraceValidator.Sample before, FootTraceValidator.Sample after) {
        return before != null && WalkingStillnessDetector.usable(after) && after.accuracy <= 50
                && after.time - before.time >= 900_000L && after.speed >= 0 && after.speed < 2.5
                && FootTraceValidator.metres(before, after) <= 500;
    }
}
