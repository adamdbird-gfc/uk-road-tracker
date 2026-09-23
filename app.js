Warning: truncated output (original token count: 76685)
Total output lines: 6749

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
// Step 8: compact, versioned local evidence derived from a successful match.
// It deliberately contains road facts only; journey geometry remains in the local archive.
const persistedRoadDiscoveryEvidence = new Map();
let mapArchiveReadyPromise = Promise.resolve();
let roadDiscoveryArchiveReadyPromise = Promise.resolve();
let footArchiveReadyPromise = Promise.resolve();
let pendingRoadImportReadyPromise = Promise.resolve();
let pendingRoadImport = null;
let pendingRoadImportSaveChain = Promise.resolve();
let activeRoadImportSource = {fileName:null, sourceFileHash:null};
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
let importMapReady = false;
// A single, persistent view of work in flight. Individual matchers update
// their own counters, while screens consult this coordinator for availability.
const importSession = { active:false, mapReady:false, statusOpen:false, roadComplete:false, footComplete:true };
let trackingSessionId = 0;
let importFootStartedAt = null;
let importFootResult = null;
let importRoadResult = null;
let roadImportProgress = {completed:0,total:0};
let footImportProgress = {completed:0,total:0};
let distanceUnit = 'miles';
let onboardingMode = null;
const refinedCoverageByRef = new Map();
const persistedCoverageByRef = new Map();
const persistedARoadCoverageByRef = new Map();
const persistedARoadReferenceSummary = new Map();
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
const LIVE_IMPORT_BATCH_SIZE = 10;
// Make each completed journey visibly join the map. The full map is still
// consolidated in small batches so the experience stays responsive.
const LIVE_IMPORT_PREVIEW_INTERVAL = 1;

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
const importReadiness = document.querySelector('.import-readiness');
const importStatusButton = document.getElementById('importStatusButton');
const closeImportStatus = document.getElementById('closeImportStatus');
const ignoredCard = document.getElementById('ignoredCard');
const ignoredCount = document.getElementById('ignoredCount');
const ignoredList = document.getElementById('ignoredList');
const footQueueCard = document.getElementById('footQueueCard');
const footProgressText = document.getElementById('footProgressText');
const footProgressBar = document.getElementById('footProgressBar');
const roadImportSummaryText = document.getElementById('roadImportSummaryText');
const roadImportSummaryBar = document.getElementById('roadImportSummaryBar');
const footImportSummaryText = document.getElementById('footImportSummaryText');
const footImportSummaryBar = document.getElementById('footImportSummaryBar');
const startFootBatch = document.getElementById('startFootBatch');
const pauseFootMatching = document.getElementById('pauseFootMatching');
const retryFootImport = document.getElementById('retryFootImport');
const retryRoadImport = document.getElementById('retryRoadImport');
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
  M1:311.946, M2:41.210, M3:98.947, M4:194.212, M5:260.202, M6:423.978, 'M6 Toll':43.0,
  M11:84.419, M18:45.214, M20:82.586, M23:26.725, M25:189.869, M26:16.462,
  M27:52.695, M32:7.303, M40:144.651, M42:64.619, M45:13.369, M48:8.899,
  M49:8.611, M50:34.438, M53:32.032, M54:36.078, M55:19.069, M56:55.688,
  M57:16.050, M58:18.657, M60:56.734, M61:43.989, M62:153.828, M65:32.238,
  M66:14.297, M67:7.656, M69:26.269, M180:41.076, M181:4.190,
  M271:3.537, M275:3.0, M602:6.958, M606:4.663, M621:14.803,
  M8:97.0, M9:53.1, M73:11.0, M74:56.0, M77:32.0, M80:40.0,
  M90:78.0, M876:13.0, M898:2.0, 'A74(M)':72.0
};
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
const CANONICAL_A_ROAD_LOAD_REQUEST_KEY = `roadprints:canonical-a-road-load-request:${CANONICAL_A_ROAD_CACHE_VERSION}`;
let canonicalARoadLoadRequested = localStorage.getItem(CANONICAL_A_ROAD_LOAD_REQUEST_KEY)==='true' || Boolean(localStorage.getItem(CANONICAL_A_ROAD_INDEX_STORAGE_KEY));
let canonicalARoadResumeAttempted = false;
const CANONICAL_A_ROAD_REQUEST_TIMEOUT_MS = 45000;
const LOCAL_PROGRESS_KEY = 'uk-road-tracker-progress-v1';
const FOOT_PLACE_NAMES_KEY = 'roadprints-foot-place-names-v1';
const MAP_ARCHIVE_DB_NAME = 'roadprints-map-archive';
const MAP_ARCHIVE_DB_VERSION = 6;
const MAP_ARCHIVE_STORE_NAME = 'journeys';
const FOOT_ACTIVITY_STORE_NAME = 'foot-activities';
const CANONICAL_ROAD_STORE_NAME = 'canonical-roads';
const CANONICAL_A_ROAD_STORE_NAME = 'canonical-a-roads';
const PENDING_ROAD_IMPORT_STORE_NAME = 'pending-road-import';
const ROAD_DISCOVERY_STORE_NAME = 'road-discovery';
const ROAD_DISCOVERY_DERIVATION_VERSION = 1;
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
    id:'mary-high-streets',
    icon:'👑',
    title:'Nice to meet you Mary',
    description:'Visit 10 different roads named High Street.',
    detail:'10 different High Streets discovered',
    type:'high-street-settlement', target:10
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
    pendingRoadImportCandidates().length>0 ||
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
  const resumableRoads=pendingRoadImportCandidates().length;
  const hasProgress=roadCount>0 || journeyCount>0 || resumableRoads>0 || persistedFootActivities.size>0;
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
    `${roadCount} motorway${roadCount===1?'':'s'} with saved coverage${resumableRoads ? ` · ${resumableRoads.toLocaleString()} road journey${resumableRoads===1?'':'s'} ready to resume` : ''} · ${range} · saved ${savedLabel}.`;
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
      if (!database.objectStoreNames.contains(PENDING_ROAD_IMPORT_STORE_NAME)) {
        database.createObjectStore(PENDING_ROAD_IMPORT_STORE_NAME,{keyPath:'id'});
      }
      if (!database.objectStoreNames.contains(ROAD_DISCOVERY_STORE_NAME)) {
        database.createObjectStore(ROAD_DISCOVERY_STORE_NAME,{keyPath:'id'});
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

async function roadDiscoveryArchiveOperation(mode,operation) {
  const database=await openMapArchiveDatabase();
  try {
    return await new Promise((resolve,reject)=>{
      const transaction=database.transaction(ROAD_DISCOVERY_STORE_NAME,mode);
      const store=transaction.objectStore(ROAD_DISCOVERY_STORE_NAME);
      const request=operation(store);
      request.onerror=()=>reject(request.error || new Error('Saved road-discovery operation failed.'));
      request.onsuccess=()=>resolve(request.result);
      transaction.onabort=()=>reject(transaction.error || new Error('Saved road-discovery transaction was aborted.'));
    });
  } finally { database.close(); }
}

function discoveryFeaturesForJourney(journey) {
  const roadFeatures=journey?.roadGeoJson?.features;
  return Array.isArray(roadFeatures) && roadFeatures.length
    ? roadFeatures
    : [...(journey?.motorwayGeoJson?.features || []),...(journey?.aRoadGeoJson?.features || [])];
}

function deriveRoadDiscoveryEvidence(journey,mode) {
  const journeyId=journeyIdentity(journey);
  if (!journeyId || !journey?.matchedGeoJson) return null;
  const roads=new Map();
  for (const feature of discoveryFeaturesForJourney(journey)) {
    const rawRef=String(feature?.properties?.road_ref || feature?.properties?.ref || '');
    const refs=rawRef.split(/[;,/]/).map(ref=>ref.trim()).filter(Boolean);
    const variants=refs.length>1
      ? refs.map(ref=>({...feature,properties:{...(feature.properties || {}),road_ref:ref}}))
      : [feature];
    for (const variant of variants) {
      const road=roadDiscoveryKey(variant);
      if (road) roads.set(road.id,{id:road.id,label:road.label,category:road.category});
    }
  }
  return {
    id:mode+':'+journeyId,
    journeyId,
    mode,
    version:ROAD_DISCOVERY_DERIVATION_VERSION,
    derivedAt:new Date().toISOString(),
    matchedFeatureCount:discoveryFeaturesForJourney(journey).length,
    roads:[...roads.values()].sort((a,b)=>a.category.localeCompare(b.category)||a.label.localeCompare(b.label,'en-GB',{numeric:true}))
  };
}

async function saveRoadDiscoveryEvidence(journey,mode) {
  const record=deriveRoadDiscoveryEvidence(journey,mode);
  if (!record) return;
  await roadDiscoveryArchiveOperation('readwrite',store=>store.put(record));
  persistedRoadDiscoveryEvidence.set(record.id,record);
}

async function recordRoadDiscoveryEvidence(journey,mode) {
  try {
    await saveRoadDiscoveryEvidence(journey,mode);
  } catch (err) {
    // The proven journey archive remains the source of truth during this
    // transition. A derived-record failure must never interrupt an import.
    console.warn('Road-discovery evidence could not be saved:',err);
  }
}

async function loadRoadDiscoveryArchive() {
  try {
    const records=await roadDiscoveryArchiveOperation('readonly',store=>store.getAll());
    persistedRoadDiscoveryEvidence.clear();
    for (const record of records || []) {
      if (record?.id && record.version===ROAD_DISCOVERY_DERIVATION_VERSION) persistedRoadDiscoveryEvidence.set(record.id,record);
    }
  } catch (err) {
    console.warn('Saved road-discovery evidence could not be loaded:',err);
  }
}

async function backfillRoadDiscoveryEvidence() {
  const candidates=[
    ...savedRoadRecords().filter(record=>record?.matchedGeoJson).map(record=>({record,mode:'driving'})),
    ...footActivities.filter(record=>record?.matchedGeoJson).map(record=>({record,mode:'foot'}))
  ];
  for (const item of candidates) {
    const id=item.mode+':'+journeyIdentity(item.record);
    if (persistedRoadDiscoveryEvidence.get(id)?.version===ROAD_DISCOVERY_DERIVATION_VERSION) continue;
    await recordRoadDiscoveryEvidence(item.record,item.mode);
  }
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
    updateImportStatusButton();
  } catch (err) {
    console.warn('Saved map journeys could not be loaded:',err);
  }
}

async function saveJourneyToMapArchive(journey) {
  const record=compactMapJourney(journey);
  if (!record) throw new Error('The matched journey did not contain saveable map geometry.');
  await mapArchiveOperation('readwrite',store=>store.put(record));
  persistedMapJourneys.set(record.id,record);
  await recordRoadDiscoveryEvidence(record,'driving');
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
  await roadDiscoveryArchiveOperation('readwrite',store=>store.clear());
  persistedRoadDiscoveryEvidence.clear();
  await clearPendingRoadImport();
  window.dispatchEvent(new Event('roadprints:archivechange'));
}

async function pendingRoadImportOperation(mode,operation) {
  const database=await openMapArchiveDatabase();
  try {
    return await new Promise((resolve,reject)=>{
      const transaction=database.transaction(PENDING_ROAD_IMPORT_STORE_NAME,mode);
      const store=transaction.objectStore(PENDING_ROAD_IMPORT_STORE_NAME);
      const request=operation(store);
      request.onerror=()=>reject(request.error || new Error('Saved road-import queue operation failed.'));
      request.onsuccess=()=>resolve(request.result);
      transaction.onabort=()=>reject(transaction.error || new Error('Saved road-import queue transaction was aborted.'));
    });
  } finally { database.close(); }
}

function compactPendingRoadJourney(journey) {
  const id=journeyIdentity(journey);
  if (!id) return null;
  return {
    id,
    start:journey.start || '', end:journey.end || '',
    travelMode:journey.travelMode || 'ROAD',
    googleDistanceKm:Number.isFinite(Number(journey.googleDistanceKm)) ? Number(journey.googleDistanceKm) : null,
    pathPointCount:Number(journey.pathPointCount || journey.points?.length || 0),
    points:(journey.points || []).filter(validPoint).map(point=>({lat:Number(point.lat),lng:Number(point.lng)})),
    repeatJourneyIds:Array.isArray(journey.repeatJourneyIds) ? journey.repeatJourneyIds : null,
    repeatJourneyMileage:journey.repeatJourneyMileage || null,
    repeatCount:Number(journey.repeatCount || 1),
    repeatDistanceKm:Number(journey.repeatDistanceKm || journey.googleDistanceKm || 0)
  };
}

function hydratePendingRoadJourney(record) {
  return {...record, importId:record.id, selected:true, _pendingRoadQueue:true};
}

function pendingRoadImportCandidates() {
  return (pendingRoadImport?.items || [])
    .filter(item=>['pending','failed'].includes(item?.state) && !persistedMapJourneys.has(item?.journey?.id))
    .map(item=>hydratePendingRoadJourney(item.journey));
}

async function loadPendingRoadImport() {
  try {
    const record=await pendingRoadImportOperation('readonly',store=>store.get('active'));
    if (!record || record.version!==1 || !Array.isArray(record.items)) return;
    pendingRoadImport=record;
  } catch (err) {
    console.warn('Saved road-import queue could not be loaded:',err);
  }
}

function savePendingRoadImport() {
  pendingRoadImportSaveChain=pendingRoadImportSaveChain.then(async()=>{
    if (!pendingRoadImport) return;
    pendingRoadImport.updatedAt=new Date().toISOString();
    await pendingRoadImportOperation('readwrite',store=>store.put(pendingRoadImport));
  });
  return pendingRoadImportSaveChain;
}

async function clearPendingRoadImport() {
  pendingRoadImport=null;
  pendingRoadImportSaveChain=pendingRoadImportSaveChain.then(() =>
    pendingRoadImportOperation('readwrite',store=>store.delete('active'))
  );
  await pendingRoadImportSaveChain;
}

async function preparePendingRoadImport(candidates) {
  const signature=candidates.map(journeyIdentity).filter(Boolean).sort().join('|');
  if (pendingRoadImport?.signature===signature) return pendingRoadImportCandidates();
  const items=candidates.map(compactPendingRoadJourney).filter(Boolean).map(journey=>({journey,state:'pending',attempts:0,error:null}));
  pendingRoadImport={
    id:'active',version:1,signature,
    sourceFileHash:activeRoadImportSource.sourceFileHash || null,
    sourceFileName:activeRoadImportSource.fileName || null,
    createdAt:new Date().toISOString(),updatedAt:new Date().toISOString(),items
  };
  await savePendingRoadImport();
  updateLocalProgressNotice();
  return pendingRoadImportCandidates();
}

async function checkpointPendingRoadJourney(journey,state,error=null) {
  const id=journeyIdentity(journey);
  const item=pendingRoadImport?.items?.find(candidate=>candidate?.journey?.id===id);
  if (!item) return;
  item.state=state;
  item.error=error ? String(error) : null;
  item.attempts=Number(item.attempts || 0)+1;
  await savePendingRoadImport();
  updateLocalProgressNotice();
}

async function finishPendingRoadImportIfComplete() {
  if (!pendingRoadImport?.items?.length) return;
  if (pendingRoadImport.items.every(item=>item.state==='completed')) await clearPendingRoadImport();
  updateLocalProgressNotice();
}

async function clearFootArchive() {
  await footArchiveOperation('readwrite',store=>store.clear());
  persistedFootActivities.clear();
  footActivities=[];
  footBatches=[];
  await roadDiscoveryArchiveOperation('readwrite',store=>store.clear());
  persistedRoadDiscoveryEvidence.clear();
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
    updateImportStatusButton();
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
    await recordRoadDiscoveryEvidence(record,'foot');
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
  for (const feature of journeyMotorwayFeatures(journey)) {
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
  onboardingCard.classList.remove('hidden');
  updateLocalProgressNotice();
}

function motorwayRefSort(a, b) {
  return a.localeCompare(b, undefined, {numeric:true});
}

function resetTrackingSession() {
  trackingSessionId++;
  easyImportPaused = false;
  easyImportRunning = false;
  importMode = null;
  journeys = [];
  diagnostics = {};
  ignoredJourneys = [];
  refinedCoverageByRef.clear();
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
  roadImportSummaryBar && (roadImportSummaryBar.value = 0);
  roadImportProgress = {completed:0,total:0};
  footImportProgress = {completed:0,total:0};
  setEasyProgressStatus('Waiting…','Preparing routes');
  renderCollectiveImportProgress();
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
  await Promise.all([mapArchiveReadyPromise,footArchiveReadyPromise,pendingRoadImportReadyPromise]);
  if (!persistedCoverageByRef.size && !persistedMapJourneys.size && !persistedFootActivities.size && !pendingRoadImportCandidates().length) return;

  const roadImportStillRunning=easyImportRunning;
  const footImportStillRunning=footMatching;
  // Road and on-foot recovery are independent. On-foot may have resumed from
  // its archive before the user opens saved data; that must not prevent the
  // pending road queue from being restored and started.
  if (!roadImportStillRunning) {
    if (!footImportStillRunning) resetTrackingSession();
    journeys=[...savedMapJourneysExcluding(),...pendingRoadImportCandidates()];
  }
  onboardingMode='saved';
  document.querySelector('main')?.classList.remove('onboarding-active');
  onboardingCard.classList.add('hidden');
  dataSourceCard.classList.add('hidden');
  closeSavedProgress.classList.remove('hidden');
  mapTitle.textContent='Your saved Roadprints progress';
  mapIntro.textContent='This is the road and on-foot progress saved on this device. Return to the start to import new Timeline data.';
  mapCard.classList.remove('hidden');
  // Switch screens before waiting for Leaflet, so “View data” never exposes
  // the previously active Progress panel during a mobile cold start.
  activateRoadprintsScreen('map');
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
  requestAnimationFrame(()=>map?.invalidateSize(true));
  if (!roadImportStillRunning && pendingRoadImportCandidates().length) {
    setTimeout(()=>void startEasyImport(),0);
  }
}

async function showDataSourceChoice() {
  if (easyImportRunning || footMatching) {
    returnToOnboarding();
    return;
  }
  resetTrackingSession();
  onboardingMode = 'data';
  document.querySelector('main')?.classList.remove('onboarding-active');
  closeSavedProgress.classList.add('hidden');
  onboardingCard.classList.add('hidden');
  dataSourceCard.classList.remove('hidden');
  mapTitle.textContent = '4. Preview';
  mapIntro.textContent = 'The cumulative credited-road layer shows each matched geometry segment once. Use the map layer control to compare credited roads, matched journeys and raw Timeline traces.';
}

function returnToOnboarding() {
  // Returning to the splash is navigation, not cancellation. Keep a live
  // import running so Saved data and Growing can take the user back to it.
  if (!easyImportRunning && !footMatching) resetTrackingSession();
  onboardingMode = null;
  closeSavedProgress.classList.add('hidden');
  dataSourceCard.classList.add('hidden');
  onboardingCard.classList.remove('hidden');
  document.querySelector('main')?.classList.add('onboarding-active');
  // Keep matching alive, but never let its workspace leak into the splash.
  document.querySelector('main')?.classList.remove('processing-active');
  easyProgress.classList.add('hidden');
  footQueueCard.classList.add('hidden');
  document.getElementById('hasDataSource')?.classList.toggle('hidden',easyImportRunning || footMatching);
  updateImportStatusButton();
}

document.getElementById('hasDataSource').addEventListener('click', showDataSourceChoice);
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
roadDiscoveryArchiveReadyPromise=loadRoadDiscoveryArchive();
pendingRoadImportReadyPromise=loadPendingRoadImport().finally(updateLocalProgressNotice);
Promise.all([mapArchiveReadyPromise,footArchiveReadyPromise,roadDiscoveryArchiveReadyPromise])
  .then(()=>backfillRoadDiscoveryEvidence())
  .catch(error=>console.warn('Saved road-discovery evidence could not be refreshed:',error));
unitMiles.classList.toggle('active',distanceUnit==='miles');
unitKm.classList.toggle('active',distanceUnit==='km');
unitMiles.setAttribute('aria-pressed',String(distanceUnit==='miles'));
unitKm.setAttribute('aria-pressed',String(distanceUnit==='km'));
aRoadUnitMiles.classList.toggle('active',distanceUnit==='miles');
aRoadUnitKm.classList.toggle('active',distanceUnit==='km');
aRoadUnitMiles.setAttribute('aria-pressed',String(distanceUnit==='miles'));
aRoadUnitKm.setAttribute('aria-pressed',String(distanceUnit==='km'));
updateLocalProgressNotice();
fileInput.addEventListener('change', async () => {
  const file = fileInput.files?.[0];
  if (!file) return;

  resetOutput();
  status(`Reading ${file.name} (${formatBytes(file.size)})…`);

  try {
    await Promise.all([mapArchiveReadyPromise,footArchiveReadyPromise,pendingRoadImportReadyPromise]);
    const text = await file.text();
    status(`Read complete. Identifying this file…`);
    const sourceFileHash = await timelineFileHash(text);
    activeRoadImportSource={fileName:file.name,sourceFileHash};
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
    Object.assign(diagnostics, classifyImportSupport(allJourneys, onFootJourneys));
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

    saveImportCoordinatorSession(file.name, sourceFileHash, diagnostics);
    void verifyImportCoordinatorCompatibility();
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
// The persistent Growing control sits above every tab overlay. Delegating its
// click preserves the action if a tab rebuilds while matching continues.
importStatusButton?.addEventListener('click',openImportStatus);
document.addEventListener('click',event=>{
  if (event.target.closest('#importStatusButton')) openImportStatus();
});
closeImportStatus?.addEventListener('click',closeImportStatusView);
document.getElementById('detailedImport').addEventListener('click', startDetailedImport);
startFootBatch.addEventListener('click',()=>{
  if(footActivities.some(activity=>!activity.matchedGeoJson&&activity.matchError))void retryUnableFootMatches();
  else void startNextFootBatch();
});
retryFootImport?.addEventListener('click',()=>void retryUnableFootMatches());
retryRoadImport?.addEventListener('click',()=>void startEasyImport());
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

  // This is the one visible import control, so it pauses/resumes both queues.
  // An already in-flight request may finish and checkpoint once, then each
  // worker stops before taking its next Journey.
  easyImportPaused = !easyImportPaused;
  footMatchingPaused = easyImportPaused;
  updateEasyImportPauseButton();
  renderFootQueue();

  if (!easyImportPaused) {
    setEasyProgressStatus('Resuming…','Continuing driving and on-foot matching');
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
  canonicalARoadLoadRequested=true;
  try { localStorage.setItem(CANONICAL_A_ROAD_LOAD_REQUEST_KEY,'true'); } catch (_) {}
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

const IMPORT_COORDINATOR_SESSION_KEY = 'roadprints-import-coordinator-session-v1';
let importCoordinatorStatus = 'Import summary saved on this device. Your Timeline file and journeys are not uploaded.';

function saveImportCoordinatorSession(fileName, sourceFileHash, summary) {
  try {
    localStorage.setItem(IMPORT_COORDINATOR_SESSION_KEY, JSON.stringify({
      version: 1,
      source: 'google_timeline',
      status: 'validated_locally',
      savedAt: new Date().toISOString(),
      fileName,
      sourceFileHash,
      summary: {
        roadActivities: Number(summary.roadActivities || 0),
        onFootActivities: Number(summary.onFootActivities || 0),
        usableRoadRoutes: Number(summary.usableJourneys || 0),
        ignoredRoutes: Number(summary.ignoredSparseJourneys || 0),
        ukSupportedActivities: Number(summary.ukSupportedActivities || 0),
        outsideSupportedActivities: Number(summary.outsideSupportedActivities || 0)
      }
    }));
    importCoordinatorStatus = 'Import summary saved on this device. Your Timeline file and journeys are not uploaded.';
  } catch (error) {
    console.warn('Import coordinator summary could not be saved:', error);
    importCoordinatorStatus = 'This import is being processed on this device. Your Timeline file and journeys are not uploaded.';
  }
}

async function verifyImportCoordinatorCompatibility() {
  try {
    const [response, catalogue] = await Promise.all([
      fetch(API_BASE_URL + '/import-coordinator', {
        method: 'POST',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify({contract_version: 1, contains_personal_data: false})
      }),
      fetch(API_BASE_URL + '/reference-catalogue/status')
    ]);
    if (!response.ok || !catalogue.ok) throw new Error('Coordinator unavailable');
  } catch (error) {
    // The local import remains fully usable if the optional compatibility
    // handshake cannot be reached. No Timeline content is ever retried or sent.
    console.info('Import coordinator compatibility check unavailable:', error);
  }
}

function isUKSupportedCoordinate(point) {
  const lat = Number(point?.lat);
  const lng = Number(point?.lng);
  if (!Number.isFinite(lat) || !Number.isFinite(lng)) return false;
  const inGreatBritain = lat >= 49.8 && lat <= 61.1 && lng >= -8.8 && lng <= 2.2;
  const inNorthernIreland = lat >= 53.8 && lat <= 55.5 && lng >= -8.5 && lng <= -5.0;
  return inGreatBritain || inNorthernIreland;
}

function classifyImportSupport(roadJourneys, onFootJourneys) {
  const activities = [...(roadJourneys || []), ...(onFootJourneys || [])];
  let ukSupportedActivities = 0;
  let outsideSupportedActivities = 0;
  for (const activity of activities) {
    const supported = (activity.points || []).some(isUKSupportedCoordinate);
    activity.ukSupportStatus = supported ? 'uk_supported' : 'outside_supported_area';
    if (supported) ukSupportedActivities++;
    else outsideSupportedActivities++;
  }
  return {ukSupportedActivities, outsideSupportedActivities};
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
    summaryStat(fmt(diagnostics.ignoredSparseJourneys), 'unable to use'),
    summaryStat(fmt(diagnostics.outsideSupportedActivities), 'outside current UK scope')
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

  const coordinatorNote = document.createElement('p');
  coordinatorNote.className = 'muted file-summary-note';
  coordinatorNote.textContent = importCoordinatorStatus;

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
  fileStatus.append(heading, stats, queues, explanation, coordinatorNote, details);
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
  const outsideSupported = Number(diagnostics.outsideSupportedActivities || 0);
  const parts = [
    `${fmt(ready)} car journey${ready === 1 ? ' is' : 's are'} ready for road matching.`
  ];
  if (repeats) parts.push(`${fmt(repeats)} repeat road ${repeats === 1 ? 'activity has' : 'activities have'} been grouped with a representative route, so their mileage can be retained without extra matching calls.`);
  if (onFoot) parts.push(`${fmt(onFoot)} walking or running ${onFoot === 1 ? 'activity is' : 'activities are'} held in the separate on-foot queue; they are not sent to the road matcher.`);
  if (previous) parts.push(`${fmt(previous)} already matched journey${previous === 1 ? ' has' : 's have'} been safely skipped.`);
  if (ignored) parts.push(`${fmt(ignored)} journey${ignored === 1 ? ' does' : 's do'} not contain enough location detail to match reliably.`);
  if (outsideSupported) parts.push(`${fmt(outsideSupported)} activity${outsideSupported === 1 ? ' is' : 'ies are'} outside the current UK support area and is recorded separately for the future UK-only pipeline.`);
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
    ['…26685 tokens truncated…(completedByRegion.GB,GB_MOTORWAY_NETWORK_KM,GB_MOTORWAY_NETWORK_MILES);
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
    ? drawable.flatMap(j=>journeyMotorwayFeatures(j).map(motorwayFeatureId).filter(Boolean))
    : [];
  // Saved journeys retain their motorway features, so use them to restore
  // references after a refresh even before coverage has been recalculated.
  const discoveredRefs=drawable
    ? [...new Set([...persistedCoverageByRef.keys(),...sessionRefs])].sort(motorwayRefSort)
    : onboardingMode==='saved'
      ? [...persistedCoverageByRef.keys()].sort(motorwayRefSort)
      : [...canonicalRoads.keys()];

  if (!discoveredRefs.length && !canonicalRoads.size) {
    canonicalMotorwayCard.classList.add('hidden');
    return;
  }
  canonicalMotorwayCard.classList.toggle('hidden',Boolean(refinementRoadRef));

  const shouldRecalculateCoverage=canonicalCoverageDirty &&
    (drawable || onboardingMode==='saved');
  if (shouldRecalculateCoverage) {
    for (const ref of discoveredRefs) {
      const road=canonicalRoadState(ref);
      if (road.status==='ready') road.coveredAnchorIds=calculateCanonicalCoverageForRoad(road,drawable || []);
    }
    canonicalCoverageDirty=false;
  }
  evaluateAchievements();
  if (drawable || onboardingMode==='saved') {
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
  const saved=refinedCoverageByRef.get(road.id);
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
  refinedCoverageByRef.set(road.id,coverage);
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
  refinedCoverageByRef.set(road.id,new Set(previous));
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
  refinedCoverageByRef.set(road.id,empty);
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
  const covered=new Set(persistedARoadCoverageByRef.get(road.id) || []);
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
    const reclassifiedM6TollKm=(journey.motorwayGeoJson?.features || []).some(isM6TollFeature)
      ? 0
      : featureCollectionDistanceKm({type:'FeatureCollection',features:(journey.roadGeoJson?.features || []).filter(isM6TollFeature)});
    const fallbackKm=Math.max(0,
      Number(journey.matchedDistanceKm || 0)-
      featureCollectionDistanceKm({type:'FeatureCollection',features:journeyMotorwayFeatures(journey)})-
      featureCollectionDistanceKm(journey.aRoadGeoJson)
    );
    matchedKm+=typeof exactKm==='number' && Number.isFinite(exactKm)
      ? Math.max(0,exactKm-reclassifiedM6TollKm)
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
  return new Set(aRoadStats(drawable).map(road=>road.key));
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
  // A user-requested A-road reference pass survives refresh/re-entry. This
  // remains opt-in: without that explicit request, saved history is not turned
  // into a large reference-loading task.
  if (canonicalARoadLoadRequested && !canonicalARoadResumeAttempted && pending>0) {
    canonicalARoadResumeAttempted=true;
    setTimeout(startCanonicalARoadLoading,0);
  }

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
  // Consolidate into the durable cumulative layer without first blanking the
  // live preview. On a mobile map that blank frame made each new journey look
  // as though earlier routes had vanished.
  renderMap({deferCalculations:true,preserveLive:true});
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
    } else if (onboardingMode==='saved') {
      const ready=[...canonicalRoads.values()].filter(road=>road.status==='ready').length;
      const unavailable=[...canonicalRoads.values()].filter(road=>road.status==='error').length;
      const expected=persistedCoverageByRef.size;
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

  const pts = onboardingMode==='saved'
    ? [...persistedCoverageByRef.keys()]
        .flatMap(ref=>(canonicalRoads.get(ref)?.anchors || []).map(anchor=>[anchor.lat,anchor.lng]))
    : journeys
        .filter(j => j.selected)
        .flatMap(j => j.points)
        .filter(validPoint)
        .map(p => [p.lat, p.lng]);

  if (!pts.length) {
    if (mapStatus) {
      mapStatus.className = 'muted map-status warn';
      mapStatus.textContent = onboardingMode==='saved'
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
  // This screen may have first rendered during splash setup, before app-ready
  // existed. Refresh it at the point the user actually opens the tab.
  if(screen==='achievements') renderAchievements();
  // Screen navigation must not accidentally hide the global import status.
  updateImportStatusButton();
  if(screen==='map' && !settlementBoundaryMode) {
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
  closeImportStatusView();
  if(settlementBoundaryMode){clearSettlementBoundary()}if(button.dataset.screen==='map' && focusedJourneyId) {
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
  // An idle reference is merely eligible to load; it is not background work.
  // Only show this chip while the browser has an actual load or coverage pass running.
  const stillWorking=canonicalARoadQueueRunning || canonicalARoadCoverageRefreshRunning ||
    states.some(road=>road.status==='loading');
  const visible=mapOpen && !settlementBoundaryMode && keys.length>0 && stillWorking;
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
  // Some OSM features carry an internal-looking number as their entire name
  // or ref.  It is not a road name, so do not surface it as a discovery.
  const numericRef=/^\d+(?:[.,]\d+)?$/.test(ref),numericName=/^\d+(?:[.,]\d+)?$/.test(name);
  if ((!ref && numericName) || (numericRef && (!name || numericName))) return null;
  const displayRef=numericRef ? '' : normaliseMotorwayRef(ref) || ref;
  const category=/^(M[0-9]+|A[0-9]+\(M\)|M6 Toll)$/.test(displayRef) ? 'Motorways' : /^A[0-9]+$/.test(displayRef) ? 'A roads' : /^B[0-9]+$/.test(displayRef) ? 'B roads' : 'Local roads';
  return {id:displayRef ? 'ref:'+displayRef : 'name:'+name.toLowerCase(),label:displayRef || name,category};
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
        const road=roadDiscoveryKey(variant); if (road) {
          // Keep the matched feature geometry as evidence.  The visible ledger
          // still de-duplicates by road identity, but settlement assignment
          // must later use the travelled geometry rather than a place label.
          const evidence={geometry:variant.geometry,journeyId:journeyIdentity(item.record),mode:item.mode};
          const existing=seen.get(road.id);
          if(existing) existing.evidence.push(evidence);
          else seen.set(road.id,{...road,evidence:[evidence]});
        }
      }
    }
    // Only read the new derived record when it exactly describes the
    // journey's current matched-road facts. Otherwise the established
    // feature calculation remains the fallback for this journey alone.
    const storedEvidence=persistedRoadDiscoveryEvidence.get(item.mode+':'+journeyIdentity(item.record));
    const currentRoads=[...seen.values()].map(road=>({id:road.id,label:road.label,category:road.category})).sort((a,b)=>a.id.localeCompare(b.id));
    const storedRoads=Array.isArray(storedEvidence?.roads)
      ? storedEvidence.roads.map(road=>({id:road.id,label:road.label,category:road.category})).sort((a,b)=>a.id.localeCompare(b.id))
      : [];
    const evidenceMatches=storedEvidence?.version===ROAD_DISCOVERY_DERIVATION_VERSION
      && storedEvidence.matchedFeatureCount===discoveryFeatures.length
      && storedRoads.length===currentRoads.length
      && storedRoads.every((road,index)=>road.id===currentRoads[index].id&&road.label===currentRoads[index].label&&road.category===currentRoads[index].category);
    const ledgerRoads=evidenceMatches
      ? storedRoads.map(road=>({...road,evidence:seen.get(road.id)?.evidence || [],point:seen.get(road.id)?.point}))
      : [...seen.values()];
    for (const road of ledgerRoads) {
      let entry=roadDiscoveryLedger.get(road.id);
      if (!entry) { entry={...road,driven:false,onFoot:false,evidence:[]}; roadDiscoveryLedger.set(road.id,entry); newRoads.push(road); }
      entry.evidence.push(...road.evidence);
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
  const openDiscoveries=new Set([...document.querySelectorAll('#journeyLogList .journey-discovery[open]')]
    .map(details=>details.dataset.journeyKey).filter(Boolean));
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
    details.className='journey-discovery';
    details.dataset.journeyKey=record.logType+':'+journeyIdentity(record);
    details.open=openDiscoveries.has(details.dataset.journeyKey);
    title.textContent=roads.length.toLocaleString()+' new road'+(roads.length===1 ? '' : 's');
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
function roadDiscoveryDisclosureKey(details){
  if(details.id==='roadDiscoveryCard')return 'card';
  const summary=details.querySelector(':scope > summary');
  const label=summary?.dataset.town||summary?.textContent?.split(' · ')[0]?.trim()||'';
  const type=details.classList.contains('road-discovery-group')?'group:'
    :details.classList.contains('road-discovery-county')?'county:':'town:';
  return type+label;
}
function captureRoadDiscoveryDisclosureState(card){
  const open=new Set();
  if(card.open)open.add('card');
  card.querySelectorAll('details[open]').forEach(details=>open.add(roadDiscoveryDisclosureKey(details)));
  return open;
}
function restoreRoadDiscoveryDisclosureState(details,open){details.open=open.has(roadDiscoveryDisclosureKey(details))}
renderRoadDiscovery=function() {
  const card=document.getElementById('roadDiscoveryCard'),count=document.getElementById('roadDiscoveryCount'),summary=document.getElementById('roadDiscoverySummary'),list=document.getElementById('roadDiscoveryList');
  if (!card || !count || !summary || !list) return;
  const openDisclosures=captureRoadDiscoveryDisclosureState(card);
  rebuildRoadDiscoveryLedger();
  const roads=[...roadDiscoveryLedger.values()].sort((a,b)=>a.category.localeCompare(b.category)||a.label.localeCompare(b.label,'en-GB',{numeric:true}));
  card.classList.toggle('hidden',!shouldShowDataDashboard() || !roads.length); if (!roads.length) return;
  const driven=roads.filter(road=>road.driven).length,foot=roads.filter(road=>road.onFoot).length,both=roads.filter(road=>road.driven&&road.onFoot).length;
  count.textContent=roads.length.toLocaleString(); summary.textContent=driven.toLocaleString()+' driven · '+foot.toLocaleString()+' on foot'+(both?' · '+both.toLocaleString()+' in both':''); list.replaceChildren();
  for (const category of ['Motorways','A roads','B roads','Local roads']) {
    const entries=roads.filter(road=>road.category===category); if (!entries.length) continue;
    const group=document.createElement('details'),title=document.createElement('summary');
    group.className='road-discovery-group'; title.textContent=category+' · '+entries.length.toLocaleString(); group.append(title);
    restoreRoadDiscoveryDisclosureState(group,openDisclosures);
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
        restoreRoadDiscoveryDisclosureState(townDetails,openDisclosures);
        for (const road of byTown.get(town).sort((a,b)=>a.label.localeCompare(b.label,'en-GB',{numeric:true}))) { const row=document.createElement('li'),label=document.createElement('strong'),state=document.createElement('span'); label.textContent=road.label; state.textContent=road.driven && road.onFoot ? 'Driven + on foot' : road.driven ? 'Driven' : 'On foot'; row.append(label,state);rows.append(row); }
        townDetails.append(townTitle,rows); group.append(townDetails);
      }
    }
    list.append(group);
  }
  restoreRoadDiscoveryDisclosureState(card,openDisclosures);
  void resolveRoadDiscoveryPlaces();
};

const LOCAL_TOWN_INVENTORY_KEY='roadprints-settlement-inventories-v2';
const SETTLEMENT_INVENTORY_INDEX_URL='settlement-catalogue-v1.json?v=20260913-counts-seven';
const SETTLEMENT_INVENTORY_MANIFEST_URL='settlement-inventory-manifest-v1.json?v=1';
const localTownInventories=new Map(), localTownInventoryStates=new Map();
let settlementInventoryIndexPromise,settlementInventoryManifestPromise,settlementBoundaryLayer,settlementBoundaryMode=false,settlementQueueRunning=0;
const settlementQueue=[],SETTLEMENT_QUEUE_LIMIT=4;
try{for(const [k,v] of Object.entries(JSON.parse(localStorage.getItem(LOCAL_TOWN_INVENTORY_KEY)||'{}')))localTownInventories.set(k,v)}catch(_){}
function saveLocalTownInventories(){try{localStorage.setItem(LOCAL_TOWN_INVENTORY_KEY,JSON.stringify(Object.fromEntries(localTownInventories)))}catch(_){}}
function townKey(town,point){return town+'|'+roadDiscoveryPlaceKey(point)}
function normaliseSettlementName(value){return String(value||'').trim().toLocaleLowerCase('en-GB').replace(/\s+/g,' ')}
function normaliseRoadName(value){return String(value||'').trim().toLocaleLowerCase('en-GB').replace(/\s+/g,' ')}
async function loadSettlementInventoryManifest(){if(!settlementInventoryManifestPromise)settlementInventoryManifestPromise=fetch(SETTLEMENT_INVENTORY_MANIFEST_URL,{cache:'no-cache'}).then(async response=>{if(!response.ok)throw Error('Settlement manifest unavailable');const data=await response.json();return new Map((data.inventories||[]).map(entry=>[entry.code,entry]))});return settlementInventoryManifestPromise}
async function loadSettlementInventoryIndex(){if(!settlementInventoryIndexPromise)settlementInventoryIndexPromise=Promise.all([fetch(SETTLEMENT_INVENTORY_INDEX_URL,{cache:'no-cache'}),loadSettlementInventoryManifest()]).then(async([response,manifest])=>{if(!response.ok)throw Error('Settlement catalogue unavailable');const data=await response.json();return new Map((data.settlements||[]).flatMap(entry=>{const merged={...entry,...(manifest.get(entry.code)||{})};return [merged.name,...(merged.aliases||[])].map(alias=>[normaliseSettlementName(alias),merged])}))});return settlementInventoryIndexPromise}
async function requestSharedTownInventory(entry){
  const response=await fetch(`${API_BASE_URL}/settlement-inventories/request`,{
    method:'POST',
    headers:{'Content-Type':'application/json'},
    body:JSON.stringify({settlement_code:entry.code})
  });
  if(!response.ok)throw Error('Shared settlement inventory request is unavailable');
  return (await response.json()).inventory;
}
async function fetchTownInventory(town){
  const entry=(await loadSettlementInventoryIndex()).get(normaliseSettlementName(town));
  const count=Number(entry?.count);
  if(!entry)return null;
  if(entry.status==='available'&&Number.isFinite(count))return entry;
  if(!entry.code)return null;
  const response=await fetch(`${API_BASE_URL}/settlement-inventories/${encodeURIComponent(entry.code)}`);
  let shared;
  if(response.ok){
    shared=(await response.json()).inventory;
  }else if(response.status===404){
    shared=await requestSharedTownInventory(entry);
  }else{
    throw Error('Shared settlement inventory status is unavailable');
  }
  if(!shared||shared.status==='not_requested')shared=await requestSharedTownInventory(entry);
  if(shared?.status==='ready'&&Number.isFinite(Number(shared.road_count))){
    return {...entry,status:'available',count:Number(shared.road_count),source:'shared'};
  }
  return {...entry,status:shared?.status||'pending',shared};
}
function setTown(town,roads){
 const key=townKey(town,roads[0].point),inv=localTownInventories.get(key),state=localTownInventoryStates.get(key)||'idle';
 document.querySelectorAll('.road-discovery-town summary[data-town]').forEach(x=>{if(x.dataset.town!==town)return;const n=document.createElement('span'),m=document.createElement('button');n.className='road-discovery-town-name';n.textContent=town;m.className='road-discovery-town-metric';m.type='button';if(Number.isFinite(Number(inv?.count))){const discovered=roads.length;m.textContent=discovered+' / '+inv.count+' · '+Math.round(discovered/inv.count*100)+'%';m.disabled=true;const view=document.createElement('button');view.className='town-count-map-quick';view.textContent='Show on map';view.onclick=e=>{e.preventDefault();e.stopPropagation();void showSettlementBoundary(town,inv)};x.replaceChildren(n,m,view)}else{m.textContent=state==='building'?'Inventory building':state==='pending'||state==='not_requested'||state==='unavailable'?'Inventory pending':state==='failed'?'Unavailable':'Loading…';m.disabled=true;x.replaceChildren(n,m)}})}
async function calculateTown(town,roads){
  const key=townKey(town,roads[0].point);
  if(localTownInventoryStates.get(key)==='loading')return;
  localTownInventoryStates.set(key,'loading');
  setTown(town,roads);
  try{
    const inventory=await Promise.race([fetchTownInventory(town),new Promise((_,reject)=>setTimeout(()=>reject(Error('Settlement inventory timed out')),20000))]);
    if(!inventory){
      localTownInventoryStates.set(key,'not_requested');
    }else if(inventory.status==='available'&&Number.isFinite(Number(inventory.count))){
      localTownInventories.set(key,inventory);
      saveLocalTownInventories();
      localTownInventoryStates.delete(key);
    }else{
      localTownInventoryStates.set(key,inventory.status||'not_requested');
    }
  }catch(error){
    console.warn('Settlement inventory status lookup failed:',error);
    localTownInventoryStates.set(key,'failed');
  }
  setTown(town,roads);
}
function queueTownInventory(town,roads){const key=townKey(town,roads[0].point);if(localTownInventories.has(key)||localTownInventoryStates.has(key)||settlementQueue.some(item=>item.key===key))return;settlementQueue.push({town,roads,key});runSettlementQueue()}
function runSettlementQueue(){while(settlementQueueRunning<SETTLEMENT_QUEUE_LIMIT&&settlementQueue.length){const next=settlementQueue.shift();settlementQueueRunning++;calculateTown(next.town,next.roads).finally(()=>{settlementQueueRunning--;runSettlementQueue()})}}
function clearSettlementBoundary(){settlementBoundaryMode=false;settlementBoundaryLayer?.remove();settlementBoundaryLayer=null;document.getElementById('settlementBoundaryReturn')?.remove();if(map){showDefaultUnitedKingdomView();renderMap();renderCanonicalMapLayers();renderCanonicalARoadMapLayers()}refreshARoadBackgroundStatus?.()}
async function loadSettlementBoundary(inventory){
  if(inventory?.code){
    try{
      const sharedResponse=await fetch(`${API_BASE_URL}/settlement-boundaries/${encodeURIComponent(inventory.code)}`);
      if(sharedResponse.ok){
        const shared=await sharedResponse.json();
        if(shared?.boundary)return shared.boundary;
      }
    }catch(error){
      console.warn('Shared settlement boundary lookup failed:',error);
    }
  }
  const response=await fetch('settlement-inventories-v1/'+encodeURIComponent(inventory.code)+'-boundary.geojson');
  if(!response.ok)throw Error('Settlement boundary unavailable');
  return response.json();
}
async function showSettlementBoundary(town,inventory){
  const boundary=await loadSettlementBoundary(inventory);
  settlementBoundaryMode=true;mapRenderingRequested=true;activateRoadprintsScreen('map');
  setTimeout(()=>{initMap();if(!map||!window.L)return;clearReferenceMapLayers();[traceLayer,matchedLayer,creditedLayer,footLayer,liveImportLayer,serviceStationLayer].forEach(layer=>layer?.clearLayers?.());settlementBoundaryLayer?.remove();settlementBoundaryLayer=L.geoJSON(boundary,{style:{color:'#f7c450',weight:3,fillColor:'#f7c450',fillOpacity:.18,interactive:false}}).addTo(map);const bar=document.createElement('div');bar.id='settlementBoundaryReturn';bar.className='journey-focus-bar';bar.innerHTML='<span>Viewing settlement boundary</span><button type="button">Back to progress</button>';mapCard.append(bar);bar.querySelector('button').onclick=()=>{clearSettlementBoundary();activateRoadprintsScreen('progress')};map.fitBounds(settlementBoundaryLayer.getBounds(),{padding:[32,32],maxZoom:13});refreshARoadBackgroundStatus?.()},100)
}
// Resolve local roads against the official ONS boundary service in the
// background; the user-facing ledger changes only once a result is available.
const SETTLEMENT_CHECK_CACHE_KEY='roadprints-settlement-check-v1';
const SETTLEMENT_METADATA_CACHE_KEY='roadprints-settlement-metadata-v2';
const settlementCheckResults=new Map(),settlementCheckPending=new Set(),settlementCheckQueue=[];
const settlementMetadataByName=new Map(),settlementMetadataPending=new Set();
try{for(const [key,value] of Object.entries(JSON.parse(localStorage.getItem(SETTLEMENT_CHECK_CACHE_KEY)||'{}')))if(value&&!value.error)settlementCheckResults.set(key,value)}catch(_){}
try{for(const [key,value] of Object.entries(JSON.parse(localStorage.getItem(SETTLEMENT_METADATA_CACHE_KEY)||'{}')))settlementMetadataByName.set(key,value)}catch(_){}
function saveSettlementCheckResults(){try{localStorage.setItem(SETTLEMENT_CHECK_CACHE_KEY,JSON.stringify(Object.fromEntries(settlementCheckResults)))}catch(_){}}
function saveSettlementMetadata(){try{localStorage.setItem(SETTLEMENT_METADATA_CACHE_KEY,JSON.stringify(Object.fromEntries(settlementMetadataByName)))}catch(_){}}
function rememberSettlementMetadata(settlement){if(!settlement?.name)return;settlementMetadataByName.set(normaliseSettlementName(settlement.name),settlement)}
async function hydrateSettlementMetadata(townEntries){
  for(const entry of townEntries.values())if(entry.settlement)rememberSettlementMetadata(entry.settlement);
  const names=[...townEntries.keys()].filter(name=>!settlementMetadataByName.has(normaliseSettlementName(name))&&!settlementMetadataPending.has(normaliseSettlementName(name)));
  if(!names.length)return;
  names.forEach(name=>settlementMetadataPending.add(normaliseSettlementName(name)));
  try{
    const response=await fetch(API_BASE_URL+'/settlement-metadata',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({names})});
    if(!response.ok)throw Error('Settlement metadata unavailable');
    const data=await response.json();
    const matches=new Map();
    for(const settlement of data.settlements||[]){
      const key=normaliseSettlementName(settlement.name);
      (matches.get(key)||matches.set(key,[]).get(key)).push(settlement);
    }
    for(const name of names){
      const key=normaliseSettlementName(name),found=matches.get(key)||[];
      settlementMetadataByName.set(key,found.length===1?found[0]:{name,county:null});
    }
    saveSettlementMetadata();
  }catch(error){
    console.warn('Settlement county metadata lookup failed:',error);
  }finally{
    names.forEach(name=>settlementMetadataPending.delete(normaliseSettlementName(name)));
    renderRoadDiscovery();
  }
}
let settlementCheckWorkers=0;
function queueSettlementCheck(road){if(settlementCheckResults.has(road.id)||settlementCheckPending.has(road.id))return;settlementCheckPending.add(road.id);settlementCheckQueue.push(road);runSettlementChecks()}
function runSettlementChecks(){while(settlementCheckWorkers<1&&settlementCheckQueue.length){const road=settlementCheckQueue.shift();settlementCheckWorkers++;(async()=>{try{const settlements=new Map();for(const evidence of road.evidence||[]){const response=await fetch(API_BASE_URL+'/settlements-for-geometry',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({geometry:evidence.geometry})});const data=await response.json().catch(()=>({}));if(!response.ok)throw Error(data.detail||'Boundary check unavailable');for(const settlement of data.settlements||[])if(settlement.name)settlements.set(settlement.code||normaliseSettlementName(settlement.name),settlement);await new Promise(resolve=>setTimeout(resolve,250))}const values=[...settlements.values()];settlementCheckResults.set(road.id,{names:values.map(settlement=>settlement.name),settlements:values});saveSettlementCheckResults()}catch(error){settlementCheckResults.set(road.id,{error:String(error.message||error)})}finally{settlementCheckPending.delete(road.id);settlementCheckWorkers--;renderRoadDiscovery();evaluateAchievements();runSettlementChecks()}})()}}
function queueSettlementChecksForLedger(){for(const road of roadDiscoveryLedger.values())if(road.category==='Local roads')queueSettlementCheck(road)}
const renderRoadDiscoveryWithSettlementCheck=renderRoadDiscovery;
function settlementCountyLabel(town,entry){
  const settlement=entry?.settlement||settlementMetadataByName.get(normaliseSettlementName(town));
  return settlement?.county||settlement?.region||settlement?.nation||'Other UK areas';
}
function renderBoundarySettlementLedger(){
  const list=document.getElementById('roadDiscoveryList'),card=document.getElementById('roadDiscoveryCard'),roads=[...roadDiscoveryLedger.values()].filter(road=>road.category==='Local roads');
  if(!list||!card||!roads.length||roads.some(road=>!settlementCheckResults.has(road.id)))return;
  const openDisclosures=captureRoadDiscoveryDisclosureState(card),legacy=[...list.querySelectorAll('.road-discovery-group')].find(group=>group.querySelector('summary')?.textContent.startsWith('Local roads'));
  if(!legacy)return;
  const towns=new Map(),unresolved=[];
  for(const road of roads){
    const result=settlementCheckResults.get(road.id);
    if(result.error){unresolved.push(road);continue}
    const metadataByName=new Map((result.settlements||[]).map(settlement=>[settlement.name,settlement]));
    for(const town of result.names||[]){
      const entry=towns.get(town)||{roads:[],settlement:metadataByName.get(town)};
      if(!entry.settlement&&metadataByName.get(town))entry.settlement=metadataByName.get(town);
      entry.roads.push(road);towns.set(town,entry);
    }
  }
  void hydrateSettlementMetadata(towns);
  const group=document.createElement('details'),title=document.createElement('summary');
  group.className='road-discovery-group';title.textContent='Local roads · '+roads.length.toLocaleString();group.append(title);restoreRoadDiscoveryDisclosureState(group,openDisclosures);
  const counties=new Map();
  for(const [town,entry] of towns){
    const county=settlementCountyLabel(town,entry);
    (counties.get(county)||counties.set(county,[]).get(county)).push([town,entry]);
  }
  const townRows=[];
  for(const county of [...counties.keys()].sort((a,b)=>a.localeCompare(b,'en-GB'))){
    const countyEntries=counties.get(county),countyDetails=document.createElement('details'),countyTitle=document.createElement('summary');
    countyDetails.className='road-discovery-county';
    const countyRoadCount=countyEntries.reduce((total,[,entry])=>total+entry.roads.length,0);
    countyTitle.textContent=county+' · '+countyEntries.length+' settlement'+(countyEntries.length===1?'':'s')+' · '+countyRoadCount.toLocaleString()+' roads';
    countyDetails.append(countyTitle);restoreRoadDiscoveryDisclosureState(countyDetails,openDisclosures);
    for(const [town,entry] of countyEntries.sort(([a],[b])=>a.localeCompare(b,'en-GB'))){
      const entries=entry.roads,townDetails=document.createElement('details'),townTitle=document.createElement('summary'),rows=document.createElement('ul');
      townDetails.className='road-discovery-town';townTitle.dataset.town=town;townTitle.textContent=town+' · '+entries.length.toLocaleString();restoreRoadDiscoveryDisclosureState(townDetails,openDisclosures);
      for(const road of entries.sort((a,b)=>a.label.localeCompare(b.label,'en-GB'))){
        const row=document.createElement('li'),label=document.createElement('strong'),state=document.createElement('span');
        label.textContent=road.label;state.textContent=road.driven&&road.onFoot?'Driven + on foot':road.driven?'Driven':'On foot';row.append(label,state);rows.append(row);
      }
      townDetails.append(townTitle,rows);countyDetails.append(townDetails);townRows.push([town,entries]);
    }
    group.append(countyDetails);
  }
  if(unresolved.length){
    const detail=document.createElement('details'),summary=document.createElement('summary'),rows=document.createElement('ul');
    detail.className='road-discovery-town';summary.textContent='Unresolved local roads · '+unresolved.length;restoreRoadDiscoveryDisclosureState(detail,openDisclosures);
    for(const road of unresolved){const row=document.createElement('li');row.textContent=road.label;rows.append(row)}
    detail.append(summary,rows);group.append(detail);
  }
  legacy.replaceWith(group);restoreRoadDiscoveryDisclosureState(card,openDisclosures);
  for(const [town,entries] of townRows)setTown(town,entries);
}
renderRoadDiscovery=function(){renderRoadDiscoveryWithSettlementCheck();queueSettlementChecksForLedger();renderBoundarySettlementLedger()};
function refreshBoundarySettlementMetrics(){const towns=new Map();for(const road of roadDiscoveryLedger.values())if(road.category==='Local roads'){const result=settlementCheckResults.get(road.id);if(!result||result.error)continue;for(const town of result.names)(towns.get(town)||towns.set(town,[]).get(town)).push(road)}for(const [town,roads] of towns){setTown(town,roads);queueTownInventory(town,roads)}}
const renderRoadDiscoveryWithBoundaryMetrics=renderRoadDiscovery;
renderRoadDiscovery=function(){renderRoadDiscoveryWithBoundaryMetrics();refreshBoundarySettlementMetrics()};
