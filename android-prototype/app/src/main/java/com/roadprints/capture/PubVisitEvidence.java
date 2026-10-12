package com.roadprints.capture;

import android.location.Location;
import androidx.core.location.LocationCompat;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.Normalizer;
import java.time.Instant;
import java.util.Locale;

/** Named Timeline place visits or fresh, explicitly confirmed live check-ins. */
final class PubVisitEvidence {
    private PubVisitEvidence(){}
    static long start(JSONObject visit){
        try{return Instant.parse(visit.getString("start")).toEpochMilli();}
        catch(Exception invalid){return 0;}
    }
    static boolean validVisit(JSONObject visit,long now){
        try{
            long from=start(visit),to=Instant.parse(visit.getString("end")).toEpochMilli();
            return "google_timeline_placeVisit".equals(visit.optString("source"))
                    && !visit.optString("id").isEmpty() && from>0 && to-from>=60000 && to<=now
                    && Double.isFinite(visit.getDouble("lat"))&&Double.isFinite(visit.getDouble("lng"));
        }catch(Exception invalid){return false;}
    }
    static double radius(JSONObject pub){
        return "Approximate building location".equals(pub.optString("location_label"))?300:125;
    }
    static JSONArray candidates(JSONObject visit,JSONArray pubs,long now){
        JSONArray result=new JSONArray();
        if(!validVisit(visit,now))return result;
        double lat=visit.optDouble("lat"),lng=visit.optDouble("lng");
        for(int i=0;i<pubs.length();i++){
            JSONObject pub=pubs.optJSONObject(i);
            if(pub==null || Math.abs(lat-pub.optDouble("lat"))>.003
                    || Math.abs(lng-pub.optDouble("lng"))>.006)continue;
            if(ServiceStationEvidence.metres(lat,lng,pub.optDouble("lat"),pub.optDouble("lng"))<=radius(pub))
                result.put(pub);
        }
        return result;
    }
    static String namedMatch(JSONObject visit,JSONArray nearby){
        String name=normal(visit.optString("place_name"));
        if(name.isEmpty())return "";
        String found="";
        for(int i=0;i<nearby.length();i++){
            JSONObject pub=nearby.optJSONObject(i);
            // Newly reconciled/converted and shared-site identities need a
            // retrospective confirmation, even when Google's label names the pub.
            if(pub!=null && !pub.optString("id").startsWith("spoons-curated-")
                    && name.equals(normal(pub.optString("name")))){
                if(!found.isEmpty())return "";
                found=pub.optString("id");
            }
        }
        return found;
    }
    private static String normal(String value){
        return Normalizer.normalize(value,Normalizer.Form.NFKD).toLowerCase(Locale.ROOT)
                .replaceAll("\\p{M}","").replaceAll("(j\\s*d\\s*)?wetherspoons?","")
                .replaceAll("[^a-z0-9]+"," ").trim().replaceFirst("^the ","").trim();
    }
    static String liveProblem(JSONObject pub,Location fix,long nowNanos){
        if(fix==null)return "A fresh location could not be obtained. Please try again.";
        long age=nowNanos-fix.getElapsedRealtimeNanos();
        if(age<0||age>60_000_000_000L)return "The location is too old. Please try again.";
        if(LocationCompat.isMock(fix))return "A genuine device location is required.";
        if(!fix.hasAccuracy()||!Float.isFinite(fix.getAccuracy())||fix.getAccuracy()<=0||fix.getAccuracy()>50)
            return "Location accuracy is insufficient. Please try again when the signal improves.";
        double distance=ServiceStationEvidence.metres(fix.getLatitude(),fix.getLongitude(),
                pub.optDouble("lat",Double.NaN),pub.optDouble("lng",Double.NaN));
        if(distance+fix.getAccuracy()>radius(pub))
            return "Your location does not support a visit here. Move closer to the pub and try again.";
        return "";
    }
}
