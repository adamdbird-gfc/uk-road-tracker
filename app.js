let journeys = [];
let footActivities = [];
let footBatches = [];
let footMatching = false;
let footMatchingPaused = false;
let footMatchingBatchId = null;
let footMatchingProgress = null;
let footMatchingError = null;
const footPlaceNames = new Map();
const footPlaceLookups = new Set();
let diagnostics = {};
const persistedMapJourneys = new Map();
const persistedFootActivities = new Map();
let mapArchiveReadyPromise = Promise.resolve();
let footArchiveReadyPromise = Promise.resolve();
let map = null;
let mapRenderingRequested = false;
let traceLayer = null;
let matchedLayer = null;
let creditedLayer = null;
let footLayer = null;
let liveImportLayer = null;
let mapLayerControl = null;
let serviceStationLayer = null;
let mapGeometryRefreshTimer = null;
let creditedMapSegmentCache = {signature:null,segments:[]};
let ignoredJourneys = [];
let importMode = null;
let easyImportPaused = false;
let easyImportRunning = false;
let trackingSessionId = 0;
let importFootStartedAt = null;
let importFootResult = null;
let distanceUnit = 'miles';
let onboardingMode = null;
const manualMotorwayRefs = new Set();
const manualARoadRefs = new Set();
const manualCoverageByRef = new Map();
const persistedCoverageByRef = new Map();
const persistedARoadCoverageByRef = new Map();
const persistedARoadReferenceSummary = new Map();
const persistedManualRefs = new Set();
const persistedManualARoadRefs = new Set();
let persistedDataStartMs = null;
let persistedDataEndMs = null;
let persistedSavedAt = null;
let persistedLegacyCutoffMs = null;
const persistedProcessedJourneyIds = new Set();
const persistedSeenJourneyIds = new Set();
const excludedJourneyIds = new Set();
let persistedSeenJourneyTrackingStarted = true;
const persistedImportedFileHashes = new Set();
let persistedFileHashTrackingStarted = true;
const persistedMotorwayContributionsByJourney = new Map();
const persistedJourneyMileageById = new Map();
let persistedMileageHistoryComplete = true;
const persistedAchievements = new Map();
const persistedConfirmedTimelineVisits = new Map();
let achievementCelebrationOpen = false;
let achievementCelebrationQueue = [];
let achievementCelebrationIndex = 0;
// A correction is a persistent local exclusion for the selected map segment.
// Imports may add fresh journeys, but cannot silently reinstate a correction;
// the user restores it deliberately from the map editor.
const removedSegmentEvidence = new Map();
const canonicalRemovalEvidenceByRef = new Map();
let localSaveTimer = null;
let localProgressDeletionRunning = false;
const canonicalRequestedRefs = new Set();
let refinementRoadRef = null;
let refinementEditMode = null;
let refinementUndoStack = [];
let refinementChunks = [];
let refinementChunkIndex = 0;
let mapCorrectionMode = null;
let mapCorrectionUndoStack = [];
let mapCorrectionChangesPending = false;
let canonicalReferenceLayer = null;
let canonicalCoverageLayer = null;
let canonicalUncoveredLayer = null;
let canonicalARoadCoverageLayer = null;
let canonicalARoadUncoveredLayer = null;
const canonicalRoads = new Map();
const canonicalARoads = new Map();
const canonicalARoadRequestedRefs = new Set();
let canonicalARoadQueueRunning = false;
let canonicalARoadCoverageDirty = true;
let canonicalARoadCoverageRefreshRunning = false;
let canonicalARoadCoverageRefreshProgress = 0;
let canonicalARoadLoadStarted = false;
let canonicalARoadWorkerEpoch = 0;
let canonicalARoadPage = 0;
const CANONICAL_A_ROAD_PAGE_SIZE = 12;
let canonicalLoadQueueRunning = false;
let canonicalCoverageDirty = true;
let motorwayAggregateDirty = true;
let focusedJourneyId = null;
let focusedJourneyType = null;
let journeyLogVisibleCount = 0;
let journeyLogSignature = '';
let journeyLogFilter = 'all';
let journeyLogObserver = null;
const JOURNEY_LOG_PAGE_SIZE = 30;
const API_BASE_URL = 'https://uk-road-tracker-api.onrender.com';
// The live map is a progress cue, not the durable full-detail renderer. Keep
// its work deliberately sparse so a large import never makes page scrolling
// compete with Leaflet painting.
const LIVE_IMPORT_BATCH_SIZE = 50;
const LIVE_IMPORT_PREVIEW_INTERVAL = 5;

const fileInput = document.getElementById('timelineFile');
const fileStatus = document.getElementById('fileStatus');
const summaryCard = document.getElementById('summaryCard');
const mapCard = document.getElementById('mapCard');
const nextCard = document.getElementById('nextCard');
const journeyList = document.getElementById('journeyList');
const journeyLogCard = document.getElementById('journeyLogCard');
const journeyLogList = document.getElementById('journeyLogList');
const journeyLogCount = document.getElementById('journeyLogCount');
const journeyFocusBar = document.getElementById('journeyFocusBar');
const closeJourneyFocus = document.getElementById('closeJourneyFocus');
const journeyCount = document.getElementById('journeyCount');
const pointCount = document.getElementById('pointCount');
const selectedCount = document.getElementById('selectedCount');
const dataDateRange = document.getElementById('dataDateRange');
const mapStatus = document.getElementById('mapStatus');
const importModeCard = document.getElementById('importModeCard');
const easyProgress = document.getElementById('easyProgress');
const easyProgressText = document.getElementById('easyProgressText');
const easyProgressBar = document.getElementById('easyProgressBar');
const ignoredCard = document.getElementById('ignoredCard');
const ignoredCount = document.getElementById('ignoredCount');
const ignoredList = document.getElementById('ignoredList');
const footQueueCard = document.getElementById('footQueueCard');
const footProgressText = document.getElementById('footProgressText');
const footProgressBar = document.getElementById('footProgressBar');
const startFootBatch = document.getElementById('startFootBatch');
const pauseFootMatching = document.getElementById('pauseFootMatching');
const clearImportedData = document.getElementById('clearImportedData');
const clearRoadData = document.getElementById('clearRoadData');
const clearFootData = document.getElementById('clearFootData');
const travelStatsCard = document.getElementById('travelStatsCard');
const travelStats = {
  total:document.getElementById('totalDistanceTravelled'),
  driving:document.getElementById('drivingDistanceTravelled'),
  foot:document.getElementById('footDistanceTravelled'),
  uniqueTotal:document.getElementById('uniqueDistanceTravelled'),
  uniqueDriving:document.getElementById('uniqueDrivingDistance'),
  uniqueFoot:document.getElementById('uniqueFootDistance'),
  uniqueDrivingPercent:document.getElementById('uniqueDrivingPercent'),
  activities:document.getElementById('recordedActivityCount')
};
const achievementsCard = document.getElementById('achievementsCard');
const achievementCount = document.getElementById('achievementCount');
const achievementList = document.getElementById('achievementList');
const achievementCelebration = document.getElementById('achievementCelebration');
const closeAchievementCelebration = document.getElementById('closeAchievementCelebration');
const previousAchievementCelebration = document.getElementById('previousAchievementCelebration');
const nextAchievementCelebration = document.getElementById('nextAchievementCelebration');
const achievementCelebrationPosition = document.getElementById('achievementCelebrationPosition');
const achievementCelebrationIcon = document.getElementById('achievementCelebrationIcon');
const achievementCelebrationTitle = document.getElementById('achievementCelebrationTitle');
const achievementCelebrationDescription = document.getElementById('achievementCelebrationDescription');
const achievementCelebrationDetail = document.getElementById('achievementCelebrationDetail');
const motorwayCard = document.getElementById('motorwayCard');
const motorwayList = document.getElementById('motorwayList');
const motorwaysDiscovered = document.getElementById('motorwaysDiscovered');
const aRoadCard = document.getElementById('aRoadCard');
const aRoadList = document.getElementById('aRoadList');
const aRoadsDiscovered = document.getElementById('aRoadsDiscovered');
const aRoadMileage = document.getElementById('aRoadMileage');
const timelineRoadMileage = document.getElementById('timelineRoadMileage');
const unitMiles = document.getElementById('unitMiles');
const unitKm = document.getElementById('unitKm');
const aRoadUnitMiles = document.getElementById('aRoadUnitMiles');
const aRoadUnitKm = document.getElementById('aRoadUnitKm');
const otherRoadCard = document.getElementById('otherRoadCard');
const otherRoadMileage = document.getElementById('otherRoadMileage');
const canonicalMotorwayCard = document.getElementById('canonicalMotorwayCard');
const canonicalMotorwayList = document.getElementById('canonicalMotorwayList');
const canonicalRoadsReady = document.getElementById('canonicalRoadsReady');
const canonicalMotorwayStatus = document.getElementById('canonicalMotorwayStatus');
const canonicalRetry = document.getElementById('canonicalRetry');
const canonicalARoadCard = document.getElementById('canonicalARoadCard');
const canonicalARoadList = document.getElementById('canonicalARoadList');
const canonicalARoadsReady = document.getElementById('canonicalARoadsReady');
const canonicalARoadStatus = document.getElementById('canonicalARoadStatus');
const canonicalARoadRetry = document.getElementById('canonicalARoadRetry');
const canonicalARoadPrevious = document.getElementById('canonicalARoadPrevious');
const canonicalARoadNext = document.getElementById('canonicalARoadNext');
const canonicalARoadPageStatus = document.getElementById('canonicalARoadPageStatus');
const networkProgressPercent = document.getElementById('networkProgressPercent');
const networkProgressDistance = document.getElementById('networkProgressDistance');
const networkProgressBar = document.getElementById('networkProgressBar');
const networkProgressFill = document.getElementById('networkProgressFill');
const gbProgressPercent = document.getElementById('gbProgressPercent');
const gbProgressDistance = document.getElementById('gbProgressDistance');
const niProgressPercent = document.getElementById('niProgressPercent');
const niProgressDistance = document.getElementById('niProgressDistance');
const onboardingCard = document.getElementById('onboardingCard');
const dataSourceCard = document.getElementById('dataSourceCard');
const manualMotorwayCard = document.getElementById('manualMotorwayCard');
const manualMotorwayList = document.getElementById('manualMotorwayList');
const manualSelectedCount = document.getElementById('manualSelectedCount');
const manualARoadList = document.getElementById('manualARoadList');
const manualARoadSelectedCount = document.getElementById('manualARoadSelectedCount');
const mapTitle = document.getElementById('mapTitle');
const mapIntro = document.getElementById('mapIntro');
const refinementPanel = document.getElementById('refinementPanel');
const refinementTitle = document.getElementById('refinementTitle');
const refinementMark = document.getElementById('refinementMark');
const refinementErase = document.getElementById('refinementErase');
const refinementUndo = document.getElementById('refinementUndo');
const refinementChunkStatus = document.getElementById('refinementChunkStatus');
const localProgressNotice = document.getElementById('localProgressNotice');
const localProgressSummary = document.getElementById('localProgressSummary');
const closeSavedProgress = document.getElementById('closeSavedProgress');
const mapCorrectionStartButton = document.getElementById('startMapCorrection');
const mapCorrectionPanel = document.getElementById('mapCorrectionPanel');
const mapCorrectionFinishButton = document.getElementById('finishMapCorrection');
const mapCorrectionRemove = document.getElementById('mapCorrectionRemove');
const mapCorrectionRestore = document.getElementById('mapCorrectionRestore');
const mapCorrectionUndo = document.getElementById('mapCorrectionUndo');

// 2025 official totals: 2,300 motorway miles in Great Britain plus
// approximately 65 miles in Northern Ireland (0.4% of 25,970 km).
const GB_MOTORWAY_NETWORK_MILES = 2300;
const NI_MOTORWAY_NETWORK_MILES = 65;
const UK_MOTORWAY_NETWORK_MILES = GB_MOTORWAY_NETWORK_MILES + NI_MOTORWAY_NETWORK_MILES;
const GB_MOTORWAY_NETWORK_KM = GB_MOTORWAY_NETWORK_MILES / 0.6213711922;
const NI_MOTORWAY_NETWORK_KM = NI_MOTORWAY_NETWORK_MILES / 0.6213711922;
const UK_MOTORWAY_NETWORK_KM = UK_MOTORWAY_NETWORK_MILES / 0.6213711922;

const MOTORWAY_LENGTH_KM = {
  M1:311.946, M2:41.210, M3:98.947, M4:194.212, M5:260.202, M6:423.978,
  M11:84.419, M18:45.214, M20:82.586, M23:26.725, M25:189.869, M26:16.462,
  M27:52.695, M32:7.303, M40:144.651, M42:64.619, M45:13.369, M48:8.899,
  M49:8.611, M50:34.438, M53:32.032, M54:36.078, M55:19.069, M56:55.688,
  M57:16.050, M58:18.657, M60:56.734, M61:43.989, M62:153.828, M65:32.238,
  M66:14.297, M67:7.656, M69:26.269, M180:41.076, M181:4.190,
  M271:3.537, M275:3.0, M602:6.958, M606:4.663, M621:14.803,
  M8:97.0, M9:53.1, M73:11.0, M74:56.0, M77:32.0, M80:40.0,
  M90:78.0, M876:13.0, M898:2.0, 'A74(M)':72.0
};
// Curated manual starter network: A(M) roads remain motorways.
const GB_MANUAL_A_ROAD_REFS = Array.from({length:99},(_,index)=>'A'+(index+1));
const NI_MANUAL_A_ROAD_REFS = ['A1','A2','A3','A4','A5','A6','A7','A8','A11','A12',...Array.from({length:21},(_,index)=>'A'+(index+20)),...Array.from({length:14},(_,index)=>'A'+(index+42)),'A57','A76','A99'].filter(ref=>ref!=='A53');

const NI_MOTORWAY_LENGTH_KM = {
  M1:61, M2:37, M3:1.3, M5:3.2, M12:2.4, M22:9
};
const CANONICAL_REFERENCE_SAMPLE_M = 100;
const CANONICAL_MATCH_SAMPLE_M = 25;
const CANONICAL_ANCHOR_MATCH_RADIUS_M = 110;
const CANONICAL_INDEX_CELL_M = 260;
const CANONICAL_DEDUPE_CELL_M = 110;
const CANONICAL_DEDUPE_RADIUS_M = 95;
const CANONICAL_CACHE_VERSION = 'v1';
const CANONICAL_CACHE_URL = `canonical-motorways-${CANONICAL_CACHE_VERSION}.json`;
const CANONICAL_A_ROAD_CACHE_VERSION = 'v4';
const CANONICAL_A_ROAD_CACHE_ROOT = `canonical-a-roads-${CANONICAL_A_ROAD_CACHE_VERSION}`;
const CANONICAL_A_ROAD_CACHE_URL = file => `${CANONICAL_A_ROAD_CACHE_ROOT}/${encodeURIComponent(file)}`;
const CANONICAL_A_ROAD_CACHE_INDEX_URL = `canonical-a-roads-${CANONICAL_A_ROAD_CACHE_VERSION}/index.json`;
const CANONICAL_A_ROAD_INDEX_STORAGE_KEY = `roadprints:canonical-a-road-index:${CANONICAL_A_ROAD_CACHE_VERSION}`;
const CANONICAL_A_ROAD_REQUEST_TIMEOUT_MS = 45000;
const LOCAL_PROGRESS_KEY = 'uk-road-tracker-progress-v1';
const FOOT_PLACE_NAMES_KEY = 'roadprints-foot-place-names-v1';
const MAP_ARCHIVE_DB_NAME = 'roadprints-map-archive';
const MAP_ARCHIVE_DB_VERSION = 4;
const MAP_ARCHIVE_STORE_NAME = 'journeys';
const FOOT_ACTIVITY_STORE_NAME = 'foot-activities';
const CANONICAL_ROAD_STORE_NAME = 'canonical-roads';
const CANONICAL_A_ROAD_STORE_NAME = 'canonical-a-roads';
let canonicalCache = null;
let canonicalCachePromise = null;
let canonicalARoadCacheEntries = new Map();
let canonicalARoadCacheIndexPromise = null;
let canonicalARoadCacheIndexAvailable = false;
let canonicalARoadCacheIndexError = null;

// Achievement locations are assessed against the same fixed motorway anchors
// used for completion, never a raw Timeline point. This keeps a celebration
// tied to evidence that the user was actually on that motorway.
const ROADPRINTS_ACHIEVEMENTS = [
  {
    id:'motorway-quarter',
    icon:'¼',
    title:'Quarter Marker',
    description:'Complete one quarter of the UK motorway network.',
    detail:'25% of the UK motorway network completed',
    type:'network-percent', target:25
  },
  {
    id:'motorway-halfway',
    icon:'½',
    title:'Halfway There',
    description:'Complete half of the UK motorway network.',
    detail:'50% of the UK motorway network completed',
    type:'network-percent', target:50
  },
  {
    id:'motorway-three-quarters',
    icon:'¾',
    title:'The Home Straight',
    description:'Complete three quarters of the UK motorway network.',
    detail:'75% of the UK motorway network completed',
    type:'network-percent', target:75
  },
  {
    id:'motorway-complete',
    icon:'★',
    title:'Completed It, Mate',
    description:'Complete the entire UK motorway network.',
    detail:'100% of the UK motorway network completed',
    type:'network-percent', target:100
  },
  {
    id:'m1-pioneer',
    icon:'①',
    title:'The Pioneer',
    description:'Drive on the M1, Britain’s first inter-urban motorway.',
    detail:'M1 · Britain’s first inter-urban motorway, opened in 1959',
    type:'motorway-visited', roadId:'M1'
  },
  {
    id:'m62-summit',
    icon:'🏔️',
    title:'M62 Summit',
    description:'Cross the UK’s highest motorway point at Windy Hill.',
    detail:'372 m (1,221 ft) above sea level · M62, near junction 22',
    type:'summit', roadId:'M62',
    summit:[-2.018561,53.62982],
    radiusM:350
  },
  {
    id:'angel-of-the-north',
    icon:'👼',
    title:'I Saw an Angel',
    description:'Drive the A1 alongside the Angel of the North in Gateshead.',
    detail:'A1 · Angel of the North, Gateshead',
    type:'a-road-landmark', roadId:'GB:A1',
    landmark:[-1.5908431,54.91330845],
    radiusM:250
  },
  {
    id:'stonehenge-solstice',
    icon:'🌞',
    title:'Enjoying the Solstice',
    description:'Drive the A303 past Stonehenge.',
    detail:'A303 · Stonehenge, Wiltshire',
    type:'a-road-landmark', roadId:'GB:A303',
    landmark:[-1.8262,51.1789],
    radiusM:500
  },
  {
    id:'spanning-the-nation',
    icon:'🌉',
    title:'Spanning the Nation',
    description:'Complete the UK’s great road crossings.',
    detail:'Seven great road crossings completed',
    type:'crossing-set',
    crossings:[
      {id:'dartford',title:'Dartford Crossing',hint:'A282 · bridge or tunnels',locations:[[0.265,51.462]],radiusM:900},
      {id:'severn',title:'Severn Crossing',hint:'M4 or M48',locations:[[-2.695,51.553],[-2.642,51.553],[-2.592,51.553],[-2.705,51.611],[-2.647,51.611],[-2.590,51.611]],radiusM:1250,zones:[{west:-2.735,east:-2.535,south:51.535,north:51.585},{west:-2.735,east:-2.535,south:51.585,north:51.640}]},
      {id:'humber',title:'Humber Bridge',hint:'A15 · near Hull',locations:[[-0.317,53.708]],radiusM:550},
      {id:'blackwall',title:'Blackwall Tunnel',hint:'A102 · London',locations:[[0.007,51.500]],radiusM:350},
      {id:'tyne',title:'Tyne Tunnels',hint:'A19 · near Jarrow',locations:[[-1.495,54.985]],radiusM:550},
      {id:'mersey',title:'Mersey Tunnel',hint:'Kingsway or Queensway',locations:[[-2.993,53.405]],radiusM:650},
      {id:'queensferry',title:'Queensferry Crossing',hint:'M90 · Forth',locations:[[-3.415,56.001]],radiusM:650}
    ]
  }
];


const MOTORWAY_CORRIDOR_CELL_M = 100;
const MOTORWAY_SAMPLE_SPACING_M = 25;
const DEFAULT_MAP_CENTER = [53.3, -1.8];
const DEFAULT_MAP_ZOOM = 6;

function showDefaultUnitedKingdomView() {
  if (!map) return;
  map.setView(DEFAULT_MAP_CENTER,DEFAULT_MAP_ZOOM);
  requestAnimationFrame(()=>map.invalidateSize(true));
}

function localProgressRoadCount() {
  return [...persistedCoverageByRef.values()].filter(ids=>ids.size).length;
}

function localProgressJourneyCount() {
  return persistedMapJourneys.size;
}

function shouldShowDataDashboard() {
  return onboardingMode==='data' || onboardingMode==='saved';
}

function updateDataDeletionControls() {
  const hasRoadData=
    persistedMapJourneys.size>0 ||
    persistedManualRefs.size>0 ||
    persistedManualARoadRefs.size>0 ||
    persistedCoverageByRef.size>0 ||
    persistedARoadCoverageByRef.size>0;
  const hasFootData=persistedFootActivities.size>0;
  clearRoadData.disabled=!hasRoadData;
  clearFootData.disabled=!hasFootData;
  clearImportedData.disabled=!hasRoadData && !hasFootData;
}

function updateLocalProgressNotice() {
  const roadCount=localProgressRoadCount();
  const journeyCount=localProgressJourneyCount();
  const hasProgress=roadCount>0 || persistedManualRefs.size>0 || persistedManualARoadRefs.size>0 || journeyCount>0 || persistedFootActivities.size>0;
  updateDataDeletionControls();
  localProgressNotice.classList.toggle('hidden',!hasProgress);
  if (!hasProgress) return;

  const savedLabel=persistedSavedAt
    ? new Intl.DateTimeFormat('en-GB',{
        day:'2-digit',month:'short',year:'numeric',hour:'2-digit',minute:'2-digit'
      }).format(new Date(persistedSavedAt))
    : 'previously';
  const range=formatDataDateRange({
    dataStartMs:persistedDataStartMs,
    dataEndMs:persistedDataEndMs
  });
  localProgressSummary.textContent=
    `${journeyCount.toLocaleString()} saved matched road journey${journeyCount===1?'':'s'} · ` +
    `${persistedFootActivities.size.toLocaleString()} on-foot activit${persistedFootActivities.size===1?'y':'ies'} · ` +
    `${roadCount} motorway${roadCount===1?'':'s'} with saved coverage · ${range} · saved ${savedLabel}.`;
}

function openMapArchiveDatabase() {
  return new Promise((resolve,reject)=>{
    if (!globalThis.indexedDB) {
      reject(new Error('This browser does not provide IndexedDB storage.'));
      return;
    }
    const request=indexedDB.open(MAP_ARCHIVE_DB_NAME,MAP_ARCHIVE_DB_VERSION);
    request.onerror=()=>reject(request.error || new Error('Saved map storage could not be opened.'));
    request.onupgradeneeded=()=>{
      const database=request.result;
      if (!database.objectStoreNames.contains(MAP_ARCHIVE_STORE_NAME)) {
        database.createObjectStore(MAP_ARCHIVE_STORE_NAME,{keyPath:'id'});
      }
      if (!database.objectStoreNames.contains(FOOT_ACTIVITY_STORE_NAME)) {
        database.createObjectStore(FOOT_ACTIVITY_STORE_NAME,{keyPath:'id'});
      }
      if (!database.objectStoreNames.contains(CANONICAL_ROAD_STORE_NAME)) {
        database.createObjectStore(CANONICAL_ROAD_STORE_NAME,{keyPath:'id'});
      }
      if (!database.objectStoreNames.contains(CANONICAL_A_ROAD_STORE_NAME)) {
        database.createObjectStore(CANONICAL_A_ROAD_STORE_NAME,{keyPath:'id'});
      }
    };
    request.onsuccess=()=>resolve(request.result);
  });
}

async function mapArchiveOperation(mode,operation) {
  const database=await openMapArchiveDatabase();
  try {
    return await new Promise((resolve,reject)=>{
      const transaction=database.transaction(MAP_ARCHIVE_STORE_NAME,mode);
      const store=transaction.objectStore(MAP_ARCHIVE_STORE_NAME);
      const request=operation(store);
      request.onerror=()=>reject(request.error || new Error('Saved map operation failed.'));
      request.onsuccess=()=>resolve(request.result);
      transaction.onabort=()=>reject(transaction.error || new Error('Saved map transaction was aborted.'));
    });
  } finally {
    database.close();
  }
}

async function footArchiveOperation(mode,operation) {
  const database=await openMapArchiveDatabase();
  try {
    return await new Promise((resolve,reject)=>{
      const transaction=database.transaction(FOOT_ACTIVITY_STORE_NAME,mode);
      const store=transaction.objectStore(FOOT_ACTIVITY_STORE_NAME);
      const request=operation(store);
      request.onerror=()=>reject(request.error || new Error('Saved on-foot activity operation failed.'));
      request.onsuccess=()=>resolve(request.result);
      transaction.onabort=()=>reject(transaction.error || new Error('Saved on-foot activity transaction was aborted.'));
    });
  } finally { database.close(); }
}

async function canonicalRoadArchiveOperation(mode,operation) {
  const database=await openMapArchiveDatabase();
  try {
    return await new Promise((resolve,reject)=>{
      const transaction=database.transaction(CANONICAL_ROAD_STORE_NAME,mode);
      const store=transaction.objectStore(CANONICAL_ROAD_STORE_NAME);
      const request=operation(store);
      request.onerror=()=>reject(request.error || new Error('Saved motorway reference operation failed.'));
      request.onsuccess=()=>resolve(request.result);
      transaction.onabort=()=>reject(transaction.error || new Error('Saved motorway reference transaction was aborted.'));
    });
  } finally { database.close(); }
}

async function canonicalARoadArchiveOperation(mode,operation) {
  const database=await openMapArchiveDatabase();
  try {
    return await new Promise((resolve,reject)=>{
      const transaction=database.transaction(CANONICAL_A_ROAD_STORE_NAME,mode);
      const store=transaction.objectStore(CANONICAL_A_ROAD_STORE_NAME);
      const request=operation(store);
      request.onerror=()=>reject(request.error || new Error('Saved A-road reference operation failed.'));
      request.onsuccess=()=>resolve(request.result);
      transaction.onabort=()=>reject(transaction.error || new Error('Saved A-road reference transaction was aborted.'));
    });
  } finally { database.close(); }
}

function compactMapJourney(journey) {
  const id=journeyIdentity(journey);
  if (!id || !journey?.matchedGeoJson) return null;
  return {
    id,
    start:journey.start || '',
    end:journey.end || '',
    googleDistanceKm:Number.isFinite(Number(journey.googleDistanceKm)) ? Number(journey.googleDistanceKm) : null,
    repeatJourneyIds:Array.isArray(journey.repeatJourneyIds) ? journey.repeatJourneyIds : null,
    repeatCount:Number(journey.repeatCount || 1),
    repeatDistanceKm:Number(journey.repeatDistanceKm || journey.googleDistanceKm || 0),
    pathPointCount:Number(journey.pathPointCount || journey.points?.length || 0),
    points:(journey.points || []).filter(validPoint).map(point=>({lat:Number(point.lat),lng:Number(point.lng)})),
    matchedGeoJson:journey.matchedGeoJson,
    motorwayGeoJson:journey.motorwayGeoJson || {type:'FeatureCollection',features:[]},
    aRoadGeoJson:journey.aRoadGeoJson || {type:'FeatureCollection',features:[]},
    roadGeoJson:journey.roadGeoJson || {type:'FeatureCollection',features:[]},
    otherRoadDistanceKm:typeof journey.otherRoadDistanceKm==='number' && Number.isFinite(journey.otherRoadDistanceKm)
      ? journey.otherRoadDistanceKm
      : null,
    matchedDistanceKm:Number(journey.matchedDistanceKm || 0),
    matchedTracepoints:Number(journey.matchedTracepoints || 0),
    pointsSentToMatcher:Number(journey.pointsSentToMatcher || 0),
    matchQuality:journey.matchQuality || null,
    title:String(journey.title || '')
  };
}

function hydrateMapJourney(record) {
  return {
    ...record,
    points:Array.isArray(record?.points) ? record.points : [],
    pathPointCount:Number(record?.pathPointCount || record?.points?.length || 0),
    selected:true,
    _savedArchive:true
  };
}

async function loadMapArchive() {
  try {
    const records=await mapArchiveOperation('readonly',store=>store.getAll());
    persistedMapJourneys.clear();
    for (const record of records || []) {
      if (record?.id && record?.matchedGeoJson) persistedMapJourneys.set(record.id,record);
    }
    renderRoadQueue();
    renderCollectiveStats();
    renderJourneyLog();
  } catch (err) {
    console.warn('Saved map journeys could not be loaded:',err);
  }
}

async function saveJourneyToMapArchive(journey) {
  const record=compactMapJourney(journey);
  if (!record) throw new Error('The matched journey did not contain saveable map geometry.');
  await mapArchiveOperation('readwrite',store=>store.put(record));
  persistedMapJourneys.set(record.id,record);
  updateLocalProgressNotice();
  renderJourneyLog();
  window.dispatchEvent(new Event('roadprints:archivechange'));
}

async function clearRoadArchive() {
  await mapArchiveOperation('readwrite',store=>store.clear());
  persistedMapJourneys.clear();
  persistedJourneyMileageById.clear();
  persistedMotorwayContributionsByJourney.clear();
  await canonicalRoadArchiveOperation('readwrite',store=>store.clear());
  await canonicalARoadArchiveOperation('readwrite',store=>store.clear());
  window.dispatchEvent(new Event('roadprints:archivechange'));
}

async function clearFootArchive() {
  await footArchiveOperation('readwrite',store=>store.clear());
  persistedFootActivities.clear();
  footActivities=[];
  footBatches=[];
  renderFootQueue();
  window.dispatchEvent(new Event('roadprints:archivechange'));
}

async function clearMapArchive() {
  await clearRoadArchive();
  await clearFootArchive();
}

function compactFootActivity(activity) {
  return {
    id:journeyIdentity(activity), start:activity.start || '', end:activity.end || '',
    travelMode:activity.travelMode || 'WALKING', googleDistanceKm:Number(activity.googleDistanceKm || 0),
    pathPointCount:Number(activity.pathPointCount || 0), points:(activity.points || []).filter(validPoint),
    matchedGeoJson:activity.matchedGeoJson || null, roadGeoJson:activity.roadGeoJson || {type:'FeatureCollection',features:[]}, matchQuality:activity.matchQuality || null,
    matchError:activity.matchError || null,
    title:String(activity.title || '')
  };
}

async function loadFootActivityArchive() {
  try {
    const records=await footArchiveOperation('readonly',store=>store.getAll());
    persistedFootActivities.clear();
    for (const record of records || []) if (record?.id) persistedFootActivities.set(record.id,{...record,selected:true});
    footActivities=[...persistedFootActivities.values()];
    buildFootBatches();
    renderFootQueue();
    renderCollectiveStats();
    // Saved walking/running routes are a persistent queue: after a refresh or a
    // browser restart, continue with any representative routes still awaiting a match.
    if (footActivities.some(activity=>!activity.matchedGeoJson && !activity.matchError)) {
      setTimeout(()=>{ if (!footMatching) void startNextFootBatch(); },0);
    }
  } catch (err) { console.warn('Saved on-foot activities could not be loaded:',err); }
}

async function saveFootActivities(activities) {
  activities=activities.filter(activity=>!excludedJourneyIds.has(journeyIdentity(activity)));
  const database=await openMapArchiveDatabase();
  try {
    await new Promise((resolve,reject)=>{
      const transaction=database.transaction(FOOT_ACTIVITY_STORE_NAME,'readwrite');
      const store=transaction.objectStore(FOOT_ACTIVITY_STORE_NAME);
      for (const activity of activities) {
        const record=compactFootActivity(activity);
        const prior=persistedFootActivities.get(record.id);
        // Timeline does not provide a user title. Preserve the name the user
        // gave this on-foot activity when the same activity is reimported.
        const title=record.title.trim() ? record.title : String(prior?.title || '');
        const updated={...record,title};
        store.put(prior?.matchedGeoJson ? {...updated,matchedGeoJson:prior.matchedGeoJson,matchQuality:prior.matchQuality} : updated);
      }
      transaction.oncomplete=resolve;
      transaction.onerror=()=>reject(transaction.error || new Error('Could not save on-foot activities.'));
    });
    for (const activity of activities) {
      const record=compactFootActivity(activity), prior=persistedFootActivities.get(record.id);
      const title=record.title.trim() ? record.title : String(prior?.title || '');
      const updated={...record,title};
      persistedFootActivities.set(record.id,prior?.matchedGeoJson ? {...updated,matchedGeoJson:prior.matchedGeoJson,matchQuality:prior.matchQuality,selected:true} : {...updated,selected:true});
    }
    footActivities=[...persistedFootActivities.values()];
    buildFootBatches(); renderFootQueue(); renderCollectiveStats();
  } finally { database.close(); }
}

async function saveFootActivityMatch(activity) {
  const ids=Array.isArray(activity.repeatJourneyIds) && activity.repeatJourneyIds.length ? activity.repeatJourneyIds : [journeyIdentity(activity)];
  for (const id of ids) {
    const source=persistedFootActivities.get(id) || activity;
    const record=compactFootActivity({...source,matchedGeoJson:activity.matchedGeoJson,roadGeoJson:activity.roadGeoJson,matchQuality:activity.matchQuality,matchError:activity.matchError});
    await footArchiveOperation('readwrite',store=>store.put(record));
    persistedFootActivities.set(record.id,{...record,selected:true});
  }
  footActivities=[...persistedFootActivities.values()];
  renderCollectiveStats();
}

function savedMapJourneysExcluding(excludedIds=new Set()) {
  return [...persistedMapJourneys.values()]
    .filter(record=>!excludedIds.has(record.id))
    .map(hydrateMapJourney);
}

function currentImportJourneys() {
  return journeys.filter(journey=>!journey._savedArchive);
}

function loadLocalProgress() {
  try {
    const raw=localStorage.getItem(LOCAL_PROGRESS_KEY);
    if (!raw) return;
    const saved=JSON.parse(raw);
    if (!saved || ![1,2,3,4,5,6,7,8].includes(saved.version) || saved.canonicalVersion!==CANONICAL_CACHE_VERSION) return;

    for (const [id,ids] of Object.entries(saved.coverage || {})) {
      if (Array.isArray(ids)) persistedCoverageByRef.set(id,new Set(ids.map(Number).filter(Number.isInteger)));
    }
    for (const [id,summary] of Object.entries(saved.aRoadReferenceSummary || {})) {
      if (!summary || !Number.isFinite(Number(summary.totalKm)) || !Number.isFinite(Number(summary.anchorCount))) continue;
      persistedARoadReferenceSummary.set(id,{
        totalKm:Number(summary.totalKm),anchorCount:Number(summary.anchorCount),coveredCount:Number(summary.coveredCount || 0)
      });
    }
    // A-road anchor coverage is deliberately not restored from localStorage.
    // At national scale it can contain hundreds of thousands of positional
    // ids, making synchronous JSON parse/save freeze mobile browsers. The
    // compact reference geometry is retained in IndexedDB and coverage is
    // rebuilt safely from the saved matched journeys instead.
    persistedARoadCoverageByRef.clear();
    for (const id of saved.manualMotorways || []) persistedManualRefs.add(String(id));
    for (const id of saved.manualARoads || []) if (parseARoadKey(id)) persistedManualARoadRefs.add(String(id));
    persistedDataStartMs=saved.dataStartMs===null || saved.dataStartMs===undefined
      ? null
      : Number.isFinite(Number(saved.dataStartMs)) ? Number(saved.dataStartMs) : null;
    persistedDataEndMs=saved.dataEndMs===null || saved.dataEndMs===undefined
      ? null
      : Number.isFinite(Number(saved.dataEndMs)) ? Number(saved.dataEndMs) : null;
    persistedSavedAt=saved.savedAt || null;
    const storedLegacyCutoff=Number(saved.legacyCutoffMs);
    const migratedLegacyCutoff=Number(saved.dataEndMs);
    persistedLegacyCutoffMs=Number.isFinite(storedLegacyCutoff)
      ? storedLegacyCutoff
      : Number.isFinite(migratedLegacyCutoff) ? migratedLegacyCutoff : null;
    for (const id of saved.processedJourneyIds || []) {
      if (typeof id==='string' && id) persistedProcessedJourneyIds.add(id);
    }
    const hasSeenJourneyState =
      saved.seenJourneyTrackingStarted === true ||
      Array.isArray(saved.seenJourneyIds);
    persistedSeenJourneyTrackingStarted =
      hasSeenJourneyState || persistedProcessedJourneyIds.size === 0;
    for (const id of saved.seenJourneyIds || []) {
      if (typeof id==='string' && id) persistedSeenJourneyIds.add(id);
    }
    for (const id of saved.excludedJourneyIds || []) {
      if (typeof id==='string' && id) excludedJourneyIds.add(id);
    }
    for (const id of persistedProcessedJourneyIds) persistedSeenJourneyIds.add(id);
    const hasFileHashState =
      saved.fileHashTrackingStarted === true ||
      Array.isArray(saved.importedFileHashes);
    persistedFileHashTrackingStarted =
      hasFileHashState || persistedProcessedJourneyIds.size === 0;
    for (const hash of saved.importedFileHashes || []) {
      if (typeof hash==='string' && hash) persistedImportedFileHashes.add(hash);
    }
    for (const [journeyId,contributions] of Object.entries(saved.version>=2 ? saved.motorwayContributionsByJourney || {} : {})) {
      if (!journeyId || !contributions || typeof contributions!=='object') continue;
      const clean={};
      for (const [roadId,distanceM] of Object.entries(contributions)) {
        const value=Number(distanceM);
        if (roadId && Number.isFinite(value) && value>0) clean[roadId]=value;
      }
      if (Object.keys(clean).length) persistedMotorwayContributionsByJourney.set(journeyId,clean);
    }
    for (const [journeyId,distanceKm] of Object.entries(saved.journeyMileageById || {})) {
      const value=Number(distanceKm);
      if (journeyId && Number.isFinite(value) && value>=0) persistedJourneyMileageById.set(journeyId,value);
    }
    for (const [id,achievement] of Object.entries(saved.achievements || {})) {
      if (!id || !achievement || typeof achievement!=='object') continue;
      persistedAchievements.set(id,{
        unlockedAt:typeof achievement.unlockedAt==='string' ? achievement.unlockedAt : null,
        announced:achievement.announced===true
      });
    }
    for (const visit of saved.confirmedTimelineVisits || []) {
      const lat=Number(visit?.lat),lng=Number(visit?.lng);
      if (!Number.isFinite(lat) || !Number.isFinite(lng)) continue;
      const cleaned={
        id:String(visit?.id || ''),
        start:visit?.start || null,
        end:visit?.end || null,
        lat,lng,
        name:typeof visit?.name==='string' ? visit.name : null
      };
      if (!cleaned.id) cleaned.id=timelineVisitFingerprint(cleaned);
      persistedConfirmedTimelineVisits.set(cleaned.id,cleaned);
    }
    for (const [segmentId,journeyIds] of Object.entries(saved.removedSegmentEvidence || {})) {
      if (!segmentId || !Array.isArray(journeyIds)) continue;
      const ids=new Set(journeyIds.filter(id=>typeof id==='string' && id));
      if (ids.size) removedSegmentEvidence.set(segmentId,ids);
    }
    for (const [roadId,anchors] of Object.entries(saved.canonicalRemovalEvidence || {})) {
      if (!roadId || !anchors || typeof anchors!=='object') continue;
      const byAnchor=new Map();
      for (const [anchorId,journeyIds] of Object.entries(anchors)) {
        const id=Number(anchorId);
        if (!Number.isInteger(id) || !Array.isArray(journeyIds)) continue;
        const ids=new Set(journeyIds.filter(value=>typeof value==='string' && value));
        if (ids.size) byAnchor.set(id,ids);
      }
      if (byAnchor.size) canonicalRemovalEvidenceByRef.set(roadId,byAnchor);
    }
    persistedMileageHistoryComplete=typeof saved.mileageHistoryComplete==='boolean'
      ? saved.mileageHistoryComplete
      : persistedProcessedJourneyIds.size===0;
    // A completed save already contains the canonical motorway sections and
    // per-journey motorway contributions. Reuse those on "View saved data";
    // only new matches or saved map edits make either calculation dirty again.
    if (persistedCoverageByRef.size) canonicalCoverageDirty=false;
    if (persistedMotorwayContributionsByJourney.size && persistedMileageHistoryComplete) {
      motorwayAggregateDirty=false;
    }
    if (saved.distanceUnit==='km') distanceUnit='km';
  } catch (err) {
    console.warn('Saved local progress could not be read:',err);
  }
}

function saveLocalProgressNow() {
  if (localProgressDeletionRunning) return;
  try {
    for (const road of canonicalRoads.values()) {
      if (road.status==='ready') {
        persistedCoverageByRef.set(road.id,new Set(road.coveredAnchorIds));
      }
    }
    for (const road of canonicalARoads.values()) {
      if (road.status==='ready') {
        persistedARoadCoverageByRef.set(road.id,new Set(road.coveredAnchorIds));
        persistedARoadReferenceSummary.set(road.id,{
          totalKm:road.totalKm,anchorCount:road.anchors.length || road.anchorCount || 0,
          coveredCount:road.coveredAnchorIds.size || road.coveredAnchorCount || 0
        });
      }
    }

    const coverage={};
    for (const [id,ids] of persistedCoverageByRef) {
      if (ids.size) coverage[id]=[...ids].sort((a,b)=>a-b);
    }

    persistedSavedAt=new Date().toISOString();
    localStorage.setItem(LOCAL_PROGRESS_KEY,JSON.stringify({
      version:8,
      canonicalVersion:CANONICAL_CACHE_VERSION,
      savedAt:persistedSavedAt,
      distanceUnit,
      dataStartMs:persistedDataStartMs,
      dataEndMs:persistedDataEndMs,
      legacyCutoffMs:persistedLegacyCutoffMs,
      processedJourneyIds:[...persistedProcessedJourneyIds].sort(),
      seenJourneyTrackingStarted:persistedSeenJourneyTrackingStarted,
      seenJourneyIds:[...persistedSeenJourneyIds].sort(),
      excludedJourneyIds:[...excludedJourneyIds].sort(),
      fileHashTrackingStarted:persistedFileHashTrackingStarted,
      importedFileHashes:[...persistedImportedFileHashes].sort(),
      motorwayContributionsByJourney:Object.fromEntries(
        [...persistedMotorwayContributionsByJourney.entries()].sort(([a],[b])=>a.localeCompare(b))
      ),
      journeyMileageById:Object.fromEntries([...persistedJourneyMileageById.entries()].sort(([a],[b])=>a.localeCompare(b))),
      mileageHistoryComplete:persistedMileageHistoryComplete,
      achievements:Object.fromEntries(persistedAchievements),
      confirmedTimelineVisits:[...persistedConfirmedTimelineVisits.values()],
      manualMotorways:[...persistedManualRefs].sort(motorwayRefSort),
      manualARoads:[...persistedManualARoadRefs].sort((a,b)=>a.localeCompare(b,undefined,{numeric:true})),
      coverage,
      aRoadReferenceSummary:Object.fromEntries(persistedARoadReferenceSummary),
      removedSegmentEvidence:Object.fromEntries(
        [...removedSegmentEvidence.entries()].map(([segmentId,journeyIds])=>[segmentId,[...journeyIds].sort()])
      ),
      canonicalRemovalEvidence:Object.fromEntries(
        [...canonicalRemovalEvidenceByRef.entries()].map(([roadId,anchors])=>[
          roadId,
          Object.fromEntries([...anchors.entries()].map(([anchorId,journeyIds])=>[anchorId,[...journeyIds].sort()]))
        ])
      )
    }));
    updateLocalProgressNotice();
  } catch (err) {
    console.warn('Local progress could not be saved:',err);
  }
}

function scheduleLocalProgressSave() {
  if (localProgressDeletionRunning) return;
  clearTimeout(localSaveTimer);
  localSaveTimer=setTimeout(saveLocalProgressNow,250);
}

function journeyFingerprint(journey) {
  const first=journey?.points?.[0];
  const last=journey?.points?.[journey.points.length-1];
  const coordinateKey=point=>point
    ? `${Number(point.lat).toFixed(5)},${Number(point.lng).toFixed(5)}`
    : '';
  const distance=Number.isFinite(Number(journey?.googleDistanceKm))
    ? Number(journey.googleDistanceKm).toFixed(3)
    : '';
  return [
    journey?.start || '',
    journey?.end || '',
    distance,
    coordinateKey(first),
    coordinateKey(last)
  ].join('|');
}

function routeRepeatFingerprint(journey) {
  const first=journey?.points?.[0];
  const last=journey?.points?.[journey.points.length-1];
  const cell=point=>point
    ? `${(Math.round(Number(point.lat)*500)/500).toFixed(3)},${(Math.round(Number(point.lng)*500)/500).toFixed(3)}`
    : '';
  const distance=Number(journey?.googleDistanceKm);
  const distanceBucket=Number.isFinite(distance) ? (Math.round(distance*2)/2).toFixed(1) : '';
  // A return journey covers the same route in the opposite direction.  Sort
  // endpoints so A→B and B→A are recognised as one repeat pattern.
  const [endpointA,endpointB]=[cell(first),cell(last)].sort();
  return [journey?.travelMode || 'ROAD', endpointA, endpointB, distanceBucket].join('|');
}

function groupRepeatedJourneys(source) {
  const groups=new Map();
  for (const journey of source) {
    const key=routeRepeatFingerprint(journey);
    if (!groups.has(key)) groups.set(key,[]);
    groups.get(key).push(journey);
  }
  return [...groups.values()].map(group=>{
    const ordered=[...group].sort((a,b)=>(b.pathPointCount || 0)-(a.pathPointCount || 0));
    const representative=ordered[0];
    representative.repeatJourneyIds=group.map(journeyIdentity);
    representative.repeatJourneyMileage=Object.fromEntries(group.map(journey=>[journeyIdentity(journey),Number(journey.googleDistanceKm || 0)]));
    representative.repeatCount=group.length;
    representative.repeatDistanceKm=group.reduce((total,journey)=>total+(Number(journey.googleDistanceKm) || 0),0);
    return representative;
  });
}

function journeyTimestampMs(journey) {
  const end=Date.parse(journey?.end || '');
  if (Number.isFinite(end)) return end;
  const start=Date.parse(journey?.start || '');
  return Number.isFinite(start) ? start : null;
}

function journeyIdentity(journey) {
  return journey?.id || journey?.importId || journeyFingerprint(journey);
}

function recordJourneySeen(journey) {
  const id=journeyIdentity(journey);
  if (id) persistedSeenJourneyIds.add(id);
}

function journeyWasPreviouslyImported(journey) {
  const id=journeyIdentity(journey);
  if (excludedJourneyIds.has(id)) return true;
  if (persistedProcessedJourneyIds.has(id)) return true;
  const timeMs=journeyTimestampMs(journey);
  return persistedLegacyCutoffMs!==null && timeMs!==null && timeMs<=persistedLegacyCutoffMs;
}

function motorwayContributionsForJourney(journey) {
  const contributions={};
  const journeyId=journeyIdentity(journey);
  for (const feature of journey?.motorwayGeoJson?.features || []) {
    const roadId=motorwayFeatureId(feature);
    const distanceM=Number(feature?.properties?.distance_m || 0);
    if (!roadId || !Number.isFinite(distanceM) || distanceM<=0) continue;
    const segments=geometrySegments({type:'FeatureCollection',features:[feature]});
    const totalGeometryM=segments.reduce(
      (total,[a,b])=>total+haversineMetres(a,b),
      0
    );
    const retainedGeometryM=segments
      .filter(([a,b])=>!segmentEvidenceIsRemoved(segmentKey(a,b),journeyId))
      .reduce((total,[a,b])=>total+haversineMetres(a,b),0);
    const retainedDistanceM=totalGeometryM>0
      ? distanceM*Math.max(0,Math.min(1,retainedGeometryM/totalGeometryM))
      : distanceM;
    if (retainedDistanceM>0) contributions[roadId]=(contributions[roadId] || 0)+retainedDistanceM;
  }
  return contributions;
}

function recordJourneyProcessed(journey) {
  canonicalCoverageDirty=true;
  canonicalARoadCoverageDirty=true;
  motorwayAggregateDirty=true;
  const ids=Array.isArray(journey?.repeatJourneyIds) && journey.repeatJourneyIds.length
    ? journey.repeatJourneyIds
    : [journeyIdentity(journey)];
  for (const id of ids) {
    if (!id) continue;
    persistedProcessedJourneyIds.add(id);
    const mileage=Number(journey?.repeatJourneyMileage?.[id] ?? journey?.googleDistanceKm);
    if (Number.isFinite(mileage) && mileage>=0) persistedJourneyMileageById.set(id,mileage);
  }
  const representativeId=journeyIdentity(journey);
  const contributions=motorwayContributionsForJourney(journey);
  if (representativeId && Object.keys(contributions).length) persistedMotorwayContributionsByJourney.set(representativeId,contributions);
  else if (representativeId) persistedMotorwayContributionsByJourney.delete(representativeId);
  const start=Date.parse(journey?.start || '');
  const end=Date.parse(journey?.end || '');
  if (Number.isFinite(start)) {
    persistedDataStartMs=persistedDataStartMs===null ? start : Math.min(persistedDataStartMs,start);
  }
  if (Number.isFinite(end)) {
    persistedDataEndMs=persistedDataEndMs===null ? end : Math.max(persistedDataEndMs,end);
  }
}

function persistCorrectedMotorwayContributions() {
  for (const journey of savedRoadRecords()) {
    const id=journeyIdentity(journey);
    if (!id) continue;
    const contributions=motorwayContributionsForJourney(journey);
    if (Object.keys(contributions).length) persistedMotorwayContributionsByJourney.set(id,contributions);
    else persistedMotorwayContributionsByJourney.delete(id);
  }
}

function mergeProgressDateRange(source) {
  const start=source?.dataStartMs===null || source?.dataStartMs===undefined
    ? NaN : Number(source.dataStartMs);
  const end=source?.dataEndMs===null || source?.dataEndMs===undefined
    ? NaN : Number(source.dataEndMs);
  if (Number.isFinite(start)) {
    persistedDataStartMs=persistedDataStartMs===null ? start : Math.min(persistedDataStartMs,start);
  }
  if (Number.isFinite(end)) {
    persistedDataEndMs=persistedDataEndMs===null ? end : Math.max(persistedDataEndMs,end);
  }
  if (persistedDataStartMs!==null) source.dataStartMs=persistedDataStartMs;
  if (persistedDataEndMs!==null) source.dataEndMs=persistedDataEndMs;
}

function resetRoadProgressState({clearExclusions=false}={}) {
  persistedCoverageByRef.clear();
  persistedManualRefs.clear();
  persistedManualARoadRefs.clear();
  persistedDataStartMs=null;
  persistedDataEndMs=null;
  persistedSavedAt=null;
  persistedLegacyCutoffMs=null;
  persistedProcessedJourneyIds.clear();
  persistedSeenJourneyIds.clear();
  if (clearExclusions) excludedJourneyIds.clear();
  persistedSeenJourneyTrackingStarted=true;
  persistedImportedFileHashes.clear();
  persistedFileHashTrackingStarted=true;
  persistedMotorwayContributionsByJourney.clear();
  persistedJourneyMileageById.clear();
  persistedARoadCoverageByRef.clear();
  persistedARoadReferenceSummary.clear();
  removedSegmentEvidence.clear();
  canonicalRemovalEvidenceByRef.clear();
  persistedMileageHistoryComplete=true;
}

async function clearRoadDataOnly() {
  if (!window.confirm('Delete saved driving journeys, motorway progress and A-road progress? Your walking and running data will be kept.')) return;
  localProgressDeletionRunning=true;
  clearTimeout(localSaveTimer);
  localSaveTimer=null;
  resetRoadProgressState();
  persistedConfirmedTimelineVisits.clear();
  localStorage.removeItem('roadprints:service-station-ledger:v1');
  localStorage.removeItem('roadprints:service-station-manual-visits:v1');
  window.dispatchEvent(new Event('roadprints:confirmed-visits-updated'));
  window.dispatchEvent(new Event('roadprints:service-station-completion-updated'));

  try {
    await clearRoadArchive();
  } catch (err) {
    console.warn('Saved road journeys could not be deleted:',err);
    window.alert('The saved road journeys could not be deleted. Please try again.');
    return;
  } finally {
    localProgressDeletionRunning=false;
  }

  journeys=[];
  canonicalRoads.clear();
  canonicalRequestedRefs.clear();
  canonicalARoads.clear();
  canonicalARoadRequestedRefs.clear();
  canonicalARoadLoadStarted=false;
  canonicalARoadPage=0;
  canonicalARoadWorkerEpoch++;
  renderMap();
  saveLocalProgressNow();
  updateLocalProgressNotice();
}

async function clearFootDataOnly() {
  if (!window.confirm('Delete saved walking and running data? Your driving, motorway and A-road progress will be kept.')) return;
  try {
    await clearFootArchive();
  } catch (err) {
    console.warn('Saved on-foot activities could not be deleted:',err);
    window.alert('The saved on-foot activities could not be deleted. Please try again.');
    return;
  }
  renderMap();
  saveLocalProgressNow();
  updateLocalProgressNotice();
}

async function clearLocalProgress() {
  if (!window.confirm('Delete all Roadprints progress, driving journeys and walking/running data from this device?')) return;
  localProgressDeletionRunning=true;
  clearTimeout(localSaveTimer);
  localSaveTimer=null;
  localStorage.removeItem(LOCAL_PROGRESS_KEY);
  persistedConfirmedTimelineVisits.clear();
  localStorage.removeItem('roadprints:service-station-ledger:v1');
  window.dispatchEvent(new Event('roadprints:confirmed-visits-updated'));
  localStorage.removeItem('roadprints:collection-entitlements:v1');
  window.dispatchEvent(new CustomEvent('roadprints:collection-entitlement-change',{detail:{collection:'service-stations',unlocked:false}}));
  resetRoadProgressState({clearExclusions:true});
  localProgressNotice.classList.add('hidden');

  try {
    await clearMapArchive();
  } catch (err) {
    console.warn('Saved map journeys could not be deleted:',err);
    window.alert('The saved map journeys could not be deleted. Please try again.');
  } finally {
    localStorage.removeItem(LOCAL_PROGRESS_KEY);
    localProgressDeletionRunning=false;
  }

  resetTrackingSession();
  onboardingMode=null;
  dataSourceCard.classList.add('hidden');
  manualMotorwayCard.classList.add('hidden');
  onboardingCard.classList.remove('hidden');
  updateLocalProgressNotice();
}

function motorwayRefSort(a, b) {
  return a.localeCompare(b, undefined, {numeric:true});
}

function renderManualMotorwayOptions() {
  manualMotorwayList.innerHTML = '';

  const catalogues=[
    {
      region:'GB',
      title:'Great Britain',
      note:'England, Scotland and Wales',
      refs:Object.keys(MOTORWAY_LENGTH_KM)
    },
    {
      region:'NI',
      title:'Northern Ireland',
      note:'Measured independently',
      refs:Object.keys(NI_MOTORWAY_LENGTH_KM)
    }
  ];

  const importedRefs=importedMotorwayRefs();

  for (const catalogue of catalogues) {
    const group=document.createElement('section');
    group.className='motorway-region-group';
    const heading=document.createElement('div');
    heading.className='motorway-region-heading';
    const title=document.createElement('h3');
    title.textContent=catalogue.title;
    const note=document.createElement('span');
    note.textContent=catalogue.note;
    const options=document.createElement('div');
    options.className='motorway-region-options';
    heading.append(title,note);

    for (const ref of catalogue.refs.sort(motorwayRefSort)) {
      const id=catalogue.region==='NI' ? `NI:${ref}` : ref;
      const label=document.createElement('label');
      const captured=importedRefs.has(id);
      label.className='manual-motorway-option' + (captured ? ' captured-motorway' : '');

      const checkbox=document.createElement('input');
      checkbox.type='checkbox';
      checkbox.value=id;
      checkbox.checked=manualMotorwayRefs.has(id);
      checkbox.disabled=captured;
      checkbox.addEventListener('change',()=>{
        if (checkbox.checked) {
          manualMotorwayRefs.add(id);
          persistedManualRefs.add(id);
        } else {
          manualMotorwayRefs.delete(id);
          persistedManualRefs.delete(id);
          manualCoverageByRef.delete(id);
          persistedCoverageByRef.delete(id);
          canonicalRequestedRefs.delete(id);
          canonicalRoads.delete(id);
        }
        updateManualMotorwaySelection();
      });

      const text=document.createElement('span');
      text.textContent=ref;
      label.append(checkbox,text);
      if (captured) {
        const status=document.createElement('small');
        status.textContent='Captured';
        label.append(status);
      }
      options.append(label);
    }

    group.append(heading,options);
    manualMotorwayList.append(group);
  }
}

function importedMotorwayRefs() {
  const refs=new Set();
  for (const contributions of persistedMotorwayContributionsByJourney.values()) {
    for (const [ref,distanceM] of Object.entries(contributions || {})) if (Number(distanceM)>0) refs.add(ref);
  }
  return refs;
}

function importedARoadRefs() {
  const refs=new Set();
  for (const journey of persistedMapJourneys.values()) {
    for (const feature of journey.aRoadGeoJson?.features || []) {
      const key=aRoadFeatureKey(feature);
      if (key && Number(feature?.properties?.distance_m || 0)>0) refs.add(key);
    }
  }
  return refs;
}

function renderManualARoadOptions() {
  manualARoadList.innerHTML='';
  const importedRefs=importedARoadRefs();
  const catalogues=[
    {region:'GB',title:'Great Britain',note:'A1–A99',refs:GB_MANUAL_A_ROAD_REFS},
    {region:'NI',title:'Northern Ireland',note:'47 key A roads',refs:NI_MANUAL_A_ROAD_REFS}
  ];
  for (const catalogue of catalogues) {
    const group=document.createElement('section'); group.className='motorway-region-group';
    const heading=document.createElement('div'); heading.className='motorway-region-heading';
    const title=document.createElement('h3'); title.textContent=catalogue.title;
    const note=document.createElement('span'); note.textContent=catalogue.note;
    const options=document.createElement('div'); options.className='motorway-region-options';
    heading.append(title,note);
    for (const ref of catalogue.refs) {
      const key=catalogue.region+':'+ref, captured=importedRefs.has(key);
      const label=document.createElement('label');
      label.className='manual-motorway-option'+(captured?' captured-motorway':'');
      const checkbox=document.createElement('input');
      checkbox.type='checkbox'; checkbox.value=key; checkbox.checked=manualARoadRefs.has(key); checkbox.disabled=captured;
      checkbox.addEventListener('change',()=>{
        if (checkbox.checked) { manualARoadRefs.add(key); persistedManualARoadRefs.add(key); }
        else {
          manualARoadRefs.delete(key); persistedManualARoadRefs.delete(key);
          const road=canonicalARoadState(key);
          if (road) { persistedARoadCoverageByRef.delete(road.id); persistedARoadReferenceSummary.delete(road.id); canonicalARoads.delete(road.id); }
          canonicalARoadRequestedRefs.delete(key);
        }
        updateManualARoadSelection();
      });
      const text=document.createElement('span'); text.textContent=ref; label.append(checkbox,text);
      if (captured) { const status=document.createElement('small'); status.textContent='Captured'; label.append(status); }
      options.append(label);
    }
    group.append(heading,options); manualARoadList.append(group);
  }
}

function setAllManualARoads(selected) {
  if (selected) {
    const importedRefs=importedARoadRefs();
    for (const ref of GB_MANUAL_A_ROAD_REFS) if (!importedRefs.has('GB:'+ref)) manualARoadRefs.add('GB:'+ref);
    for (const ref of NI_MANUAL_A_ROAD_REFS) if (!importedRefs.has('NI:'+ref)) manualARoadRefs.add('NI:'+ref);
    persistedManualARoadRefs.clear(); for (const key of manualARoadRefs) persistedManualARoadRefs.add(key);
  } else {
    for (const key of manualARoadRefs) {
      const road=canonicalARoadState(key);
      if (road) { persistedARoadCoverageByRef.delete(road.id); persistedARoadReferenceSummary.delete(road.id); canonicalARoads.delete(road.id); }
      canonicalARoadRequestedRefs.delete(key);
    }
    manualARoadRefs.clear(); persistedManualARoadRefs.clear();
  }
  renderManualARoadOptions(); updateManualARoadSelection();
}

function updateManualARoadSelection() {
  manualARoadSelectedCount.textContent=manualARoadRefs.size+' selected';
  scheduleLocalProgressSave(); renderMap();
  if (manualARoadRefs.size) void ensureCanonicalARoadsForDiscoveredRefs([...manualARoadRefs]);
}

function resetTrackingSession() {
  trackingSessionId++;
  easyImportPaused = false;
  easyImportRunning = false;
  importMode = null;
  journeys = [];
  diagnostics = {};
  ignoredJourneys = [];
  manualMotorwayRefs.clear();
  manualARoadRefs.clear();
  manualCoverageByRef.clear();
  canonicalRequestedRefs.clear();
  canonicalRoads.clear();
  canonicalARoadRequestedRefs.clear();
  canonicalARoads.clear();
  canonicalARoadLoadStarted=false;
  canonicalARoadPage=0;
  canonicalARoadWorkerEpoch++;
  refinementRoadRef = null;
  refinementUndoStack = [];
  refinementChunks = [];
  refinementChunkIndex = 0;

  fileInput.value = '';
  fileStatus.className = 'muted';
  fileStatus.textContent = 'No file selected.';
  journeyList.innerHTML = '';
  ignoredList.innerHTML = '';
  motorwayList.innerHTML = '';
  aRoadList.innerHTML = '';
  canonicalMotorwayList.innerHTML = '';
  canonicalARoadList.innerHTML = '';
  journeyCount.textContent = '0';
  pointCount.textContent = '0';
  selectedCount.textContent = '0';
  dataDateRange.querySelector('span').textContent = 'Date range unavailable';
  ignoredCount.textContent = '0';
  motorwaysDiscovered.textContent = '0';
  aRoadsDiscovered.textContent = '0';
  aRoadMileage.textContent = distanceUnit === 'km' ? '0 km' : '0 mi';
  otherRoadMileage.textContent = distanceUnit === 'km' ? '0 km' : '0 mi';
  timelineRoadMileage.textContent = distanceUnit === 'km' ? '0 km' : '0 mi';
  canonicalRoadsReady.textContent = '0';
  gbProgressPercent.textContent = '0.0%';
  niProgressPercent.textContent = '0.0%';
  gbProgressDistance.textContent = distanceUnit === 'km'
    ? `0.0 of approximately ${GB_MOTORWAY_NETWORK_KM.toFixed(0)} km`
    : `0.0 of approximately ${GB_MOTORWAY_NETWORK_MILES.toLocaleString()} miles`;
  niProgressDistance.textContent = distanceUnit === 'km'
    ? `0.0 of approximately ${NI_MOTORWAY_NETWORK_KM.toFixed(0)} km`
    : `0.0 of approximately ${NI_MOTORWAY_NETWORK_MILES.toLocaleString()} miles`;
  networkProgressPercent.textContent = '0.0%';
  networkProgressDistance.textContent = distanceUnit === 'km'
    ? `0.0 of approximately ${UK_MOTORWAY_NETWORK_KM.toFixed(0)} km`
    : `0.0 of approximately ${UK_MOTORWAY_NETWORK_MILES.toLocaleString()} miles`;
  networkProgressFill.style.width = '0%';
  networkProgressBar.setAttribute('aria-valuenow', '0');
  easyProgressBar.value = 0;
  setEasyProgressStatus('Waiting…','Preparing routes');
  updateEasyImportPauseButton();

  importModeCard.classList.add('hidden');
  easyProgress.classList.add('hidden');
  ignoredCard.classList.add('hidden');
  summaryCard.classList.add('hidden');
  motorwayCard.classList.add('hidden');
  aRoadCard.classList.add('hidden');
  otherRoadCard.classList.add('hidden');
  canonicalMotorwayCard.classList.add('hidden');
  canonicalARoadCard.classList.add('hidden');
  mapCard.classList.add('hidden');
  nextCard?.classList.add('hidden');
  refinementPanel.classList.add('hidden');
  mapCorrectionPanel.classList.add('hidden');
  mapCard.classList.remove('refinement-active');

  for (const layer of [
    traceLayer, matchedLayer, creditedLayer, canonicalReferenceLayer,
    canonicalCoverageLayer, canonicalUncoveredLayer,
    canonicalARoadCoverageLayer, canonicalARoadUncoveredLayer,
    liveImportLayer
  ]) {
    if (layer) layer.clearLayers();
  }

  if (map) {
    showDefaultUnitedKingdomView();
  }
}

async function showSavedProgress() {
  await Promise.all([mapArchiveReadyPromise,footArchiveReadyPromise]);
  if (!persistedCoverageByRef.size && !persistedManualRefs.size && !persistedManualARoadRefs.size &&
      !persistedMapJourneys.size && !persistedFootActivities.size) return;

  resetTrackingSession();
  for (const id of persistedManualRefs) manualMotorwayRefs.add(id);
  for (const key of persistedManualARoadRefs) manualARoadRefs.add(key);
  journeys=savedMapJourneysExcluding();
  onboardingMode='saved';
  onboardingCard.classList.add('hidden');
  dataSourceCard.classList.add('hidden');
  manualMotorwayCard.classList.add('hidden');
  closeSavedProgress.classList.remove('hidden');
  mapTitle.textContent='Your saved Roadprints progress';
  mapIntro.textContent='This is the road and on-foot progress saved on this device. Return to the start to import new Timeline data or make manual changes.';
  mapCard.classList.remove('hidden');
  // Saved maps must render on arrival. The old deferred-map experiment left
  // this screen announcing a nonexistent “Load map” action.
  mapRenderingRequested=true;
  nextCard?.classList.add('hidden');
  renderRoadQueue();
  renderFootQueue();
  renderCollectiveStats();
  renderJourneyLog();

  await ensureLeaflet();
  initMap();
  renderMap();
  activateRoadprintsScreen('map');
  requestAnimationFrame(()=>map?.invalidateSize(true));
}

async function showDataSourceChoice(mode) {
  // Wait for device-stored journey evidence before building the manual list,
  // so imported A roads cannot briefly appear selectable after a reload.
  if (mode==='manual') await mapArchiveReadyPromise;
  resetTrackingSession();
  onboardingMode = mode;
  document.querySelector('main')?.classList.toggle('manual-setup-active', mode === 'manual');
  closeSavedProgress.classList.add('hidden');
  onboardingCard.classList.add('hidden');
  dataSourceCard.classList.toggle('hidden', mode !== 'data');
  manualMotorwayCard.classList.toggle('hidden', mode !== 'manual');

  if (mode === 'data') {
    manualMotorwayRefs.clear();
    canonicalRequestedRefs.clear();
    canonicalRoads.clear();
    mapTitle.textContent = '4. Preview';
    mapIntro.textContent = 'The cumulative credited-road layer shows each matched geometry segment once. Use the map layer control to compare credited roads, matched journeys and raw Timeline traces.';
    return;
  }

  for (const id of persistedManualRefs) manualMotorwayRefs.add(id);
  for (const key of persistedManualARoadRefs) manualARoadRefs.add(key);
  mapTitle.textContent = '3. Preview';
  mapIntro.textContent = 'Selected motorways are shown in blue as complete. In the next enhancement, you will be able to refine these into the individual sections you have driven.';
  mapCard.classList.remove('hidden');
  nextCard?.classList.add('hidden');
  renderManualMotorwayOptions();
  renderManualARoadOptions();
  await ensureLeaflet();
  initMap();
  renderMap();
}

function returnToOnboarding() {
  document.querySelector('main')?.classList.remove('manual-setup-active');
  resetTrackingSession();
  onboardingMode = null;
  closeSavedProgress.classList.add('hidden');
  dataSourceCard.classList.add('hidden');
  manualMotorwayCard.classList.add('hidden');
  onboardingCard.classList.remove('hidden');
}

function setAllManualMotorways(selected) {
  const previouslySelected=[...manualMotorwayRefs];
  manualMotorwayRefs.clear();
  if (selected) {
    const importedRefs=importedMotorwayRefs();
    for (const ref of Object.keys(MOTORWAY_LENGTH_KM)) if (!importedRefs.has(ref)) manualMotorwayRefs.add(ref);
    for (const ref of Object.keys(NI_MOTORWAY_LENGTH_KM)) if (!importedRefs.has(`NI:${ref}`)) manualMotorwayRefs.add(`NI:${ref}`);
    persistedManualRefs.clear();
    for (const id of manualMotorwayRefs) persistedManualRefs.add(id);
  } else {
    persistedManualRefs.clear();
    for (const id of previouslySelected) {
      manualCoverageByRef.delete(id);
      persistedCoverageByRef.delete(id);
    }
    canonicalRequestedRefs.clear();
    canonicalRoads.clear();
  }
  renderManualMotorwayOptions();
  updateManualMotorwaySelection();
}

function updateManualMotorwaySelection() {
  manualSelectedCount.textContent = `${manualMotorwayRefs.size} selected`;
  scheduleLocalProgressSave();
  renderMap();
  if (manualMotorwayRefs.size) ensureCanonicalRoadsForDiscoveredRefs([...manualMotorwayRefs]);
}

document.getElementById('hasDataSource').addEventListener('click', () => showDataSourceChoice('data'));
document.getElementById('noDataSource').addEventListener('click', () => showDataSourceChoice('manual'));
document.getElementById('changeDataSource').addEventListener('click', returnToOnboarding);
document.getElementById('changeManualSource').addEventListener('click', returnToOnboarding);
document.getElementById('selectAllMotorways').addEventListener('click', () => setAllManualMotorways(true));
document.getElementById('clearAllMotorways').addEventListener('click', () => setAllManualMotorways(false));
document.getElementById('selectAllARoads').addEventListener('click', () => setAllManualARoads(true));
document.getElementById('clearAllARoads').addEventListener('click', () => setAllManualARoads(false));
document.getElementById('viewSavedProgress').addEventListener('click', showSavedProgress);
closeSavedProgress.addEventListener('click', returnToOnboarding);
closeAchievementCelebration.addEventListener('click',hideAchievementCelebration);
previousAchievementCelebration?.addEventListener('click',()=>{
  if (achievementCelebrationIndex<=0) return;
  achievementCelebrationIndex--;
  renderAchievementCelebration();
});
nextAchievementCelebration?.addEventListener('click',()=>{
  if (achievementCelebrationIndex>=achievementCelebrationQueue.length-1) return;
  achievementCelebrationIndex++;
  renderAchievementCelebration();
});
achievementCelebration.addEventListener('click',event=>{
  if (event.target===achievementCelebration) hideAchievementCelebration();
});
mapCorrectionStartButton.addEventListener('click',startMapCorrection);
mapCorrectionFinishButton.addEventListener('click',finishMapCorrection);
// Mobile browsers may suspend a tab before a delayed task can run. Persist any
// in-progress correction synchronously as the page is being left.
window.addEventListener('pagehide',()=>{
  if (!mapCorrectionChangesPending) return;
  if (motorwayAggregateDirty) persistCorrectedMotorwayContributions();
  saveLocalProgressNow();
});

mapCorrectionRemove.addEventListener('click',()=>setMapCorrectionMode('remove'));
mapCorrectionRestore.addEventListener('click',()=>setMapCorrectionMode('restore'));
mapCorrectionUndo.addEventListener('click',()=>{
  const previous=mapCorrectionUndoStack.pop();
  if (!previous) return;
  restoreMapCorrectionState(previous);
  canonicalCoverageDirty=true;
  canonicalARoadCoverageDirty=true;
  motorwayAggregateDirty=true;
  mapCorrectionChangesPending=true;
  mapCorrectionUndo.disabled=!mapCorrectionUndoStack.length;
  renderMap({deferCalculations:true});
});
loadLocalProgress();
loadFootPlaceNames();
mapArchiveReadyPromise=loadMapArchive().finally(updateLocalProgressNotice);
footArchiveReadyPromise=loadFootActivityArchive().finally(updateLocalProgressNotice);
unitMiles.classList.toggle('active',distanceUnit==='miles');
unitKm.classList.toggle('active',distanceUnit==='km');
unitMiles.setAttribute('aria-pressed',String(distanceUnit==='miles'));
unitKm.setAttribute('aria-pressed',String(distanceUnit==='km'));
aRoadUnitMiles.classList.toggle('active',distanceUnit==='miles');
aRoadUnitKm.classList.toggle('active',distanceUnit==='km');
aRoadUnitMiles.setAttribute('aria-pressed',String(distanceUnit==='miles'));
aRoadUnitKm.setAttribute('aria-pressed',String(distanceUnit==='km'));
renderManualMotorwayOptions();
renderManualARoadOptions();
updateLocalProgressNotice();
fileInput.addEventListener('change', async () => {
  const file = fileInput.files?.[0];
  if (!file) return;

  resetOutput();
  status(`Reading ${file.name} (${formatBytes(file.size)})…`);

  try {
    await Promise.all([mapArchiveReadyPromise,footArchiveReadyPromise]);
    const text = await file.text();
    status(`Read complete. Identifying this file…`);
    const sourceFileHash = await timelineFileHash(text);
    const fileWasPreviouslySeen = persistedImportedFileHashes.has(sourceFileHash);
    const hadReliableFileHashHistory = persistedFileHashTrackingStarted;
    status(`File identified. Parsing JSON…`);
    await yieldToBrowser();

    const json = JSON.parse(text);
    status(`JSON parsed. Inspecting Timeline structure…`);
    await yieldToBrowser();

    const result = extractTimelineActivities(json);
    const allJourneys = result.roadJourneys;
    const onFootJourneys = result.onFootJourneys;
    diagnostics = result.diagnostics;
    await saveFootActivities(onFootJourneys);
    saveConfirmedTimelineVisits(result.confirmedVisits || []);

    const needsMileageRebuild=
      persistedProcessedJourneyIds.size>0 &&
      !persistedMileageHistoryComplete;
    const rebuildMileage=needsMileageRebuild && window.confirm(
      'Your saved motorway coverage predates cumulative mileage saving. ' +
      'Rebuild the mileage totals from this file now? This is a one-off process and will rematch the earlier journeys. ' +
      'Choose Cancel to process only genuinely new journeys.'
    );
    const seenBeforeImport = new Set(persistedSeenJourneyIds);
    const hadReliableSeenJourneyHistory = persistedSeenJourneyTrackingStarted;
    const fileHistoryNeedsBaseline = !hadReliableFileHashHistory;
    const candidateJourneys=rebuildMileage
      ? allJourneys
      : allJourneys.filter(j=>!journeyWasPreviouslyImported(j));
    const genuinelyNewJourneys = rebuildMileage
      ? []
      : candidateJourneys.filter(j =>
          hadReliableSeenJourneyHistory &&
          hadReliableFileHashHistory &&
          !fileWasPreviouslySeen &&
          !seenBeforeImport.has(journeyIdentity(j))
        );
    const previouslySeenUnmatchedJourneys = rebuildMileage
      ? []
      : candidateJourneys.filter(j =>
          !hadReliableSeenJourneyHistory ||
          fileHistoryNeedsBaseline ||
          fileWasPreviouslySeen ||
          seenBeforeImport.has(journeyIdentity(j))
        );

    for (const journey of allJourneys) recordJourneySeen(journey);
    persistedSeenJourneyTrackingStarted=true;
    persistedImportedFileHashes.add(sourceFileHash);
    persistedFileHashTrackingStarted=true;
    scheduleLocalProgressSave();

    diagnostics.mileageRebuild=rebuildMileage;
    diagnostics.previouslyImportedJourneys=rebuildMileage ? 0 : allJourneys.length-candidateJourneys.length;
    diagnostics.newPassengerVehicleJourneys=genuinelyNewJourneys.length;
    diagnostics.previouslySeenUnmatchedJourneys=previouslySeenUnmatchedJourneys.length;
    diagnostics.seenJourneyMigration=
      !hadReliableSeenJourneyHistory || fileHistoryNeedsBaseline;
    diagnostics.sourceFilePreviouslySeen=fileWasPreviouslySeen;
    const groupedRoadJourneys=groupRepeatedJourneys(candidateJourneys);
    const groupedOnFootJourneys=groupRepeatedJourneys(onFootJourneys);
    diagnostics.roadActivities=allJourneys.length;
    diagnostics.roadDistinctRoutes=groupedRoadJourneys.length;
    diagnostics.roadRepeatActivities=Math.max(0,candidateJourneys.length-groupedRoadJourneys.length);
    diagnostics.onFootActivities=onFootJourneys.length;
    diagnostics.onFootDistinctRoutes=groupedOnFootJourneys.length;
    diagnostics.onFootRepeatActivities=Math.max(0,onFootJourneys.length-groupedOnFootJourneys.length);
    diagnostics.journeysReadyForMatching=groupedRoadJourneys.length;

    ignoredJourneys = groupedRoadJourneys.filter(j => j.pathPointCount < 2);
    const importJourneys = groupedRoadJourneys.filter(j => j.pathPointCount >= 2);
    const importJourneyIds = new Set(importJourneys.map(journeyIdentity));
    journeys = [...savedMapJourneysExcluding(importJourneyIds), ...importJourneys];
    diagnostics.usableJourneys = importJourneys.length;
    diagnostics.ignoredSparseJourneys = ignoredJourneys.length;

    for (const journey of ignoredJourneys) recordJourneyProcessed(journey);
    if (ignoredJourneys.length) scheduleLocalProgressSave();

    showDiagnostics(file.name);
    renderIgnoredJourneys();

    if (!diagnostics.passengerVehicleActivities && !diagnostics.onFootActivities) {
      throw new Error(
        `Diagnostic result: ${diagnostics.semanticSegments.toLocaleString()} semantic segments were found, ` +
        `but no road or on-foot activities were detected.`
      );
    }

    if (!importJourneys.length) {
      fileStatus.className = 'muted';
      fileStatus.textContent =
        `${file.name} inspected successfully. No journeys currently need road matching. ` +
        `${diagnostics.previouslyImportedJourneys.toLocaleString()} successfully processed journey` +
        `${diagnostics.previouslyImportedJourneys===1?' was':'s were'} safely skipped.`;
      return;
    }

    importJourneys.forEach(j => j.selected = true);
    await ensureLeaflet();
    importMode = null;
    summaryCard.classList.add('hidden');
    mapCard.classList.add('hidden');
    nextCard?.classList.add('hidden');
  importModeCard.classList.add('hidden');
  easyProgress.classList.add('hidden');
  ignoredCard.classList.add('hidden');
  motorwayCard.classList.add('hidden');
  canonicalMotorwayCard.classList.add('hidden');
    importModeCard.classList.add('hidden');
    easyProgress.classList.remove('hidden');
    document.querySelector('main')?.classList.add('processing-active');
    void startEasyImport();
  } catch (err) {
    summaryCard.classList.add('hidden');
    mapCard.classList.add('hidden');
    nextCard?.classList.add('hidden');
    fileStatus.className = 'error';
    const prefix = Object.keys(diagnostics).length ? diagnosticText() + '\n\n' : '';
    fileStatus.textContent = prefix + (err.message || String(err));
  }
});

document.getElementById('selectAll').addEventListener('click', () => {
  currentImportJourneys().forEach(j => j.selected = true);
  syncCheckboxes();
  renderMap();
  updateSelectedCount();
});

document.getElementById('selectNone').addEventListener('click', () => {
  currentImportJourneys().forEach(j => j.selected = false);
  syncCheckboxes();
  renderMap();
  updateSelectedCount();
});

document.getElementById('fitMap').addEventListener('click', fitSelected);
document.getElementById('clearMatches').addEventListener('click', clearMatchedRoads);
document.getElementById('easyImport').addEventListener('click', startEasyImport);
document.getElementById('plotImportedMap')?.addEventListener('click',()=>{
  document.querySelector('main')?.classList.remove('processing-active');
  activateRoadprintsScreen('map');
  renderMap();
  showDefaultUnitedKingdomView();
});
document.getElementById('detailedImport').addEventListener('click', startDetailedImport);
startFootBatch.addEventListener('click', startNextFootBatch);
pauseFootMatching.addEventListener('click',()=>{
  if (!footMatching) return;
  footMatchingPaused=!footMatchingPaused;
  pauseFootMatching.textContent=footMatchingPaused ? 'Resume' : 'Pause';
  pauseFootMatching.setAttribute('aria-pressed',String(footMatchingPaused));
  renderFootQueue();
});
clearImportedData.addEventListener('click', clearLocalProgress);
clearRoadData.addEventListener('click', clearRoadDataOnly);
clearFootData.addEventListener('click', clearFootDataOnly);
document.getElementById('stopEasyImport').addEventListener('click', () => {
  if (!easyImportRunning) return;

  easyImportPaused = !easyImportPaused;
  updateEasyImportPauseButton();

  if (!easyImportPaused) {
    setEasyProgressStatus('Resuming…','Continuing road matching');
  }
});
unitMiles.addEventListener('click', () => setDistanceUnit('miles'));
unitKm.addEventListener('click', () => setDistanceUnit('km'));
unitMiles.addEventListener('click', event => event.stopPropagation());
unitKm.addEventListener('click', event => event.stopPropagation());
aRoadUnitMiles.addEventListener('click', () => setDistanceUnit('miles'));
aRoadUnitKm.addEventListener('click', () => setDistanceUnit('km'));
aRoadUnitMiles.addEventListener('click', event => event.stopPropagation());
aRoadUnitKm.addEventListener('click', event => event.stopPropagation());
canonicalRetry.addEventListener('click', retryCanonicalRoads);
function startCanonicalARoadLoading() {
  if (canonicalARoadQueueRunning) return;
  const refs=[...activeARoadKeys()];
  if (!refs.length) return;
  canonicalARoadLoadStarted=true;
  void ensureCanonicalARoadsForDiscoveredRefs(refs).catch(err=>{
    console.error('Roadprints A-road references could not start:',err);
    canonicalARoadLoadStarted=false;
  });
}
// Opening this panel is presentation only. Large reference work is user-led,
 // never a side effect of browser state restoration during startup.
canonicalARoadCard.addEventListener('toggle',()=>renderCanonicalARoadDashboard());
canonicalARoadPrevious.addEventListener('click',()=>showCanonicalARoadPage(canonicalARoadPage-1));
canonicalARoadNext.addEventListener('click',()=>showCanonicalARoadPage(canonicalARoadPage+1));
canonicalARoadRetry.addEventListener('click',async()=>{
  // This must be a light retry: refreshing a tiny manifest is safe, while
  // restarting all discovered roads can monopolise a mobile browser.
  canonicalARoadRetry.disabled=true;
  canonicalARoadRetry.textContent='Checking A-road reference index…';
  canonicalARoadCacheIndexPromise=null;
  canonicalARoadCacheIndexError=null;
  try {
    await loadCanonicalARoadCacheIndex();
    canonicalARoadLoadStarted=false;
    if (canonicalARoadCacheIndexAvailable) startCanonicalARoadLoading();
  } finally {
    renderCanonicalARoadDashboard();
  }
});

function resetOutput() {
  journeys = [];
  diagnostics = {};
  ignoredJourneys = [];
  importMode = null;
  easyImportPaused = false;
  easyImportRunning = false;
  fileStatus.className = 'muted';
  summaryCard.classList.add('hidden');
  mapCard.classList.add('hidden');
  nextCard?.classList.add('hidden');
}

function status(text) {
  fileStatus.className = 'muted';
  fileStatus.textContent = text;
}

function yieldToBrowser() {
  return new Promise(resolve => setTimeout(resolve, 0));
}

async function timelineFileHash(text) {
  if (globalThis.crypto?.subtle && typeof TextEncoder !== 'undefined') {
    const bytes = new TextEncoder().encode(text);
    const digest = await crypto.subtle.digest('SHA-256', bytes);
    return [...new Uint8Array(digest)]
      .map(byte => byte.toString(16).padStart(2, '0'))
      .join('');
  }

  // Deterministic fallback for older browsers. This is an identity check, not security.
  let hash = 2166136261;
  for (let index = 0; index < text.length; index++) {
    hash ^= text.charCodeAt(index);
    hash = Math.imul(hash, 16777619);
  }
  return `fallback-${text.length}-${(hash >>> 0).toString(16)}`;
}

function showDiagnostics(fileName) {
  fileStatus.className = 'file-summary';
  fileStatus.replaceChildren();

  const heading = document.createElement('div');
  heading.className = 'file-summary-heading';
  const tick = document.createElement('span');
  tick.className = 'file-summary-tick';
  tick.setAttribute('aria-hidden', 'true');
  tick.textContent = '✓';
  const headingText = document.createElement('div');
  const title = document.createElement('strong');
  title.textContent = 'Timeline file ready';
  const subtitle = document.createElement('span');
  subtitle.textContent = `${fileName} was read successfully.`;
  headingText.append(title, subtitle);
  heading.append(tick, headingText);

  const stats = document.createElement('div');
  stats.className = 'file-summary-stats';
  stats.append(
    summaryStat(formatDataDateRange(), 'dates covered'),
    summaryStat(fmt(diagnostics.roadActivities), 'road activities'),
    summaryStat(fmt(diagnostics.roadDistinctRoutes), 'road routes to match'),
    summaryStat(fmt(diagnostics.roadRepeatActivities), 'repeat road activities'),
    summaryStat(fmt(diagnostics.onFootActivities), 'on-foot activities'),
    summaryStat(fmt(diagnostics.onFootDistinctRoutes), 'on-foot routes queued'),
    summaryStat(fmt(diagnostics.ignoredSparseJourneys), 'unable to use')
  );

  const queues=document.createElement('div');
  queues.className='activity-queues';
  const roadQueue=document.createElement('div');
  roadQueue.className='activity-queue road-queue';
  roadQueue.innerHTML=`<strong>Easy load road activities</strong><span>${fmt(diagnostics.roadActivities)} activities · ${fmt(diagnostics.roadDistinctRoutes)} routes to match · ${fmt(diagnostics.roadRepeatActivities)} repeats grouped</span>`;
  const footQueue=document.createElement('div');
  footQueue.className='activity-queue foot-queue';
  footQueue.innerHTML=`<strong>Easy load on-foot activities</strong><span>${fmt(diagnostics.onFootActivities)} activities · ${fmt(diagnostics.onFootDistinctRoutes)} distinct routes · held separately from road matching</span>`;
  queues.append(roadQueue,footQueue);

  const explanation = document.createElement('p');
  explanation.className = 'file-summary-note';
  explanation.textContent = importSummaryMessage();

  const details = document.createElement('details');
  details.className = 'technical-details';
  const detailsSummary = document.createElement('summary');
  detailsSummary.textContent = 'Technical details';
  const detailsIntro = document.createElement('p');
  detailsIntro.textContent = 'These figures show how Roadprints interpreted the Google Timeline file. They are mainly useful for troubleshooting.';
  const detailList = document.createElement('dl');
  technicalDiagnosticRows().forEach(([label, value, help]) => {
    const term = document.createElement('dt');
    term.textContent = label;
    if (help) term.title = help;
    const description = document.createElement('dd');
    description.textContent = value;
    detailList.append(term, description);
  });
  details.append(detailsSummary, detailsIntro, detailList);
  fileStatus.append(heading, stats, queues, explanation, details);
}

function summaryStat(value, label) {
  const item = document.createElement('div');
  const strong = document.createElement('strong');
  strong.textContent = value;
  const span = document.createElement('span');
  span.textContent = label;
  item.append(strong, span);
  return item;
}

function importSummaryMessage() {
  const ready = Number(diagnostics.usableJourneys || 0);
  const repeats = Number(diagnostics.roadRepeatActivities || 0);
  const onFoot = Number(diagnostics.onFootActivities || 0);
  const previous = Number(diagnostics.previouslyImportedJourneys || 0);
  const ignored = Number(diagnostics.ignoredSparseJourneys || 0);
  const parts = [
    `${fmt(ready)} car journey${ready === 1 ? ' is' : 's are'} ready for road matching.`
  ];
  if (repeats) parts.push(`${fmt(repeats)} repeat road ${repeats === 1 ? 'activity has' : 'activities have'} been grouped with a representative route, so their mileage can be retained without extra matching calls.`);
  if (onFoot) parts.push(`${fmt(onFoot)} walking or running ${onFoot === 1 ? 'activity is' : 'activities are'} held in the separate on-foot queue; they are not sent to the road matcher.`);
  if (previous) parts.push(`${fmt(previous)} already matched journey${previous === 1 ? ' has' : 's have'} been safely skipped.`);
  if (ignored) parts.push(`${fmt(ignored)} journey${ignored === 1 ? ' does' : 's do'} not contain enough location detail to match reliably.`);
  if (diagnostics.seenJourneyMigration) {
    parts.push('Roadprints has now created its one-off baseline of journeys previously seen on this device.');
  }
  return parts.join(' ');
}

function technicalDiagnosticRows() {
  return [
    ['Timeline entries', fmt(diagnostics.semanticSegments), 'All entries found in the Timeline file, including visits and journeys.'],
    ['Movement entries', fmt(diagnostics.activitySegments), 'Timeline entries describing movement between places.'],
    ['Road activity entries', fmt(diagnostics.passengerVehicleActivities), 'Movement entries Google identified as travel in a passenger vehicle.'],
    ['On-foot activity entries', fmt(diagnostics.onFootActivities), 'Movement entries Google identified as walking, running or pedestrian travel.'],
    ['Recorded route sections', fmt(diagnostics.timelinePathSegments), 'Route traces included in the Timeline file.'],
    ['Recorded location points', fmt(diagnostics.timelinePathPoints), 'Timestamped positions available for reconstructing routes.'],
    ['Car journeys with route points', fmt(diagnostics.vehiclesWithPathPoints), 'Car journeys that overlap recorded route positions.'],
    ['Car journeys with start and end points', fmt(diagnostics.vehiclesWithAnchors), 'Car journeys with enough information to identify their beginning and end.'],
    ['Road journeys reconstructed', fmt(diagnostics.journeysConstructed), 'Road journeys Roadprints successfully reconstructed from the source data.'],
    ['Distinct road routes', fmt(diagnostics.roadDistinctRoutes), 'Conservatively grouped road patterns. One representative from each group is sent to road matching.'],
    ['Repeat road activities', fmt(diagnostics.roadRepeatActivities), 'Activities grouped with a representative road route. They avoid repeat matcher calls while retaining their mileage.'],
    ['Distinct on-foot routes', fmt(diagnostics.onFootDistinctRoutes), 'Walking and running patterns placed in the separate on-foot queue for a future matching feature.'],
    ['Genuinely new journeys', fmt(diagnostics.newPassengerVehicleJourneys), 'Journeys never previously seen in an imported file on this device.'],
    ['Previously seen, unmatched', fmt(diagnostics.previouslySeenUnmatchedJourneys), 'Journeys seen in an earlier import but not successfully road-matched, available to retry.'],
    ['Ready for road matching', fmt(diagnostics.usableJourneys), 'Distinct road routes containing at least two location points.'],
    ['Unable to use', fmt(diagnostics.ignoredSparseJourneys), 'Journeys with fewer than two location points, which cannot be matched reliably.'],
    ...(diagnostics.mileageRebuild ? [['Mileage update', 'One-off rebuild selected', 'Earlier journeys will be matched again to rebuild cumulative mileage totals.']] : [])
  ];
}

function formatDataDateRange(source=diagnostics) {
  if (source?.dataStartMs===null || source?.dataStartMs===undefined ||
      source?.dataEndMs===null || source?.dataEndMs===undefined) {
    return 'Date range unavailable';
  }
  const start=Number(source.dataStartMs);
  const end=Number(source.dataEndMs);
  if (!Number.isFinite(start) || !Number.isFinite(end)) return 'Date range unavailable';
  const startLabel=formatDate(new Date(start).toISOString());
  const endLabel=formatDate(new Date(end).toISOString());
  return startLabel===endLabel ? startLabel : `${startLabel} to ${endLabel}`;
}

function diagnosticText() {
  return [
    `Data coverage: ${formatDataDateRange()}`,
    `Semantic segments: ${fmt(diagnostics.semanticSegments)}`,
    `Activity segments: ${fmt(diagnostics.activitySegments)}`,
    `Passenger-vehicle activities: ${fmt(diagnostics.passengerVehicleActivities)}`,
    `timelinePath segments: ${fmt(diagnostics.timelinePathSegments)}`,
    `Timestamped timelinePath points: ${fmt(diagnostics.timelinePathPoints)}`,
    `Vehicle activities with overlapping path points: ${fmt(diagnostics.vehiclesWithPathPoints)}`,
    `Vehicle activities with usable start/end anchors: ${fmt(diagnostics.vehiclesWithAnchors)}`,
    `Journeys constructed: ${fmt(diagnostics.journeysConstructed)}`,
    `Previously imported and skipped: ${fmt(diagnostics.previouslyImportedJourneys)}`,
    diagnostics.mileageRebuild ? 'Mileage totals: one-off cumulative rebuild selected' : '',
    `Genuinely new passenger-vehicle journeys: ${fmt(diagnostics.newPassengerVehicleJourneys)}`,
    `Previously seen but unmatched: ${fmt(diagnostics.previouslySeenUnmatchedJourneys)}`,
    `Ready for road matching: ${fmt(diagnostics.usableJourneys)}`,
    `Ignored — fewer than 2 Timeline points: ${fmt(diagnostics.ignoredSparseJourneys)}`
  ].filter(Boolean).join('\n');
}

function fmt(n) {
  return Number(n || 0).toLocaleString();
}

function footAreaKey(activity) {
  const point=activity?.points?.[0];
  if (!point) return 'Unknown area';
  return `${(Math.floor(Number(point.lat)*10)/10).toFixed(1)}, ${(Math.floor(Number(point.lng)*10)/10).toFixed(1)}`;
}

function buildFootBatches() {
  const representatives=groupRepeatedJourneys(footActivities.filter(a=>a.points?.length>=2));
  const byArea=new Map();
  for (const activity of representatives) {
    const key=footAreaKey(activity);
    if (!byArea.has(key)) byArea.set(key,[]);
    byArea.get(key).push(activity);
  }
  footBatches=[...byArea.entries()].map(([area,activities])=>({
    id:area, area, activities,
    lat:activities.reduce((sum,item)=>sum+Number(item.points?.[0]?.lat || 0),0)/activities.length,
    lng:activities.reduce((sum,item)=>sum+Number(item.points?.[0]?.lng || 0),0)/activities.length,
    matched:activities.filter(a=>a.matchedGeoJson).length,
    failed:activities.filter(a=>a.matchError).length
  })).sort((a,b)=>b.activities.length-a.activities.length);
  resolveFootBatchPlaceNames();
}

function loadFootPlaceNames() {
  try {
    const saved=JSON.parse(localStorage.getItem(FOOT_PLACE_NAMES_KEY) || '{}');
    for (const [key,value] of Object.entries(saved)) if (typeof value==='string' && value) footPlaceNames.set(key,value);
  } catch (err) {}
}

function saveFootPlaceNames() {
  try { localStorage.setItem(FOOT_PLACE_NAMES_KEY,JSON.stringify(Object.fromEntries(footPlaceNames))); } catch (err) {}
}

async function resolveFootBatchPlaceNames() {
  for (const batch of footBatches) {
    const builtInName=friendlyFootAreaName(batch.lat,batch.lng);
    if (builtInName) {
      if (footPlaceNames.get(batch.id)!==builtInName) { footPlaceNames.set(batch.id,builtInName); saveFootPlaceNames(); renderFootQueue(); }
      continue;
    }
    if ((footPlaceNames.has(batch.id) && footPlaceNames.get(batch.id)!=='Local area') || footPlaceLookups.has(batch.id) || !Number.isFinite(batch.lat) || !Number.isFinite(batch.lng)) continue;
    footPlaceLookups.add(batch.id);
    try {
      const response=await fetch(`${API_BASE_URL}/place-name?lat=${encodeURIComponent(batch.lat)}&lng=${encodeURIComponent(batch.lng)}`);
      const data=await response.json().catch(()=>({}));
      footPlaceNames.set(batch.id,data.name || 'Local area');
      saveFootPlaceNames();
    } catch (err) { footPlaceNames.set(batch.id,'Local area'); }
    finally { footPlaceLookups.delete(batch.id); renderFootQueue(); }
    await new Promise(resolve=>setTimeout(resolve,1050));
  }
}

function friendlyFootAreaName(lat,lng) {
  const places=[
    ['Gravesend',51.44,0.37,35],['London',51.51,-0.13,55],['Medway and north Kent',51.36,0.52,45],
    ['Thanet and the Kent coast',51.36,1.30,60],['Colchester and north Essex',51.89,0.90,55],
    ['Portsmouth',50.82,-1.09,55],['Sussex coast',50.90,-0.20,85],['Cambridge',52.21,0.12,60],['Northampton',52.24,-0.89,55],
    ['Luton and south Bedfordshire',51.88,-0.42,55],['Chilterns',51.70,-0.50,70],['Canterbury',51.28,1.08,55],['Ibiza',38.98,1.43,120],
    ['Sheffield',53.38,-1.47,65],['Liverpool',53.41,-2.99,65],['Fylde Coast',53.76,-3.03,85]
  ];
  let nearest=null;
  for (const [name,placeLat,placeLng,radiusKm] of places) {
    const distanceKm=Math.hypot((lat-placeLat)*111,(lng-placeLng)*111*Math.cos(lat*Math.PI/180));
    if (distanceKm<=radiusKm && (!nearest || distanceKm<nearest.distanceKm)) nearest={name,distanceKm};
  }
  return nearest?.name || 'UK activity area';
}

function renderFootQueue() {
  if (!shouldShowDataDashboard()) { footQueueCard.classList.add('hidden'); return; }
  if (!footActivities.length) { footQueueCard.classList.add('hidden'); return; }
  footQueueCard.classList.remove('hidden');
  const distinct=groupRepeatedJourneys(footActivities.filter(a=>a.points?.length>=2)).length;
  const matched=footBatches.reduce((n,b)=>n+b.matched,0);
  const failed=footBatches.reduce((n,b)=>n+b.failed,0);
  footProgressBar.max=distinct || 1;
  footProgressBar.value=Math.min(distinct,matched+failed);
  setFootProgressStatus(
    footMatchingError ? 'Stopped' : footMatchingProgress ? `${footMatchingProgress.completed} / ${footMatchingProgress.total}` : `${matched + failed} / ${distinct}`,
    footMatchingError ? footMatchingError : footMatchingProgress
      ? `${footMatchingPaused ? 'Paused in' : 'Matching'} ${footMatchingProgress.area} · ${footMatchingProgress.succeeded} matched · ${footMatchingProgress.failed} unable`
      : `${matched} matched${failed ? ` · ${failed} unable` : ''} · ${footActivities.length} activities${importFootResult ? ` · ${importFootResult}` : ''}`
  );
  const next=footBatches.find(batch=>batch.activities.some(item=>!item.matchedGeoJson && !item.matchError));
  startFootBatch.disabled=footMatching || !next;
  const queued=footBatches.reduce((count,batch)=>count+batch.activities.filter(item=>!item.matchedGeoJson && !item.matchError).length,0);
  startFootBatch.textContent=footMatching ? 'Matching queued routes…' : next ? `Match queued routes (${queued})` : 'All areas processed';
  pauseFootMatching.classList.toggle('hidden',!footMatching);
  pauseFootMatching.textContent=footMatchingPaused ? 'Resume' : 'Pause';
  pauseFootMatching.setAttribute('aria-pressed',String(footMatchingPaused));
}

function setFootProgressStatus(primary, secondary) {
  footProgressText.replaceChildren();
  const headline=document.createElement('strong');
  headline.textContent=primary;
  const detail=document.createElement('span');
  detail.textContent=secondary;
  footProgressText.append(headline,detail);
}

function savedRoadRecords() {
  const records=new Map(persistedMapJourneys);
  for (const journey of journeys) {
    if (journey?.matchedGeoJson) records.set(journeyIdentity(journey),journey);
  }
  return [...records.values()];
}

function savedServiceStationRecords() {
  try {
    const entitlements=JSON.parse(localStorage.getItem('roadprints:collection-entitlements:v1') || '{}');
    if (!entitlements['service-stations']) return [];
    const ledger=JSON.parse(localStorage.getItem('roadprints:service-station-ledger:v1') || '{}');
    return Array.isArray(ledger?.visits) ? ledger.visits.filter(visit=>visit && visit.serviceId && visit.start) : [];
  } catch (_) {
    return [];
  }
}

function serviceStopDurationLabel(record) {
  const start=Date.parse(record?.start || '');
  const end=Date.parse(record?.end || '');
  if (!Number.isFinite(start) || !Number.isFinite(end) || end<=start) return 'Confirmed Timeline stop';
  const minutes=Math.round((end-start)/60000);
  if (minutes<60) return minutes+' min stop';
  const hours=Math.floor(minutes/60),remainder=minutes%60;
  return remainder ? hours+'h '+remainder+'m stop' : hours+'h stop';
}

function renderJourneyLog() {
  if (!journeyLogCard || !journeyLogList || !journeyLogCount) return;
  const allRecords=[
    ...savedRoadRecords().filter(record=>record?.matchedGeoJson).map(record=>({...record,logType:'road'})),
    ...footActivities.filter(activity=>activity?.points?.length>1).map(activity=>({...activity,logType:'foot'})),
    ...savedServiceStationRecords().map(record=>({...record,logType:'service'}))
  ].sort((a,b)=>Date.parse(b.start || '')-Date.parse(a.start || ''));

  const servicesUnlocked=savedServiceStationRecords().length>0 ||
    (()=>{ try { return Boolean(JSON.parse(localStorage.getItem('roadprints:collection-entitlements:v1') || '{}')['service-stations']); } catch (_) { return false; } })();
  if (journeyLogFilter==='service' && !servicesUnlocked) journeyLogFilter='all';
  const filters=document.getElementById('journeyLogFilters');
  filters?.classList.toggle('hidden',!shouldShowDataDashboard() || !allRecords.length);
  filters?.querySelector('[data-journey-filter="service"]')?.classList.toggle('hidden',!servicesUnlocked);
  filters?.querySelectorAll('[data-journey-filter]').forEach(button=>{
    button.classList.toggle('active',button.dataset.journeyFilter===journeyLogFilter);
    button.setAttribute('aria-pressed',String(button.dataset.journeyFilter===journeyLogFilter));
  });
  const records=journeyLogFilter==='all' ? allRecords : allRecords.filter(record=>
    journeyLogFilter==='driving' ? record.logType==='road' :
    journeyLogFilter==='foot' ? record.logType==='foot' : record.logType==='service'
  );

  const hasData=shouldShowDataDashboard() && allRecords.length>0;
  journeyLogCard.classList.toggle('hidden',!hasData);
  if (journeyLogObserver) { journeyLogObserver.disconnect(); journeyLogObserver=null; }
  if (!hasData) return;
  if (!records.length) {
    journeyLogCount.textContent='0';
    journeyLogList.replaceChildren();
    const empty=document.createElement('p');
    empty.className='muted journey-log-empty';
    empty.textContent='No journeys match this filter yet.';
    journeyLogList.append(empty);
    return;
  }

  const signature=records.map(record=>record.logType+':'+(record.serviceId || journeyIdentity(record))+':'+(record.start || '')).join('|');
  if (signature!==journeyLogSignature) { journeyLogSignature=signature; journeyLogVisibleCount=0; }

  const patternTotals=new Map();
  for (const record of records.filter(record=>record.logType!=='service')) {
    const key=routeRepeatFingerprint(record);
    const trips=Math.max(1,Number(record.repeatCount || 1));
    patternTotals.set(key,(patternTotals.get(key) || 0)+trips);
  }
  journeyLogCount.textContent=records.length.toLocaleString();
  journeyLogList.replaceChildren();

  const renderRecord=record=>{
    const item=document.createElement('article');
    item.className='journey-log-item'+(record.logType==='foot' ? ' journey-log-foot' : record.logType==='service' ? ' journey-log-service' : '');
    const copy=document.createElement('div');
    const date=document.createElement('small');
    const modeLabel=record.logType==='foot' ? (record.travelMode==='RUNNING' ? '👟 RUNNING' : '👟 ON FOOT') : record.logType==='service' ? '⛽ SERVICE STATION' : '🚗 DRIVING';
    date.textContent=modeLabel+(record.logType==='service' && record.road ? ' · '+record.road : '')+(record.start ? ' · '+formatDate(record.start)+' · '+formatTime(record.start) : '')+(record.logType==='service' && record.isFirstVisit ? ' · FIRST VISIT' : '');
    const title=document.createElement('strong');
    title.textContent=record.logType==='service' ? record.serviceName : (record.title || 'Untitled journey');
    const meta=document.createElement('span');
    if (record.logType==='service') {
      meta.textContent=serviceStopDurationLabel(record)+' · confirmed Timeline stop';
      copy.append(date,title,meta); item.append(copy); return item;
    }
    const distance=Number(record.googleDistanceKm || record.matchedDistanceKm || record.repeatDistanceKm || 0);
    const recordTrips=Math.max(1,Number(record.repeatCount || 1));
    const patternTrips=patternTotals.get(routeRepeatFingerprint(record)) || recordTrips;
    meta.textContent=(distance>0 ? displayDistance(distance) : 'Distance unavailable')+(patternTrips>1 ? ' · repeated route pattern · '+patternTrips+' journeys' : ' · no similar route recorded');
    copy.append(date,title,meta);

    const id=journeyIdentity(record);
    const actions=document.createElement('div');
    actions.className='journey-log-actions';
    const rename=document.createElement('button');
    rename.type='button'; rename.className='secondary'; rename.textContent='Rename';
    rename.addEventListener('click',()=>renameSavedJourney(id,record.logType));
    const view=document.createElement('button');
    view.type='button'; view.textContent='View & refine';
    view.addEventListener('click',()=>openJourneyFocus(id,record.logType));
    const remove=document.createElement('button');
    remove.type='button'; remove.className='secondary journey-log-remove'; remove.textContent='Remove';
    remove.addEventListener('click',()=>deleteSavedJourney(id,record.logType));
    actions.append(rename,view,remove); item.append(copy,actions); return item;
  };

  let pagination=null;
  const appendNextPage=()=>{
    if (pagination) { pagination.remove(); pagination=null; }
    const next=Math.min(records.length,journeyLogVisibleCount+JOURNEY_LOG_PAGE_SIZE);
    const fragment=document.createDocumentFragment();
    for (const record of records.slice(journeyLogVisibleCount,next)) fragment.append(renderRecord(record));
    journeyLogList.append(fragment); journeyLogVisibleCount=next;
    if (journeyLogVisibleCount>=records.length) return;
    pagination=document.createElement('div'); pagination.className='journey-log-pagination';
    const note=document.createElement('span');
    note.textContent='Showing '+journeyLogVisibleCount.toLocaleString()+' of '+records.length.toLocaleString()+' journeys';
    const more=document.createElement('button');
    more.type='button'; more.className='secondary'; more.textContent='Load older journeys';
    more.addEventListener('click',appendNextPage);
    const sentinel=document.createElement('div');
    sentinel.className='journey-log-sentinel'; sentinel.setAttribute('aria-hidden','true');
    pagination.append(note,more,sentinel); journeyLogList.append(pagination);
    if ('IntersectionObserver' in window) {
      if (journeyLogObserver) journeyLogObserver.disconnect();
      journeyLogObserver=new IntersectionObserver(entries=>{ if (entries.some(entry=>entry.isIntersecting)) appendNextPage(); },{root:null,rootMargin:'0px 0px 700px 0px',threshold:0});
      journeyLogObserver.observe(sentinel);
    }
  };
  const initiallyVisible=Math.min(records.length,Math.max(JOURNEY_LOG_PAGE_SIZE,journeyLogVisibleCount));
  journeyLogVisibleCount=0;
  while (journeyLogVisibleCount<initiallyVisible) appendNextPage();
}
window.addEventListener('roadprints:service-station-ledger-updated',()=>renderJourneyLog());
window.addEventListener('roadprints:collection-entitlement-change',()=>renderJourneyLog());
document.getElementById('journeyLogFilters')?.addEventListener('click',event=>{
  const button=event.target.closest('[data-journey-filter]');
  if (!button) return;
  journeyLogFilter=button.dataset.journeyFilter || 'all';
  renderJourneyLog();
});

async function renameSavedJourney(id,type='road') {
  const records=type==='foot' ? persistedFootActivities : persistedMapJourneys;
  const record=records.get(id);
  if (!record) return;
  const proposed=window.prompt('Name this journey',record.title || '');
  if (proposed===null) return;
  const title=proposed.trim();
  const updated={...record,title};
  try {
    if (type==='foot') {
      await footArchiveOperation('readwrite',store=>store.put(updated));
      persistedFootActivities.set(id,updated);
      const inSession=footActivities.find(activity=>journeyIdentity(activity)===id);
      if (inSession) inSession.title=title;
    } else {
      await mapArchiveOperation('readwrite',store=>store.put(updated));
      persistedMapJourneys.set(id,updated);
      const inSession=journeys.find(journey=>journeyIdentity(journey)===id);
      if (inSession) inSession.title=title;
    }
    renderJourneyLog();
  } catch (err) {
    console.warn('Journey name could not be saved:',err);
    window.alert('The journey name could not be saved. Please try again.');
  }
}

async function deleteSavedJourney(id,type='road') {
  const records=type==='foot' ? persistedFootActivities : persistedMapJourneys;
  const record=records.get(id);
  if (!record) return;
  const label=type==='foot' ? 'this on-foot activity' : 'this driving journey';
  if (!window.confirm(`Remove ${label}? It will be excluded from future Timeline imports on this device.`)) return;
  const excludedIds=Array.isArray(record.repeatJourneyIds) && record.repeatJourneyIds.length
    ? record.repeatJourneyIds : [id];
  for (const excludedId of excludedIds) {
    if (!excludedId) continue;
    excludedJourneyIds.add(excludedId);
    persistedProcessedJourneyIds.delete(excludedId);
    persistedSeenJourneyIds.delete(excludedId);
    persistedJourneyMileageById.delete(excludedId);
  }
  try {
    if (type==='foot') {
      await footArchiveOperation('readwrite',store=>store.delete(id));
      persistedFootActivities.delete(id);
      footActivities=footActivities.filter(activity=>journeyIdentity(activity)!==id);
      buildFootBatches();
      renderFootQueue();
    } else {
      await mapArchiveOperation('readwrite',store=>store.delete(id));
      persistedMapJourneys.delete(id);
      persistedMotorwayContributionsByJourney.delete(id);
      journeys=journeys.filter(journey=>journeyIdentity(journey)!==id);
      canonicalCoverageDirty=true;
      canonicalARoadCoverageDirty=true;
      motorwayAggregateDirty=true;
    }
    if (focusedJourneyId===id) {
      focusedJourneyId=null;
      focusedJourneyType=null;
      journeyFocusBar?.classList.add('hidden');
    }
    renderCollectiveStats();
    renderJourneyLog();
    renderMap();
    scheduleLocalProgressSave();
  } catch (err) {
    console.warn('Journey could not be removed:',err);
    window.alert('The journey could not be removed. Please try again.');
  }
}

function openJourneyFocus(id,type='road') {
  const record=(type==='foot' ? persistedFootActivities : persistedMapJourneys).get(id);
  if (!record) return;
  if (type==='road' && !journeys.some(journey=>journeyIdentity(journey)===id)) journeys.push(hydrateMapJourney(record));
  focusedJourneyId=id;
  focusedJourneyType=type;
  window.dispatchEvent(new CustomEvent('roadprints:journey-focus-change',{detail:{active:true}}));
  journeyFocusBar?.classList.remove('hidden');
  activateRoadprintsScreen('map');
  renderMap();
  const journey=type==='foot'
    ? (footActivities.find(candidate=>journeyIdentity(candidate)===id) || record)
    : (journeys.find(candidate=>journeyIdentity(candidate)===id) || hydrateMapJourney(record));
  const points=(journey.points || []).filter(validPoint);
  setTimeout(()=>{
    if (map && window.L && points.length>1) {
      map.fitBounds(L.latLngBounds(points.map(point=>[point.lat,point.lng])),{padding:[36,36],maxZoom:15});
      renderMap({deferCalculations:true});
    }
  },80);
}

function closeJourneyFocusView() {
  focusedJourneyId=null;
  focusedJourneyType=null;
  window.dispatchEvent(new CustomEvent('roadprints:journey-focus-change',{detail:{active:false}}));
  journeyFocusBar?.classList.add('hidden');
  renderMap();
  activateRoadprintsScreen('journeys');
}

function savedOnFootRecords() {
  return footActivities.filter(activity=>activity?.matchedGeoJson);
}

function editableMappedActivities() {
  if (focusedJourneyId && focusedJourneyType==='foot') {
    return savedOnFootRecords().filter(record=>journeyIdentity(record)===focusedJourneyId);
  }
  const roads=focusedJourneyId
    ? savedRoadRecords().filter(record=>journeyIdentity(record)===focusedJourneyId)
    : savedRoadRecords();
  return focusedJourneyId ? roads : [...roads,...savedOnFootRecords()];
}

function segmentDistanceKm(segments) {
  return segments.reduce((total,segment)=>total+haversineMetres(segment.a,segment.b)/1000,0);
}

function renderRoadQueue() {
  if (!shouldShowDataDashboard()) { easyProgress.classList.add('hidden'); return; }
  if (easyImportRunning) return;
  const routes=savedRoadRecords();
  const activities=persistedJourneyMileageById.size;
  if (!routes.length && !activities) return;
  easyProgress.classList.remove('hidden');
  easyProgressBar.max=Math.max(routes.length,1);
  easyProgressBar.value=routes.length;
  setEasyProgressStatus(
    'Complete',
    `${routes.length.toLocaleString()} route pattern${routes.length===1?'':'s'} mapped · ${activities.toLocaleString()} driving activit${activities===1?'y':'ies'}`
  );
  updateEasyImportPauseButton();
}

function renderCollectiveStats() {
  if (!travelStatsCard) return;
  if (!shouldShowDataDashboard()) { travelStatsCard.classList.add('hidden'); return; }
  const drivingKm=[...persistedJourneyMileageById.values()].reduce((total,value)=>total+(Number(value)||0),0);
  const footKm=footActivities.reduce((total,activity)=>total+(Number(activity.googleDistanceKm)||0),0);
  const roadUniqueKm=segmentDistanceKm(buildCreditedSegments(savedRoadRecords()));
  const footRepresentatives=groupRepeatedJourneys(footActivities.filter(activity=>activity?.points?.length>=2));
  const footUniqueKm=segmentDistanceKm(buildCreditedSegments(footRepresentatives));
  const totalKm=drivingKm+footKm;
  const uniqueKm=roadUniqueKm+footUniqueKm;
  const hasData=totalKm>0 || uniqueKm>0 || footActivities.length || persistedMapJourneys.size;
  travelStatsCard.classList.toggle('hidden',!hasData);
  if (!hasData) return;

  travelStats.total.textContent=displayDistance(totalKm);
  travelStats.driving.textContent=displayDistance(drivingKm);
  travelStats.foot.textContent=displayDistance(footKm);
  travelStats.uniqueTotal.textContent=displayDistance(uniqueKm);
  travelStats.uniqueDriving.textContent=displayDistance(roadUniqueKm);
  travelStats.uniqueFoot.textContent=displayDistance(footUniqueKm);
  travelStats.uniqueDrivingPercent.textContent=drivingKm ? `${(roadUniqueKm/drivingKm*100).toFixed(1)}%` : '0.0%';
  travelStats.activities.textContent=(persistedJourneyMileageById.size+footActivities.length).toLocaleString();
}

function motorwayNetworkCompletionPercent() {
  const completedByRegion={GB:0,NI:0};
  for (const road of canonicalRoads.values()) {
    if (road.status!=='ready' || !road.anchors.length) continue;
    const fraction=Math.min(1,road.coveredAnchorIds.size/road.anchors.length);
    completedByRegion[road.region]+=road.totalKm*fraction;
  }
  completedByRegion.GB=Math.min(GB_MOTORWAY_NETWORK_KM,completedByRegion.GB);
  completedByRegion.NI=Math.min(NI_MOTORWAY_NETWORK_KM,completedByRegion.NI);
  return Math.min(100,(completedByRegion.GB+completedByRegion.NI)/UK_MOTORWAY_NETWORK_KM*100);
}

let crossingProgressCache={signature:null,entries:null};

function segmentPassesThroughCrossingZone(segment, zones=[]) {
  // A bridge is a corridor, not a pin. Sampling preserves recognition when
  // map-matched geometry is slightly offset from the published centreline.
  return zones.some(zone=>{
    const lengthM=haversineMetres(segment.a,segment.b);
    const steps=Math.max(1,Math.ceil((Number.isFinite(lengthM) ? lengthM : 0)/100));
    for (let index=0;index<=steps;index++) {
      const [lng,lat]=interpolateLngLat(segment.a,segment.b,index/steps);
      if (lng>=zone.west && lng<=zone.east && lat>=zone.south && lat<=zone.north) return true;
    }
    return false;
  });
}

function crossingSetProgress(definition) {
  const records=savedRoadRecords();
  const byId=new Map(records.map(record=>[journeyIdentity(record),record]));
  const segments=creditedSegmentsForMap(records);
  const signature=(creditedMapSegmentCache.signature || '')+'|'+records.length+'|'+definition.id;
  if (crossingProgressCache.signature===signature) return crossingProgressCache.entries;

  const entries=definition.crossings.map(crossing=>({
    ...crossing,
    journeyIds:new Set(),
    completed:false,
    completedAt:null
  }));

  // Use the matched road geometry, rather than one road reference alone: a
  // crossing can be reached via a tunnel or a bridge, and both must count.
  for (const segment of segments) {
    for (const entry of entries) {
      const nearby=entry.locations.some(([lng,lat])=>
        distancePointToSegmentM([lng,lat],segment.a,segment.b)<=entry.radiusM
      ) || segmentPassesThroughCrossingZone(segment,entry.zones);
      if (!nearby) continue;
      for (const id of segment.journeyIds) entry.journeyIds.add(id);
    }
  }
  for (const entry of entries) {
    const dates=[...entry.journeyIds]
      .map(id=>Date.parse(byId.get(id)?.start || ''))
      .filter(Number.isFinite)
      .sort((a,b)=>a-b);
    entry.completed=entry.journeyIds.size>0;
    entry.completedAt=dates.length ? new Date(dates[0]).toISOString() : null;
    delete entry.journeyIds;
  }
  crossingProgressCache={signature,entries};
  return entries;
}

function achievementIsEarned(definition) {
  if (definition.type==='crossing-set') return crossingSetProgress(definition).every(entry=>entry.completed);
  if (definition.type==='network-percent') return motorwayNetworkCompletionPercent() >= definition.target;
  if (definition.type==='motorway-visited') {
    const road=canonicalRoads.get(definition.roadId);
    return Boolean(
      persistedManualRefs.has(definition.roadId) ||
      persistedCoverageByRef.get(definition.roadId)?.size ||
      (road?.status==='ready' && road.coveredAnchorIds.size)
    );
  }
  if (definition.type==='a-road-landmark') {
    const road=canonicalARoadState(definition.roadId);
    if (!road || road.status!=='ready' || !road.coveredAnchorIds.size) return false;
    const [landmarkX,landmarkY]=mercatorXY(definition.landmark[0],definition.landmark[1]);
    return road.anchors.some(anchor=>
      road.coveredAnchorIds.has(anchor.id) &&
      Math.hypot(anchor.x-landmarkX,anchor.y-landmarkY)<=definition.radiusM
    );
  }
  const road=canonicalRoads.get(definition.roadId);
  if (!road || road.status!=='ready' || !road.coveredAnchorIds.size) return false;
  const [summitX,summitY]=mercatorXY(definition.summit[0],definition.summit[1]);
  return road.anchors.some(anchor=>
    road.coveredAnchorIds.has(anchor.id) &&
    Math.hypot(anchor.x-summitX,anchor.y-summitY)<=definition.radiusM
  );
}

function renderAchievementCelebration() {
  const definition=achievementCelebrationQueue[achievementCelebrationIndex];
  if (!definition) return;
  if (achievementCelebrationIcon) achievementCelebrationIcon.textContent=definition.icon || '★';
  if (achievementCelebrationTitle) achievementCelebrationTitle.textContent=definition.title;
  if (achievementCelebrationDescription) achievementCelebrationDescription.textContent=definition.description || '';
  if (achievementCelebrationDetail) achievementCelebrationDetail.textContent=definition.detail || '';
  const multiple=achievementCelebrationQueue.length>1;
  previousAchievementCelebration?.classList.toggle('hidden',!multiple);
  nextAchievementCelebration?.classList.toggle('hidden',!multiple);
  if (previousAchievementCelebration) previousAchievementCelebration.disabled=achievementCelebrationIndex===0;
  if (nextAchievementCelebration) nextAchievementCelebration.disabled=achievementCelebrationIndex===achievementCelebrationQueue.length-1;
  if (achievementCelebrationPosition) {
    achievementCelebrationPosition.textContent=multiple
      ? (achievementCelebrationIndex+1)+' of '+achievementCelebrationQueue.length
      : '';
    achievementCelebrationPosition.classList.toggle('hidden',!multiple);
  }
  if (closeAchievementCelebration) {
    closeAchievementCelebration.textContent=multiple && achievementCelebrationIndex<achievementCelebrationQueue.length-1
      ? 'Claim all achievements'
      : 'Claim achievement';
  }
}

function hideAchievementCelebration() {
  achievementCelebrationOpen=false;
  achievementCelebrationQueue=[];
  achievementCelebrationIndex=0;
  achievementCelebration.classList.add('hidden');
}

function showAchievementCelebration(definitions=[]) {
  const known=new Set(achievementCelebrationQueue.map(definition=>definition.id));
  for (const definition of definitions) {
    if (definition?.id && !known.has(definition.id)) {
      achievementCelebrationQueue.push(definition);
      known.add(definition.id);
    }
  }
  if (!achievementCelebrationQueue.length) return;
  achievementCelebrationIndex=0;
  renderAchievementCelebration();
  if (achievementCelebrationOpen) return;
  achievementCelebrationOpen=true;
  achievementCelebration.classList.remove('hidden');
  closeAchievementCelebration.focus({preventScroll:true});
}

function unlockAchievement(definition) {
  if (!definition?.id || persistedAchievements.has(definition.id)) return false;
  persistedAchievements.set(definition.id,{unlockedAt:new Date().toISOString(),announced:true});
  scheduleLocalProgressSave();
  showAchievementCelebration([definition]);
  return true;
}

window.roadprintsUnlockAchievement=unlockAchievement;

function evaluateAchievements() {
  const newlyUnlocked=[];
  for (const definition of ROADPRINTS_ACHIEVEMENTS) {
    if (!achievementIsEarned(definition)) continue;
    if (unlockAchievement(definition)) newlyUnlocked.push(definition);
  }
  renderAchievements();
  if (newlyUnlocked.length>1) showAchievementCelebration(newlyUnlocked);
}

function renderAchievements() {
  if (!achievementsCard || !achievementList) return;
  const hasData=shouldShowDataDashboard() && (
    persistedMapJourneys.size || persistedCoverageByRef.size || persistedManualRefs.size || persistedManualARoadRefs.size
  );
  achievementsCard.classList.toggle('hidden',!hasData);
  if (!hasData) return;

  const earnedCount=ROADPRINTS_ACHIEVEMENTS.filter(definition=>persistedAchievements.has(definition.id)).length;
  achievementCount.textContent=`${earnedCount} of ${ROADPRINTS_ACHIEVEMENTS.length}`;
  achievementList.innerHTML='';
  for (const definition of ROADPRINTS_ACHIEVEMENTS) {
    const unlocked=persistedAchievements.has(definition.id);
    const item=document.createElement('article');
    item.className=`achievement ${unlocked?'unlocked':'locked'}`;
    const icon=document.createElement('span');
    icon.className='achievement-icon';
    icon.textContent=unlocked ? definition.icon : '🔒';
    const copy=document.createElement('div');
    const progress=definition.type==='crossing-set' ? crossingSetProgress(definition) : null;
    const completeCount=progress ? progress.filter(entry=>entry.completed).length : 0;
    const eyebrow=document.createElement('small');
    eyebrow.textContent=unlocked ? 'Unlocked' : progress ? completeCount+' of '+progress.length+' crossings' : 'Next milestone';
    const title=document.createElement('strong');
    title.textContent=definition.title;
    const description=document.createElement('span');
    description.textContent=unlocked ? definition.detail : progress ? completeCount+' of '+progress.length+' great road crossings completed' : definition.description;
    copy.append(eyebrow,title,description);
    if (!progress) {
      item.append(icon,copy);
    } else {
      item.classList.add('achievement-crossing-set');
      const main=document.createElement('div');
      main.className='achievement-main';
      main.append(icon,copy);
      const details=document.createElement('details');
      details.className='achievement-crossing-progress';
      const summary=document.createElement('summary');
      summary.textContent='View crossing checklist';
      const meter=document.createElement('div');
      meter.className='achievement-crossing-meter';
      const fill=document.createElement('span');
      fill.style.width=(completeCount/progress.length*100)+'%';
      meter.append(fill);
      const checklist=document.createElement('ul');
      for (const entry of progress) {
        const row=document.createElement('li');
        row.className=entry.completed ? 'complete' : '';
        const name=document.createElement('strong');
        name.textContent=(entry.completed ? '✓ ' : '○ ')+entry.title;
        const status=document.createElement('span');
        status.textContent=entry.completedAt ? 'First crossed '+formatDate(entry.completedAt) : entry.hint;
        row.append(name,status); checklist.append(row);
      }
      details.append(summary,meter,checklist);
      item.append(main,details);
    }
    achievementList.append(item);
  }
}

async function startNextFootBatch() {
  if (footMatching) return;
  const candidates=footBatches.flatMap(batch=>batch.activities.map(activity=>({batch,activity})))
    .filter(({activity})=>!activity.matchedGeoJson && !activity.matchError);
  if (!candidates.length) return;
  await showFootMap();
  footMatching=true;
  footMatchingPaused=false;
  footMatchingError=null;
  footMatchingBatchId=candidates[0].batch.id;
  footMatchingProgress={area:'walking and running routes',completed:0,total:candidates.length,succeeded:0,failed:0};
  let cursor=0,lastFootMapBatchCount=0;

  const processCandidate=async ({batch,activity})=>{
    footMatchingBatchId=batch.id;
    try {
      const response=await fetch(`${API_BASE_URL}/match-walking`,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({points:activity.points})});
      const data=await response.json().catch(()=>({}));
      if (!response.ok) throw new Error(data.detail || `HTTP ${response.status}`);
      activity.matchedGeoJson=data.geojson;
      activity.roadGeoJson=data.road_geojson || {type:'FeatureCollection',features:[]};
      activity.matchQuality=assessMatchQuality(activity,data);
      await saveFootActivityMatch(activity);
      batch.matched++;
      footMatchingProgress.succeeded++;
      if (easyImportRunning && (footMatchingProgress.succeeded===1 || footMatchingProgress.succeeded%LIVE_IMPORT_PREVIEW_INTERVAL===0)) {
        appendLiveImportGeometry(activity,{color:'#7642a8',weight:4,opacity:.86});
      }
    } catch (err) {
      activity.matchError=err.message || String(err);
      footMatchingProgress.failed++;
      try { await saveFootActivityMatch(activity); } catch (saveError) { footMatchingError=`${activity.matchError}. It could not be saved: ${saveError.message || saveError}`; }
    } finally {
      footMatchingProgress.completed++;
      renderFootQueue();
      try {
        if (easyImportRunning) {
          if (footMatchingProgress.completed-lastFootMapBatchCount>=LIVE_IMPORT_BATCH_SIZE) {
            refreshImportMapBatch(); lastFootMapBatchCount=footMatchingProgress.completed;
          }
          if (footMatchingProgress.completed===1) fitFootRoutes();
        } else {
          renderMap();
          if (footMatchingProgress.completed===1) fitFootRoutes();
        }
      } catch (err) { footMatchingError=`A route was processed, but the map could not update: ${err.message || err}`; }
    }
  };

  const worker=async()=>{
    while (true) {
      while (footMatchingPaused) {
        renderFootQueue();
        await new Promise(resolve=>setTimeout(resolve,250));
      }
      const candidate=candidates[cursor++];
      if (!candidate) return;
      await processCandidate(candidate);
      await new Promise(resolve=>setTimeout(resolve,100));
    }
  };

  renderFootQueue();
  try {
    await Promise.all(Array.from({length:Math.min(2,candidates.length)},worker));
  } catch (err) {
    footMatchingError=err.message || String(err);
  } finally {
    if (importFootStartedAt && footMatchingProgress) {
      const seconds=Math.max(1,Math.round((Date.now()-importFootStartedAt)/1000));
      const elapsed=seconds>=60 ? `${Math.floor(seconds/60)}m ${seconds%60}s` : `${seconds}s`;
      importFootResult=`completed in ${elapsed}`;
    }
    footMatching=false; footMatchingPaused=false; footMatchingBatchId=null; footMatchingProgress=null;
    buildFootBatches(); renderFootQueue();
    if (!easyImportRunning) renderMap();
    if (footBatches.some(batch=>batch.activities.some(activity=>!activity.matchedGeoJson && !activity.matchError))) {
      setTimeout(()=>{ if (!footMatching) void startNextFootBatch(); },1500);
    }
  }
}

async function showFootMap() {
  if (!shouldShowDataDashboard()) return true;
  const ready=await ensureLeaflet();
  if (!ready) {
    footMatchingError='The on-foot routes matched, but the map library could not load on this device.';
    return;
  }
  mapCard.classList.remove('hidden');
  mapTitle.textContent='Journey map';
  mapIntro.textContent='One combined map for your journeys: black shows driven routes, purple shows walking and running routes, and the layer control lets you compare them with motorway and A-road coverage.';
  initMap();
  renderMap();
  requestAnimationFrame(()=>{ map?.invalidateSize(true); fitFootRoutes(); });
}

function fitFootRoutes() {
  if (!map || !window.L) return;
  const points=footActivities.filter(activity=>activity.matchedGeoJson).flatMap(activity=>activity.points || []);
  if (!points.length) return;
  map.fitBounds(L.latLngBounds(points.map(point=>[point.lat,point.lng])),{padding:[24,24],maxZoom:15});
}


function renderIgnoredJourneys() {
  ignoredCount.textContent = ignoredJourneys.length.toLocaleString();
  ignoredList.innerHTML = '';

  if (!ignoredJourneys.length) {
    ignoredCard.classList.add('hidden');
    return;
  }

  ignoredCard.classList.remove('hidden');

  for (const j of ignoredJourneys) {
    const item = document.createElement('div');
    item.className = 'ignored-item';
    item.textContent =
      `${formatDate(j.start)} · ${formatTime(j.start)}–${formatTime(j.end)} · ` +
      `${j.pathPointCount} Timeline point${j.pathPointCount === 1 ? '' : 's'}` +
      `${Number.isFinite(j.googleDistanceKm) ? ` · ${j.googleDistanceKm.toFixed(1)} km Google distance` : ''}`;
    ignoredList.append(item);
  }
}

function setEasyProgressStatus(primary, secondary) {
  easyProgressText.replaceChildren();
  const headline=document.createElement('strong');
  headline.textContent=primary;
  const detail=document.createElement('span');
  detail.textContent=secondary;
  easyProgressText.append(headline,detail);
}

function updateEasyImportPauseButton() {
  const button = document.getElementById('stopEasyImport');
  if (!button) return;

  button.classList.toggle('hidden',!easyImportRunning);

  if (!easyImportRunning) {
    button.textContent = 'Pause';
    button.disabled = true;
    button.setAttribute('aria-pressed', 'false');
    return;
  }

  button.disabled = false;
  button.textContent = easyImportPaused ? 'Resume' : 'Pause';
  button.setAttribute('aria-pressed', String(easyImportPaused));
}

function startDetailedImport() {
  importMode = 'detailed';
  importModeCard.classList.add('hidden');
  easyProgress.classList.add('hidden');
  renderAll('Timeline.json');
}

async function startEasyImport() {
  const sessionId = trackingSessionId;
  const importStartedAt=Date.now();
  if (easyImportRunning) return;
  importMode = 'easy';
  easyImportPaused = false;
  easyImportRunning = true;
  // Automatic matching is the one time the map should open by itself: it is
  // the live progress display, rather than a heavy saved-map restore.
  mapRenderingRequested=true;
  updateEasyImportPauseButton();
  importModeCard.classList.add('hidden');
  dataSourceCard.classList.add('hidden');
  easyProgress.classList.remove('hidden');
  updateEasyImportPauseButton();

  renderAll('Timeline.json');
  journeyList.style.display = 'none';

  // The road and pedestrian matchers are independently throttled, so both queues
  // can progress at the same time without adding pressure to either service.
  if (footActivities.some(activity=>!activity.matchedGeoJson && !activity.matchError)) {
    importFootStartedAt=Date.now();
    importFootResult=null;
    void startNextFootBatch();
  }

  const candidates = currentImportJourneys().filter(j => j.points.length > 1);
  let completed = 0;
  let succeeded = 0;
  let failed = 0;
  let lastRoadMapBatchCount = 0;

  easyProgressBar.max = candidates.length || 1;
  easyProgressBar.value = 0;

  for (const journey of candidates) {
    if (sessionId !== trackingSessionId) return;

    while (easyImportPaused && sessionId === trackingSessionId) {
      setEasyProgressStatus(
        `Paused · ${completed} / ${candidates.length}`,
        `${succeeded} matched · ${failed} skipped`
      );
      await new Promise(resolve => setTimeout(resolve, 250));
    }

    if (sessionId !== trackingSessionId) return;

    setEasyProgressStatus(
      `${completed} / ${candidates.length}`,
      `Matching ${formatDate(journey.start)}`
    );

    try {
      const response = await fetch(`${API_BASE_URL}/match`, {
        method: 'POST',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify({points: journey.points})
      });
      const data = await response.json().catch(() => ({}));
      if (!response.ok) throw new Error(data.detail || `HTTP ${response.status}`);
      if (sessionId !== trackingSessionId) return;

      journey.matchedGeoJson = data.geojson;
      journey.motorwayGeoJson = data.motorway_geojson;
      journey.aRoadGeoJson = data.a_road_geojson || {type:'FeatureCollection',features:[]};
      journey.roadGeoJson = data.road_geojson || {type:'FeatureCollection',features:[]};
      journey.otherRoadDistanceKm = Number(data.other_road_distance_m || 0) / 1000;
      delete journey.otherRoadGeoJson;
      journey.matchedDistanceKm = Number(data.matched_distance_m || 0) / 1000;
      journey.matchedTracepoints = Number(data.matched_tracepoints || 0);
      journey.pointsSentToMatcher = Number(data.points_sent_to_matcher || 0);
      journey.matchQuality = assessMatchQuality(journey, data);
      await saveJourneyToMapArchive(journey);
      recordJourneyProcessed(journey);
      scheduleLocalProgressSave();
      succeeded++;
      if (succeeded===1 || succeeded%LIVE_IMPORT_PREVIEW_INTERVAL===0) {
        appendLiveImportGeometry(journey,{color:'#111111',weight:4,opacity:.78});
      }
    } catch (err) {
      journey.easyImportError = err.message || String(err);
      failed++;
    }

    completed++;
    easyProgressBar.value = completed;
    setEasyProgressStatus(
      `${completed} / ${candidates.length}`,
      `${succeeded} matched · ${failed} skipped`
    );
    if (succeeded-lastRoadMapBatchCount>=LIVE_IMPORT_BATCH_SIZE) {
      refreshImportMapBatch();
      lastRoadMapBatchCount=succeeded;
    }
    // Yield briefly so progress remains responsive, without adding a
    // quarter-second idle period after every completed matcher request.
    await new Promise(resolve => setTimeout(resolve, 20));
  }

  if (sessionId !== trackingSessionId) return;
  // Keep the queue workspace on screen until on-foot matching has completed.
  updateEasyImportPauseButton();
  if (diagnostics.mileageRebuild && failed===0) {
    persistedMileageHistoryComplete=true;
    scheduleLocalProgressSave();
  }
  const roadSeconds=Math.max(1,Math.round((Date.now()-importStartedAt)/1000));
  const roadElapsed=roadSeconds>=60 ? `${Math.floor(roadSeconds/60)}m ${roadSeconds%60}s` : `${roadSeconds}s`;
  if (footMatching) {
    setEasyProgressStatus('Road matching complete',`${succeeded} matched · ${failed} skipped · completed in ${roadElapsed}`);
    while (footMatching && sessionId===trackingSessionId) {
      await new Promise(resolve=>setTimeout(resolve,200));
    }
  }
  if (sessionId !== trackingSessionId) return;
  easyImportRunning=false;
  easyImportPaused=false;
  updateEasyImportPauseButton();
  refreshImportMapBatch();
  renderRoadQueue();
  setEasyProgressStatus('Road matching complete', `${succeeded} matched · ${failed} skipped · completed in ${roadElapsed}`);
  document.getElementById('plotImportedMap')?.classList.remove('hidden');
  if (mapStatus) {
    mapStatus.classList.add('hidden');
  }
}

function renderAll(fileName) {
  const importJourneys=currentImportJourneys();
  const points = importJourneys.reduce((n, j) => n + j.points.length, 0);

  dataDateRange.querySelector('span').textContent = formatDataDateRange();
  journeyCount.textContent = importJourneys.length.toLocaleString();
  const journeyLabel = journeyCount.parentElement?.querySelector('span');
  if (journeyLabel) journeyLabel.textContent = 'usable journeys';
  pointCount.textContent = points.toLocaleString();

  // The old per-journey chooser is retained in the DOM only for the detailed
  // import fallback. Automatic imports already manage the queue themselves,
  // so rendering hundreds of hidden journey rows wastes mobile memory.
  summaryCard.classList.add('hidden');
  mapCard.classList.remove('hidden');
  nextCard?.classList.remove('hidden');

  journeyList.innerHTML='';
  journeyList.style.display='none';
  initMap();
  renderMap();
  updateSelectedCount();

  setTimeout(showDefaultUnitedKingdomView,100);
}

function renderJourneyList() {
  journeyList.innerHTML = '';

  journeys.forEach((j, i) => {
    if (j._savedArchive) return;
    const wrapper = document.createElement('div');
    wrapper.className = 'journey';

    const cb = document.createElement('input');
    cb.type = 'checkbox';
    cb.checked = j.selected;
    cb.dataset.i = i;
    cb.addEventListener('change', e => {
      journeys[Number(e.target.dataset.i)].selected = e.target.checked;
      updateSelectedCount();
      renderMap();
    });

    const body = document.createElement('div');

    const title = document.createElement('div');
    title.className = 'journey-title';
    title.textContent =
      `${formatDate(j.start)} · ${formatTime(j.start)}–${formatTime(j.end)}`;

    if (j.matchQuality) {
      const badge = document.createElement('span');
      badge.className = `match-badge ${j.matchQuality.level}`;
      badge.textContent = j.matchQuality.label;
      title.append(badge);
    }

    const meta = document.createElement('div');
    meta.className = 'journey-meta';

    const detail = [];
    detail.push(`${j.pathPointCount.toLocaleString()} Timeline point${j.pathPointCount === 1 ? '' : 's'}`);

    if (Number.isFinite(j.googleDistanceKm)) {
      detail.push(`${j.googleDistanceKm.toFixed(1)} km Google distance`);
    }

    if (j.points.length < 2) {
      detail.push('no drawable trace');
    } else if (j.pathPointCount === 0) {
      detail.push('start/end anchors only');
    }

    meta.textContent = detail.join(' · ');

    const actions = document.createElement('div');
    actions.className = 'journey-actions';

    const matchButton = document.createElement('button');
    matchButton.type = 'button';
    matchButton.textContent = j.matchedGeoJson ? 'Re-match road' : 'Match road';
    matchButton.disabled = j.points.length < 2;

    const status = document.createElement('div');
    status.className = j.matchedGeoJson
      ? `match-status ${j.matchQuality?.level === 'high' ? 'ok' : 'warn'}`
      : 'match-status';

    if (j.matchedGeoJson) {
      status.textContent = matchStatusText(j);
    } else {
      status.textContent = j.points.length < 2
        ? 'Not enough coordinates to road-match.'
        : 'Not matched yet.';
    }

    matchButton.addEventListener('click', () => matchJourney(i, matchButton, status));

    actions.append(matchButton);
    body.append(title, meta, actions, status);
    wrapper.append(cb, body);
    journeyList.append(wrapper);
  });
}

function assessMatchQuality(journey, data) {
  const sent = Number(data.points_sent_to_matcher || 0);
  const matched = Number(data.matched_tracepoints || 0);
  const coverage = sent > 0 ? matched / sent : 0;

  const confidences = (data.geojson?.features || [])
    .map(f => Number(f?.properties?.confidence))
    .filter(Number.isFinite);

  const avgConfidence = confidences.length
    ? confidences.reduce((a, b) => a + b, 0) / confidences.length
    : null;

  if (coverage >= 0.9 && (avgConfidence === null || avgConfidence >= 0.65)) {
    return {level: 'high', label: 'HIGH', coverage, avgConfidence};
  }

  if (coverage >= 0.65 && (avgConfidence === null || avgConfidence >= 0.30)) {
    return {level: 'review', label: 'REVIEW', coverage, avgConfidence};
  }

  return {level: 'low', label: 'LOW', coverage, avgConfidence};
}

function matchStatusText(journey) {
  const q = journey.matchQuality;
  const coverage = q ? `${Math.round(q.coverage * 100)}% point coverage` : '';
  const confidence = q && Number.isFinite(q.avgConfidence)
    ? `${Math.round(q.avgConfidence * 100)}% avg confidence`
    : '';

  return [
    `${journey.matchedTracepoints}/${journey.pointsSentToMatcher} matched tracepoints`,
    coverage,
    confidence
  ].filter(Boolean).join(' · ');
}

async function matchJourney(index, button, statusNode) {
  const journey = journeys[index];
  if (!journey || journey.points.length < 2) return;

  button.disabled = true;
  statusNode.className = 'match-status warn';
  statusNode.textContent = 'Sending journey to road matcher…';

  try {
    const response = await fetch(`${API_BASE_URL}/match`, {
      method: 'POST',
      headers: {'Content-Type': 'application/json'},
      body: JSON.stringify({points: journey.points})
    });

    const data = await response.json().catch(() => ({}));

    if (!response.ok) {
      throw new Error(data.detail || `Road matcher returned HTTP ${response.status}.`);
    }

    journey.matchedGeoJson = data.geojson;
    journey.motorwayGeoJson = data.motorway_geojson;
    journey.aRoadGeoJson = data.a_road_geojson || {type:'FeatureCollection',features:[]};
    journey.otherRoadDistanceKm = Number(data.other_road_distance_m || 0) / 1000;
    delete journey.otherRoadGeoJson;
    journey.matchedDistanceKm = Number(data.matched_distance_m || 0) / 1000;
    journey.matchedTracepoints = Number(data.matched_tracepoints || 0);
    journey.pointsSentToMatcher = Number(data.points_sent_to_matcher || 0);
    journey.matchQuality = assessMatchQuality(journey, data);
    await saveJourneyToMapArchive(journey);
    recordJourneyProcessed(journey);
    scheduleLocalProgressSave();

    statusNode.className =
      `match-status ${journey.matchQuality.level === 'high' ? 'ok' : 'warn'}`;
    statusNode.textContent = matchStatusText(journey);

    renderJourneyList();
    renderMap();
    fitMatchedJourney(journey);
  } catch (err) {
    statusNode.className = 'match-status error';
    statusNode.textContent = err.message || String(err);
  } finally {
    button.disabled = false;
  }
}

function fitMatchedJourney(journey) {
  if (!map || !journey?.matchedGeoJson || !window.L) return;

  try {
    const layer = L.geoJSON(journey.matchedGeoJson);
    const bounds = layer.getBounds();
    if (bounds.isValid()) {
      map.fitBounds(bounds, {padding: [24, 24], maxZoom: 15});
    }
  } catch (err) {}
}

function clearMatchedRoads() {
  currentImportJourneys().forEach(j => {
    delete j.matchedGeoJson;
    delete j.motorwayGeoJson;
    delete j.aRoadGeoJson;
    delete j.roadGeoJson;
    delete j.otherRoadGeoJson;
    delete j.otherRoadDistanceKm;
    delete j.matchedDistanceKm;
    delete j.matchedTracepoints;
    delete j.pointsSentToMatcher;
    delete j.matchQuality;
  });
  renderJourneyList();
  renderMap();
}

function syncCheckboxes() {
  journeyList.querySelectorAll('input[type=checkbox]').forEach(cb => {
    cb.checked = journeys[Number(cb.dataset.i)].selected;
  });
}

function updateSelectedCount() {
  selectedCount.textContent =
    currentImportJourneys().filter(j => j.selected).length.toLocaleString();
}

async function ensureLeaflet() {
  if (window.L && typeof window.L.map === 'function') return true;

  if (mapStatus) {
    mapStatus.className = 'muted map-status warn';
    mapStatus.textContent = 'Primary map library did not load. Trying fallback…';
  }

  try {
    await loadScript('https://cdn.jsdelivr.net/npm/leaflet@1.9.4/dist/leaflet.js');
  } catch (err) {}

  if (window.L && typeof window.L.map === 'function') return true;

  if (mapStatus) {
    mapStatus.className = 'error map-status';
    mapStatus.textContent =
      'The map library could not be loaded on this device/network. Journey parsing still works.';
  }

  return false;
}

function loadScript(src) {
  return new Promise((resolve, reject) => {
    const script = document.createElement('script');
    script.src = src;
    script.async = true;
    script.onload = resolve;
    script.onerror = reject;
    document.head.appendChild(script);
  });
}

function initMap() {
  if (!mapRenderingRequested) {
    if (mapStatus) {
      mapStatus.className='muted map-status';
      mapStatus.textContent='Map paused to keep this page responsive. Tap “Load map” when you are ready to view it.';
    }
    return;
  }
  if (map) {
    requestAnimationFrame(() => map.invalidateSize(true));
    return;
  }

  if (!window.L || typeof window.L.map !== 'function') {
    if (mapStatus) {
      mapStatus.className = 'error map-status';
      mapStatus.textContent = 'Map unavailable: the Leaflet library did not initialise.';
    }
    return;
  }

  try {
    map = L.map('map', {
      preferCanvas: true,
      zoomControl: true
    }).setView(DEFAULT_MAP_CENTER,DEFAULT_MAP_ZOOM);

    // Fixed panes keep semantic colours in a stable order even when layer
    // groups are cleared and rebuilt during imports, zooming or refinement.
    const paneOrder = {
      drivenRoadPane: 410,
      canonicalReferencePane: 420,
      motorwayUnconfirmedPane: 430,
      // Incomplete A-road reference is deliberately behind completed
      // coverage, so small matching gaps cannot visually punch through green.
      aRoadUnconfirmedPane: 435,
      aRoadConfirmedPane: 437,
      motorwayConfirmedPane: 440
    };
    for (const [paneName,zIndex] of Object.entries(paneOrder)) {
      const pane=map.createPane(paneName);
      pane.style.zIndex=String(zIndex);
      pane.style.pointerEvents='none';
    }

    const tiles = L.tileLayer(
      'https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',
      {
        maxZoom: 19,
        attribution: '&copy; OpenStreetMap contributors'
      }
    );

    let anyTileLoaded = false;

    tiles.on('tileload', () => {
      anyTileLoaded = true;
    });

    tiles.on('load', () => {
      if (mapStatus && !journeys.some(j => j.matchedGeoJson)) {
        mapStatus.className = 'muted map-status ok';
        if (onboardingMode==='manual') {
          const ready=[...manualMotorwayRefs]
            .filter(ref=>canonicalRoadState(ref).status==='ready').length;
          mapStatus.textContent=manualMotorwayRefs.size
            ? `${ready} of ${manualMotorwayRefs.size} selected motorway${manualMotorwayRefs.size===1?'':'s'} ready on the map.`
            : 'Select a motorway above to add it to your map.';
        } else {
          const selectedDrawable = journeys.filter(j => j.selected && j.points.length > 1).length;
          mapStatus.textContent =
            `Map loaded. ${selectedDrawable.toLocaleString()} selected raw journey trace${selectedDrawable === 1 ? '' : 's'} available.`;
        }
      }
    });

    tiles.on('tileerror', () => {
      if (!anyTileLoaded && mapStatus) {
        mapStatus.className = 'muted map-status warn';
        mapStatus.textContent =
          'The map frame loaded, but OpenStreetMap tiles are not loading on this device/network.';
      }
    });

    tiles.addTo(map);
    map.on('click', event=>{
      if (!mapCorrectionPanel.classList.contains('hidden')) handleMapCorrectionMapClick(event);
      else handleRefinementMapClick(event);
    });
    // The raw and matched-route layers were useful while validating the
    // matcher, but duplicate a large amount of geometry on a normal map.
    traceLayer = null;
    matchedLayer = null;
    creditedLayer = L.layerGroup().addTo(map);
    footLayer = L.layerGroup().addTo(map);
    // A temporary layer lets an automatic import visibly grow the map one
    // route at a time without rebuilding all saved geometry after each match.
    liveImportLayer = L.layerGroup().addTo(map);
    canonicalReferenceLayer = null;
    canonicalCoverageLayer = L.layerGroup().addTo(map);
    canonicalUncoveredLayer = L.layerGroup().addTo(map);
    canonicalARoadCoverageLayer = L.layerGroup().addTo(map);
    canonicalARoadUncoveredLayer = L.layerGroup().addTo(map);
    serviceStationLayer = L.layerGroup().addTo(map);

    mapLayerControl = L.control.layers(
      {},
      {
        'Road journeys (black)': creditedLayer,
        'On-foot journeys (purple)': footLayer,
        'Motorway service stations (unvisited)': serviceStationLayer,
        'A-road completed sections (green)': canonicalARoadCoverageLayer,
        'A-road incomplete sections (red)': canonicalARoadUncoveredLayer,
        'Motorway completed sections (blue)': canonicalCoverageLayer,
        'Motorway incomplete sections (red)': canonicalUncoveredLayer
      },
      {collapsed: true}
    ).addTo(map);

    window.roadprintsMapContext={map,mapLayerControl,serviceStationLayer};
    window.dispatchEvent(new CustomEvent('roadprints:map-ready',{detail:window.roadprintsMapContext}));

    // Refresh only after the pan or zoom has settled, not while the gesture
    // is in progress.
    map.on('moveend zoomend',()=>{
      clearTimeout(mapGeometryRefreshTimer);
      mapGeometryRefreshTimer=setTimeout(()=>{
        renderMap({deferCalculations:true,preserveLive:true});
        if (focusedJourneyId) clearReferenceMapLayers();
        else {
          renderCanonicalMapLayers();
          renderCanonicalARoadMapLayers();
        }
      },80);
    });

    if (mapStatus) {
      mapStatus.className = 'muted map-status';
      mapStatus.textContent = 'Map initialised. Loading tiles and journey traces…';
    }

    requestAnimationFrame(() => {
      map.invalidateSize(true);
      requestAnimationFrame(() => map.invalidateSize(true));
    });
  } catch (err) {
    if (mapStatus) {
      mapStatus.className = 'error map-status';
      mapStatus.textContent = `Map initialisation failed: ${err.message || err}`;
    }
  }
}

function geometrySegments(geojson) {
  const segments = [];

  for (const feature of geojson?.features || []) {
    const geometry = feature?.geometry;
    if (!geometry) continue;

    const lines =
      geometry.type === 'LineString'
        ? [geometry.coordinates]
        : geometry.type === 'MultiLineString'
          ? geometry.coordinates
          : [];

    for (const line of lines) {
      for (let i = 1; i < line.length; i++) {
        const a = line[i - 1];
        const b = line[i];
        if (!Array.isArray(a) || !Array.isArray(b)) continue;
        segments.push([a, b]);
      }
    }
  }

  return segments;
}

function pointKey(point) {
  // 5 decimal places is roughly metre-level in the UK.
  return `${Number(point[0]).toFixed(5)},${Number(point[1]).toFixed(5)}`;
}

function segmentKey(a, b) {
  const aa = pointKey(a);
  const bb = pointKey(b);
  return aa < bb ? `${aa}|${bb}` : `${bb}|${aa}`;
}

function cloneRemovalEvidence(source) {
  return new Map([...source.entries()].map(([key,ids])=>[key,new Set(ids)]));
}

function cloneCanonicalRemovalEvidence(source) {
  return new Map([...source.entries()].map(([roadId,anchors])=>[
    roadId,new Map([...anchors.entries()].map(([anchorId,ids])=>[anchorId,new Set(ids)]))
  ]));
}

function restoreMapCorrectionState(snapshot) {
  removedSegmentEvidence.clear();
  for (const [key,ids] of snapshot.segments) removedSegmentEvidence.set(key,new Set(ids));
  canonicalRemovalEvidenceByRef.clear();
  for (const [roadId,anchors] of snapshot.canonical) {
    canonicalRemovalEvidenceByRef.set(roadId,new Map([...anchors.entries()].map(([id,ids])=>[id,new Set(ids)])));
  }
  creditedMapSegmentCache.signature=null;
}

function segmentEvidenceIsRemoved(key) {
  return removedSegmentEvidence.has(key);
}

function buildCreditedSegments(drawable, {includeRemoved=false}={}) {
  const unique = new Map();

  for (const journey of drawable) {
    if (!journey.matchedGeoJson) continue;
    const journeyId=journeyIdentity(journey);

    for (const [a, b] of geometrySegments(journey.matchedGeoJson)) {
      const key = segmentKey(a, b);
      if (!includeRemoved && segmentEvidenceIsRemoved(key,journeyId)) continue;

      if (!unique.has(key)) {
        unique.set(key, {
          a,
          b,
          journeys: 0,
          journeyIds: new Set(),
          hasRoadEvidence:false,
          quality: journey.matchQuality?.level || 'review'
        });
      }

      const item = unique.get(key);
      item.journeys += 1;
      if (journeyId) item.journeyIds.add(journeyId);
      if (!journey.travelMode || journey.travelMode==='ROAD') item.hasRoadEvidence=true;

      // Keep the least-confident status when multiple journeys credit a segment.
      if (journey.matchQuality?.level === 'low') item.quality = 'low';
      else if (
        journey.matchQuality?.level === 'review' &&
        item.quality === 'high'
      ) item.quality = 'review';
    }
  }

  return [...unique.values()];
}

function creditedSegmentsForMap(drawable) {
  // Panning does not alter the underlying evidence. Retaining this lightweight
  // index avoids rebuilding all 134k+ unique segments after every move.
  const signature=drawable.map(j=>[
    journeyIdentity(j),
    j.selected ? 1 : 0,
    j.matchedGeoJson?.features?.length || 0,
    j.matchQuality?.level || ''
  ].join(':')).join('|')+`:${removedSegmentEvidence.size}`;
  if (creditedMapSegmentCache.signature!==signature) {
    creditedMapSegmentCache={signature,segments:buildCreditedSegments(drawable)};
  }
  return creditedMapSegmentCache.segments;
}

function haversineMetres(a, b) {
  const R = 6371000;
  const toRad = d => d * Math.PI / 180;
  const lat1 = toRad(a[1]), lat2 = toRad(b[1]);
  const dLat = lat2 - lat1;
  const dLon = toRad(b[0] - a[0]);
  const h = Math.sin(dLat/2) ** 2 +
    Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLon/2) ** 2;
  return 2 * R * Math.asin(Math.sqrt(h));
}

function mercatorXY(lng, lat) {
  const R = 6378137;
  const x = R * lng * Math.PI / 180;
  const clippedLat = Math.max(-85, Math.min(85, lat));
  const y = R * Math.log(Math.tan(Math.PI / 4 + clippedLat * Math.PI / 360));
  return [x, y];
}

function distancePointToSegmentM(pointLngLat, a, b) {
  const [px,py]=mercatorXY(pointLngLat[0],pointLngLat[1]);
  const [ax,ay]=mercatorXY(a[0],a[1]);
  const [bx,by]=mercatorXY(b[0],b[1]);
  const dx=bx-ax, dy=by-ay;
  const lengthSquared=dx*dx+dy*dy;
  if (!lengthSquared) return Math.hypot(px-ax,py-ay);
  const t=Math.max(0,Math.min(1,((px-ax)*dx+(py-ay)*dy)/lengthSquared));
  return Math.hypot(px-(ax+dx*t),py-(ay+dy*t));
}

function setMapCorrectionMode(mode) {
  mapCorrectionMode=['remove','restore'].includes(mode) ? mode : null;
  mapCorrectionRemove.setAttribute('aria-pressed',String(mapCorrectionMode==='remove'));
  mapCorrectionRestore.setAttribute('aria-pressed',String(mapCorrectionMode==='restore'));
  if (!mapCorrectionMode) {
    mapStatus.className='muted map-status warn';
    mapStatus.textContent='Choose whether tapping should remove or restore a credited map section.';
    return;
  }
  mapStatus.className='muted map-status ok';
  mapStatus.textContent=mapCorrectionMode==='remove'
    ? 'Remove mode active. Tap a credited map section to remove it.'
    : 'Restore mode active. Tap a corrected map section to put it back on the map.';
}

function correctionTargetsNear(latlng, includeRemoved) {
  const point=[Number(latlng.lng),Number(latlng.lat)];
  const candidates=buildCreditedSegments(editableMappedActivities(),{includeRemoved})
    .map(segment=>({...segment,distanceM:distancePointToSegmentM(point,segment.a,segment.b)}))
    .sort((a,b)=>a.distanceM-b.distanceM);

  if (!includeRemoved) {
    // Map matching can contain many tiny links at one junction. Removing only
    // the closest one makes this a true precision tool for things such as a
    // private spur or a wrong turn, rather than a broad-area eraser.
    return candidates[0]?.distanceM<=45 ? [candidates[0]] : [];
  }

  // Restore is intentionally more forgiving: it lets someone recover a
  // previous broad correction in one tap, including corrections made before
  // the precision tool was introduced.
  return candidates.filter(segment=>
    segment.distanceM<=220 && removedSegmentEvidence.has(segmentKey(segment.a,segment.b))
  );
}

function recordCanonicalCorrectionForSegments(segments, mode) {
  for (const segment of segments) {
    if (!segment.hasRoadEvidence) continue;
    for (const road of canonicalRoads.values()) {
      if (road.status!=='ready') continue;
      const nearby=road.anchors.filter(anchor=>
        distancePointToSegmentM([anchor.lng,anchor.lat],segment.a,segment.b)<=CANONICAL_ANCHOR_MATCH_RADIUS_M
      );
      if (!nearby.length) continue;
      if (!canonicalRemovalEvidenceByRef.has(road.id)) canonicalRemovalEvidenceByRef.set(road.id,new Map());
      const removals=canonicalRemovalEvidenceByRef.get(road.id);
      for (const anchor of nearby) {
        if (mode==='restore') removals.delete(anchor.id);
        else removals.set(anchor.id,new Set(segment.journeyIds));
      }
      if (!removals.size) canonicalRemovalEvidenceByRef.delete(road.id);
    }
  }
}

function handleMapCorrectionMapClick(event) {
  if (!mapCorrectionMode) {
    setMapCorrectionMode(null);
    mapCorrectionRemove.focus();
    return;
  }
  const targets=correctionTargetsNear(event.latlng,mapCorrectionMode==='restore');
  if (!targets.length) {
    mapStatus.className='muted map-status warn';
    mapStatus.textContent='Tap closer to a credited map section.';
    return;
  }
  mapCorrectionUndoStack.push({
    segments:cloneRemovalEvidence(removedSegmentEvidence),
    canonical:cloneCanonicalRemovalEvidence(canonicalRemovalEvidenceByRef)
  });
  if (mapCorrectionUndoStack.length>30) mapCorrectionUndoStack.shift();
  for (const segment of targets) {
    const key=segmentKey(segment.a,segment.b);
    if (mapCorrectionMode==='restore') removedSegmentEvidence.delete(key);
    else removedSegmentEvidence.set(key,new Set(segment.journeyIds));
  }
  creditedMapSegmentCache.signature=null;
  const roadChanged=targets.some(segment=>segment.hasRoadEvidence);
  recordCanonicalCorrectionForSegments(targets,mapCorrectionMode);
  if (roadChanged) {
    canonicalCoverageDirty=true;
    canonicalARoadCoverageDirty=true;
    motorwayAggregateDirty=true;
  }
  mapCorrectionUndo.disabled=false;
  mapCorrectionChangesPending=true;
  // A correction is durable as soon as it is made. “Done” now only closes the
  // editor, so a refresh, accidental navigation or mobile browser suspension
  // cannot discard an already-applied section change.
  if (roadChanged) persistCorrectedMotorwayContributions();
  saveLocalProgressNow();
  renderMap({deferCalculations:true});
  const verb=mapCorrectionMode==='remove' ? 'Removed' : 'Restored';
  mapStatus.className='muted map-status ok';
  mapStatus.textContent=`${verb} ${targets.length} credited map segment${targets.length===1?'':'s'} · saved.`;
}

function startMapCorrection() {
  if (!shouldShowDataDashboard() || !editableMappedActivities().length) return;
  mapCorrectionUndoStack=[];
  mapCorrectionChangesPending=false;
  mapCorrectionUndo.disabled=true;
  mapCorrectionPanel.classList.remove('hidden');
  mapCard.classList.add('refinement-active');
  mapCorrectionStartButton.classList.add('hidden');
  setMapCorrectionMode(null);
  renderMap({deferCalculations:true});
  mapCorrectionRemove.focus();
}

function finishMapCorrection() {
  mapCorrectionMode=null;
  mapCorrectionPanel.classList.add('hidden');
  mapCard.classList.remove('refinement-active');
  if (mapCorrectionChangesPending) {
    if (motorwayAggregateDirty) {
      persistCorrectedMotorwayContributions();
      motorwayAggregateDirty=false;
    }
    saveLocalProgressNow();
  }
  mapCorrectionChangesPending=false;
  renderMap();
}

function corridorCellKey(lng, lat) {
  const [x, y] = mercatorXY(lng, lat);
  return `${Math.floor(x / MOTORWAY_CORRIDOR_CELL_M)},${Math.floor(y / MOTORWAY_CORRIDOR_CELL_M)}`;
}

function interpolateLngLat(a, b, t) {
  return [
    a[0] + (b[0] - a[0]) * t,
    a[1] + (b[1] - a[1]) * t
  ];
}

function addSegmentCorridorCells(cellSet, a, b) {
  const lengthM = haversineMetres(a, b);
  if (!Number.isFinite(lengthM) || lengthM <= 0) return;

  const samples = Math.max(1, Math.ceil(lengthM / MOTORWAY_SAMPLE_SPACING_M));

  for (let i = 0; i <= samples; i++) {
    const point = interpolateLngLat(a, b, i / samples);
    cellSet.add(corridorCellKey(point[0], point[1]));
  }
}

function setDistanceUnit(unit) {
  distanceUnit = unit === 'km' ? 'km' : 'miles';
  scheduleLocalProgressSave();

  unitMiles.classList.toggle('active', distanceUnit === 'miles');
  unitKm.classList.toggle('active', distanceUnit === 'km');
  unitMiles.setAttribute('aria-pressed', String(distanceUnit === 'miles'));
  unitKm.setAttribute('aria-pressed', String(distanceUnit === 'km'));
  aRoadUnitMiles.classList.toggle('active', distanceUnit === 'miles');
  aRoadUnitKm.classList.toggle('active', distanceUnit === 'km');
  aRoadUnitMiles.setAttribute('aria-pressed', String(distanceUnit === 'miles'));
  aRoadUnitKm.setAttribute('aria-pressed', String(distanceUnit === 'km'));

  renderMap();
  renderCanonicalMotorwayDashboard();
}

function displayDistance(km) {
  const format=value=>Number(value).toLocaleString('en-GB',{
    minimumFractionDigits:1,
    maximumFractionDigits:1
  });
  if (distanceUnit === 'km') {
    return `${format(km)} km`;
  }

  const miles = km * 0.6213711922;
  return `${format(miles)} mi`;
}


function gridKeyXY(x, y, cellM) {
  return `${Math.floor(x / cellM)},${Math.floor(y / cellM)}`;
}

function neighbourGridKeys(x, y, cellM) {
  const gx = Math.floor(x / cellM);
  const gy = Math.floor(y / cellM);
  const keys = [];
  for (let dx = -1; dx <= 1; dx++) {
    for (let dy = -1; dy <= 1; dy++) keys.push(`${gx + dx},${gy + dy}`);
  }
  return keys;
}

function sampleLineEvery(coords, spacingM, callback) {
  for (let i = 1; i < coords.length; i++) {
    const a = coords[i - 1], b = coords[i];
    const lengthM = haversineMetres(a, b);
    if (!Number.isFinite(lengthM) || lengthM <= 0) continue;
    const samples = Math.max(1, Math.ceil(lengthM / spacingM));
    for (let s = 0; s < samples; s++) callback(interpolateLngLat(a, b, s / samples));
  }
  if (coords.length) callback(coords[coords.length - 1]);
}

function overpassWayCoordinates(element) {
  return (element?.geometry || [])
    .map(p => [Number(p.lon), Number(p.lat)])
    .filter(p => Number.isFinite(p[0]) && Number.isFinite(p[1]));
}

function normaliseMotorwayRef(ref) {
  return String(ref || '').toUpperCase().replace(/\s+/g, '');
}

function isNorthernIrelandCoordinate(lng, lat) {
  return Number.isFinite(lng) && Number.isFinite(lat) &&
    lng < -5.3 && lat > 53.9 && lat < 55.6;
}

function parseMotorwayId(value) {
  const raw=String(value || '').toUpperCase().replace(/\s+/g,'');
  const region=raw.startsWith('NI:') ? 'NI' : 'GB';
  const ref=normaliseMotorwayRef(region==='NI' ? raw.slice(3) : raw);
  return {id:region==='NI' ? `NI:${ref}` : ref,region,ref};
}

function isMotorwayCoordinateForRegion(region, lng, lat) {
  return region==='NI'
    ? isNorthernIrelandCoordinate(lng,lat)
    : !isNorthernIrelandCoordinate(lng,lat);
}

function canonicalReferenceLengthKm(id, fallbackKm = 0) {
  const road=parseMotorwayId(id);
  const catalogue=road.region==='NI' ? NI_MOTORWAY_LENGTH_KM : MOTORWAY_LENGTH_KM;
  const canonicalKm=Number(catalogue[road.ref]);
  return Number.isFinite(canonicalKm) && canonicalKm > 0 ? canonicalKm : fallbackKm;
}

function canonicalRoadState(value) {
  const parsed=parseMotorwayId(value);
  if (!canonicalRoads.has(parsed.id)) {
    canonicalRoads.set(parsed.id, {
      id:parsed.id, ref:parsed.ref, region:parsed.region,
      status:'idle', error:null, ways:[], anchors:[],
      anchorIndex:new Map(), coveredAnchorIds:new Set(), totalKm:0
    });
  }
  return canonicalRoads.get(parsed.id);
}

function buildCanonicalAnchors(ways) {
  const anchors = [];
  const dedupeIndex = new Map();

  function addAnchor(point) {
    const [x,y] = mercatorXY(point[0],point[1]);
    for (const key of neighbourGridKeys(x,y,CANONICAL_DEDUPE_CELL_M)) {
      for (const id of dedupeIndex.get(key) || []) {
        const candidate=anchors[id];
        if (Math.hypot(x-candidate.x,y-candidate.y) <= CANONICAL_DEDUPE_RADIUS_M) return;
      }
    }
    const id=anchors.length;
    anchors.push({id,lng:point[0],lat:point[1],x,y});
    const key=gridKeyXY(x,y,CANONICAL_DEDUPE_CELL_M);
    if (!dedupeIndex.has(key)) dedupeIndex.set(key,[]);
    dedupeIndex.get(key).push(id);
  }

  for (const way of ways) sampleLineEvery(way.coords,CANONICAL_REFERENCE_SAMPLE_M,addAnchor);
  return anchors;
}

function buildAnchorIndex(anchors) {
  const index=new Map();
  for (const anchor of anchors) {
    const key=gridKeyXY(anchor.x,anchor.y,CANONICAL_INDEX_CELL_M);
    if (!index.has(key)) index.set(key,[]);
    index.get(key).push(anchor.id);
  }
  return index;
}

function nearestCanonicalAnchor(road, point) {
  if (!road || road.status !== 'ready') return null;
  const [x,y]=mercatorXY(point[0],point[1]);
  let best=null,bestDistance=CANONICAL_ANCHOR_MATCH_RADIUS_M;
  for (const key of neighbourGridKeys(x,y,CANONICAL_INDEX_CELL_M)) {
    for (const id of road.anchorIndex.get(key) || []) {
      const a=road.anchors[id];
      const d=Math.hypot(x-a.x,y-a.y);
      if (d < bestDistance) { bestDistance=d; best=id; }
    }
  }
  return best;
}


async function loadCanonicalCache(force = false) {
  if (!force && canonicalCache) return canonicalCache;
  if (!force && canonicalCachePromise) return canonicalCachePromise;

  canonicalCachePromise = (async () => {
    const response = await fetch(CANONICAL_CACHE_URL, {cache: 'no-store'});
    if (!response.ok) {
      throw new Error(`Canonical cache returned HTTP ${response.status}.`);
    }

    const data = await response.json();

    if (!data || data.version !== CANONICAL_CACHE_VERSION || !data.roads) {
      throw new Error('Canonical motorway cache format/version mismatch.');
    }

    canonicalCache = data;
    return data;
  })();

  try {
    return await canonicalCachePromise;
  } finally {
    canonicalCachePromise = null;
  }
}

function hydrateCanonicalRoadFromCache(road, cached) {
  const anchors = (cached.anchors || [])
    .map(point => [Number(point[0]), Number(point[1])])
    .filter(point =>
      Number.isFinite(point[0]) &&
      Number.isFinite(point[1]) &&
      isMotorwayCoordinateForRegion(road.region, point[0], point[1])
    )
    .map((point, id) => {
      const [lng, lat] = point;
      const [x, y] = mercatorXY(lng, lat);
      return {id, lng, lat, x, y};
    });

  if (anchors.length < 3) {
    throw new Error(`${road.ref} cached reference was unexpectedly sparse.`);
  }

  road.ways = [];
  road.anchors = anchors;
  road.anchorIndex = buildAnchorIndex(anchors);
  road.coveredAnchorIds = new Set(
    [...(persistedCoverageByRef.get(road.id) || [])]
      .filter(id=>Number.isInteger(id) && id>=0 && id<anchors.length)
  );
  road.totalKm = canonicalReferenceLengthKm(
    road.id,
    Number(cached.total_km || anchors.length * CANONICAL_REFERENCE_SAMPLE_M / 1000)
  );
  road.status = 'ready';
  road.source = 'cache';
}

function canonicalRoadArchiveRecord(road) {
  return {
    id:road.id,
    version:CANONICAL_CACHE_VERSION,
    totalKm:road.totalKm,
    anchors:road.anchors.map(anchor=>[anchor.lng,anchor.lat])
  };
}

async function saveCanonicalRoadReference(road) {
  if (!road || road.status!=='ready' || road.anchors.length<3) return;
  try {
    await canonicalRoadArchiveOperation('readwrite',store=>store.put(canonicalRoadArchiveRecord(road)));
  } catch (err) {
    console.warn('Motorway reference could not be retained on this device:',err);
  }
}

async function hydrateCanonicalRoadFromDevice(road) {
  const stored=await canonicalRoadArchiveOperation('readonly',store=>store.get(road.id));
  if (!stored || stored.version!==CANONICAL_CACHE_VERSION || !Array.isArray(stored.anchors)) return false;
  hydrateCanonicalRoadFromCache(road,{anchors:stored.anchors,total_km:stored.totalKm});
  road.source='device';
  return true;
}

// A motorway reference can be restored without re-running the costly coverage
// and mileage calculations. Keep the map and its reference count truthful as
// each saved/cache road arrives; otherwise a long restore looks like a broken
// empty map until the very last motorway has completed.
function refreshCanonicalRestoreDisplay() {
  renderCanonicalMotorwayDashboard();
  if (map) renderMap({deferCalculations:true});
}

async function loadCanonicalRoad(ref, force=false) {
  const road=canonicalRoadState(ref);
  if (!force && ['loading','ready'].includes(road.status)) return road;

  road.status='loading';
  road.error=null;
  if (!canonicalLoadQueueRunning) renderCanonicalMotorwayDashboard();

  try {
    /*
     * POC 18 first loads a prebuilt canonical motorway cache from GitHub Pages.
     * Live Overpass construction remains only as a fallback for roads absent
     * from the cache or while the cache is being expanded.
     */
    try {
      if (!force && await hydrateCanonicalRoadFromDevice(road)) {
        if (!persistedCoverageByRef.has(road.id)) canonicalCoverageDirty=true;
        refreshCanonicalRestoreDisplay();
        return road;
      }
    } catch (deviceErr) {
      // The reference can be rebuilt from the bundled cache if device storage
      // is unavailable or holds an older version.
      console.warn('Saved motorway reference could not be read:',deviceErr);
    }

    try {
      const cache = await loadCanonicalCache();
      const cached = cache.roads?.[road.ref];

      if (cached) {
        hydrateCanonicalRoadFromCache(road, cached);
        void saveCanonicalRoadReference(road);
        if (!persistedCoverageByRef.has(road.id)) canonicalCoverageDirty=true;
        refreshCanonicalRestoreDisplay();
        return road;
      }
    } catch (cacheErr) {
      // Fall through to live construction; surface the live result instead.
      console.warn('Canonical cache unavailable, falling back to Overpass:', cacheErr);
    }

    // Opening saved progress must be deterministic and quick. A historical
    // reference that is absent from both device storage and the bundled cache
    // must not leave the entire restore queue waiting on a live Overpass call.
    // It is shown as unavailable and can be retried deliberately instead.
    if (!force && onboardingMode==='saved') {
      throw new Error(`${road.ref} is not available in the saved motorway reference cache.`);
    }

    const escapedRef=road.ref.replace(/"/g,'\\"');
    const query =
      `[out:json][timeout:90];` +
      `area["ISO3166-1"="GB"][admin_level=2]->.gb;` +
      `way(area.gb)["highway"="motorway"]["ref"="${escapedRef}"];` +
      `out tags geom;`;

    const endpoints=[
      'https://overpass-api.de/api/interpreter',
      'https://overpass.kumi.systems/api/interpreter'
    ];

    let data=null;
    let lastError=null;

    for (const endpoint of endpoints) {
      const url=`${endpoint}?data=${encodeURIComponent(query)}`;

      for (let attempt=0; attempt<2; attempt++) {
        try {
          const response=await fetch(url);

          if (!response.ok) {
            lastError=`Reference service returned HTTP ${response.status}.`;
            if ([429,502,503,504].includes(response.status)) {
              await new Promise(resolve=>setTimeout(resolve,3000 + attempt*2000));
              continue;
            }
            break;
          }

          data=await response.json();
          break;
        } catch (err) {
          lastError=err.message || String(err);
          await new Promise(resolve=>setTimeout(resolve,2500));
        }
      }

      if (data) break;
    }

    if (!data) throw new Error(lastError || 'Motorway reference could not be loaded.');

    const ways=(data.elements || [])
      .map(element=>({
        id:element.id,
        tags:element.tags || {},
        coords:overpassWayCoordinates(element)
      }))
      .filter(way=>
        way.coords.length>=2 &&
        way.coords.some(point =>
          isMotorwayCoordinateForRegion(road.region, point[0], point[1])
        )
      );

    if (!ways.length) {
      throw new Error(`No exact-ref OpenStreetMap motorway geometry found for ${road.ref}.`);
    }

    const anchors=buildCanonicalAnchors(ways);

    if (anchors.length<3) {
      throw new Error(`${road.ref} reference was unexpectedly sparse.`);
    }

    road.ways=ways;
    road.anchors=anchors;
    road.anchorIndex=buildAnchorIndex(anchors);
    road.coveredAnchorIds=new Set(
      [...(persistedCoverageByRef.get(road.id) || [])]
        .filter(id=>Number.isInteger(id) && id>=0 && id<anchors.length)
    );
    road.totalKm=canonicalReferenceLengthKm(
      road.id,
      anchors.length * CANONICAL_REFERENCE_SAMPLE_M / 1000
    );
    road.status='ready';
    road.source='live';
    void saveCanonicalRoadReference(road);
    if (!persistedCoverageByRef.has(road.id)) canonicalCoverageDirty=true;

    refreshCanonicalRestoreDisplay();
    return road;
  } catch (err) {
    road.status='error';
    road.error=err.message || String(err);
    renderCanonicalMotorwayDashboard();
    return road;
  }
}

async function hydrateSavedCanonicalRoads(refs) {
  const roads=refs
    .map(normaliseMotorwayRef)
    .filter(Boolean)
    .map(canonicalRoadState);

  // Saved progress is tied to the fixed, versioned reference bundled with the
  // app. Hydrate that complete set in one operation instead of reading a
  // separate IndexedDB record for every motorway: one damaged/slow record
  // must never hold the saved map at 21 of 22.
  let cache;
  try {
    cache=await loadCanonicalCache();
  } catch (err) {
    for (const road of roads) {
      if (road.status==='ready') continue;
      road.status='error';
      road.error='The saved motorway reference file could not be loaded.';
    }
    refreshCanonicalRestoreDisplay();
    return;
  }

  for (const road of roads) {
    if (road.status==='ready') continue;
    const cached=cache.roads?.[road.ref];
    if (!cached) {
      road.status='error';
      road.error=`${road.ref} is not available in the saved motorway reference cache.`;
      continue;
    }
    try {
      hydrateCanonicalRoadFromCache(road,cached);
      void saveCanonicalRoadReference(road);
    } catch (err) {
      road.status='error';
      road.error=err.message || String(err);
    }
  }
  refreshCanonicalRestoreDisplay();
}

async function ensureCanonicalRoadsForDiscoveredRefs(refs) {
  for (const ref of refs.map(normaliseMotorwayRef).filter(Boolean)) {
    canonicalRequestedRefs.add(ref);
  }
  if (canonicalLoadQueueRunning) return;
  // Do not start an empty queue. Its completion renders the map, which used
  // to immediately call this function again and caused a full-render loop
  // once all motorway references were already ready.
  if (![...canonicalRequestedRefs].some(ref=>canonicalRoadState(ref).status==='idle')) return;

  canonicalLoadQueueRunning=true;
  try {
    if (onboardingMode==='saved') {
      await hydrateSavedCanonicalRoads([...canonicalRequestedRefs]);
      return;
    }
    while (true) {
      const ref=[...canonicalRequestedRefs]
        .find(candidate=>canonicalRoadState(candidate).status==='idle');
      if (!ref) break;
      const road=await loadCanonicalRoad(ref);
      // The pause protects live Overpass requests. Device and bundled-cache
      // references are local/one-request restores, so queue them without a
      // visible per-motorway delay.
      if (road?.source==='live') await new Promise(resolve=>setTimeout(resolve,400));
    }
  } finally {
    canonicalLoadQueueRunning=false;
    if (map) renderMap();
  }
}

function motorwayFeatureId(feature) {
  const ref=normaliseMotorwayRef(feature?.properties?.road_ref);
  if (!ref) return null;
  const firstSegment=geometrySegments({type:'FeatureCollection',features:[feature]})[0];
  const point=firstSegment?.[0];
  const region=point && isNorthernIrelandCoordinate(Number(point[0]),Number(point[1])) ? 'NI' : 'GB';
  return region==='NI' ? `NI:${ref}` : ref;
}

function calculateCanonicalCoverageForRoad(road, drawable) {
  const covered=new Set(persistedCoverageByRef.get(road?.id) || []);
  if (!road || road.status!=='ready') return covered;
  if (onboardingMode==='manual' && manualMotorwayRefs.has(road.id)) {
    const saved=manualCoverageByRef.get(road.id);
    return saved
      ? new Set(saved)
      : covered.size
        ? covered
        : new Set(road.anchors.map(anchor=>anchor.id));
  }
  const removedEvidenceByAnchor=new Map();
  for (const journey of drawable) {
    const journeyId=journeyIdentity(journey);
    for (const feature of journey.motorwayGeoJson?.features || []) {
      if (motorwayFeatureId(feature)!==road.id) continue;
      for (const [a,b] of geometrySegments({type:'FeatureCollection',features:[feature]})) {
        const lengthM=haversineMetres(a,b);
        if (!Number.isFinite(lengthM) || lengthM<=0) continue;
        const samples=Math.max(1,Math.ceil(lengthM/CANONICAL_MATCH_SAMPLE_M));
        const removed=segmentEvidenceIsRemoved(segmentKey(a,b),journeyId);
        for (let i=0;i<=samples;i++) {
          const id=nearestCanonicalAnchor(road,interpolateLngLat(a,b,i/samples));
          if (removed) {
            if (id!==null && journeyId) {
              if (!removedEvidenceByAnchor.has(id)) removedEvidenceByAnchor.set(id,new Set());
              removedEvidenceByAnchor.get(id).add(journeyId);
            }
            continue;
          }
          if (id!==null) {
            covered.add(id);
          }
        }
      }
    }
  }
  // Corrections take precedence over all imported evidence. Restoring a
  // motorway section is an explicit map-editor action, not a side effect of
  // loading another Timeline export.
  const allRemovalEvidence=new Map(canonicalRemovalEvidenceByRef.get(road.id) || []);
  for (const [anchorId,journeyIds] of removedEvidenceByAnchor) {
    if (!allRemovalEvidence.has(anchorId)) allRemovalEvidence.set(anchorId,new Set());
    for (const journeyId of journeyIds) allRemovalEvidence.get(anchorId).add(journeyId);
  }
  for (const anchorId of allRemovalEvidence.keys()) {
    covered.delete(anchorId);
  }
  persistedCoverageByRef.set(road.id,new Set(covered));
  scheduleLocalProgressSave();
  return covered;
}

function clearReferenceMapLayers() {
  canonicalReferenceLayer?.clearLayers();
  canonicalCoverageLayer?.clearLayers();
  canonicalUncoveredLayer?.clearLayers();
  canonicalARoadCoverageLayer?.clearLayers();
  canonicalARoadUncoveredLayer?.clearLayers();
}

function renderCanonicalMapLayers() {
  if (!canonicalCoverageLayer || !canonicalUncoveredLayer) return;
  canonicalReferenceLayer?.clearLayers();
  canonicalCoverageLayer.clearLayers();
  canonicalUncoveredLayer.clearLayers();
  const bounds=visibleMapBounds();

  for (const road of canonicalRoads.values()) {
    if (road.status!=='ready') continue;
    if (refinementRoadRef && road.id!==refinementRoadRef) continue;

    if (road.ways.length) {
      for (const way of road.ways) {
        for (let i=1; i<way.coords.length; i++) {
          const a=way.coords[i-1], b=way.coords[i];
          if (!segmentIntersectsMapBounds(a,b,bounds)) continue;
          const lengthM=haversineMetres(a,b);
          if (!Number.isFinite(lengthM) || lengthM<=0) continue;
          const samples=Math.max(1,Math.ceil(lengthM/CANONICAL_REFERENCE_SAMPLE_M));
          for (let s=0; s<samples; s++) {
            const start=interpolateLngLat(a,b,s/samples);
            const end=interpolateLngLat(a,b,(s+1)/samples);
            const anchorId=nearestCanonicalAnchor(road,interpolateLngLat(start,end,.5));
            const covered=anchorId!==null && road.coveredAnchorIds.has(anchorId);
            L.polyline(
              [[start[1],start[0]],[end[1],end[0]]],
              {
                weight:4,
                opacity:.9,
                color:covered ? '#005eb8' : '#d93a3a',
                pane:covered ? 'motorwayConfirmedPane' : 'motorwayUnconfirmedPane',
                interactive:false
              }
            ).addTo(covered ? canonicalCoverageLayer : canonicalUncoveredLayer);
          }
        }
      }
    } else {
      // Cached references store sampled anchors rather than source OSM ways.
      // Draw contiguous runs as multi-polylines instead of creating a Leaflet
      // marker for every 100 m anchor. A full saved map otherwise creates many
      // thousands of SVG elements before the user can interact with it.
      const runs={covered:[],uncovered:[]};
      let previous=null;
      let current=null;
      let currentKind=null;
      for (const anchor of road.anchors) {
        const anchorPoint=[anchor.lng,anchor.lat];
        const covered=road.coveredAnchorIds.has(anchor.id);
        const kind=covered ? 'covered' : 'uncovered';
        const isContinuous=previous &&
          haversineMetres([previous.lng,previous.lat],[anchor.lng,anchor.lat])<=250;
        const isVisible=!previous || segmentIntersectsMapBounds(
          [previous.lng,previous.lat],anchorPoint,bounds
        );
        if (!isVisible) {
          if (current?.length>1) current=null;
          previous=anchor;
          continue;
        }
        if (!current || currentKind!==kind || !isContinuous) {
          current=[];
          runs[kind].push(current);
          currentKind=kind;
        }
        // A cache can contain separate motorway branches. Only carry the
        // preceding anchor into a new visible run when it is genuinely the
        // adjacent 100 m sample; otherwise Leaflet draws a false diagonal
        // between distant sections.
        if (!current.length && previous && isContinuous) {
          current.push([previous.lat,previous.lng]);
        }
        current.push([anchor.lat,anchor.lng]);
        previous=anchor;
      }
      for (const [kind,paths] of Object.entries(runs)) {
        const covered=kind==='covered';
        L.polyline(paths.filter(path=>path.length>1),{
          weight:4,
          opacity:.9,
          color:covered ? '#005eb8' : '#d93a3a',
          pane:covered ? 'motorwayConfirmedPane' : 'motorwayUnconfirmedPane',
          interactive:false
        }).addTo(covered ? canonicalCoverageLayer : canonicalUncoveredLayer);
      }
    }
  }
}

function renderNetworkCompletion(roads) {
  const completedByRegion={GB:0,NI:0};
  for (const road of roads) {
    if (road.status!=='ready' || !road.anchors.length) continue;
    const fraction=Math.min(1,road.coveredAnchorIds.size/road.anchors.length);
    completedByRegion[road.region]+=road.totalKm*fraction;
  }

  completedByRegion.GB=Math.min(GB_MOTORWAY_NETWORK_KM,completedByRegion.GB);
  completedByRegion.NI=Math.min(NI_MOTORWAY_NETWORK_KM,completedByRegion.NI);
  const completedKm=completedByRegion.GB+completedByRegion.NI;
  const gbPercent=completedByRegion.GB/GB_MOTORWAY_NETWORK_KM*100;
  const niPercent=completedByRegion.NI/NI_MOTORWAY_NETWORK_KM*100;
  const percent=Math.min(100,completedKm/UK_MOTORWAY_NETWORK_KM*100);

  const regionalDistance=(km,totalKm,totalMiles)=>
    `${displayDistance(km)} of approximately ${distanceUnit==='km' ? `${totalKm.toFixed(0)} km` : `${totalMiles.toLocaleString()} miles`}`;

  gbProgressPercent.textContent=`${gbPercent.toFixed(1)}%`;
  niProgressPercent.textContent=`${niPercent.toFixed(1)}%`;
  gbProgressDistance.textContent=regionalDistance(completedByRegion.GB,GB_MOTORWAY_NETWORK_KM,GB_MOTORWAY_NETWORK_MILES);
  niProgressDistance.textContent=regionalDistance(completedByRegion.NI,NI_MOTORWAY_NETWORK_KM,NI_MOTORWAY_NETWORK_MILES);

  const totalLabel=distanceUnit==='km'
    ? `approximately ${UK_MOTORWAY_NETWORK_KM.toFixed(0)} km`
    : `approximately ${UK_MOTORWAY_NETWORK_MILES.toLocaleString()} miles`;
  networkProgressPercent.textContent=`${percent.toFixed(1)}%`;
  networkProgressDistance.textContent=`${displayDistance(completedKm)} of ${totalLabel}`;
  networkProgressFill.style.width=`${percent}%`;
  networkProgressBar.setAttribute('aria-valuenow',percent.toFixed(1));
  networkProgressBar.setAttribute(
    'aria-valuetext',
    `${percent.toFixed(1)} percent, ${displayDistance(completedKm)} completed out of ${totalLabel}`
  );
}

function renderCanonicalMotorwayDashboard(drawable=null) {
  const sessionRefs=drawable
    ? drawable.flatMap(j=>(j.motorwayGeoJson?.features || []).map(motorwayFeatureId).filter(Boolean))
    : [];
  const discoveredRefs=onboardingMode==='manual'
    ? [...manualMotorwayRefs].sort(motorwayRefSort)
    : onboardingMode==='saved'
      ? [...new Set([...persistedCoverageByRef.keys(),...persistedManualRefs])].sort(motorwayRefSort)
      : drawable
      ? [...new Set([...persistedCoverageByRef.keys(),...sessionRefs])].sort(motorwayRefSort)
      : [...canonicalRoads.keys()];

  if (!discoveredRefs.length && !canonicalRoads.size) {
    canonicalMotorwayCard.classList.add('hidden');
    return;
  }
  canonicalMotorwayCard.classList.toggle('hidden',Boolean(refinementRoadRef));

  const shouldRecalculateCoverage=canonicalCoverageDirty &&
    (drawable || onboardingMode==='manual' || onboardingMode==='saved');
  if (shouldRecalculateCoverage) {
    for (const ref of discoveredRefs) {
      const road=canonicalRoadState(ref);
      if (road.status==='ready') road.coveredAnchorIds=calculateCanonicalCoverageForRoad(road,drawable || []);
    }
    canonicalCoverageDirty=false;
  }
  evaluateAchievements();
  if (drawable || onboardingMode==='manual' || onboardingMode==='saved') {
    ensureCanonicalRoadsForDiscoveredRefs(discoveredRefs);
  }

  const roads=discoveredRefs.map(ref=>canonicalRoadState(ref)).sort((a,b)=>{
    const ap=a.status==='ready' && a.anchors.length ? a.coveredAnchorIds.size/a.anchors.length : -1;
    const bp=b.status==='ready' && b.anchors.length ? b.coveredAnchorIds.size/b.anchors.length : -1;
    return bp-ap || a.region.localeCompare(b.region) || a.ref.localeCompare(b.ref,undefined,{numeric:true});
  });

  renderNetworkCompletion(roads);
  canonicalMotorwayList.innerHTML='';
  const readyCount=roads.filter(r=>r.status==='ready').length;
  canonicalRoadsReady.textContent=readyCount.toLocaleString();
  let loadingCount=0,errorCount=0;

  for (const road of roads) {
    const row=document.createElement('div'); row.className='canonical-road-row';
    const top=document.createElement('div'); top.className='canonical-road-top';
    const ref=document.createElement('div'); ref.className='canonical-road-ref motorway-ref';
    ref.textContent=road.region==='NI' ? `${road.ref} · NI` : road.ref;
    const progress=document.createElement('div'); progress.className='canonical-road-progress';
    const fill=document.createElement('div'); fill.className='canonical-road-fill';
    const pct=document.createElement('div'); pct.className='canonical-road-pct';
    const meta=document.createElement('div'); meta.className='canonical-road-meta';

    if (road.status==='ready') {
      const covered=road.coveredAnchorIds.size,total=road.anchors.length;
      const percent=total ? Math.min(100,covered/total*100) : 0;
      const drivenKm=road.totalKm*percent/100;
      fill.style.width=`${percent}%`; pct.textContent=`${percent.toFixed(1)}%`;
      meta.textContent=`${displayDistance(drivenKm)} estimated unique · ${covered.toLocaleString()} / ${total.toLocaleString()} canonical sections · ${displayDistance(road.totalKm)} reference`;
    } else if (road.status==='loading') {
      loadingCount++; fill.style.width='0%'; pct.textContent='…'; meta.classList.add('canonical-road-loading'); meta.textContent='Loading canonical OpenStreetMap reference…';
    } else if (road.status==='error') {
      errorCount++; fill.style.width='0%'; pct.textContent='—'; meta.classList.add('canonical-road-error'); meta.textContent=road.error || 'Reference unavailable.';
    } else {
      fill.style.width='0%'; pct.textContent='…'; meta.textContent='Waiting to load reference…';
    }
    progress.append(fill); top.append(ref,progress,pct); row.append(top,meta);

    if (onboardingMode==='manual' && road.status==='ready') {
      const refineButton=document.createElement('button');
      refineButton.type='button';
      refineButton.className='secondary refine-road-button';
      refineButton.textContent=`Refine ${road.ref}${road.region==='NI'?' (Northern Ireland)':''} sections`;
      refineButton.addEventListener('click',()=>startMotorwayRefinement(road.id));
      row.append(refineButton);
    }

    canonicalMotorwayList.append(row);
  }

  if (loadingCount) {
    canonicalMotorwayStatus.className='muted canonical-status';
    canonicalMotorwayStatus.textContent=`Loading ${loadingCount} motorway reference${loadingCount===1?'':'s'}…`;
  } else if (errorCount) {
    canonicalMotorwayStatus.className='muted canonical-status warn';
    canonicalMotorwayStatus.textContent=`${readyCount} reference${readyCount===1?'':'s'} ready · ${errorCount} need retry/review.`;
    canonicalRetry.classList.remove('hidden');
  } else {
    canonicalMotorwayStatus.className='muted canonical-status ok';
    canonicalMotorwayStatus.textContent=`${readyCount} canonical motorway reference${readyCount===1?'':'s'} ready.`;
    canonicalRetry.classList.add('hidden');
  }
  renderCanonicalMapLayers();
}

function buildRefinementChunks(road) {
  const groups=new Map();
  const cellM=8000;
  for (const anchor of road?.anchors || []) {
    const key=gridKeyXY(anchor.x,anchor.y,cellM);
    if (!groups.has(key)) groups.set(key,[]);
    groups.get(key).push(anchor.id);
  }
  return [...groups.values()]
    .map(ids=>{
      const anchors=ids.map(id=>road.anchors[id]);
      return {
        ids,
        lat:anchors.reduce((sum,a)=>sum+a.lat,0)/anchors.length,
        lng:anchors.reduce((sum,a)=>sum+a.lng,0)/anchors.length
      };
    })
    .sort((a,b)=>b.lat-a.lat || a.lng-b.lng);
}

function refinementCoverageSet(road) {
  const saved=manualCoverageByRef.get(road.id);
  return saved
    ? new Set(saved)
    : new Set(road.anchors.map(anchor=>anchor.id));
}

function saveRefinementUndo(coverage) {
  refinementUndoStack.push(new Set(coverage));
  if (refinementUndoStack.length>30) refinementUndoStack.shift();
  refinementUndo.disabled=false;
}

function applyRefinementIds(ids, mode=refinementEditMode) {
  const road=canonicalRoads.get(refinementRoadRef);
  if (!road || road.status!=='ready' || !ids.length) return;
  const coverage=refinementCoverageSet(road);
  saveRefinementUndo(coverage);
  for (const id of ids) {
    if (mode==='erase') coverage.delete(id);
    else coverage.add(id);
  }
  manualCoverageByRef.set(road.id,coverage);
  persistedCoverageByRef.set(road.id,new Set(coverage));
  road.coveredAnchorIds=new Set(coverage);
  scheduleLocalProgressSave();
  renderCanonicalMotorwayDashboard([]);
  renderMap();
  updateRefinementChunkStatus();
}

function nearestRefinementAnchor(road, lat, lng) {
  const [x,y]=mercatorXY(lng,lat);
  let best=null,bestDistance=12000;
  for (const anchor of road.anchors) {
    const distance=Math.hypot(x-anchor.x,y-anchor.y);
    if (distance<bestDistance) {
      bestDistance=distance;
      best=anchor;
    }
  }
  return best;
}

function handleRefinementMapClick(event) {
  if (!refinementRoadRef) return;
  if (!refinementEditMode) {
    mapStatus.className='muted map-status warn';
    mapStatus.textContent='Choose “Add driven section” or “Remove driven section” before tapping the map.';
    refinementMark.focus();
    return;
  }
  const road=canonicalRoads.get(refinementRoadRef);
  if (!road || road.status!=='ready') return;
  const nearest=nearestRefinementAnchor(road,event.latlng.lat,event.latlng.lng);
  if (!nearest) {
    mapStatus.className='muted map-status warn';
    mapStatus.textContent=`Tap closer to the ${road.ref} line.`;
    return;
  }
  const brushRadiusM=5000;
  const ids=road.anchors
    .filter(anchor=>Math.hypot(anchor.x-nearest.x,anchor.y-nearest.y)<=brushRadiusM)
    .map(anchor=>anchor.id);
  applyRefinementIds(ids);
}

function setRefinementMode(mode) {
  refinementEditMode=['mark','erase'].includes(mode) ? mode : null;
  refinementMark.setAttribute('aria-pressed',String(refinementEditMode==='mark'));
  refinementErase.setAttribute('aria-pressed',String(refinementEditMode==='erase'));

  if (!refinementEditMode) {
    mapStatus.className='muted map-status warn';
    mapStatus.textContent='Choose what tapping the map should do: add a blue driven section or remove one to red.';
    return;
  }

  mapStatus.className='muted map-status ok';
  mapStatus.textContent=refinementEditMode==='mark'
    ? 'Add mode active. Tap the motorway to turn an approximately 5 km section blue.'
    : 'Remove mode active. Tap the motorway to turn an approximately 5 km section red.';
}

function updateRefinementChunkStatus(focus=false) {
  const road=canonicalRoads.get(refinementRoadRef);
  const chunk=refinementChunks[refinementChunkIndex];
  if (!road || !chunk) {
    refinementChunkStatus.textContent='No areas available';
    return;
  }
  const coverage=refinementCoverageSet(road);
  const covered=chunk.ids.filter(id=>coverage.has(id)).length;
  const state=covered===chunk.ids.length ? 'driven' : covered ? 'partly driven' : 'not driven';
  refinementChunkStatus.textContent=
    `Area ${refinementChunkIndex+1} of ${refinementChunks.length} · ${state}`;

  if (focus && map) {
    const points=chunk.ids.map(id=>road.anchors[id]).map(a=>[a.lat,a.lng]);
    if (points.length) map.fitBounds(L.latLngBounds(points),{padding:[70,70],maxZoom:11});
  }
}

function moveRefinementChunk(direction) {
  if (!refinementChunks.length) return;
  refinementChunkIndex=
    (refinementChunkIndex+direction+refinementChunks.length)%refinementChunks.length;
  updateRefinementChunkStatus(true);
}

function startMotorwayRefinement(id) {
  const road=canonicalRoads.get(id);
  if (!road || road.status!=='ready') return;
  refinementRoadRef=id;
  refinementUndoStack=[];
  refinementChunks=buildRefinementChunks(road);
  refinementChunkIndex=0;
  const label=road.region==='NI' ? `${road.ref} (Northern Ireland)` : road.ref;
  refinementTitle.textContent=`Refine ${label} sections`;
  refinementPanel.classList.remove('hidden');
  mapCard.classList.add('refinement-active');
  manualMotorwayCard.classList.add('hidden');
  canonicalMotorwayCard.classList.add('hidden');
  mapTitle.textContent=`Refine ${label}`;
  mapIntro.textContent='Tap the motorway to mark or erase sections, or open the keyboard controls to work through geographic areas.';
  refinementUndo.disabled=true;
  setRefinementMode(null);
  renderMap();
  const points=road.anchors.map(anchor=>[anchor.lat,anchor.lng]);
  if (points.length) map.fitBounds(L.latLngBounds(points),{padding:[25,25],maxZoom:9});
  updateRefinementChunkStatus();
  refinementMark.focus();
}

function finishMotorwayRefinement() {
  refinementRoadRef=null;
  refinementPanel.classList.add('hidden');
  mapCorrectionPanel.classList.add('hidden');
  mapCard.classList.remove('refinement-active');
  manualMotorwayCard.classList.remove('hidden');
  canonicalMotorwayCard.classList.remove('hidden');
  mapTitle.textContent='3. Preview';
  mapIntro.textContent='Selected motorways are shown in blue where confirmed and red where unconfirmed. Use Refine sections to edit an individual motorway.';
  renderMap();
  fitSelected();
}

document.getElementById('finishRefinement').addEventListener('click',finishMotorwayRefinement);
refinementMark.addEventListener('click',()=>setRefinementMode('mark'));
refinementErase.addEventListener('click',()=>setRefinementMode('erase'));
refinementUndo.addEventListener('click',()=>{
  const road=canonicalRoads.get(refinementRoadRef);
  const previous=refinementUndoStack.pop();
  if (!road || !previous) return;
  manualCoverageByRef.set(road.id,new Set(previous));
  persistedCoverageByRef.set(road.id,new Set(previous));
  road.coveredAnchorIds=new Set(previous);
  scheduleLocalProgressSave();
  refinementUndo.disabled=!refinementUndoStack.length;
  renderCanonicalMotorwayDashboard([]);
  renderMap();
  updateRefinementChunkStatus();
});
document.getElementById('refinementWhole').addEventListener('click',()=>{
  const road=canonicalRoads.get(refinementRoadRef);
  if (road) applyRefinementIds(road.anchors.map(anchor=>anchor.id),'mark');
});
document.getElementById('refinementClear').addEventListener('click',()=>{
  const road=canonicalRoads.get(refinementRoadRef);
  if (!road) return;
  const coverage=refinementCoverageSet(road);
  saveRefinementUndo(coverage);
  const empty=new Set();
  manualCoverageByRef.set(road.id,empty);
  persistedCoverageByRef.set(road.id,empty);
  road.coveredAnchorIds=empty;
  scheduleLocalProgressSave();
  renderCanonicalMotorwayDashboard([]);
  renderMap();
  updateRefinementChunkStatus();
});
document.getElementById('previousRefinementChunk').addEventListener('click',()=>moveRefinementChunk(-1));
document.getElementById('nextRefinementChunk').addEventListener('click',()=>moveRefinementChunk(1));
document.getElementById('markRefinementChunk').addEventListener('click',()=>{
  const chunk=refinementChunks[refinementChunkIndex];
  if (chunk) applyRefinementIds(chunk.ids,'mark');
});
document.getElementById('eraseRefinementChunk').addEventListener('click',()=>{
  const chunk=refinementChunks[refinementChunkIndex];
  if (chunk) applyRefinementIds(chunk.ids,'erase');
});

function retryCanonicalRoads() {
  const errored=[...canonicalRoads.values()].filter(r=>r.status==='error').map(r=>r.id);
  for (const ref of errored) { const road=canonicalRoadState(ref); road.status='idle'; road.error=null; }
  canonicalRetry.classList.add('hidden');
  ensureCanonicalRoadsForDiscoveredRefs(errored);
}

function motorwayStats(drawable) {
  const contributionByJourney=new Map(persistedMotorwayContributionsByJourney);
  for (const journey of drawable) {
    if (!journey.motorwayGeoJson) continue;
    const journeyId=journey.importId || journeyFingerprint(journey);
    contributionByJourney.set(journeyId,motorwayContributionsForJourney(journey));
  }

  const roads=new Map();
  for (const [journeyId,contributions] of contributionByJourney) {
    for (const [id,rawDistanceM] of Object.entries(contributions || {})) {
      const distanceM=Number(rawDistanceM);
      if (!id || !Number.isFinite(distanceM) || distanceM<=0) continue;
      const parsed=parseMotorwayId(id);
      if (!roads.has(id)) {
        roads.set(id,{
          id,
          ref:parsed.ref,
          region:parsed.region,
          matchedDistanceM:0,
          journeyIds:new Set()
        });
      }
      const road=roads.get(id);
      road.matchedDistanceM+=distanceM;
      road.journeyIds.add(journeyId);
    }
  }

  return [...roads.values()]
    .map(road=>({
      id:road.id,
      ref:road.ref,
      region:road.region,
      matchedKm:road.matchedDistanceM/1000,
      journeys:road.journeyIds.size
    }))
    .sort((a,b)=>b.matchedKm-a.matchedKm || a.ref.localeCompare(b.ref,undefined,{numeric:true}));
}

function renderMotorwayDashboard(drawable) {
  const stats = motorwayStats(drawable);
  motorwayList.innerHTML = '';
  motorwaysDiscovered.textContent = stats.length.toLocaleString();
  const matchedMotorwayKm=stats.reduce((sum,road)=>sum+road.matchedKm,0);
  timelineRoadMileage.textContent=displayDistance(matchedMotorwayKm);

  if (!stats.length) {
    motorwayCard.classList.add('hidden');
    return;
  }

  motorwayCard.classList.remove('hidden');

  const maxKm = Math.max(...stats.map(r => r.matchedKm), 1);

  for (const road of stats) {
    const row = document.createElement('div');
    row.className = 'motorway-row';

    const ref = document.createElement('div');
    ref.className = 'motorway-ref';
    ref.textContent = road.region==='NI' ? `${road.ref} · NI` : road.ref;

    const bar = document.createElement('div');
    bar.className = 'motorway-bar';

    const fill = document.createElement('div');
    fill.className = 'motorway-fill';
    fill.style.width = `${Math.max(2, road.matchedKm / maxKm * 100)}%`;
    bar.append(fill);

    const value = document.createElement('div');
    value.className = 'motorway-pct';
    value.textContent = displayDistance(road.matchedKm);

    const meta = document.createElement('div');
    meta.className = 'motorway-meta';
    meta.textContent =
      `${road.journeys} matched journey${road.journeys === 1 ? '' : 's'} contributed · ${displayDistance(road.matchedKm)} matched · completion % pending canonical road sections`;

    row.append(ref, bar, value, meta);
    motorwayList.append(row);
  }
}

function aRoadFeatureId(feature) {
  const ref=String(feature?.properties?.road_ref || '').toUpperCase().replace(/\s+/g,'');
  return /^A\d+[A-Z]?$/.test(ref) ? ref : null;
}

function featureFirstLngLat(feature) {
  const geometry=feature?.geometry;
  const coordinates=geometry?.coordinates;
  if (!coordinates) return null;
  let point=coordinates;
  while (Array.isArray(point) && Array.isArray(point[0])) point=point[0];
  return Array.isArray(point) && Number.isFinite(Number(point[0])) && Number.isFinite(Number(point[1]))
    ? [Number(point[0]),Number(point[1])] : null;
}

function aRoadFeatureRegion(feature) {
  const saved=String(feature?.properties?.road_region || '').toUpperCase();
  if (saved==='GB' || saved==='NI') return saved;
  // Existing imports pre-date the region field. Northern Ireland's compact
  // bounding box is sufficient here: an A road cannot cross the Irish Sea.
  const point=featureFirstLngLat(feature);
  return point && point[0]>=-8.5 && point[0]<=-5.0 && point[1]>=53.8 && point[1]<=55.5 ? 'NI' : 'GB';
}

function aRoadFeatureKey(feature) {
  const ref=aRoadFeatureId(feature);
  return ref ? `${aRoadFeatureRegion(feature)}:${ref}` : null;
}

function parseARoadKey(value) {
  const match=String(value || '').toUpperCase().match(/^(GB|NI):(A\d+[A-Z]?)$/);
  return match ? {key:match[0],region:match[1],ref:match[2]} : null;
}

function canonicalARoadState(key) {
  const parsed=parseARoadKey(key);
  if (!parsed) return null;
  const {region,ref}=parsed;
  // Coverage anchor ids are positional. Include the reference-data version so
  // a former source cannot colour a newly rebuilt official centreline.
  const id=`A:${CANONICAL_A_ROAD_CACHE_VERSION}:${region}:${ref}`;
  if (!canonicalARoads.has(id)) {
    const saved=persistedARoadReferenceSummary.get(id);
    canonicalARoads.set(id,{
      id,key:parsed.key,region,ref,status:saved?'ready':'idle',error:null,ways:[],anchors:[],paths:[],
      anchorIndex:new Map(),coveredAnchorIds:new Set(),totalKm:saved?.totalKm || 0,
      anchorCount:saved?.anchorCount || 0,coveredAnchorCount:saved?.coveredCount || 0,
      source:saved?'summary':null,coverageCalculated:Boolean(saved)
    });
  }
  return canonicalARoads.get(id);
}

function canonicalARoadArchiveRecord(road) {
  // IndexedDB can retain positional coverage safely without making startup
  // parse a national-scale list from localStorage.
  return {
    id:road.id,
    version:CANONICAL_A_ROAD_CACHE_VERSION,
    totalKm:road.totalKm,
    anchors:road.anchors.map(anchor=>[anchor.lng,anchor.lat,anchor.component || 0]),
    coverage:[...road.coveredAnchorIds].sort((a,b)=>a-b)
  };
}

function canonicalARoadAnchors(points) {
  return (points || []).map(point=>[Number(point[0]),Number(point[1]),Number(point[2] || 0)])
    .filter(point=>Number.isFinite(point[0]) && Number.isFinite(point[1]))
    .map((point,id)=>{ const [x,y]=mercatorXY(point[0],point[1]); return {id,lng:point[0],lat:point[1],component:point[2],x,y}; });
}

function canonicalARoadPathsFromAnchors(anchors) {
  const paths=[]; let current=[]; let component=null;
  for (const anchor of anchors || []) {
    if (!current.length || anchor.component===component) current.push(anchor);
    else { if (current.length>1) paths.push(current); current=[anchor]; }
    component=anchor.component;
  }
  if (current.length>1) paths.push(current);
  return paths;
}

function canonicalARoadGeometry(paths,fallbackAnchors=[]) {
  if (Array.isArray(paths) && paths.some(path=>Array.isArray(path))) {
    const anchors=[]; const normalisedPaths=[];
    for (const rawPath of paths) {
      const path=[];
      for (const raw of rawPath || []) {
        const point=[Number(raw?.[0]),Number(raw?.[1]),Number(raw?.[2] || 0)];
        if (!Number.isFinite(point[0]) || !Number.isFinite(point[1])) continue;
        const [x,y]=mercatorXY(point[0],point[1]);
        const anchor={id:anchors.length,lng:point[0],lat:point[1],component:point[2],x,y};
        anchors.push(anchor); path.push(anchor);
      }
      if (path.length>1) normalisedPaths.push(path);
    }
    return {anchors,paths:normalisedPaths};
  }
  const anchors=canonicalARoadAnchors(fallbackAnchors);
  return {anchors,paths:canonicalARoadPathsFromAnchors(anchors)};
}

function sampledCanonicalARoadPath(path,stride) {
  if (!path || path.length<3 || stride<=1) return path || [];
  const sampled=[path[0]];
  for (let index=stride; index<path.length-1; index+=stride) sampled.push(path[index]);
  if (sampled[sampled.length-1]!==path[path.length-1]) sampled.push(path[path.length-1]);
  return sampled;
}

function canonicalARoadBounds(anchors) {
  if (!anchors?.length) return null;
  let west=Infinity,east=-Infinity,south=Infinity,north=-Infinity;
  for (const anchor of anchors) {
    west=Math.min(west,anchor.lng); east=Math.max(east,anchor.lng);
    south=Math.min(south,anchor.lat); north=Math.max(north,anchor.lat);
  }
  return {west,east,south,north};
}

async function hydrateCanonicalARoadFromDevice(road) {
  const stored=await canonicalARoadArchiveOperation('readonly',store=>store.get(road.id));
  if (!stored || stored.version!==CANONICAL_A_ROAD_CACHE_VERSION || !Array.isArray(stored.anchors)) return false;
  const geometry=canonicalARoadGeometry(stored.paths,stored.anchors);
  const anchors=geometry.anchors;
  if (anchors.length<3) return false;
  road.ways=[]; road.anchors=anchors; road.paths=geometry.paths; road.anchorIndex=buildAnchorIndex(anchors); road.bounds=canonicalARoadBounds(anchors);
  const storedCoverage=Array.isArray(stored.coverage) ? stored.coverage : persistedARoadCoverageByRef.get(road.id) || [];
  road.coveredAnchorIds=new Set([...storedCoverage]
    .filter(id=>Number.isInteger(id) && id>=0 && id<anchors.length));
  road.totalKm=Number(stored.totalKm || anchors.length*CANONICAL_REFERENCE_SAMPLE_M/1000);
  road.anchorCount=anchors.length;
  road.status='ready'; road.source='device';
  // Legacy archive entries have geometry only. Rebuild once, then persist the
  // result in IndexedDB so subsequent map opens are immediate and green.
  road.coverageCalculated=Array.isArray(stored.coverage);
  canonicalARoadCoverageDirty=true;
  return true;
}

async function saveCanonicalARoadReference(road) {
  if (!road || road.status!=='ready' || road.anchors.length<3) return;
  try { await canonicalARoadArchiveOperation('readwrite',store=>store.put(canonicalARoadArchiveRecord(road))); }
  catch (err) { console.warn('A-road reference could not be retained on this device:',err); }
}

async function loadCanonicalARoadCacheIndex() {
  if (canonicalARoadCacheIndexPromise) return canonicalARoadCacheIndexPromise;
  canonicalARoadCacheIndexPromise=(async()=>{
    const useEntries=(entries,{cached=false}={})=>{
      canonicalARoadCacheEntries=new Map(entries);
      canonicalARoadCacheIndexAvailable=true;
      canonicalARoadCacheIndexError=cached
        ? 'Using the last verified A-road reference index while a network refresh is unavailable.'
        : null;
    };
    const parseIndex=index=>Object.entries(index?.roads && typeof index.roads==='object' ? index.roads : {})
      .filter(([key,value])=>parseARoadKey(key) && value && typeof value.file==='string');
    let cachedEntries=[];
    try {
      cachedEntries=parseIndex(JSON.parse(localStorage.getItem(CANONICAL_A_ROAD_INDEX_STORAGE_KEY)||'{}'));
    } catch (_) {
      localStorage.removeItem(CANONICAL_A_ROAD_INDEX_STORAGE_KEY);
    }
    const controller=new AbortController();
    const timeout=setTimeout(()=>controller.abort(),15000);
    try {
      const response=await fetch(CANONICAL_A_ROAD_CACHE_INDEX_URL,{cache:'no-cache',signal:controller.signal});
      if (!response.ok) throw new Error(`A-road cache index returned HTTP ${response.status}.`);
      const index=await response.json();
      if (index?.version!==CANONICAL_A_ROAD_CACHE_VERSION) throw new Error('A-road cache index has an incompatible version.');
      const entries=parseIndex(index);
      if (!entries.length) throw new Error('A-road cache index contains no usable references.');
      localStorage.setItem(CANONICAL_A_ROAD_INDEX_STORAGE_KEY,JSON.stringify(index));
      useEntries(entries);
    } catch (err) {
      if (cachedEntries.length) {
        useEntries(cachedEntries,{cached:true});
      } else {
        canonicalARoadCacheEntries.clear();
        canonicalARoadCacheIndexAvailable=false;
        canonicalARoadCacheIndexError=controller.signal.aborted
          ? 'A-road cache index request timed out after 15 seconds.'
          : (err?.message || String(err));
        canonicalARoadCacheIndexPromise=null;
        console.info('A-road cache index is not available:',err);
      }
    } finally {
      clearTimeout(timeout);
    }
    return canonicalARoadCacheEntries;
  })();
  return canonicalARoadCacheIndexPromise;
}
async function fetchCanonicalARoadWays(road) {
  const entry=canonicalARoadCacheEntries.get(road.key);
  if (!entry) throw new Error(`${road.ref} ${road.region==='NI'?'Northern Ireland ':''}reference is being prepared.`);
  const controller=new AbortController();
  const timeout=setTimeout(()=>controller.abort(),CANONICAL_A_ROAD_REQUEST_TIMEOUT_MS);
  try {
    const response=await fetch(CANONICAL_A_ROAD_CACHE_URL(entry.file),{
      cache:'no-store',signal:controller.signal
    });
    if (response.status===404) throw new Error(`${road.ref} reference is being prepared.`);
    if (!response.ok) throw new Error(`A-road reference cache returned HTTP ${response.status}.`);
    const cached=await response.json();
    if (!cached || cached.version!==CANONICAL_A_ROAD_CACHE_VERSION ||
      (!Array.isArray(cached.paths) && !Array.isArray(cached.anchors))) {
      throw new Error(`${road.ref} reference cache format is invalid.`);
    }
    return cached;
  } catch (err) {
    if (controller.signal.aborted) {
      throw new Error(`${road.ref} reference request timed out; the remaining roads will continue loading.`);
    }
    throw err;
  } finally {
    clearTimeout(timeout);
  }
}

async function loadCanonicalARoad(key,force=false,{deferRender=false}={}) {
  const road=canonicalARoadState(key);
  if (!road || (!force && ['loading','ready'].includes(road.status))) return road;
  road.status='loading'; road.error=null;
  if (!deferRender) renderCanonicalARoadDashboard();
  try {
    if (!force && await hydrateCanonicalARoadFromDevice(road)) return road;
    const cached=await fetchCanonicalARoadWays(road);
    const geometry=canonicalARoadGeometry(cached.paths,cached.anchors);
    const anchors=geometry.anchors;
    if (anchors.length<3) throw new Error(`${road.ref} reference was unexpectedly sparse.`);
    road.ways=[]; road.anchors=anchors; road.paths=geometry.paths; road.anchorIndex=buildAnchorIndex(anchors); road.bounds=canonicalARoadBounds(anchors);
    road.coveredAnchorIds=new Set([...(persistedARoadCoverageByRef.get(road.id) || [])]
      .filter(id=>Number.isInteger(id) && id>=0 && id<anchors.length));
    road.totalKm=Number(cached.total_km) || anchors.length*CANONICAL_REFERENCE_SAMPLE_M/1000;
    road.anchorCount=anchors.length;
    road.status='ready'; road.source='cache';
    road.coverageCalculated=false;
    void saveCanonicalARoadReference(road);
    canonicalARoadCoverageDirty=true;
  } catch (err) {
    road.status='error'; road.error=err.message || String(err);
  }
  if (!deferRender) {
    renderCanonicalARoadDashboard();
    renderCanonicalARoadMapLayers();
  }
  return road;
}

function calculateCanonicalARoadCoverage(road,drawable) {
  if (!road || road.status!=='ready') return new Set();
  const covered=manualARoadRefs.has(road.key) ? new Set(road.anchors.map(anchor=>anchor.id)) : new Set(persistedARoadCoverageByRef.get(road.id) || []);
  for (const journey of drawable) {
    for (const feature of journey.aRoadGeoJson?.features || []) {
      if (aRoadFeatureKey(feature)!==road.key) continue;
      for (const [a,b] of geometrySegments({type:'FeatureCollection',features:[feature]})) {
        if (segmentEvidenceIsRemoved(segmentKey(a,b))) continue;
        const lengthM=haversineMetres(a,b);
        const samples=Math.max(1,Math.ceil(lengthM/CANONICAL_MATCH_SAMPLE_M));
        for (let i=0;i<=samples;i++) {
          const id=nearestCanonicalAnchor(road,interpolateLngLat(a,b,i/samples));
          if (id!==null) covered.add(id);
        }
      }
    }
  }
  road.coveredAnchorIds=covered;
  road.coveredAnchorCount=covered.size;
  road.coverageCalculated=true;
  persistedARoadCoverageByRef.set(road.id,new Set(covered));
  return covered;
}

function requestCanonicalARoadCoverageRefresh() {
  if (canonicalARoadQueueRunning || canonicalARoadCoverageRefreshRunning || !canonicalARoadCoverageDirty) return;
  canonicalARoadCoverageRefreshRunning=true;
  const drawable=canonicalARoadDrawable();
  const ready=[...canonicalARoads.values()].filter(road=>road.status==='ready' && !road.coverageCalculated);
  canonicalARoadCoverageRefreshProgress=0;
  void (async()=>{
    try {
      for (const road of ready) {
        calculateCanonicalARoadCoverage(road,drawable);
        canonicalARoadCoverageRefreshProgress++;
        // Yield frequently enough that scrolling and input remain responsive.
        await new Promise(resolve=>setTimeout(resolve,0));
      }
      canonicalARoadCoverageDirty=false;
      scheduleLocalProgressSave();
    } finally {
      canonicalARoadCoverageRefreshRunning=false;
      renderCanonicalARoadDashboard();
      renderCanonicalARoadMapLayers();
    }
  })();
}

async function ensureCanonicalARoadsForDiscoveredRefs(refs,{limit=12}={}) {
  for (const key of refs) {
    const road=canonicalARoadState(key);
    if (road) canonicalARoadRequestedRefs.add(road.key);
  }
  if (canonicalARoadQueueRunning) return;
  canonicalARoadQueueRunning=true;
  const workerEpoch=canonicalARoadWorkerEpoch;
  let available=new Map();
  try {
    available=await loadCanonicalARoadCacheIndex();
    // Do not leave an unavailable reference permanently labelled “queued”.
    // It should not block the ready references from rendering.
    for (const key of canonicalARoadRequestedRefs) {
      const road=canonicalARoadState(key);
      if (road?.status==='idle' && !available.has(key)) {
        road.status='error';
        road.error=`${road.ref} reference is not available yet.`;
      }
    }
    let moreAvailable=true;
    while (moreAvailable && workerEpoch===canonicalARoadWorkerEpoch) {
      let loaded=0;
      while (loaded<limit && workerEpoch===canonicalARoadWorkerEpoch) {
        const key=[...canonicalARoadRequestedRefs].find(candidate=>
          available.has(candidate) && canonicalARoadState(candidate)?.status==='idle'
        );
        if (!key) break;
        // A batch commits to the map only once. Repainting Leaflet for every
        // downloaded road was the source of the visible stalls during loading.
        const road=await loadCanonicalARoad(key,false,{deferRender:true});
        // Match the newly available reference quietly in the worker. The
        // dashboard remains on its current page until the user asks to move.
        if (road?.status==='ready' && !road.coverageCalculated) {
          calculateCanonicalARoadCoverage(road,canonicalARoadDrawable());
        }
        loaded++;
        await new Promise(resolve=>setTimeout(resolve,350));
      }
      moreAvailable=[...canonicalARoadRequestedRefs]
        .some(key=>available.has(key) && canonicalARoadState(key)?.status==='idle');
      if (moreAvailable && loaded) await new Promise(resolve=>setTimeout(resolve,1200));
      if (!loaded) break;
    }
  } finally {
    canonicalARoadQueueRunning=false;
    canonicalARoadCoverageDirty=false;
    renderCanonicalARoadDashboard();
    // Loads in this worker deliberately defer per-road Leaflet rendering.
    // Commit the finished batch once so ready A-road references actually
    // become visible on the map.
    renderCanonicalARoadMapLayers();
    refreshARoadBackgroundStatus();
    scheduleLocalProgressSave();
  }
}

function aRoadStats(drawable) {
  const roads=new Map();
  for (const journey of drawable) {
    for (const feature of journey.aRoadGeoJson?.features || []) {
      const ref=aRoadFeatureId(feature);
      const region=aRoadFeatureRegion(feature);
      const key=ref ? `${region}:${ref}` : null;
      const distanceM=Number(feature?.properties?.distance_m || 0);
      if (!key || !Number.isFinite(distanceM) || distanceM<=0) continue;
      const road=roads.get(key) || {key,region,ref,distanceM:0,journeys:new Set()};
      road.distanceM+=distanceM;
      road.journeys.add(journeyIdentity(journey));
      roads.set(key,road);
    }
  }
  return [...roads.values()]
    .map(road=>({
      ...road,
      matchedKm:road.distanceM/1000,
      journeys:road.journeys.size
    }))
    .sort((a,b)=>b.matchedKm-a.matchedKm || a.region.localeCompare(b.region) || a.ref.localeCompare(b.ref,undefined,{numeric:true}));
}

function renderARoadDashboard(drawable) {
  const rows=aRoadStats(drawable);
  aRoadList.innerHTML='';
  aRoadsDiscovered.textContent=rows.length.toLocaleString();
  aRoadMileage.textContent=displayDistance(rows.reduce((sum,road)=>sum+road.matchedKm,0));
  if (!rows.length) {
    aRoadCard.classList.add('hidden');
    return;
  }
  aRoadCard.classList.remove('hidden');
  const maxKm=Math.max(...rows.map(road=>road.matchedKm),1);
  for (const road of rows) {
    const row=document.createElement('div'); row.className='motorway-row';
    const ref=document.createElement('div'); ref.className='motorway-ref a-road-ref'; ref.textContent=road.region==='NI' ? `${road.ref} · NI` : road.ref;
    const bar=document.createElement('div'); bar.className='motorway-bar';
    const fill=document.createElement('div'); fill.className='motorway-fill a-road-fill';
    fill.style.width=`${Math.max(2,road.matchedKm/maxKm*100)}%`;
    bar.append(fill);
    const value=document.createElement('div'); value.className='motorway-pct'; value.textContent=displayDistance(road.matchedKm);
    const meta=document.createElement('div'); meta.className='motorway-meta';
    meta.textContent=`${road.journeys} matched journey${road.journeys===1?'':'s'} contributed · ${displayDistance(road.matchedKm)} matched · completion % pending canonical road sections`;
    row.append(ref,bar,value,meta); aRoadList.append(row);
  }
}

function featureCollectionDistanceKm(collection) {
  return (collection?.features || []).reduce((total,feature)=>{
    const metres=Number(feature?.properties?.distance_m || 0);
    return total+(Number.isFinite(metres) && metres>0 ? metres/1000 : 0);
  },0);
}

function renderOtherRoadDashboard(drawable) {
  let matchedKm=0;
  let matchedJourneys=0;
  for (const journey of drawable) {
    if (!journey.matchedGeoJson) continue;
    const classified=journey.otherRoadGeoJson;
    const exactKm=journey.otherRoadDistanceKm;
    const fallbackKm=Math.max(0,
      Number(journey.matchedDistanceKm || 0)-
      featureCollectionDistanceKm(journey.motorwayGeoJson)-
      featureCollectionDistanceKm(journey.aRoadGeoJson)
    );
    matchedKm+=typeof exactKm==='number' && Number.isFinite(exactKm)
      ? Math.max(0,exactKm)
      : classified ? featureCollectionDistanceKm(classified) : fallbackKm;
    matchedJourneys++;
  }
  otherRoadMileage.textContent=displayDistance(matchedKm);
  otherRoadCard.classList.toggle('hidden',!shouldShowDataDashboard() || !matchedJourneys);
}

function canonicalARoadDrawable() {
  return journeys.filter(journey=>journey.selected && journey.points.length>1);
}

function activeARoadKeys(drawable=canonicalARoadDrawable()) {
  // Imported journeys retain A-road evidence at every number length. The
  // A1–A99 limitation applies only to the manual-selection catalogue.
  return new Set([...aRoadStats(drawable).map(road=>road.key),...manualARoadRefs]);
}

function canonicalARoadPageKeys(drawable=canonicalARoadDrawable()) {
  return [...activeARoadKeys(drawable)]
    .slice(canonicalARoadPage*CANONICAL_A_ROAD_PAGE_SIZE,(canonicalARoadPage+1)*CANONICAL_A_ROAD_PAGE_SIZE);
}

function showCanonicalARoadPage(page) {
  const total=activeARoadKeys().size;
  const lastPage=Math.max(0,Math.ceil(total/CANONICAL_A_ROAD_PAGE_SIZE)-1);
  canonicalARoadPage=Math.max(0,Math.min(page,lastPage));
  renderCanonicalARoadDashboard();
}

function renderCanonicalARoadDashboard(drawable=canonicalARoadDrawable()) {
  const refs=[...activeARoadKeys(drawable)];
  canonicalARoadCard.classList.toggle('hidden',!shouldShowDataDashboard() || !refs.length);
  if (!refs.length) return;
  const roads=refs.map(canonicalARoadState).filter(Boolean);
  const pageCount=Math.max(1,Math.ceil(roads.length/CANONICAL_A_ROAD_PAGE_SIZE));
  canonicalARoadPage=Math.min(canonicalARoadPage,pageCount-1);
  const pageRoads=roads.slice(canonicalARoadPage*CANONICAL_A_ROAD_PAGE_SIZE,(canonicalARoadPage+1)*CANONICAL_A_ROAD_PAGE_SIZE);
  const ready=roads.filter(road=>road.status==='ready');
  const loading=pageRoads.filter(road=>road.status==='loading');
  const errors=roads.filter(road=>road.status==='error');
  const available=roads.filter(road=>canonicalARoadCacheEntries.has(road.key));
  canonicalARoadsReady.textContent=ready.length.toLocaleString();
  canonicalARoadList.innerHTML='';
  for (const road of pageRoads) {
    const preparing=road.status==='error' && /reference is being prepared/i.test(road.error || '');
    const totalKm=road.totalKm || 0;
    const anchorCount=road.anchors.length || road.anchorCount || 0;
    const coveredCount=road.coveredAnchorIds.size || road.coveredAnchorCount || 0;
    const coveredKm=totalKm && anchorCount
      ? totalKm*coveredCount/anchorCount : 0;
    const percent=totalKm ? Math.min(100,coveredKm/totalKm*100) : 0;
    const row=document.createElement('div'); row.className='canonical-road-row';
    const top=document.createElement('div'); top.className='canonical-road-top';
    const ref=document.createElement('div'); ref.className='canonical-road-ref'; ref.textContent=road.region==='NI' ? `${road.ref} · NI` : road.ref;
    const progress=document.createElement('div'); progress.className='canonical-road-progress';
    const fill=document.createElement('div'); fill.className='canonical-road-fill'; fill.style.width=`${percent}%`; progress.append(fill);
    const value=document.createElement('div'); value.className='canonical-road-pct';
    value.textContent=road.status==='ready' ? `${percent.toFixed(1)}%` : road.status==='loading' ? 'Loading' : preparing ? 'Preparing' : road.status==='error' ? 'Unavailable' : 'Queued';
    top.append(ref,progress,value);
    const meta=document.createElement('p'); meta.className=`canonical-road-meta ${road.status==='error' && !preparing?'canonical-road-error':road.status==='loading'?'canonical-road-loading':''}`;
    meta.textContent=road.status==='ready'
      ? `${displayDistance(coveredKm)} of ${displayDistance(totalKm)} complete`
      : road.status==='error' ? road.error : road.status==='loading' ? 'Building fixed reference…' : 'Reference queued for progressive loading.';
    row.append(top,meta); canonicalARoadList.append(row);
  }
  const pending=roads.filter(road=>road.status==='idle').length;
  canonicalARoadPrevious.classList.toggle('hidden',canonicalARoadPage===0);
  canonicalARoadNext.classList.toggle('hidden',canonicalARoadPage>=pageCount-1);
  canonicalARoadPrevious.disabled=canonicalARoadPage===0;
  canonicalARoadNext.disabled=canonicalARoadPage>=pageCount-1;
  canonicalARoadPageStatus.textContent=`Showing ${canonicalARoadPage*CANONICAL_A_ROAD_PAGE_SIZE+1}–${Math.min((canonicalARoadPage+1)*CANONICAL_A_ROAD_PAGE_SIZE,roads.length)} of ${roads.length} A roads`;
  canonicalARoadNext.textContent=canonicalARoadPage>=pageCount-1 ? 'All A roads shown' : 'Next 12 A roads';
  const canLoadARoads=pending>0 && !canonicalARoadQueueRunning;
  canonicalARoadRetry.classList.toggle('hidden',!canLoadARoads);
  canonicalARoadRetry.disabled=!canLoadARoads;
  canonicalARoadRetry.textContent=canonicalARoadCacheIndexAvailable
    ? 'Load A-road completion references'
    : 'Check A-road reference index';
  canonicalARoadStatus.className=`muted canonical-status ${errors.length?'warn':ready.length?'ok':''}`;
  canonicalARoadStatus.textContent=canonicalARoadCoverageRefreshRunning
    ? `Applying saved coverage to ${canonicalARoadCoverageRefreshProgress} of ${ready.length} A-road references…`
    : loading.length
      ? `Building ${loading.length} A-road reference${loading.length===1?'':'s'}…`
      : errors.length
      ? `${ready.length} of ${roads.length} A-road references ready · ${errors.map(road=>`${road.region==='NI'?'NI ':''}${road.ref}: ${road.error || 'unavailable'}`).join(' · ')}`
      : ready.length===roads.length
        ? `${ready.length} of ${roads.length} A-road references ready.`
        : !canonicalARoadCacheIndexAvailable
          ? `${ready.length} of ${roads.length} A-road references ready · cache index unavailable: ${canonicalARoadCacheIndexError || 'unknown error'}`
          : `${ready.length} of ${roads.length} A-road references ready${available.length>ready.length?` · ${available.length-ready.length} available to load.`:pending?` · ${pending} queued.`:'.'}`;
  // Reference loading is always explicit. Restoring an expanded panel must not
  // turn the 168-road cache into a startup task.

}

let canonicalARoadMapHydrationRunning=false;
async function hydrateCanonicalARoadsForMap() {
  if (canonicalARoadMapHydrationRunning || document.querySelector('main')?.dataset.activeScreen!=='map') return;
  canonicalARoadMapHydrationRunning=true;
  try {
    const refs=[...activeARoadKeys()];
    let hydrated=0;
    for (const key of refs) {
      if (document.querySelector('main')?.dataset.activeScreen!=='map') break;
      const road=canonicalARoadState(key);
      if (!road || road.status!=='ready' || road.anchors.length) continue;
      const restored=await hydrateCanonicalARoadFromDevice(road);
      if (!restored) continue;
      if (!road.coverageCalculated) {
        calculateCanonicalARoadCoverage(road,canonicalARoadDrawable());
        void saveCanonicalARoadReference(road);
      }
      hydrated++;
      if (hydrated%12===0) {
        renderCanonicalARoadMapLayers();
        await new Promise(resolve=>setTimeout(resolve,16));
      }
    }
    if (hydrated) renderCanonicalARoadMapLayers();
  } finally {
    canonicalARoadMapHydrationRunning=false;
  }
}

function renderCanonicalARoadMapLayers() {
  // The completion list can finish in the background. Never build Leaflet
  // geometry while a full-screen panel is active: that blocks the footer.
  if (!canonicalARoadCoverageLayer || !canonicalARoadUncoveredLayer) return;
  if (document.querySelector('main')?.dataset.activeScreen!=='map') {
    canonicalARoadCoverageLayer.clearLayers();
    canonicalARoadUncoveredLayer.clearLayers();
    return;
  }
  if (!map?.hasLayer(canonicalARoadCoverageLayer)) canonicalARoadCoverageLayer.addTo(map);
  if (!map?.hasLayer(canonicalARoadUncoveredLayer)) canonicalARoadUncoveredLayer.addTo(map);
  canonicalARoadCoverageLayer.clearLayers(); canonicalARoadUncoveredLayer.clearLayers();

  const bounds=visibleMapBounds();
  const roads=[...canonicalARoads.values()].filter(road=>
    road.status==='ready' && canonicalARoadIntersectsMapBounds(road,bounds)
  );
  const visibleAnchors=roads.reduce((total,road)=>total+road.anchors.length,0);
  const zoom=map?.getZoom?.() || DEFAULT_MAP_ZOOM;
  // Simplify within an already-continuous path. Crucially, first and last
  // points of every road link remain, so a short link can never become a dot.
  const anchorBudget=zoom<7 ? 18000 : zoom<9 ? 24000 : 30000;
  const samplingStep=Math.max(1,Math.ceil(visibleAnchors/anchorBudget));
  const strokeWeight=zoom<7 ? 8 : zoom<9 ? 7 : 6;

  for (const road of roads) {
    const runs={covered:[],uncovered:[]};
    for (const path of road.paths || canonicalARoadPathsFromAnchors(road.anchors)) {
      const sampled=sampledCanonicalARoadPath(path,samplingStep);
      let previous=null,current=null,currentKind=null;
      for (const anchor of sampled) {
        if (!previous) { previous=anchor; continue; }
        if (!segmentIntersectsMapBounds([previous.lng,previous.lat],[anchor.lng,anchor.lat],bounds)) {
          current=null; previous=anchor; continue;
        }
        const kind=road.coveredAnchorIds.has(anchor.id) ? 'covered' : 'uncovered';
        if (!current || currentKind!==kind) {
          current=[[previous.lat,previous.lng]];
          runs[kind].push(current);
          currentKind=kind;
        }
        current.push([anchor.lat,anchor.lng]);
        previous=anchor;
      }
    }
    for (const [kind,paths] of Object.entries(runs)) {
      const covered=kind==='covered';
      if (!paths.length) continue;
      L.polyline(paths,{
        weight:strokeWeight,opacity:1,color:covered?'#32c96b':'#d93a3a',
        lineCap:'round',lineJoin:'round',
        pane:covered?'aRoadConfirmedPane':'aRoadUnconfirmedPane',interactive:false
      }).addTo(covered?canonicalARoadCoverageLayer:canonicalARoadUncoveredLayer);
    }
  }
}
function canonicalARoadIntersectsMapBounds(road,bounds) {
  if (!bounds || !road.bounds) return true;
  return road.bounds.east>=bounds.getWest() && road.bounds.west<=bounds.getEast() &&
    road.bounds.north>=bounds.getSouth() && road.bounds.south<=bounds.getNorth();
}

function visibleMapBounds() {
  return map?.getBounds?.().pad(.12) || null;
}

function segmentIntersectsMapBounds(a,b,bounds) {
  if (!bounds) return true;
  const west=bounds.getWest(), east=bounds.getEast();
  const south=bounds.getSouth(), north=bounds.getNorth();
  return Math.max(a[0],b[0])>=west && Math.min(a[0],b[0])<=east &&
    Math.max(a[1],b[1])>=south && Math.min(a[1],b[1])<=north;
}

function geometryPathsInMapBounds(geometry,bounds) {
  const lines=geometry?.type==='LineString' ? [geometry.coordinates] :
    geometry?.type==='MultiLineString' ? geometry.coordinates : [];
  const paths=[];
  for (const line of lines) {
    let current=[];
    for (let index=1; index<(line || []).length; index++) {
      const a=line[index-1], b=line[index];
      if (!Array.isArray(a) || !Array.isArray(b) || !segmentIntersectsMapBounds(a,b,bounds)) {
        if (current.length>1) paths.push(current);
        current=[];
        continue;
      }
      if (!current.length) current.push([Number(a[1]),Number(a[0])]);
      current.push([Number(b[1]),Number(b[0])]);
    }
    if (current.length>1) paths.push(current);
  }
  return paths;
}

function mapOverviewSegmentLimit() {
  const zoom=map?.getZoom?.() || DEFAULT_MAP_ZOOM;
  if (zoom<7) return 28000;
  if (zoom<9) return 55000;
  return 110000;
}

function reducePathsForMap(paths,limit) {
  if (paths.length<=limit) return paths;
  const stride=Math.ceil(paths.length/limit);
  return paths.filter((_,index)=>index%stride===0);
}

function liveImportPreviewPaths(geojson,maxPoints=120) {
  const lines=[];
  for (const feature of geojson?.features || []) {
    const geometry=feature?.geometry;
    if (geometry?.type==='LineString') lines.push(geometry.coordinates || []);
    if (geometry?.type==='MultiLineString') lines.push(...(geometry.coordinates || []));
  }
  const pointCount=lines.reduce((total,line)=>total+line.length,0);
  const stride=Math.max(1,Math.ceil(pointCount/maxPoints));
  return lines.map(line=>{
    if (line.length<=2 || stride===1) return line.map(([lng,lat])=>[lat,lng]);
    const preview=line.filter((_,index)=>index===0 || index===line.length-1 || index%stride===0);
    return preview.map(([lng,lat])=>[lat,lng]);
  }).filter(line=>line.length>1);
}

function appendLiveImportGeometry(activity,{color,weight,opacity=.86}={}) {
  if (!map || !liveImportLayer || !activity?.matchedGeoJson || !window.L) return;
  const paths=liveImportPreviewPaths(activity.matchedGeoJson);
  if (paths.length) L.polyline(paths,{color,weight,opacity,pane:'drivenRoadPane',interactive:false}).addTo(liveImportLayer);
}

function refreshImportMapBatch() {
  // Consolidate occasionally. This applies deduplication and updates the
  // dashboard without turning every newly matched route into a full redraw.
  renderMap();
  liveImportLayer?.clearLayers();
}

function renderMap({deferCalculations=false,preserveLive=false}={}) {
  if (!deferCalculations) {
    renderRoadQueue();
    renderCollectiveStats();
  }
  const hasCreditedRoutes=editableMappedActivities().length>0;
  mapCorrectionStartButton.classList.toggle(
    'hidden',
    !shouldShowDataDashboard() || !hasCreditedRoutes || !mapCorrectionPanel.classList.contains('hidden')
  );
  const allDrawable = journeys.filter(
    j => j.selected && j.points.length > 1
  );
  const drawable=focusedJourneyId && focusedJourneyType==='road'
    ? allDrawable.filter(journey=>journeyIdentity(journey)===focusedJourneyId)
    : focusedJourneyId ? [] : allDrawable;
  journeyFocusBar?.classList.toggle('hidden',!focusedJourneyId);

  // Keep the lightweight summary cards available before the user opts in to
  // loading the map itself.
  let dashboardError=null;
  if (!deferCalculations) {
    try {
      renderMotorwayDashboard(motorwayAggregateDirty ? allDrawable : []);
      motorwayAggregateDirty=false;
      renderARoadDashboard(allDrawable);
      renderOtherRoadDashboard(allDrawable);
    } catch (err) {
      dashboardError=err;
      console.error('Roadprints motorway mileage summary could not refresh:',err);
    }
    try {
      renderCanonicalMotorwayDashboard(allDrawable);
    } catch (err) {
      dashboardError=dashboardError || err;
      console.error('Roadprints canonical motorway map could not refresh:',err);
    }
    try {
      renderCanonicalARoadDashboard(allDrawable);
    } catch (err) {
      dashboardError=dashboardError || err;
      console.error('Roadprints canonical A-road map could not refresh:',err);
    }
  }
  if (!map || !creditedLayer) return;

  if (focusedJourneyId) {
    clearReferenceMapLayers();
  } else {
    renderCanonicalARoadMapLayers();
  }

  creditedLayer.clearLayers();
  footLayer?.clearLayers();
  if (!preserveLive) liveImportLayer?.clearLayers();
  const bounds=visibleMapBounds();

  const footPaths=[];
  const visibleFootActivities=focusedJourneyId
    ? (focusedJourneyType==='foot' ? footActivities.filter(activity=>journeyIdentity(activity)===focusedJourneyId) : [])
    : footActivities;
  for (const activity of visibleFootActivities) {
    if (!activity.matchedGeoJson || !footLayer) continue;
    const activityId=journeyIdentity(activity);
    for (const [a,b] of geometrySegments(activity.matchedGeoJson)) {
      if (segmentEvidenceIsRemoved(segmentKey(a,b),activityId)) continue;
      if (!segmentIntersectsMapBounds(a,b,bounds)) continue;
      footPaths.push([[a[1],a[0]],[b[1],b[0]]]);
    }
  }
  if (footPaths.length) {
    L.polyline(footPaths,{
      color:'#7642a8',weight:4,opacity:.86,pane:'drivenRoadPane',interactive:false
    }).addTo(footLayer);
  }

  const credited = creditedSegmentsForMap(drawable);
  const creditedPaths={high:[],review:[],low:[]};
  for (const seg of credited) {
    if (!segmentIntersectsMapBounds(seg.a,seg.b,bounds)) continue;
    const quality=creditedPaths[seg.quality] ? seg.quality : 'review';
    creditedPaths[quality].push([[seg.a[1],seg.a[0]],[seg.b[1],seg.b[0]]]);
  }
  for (const [quality,paths] of Object.entries(creditedPaths)) {
    if (!paths.length) continue;
    L.polyline(reducePathsForMap(paths,mapOverviewSegmentLimit()),{
      weight:quality==='high' ? 5 : 4,
      opacity:quality==='low' ? .45 : .85,
      color:'#111111',
      pane:'drivenRoadPane',
      dashArray:quality==='low' ? '4,6' : null,
      interactive:false
    }).addTo(creditedLayer);
  }

  requestAnimationFrame(() => map.invalidateSize(true));

  const matchedCount = drawable.filter(j => j.matchedGeoJson).length;
  const highCount = drawable.filter(j => j.matchQuality?.level === 'high').length;
  const reviewCount = drawable.filter(
    j => j.matchQuality && j.matchQuality.level !== 'high'
  ).length;

  if (mapStatus) {
    mapStatus.className = dashboardError ? 'muted map-status warn' : 'muted map-status ok';
    mapStatus.classList.remove('hidden');
    if (dashboardError) {
      mapStatus.textContent='Your saved routes are shown. Motorway figures are refreshing after an update.';
    } else if (!mapCorrectionPanel.classList.contains('hidden')) {
      mapStatus.textContent=mapCorrectionMode
        ? `${mapCorrectionMode==='remove'?'Remove':'Restore'} mode · tap a credited map section.`
        : 'Choose “Remove incorrect section” or “Restore a section” before tapping the map.';
    } else if (refinementRoadRef) {
      mapStatus.textContent=refinementEditMode
        ? `${refinementEditMode==='mark'?'Add':'Remove'} mode · tap close to the ${canonicalRoads.get(refinementRoadRef)?.ref || 'motorway'} line, or use the keyboard section controls.`
        : 'Choose “Add driven section” or “Remove driven section” before tapping the map.';
    } else if (onboardingMode==='manual') {
      const ready=[...manualMotorwayRefs]
        .filter(ref=>canonicalRoadState(ref).status==='ready').length;
      mapStatus.textContent=manualMotorwayRefs.size
        ? `${ready} of ${manualMotorwayRefs.size} selected motorway${manualMotorwayRefs.size===1?'':'s'} ready on the map.`
        : 'Select a motorway above to add it to your map.';
    } else if (onboardingMode==='saved') {
      const ready=[...canonicalRoads.values()].filter(road=>road.status==='ready').length;
      const unavailable=[...canonicalRoads.values()].filter(road=>road.status==='error').length;
      const expected=new Set([...persistedCoverageByRef.keys(),...persistedManualRefs]).size;
      if (expected && ready>=expected) {
        mapStatus.textContent='';
        mapStatus.classList.add('hidden');
      } else if (unavailable) {
        mapStatus.textContent=`Saved motorway map loaded · ${ready} of ${expected} references ready · ${unavailable} unavailable reference${unavailable===1?'':'s'} shown in Motorway progress.`;
      } else {
        mapStatus.textContent=`Restoring saved motorway map · ${ready} of ${expected} references ready.`;
      }
    } else {
      mapStatus.textContent =
        `Credited road routes: ${credited.length.toLocaleString()} unique geometry segments · ` +
        `${matchedCount.toLocaleString()} matched journeys (${highCount} high confidence, ${reviewCount} review).`;
    }
  }
}

function fitSelected() {
  if (!map || !window.L) return;

  const pts = onboardingMode==='manual' || onboardingMode==='saved'
    ? [...(onboardingMode==='manual'
        ? manualMotorwayRefs
        : new Set([...persistedCoverageByRef.keys(),...persistedManualRefs]))]
        .flatMap(ref=>(canonicalRoads.get(ref)?.anchors || []).map(anchor=>[anchor.lat,anchor.lng]))
    : journeys
        .filter(j => j.selected)
        .flatMap(j => j.points)
        .filter(validPoint)
        .map(p => [p.lat, p.lng]);

  if (!pts.length) {
    if (mapStatus) {
      mapStatus.className = 'muted map-status warn';
      mapStatus.textContent = onboardingMode==='manual'
        ? 'Select at least one motorway and wait for its reference to load.'
        : onboardingMode==='saved'
          ? 'Saved motorway references are still loading. Try Fit selected again shortly.'
          : 'No valid selected coordinates are available to fit on the map.';
    }
    return;
  }

  try {
    map.invalidateSize(true);
    map.fitBounds(L.latLngBounds(pts), {
      padding: [20, 20],
      maxZoom: 13
    });
  } catch (err) {
    if (mapStatus) {
      mapStatus.className = 'error map-status';
      mapStatus.textContent = `Could not fit selected journeys: ${err.message || err}`;
    }
  }
}

function timelineVisitFingerprint(visit) {
  const lat=Number(visit?.lat),lng=Number(visit?.lng);
  return [
    visit?.start || '',
    visit?.end || '',
    Number.isFinite(lat) ? lat.toFixed(5) : '',
    Number.isFinite(lng) ? lng.toFixed(5) : ''
  ].join('|');
}

function saveConfirmedTimelineVisits(visits) {
  for (const visit of visits || []) {
    const lat=Number(visit?.lat),lng=Number(visit?.lng);
    if (!Number.isFinite(lat) || !Number.isFinite(lng)) continue;
    const cleaned={
      id:String(visit?.id || ''),
      start:visit?.start || null,
      end:visit?.end || null,
      lat,lng,
      name:typeof visit?.name==='string' ? visit.name : null
    };
    if (!cleaned.id) cleaned.id=timelineVisitFingerprint(cleaned);
    persistedConfirmedTimelineVisits.set(cleaned.id,cleaned);
  }
  saveLocalProgressNow();
  window.dispatchEvent(new Event('roadprints:confirmed-visits-updated'));
}

function extractTimelineActivities(data) {
  const segments = Array.isArray(data?.semanticSegments)
    ? data.semanticSegments
    : [];

  const diag = {
    semanticSegments: segments.length,
    activitySegments: 0,
    passengerVehicleActivities: 0,
    onFootActivities: 0,
    confirmedVisits: 0,
    timelinePathSegments: 0,
    timelinePathPoints: 0,
    vehiclesWithPathPoints: 0,
    vehiclesWithAnchors: 0,
    journeysConstructed: 0,
    dataStartMs: null,
    dataEndMs: null
  };

  function recordDataTimestamp(value) {
    const timeMs=typeof value==='number' ? value : Date.parse(value || '');
    if (!Number.isFinite(timeMs)) return;
    diag.dataStartMs=diag.dataStartMs===null ? timeMs : Math.min(diag.dataStartMs,timeMs);
    diag.dataEndMs=diag.dataEndMs===null ? timeMs : Math.max(diag.dataEndMs,timeMs);
  }

  const pathPoints = [];
  const confirmedVisits = [];

  for (const seg of segments) {
    recordDataTimestamp(seg?.startTime);
    recordDataTimestamp(seg?.endTime);
    if (seg?.activity) diag.activitySegments++;

    const visit=seg?.visit || seg?.placeVisit;
    if (visit) {
      const point=parseLocation(
        visit?.topCandidate?.placeLocation?.latLng ||
        visit?.topCandidate?.placeLocation ||
        visit?.location || visit?.latLng
      );
      if (point) {
        const confirmed={
          start:seg.startTime || visit?.startTime || null,
          end:seg.endTime || visit?.endTime || null,
          lat:point.lat,lng:point.lng,
          name:visit?.topCandidate?.placeLocation?.name || visit?.topCandidate?.placeLocation?.address || null
        };
        confirmed.id=timelineVisitFingerprint(confirmed);
        confirmedVisits.push(confirmed);
        diag.confirmedVisits++;
      }
    }

    if (Array.isArray(seg?.timelinePath)) {
      diag.timelinePathSegments++;

      for (const item of seg.timelinePath) {
        const point = parseLocation(item?.point);
        const timeMs = Date.parse(item?.time || '');
        recordDataTimestamp(timeMs);

        if (point && Number.isFinite(timeMs)) {
          pathPoints.push({ ...point, timeMs });
          diag.timelinePathPoints++;
        }
      }
    }
  }

  pathPoints.sort((a, b) => a.timeMs - b.timeMs);

  const roadJourneys = [];
  const onFootJourneys = [];

  for (const seg of segments) {
    const activity = seg?.activity;
    if (!activity) continue;

    const mode = String(activity?.topCandidate?.type || '').trim().toUpperCase();
    const isRoad=mode === 'IN_PASSENGER_VEHICLE';
    const isOnFoot=mode === 'WALKING' || mode === 'RUNNING' || mode === 'IN_PEDESTRIAN';
    if (!isRoad && !isOnFoot) continue;

    if (isRoad) diag.passengerVehicleActivities++;
    if (isOnFoot) diag.onFootActivities++;

    const startMs = Date.parse(seg.startTime || '');
    const endMs = Date.parse(seg.endTime || '');

    let overlapping = [];

    if (Number.isFinite(startMs) && Number.isFinite(endMs)) {
      const from = lowerBound(pathPoints, startMs);
      const to = upperBound(pathPoints, endMs);

      overlapping = pathPoints
        .slice(from, to)
        .map(({ lat, lng }) => ({ lat, lng }));
    }

    if (overlapping.length) diag.vehiclesWithPathPoints++;

    const startPoint = parseLocation(activity?.start?.latLng);
    const endPoint = parseLocation(activity?.end?.latLng);

    if (startPoint || endPoint) diag.vehiclesWithAnchors++;

    const points = [];
    if (startPoint) points.push(startPoint);
    points.push(...overlapping);
    if (endPoint) points.push(endPoint);

    const cleanPoints = dedupePoints(points).filter(validPoint);

    // Keep sparse activities for an honest queue summary, but do not send them to a matcher.
    const journey={
      start: seg.startTime || null,
      end: seg.endTime || null,
      points: cleanPoints,
      pathPointCount: overlapping.length,
      googleDistanceKm: Number.isFinite(Number(activity?.distanceMeters))
        ? Number(activity.distanceMeters) / 1000
        : null,
      travelMode:isRoad ? 'ROAD' : mode,
      selected: true
    };
    if (isRoad) roadJourneys.push(journey);
    else onFootJourneys.push(journey);
  }

  for (const journey of [...roadJourneys,...onFootJourneys]) journey.importId=journeyFingerprint(journey);
  diag.journeysConstructed = roadJourneys.length;

  for (const list of [roadJourneys,onFootJourneys]) list.sort((a, b) => {
    const aa = Date.parse(a.start || '');
    const bb = Date.parse(b.start || '');
    return (Number.isFinite(aa) ? aa : 0) - (Number.isFinite(bb) ? bb : 0);
  });

  return { roadJourneys, onFootJourneys, confirmedVisits, diagnostics: diag };
}

function lowerBound(arr, target) {
  let lo = 0;
  let hi = arr.length;

  while (lo < hi) {
    const mid = (lo + hi) >> 1;
    if (arr[mid].timeMs < target) lo = mid + 1;
    else hi = mid;
  }

  return lo;
}

function upperBound(arr, target) {
  let lo = 0;
  let hi = arr.length;

  while (lo < hi) {
    const mid = (lo + hi) >> 1;
    if (arr[mid].timeMs <= target) lo = mid + 1;
    else hi = mid;
  }

  return lo;
}

function parseLocation(v) {
  if (!v) return null;

  if (typeof v === 'string') {
    const nums = String(v).match(/-?\d+(?:\.\d+)?/g);

    if (nums && nums.length >= 2) {
      const lat = Number(nums[0]);
      const lng = Number(nums[1]);

      if (Number.isFinite(lat) && Number.isFinite(lng)) {
        return { lat, lng };
      }
    }

    return null;
  }

  if (typeof v === 'object') {
    const raw = v.point || v.geo || v.latLng || v.location || v;

    if (typeof raw === 'string') return parseLocation(raw);

    if (raw && typeof raw === 'object') {
      const lat = numberish(
        raw.latitude ??
        raw.lat ??
        (Number.isFinite(Number(raw.latitudeE7))
          ? Number(raw.latitudeE7) / 1e7
          : undefined)
      );

      const lng = numberish(
        raw.longitude ??
        raw.lng ??
        raw.lon ??
        (Number.isFinite(Number(raw.longitudeE7))
          ? Number(raw.longitudeE7) / 1e7
          : undefined)
      );

      if (Number.isFinite(lat) && Number.isFinite(lng)) {
        return { lat, lng };
      }
    }
  }

  return null;
}

function numberish(v) {
  const n = Number(v);
  return Number.isFinite(n) ? n : NaN;
}

function validPoint(p) {
  return (
    p &&
    p.lat >= -90 &&
    p.lat <= 90 &&
    p.lng >= -180 &&
    p.lng <= 180
  );
}

function dedupePoints(pts) {
  return pts.filter(
    (p, i, a) =>
      i === 0 ||
      p.lat !== a[i - 1].lat ||
      p.lng !== a[i - 1].lng
  );
}

function formatDate(v) {
  if (!v) return 'Unknown date';

  const d = new Date(v);
  if (Number.isNaN(d.getTime())) return String(v);

  return new Intl.DateTimeFormat('en-GB', {
    day: '2-digit',
    month: 'short',
    year: 'numeric'
  }).format(d);
}

function formatTime(v) {
  if (!v) return '';

  const d = new Date(v);
  if (Number.isNaN(d.getTime())) return '';

  return new Intl.DateTimeFormat('en-GB', {
    hour: '2-digit',
    minute: '2-digit'
  }).format(d);
}

function formatBytes(bytes) {
  if (!Number.isFinite(bytes)) return '';
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

function activateRoadprintsScreen(screen) {
  const shell=document.querySelector('main'),nav=document.getElementById('appNavigation');
  if(!shell||!nav)return;
  shell.classList.add('app-ready');
  shell.dataset.activeScreen=screen;
  nav.classList.remove('hidden');
  nav.querySelectorAll('[data-screen]').forEach(b=>b.classList.toggle('active',b.dataset.screen===screen));
  if(screen==='map') {
    setTimeout(()=>{
      map?.invalidateSize();
      renderCanonicalARoadMapLayers();
      void hydrateCanonicalARoadsForMap();
    },80);
  }
}
document.getElementById('appNavigation')?.addEventListener('click',event=>{
  const button=event.target.closest('[data-screen]');
  if(!button) return;
  if(button.dataset.screen==='map' && focusedJourneyId) {
    focusedJourneyId=null;
    focusedJourneyType=null;
    journeyFocusBar?.classList.add('hidden');
    renderMap();
  }
  activateRoadprintsScreen(button.dataset.screen);
});
closeJourneyFocus?.addEventListener('click',closeJourneyFocusView);
const aRoadBackgroundStatus=document.getElementById('aRoadBackgroundStatus'),aRoadBackgroundText=document.getElementById('aRoadBackgroundText');aRoadBackgroundStatus?.addEventListener('click',()=>activateRoadprintsScreen('progress'));
new MutationObserver(()=>refreshARoadBackgroundStatus()).observe(canonicalARoadStatus,{childList:true,characterData:true,subtree:true});
function refreshARoadBackgroundStatus(){
  if(!aRoadBackgroundStatus)return;
  const mapOpen=document.querySelector('main')?.dataset.activeScreen==='map';
  const keys=typeof activeARoadKeys==='function' ? [...activeARoadKeys()] : [];
  const states=typeof canonicalARoadState==='function' ? keys.map(canonicalARoadState).filter(Boolean) : [];
  const stillWorking=canonicalARoadQueueRunning || canonicalARoadCoverageRefreshRunning ||
    states.some(road=>road.status==='idle' || road.status==='loading');
  const visible=mapOpen && keys.length>0 && stillWorking;
  aRoadBackgroundStatus.classList.toggle('hidden',!visible);
  if(visible && aRoadBackgroundText) aRoadBackgroundText.textContent='Road references are updating in the background';
}
const originalActivateRoadprintsScreen=activateRoadprintsScreen; activateRoadprintsScreen=function(screen){ originalActivateRoadprintsScreen(screen); refreshARoadBackgroundStatus(); };
setTimeout(refreshARoadBackgroundStatus,0);

document.getElementById('mapBrandMenu')?.addEventListener('click',()=>{ document.querySelector('main')?.classList.remove('app-ready'); document.getElementById('appNavigation')?.classList.add('hidden'); returnToOnboarding(); });


const roadDiscoveryLedger = new Map();
const roadDiscoveryByJourneyId = new Map();
let roadDiscoverySignature = '';

function roadDiscoveryKey(feature) {
  const props=feature && feature.properties || {};
  const ref=String(props.ref || props.road_ref || '').trim().toUpperCase();
  const name=String(props.name || props.road_name || '').trim();
  const kind=String(props.highway || '').toLowerCase();
  if ((!ref && !name) || /_link$|^(service|footway|path|steps|cycleway)$/.test(kind)) return null;
  const category=/^(M[0-9]+|A[0-9]+\(M\))$/.test(ref) ? 'Motorways' : /^A[0-9]+$/.test(ref) ? 'A roads' : /^B[0-9]+$/.test(ref) ? 'B roads' : 'Local roads';
  return {id:ref ? 'ref:'+ref : 'name:'+name.toLowerCase(),label:ref || name,category};
}
function rebuildRoadDiscoveryLedger() {
  const records=[...savedRoadRecords().filter(r=>r && r.matchedGeoJson).map(r=>({record:r,mode:'driving'})),...footActivities.filter(r=>r && r.matchedGeoJson).map(r=>({record:r,mode:'foot'}))].sort((a,b)=>Date.parse(a.record.start || '')-Date.parse(b.record.start || ''));
  const signature=records.map(item=>item.mode+':'+journeyIdentity(item.record)+':'+(item.record.matchedGeoJson.features || []).length).join('|');
  if (signature===roadDiscoverySignature) return;
  roadDiscoverySignature=signature; roadDiscoveryLedger.clear(); roadDiscoveryByJourneyId.clear();
  for (const item of records) {
    const seen=new Map(), newRoads=[];
    const discoveryFeatures=(item.record.roadGeoJson && item.record.roadGeoJson.features && item.record.roadGeoJson.features.length)
      ? item.record.roadGeoJson.features
      : [...(item.record.motorwayGeoJson?.features || []),...(item.record.aRoadGeoJson?.features || [])];
    for (const feature of discoveryFeatures) {
      const rawRef=String(feature?.properties?.road_ref || feature?.properties?.ref || '');
      const refs=rawRef.split(/[;,/]/).map(ref=>ref.trim()).filter(Boolean);
      const variants=refs.length>1
        ? refs.map(ref=>({...feature,properties:{...(feature.properties || {}),road_ref:ref}}))
        : [feature];
      for (const variant of variants) {
        const road=roadDiscoveryKey(variant); if (road) seen.set(road.id,road);
      }
    }
    for (const road of seen.values()) {
      let entry=roadDiscoveryLedger.get(road.id);
      if (!entry) { entry={...road,driven:false,onFoot:false}; roadDiscoveryLedger.set(road.id,entry); newRoads.push(road); }
      if (item.mode==='driving') entry.driven=true; else entry.onFoot=true;
    }
    roadDiscoveryByJourneyId.set(item.mode+':'+journeyIdentity(item.record),newRoads);
  }
}
function renderRoadDiscovery() {
  const card=document.getElementById('roadDiscoveryCard'), count=document.getElementById('roadDiscoveryCount'), summary=document.getElementById('roadDiscoverySummary'), list=document.getElementById('roadDiscoveryList');
  if (!card || !count || !summary || !list) return;
  rebuildRoadDiscoveryLedger();
  const roads=[...roadDiscoveryLedger.values()].sort((a,b)=>a.category.localeCompare(b.category)||a.label.localeCompare(b.label,'en-GB',{numeric:true}));
  card.classList.toggle('hidden',!shouldShowDataDashboard() || !roads.length);
  if (!roads.length) return;
  count.textContent=roads.length.toLocaleString();
  const driven=roads.filter(road=>road.driven).length, foot=roads.filter(road=>road.onFoot).length;
  summary.textContent=driven.toLocaleString()+' driven · '+foot.toLocaleString()+' on foot';
  list.replaceChildren();
  for (const category of ['Motorways','A roads','B roads','Local roads']) {
    const entries=roads.filter(road=>road.category===category); if (!entries.length) continue;
    const group=document.createElement('details'), title=document.createElement('summary'), rows=document.createElement('ul');
    group.className='road-discovery-group'; title.textContent=category+' · '+entries.length.toLocaleString();
    for (const road of entries) { const row=document.createElement('li'), label=document.createElement('strong'), state=document.createElement('span'); label.textContent=road.label; state.textContent=road.driven && road.onFoot ? 'Driven + on foot' : road.driven ? 'Driven' : 'On foot'; row.append(label,state); rows.append(row); }
    group.append(title,rows); list.append(group);
  }
}
const renderCollectiveStatsWithDiscovery=renderCollectiveStats;
renderCollectiveStats=function() { renderCollectiveStatsWithDiscovery(); renderRoadDiscovery(); };


const renderJourneyLogWithDiscovery=renderJourneyLog;
renderJourneyLog=function() {
  renderJourneyLogWithDiscovery();
  rebuildRoadDiscoveryLedger();
  const items=[...document.querySelectorAll('#journeyLogList .journey-log-item')];
  const records=[
    ...savedRoadRecords().filter(record=>record && record.matchedGeoJson).map(record=>({...record,logType:'road'})),
    ...footActivities.filter(record=>record && record.points && record.points.length>1).map(record=>({...record,logType:'foot'}))
  ].sort((a,b)=>Date.parse(b.start || '')-Date.parse(a.start || ''));
  const filtered=journeyLogFilter==='driving' ? records.filter(record=>record.logType==='road') : journeyLogFilter==='foot' ? records.filter(record=>record.logType==='foot') : journeyLogFilter==='service' ? [] : records;
  for (let index=0;index<items.length && index<filtered.length;index++) {
    const record=filtered[index], roads=roadDiscoveryByJourneyId.get((record.logType==='foot' ? 'foot' : 'driving')+':'+journeyIdentity(record));
    if (!roads || !roads.length) continue;
    const copy=items[index].querySelector(':scope > div');
    if (!copy || copy.querySelector('.journey-discovery')) continue;
    const details=document.createElement('details'), title=document.createElement('summary'), list=document.createElement('ul');
    details.className='journey-discovery'; title.textContent=roads.length.toLocaleString()+' new road'+(roads.length===1 ? '' : 's');
    for (const road of roads.sort((a,b)=>a.label.localeCompare(b.label,'en-GB',{numeric:true}))) { const row=document.createElement('li'); row.textContent=road.label; list.append(row); }
    details.append(title,list); copy.append(details);
  }
};

const ROAD_DISCOVERY_PLACE_NAMES_KEY='roadprints-road-discovery-places-v2';
const roadDiscoveryPlaces=new Map();
let roadDiscoveryPlaceQueueRunning=false;

function roadDiscoveryFirstPoint(feature) {
  const geometry=feature?.geometry || {};
  let coordinates=geometry.coordinates;
  while (Array.isArray(coordinates) && Array.isArray(coordinates[0]) && Array.isArray(coordinates[0][0])) coordinates=coordinates[0];
  const point=Array.isArray(coordinates) ? coordinates[0] : null;
  return Array.isArray(point) && Number.isFinite(Number(point[0])) && Number.isFinite(Number(point[1]))
    ? {lng:Number(point[0]),lat:Number(point[1])} : null;
}
function roadDiscoveryPlaceKey(point) {
  return point ? point.lat.toFixed(3)+','+point.lng.toFixed(3) : '';
}
function loadRoadDiscoveryPlaces() {
  if (roadDiscoveryPlaces.size) return;
  try { for (const [key,value] of Object.entries(JSON.parse(localStorage.getItem(ROAD_DISCOVERY_PLACE_NAMES_KEY) || '{}'))) if (typeof value==='string' && value) roadDiscoveryPlaces.set(key,value); } catch (err) {}
}
function saveRoadDiscoveryPlaces() {
  try { localStorage.setItem(ROAD_DISCOVERY_PLACE_NAMES_KEY,JSON.stringify(Object.fromEntries(roadDiscoveryPlaces))); } catch (err) {}
}
const roadDiscoveryKeyWithPoint=roadDiscoveryKey;
roadDiscoveryKey=function(feature) {
  const road=roadDiscoveryKeyWithPoint(feature);
  return road ? {...road,point:roadDiscoveryFirstPoint(feature)} : null;
};
async function resolveRoadDiscoveryPlaces() {
  if (roadDiscoveryPlaceQueueRunning || easyImportRunning) return;
  loadRoadDiscoveryPlaces();
  const pending=[...roadDiscoveryLedger.values()].filter(road=>road.category==='Local roads' && road.point && !roadDiscoveryPlaces.has(roadDiscoveryPlaceKey(road.point)));
  if (!pending.length) return;
  roadDiscoveryPlaceQueueRunning=true;
  try {
    for (const road of pending) {
      if (easyImportRunning) break;
      const key=roadDiscoveryPlaceKey(road.point);
      if (!key || roadDiscoveryPlaces.has(key)) continue;
      try {
        const response=await fetch(API_BASE_URL+'/place-name?lat='+encodeURIComponent(road.point.lat)+'&lng='+encodeURIComponent(road.point.lng));
        const data=await response.json().catch(()=>({}));
        roadDiscoveryPlaces.set(key,data.name || 'Local area');
      } catch (err) { roadDiscoveryPlaces.set(key,'Local area'); }
      saveRoadDiscoveryPlaces();
      renderRoadDiscovery();
      await new Promise(resolve=>setTimeout(resolve,1050));
    }
  } finally { roadDiscoveryPlaceQueueRunning=false; }
}
function roadDiscoveryLocality(road) {
  loadRoadDiscoveryPlaces();
  return road.point ? roadDiscoveryPlaces.get(roadDiscoveryPlaceKey(road.point)) || 'Assigning area…' : 'Local area';
}
renderRoadDiscovery=function() {
  const card=document.getElementById('roadDiscoveryCard'),count=document.getElementById('roadDiscoveryCount'),summary=document.getElementById('roadDiscoverySummary'),list=document.getElementById('roadDiscoveryList');
  if (!card || !count || !summary || !list) return;
  rebuildRoadDiscoveryLedger();
  const roads=[...roadDiscoveryLedger.values()].sort((a,b)=>a.category.localeCompare(b.category)||a.label.localeCompare(b.label,'en-GB',{numeric:true}));
  card.classList.toggle('hidden',!shouldShowDataDashboard() || !roads.length); if (!roads.length) return;
  const driven=roads.filter(road=>road.driven).length,foot=roads.filter(road=>road.onFoot).length;
  count.textContent=roads.length.toLocaleString(); summary.textContent=driven.toLocaleString()+' driven · '+foot.toLocaleString()+' on foot'; list.replaceChildren();
  for (const category of ['Motorways','A roads','B roads','Local roads']) {
    const entries=roads.filter(road=>road.category===category); if (!entries.length) continue;
    const group=document.createElement('details'),title=document.createElement('summary');
    group.className='road-discovery-group'; title.textContent=category+' · '+entries.length.toLocaleString(); group.append(title);
    if (category!=='Local roads') {
      const rows=document.createElement('ul');
      for (const road of entries) { const row=document.createElement('li'),label=document.createElement('strong'),state=document.createElement('span'); label.textContent=road.label; state.textContent=road.driven && road.onFoot ? 'Driven + on foot' : road.driven ? 'Driven' : 'On foot'; row.append(label,state);rows.append(row); }
      group.append(rows);
    } else {
      const byTown=new Map();
      for (const road of entries) { const town=roadDiscoveryLocality(road); if (!byTown.has(town)) byTown.set(town,[]); byTown.get(town).push(road); }
      for (const town of [...byTown.keys()].sort((a,b)=>a.localeCompare(b,'en-GB'))) {
        const townDetails=document.createElement('details'),townTitle=document.createElement('summary'),rows=document.createElement('ul');
        townDetails.className='road-discovery-town'; townTitle.textContent=town+' · '+byTown.get(town).length.toLocaleString();
        for (const road of byTown.get(town).sort((a,b)=>a.label.localeCompare(b.label,'en-GB',{numeric:true}))) { const row=document.createElement('li'),label=document.createElement('strong'),state=document.createElement('span'); label.textContent=road.label; state.textContent=road.driven && road.onFoot ? 'Driven + on foot' : road.driven ? 'Driven' : 'On foot'; row.append(label,state);rows.append(row); }
        townDetails.append(townTitle,rows); group.append(townDetails);
      }
    }
    list.append(group);
  }
  void resolveRoadDiscoveryPlaces();
};

const GRAVESEND_INVENTORY_KEY='roadprints-gravesend-inventory-v1';
let gravesendInventoryLoading=false;
async function renderGravesendExplorer() {
  const list=document.getElementById('roadDiscoveryList'); if (!list) return;
  const localGroup=[...list.querySelectorAll('.road-discovery-group')].find(group=>group.querySelector('summary')?.textContent.startsWith('Local roads'));
  if (!localGroup) return;
  let inventory; try { inventory=JSON.parse(localStorage.getItem(GRAVESEND_INVENTORY_KEY) || 'null'); } catch (_) {}
  if (!inventory && !gravesendInventoryLoading) {
    gravesendInventoryLoading=true;
    try { const response=await fetch(API_BASE_URL+'/local-road-inventory/gravesend'); inventory=await response.json(); if (response.ok) localStorage.setItem(GRAVESEND_INVENTORY_KEY,JSON.stringify(inventory)); }
    catch (_) {} finally { gravesendInventoryLoading=false; if (inventory) renderRoadDiscovery(); }
  }
  let card=localGroup.querySelector('.gravesend-explorer'); if (!card) { card=document.createElement('section'); card.className='gravesend-explorer'; localGroup.insertBefore(card,localGroup.querySelector('.road-discovery-town')); }
  if (!inventory?.roads) { card.textContent='Loading Gravesend Explorer…'; return; }
  const [south,west,north,east]=inventory.bounds || [];
  const eligible=new Set(inventory.roads.map(name=>name.toLocaleLowerCase('en-GB')));
  const discovered=[...roadDiscoveryLedger.values()].filter(road=>{
    const point=road.point;
    return road.category==='Local roads' && point && point.lat>=south && point.lat<=north && point.lng>=west && point.lng<=east && eligible.has(road.label.toLocaleLowerCase('en-GB'));
  });
  const percent=inventory.count ? Math.round(discovered.length/inventory.count*100) : 0;
  card.replaceChildren();
  const title=document.createElement('strong'); title.textContent='Gravesend Explorer';
  const value=document.createElement('span'); value.textContent=discovered.length.toLocaleString()+' of '+inventory.count.toLocaleString()+' named local roads discovered · '+percent+'%';
  const note=document.createElement('small'); note.textContent='Roadprints-defined Gravesend area · named public local roads only';
  card.append(title,value,note);
}
const renderRoadDiscoveryWithGravesend=renderRoadDiscovery;
renderRoadDiscovery=function() { renderRoadDiscoveryWithGravesend(); void renderGravesendExplorer(); };

const LOCAL_TOWN_INVENTORY_KEY='roadprints-local-town-inventories-v2';
const localTownInventories=new Map(); let localTownInventoryRunning=false;
// Gravesend is a previously verified Roadprints inventory. It keeps the
// established 767-road denominator available while the live service refreshes
// the remaining towns.
const localTownInventoryBaselines={Gravesend:767};
try { for (const [key,value] of Object.entries(JSON.parse(localStorage.getItem(LOCAL_TOWN_INVENTORY_KEY)||'{}'))) localTownInventories.set(key,value); } catch (_) {}
function saveLocalTownInventories(){try{localStorage.setItem(LOCAL_TOWN_INVENTORY_KEY,JSON.stringify(Object.fromEntries(localTownInventories)))}catch(_){}}
function setLocalTownSummary(town,discovered,inventory,status='checking'){
  const text=inventory?.count
    ? town+' · '+discovered.toLocaleString()+' of '+inventory.count.toLocaleString()+' · '+Math.round(discovered/inventory.count*100)+'%'
    : town+' · '+discovered.toLocaleString()+' roads · '+(status==='unavailable'?'coverage unavailable':'checking coverage…');
  document.querySelectorAll('.road-discovery-town summary[data-town]').forEach(summary=>{if(summary.dataset.town===town)summary.textContent=text;});
}
renderGravesendExplorer=async function(){};
async function updateLocalTownInventories() {
 if(localTownInventoryRunning||easyImportRunning)return; localTownInventoryRunning=true;
 try {
  const towns=new Map();
  for(const road of roadDiscoveryLedger.values()) if(road.category==='Local roads'&&road.point){const town=roadDiscoveryLocality(road);if(!towns.has(town))towns.set(town,[]);towns.get(town).push(road);}
  for(const [town,roads] of towns){
   const discovered=roads.length;
   const key=town+'|'+roadDiscoveryPlaceKey(roads[0].point);
   let inventory=localTownInventories.get(key);
   if(!inventory && localTownInventoryBaselines[town]) inventory={count:localTownInventoryBaselines[town],source:'verified baseline'};
   setLocalTownSummary(town,discovered,inventory);
   if(inventory?.count) continue;
   try {
     const controller=new AbortController(), timeout=window.setTimeout(()=>controller.abort(),20000);
     const response=await fetch(API_BASE_URL+'/local-road-inventory?lat='+encodeURIComponent(roads[0].point.lat)+'&lng='+encodeURIComponent(roads[0].point.lng),{signal:controller.signal});
     window.clearTimeout(timeout);
     const result=await response.json();
     if(response.ok && result?.count){localTownInventories.set(key,result);saveLocalTownInventories();setLocalTownSummary(town,discovered,result);}
     else setLocalTownSummary(town,discovered,null,'unavailable');
   } catch (_) { setLocalTownSummary(town,discovered,null,'unavailable'); }
   await new Promise(resolve=>setTimeout(resolve,1050));
  }
 }finally{localTownInventoryRunning=false}
}
const renderRoadDiscoveryWithTownInventories=renderRoadDiscovery;
renderRoadDiscovery=function(){
  renderRoadDiscoveryWithTownInventories();
  document.querySelectorAll('.gravesend-explorer').forEach(node=>node.remove());
  document.querySelectorAll('.road-discovery-town summary').forEach(summary=>{
    const town=summary.textContent.split(' · ')[0]; summary.dataset.town=town;
    const roads=summary.closest('.road-discovery-town')?.querySelectorAll('li').length||0;
    setLocalTownSummary(town,roads,null);
  });
  void updateLocalTownInventories();
};
