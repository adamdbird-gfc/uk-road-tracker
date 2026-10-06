package com.roadprints.capture;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Canonical coverage for the discovered A roads, using the POC v5 references. */
final class ARoadProgressCalculator {
    private static final double EARTH_RADIUS_M = 6_371_000.0;
    private static final double MATCH_RADIUS_M = 100.0;
    private static final double GRID_M = 250.0;
    private static final double SAMPLE_M = 100.0;
    // The source geometry for A2 includes overlapping/connector ways. Use the
    // published London–Dover route length instead of the raw way-length sum.
    private static final Map<String, Double> VERIFIED_LENGTH_KM = new HashMap<>();
    static { VERIFIED_LENGTH_KM.put("A2", 115.79); }

    static final class Road {
        final String id, ref, region;
        final Set<String> journeyIds = new HashSet<>();
        final Set<Integer> covered = new HashSet<>();
        final List<JSONArray> coveredMapSections = new ArrayList<>();
        final List<JSONArray> incompleteMapSections = new ArrayList<>();
        final List<Anchor> anchors = new ArrayList<>();
        int referenceSections;
        final Map<String, List<Anchor>> grid = new HashMap<>();
        double matchedMetres, totalKm;
        boolean referenceAvailable;
        Road(String id, String ref, String region) { this.id=id; this.ref=ref; this.region=region; }
        double percent() { return referenceAvailable && referenceSections>0 ? covered.size()*100.0/referenceSections : Double.NaN; }
        double uniqueKm() { return referenceAvailable ? totalKm * percent()/100.0 : 0; }
    }
    static final class Summary {
        final List<Road> roads;
        final List<String> missing;
        double matchedMetres;
        Summary(List<Road> roads,List<String> missing) { this.roads=roads;this.missing=missing; }
        double totalKm() { double total=0; for(Road r:roads) total+=r.uniqueKm(); return total; }
        double referenceKm() { double total=0; for(Road r:roads) if(r.referenceAvailable) total+=r.totalKm; return total; }
        double percent() { return referenceKm()>0?Math.min(100,totalKm()*100/referenceKm()):0; }
    }
    static final class Anchor {
        final int id; final double lng,lat,x,y; final int component;
        Anchor(int id,double lng,double lat,int component) { this.id=id;this.lng=lng;this.lat=lat;this.component=component;double[] xy=mercator(lng,lat);x=xy[0];y=xy[1]; }
    }
    private final Context context;
    private final boolean includeMapSections;
    private Summary completedSummary;
    private final Map<String,Road> roads=new TreeMap<>();
    private final Map<String,JSONObject> index=new HashMap<>();
    private final Set<String> failed=new HashSet<>();
    ARoadProgressCalculator(Context context) { this(context,true); }
    ARoadProgressCalculator(Context context, boolean includeMapSections) {
        this.context=context.getApplicationContext(); this.includeMapSections=includeMapSections; loadIndex();
    }

    void addJourney(JSONObject journey) {
        if(completedSummary!=null) throw new IllegalStateException("Coverage calculation is already finished");
        if (journey==null || !"complete".equals(journey.optString("processing_status",""))) return;
        String mode=journey.optString("mode","").toLowerCase(Locale.ROOT);
        if (!("driving".equals(mode)||"bus".equals(mode))) return;
        JSONObject result=journey.optJSONObject("processing_result");
        JSONObject collection=result==null?null:result.optJSONObject("a_road_geojson");
        JSONArray features=collection==null?null:collection.optJSONArray("features");
        if (features==null) return;
        String jid=journey.optString("journey_id","");
        for(int i=0;i<features.length();i++) {
            JSONObject feature=features.optJSONObject(i);
            if(feature==null || JourneyCorrectionUtils.excludesRoadFeature(journey, feature)) continue;
            JSONObject props=feature.optJSONObject("properties"); if(props==null) continue;
            String ref=normalize(props.optString("road_ref",""));
            if(!ref.matches("A[0-9]+[A-Z]?")) continue;
            String region=props.optString("road_region","").toUpperCase(Locale.ROOT);
            if(!"NI".equals(region)) region=isNi(feature)?"NI":"GB";
            String id=region+":"+ref;
            Road road=roads.get(id);
            if(road==null){road=new Road(id,ref,region);roads.put(id,road);}
            road.matchedMetres+=Math.max(0,props.optDouble("distance_m",0));
            if(!jid.isEmpty()) road.journeyIds.add(jid);
            ensureReference(road);
            if(road.referenceAvailable) matchFeature(road,feature.optJSONObject("geometry"));
        }
    }
    Summary finish() {
        if(completedSummary!=null) return completedSummary;
        List<String> missing=new ArrayList<>();
        for(Road road:roads.values()) {
            if(!road.referenceAvailable) missing.add(road.id);
            else if(includeMapSections) buildMapSections(road);
            // Coverage figures retain counts, not the spatial lookup or anchors.
            road.anchors.clear();
            road.grid.clear();
        }
        List<Road> sorted=new ArrayList<>(roads.values());
        sorted.sort((a,b)->Double.compare(b.matchedMetres,a.matchedMetres));
        Summary result=new Summary(sorted,missing);
        for(Road road:sorted) result.matchedMetres+=road.matchedMetres;
        index.clear();
        completedSummary=result;
        return result;
    }
    private void loadIndex() {
        try(InputStream in=context.getAssets().open("index.json");
            BufferedReader reader=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8))) {
            StringBuilder s=new StringBuilder();String line;while((line=reader.readLine())!=null)s.append(line);
            JSONObject root=new JSONObject(s.toString()).optJSONObject("roads"); if(root==null)return;
            java.util.Iterator<String> it=root.keys();while(it.hasNext()){String key=it.next();index.put(key,root.optJSONObject(key));}
        } catch(Exception ignored) { }
    }
    private void ensureReference(Road road) {
        if(road.referenceAvailable||failed.contains(road.id))return;
        JSONObject entry=index.get(road.id); String file=entry==null?null:entry.optString("file",null);
        if(file==null){failed.add(road.id);return;}
        try(InputStream in=context.getAssets().open(file);
            BufferedReader reader=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8))) {
            StringBuilder s=new StringBuilder();String line;while((line=reader.readLine())!=null)s.append(line);
            JSONObject data=new JSONObject(s.toString()); JSONArray paths=data.optJSONArray("paths");
            if(paths==null){failed.add(road.id);return;}
            int component=-1;
            for(int p=0;p<paths.length();p++){
                JSONArray path=paths.optJSONArray(p);if(path==null||path.length()<2)continue;
                List<double[]> points=new ArrayList<>();
                for(int j=0;j<path.length();j++){
                    JSONArray raw=path.optJSONArray(j);if(raw==null||raw.length()<2)continue;
                    double lng=raw.optDouble(0,Double.NaN),lat=raw.optDouble(1,Double.NaN);
                    if(Double.isFinite(lng)&&Double.isFinite(lat))points.add(new double[]{lng,lat,raw.optInt(2,0)});
                }
                if(points.size()<2)continue;
                component++;
                sampleReferencePath(road,points,component);
            }
            road.totalKm=VERIFIED_LENGTH_KM.getOrDefault(road.ref,
                    data.optDouble("total_km",entry.optDouble("total_km",0)));
            road.referenceSections=road.anchors.size();
            road.referenceAvailable=road.referenceSections>0&&road.totalKm>0;
            if(!road.referenceAvailable)failed.add(road.id);
        }catch(Exception ignored){failed.add(road.id);}
    }
    private void sampleReferencePath(Road road,List<double[]> points,int component) {
        double carry=0; double[] previous=points.get(0); addAnchor(road,previous,component);
        for(int i=1;i<points.size();i++){
            double[] next=points.get(i); double d=distance(previous[0],previous[1],next[0],next[1]);
            if(!Double.isFinite(d)||d<=0){previous=next;continue;}
            double first=SAMPLE_M-carry;
            for(double along=first;along<=d+0.000001;along+=SAMPLE_M){double f=along/d;addAnchor(road,new double[]{previous[0]+(next[0]-previous[0])*f,previous[1]+(next[1]-previous[1])*f,component},component);}
            carry=(carry+d)%SAMPLE_M; previous=next;
        }
        double[] last=points.get(points.size()-1); Anchor tail=road.anchors.isEmpty()?null:road.anchors.get(road.anchors.size()-1);
        if(tail==null||distance(tail.lng,tail.lat,last[0],last[1])>2)addAnchor(road,last,component);
    }
    private void addAnchor(Road road,double[] p,int component){Anchor a=new Anchor(road.anchors.size(),p[0],p[1],component);road.anchors.add(a);road.grid.computeIfAbsent(gridKey(a.x,a.y),k->new ArrayList<>()).add(a);}
    private void matchFeature(Road road,JSONObject geometry){
        if(geometry==null)return;JSONArray coords=geometry.optJSONArray("coordinates");if(coords==null)return;
        String type=geometry.optString("type","");
        if("LineString".equals(type))matchLine(road,coords);
        else if("MultiLineString".equals(type))for(int i=0;i<coords.length();i++){JSONArray line=coords.optJSONArray(i);if(line!=null)matchLine(road,line);}
    }
    private void matchLine(Road road,JSONArray line){
        for(int i=1;i<line.length();i++){JSONArray a=line.optJSONArray(i-1),b=line.optJSONArray(i);if(a==null||b==null||a.length()<2||b.length()<2)continue;
            double lng1=a.optDouble(0,Double.NaN),lat1=a.optDouble(1,Double.NaN),lng2=b.optDouble(0,Double.NaN),lat2=b.optDouble(1,Double.NaN);
            double d=distance(lng1,lat1,lng2,lat2);if(!Double.isFinite(d)||d<=0)continue;int n=Math.max(1,(int)Math.ceil(d/25));
            for(int j=0;j<=n;j++){double f=j/(double)n;Anchor near=nearest(road,lng1+(lng2-lng1)*f,lat1+(lat2-lat1)*f);if(near!=null)road.covered.add(near.id);}
        }
    }
    private Anchor nearest(Road road,double lng,double lat){double[] xy=mercator(lng,lat);Anchor best=null;double bd=MATCH_RADIUS_M;long gx=(long)Math.floor(xy[0]/GRID_M),gy=(long)Math.floor(xy[1]/GRID_M);
        for(long x=gx-1;x<=gx+1;x++)for(long y=gy-1;y<=gy+1;y++)for(Anchor a:road.grid.getOrDefault(x+","+y,Collections.emptyList())){double d=Math.hypot(xy[0]-a.x,xy[1]-a.y);if(d<bd){best=a;bd=d;}}return best;}
    private void buildMapSections(Road road){JSONArray current=null;Boolean covered=null;Anchor prev=null;
        for(Anchor a:road.anchors){if(prev==null){prev=a;continue;}if(a.component!=prev.component||distance(prev.lng,prev.lat,a.lng,a.lat)>250){addSection(road,current,covered);current=null;covered=null;prev=a;continue;}
            boolean next=road.covered.contains(prev.id)||road.covered.contains(a.id);
            if(current==null||covered==null||covered!=next){addSection(road,current,covered);current=new JSONArray();current.put(coord(prev));covered=next;}current.put(coord(a));prev=a;
        }addSection(road,current,covered);
    }
    private JSONArray coord(Anchor a){JSONArray p=new JSONArray();try{p.put(a.lng);p.put(a.lat);}catch(org.json.JSONException ignored){}return p;}
    private void addSection(Road r,JSONArray pts,Boolean isCovered){if(pts==null||pts.length()<2)return;(isCovered?r.coveredMapSections:r.incompleteMapSections).add(pts);}
    private static boolean isNi(JSONObject feature){JSONObject g=feature.optJSONObject("geometry");JSONArray c=g==null?null:g.optJSONArray("coordinates");if(c==null||c.length()==0)return false;JSONArray p=c.optJSONArray(0);if("MultiLineString".equals(g.optString("type"))){JSONArray line=c.optJSONArray(0);p=line==null?null:line.optJSONArray(0);}return p!=null&&p.length()>1&&p.optDouble(0)<-5.3&&p.optDouble(1)>53.9&&p.optDouble(1)<55.6;}
    private static String normalize(String s){return s==null?"":s.toUpperCase(Locale.ROOT).replaceAll("\\s+","");}
    private static String gridKey(double x,double y){return (long)Math.floor(x/GRID_M)+","+(long)Math.floor(y/GRID_M);}
    private static double[] mercator(double lng,double lat){double x=6378137*Math.toRadians(lng),y=6378137*Math.log(Math.tan(Math.PI/4+Math.toRadians(Math.max(-85,Math.min(85,lat)))/2));return new double[]{x,y};}
    private static double distance(double lng1,double lat1,double lng2,double lat2){double a=Math.toRadians(lat1),b=Math.toRadians(lat2),dl=Math.toRadians(lng2-lng1),dp=b-a;double h=Math.sin(dp/2)*Math.sin(dp/2)+Math.cos(a)*Math.cos(b)*Math.sin(dl/2)*Math.sin(dl/2);return 2*EARTH_RADIUS_M*Math.atan2(Math.sqrt(h),Math.sqrt(1-h));}
}
