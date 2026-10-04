package com.roadprints.capture;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ScrollView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MapActivity extends Activity {
    private static final int NAVY = 0xFF0B1C50;
    private static final int NAV_BAR = 0xFF10275D;
    private static final int MUTED = 0xFFB9C5D8;
    private static final int GOLD = 0xFFF7C450;
    private static final int MAX_MAP_POINTS = 350_000;
    private static final int MAX_MOTORWAY_POINTS = 120_000;
    private static final int MAX_POINTS_PER_ROUTE = 1_400;
    private static final int MAX_SETTLEMENT_POINTS_PER_ROUTE = 1_200;
    private static final int MAX_SETTLEMENT_POINTS = 45_000;
    private static final int MAX_MAP_ROUTES = 5_000;
    private static final Object MAP_CACHE_LOCK = new Object();
    private static MapRoutes processMapCache;
    private static long processMapCacheRevision = Long.MIN_VALUE;
    private TextView mapSubtitle;
    private FrameLayout mapFrame;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private int mapLoadGeneration;
    private Map<String, Double> roadPercentages = new HashMap<>();
    private RoutePreviewView mapView;
    private long displayedMapRevision = Long.MIN_VALUE;
    private double savedCameraLongitude, savedCameraLatitude, savedCameraZoom;
    private boolean hasSavedCamera;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(NAVY);
        getWindow().setNavigationBarColor(NAV_BAR);
        if (state != null && state.containsKey("map_camera_longitude")) {
            savedCameraLongitude = state.getDouble("map_camera_longitude");
            savedCameraLatitude = state.getDouble("map_camera_latitude");
            savedCameraZoom = state.getDouble("map_camera_zoom");
            hasSavedCamera = true;
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(NAVY);

        LinearLayout heading = new LinearLayout(this);
        heading.setOrientation(LinearLayout.VERTICAL);
        heading.setPadding(dp(18), dp(18), dp(18), dp(14));

        LinearLayout brand = RoadprintsHeader.create(this);
        brand.setPadding(0, 0, 0, dp(24));
        heading.addView(brand);

        TextView eyebrow = new TextView(this);
        eyebrow.setText("YOUR TRAVEL RECORD");
        eyebrow.setTextSize(12);
        eyebrow.setTypeface(null, Typeface.BOLD);
        eyebrow.setTextColor(0xFF67D5CC);

        TextView title = new TextView(this);
        title.setText("Map");
        title.setTextSize(28);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(Color.WHITE);

        mapSubtitle = new TextView(this);
        mapSubtitle.setTextSize(14);
        mapSubtitle.setTextColor(0xFFD3DCED);
        mapSubtitle.setPadding(0, dp(4), 0, 0);

        heading.addView(eyebrow);
        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        title.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
        titleRow.addView(title);
        String settlementCode = getIntent().getStringExtra("settlement_code");
        if (settlementCode != null && !settlementCode.isEmpty()) {
            TextView close = new TextView(this);
            close.setText("× Close");
            close.setTextSize(13);
            close.setTypeface(null, Typeface.BOLD);
            close.setTextColor(0xFFD3DCED);
            close.setGravity(Gravity.CENTER);
            close.setPadding(dp(12), dp(8), dp(12), dp(8));
            close.setBackground(roundRect(0xFF182F62, dp(10)));
            close.setContentDescription("Close town map and return to Progress");
            close.setOnClickListener(v -> finish());
            titleRow.addView(close);
        }
                heading.addView(titleRow);
        heading.addView(mapSubtitle);
        root.addView(heading);

        mapFrame = new FrameLayout(this);
        mapFrame.setBackgroundColor(NAVY);
        root.addView(mapFrame, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        mapFrame.addView(ScreenLoadingView.create(this, "Preparing your map",
                "Reading saved journeys within the memory limit."), new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        View bottomNavigation = buildBottomNavigation();
        root.addView(bottomNavigation);
        setContentView(root);
        applySystemBarInsets(root, heading, bottomNavigation);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mapFrame != null && mapSubtitle != null) refreshMap();
    }

    @Override
    protected void onPause() {
        saveMapCamera();
        super.onPause();
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        saveMapCamera();
        if (hasSavedCamera) {
            state.putDouble("map_camera_longitude", savedCameraLongitude);
            state.putDouble("map_camera_latitude", savedCameraLatitude);
            state.putDouble("map_camera_zoom", savedCameraZoom);
        }
        super.onSaveInstanceState(state);
    }

    private void saveMapCamera() {
        if (mapView == null || getIntent().hasExtra("settlement_code")) return;
        double[] camera = mapView.cameraState();
        if (camera == null || camera.length < 3) return;
        savedCameraLongitude = camera[0];
        savedCameraLatitude = camera[1];
        savedCameraZoom = camera[2];
        hasSavedCamera = true;
    }

    @Override
    protected void onDestroy() {
        mapLoadGeneration++;
        super.onDestroy();
    }

    private void refreshMap() {
        String settlementCode = getIntent().getStringExtra("settlement_code");
        if ((settlementCode == null || settlementCode.isEmpty())
                && mapView != null && displayedMapRevision == mapDataRevision()) return;
        saveMapCamera();
        final int generation = ++mapLoadGeneration;
        if (settlementCode != null && !settlementCode.isEmpty()) {
            refreshSettlementMap(generation, settlementCode,
                    getIntent().getStringExtra("settlement_name"),
                    getIntent().getStringExtra("settlement_journey_ids"));
            return;
        }
        final long revision = mapDataRevision();
        MapRoutes cached = null;
        synchronized (MAP_CACHE_LOCK) {
            if (processMapCacheRevision == revision) cached = processMapCache;
            else {
                processMapCache = null;
                processMapCacheRevision = revision;
            }
        }
        if (cached != null) {
            displayMapRoutes(cached, generation);
            return;
        }
        mapSubtitle.setText("Loading matched journeys…");
        ScreenDataLoader.execute(() -> {
            MapRoutes mapRoutes;
            try {
                mapRoutes = readMapRoutes();
            } catch (Exception error) {
                mainHandler.post(() -> {
                    if (isFinishing() || generation != mapLoadGeneration) return;
                    mapSubtitle.setText("Map data could not be loaded. Reopen the map to retry.");
                    mapFrame.removeAllViews();
                    TextView failure = new TextView(this);
                    failure.setText("Map data could not be loaded.");
                    failure.setTextColor(Color.WHITE);
                    failure.setGravity(Gravity.CENTER);
                    mapFrame.addView(failure, new FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT));
                });
                return;
            }
            long currentRevision = mapDataRevision();
            Runtime runtime = Runtime.getRuntime();
            long freeHeadroom = runtime.maxMemory()
                    - (runtime.totalMemory() - runtime.freeMemory());
            if (currentRevision == revision && freeHeadroom >= 64L * 1024L * 1024L) {
                synchronized (MAP_CACHE_LOCK) {
                    processMapCache = mapRoutes;
                    processMapCacheRevision = revision;
                }
            }
            displayMapRoutes(mapRoutes, generation);
        });
    }

    static void clearProcessMapCache() {
        synchronized (MAP_CACHE_LOCK) {
            processMapCache = null;
            processMapCacheRevision = Long.MIN_VALUE;
        }
    }

    private void displayMapRoutes(MapRoutes mapRoutes, int generation) {
        mainHandler.post(() -> {
            if (isFinishing() || generation != mapLoadGeneration) return;
            mapSubtitle.setText(mapRoutes.sections.isEmpty()
                    && mapRoutes.motorwaySections.isEmpty()
                    && mapRoutes.incompleteMotorwaySections.isEmpty()
                    ? (mapRoutes.serviceStations.length() > 0
                        ? "Service stations are shown on the map. Recorded journeys will appear here."
                        : "Recorded journeys will appear here.")
                    : mapRoutes.matchedJourneys + " recorded journeys shown"
                            + (mapRoutes.simplified ? " · map simplified for performance." : "."));
            mapFrame.removeAllViews();
            RoutePreviewView map = mapRoutes.sections.isEmpty()
                    ? new RoutePreviewView(this)
                    : new RoutePreviewView(this, mapRoutes.sections, true);
            map.setFlatRoadMapStyle(true);
            map.setMotorwaySegments(mapRoutes.motorwaySections);
            map.setMotorwayCoverageSegments(mapRoutes.incompleteMotorwaySections,
                    mapRoutes.coveredMotorwaySections);
            map.setARoadCoverageSegments(mapRoutes.incompleteARoadSections,
                    mapRoutes.coveredARoadSections);
            map.setServiceStations(mapRoutes.serviceStations,
                    mapRoutes.visitedServiceStationIds);
            map.setServiceStationTapListener(station -> showServiceStationCard(
                    station, mapRoutes.visitedServiceStationIds.contains(station.optString("id", ""))));
            roadPercentages = new HashMap<>(mapRoutes.roadPercentages);
            map.setMapRoadTapListener((latitude, longitude) ->
                    loadRoadSummaryAt(latitude, longitude));
            map.setContentDescription("Interactive OpenStreetMap. Pinch to zoom and drag to move.");
            mapFrame.addView(map, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
            if (hasSavedCamera) {
                map.restoreCameraState(savedCameraLongitude, savedCameraLatitude, savedCameraZoom);
                hasSavedCamera = false;
            }
            mapView = map;
            displayedMapRevision = mapDataRevision();
            addZoomControls(mapFrame, map);
        });
    }
    private void refreshSettlementMap(int generation, String code, String name,
                                      String journeyIdJson) {
        mapSubtitle.setText("Loading " + name + " and your visited roads…");
        ScreenDataLoader.execute(() -> {
            JSONObject boundary = null;
            List<JSONArray> roads = new ArrayList<>();
            int retainedSettlementPoints = 0;
            String failure = null;
            try {
                boundary = LocalRoadSettlementMatcher.boundary(code);
                JSONArray journeyIds = new JSONArray(journeyIdJson == null ? "[]" : journeyIdJson);
                for (int index = 0; index < journeyIds.length()
                        && roads.size() < MAX_MAP_ROUTES
                        && retainedSettlementPoints < MAX_SETTLEMENT_POINTS; index++) {
                    String journeyId = journeyIds.optString(index, "");
                    if (journeyId.isEmpty()) continue;
                    JSONObject journey = JourneyStore.get(getApplicationContext(), journeyId);
                    JSONObject result = journey == null ? null : journey.optJSONObject("processing_result");
                    JSONObject geojson = result == null ? null : result.optJSONObject("geojson");
                    JSONArray features = geojson == null ? null : geojson.optJSONArray("features");
                    if (features == null) continue;
                    for (int featureIndex = 0; featureIndex < features.length()
                            && roads.size() < MAX_MAP_ROUTES
                            && retainedSettlementPoints < MAX_SETTLEMENT_POINTS; featureIndex++) {
                        JSONObject feature = features.optJSONObject(featureIndex);
                        JSONObject geometry = feature == null ? null : feature.optJSONObject("geometry");
                        if (geometry == null) continue;
                        String type = geometry.optString("type", "");
                        JSONArray coordinates = geometry.optJSONArray("coordinates");
                        if (coordinates == null) continue;
                        if ("LineString".equals(type)) {
                            retainedSettlementPoints += appendClippedRoute(coordinates, boundary, roads,
                                    MAX_SETTLEMENT_POINTS - retainedSettlementPoints);
                        } else if ("MultiLineString".equals(type)) {
                            for (int lineIndex = 0; lineIndex < coordinates.length()
                                    && retainedSettlementPoints < MAX_SETTLEMENT_POINTS
                                    && roads.size() < MAX_MAP_ROUTES; lineIndex++) {
                                JSONArray line = coordinates.optJSONArray(lineIndex);
                                retainedSettlementPoints += appendClippedRoute(line, boundary, roads,
                                        MAX_SETTLEMENT_POINTS - retainedSettlementPoints);
                            }
                        }
                    }
                }
                if (boundary == null) failure = "The settlement boundary is not available yet.";
            } catch (Exception error) {
                failure = "The settlement map could not be loaded. Reopen it to retry.";
            }
            final JSONObject resultBoundary = boundary;
            final List<JSONArray> resultRoads = roads;
            final String resultFailure = failure;
            mainHandler.post(() -> {
                if (isFinishing() || generation != mapLoadGeneration) return;
                mapFrame.removeAllViews();
                mapSubtitle.setText((name == null ? "Settlement" : name)
                        + " · visited roads inside the red boundary");
                RoutePreviewView map = new RoutePreviewView(this, resultRoads, true);
                map.setFlatRoadMapStyle(true);
                map.setSettlementBoundary(resultBoundary);
                map.setContentDescription("Visited roads in " + name
                        + " inside the red settlement boundary. Pinch to zoom and drag to move.");
                mapFrame.addView(map, new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
                addZoomControls(mapFrame, map);
                if (resultFailure != null) mapSubtitle.setText(resultFailure);
            });
        });
    }

    private int appendClippedRoute(JSONArray route, JSONObject boundary,
                                   List<JSONArray> output, int pointBudget) {
        if (route == null || route.length() < 2 || pointBudget < 2) return 0;
        JSONArray current = null;
        int retained = 0;
        int stride = Math.max(1, (route.length() + MAX_SETTLEMENT_POINTS_PER_ROUTE - 1)
                / MAX_SETTLEMENT_POINTS_PER_ROUTE);
        for (int index = 1; index < route.length() && retained < pointBudget
                && output.size() < MAX_MAP_ROUTES; index++) {
            JSONArray a = route.optJSONArray(index - 1);
            JSONArray b = route.optJSONArray(index);
            if (a == null || b == null || a.length() < 2 || b.length() < 2) continue;
            double longitude = (a.optDouble(0) + b.optDouble(0)) / 2d;
            double latitude = (a.optDouble(1) + b.optDouble(1)) / 2d;
            if (settlementContains(boundary, longitude, latitude)) {
                if (current == null) {
                    current = new JSONArray();
                    current.put(copyCoordinate(a));
                    retained++;
                }
                if (index % stride == 0 || index == route.length() - 1) {
                    current.put(copyCoordinate(b));
                    retained++;
                    if (current.length() >= MAX_SETTLEMENT_POINTS_PER_ROUTE) {
                        if (current.length() >= 2) output.add(current);
                        current = new JSONArray();
                        current.put(copyCoordinate(b));
                        retained++;
                    }
                }
            } else if (current != null) {
                if (current.length() >= 2) output.add(current);
                current = null;
            }
        }
        if (current != null && current.length() >= 2 && output.size() < MAX_MAP_ROUTES)
            output.add(current);
        return retained;
    }

    private JSONArray copyCoordinate(JSONArray coordinate) {
        JSONArray copy = new JSONArray();
        try {
            copy.put(coordinate.optDouble(0));
            copy.put(coordinate.optDouble(1));
            if (coordinate.length() > 2) copy.put(coordinate.optDouble(2));
        } catch (org.json.JSONException ignored) {
            // Coordinates are numeric and the fresh array cannot otherwise fail.
        }
        return copy;
    }

    private boolean settlementContains(JSONObject value, double longitude, double latitude) {
        if (value == null) return false;
        String type = value.optString("type", "");
        if ("FeatureCollection".equals(type)) {
            JSONArray features = value.optJSONArray("features");
            if (features != null) for (int i = 0; i < features.length(); i++) {
                if (settlementContains(features.optJSONObject(i), longitude, latitude)) return true;
            }
            return false;
        }
        if ("Feature".equals(type))
            return settlementContains(value.optJSONObject("geometry"), longitude, latitude);
        JSONArray coordinates = value.optJSONArray("coordinates");
        if ("Polygon".equals(type)) return polygonContains(coordinates, longitude, latitude);
        if ("MultiPolygon".equals(type) && coordinates != null) {
            for (int i = 0; i < coordinates.length(); i++)
                if (polygonContains(coordinates.optJSONArray(i), longitude, latitude)) return true;
        }
        return false;
    }

    private boolean polygonContains(JSONArray rings, double longitude, double latitude) {
        if (rings == null || rings.length() == 0
                || !ringContains(rings.optJSONArray(0), longitude, latitude)) return false;
        for (int i = 1; i < rings.length(); i++)
            if (ringContains(rings.optJSONArray(i), longitude, latitude)) return false;
        return true;
    }

    private boolean ringContains(JSONArray ring, double longitude, double latitude) {
        if (ring == null || ring.length() < 4) return false;
        boolean inside = false;
        for (int i = 0, j = ring.length() - 1; i < ring.length(); j = i++) {
            JSONArray left = ring.optJSONArray(i), right = ring.optJSONArray(j);
            if (left == null || right == null || left.length() < 2 || right.length() < 2) continue;
            double x1 = left.optDouble(0), y1 = left.optDouble(1);
            double x2 = right.optDouble(0), y2 = right.optDouble(1);
            if ((y1 > latitude) != (y2 > latitude)
                    && longitude < (x2 - x1) * (latitude - y1) / (y2 - y1) + x1) inside = !inside;
        }
        return inside;
    }

    private long mapDataRevision() {
        return 31L * JourneyStore.dataRevision(getApplicationContext())
                + ServiceStationStore.revision(getApplicationContext());
    }

    private MapRoutes readMapRoutes() throws Exception {
        MapRoutes output = new MapRoutes();
        if (ServiceStationStore.unlocked(getApplicationContext())) {
            output.serviceStations = ServiceStationStore.stations(getApplicationContext());
            output.visitedServiceStationIds = ServiceStationStore.completed(getApplicationContext());
        }
        MotorwayProgressCalculator motorwayCalculator =
                new MotorwayProgressCalculator(getApplicationContext());
        ARoadProgressCalculator aRoadCalculator = new ARoadProgressCalculator(getApplicationContext());
        JourneyStore.forEach(getApplicationContext(), journey -> {
            if (!"complete".equals(journey.optString("processing_status", ""))) return;
            String mode = journey.optString("mode", "").trim().toLowerCase(java.util.Locale.ROOT);
            boolean roadJourney = "driving".equals(mode) || "bus".equals(mode);
            boolean footJourney = "walking".equals(mode) || "running".equals(mode)
                    || "pedestrian".equals(mode);
            if (!roadJourney && !footJourney) return;
            motorwayCalculator.addJourney(journey);
            aRoadCalculator.addJourney(journey);
            // The main map represents matched travel along the road network. Raw GPS
            // traces can be sparse and make misleading point-to-point straight lines.
            List<JSONArray> matched = matchedSegments(journey);
            List<JSONArray> motorways = motorwaySegments(journey);
            if (matched.isEmpty() && motorways.isEmpty()) return;
            for (JSONArray route : matched) {
                if (output.retainedPoints >= MAX_MAP_POINTS
                        || output.sections.size() >= MAX_MAP_ROUTES) {
                    output.simplified = true;
                    break;
                }
                int allowed = Math.min(MAX_POINTS_PER_ROUTE,
                        MAX_MAP_POINTS - output.retainedPoints);
                JSONArray projected = projectMapRoute(route, allowed);
                if (projected != null) {
                    output.sections.add(projected);
                    output.retainedPoints += projected.length();
                    if (projected.length() < route.length()) output.simplified = true;
                }
            }
            for (JSONArray route : motorways) {
                if (output.motorwayRetainedPoints >= MAX_MOTORWAY_POINTS
                        || output.motorwaySections.size() >= MAX_MAP_ROUTES) {
                    output.simplified = true;
                    break;
                }
                int allowed = Math.min(MAX_POINTS_PER_ROUTE,
                        MAX_MOTORWAY_POINTS - output.motorwayRetainedPoints);
                JSONArray projected = projectMapRoute(route, allowed);
                if (projected != null) {
                    output.motorwaySections.add(projected);
                    output.motorwayRetainedPoints += projected.length();
                    if (projected.length() < route.length()) output.simplified = true;
                }
            }
            output.matchedJourneys++;
        });
        MotorwayProgressCalculator.Summary summary = motorwayCalculator.finish();
        for (MotorwayProgressCalculator.Road road : summary.roads) {
            if (!road.referenceAvailable) continue;
            output.roadPercentages.put(road.ref.toUpperCase(java.util.Locale.ROOT), road.percent());
            appendCanonicalSections(output, road.incompleteMapSections, false);
            appendCanonicalSections(output, road.coveredMapSections, true);
        }
        ARoadProgressCalculator.Summary aSummary = aRoadCalculator.finish();
        for (ARoadProgressCalculator.Road road : aSummary.roads) {
            if (!road.referenceAvailable) continue;
            output.roadPercentages.put(road.ref.toUpperCase(java.util.Locale.ROOT), road.percent());
            appendARoadSections(output, road.incompleteMapSections, false);
            appendARoadSections(output, road.coveredMapSections, true);
        }
        return output;
    }

    private void appendCanonicalSections(MapRoutes output, List<JSONArray> routes,
                                         boolean covered) {
        List<JSONArray> target = covered ? output.coveredMotorwaySections
                : output.incompleteMotorwaySections;
        int used = covered ? output.coveredMotorwayPoints : output.incompleteMotorwayPoints;
        for (JSONArray route : routes) {
            if (used >= MAX_MOTORWAY_POINTS || target.size() >= MAX_MAP_ROUTES) {
                output.simplified = true;
                break;
            }
            int allowed = Math.min(MAX_POINTS_PER_ROUTE, MAX_MOTORWAY_POINTS - used);
            JSONArray projected = projectMapRoute(route, allowed);
            if (projected == null) continue;
            target.add(projected);
            used += projected.length();
            if (projected.length() < route.length()) output.simplified = true;
        }
        if (covered) output.coveredMotorwayPoints = used;
        else output.incompleteMotorwayPoints = used;
    }

    private void appendARoadSections(MapRoutes output, List<JSONArray> routes, boolean covered) {
        List<JSONArray> target=covered?output.coveredARoadSections:output.incompleteARoadSections;
        int used=covered?output.coveredARoadPoints:output.incompleteARoadPoints;
        for(JSONArray route:routes){
            if(used>=MAX_MOTORWAY_POINTS||target.size()>=MAX_MAP_ROUTES){output.simplified=true;break;}
            int allowed=Math.min(MAX_POINTS_PER_ROUTE,MAX_MOTORWAY_POINTS-used);
            JSONArray projected=projectMapRoute(route,allowed);if(projected==null)continue;
            target.add(projected);used+=projected.length();if(projected.length()<route.length())output.simplified=true;
        }
        if(covered)output.coveredARoadPoints=used;else output.incompleteARoadPoints=used;
    }

    private JSONArray projectMapRoute(JSONArray route, int maxPoints) {
        if (route == null || route.length() < 2 || maxPoints < 2) return null;
        int pointCount = route.length();
        boolean[] keep = new boolean[pointCount];
        keep[0] = true;
        keep[pointCount - 1] = true;
        int[] starts = new int[pointCount];
        int[] ends = new int[pointCount];
        int stackSize = 1;
        starts[0] = 0;
        ends[0] = pointCount - 1;
        double midLatitude = 0;
        JSONArray firstPoint = route.optJSONArray(0);
        JSONArray lastPoint = route.optJSONArray(pointCount - 1);
        if (firstPoint != null && lastPoint != null) {
            midLatitude = (firstPoint.optDouble(1) + lastPoint.optDouble(1)) / 2.0;
        }
        double longitudeScale = Math.cos(Math.toRadians(midLatitude));
        double toleranceSquared = 0.00008 * 0.00008;
        int retained = 2;
        while (stackSize > 0) {
            int start = starts[--stackSize];
            int end = ends[stackSize];
            JSONArray a = route.optJSONArray(start);
            JSONArray b = route.optJSONArray(end);
            if (a == null || b == null || a.length() < 2 || b.length() < 2) continue;
            double ax = a.optDouble(0) * longitudeScale;
            double ay = a.optDouble(1);
            double bx = b.optDouble(0) * longitudeScale;
            double by = b.optDouble(1);
            double dx = bx - ax;
            double dy = by - ay;
            double lengthSquared = dx * dx + dy * dy;
            double greatestDistance = -1;
            int greatestIndex = -1;
            for (int index = start + 1; index < end; index++) {
                JSONArray point = route.optJSONArray(index);
                if (point == null || point.length() < 2) continue;
                double px = point.optDouble(0) * longitudeScale;
                double py = point.optDouble(1);
                double amount = lengthSquared == 0 ? 0 : Math.max(0, Math.min(1,
                        ((px - ax) * dx + (py - ay) * dy) / lengthSquared));
                double offsetX = px - (ax + amount * dx);
                double offsetY = py - (ay + amount * dy);
                double distanceSquared = offsetX * offsetX + offsetY * offsetY;
                if (distanceSquared > greatestDistance) {
                    greatestDistance = distanceSquared;
                    greatestIndex = index;
                }
            }
            if (greatestIndex >= 0 && greatestDistance > toleranceSquared) {
                keep[greatestIndex] = true;
                retained++;
                starts[stackSize] = start;
                ends[stackSize++] = greatestIndex;
                starts[stackSize] = greatestIndex;
                ends[stackSize++] = end;
            }
        }
        JSONArray projected = new JSONArray();
        if (retained <= maxPoints) {
            for (int index = 0; index < pointCount; index++) {
                if (keep[index]) projected.put(route.optJSONArray(index));
            }
        } else {
            int emitted = 0;
            int skipped = 0;
            for (int index = 0; index < pointCount; index++) {
                if (!keep[index]) continue;
                int target = (int) Math.round(emitted * (retained - 1.0) / (maxPoints - 1.0));
                if (skipped++ == target) {
                    projected.put(route.optJSONArray(index));
                    emitted++;
                }
            }
        }
        return projected.length() >= 2 ? projected : null;
    }

    private List<JSONArray> recordedSegments(JSONObject journey) {
        List<JSONArray> routes = new ArrayList<>();
        JSONObject geometry = journey.optJSONObject("route_geometry");
        JSONArray coordinates = geometry == null ? null : geometry.optJSONArray("coordinates");
        if (coordinates == null) return routes;
        String type = geometry.optString("type", "LineString");
        if ("LineString".equals(type) && hasLinePoints(coordinates)) {
            routes.add(coordinates);
        } else if ("MultiLineString".equals(type)) {
            for (int index = 0; index < coordinates.length(); index++) {
                JSONArray segment = coordinates.optJSONArray(index);
                if (hasLinePoints(segment)) routes.add(segment);
            }
        }
        return routes;
    }

    private List<JSONArray> matchedSegments(JSONObject journey) {
        List<JSONArray> routes = new ArrayList<>();
        JSONObject result = journey.optJSONObject("processing_result");
        JSONObject geojson = result == null ? null : result.optJSONObject("geojson");
        JSONArray features = geojson == null ? null : geojson.optJSONArray("features");
        if (features == null) return routes;
        for (int index = 0; index < features.length(); index++) {
            JSONObject feature = features.optJSONObject(index);
            JSONObject geometry = feature == null ? null : feature.optJSONObject("geometry");
            if (geometry == null) continue;
            String type = geometry.optString("type", "");
            JSONArray coordinates = geometry.optJSONArray("coordinates");
            if ("LineString".equals(type) && hasLinePoints(coordinates)) {
                routes.add(coordinates);
            } else if ("MultiLineString".equals(type) && coordinates != null) {
                for (int line = 0; line < coordinates.length(); line++) {
                    JSONArray segment = coordinates.optJSONArray(line);
                    if (hasLinePoints(segment)) routes.add(segment);
                }
            }
        }
        JSONObject corrections = journey.optJSONObject("journey_corrections");
        JSONArray removedValues = corrections == null
                ? null : corrections.optJSONArray("removed_matched_segments");
        Set<Integer> removedEdges = new HashSet<>();
        if (removedValues != null) {
            for (int index = 0; index < removedValues.length(); index++) {
                int edge = removedValues.optInt(index, -1);
                if (edge >= 0) removedEdges.add(edge);
            }
        }
        if (removedEdges.isEmpty()) return routes;

        List<JSONArray> visible = new ArrayList<>();
        int edgeIndex = 0;
        for (JSONArray route : routes) {
            JSONArray current = null;
            for (int index = 1; index < route.length(); index++, edgeIndex++) {
                if (removedEdges.contains(edgeIndex)) {
                    if (current != null && current.length() >= 2) visible.add(current);
                    current = null;
                    continue;
                }
                if (current == null) {
                    current = new JSONArray();
                    current.put(route.optJSONArray(index - 1));
                }
                current.put(route.optJSONArray(index));
            }
            if (current != null && current.length() >= 2) visible.add(current);
        }
        return visible;
    }

    private List<JSONArray> motorwaySegments(JSONObject journey) {
        List<JSONArray> routes = new ArrayList<>();
        String mode = journey.optString("mode", "").toLowerCase(java.util.Locale.ROOT);
        if (!("driving".equals(mode) || "bus".equals(mode))) return routes;
        JSONObject result = journey.optJSONObject("processing_result");
        JSONObject geojson = result == null ? null : result.optJSONObject("motorway_geojson");
        JSONArray features = geojson == null ? null : geojson.optJSONArray("features");
        if (features == null) return routes;
        for (int index = 0; index < features.length(); index++) {
            JSONObject feature = features.optJSONObject(index);
            if (JourneyCorrectionUtils.excludesRoadFeature(journey, feature)) continue;
            JSONObject geometry = feature == null ? null : feature.optJSONObject("geometry");
            if (geometry == null) continue;
            String type = geometry.optString("type", "");
            JSONArray coordinates = geometry.optJSONArray("coordinates");
            if ("LineString".equals(type) && hasLinePoints(coordinates)) {
                routes.add(coordinates);
            } else if ("MultiLineString".equals(type) && coordinates != null) {
                for (int line = 0; line < coordinates.length(); line++) {
                    JSONArray segment = coordinates.optJSONArray(line);
                    if (hasLinePoints(segment)) routes.add(segment);
                }
            }
        }
        return routes;
    }

    private boolean hasLinePoints(JSONArray points) {
        return points != null && points.length() >= 2;
    }

    private void showServiceStationCard(JSONObject station, boolean visited) {
        if (station == null || isFinishing()) return;
        String name = station.optString("name", "Service station");
        LinearLayout content = cardContainer();
        addCardLabel(content, "SERVICE STATION");
        addCardTitle(content, name);
        addCardBody(content, visited ? "Visited" : "Not visited");
        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        TextView close = cardAction("CLOSE", false);
        close.setOnClickListener(v -> stationDialog.dismiss());
        if (visited) {
            TextView related = cardAction("VIEW RELATED JOURNEYS", true);
            related.setOnClickListener(v -> {
                stationDialog.dismiss();
                loadRelatedServiceStationJourneys(station);
            });
            actions.addView(related);
        }
        actions.addView(close);
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(-1, -2);
        actionParams.topMargin = dp(14);
        content.addView(actions, actionParams);
        stationDialog = new AlertDialog.Builder(this).setView(content).create();
        stationDialog.setOnShowListener(dialog -> {
            if (stationDialog.getWindow() != null)
                stationDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        });
        stationDialog.show();
    }

    private AlertDialog stationDialog;

    private LinearLayout cardContainer() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(22), dp(20), dp(22), dp(14));
        card.setBackground(roundRect(0xFF10275D, dp(20)));
        return card;
    }

    private void addCardLabel(LinearLayout parent, String value) {
        TextView label = new TextView(this);
        label.setText(value);
        label.setTextColor(0xFF67D5CC);
        label.setTextSize(12);
        label.setTypeface(null, Typeface.BOLD);
        parent.addView(label);
    }

    private void addCardTitle(LinearLayout parent, String value) {
        TextView title = new TextView(this);
        title.setText(value);
        title.setTextColor(Color.WHITE);
        title.setTextSize(21);
        title.setTypeface(null, Typeface.BOLD);
        title.setPadding(0, dp(5), 0, dp(4));
        parent.addView(title);
    }

    private void addCardBody(LinearLayout parent, String value) {
        TextView body = new TextView(this);
        body.setText(value);
        body.setTextColor(0xFFD3DCED);
        body.setTextSize(16);
        parent.addView(body);
    }

    private TextView cardAction(String value, boolean primary) {
        TextView button = new TextView(this);
        button.setText(value);
        button.setTextColor(primary ? GOLD : 0xFF67D5CC);
        button.setTextSize(13);
        button.setTypeface(null, Typeface.BOLD);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(12), dp(12), dp(12), dp(12));
        button.setClickable(true);
        button.setFocusable(true);
        return button;
    }

    private void loadRelatedServiceStationJourneys(JSONObject station) {
        String stationId = station.optString("id", "");
        String stationName = station.optString("name", "Service station");
        double latitude = station.optDouble("lat", Double.NaN);
        double longitude = station.optDouble("lng", Double.NaN);
        ScreenDataLoader.execute(() -> {
            List<VisitTimeWindow> confirmedVisits = new ArrayList<>();
            List<JSONObject> related = new ArrayList<>();
            Set<String> relatedJourneyIds = new HashSet<>();
            try {
                for (String journeyId : ServiceStationVisitStore.journeyIdsForStation(
                        getApplicationContext(), stationId)) {
                    JSONObject linked = JourneyStore.get(getApplicationContext(), journeyId);
                    if (linked != null) {
                        related.add(linked);
                        relatedJourneyIds.add(journeyId);
                    }
                }
                JSONArray visits = TimelineVisitStore.all(getApplicationContext());
                JSONArray catalogue = ServiceStationStore.stations(getApplicationContext());
                for (int i = 0; i < visits.length(); i++) {
                    JSONObject visit = visits.optJSONObject(i);
                    if (visit == null || !Double.isFinite(latitude) || !Double.isFinite(longitude))
                        continue;
                    double visitLat = visit.optDouble("lat", Double.NaN);
                    double visitLng = visit.optDouble("lng", Double.NaN);
                    JSONObject nearestStation = null;
                    double nearestDistance = Double.MAX_VALUE;
                    for (int j = 0; j < catalogue.length(); j++) {
                        JSONObject candidate = catalogue.optJSONObject(j);
                        if (candidate == null) continue;
                        double distance = distanceMetres(visitLat, visitLng,
                                candidate.optDouble("lat", Double.NaN),
                                candidate.optDouble("lng", Double.NaN));
                        if (distance < nearestDistance) {
                            nearestDistance = distance;
                            nearestStation = candidate;
                        }
                    }
                    if (nearestStation == null || nearestDistance > 350.0
                            || !stationId.equals(nearestStation.optString("id", ""))) continue;
                    long visitStart = parseTimelineTime(visit.optString("start", ""));
                    long visitEnd = parseTimelineTime(visit.optString("end", ""));
                    String fingerprint = visit.optString("source_file_fingerprint", "");
                    if (fingerprint.isEmpty() || visitStart < 0 || visitEnd < 0) continue;
                    confirmedVisits.add(new VisitTimeWindow(fingerprint,
                            Math.min(visitStart, visitEnd), Math.max(visitStart, visitEnd)));
                }
                if (!confirmedVisits.isEmpty()) {
                    JourneyStore.forEach(getApplicationContext(), journey -> {
                        if (!"complete".equals(journey.optString("processing_status", ""))
                                || relatedJourneyIds.contains(journey.optString("journey_id", ""))) return;
                        JSONObject source = journey.optJSONObject("source");
                        String fingerprint = source == null ? ""
                                : source.optString("source_file_fingerprint", "");
                        if (fingerprint.isEmpty()) return;
                        long journeyStart = parseTimelineTime(journey.optString("started_at", ""));
                        long journeyEnd = parseTimelineTime(journey.optString("ended_at", ""));
                        if (journeyStart < 0 || journeyEnd < 0
                                || !hasMatchingTimelineVisit(confirmedVisits, fingerprint,
                                        journeyStart, journeyEnd)) return;
                        String mode = journey.optString("mode", "")
                                .toLowerCase(java.util.Locale.ROOT);
                        if (!("driving".equals(mode) || "bus".equals(mode)
                                || "walking".equals(mode) || "running".equals(mode)
                                || "pedestrian".equals(mode))) return;
                        for (JSONArray route : matchedSegments(journey)) {
                            if (routeNearStation(route, latitude, longitude, 600.0)) {
                                related.add(journey);
                                break;
                            }
                        }
                    });
                }
            } catch (Exception error) {
                android.util.Log.w("Roadprints", "Could not load service station journeys", error);
            }
            mainHandler.post(() -> showRelatedServiceStationJourneys(stationName, related));
        });
    }

    private static final class VisitTimeWindow {
        final String fingerprint;
        final long start;
        final long end;

        VisitTimeWindow(String fingerprint, long start, long end) {
            this.fingerprint = fingerprint;
            this.start = start;
            this.end = end;
        }
    }

    private boolean hasMatchingTimelineVisit(List<VisitTimeWindow> visits, String fingerprint,
                                             long journeyStart, long journeyEnd) {
        long allowance = 30L * 60L * 1000L;
        for (VisitTimeWindow visit : visits) {
            if (fingerprint.equals(visit.fingerprint)
                    && visit.start <= journeyEnd + allowance
                    && visit.end + allowance >= journeyStart) return true;
        }
        return false;
    }

    private long parseTimelineTime(String value) {
        if (value == null || value.isEmpty()) return -1;
        try {
            return java.time.Instant.parse(value).toEpochMilli();
        } catch (Exception ignored) {
            return -1;
        }
    }

    private boolean routeNearStation(JSONArray route, double latitude, double longitude,
                                     double thresholdMetres) {
        if (route == null || route.length() == 0) return false;
        for (int i = 0; i < route.length(); i++) {
            JSONArray point = route.optJSONArray(i);
            if (point == null || point.length() < 2) continue;
            double lng = point.optDouble(0, Double.NaN);
            double lat = point.optDouble(1, Double.NaN);
            if (!Double.isFinite(lat) || !Double.isFinite(lng)) continue;
            if (distanceMetres(latitude, longitude, lat, lng) <= thresholdMetres) return true;
            if (i == 0) continue;
            JSONArray previous = route.optJSONArray(i - 1);
            if (previous == null || previous.length() < 2) continue;
            double prevLng = previous.optDouble(0, Double.NaN);
            double prevLat = previous.optDouble(1, Double.NaN);
            if (Double.isFinite(prevLat) && Double.isFinite(prevLng)
                    && pointSegmentDistanceMetres(longitude, latitude, prevLng, prevLat,
                            lng, lat) <= thresholdMetres) return true;
        }
        return false;
    }

    private double distanceMetres(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 6_371_000 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private double pointSegmentDistanceMetres(double lng, double lat,
            double ax, double ay, double bx, double by) {
        double metresX = 111320.0 * Math.cos(Math.toRadians(lat));
        double px = (lng - ax) * metresX, py = (lat - ay) * 111320.0;
        double dx = (bx - ax) * metresX, dy = (by - ay) * 111320.0;
        double lengthSquared = dx * dx + dy * dy;
        double t = lengthSquared <= 0 ? 0
                : Math.max(0, Math.min(1, (px * dx + py * dy) / lengthSquared));
        return Math.hypot(px - t * dx, py - t * dy);
    }

    private void showRelatedServiceStationJourneys(String stationName, List<JSONObject> journeys) {
        if (isFinishing()) return;
        LinearLayout content = cardContainer();
        addCardLabel(content, "RELATED JOURNEYS");
        addCardTitle(content, stationName);
        if (journeys.isEmpty()) {
            addCardBody(content, "No matched journeys were found close to this service station.");
        } else {
            ScrollView scroll = new ScrollView(this);
            LinearLayout rows = new LinearLayout(this);
            rows.setOrientation(LinearLayout.VERTICAL);
            for (int i = 0; i < journeys.size(); i++) {
                JSONObject journey = journeys.get(i);
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.VERTICAL);
                row.setPadding(dp(14), dp(12), dp(14), dp(12));
                row.setBackground(roundRect(i % 2 == 0 ? 0xFF1B356E : 0xFF203E78, dp(12)));
                TextView title = new TextView(this);
                title.setText(journey.optString("title", journey.optString("name", "Journey")));
                title.setTextColor(Color.WHITE);
                title.setTextSize(15);
                title.setTypeface(null, Typeface.BOLD);
                TextView date = new TextView(this);
                date.setText(formatJourneyDate(journey.optString("started_at", "")));
                date.setTextColor(0xFF67D5CC);
                date.setTextSize(13);
                date.setPadding(0, dp(4), 0, 0);
                row.addView(title);
                row.addView(date);
                row.setClickable(true);
                row.setFocusable(true);
                row.setOnClickListener(v -> {
                    String journeyId = journey.optString("journey_id", "");
                    if (journeyId.isEmpty()) return;
                    journeyListDialog.dismiss();
                    Intent editor = new Intent(this, JourneyMapEditorActivity.class);
                    editor.putExtra("journey_id", journeyId);
                    startActivity(editor);
                });
                LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
                rowParams.bottomMargin = dp(8);
                rows.addView(row, rowParams);
            }
            scroll.addView(rows);
            content.addView(scroll, new LinearLayout.LayoutParams(-1, dp(330)));
        }
        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.END);
        TextView close = cardAction("CLOSE", false);
        close.setOnClickListener(v -> journeyListDialog.dismiss());
        actions.addView(close);
        content.addView(actions);
        journeyListDialog = new AlertDialog.Builder(this).setView(content).create();
        journeyListDialog.setOnShowListener(dialog -> {
            if (journeyListDialog.getWindow() != null)
                journeyListDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        });
        journeyListDialog.show();
    }

    private AlertDialog journeyListDialog;

    private String formatJourneyDate(String value) {
        if (value == null || value.isEmpty()) return "Date unavailable";
        try {
            java.time.Instant instant = java.time.Instant.parse(value);
            return java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy · HH:mm",
                    java.util.Locale.UK).withZone(java.time.ZoneId.systemDefault()).format(instant);
        } catch (Exception ignored) {
            return "Date unavailable";
        }
    }

    private void loadRoadSummaryAt(double latitude, double longitude) {
        ScreenDataLoader.execute(() -> {
            String bestKey = null, bestName = null;
            double bestDistance = 85.0;
            Map<String, List<JSONObject>> matchesByRoad = new HashMap<>();
            Map<String, Double> metresByRoadJourney = new HashMap<>();
            try {
                final String[] selectedKey = {null};
                final String[] selectedName = {null};
                final double[] selectedDistance = {85.0};
                JourneyStore.forEach(getApplicationContext(), journey -> {
                    if (!"complete".equals(journey.optString("processing_status", ""))) return;
                    String mode = journey.optString("mode", "").toLowerCase(java.util.Locale.ROOT);
                    if (!(mode.equals("driving") || mode.equals("bus") || mode.equals("walking")
                            || mode.equals("running") || mode.equals("pedestrian"))) return;
                    JSONObject result = journey.optJSONObject("processing_result");
                    JSONObject geo = result == null ? null : result.optJSONObject("road_geojson");
                    JSONArray features = geo == null ? null : geo.optJSONArray("features");
                    if (features == null || features.length() == 0) {
                        if (result != null) {
                            features = new JSONArray();
                            appendFeatureArray(features, result.optJSONObject("motorway_geojson"));
                            appendFeatureArray(features, result.optJSONObject("a_road_geojson"));
                        }
                    }
                    if (features == null) return;
                    for (int i = 0; i < features.length(); i++) {
                        JSONObject feature = features.optJSONObject(i);
                        if (feature == null || JourneyCorrectionUtils.excludesRoadFeature(journey, feature))
                            continue;
                        JSONObject props = feature.optJSONObject("properties");
                        if (props == null) continue;
                        String rawRef = props.optString("road_ref", props.optString("ref", "")).trim();
                        String name = props.optString("name", props.optString("road_name", "")).trim();
                        String label = !rawRef.isEmpty() ? rawRef : name;
                        if (label.isEmpty()) continue;
                        String key = label.toUpperCase(java.util.Locale.ROOT);
                        double distance = featureDistanceMetres(feature, latitude, longitude);
                        if (distance < selectedDistance[0]) {
                            selectedDistance[0] = distance;
                            selectedKey[0] = key;
                            selectedName[0] = label;
                        }
                    }
                });
                bestKey = selectedKey[0];
                bestName = selectedName[0];
                bestDistance = selectedDistance[0];
                if (bestKey != null && bestDistance <= 85.0) {
                    final String chosen = bestKey;
                    JourneyStore.forEach(getApplicationContext(), journey -> {
                        if (!"complete".equals(journey.optString("processing_status", ""))) return;
                        JSONObject result = journey.optJSONObject("processing_result");
                        JSONObject geo = result == null ? null : result.optJSONObject("road_geojson");
                        JSONArray features = geo == null ? null : geo.optJSONArray("features");
                        if (features == null || features.length() == 0) return;
                        boolean included = false;
                        double travelled = 0;
                        for (int i = 0; i < features.length(); i++) {
                            JSONObject feature = features.optJSONObject(i);
                            JSONObject props = feature == null ? null : feature.optJSONObject("properties");
                            if (feature == null || props == null
                                    || JourneyCorrectionUtils.excludesRoadFeature(journey, feature)) continue;
                            String label = props.optString("road_ref", props.optString("ref",
                                    props.optString("name", props.optString("road_name", "")))).trim();
                            if (!chosen.equals(label.toUpperCase(java.util.Locale.ROOT))) continue;
                            if (featureDistanceMetres(feature, latitude, longitude) > 85.0) continue;
                            included = true;
                            travelled += Math.max(0, props.optDouble("distance_m", 0));
                        }
                        if (included) {
                            String id = journey.optString("journey_id", "");
                            if (!id.isEmpty()) {
                                matchesByRoad.computeIfAbsent(chosen, k -> new ArrayList<>()).add(journey);
                                metresByRoadJourney.put(id, travelled);
                            }
                        }
                    });
                }
            } catch (Exception error) {
                android.util.Log.w("Roadprints", "Could not calculate the tapped road summary", error);
            }
            final String key = bestKey, label = bestName;
            final List<JSONObject> matches = key == null
                    ? new ArrayList<>() : matchesByRoad.getOrDefault(key, new ArrayList<>());
            final double distance = bestDistance;
            double totalMetres = 0;
            for (JSONObject journey : matches)
                totalMetres += metresByRoadJourney.getOrDefault(journey.optString("journey_id", ""), 0d);
            final double travelled = totalMetres;
            mainHandler.post(() -> {
                if (isFinishing()) return;
                if (label == null || distance > 85.0) {
                    showRoadSummaryCard("Road", 0, 0, Double.NaN, new ArrayList<>());
                    return;
                }
                double percent = roadPercentages.getOrDefault(key, Double.NaN);
                showRoadSummaryCard(label, travelled, matches.size(), percent, matches);
            });
        });
    }

    private void appendFeatureArray(JSONArray target, JSONObject collection) {
        JSONArray features = collection == null ? null : collection.optJSONArray("features");
        if (features == null) return;
        for (int i = 0; i < features.length(); i++) target.put(features.optJSONObject(i));
    }

    private double featureDistanceMetres(JSONObject feature, double latitude, double longitude) {
        JSONObject geometry = feature.optJSONObject("geometry");
        if (geometry == null) return Double.MAX_VALUE;
        String type = geometry.optString("type", "");
        JSONArray coordinates = geometry.optJSONArray("coordinates");
        double nearest = Double.MAX_VALUE;
        if ("LineString".equals(type)) return lineDistanceMetres(coordinates, latitude, longitude);
        if ("MultiLineString".equals(type) && coordinates != null)
            for (int i = 0; i < coordinates.length(); i++)
                nearest = Math.min(nearest, lineDistanceMetres(
                        coordinates.optJSONArray(i), latitude, longitude));
        return nearest;
    }

    private double lineDistanceMetres(JSONArray line, double latitude, double longitude) {
        if (line == null) return Double.MAX_VALUE;
        double nearest = Double.MAX_VALUE;
        for (int i = 1; i < line.length(); i++) {
            JSONArray a = line.optJSONArray(i - 1), b = line.optJSONArray(i);
            if (a == null || b == null || a.length() < 2 || b.length() < 2) continue;
            nearest = Math.min(nearest, pointSegmentDistanceMetres(longitude, latitude,
                    a.optDouble(0), a.optDouble(1), b.optDouble(0), b.optDouble(1)));
        }
        return nearest;
    }

    private void showRoadSummaryCard(String road, double metres, int times,
                                    double percent, List<JSONObject> journeys) {
        LinearLayout content = cardContainer();
        addCardLabel(content, "ROAD SUMMARY");
        addCardTitle(content, road);
        addRoadMetric(content, "Distance travelled", DistanceUnits.format(this, metres));
        addRoadMetric(content, "Times travelled", Integer.toString(times));
        addRoadMetric(content, "Road completed",
                Double.isFinite(percent) ? String.format(java.util.Locale.UK, "%.1f%%", percent)
                        : "Percentage unavailable");
        if (!journeys.isEmpty()) {
            TextView subheading = new TextView(this);
            subheading.setText("MATCHED JOURNEYS");
            subheading.setTextColor(0xFF67D5CC);
            subheading.setTextSize(12);
            subheading.setTypeface(null, Typeface.BOLD);
            subheading.setPadding(0, dp(12), 0, dp(8));
            content.addView(subheading);
            ScrollView scroll = new ScrollView(this);
            LinearLayout rows = new LinearLayout(this);
            rows.setOrientation(LinearLayout.VERTICAL);
            for (int i = 0; i < journeys.size(); i++) {
                JSONObject journey = journeys.get(i);
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.VERTICAL);
                row.setPadding(dp(14), dp(12), dp(14), dp(12));
                row.setBackground(roundRect(i % 2 == 0 ? 0xFF1B356E : 0xFF203E78, dp(12)));
                TextView title = new TextView(this);
                title.setText(journey.optString("title", journey.optString("name", "Journey")));
                title.setTextColor(Color.WHITE); title.setTextSize(15);
                title.setTypeface(null, Typeface.BOLD);
                TextView date = new TextView(this);
                date.setText(formatJourneyDate(journey.optString("started_at", "")));
                date.setTextColor(0xFF67D5CC); date.setTextSize(13);
                date.setPadding(0, dp(4), 0, 0);
                row.addView(title); row.addView(date);
                row.setClickable(true); row.setFocusable(true);
                row.setOnClickListener(v -> {
                    String id = journey.optString("journey_id", "");
                    if (id.isEmpty()) return;
                    roadDialog.dismiss();
                    Intent editor = new Intent(this, JourneyMapEditorActivity.class);
                    editor.putExtra("journey_id", id);
                    startActivity(editor);
                });
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
                params.bottomMargin = dp(8); rows.addView(row, params);
            }
            scroll.addView(rows);
            content.addView(scroll, new LinearLayout.LayoutParams(-1, dp(250)));
        }
        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.END);
        TextView close = cardAction("CLOSE", false);
        close.setOnClickListener(v -> roadDialog.dismiss());
        actions.addView(close); content.addView(actions);
        roadDialog = new AlertDialog.Builder(this).setView(content).create();
        roadDialog.setOnShowListener(dialog -> {
            if (roadDialog.getWindow() != null)
                roadDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        });
        roadDialog.show();
    }

    private AlertDialog roadDialog;

    private void addRoadMetric(LinearLayout parent, String heading, String value) {
        TextView label = new TextView(this);
        label.setText(heading);
        label.setTextColor(0xFF67D5CC);
        label.setTextSize(13);
        label.setTypeface(null, Typeface.BOLD);
        label.setPadding(0, dp(8), 0, 0);
        TextView number = new TextView(this);
        number.setText(value);
        number.setTextColor(Color.WHITE);
        number.setTextSize(17);
        parent.addView(label);
        parent.addView(number);
    }

    private void addZoomControls(FrameLayout mapFrame, RoutePreviewView map) {
        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setGravity(Gravity.CENTER);
        controls.addView(zoomButton("+", "Zoom in", map::zoomIn));
        View gap = new View(this);
        controls.addView(gap, new LinearLayout.LayoutParams(1, dp(8)));
        controls.addView(zoomButton("−", "Zoom out", map::zoomOut));

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                dp(48), LinearLayout.LayoutParams.WRAP_CONTENT,
                Gravity.END | Gravity.CENTER_VERTICAL);
        params.setMargins(0, 0, dp(12), 0);
        mapFrame.addView(controls, params);
    }

    private TextView zoomButton(String label, String description, Runnable action) {
        TextView button = new TextView(this);
        button.setText(label);
        button.setTextSize(27);
        button.setTypeface(null, Typeface.BOLD);
        button.setTextColor(0xFF10275D);
        button.setGravity(Gravity.CENTER);
        button.setContentDescription(description);
        button.setBackground(roundRect(Color.WHITE, dp(8)));
        button.setElevation(dp(4));
        button.setClickable(true);
        button.setFocusable(true);
        button.setOnClickListener(v -> action.run());
        button.setLayoutParams(new LinearLayout.LayoutParams(dp(48), dp(48)));
        return button;
    }

    private GradientDrawable roundRect(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private void applySystemBarInsets(View root, View heading, View bottomNavigation) {
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            int top;
            int bottom;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                top = bars.top;
                bottom = bars.bottom;
            } else {
                top = insets.getSystemWindowInsetTop();
                bottom = insets.getSystemWindowInsetBottom();
            }
            heading.setPadding(dp(18), dp(18) + top, dp(18), dp(14));
            LinearLayout.LayoutParams navParams =
                    (LinearLayout.LayoutParams) bottomNavigation.getLayoutParams();
            navParams.height = dp(68) + bottom;
            bottomNavigation.setPadding(dp(8), 0, dp(8), bottom);
            bottomNavigation.setLayoutParams(navParams);
            return insets;
        });
        root.requestApplyInsets();
    }

    private View buildBottomNavigation() {
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        nav.setPadding(dp(8), 0, dp(8), 0);
        nav.setBackgroundColor(NAV_BAR);

        int[] icons = {R.drawable.ic_nav_map, R.drawable.ic_nav_journeys,
                R.drawable.ic_nav_progress, R.drawable.ic_nav_achievements,
                R.drawable.ic_nav_collections};
        String[] labels = {"Map", "Journeys", "Progress", "Achievements", "Collections"};
        for (int index = 0; index < labels.length; index++) {
            final int selected = index;
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            item.setPadding(0, dp(4), 0, 0);

            ImageView icon = new ImageView(this);
            icon.setImageResource(icons[index]);
            icon.setContentDescription(labels[index]);
            icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            icon.setColorFilter(index == 0 ? GOLD : MUTED,
                    android.graphics.PorterDuff.Mode.SRC_IN);
            item.addView(icon, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(32)));

            TextView label = new TextView(this);
            label.setText(labels[index]);
            label.setTextSize(10);
            label.setGravity(Gravity.CENTER);
            label.setIncludeFontPadding(false);
            label.setMaxLines(1);
            label.setTextColor(index == 0 ? GOLD : MUTED);
            item.addView(label, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(24)));

            if (index <= 4) {
                item.setClickable(true);
                item.setFocusable(true);
                item.setOnClickListener(v -> {
                    if (selected == 1) {
                        startActivity(new Intent(this, JourneyListActivity.class));
                        finish();
                    } else if (selected == 2) {
                        startActivity(new Intent(this, ProgressActivity.class));
                        finish();
                    } else if (selected == 3) {
                        startActivity(new Intent(this, AchievementsActivity.class));
                        finish();
                    } else if (selected == 4) {
                        startActivity(new Intent(this, CollectionsActivity.class));
                        finish();
                    }
                });
            } else {
                item.setAlpha(0.55f);
            }
            nav.addView(item, new LinearLayout.LayoutParams(0, dp(68), 1));
        }
        return nav;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static final class MapRoutes {
        final List<JSONArray> sections = new ArrayList<>();
        final List<JSONArray> motorwaySections = new ArrayList<>();
        final List<JSONArray> incompleteMotorwaySections = new ArrayList<>();
        final List<JSONArray> coveredMotorwaySections = new ArrayList<>();
        final List<JSONArray> incompleteARoadSections = new ArrayList<>();
        final List<JSONArray> coveredARoadSections = new ArrayList<>();
        JSONArray serviceStations = new JSONArray();
        Set<String> visitedServiceStationIds = new HashSet<>();
        int matchedJourneys;
        int retainedPoints;
        int motorwayRetainedPoints;
        int incompleteMotorwayPoints;
        int coveredMotorwayPoints;
        int incompleteARoadPoints;
        int coveredARoadPoints;
        boolean simplified;
        final Map<String, Double> roadPercentages = new HashMap<>();
    }
}


