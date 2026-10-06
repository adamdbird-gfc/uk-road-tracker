package com.roadprints.capture;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Local, evidence-based achievement definitions and durable unlock state. */
final class AchievementStore {
    private static final String PREFS = "roadprints_achievements_v1";
    static final int CATALOGUE_VERSION=2;
    private static final double MILE=1609.344;
    private static final String HIGH_STREETS = "high_street_settlements";
    private static final double EARTH_METRES_PER_DEGREE = 111_320.0;
    private static final List<Definition> DEFINITIONS = buildDefinitions();

    static final class Definition {
        final String id, icon, title, description, detail, type, roadId;
        final double target, longitude, latitude, radiusMetres;
        double[] levels;
        Definition(String id, String icon, String title, String description, String detail,
                   String type, double target, String roadId, double longitude,
                   double latitude, double radiusMetres) {
            this.id=id; this.icon=icon; this.title=title; this.description=description;
            this.detail=detail; this.type=type; this.target=target; this.roadId=roadId;
            this.longitude=longitude; this.latitude=latitude; this.radiusMetres=radiusMetres;
        }
    }

    static final class Crossing {
        final String title, hint;
        final double[][] locations;
        final double radiusMetres;
        Crossing(String title, String hint, double radiusMetres, double[][] locations) {
            this.title=title; this.hint=hint; this.radiusMetres=radiusMetres;
            this.locations=locations;
        }
    }

    static final class Progress {
        final Definition definition;
        final double value;
        final int completed, total;
        final boolean unlocked;
        String display;
        final boolean[] crossingProgress;
        int level;
        List<String> contributions=Collections.emptyList();
        List<String> milestones=Collections.emptyList();
        Progress(Definition definition, double value, int completed, int total,
                 boolean unlocked, String display, boolean[] crossingProgress) {
            this.definition=definition; this.value=value; this.completed=completed;
            this.total=total; this.unlocked=unlocked; this.display=display;
            this.crossingProgress=crossingProgress;
        }
    }

    static final class Snapshot {
        final List<Progress> achievements;
        final List<Definition> newlyUnlocked;
        final long dataRevision;
        Snapshot(List<Progress> achievements, List<Definition> newlyUnlocked, long revision) {
            this.achievements=achievements; this.newlyUnlocked=newlyUnlocked;
            this.dataRevision=revision;
        }
        int unlockedCount() {
            int count=0;
            for (Progress progress : achievements) if (progress.unlocked) count++;
            return count;
        }
    }

    private AchievementStore() {}

    static List<Definition> definitions() { return DEFINITIONS; }

    /** Fixed cards and durable unlocks, without reading or calculating journey coverage. */
    static Snapshot catalogueSnapshot(Context context) {
        JSONObject unlocked=readUnlocked(context.getSharedPreferences(PREFS,Context.MODE_PRIVATE));
        List<Progress> rows=new ArrayList<>();
        for(Definition definition:DEFINITIONS) {
            boolean earned=unlocked.has(definition.id);
            int total="crossing-set".equals(definition.type)?CROSSINGS.size():(int)definition.target;
            Progress progress=new Progress(definition,0,0,total,earned,
                    earned?definition.detail:definition.description,null);
            JSONObject state=unlocked.optJSONObject(definition.id);
            progress.level=state==null?0:state.optInt("level",earned?1:0);
            rows.add(progress);
        }
        return new Snapshot(rows,Collections.emptyList(),Long.MIN_VALUE);
    }

    /** Called by Progress after its existing cached town lookups complete. */
    static void recordHighStreetSettlements(Context context, String roadId,
                                             List<LocalRoadSettlementMatcher.Settlement> settlements) {
        if (roadId == null || !"name:high street".equals(roadId.toLowerCase(Locale.ROOT))) return;
        SharedPreferences preferences=context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        Set<String> names=new HashSet<>(preferences.getStringSet(HIGH_STREETS, Collections.emptySet()));
        for (LocalRoadSettlementMatcher.Settlement settlement : settlements) {
            if (settlement != null && settlement.name != null && !settlement.name.trim().isEmpty()) {
                names.add(normalizeName(settlement.name));
            }
        }
        preferences.edit().putStringSet(HIGH_STREETS, names).apply();
    }

    static int highStreetEvidenceRevision(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getStringSet(HIGH_STREETS, Collections.emptySet()).hashCode()
                +context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getInt("evidence_revision",0);
    }

    static Snapshot calculate(Context context) {
        Context app=context.getApplicationContext();
        long revision=JourneyStore.dataRevision(app);
        MotorwayProgressCalculator motorways=new MotorwayProgressCalculator(app,null,false);
        ARoadProgressCalculator aRoads=new ARoadProgressCalculator(app,false);
        CrossingTracker crossingTracker=new CrossingTracker();
        CoreAchievementEvidence core=new CoreAchievementEvidence();
        try {
            JourneyStore.forEachAchievementEvidence(app,()->false,journey -> {
                core.add(journey);
                if (!"complete".equals(journey.optString("processing_status", ""))) return;
                String mode=journey.optString("mode", "unknown").toLowerCase(Locale.ROOT);
                if (!"driving".equals(mode) && !"bus".equals(mode)) return;
                motorways.addJourney(journey); aRoads.addJourney(journey);
                JSONObject result=journey.optJSONObject("processing_result");
                if(result==null)return;
                crossingTracker.add(result.optJSONObject("road_geojson"),journey);
                crossingTracker.add(result.optJSONObject("motorway_geojson"),journey);
                crossingTracker.add(result.optJSONObject("a_road_geojson"),journey);
            });
        } catch(Exception error){throw new IllegalStateException("Achievement evidence scan failed",error);}
        MotorwayProgressCalculator.Summary motorwaySummary=motorways.finish();
        ARoadProgressCalculator.Summary aRoadSummary=aRoads.finish();
        Map<String,Double> values=new LinkedHashMap<>();
        values.put("motorway-quarter",motorwaySummary.ukPercent());
        values.put("motorway-halfway",motorwaySummary.ukPercent());
        values.put("motorway-three-quarters",motorwaySummary.ukPercent());
        values.put("motorway-complete",motorwaySummary.ukPercent());
        values.put("m1-pioneer",hasMotorwayCoverage(motorwaySummary,"M1")?1.0:0.0);
        values.put("m62-summit",motorwaySummitReached(motorwaySummary)?1.0:0.0);
        values.put("angel-of-the-north",aRoadLandmarkReached(aRoadSummary,"GB:A1",-1.5908431,54.91330845,250)?1.0:0.0);
        values.put("stonehenge-solstice",aRoadLandmarkReached(aRoadSummary,"GB:A303",-1.8262,51.1789,500)?1.0:0.0);
        boolean[] crossings=crossingTracker.completed();int completeCrossings=0;
        for(boolean crossing:crossings)if(crossing)completeCrossings++;
        values.put("spanning-the-nation",(double)completeCrossings);
        values.putAll(ServiceStationStore.achievementValues(app));
        values.put("foot-total",core.footMetres/MILE);values.put("road-total",core.roadMetres/MILE);
        values.put("foot-unique",core.footCoverage.metres()/MILE);values.put("road-unique",core.roadCoverage.metres()/MILE);
        values.put("long-way-home",core.longestFoot>=10*MILE||core.longestRoad>=250*MILE?1.0:0.0);
        Map<String,List<String>> lists=core.roadLists(app);
        for(Map.Entry<String,List<String>> entry:lists.entrySet())values.put(entry.getKey(),(double)entry.getValue().size());
        for(MotorwayProgressCalculator.Road road:motorwaySummary.roads) {
            if(Double.isFinite(road.percent()))values.put("completion-motorway-"+road.id,road.percent());
            lists.put("completion-motorway-"+road.id,Collections.singletonList(road.journeyIds.size()+" matched journeys contribute to this road"));
        }
        for(ARoadProgressCalculator.Road road:aRoadSummary.roads) {
            if(Double.isFinite(road.percent()))values.put("completion-aroad-"+road.id,road.percent());
            lists.put("completion-aroad-"+road.id,Collections.singletonList(road.journeyIds.size()+" matched journeys contribute to this road"));
        }
        synchronized(JourneyStore.class) {
            if(JourneyStore.dataRevision(app)!=revision)throw new java.util.ConcurrentModificationException("Journey evidence changed; retry refresh");
            if(core.captured)recordCaptureEarned(app);
            for(String id:core.edited)recordSavedEdit(app,id,false);
            SharedPreferences preferences=app.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
            values.put("sat-nav-on",preferences.getBoolean("capture_used",false)?1.0:0.0);
            values.put("picasso",(double)preferences.getStringSet("edited_journeys",Collections.emptySet()).size());
            values.put("joining-the-dots",preferences.getBoolean("trace_used",false)?1.0:0.0);
            return evaluate(app,values,lists,crossings,revision);
        }
    }

    static synchronized Snapshot evaluate(Context app,Map<String,Double> values,
            Map<String,List<String>> lists,boolean[] crossings,long revision) {
        SharedPreferences preferences=app.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
        JSONObject unlocked=readUnlocked(preferences);
        JSONObject history;
        try{history=new JSONObject(preferences.getString("level_history","{}"));}catch(Exception error){history=new JSONObject();}
        List<Definition> newly=new ArrayList<>();List<Progress> output=new ArrayList<>();
        for(Definition definition:DEFINITIONS) {
            double value=values.getOrDefault(definition.id,0.0);if(!Double.isFinite(value)||value<0)value=0;
            int level=levelFor(definition,value);
            if("app-use".equals(definition.type)&&unlocked.has(definition.id))level=1;
            boolean earned=level>0;
            JSONObject dates=history.optJSONObject(definition.id);if(dates==null)dates=new JSONObject();
            boolean firstRecognition=false;
            if(dates.length()==0&&unlocked.has(definition.id)) {
                JSONObject previous=unlocked.optJSONObject(definition.id);
                try{dates.put("1",previous==null?System.currentTimeMillis():previous.optLong("unlocked_at",System.currentTimeMillis()));}catch(Exception ignored){}
            }
            try {
                for(int i=1;i<=level;i++)if(!dates.has(String.valueOf(i))) {
                    dates.put(String.valueOf(i),System.currentTimeMillis());firstRecognition=true;
                }
                history.put(definition.id,dates);
                if(earned)unlocked.put(definition.id,new JSONObject().put("level",level).put("unlocked_at",dates.optLong("1")));
                else unlocked.remove(definition.id);
            } catch(org.json.JSONException ignored){}
            if(firstRecognition)newly.add(definition);
            double target=nextTarget(definition,value);
            Progress progress=new Progress(definition,value,(int)Math.min(target,Math.floor(value)),
                    (int)target,earned,"", "spanning-the-nation".equals(definition.id)?crossings:null);
            progress.level=level;
            progress.display=progressText(app,progress);
            List<String> evidence=lists.get(definition.id);
            if(evidence!=null)progress.contributions=new ArrayList<>(evidence);
            List<String> milestoneDates=new ArrayList<>();
            for(int i=1;i<=(definition.levels==null?1:definition.levels.length);i++) {
                long when=dates.optLong(String.valueOf(i),0);
                if(when>0)milestoneDates.add("Level "+i+" · recognised "+new java.text.SimpleDateFormat("d MMM yyyy",Locale.UK).format(new java.util.Date(when))
                        +(i>level?" · previous milestone":""));
            }
            progress.milestones=milestoneDates;output.add(progress);
        }
        preferences.edit().putString("unlocked",unlocked.toString()).putString("level_history",history.toString()).apply();
        return new Snapshot(output,newly,revision);
    }
    static int levelFor(Definition definition,double value) {
        if(definition.levels==null)return value>=definition.target?1:0;
        int level=0;for(double target:definition.levels)if(value>=target)level++;return level;
    }
    static double nextTarget(Definition definition,double value) {
        if(definition.levels==null)return definition.target;
        for(double target:definition.levels)if(value<target)return target;
        return definition.levels[definition.levels.length-1];
    }
    static String progressText(Context context,Progress progress) {
        Definition d=progress.definition;double target=nextTarget(d,progress.value);
        if("distance".equals(d.type))return (d.id.startsWith("foot")?"Walking/running":"Driving and bus")+" · "+DistanceUnits.format(context,progress.value*MILE)
                +" / "+DistanceUnits.format(context,target*MILE)+(levelFor(d,progress.value)==d.levels.length?" · complete":"");
        if("road-completion".equals(d.type))return String.format(Locale.UK,"%.1f%% of %s · %s %.0f%%",progress.value,roadLabel(d.roadId),
                progress.level==4?"complete":"next",target);
        if("the-knowledge".equals(d.id))return String.format(Locale.UK,"%,d / %,.0f distinct roads unlocked",(int)progress.value,target);
        if("mary-high-streets".equals(d.id)||"mastered-monopoly".equals(d.id))return String.format(Locale.UK,"%,d / %.0f different %s unlocked",(int)progress.value,target,
                "mary-high-streets".equals(d.id)?"High Streets":"Station Roads");
        if("long-journey".equals(d.type))return progress.unlocked?d.detail:"Complete one journey of "+DistanceUnits.format(context,10*MILE)+" on foot or "+DistanceUnits.format(context,250*MILE)+" by road.";
        if("picasso".equals(d.id))return Math.min(5,(int)progress.value)+" / 5 distinct journeys edited";
        if("network-percent".equals(d.type))return String.format(Locale.UK,"%.1f%% of the UK motorway network · target %.0f%%",progress.value,d.target);
        if("crossing-set".equals(d.type))return (int)progress.value+" of "+CROSSINGS.size()+" great road crossings completed";
        return progress.unlocked?d.detail:d.description;
    }

    static synchronized void clearTravelUnlocks(Context context) {
        SharedPreferences p=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
        JSONObject unlocked=readUnlocked(p);
        for(Definition d:DEFINITIONS)if(!"app-use".equals(d.type))unlocked.remove(d.id);
        p.edit().putString("unlocked",unlocked.toString()).apply();
    }
    static synchronized void recordCapture(Context context,JSONObject journey) {
        JSONObject source=journey.optJSONObject("source");
        if(source!=null&&"android_activity_capture".equals(source.optString("type"))&&!journey.optString("ended_at").isEmpty())recordCaptureEarned(context);
    }
    private static void recordCaptureEarned(Context context) {
        SharedPreferences p=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
        if(!p.getBoolean("capture_used",false))p.edit().putBoolean("capture_used",true).putInt("evidence_revision",p.getInt("evidence_revision",0)+1).apply();
    }
    static synchronized void recordSavedEdit(Context context,String journeyId,boolean trace) {
        if(journeyId==null||journeyId.isEmpty())return;
        SharedPreferences p=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
        Set<String> ids=new HashSet<>(p.getStringSet("edited_journeys",Collections.emptySet()));
        boolean changed=ids.add(journeyId)||(trace&&!p.getBoolean("trace_used",false));
        if(changed)p.edit().putStringSet("edited_journeys",ids).putBoolean("trace_used",trace||p.getBoolean("trace_used",false))
                .putInt("evidence_revision",p.getInt("evidence_revision",0)+1).apply();
    }
    static String roadEvidenceKey(String roadId,List<JSONObject> geometries) {
        java.util.TreeSet<String> keys=new java.util.TreeSet<>();for(JSONObject geometry:geometries)keys.add(geometry.toString());
        try {
            java.security.MessageDigest digest=java.security.MessageDigest.getInstance("SHA-256");
            for(String key:keys)digest.update(key.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex=new StringBuilder();for(byte b:digest.digest())hex.append(String.format(Locale.ROOT,"%02x",b&255));
            return "road:"+roadId+":"+hex;
        }catch(Exception error){throw new IllegalStateException(error);}
    }
    private static java.util.TreeSet<String> settlementNames(List<LocalRoadSettlementMatcher.Settlement> settlements) {
        java.util.TreeSet<String> names=new java.util.TreeSet<>();
        if(settlements!=null)for(LocalRoadSettlementMatcher.Settlement settlement:settlements)
            if(settlement!=null&&settlement.name!=null&&!settlement.name.trim().isEmpty())names.add(settlement.name.trim());
        return names;
    }
    static synchronized void recordRoadSettlements(Context context,String roadId,List<JSONObject> geometries,
            List<LocalRoadSettlementMatcher.Settlement> settlements) {
        java.util.TreeSet<String> names=settlementNames(settlements);
        SharedPreferences p=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
        String key=roadEvidenceKey(roadId,geometries);
        // An empty set is a completed lookup, distinct from a missing cache entry.
        if(!p.contains(key)||!names.equals(p.getStringSet(key,Collections.emptySet())))p.edit().putStringSet(key,names)
                .putInt("evidence_revision",p.getInt("evidence_revision",0)+1).apply();
    }
    static List<String> roadSettlements(Context context,String roadId,List<JSONObject> geometries) {
        Set<String> saved=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
                .getStringSet(roadEvidenceKey(roadId,geometries),null);
        if(saved!=null)return new ArrayList<>(saved);
        try {
            List<LocalRoadSettlementMatcher.Settlement> matches=LocalRoadSettlementMatcher.cached(context,roadId,geometries);
            if(matches!=null) {
                recordRoadSettlements(context,roadId,geometries,matches);
                // Return the lookup result directly; never rely on a recursive cache read.
                return new ArrayList<>(settlementNames(matches));
            }
        }catch(Exception ignored){}
        return Collections.emptyList();
    }

    private static JSONObject readUnlocked(SharedPreferences preferences) {
        try { return new JSONObject(preferences.getString("unlocked", "{}")); }
        catch (Exception ignored) { return new JSONObject(); }
    }

    private static boolean hasMotorwayCoverage(MotorwayProgressCalculator.Summary summary, String ref) {
        for (MotorwayProgressCalculator.Road road : summary.roads) {
            if (ref.equals(road.ref) && road.matchedMetres > 0) return true;
        }
        return false;
    }

    private static boolean motorwaySummitReached(MotorwayProgressCalculator.Summary summary) {
        for (MotorwayProgressCalculator.Road road : summary.roads) {
            if (!"M62".equals(road.ref)) continue;
            for (JSONArray line : road.coveredMapSections) {
                for (int index=0; index<line.length(); index++) {
                    JSONArray point=line.optJSONArray(index);
                    if (point != null && point.length() >= 2 && distanceMetres(
                            point.optDouble(0), point.optDouble(1), -2.018561, 53.62982) <= 350) return true;
                }
            }
        }
        return false;
    }

    private static boolean aRoadLandmarkReached(ARoadProgressCalculator.Summary summary,
                                                 String id, double longitude, double latitude,
                                                 double radiusMetres) {
        double[] landmark=mercator(longitude, latitude);
        for (ARoadProgressCalculator.Road road : summary.roads) {
            if (!id.equals(road.id)) continue;
            for (ARoadProgressCalculator.Anchor anchor : road.anchors) {
                if (road.covered.contains(anchor.id)
                        && Math.hypot(anchor.x-landmark[0], anchor.y-landmark[1]) <= radiusMetres) return true;
            }
        }
        return false;
    }

    private static double[] mercator(double longitude, double latitude) {
        double x=6_378_137.0 * Math.toRadians(longitude);
        double limited=Math.max(-85, Math.min(85, latitude));
        double y=6_378_137.0 * Math.log(Math.tan(Math.PI / 4 + Math.toRadians(limited) / 2));
        return new double[]{x,y};
    }

    private static final class CrossingTracker {
        private final boolean[] completed=new boolean[CROSSINGS.size()];

        void add(JSONObject collection, JSONObject journey) {
            JSONArray features=collection == null ? null : collection.optJSONArray("features");
            if (features == null) return;
            for (int index=0; index<features.length(); index++) {
                JSONObject feature=features.optJSONObject(index);
                if (JourneyCorrectionUtils.excludesRoadFeature(journey, feature)) continue;
                JSONObject geometry=feature == null ? null : feature.optJSONObject("geometry");
                if (geometry == null) continue;
                String type=geometry.optString("type", "");
                JSONArray coordinates=geometry.optJSONArray("coordinates");
                if ("LineString".equals(type)) addLine(coordinates);
                else if ("MultiLineString".equals(type) && coordinates != null) {
                    for (int line=0; line<coordinates.length(); line++)
                        addLine(coordinates.optJSONArray(line));
                }
            }
        }

        private void addLine(JSONArray line) {
            if (line == null || line.length() < 2) return;
            for (int crossing=0; crossing<CROSSINGS.size(); crossing++) {
                if (completed[crossing]) continue;
                Crossing target=CROSSINGS.get(crossing);
                for (int segment=1; segment<line.length() && !completed[crossing]; segment++) {
                    JSONArray a=line.optJSONArray(segment-1), b=line.optJSONArray(segment);
                    if (a == null || b == null || a.length() < 2 || b.length() < 2) continue;
                    for (double[] location : target.locations) {
                        if (pointToSegmentMetres(location[0], location[1],
                                a.optDouble(0), a.optDouble(1), b.optDouble(0), b.optDouble(1))
                                <= target.radiusMetres) { completed[crossing]=true; break; }
                    }
                }
            }
        }

        boolean[] completed() { return completed.clone(); }
    }

    private static double pointToSegmentMetres(double longitude, double latitude,
                                                double ax, double ay, double bx, double by) {
        double scaleX=EARTH_METRES_PER_DEGREE*Math.cos(Math.toRadians(latitude));
        double px=(longitude-ax)*scaleX, py=(latitude-ay)*EARTH_METRES_PER_DEGREE;
        double dx=(bx-ax)*scaleX, dy=(by-ay)*EARTH_METRES_PER_DEGREE;
        double denominator=dx*dx+dy*dy;
        double t=denominator <= 0 ? 0 : Math.max(0, Math.min(1, (px*dx+py*dy)/denominator));
        return Math.hypot(px-t*dx, py-t*dy);
    }

    private static double distanceMetres(double longitudeA, double latitudeA,
                                         double longitudeB, double latitudeB) {
        return pointToSegmentMetres(longitudeA, latitudeA, longitudeB, latitudeB,
                longitudeB, latitudeB);
    }

    private static String normalizeName(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private static final List<Crossing> CROSSINGS=Collections.unmodifiableList(Arrays.asList(
            new Crossing("Dartford Crossing", "A282 · bridge or tunnels", 900,
                    new double[][]{{0.265,51.462}}),
            new Crossing("Severn Crossing", "M4 or M48", 1250,
                    new double[][]{{-2.695,51.553},{-2.642,51.553},{-2.592,51.553},
                            {-2.705,51.611},{-2.647,51.611},{-2.590,51.611}}),
            new Crossing("Humber Bridge", "A15 · near Hull", 550,
                    new double[][]{{-0.317,53.708}}),
            new Crossing("Blackwall Tunnel", "A102 · London", 350,
                    new double[][]{{0.007,51.500}}),
            new Crossing("Tyne Tunnels", "A19 · near Jarrow", 550,
                    new double[][]{{-1.495,54.985}}),
            new Crossing("Mersey Tunnel", "Kingsway or Queensway", 650,
                    new double[][]{{-2.993,53.405}}),
            new Crossing("Queensferry Crossing", "M90 · Forth", 650,
                    new double[][]{{-3.415,56.001}})));

    private static List<Definition> buildDefinitions() {
        List<Definition> output=new ArrayList<>();
        output.add(family("foot-total","Going the distance","Walking/running","distance",new double[]{1,25,100,500,1000}));
        output.add(family("road-total","Going the distance","Driving and bus","distance",new double[]{10,100,1000,10000,25000}));
        output.add(family("foot-unique","Blazing a trail","Unique walking/running coverage","distance",new double[]{1,10,50,100,250}));
        output.add(family("road-unique","Blazing a trail","Unique driving and bus coverage","distance",new double[]{10,100,500,1000,5000}));
        output.add(family("the-knowledge","The Knowledge","A nod to London taxi drivers’ street knowledge. Unlock distinct roads across eligible modes.","road-count",new double[]{10,100,500,1000,5000}));
        output.add(new Definition("sat-nav-on","","Sat nav: on","Complete your first journey recorded by Roadprints.","Your first Roadprints recording completed.","app-use",1,null,0,0,0));
        output.add(new Definition("picasso","","The artist formerly known as Picasso","Save edits to five distinct journeys.","Five distinct journeys edited.","app-use",5,null,0,0,0));
        output.add(new Definition("joining-the-dots","","Joining the dots","Save your first correction trace using the drawing tool.","Your first correction trace saved.","app-use",1,null,0,0,0));
        output.add(new Definition("long-way-home","","Taking the long way home","Complete one journey of 10 miles on foot or 250 miles by road.","A long journey completed.","long-journey",1,null,0,0,0));
        output.add(new Definition("mastered-monopoly","","Mastered Monopoly","Unlock four different Station Roads. You do not collect £200.","Four different Station Roads unlocked. You do not collect £200.","road-count",4,null,0,0,0));
        for(String id:MotorwayProgressCalculator.canonicalRoadIds()) output.add(roadFamily("completion-motorway-"+id,id));
        // The two highest A-road tiers, using the bundled canonical GB catalogue.
        for(String id:ELIGIBLE_A_ROADS.split(","))output.add(roadFamily("completion-aroad-"+id,id));
        output.add(new Definition("motorway-quarter", "¼", "Quarter Marker",
                "Complete one quarter of the UK motorway network.",
                "25% of the UK motorway network completed", "network-percent", 25, null,0,0,0));
        output.add(new Definition("motorway-halfway", "½", "Halfway There",
                "Complete half of the UK motorway network.",
                "50% of the UK motorway network completed", "network-percent", 50, null,0,0,0));
        output.add(new Definition("motorway-three-quarters", "¾", "The Home Straight",
                "Complete three quarters of the UK motorway network.",
                "75% of the UK motorway network completed", "network-percent", 75, null,0,0,0));
        output.add(new Definition("motorway-complete", "★", "Completed It, Mate",
                "Complete the entire UK motorway network.",
                "100% of the UK motorway network completed", "network-percent", 100, null,0,0,0));
        output.add(new Definition("m1-pioneer", "①", "The Pioneer",
                "Drive on the M1, Britain’s first inter-urban motorway.",
                "M1 · Britain’s first inter-urban motorway, opened in 1959", "motorway-visited", 1,"M1",0,0,0));
        output.add(new Definition("m62-summit", "🏔️", "M62 Summit",
                "Cross the UK’s highest motorway point at Windy Hill.",
                "372 m (1,221 ft) above sea level · M62, near junction 22", "summit", 1,"M62",-2.018561,53.62982,350));
        output.add(new Definition("mary-high-streets", "👑", "Nice to meet you, Mary",
                "A nod to Mary Portas, Queen of Shops. Unlock 10 different High Streets.",
                "10 different High Streets discovered", "high-street-settlement", 10,null,0,0,0));
        output.add(new Definition("angel-of-the-north", "👼", "I Saw an Angel",
                "Drive the A1 alongside the Angel of the North in Gateshead.",
                "A1 · Angel of the North, Gateshead", "a-road-landmark", 1,"GB:A1",-1.5908431,54.91330845,250));
        output.add(new Definition("stonehenge-solstice", "🌞", "Enjoying the Solstice",
                "Drive the A303 past Stonehenge.", "A303 · Stonehenge, Wiltshire",
                "a-road-landmark", 1,"GB:A303",-1.8262,51.1789,500));
        output.add(new Definition("spanning-the-nation", "🌉", "Spanning the Nation",
                "Complete the UK’s great road crossings.",
                "Seven great road crossings completed", "crossing-set", 7,null,0,0,0));
        output.add(new Definition("service-first-stop", "⛽", "First Stop",
                "Visit your first motorway service area.",
                "Your first motorway service area is on the board.", "service-station", 1,null,0,0,0));
        output.add(new Definition("service-ten-stops", "🔟", "Ten Stops",
                "Visit ten different motorway service areas.",
                "Ten different motorway service areas collected.", "service-station", 1,null,0,0,0));
        output.add(new Definition("service-moneybags", "💰", "Moneybags",
                "Stop at Norton Canes on the M6 Toll.",
                "M6 Toll · Norton Canes Services", "service-station", 1,null,0,0,0));
        output.add(new Definition("service-being-posh", "🎩", "Being Posh",
                "Stop at Peterborough Services on the A1(M).",
                "A1(M) · Peterborough Services", "service-station", 1,null,0,0,0));
        return Collections.unmodifiableList(output);
    }

    private static Definition family(String id,String title,String copy,String type,double[] levels) {
        Definition definition=new Definition(id,"",title,copy,copy,type,levels[0],null,0,0,0);
        definition.levels=levels;return definition;
    }
    private static String roadLabel(String road) {
        return road.startsWith("GB:")?road.substring(3):road.startsWith("NI:")?road.substring(3)+" (Northern Ireland)":road;
    }
    private static Definition roadFamily(String id,String road) {
        Definition definition=new Definition(id,"",roadLabel(road)+" completion","Complete 25%, 50%, 75% and 100% of "+roadLabel(road)+".",
                roadLabel(road)+" completed","road-completion",25,road,0,0,0);
        definition.levels=new double[]{25,50,75,100};return definition;
    }
    private static final String ELIGIBLE_A_ROADS="GB:A1,GB:A2,GB:A3,GB:A4,GB:A5,GB:A6,GB:A7,GB:A8,GB:A9,GB:A10,GB:A11,GB:A12,GB:A13,GB:A14,GB:A15,GB:A16,GB:A17,GB:A18,GB:A19,GB:A20,GB:A21,GB:A22,GB:A23,GB:A24,GB:A25,GB:A26,GB:A27,GB:A28,GB:A29,GB:A30,GB:A31,GB:A32,GB:A33,GB:A34,GB:A35,GB:A36,GB:A37,GB:A38,GB:A39,GB:A40,GB:A41,GB:A42,GB:A43,GB:A44,GB:A45,GB:A46,GB:A47,GB:A48,GB:A49,GB:A50,GB:A51,GB:A52,GB:A53,GB:A54,GB:A55,GB:A56,GB:A57,GB:A58,GB:A59,GB:A60,GB:A61,GB:A62,GB:A63,GB:A64,GB:A65,GB:A66,GB:A67,GB:A68,GB:A69,GB:A70,GB:A71,GB:A72,GB:A73,GB:A74,GB:A75,GB:A76,GB:A77,GB:A78,GB:A79,GB:A80,GB:A81,GB:A82,GB:A83,GB:A84,GB:A85,GB:A86,GB:A87,GB:A88,GB:A89,GB:A90,GB:A91,GB:A92,GB:A93,GB:A94,GB:A95,GB:A96,GB:A97,GB:A98,GB:A99";
    static List<Crossing> crossings() { return CROSSINGS; }
}
