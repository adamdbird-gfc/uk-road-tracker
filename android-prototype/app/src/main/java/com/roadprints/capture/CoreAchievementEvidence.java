package com.roadprints.capture;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.*;

/** One bounded, streaming scan: totals and distinct matched coverage are separate. */
final class CoreAchievementEvidence {
    double roadMetres, footMetres, longestRoad, longestFoot;
    boolean captured;
    final Set<String> edited=new HashSet<>();
    final Set<String> refs=new TreeSet<>();
    final Map<String,LinkedHashMap<String,JSONObject>> named=new TreeMap<>();
    final Coverage roadCoverage=new Coverage(), footCoverage=new Coverage();

    void add(JSONObject journey) {
        String mode=journey.optString("mode","").toLowerCase(Locale.ROOT);
        boolean road="driving".equals(mode)||"bus".equals(mode);
        boolean foot="walking".equals(mode)||"running".equals(mode)||"pedestrian".equals(mode);
        double metres=journey.optDouble("distance_meters",0);
        if(!Double.isFinite(metres)||metres<0) metres=0;
        if(road){roadMetres+=metres;longestRoad=Math.max(longestRoad,metres);}
        if(foot){footMetres+=metres;longestFoot=Math.max(longestFoot,metres);}
        JSONObject source=journey.optJSONObject("source");
        if(source!=null&&"android_activity_capture".equals(source.optString("type"))
                &&!journey.optString("ended_at").isEmpty()) captured=true;
        JSONObject edits=journey.optJSONObject("journey_corrections");
        // Existing saved corrections are legitimate feature-use evidence too.
        JSONArray removed=edits==null?null:edits.optJSONArray("removed_matched_segments");
        JSONArray removedRoads=edits==null?null:edits.optJSONArray("removed_road_ids");
        if((removed!=null&&removed.length()>0)||(removedRoads!=null&&removedRoads.length()>0))
            edited.add(journey.optString("journey_id"));
        if((!road&&!foot)||!"complete".equals(journey.optString("processing_status"))) return;
        JSONObject result=journey.optJSONObject("processing_result");
        if(result==null)return;
        (road?roadCoverage:footCoverage).add(result.optJSONObject("geojson"),removed);
        for(String field:new String[]{"road_geojson","motorway_geojson","a_road_geojson"}) {
            JSONObject collection=result.optJSONObject(field);
            JSONArray features=collection==null?null:collection.optJSONArray("features");
            if(features==null)continue;
            for(int i=0;i<features.length();i++) {
                JSONObject feature=features.optJSONObject(i);
                if(feature==null||JourneyCorrectionUtils.excludesRoadFeature(journey,feature))continue;
                JSONObject props=feature.optJSONObject("properties");
                JSONObject geometry=feature.optJSONObject("geometry");
                if(props==null||geometry==null)continue;
                String kind=props.optString("highway").toLowerCase(Locale.ROOT);
                if(kind.endsWith("_link")||Arrays.asList("service","path","footway","cycleway","steps","track").contains(kind))continue;
                String raw=props.optString("road_ref",props.optString("ref",""));
                boolean numbered=false;
                for(String part:raw.split("[;,/]")) {
                    String ref=part.replaceAll("\\s+","").toUpperCase(Locale.ROOT);
                    if(ref.matches("[AMB][0-9]+(?:\\(M\\))?")||"M6TOLL".equals(ref)) {
                        String region=props.optString("road_region","");
                        if(region.isEmpty())region=isNorthernIreland(geometry)?"NI":"GB";
                        refs.add(region+":"+ref);numbered=true;
                    }
                }
                if(numbered)continue;
                String name=normalize(props.optString("name",props.optString("road_name","")));
                if(name.isEmpty()||name.matches("[0-9.,]+"))continue;
                named.computeIfAbsent(name,k->new LinkedHashMap<>()).putIfAbsent(geometry.toString(),geometry);
            }
        }
    }
    static String normalize(String name){return name.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+"," ");}
    static boolean isNorthernIreland(JSONObject geometry) {
        List<JSONArray> lines=lines(geometry);
        if(lines.isEmpty())return false;
        JSONArray pt=lines.get(0).optJSONArray(0);
        return pt!=null&&pt.optDouble(0)<-5.3&&pt.optDouble(0)>-8.3&&pt.optDouble(1)>54&&pt.optDouble(1)<55.5;
    }
    Map<String,List<String>> roadLists(Context context) {
        List<String> all=new ArrayList<>(refs), high=new ArrayList<>(), station=new ArrayList<>();
        for(Map.Entry<String,LinkedHashMap<String,JSONObject>> row:named.entrySet()) {
            List<JSONObject> geometries=new ArrayList<>(row.getValue().values());
            List<String> towns=AchievementStore.roadSettlements(context,"name:"+row.getKey(),geometries);
            // Unresolved names count once conservatively, matching existing discovery.
            if(towns.isEmpty())all.add(row.getKey());
            else for(String town:towns) {
                String label=row.getKey()+" · "+town;all.add(label);
                if("high street".equals(row.getKey()))high.add(label);
                if("station road".equals(row.getKey()))station.add(label);
            }
        }
        Map<String,List<String>> result=new HashMap<>();
        result.put("the-knowledge",all);result.put("mary-high-streets",high);result.put("mastered-monopoly",station);
        return result;
    }
    static List<JSONArray> lines(JSONObject geometry) {
        List<JSONArray> result=new ArrayList<>();if(geometry==null)return result;
        JSONArray c=geometry.optJSONArray("coordinates");if(c==null)return result;
        if("LineString".equals(geometry.optString("type")))result.add(c);
        else if("MultiLineString".equals(geometry.optString("type")))for(int i=0;i<c.length();i++)if(c.optJSONArray(i)!=null)result.add(c.optJSONArray(i));
        return result;
    }
    /** Merge collinear intervals, including reverse travel, splits and partial overlaps.
     * Coordinates are projected in a fixed UK metric plane. Keys tolerate sub-metre
     * coordinate rounding; length uses ground-distance correction at segment latitude. */
    static final class Coverage {
        final Map<String,List<double[]>> intervals=new HashMap<>();
        void add(JSONObject collection,JSONArray removedValues) {
            Set<Integer> removed=new HashSet<>();if(removedValues!=null)for(int i=0;i<removedValues.length();i++)removed.add(removedValues.optInt(i,-1));
            JSONArray features=collection==null?null:collection.optJSONArray("features");if(features==null)return;
            int edge=0;
            for(int i=0;i<features.length();i++) {
                JSONObject f=features.optJSONObject(i);
                for(JSONArray line:lines(f==null?null:f.optJSONObject("geometry")))for(int j=1;j<line.length();j++,edge++) {
                    if(removed.contains(edge))continue;
                    JSONArray a=line.optJSONArray(j-1),b=line.optJSONArray(j);if(a==null||b==null||a.length()<2||b.length()<2)continue;
                    double lngA=a.optDouble(0,Double.NaN),latA=a.optDouble(1,Double.NaN),lngB=b.optDouble(0,Double.NaN),latB=b.optDouble(1,Double.NaN);
                    if(!Double.isFinite(lngA)||!Double.isFinite(latA)||!Double.isFinite(lngB)||!Double.isFinite(latB)||Math.abs(latA)>85||Math.abs(latB)>85)continue;
                    double ax=Math.toRadians(lngA)*6378137,ay=mercatorY(latA),bx=Math.toRadians(lngB)*6378137,by=mercatorY(latB);
                    double dx=bx-ax,dy=by-ay,length=Math.hypot(dx,dy);if(length<0.01)continue;
                    double angle=Math.atan2(dy,dx);if(angle<0)angle+=Math.PI;if(angle>=Math.PI)angle-=Math.PI;
                    // 0.00001 rad is fine enough to distinguish bends and nearby roads.
                    long bearing=Math.round(angle*100000);double theta=bearing/100000.0,ux=Math.cos(theta),uy=Math.sin(theta);
                    long offset=Math.round(-ax*uy+ay*ux);
                    String key=bearing+":"+offset;
                    double start=ax*ux+ay*uy,end=bx*ux+by*uy;
                    List<double[]> values=intervals.computeIfAbsent(key,k->new ArrayList<>());
                    double lo=Math.min(start,end),hi=Math.max(start,end),scale=Math.cos(Math.toRadians((latA+latB)/2));
                    for(Iterator<double[]> it=values.iterator();it.hasNext();) {
                        double[] old=it.next();if(old[1]<lo-0.5||old[0]>hi+0.5)continue;
                        lo=Math.min(lo,old[0]);hi=Math.max(hi,old[1]);scale=old[2];it.remove();
                    }
                    values.add(new double[]{lo,hi,scale});
                }
            }
        }
        double metres(){double total=0;for(List<double[]> values:intervals.values())for(double[] v:values)total+=(v[1]-v[0])*v[2];return total;}
        static double mercatorY(double lat){return 6378137*Math.log(Math.tan(Math.PI/4+Math.toRadians(lat)/2));}
    }
}
