package com.roadprints.capture;

import android.app.Activity;
import android.content.Context;
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
import android.widget.ProgressBar;
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
    private TextView mapSubtitle, legendButton;
    private JSONArray collectionServiceVenues=new JSONArray();
    private Set<String> collectionServiceVisits=java.util.Collections.emptySet();
    private MapLoadingView loadingCard;
    private GrowingStatusControl mapStatus;
    private boolean skipDiskMapCache,forceMapReload;
    private FrameLayout mapFrame;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private volatile int mapLoadGeneration;
    private Map<String, Double> roadPercentages = new HashMap<>();
    private List<RoadCoverageSegment> incompleteRoadSegments = new ArrayList<>();
    private RoutePreviewView mapView;
    private JourneyFilters mapFilters = JourneyFilters.all();
    private TextView mapFilterSummary;
    private View mapFilterControls, zoomControls;
    private boolean mapReady, recapVisible, mapLoadFailed, hasDisplayedMap;
    private JourneyFilters displayedFilters = JourneyFilters.all();
    private java.util.concurrent.Future<?> mapPending;
    private Runnable mapDeadline;
    private long mapStartedAt;
    private View mapRecovery;
    static final long MAP_TIMEOUT_MS = 120_000; // Cold archive/reference preparation can legitimately exceed 30 seconds.
    interface MapRouteLoader {
        MapRoutes load(MapActivity screen, JourneyFilters selection, java.time.LocalDate today, java.time.ZoneId zone) throws Exception;
    }
    static MapRouteLoader mapRouteLoader = MapActivity::readMapRoutes;
    private static String filterKey(JourneyFilters f) {return f.mode+"|"+f.status+"|"+f.date.preset+"|"+f.date.from+"|"+f.date.to;}
    private boolean sameFilters(JourneyFilters a, JourneyFilters b) {return filterKey(a).equals(filterKey(b));}
    private android.app.AlertDialog filterDialog;
    private int filterDialogGeneration;
    private static final String FILTER_PREFS = "roadprints_map_journey_filters";
    private long displayedMapRevision = Long.MIN_VALUE;
    private double savedCameraLongitude, savedCameraLatitude, savedCameraZoom;
    private boolean hasSavedCamera;
    private long loadingMapRevision = Long.MIN_VALUE;
    private boolean recapLoading, replayPreparing, active;
    private Object ncnSnapshot;private String ncnMode;
    private int recapGeneration;
    private final java.util.concurrent.ExecutorService recapIo = java.util.concurrent.Executors.newSingleThreadExecutor();
    private View recapCard;
    private TextView replayMessage;
    private List<JSONObject> recapJourneys = new ArrayList<>();
    private Set<String> replayIds = new HashSet<>();
    private double[] beforeReplayCamera;
    private final Runnable observeChanges = new Runnable() {
        @Override public void run() {
            if (!active || getIntent().hasExtra("settlement_code")) return;
            if (!replayPreparing && replayMessage == null && displayedMapRevision != mapDataRevision()) refreshMap();
            refreshRecap();
            if(CollectionAccessStore.NCN.equals(CollectionAccessStore.selected(MapActivity.this))&&(ncnSnapshot!=NcnProgressStore.current()||!java.util.Objects.equals(ncnMode,NcnProgressStore.mode(MapActivity.this)))){ncnSnapshot=NcnProgressStore.current();ncnMode=NcnProgressStore.mode(MapActivity.this);NcnMapOverlay.apply(MapActivity.this,mapView);}
            updateMapStatus();
            mainHandler.postDelayed(this, 3000);
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        android.content.SharedPreferences selection = getSharedPreferences(FILTER_PREFS, MODE_PRIVATE);
        mapFilters = new JourneyFilters(selection.getInt("mode", 0), selection.getString("status", "all"),
                JourneyDateFilter.restore(selection.getString("date", "all"), selection.getString("from", null), selection.getString("to", null)));
        getWindow().getDecorView().post(()->ContextConsent.maybeExplain(this));
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        androidx.core.view.WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView())
                .setAppearanceLightStatusBars(false);
        androidx.core.view.WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView())
                .setAppearanceLightNavigationBars(false);
        getWindow().setStatusBarColor(NAVY);
        getWindow().setNavigationBarColor(NAV_BAR);
        if (state != null && state.containsKey("map_camera_longitude")) {
            savedCameraLongitude = state.getDouble("map_camera_longitude");
            savedCameraLatitude = state.getDouble("map_camera_latitude");
            savedCameraZoom = state.getDouble("map_camera_zoom");
            hasSavedCamera = true;
        }

        if (!hasSavedCamera && !getIntent().hasExtra("settlement_code")) {
            android.content.SharedPreferences camera = getSharedPreferences("roadprints_map_camera", MODE_PRIVATE);
            if (camera.contains("longitude")) {
                savedCameraLongitude = Double.longBitsToDouble(camera.getLong("longitude", 0));
                savedCameraLatitude = Double.longBitsToDouble(camera.getLong("latitude", 0));
                savedCameraZoom = Double.longBitsToDouble(camera.getLong("zoom", 0));
                hasSavedCamera = true;
            }
        }
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(NAVY);

        LinearLayout heading = new LinearLayout(this);
        heading.setOrientation(LinearLayout.VERTICAL);
        heading.setPadding(dp(18), dp(18), dp(18), dp(14));

        LinearLayout brand = RoadprintsHeader.create(this);
        brand.setPadding(0, 0, 0, dp(6));
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


        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        title.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
        titleRow.addView(title);
        String settlementCode = getIntent().getStringExtra("settlement_code");
        if (settlementCode != null && !settlementCode.isEmpty()) {
            TextView close = RoadprintsDialog.closeControl(this,this::finish);
            titleRow.addView(close);
        }
                if (getIntent().hasExtra("settlement_code")) heading.addView(titleRow);
        heading.addView(mapSubtitle);
        root.addView(heading);

        mapFrame = new FrameLayout(this);
        mapFrame.setBackgroundColor(NAVY);
        root.addView(mapFrame, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        mapView = RoutePreviewView.overview(this);
        mapView.setFlatRoadMapStyle(true);
        mapView.setContentDescription("Interactive OpenStreetMap. Pinch to zoom and drag to move.");
        mapFrame.addView(mapView, new FrameLayout.LayoutParams(-1, -1));
        if (hasSavedCamera) mapView.restoreCameraState(
                savedCameraLongitude, savedCameraLatitude, savedCameraZoom);
        addZoomControls(mapFrame, mapView);
        legendButton=new TextView(this);legendButton.setText("☷ Legend");legendButton.setTextSize(14);legendButton.setTextColor(NAVY);
        legendButton.setTypeface(null,Typeface.BOLD);legendButton.setGravity(Gravity.CENTER);legendButton.setMinHeight(dp(48));
        legendButton.setPadding(dp(14),dp(10),dp(14),dp(10));legendButton.setBackground(roundRect(GOLD,dp(14)));legendButton.setTag("map_legend_button");
        legendButton.setContentDescription("Legend, map layer visibility and collection selection");
        legendButton.setOnClickListener(v->MapLegendPanel.show(this,getIntent().hasExtra("settlement_code"),this::applyCollectionLayer));
        FrameLayout.LayoutParams legendParams=new FrameLayout.LayoutParams(-2,-2,Gravity.START|Gravity.BOTTOM);
        legendParams.setMargins(dp(12),dp(12),dp(12),dp(12));mapFrame.addView(legendButton,legendParams);
        if (!getIntent().hasExtra("settlement_code")) addMapFilters();
        mapView.setMapLayersFromPreferences();
        showMapLoading(false);

        View bottomNavigation = buildBottomNavigation();
        mapStatus=(GrowingStatusControl)GrowingStatusControl.create(this);
        root.addView(mapStatus);
        mapStatus.setScreenStatus("Loading map…");
        root.addView(bottomNavigation);
        setContentView(root);
        applySystemBarInsets(root, heading, bottomNavigation);
    }

    @Override
    protected void onResume() {
        super.onResume();
        active = true;
        if (replayMessage == null && mapFrame != null && mapSubtitle != null) refreshMap();
        String requestedCollection=getIntent().getStringExtra("collection_id");
        if(requestedCollection!=null){CollectionAccessStore.select(this,requestedCollection);getIntent().removeExtra("collection_id");}
        applyCollectionLayer();
        for(VenueCollection.Definition collection:VenueCollection.all())VenueVisitStore.ensureHistory(this,collection.id,()->{
            if(active&&!isFinishing()&&!isDestroyed())applyCollectionLayer();
        });
        CollectionCatalogueUpdates.refreshIfDue(this,()->{
            if(active&&!isFinishing()&&!isDestroyed())applyCollectionLayer();
        });
        if (loadingCard != null) loadingCard.start();
        // Queue lightweight summaries before the heavier route/coverage preparation.
        refreshRecap();
        if (replayMessage == null && mapFrame != null && mapSubtitle != null) refreshMap();
        mainHandler.removeCallbacks(observeChanges);
        mainHandler.postDelayed(observeChanges, 3000);
    }

    @Override
    protected void onPause() {
        active = false;
        if (filterDialog != null) filterDialog.dismiss();
        if (loadingCard != null) loadingCard.stop();
        mainHandler.removeCallbacks(observeChanges);
        if (replayPreparing) stopReplay(false);
        else if (mapView != null) mapView.finishDiscoveryReplay();
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
        double[] camera = beforeReplayCamera != null ? beforeReplayCamera : mapView.cameraState();
        if (camera == null || camera.length < 3) return;
        savedCameraLongitude = camera[0];
        savedCameraLatitude = camera[1];
        savedCameraZoom = camera[2];
        hasSavedCamera = true;
        getSharedPreferences("roadprints_map_camera", MODE_PRIVATE).edit()
                .putLong("longitude", Double.doubleToLongBits(savedCameraLongitude))
                .putLong("latitude", Double.doubleToLongBits(savedCameraLatitude))
                .putLong("zoom", Double.doubleToLongBits(savedCameraZoom)).apply();
    }

    @Override
    protected void onDestroy() {
        mainHandler.removeCallbacks(observeChanges);
        cancelMapWork();
        hideMapLoading();
        mapLoadGeneration++;
        recapGeneration++;
        recapIo.shutdownNow();
        super.onDestroy();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        String previous = getIntent().getStringExtra("settlement_code");
        setIntent(intent);
        if (!java.util.Objects.equals(previous, intent.getStringExtra("settlement_code"))) recreate();
        else {String requested=intent.getStringExtra("collection_id");
            if(requested!=null){CollectionAccessStore.select(this,requested);intent.removeExtra("collection_id");}
            applyCollectionLayer();}
    }

    private void applyCollectionLayer() {
        if(mapView==null)return;
        mapView.setMapLayersFromPreferences();
        String selected=CollectionAccessStore.selected(this);
        boolean townMap=getIntent().hasExtra("settlement_code");
        if(townMap)return;
        double[] camera=mapView.cameraState();
        JSONArray pins=CollectionAccessStore.known(selected)?VenueCollection.catalogue(this,selected):new JSONArray();
        Set<String> visited=CollectionAccessStore.known(selected)?VenueCollection.completed(this,selected):java.util.Collections.emptySet();
        mapView.setCollectionVenues(selected,pins,visited);
        NcnMapOverlay.apply(this,mapView);
        VenueVisitUi.offerPending(this,this::applyCollectionLayer);
        mapView.setServiceStationTapListener(venue->{
            if(CollectionAccessStore.SERVICES.equals(CollectionAccessStore.selected(this)))
                showServiceStationCard(venue,ServiceStationStore.completed(this).contains(venue.optString("id")));
            else if(CollectionAccessStore.known(CollectionAccessStore.selected(this)))showPubCard(venue);
        });
        if(camera!=null)mapView.restoreCameraState(camera[0],camera[1],camera[2]);
    }

    private void showMapLoading(boolean updating) {
        hideMapLoading();
        setMapReady(false);
        mapSubtitle.setText(updating ? "Updating your map…" : "Bringing your roads into view…");
        loadingCard = new MapLoadingView(this, updating);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        params.setMargins(dp(16), dp(16), dp(16), dp(32));
        mapFrame.addView(loadingCard, params);
        if (active) loadingCard.start();
        if (updating) showMapRecovery(false);
    }

    private void cancelMapWork() {
        if(mapDeadline!=null)mainHandler.removeCallbacks(mapDeadline);
        if(mapPending!=null){mapPending.cancel(true);mapPending=null;}
        loadingMapRevision=Long.MIN_VALUE;
    }
    private void showMapRecovery(boolean failed) {
        if(mapRecovery!=null)mapFrame.removeView(mapRecovery);
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER);row.setPadding(dp(12),dp(6),dp(12),dp(6));
        row.setBackground(roundRect(0xFF0B1C50,12));row.setTag("map_load_recovery");
        if(failed){TextView retry=mapFilterButton("RETRY","Retry loading map journeys");retry.setTag("map_load_retry");retry.setOnClickListener(v->retryMap());row.addView(retry);}
        if(hasDisplayedMap){TextView cancel=mapFilterButton("CANCEL","Cancel map refresh and keep previous results");cancel.setTag("map_load_cancel");cancel.setOnClickListener(v->cancelMapRefresh());row.addView(cancel);}
        TextView reset=mapFilterButton("RESET","Reset map journey filters");reset.setTag("map_load_reset");reset.setOnClickListener(v->applyMapFilters(JourneyFilters.all()));row.addView(reset);
        FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.CENTER_HORIZONTAL);lp.topMargin=dp(12);mapFrame.addView(row,lp);mapRecovery=row;updateMapControls();
    }
    private void retryMap() {
        mapLoadFailed=false;skipDiskMapCache=true;forceMapReload=true;clearProcessMapCache();refreshMap();
    }
    private void completeMapWork() {
        if(mapDeadline!=null)mainHandler.removeCallbacks(mapDeadline);
        mapPending=null;loadingMapRevision=Long.MIN_VALUE;
    }
    private void cancelMapRefresh() {
        mapLoadGeneration++;cancelMapWork();mapLoadFailed=false;forceMapReload=false;hideMapLoading();
        mapFilters=displayedFilters;persistMapFilters();mapFilterSummary.setText(mapFilters.summary());
        mapSubtitle.setOnClickListener(null);mapSubtitle.setText("Map refresh cancelled. Previous results retained.");setMapReady(hasDisplayedMap);
        android.util.Log.i("RoadprintsTiming","map_outcome=cancelled total_ms="+(android.os.SystemClock.elapsedRealtime()-mapStartedAt));
    }
    private void mapFailure(int request,String message) {
        if(request!=mapLoadGeneration||isFinishing()||isDestroyed())return;
        mapLoadGeneration++;cancelMapWork();hideMapLoading();mapLoadFailed=true;
        mapSubtitle.setText(message);mapSubtitle.setOnClickListener(v->retryMap());
        setMapReady(false);showMapRecovery(true);
        android.util.Log.i("RoadprintsTiming","map_outcome=failure total_ms="+(android.os.SystemClock.elapsedRealtime()-mapStartedAt));
    }

    private void hideMapLoading() {
        if(mapRecovery!=null){mapFrame.removeView(mapRecovery);mapRecovery=null;}
        if (loadingCard != null) {
            loadingCard.stop();
            mapFrame.removeView(loadingCard);
            loadingCard = null;
        }
    }

    private void setMapReady(boolean ready) {
        mapReady=ready;
        updateMapStatus();
        updateMapControls();
        if(recapCard!=null)recapCard.setVisibility(View.VISIBLE);
    }
    private void updateMapStatus(){
        if(mapStatus==null)return;
        String state=mapReady?"Map ready":mapLoadFailed?"Map needs retry":"Loading map…";
        if(mapReady&&mapView!=null&&CollectionAccessStore.NCN.equals(CollectionAccessStore.selected(this))&&mapView.ncnLoadingStatus!=null)
            state="assessment".equals(mapView.ncnLoadingStatus)?NcnProgressStore.status():mapView.ncnLoadingStatus;
        mapStatus.setScreenStatus(state);
    }
    private void updateMapControls() {
        boolean interactive=mapReady&&!recapVisible;
        for(View control:new View[]{legendButton,mapFilterControls,zoomControls})
            if(control!=null)control.setVisibility(interactive?View.VISIBLE:View.GONE);
        if(mapView!=null)mapView.setOverviewLoading(recapVisible||(!interactive&&!hasDisplayedMap));
        if(mapRecovery!=null)mapRecovery.setVisibility(recapVisible?View.GONE:View.VISIBLE);
    }

    private void refreshMap() {
        String settlementCode = getIntent().getStringExtra("settlement_code");
        if (!forceMapReload && mapView != null && displayedMapRevision == mapDataRevision() && sameFilters(mapFilters,displayedFilters)) {
            if(hasDisplayedMap){hideMapLoading();setMapReady(true);}
            android.util.Log.i("RoadprintsTiming", "map_revisit=reused");return;
        }
        final long requestedRevision = mapDataRevision();
        if (loadingMapRevision != Long.MIN_VALUE || mapLoadFailed) return;
        saveMapCamera();
        final int generation = ++mapLoadGeneration;
        if (settlementCode != null && !settlementCode.isEmpty()) {
            refreshSettlementMap(generation, settlementCode,
                    getIntent().getStringExtra("settlement_name"),
                    getIntent().getStringExtra("settlement_journey_ids"));
            return;
        }
        final long revision = mapDataRevision();
        final JourneyFilters selection = mapFilters;
        final java.time.LocalDate today = java.time.LocalDate.now();
        final java.time.ZoneId zone = java.time.ZoneId.systemDefault();
        MapRoutes cached = null;
        synchronized (MAP_CACHE_LOCK) {
            if (selection.isAll() && processMapCacheRevision == revision) cached = processMapCache;
            else {
                processMapCache = null;
                processMapCacheRevision = revision;
            }
        }
        mapStartedAt=android.os.SystemClock.elapsedRealtime();
        loadingMapRevision = revision;
        if (cached != null) {
            displayMapRoutes(cached, generation);
            return;
        }
        mapLoadFailed=false;
        showMapLoading(hasDisplayedMap);
        mapDeadline=()->mapFailure(generation,"Loading took too long. Retry or Reset your filters.");
        mainHandler.postDelayed(mapDeadline,MAP_TIMEOUT_MS);
        final long queuedAt = android.os.SystemClock.elapsedRealtime();mapStartedAt=queuedAt;
        mapPending=ScreenDataLoader.map(() -> {
            android.util.Log.i("RoadprintsTiming", "map_queue_ms="+(android.os.SystemClock.elapsedRealtime()-queuedAt));
            MapRoutes mapRoutes;
            try (BetaMeasurement.Operation measurement = BetaMeasurement.operation(this, "map_data_load")) {
                long cacheStarted=android.os.SystemClock.elapsedRealtime();
                JSONObject saved = selection.isAll()&&!skipDiskMapCache ? PersistentScreenCache.read(getApplicationContext(),
                        "map-routes", revision) : null;
                try {mapRoutes = saved == null ? null : mapRoutesFromCache(saved);}
                catch(Exception malformedCache){mapRoutes=null;}
                PerformanceDiagnostics.timing("map_cache",android.os.SystemClock.elapsedRealtime()-cacheStarted,
                        mapRoutes==null?"miss_or_bypass":"hit");
                if (mapRoutes == null) {
                    mapRoutes = mapRouteLoader.load(this,selection, today, zone);
                    if(Thread.currentThread().isInterrupted()||generation!=mapLoadGeneration)return;

                }
                android.util.Log.i("RoadprintsTiming", "map_prepare_ms="+measurement.duration());
            } catch (Exception error) {
                mainHandler.post(() -> mapFailure(generation,"Your roads could not be loaded. Retry or Reset your filters."));
                return;
            }
            long currentRevision = mapDataRevision();
            Runtime runtime = Runtime.getRuntime();
            long freeHeadroom = runtime.maxMemory()
                    - (runtime.totalMemory() - runtime.freeMemory());
            if (selection.isAll() && generation == mapLoadGeneration && currentRevision == revision && freeHeadroom >= 64L * 1024L * 1024L) {
                synchronized (MAP_CACHE_LOCK) {
                    processMapCache = mapRoutes;
                    processMapCacheRevision = revision;
                }
            }
            long pathStarted=android.os.SystemClock.elapsedRealtime();
            for(java.util.List<JSONArray> lines:java.util.Arrays.asList(mapRoutes.sections,mapRoutes.motorwaySections,mapRoutes.incompleteMotorwaySections,mapRoutes.coveredMotorwaySections,mapRoutes.incompleteARoadSections,mapRoutes.coveredARoadSections,mapRoutes.ferrySections))MapRenderGeometry.prepare(lines);
            PerformanceDiagnostics.timing("map_paths_worker",android.os.SystemClock.elapsedRealtime()-pathStarted,"");
            displayMapRoutes(mapRoutes, generation);
            // Publish usable geometry before optional JSON serialization/fsync. Cache work
            // must not hold the interactive map lane or delay the readiness transition.
            final MapRoutes prepared=mapRoutes;
            if(selection.isAll()&&prepared.retainedPoints<=180000&&freeHeadroom>=96L*1024L*1024L)
                ScreenDataLoader.cache(()->{
                    if(generation!=mapLoadGeneration||mapDataRevision()!=revision)return;
                    try{PersistentScreenCache.write(getApplicationContext(),"map-routes",revision,mapRoutesToCache(prepared));}
                    catch(Exception ignored){android.util.Log.w("RoadprintsTiming","map_cache_write=skipped");}
                });
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
            if (isFinishing() || isDestroyed() || generation != mapLoadGeneration) return;
            try {
            long renderStarted = android.os.SystemClock.elapsedRealtime();
            hideMapLoading();
            mapSubtitle.setOnClickListener(null);
            mapSubtitle.setText(mapRoutes.sections.isEmpty()
                    && mapRoutes.ferrySections.isEmpty()
                    && mapRoutes.motorwaySections.isEmpty()
                    && mapRoutes.incompleteMotorwaySections.isEmpty()
                    ? (mapFilters.isAll() ? "Recorded journeys will appear here." : "No mapped journeys match these filters. Try another selection or Reset.")
                    : String.format(java.util.Locale.UK, "%,d recorded journeys shown%s",
                            mapRoutes.matchedJourneys,
                            mapRoutes.simplified ? " · map simplified for performance." : "."));
            double[] camera = mapView == null ? null : mapView.cameraState();
            RoutePreviewView map = mapView;
            if (map == null) {
                map = RoutePreviewView.overview(this);
                mapFrame.addView(map, 0, new FrameLayout.LayoutParams(-1, -1));
            }
            map.beginOverviewUpdate();
            map.setOverviewLoading(false);
            map.setRouteData(null, mapRoutes.sections);
            map.setFerrySegments(mapRoutes.ferrySections);
            map.setFlatRoadMapStyle(true);
            map.setMotorwaySegments(mapRoutes.motorwaySections);
            map.setMotorwayCoverageSegments(mapRoutes.incompleteMotorwaySections,
                    mapRoutes.coveredMotorwaySections);
            map.setARoadCoverageSegments(mapRoutes.incompleteARoadSections,
                    mapRoutes.coveredARoadSections);
            collectionServiceVenues=mapRoutes.serviceStations;
            collectionServiceVisits=mapRoutes.visitedServiceStationIds;
            mapView=map;
            applyCollectionLayer();
            roadPercentages = new HashMap<>(mapRoutes.roadPercentages);
            incompleteRoadSegments = new ArrayList<>(mapRoutes.incompleteRoadSegments);
            map.setMapRoadTapListener((latitude, longitude) ->
                    loadRoadSummaryAt(latitude, longitude));
            map.setContentDescription("Interactive OpenStreetMap. Pinch to zoom and drag to move.");
            map.endOverviewUpdate(camera==null&&!hasSavedCamera);
            if (camera != null) map.restoreCameraState(camera[0], camera[1], camera[2]);
            else if (hasSavedCamera) {
                map.restoreCameraState(savedCameraLongitude, savedCameraLatitude, savedCameraZoom);
                hasSavedCamera = false;
            }
            mapView = map;
            displayedMapRevision = loadingMapRevision == Long.MIN_VALUE ? mapDataRevision() : loadingMapRevision;
            displayedFilters=mapFilters;hasDisplayedMap=true;mapLoadFailed=false;skipDiskMapCache=false;forceMapReload=false;
            completeMapWork();
            setMapReady(true);
            android.util.Log.i("RoadprintsTiming","map_outcome=ready total_ms="+(android.os.SystemClock.elapsedRealtime()-mapStartedAt)+" selection="+filterKey(mapFilters));
            PerformanceDiagnostics.timing("map_render",android.os.SystemClock.elapsedRealtime()-renderStarted,"");
            PerformanceDiagnostics.timing("map_total",android.os.SystemClock.elapsedRealtime()-mapStartedAt,"");
            renderRecap();
            String replay = getIntent().getStringExtra("replay_journey_id");
            if (replay != null) {
                getIntent().removeExtra("replay_journey_id");
                startReplay(new HashSet<>(java.util.Collections.singleton(replay)));
            }
            }catch(Exception error){if(mapView!=null)mapView.endOverviewUpdate(false);mapFailure(generation,"Map display failed. Retry or Reset your filters.");}
        });
    }
    private void refreshSettlementMap(int generation, String code, String name,
                                      String journeyIdJson) {
        loadingMapRevision = mapDataRevision();
        showMapLoading(false);
        mapSubtitle.setText("Finding your roads in " + (name == null ? "this place" : name) + "…");
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
                hideMapLoading();
                loadingMapRevision = Long.MIN_VALUE;
                displayedMapRevision = mapDataRevision();
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
                setMapReady(resultFailure == null);
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
        long revision = 31L * JourneyStore.dataRevision(getApplicationContext()) + 201L;
        // Relative date selections must refresh at local midnight even without a new journey.
        String preset = mapFilters.date.preset;
        if (!getIntent().hasExtra("settlement_code") && ("today".equals(preset)
                || "week".equals(preset) || "month".equals(preset))) {
            revision = 31L * revision + java.time.LocalDate.now().toEpochDay();
        }
        return revision;
    }

    private MapRoutes readMapRoutes(JourneyFilters selection, java.time.LocalDate today, java.time.ZoneId zone) throws Exception {
        MapRoutes output = new MapRoutes();
        if (ServiceStationStore.unlocked(getApplicationContext())) {
            output.serviceStations = ServiceStationStore.stations(getApplicationContext());
            output.visitedServiceStationIds = ServiceStationStore.completed(getApplicationContext());
        }
        long initialiseStarted=System.nanoTime();
        MotorwayProgressCalculator motorwayCalculator =
                new MotorwayProgressCalculator(getApplicationContext());
        ARoadProgressCalculator aRoadCalculator = new ARoadProgressCalculator(getApplicationContext());
        PerformanceDiagnostics.timing("map_reference_init",(System.nanoTime()-initialiseStarted)/1_000_000,"");
        long evidenceStarted=System.nanoTime();
        long[] work=new long[3];int[] counts=new int[6];int[] unprocessedModes=new int[4];
        try(MapJourneyStore records=new MapJourneyStore(getApplicationContext(),skipDiskMapCache)) {
        records.forEach(() -> isDestroyed() || Thread.currentThread().isInterrupted(), record -> {
            JSONObject journey=record.header;
            counts[0]++;
            long filterStarted=System.nanoTime();
            boolean selected=selection.matches(journey,today,zone);
            work[0]+=System.nanoTime()-filterStarted;
            if(!selected){counts[1]++;return;}
            if("ferry".equals(journey.optString("mode"))) {
                JSONObject prepared=record.prepared(this::prepareFerryRecord);
                if(!prepared.has("ferries"))prepared=record.rebuild(this::prepareFerryRecord);
                JSONArray ferries=prepared.getJSONArray("ferries");
                for(int i=0;i<ferries.length()&&output.ferryPoints<20000&&output.ferrySections.size()<MAX_MAP_ROUTES;i++) {
                    JSONArray line=ferries.getJSONObject(i).getJSONArray("points");
                    if(output.ferryPoints+line.length()>20000){output.simplified=true;break;}
                    output.ferrySections.add(line);output.ferryPoints+=line.length();
                }
                if(ferries.length()>0){output.matchedJourneys++;counts[5]++;}else counts[4]++;
                return; // Display only: never enter road coverage preparation or attribution.
            }
            if (!"complete".equals(journey.optString("processing_status", ""))) {
                counts[2]++;
                String m=journey.optString("mode");
                if("train".equals(m))unprocessedModes[0]++;
                else if("plane".equals(m))unprocessedModes[1]++;
                else if(JourneyFilters.isUnknownMode(m))unprocessedModes[2]++;
                else unprocessedModes[3]++;
                return;
            }
            String mode=journey.optString("mode","").trim().toLowerCase(java.util.Locale.ROOT);
            if(!JourneyFilters.isRoadMode(mode)&&!JourneyFilters.isFootMode(mode)&&!MatchingCoordinator.isCycle(mode)){counts[3]++;return;}
            MapJourneyStore.Factory factory=raw->{
                long start=System.nanoTime();
                JSONObject prepared=new JSONObject().put("motorway",motorwayCalculator.contribution(raw))
                        .put("a_road",aRoadCalculator.contribution(raw));
                work[1]+=System.nanoTime()-start;
                start=System.nanoTime();
                prepared.put("roads",preparedLines(matchedSegments(raw)))
                        .put("motorways",preparedLines(motorwaySegments(raw)));
                work[2]+=System.nanoTime()-start;
                return prepared;
            };
            JSONObject prepared=record.prepared(factory);
            long coverageStarted=System.nanoTime();
            if(!motorwayCalculator.accepts(prepared.getJSONArray("motorway"))||!aRoadCalculator.accepts(prepared.getJSONArray("a_road"))) {
                work[1]+=System.nanoTime()-coverageStarted;
                prepared=record.rebuild(factory);
                coverageStarted=System.nanoTime();
            }
            motorwayCalculator.addContribution(journey.optString("journey_id"),prepared.getJSONArray("motorway"));
            aRoadCalculator.addContribution(journey.optString("journey_id"),prepared.getJSONArray("a_road"));
            work[1]+=System.nanoTime()-coverageStarted;
            long geometryStarted=System.nanoTime();
            JSONArray matched=prepared.getJSONArray("roads"),motorways=prepared.getJSONArray("motorways");
            if(matched.length()==0&&motorways.length()==0){counts[4]++;return;}
            appendPreparedLines(output,matched,false);
            appendPreparedLines(output,motorways,true);
            output.matchedJourneys++;counts[5]++;
            work[2]+=System.nanoTime()-geometryStarted;
        });
        PerformanceDiagnostics.timing("map_records",0,"reused="+records.reused+" rebuilt="+records.rebuilt
                +" source_reads="+records.sourceReads+" legacy_reads="+records.legacyReads+" oversized="+records.oversized);
        }
        long evidenceDuration=System.nanoTime()-evidenceStarted;
        PerformanceDiagnostics.timing("map_evidence",Math.max(0,evidenceDuration-work[0]-work[1]-work[2])/1_000_000,"compact_records_or_archive");
        PerformanceDiagnostics.timing("map_filter",work[0]/1_000_000,"total="+counts[0]+" filtered="+counts[1]+" unprocessed="+counts[2]+" hidden_mode="+counts[3]+" no_geometry="+counts[4]+" shown="+counts[5]+" unprocessed_train="+unprocessedModes[0]+" unprocessed_plane="+unprocessedModes[1]+" unprocessed_unknown="+unprocessedModes[2]+" unprocessed_other="+unprocessedModes[3]);
        PerformanceDiagnostics.timing("map_coverage_add",work[1]/1_000_000,"");
        PerformanceDiagnostics.timing("map_geometry",work[2]/1_000_000,"");
        long finishStarted=System.nanoTime();
        MotorwayProgressCalculator.Summary summary = motorwayCalculator.finish();
        for (MotorwayProgressCalculator.Road road : summary.roads) {
            if (!road.referenceAvailable) continue;
            output.roadPercentages.put(road.ref.toUpperCase(java.util.Locale.ROOT), road.percent());
            if (selection.isAll()) appendCanonicalSections(output, road.incompleteMapSections, false);
            appendCanonicalSections(output, road.coveredMapSections, true);
            if (selection.isAll()) addIncompleteRoadSegments(output, road.ref, road.region, road.percent(),
                    road.matchedMetres, road.journeyIds.size(), road.incompleteMapSections);
        }
        ARoadProgressCalculator.Summary aSummary = aRoadCalculator.finish();
        for (ARoadProgressCalculator.Road road : aSummary.roads) {
            if (!road.referenceAvailable) continue;
            output.roadPercentages.put(road.ref.toUpperCase(java.util.Locale.ROOT), road.percent());
            if (selection.isAll()) appendARoadSections(output, road.incompleteMapSections, false);
            appendARoadSections(output, road.coveredMapSections, true);
            if (selection.isAll()) addIncompleteRoadSegments(output, road.ref, road.region, road.percent(),
                    road.matchedMetres, road.journeyIds.size(), road.incompleteMapSections);
        }
        PerformanceDiagnostics.timing("map_coverage_finish",(System.nanoTime()-finishStarted)/1_000_000,"");
        return output;
    }


    private JSONObject prepareFerryRecord(JSONObject header) throws Exception {
        JSONObject geometry=JourneyStore.recordedGeometry(getApplicationContext(),header.optString("journey_id"));
        JSONObject recorded=new JSONObject();if(geometry!=null)recorded.put("route_geometry",geometry);
        return new JSONObject().put("motorway",new JSONArray()).put("a_road",new JSONArray())
                .put("roads",new JSONArray()).put("motorways",new JSONArray())
                .put("ferries",preparedLines(recordedSegments(recorded)));
    }

    private JSONArray preparedLines(List<JSONArray> routes) throws Exception {
        JSONArray result=new JSONArray();
        for(JSONArray route:routes) {
            JSONArray projected=projectMapRoute(route,MAX_POINTS_PER_ROUTE);
            if(projected!=null)result.put(new JSONObject().put("points",projected).put("original",route.length()));
        }
        return result;
    }
    private void appendPreparedLines(MapRoutes output,JSONArray rows,boolean motorway) throws Exception {
        List<JSONArray> target=motorway?output.motorwaySections:output.sections;
        int limit=motorway?MAX_MOTORWAY_POINTS:MAX_MAP_POINTS;
        int used=motorway?output.motorwayRetainedPoints:output.retainedPoints;
        for(int i=0;i<rows.length();i++) {
            if(used>=limit||target.size()>=MAX_MAP_ROUTES){output.simplified=true;break;}
            JSONObject row=rows.getJSONObject(i);JSONArray points=row.getJSONArray("points");
            int allowed=Math.min(MAX_POINTS_PER_ROUTE,limit-used);
            JSONArray projected=points.length()<=allowed?points:projectMapRoute(points,allowed);
            if(projected!=null){target.add(projected);used+=projected.length();if(projected.length()<row.getInt("original"))output.simplified=true;}
        }
        if(motorway)output.motorwayRetainedPoints=used;else output.retainedPoints=used;
    }

    private void addIncompleteRoadSegments(MapRoutes output, String ref, String region,
                                           double percent, double travelledMetres,
                                           int journeyCount, List<JSONArray> sections) {
        String label = "NI".equalsIgnoreCase(region) ? ref + " · Northern Ireland" : ref;
        for (JSONArray section : sections)
            output.incompleteRoadSegments.add(new RoadCoverageSegment(label, section,
                    percent, travelledMetres, journeyCount));
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

    private void showPubCard(JSONObject pub) {
        if(pub==null||isFinishing())return;
        String collection=CollectionAccessStore.selected(this);
        boolean visited=VenueCollection.completed(this,collection).contains(pub.optString("id"));
        LinearLayout content=cardContainer();
        addCardLabel(content,CollectionAccessStore.name(collection).toUpperCase(java.util.Locale.ROOT));addCardTitle(content,pub.optString("name","Pub"));
        if(!pub.optString("town").isEmpty())addCardBody(content,pub.optString("town"));
        addCardBody(content,visited?"Visited":"Not visited");
        if(!pub.optString("location_label").isEmpty())addCardBody(content,pub.optString("location_label"));
        if(!pub.optString("attribution").isEmpty())addCardBody(content,pub.optString("attribution"));
        AlertDialog popup=new AlertDialog.Builder(this).setView(content).create();
        LinearLayout actions=new LinearLayout(this);actions.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);
        TextView detail=cardAction(visited?"VISIT DETAILS":"CHECK IN",true);
        detail.setOnClickListener(v->{popup.dismiss();if(visited)VenueVisitUi.details(this,collection,pub,this::applyCollectionLayer);
            else VenueVisitUi.live(this,collection,pub,this::applyCollectionLayer,null);});
        TextView close=cardAction("CLOSE",false);close.setOnClickListener(v->popup.dismiss());
        actions.addView(detail);content.addView(actions);RoadprintsDialog.placeClose(content,close);
        popup.setOnShowListener(dialog->{if(popup.getWindow()!=null)popup.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));});
        popup.show();
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
        TextView checkin=cardAction(visited?"VISIT DETAILS":"CHECK IN",true);
        checkin.setOnClickListener(v->{stationDialog.dismiss();
            if(visited)VenueVisitUi.details(this,CollectionAccessStore.SERVICES,station,this::applyCollectionLayer);
            else VenueVisitUi.live(this,CollectionAccessStore.SERVICES,station,this::applyCollectionLayer,null);});
        actions.addView(checkin);
        RoadprintsDialog.placeClose(content,close);
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
        RoadprintsDialog.placeClose(content,close);
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

    private static final class RoadFeatureMatch {
        final JSONObject journey;
        final String journeyId;
        final String key;
        final String label;
        final double distanceFromTap;
        final double travelledMetres;

        RoadFeatureMatch(JSONObject journey, String key, String label,
                         double distanceFromTap, double travelledMetres) {
            this.journey = journey;
            this.journeyId = journey.optString("journey_id", "");
            this.key = key;
            this.label = label;
            this.distanceFromTap = distanceFromTap;
            this.travelledMetres = travelledMetres;
        }
    }

    private void loadRoadSummaryAt(double latitude, double longitude) {
        if(!mapReady||recapVisible)return; // Panning can continue; summaries require the committed selection.
        final JourneyFilters selection = mapFilters;
        final int requestId = ++roadSummaryRequestId;
        showRoadSummaryLoadingCard();
        ScreenDataLoader.execute(() -> {
            RoadCoverageSegment incomplete = nearestIncompleteRoad(latitude, longitude);
            if (incomplete != null && incomplete.distanceMetres <= 85.0) {
                mainHandler.post(() -> {
                    if (isFinishing() || requestId != roadSummaryRequestId) return;
                    showIncompleteRoadSummaryCard(incomplete);
                });
                return;
            }
            List<RoadFeatureMatch> candidates = new ArrayList<>();
            try {
                JourneyStore.forEach(getApplicationContext(), journey -> {
                    if (!selection.matches(journey, java.time.LocalDate.now(), java.time.ZoneId.systemDefault())) return;
                    if (!"complete".equals(journey.optString("processing_status", ""))) return;
                    String mode = journey.optString("mode", "").toLowerCase(java.util.Locale.ROOT);
                    if (!(mode.equals("driving") || mode.equals("bus") || mode.equals("walking")
                            || mode.equals("running") || mode.equals("pedestrian") || MatchingCoordinator.isCycle(mode))) return;
                    JSONObject result = journey.optJSONObject("processing_result");
                    JSONArray features = roadFeaturesForTap(result);
                    if (features == null) return;
                    for (int i = 0; i < features.length(); i++) {
                        JSONObject feature = features.optJSONObject(i);
                        if (feature == null || JourneyCorrectionUtils.excludesRoadFeature(journey, feature))
                            continue;
                        JSONObject properties = feature.optJSONObject("properties");
                        if (properties == null) continue;
                        String rawRef = properties.optString("road_ref",
                                properties.optString("ref", "")).trim();
                        String name = properties.optString("name",
                                properties.optString("road_name", "")).trim();
                        String label = !rawRef.isEmpty() ? rawRef : name;
                        if (label.isEmpty()) continue;
                        double distance = featureDistanceMetres(feature, latitude, longitude);
                        // The map callback supplies the nearest rendered line point,
                        // which lets us allow for small offsets between matched traces
                        // and the road catalogue without relying on the finger position.
                        if (distance > 120.0) continue;
                        String key = label.toUpperCase(java.util.Locale.ROOT);
                        double travelled = properties.optDouble("distance_m", 0);
                        if (travelled <= 0) travelled = featureLengthMetres(feature);
                        candidates.add(new RoadFeatureMatch(
                                journey, key, label, distance, travelled));
                    }
                });
            } catch (Exception error) {
                android.util.Log.w("Roadprints", "Could not calculate the tapped road summary", error);
            }

            RoadFeatureMatch nearest = null;
            for (RoadFeatureMatch candidate : candidates) {
                if (nearest == null || candidate.distanceFromTap < nearest.distanceFromTap)
                    nearest = candidate;
            }
            final String roadName = nearest == null ? null : nearest.label;
            final String roadKey = nearest == null ? null : nearest.key;
            final List<JSONObject> related = new ArrayList<>();
            double totalMetres = 0;
            if (nearest != null) {
                Map<String, JSONObject> journeysById = new java.util.LinkedHashMap<>();
                Map<String, Double> distancesById = new HashMap<>();
                for (RoadFeatureMatch candidate : candidates) {
                    if (!roadKey.equals(candidate.key) || candidate.distanceFromTap > 120.0
                            || candidate.journeyId.isEmpty()) continue;
                    journeysById.putIfAbsent(candidate.journeyId, candidate.journey);
                    distancesById.put(candidate.journeyId,
                            distancesById.getOrDefault(candidate.journeyId, 0d)
                                    + candidate.travelledMetres);
                }
                related.addAll(journeysById.values());
                for (double distance : distancesById.values()) totalMetres += distance;
            }
            final double travelled = totalMetres;
            final int times = related.size();
            final double percent = roadKey == null ? Double.NaN
                    : roadPercentages.getOrDefault(roadKey, Double.NaN);
            mainHandler.post(() -> {
                if (isFinishing() || requestId != roadSummaryRequestId) return;
                if (roadName == null) {
                    showRoadSummaryCard("Road not identified",
                            0, 0, Double.NaN, new ArrayList<>(),
                            "Try zooming in and tapping the centre of the road.");
                    return;
                }
                showRoadSummaryCard(roadName, travelled, times, percent, related, null);
            });
        });
    }

    private RoadCoverageSegment nearestIncompleteRoad(double latitude, double longitude) {
        if(!MapLayerPreferences.visible(this,MapLayerPreferences.Layer.REMAINING))return null;
        RoadCoverageSegment nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (RoadCoverageSegment segment : incompleteRoadSegments) {
            double distance = lineDistanceMetres(segment.coordinates, latitude, longitude);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = segment;
            }
        }
        if (nearest == null) return null;
        nearest.distanceMetres = nearestDistance;
        return nearest;
    }

    private void showIncompleteRoadSummaryCard(RoadCoverageSegment road) {
        double remaining = Math.max(0, 100.0 - road.completedPercent);
        LinearLayout content = cardContainer();
        addCardLabel(content, "ROAD DISCOVERY");
        addCardTitle(content, road.label);
        addRoadMetric(content, "Distance travelled", DistanceUnits.format(this, road.travelledMetres));
        addRoadMetric(content, "Times travelled", Integer.toString(road.journeyCount));
        addRoadMetric(content, "Road completed",
                String.format(java.util.Locale.UK, "%.1f%%", road.completedPercent));
        addRoadMetric(content, "Remaining to complete",
                String.format(java.util.Locale.UK, "%.1f%%", remaining));
        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.END);
        TextView close = cardAction("CLOSE", false);
        close.setOnClickListener(v -> roadDialog.dismiss());
        RoadprintsDialog.placeClose(content,close);
        content.addView(actions);
        presentRoadSummaryContent(content);
    }

    private JSONArray roadFeaturesForTap(JSONObject result) {
        if (result == null) return null;
        JSONObject roadGeoJson = result.optJSONObject("road_geojson");
        JSONArray features = roadGeoJson == null ? null : roadGeoJson.optJSONArray("features");
        if (features != null && features.length() > 0) return features;
        JSONArray combined = new JSONArray();
        appendFeatureArray(combined, result.optJSONObject("motorway_geojson"));
        appendFeatureArray(combined, result.optJSONObject("a_road_geojson"));
        return combined.length() == 0 ? null : combined;
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

    private double featureLengthMetres(JSONObject feature) {
        JSONObject geometry = feature == null ? null : feature.optJSONObject("geometry");
        if (geometry == null) return 0;
        String type = geometry.optString("type", "");
        JSONArray coordinates = geometry.optJSONArray("coordinates");
        double length = 0;
        if ("LineString".equals(type)) return lineLengthMetres(coordinates);
        if ("MultiLineString".equals(type) && coordinates != null)
            for (int i = 0; i < coordinates.length(); i++)
                length += lineLengthMetres(coordinates.optJSONArray(i));
        return length;
    }

    private double lineLengthMetres(JSONArray line) {
        double length = 0;
        if (line == null) return length;
        for (int i = 1; i < line.length(); i++) {
            JSONArray a = line.optJSONArray(i - 1), b = line.optJSONArray(i);
            if (a == null || b == null || a.length() < 2 || b.length() < 2) continue;
            length += distanceMetres(a.optDouble(1), a.optDouble(0),
                    b.optDouble(1), b.optDouble(0));
        }
        return length;
    }

    private void showRoadSummaryCard(String road, double metres, int times,
                                    double percent, List<JSONObject> journeys, String note) {
        LinearLayout content = cardContainer();
        addCardLabel(content, "ROAD SUMMARY");
        addCardTitle(content, road);
        if (note != null) {
            addCardBody(content, note);
        } else {
            addRoadMetric(content, "Distance travelled", DistanceUnits.format(this, metres));
            addRoadMetric(content, "Times travelled", Integer.toString(times));
            addRoadMetric(content, "Road completed",
                    Double.isFinite(percent) ? String.format(java.util.Locale.UK, "%.1f%%", percent)
                            : "Percentage unavailable");
        }
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
        close.setOnClickListener(v -> {
            roadSummaryRequestId++;
            roadDialog.dismiss();
        });
        content.addView(actions);RoadprintsDialog.placeClose(content,close);
        presentRoadSummaryContent(content);
    }

    private AlertDialog roadDialog;
    private int roadSummaryRequestId;
    private LinearLayout roadDialogContent;

    private void showRoadSummaryLoadingCard() {
        LinearLayout content = cardContainer();
        addCardLabel(content, "ROAD SUMMARY");
        addCardTitle(content, "Finding road details…");
        LinearLayout loading = new LinearLayout(this);
        loading.setGravity(Gravity.CENTER_VERTICAL);
        ProgressBar spinner = new ProgressBar(this);
        spinner.setIndeterminate(true);
        spinner.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(0xFF67D5CC));
        loading.addView(spinner, new LinearLayout.LayoutParams(dp(24), dp(24)));
        TextView text = new TextView(this);
        text.setText("Loading distance, coverage and journeys");
        text.setTextColor(0xFFD3DCED);
        text.setTextSize(14);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(-2, -2);
        textParams.leftMargin = dp(12);
        loading.addView(text, textParams);
        LinearLayout.LayoutParams loadingParams = new LinearLayout.LayoutParams(-1, -2);
        loadingParams.topMargin = dp(12);
        content.addView(loading, loadingParams);
        TextView close=RoadprintsDialog.closeControl(this,()->{roadSummaryRequestId++;if(roadDialog!=null)roadDialog.dismiss();});
        RoadprintsDialog.placeClose(content,close);
        presentRoadSummaryContent(content);
    }

    private void presentRoadSummaryContent(LinearLayout content) {
        if (roadDialog != null && roadDialog.isShowing()) {
            // AlertDialog.setView() only takes effect before show() on several Android
            // versions. Move the fetched card rows into the already visible root instead.
            roadDialogContent.removeAllViews();
            while (content.getChildCount() > 0) {
                View child = content.getChildAt(0);
                LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) child.getLayoutParams();
                content.removeViewAt(0);
                roadDialogContent.addView(child, params);
            }
            return;
        }
        roadDialogContent = content;
        roadDialog = new AlertDialog.Builder(this).setView(content).create();
        roadDialog.setOnShowListener(dialog -> {
            if (roadDialog.getWindow() != null)
                roadDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        });
        roadDialog.setOnDismissListener(dialog -> {
            roadSummaryRequestId++;
            roadDialogContent = null;
        });
        roadDialog.show();
    }

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

    private void addMapFilters() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(10), dp(8), dp(10), dp(8));
        panel.setBackground(roundRect(0xF50B1C50, dp(14)));
        panel.setElevation(dp(4));
        LinearLayout row = new LinearLayout(this);
        TextView filter = mapFilterButton("☷ FILTER JOURNEYS", "Filter map journeys");
        android.graphics.drawable.Drawable icon = getDrawable(R.drawable.ic_roadprints_filter).mutate();
        icon.setTint(Color.WHITE); icon.setBounds(0, 0, dp(20), dp(20));
        filter.setText("FILTER JOURNEYS");
        filter.setCompoundDrawablesRelative(icon, null, null, null);
        filter.setCompoundDrawablePadding(dp(6));
        filter.setTag("map_filters_open");
        filter.setOnClickListener(v -> showMapFilters());
        LinearLayout.LayoutParams open = new LinearLayout.LayoutParams(0, dp(48), 1);
        open.rightMargin = dp(8);
        row.addView(filter, open);
        TextView reset = mapFilterButton("RESET", "Reset map journey filters");
        reset.setTextColor(GOLD);
        reset.setBackground(roundRect(NAVY, dp(10)));
        reset.setTag("map_filters_reset");
        reset.setOnClickListener(v -> applyMapFilters(JourneyFilters.all()));
        row.addView(reset, new LinearLayout.LayoutParams(dp(76), dp(48)));
        panel.addView(row);
        mapFilterSummary = new TextView(this);
        mapFilterSummary.setTextColor(0xFF67D5CC);
        mapFilterSummary.setTextSize(12);
        mapFilterSummary.setPadding(dp(2), dp(6), dp(2), 0);
        mapFilterSummary.setText(mapFilters.summary());
        panel.addView(mapFilterSummary);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP);
        params.setMargins(dp(12), dp(12), dp(12), 0);
        mapFrame.addView(panel, params);
        mapFilterControls = panel;
        panel.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob) -> { if (b-t != ob-ot) renderRecap(); });
    }

    private TextView mapFilterButton(String label, String description) {
        TextView button = new TextView(this);
        button.setText(label); button.setTextSize(12); button.setTextColor(Color.WHITE);
        button.setTypeface(null, Typeface.BOLD); button.setGravity(Gravity.CENTER);
        button.setPadding(dp(8), 0, dp(8), 0);
        button.setBackground(roundRect(0xFF233B78, dp(10)));
        button.setContentDescription(description); button.setFocusable(true);
        button.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override public void onInitializeAccessibilityNodeInfo(View host, android.view.accessibility.AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setClassName(android.widget.Button.class.getName());
            }
        });
        return button;
    }

    private void showMapFilters() {
        if (!active || !mapReady || recapVisible || (filterDialog != null && filterDialog.isShowing())) return;
        filterDialog = new JourneyFilterPanel(this, null, mapFilters, true, this::applyMapFilters).show();
    }

    private void applyMapFilters(JourneyFilters selection) {
        if(mapLoadFailed){skipDiskMapCache=true;forceMapReload=true;clearProcessMapCache();}
        mapFilters = selection;
        persistMapFilters();
        mapFilterSummary.setText(selection.summary());
        filterDialogGeneration++;
        roadSummaryRequestId++;
        if (roadDialog != null) roadDialog.dismiss();
        stopReplay(false);
        mapLoadGeneration++;
        cancelMapWork();mapLoadFailed=false;
        android.util.Log.i("RoadprintsTiming","map_filter_apply selection="+filterKey(selection));
        refreshMap();
    }

    private void persistMapFilters() {
        getSharedPreferences(FILTER_PREFS, MODE_PRIVATE).edit().putInt("mode", mapFilters.mode)
                .putString("status", mapFilters.status).putString("date", mapFilters.date.preset)
                .putString("from", mapFilters.date.from == null ? null : mapFilters.date.from.toString())
                .putString("to", mapFilters.date.to == null ? null : mapFilters.date.to.toString()).apply();
    }

    private void addZoomControls(FrameLayout frame, RoutePreviewView map) {
        zoomControls=MapZoomControls.add(frame,map);updateMapControls();
    }

    private GradientDrawable roundRect(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private void applySystemBarInsets(View root, View heading, View bottomNavigation) {
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            androidx.core.graphics.Insets safe = insets.getInsets(
                    androidx.core.view.WindowInsetsCompat.Type.systemBars()
                            | androidx.core.view.WindowInsetsCompat.Type.displayCutout());
            root.setPadding(safe.left, 0, safe.right, 0);
            heading.setPadding(dp(18), dp(12) + safe.top, dp(18), dp(8));
            LinearLayout.LayoutParams navParams = (LinearLayout.LayoutParams) bottomNavigation.getLayoutParams();
            navParams.height = dp(68) + safe.bottom;
            bottomNavigation.setPadding(dp(8), 0, dp(8), safe.bottom);
            bottomNavigation.setLayoutParams(navParams);
            return insets;
        });
        androidx.core.view.ViewCompat.requestApplyInsets(root);
    }

    private long recapRevision = Long.MIN_VALUE;

    private void refreshRecap() {
        if (getIntent().hasExtra("settlement_code") || recapLoading) return;
        long revision = JourneyStore.dataRevision(getApplicationContext());
        if (recapRevision == revision) return;
        recapLoading = true;
        int generation = ++recapGeneration;
        final long observedAt = System.currentTimeMillis();
        recapIo.execute(() -> {
            try {
                List<JSONObject> inbox = ReturnRecapStore.pending(getApplicationContext());
                if (ReturnRecapStore.inboxReady(getApplicationContext())) {
                    mainHandler.post(() -> {
                        if (isFinishing() || generation != recapGeneration) return;
                        recapLoading=false;recapRevision=revision;recapJourneys=inbox;renderRecap();
                    });
                    return;
                }
                mainHandler.post(() -> {
                    if (!active || isFinishing() || generation != recapGeneration || inbox.isEmpty()) return;
                    recapJourneys = inbox;
                    renderRecap();
                });
                List<JSONObject> rows = JourneyListActivity.preloadSummaries(getApplicationContext(), revision);
                if (JourneyStore.dataRevision(getApplicationContext()) != revision) {
                    mainHandler.post(() -> { if (generation == recapGeneration) { recapLoading = false; if (active) refreshRecap(); } });
                    return;
                }
                List<JSONObject> collected = ReturnRecapStore.collect(getApplicationContext(), rows, observedAt);
                ReturnRecapStore.inboxReady(getApplicationContext(),true);
                mainHandler.post(() -> {
                    if (isFinishing() || generation != recapGeneration) return;
                    recapLoading = false;
                    recapRevision = revision;
                    recapJourneys = collected;
                    renderRecap();
                });
            } catch (Exception error) {
                mainHandler.post(() -> { if (generation == recapGeneration) recapLoading = false; });
            }
        });
    }

    private Set<String> recapIds() {
        Set<String> ids = new HashSet<>();
        for (JSONObject journey : recapJourneys) ids.add(journey.optString("journey_id"));
        return ids;
    }

    private TextView recapText(CharSequence value, int size, int color, boolean bold) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(color);
        if (bold) text.setTypeface(null, Typeface.BOLD);
        return text;
    }

    private TextView recapMetric(String title, String value) {
        android.text.SpannableStringBuilder row = new android.text.SpannableStringBuilder(title + ": " + value);
        row.setSpan(new android.text.style.ForegroundColorSpan(0xFF67D5CC), 0, title.length() + 1,
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        row.setSpan(new android.text.style.StyleSpan(Typeface.BOLD), 0, title.length() + 1,
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return recapText(row, 13, Color.WHITE, false);
    }

    private void renderRecap() {
        if (mapFrame == null || replayMessage != null) return;
        if (recapCard != null) mapFrame.removeView(recapCard);
        recapCard = null;
        recapVisible=false;updateMapControls();
        if (recapJourneys.isEmpty()) return;
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(12), dp(16), dp(12));
        card.setBackground(roundRect(0xF50B1C50, dp(16)));
        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        titleRow.addView(recapText("Welcome back", 18, Color.WHITE, true),
                new LinearLayout.LayoutParams(0, -2, 1));
        final Set<String> shownIds = recapIds();
        TextView dismiss = recapText("DISMISS", 11, GOLD, true);
        dismiss.setPadding(dp(8), dp(12), dp(8), dp(12));
        dismiss.setContentDescription("Dismiss new journey recap");
        dismiss.setOnClickListener(view -> dismissRecap(shownIds));
        titleRow.addView(dismiss);
        card.addView(titleRow);
        int complete = 0, failed = 0;
        double distance = 0;
        for (JSONObject journey : recapJourneys) {
            if ("complete".equals(journey.optString("processing_status"))) complete++;
            else if ("failed".equals(journey.optString("processing_status"))) failed++;
            double value = journey.optDouble("distance_meters", 0);
            if (Double.isFinite(value) && value > 0) distance += value;
        }
        card.addView(recapMetric("Journeys recorded", DistanceUnits.formatPointCount(recapJourneys.size())));
        card.addView(recapMetric("Distance", DistanceUnits.format(this, distance)));
        int pending = recapJourneys.size() - complete - failed;
        card.addView(recapMetric("Matching", DistanceUnits.formatPointCount(complete) + " complete"
                + (pending > 0 ? " · " + DistanceUnits.formatPointCount(pending) + " pending" : "")
                + (failed > 0 ? " · " + DistanceUnits.formatPointCount(failed) + " need retry" : "")));
        LinearLayout actions = new LinearLayout(this);
        TextView journeys = recapText("VIEW NEW JOURNEYS", 12, Color.WHITE, true);
        journeys.setPadding(0, dp(14), dp(8), dp(10));
        journeys.setOnClickListener(view -> {
            ArrayList<String> ids=new ArrayList<>(shownIds);dismissRecap(shownIds);
            startActivity(new Intent(this,JourneyListActivity.class).putStringArrayListExtra("recap_journey_ids",ids));
        });
        actions.addView(journeys, new LinearLayout.LayoutParams(0, -2, 1));
        if (complete > 0) {
            TextView replay = recapText("SEE WHAT’S NEW", 12, GOLD, true);
            replay.setPadding(dp(8), dp(14), 0, dp(10));
            replay.setOnClickListener(view -> {dismissRecap(shownIds);startReplay(shownIds);});
            actions.addView(replay);
        }
        card.addView(actions);
        FrameLayout overlay=new FrameLayout(this);overlay.setTag("welcome_back_overlay");overlay.setBackgroundColor(0x66000000);
        overlay.setClickable(true);overlay.setFocusable(true);overlay.setElevation(dp(12));
        ScrollView scroll=new ScrollView(this){
            @Override protected void onMeasure(int w,int h){super.onMeasure(w,View.MeasureSpec.makeMeasureSpec(Math.max(dp(120),(int)(View.MeasureSpec.getSize(h)*.72)),View.MeasureSpec.AT_MOST));}
        };
        scroll.addView(card);
        FrameLayout.LayoutParams params=new FrameLayout.LayoutParams(-1,-2,Gravity.TOP);
        params.setMargins(dp(12),dp(12),dp(12),dp(12));overlay.addView(scroll,params);
        mapFrame.addView(overlay,new FrameLayout.LayoutParams(-1,-1));recapCard=overlay;
        recapVisible=true;updateMapControls();
        PerformanceDiagnostics.timing("new_journeys_presented", 0, "count=" + shownIds.size());
    }
    private void dismissRecap(Set<String> shownIds) {
        ReturnRecapStore.dismiss(this,shownIds);recapGeneration++;recapLoading=false;recapRevision=Long.MIN_VALUE;
        recapJourneys.removeIf(journey->shownIds.contains(journey.optString("journey_id")));renderRecap();
        if(active)refreshRecap();
    }
    @Override public void onBackPressed(){
        if(recapVisible){dismissRecap(recapIds());return;}super.onBackPressed();
    }

    private void startReplay(Set<String> ids) {
        startActivity(new Intent(this, JourneyReplayActivity.class)
                .putStringArrayListExtra("journey_ids", new ArrayList<>(ids)));
    }

    static final class ReplayContext {
        final List<JSONArray> roads = new ArrayList<>(), motorways = new ArrayList<>(), aRoads = new ArrayList<>();
        int points;
    }

    /** Reuse already displayed overview geometry; no disk read or archive scan on Play. */
    static ReplayContext cachedReplayContext(Context context) {
        long revision=31L*JourneyStore.dataRevision(context)+201L;
        synchronized(MAP_CACHE_LOCK) {
            if(processMapCache==null||processMapCacheRevision!=revision)return null;
            ReplayContext result=new ReplayContext();
            result.roads.addAll(processMapCache.sections);
            result.motorways.addAll(processMapCache.coveredMotorwaySections);
            if(result.motorways.isEmpty())result.motorways.addAll(processMapCache.motorwaySections);
            result.aRoads.addAll(processMapCache.coveredARoadSections);
            return result;
        }
    }

    static ReplayContext readReplayContext(Context context, DiscoveryReplay trace,
            java.util.function.BooleanSupplier cancelled) throws Exception {
        ReplayContext output = new ReplayContext();
        double west=180,east=-180,south=90,north=-90;
        for (DiscoveryReplay.Section section : trace.routes) for(int i=0;i<section.points.length();i++) {
            JSONArray p=section.points.optJSONArray(i);if(p==null)continue;
            west=Math.min(west,p.optDouble(0));east=Math.max(east,p.optDouble(0));
            south=Math.min(south,p.optDouble(1));north=Math.max(north,p.optDouble(1));
        }
        final double w=west-.03,e=east+.03,s=south-.02,n=north+.02;
        JourneyStore.forEachRoadEvidence(context,cancelled,journey->{
            if(!"complete".equals(journey.optString("processing_status")))return;
            JSONObject result=journey.optJSONObject("processing_result");if(result==null)return;
            String[] keys={"road_geojson","motorway_geojson","a_road_geojson"};
            List<List<JSONArray>> targets=java.util.Arrays.asList(output.roads,output.motorways,output.aRoads);
            for(int k=0;k<keys.length;k++) {
                JSONObject collection=result.optJSONObject(keys[k]);
                JSONArray features=collection==null?null:collection.optJSONArray("features");if(features==null)continue;
                for(int f=0;f<features.length();f++) {
                    JSONObject feature=features.optJSONObject(f);
                    if(feature==null||JourneyCorrectionUtils.excludesRoadFeature(journey,feature))continue;
                    for(JSONArray line:DiscoveryReplay.lines(feature.optJSONObject("geometry"))) {
                        JSONArray retained=new JSONArray();
                        for(int i=0;i<line.length();i++) {
                            JSONArray p=line.optJSONArray(i);
                            boolean nearby=p!=null&&p.optDouble(0)>=w&&p.optDouble(0)<=e&&p.optDouble(1)>=s&&p.optDouble(1)<=n;
                            if(!nearby||output.points>=45000||output.roads.size()+output.motorways.size()+output.aRoads.size()>=2000) {
                                if(retained.length()>1)targets.get(k).add(retained);
                                retained=new JSONArray();continue;
                            }
                            retained.put(p);output.points++;
                        }
                        if(retained.length()>1)targets.get(k).add(retained);
                    }
                }
            }
        });
        return output;
    }

    private void stopReplay(boolean showRecap) {
        if (mapView != null) mapView.stopDiscoveryReplay();
        if (replayMessage != null) mapFrame.removeView((View) replayMessage.getTag());
        replayMessage = null;
        replayPreparing = false;
        if (beforeReplayCamera != null && mapView != null)
            mapView.restoreCameraState(beforeReplayCamera[0], beforeReplayCamera[1], beforeReplayCamera[2]);
        beforeReplayCamera = null;
        if (showRecap) renderRecap();
    }

    private View buildBottomNavigation() {
        return RoadprintsNavigation.create(this, 0);
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private JSONObject mapRoutesToCache(MapRoutes routes) throws Exception {
        JSONObject root = new JSONObject();
        putRouteArrays(root, "sections", routes.sections);
        putRouteArrays(root,"ferries",routes.ferrySections);
        putRouteArrays(root, "motorways", routes.motorwaySections);
        putRouteArrays(root, "incompleteMotorways", routes.incompleteMotorwaySections);
        putRouteArrays(root, "coveredMotorways", routes.coveredMotorwaySections);
        putRouteArrays(root, "incompleteARoads", routes.incompleteARoadSections);
        putRouteArrays(root, "coveredARoads", routes.coveredARoadSections);
        root.put("stations", routes.serviceStations);
        JSONArray visited = new JSONArray();
        for (String id : routes.visitedServiceStationIds) visited.put(id);
        root.put("visited", visited);
        root.put("matchedJourneys", routes.matchedJourneys);
        root.put("retainedPoints", routes.retainedPoints);
        root.put("motorwayRetainedPoints", routes.motorwayRetainedPoints);
        root.put("incompleteMotorwayPoints", routes.incompleteMotorwayPoints);
        root.put("coveredMotorwayPoints", routes.coveredMotorwayPoints);
        root.put("incompleteARoadPoints", routes.incompleteARoadPoints);
        root.put("coveredARoadPoints", routes.coveredARoadPoints);
        root.put("simplified", routes.simplified);
        JSONObject percentages = new JSONObject();
        for (Map.Entry<String, Double> entry : routes.roadPercentages.entrySet())
            percentages.put(entry.getKey(), entry.getValue());
        root.put("percentages", percentages);
        JSONArray incomplete = new JSONArray();
        for (RoadCoverageSegment segment : routes.incompleteRoadSegments) {
            incomplete.put(new JSONObject().put("label", segment.label)
                    .put("coordinates", segment.coordinates)
                    .put("completedPercent", segment.completedPercent)
                    .put("travelledMetres", segment.travelledMetres)
                    .put("journeyCount", segment.journeyCount)
                    .put("distanceMetres", segment.distanceMetres));
        }
        root.put("incompleteRoadSegments", incomplete);
        return root;
    }

    private MapRoutes mapRoutesFromCache(JSONObject root) {
        MapRoutes routes = new MapRoutes();
        if (!root.has("sections") || !root.has("stations")) return null;
        getRouteArrays(root, "sections", routes.sections);
        getRouteArrays(root,"ferries",routes.ferrySections);
        getRouteArrays(root, "motorways", routes.motorwaySections);
        getRouteArrays(root, "incompleteMotorways", routes.incompleteMotorwaySections);
        getRouteArrays(root, "coveredMotorways", routes.coveredMotorwaySections);
        getRouteArrays(root, "incompleteARoads", routes.incompleteARoadSections);
        getRouteArrays(root, "coveredARoads", routes.coveredARoadSections);
        routes.serviceStations = root.optJSONArray("stations");
        if (routes.serviceStations == null) routes.serviceStations = new JSONArray();
        JSONArray visited = root.optJSONArray("visited");
        if (visited != null)
            for (int i = 0; i < visited.length(); i++)
                routes.visitedServiceStationIds.add(visited.optString(i));
        routes.matchedJourneys = root.optInt("matchedJourneys");
        routes.retainedPoints = root.optInt("retainedPoints");
        routes.motorwayRetainedPoints = root.optInt("motorwayRetainedPoints");
        routes.incompleteMotorwayPoints = root.optInt("incompleteMotorwayPoints");
        routes.coveredMotorwayPoints = root.optInt("coveredMotorwayPoints");
        routes.incompleteARoadPoints = root.optInt("incompleteARoadPoints");
        routes.coveredARoadPoints = root.optInt("coveredARoadPoints");
        routes.simplified = root.optBoolean("simplified");
        JSONObject percentages = root.optJSONObject("percentages");
        if (percentages != null) {
            java.util.Iterator<String> keys = percentages.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                routes.roadPercentages.put(key, percentages.optDouble(key));
            }
        }
        JSONArray incomplete = root.optJSONArray("incompleteRoadSegments");
        if (incomplete != null) {
            for (int i = 0; i < incomplete.length(); i++) {
                JSONObject item = incomplete.optJSONObject(i);
                JSONArray coordinates = item == null ? null : item.optJSONArray("coordinates");
                if (item == null || coordinates == null) continue;
                RoadCoverageSegment segment = new RoadCoverageSegment(item.optString("label"),
                        coordinates, item.optDouble("completedPercent"),
                        item.optDouble("travelledMetres"), item.optInt("journeyCount"));
                segment.distanceMetres = item.optDouble("distanceMetres", Double.MAX_VALUE);
                routes.incompleteRoadSegments.add(segment);
            }
        }
        return routes;
    }

    private void putRouteArrays(JSONObject root, String name, List<JSONArray> values)
            throws Exception {
        JSONArray array = new JSONArray();
        for (JSONArray value : values) array.put(value);
        root.put(name, array);
    }

    private void getRouteArrays(JSONObject root, String name, List<JSONArray> target) {
        JSONArray array = root.optJSONArray(name);
        if (array == null) return;
        for (int i = 0; i < array.length(); i++) {
            JSONArray route = array.optJSONArray(i);
            if (route != null) target.add(route);
        }
    }

    static final class MapRoutes {
        final List<JSONArray> sections = new ArrayList<>();
        final List<JSONArray> ferrySections = new ArrayList<>();
        int ferryPoints;
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
        final List<RoadCoverageSegment> incompleteRoadSegments = new ArrayList<>();
    }

    private static final class RoadCoverageSegment {
        final String label;
        final JSONArray coordinates;
        final double completedPercent;
        final double travelledMetres;
        final int journeyCount;
        double distanceMetres = Double.MAX_VALUE;

        RoadCoverageSegment(String label, JSONArray coordinates, double completedPercent,
                            double travelledMetres, int journeyCount) {
            this.label = label;
            this.coordinates = coordinates;
            this.completedPercent = completedPercent;
            this.travelledMetres = travelledMetres;
            this.journeyCount = journeyCount;
        }
    }
}







