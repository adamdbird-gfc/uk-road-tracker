package com.roadprints.capture;

import android.content.Context;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import org.json.*;

/** Sole production catalogue reader. Reviewed source pins only; no network/preferences/import override.
 * Revision 3 accepts OSM source evidence under the owner-approved policy.
 * Candidate inventory and acceptance fixtures are never read by this class. */
final class MilepostCatalogue {
    static final int REVISION=3, APPROVED_COUNT=887, MAX_BYTES=2*1024*1024;
    static final String ASSET="millennium-mileposts/catalogue.json";
    static final String SHA="1f7468a5ed054c5822c88614650fe33814554502a57779fc1b7375749d35c2ce";
    // Activation requires a reviewed catalogue/pin change. No runtime/debug bypass.
    static final boolean READY=APPROVED_COUNT>0;
    static final String BLOCKER="Approved catalogue unavailable or incompatible";
    private static volatile MapLocations mapLocations;
    private MilepostCatalogue(){}

    /** Reused by map screens. Retain coordinates only, with no source records or aliases. */
    static final class MapLocations {
        final double[] longitude,latitude;
        final String[] identity,design;
        MapLocations(List<MilepostDiscoveryLedger.Marker> markers) {
            longitude=new double[markers.size()];latitude=new double[markers.size()];identity=new String[markers.size()];design=new String[markers.size()];
            for(int i=0;i<markers.size();i++) {
                longitude[i]=markers.get(i).lon;latitude[i]=markers.get(i).lat;identity[i]=markers.get(i).id;design[i]=markers.get(i).designer;
            }
        }
        int size(){return longitude.length;}
    }
    static synchronized MapLocations mapLocations(Context context)throws Exception {
        if(mapLocations==null)mapLocations=new MapLocations(load(context));
        return mapLocations;
    }

    static List<MilepostDiscoveryLedger.Marker> load(Context context)throws Exception {
        try(InputStream input=context.getAssets().open(ASSET)) {
            List<MilepostDiscoveryLedger.Marker> markers=parse(read(input),SHA,REVISION,APPROVED_COUNT);
            if(!fingerprint().equals(MilepostDiscoveryLedger.markerFingerprint(markers)))throw new IOException(BLOCKER);
            return markers;
        }
    }
    private static byte[] read(InputStream input)throws IOException {
        ByteArrayOutputStream output=new ByteArrayOutputStream();byte[] buffer=new byte[4096];int n;
        while((n=input.read(buffer))!=-1){if(output.size()+n>MAX_BYTES)throw new IOException("Oversized milepost catalogue");output.write(buffer,0,n);}
        return output.toByteArray();
    }
    /** Package-private pure validation for hostile-input tests; production always calls load with source pins. */
    static List<MilepostDiscoveryLedger.Marker> parse(byte[] bytes,String sha,int revision,int count)throws Exception {
        if(bytes.length>MAX_BYTES||!hex(MessageDigest.getInstance("SHA-256").digest(bytes)).equals(sha))
            throw new IOException("Milepost catalogue checksum");
        JSONObject root=new JSONObject(new String(bytes,StandardCharsets.UTF_8));
        if(root.getInt("schema_version")!=1||root.getInt("revision")!=revision
                ||!"approved-physical-mileposts".equals(root.getString("scope"))
                ||!MilepostDiscoveryLedger.EVALUATOR.equals(root.getString("evaluator"))
                ||!"ODbL-1.0".equals(root.getString("licence")))throw new IOException("Milepost catalogue compatibility");
        url(root.getString("source_url"));text(root,"attribution");
        JSONArray records=root.getJSONArray("records");
        if(count<0||count>MilepostDiscoveryLedger.MAX_CATALOGUE||records.length()!=count)throw new IOException("Milepost catalogue count");
        List<MilepostDiscoveryLedger.Marker> markers=new ArrayList<>();Set<String> identities=new HashSet<>(),aliases=new HashSet<>();
        for(int i=0;i<records.length();i++) {
            JSONObject r=records.getJSONObject(i);
            if(!"approved".equals(r.getString("status"))||!"verified".equals(r.getString("identity_status"))
                    ||!"verified".equals(r.getString("location_status"))||!"UK".equals(r.getString("scope")))
                throw new IOException("Unapproved milepost");
            String id=text(r,"physical_marker_id");
            if(!id.startsWith("roadprints:millennium:")||!identities.add(id))throw new IOException("Duplicate/invalid physical identity");
            JSONArray refs=r.getJSONArray("evidence");if(refs.length()==0)throw new IOException("Missing provenance");
            for(int j=0;j<refs.length();j++){
                JSONObject e=refs.getJSONObject(j);url(e.getString("url"));text(e,"licence");text(e,"attribution");
                Instant.parse(e.getString("observed_at"));
                if(!e.getString("receipt_sha256").matches("[0-9a-f]{64}"))throw new IOException("Missing evidence receipt pin");
            }
            // Dates are explicit policy bounds, not invented survey dates. Known moves restrict retrospective use.
            text(r,"coordinate_evidence");text(r,"precision_resolution");text(r,"validity_evidence");Instant.parse(r.getString("reviewed_at"));
            long from=Instant.parse(r.getString("valid_from")).toEpochMilli(),until=Instant.parse(r.getString("valid_until")).toEpochMilli();
            if(r.has("temporal_policy")&&!Arrays.asList("source_assumed_stable","snapshot_forward_known_movement").contains(r.getString("temporal_policy")))throw new IOException("Unknown temporal policy");
            if(from<946684800000L||until<=from)throw new IOException("Invalid milepost position interval");
            List<String> names=new ArrayList<>();JSONArray a=r.getJSONArray("aliases");
            for(int j=0;j<a.length();j++){
                String alias=a.getString(j);
                if(!alias.matches("osm:(node|way|relation):[1-9][0-9]*")||!aliases.add(alias))throw new IOException("Duplicate/invalid alias");
                names.add(alias);
            }
            if(names.isEmpty())throw new IOException("Missing source identity");
            String designer=r.getString("designer");boolean verified=r.getBoolean("designer_verified");
            if(verified)text(r,"designer_evidence");
            markers.add(new MilepostDiscoveryLedger.Marker(id,r.getDouble("longitude"),r.getDouble("latitude"),true,from,until,names,designer,verified));
        }
        // Validate cross-row geometry/identity invariants before any database write.
        MilepostDiscoveryLedger.markerFingerprint(markers);
        return Collections.unmodifiableList(markers);
    }
    private static String text(JSONObject r,String key)throws Exception {
        String value=r.getString(key);if(value.trim().isEmpty()||value.length()>4096)throw new IOException("Missing/oversized "+key);return value;
    }
    private static void url(String value)throws IOException {if(!value.startsWith("https://")||value.length()>2048)throw new IOException("Invalid evidence URL");}
    private static String hex(byte[] bytes){StringBuilder s=new StringBuilder();for(byte b:bytes)s.append(String.format(Locale.ROOT,"%02x",b&255));return s.toString();}
    static String fingerprint() {
        // Reviewed marker envelope and file checksum are both checked by production installation.
        return "022118567034f1c648c7e158b0f44cb3969b3327618d739fad19a47b26ebc535";
    }
    static void install(Context context,MilepostDiscoveryLedger ledger)throws Exception {
        List<MilepostDiscoveryLedger.Marker> markers=load(context);
        if(!READY||markers.isEmpty())
            throw new IOException(BLOCKER);
        ledger.install(REVISION,SHA,markers);
    }
}
