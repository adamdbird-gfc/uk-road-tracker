package com.roadprints.capture;
import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
/** Venue behaviour is defined once; identities, labels and qualification policy are data. */
final class VenueCollection {
    static final class Definition {
        interface CatalogueSource {JSONArray load(Context context)throws Exception;}
        final String id,name,singular,plural,brandPattern; final double radius; final boolean siteStop;
        final CatalogueSource source;
        Definition(String id,String name,String singular,String plural,double radius,boolean siteStop,String brandPattern,CatalogueSource source){
            this.id=id;this.name=name;this.singular=singular;this.plural=plural;this.radius=radius;this.siteStop=siteStop;this.brandPattern=brandPattern;this.source=source;
        }
    }
    private static final Definition[] ALL={
        new Definition("services","Motorway Service Stations","service station","service stations",125,true,"",ServiceStationStore::stations),
        new Definition("wetherspoons","UK Wetherspoons Collection","pub","pubs",125,false,"(j\\s*d\\s*)?wetherspoons?",CollectionMapCatalog::spoons)
    };
    static Definition[] all(){return ALL.clone();}
    static Definition definition(String id){for(Definition d:ALL)if(d.id.equals(id))return d;throw new IllegalArgumentException("Unknown venue collection");}
    static JSONArray catalogue(Context context,String id){
        try{return definition(id).source.load(context);}
        catch(Exception unavailable){throw new IllegalStateException("Collection catalogue unavailable",unavailable);}
    }
    static double radius(String id,JSONObject venue){return !definition(id).siteStop&&"Approximate building location".equals(venue.optString("location_label"))?300:definition(id).radius;}
    static double distance(JSONObject venue,double lat,double lng){return ServiceStationEvidence.distance(venue,lat,lng);}
    static java.util.Set<String> completed(Context context,String id){
        java.util.Set<String> ids=VenueVisitStore.completed(context,id);
        if(CollectionAccessStore.SERVICES.equals(id))return ServiceStationStore.completed(context);
        return ids;
    }
    static long revision(Context context,String id){return CollectionAccessStore.SERVICES.equals(id)?ServiceStationStore.revision(context):VenueVisitStore.revision(context,id);}
}
