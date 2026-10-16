package com.roadprints.capture;
import android.location.Location;
import androidx.core.location.LocationCompat;
import org.json.JSONArray;
import org.json.JSONObject;
import java.time.Instant;
import java.text.Normalizer;
import java.util.Locale;
/** Shared qualification; map pins alone never certify an individual premises visit. */
final class VenueEvidence {
    static JSONArray candidates(String collection,JSONObject visit,JSONArray venues,long now){
        JSONArray out=new JSONArray();if(!PubVisitEvidence.validVisit(visit,now))return out;
        double lat=visit.optDouble("lat"),lng=visit.optDouble("lng");
        for(int i=0;i<venues.length();i++){
            JSONObject venue=venues.optJSONObject(i);if(venue==null)continue;
            if(VenueCollection.supports(venue,lat,lng,0,VenueCollection.radius(collection,venue)))out.put(venue);
        }return out;
    }
    static String namedMatch(String collection,JSONObject visit,JSONArray nearby){return namedMatch(VenueCollection.definition(collection),visit,nearby);}
    static String namedMatch(VenueCollection.Definition definition,JSONObject visit,JSONArray nearby){
        String name=identityName(definition,visit.optString("place_name")),id="";
        if(name.isEmpty())return "";
        for(int i=0;i<nearby.length();i++){
            JSONObject venue=nearby.optJSONObject(i);
            if(venue==null||requiresConfirmation(definition,venue))continue;
            if(name.equals(identityName(definition,venue.optString("name")))){if(!id.isEmpty())return "";id=venue.optString("id");}
        }return id;
    }
    private static boolean requiresConfirmation(VenueCollection.Definition definition,JSONObject venue){return venue.optBoolean("confirmation_required")||(!definition.confirmationIdPrefix.isEmpty()&&venue.optString("id").startsWith(definition.confirmationIdPrefix))||"Approximate building location".equals(venue.optString("location_label"));}
    private static String identityName(VenueCollection.Definition definition,String name){
        String value=Normalizer.normalize(name,Normalizer.Form.NFKD).toLowerCase(Locale.ROOT).replaceAll("\\p{M}","");
        if(!definition.brandPattern.isEmpty())value=value.replaceAll(definition.brandPattern,"");
        return value.replaceAll("[^a-z0-9]+"," ").trim().replaceFirst("^the ","").trim();
    }
    static String liveProblem(String collection,JSONObject venue,Location fix,long nowNanos){
        if(fix==null)return "A fresh location could not be obtained. Please try again.";
        long age=nowNanos-fix.getElapsedRealtimeNanos();
        if(age<0||age>60_000_000_000L)return "The location is too old. Please try again.";
        if(LocationCompat.isMock(fix))return "A genuine device location is required.";
        if(!fix.hasAccuracy()||!Float.isFinite(fix.getAccuracy())||fix.getAccuracy()<=0||fix.getAccuracy()>50)return "Location accuracy is insufficient. Please try again when the signal improves.";
        if(!VenueCollection.supports(venue,fix.getLatitude(),fix.getLongitude(),fix.getAccuracy(),VenueCollection.radius(collection,venue)))
            return ServiceSiteBoundary.present(venue)?"Your location could not be verified inside this services site. Check the selected services and try again when the signal improves.":"Your location does not support a visit here. Move closer and try again.";
        return "";
    }
    static JSONArray nativeVisits(String collection,JSONObject journey,JSONArray venues)throws Exception{return nativeVisits(VenueCollection.definition(collection),journey,venues);}
    static JSONArray nativeVisits(VenueCollection.Definition definition,JSONObject journey,JSONArray venues)throws Exception{
        JSONArray out=new JSONArray();JSONObject source=journey.optJSONObject("source");
        if(source==null||!"android_activity_capture".equals(source.optString("type")))return out;
        JSONArray samples=journey.optJSONArray("raw_capture_samples");if(samples==null)samples=journey.optJSONArray("capture_route_samples");
        // The same accurate stop detector supplies candidate evidence for both catalogues.
        JSONArray stops=journey.optJSONArray("stops");
        for(VenueStopDetector.Stop observed:VenueStopDetector.stops(samples,venues)){
            String id=observed.id;JSONObject venue=VenueVisitStore.find(venues,id);if(venue==null)continue;
            long at=observed.first;JSONObject evidence=null;
            if(!VenueCollection.supports(venue,observed.lat,observed.lng,0,definition.radius))continue;
            boolean automatic=definition.siteStop&&!requiresConfirmation(definition,venue);
            if(stops!=null)for(int i=0;i<stops.length();i++){
                JSONObject stop=stops.optJSONObject(i);if(stop==null||!"arrival".equals(stop.optString("role")))continue;
                double accuracy=stop.optDouble("accuracy_m",Double.NaN);
                if(!Double.isFinite(accuracy)||accuracy<=0||accuracy>50||stop.optLong("dwell_seconds")<180
                        ||"rejected".equals(stop.optString("classification"))
                        ||!VenueCollection.supports(venue,stop.optDouble("latitude"),stop.optDouble("longitude"),0,definition.radius))continue;
                evidence=stop;
                // A uniquely named supported premises stop supplies identity; broad/shared sites require review.
                JSONArray nearby=new JSONArray();
                for(int j=0;j<venues.length();j++){
                    JSONObject candidate=venues.optJSONObject(j);if(candidate!=null&&VenueCollection.supports(candidate,stop.optDouble("latitude"),stop.optDouble("longitude"),0,definition.radius))nearby.put(candidate);
                }
                JSONObject named=new JSONObject().put("place_name",stop.optString("name"));
                boolean identity=id.equals(namedMatch(definition,named,nearby));
                automatic|=identity&&("supported".equals(stop.optString("classification"))||"confirmed".equals(stop.optString("classification")))
                        &&!requiresConfirmation(definition,venue)
                        &&"map_footprint_and_dwell".equals(stop.optString("source"));
            }
            // Unknown nearby GPS stops are review proposals, never automatic pub credit.
            if(evidence==null)evidence=new JSONObject().put("source","raw_timed_stationary_samples").put("from_ms",observed.first).put("to_ms",observed.last).put("lat",observed.lat).put("lng",observed.lng).put("accuracy_m",observed.accuracy);
            out.put(new JSONObject().put("venue",venue).put("automatic",automatic).put("stop_key",id+":"+at)
                    .put("visited_at",Instant.ofEpochMilli(at).toString()).put("evidence",evidence));
        }return out;
    }
    static String normal(String name){return Normalizer.normalize(name,Normalizer.Form.NFKD).toLowerCase(Locale.ROOT).replaceAll("\\p{M}","").replaceAll("[^a-z0-9]+"," ").trim();}
}
