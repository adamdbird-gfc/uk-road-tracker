package com.roadprints.capture;

import java.time.*;
import java.util.*;
import org.json.*;

/** Bounded route fingerprints and distinct local weekday dates; fresh on every evidence scan. */
final class GroundhogDayEvidence {
    static final int ANCHORS=65;
    static final class Trip {
        String mode,id; LocalDate date; double length; double[][] points;
    }
    static final class Pattern {
        final List<Trip> trips=new ArrayList<>();
        final Set<LocalDate> dates=new TreeSet<>();
    }
    final Map<String,List<Pattern>> buckets=new HashMap<>();
    Pattern best;

    void add(JSONObject journey) {
        Trip trip=read(journey);if(trip==null)return;
        int x=cell(trip.points[0][0]),y=cell(trip.points[0][1]);Pattern selected=null;
        for(int dx=-1;dx<=1&&selected==null;dx++)for(int dy=-1;dy<=1&&selected==null;dy++)
            for(Pattern pattern:buckets.getOrDefault(key(trip.mode,x+dx,y+dy),Collections.emptyList())) {
                boolean matches=true;
                for(Trip existing:pattern.trips)if(!similar(existing,trip)){matches=false;break;}
                if(matches){selected=pattern;break;}
            }
        if(selected==null) {
            selected=new Pattern();buckets.computeIfAbsent(key(trip.mode,x,y),k->new ArrayList<>()).add(selected);
        }
        // Five representatives are sufficient for one badge, and prevent duplicate
        // imports/same-day repeats growing memory or inflating the target.
        if(selected.dates.size()<5&&selected.dates.add(trip.date))selected.trips.add(trip);
        if(best==null||selected.dates.size()>best.dates.size())best=selected;
    }
    int value(){return best==null?0:best.dates.size();}
    List<String> contributions() {
        List<String> rows=new ArrayList<>();if(best==null)return rows;
        rows.add("Same "+best.trips.get(0).mode+" route and direction · "+value()+" / 5 weekday dates");
        for(LocalDate date:best.dates)rows.add("✓ "+date);
        return rows;
    }
    private static int cell(double coordinate){return (int)Math.floor(coordinate/.004);}
    private static String key(String mode,int x,int y){return mode+":"+x+":"+y;}
    static Trip read(JSONObject journey) {
        String mode=journey.optString("mode","").toLowerCase(Locale.ROOT);
        if("pedestrian".equals(mode))mode="walking";if("bicycle".equals(mode))mode="cycling";
        if(!TravelAchievementRoutes.eligible(mode)&&!"train".equals(mode))return null;
        String status=journey.optString("processing_status");
        if("failed".equals(status)||"recording".equals(status)||(!"train".equals(mode)&&!"complete".equals(status)))return null;
        Trip trip=new Trip();trip.mode=mode;trip.id=journey.optString("journey_id");
        try {
            String start=journey.getString("started_at");OffsetDateTime time=OffsetDateTime.parse(start);
            OffsetDateTime end=OffsetDateTime.parse(journey.getString("ended_at"));
            if(!end.toInstant().isAfter(time.toInstant()))return null;
            String zone=journey.optString("timezone","");
            ZoneId local=zone.isEmpty()?(start.endsWith("Z")?ZoneId.of("Europe/London"):time.getOffset()):ZoneId.of(zone);
            trip.date=time.toInstant().atZone(local).toLocalDate();
            if(trip.date.getDayOfWeek().getValue()>5)return null;
        }catch(Exception uncertain){return null;}
        JSONObject edits=journey.optJSONObject("journey_corrections");
        JSONArray removed=edits==null?null:edits.optJSONArray("removed_matched_segments");
        if((removed!=null&&removed.length()>0)||!JourneyCorrectionUtils.removedRoadIds(journey).isEmpty())return null;
        List<List<double[]>> parts=TravelAchievementRoutes.matched(journey);
        if(parts.isEmpty()&&"train".equals(mode)) {
            JSONObject raw=journey.optJSONObject("route_geometry");
            if(raw!=null&&raw.optBoolean("pattern_route_invalid",false))return null;
            JSONArray points=raw==null?null:raw.optJSONArray("coordinates");List<double[]> line=new ArrayList<>();
            if(points!=null)for(int i=0;i<points.length();i++){double[] p=TravelAchievementRoutes.point(points.optJSONArray(i));if(p==null)return null;line.add(p);}
            if(line.size()>1)parts.add(line);
        }
        List<double[]> line=new ArrayList<>();
        for(List<double[]> part:parts) {
            if(!line.isEmpty()&&TravelAchievementRoutes.metres(line.get(line.size()-1),part.get(0))>2)return null;
            for(double[] p:part)if(line.isEmpty()||TravelAchievementRoutes.metres(line.get(line.size()-1),p)>.2)line.add(p);
        }
        if(line.size()<4)return null; // endpoints alone cannot establish a broadly similar route
        double[] distances=new double[line.size()];
        for(int i=1;i<line.size();i++)distances[i]=distances[i-1]+TravelAchievementRoutes.metres(line.get(i-1),line.get(i));
        trip.length=distances[line.size()-1];if(trip.length<50)return null;
        trip.points=new double[ANCHORS][2];int edge=1;
        for(int i=0;i<ANCHORS;i++) {
            double at=trip.length*i/(ANCHORS-1);
            while(edge<distances.length-1&&distances[edge]<at)edge++;
            double t=(at-distances[edge-1])/(distances[edge]-distances[edge-1]);
            for(int axis=0;axis<2;axis++)trip.points[i][axis]=line.get(edge-1)[axis]+t*(line.get(edge)[axis]-line.get(edge-1)[axis]);
        }
        return trip;
    }
    static boolean similar(Trip a,Trip b) {
        if(!a.mode.equals(b.mode)||Math.max(a.length,b.length)>Math.min(a.length,b.length)*1.2)return false;
        double cap=TravelAchievementRoutes.foot(a.mode)?35:"train".equals(a.mode)?120:60;
        double tolerance=Math.min(cap,Math.max(8,Math.min(a.length,b.length)*.08));
        // Relative-distance anchors retain order and loop direction. Require every
        // anchor, not just shared endpoints or an average that hides a detour.
        for(int i=0;i<ANCHORS;i++)if(TravelAchievementRoutes.metres(a.points[i],b.points[i])>tolerance)return false;
        return true;
    }
}
