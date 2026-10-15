package com.roadprints.capture;

import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/** A place visit is not a geofence ban: sustained exercise can begin inside it. */
final class PlaceVisitEvidence {
    static final class Decision {
        final int startIndex;
        final String mode, reason;
        Decision(int index,String mode,String reason){startIndex=index;this.mode=mode;this.reason=reason;}
    }
    static boolean eligible(JSONObject stop) {
        return stop!=null && !"rejected".equals(stop.optString("classification"))
                && !"station".equals(stop.optString("kind"))
                && Double.isFinite(stop.optDouble("latitude")) && Double.isFinite(stop.optDouble("longitude"))
                && Math.abs(stop.optDouble("latitude"))<=90 && Math.abs(stop.optDouble("longitude"))<=180
                && stop.optDouble("accuracy_m",1000)>0 && stop.optDouble("accuracy_m",1000)<=50;
    }
    static boolean outside(JSONObject stop,FootTraceValidator.Sample p) {
        JSONArray ring=stop.optJSONArray("site_geometry"),holes=stop.optJSONArray("site_holes");
        if(ring!=null && ring.length()>=4) {
            boolean inside=ContextMap.inside(ring,p.lat,p.lon);
            double edge=ContextMap.distance(ring,p.lat,p.lon);
            if(holes!=null)for(int i=0;i<holes.length();i++) {
                JSONArray hole=holes.optJSONArray(i);
                if(ContextMap.inside(hole,p.lat,p.lon))inside=false;
                edge=Math.min(edge,ContextMap.distance(hole,p.lat,p.lon));
            }
            return !inside && edge>p.accuracy+10;
        }
        // Offline fallback stays deliberately local; it never grows to fit an entire route.
        double radius=Math.max(60,35+stop.optDouble("accuracy_m",25));
        return RailStationCatalog.metres(stop.optDouble("latitude"),stop.optDouble("longitude"),p.lat,p.lon)
                > radius+p.accuracy+10;
    }
    static Decision inspect(JSONObject stop,List<FootTraceValidator.Sample> points) {
        return inspect(stop,points,null);
    }
    static Decision inspect(JSONObject stop,List<FootTraceValidator.Sample> points,String hint) {
        if(!eligible(stop))return null;
        FootTraceValidator.Sample firstOutside=null,previous=null;
        int outsideCount=0;
        for(FootTraceValidator.Sample p:points) {
            if(!WalkingStillnessDetector.usable(p)||p.accuracy>35)continue;
            if(previous!=null && p.time<=previous.time)continue;
            if(previous!=null && (p.time-previous.time>90_000L || !CaptureQualityValidator.reliable(previous,p,false))) {
                firstOutside=null;outsideCount=0;
            }
            if(outside(stop,p)) {
                if(firstOutside==null)firstOutside=p;
                outsideCount++;
                if(outsideCount>=3 && p.time-firstOutside.time>=30_000L
                        && CaptureMovementEvidence.hasJourneyMovement(points.subList(firstOutside.index, p.index+1), "walking")) {
                    String mode="cycling".equals(hint) && CaptureMovementEvidence.confirmsMode(points,"cycling",firstOutside.time,p.time)?"cycling":
                            exercise(points,"running")!=null?"running":
                            CaptureMovementEvidence.hasVehicleMovement(points)?"unknown":"walking";
                    return new Decision(firstOutside.index,mode,"departure_from_visit");
                }
            } else {firstOutside=null;outsideCount=0;}
            previous=p;
        }
        // Use extent across the session, not start-to-end distance: closed loops qualify.
        for(String mode:("cycling".equals(hint)?new String[]{"cycling","running","walking"}:new String[]{"running","cycling","walking"})) {
            FootTraceValidator.Sample start=exercise(points,mode);
            if(start!=null)return new Decision(start.index,mode,"sustained_activity_within_place");
        }
        return null;
    }
    private static FootTraceValidator.Sample exercise(List<FootTraceValidator.Sample> points,String mode) {
        boolean running="running".equals(mode),cycling="cycling".equals(mode);
        double minimum=running?2.2:cycling?3:.8, maximum=running?6:cycling?15:2.2;
        long duration=running||cycling?120_000L:360_000L;
        double required=running?220:cycling?350:450;
        FootTraceValidator.Sample first=null,previous=null;
        double path=0,extent=0;int count=0;
        for(FootTraceValidator.Sample p:points) {
            if(!WalkingStillnessDetector.usable(p)||p.accuracy>25)continue;
            if(previous!=null&&p.time<=previous.time)continue;
            if(!Double.isFinite(p.speed)||p.speed<0)continue;
            if(p.speed<minimum||p.speed>maximum
                    || previous!=null&&(p.time-previous.time>45_000L||!CaptureQualityValidator.reliable(previous,p,!cycling))) {
                first=null;previous=null;path=0;extent=0;count=0;continue;
            }
            if(first==null)first=p;
            if(previous!=null)path+=CaptureStartGate.reliableMovementIncrement((float)FootTraceValidator.metres(previous,p),
                    (float)previous.accuracy,(float)p.accuracy);
            extent=Math.max(extent,FootTraceValidator.metres(first,p)-first.accuracy-p.accuracy);
            count++;previous=p;
            if(count>=5 && p.time-first.time>=duration && path>=required && extent>=(running||cycling?50:100))return first;
        }
        return null;
    }
}
