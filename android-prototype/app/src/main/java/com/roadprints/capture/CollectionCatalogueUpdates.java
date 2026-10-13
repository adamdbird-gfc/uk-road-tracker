package com.roadprints.capture;
import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
/** Compatibility facade for the existing Wetherspoons release/cache contract. */
final class CollectionCatalogueUpdates {
    static final long BUNDLED_REVISION=VenueCollection.definition("wetherspoons").bundledRevision;
    static final long DAY_MS=VenueCatalogueUpdates.DAY_MS,RETRY_MS=VenueCatalogueUpdates.RETRY_MS;
    static final int MANIFEST_LIMIT=VenueCatalogueUpdates.MANIFEST_LIMIT;
    interface Transport {byte[] get(String filename,int limit)throws Exception;}
    private static VenueCatalogueUpdates store(){return VenueCatalogueUpdates.forCollection(VenueCollection.definition("wetherspoons"));}
    static long revision(){return store().revision();}
    static JSONArray pins(Context context,JSONObject bundled){return store().pins(context,bundled);}
    static void refreshIfDue(Context context,Runnable onChanged){for(VenueCollection.Definition collection:VenueCollection.all())VenueCatalogueUpdates.forCollection(collection).refreshIfDue(context,onChanged);}
    static boolean due(long now,long next){return VenueCatalogueUpdates.due(now,next);}
    static boolean refreshNow(Context context,Transport transport)throws Exception{return store().refreshNow(context,transport::get);}
    static void validateManifest(JSONObject manifest)throws Exception{store().validateManifest(manifest);}
    static JSONObject validatePayload(JSONObject manifest,byte[] bytes)throws Exception{return store().validatePayload(manifest,bytes);}
    static String sha256(byte[] bytes)throws Exception{return VenueCatalogueUpdates.sha256(bytes);}
    static byte[] readBounded(java.io.InputStream input,int limit)throws Exception{return VenueCatalogueUpdates.readBounded(input,limit);}
    static void resetForTests(){store().resetForTests();}
    private CollectionCatalogueUpdates(){}
}
