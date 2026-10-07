package com.roadprints.capture;

/** GPS corroboration for pedestrian stops; silence and inaccurate fixes are not stillness. */
final class WalkingStillnessDetector {
    static final long FRESH_MS=120_000L;
    private FootTraceValidator.Sample anchor, latest;
    private int observations;
    private boolean confirmed;
    private double movement;

    void reset() { anchor=null; latest=null; observations=0; confirmed=false; movement=0; }
    void accept(FootTraceValidator.Sample point) { accept(point,false); }
    void accept(FootTraceValidator.Sample point, boolean road) {
        if (!usable(point) || point.accuracy>50) return;
        if (latest!=null && point.time<=latest.time) return;
        if (latest!=null && !CaptureQualityValidator.reliable(latest,point,!road)) return;
        // Replay the same timer decision a live capture can make between fixes.
        // This still needs several corroborating fixes fresh at the five-minute mark.
        if(anchor!=null && latest!=null && observations>=3
                && point.time>=anchor.time+CaptureStartGate.STILLNESS_END_THRESHOLD_MS
                && latest.time>=anchor.time+CaptureStartGate.STILLNESS_END_THRESHOLD_MS-FRESH_MS)
            confirmed=true;
        if(latest!=null && point.time-latest.time<=FRESH_MS)
            movement+=Math.max(0,FootTraceValidator.metres(latest,point)-latest.accuracy-point.accuracy);
        if (anchor==null || (!confirmed && latest!=null && point.time-latest.time>FRESH_MS)
                || movement>=50 || FootTraceValidator.metres(anchor,point)>35+anchor.accuracy+point.accuracy) {
            anchor=point; observations=0; confirmed=false; movement=0;
        }
        latest=point; observations++;
        if (observations>=3 && point.time-anchor.time>=CaptureStartGate.STILLNESS_END_THRESHOLD_MS)
            confirmed=true;
    }
    boolean quiet(long now) {
        return anchor!=null && latest!=null && observations>=3 && now-latest.time<=FRESH_MS
                && now-anchor.time>=60_000L;
    }
    boolean canFinish(long now) {
        return quiet(now) && now-anchor.time>=CaptureStartGate.STILLNESS_END_THRESHOLD_MS;
    }
    int confirmedEndpoint() { return confirmed && anchor!=null ? anchor.index : -1; }
    FootTraceValidator.Sample anchor() { return anchor; }
    static boolean usable(FootTraceValidator.Sample p) {
        return Double.isFinite(p.lon) && Double.isFinite(p.lat) && Math.abs(p.lon)<=180
                && Math.abs(p.lat)<=90 && Double.isFinite(p.accuracy) && p.accuracy>=0
                && p.accuracy<=100 && p.time>0;
    }
}
