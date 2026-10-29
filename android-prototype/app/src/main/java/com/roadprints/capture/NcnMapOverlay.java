package com.roadprints.capture;

import android.app.Activity;
import org.json.JSONArray;
import java.util.*;

/** Independently prepared NCN overlay; never gates core-map publication/readiness. */
final class NcnMapOverlay {
    static final class Line {
        final String route;final JSONArray points;final boolean completed;final double west,east,south,north;
        Line(String route,JSONArray p,boolean completed){this.route=route;points=p;this.completed=completed;double w=180,e=-180,s=90,n=-90;for(int i=0;i<p.length();i++){JSONArray c=p.optJSONArray(i);if(c!=null){w=Math.min(w,c.optDouble(0));e=Math.max(e,c.optDouble(0));s=Math.min(s,c.optDouble(1));n=Math.max(n,c.optDouble(1));}}west=w;east=e;south=s;north=n;}
    }
    private static final java.util.concurrent.ThreadPoolExecutor WORKER=new java.util.concurrent.ThreadPoolExecutor(1,1,0,java.util.concurrent.TimeUnit.MILLISECONDS,new java.util.concurrent.LinkedBlockingQueue<>(),task->new Thread(()->{android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);task.run();},"roadprints-ncn-overlay"));
    private static final java.util.concurrent.atomic.AtomicLong generation=new java.util.concurrent.atomic.AtomicLong();
    private static NcnProgressStore.Snapshot prepared;private static String preparedMode;private static List<Line> cached;
    static void apply(Activity a,RoutePreviewView map){
        long request=generation.incrementAndGet();WORKER.getQueue().clear();
        if(!CollectionAccessStore.NCN.equals(CollectionAccessStore.selected(a))){map.ncnLoadingStatus=null;map.setNcnOverlay(null,null);return;}
        NcnProgressStore.ensure(a);NcnProgressStore.Snapshot s=NcnProgressStore.current();String mode=NcnProgressStore.mode(a);
        if(s==null){map.ncnLoadingStatus="assessment";map.setNcnOverlay(java.util.Collections.emptyList(),routes->choose(a,routes));return;}
        map.ncnLoadingStatus="Loading NCN map…";
        WORKER.execute(()->{long started=android.os.SystemClock.elapsedRealtime();try{
            List<Line> lines;
            if(prepared==s&&mode.equals(preparedMode)&&cached!=null)lines=cached;
            else {lines=new ArrayList<>();for(String route:s.catalogue.routes.keySet()){
                if(request!=generation.get()||a.isDestroyed()||!CollectionAccessStore.NCN.equals(CollectionAccessStore.selected(a))||s!=NcnProgressStore.current()||!mode.equals(NcnProgressStore.mode(a)))return;
                if(s.ledger.metres(route,mode)<=0){for(JSONArray line:s.catalogue.overview(route))lines.add(new Line(route,line,false));}
                else {List<JSONArray> remaining=new ArrayList<>();s.catalogue.targets(route,(key,length,coords)->{if(request!=generation.get())throw new java.util.concurrent.CancellationException();if(length<=0){remaining.add(coords);return;}double cursor=0;for(double[] span:s.ledger.covered(route,key,mode)){if(span[0]>cursor)remaining.add(NcnProgressStore.targetSlice(coords,cursor/length,span[0]/length));cursor=Math.max(cursor,span[1]);}if(cursor<length)remaining.add(NcnProgressStore.targetSlice(coords,cursor/length,1));});for(JSONArray line:NcnGeometry.join(remaining))lines.add(new Line(route,line,false));}
            }
                // Paint only proven source travel, never the offset publisher line.
                java.util.Set<String> seen=new HashSet<>();for(org.json.JSONObject activity:s.ledger.activities.values()){
                    if(request!=generation.get())return;String m=activity.optString("mode");if(!m.equals("cycling")&&!m.equals("on_foot")||!mode.equals("combined")&&!mode.equals(m))continue;
                    JSONArray spans=activity.getJSONArray("spans");for(int i=0;i<spans.length();i++){JSONArray span=spans.getJSONArray(i);String id=span.getString(0)+"/"+span.getString(1);if(!s.catalogue.routes.get(span.getString(0)).optBoolean("measurable",true)||!seen.add(id))continue;
                        for(NcnCatalogue.Path path:s.catalogue.way(span.getString(1)))if(path.route.equals(span.getString(0)))for(double[] covered:s.ledger.source(path,mode,null))lines.add(new Line(path.route,NcnLedger.slice(path,covered[0],covered[1]),true));
                    }
                }prepared=s;preparedMode=mode;cached=lines;
            }
            PerformanceDiagnostics.timing("ncn_overlay",android.os.SystemClock.elapsedRealtime()-started,"lines="+lines.size());
            final List<Line> ready=lines;a.runOnUiThread(()->{if(request==generation.get()&&!a.isFinishing()&&!a.isDestroyed()&&CollectionAccessStore.NCN.equals(CollectionAccessStore.selected(a))&&s==NcnProgressStore.current()&&mode.equals(NcnProgressStore.mode(a))){map.setNcnOverlay(ready,routes->choose(a,routes));map.ncnLoadingStatus=null;}});
        }catch(java.util.concurrent.CancellationException cancelled){/* A newer selection owns publication. */}catch(Exception e){a.runOnUiThread(()->{if(request==generation.get()&&!a.isDestroyed()){map.ncnLoadingStatus="NCN map needs retry";android.widget.Toast.makeText(a,"NCN overlay unavailable. Retry from Legend or Progress.",android.widget.Toast.LENGTH_LONG).show();}});}});
    }
    private static void choose(Activity a,Set<String> routes){
        LinearChoices.show(a,routes);
    }
    private static final class LinearChoices {
        static void show(Activity a,Set<String> routes){android.widget.LinearLayout rows=new android.widget.LinearLayout(a);rows.setOrientation(android.widget.LinearLayout.VERTICAL);
            for(String route:routes){
                NcnProgressStore.Snapshot s=NcnProgressStore.current();try{if(s!=null){org.json.JSONObject r=s.catalogue.routes.get(route);double completed=s.ledger.metres(route,NcnProgressStore.mode(a)),total=r.getDouble("length_m");rows.addView(NcnProgressUi.text(a,route.replace(":"," ")+" · "+(r.optBoolean("measurable",true)?String.format(java.util.Locale.UK,"%.1f%%",100*completed/total):"assessment held"),19,android.graphics.Color.WHITE));rows.addView(NcnProgressUi.text(a,r.optBoolean("measurable",true)?DistanceUnits.format(a,completed)+" completed / "+DistanceUnits.format(a,total)+" reference · "+DistanceUnits.format(a,Math.max(0,total-completed))+" remaining":"Preserved alternative source inventory. Open-target assessment held; no completion awarded.",14,android.graphics.Color.WHITE));}}catch(Exception e){/* Route map exposes retry for unavailable details. */}
                rows.addView(NcnProgressUi.action(a,route.replace(":"," ")+" · VIEW ROUTE & JOURNEYS",()->NcnProgressUi.open(a,route)));
            }
            RoadprintsDialog.show(a,"National Cycle Network",NcnProgressUi.label(NcnProgressStore.mode(a))+" · Purple completed; solid orange verified-open remaining. Choose a verified identity.",rows,"CLOSE",null,null,null);
        }
    }
}
