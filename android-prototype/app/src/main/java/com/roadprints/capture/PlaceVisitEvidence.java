package com.roadprints.capture;

import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/** A place visit is not a geofence ban: sustained exercise can begin inside it. */
final class PlaceVisitEvidence {
    static final class Decision {
        final int startIndex;
        final String mode, reason;
        final int transitionIndex;
        final String nextMode;
        Decision(int index,String mode,String reason){this(index,mode,reason,-1,null);}
        Decision(int index,String mode,String reason,int transition,String next){
            startIndex=index;this.mode=mode;this.reason=reason;transitionIndex=transition;nextMode=next;
        }
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
        return inspect(stop,points,hint,points.isEmpty()?0:points.get(points.size()-1).time);
    }
    static Decision inspect(JSONObject stop,List<FootTraceValidator.Sample> points,String hint,long now) {
        if(!eligible(stop)||points.isEmpty())return null;
        // Inspect current support, never replay an earlier departure after a later stop.
        FootTraceValidator.Sample end=null,firstOutside=null,previous=null;
        int outsideCount=0;
        for(int i=points.size()-1;i>=0;i--) {
            FootTraceValidator.Sample p=points.get(i);
            if(!WalkingStillnessDetector.usable(p)||p.accuracy>35||p.time>now)continue;
            if(end==null)end=p;
            if(previous!=null&&(p.time>=previous.time||previous.time-p.time>90_000L
                    ||!CaptureQualityValidator.reliable(p,previous,false)))break;
            if(!outside(stop,p))break;
            firstOutside=p;outsideCount++;previous=p;
        }
        if(end==null||now-end.time>60_000L)return null;
        if(firstOutside!=null&&outsideCount>=3&&end.time-firstOutside.time>=30_000L) {
            String[] modes="cycling".equals(hint)?new String[]{"cycling","unknown","running","walking"}
                    :new String[]{"unknown","running","walking"};
            for(String mode:modes) {
                int start=CaptureMovementEvidence.modeChangeStart(points,mode,0,now);
                if(start<0||start>firstOutside.index)continue;
                FootTraceValidator.Sample origin=points.get(start);
                double extent=FootTraceValidator.metres(origin,end)-origin.accuracy-end.accuracy;
                if(extent<("unknown".equals(mode)?50:20))continue;
                // Preserve measured movement before the boundary crossing too.
                return "unknown".equals(mode)?vehicleDeparture(points,start):new Decision(start,mode,"departure_from_visit");
            }
        }
        // Real vehicle departure need not wait to leave a very large campus.
        if(!"cycling".equals(hint)) {
            int start=CaptureMovementEvidence.modeChangeStart(points,"unknown",0,now);
            if(start>=0&&FootTraceValidator.metres(points.get(start),end)
                    -points.get(start).accuracy-end.accuracy>=50)
                return vehicleDeparture(points,start);
        }
        // Use maximum extent, not start-to-end distance: closed loops qualify.
        for(String mode:("cycling".equals(hint)?new String[]{"cycling","walking"}
                :"running".equals(hint)?new String[]{"running","walking"}:new String[]{"running","unknown","walking"})) {
            FootTraceValidator.Sample start=exercise(points,mode,now);
            if(start!=null)return new Decision(start.index,mode,"sustained_activity_within_place");
        }
        return null;
    }
    private static Decision vehicleDeparture(List<FootTraceValidator.Sample> points,int vehicleStart) {
        // A short real walk to the car can occur wholly inside the venue. Once
        // vehicle departure is verified, retain that recent walk as its own leg.
        long vehicleTime=points.get(vehicleStart).time;
        for(int end=vehicleStart-1;end>=0&&vehicleTime-points.get(end).time<=120_000L;end--) {
            FootTraceValidator.Sample p=points.get(end);
            if(!WalkingStillnessDetector.usable(p)||p.accuracy>25)continue;
            if(p.speed>=6)break;
            if(p.speed<.5)continue;
            int start=CaptureMovementEvidence.modeChangeStart(points.subList(0,end+1),"walking",0,p.time);
            if(start>=0)return new Decision(start,"walking","walk_to_vehicle_departure",vehicleStart,"unknown");
        }
        return new Decision(vehicleStart,"unknown","vehicle_departure_within_place");
    }
    private static FootTraceValidator.Sample exercise(List<FootTraceValidator.Sample> points,String mode,long now) {
        boolean running="running".equals(mode),cycling="cycling".equals(mode)||"unknown".equals(mode);
        double minimum=running?2.2:cycling?3:.8, maximum=running?6:cycling?15:2.2;
        long duration=running||cycling?120_000L:360_000L;
        double required=running?220:cycling?350:450;
        FootTraceValidator.Sample first=null,previous=null;
        double path=0,extent=0;int count=0;
        for(FootTraceValidator.Sample p:points) {
            if(!WalkingStillnessDetector.usable(p)||p.accuracy>25||p.time>now)continue;
            if(previous!=null&&p.time<=previous.time)continue;
            if(!Double.isFinite(p.speed)||p.speed<0)continue;
            if(p.speed<minimum||p.speed>maximum
                    || previous!=null&&(p.time-previous.time>90_000L||!CaptureQualityValidator.reliable(previous,p,!cycling))) {
                first=null;previous=null;path=0;extent=0;count=0;continue;
            }
            if(first==null)first=p;
            if(previous!=null)path+=CaptureStartGate.reliableMovementIncrement((float)FootTraceValidator.metres(previous,p),
                    (float)previous.accuracy,(float)p.accuracy);
            extent=Math.max(extent,FootTraceValidator.metres(first,p)-first.accuracy-p.accuracy);
            count++;previous=p;
        }
        return first!=null&&previous!=null&&now-previous.time<=60_000L&&count>=5&&previous.time-first.time>=duration
                && path>=required&&extent>=(running||cycling?50:100)?first:null;
    }
}
