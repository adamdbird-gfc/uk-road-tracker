package com.roadprints.capture;
/** Missing provider speed can be corroborated by precise continuous displacement. */
final class MovementSpeed {
    static double foot(FootTraceValidator.Sample before,FootTraceValidator.Sample point) {
        if(Double.isFinite(point.speed)&&point.speed>=.5)return point.speed;
        if(before==null||before.accuracy>15||point.accuracy>15||before.accuracy<=0||point.accuracy<=0
                ||point.time-before.time<5000||point.time-before.time>60000
                ||!CaptureQualityValidator.reliable(before,point,true))return -1;
        double seconds=(point.time-before.time)/1000.0;
        double speed=Math.max(0,(FootTraceValidator.metres(before,point)-(before.accuracy+point.accuracy)*.5)/seconds);
        return speed>=.5&&speed<=3.5?speed:point.speed>=0?point.speed:-1; // Missing speed cannot infer vehicle, running or cycling intent.
    }
}
