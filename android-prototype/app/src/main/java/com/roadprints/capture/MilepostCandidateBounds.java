package com.roadprints.capture;

import java.util.function.BooleanSupplier;

/** Conservative WGS84 surface-distance envelope, never a proximity decision.
 * WGS84's minimum meridional radius exceeds 6,335,000m. Thus surface length
 * bounds central angle using this smaller radius. A cap about edge.a with
 * radius edge length + 100m contains the whole finite edge and its buffer.
 * Longitude is unwrapped about the first cap, then split at the date line.
 * Disconnected edges enlarge the envelope but never invent connecting evidence. */
final class MilepostCandidateBounds {
    double south=90,north=-90,west=Double.POSITIVE_INFINITY,east=Double.NEGATIVE_INFINITY;
    double origin;
    boolean any, global;
    static MilepostCandidateBounds of(MilepostProximity.Evidence e, BooleanSupplier cancelled) {
        MilepostCandidateBounds out=new MilepostCandidateBounds();
        if(e.problem()!=null)return out;
        int count=0;
        for(MilepostProximity.Segment s:e.segments()) {
            if(cancelled.getAsBoolean()||Thread.currentThread().isInterrupted())throw new java.util.concurrent.CancellationException();
            if(++count>MilepostJourneyEvidence.MAX_POINTS)throw new IllegalArgumentException("Unbounded evidence");
            if(s==null||s.a==null||s.b==null||!s.a.valid()||!s.b.valid()||s.section<0||s.sourceStart<0||s.sourceEnd<=s.sourceStart) {
                out.global=true;return out; // Let exact evaluator report malformed evidence.
            }
            double length=MilepostProximity.distance(s.a,s.b);
            if(!(length>0)||length>10000) {out.global=true;return out;}
            if(!out.any)out.origin=s.a.lon;
            out.any=true;
            double angle=(length+100.001)/6335000.0; // Includes evaluator tolerance + outward margin.
            double pad=Math.toDegrees(angle);
            out.south=Math.min(out.south,Math.max(-90,s.a.lat-pad));
            out.north=Math.max(out.north,Math.min(90,s.a.lat+pad));
            double lonPad=180;
            if(Math.abs(s.a.lat)+pad<90)lonPad=Math.toDegrees(Math.asin(Math.min(1,Math.sin(angle)/Math.cos(Math.toRadians(s.a.lat)))));
            double centre=out.origin+wrap(s.a.lon-out.origin);
            out.west=Math.min(out.west,centre-lonPad);out.east=Math.max(out.east,centre+lonPad);
        }
        return out;
    }
    static double wrap(double lon){return ((lon+180)%360+360)%360-180;}
    String sql() {
        if(global)return "1=1";
        if(!any)return "1=0";
        String latitude="lat >= "+south+" AND lat <= "+north;
        if(east-west>=360)return latitude;
        double w=wrap(west), e=wrap(east);
        return latitude+" AND ("+(w<=e?"lon >= "+w+" AND lon <= "+e:"lon >= "+w+" OR lon <= "+e)+")";
    }
}
