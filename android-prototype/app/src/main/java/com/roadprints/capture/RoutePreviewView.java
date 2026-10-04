package com.roadprints.capture;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import org.json.JSONArray;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RoutePreviewView extends View {
    private static final int TILE_SIZE = 256;
    private static final int MAX_ZOOM = 18;
    private static final long DEFAULT_TILE_TTL_MS = 7L * 24L * 60L * 60L * 1000L;
    private static final ExecutorService TILE_EXECUTOR = Executors.newFixedThreadPool(3);
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    private final JSONArray coordinates;
    private final List<JSONArray> matchedSegments;
    private final List<JSONArray> routeExtensions = new ArrayList<>();
    private final List<JSONArray> motorwaySegments = new ArrayList<>();
    private final List<JSONArray> incompleteMotorwaySegments = new ArrayList<>();
    private final List<JSONArray> coveredMotorwaySegments = new ArrayList<>();
    private final List<JSONArray> incompleteARoadSegments = new ArrayList<>();
    private final List<JSONArray> coveredARoadSegments = new ArrayList<>();
    private final List<JSONArray> settlementBoundaryRings = new ArrayList<>();
    private final Paint aRoadPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint aRoadIncompletePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint settlementBoundaryPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final boolean interactive;
    private final boolean showEmptyMessage;
    private final boolean showEndpointMarkers;
    private boolean routeEditMode;
    private Set<Integer> removedRouteEdges = Collections.emptySet();
    private Set<Integer> selectedRouteEdges = Collections.emptySet();
    private boolean restoreRouteMode;
    private final Paint selectedRoutePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private OnRouteEdgeTapListener routeEdgeTapListener;
    private final Paint removedRoutePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint routePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint motorwayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint motorwayIncompletePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint routeHaloPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint markerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint markerTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint attributionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint attributionBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint serviceStationPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint serviceStationOutlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private JSONArray serviceStations = new JSONArray();
    private Set<String> visitedServiceStationIds = Collections.emptySet();
    private final Paint messagePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final LruCache<String, Bitmap> tileBitmaps = new LruCache<>(48);
    private final Set<String> loadingTiles =
            Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    private final ScaleGestureDetector scaleDetector;
    private final GestureDetector gestureDetector;

    private double centerLongitude = -3.0;
    private double centerLatitude = 54.5;
    private double cameraZoom = 5.5;
    private double lastTouchX;
    private double lastTouchY;
    private boolean coordinatesValid;
    private boolean routeFitPending;
    private boolean flatRoadMapStyle;

    public RoutePreviewView(Context context, JSONArray coordinates) {
        this(context, coordinates, Collections.emptyList());
    }

    public RoutePreviewView(
            Context context, JSONArray coordinates, List<JSONArray> matchedSegments) {
        this(context, coordinates, matchedSegments, false, false, true);
    }

    public RoutePreviewView(
            Context context, List<JSONArray> routeSections, boolean interactive) {
        this(context, null, routeSections, interactive, false, false);
    }

    public interface OnRouteEdgeTapListener {
        void onRouteEdgesTap(Set<Integer> edgeIndices);
    }

    public RoutePreviewView(Context context, JSONArray coordinates,
                            List<JSONArray> matchedSegments, Set<Integer> removedEdges,
                            OnRouteEdgeTapListener listener) {
        this(context, coordinates, matchedSegments, true, false, true);
        this.routeEditMode = true;
        this.removedRouteEdges = removedEdges == null ? Collections.emptySet() : removedEdges;
        this.routeEdgeTapListener = listener;
        removedRoutePaint.setColor(Color.rgb(190, 55, 55));
        removedRoutePaint.setStyle(Paint.Style.STROKE);
        removedRoutePaint.setStrokeWidth(dp(4));
        removedRoutePaint.setStrokeCap(Paint.Cap.ROUND);
        removedRoutePaint.setStrokeJoin(Paint.Join.ROUND);
        removedRoutePaint.setPathEffect(new android.graphics.DashPathEffect(
                new float[]{dp(7), dp(5)}, 0));
    }

    public RoutePreviewView(Context context) {
        this(context, null, Collections.emptyList(), true, true, true);
    }

    private RoutePreviewView(
            Context context, JSONArray coordinates, List<JSONArray> matchedSegments,
            boolean interactive, boolean showEmptyMessage, boolean showEndpointMarkers) {
        super(context);
        this.coordinates = coordinates;
        this.matchedSegments = new ArrayList<>();
        if (matchedSegments != null) {
            for (JSONArray segment : matchedSegments) {
                if (hasRoutePoints(segment)) this.matchedSegments.add(segment);
            }
        }
        this.interactive = interactive;
        this.showEmptyMessage = showEmptyMessage;
        this.showEndpointMarkers = showEndpointMarkers;
        this.coordinatesValid = hasRoutePoints(coordinates) || !this.matchedSegments.isEmpty();
        this.routeFitPending = coordinatesValid;

        routePaint.setColor(Color.BLACK);
        routePaint.setStyle(Paint.Style.STROKE);
        routePaint.setStrokeWidth(dp(7));
        routePaint.setStrokeCap(Paint.Cap.ROUND);
        routePaint.setStrokeJoin(Paint.Join.ROUND);
        motorwayPaint.setColor(Color.rgb(0, 94, 184));
        motorwayPaint.setStyle(Paint.Style.STROKE);
        motorwayPaint.setStrokeWidth(dp(4));
        motorwayPaint.setStrokeCap(Paint.Cap.ROUND);
        motorwayPaint.setStrokeJoin(Paint.Join.ROUND);
        motorwayIncompletePaint.setColor(Color.rgb(217, 58, 58));
        motorwayIncompletePaint.setStyle(Paint.Style.STROKE);
        motorwayIncompletePaint.setStrokeWidth(dp(4));
        motorwayIncompletePaint.setStrokeCap(Paint.Cap.ROUND);
        motorwayIncompletePaint.setStrokeJoin(Paint.Join.ROUND);
        aRoadPaint.setColor(0xFF25834A);
        aRoadPaint.setStyle(Paint.Style.STROKE);
        aRoadPaint.setStrokeWidth(dp(4));
        aRoadPaint.setStrokeCap(Paint.Cap.ROUND);
        aRoadPaint.setStrokeJoin(Paint.Join.ROUND);
        aRoadIncompletePaint.setColor(Color.rgb(217, 58, 58));
        aRoadIncompletePaint.setStyle(Paint.Style.STROKE);
        aRoadIncompletePaint.setStrokeWidth(dp(4));
        aRoadIncompletePaint.setStrokeCap(Paint.Cap.ROUND);
        aRoadIncompletePaint.setStrokeJoin(Paint.Join.ROUND);
        settlementBoundaryPaint.setColor(Color.rgb(228, 87, 87));
        settlementBoundaryPaint.setStyle(Paint.Style.STROKE);
        settlementBoundaryPaint.setStrokeWidth(dp(3));
        settlementBoundaryPaint.setStrokeCap(Paint.Cap.ROUND);
        settlementBoundaryPaint.setStrokeJoin(Paint.Join.ROUND);
        routeHaloPaint.setColor(Color.WHITE);
        routeHaloPaint.setStyle(Paint.Style.STROKE);
        routeHaloPaint.setStrokeWidth(dp(10));
        routeHaloPaint.setStrokeCap(Paint.Cap.ROUND);
        routeHaloPaint.setStrokeJoin(Paint.Join.ROUND);
        selectedRoutePaint.setColor(Color.rgb(218, 55, 55));
        selectedRoutePaint.setStyle(Paint.Style.STROKE);
        selectedRoutePaint.setStrokeWidth(dp(12));
        selectedRoutePaint.setStrokeCap(Paint.Cap.ROUND);
        selectedRoutePaint.setStrokeJoin(Paint.Join.ROUND);
        markerTextPaint.setColor(Color.WHITE);
        markerTextPaint.setTextAlign(Paint.Align.CENTER);
        markerTextPaint.setTextSize(dp(11));
        markerTextPaint.setTypeface(android.graphics.Typeface.create(
                "sans-serif", android.graphics.Typeface.BOLD));
        serviceStationPaint.setStyle(Paint.Style.FILL);
        serviceStationOutlinePaint.setStyle(Paint.Style.FILL);
        serviceStationOutlinePaint.setColor(Color.WHITE);
        attributionPaint.setColor(Color.rgb(18, 37, 75));
        attributionPaint.setTextSize(dp(10));
        attributionPaint.setTypeface(android.graphics.Typeface.create(
                "sans-serif-medium", android.graphics.Typeface.NORMAL));
        attributionBackgroundPaint.setColor(0xEFFFFFFF);
        messagePaint.setColor(Color.rgb(35, 55, 86));
        messagePaint.setTextAlign(Paint.Align.CENTER);
        messagePaint.setTextSize(dp(13));
        setBackgroundColor(Color.rgb(239, 246, 250));
        setMinimumHeight(Math.round(dp(180)));

        scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                if (!RoutePreviewView.this.interactive) return false;
                zoomAt(detector.getScaleFactor(), detector.getFocusX(), detector.getFocusY());
                return true;
            }
        });
        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent event) {
                return true;
            }

            @Override
            public boolean onSingleTapUp(MotionEvent event) {
                if (RoutePreviewView.this.routeEditMode) {
                    tapNearestRouteEdge(event.getX(), event.getY());
                    return true;
                }
                return false;
            }

            @Override
            public boolean onDoubleTap(MotionEvent event) {
                if (!RoutePreviewView.this.interactive) return false;
                zoomAt(1.6, event.getX(), event.getY());
                return true;
            }
        });
    }

    /** Adds the local service-station catalogue as map pins. */
    public void setServiceStations(JSONArray stations, Set<String> visitedIds) {
        serviceStations = stations == null ? new JSONArray() : stations;
        visitedServiceStationIds = visitedIds == null
                ? Collections.emptySet() : new java.util.HashSet<>(visitedIds);
        if (serviceStations.length() > 0) {
            coordinatesValid = true;
            routeFitPending = true;
            fitRouteIfReady();
        }
        invalidate();
    }

    /** Adds the POC-style blue motorway evidence over the normal journey map. */
    public void setMotorwaySegments(List<JSONArray> segments) {
        motorwaySegments.clear();
        if (segments != null) {
            for (JSONArray segment : segments) {
                if (hasRoutePoints(segment)) motorwaySegments.add(segment);
            }
        }
        if (!motorwaySegments.isEmpty()) {
            coordinatesValid = true;
            routeFitPending = true;
            fitRouteIfReady();
        }
        invalidate();
    }

    public void setMotorwayCoverageSegments(List<JSONArray> incomplete, List<JSONArray> covered) {
        replaceSegments(incompleteMotorwaySegments, incomplete);
        replaceSegments(coveredMotorwaySegments, covered);
        if (!incompleteMotorwaySegments.isEmpty() || !coveredMotorwaySegments.isEmpty()) {
            coordinatesValid = true;
            routeFitPending = true;
            fitRouteIfReady();
        }
        invalidate();
    }

    public void setARoadCoverageSegments(List<JSONArray> incomplete, List<JSONArray> covered) {
        replaceSegments(incompleteARoadSegments, incomplete);
        replaceSegments(coveredARoadSegments, covered);
        if (!incompleteARoadSegments.isEmpty() || !coveredARoadSegments.isEmpty()) {
            coordinatesValid = true;
            routeFitPending = true;
            fitRouteIfReady();
        }
        invalidate();
    }

    public void setSettlementBoundary(org.json.JSONObject boundary) {
        settlementBoundaryRings.clear();
        collectSettlementRings(boundary);
        if (!settlementBoundaryRings.isEmpty()) {
            coordinatesValid = true;
            routeFitPending = true;
            fitRouteIfReady();
        }
        invalidate();
    }

    private void collectSettlementRings(org.json.JSONObject value) {
        if (value == null) return;
        String type = value.optString("type", "");
        if ("FeatureCollection".equals(type)) {
            org.json.JSONArray features = value.optJSONArray("features");
            if (features != null) for (int index = 0; index < features.length(); index++)
                collectSettlementRings(features.optJSONObject(index));
            return;
        }
        if ("Feature".equals(type)) {
            collectSettlementRings(value.optJSONObject("geometry"));
            return;
        }
        org.json.JSONArray coordinates = value.optJSONArray("coordinates");
        if ("Polygon".equals(type) && coordinates != null) {
            addBoundaryRing(coordinates.optJSONArray(0));
        } else if ("MultiPolygon".equals(type) && coordinates != null) {
            for (int index = 0; index < coordinates.length(); index++)
                addBoundaryRing(coordinates.optJSONArray(index) == null ? null
                        : coordinates.optJSONArray(index).optJSONArray(0));
        }
    }

    private void addBoundaryRing(JSONArray ring) {
        if (ring != null && ring.length() >= 4) settlementBoundaryRings.add(ring);
    }

    private void replaceSegments(List<JSONArray> target, List<JSONArray> source) {
        target.clear();
        if (source == null) return;
        for (JSONArray segment : source) {
            if (hasRoutePoints(segment)) target.add(segment);
        }
    }

    public void setFlatRoadMapStyle(boolean flat) {
        flatRoadMapStyle = flat;
        if (flat) routePaint.setStrokeWidth(dp(5));
        invalidate();
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        fitRouteIfReady();
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(Color.rgb(239, 246, 250));
        boolean hasTiles = drawTiles(canvas);

        if (coordinatesValid) {
            for (JSONArray ring : settlementBoundaryRings)
                drawRoute(canvas, ring, settlementBoundaryPaint, null);
            boolean hasMatchedRoute = !matchedSegments.isEmpty();
            JSONArray startEndRoute = hasRoutePoints(coordinates)
                    ? coordinates : hasMatchedRoute ? matchedSegments.get(0)
                    : firstMotorwayRoute();
            if (hasMatchedRoute) {
                if (routeEditMode) {
                    drawEditableRoutes(canvas);
                    for (JSONArray extension : routeExtensions) {
                        drawRoute(canvas, extension, routePaint,
                                flatRoadMapStyle ? null : routeHaloPaint);
                    }
                } else for (JSONArray segment : matchedSegments) {
                    drawRoute(canvas, segment, routePaint,
                            flatRoadMapStyle ? null : routeHaloPaint);
                }
            } else if (hasRoutePoints(coordinates)) {
                drawRoute(canvas, coordinates, routePaint,
                        flatRoadMapStyle ? null : routeHaloPaint);
            }
            for (JSONArray segment : incompleteMotorwaySegments) {
                drawRoute(canvas, segment, motorwayIncompletePaint, null);
            }
            for (JSONArray segment : incompleteARoadSegments) {
                drawRoute(canvas, segment, aRoadIncompletePaint, null);
            }
            for (JSONArray segment : coveredARoadSegments) {
                drawRoute(canvas, segment, aRoadPaint, null);
            }
            for (JSONArray segment : coveredMotorwaySegments) {
                drawRoute(canvas, segment, motorwayPaint, null);
            }
            for (JSONArray segment : motorwaySegments) {
                drawRoute(canvas, segment, motorwayPaint, null);
            }
            if (showEndpointMarkers) {
                JSONArray endRoute = hasRoutePoints(coordinates)
                        ? coordinates : hasMatchedRoute
                                ? matchedSegments.get(matchedSegments.size() - 1)
                                : lastMotorwayRoute();
                drawEndpoint(canvas, startEndRoute, 0, Color.rgb(35, 140, 75), "S");
                drawEndpoint(canvas, endRoute, endRoute.length() - 1,
                        Color.rgb(190, 55, 55), "E");
            }
        } else if (showEmptyMessage) {
            drawEmptyMessage(canvas);
        } else {
            drawMessage(canvas, "Not enough GPS points for a route preview");
        }

        drawServiceStations(canvas);
        drawAttribution(canvas);
        if (!hasTiles) drawMessage(canvas, coordinatesValid
                ? "Map tiles unavailable · showing route only"
                : "Map tiles unavailable · check connection");
    }

    private void fitRouteIfReady() {
        if (!routeFitPending || getWidth() <= 0 || getHeight() <= 0) return;
        double minX = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        try {
            // Local settlement views are framed by their boundary. Matched
            // roads may continue far beyond town limits and must not zoom out
            // the map just to include those off-screen ends.
            List<JSONArray> fitRoutes = new ArrayList<>();
            if (!settlementBoundaryRings.isEmpty()) {
                fitRoutes.addAll(settlementBoundaryRings);
            } else {
                fitRoutes.addAll(matchedSegments);
                fitRoutes.addAll(routeExtensions);
                if (hasRoutePoints(coordinates)) fitRoutes.add(coordinates);
                fitRoutes.addAll(motorwaySegments);
                fitRoutes.addAll(incompleteMotorwaySegments);
                fitRoutes.addAll(coveredMotorwaySegments);
                fitRoutes.addAll(incompleteARoadSegments);
                fitRoutes.addAll(coveredARoadSegments);
            }
            for (JSONArray routeCoordinates : fitRoutes) {
                for (int index = 0; index < routeCoordinates.length(); index++) {
                    org.json.JSONArray point = routeCoordinates.getJSONArray(index);
                    double lon = point.getDouble(0);
                    double lat = clampLatitude(point.getDouble(1));
                    minX = Math.min(minX, longitudeToUnitX(lon));
                    maxX = Math.max(maxX, longitudeToUnitX(lon));
                    minY = Math.min(minY, latitudeToUnitY(lat));
                    maxY = Math.max(maxY, latitudeToUnitY(lat));
                }
            }
            if (settlementBoundaryRings.isEmpty()) {
                for (int i = 0; i < serviceStations.length(); i++) {
                    org.json.JSONObject station = serviceStations.optJSONObject(i);
                    if (station == null) continue;
                    double lon = station.optDouble("lng", Double.NaN);
                    double lat = station.optDouble("lat", Double.NaN);
                    if (!Double.isFinite(lon) || !Double.isFinite(lat)) continue;
                    minX = Math.min(minX, longitudeToUnitX(lon));
                    maxX = Math.max(maxX, longitudeToUnitX(lon));
                    minY = Math.min(minY, latitudeToUnitY(lat));
                    maxY = Math.max(maxY, latitudeToUnitY(lat));
                }
            }
            double spanX = Math.max(0.000001, maxX - minX);
            double spanY = Math.max(0.000001, maxY - minY);
            double availableWidth = Math.max(1, getWidth() - dp(72));
            double availableHeight = Math.max(1, getHeight() - dp(72));
            double zoomX = log2(availableWidth / (spanX * mapTileSize()));
            double zoomY = log2(availableHeight / (spanY * mapTileSize()));
            cameraZoom = clampZoom(Math.min(MAX_ZOOM, Math.min(zoomX, zoomY)));
            centerLongitude = ((minX + maxX) / 2.0) * 360.0 - 180.0;
            centerLatitude = unitYToLatitude((minY + maxY) / 2.0);
            routeFitPending = false;
        } catch (Exception ignored) {
            coordinatesValid = false;
            routeFitPending = false;
        }
    }

    private JSONArray firstMotorwayRoute() {
        if (!motorwaySegments.isEmpty()) return motorwaySegments.get(0);
        if (!coveredMotorwaySegments.isEmpty()) return coveredMotorwaySegments.get(0);
        if (!incompleteMotorwaySegments.isEmpty()) return incompleteMotorwaySegments.get(0);
        if (!coveredARoadSegments.isEmpty()) return coveredARoadSegments.get(0);
        return incompleteARoadSegments.get(0);
    }

    private JSONArray lastMotorwayRoute() {
        if (!motorwaySegments.isEmpty()) return motorwaySegments.get(motorwaySegments.size() - 1);
        if (!coveredMotorwaySegments.isEmpty()) {
            return coveredMotorwaySegments.get(coveredMotorwaySegments.size() - 1);
        }
        if (!incompleteMotorwaySegments.isEmpty()) return incompleteMotorwaySegments.get(incompleteMotorwaySegments.size() - 1);
        if (!coveredARoadSegments.isEmpty()) return coveredARoadSegments.get(coveredARoadSegments.size() - 1);
        return incompleteARoadSegments.get(incompleteARoadSegments.size() - 1);
    }

    private boolean drawTiles(Canvas canvas) {
        int tileZoom = tileZoom();
        double scale = Math.pow(2.0, cameraZoom - tileZoom);
        double tilePixels = mapTileSize();
        double tileScreenSize = tilePixels * scale;
        double worldSize = tilePixels * Math.pow(2.0, cameraZoom);
        double centerX = longitudeToUnitX(centerLongitude) * worldSize;
        double centerY = latitudeToUnitY(centerLatitude) * worldSize;
        int tileCount = 1 << tileZoom;
        int firstX = (int) Math.floor((centerX - getWidth() / 2.0) / tileScreenSize);
        int lastX = (int) Math.floor((centerX + getWidth() / 2.0) / tileScreenSize);
        int firstY = (int) Math.floor((centerY - getHeight() / 2.0) / tileScreenSize);
        int lastY = (int) Math.floor((centerY + getHeight() / 2.0) / tileScreenSize);
        boolean drawn = false;

        for (int y = firstY; y <= lastY; y++) {
            if (y < 0 || y >= tileCount) continue;
            for (int x = firstX; x <= lastX; x++) {
                int wrappedX = ((x % tileCount) + tileCount) % tileCount;
                String key = tileZoom + "/" + wrappedX + "/" + y;
                Bitmap bitmap = tileBitmaps.get(key);
                if (bitmap == null || bitmap.isRecycled()) {
                    requestTile(tileZoom, wrappedX, y, key);
                    continue;
                }
                float left = (float) (getWidth() / 2.0 + x * tileScreenSize - centerX);
                float top = (float) (getHeight() / 2.0 + y * tileScreenSize - centerY);
                canvas.drawBitmap(bitmap, null, new RectF(left, top,
                        left + (float) tileScreenSize, top + (float) tileScreenSize), null);
                drawn = true;
            }
        }
        return drawn;
    }

    public void setRouteExtensions(List<JSONArray> extensions) {
        routeExtensions.clear();
        if (extensions != null) {
            for (JSONArray route : extensions) if (hasRoutePoints(route)) routeExtensions.add(route);
        }
        routeFitPending = true;
        fitRouteIfReady();
        invalidate();
    }

    public void setRouteEditState(Set<Integer> removedEdges, Set<Integer> selectedEdges,
                                   boolean restoreMode) {
        this.removedRouteEdges = removedEdges == null ? Collections.emptySet() : removedEdges;
        this.selectedRouteEdges = selectedEdges == null ? Collections.emptySet() : selectedEdges;
        this.restoreRouteMode = restoreMode;
        invalidate();
    }

    private void drawEditableRoutes(Canvas canvas) {
        int edgeIndex = 0;
        for (JSONArray route : matchedSegments) {
            Path activePath = new Path();
            boolean hasActivePath = false;
            for (int index = 1; index < route.length(); index++, edgeIndex++) {
                try {
                    JSONArray first = route.getJSONArray(index - 1);
                    JSONArray second = route.getJSONArray(index);
                    float x1 = screenX(first.getDouble(0));
                    float y1 = screenY(first.getDouble(1));
                    float x2 = screenX(second.getDouble(0));
                    float y2 = screenY(second.getDouble(1));
                    boolean removed = removedRouteEdges.contains(edgeIndex);
                    boolean selected = selectedRouteEdges.contains(edgeIndex);
                    if (removed || selected) {
                        if (hasActivePath) {
                            canvas.drawPath(activePath, routeHaloPaint);
                            canvas.drawPath(activePath, routePaint);
                            activePath = new Path();
                            hasActivePath = false;
                        }
                        Path edge = new Path();
                        edge.moveTo(x1, y1);
                        edge.lineTo(x2, y2);
                        if (selected) {
                            canvas.drawPath(edge, routeHaloPaint);
                            canvas.drawPath(edge, selectedRoutePaint);
                            float markerRadius = dp(5);
                            markerPaint.setColor(Color.rgb(218, 55, 55));
                            canvas.drawCircle((x1 + x2) / 2f, (y1 + y2) / 2f,
                                    markerRadius, markerPaint);
                        } else if (restoreRouteMode) {
                            canvas.drawPath(edge, removedRoutePaint);
                        }
                    } else {
                        if (!hasActivePath) {
                            activePath.moveTo(x1, y1);
                            hasActivePath = true;
                        }
                        activePath.lineTo(x2, y2);
                    }
                } catch (Exception ignored) {
                    if (hasActivePath) {
                        canvas.drawPath(activePath, routeHaloPaint);
                        canvas.drawPath(activePath, routePaint);
                        activePath = new Path();
                        hasActivePath = false;
                    }
                }
            }
            if (hasActivePath) {
                canvas.drawPath(activePath, routeHaloPaint);
                canvas.drawPath(activePath, routePaint);
            }
        }
    }

    private void tapNearestRouteEdge(float tapX, float tapY) {
        int nearestIndex = -1;
        float nearestDistance = Float.MAX_VALUE;
        int edgeIndex = 0;
        for (JSONArray route : matchedSegments) {
            for (int index = 1; index < route.length(); index++, edgeIndex++) {
                float distance = editableEdgeDistance(route, index, tapX, tapY);
                if (Float.isFinite(distance)
                        && removedRouteEdges.contains(edgeIndex) == restoreRouteMode
                        && distance < nearestDistance) {
                    nearestDistance = distance;
                    nearestIndex = edgeIndex;
                }
            }
        }
        // Keep selection precise: unmatched endpoint traces and nearby roads may be
        // visible, but they are not editable matched sections. Ignore taps that are
        // too far from a matched edge instead of snapping to another line.
        if (nearestIndex < 0 || nearestDistance > dp(32) || routeEdgeTapListener == null) return;

        // Select only truly coincident strokes. A narrow tolerance preserves the
        // one-tap stacked-line removal without catching a neighbouring road below.
        float stackTolerance = dp(4);
        float stackLimit = nearestDistance + stackTolerance;
        Set<Integer> selected = new java.util.LinkedHashSet<>();
        edgeIndex = 0;
        for (JSONArray route : matchedSegments) {
            for (int index = 1; index < route.length(); index++, edgeIndex++) {
                float distance = editableEdgeDistance(route, index, tapX, tapY);
                if (Float.isFinite(distance)
                        && removedRouteEdges.contains(edgeIndex) == restoreRouteMode
                        && distance <= stackLimit) {
                    selected.add(edgeIndex);
                }
            }
        }
        if (!selected.isEmpty()) {
            routeEdgeTapListener.onRouteEdgesTap(selected);
            invalidate();
        }
    }

    private float editableEdgeDistance(JSONArray route, int index, float tapX, float tapY) {
        try {
            JSONArray first = route.getJSONArray(index - 1);
            JSONArray second = route.getJSONArray(index);
            float x1 = screenX(first.getDouble(0));
            float y1 = screenY(first.getDouble(1));
            float x2 = screenX(second.getDouble(0));
            float y2 = screenY(second.getDouble(1));
            float dx = x2 - x1;
            float dy = y2 - y1;
            float lengthSquared = dx * dx + dy * dy;
            float amount = lengthSquared == 0 ? 0 : Math.max(0, Math.min(1,
                    ((tapX - x1) * dx + (tapY - y1) * dy) / lengthSquared));
            return (float) Math.hypot(tapX - (x1 + amount * dx), tapY - (y1 + amount * dy));
        } catch (Exception ignored) {
            return Float.NaN;
        }
    }

    private static final class ServiceStationCluster {
        float xTotal;
        float yTotal;
        int count;
        int visitedCount;

        void add(float x, float y, boolean visited) {
            xTotal += x;
            yTotal += y;
            count++;
            if (visited) visitedCount++;
        }

        float centerX() { return xTotal / count; }
        float centerY() { return yTotal / count; }
    }

    private void drawServiceStations(Canvas canvas) {
        float singleRadius = dp(6);
        float cellSize = dp(44);
        Map<Long, ServiceStationCluster> clusters = new LinkedHashMap<>();
        for (int i = 0; i < serviceStations.length(); i++) {
            org.json.JSONObject station = serviceStations.optJSONObject(i);
            if (station == null) continue;
            double lat = station.optDouble("lat", Double.NaN);
            double lng = station.optDouble("lng", Double.NaN);
            if (!Double.isFinite(lat) || !Double.isFinite(lng)) continue;
            float x = screenX(lng);
            float y = screenY(lat);
            if (x < -cellSize || x > getWidth() + cellSize
                    || y < -cellSize || y > getHeight() + cellSize) continue;
            int column = (int) Math.floor(x / cellSize);
            int row = (int) Math.floor(y / cellSize);
            long key = (((long) column) << 32) ^ (row & 0xffffffffL);
            ServiceStationCluster cluster = clusters.get(key);
            if (cluster == null) {
                cluster = new ServiceStationCluster();
                clusters.put(key, cluster);
            }
            String id = station.optString("id", "");
            cluster.add(x, y, visitedServiceStationIds.contains(id));
        }

        float originalTextSize = markerTextPaint.getTextSize();
        int originalTextColor = markerTextPaint.getColor();
        markerTextPaint.setTextSize(dp(10));
        markerTextPaint.setTextAlign(Paint.Align.CENTER);
        for (ServiceStationCluster cluster : clusters.values()) {
            float x = cluster.centerX();
            float y = cluster.centerY();
            boolean visited = cluster.visitedCount > 0;
            serviceStationPaint.setColor(visited ? 0xFFF7C450 : 0xFF74829A);
            if (cluster.count > 1) {
                float radius = dp(12);
                canvas.drawCircle(x, y, radius + dp(1.5f), serviceStationOutlinePaint);
                canvas.drawCircle(x, y, radius, serviceStationPaint);
                markerTextPaint.setColor(Color.WHITE);
                String label = cluster.count > 99 ? "99+" : String.valueOf(cluster.count);
                canvas.drawText(label, x,
                        y - (markerTextPaint.ascent() + markerTextPaint.descent()) / 2,
                        markerTextPaint);
            } else {
                canvas.drawCircle(x, y, singleRadius + dp(1.5f), serviceStationOutlinePaint);
                canvas.drawCircle(x, y, singleRadius, serviceStationPaint);
                markerPaint.setColor(Color.WHITE);
                canvas.drawCircle(x, y, dp(1.7f), markerPaint);
            }
        }
        markerTextPaint.setTextSize(originalTextSize);
        markerTextPaint.setColor(originalTextColor);
    }

    private void drawRoute(Canvas canvas, JSONArray coordinates, Paint paint, Paint haloPaint) {
        try {
            Path route = new Path();
            for (int index = 0; index < coordinates.length(); index++) {
                org.json.JSONArray point = coordinates.getJSONArray(index);
                float x = screenX(point.getDouble(0));
                float y = screenY(point.getDouble(1));
                if (index == 0) route.moveTo(x, y);
                else route.lineTo(x, y);
            }
            if (haloPaint != null) canvas.drawPath(route, haloPaint);
            canvas.drawPath(route, paint);
        } catch (Exception ignored) {
            drawMessage(canvas, "Route preview unavailable");
        }
    }

    private boolean hasRoutePoints(JSONArray points) {
        return points != null && points.length() >= 2;
    }

    private void drawEndpoint(
            Canvas canvas, JSONArray coordinates, int index, int color, String label) {
        try {
            org.json.JSONArray point = coordinates.getJSONArray(index);
            float x = screenX(point.getDouble(0));
            float y = screenY(point.getDouble(1));
            float radius = dp(12);
            markerPaint.setColor(Color.WHITE);
            canvas.drawCircle(x, y, radius + dp(2), markerPaint);
            markerPaint.setColor(color);
            canvas.drawCircle(x, y, radius, markerPaint);
            canvas.drawText(label, x,
                    y - (markerTextPaint.ascent() + markerTextPaint.descent()) / 2,
                    markerTextPaint);
        } catch (Exception ignored) {
            // The route still renders if an endpoint is malformed.
        }
    }

    private void drawEmptyMessage(Canvas canvas) {
        String title = "Your map is ready to explore";
        String subtitle = "Travelled roads will appear here as journeys are matched.";
        float centerY = getHeight() * 0.76f;
        Paint background = new Paint(Paint.ANTI_ALIAS_FLAG);
        background.setColor(0xEFFFFFFF);
        RectF box = new RectF(dp(16), centerY - dp(40),
                getWidth() - dp(16), centerY + dp(40));
        canvas.drawRoundRect(box, dp(12), dp(12), background);
        Paint titlePaint = new Paint(messagePaint);
        titlePaint.setTypeface(android.graphics.Typeface.create(
                "sans-serif", android.graphics.Typeface.BOLD));
        titlePaint.setTextSize(dp(14));
        canvas.drawText(title, getWidth() / 2f, centerY - dp(6), titlePaint);
        messagePaint.setTextSize(dp(11));
        canvas.drawText(subtitle, getWidth() / 2f, centerY + dp(15), messagePaint);
        messagePaint.setTextSize(dp(13));
    }

    private void drawMessage(Canvas canvas, String message) {
        Paint background = new Paint(Paint.ANTI_ALIAS_FLAG);
        background.setColor(0xEFFFFFFF);
        float textWidth = messagePaint.measureText(message);
        float horizontalPadding = dp(10);
        float centerX = getWidth() / 2f;
        float centerY = getHeight() / 2f;
        canvas.drawRoundRect(new RectF(centerX - textWidth / 2f - horizontalPadding,
                centerY - dp(18), centerX + textWidth / 2f + horizontalPadding,
                centerY + dp(8)), dp(6), dp(6), background);
        canvas.drawText(message, centerX, centerY, messagePaint);
    }

    private void drawAttribution(Canvas canvas) {
        String text = "© OpenStreetMap contributors";
        float paddingX = dp(5);
        float paddingY = dp(3);
        float width = attributionPaint.measureText(text);
        float right = getWidth() - dp(6);
        float bottom = getHeight() - dp(6);
        RectF background = new RectF(right - width - paddingX * 2,
                bottom - dp(17) - paddingY, right, bottom);
        canvas.drawRoundRect(background, dp(3), dp(3), attributionBackgroundPaint);
        canvas.drawText(text, background.left + paddingX,
                background.bottom - paddingY - attributionPaint.descent(), attributionPaint);
    }

    private void requestTile(int tileZoom, int tileX, int tileY, String key) {
        if (!loadingTiles.add(key)) return;
        TILE_EXECUTOR.execute(() -> {
            try {
                File tile = tileFile(key, ".png");
                File expiryFile = tileFile(key, ".expiry");
                Bitmap cached = tile.exists()
                        ? BitmapFactory.decodeFile(tile.getAbsolutePath()) : null;
                if (cached != null) {
                    tileBitmaps.put(key, cached);
                    MAIN_HANDLER.post(this::invalidate);
                }
                if (cached != null && readExpiry(expiryFile) > System.currentTimeMillis()) return;
                fetchTile(tileZoom, tileX, tileY, tile, expiryFile);
                Bitmap fresh = tile.exists()
                        ? BitmapFactory.decodeFile(tile.getAbsolutePath()) : null;
                if (fresh != null) tileBitmaps.put(key, fresh);
            } finally {
                loadingTiles.remove(key);
                MAIN_HANDLER.post(this::invalidate);
            }
        });
    }

    private void fetchTile(
            int tileZoom, int tileX, int tileY, File tile, File expiryFile) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL("https://tile.openstreetmap.org/"
                    + tileZoom + "/" + tileX + "/" + tileY + ".png");
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(7000);
            connection.setReadTimeout(7000);
            connection.setRequestProperty("User-Agent",
                    "Roadprints/" + BuildConfig.VERSION_NAME
                            + " (https://github.com/adamdbird-gfc/uk-road-tracker)");
            connection.setRequestProperty("Accept", "image/png");
            File etagFile = tileFile(tile.getName(), ".etag");
            File modifiedFile = tileFile(tile.getName(), ".modified");
            String etag = readText(etagFile);
            String lastModified = readText(modifiedFile);
            if (etag.length() > 0) connection.setRequestProperty("If-None-Match", etag);
            if (lastModified.length() > 0) {
                connection.setRequestProperty("If-Modified-Since", lastModified);
            }
            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_NOT_MODIFIED) {
                writeExpiry(expiryFile, responseExpiry(connection));
                return;
            }
            if (responseCode != HttpURLConnection.HTTP_OK) return;

            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (InputStream input = connection.getInputStream()) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) bytes.write(buffer, 0, count);
            }
            File directory = tile.getParentFile();
            if (directory != null && !directory.exists() && !directory.mkdirs()) return;
            File temporary = new File(directory, tile.getName() + ".tmp");
            try (FileOutputStream output = new FileOutputStream(temporary)) {
                output.write(bytes.toByteArray());
            }
            if (tile.exists()) tile.delete();
            if (!temporary.renameTo(tile)) temporary.delete();

            writeExpiry(expiryFile, responseExpiry(connection));
            writeText(etagFile, connection.getHeaderField("ETag"));
            writeText(modifiedFile, connection.getHeaderField("Last-Modified"));
        } catch (Exception ignored) {
            // Cached tiles and the journey route remain available if the network is offline.
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private long responseExpiry(HttpURLConnection connection) {
        long now = System.currentTimeMillis();
        String cacheControl = connection.getHeaderField("Cache-Control");
        if (cacheControl != null) {
            Matcher matcher = Pattern.compile("max-age=(\\d+)", Pattern.CASE_INSENSITIVE)
                    .matcher(cacheControl);
            if (matcher.find()) {
                try {
                    return now + Long.parseLong(matcher.group(1)) * 1000L;
                } catch (Exception ignored) {
                    // Fall back to Expires or a modest local cache duration.
                }
            }
        }
        long expires = connection.getExpiration();
        return expires > now ? expires : now + DEFAULT_TILE_TTL_MS;
    }

    private void writeExpiry(File file, long expiry) {
        writeText(file, String.valueOf(expiry));
    }

    private void writeText(File file, String value) {
        if (value == null || value.length() == 0) return;
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(value.getBytes("UTF-8"));
        } catch (Exception ignored) { }
    }

    private String readText(File file) {
        if (!file.exists()) return "";
        try (InputStream input = new java.io.FileInputStream(file)) {
            byte[] data = new byte[(int) Math.min(file.length(), 2048)];
            int length = input.read(data);
            if (length <= 0) return "";
            return new String(data, 0, length, "UTF-8");
        } catch (Exception ignored) {
            return "";
        }
    }

    private long readExpiry(File file) {
        if (!file.exists()) return 0;
        try (InputStream input = new java.io.FileInputStream(file)) {
            byte[] data = new byte[(int) Math.min(file.length(), 32)];
            int length = input.read(data);
            if (length <= 0) return 0;
            return Long.parseLong(new String(data, 0, length, "UTF-8"));
        } catch (Exception ignored) {
            return 0;
        }
    }

    private File tileFile(String key, String extension) {
        File directory = new File(getContext().getCacheDir(), "roadprints_osm_tiles");
        if (!directory.exists()) directory.mkdirs();
        return new File(directory, key.replace('/', '_') + extension);
    }

    public void zoomIn() {
        zoomAt(1.5, getWidth() / 2f, getHeight() / 2f);
    }

    public void zoomOut() {
        zoomAt(1.0 / 1.5, getWidth() / 2f, getHeight() / 2f);
    }

    private void zoomAt(double factor, float focusX, float focusY) {
        double oldZoom = cameraZoom;
        double oldWorld = mapTileSize() * Math.pow(2.0, oldZoom);
        double focusUnitX = longitudeToUnitX(centerLongitude)
                + (focusX - getWidth() / 2.0) / oldWorld;
        double focusUnitY = latitudeToUnitY(centerLatitude)
                + (focusY - getHeight() / 2.0) / oldWorld;
        cameraZoom = clampZoom(cameraZoom + log2(factor));
        double newWorld = mapTileSize() * Math.pow(2.0, cameraZoom);
        centerLongitude = clampUnitX(focusUnitX
                - (focusX - getWidth() / 2.0) / newWorld) * 360.0 - 180.0;
        centerLatitude = unitYToLatitude(clampUnitY(focusUnitY
                - (focusY - getHeight() / 2.0) / newWorld));
        invalidate();
    }

    private float screenX(double longitude) {
        double world = mapTileSize() * Math.pow(2.0, cameraZoom);
        return (float) (getWidth() / 2.0
                + (longitudeToUnitX(longitude) - longitudeToUnitX(centerLongitude)) * world);
    }

    private float screenY(double latitude) {
        double world = mapTileSize() * Math.pow(2.0, cameraZoom);
        return (float) (getHeight() / 2.0
                + (latitudeToUnitY(clampLatitude(latitude))
                - latitudeToUnitY(centerLatitude)) * world);
    }

    private double longitudeToUnitX(double longitude) {
        return (longitude + 180.0) / 360.0;
    }

    private double latitudeToUnitY(double latitude) {
        double radians = Math.toRadians(clampLatitude(latitude));
        return (1.0 - Math.log(Math.tan(radians) + 1.0 / Math.cos(radians))
                / Math.PI) / 2.0;
    }

    private double unitYToLatitude(double unitY) {
        double y = clampUnitY(unitY);
        return Math.toDegrees(Math.atan(Math.sinh(Math.PI * (1.0 - 2.0 * y))));
    }

    private double clampLatitude(double latitude) {
        return Math.max(-85.05112878, Math.min(85.05112878, latitude));
    }

    private double clampUnitX(double x) {
        if (x < 0) return x + 1.0;
        if (x > 1) return x - 1.0;
        return x;
    }

    private double clampUnitY(double y) {
        return Math.max(0.0, Math.min(1.0, y));
    }

    private double mapTileSize() { return dp(TILE_SIZE); }

    private int tileZoom() {
        return Math.max(0, Math.min(MAX_ZOOM, (int) Math.floor(cameraZoom)));
    }

    private double clampZoom(double value) {
        return Math.max(2.0, Math.min(MAX_ZOOM, value));
    }

    private double log2(double value) {
        return Math.log(value) / Math.log(2.0);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!interactive) return false;
        scaleDetector.onTouchEvent(event);
        gestureDetector.onTouchEvent(event);

        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            lastTouchX = event.getX();
            lastTouchY = event.getY();
            return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_MOVE
                && !scaleDetector.isInProgress()) {
            double scale = Math.pow(2.0, cameraZoom);
            double tilePixels = mapTileSize();
            double centerX = longitudeToUnitX(centerLongitude) * tilePixels * scale;
            double centerY = latitudeToUnitY(centerLatitude) * tilePixels * scale;
            centerX -= event.getX() - lastTouchX;
            centerY -= event.getY() - lastTouchY;
            centerLongitude = clampUnitX(centerX / (tilePixels * scale)) * 360.0 - 180.0;
            centerLatitude = unitYToLatitude(centerY / (tilePixels * scale));
            lastTouchX = event.getX();
            lastTouchY = event.getY();
            invalidate();
            return true;
        }
        return true;
    }
}
