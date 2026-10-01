package com.roadprints.capture;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.zip.GZIPInputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
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

/** Derives motorway mileage and canonical section coverage from saved matched journeys. */
final class MotorwayProgressCalculator {
    private static final double EARTH_RADIUS_M = 6_371_000.0;
    private static final double MERCATOR_RADIUS_M = 6_378_137.0;
    private static final double REFERENCE_SAMPLE_M = 100.0;
    private static final double MATCH_SAMPLE_M = 25.0;
    private static final double MATCH_RADIUS_M = 110.0;
    private static final double DEDUPE_RADIUS_M = 95.0;
    private static final double GRID_CELL_M = 260.0;
    private static final double DEDUPE_CELL_M = 110.0;
    private static final double GB_NETWORK_MILES = 2300.0;
    private static final double NI_NETWORK_MILES = 65.0;

    private static final Map<String, Double> GB_LENGTH_KM = lengths(
            "M1:311.946,M2:41.210,M3:98.947,M4:194.212,M5:260.202,M6:423.978,"
                    + "M6 Toll:43.0,M11:84.419,M18:45.214,M20:82.586,M23:26.725,"
                    + "M25:189.869,M26:16.462,M27:52.695,M32:7.303,M40:144.651,"
                    + "M42:64.619,M45:13.369,M48:8.899,M49:8.611,M50:34.438,"
                    + "M53:32.032,M54:36.078,M55:19.069,M56:55.688,M57:16.050,"
                    + "M58:18.657,M60:56.734,M61:43.989,M62:153.828,M65:32.238,"
                    + "M66:14.297,M67:7.656,M69:26.269,M180:41.076,M181:4.190,"
                    + "M271:3.537,M275:3.0,M602:6.958,M606:4.663,M621:14.803,"
                    + "M8:97.0,M9:53.1,M73:11.0,M74:56.0,M77:32.0,M80:40.0,"
                    + "M90:78.0,M876:13.0,M898:2.0,A74(M):72.0,"
                    + "A1(M):298.7,A194(M):8.8,A3(M):11.4,A308(M):1.6,"
                    + "A329(M):9.6,A404(M):5.3,A48(M):5.9,A57(M):3.4,"
                    + "A58(M):2.2,A627(M):10.2,A64(M):2.2,A66(M):5.1");
    private static final Map<String, Double> NI_LENGTH_KM = lengths(
            "M1:61,M2:37,M3:1.3,M5:3.2,M12:2.4,M22:9");

    static final class Road {
        final String id;
        final String ref;
        final String region;
        final Set<String> journeyIds = new HashSet<>();
        final Set<Integer> coveredSections = new HashSet<>();
        final List<JSONArray> coveredMapSections = new ArrayList<>();
        final List<JSONArray> incompleteMapSections = new ArrayList<>();
        double matchedMetres;
        double referenceKm;
        int referenceSections;
        boolean referenceAvailable;

        Road(String id, String ref, String region) {
            this.id = id;
            this.ref = ref;
            this.region = region;
        }

        double percent() {
            return referenceAvailable && referenceSections > 0
                    ? Math.min(100.0, coveredSections.size() * 100.0 / referenceSections)
                    : Double.NaN;
        }

        double estimatedUniqueKm() {
            double percent = percent();
            return Double.isFinite(percent) ? referenceKm * percent / 100.0 : 0.0;
        }
    }

    static final class Summary {
        final List<Road> roads;
        final double gbUniqueKm;
        final double niUniqueKm;
        final boolean missingReferences;
        final List<String> missingReferenceRoads;

        Summary(List<Road> roads, double gbUniqueKm, double niUniqueKm,
                boolean missingReferences, List<String> missingReferenceRoads) {
            this.roads = roads;
            this.gbUniqueKm = Math.min(GB_NETWORK_MILES / 0.6213711922, gbUniqueKm);
            this.niUniqueKm = Math.min(NI_NETWORK_MILES / 0.6213711922, niUniqueKm);
            this.missingReferences = missingReferences;
            this.missingReferenceRoads = missingReferenceRoads;
        }

        double gbPercent() { return gbUniqueKm / (GB_NETWORK_MILES / 0.6213711922) * 100.0; }
        double niPercent() { return niUniqueKm / (NI_NETWORK_MILES / 0.6213711922) * 100.0; }
        double ukPercent() {
            return Math.min(100.0, (gbUniqueKm + niUniqueKm)
                    / ((GB_NETWORK_MILES + NI_NETWORK_MILES) / 0.6213711922) * 100.0);
        }
        double ukUniqueKm() { return gbUniqueKm + niUniqueKm; }
    }

    private static final class Anchor {
        final int id;
        final double lng, lat, x, y;
        Anchor(int id, double lng, double lat) {
            this.id = id;
            this.lng = lng;
            this.lat = lat;
            double[] projected = mercator(lng, lat);
            x = projected[0];
            y = projected[1];
        }
    }

    private static final class Reference {
        final List<Anchor> anchors = new ArrayList<>();
        final Map<String, List<Anchor>> grid = new HashMap<>();
        final Map<String, List<Anchor>> dedupeGrid = new HashMap<>();
        double totalKm;

        void add(Anchor anchor) {
            anchors.add(anchor);
            grid.computeIfAbsent(gridKey(anchor.x, anchor.y, GRID_CELL_M), k -> new ArrayList<>())
                    .add(anchor);
            dedupeGrid.computeIfAbsent(gridKey(anchor.x, anchor.y, DEDUPE_CELL_M),
                    k -> new ArrayList<>()).add(anchor);
        }

        void addDeduped(double lng, double lat) {
            double[] projected = mercator(lng, lat);
            for (String key : neighbours(projected[0], projected[1], DEDUPE_CELL_M)) {
                for (Anchor existing : dedupeGrid.getOrDefault(key, Collections.emptyList())) {
                    if (Math.hypot(projected[0] - existing.x, projected[1] - existing.y)
                            <= DEDUPE_RADIUS_M) return;
                }
            }
            add(new Anchor(anchors.size(), lng, lat));
        }

        Integer nearest(double lng, double lat) {
            double[] projected = mercator(lng, lat);
            Anchor best = null;
            double bestDistance = MATCH_RADIUS_M;
            for (String key : neighbours(projected[0], projected[1], GRID_CELL_M)) {
                for (Anchor anchor : grid.getOrDefault(key, Collections.emptyList())) {
                    double distance = Math.hypot(projected[0] - anchor.x,
                            projected[1] - anchor.y);
                    if (distance < bestDistance) {
                        best = anchor;
                        bestDistance = distance;
                    }
                }
            }
            return best == null ? null : best.id;
        }
    }

    private final Context context;
    private final Map<String, Road> roads = new TreeMap<>();
    private final Map<String, Reference> references = new HashMap<>();
    private final Map<String, List<double[]>> pendingSegments = new HashMap<>();
    private final Map<String, JSONObject> assetRoads = new HashMap<>();
    private final Set<String> referenceFetchAttempts = new HashSet<>();
    private final boolean allowReferenceFetch;

    MotorwayProgressCalculator(Context context) {
        this(context, null, false);
    }

    MotorwayProgressCalculator(Context context, JSONObject canonicalCache) {
        this(context, canonicalCache, false);
    }

    MotorwayProgressCalculator(Context context, JSONObject canonicalCache,
                               boolean allowReferenceFetch) {
        this.context = context.getApplicationContext();
        this.allowReferenceFetch = allowReferenceFetch;
        if (canonicalCache == null) loadBundledReferences();
        else cacheBundledReferences(canonicalCache);
    }

    void addJourney(JSONObject journey) {
        if (journey == null || !"complete".equals(journey.optString("processing_status", ""))) return;
        String mode = journey.optString("mode", "unknown").toLowerCase(Locale.ROOT);
        if (!("driving".equals(mode) || "bus".equals(mode))) return;
        JSONObject result = journey.optJSONObject("processing_result");
        JSONObject geojson = result == null ? null : result.optJSONObject("motorway_geojson");
        JSONArray features = geojson == null ? null : geojson.optJSONArray("features");
        if (features == null) return;
        String journeyId = journey.optString("journey_id", "");
        for (int index = 0; index < features.length(); index++) {
            JSONObject feature = features.optJSONObject(index);
            if (feature == null) continue;
            JSONObject properties = feature.optJSONObject("properties");
            if (properties == null) continue;
            String rawRef = properties.optString("road_ref", "");
            for (String item : rawRef.split("[;,/]")) {
                String ref = normalizeRef(item);
                if (!isMotorway(ref)) continue;
                String region = firstPointIsNi(feature) ? "NI" : "GB";
                String id = "NI".equals(region) ? "NI:" + ref : ref;
                Road road = roads.computeIfAbsent(id, ignored -> new Road(id, ref, region));
                road.matchedMetres += Math.max(0, properties.optDouble("distance_m", 0));
                if (!journeyId.isEmpty()) road.journeyIds.add(journeyId);

                Reference reference = referenceFor(id, road);
                if (reference != null) {
                    markCovered(reference, road, feature.optJSONObject("geometry"));
                }
            }
        }
    }

    Summary finish() {
        boolean missingReferences = false;
        List<String> missingReferenceRoads = new ArrayList<>();
        for (Road road : roads.values()) {
            Reference reference = references.get(road.id);
            if (reference == null) {
                reference = loadCachedReference(road.id);
                if (reference != null) {
                    references.put(road.id, reference);
                }
            }
            if (reference == null || reference.anchors.isEmpty()) {
                missingReferences = true;
                missingReferenceRoads.add(road.id);
                continue;
            }
            road.referenceAvailable = true;
            road.referenceSections = reference.anchors.size();
            road.referenceKm = reference.totalKm > 0 ? reference.totalKm
                    : reference.anchors.size() * REFERENCE_SAMPLE_M / 1000.0;
            buildMapSections(road, reference);
        }

        double gbKm = 0, niKm = 0;
        for (Road road : roads.values()) {
            if (!road.referenceAvailable) continue;
            if ("NI".equals(road.region)) niKm += road.estimatedUniqueKm();
            else gbKm += road.estimatedUniqueKm();
        }
        List<Road> sorted = new ArrayList<>(roads.values());
        sorted.sort((left, right) -> {
            double leftPercent = left.percent(), rightPercent = right.percent();
            if (Double.isFinite(leftPercent) && Double.isFinite(rightPercent)) {
                int byPercent = Double.compare(rightPercent, leftPercent);
                if (byPercent != 0) return byPercent;
            } else if (Double.isFinite(leftPercent)) return -1;
            else if (Double.isFinite(rightPercent)) return 1;
            int byRegion = left.region.compareTo(right.region);
            if (byRegion != 0) return byRegion;
            return String.CASE_INSENSITIVE_ORDER.compare(left.ref, right.ref);
        });
        return new Summary(sorted, gbKm, niKm, missingReferences, missingReferenceRoads);
    }

    private Reference referenceFor(String id, Road road) {
        Reference existing = references.get(id);
        if (existing != null) return existing;
        Reference saved = loadCachedReference(id);
        if (saved != null) {
            references.put(id, saved);
            return saved;
        }
        JSONObject cached = assetRoads.get(road.ref);
        if (cached == null) return fetchMissingReference(road);
        Reference reference = new Reference();
        JSONArray anchors = cached.optJSONArray("anchors");
        if (anchors == null) return null;
        for (int index = 0; index < anchors.length(); index++) {
            JSONArray point = anchors.optJSONArray(index);
            if (point == null || point.length() < 2) continue;
            double lng = point.optDouble(0, Double.NaN), lat = point.optDouble(1, Double.NaN);
            if (Double.isFinite(lng) && Double.isFinite(lat)
                    && isCoordinateInRegion(road.region, lng, lat)) {
                reference.add(new Anchor(reference.anchors.size(), lng, lat));
            }
        }
        reference.totalKm = ("NI".equals(road.region) ? NI_LENGTH_KM : GB_LENGTH_KM).getOrDefault(road.ref,
                cached.optDouble("total_km", 0));
        if (reference.anchors.isEmpty()) return fetchMissingReference(road);
        references.put(id, reference);
        return reference;
    }

    private Reference fetchMissingReference(Road road) {
        if (!allowReferenceFetch || !referenceFetchAttempts.add(road.id)) return null;
        Reference fetched = fetchReference(road);
        if (fetched != null) references.put(road.id, fetched);
        return fetched;
    }

    private void buildMapSections(Road road, Reference reference) {
        JSONArray current = null;
        Boolean currentCovered = null;
        for (int index = 0; index + 1 < reference.anchors.size(); index++) {
            Anchor start = reference.anchors.get(index);
            Anchor end = reference.anchors.get(index + 1);
            if (haversine(start.lng, start.lat, end.lng, end.lat) > 250.0) {
                addMapSection(road, current, currentCovered);
                current = null;
                currentCovered = null;
                continue;
            }
            boolean covered = road.coveredSections.contains(start.id)
                    || road.coveredSections.contains(end.id);
            if (current == null || currentCovered == null || currentCovered != covered) {
                addMapSection(road, current, currentCovered);
                current = new JSONArray().put(mapCoordinate(start.lng, start.lat));
                currentCovered = covered;
            }
            current.put(mapCoordinate(end.lng, end.lat));
        }
        addMapSection(road, current, currentCovered);
    }

    private void addMapSection(Road road, JSONArray points, Boolean covered) {
        if (points == null || points.length() < 2 || covered == null) return;
        (covered ? road.coveredMapSections : road.incompleteMapSections).add(points);
    }

    private JSONArray mapCoordinate(double lng, double lat) {
        JSONArray coordinate = new JSONArray();
        try {
            coordinate.put(lng);
            coordinate.put(lat);
        } catch (org.json.JSONException ignored) { }
        return coordinate;
    }

    private void loadBundledReferences() {
        try (InputStream input = new GZIPInputStream(
                     context.getAssets().open("canonical-motorways-v1.json.gz"));
             BufferedReader reader = new BufferedReader(new InputStreamReader(input,
                     StandardCharsets.UTF_8))) {
            StringBuilder text = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) text.append(line);
            JSONObject cache = new JSONObject(text.toString());
            if (!"v1".equals(cache.optString("version"))) return;
            cacheBundledReferences(cache);
        } catch (Exception error) {
            assetRoads.clear();
        }
    }

    private void cacheBundledReferences(JSONObject cache) {
        JSONObject cacheRoads = cache.optJSONObject("roads");
        if (cacheRoads == null) return;
        java.util.Iterator<String> names = cacheRoads.keys();
        while (names.hasNext()) {
            String key = names.next();
            if (key != null && isMotorway(normalizeRef(key))) {
                assetRoads.put(normalizeRef(key), cacheRoads.optJSONObject(key));
            }
        }
    }

    private Reference loadCachedReference(String id) {
        File file = referenceFile(id);
        if (!file.isFile()) return null;
        try (FileInputStream input = new FileInputStream(file);
             BufferedReader reader = new BufferedReader(new InputStreamReader(input,
                     StandardCharsets.UTF_8))) {
            StringBuilder text = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) text.append(line);
            JSONObject stored = new JSONObject(text.toString());
            JSONArray points = stored.optJSONArray("anchors");
            if (points == null) return null;
            Reference reference = new Reference();
            for (int index = 0; index < points.length(); index++) {
                JSONArray point = points.optJSONArray(index);
                if (point == null || point.length() < 2) continue;
                reference.add(new Anchor(reference.anchors.size(), point.optDouble(0),
                        point.optDouble(1)));
            }
            reference.totalKm = stored.optDouble("total_km", 0);
            return reference.anchors.isEmpty() ? null : reference;
        } catch (Exception ignored) { return null; }
    }

    private Reference fetchReference(Road road) {
        HttpURLConnection connection = null;
        try {
            String ref = road.ref.replace("\"", "\\\"");
            String query = "NI".equals(road.region)
                    ? "[out:json][timeout:25];way[\"highway\"=\"motorway\"][\"ref\"=\""
                            + ref + "\"](53.9,-8.5,55.6,-5.3);out tags geom;"
                    : "[out:json][timeout:25];area[\"ISO3166-1\"=\"GB\"][admin_level=2]->.region;"
                            + "way(area.region)[\"highway\"=\"motorway\"][\"ref\"=\""
                            + ref + "\"];out tags geom;";
            String url = "https://overpass-api.de/api/interpreter?data="
                    + URLEncoder.encode(query, "UTF-8");
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(8000);
            connection.setRequestProperty("User-Agent", "Roadprints-Android/0.25");
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) return null;
            StringBuilder response = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    connection.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) response.append(line);
            }
            JSONObject data = new JSONObject(response.toString());
            JSONArray elements = data.optJSONArray("elements");
            if (elements == null) return null;
            Reference reference = new Reference();
            for (int index = 0; index < elements.length(); index++) {
                JSONObject element = elements.optJSONObject(index);
                JSONArray geometry = element == null ? null : element.optJSONArray("geometry");
                if (geometry == null || geometry.length() < 2) continue;
                List<double[]> line = new ArrayList<>();
                for (int pointIndex = 0; pointIndex < geometry.length(); pointIndex++) {
                    JSONObject point = geometry.optJSONObject(pointIndex);
                    if (point == null) continue;
                    double lng = point.optDouble("lon", Double.NaN);
                    double lat = point.optDouble("lat", Double.NaN);
                    if (Double.isFinite(lng) && Double.isFinite(lat)
                            && isCoordinateInRegion(road.region, lng, lat)) {
                        line.add(new double[]{lng, lat});
                    } else if (line.size() > 1) {
                        sampleLine(reference, line);
                        line.clear();
                    }
                }
                sampleLine(reference, line);
            }
            if (reference.anchors.isEmpty()) return null;
            reference.totalKm = ("NI".equals(road.region) ? NI_LENGTH_KM : GB_LENGTH_KM)
                    .getOrDefault(road.ref, reference.anchors.size() * REFERENCE_SAMPLE_M / 1000.0);
            saveReference(road.id, reference);
            return reference;
        } catch (Exception ignored) {
            return null;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private void sampleLine(Reference reference, List<double[]> points) {
        if (points.size() < 2) return;
        for (int index = 1; index < points.size(); index++) {
            double[] a = points.get(index - 1), b = points.get(index);
            double distance = haversine(a[0], a[1], b[0], b[1]);
            if (!Double.isFinite(distance) || distance <= 0) continue;
            int samples = Math.max(1, (int) Math.ceil(distance / REFERENCE_SAMPLE_M));
            for (int sample = 0; sample < samples; sample++) {
                double fraction = sample / (double) samples;
                reference.addDeduped(a[0] + (b[0] - a[0]) * fraction,
                        a[1] + (b[1] - a[1]) * fraction);
            }
        }
        double[] last = points.get(points.size() - 1);
        reference.addDeduped(last[0], last[1]);
    }

    private void saveReference(String id, Reference reference) {
        try {
            JSONObject data = new JSONObject().put("total_km", reference.totalKm);
            JSONArray points = new JSONArray();
            for (Anchor anchor : reference.anchors) {
                points.put(new JSONArray().put(anchor.lng).put(anchor.lat));
            }
            data.put("anchors", points);
            File target = referenceFile(id);
            File temp = new File(target.getParentFile(), target.getName() + ".tmp");
            try (FileOutputStream output = new FileOutputStream(temp)) {
                output.write(data.toString().getBytes(StandardCharsets.UTF_8));
                output.flush();
            }
            if (!temp.renameTo(target)) {
                if (target.exists()) target.delete();
                temp.renameTo(target);
            }
        } catch (Exception ignored) { }
    }

    private File referenceFile(String id) {
        String safe = id.replaceAll("[^A-Za-z0-9_-]", "_");
        return new File(context.getFilesDir(), "canonical_motorway_" + safe + "_v1.json");
    }

    private void collectSegments(String id, JSONObject geometry) {
        if (geometry == null) return;
        JSONArray coordinates = geometry.optJSONArray("coordinates");
        if (coordinates == null) return;
        String type = geometry.optString("type", "");
        if ("LineString".equals(type)) addLineSegments(id, coordinates);
        else if ("MultiLineString".equals(type)) {
            for (int index = 0; index < coordinates.length(); index++) {
                JSONArray line = coordinates.optJSONArray(index);
                if (line != null) addLineSegments(id, line);
            }
        }
    }

    private void addLineSegments(String id, JSONArray coordinates) {
        List<double[]> segments = pendingSegments.computeIfAbsent(id, ignored -> new ArrayList<>());
        for (int index = 1; index < coordinates.length(); index++) {
            JSONArray a = coordinates.optJSONArray(index - 1), b = coordinates.optJSONArray(index);
            if (a == null || b == null || a.length() < 2 || b.length() < 2) continue;
            double lngA = a.optDouble(0, Double.NaN), latA = a.optDouble(1, Double.NaN);
            double lngB = b.optDouble(0, Double.NaN), latB = b.optDouble(1, Double.NaN);
            if (Double.isFinite(lngA) && Double.isFinite(latA)
                    && Double.isFinite(lngB) && Double.isFinite(latB)) {
                segments.add(new double[]{lngA, latA, lngB, latB});
            }
        }
    }

    private void markCovered(Reference reference, Road road, JSONObject geometry) {
        if (geometry == null) return;
        JSONArray coordinates = geometry.optJSONArray("coordinates");
        if (coordinates == null) return;
        String type = geometry.optString("type", "");
        if ("LineString".equals(type)) markLineCovered(reference, road, coordinates);
        else if ("MultiLineString".equals(type)) {
            for (int index = 0; index < coordinates.length(); index++) {
                JSONArray line = coordinates.optJSONArray(index);
                if (line != null) markLineCovered(reference, road, line);
            }
        }
    }

    private void markCovered(Reference reference, Road road, double[] segment) {
        double distance = haversine(segment[0], segment[1], segment[2], segment[3]);
        if (!Double.isFinite(distance) || distance <= 0) return;
        int samples = Math.max(1, (int) Math.ceil(distance / MATCH_SAMPLE_M));
        for (int index = 0; index <= samples; index++) {
            double fraction = index / (double) samples;
            Integer anchor = reference.nearest(segment[0] + (segment[2] - segment[0]) * fraction,
                    segment[1] + (segment[3] - segment[1]) * fraction);
            if (anchor != null) road.coveredSections.add(anchor);
        }
    }

    private void markLineCovered(Reference reference, Road road, JSONArray coordinates) {
        for (int index = 1; index < coordinates.length(); index++) {
            JSONArray a = coordinates.optJSONArray(index - 1), b = coordinates.optJSONArray(index);
            if (a == null || b == null || a.length() < 2 || b.length() < 2) continue;
            double lngA = a.optDouble(0, Double.NaN), latA = a.optDouble(1, Double.NaN);
            double lngB = b.optDouble(0, Double.NaN), latB = b.optDouble(1, Double.NaN);
            if (Double.isFinite(lngA) && Double.isFinite(latA)
                    && Double.isFinite(lngB) && Double.isFinite(latB)) {
                markCovered(reference, road, new double[]{lngA, latA, lngB, latB});
            }
        }
    }

    private static boolean firstPointIsNi(JSONObject feature) {
        JSONObject geometry = feature.optJSONObject("geometry");
        JSONArray coordinates = geometry == null ? null : geometry.optJSONArray("coordinates");
        if (coordinates == null || coordinates.length() == 0) return false;
        JSONArray point = coordinates.optJSONArray(0);
        if ("MultiLineString".equals(geometry.optString("type"))) {
            JSONArray line = coordinates.optJSONArray(0);
            point = line == null ? null : line.optJSONArray(0);
        }
        return point != null && point.length() >= 2
                && isNi(point.optDouble(0), point.optDouble(1));
    }

    private static boolean isCoordinateInRegion(String region, double lng, double lat) {
        return "NI".equals(region) == isNi(lng, lat);
    }

    private static boolean isNi(double lng, double lat) {
        return Double.isFinite(lng) && Double.isFinite(lat)
                && lng < -5.3 && lat > 53.9 && lat < 55.6;
    }

    private static boolean isMotorway(String ref) {
        return ref.matches("M[0-9]+[A-Z]?|M6 Toll|A[0-9]+\\(M\\)");
    }

    private static String normalizeRef(String ref) {
        String clean = ref == null ? "" : ref.toUpperCase(Locale.ROOT).replaceAll("\\s+", "");
        return "M6T".equals(clean) || "M6TOLL".equals(clean) ? "M6 Toll" : clean;
    }

    private static Map<String, Double> lengths(String source) {
        Map<String, Double> result = new HashMap<>();
        for (String part : source.split(",")) {
            int colon = part.lastIndexOf(':');
            if (colon <= 0) continue;
            try { result.put(part.substring(0, colon), Double.parseDouble(part.substring(colon + 1))); }
            catch (NumberFormatException ignored) { }
        }
        return result;
    }

    private static double[] mercator(double lng, double lat) {
        double clamped = Math.max(-85.0, Math.min(85.0, lat));
        double x = MERCATOR_RADIUS_M * Math.toRadians(lng);
        double y = MERCATOR_RADIUS_M * Math.log(Math.tan(Math.PI / 4.0
                + Math.toRadians(clamped) / 2.0));
        return new double[]{x, y};
    }

    private static String gridKey(double x, double y, double cell) {
        return ((long) Math.floor(x / cell)) + "," + ((long) Math.floor(y / cell));
    }

    private static List<String> neighbours(double x, double y, double cell) {
        long gx = (long) Math.floor(x / cell), gy = (long) Math.floor(y / cell);
        List<String> result = new ArrayList<>(9);
        for (long dx = -1; dx <= 1; dx++) for (long dy = -1; dy <= 1; dy++) {
            result.add((gx + dx) + "," + (gy + dy));
        }
        return result;
    }

    private static double haversine(double lngA, double latA, double lngB, double latB) {
        double a = Math.toRadians(latA), b = Math.toRadians(latB);
        double dLat = b - a, dLng = Math.toRadians(lngB - lngA);
        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(a) * Math.cos(b) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * EARTH_RADIUS_M * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h));
    }
}
