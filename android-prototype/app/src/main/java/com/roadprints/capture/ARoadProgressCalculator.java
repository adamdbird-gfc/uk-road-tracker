package com.roadprints.capture;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.JsonReader;
import android.util.JsonToken;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Canonical coverage for the discovered A roads, using the POC v5 references. */
final class ARoadProgressCalculator {
    private static long spatialKey(long x,long y){return (x<<32)^(y&0xffffffffL);}
    private static final double EARTH_RADIUS_M = 6_371_000.0;
    private static final double MATCH_RADIUS_M = 100.0;
    private static final double GRID_M = 250.0;
    private static final double SAMPLE_M = 100.0;
    // The source geometry for A2 includes overlapping/connector ways. Use the
    // published London–Dover route length instead of the raw way-length sum.
    private static final Map<String, Double> VERIFIED_LENGTH_KM = new HashMap<>();
    static { VERIFIED_LENGTH_KM.put("A2", 115.79); }

    /** Covered canonical reference anchors are non-negative integer IDs.
     * A bit set retains one bit per anchor instead of a boxed Integer and a
     * HashMap entry per hit. Keep Set<Integer> semantics for attribution,
     * serialization and all existing callers; road geometry is unchanged. */
    static final class CompactAnchorSet extends java.util.AbstractSet<Integer> {
        private final java.util.BitSet bits = new java.util.BitSet();

        @Override public boolean add(Integer value) {
            if (value == null) throw new NullPointerException("canonical anchor");
            if (value < 0) throw new IllegalArgumentException("Negative canonical anchor");
            boolean present = bits.get(value);
            bits.set(value);
            return !present;
        }

        @Override public boolean contains(Object value) {
            return value instanceof Integer index && index >= 0 && bits.get(index);
        }

        @Override public boolean remove(Object value) {
            if (!(value instanceof Integer index) || index < 0 || !bits.get(index)) return false;
            bits.clear(index);
            return true;
        }

        @Override public int size() { return bits.cardinality(); }
        @Override public boolean isEmpty() { return bits.isEmpty(); }
        @Override public void clear() { bits.clear(); }

        @Override public java.util.Iterator<Integer> iterator() {
            return new java.util.Iterator<>() {
                private int next = bits.nextSetBit(0);
                private int previous = -1;

                @Override public boolean hasNext() { return next >= 0; }

                @Override public Integer next() {
                    if (next < 0) throw new java.util.NoSuchElementException();
                    previous = next;
                    next = bits.nextSetBit(previous + 1);
                    return previous;
                }

                @Override public void remove() {
                    if (previous < 0) throw new IllegalStateException();
                    bits.clear(previous);
                    previous = -1;
                }
            };
        }
    }

    static final class Road {
        final String id, ref, region;
        final Set<String> journeyIds = new HashSet<>();
        final Set<Integer> covered = new CompactAnchorSet();
        final List<JSONArray> coveredMapSections = new ArrayList<>();
        final List<JSONArray> incompleteMapSections = new ArrayList<>();
        final List<Anchor> anchors = Collections.emptyList();
        int referenceSections;
        final android.util.LongSparseArray<List<Anchor>> grid = new android.util.LongSparseArray<>();
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
    private final AssetManager assets;
    private final boolean includeMapSections;
    private Summary completedSummary;
    private Map<String,Road> roads=new TreeMap<>();
    private final Map<String,JSONObject> index=new HashMap<>();
    // Aggregates and cache validation retain only metadata. Spatial indexes are
    // needed for fresh matching, and are loaded one road at a time for map geometry.
    private static final int MAX_CACHED_ANCHORS = 16_384;
    private static final class Reference {
        final List<Anchor> anchors = new ArrayList<>();
        android.util.LongSparseArray<List<Anchor>> grid;
        double totalKm;
    }
    private static final class ReferenceInfo {
        final String signature;
        final int sections;
        final double totalKm;
        ReferenceInfo(String signature, int sections, double totalKm) {
            this.signature = signature;
            this.sections = sections;
            this.totalKm = totalKm;
        }
    }
    // These references are bundled assets. Share signatures and counts across scans
    // of the same assets without keeping any geometry or spatial indexes alive.
    private static final int MAX_SHARED_REFERENCES = 512;
    private record ReferenceKey(String id, String ref) {}
    private static final Map<ReferenceKey, ReferenceInfo> SHARED_INFO = new LinkedHashMap<>(16, .75f, true);
    private static WeakReference<AssetManager> sharedAssets = new WeakReference<>(null);

    private static synchronized ReferenceInfo sharedInfo(AssetManager assets, ReferenceKey key) {
        return sharedAssets.get() == assets ? SHARED_INFO.get(key) : null;
    }

    private static synchronized void rememberInfo(AssetManager assets, ReferenceKey key, ReferenceInfo info) {
        if (sharedAssets.get() != assets) {
            SHARED_INFO.clear();
            sharedAssets = new WeakReference<>(assets);
        }
        SHARED_INFO.put(key, info);
        if (SHARED_INFO.size() > MAX_SHARED_REFERENCES) {
            var oldest = SHARED_INFO.keySet().iterator();
            oldest.next();
            oldest.remove();
        }
    }

    private static final ReferenceInfo MISSING = new ReferenceInfo("missing", 0, 0);
    private final LinkedHashMap<String, Reference> references = new LinkedHashMap<>(16, .75f, true);
    private final Map<String, ReferenceInfo> referenceInfo = new HashMap<>();
    private int retainedAnchors;
    private int peakRetainedAnchors;
    private int referenceReads;
    // Identical matched geometry can recur on daily journeys. Retain only small
    // index arrays, keyed by geometry and canonical road, for this scan.
    private final LinkedHashMap<String, int[]> projectedFeatures = new LinkedHashMap<>(64, .75f, true);

    int peakRetainedAnchors() { return peakRetainedAnchors; }

    int referenceReads() { return referenceReads; }

    private ReferenceInfo info(Road road) {
        return info(road.id, road.ref);
    }

    private ReferenceInfo info(String id, String ref) {
        ReferenceInfo info = referenceInfo.get(id);
        if (info != null) return info;
        info = sharedInfo(assets, new ReferenceKey(id, ref));
        if (info == null) {
            Reference reference = readReference(id, ref);
            info = reference == null ? MISSING : describeReference(id, ref, reference);
        }
        referenceInfo.put(id, info);
        return info;
    }

    private void applyInfo(Road road) {
        ReferenceInfo info = info(road);
        road.referenceSections = info.sections;
        road.totalKm = info.totalKm;
        road.referenceAvailable = info != MISSING;
    }
    ARoadProgressCalculator(Context context) { this(context,true); }
    ARoadProgressCalculator(Context context, boolean includeMapSections) {
        this.assets=context.getApplicationContext().getAssets(); this.includeMapSections=includeMapSections; loadIndex();
    }

    void addJourney(JSONObject journey) { addJourney(journey, null); }

    void addJourney(JSONObject journey, Set<String> onlyRefs) {
        if(completedSummary!=null) throw new IllegalStateException("Coverage calculation is already finished");
        if (journey==null || !"complete".equals(journey.optString("processing_status",""))) return;
        String mode=journey.optString("mode","").toLowerCase(Locale.ROOT);
        if (!AcceptedRoadRoute.eligibleMode(mode)) return;
        JSONObject result=journey.optJSONObject("processing_result");
        JSONObject collection=result==null?null:result.optJSONObject("a_road_geojson");
        JSONArray features=collection==null?null:collection.optJSONArray("features");
        if (features==null) return;
        String jid=journey.optString("journey_id","");
        for(int i=0;i<features.length();i++) {
            JSONObject feature = JourneyCorrectionUtils.retainedRoadFeature(journey, features.optJSONObject(i));
            if(feature==null || !UkRoadScope.candidateFeature(feature)) continue;
            JSONObject props=feature.optJSONObject("properties"); if(props==null) continue;
            String ref=normalize(props.optString("road_ref",""));
            if(!ref.matches("A[0-9]+[A-Z]?")) continue;
            String region=props.optString("road_region","").toUpperCase(Locale.ROOT);
            if(!"NI".equals(region)) region=isNi(feature)?"NI":"GB";
            if (onlyRefs != null && !onlyRefs.contains(ref)) continue;
            String id=region+":"+ref;
            Road road=roads.get(id);
            if(road==null){road=new Road(id,ref,region);roads.put(id,road);}
            road.matchedMetres+=Math.max(0,props.optDouble("distance_m",0));
            if(!jid.isEmpty()) road.journeyIds.add(jid);
            addFeatureCoverage(road, feature.optJSONObject("geometry"));
        }
    }

    private void addFeatureCoverage(Road road, JSONObject geometry) {
        if (geometry == null) return;
        // Cache only compact features. Large features still use the canonical matcher.
        JSONArray coordinates = geometry.optJSONArray("coordinates");
        String key = null;
        if ("LineString".equals(geometry.optString("type"))
                && coordinates != null && coordinates.length() <= 256) {
            key = road.id + ":" + NcnJson.hash(geometry.toString().getBytes(StandardCharsets.UTF_8));
            int[] cached = projectedFeatures.get(key);
            if (cached != null) {
                for (int index : cached) road.covered.add(index);
                return;
            }
        }
        Reference reference = reference(road);
        if (reference == null) return;
        if (key == null) {
            matchFeature(reference, road, geometry);
            return;
        }
        Road contribution = new Road(road.id, road.ref, road.region);
        matchFeature(reference, contribution, geometry);
        road.covered.addAll(contribution.covered);
        if (contribution.covered.size() <= 256) {
            int[] indices = new int[contribution.covered.size()];
            int offset = 0;
            for (int index : contribution.covered) indices[offset++] = index;
            if (projectedFeatures.size() == 512) {
                java.util.Iterator<String> oldest = projectedFeatures.keySet().iterator();
                oldest.next();
                oldest.remove();
            }
            projectedFeatures.put(key, indices);
        }
    }

    /** Compact, per-journey contribution; reference grids are shared within this worker. */
    JSONArray contribution(JSONObject journey) throws Exception {
        Map<String,Road> previous=roads;roads=new TreeMap<>();
        try {
            addJourney(journey);JSONArray rows=new JSONArray();
            for(Road road:roads.values()) {
                String signature=info(road).signature;
                JSONArray indices=new JSONArray();for(int index:road.covered)indices.put(index);
                rows.put(new JSONObject().put("id",road.id).put("ref",road.ref).put("region",road.region)
                        .put("metres",road.matchedMetres).put("indices",indices).put("reference",signature));
            }
            return rows;
        } finally {roads=previous;}
    }

    /** Validate every reference before merging anything, so a failed cache hit is atomic. */
    boolean accepts(JSONArray rows) throws Exception {
        for(int i=0;i<rows.length();i++) {
            JSONObject row=rows.getJSONObject(i);
            String id = row.getString("id");
            String ref = row.getString("ref");
            row.getString("region"); // Required by the contribution merge.
            ReferenceInfo info = info(id, ref);
            int count = info.sections;
            if (!info.signature.equals(row.getString("reference"))) return false;
            JSONArray indices=row.getJSONArray("indices");
            for(int j=0;j<indices.length();j++)if(indices.getInt(j)<0||indices.getInt(j)>=count)return false;
        }
        return true;
    }

    // Accepted on-foot sections are attributed to a particular OSM physical
    // road before entering this calculator. Do not run their coordinates through
    // the ordinary 100m proximity matcher: a parallel carriageway could qualify.
    private static final double ACCEPTED_FOOT_RADIUS_M = 2.5;
    private static final double ACCEPTED_FOOT_HEADING_COS = .9;
    private static final int MAX_ACCEPTED_FOOT_REFERENCE_CELLS = 160;

    int addAcceptedFootSections(String journeyId,
            Map<LocalRoadNetwork.Edge, List<LocalRoadNetwork.Interval>> sections) {
        if (completedSummary != null) throw new IllegalStateException("Coverage is finished");
        int credited = 0;
        for (var entry : sections.entrySet()) {
            checkInterrupted();
            LocalRoadNetwork.Edge edge = entry.getKey();
            if (edge.eligible || edge.mm <= 0 || edge.length <= 0) continue;
            // A street name or an unknown class cannot be converted to A-road
            // completion. The reference must explicitly identify the A road.
            String ref = normalize(edge.roadRef.isEmpty() ? edge.label : edge.roadRef);
            if (!ref.matches("A[0-9]+[A-Z]?")) continue;
            double lon = LocalRoadNetwork.ukLongitude(edge.a);
            double lat = LocalRoadNetwork.ukLatitude(edge.a);
            String region = lon < -5.3 && lat > 53.9 && lat < 55.6 ? "NI" : "GB";
            String id = region + ":" + ref;
            Road road = roads.get(id);
            if (road == null) road = new Road(id, ref, region);
            Reference reference = reference(road);
            if (reference == null) continue;
            prepareGrid(reference);
            double matched = 0;
            for (LocalRoadNetwork.Interval span : entry.getValue()) {
                checkInterrupted();
                if (span.start() < 0 || span.end() > edge.mm || span.start() >= span.end())
                    continue;
                LocalRoadNetwork.Point a = edge.at(span.start() / (double) edge.mm);
                LocalRoadNetwork.Point b = edge.at(span.end() / (double) edge.mm);
                double[] start = mercator(LocalRoadNetwork.ukLongitude(a), LocalRoadNetwork.ukLatitude(a));
                double[] end = mercator(LocalRoadNetwork.ukLongitude(b), LocalRoadNetwork.ukLatitude(b));
                if (matchAcceptedFootReference(reference, road, start, end))
                    matched += (span.end() - span.start()) / 1000.0;
            }
            if (matched > 0) {
                roads.putIfAbsent(id, road);
                road.matchedMetres += matched;
                if (journeyId != null && !journeyId.isEmpty()) road.journeyIds.add(journeyId);
                credited++;
            }
        }
        return credited;
    }

    /** Reference-anchor sampling is a subset of exact OSM-identified intervals. */
    private static boolean matchAcceptedFootReference(Reference reference, Road road,
                                                      double[] start, double[] end) {
        double dx = end[0] - start[0], dy = end[1] - start[1], length2 = dx * dx + dy * dy;
        if (!Double.isFinite(length2) || length2 < 1 || length2 > 4_000_000) return false;
        long left = (long) Math.floor((Math.min(start[0], end[0]) - ACCEPTED_FOOT_RADIUS_M) / GRID_M);
        long right = (long) Math.floor((Math.max(start[0], end[0]) + ACCEPTED_FOOT_RADIUS_M) / GRID_M);
        long bottom = (long) Math.floor((Math.min(start[1], end[1]) - ACCEPTED_FOOT_RADIUS_M) / GRID_M);
        long top = (long) Math.floor((Math.max(start[1], end[1]) + ACCEPTED_FOOT_RADIUS_M) / GRID_M);
        if ((right - left + 1) * (top - bottom + 1) > MAX_ACCEPTED_FOOT_REFERENCE_CELLS)
            return false;
        int accepted = 0;
        for (long x = left; x <= right; x++) for (long y = bottom; y <= top; y++) {
            List<Anchor> anchors = reference.grid.get(spatialKey(x, y));
            if (anchors == null) continue;
            for (Anchor anchor : anchors) {
                double projection = ((anchor.x - start[0]) * dx + (anchor.y - start[1]) * dy) / length2;
                if (projection < 0 || projection > 1) continue;
                double perpendicular = Math.abs(dx * (anchor.y - start[1]) -
                        dy * (anchor.x - start[0])) / Math.sqrt(length2);
                if (perpendicular > ACCEPTED_FOOT_RADIUS_M
                        || !alignedWithReference(reference, anchor, dx, dy, length2))
                    continue;
                road.covered.add(anchor.id);
                accepted++;
            }
        }
        return accepted > 0;
    }

    private static boolean alignedWithReference(Reference reference, Anchor anchor,
                                                double dx, double dy, double length2) {
        int id = anchor.id;
        for (int neighborId : new int[]{id - 1, id + 1}) {
            if (neighborId < 0 || neighborId >= reference.anchors.size()) continue;
            Anchor neighbor = reference.anchors.get(neighborId);
            if (neighbor.component != anchor.component) continue;
            double rx = neighbor.x - anchor.x, ry = neighbor.y - anchor.y;
            double referenceLength2 = rx * rx + ry * ry;
            if (referenceLength2 < 1 || referenceLength2 > 250 * 250) continue;
            if (Math.abs((dx * rx + dy * ry) /
                    Math.sqrt(length2 * referenceLength2)) >= ACCEPTED_FOOT_HEADING_COS)
                return true;
        }
        return false;
    }

    void addContribution(String journeyId,JSONArray rows) throws Exception {
        for(int i=0;i<rows.length();i++) {
            JSONObject row=rows.getJSONObject(i);String id=row.getString("id");
            Road road=roads.get(id);
            if(road==null){road=new Road(id,row.getString("ref"),row.getString("region"));roads.put(id,road);}
            applyInfo(road);
            road.matchedMetres+=row.getDouble("metres");
            if(!journeyId.isEmpty())road.journeyIds.add(journeyId);
            JSONArray indices=row.getJSONArray("indices");for(int j=0;j<indices.length();j++)road.covered.add(indices.getInt(j));
        }
    }
    /** Uses the same canonical edge coverage rule as the Progress map. */
    Map<String, List<JSONArray>> newlyCoveredSections(Map<String, Set<Integer>> previous) {
        Map<String, List<JSONArray>> result = new TreeMap<>();
        for (Road road : roads.values()) {
            Set<Integer> before = previous.getOrDefault(road.id, java.util.Collections.emptySet());
            List<JSONArray> lines = new ArrayList<>();
            JSONArray current = null;
            Reference reference = reference(road);
            if (reference == null) continue;
            List<Anchor> anchors = reference.anchors;
            for (int i = 1; i < anchors.size(); i++) {
                Anchor a = anchors.get(i - 1), b = anchors.get(i);
                boolean fresh = (road.covered.contains(a.id) || road.covered.contains(b.id))
                        && !before.contains(a.id) && !before.contains(b.id)
                        && a.component == b.component && distance(a.lng, a.lat, b.lng, b.lat) <= 250;
                if (!fresh) {
                    if (current != null) lines.add(current);
                    current = null;
                } else {
                    if (current == null) current = new JSONArray().put(coord(a));
                    current.put(coord(b));
                }
            }
            if (current != null) lines.add(current);
            if (!lines.isEmpty()) result.put(road.id, lines);
        }
        return result;
    }

    boolean populateMapSections(Road road) {
        Reference reference = readReference(road);
        if (reference == null) return false;
        buildMapSections(road, reference.anchors);
        return true;
    }

    Summary finish() {
        if(completedSummary!=null) return completedSummary;
        List<String> missing=new ArrayList<>();
        for(Road road:roads.values()) {
            checkInterrupted();
            applyInfo(road);
            if (!road.referenceAvailable) missing.add(road.id);
            else if (includeMapSections) {
                Reference reference = references.remove(road.id);
                if (reference == null) reference = readReference(road);
                else retainedAnchors -= reference.anchors.size();
                if (reference != null) buildMapSections(road, reference.anchors);
            }
        }
        List<Road> sorted=new ArrayList<>(roads.values());
        sorted.sort((a,b)->Double.compare(b.matchedMetres,a.matchedMetres));
        Summary result=new Summary(sorted,missing);
        for(Road road:sorted) result.matchedMetres+=road.matchedMetres;
        projectedFeatures.clear();
        references.clear();
        referenceInfo.clear();
        retainedAnchors = 0;
        if (includeMapSections) index.clear();
        completedSummary=result;
        return result;
    }
    private void loadIndex() {
        try(InputStream in=assets.open("index.json");
            BufferedReader reader=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8))) {
            StringBuilder s=new StringBuilder();String line;while((line=reader.readLine())!=null)s.append(line);
            JSONObject root=new JSONObject(s.toString()).optJSONObject("roads"); if(root==null)return;
            java.util.Iterator<String> it=root.keys();while(it.hasNext()){String key=it.next();index.put(key,root.optJSONObject(key));}
        } catch(Exception ignored) { }
    }
    private Reference reference(Road road) {
        if (referenceInfo.get(road.id) == MISSING) return null;
        Reference reference = references.get(road.id);
        if (reference != null) return reference;
        reference = readReference(road);
        if (reference == null) {
            referenceInfo.putIfAbsent(road.id, MISSING);
            return null;
        }
        if (!referenceInfo.containsKey(road.id)) {
            referenceInfo.put(road.id, describeReference(road.id, road.ref, reference));
        }
        int count = reference.anchors.size();
        if (count <= MAX_CACHED_ANCHORS) {
            while (retainedAnchors + count > MAX_CACHED_ANCHORS) {
                java.util.Iterator<Reference> oldest = references.values().iterator();
                retainedAnchors -= oldest.next().anchors.size();
                oldest.remove();
            }
            references.put(road.id, reference);
            retainedAnchors += count;
            peakRetainedAnchors = Math.max(peakRetainedAnchors, retainedAnchors);
        }
        return reference;
    }

    private ReferenceInfo describeReference(String id, String ref, Reference reference) {
        // Keep the signature format used by existing compact rows.
        StringBuilder text = new StringBuilder().append(reference.totalKm).append('|');
        for (Anchor point : reference.anchors) {
            text.append(point.lng).append(',').append(point.lat).append(',')
                    .append(point.component).append(';');
        }
        ReferenceInfo info = new ReferenceInfo(
                NcnJson.hash(text.toString().getBytes(StandardCharsets.UTF_8)),
                reference.anchors.size(), reference.totalKm);
        rememberInfo(assets, new ReferenceKey(id, ref), info);
        return info;
    }

    private Reference readReference(Road road) {
        return readReference(road.id, road.ref);
    }

    private Reference readReference(String id, String ref) {
        JSONObject entry = index.get(id);
        String file = entry == null ? null : entry.optString("file", null);
        if (file == null) return null;
        referenceReads++;
        Reference reference = new Reference();
        double totalKm = entry.optDouble("total_km", 0);
        try (InputStream in = assets.open(file);
             JsonReader reader = new JsonReader(new BufferedReader(
                     new InputStreamReader(in, StandardCharsets.UTF_8)))) {
            reader.beginObject();
            while (reader.hasNext()) {
                checkInterrupted();
                String field = reader.nextName();
                if ("paths".equals(field) && reader.peek() == JsonToken.BEGIN_ARRAY) {
                    reader.beginArray();
                    int component = 0;
                    while (reader.hasNext()) {
                        if (reader.peek() == JsonToken.BEGIN_ARRAY) {
                            if (sampleReferencePath(reference, reader, component)) component++;
                        } else reader.skipValue();
                    }
                    reader.endArray();
                } else if ("total_km".equals(field) && reader.peek() == JsonToken.NUMBER) {
                    totalKm = reader.nextDouble();
                } else reader.skipValue();
            }
            reader.endObject();
        } catch (java.util.concurrent.CancellationException stopped) {
            throw stopped;
        } catch (Exception invalid) {
            return null;
        }
        reference.totalKm = VERIFIED_LENGTH_KM.getOrDefault(ref, totalKm);
        return reference.anchors.isEmpty() || reference.totalKm <= 0 ? null : reference;
    }

    private boolean sampleReferencePath(Reference reference, JsonReader reader, int component)
            throws java.io.IOException {
        reader.beginArray();
        int points = 0;
        double previousLon = 0, previousLat = 0, carry = 0;
        while (reader.hasNext()) {
            checkInterrupted();
            if (reader.peek() != JsonToken.BEGIN_ARRAY) {
                reader.skipValue();
                continue;
            }
            reader.beginArray();
            double lon = readCoordinate(reader), lat = readCoordinate(reader);
            while (reader.hasNext()) reader.skipValue();
            reader.endArray();
            if (!Double.isFinite(lon) || !Double.isFinite(lat)) continue;
            if (++points == 2) addAnchor(reference, previousLon, previousLat, component);
            if (points >= 2) {
                double metres = distance(previousLon, previousLat, lon, lat);
                if (Double.isFinite(metres) && metres > 0) {
                    for (double along = SAMPLE_M - carry; along <= metres + .000001; along += SAMPLE_M) {
                        checkInterrupted();
                        double fraction = along / metres;
                        addAnchor(reference, previousLon + (lon - previousLon) * fraction,
                                previousLat + (lat - previousLat) * fraction, component);
                    }
                    carry = (carry + metres) % SAMPLE_M;
                }
            }
            previousLon = lon;
            previousLat = lat;
        }
        reader.endArray();
        if (points < 2) return false;
        Anchor tail = reference.anchors.get(reference.anchors.size() - 1);
        if (distance(tail.lng, tail.lat, previousLon, previousLat) > 2) {
            addAnchor(reference, previousLon, previousLat, component);
        }
        return true;
    }

    private static double readCoordinate(JsonReader reader) throws java.io.IOException {
        if (!reader.hasNext()) return Double.NaN;
        if (reader.peek() == JsonToken.NUMBER || reader.peek() == JsonToken.STRING) {
            try { return Double.parseDouble(reader.nextString()); }
            catch (NumberFormatException invalid) { return Double.NaN; }
        }
        reader.skipValue();
        return Double.NaN;
    }

    private static void checkInterrupted() {
        if (Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException();
    }

    private void addAnchor(Reference reference, double lon, double lat, int component) {
        reference.anchors.add(new Anchor(reference.anchors.size(), lon, lat, component));
    }

    private void prepareGrid(Reference reference) {
        if (reference.grid != null) return;
        android.util.LongSparseArray<List<Anchor>> grid = new android.util.LongSparseArray<>();
        for (Anchor anchor : reference.anchors) {
            checkInterrupted();
            long key = spatialKey((long)Math.floor(anchor.x / GRID_M), (long)Math.floor(anchor.y / GRID_M));
            List<Anchor> bucket = grid.get(key);
            if (bucket == null) {
                bucket = new ArrayList<>();
                grid.put(key, bucket);
            }
            bucket.add(anchor);
        }
        reference.grid = grid;
    }
    private void matchFeature(Reference reference, Road road,JSONObject geometry){
        if(geometry==null)return;JSONArray coords=geometry.optJSONArray("coordinates");if(coords==null)return;
        prepareGrid(reference);
        String type=geometry.optString("type","");
        if("LineString".equals(type))matchLine(reference, road,coords);
        else if("MultiLineString".equals(type))for(int i=0;i<coords.length();i++){JSONArray line=coords.optJSONArray(i);if(line!=null)matchLine(reference, road,line);}
    }
    private void matchLine(Reference reference, Road road,JSONArray line){
        for(int i=1;i<line.length();i++){JSONArray a=line.optJSONArray(i-1),b=line.optJSONArray(i);if(a==null||b==null||a.length()<2||b.length()<2)continue;
            double lng1=a.optDouble(0,Double.NaN),lat1=a.optDouble(1,Double.NaN),lng2=b.optDouble(0,Double.NaN),lat2=b.optDouble(1,Double.NaN);
            double d=distance(lng1,lat1,lng2,lat2);if(!Double.isFinite(d)||d<=0)continue;int n=Math.max(1,(int)Math.ceil(d/25));
            for(int j=0;j<=n;j++){double f=j/(double)n;Anchor near=nearest(reference,lng1+(lng2-lng1)*f,lat1+(lat2-lat1)*f);if(near!=null)road.covered.add(near.id);}
        }
    }
    private Anchor nearest(Reference reference,double lng,double lat){double[] xy=mercator(lng,lat);Anchor best=null;double bd=MATCH_RADIUS_M;long gx=(long)Math.floor(xy[0]/GRID_M),gy=(long)Math.floor(xy[1]/GRID_M);
        for(long x=gx-1;x<=gx+1;x++)for(long y=gy-1;y<=gy+1;y++){List<Anchor> bucket=reference.grid.get(spatialKey(x,y));if(bucket==null)continue;for(Anchor a:bucket){double d=Math.hypot(xy[0]-a.x,xy[1]-a.y);if(d<bd){best=a;bd=d;}}}return best;}
    private void buildMapSections(Road road, List<Anchor> anchors){JSONArray current=null;Boolean covered=null;Anchor prev=null;
        for(Anchor a:anchors){if(prev==null){prev=a;continue;}if(a.component!=prev.component||distance(prev.lng,prev.lat,a.lng,a.lat)>250){addSection(road,current,covered);current=null;covered=null;prev=a;continue;}
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


