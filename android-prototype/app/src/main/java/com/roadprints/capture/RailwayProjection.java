package com.roadprints.capture;

/** WGS84 to UTM zone 30N, the projection pinned by the regional SQLite asset. */
final class RailwayProjection {
    private RailwayProjection() {}

    static RailwayMatcher.Fix fix(double lon,double lat,double accuracy,long time) {
        double p = Math.toRadians(lat);
        double a = Math.toRadians(lon+3)*Math.cos(p);
        double e = 0.0066943799901413165;
        double n = 6378137/Math.sqrt(1-e*Math.pow(Math.sin(p),2));
        double t = Math.pow(Math.tan(p),2);
        double c = e/(1-e)*Math.pow(Math.cos(p),2);
        double m = 6378137*((1-e/4-3*e*e/64-5*e*e*e/256)*p
                -(3*e/8+3*e*e/32+45*e*e*e/1024)*Math.sin(2*p)
                +(15*e*e/256+45*e*e*e/1024)*Math.sin(4*p)
                -35*e*e*e/3072*Math.sin(6*p));
        double x = 500000+.9996*n*(a+(1-t+c)*Math.pow(a,3)/6
                +(5-18*t+t*t+72*c-58*e/(1-e))*Math.pow(a,5)/120);
        double y = .9996*(m+n*Math.tan(p)*(a*a/2+(5-t+9*c+4*c*c)*Math.pow(a,4)/24
                +(61-58*t+t*t+600*c-330*e/(1-e))*Math.pow(a,6)/720));
        return new RailwayMatcher.Fix(x,y,accuracy,time);
    }
}
