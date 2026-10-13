package com.roadprints.capture;
import org.json.*;
import java.time.*;
import java.util.*;
/** Explicit partial or dated-complete approval contracts; neither enables visits by itself. */
final class VenueCatalogueApproval {
    static boolean valid(JSONObject root){try{validate(root);return true;}catch(Exception invalid){return false;}}
    static void validate(JSONObject root)throws Exception{
        if(!Boolean.FALSE.equals(root.opt("visit_enabled")))throw new IllegalArgumentException("Map release cannot change visit policy");
        String scope=root.getString("approval_scope");
        if("map_only_partial_beta".equals(scope)){
            if(!Boolean.FALSE.equals(root.opt("coverage_complete"))||!root.has("completion_target")||root.opt("completion_target")!=JSONObject.NULL)throw new IllegalArgumentException("Partial release cannot certify completion");
        }else if("reviewed_uk_completion_v1".equals(scope)){
            JSONArray rows=root.getJSONArray("venues");JSONObject review=root.getJSONObject("coverage_review");
            if(!Boolean.TRUE.equals(root.opt("coverage_complete"))||rows.length()==0||root.getInt("completion_target")!=rows.length()||review.getInt("active_members")!=rows.length()||review.getInt("current_members")!=rows.length()+review.getInt("temporarily_closed_members")||review.getInt("source_listings")-review.getInt("outside_uk")-review.getInt("future_openings")-review.getInt("former_members")!=review.getInt("current_members")||!review.getString("membership_sha256").matches("[0-9a-f]{64}")||!review.getString("source_url").startsWith("https://")||review.getString("review_id").isEmpty())throw new IllegalArgumentException("Unreconciled completion target");
            LocalDate checked=LocalDate.parse(review.getString("reviewed_at"));if(checked.isAfter(LocalDate.now(ZoneOffset.UTC))||LocalDate.parse(review.getString("reference_snapshot_date")).isAfter(checked))throw new IllegalArgumentException("Future coverage review");
            Map<String,Integer> counts=new HashMap<>();Set<String> active=new HashSet<>();
            for(int i=0;i<rows.length();i++){JSONObject v=rows.getJSONObject(i),c=v.getJSONObject("county");String code=c.getString("code");if(code.isEmpty()||c.getString("name").isEmpty()||c.getString("nation").isEmpty()||!active.add(v.getString("id")))throw new IllegalArgumentException("Invalid county identity");counts.put(code,counts.getOrDefault(code,0)+1);}
            JSONObject expected=review.getJSONObject("county_counts");if(expected.length()!=counts.size())throw new IllegalArgumentException("County coverage mismatch");for(String code:counts.keySet())if(expected.getInt(code)!=counts.get(code))throw new IllegalArgumentException("County target mismatch");
            Instant started=Instant.parse(root.getString("lifecycle_started_at"));if(started.isAfter(Instant.now()))throw new IllegalArgumentException("Future lifecycle baseline");
            JSONArray lifecycle=root.getJSONArray("lifecycle");Set<String> closed=new HashSet<>();
            for(int i=0;i<lifecycle.length();i++){
                JSONObject v=lifecycle.getJSONObject(i);double lat=v.getDouble("lat"),lon=v.getDouble("lon");if(!Double.isFinite(lat)||!Double.isFinite(lon)||lat<49||lat>61||lon< -9||lon>2)throw new IllegalArgumentException("Missing last-known reviewed position");String id=v.getString("id");Instant at=Instant.parse(v.getString("permanently_closed_at")),known=Instant.parse(v.getString("known_active_from"));
                if(active.contains(id)||!closed.add(id)||!"permanently_closed".equals(v.getString("status"))||!at.isAfter(started)||!known.isBefore(at)||at.isAfter(Instant.now())||!v.getString("evidence_url").startsWith("https://")||v.getString("name").isEmpty()||v.getJSONObject("county").getString("code").isEmpty()||(LocalDate.parse(v.getString("reviewed_at")).isBefore(at.atZone(ZoneOffset.UTC).toLocalDate())||LocalDate.parse(v.getString("reviewed_at")).isAfter(LocalDate.now(ZoneOffset.UTC))))throw new IllegalArgumentException("Unverified future closure");
            }
        }else throw new IllegalArgumentException("Unsupported approval scope");
    }
    private VenueCatalogueApproval(){}
}
