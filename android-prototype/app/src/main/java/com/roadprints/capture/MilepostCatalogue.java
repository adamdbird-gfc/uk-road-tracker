package com.roadprints.capture;

import android.content.Context;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import org.json.*;

/** Sole production catalogue reader. Reviewed source pins only; no network/preferences/import override.
 * Revision 2 is a blocked approval checkpoint, explicitly containing zero approved records.
 * Candidate inventory and acceptance fixtures are never read by this class. */
final class MilepostCatalogue {
    static final int REVISION=2, APPROVED_COUNT=0, MAX_BYTES=2*1024*1024;
    static final String ASSET="millennium-mileposts/catalogue.json";
    static final String SHA="3ef1c32d74c12f8d24c9892fea3c4e960d09f601a4f9cf71a390c7db3572a28e";
    // Activation requires a reviewed catalogue/pin change. No runtime/debug bypass.
    static final boolean READY=APPROVED_COUNT>0;
    static final String BLOCKER="No physical positions with resolved precision and dated validity approved";
    private MilepostCatalogue(){}

    static List<MilepostDiscoveryLedger.Marker> load(Context context)throws Exception {
        try(InputStream input=context.getAssets().open(ASSET)) {
            return parse(read(input),SHA,REVISION,APPROVED_COUNT);
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
            // Approval records must document resolution; no guessed tolerance or uncertainty radius.
            text(r,"coordinate_evidence");text(r,"precision_resolution");text(r,"validity_evidence");Instant.parse(r.getString("reviewed_at"));
            long from=Instant.parse(r.getString("valid_from")).toEpochMilli(),until=Instant.parse(r.getString("valid_until")).toEpochMilli();
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
        return "f3e3436ecfb80e649cbef6cf06bcde07b54f5b01cc45108f6599c1fd1f359ee3";
    }
    static void install(Context context,MilepostDiscoveryLedger ledger)throws Exception {
        List<MilepostDiscoveryLedger.Marker> markers=load(context);
        if(!READY||markers.isEmpty()||!fingerprint().equals(MilepostDiscoveryLedger.markerFingerprint(markers)))
            throw new IOException(BLOCKER);
        ledger.install(REVISION,SHA,markers);
    }
}
