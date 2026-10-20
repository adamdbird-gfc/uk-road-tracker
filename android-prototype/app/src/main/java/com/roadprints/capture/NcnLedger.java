package com.roadprints.capture;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

/** Interval unions: repeated journeys and walking/cycling overlap count once. */
final class NcnLedger {
    final NcnCatalogue catalogue;
    final Map<String,JSONObject> activities=new java.util.LinkedHashMap<>();
    NcnLedger(NcnCatalogue catalogue){this.catalogue=catalogue;}
    void remove(String id){activities.remove(id);index=null;}
    private Map<String,Map<String,List<double[]>>> index;
    private final Map<String,Double> totals=new HashMap<>();
    private final Map<String,List<double[]>> sourceIndex=new HashMap<>();
    void put(String id,JSONObject assessment){activities.put(id,assessment);index=null;}
    private void prepare() throws Exception {
        if(index!=null)return;
        Map<String,Map<String,List<double[]>>> built=new HashMap<>();sourceIndex.clear();
        // Union repeated source evidence before catalogue access. Commuting over the
        // same way 1,000 times must decode its compressed path once, not 1,000 times.
        Map<String,Map<String,List<double[]>>> raw=new HashMap<>();
        for(JSONObject activity:activities.values()) {
            String mode=activity.getString("mode");
            if(!mode.equals("cycling")&&!mode.equals("on_foot"))continue;
            JSONArray spans=activity.getJSONArray("spans");
            for(int i=0;i<spans.length();i++) {
                if(Thread.currentThread().isInterrupted())throw new InterruptedException();
                JSONArray span=spans.getJSONArray(i);
                String route=span.getString(0);
                JSONObject definition=catalogue.routes.get(route);
                if(definition==null||!definition.optBoolean("measurable",true))continue;
                String key=route+"/"+span.getString(1);
                raw.computeIfAbsent(key,k->new HashMap<>()).computeIfAbsent(mode,k->new ArrayList<>())
                        .add(new double[]{span.getDouble(2),span.getDouble(3)});
            }
        }
        for(Map.Entry<String,Map<String,List<double[]>>> entry:raw.entrySet()) {
            int split=entry.getKey().indexOf('/');
            String route=entry.getKey().substring(0,split),way=entry.getKey().substring(split+1);
            List<NcnCatalogue.Path> paths=catalogue.way(way);
            for(Map.Entry<String,List<double[]>> modeEntry:entry.getValue().entrySet()) {
                String mode=modeEntry.getKey();
                for(double[] span:merge(modeEntry.getValue()))for(NcnCatalogue.Path p:paths)if(p.route.equals(route))
                    for(int j=0;j<p.bindings.length();j++) {
                        JSONObject b=p.bindings.getJSONObject(j);if(!has(b.getJSONArray("modes"),mode))continue;
                        double first=b.getDouble("start"),last=b.getDouble("end");
                        double a=Math.max(first,span[0]),z=Math.min(last,span[1]);if(z<=a)continue;
                        sourceIndex.computeIfAbsent(mode+"/"+p.route+"/"+p.way,k->new ArrayList<>()).add(new double[]{a,z});
                        sourceIndex.computeIfAbsent("combined/"+p.route+"/"+p.way,k->new ArrayList<>()).add(new double[]{a,z});
                        double low=b.getDouble("low"),high=b.getDouble("high");
                        double[] interval={low+(a-first)/(last-first)*(high-low),low+(z-first)/(last-first)*(high-low)};
                        String key=p.route+"/"+b.getString("target");
                        add(built,mode,key,interval);add(built,"combined",key,interval);
                    }
            }
        }
        for(Map<String,List<double[]>> values:built.values())for(String key:new ArrayList<>(values.keySet()))values.put(key,merge(values.get(key)));
        // Convert route-local logical offsets piecewise onto the canonical physical
        // target union. Shared identities and activity modes cannot inflate totals.
        for(String mode:new String[]{"combined","cycling","on_foot"}){
            Map<String,List<double[]>> values=built.get(mode);if(values==null)continue;
            for(String qualified:new ArrayList<>(values.keySet())){
                int split=qualified.indexOf('/');String route=qualified.substring(0,split),target=qualified.substring(split+1);
                JSONArray pieces=catalogue.network(route,target);
                for(int i=0;i<pieces.length();i++){JSONArray piece=pieces.getJSONArray(i);double a=piece.getDouble(1),z=piece.getDouble(2),length=piece.getDouble(3);
                    for(double[] span:values.get(qualified)){double low=Math.max(span[0],Math.min(a,z)),high=Math.min(span[1],Math.max(a,z));if(high>low)add(built,mode,"network/"+piece.getInt(0),new double[]{(low-a)/(z-a)*length,(high-a)/(z-a)*length});}
                }
            }
        }
        for(Map<String,List<double[]>> values:built.values())for(String key:new ArrayList<>(values.keySet()))values.put(key,merge(values.get(key)));
        for(String key:sourceIndex.keySet())sourceIndex.put(key,merge(sourceIndex.get(key)));
        totals.clear();
        for(Map.Entry<String,Map<String,List<double[]>>> mode:built.entrySet())for(Map.Entry<String,List<double[]>> entry:mode.getValue().entrySet()){
            String key=entry.getKey();String owner=key.startsWith("network/")?"network":key.substring(0,key.indexOf('/'));
            double length=0;for(double[] span:entry.getValue())length+=span[1]-span[0];
            String id=mode.getKey()+"/"+owner;totals.put(id,totals.getOrDefault(id,0.0)+length);
        }
        index=built;
    }
    private static void add(Map<String,Map<String,List<double[]>>> index,String mode,String key,double[] span){index.computeIfAbsent(mode,k->new HashMap<>()).computeIfAbsent(key,k->new ArrayList<>()).add(span);}
    double networkMetres(String mode)throws Exception{prepare();return totals.getOrDefault(mode+"/network",0.0);}
    List<double[]> covered(String route,String target,String mode) throws Exception {
        prepare();return index.getOrDefault(mode,java.util.Collections.emptyMap()).getOrDefault(route+"/"+target,java.util.Collections.emptyList());
    }
    double metres(String route,String mode) throws Exception {
        prepare();return totals.getOrDefault(mode+"/"+route,0.0);
    }
    List<double[]> source(NcnCatalogue.Path path,String mode,JSONObject onlyActivity) throws Exception {
        if(onlyActivity==null){prepare();return sourceIndex.getOrDefault(mode+"/"+path.route+"/"+path.way,java.util.Collections.emptyList());}
        List<double[]> intervals=new ArrayList<>();
        Iterable<JSONObject> records=onlyActivity==null?activities.values():java.util.Collections.singletonList(onlyActivity);
        for(JSONObject a:records) {
            String m=a.getString("mode");if((!m.equals("on_foot")&&!m.equals("cycling"))||(!mode.equals("combined")&&!mode.equals(m)))continue;
            JSONArray spans=a.getJSONArray("spans");for(int i=0;i<spans.length();i++){JSONArray s=spans.getJSONArray(i);if(s.getString(0).equals(path.route)&&s.getString(1).equals(path.way)) {
                // A source can have different access modes along its explicit target bindings.
                for(int j=0;j<path.bindings.length();j++){JSONObject b=path.bindings.getJSONObject(j);if(!has(b.getJSONArray("modes"),m))continue;
                    double low=Math.max(s.getDouble(2),b.getDouble("start")),high=Math.min(s.getDouble(3),b.getDouble("end"));if(high>low)intervals.add(new double[]{low,high});}
            }}
        }return merge(intervals);
    }
    static boolean has(JSONArray values,String mode){for(int i=0;i<values.length();i++)if(values.optString(i).equals(mode))return true;return false;}
    static List<double[]> merge(List<double[]> spans) {
        List<double[]> sorted=new ArrayList<>();for(double[] s:spans){double a=Math.min(s[0],s[1]),z=Math.max(s[0],s[1]);if(Double.isFinite(a)&&Double.isFinite(z)&&z>a)sorted.add(new double[]{a,z});}
        sorted.sort((a,b)->Double.compare(a[0],b[0]));List<double[]> result=new ArrayList<>();
        for(double[] span:sorted){if(result.isEmpty()||span[0]>result.get(result.size()-1)[1]+1e-7)result.add(span);else result.get(result.size()-1)[1]=Math.max(result.get(result.size()-1)[1],span[1]);}return result;
    }
    static JSONArray slice(NcnCatalogue.Path path,double low,double high) throws Exception {
        JSONArray points=new JSONArray();for(int i=0;i<path.positions.length-1;i++) {
            double start=path.positions[i],end=path.positions[i+1];if(end<=start||end<=low||start>=high)continue;
            double a=Math.max(start,low),z=Math.min(end,high);JSONArray first=path.coordinates.getJSONArray(i),last=path.coordinates.getJSONArray(i+1);
            if(points.length()==0)points.put(point(first,last,(a-start)/(end-start)));points.put(point(first,last,(z-start)/(end-start)));
        }return points;
    }
    private static JSONArray point(JSONArray a,JSONArray b,double t) throws Exception {
        return new JSONArray().put(a.getDouble(0)+t*(b.getDouble(0)-a.getDouble(0))).put(a.getDouble(1)+t*(b.getDouble(1)-a.getDouble(1)));
    }
}
