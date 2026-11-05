package com.roadprints.capture;

import java.util.function.BooleanSupplier;
import net.sf.geographiclib.Geodesic;
import net.sf.geographiclib.GeodesicData;
import net.sf.geographiclib.GeodesicLine;

/** Pure evidence assessment; no persistence, Android lifecycle, catalogue activation or awards. */
final class MilepostProximity {
    enum Outcome {
        QUALIFIED, OUTSIDE_RADIUS, INELIGIBLE_MODE, INSUFFICIENT_GEOMETRY,
        UNSUPPORTED_GAP, UNRELIABLE_LOCATION, MARKER_INELIGIBLE, INVALID_MARKER,
        UNVALIDATED_MATCH, UNTRUSTED_SOURCE, CANCELLED
    }
    static final class Policy {
        static final Policy V1 = new Policy("milepost-proximity-1",100,0.00001);
        final String revision;
        final double radiusMetres, toleranceMetres;
        private Policy(String revision,double radius,double tolerance) {
            this.revision=revision; radiusMetres=radius; toleranceMetres=tolerance;
        }
    }
    static final class Point {
        final double lon,lat;
        Point(double lon,double lat){this.lon=lon;this.lat=lat;}
        boolean valid(){return Double.isFinite(lon)&&Double.isFinite(lat)&&Math.abs(lon)<=180&&Math.abs(lat)<=90;}
    }
    static final class Marker {
        final String physicalId,catalogueRevision;
        final Point point;
        final boolean eligible;
        Marker(String physicalId,Point point,boolean eligible,String catalogueRevision){
            this.physicalId=physicalId;this.point=point;this.eligible=eligible;this.catalogueRevision=catalogueRevision;
        }
    }
    /** An edge is supported only when the upstream validator proves its entire finite path.
     * No implicit links between edges; unknown timing is represented by journey date precision. */
    static final class Segment {
        final Point a,b;
        final int section,sourceStart,sourceEnd;
        Segment(Point a,Point b,int section,int sourceStart,int sourceEnd){
            this.a=a;this.b=b;this.section=section;this.sourceStart=sourceStart;this.sourceEnd=sourceEnd;
        }
    }
    /** Must be built by a trusted validator, never deserialized from a caller's 'validated=true'.
     * Marker historical validity and effective mode must already be resolved for this journey. */
    interface Evidence {
        String journeyId();
        String journeyDate();
        /** Complete source interval for dated-location ledger checks; missing remains pending. */
        default String journeyEndDate(){return null;}
        String mode();
        String source();
        String token();
        String validationRevision();
        Outcome problem();
        Iterable<Segment> segments();
    }
    static final class Result {
        final Outcome outcome;
        final String markerId,journeyId,journeyDate,mode,source,evidenceToken,validationRevision,
                catalogueRevision,policyRevision;
        final double closestMetres;
        final Point closestPoint;
        final int section,sourceStart,sourceEnd;
        final String validationStatus;
        final String datePrecision="journey";
        final String method="WGS84-geodesic-finite-segment";
        Result(Outcome outcome,Marker marker,Evidence evidence,Policy policy,double distance,Point nearest,Segment edge){
            this.outcome=outcome;validationStatus=outcome==Outcome.QUALIFIED||outcome==Outcome.OUTSIDE_RADIUS?"validated":"ineligible_or_unresolved";markerId=marker==null?null:marker.physicalId;
            catalogueRevision=marker==null?null:marker.catalogueRevision;
            journeyId=evidence==null?null:evidence.journeyId();journeyDate=evidence==null?null:evidence.journeyDate();
            mode=evidence==null?null:evidence.mode();source=evidence==null?null:evidence.source();
            evidenceToken=evidence==null?null:evidence.token();validationRevision=evidence==null?null:evidence.validationRevision();
            policyRevision=policy.revision;closestMetres=distance;closestPoint=nearest;
            section=edge==null?-1:edge.section;sourceStart=edge==null?-1:edge.sourceStart;sourceEnd=edge==null?-1:edge.sourceEnd;
        }
    }
    static boolean eligibleMode(String mode){return "walking".equals(mode)||"running".equals(mode)||"cycling".equals(mode);}
    static boolean text(String s){return s!=null&&!s.trim().isEmpty();}
    static Result assess(Marker marker,Evidence evidence,Policy policy,BooleanSupplier cancelled){
        Outcome failure=null;
        if(marker==null||marker.point==null||!marker.point.valid()||!text(marker.catalogueRevision))failure=Outcome.INVALID_MARKER;
        else if(!marker.eligible)failure=Outcome.MARKER_INELIGIBLE;
        else if(!text(marker.physicalId))failure=Outcome.INVALID_MARKER;
        else if(evidence==null)failure=Outcome.INSUFFICIENT_GEOMETRY;
        else if(!eligibleMode(evidence.mode()))failure=Outcome.INELIGIBLE_MODE;
        else if(!text(evidence.journeyId())||!text(evidence.journeyDate())||!text(evidence.token())
                ||!text(evidence.source())||!text(evidence.validationRevision()))failure=Outcome.INSUFFICIENT_GEOMETRY;
        else failure=evidence.problem();
        if(failure!=null)return new Result(failure,marker,evidence,policy,Double.NaN,null,null);
        double best=Double.POSITIVE_INFINITY;Point nearest=null;Segment winner=null;
        for(Segment edge:evidence.segments()){
            if(cancelled.getAsBoolean())return new Result(Outcome.CANCELLED,marker,evidence,policy,Double.NaN,null,null);
            if(edge==null||edge.a==null||edge.b==null||!edge.a.valid()||!edge.b.valid()
                    ||edge.section<0||edge.sourceStart<0||edge.sourceEnd<=edge.sourceStart)
                return new Result(Outcome.UNRELIABLE_LOCATION,marker,evidence,policy,Double.NaN,null,null);
            GeodesicData span=Geodesic.WGS84.Inverse(edge.a.lat,edge.a.lon,edge.b.lat,edge.b.lon);
            // No stationary point, whole-road or long straight connector can serve as a supported edge.
            if(span.s12<=0||span.s12>10000)return new Result(Outcome.INSUFFICIENT_GEOMETRY,marker,evidence,policy,Double.NaN,null,null);
            GeodesicLine line=Geodesic.WGS84.InverseLine(edge.a.lat,edge.a.lon,edge.b.lat,edge.b.lon);
            // Conservative triangle bound saves geodesic minimisation on distant edges.
            double da=distance(marker.point,edge.a),db=distance(marker.point,edge.b);
            if(Math.min(da,db)-span.s12>best)continue;
            double lo=0,hi=span.s12;
            // Convex local minimum on a short geodesic; endpoints included explicitly below.
            for(int i=0;i<58;i++){
                double left=lo+(hi-lo)/3,right=hi-(hi-lo)/3;
                if(distance(marker.point,position(line,left))<=distance(marker.point,position(line,right)))hi=right;else lo=left;
            }
            Point p=position(line,(lo+hi)/2);double d=distance(marker.point,p);
            if(da<=d){d=da;p=edge.a;}if(db<d){d=db;p=edge.b;}
            if(d<best){best=d;nearest=p;winner=edge;}
        }
        if(winner==null){
            return new Result(best==Double.POSITIVE_INFINITY?Outcome.INSUFFICIENT_GEOMETRY:Outcome.OUTSIDE_RADIUS,
                    marker,evidence,policy,Double.NaN,null,null);
        }
        return new Result(best<=policy.radiusMetres+policy.toleranceMetres?Outcome.QUALIFIED:Outcome.OUTSIDE_RADIUS,
                marker,evidence,policy,best,nearest,winner);
    }
    private static Point position(GeodesicLine line,double metres){GeodesicData p=line.Position(metres);return new Point(p.lon2,p.lat2);}
    static double distance(Point a,Point b){return Geodesic.WGS84.Inverse(a.lat,a.lon,b.lat,b.lon).s12;}
}
