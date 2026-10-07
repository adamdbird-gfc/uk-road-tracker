package com.roadprints.capture;

import android.content.Context;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;

/** Offline, retained-route evidence. Crossing runs stay contiguous; mileage bins are unique. */
final class LandmarkEvidence {
    static final double METRES=111320, SCALE=METRES*Math.cos(Math.toRadians(54));
    static final class Sample {
        double x,y,dx,dy,weight; int index; String ref;
    }
    static final class Reference {
        String id,kind; double tolerance,minimum,total,scale; Set<String> modes=new HashSet<>(),areaModes=new HashSet<>();
        List<Sample> samples=new ArrayList<>(); Map<Long,List<Sample>> grid=new HashMap<>();
        double[][] polygon;
    }
    final List<Reference> references=new ArrayList<>();
    final Set<String> completed=new HashSet<>();
    LandmarkEvidence(Context context) throws IOException {
        try(InputStream stream=context.getAssets().open("landmarks/reference.json")) {
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] block=new byte[8192];int n;
            while((n=stream.read(block))!=-1)bytes.write(block,0,n);
            try {load(new JSONObject(bytes.toString(StandardCharsets.UTF_8.name())));}
            catch(JSONException invalid){throw new IOException("Invalid landmark catalogue",invalid);}
        }
    }
    LandmarkEvidence(JSONObject document) throws JSONException {load(document);}
    static double[] xy(double[] p,Reference ref){return new double[]{p[0]*ref.scale,p[1]*METRES};}
    static long key(int x,int y){return ((long)x<<32)^(y&0xffffffffL);}
    void load(JSONObject document) throws JSONException {
        if(document.getInt("version")!=1)throw new JSONException("Unsupported landmark reference");
        JSONArray rows=document.getJSONArray("landmarks");
        for(int r=0;r<rows.length();r++) {
            JSONObject row=rows.getJSONObject(r); Reference ref=new Reference();
            ref.id=row.getString("id");ref.kind=row.getString("kind");ref.tolerance=row.optDouble("tolerance",25);ref.minimum=row.optDouble("minimum",0);
            for(int m=0;m<row.getJSONArray("modes").length();m++)ref.modes.add(row.getJSONArray("modes").getString(m));
            JSONArray areaModes=row.optJSONArray("area_modes");if(areaModes==null&&"area".equals(ref.kind))ref.areaModes.addAll(ref.modes);
            else if(areaModes!=null)for(int m=0;m<areaModes.length();m++)ref.areaModes.add(areaModes.getString(m));
            JSONArray polygon=row.optJSONArray("polygon");
            JSONArray roads=row.optJSONArray("roads");
            JSONArray origin=polygon!=null?polygon.getJSONArray(0):roads.getJSONObject(0).getJSONArray("coordinates").getJSONArray(0);
            ref.scale=METRES*Math.cos(Math.toRadians(origin.getDouble(1)));if(polygon!=null){ref.polygon=new double[polygon.length()][];for(int p=0;p<polygon.length();p++)ref.polygon[p]=xy(TravelAchievementRoutes.point(polygon.getJSONArray(p)),ref);}
            if(roads!=null)for(int road=0;road<roads.length();road++) {
                JSONObject value=roads.getJSONObject(road);JSONArray line=value.getJSONArray("coordinates");
                for(int p=1;p<line.length();p++) {
                    double[] a=xy(TravelAchievementRoutes.point(line.getJSONArray(p-1)),ref),b=xy(TravelAchievementRoutes.point(line.getJSONArray(p)),ref);
                    double dx=b[0]-a[0],dy=b[1]-a[1],length=Math.hypot(dx,dy);if(length<.01)continue;
                    int count=(int)Math.ceil(length/5);
                    for(int i=0;i<count;i++) {
                        Sample s=new Sample();double t=(i+.5)/count;s.x=a[0]+t*dx;s.y=a[1]+t*dy;s.dx=dx/length;s.dy=dy/length;s.weight=length/count;s.ref=value.optString("ref","");s.index=ref.samples.size();
                        ref.samples.add(s);ref.total+=s.weight;ref.grid.computeIfAbsent(key((int)Math.floor(s.x/100),(int)Math.floor(s.y/100)),k->new ArrayList<>()).add(s);
                    }
                }
            }
            references.add(ref);
        }
    }
    void add(JSONObject journey) {
        if(!"complete".equals(journey.optString("processing_status")))return;
        String mode=journey.optString("mode","").toLowerCase(Locale.ROOT);if(!TravelAchievementRoutes.eligible(mode))return;
        List<List<double[]>> parts=TravelAchievementRoutes.matched(journey,true);if(parts.isEmpty())return;
        for(Reference ref:references) {
            if(completed.contains(ref.id)||!ref.modes.contains(mode))continue;
            double areaLength=0;BitSet all=new BitSet(),run=new BitSet();double[] previous=null;
            Set<String> allowedRefs=new HashSet<>(),checkedRefs=new HashSet<>();for(Sample s:ref.samples)if(checkedRefs.add(s.ref)&&(s.ref.isEmpty()||TravelAchievementRoutes.hasRoad(journey,s.ref)))allowedRefs.add(s.ref);
            for(List<double[]> part:parts) {
                double[] first=part.get(0);if(previous!=null&&TravelAchievementRoutes.metres(previous,first)>2)run.clear();
                for(int edge=1;edge<part.size();edge++) {
                    double[] a=xy(part.get(edge-1),ref),b=xy(part.get(edge),ref);
                    if(ref.polygon!=null&&ref.areaModes.contains(mode))areaLength+=insideLength(a,b,ref.polygon);
                    cover(ref,a,b,run,allowedRefs);
                }
                all.or(run);previous=part.get(part.size()-1);
                if("crossing".equals(ref.kind)&&!ref.samples.isEmpty()&&run.get(0)&&run.get(ref.samples.size()-1)&&weight(ref,run)>=ref.total*.95){completed.add(ref.id);break;}
            }
            if(areaLength>=10||("distance".equals(ref.kind)&&weight(ref,all)>=ref.minimum))completed.add(ref.id);
        }
    }
    static double weight(Reference ref,BitSet bits){double total=0;for(int i=bits.nextSetBit(0);i>=0;i=bits.nextSetBit(i+1))total+=ref.samples.get(i).weight;return total;}
    static void cover(Reference ref,double[] a,double[] b,BitSet bits,Set<String> refs) {
        double dx=b[0]-a[0],dy=b[1]-a[1],length=Math.hypot(dx,dy);if(length<.01)return;
        // Split long route edges for bounded grid queries, without changing coverage.
        int steps=(int)Math.ceil(length/100);
        for(int step=0;step<steps;step++) {
            double ax=a[0]+dx*step/steps,ay=a[1]+dy*step/steps,bx=a[0]+dx*(step+1)/steps,by=a[1]+dy*(step+1)/steps;
            int minX=(int)Math.floor((Math.min(ax,bx)-ref.tolerance)/100),maxX=(int)Math.floor((Math.max(ax,bx)+ref.tolerance)/100);
            int minY=(int)Math.floor((Math.min(ay,by)-ref.tolerance)/100),maxY=(int)Math.floor((Math.max(ay,by)+ref.tolerance)/100);
            for(int x=minX;x<=maxX;x++)for(int y=minY;y<=maxY;y++) {
                List<Sample> candidates=ref.grid.get(key(x,y));if(candidates==null)continue;
                for(Sample s:candidates) {
                    if(bits.get(s.index)||!refs.contains(s.ref)||Math.abs(dx*s.dx+dy*s.dy)/length<.85)continue;
                    double t=((s.x-a[0])*dx+(s.y-a[1])*dy)/(length*length);
                    if(t<0||t>1)continue;
                    if(Math.hypot(s.x-a[0]-t*dx,s.y-a[1]-t*dy)<=ref.tolerance)bits.set(s.index);
                }
            }
        }
    }
    static boolean inside(double x,double y,double[][] polygon) {
        boolean found=false;for(int i=0,j=polygon.length-1;i<polygon.length;j=i++) {
            double[] a=polygon[i],b=polygon[j];if((a[1]>y)!=(b[1]>y)&&x<(b[0]-a[0])*(y-a[1])/(b[1]-a[1])+a[0])found=!found;
        }return found;
    }
    static double insideLength(double[] a,double[] b,double[][] polygon) {
        List<Double> cuts=new ArrayList<>(Arrays.asList(0.0,1.0));double dx=b[0]-a[0],dy=b[1]-a[1];
        for(int i=1;i<polygon.length;i++) {
            double[] p=polygon[i-1],q=polygon[i];double ex=q[0]-p[0],ey=q[1]-p[1],cross=dx*ey-dy*ex;if(Math.abs(cross)<1e-9)continue;
            double t=((p[0]-a[0])*ey-(p[1]-a[1])*ex)/cross,u=((p[0]-a[0])*dy-(p[1]-a[1])*dx)/cross;
            if(t>0&&t<1&&u>=0&&u<=1)cuts.add(t);
        }
        Collections.sort(cuts);double fraction=0;
        for(int i=1;i<cuts.size();i++){double t=(cuts.get(i)+cuts.get(i-1))/2;if(inside(a[0]+t*dx,a[1]+t*dy,polygon))fraction+=cuts.get(i)-cuts.get(i-1);}
        return fraction*Math.hypot(dx,dy);
    }
    void legacy(String id,boolean earned){if(earned)completed.add(id);}
    int count(){return completed.size();}
    List<String> checklist(){List<String> rows=new ArrayList<>();for(String[] entry:LandmarkDefinitions.ALL)rows.add((completed.contains(entry[0])?"✓ ":"○ ")+entry[2]);return rows;}
}
