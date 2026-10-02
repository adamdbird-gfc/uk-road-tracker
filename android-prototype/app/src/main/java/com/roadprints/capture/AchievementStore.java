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
    private static final String HIGH_STREETS = "high_street_settlements";
    private static final double EARTH_METRES_PER_DEGREE = 111_320.0;
    private static final List<Definition> DEFINITIONS = buildDefinitions();

    static final class Definition {
        final String id, icon, title, description, detail, type, roadId;
        final double target, longitude, latitude, radiusMetres;
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
        final String display;
        final boolean[] crossingProgress;
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
                .getStringSet(HIGH_STREETS, Collections.emptySet()).hashCode();
    }

    static Snapshot calculate(Context context) {
        Context app=context.getApplicationContext();
        long revision=JourneyStore.dataRevision(app);
        MotorwayProgressCalculator motorways=new MotorwayProgressCalculator(app, null, false);
        ARoadProgressCalculator aRoads=new ARoadProgressCalculator(app);
        CrossingTracker crossingTracker=new CrossingTracker();
        JourneyStore.forEach(app, journey -> {
            if (!"complete".equals(journey.optString("processing_status", ""))) return;
            String mode=journey.optString("mode", "unknown").toLowerCase(Locale.ROOT);
            boolean roadMode="driving".equals(mode) || "bus".equals(mode);
            if (!roadMode) return;
            motorways.addJourney(journey);
            aRoads.addJourney(journey);
            JSONObject result=journey.optJSONObject("processing_result");
            if (result == null) return;
            crossingTracker.add(result.optJSONObject("road_geojson"));
            crossingTracker.add(result.optJSONObject("motorway_geojson"));
            crossingTracker.add(result.optJSONObject("a_road_geojson"));
        });
        MotorwayProgressCalculator.Summary motorwaySummary=motorways.finish();
        ARoadProgressCalculator.Summary aRoadSummary=aRoads.finish();

        Map<String, Double> values=new LinkedHashMap<>();
        values.put("motorway-quarter", motorwaySummary.ukPercent());
        values.put("motorway-halfway", motorwaySummary.ukPercent());
        values.put("motorway-three-quarters", motorwaySummary.ukPercent());
        values.put("motorway-complete", motorwaySummary.ukPercent());
        values.put("m1-pioneer", hasMotorwayCoverage(motorwaySummary, "M1") ? 1.0 : 0.0);
        values.put("m62-summit", motorwaySummitReached(motorwaySummary) ? 1.0 : 0.0);
        int highStreetCount=app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getStringSet(HIGH_STREETS, Collections.emptySet()).size();
        values.put("mary-high-streets", (double)highStreetCount);
        values.put("angel-of-the-north", aRoadLandmarkReached(aRoadSummary, "GB:A1",
                -1.5908431, 54.91330845, 250) ? 1.0 : 0.0);
        values.put("stonehenge-solstice", aRoadLandmarkReached(aRoadSummary, "GB:A303",
                -1.8262, 51.1789, 500) ? 1.0 : 0.0);
        boolean[] crossings=crossingTracker.completed();
        int completeCrossings=0;
        for (boolean crossing : crossings) if (crossing) completeCrossings++;
        values.put("spanning-the-nation", (double)completeCrossings);
        values.putAll(ServiceStationStore.achievementValues(app));

        SharedPreferences preferences=app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        JSONObject unlocked=readUnlocked(preferences);
        List<Definition> newlyUnlocked=new ArrayList<>();
        List<Progress> output=new ArrayList<>();
        for (Definition definition : DEFINITIONS) {
            double value=values.getOrDefault(definition.id, 0.0);
            boolean earned=value >= definition.target;
            boolean already=unlocked.has(definition.id);
            if (earned && !already) {
                try {
                    unlocked.put(definition.id, new JSONObject().put("unlocked_at", System.currentTimeMillis()));
                } catch (org.json.JSONException ignored) { }
                newlyUnlocked.add(definition);
                already=true;
            }
            int total=definition.id.equals("spanning-the-nation") ? CROSSINGS.size()
                    : definition.id.equals("mary-high-streets") ? 10 : (int)definition.target;
            int complete=definition.id.equals("spanning-the-nation") ? completeCrossings
                    : definition.id.equals("mary-high-streets") ? highStreetCount
                    : (int)Math.min(total, Math.floor(value));
            output.add(new Progress(definition, value, complete, total, already,
                    progressText(definition, value, complete, total, already),
                    "spanning-the-nation".equals(definition.id) ? crossings : null));
        }
        if (!newlyUnlocked.isEmpty()) preferences.edit().putString("unlocked", unlocked.toString()).apply();
        return new Snapshot(output, newlyUnlocked, revision);
    }

    private static String progressText(Definition definition, double value,
                                       int completed, int total, boolean unlocked) {
        if (unlocked) return definition.detail;
        if ("network-percent".equals(definition.type)) {
            return String.format(Locale.UK, "%.1f%% of the UK motorway network · target %.0f%%",
                    value, definition.target);
        }
        if ("crossing-set".equals(definition.type)) {
            return completed + " of " + total + " great road crossings completed";
        }
        if ("high-street-settlement".equals(definition.type)) {
            return completed + " of " + total + " different High Streets discovered";
        }
        return value >= 1 ? "Requirement met" : definition.description;
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

        void add(JSONObject collection) {
            JSONArray features=collection == null ? null : collection.optJSONArray("features");
            if (features == null) return;
            for (int index=0; index<features.length(); index++) {
                JSONObject feature=features.optJSONObject(index);
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
        output.add(new Definition("mary-high-streets", "👑", "Nice to meet you Mary",
                "Visit 10 different roads named High Street.",
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

    static List<Crossing> crossings() { return CROSSINGS; }
}
