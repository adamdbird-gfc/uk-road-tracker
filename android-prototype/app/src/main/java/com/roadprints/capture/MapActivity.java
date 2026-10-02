package com.roadprints.capture;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
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

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
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
    private static final int MAX_SETTLEMENT_POINTS_PER_ROUTE = 6_000;
    private static final int MAX_MAP_ROUTES = 5_000;
    private TextView mapSubtitle;
    private GrowingStatusControl growingStatus;
    private FrameLayout mapFrame;
    private final ExecutorService mapLoader = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private int mapLoadGeneration;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(NAVY);
        getWindow().setNavigationBarColor(NAV_BAR);

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
        growingStatus = new GrowingStatusControl(this, titleRow);
        heading.addView(titleRow);
        heading.addView(mapSubtitle);
        root.addView(heading);

        mapFrame = new FrameLayout(this);
        mapFrame.setBackgroundColor(NAVY);
        root.addView(mapFrame, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        TextView loading = new TextView(this);
        loading.setText("Loading matched journeys…");
        loading.setTextColor(Color.WHITE);
        loading.setGravity(Gravity.CENTER);
        mapFrame.addView(loading, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        View bottomNavigation = buildBottomNavigation();
        root.addView(bottomNavigation);
        setContentView(root);
        applySystemBarInsets(root, heading, bottomNavigation);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (growingStatus != null) growingStatus.start();
        if (mapFrame != null && mapSubtitle != null) refreshMap();
    }

    @Override
    protected void onPause() {
        if (growingStatus != null) growingStatus.stop();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        mapLoadGeneration++;
        mapLoader.shutdownNow();
        super.onDestroy();
    }

    private void refreshMap() {
        final int generation = ++mapLoadGeneration;
        String settlementCode = getIntent().getStringExtra("settlement_code");
        if (settlementCode != null && !settlementCode.isEmpty()) {
            refreshSettlementMap(generation, settlementCode,
                    getIntent().getStringExtra("settlement_name"),
                    getIntent().getStringExtra("settlement_routes"));
            return;
        }
        mapSubtitle.setText("Loading matched journeys…");
        mapLoader.execute(() -> {
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
            mainHandler.post(() -> {
                if (isFinishing() || generation != mapLoadGeneration) return;
                mapSubtitle.setText(mapRoutes.sections.isEmpty()
                        && mapRoutes.motorwaySections.isEmpty()
                        && mapRoutes.incompleteMotorwaySections.isEmpty()
                        ? "Successfully matched journeys will appear here."
                        : mapRoutes.matchedJourneys + " successfully matched journeys"
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
                map.setContentDescription("Interactive OpenStreetMap. Pinch to zoom and drag to move.");
                mapFrame.addView(map, new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
                addZoomControls(mapFrame, map);
            });
        });
    }

    private void refreshSettlementMap(int generation, String code, String name,
                                      String routeJson) {
        mapSubtitle.setText("Loading " + name + " and your visited roads…");
        mapLoader.execute(() -> {
            JSONObject boundary = null;
            List<JSONArray> roads = new ArrayList<>();
            String failure = null;
            try {
                boundary = LocalRoadSettlementMatcher.boundary(code);
                JSONArray routeArray = new JSONArray(routeJson == null ? "[]" : routeJson);
                for (int index = 0; index < routeArray.length() && roads.size() < MAX_MAP_ROUTES; index++) {
                    JSONArray route = routeArray.optJSONArray(index);
                    for (JSONArray clipped : clipRouteToSettlement(route, boundary)) {
                        JSONArray projected = projectMapRoute(clipped, MAX_SETTLEMENT_POINTS_PER_ROUTE);
                        if (projected != null) roads.add(projected);
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

    private List<JSONArray> clipRouteToSettlement(JSONArray route, JSONObject boundary) {
        List<JSONArray> clipped = new ArrayList<>();
        if (route == null || boundary == null) return clipped;
        JSONArray current = null;
        for (int index = 1; index < route.length(); index++) {
            JSONArray a = route.optJSONArray(index - 1);
            JSONArray b = route.optJSONArray(index);
            if (a == null || b == null || a.length() < 2 || b.length() < 2) continue;
            double longitude = (a.optDouble(0) + b.optDouble(0)) / 2d;
            double latitude = (a.optDouble(1) + b.optDouble(1)) / 2d;
            if (settlementContains(boundary, longitude, latitude)) {
                if (current == null) {
                    current = new JSONArray();
                    current.put(a);
                }
                current.put(b);
            } else if (current != null) {
                if (current.length() >= 2) clipped.add(current);
                current = null;
            }
        }
        if (current != null && current.length() >= 2) clipped.add(current);
        return clipped;
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

    private MapRoutes readMapRoutes() {
        MapRoutes output = new MapRoutes();
        MotorwayProgressCalculator motorwayCalculator =
                new MotorwayProgressCalculator(getApplicationContext());
        ARoadProgressCalculator aRoadCalculator = new ARoadProgressCalculator(getApplicationContext());
        JourneyStore.forEach(getApplicationContext(), journey -> {
            if (!"complete".equals(journey.optString("processing_status", ""))) return;
            motorwayCalculator.addJourney(journey);
            aRoadCalculator.addJourney(journey);
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
            appendCanonicalSections(output, road.incompleteMapSections, false);
            appendCanonicalSections(output, road.coveredMapSections, true);
        }
        ARoadProgressCalculator.Summary aSummary = aRoadCalculator.finish();
        for (ARoadProgressCalculator.Road road : aSummary.roads) {
            if (!road.referenceAvailable) continue;
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

            if (index <= 3) {
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
        int matchedJourneys;
        int retainedPoints;
        int motorwayRetainedPoints;
        int incompleteMotorwayPoints;
        int coveredMotorwayPoints;
        int incompleteARoadPoints;
        int coveredARoadPoints;
        boolean simplified;
    }
}
