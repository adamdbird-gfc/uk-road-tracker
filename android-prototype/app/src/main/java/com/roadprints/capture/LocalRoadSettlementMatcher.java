package com.roadprints.capture;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Resolves matched local-road geometry to public settlement facts. Route geometry is transient. */
final class LocalRoadSettlementMatcher {
    static final String API = "https://uk-road-tracker-api.onrender.com";
    private static final String CACHE = "roadprints_local_settlement_matches_v1";
    private static final String INVENTORIES = "roadprints_local_settlement_inventories_v1";

    static final class Settlement {
        final String code;
        final String name;
        String county; // Source administrative label, separate from historic geography.
        String region;
        String nation;
        List<String> historicCountyCodes = java.util.Collections.emptyList();
        List<String> historicCountyNames = java.util.Collections.emptyList();
        String historicCountyVersion = "";
        Settlement(String code, String name) { this(code, name, "", "", ""); }
        Settlement(String code, String name, String county, String region, String nation) {
            this.code = code;
            this.name = name;
            this.county = county == null ? "" : county;
            this.region = region == null ? "" : region;
            this.nation = nation == null ? "" : nation;
        }
    }

    private LocalRoadSettlementMatcher() {}

    static List<Settlement> resolve(Context context, String roadId, List<JSONObject> geometries)
            throws Exception {
        List<Settlement> cached = cached(context, roadId, geometries);
        if (cached != null) return cached;
        String cacheKey = roadId + ":" + Integer.toHexString(geometries.toString().hashCode());
        // The boundary endpoint accepts MultiLineString. Combine every matched
        // segment for this named road so a frequently travelled road only needs
        // one HTTP/PostGIS lookup, even when it appears in many journeys.
        JSONArray lines = new JSONArray();
        for (JSONObject geometry : geometries) {
            if (geometry == null) continue;
            String type = geometry.optString("type", "");
            JSONArray coordinates = geometry.optJSONArray("coordinates");
            if (coordinates == null) continue;
            if ("LineString".equals(type)) {
                if (coordinates.length() >= 2) lines.put(coordinates);
            } else if ("MultiLineString".equals(type)) {
                for (int index = 0; index < coordinates.length(); index++) {
                    JSONArray line = coordinates.optJSONArray(index);
                    if (line != null && line.length() >= 2) lines.put(line);
                }
            }
        }
        if (lines.length() == 0) return new ArrayList<>();
        JSONObject combined = new JSONObject().put("type", "MultiLineString")
                .put("coordinates", lines);
        JSONObject response = request("POST", API + "/settlements-for-geometry",
                new JSONObject().put("geometry", combined));
        Map<String, Settlement> result = new LinkedHashMap<>();
        for (Settlement settlement : decode(response.optJSONArray("settlements"))) {
            result.put(settlement.code.isEmpty() ? settlement.name : settlement.code, settlement);
        }
        List<Settlement> resolved = new ArrayList<>(result.values());
        if (needsMetadata(resolved)) hydrateMetadata(resolved);
        HistoricSettlementCounties.align(context, resolved);
        Thread.sleep(1050L);
        save(context, cacheKey, resolved);
        return resolved;
    }

    static List<Settlement> cached(
            Context context, String roadId, List<JSONObject> geometries) throws Exception {
        String cacheKey = roadId + ":" + Integer.toHexString(geometries.toString().hashCode());
        String saved = context.getSharedPreferences(CACHE, Context.MODE_PRIVATE)
                .getString(cacheKey, null);
        if (saved == null) return null;
        List<Settlement> rows = decode(new JSONObject(saved).optJSONArray("settlements"));
        HistoricSettlementCounties.align(context, rows);
        return rows;
    }

    static int cachedInventoryCount(Context context,String code) {
        String saved=context.getSharedPreferences(INVENTORIES,Context.MODE_PRIVATE).getString(code,null);
        if(saved==null)return -1;
        try { return Integer.parseInt(saved); } catch(NumberFormatException invalid) { return -1; }
    }

    static int inventoryCount(Context context, String code) throws Exception {
        String saved = context.getSharedPreferences(INVENTORIES, Context.MODE_PRIVATE)
                .getString(code, null);
        if (saved != null) return Integer.parseInt(saved);
        JSONObject response;
        try {
            response = request("GET", API + "/settlement-inventories/" + code, null);
        } catch (HttpFailure error) {
            if (error.status != 404) throw error;
            response = request("POST", API + "/settlement-inventories/request",
                    new JSONObject().put("settlement_code", code));
        }
        JSONObject inventory = response.optJSONObject("inventory");
        if (inventory == null) return -1;
        if ("not_requested".equals(inventory.optString("status"))) {
            response = request("POST", API + "/settlement-inventories/request",
                    new JSONObject().put("settlement_code", code));
            inventory = response.optJSONObject("inventory");
        }
        if (inventory == null || !"ready".equals(inventory.optString("status"))) return -1;
        int count = inventory.optInt("road_count", -1);
        if (count >= 0) {
            context.getSharedPreferences(INVENTORIES, Context.MODE_PRIVATE)
                    .edit().putString(code, Integer.toString(count)).apply();
            AchievementStore.advanceEvidenceRevision(context);
        }
        return …3389 tokens truncated…    || !m.getString("sha256").matches("[0-9a-f]{64}")
                || m.getInt("bytes")<2 || m.getInt("bytes")>CollectionMapCatalog.MAX_CATALOGUE_BYTES
                || m.getString("catalogue_version").isEmpty())
            throw new IllegalArgumentException("Unsupported venue manifest");
    }
    JSONObject validatePayload(JSONObject manifest,byte[] bytes) throws Exception {
        validateManifest(manifest);
        if(bytes.length!=manifest.getInt("bytes") || !sha256(bytes).equals(manifest.getString("sha256")))
            throw new IllegalArgumentException("Venue catalogue integrity check failed");
        JSONObject root=new JSONObject(new String(bytes,StandardCharsets.UTF_8));
        if("services_v1".equals(definition.catalogueFormat)){
            validateServices(root,manifest);return root;
        }
        if(root.getInt("schema_version")!=1 || !definition.id.equals(root.getString("collection_id"))
                || !"UK".equals(root.getString("scope")) || !"approved".equals(root.getString("release_status"))
                || !Boolean.TRUE.equals(root.opt("ready_for_app"))
                || !Boolean.FALSE.equals(root.opt("visit_enabled"))
                || !manifest.getString("catalogue_version").equals(root.getString("catalogue_version"))
                || root.getString("attribution").isEmpty())
            throw new IllegalArgumentException("Unsupported venue catalogue");
        VenueCatalogueApproval.validate(root);
        if("reviewed_uk_completion_v1".equals(root.optString("approval_scope"))&&!definition.lifecycleStartedAt.equals(root.optString("lifecycle_started_at")))throw new IllegalArgumentException("Catalogue cannot change lifecycle epoch");
        JSONArray venues=root.getJSONArray("venues");
        if(venues.length()>5000 || root.getJSONObject("review_summary").getInt("map_venues")!=venues.length())
            throw new IllegalArgumentException("Venue count does not match review");
        HashSet<String> ids=new HashSet<>(),aliases=new HashSet<>();
        LocalDate today=LocalDate.now(ZoneOffset.UTC);
        for(int i=0;i<venues.length();i++){
            JSONObject v=venues.getJSONObject(i);
            String id=v.getString("id");
            if(!id.matches(definition.identityPattern) || id.length()>200 || !ids.add(id)
                    || !"active".equals(v.getString("status")) || !"reviewed_current".equals(v.getString("operating_status"))
                    || !Boolean.FALSE.equals(v.opt("visit_enabled"))
                    || v.getString("name").trim().isEmpty() || v.getString("name").length()>200
                    || v.getString("town").length()>200
                    || LocalDate.parse(v.getString("map_reviewed_at")).isAfter(today))
                throw new IllegalArgumentException("Unreviewed or duplicate venue");
            double lat=v.getDouble("lat"),lon=v.getDouble("lon");
            if(!Double.isFinite(lat)||!Double.isFinite(lon)||lat<49||lat>61||lon< -9||lon>2)
                throw new IllegalArgumentException("Venue is outside UK coordinate bounds");
            if((!definition.confirmationIdPrefix.isEmpty()&&id.startsWith(definition.confirmationIdPrefix)) && (!Boolean.TRUE.equals(v.opt("confirmation_required"))
                    || v.getString("location_label").isEmpty() || v.getString("attribution").isEmpty()))
                throw new IllegalArgumentException("Curated venue labels are required");
            JSONArray names=v.getJSONArray("osm_aliases");
            for(int j=0;j<names.length();j++){
                String alias=names.getString(j);
                if(!alias.matches("(node|way|relation)/[0-9]+")||!aliases.add(alias))
                    throw new IllegalArgumentException("Duplicate venue source identity");
            }
        }
        if(CollectionMapCatalog.approvedVenues(root,definition).length()!=venues.length())
            throw new IllegalArgumentException("Pub map projection rejected");
        return root;
    }
    private void validateServices(JSONObject root,JSONObject manifest)throws Exception{
        if(root.getInt("schema_version")!=1||!definition.id.equals(root.getString("collection_id"))||!"approved".equals(root.getString("release_status"))||!"UK".equals(root.getString("scope"))||!manifest.getString("catalogue_version").equals(root.getString("catalogue_version"))||root.getString("attribution").isEmpty())throw new IllegalArgumentException("Unsupported site catalogue");
        JSONArray rows=root.getJSONArray("services");if(rows.length()==0||rows.length()>5000)throw new IllegalArgumentException("Invalid site count");
        HashSet<String> ids=new HashSet<>();
        for(int i=0;i<rows.length();i++){
            JSONObject v=rows.getJSONObject(i);String id=v.getString("id");
            if(!id.matches(definition.identityPattern)||!ids.add(id)||v.getString("name").isEmpty()||v.getString("road").isEmpty()||v.getString("country").isEmpty())throw new IllegalArgumentException("Invalid site identity");
            position(v);JSONArray points=v.getJSONArray("points");if(points.length()==0||points.length()>100)throw new IllegalArgumentException("Invalid site positions");for(int n=0;n<points.length();n++)position(points.getJSONObject(n));
        }
    }
    private static void position(JSONObject v)throws Exception{double lat=v.getDouble("lat"),lng=v.getDouble("lng");if(!Double.isFinite(lat)||!Double.isFinite(lng)||lat<49||lat>61||lng< -9||lng>2)throw new IllegalArgumentException("Invalid UK site position");}
    static String sha256(byte[] bytes) throws Exception {
        byte[] digest=MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder hex=new StringBuilder();
        for(byte b:digest)hex.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
        return hex.toString();
    }
    private byte[] download(String filename,int limit) throws Exception {
        if(!filename.equals("manifest-v1.json")&&!filename.matches("catalogue-[0-9]{10}\\.json"))
            throw new IllegalArgumentException("Unsupported venue file");
        HttpURLConnection connection=(HttpURLConnection)new URL(BASE_URL+definition.id+"/"+filename).openConnection();
        connection.setConnectTimeout(5000);connection.setReadTimeout(10000);
        connection.setInstanceFollowRedirects(false);
        try{
            if(connection.getResponseCode()!=200 || connection.getContentLengthLong()>limit)
                throw new java.io.IOException("Venue release unavailable");
            try(InputStream input=connection.getInputStream()){return readBounded(input,limit);}
        }finally{connection.disconnect();}
    }
    static byte[] readBounded(InputStream input,int limit) throws Exception {
        ByteArrayOutputStream output=new ByteArrayOutputStream();
        byte[] buffer=new byte[8192];int count;
        while((count=input.read(buffer))!=-1){
            if(output.size()+count>limit)throw new java.io.IOException("Venue file exceeds limit");
            output.write(buffer,0,count);
        }
        return output.toByteArray();
    }
    private AtomicFile cache(Context context){
        return new AtomicFile(new File(context.getFilesDir(),definition.id+"-map-cache-v1.json"));
    }
    void resetForTests(){synchronized(LOCK){current=null;pins=null;revision=definition.bundledRevision;}}
}

