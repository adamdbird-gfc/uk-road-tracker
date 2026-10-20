package com.roadprints.capture;

import android.content.Context;
import android.content.Intent;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Locale;

/** Small navigation contract: identities only, never archive geometry or journey arrays. */
final class RoadDetailSelection {
    static final String MOTORWAY="motorway", A_ROAD="a_road", B_ROAD="b_road", LOCAL="local", NCN="ncn";
    String ncnMode="combined";
    final String kind, label, region, settlementCode, settlementName;
    RoadDetailSelection(String kind, String label, String region, String code, String name) {
        if (!(MOTORWAY.equals(kind)||A_ROAD.equals(kind)||B_ROAD.equals(kind)||LOCAL.equals(kind)||NCN.equals(kind))
                || label==null || label.trim().isEmpty()) throw new IllegalArgumentException("Road identity required");
        this.kind=kind; this.label=label.trim(); this.region=region==null?"":region;
        settlementCode=code==null?"":code; settlementName=name==null?"":name;
        if (LOCAL.equals(kind) && settlementCode.isEmpty()) throw new IllegalArgumentException("Local road needs its settlement");
    }
    String title() { return (NCN.equals(kind)?label.replace(":"," "):label)+("NI".equals(region)?" · NI":"")+(settlementName.isEmpty()?"":" · "+settlementName); }
    Intent intent(Context context, boolean journeys) {
        return new Intent(context,RoadDetailActivity.class).putExtra("road_kind",kind).putExtra("road_label",label)
                .putExtra("road_region",region).putExtra("road_settlement",settlementCode)
                .putExtra("road_settlement_name",settlementName).putExtra("road_journeys",journeys).putExtra("ncn_mode",ncnMode);
    }
    static RoadDetailSelection from(Intent intent) {
        RoadDetailSelection s=new RoadDetailSelection(intent.getStringExtra("road_kind"),intent.getStringExtra("road_label"),
                intent.getStringExtra("road_region"),intent.getStringExtra("road_settlement"),intent.getStringExtra("road_settlement_name"));
        s.ncnMode=intent.getStringExtra("ncn_mode");if(s.ncnMode==null)s.ncnMode="combined";return s;
    }
    static String normalizeRef(String value) {
        String ref=value.trim().replaceAll("\\s+","").toUpperCase(Locale.ROOT);
        return ref.equals("M6T")||ref.equals("M6TOLL")?"M6 Toll":ref;
    }
    boolean matches(JSONObject feature) {
        JSONObject p=feature==null?null:feature.optJSONObject("properties");
        if(p==null || (!region.isEmpty()&&!region.equals(region(feature)))) return false;
        if(LOCAL.equals(kind)) {
            String highway=p.optString("highway","");
            if(highway.endsWith("_link")||highway.equals("service")||highway.equals("path")||highway.equals("footway")||highway.equals("steps")||highway.equals("cycleway"))return false;
            String refs=p.optString("road_ref",p.optString("ref",""));
            for(String ref:refs.split("[;,/]")) if(normalizeRef(ref).matches("[MAB][0-9]+.*")) return false;
            String name=p.optString("name",p.optString("road_name",""));
            return name.trim().replaceAll("\\s+"," ").equalsIgnoreCase(label.trim().replaceAll("\\s+"," "));
        }
        for(String ref:p.optString("road_ref",p.optString("ref","")).split("[;,/]"))
            if(normalizeRef(ref).equals(normalizeRef(label))) return true;
        return false;
    }
    static String region(JSONObject feature) {
        JSONObject p=feature.optJSONObject("properties");
        String explicit=p==null?"":p.optString("road_region","").toUpperCase(Locale.ROOT);
        if("NI".equals(explicit)||"GB".equals(explicit)) return explicit;
        JSONObject g=feature.optJSONObject("geometry");JSONArray c=g==null?null:g.optJSONArray("coordinates");
        JSONArray first=c==null?null:c.optJSONArray(0);
        if(g!=null&&"MultiLineString".equals(g.optString("type"))) first=first==null?null:first.optJSONArray(0);
        return first!=null&&first.optDouble(0,0)<-5.3&&first.optDouble(1,0)>53.9&&first.optDouble(1,0)<55.6?"NI":"GB";
    }
}
