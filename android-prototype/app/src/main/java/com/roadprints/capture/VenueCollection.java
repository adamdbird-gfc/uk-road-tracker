package com.roadprints.capture;
import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
/** Venue behaviour is defined once; identities, labels and qualification policy are data. */
final class VenueCollection {
    static final class Definition {
        interface CatalogueSource {JSONArray load(Context context)throws Exception;}
        final String id,name,singular,plural,brandPattern,identityPattern,description,catalogueAsset,catalogueFormat,pinLabel,achievementType,achievementIcon; final int pinColor; final long bundledRevision; final JSONArray goals; final double radius; final boolean siteStop;
        final CatalogueSource source;
        Definition(String id,String name,String singular,String plural,double radius,boolean siteStop,String brandPattern,CatalogueSource source){
            this.id=id;this.name=name;this.singular=singular;this.plural=plural;this.radius=radius;this.siteStop=siteStop;this.brandPattern=brandPattern;this.source=source;this.identityPattern="[a-zA-Z0-9:-]+";this.description="";this.catalogueAsset="";this.catalogueFormat="reviewed_map_v1";this.pinLabel="";this.pinColor=0xFF238F89;this.achievementType="venue-visit";this.achievementIcon="";this.bundledRevision=1;this.goals=new JSONArray();
        }
        Definition(JSONObject row){
            id=row.optString("id");name=row.optString("name");singular=row.optString("singular");plural=row.optString("plural");
            radius=row.optDouble("radius");siteStop=row.optBoolean("site_stop");brandPattern=row.optString("brand_pattern");description=row.optString("description");identityPattern=row.optString("identity_pattern");
            catalogueAsset=row.optString("catalogue_asset");catalogueFormat=row.optString("catalogue_format");bundledRevision=row.optLong("bundled_revision");
            pinLabel=row.optString("pin_label");pinColor=android.graphics.Color.parseColor(row.optString("pin_color"));achievementType=row.optString("achievement_type");achievementIcon=row.optString("achievement_icon");goals=row.optJSONArray("achievements");
            source=context->"services_v1".equals(catalogueFormat)?ServiceStationStore.stations(context):CollectionMapCatalog.venues(context,this);
        }
    }
    private static final Definition[] ALL=load();
    private static Definition[] load(){JSONArray rows=VenueCollectionConfig.read().optJSONArray("collections");Definition[] definitions=new Definition[rows.length()];for(int i=0;i<rows.length();i++)definitions[i]=new Definition(rows.optJSONObject(i));return definitions;}
    static boolean known(String id){for(Definition d:ALL)if(d.id.equals(id))return true;return false;}
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
