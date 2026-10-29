package com.roadprints.capture;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Conservative source-identity consumer. No road-name or proximity membership fallback. */
final class NcnMatcher {
    private final NcnCatalogue catalogue;
    NcnMatcher(NcnCatalogue catalogue){this.catalogue=catalogue;}
    JSONObject assess(JSONObject journey) {
        JSONArray spans=new JSONArray();String raw=journey.optString("mode","").trim().toLowerCase(java.util.Locale.ROOT);
        String mode=(raw.equals("walking")||raw.equals("running")||raw.equals("pedestrian"))?"on_foot":(raw.equals("cycling")||raw.equals("bicycle"))?"cycling":"ineligible";
        String reason="no_supported_source_evidence";
        try {
            if(mode.equals("ineligible")||!journey.optString("processing_status").equals("complete"))return result(mode,spans,"ineligible_or_unfinished");
            JSONObject correction=journey.optJSONObject("journey_corrections");
            if(nonempty(correction,"removed_road_ids")||nonempty(correction,"removed_road_labels"))return result(mode,spans,"road_correction_requires_reassessment");
            Set<Integer> removed=new HashSet<>();JSONArray r=correction==null?null:correction.optJSONArray("removed_matched_segments");
            if(r!=null)for(int i=0;i<r.length();i++){int n=NcnJson.integer(r.get(i));if(n<0)throw new IllegalArgumentException();removed.add(n);}
            JSONObject data=journey.getJSONObject("processing_result");JSONArray features=data.getJSONObject("geojson").getJSONArray("features");
            if(mode.equals("on_foot")) {
                if(!data.optString("matcher").equals("preloaded_pedestrian_network_v4"))return result(mode,spans,"unsupported_matcher");
                JSONObject evidence=data.optJSONObject("matched_path_evidence");
                if(evidence!=null)walking(evidence,features,removed,spans);
                else legacy(data,features,removed,spans);
            } else {
                if(!data.optString("matcher").equals("osrm_bicycle_v1")||!data.optString("matching_mode").equals("cycling"))return result(mode,spans,"unsupported_matcher");
                cycling(data.getJSONObject("cycling_path_evidence"),features,removed,spans);
            }
            if(spans.length()>0)reason="source_identity_verified";
        } catch(Exception invalid) {spans=new JSONArray();reason="invalid_or_changed_source_evidence";}
        return result(mode,spans,reason);
    }
    private static boolean nonempty(JSONObject object,String key){JSONArray a=object==null?null:object.optJSONArray(key);return a!=null&&a.length()>0;}
    private static JSONObject result(String mode,JSONArray spans,String reason) {
        try{
            Map<String,List<double[]>> compact=new java.util.TreeMap<>();for(int i=0;i<spans.length();i++){JSONArray span=spans.getJSONArray(i);compact.computeIfAbsent(span.getString(0)+"/"+span.getString(1),k->new ArrayList<>()).add(new double[]{span.getDouble(2),span.getDouble(3)});}
            JSONArray normalized=new JSONArray();for(Map.Entry<String,List<double[]>> e:compact.entrySet()){int slash=e.getKey().indexOf('/');for(double[] interval:NcnLedger.merge(e.getValue()))normalized.put(new JSONArray().put(e.getKey().substring(0,slash)).put(e.getKey().substring(slash+1)).put(interval[0]).put(interval[1]));}
            return new JSONObject().put("mode",mode).put("spans",normalized).put("reason",reason);
        }catch(Exception impossible){throw new IllegalStateException(impossible);}
    }
    private JSONArray coordinates(JSONArray features,int index) throws Exception {
        JSONObject g=features.getJSONObject(index).getJSONObject("geometry");if(!g.getString("type").equals("LineString"))throw new IllegalArgumentException();
        JSONArray points=g.getJSONArray("coordinates");NcnJson.geometry(points);return points;
    }
    private void walking(JSONObject evidence,JSONArray features,Set<Integer> removed,JSONArray output) throws Exception {
        if(NcnJson.integer(evidence.get("schema_version"))!=1||!evidence.getString("status").equals("available")||NcnJson.integer(evidence.get("geometry_feature_index"))!=0)throw new IllegalArgumentException();
        JSONArray points=coordinates(features,0);if(!NcnJson.geometry(points).equals(evidence.getString("geometry_sha256")))throw new IllegalArgumentException();
        JSONArray references=evidence.getJSONArray("references"),spans=evidence.getJSONArray("spans");int expected=0;
        for(int i=0;i<spans.length();i++) {
            JSONArray span=spans.getJSONArray(i);if(span.length()!=3)throw new IllegalArgumentException();
            int a=NcnJson.integer(span.get(0)),z=NcnJson.integer(span.get(1)),ref=NcnJson.integer(span.get(2));
            if(a!=expected||z<=a||z>=points.length()||ref<0||ref>=references.length())throw new IllegalArgumentException();expected=z;
            JSONObject identity=references.getJSONObject(ref);if(!NcnJson.signed(identity))throw new IllegalArgumentException();
            if(!identity.getString("source_key").startsWith("osm_geofabrik_"))continue;
            JSONObject tags=identity.optJSONObject("tags");if(tags!=null){String foot=tags.optString("foot"),access=tags.optString("access");
                if(foot.equals("no")||foot.equals("private")||((access.equals("no")||access.equals("private"))&&!foot.equals("yes")&&!foot.equals("designated")&&!foot.equals("permissive")))continue;}
            for(NcnCatalogue.Path path:catalogue.way(identity.getString("source_feature_id"))) if(path.modes.contains("on_foot")&&path.way.equals(identity.getString("source_feature_id"))&&path.sourceHash.equals(identity.getString("source_geometry_sha256")))
                for(int edge=a;edge<z;edge++)if(!removed.contains(edge))credit(path,points.getJSONArray(edge),points.getJSONArray(edge+1),0,path.positions.length-1,1e-6,output);
        }
        if(expected!=points.length()-1)throw new IllegalArgumentException();
    }
    private void legacy(JSONObject data,JSONArray features,Set<Integer> removed,JSONArray output) throws Exception {
        JSONArray roads=data.optJSONObject("road_geojson")==null?null:data.getJSONObject("road_geojson").optJSONArray("features");Set<String> identities=new HashSet<>();
        if(roads!=null)for(int i=0;i<roads.length();i++) {
            JSONObject road=roads.optJSONObject(i),g=road==null?null:road.optJSONObject("geometry");if(g==null||!g.optString("type").equals("LineString"))continue;
            Set<String> candidates=new HashSet<>();for(NcnCatalogue.Path p:catalogue.hash(NcnJson.sourceGeometry(g.getJSONArray("coordinates"))))if(p.modes.contains("on_foot")&&sameSource(g.optJSONArray("coordinates"),p.coordinates))candidates.add(p.way);
            if(candidates.size()==1)identities.addAll(candidates);
        }
        int offset=0;
        for(int f=0;f<features.length();f++) {
            JSONArray points=coordinates(features,f);
            for(int edge=0;edge<points.length()-1;edge++)if(!removed.contains(offset+edge))
                for(String identity:identities)for(NcnCatalogue.Path p:catalogue.way(identity))if(p.modes.contains("on_foot"))credit(p,points.getJSONArray(edge),points.getJSONArray(edge+1),0,p.positions.length-1,1e-6,output);
            offset+=points.length()-1;
        }
    }
    private boolean sameSource(JSONArray first,JSONArray second) throws Exception {
        if(first==null)return false;List<String> a=sourcePoints(first),b=sourcePoints(second);if(a.equals(b))return true;java.util.Collections.reverse(a);return a.equals(b);
    }
    private List<String> sourcePoints(JSONArray points) throws Exception {
        List<String> out=new ArrayList<>();for(int i=0;i<points.length();i++){JSONArray p=points.getJSONArray(i);if(!RoadDetailGeometry.valid(p))throw new IllegalArgumentException();
            String point=Math.rint(p.getDouble(0)*1e7)+":"+Math.rint(p.getDouble(1)*1e7);if(out.isEmpty()||!out.get(out.size()-1).equals(point))out.add(point);}return out;
    }
    private void cycling(JSONObject evidence,JSONArray features,Set<Integer> removed,JSONArray output) throws Exception {
        if(NcnJson.integer(evidence.get("schema_version"))!=1||!evidence.getString("provider").equals("osrm")||!evidence.getString("router_graph").equals("bicycle")
                ||!(evidence.getString("status").equals("available")||evidence.getString("status").equals("partial")))throw new IllegalArgumentException();
        int[] offsets=new int[features.length()];int offset=0;for(int i=0;i<features.length();i++){offsets[i]=offset;offset+=coordinates(features,i).length()-1;}
        Set<Integer> seen=new HashSet<>();JSONArray records=evidence.getJSONArray("features");
        for(int i=0;i<records.length();i++) {
            JSONObject record=records.getJSONObject(i);if(!NcnJson.signed(record))throw new IllegalArgumentException();
            int f=NcnJson.integer(record.get("geometry_feature_index"));if(f<0||f>=features.length()||!seen.add(f))throw new IllegalArgumentException();
            JSONArray points=coordinates(features,f);if(!NcnJson.geometry(points).equals(record.getString("geometry_sha256")))throw new IllegalArgumentException();
            JSONArray legs=record.getJSONArray("legs");int previous=-1;
            for(int j=0;j<legs.length();j++) {
                JSONObject leg=legs.getJSONObject(j);int a=NcnJson.integer(leg.get("from_vertex")),z=NcnJson.integer(leg.get("to_vertex"));
                if(a<0||z<=a||z>=points.length()||a<previous)throw new IllegalArgumentException();previous=z;
                JSONArray raw=leg.getJSONArray("osm_nodes");List<Long> nodes=new ArrayList<>();
                for(int n=0;n<raw.length();n++){long id=NcnJson.node(raw.get(n));if(nodes.isEmpty()||nodes.get(nodes.size()-1)!=id)nodes.add(id);}
                if(nodes.size()<2)continue;Map<String,int[]> candidates=new LinkedHashMap<>();
                for(NcnCatalogue.Path path:catalogue.nodes(nodes.get(0),nodes.get(nodes.size()-1)))if(path.modes.contains("cycling")) {
                    List<Integer> positions=new ArrayList<>();boolean valid=nodes.size()>=2;
                    for(long id:nodes){int found=-1;for(int n=0;n<path.nodes.length();n++)if(path.nodes.getLong(n)==id){if(found!=-1){valid=false;break;}found=n;}if(found<0){valid=false;break;}positions.add(found);}
                    if(!valid)continue;boolean up=true,down=true;for(int n=1;n<positions.size();n++){up&=positions.get(n)>positions.get(n-1);down&=positions.get(n)<positions.get(n-1);}
                    if(up||down)candidates.put(path.way,new int[]{Math.min(positions.get(0),positions.get(positions.size()-1)),Math.max(positions.get(0),positions.get(positions.size()-1))});
                }
                if(candidates.size()!=1)continue;String way=candidates.keySet().iterator().next();int[] range=candidates.get(way);
                for(NcnCatalogue.Path path:catalogue.way(way))if(path.modes.contains("cycling"))
                    for(int edge=a;edge<z;edge++)if(!removed.contains(offsets[f]+edge))credit(path,points.getJSONArray(edge),points.getJSONArray(edge+1),range[0],range[1],.12,output);
            }
        }
    }
    private void credit(NcnCatalogue.Path path,JSONArray first,JSONArray last,int low,int high,double tolerance,JSONArray output) throws Exception {
        double[] a=path.project(first),b=path.project(last);double length=Math.hypot(b[0]-a[0],b[1]-a[1]);if(length<=.01||length>200_000)return;
        int samples=Math.max(2,(int)Math.ceil(length/2));double start=Double.NaN,end=Double.NaN,previous=Double.NaN;boolean up=true,down=true;
        for(int i=0;i<=samples;i++) {
            double t=i/(double)samples,x=a[0]+t*(b[0]-a[0]),y=a[1]+t*(b[1]-a[1]);double position=project(path,x,y,low,high,tolerance);
            if(!Double.isFinite(position))return;if(i==0)start=position;else{up&=position>=previous-1e-7;down&=position<=previous+1e-7;}previous=position;end=position;
        }
        double covered=Math.abs(end-start);if(!(up||down)||covered<.9*length||covered>1.1*length+.15)return;
        output.put(new JSONArray().put(path.route).put(path.way).put(Math.min(start,end)).put(Math.max(start,end)));
    }
    private double project(NcnCatalogue.Path path,double x,double y,int low,int high,double tolerance) {
        double nearest=Double.POSITIVE_INFINITY,result=Double.NaN;boolean ambiguous=false;
        for(int i=low;i<high;i++) {
            double[] a=path.xy[i],b=path.xy[i+1];double dx=b[0]-a[0],dy=b[1]-a[1],squared=dx*dx+dy*dy;if(squared<=0)continue;
            double t=Math.max(0,Math.min(1,((x-a[0])*dx+(y-a[1])*dy)/squared));double distance=Math.hypot(x-a[0]-t*dx,y-a[1]-t*dy);
            double p=path.positions[i]+t*(path.positions[i+1]-path.positions[i]);
            if(distance<nearest-1e-8){nearest=distance;result=p;ambiguous=false;}else if(Math.abs(distance-nearest)<=1e-8&&Math.abs(p-result)>.001)ambiguous=true;
        }
        return nearest<=tolerance&&!ambiguous?result:Double.NaN;
    }
}
