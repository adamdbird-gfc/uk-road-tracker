let journeys = [];
let diagnostics = {};
let currentTimelineJson = null;
let currentTimelineFileInfo = null;
let sanitisedDiagnosticCopy = null;
let map = null;
let traceLayer = null;
let matchedLayer = null;
let creditedLayer = null;
let mapLayerControl = null;
let ignoredJourneys = [];
let importMode = null;
let easyImportPaused = false;
let easyImportRunning = false;
let trackingSessionId = 0;
let distanceUnit = 'miles';
let onboardingMode = null;
const manualMotorwayRefs = new Set();
const manualCoverageByRef = new Map();
const persistedCoverageByRef = new Map();
const persistedManualRefs = new Set();
let persistedDataStartMs = null;
let persistedDataEndMs = null;
let persistedSavedAt = null;
let persistedLegacyCutoffMs = null;
const persistedProcessedJourneyIds = new Set();
const persistedSeenJourneyIds = new Set();
let persistedSeenJourneyTrackingStarted = true;
const persistedImportedFileHashes = new Set();
let persistedFileHashTrackingStarted = true;
const persistedMotorwayContributionsByJourney = new Map();
let persistedMileageHistoryComplete = true;
let localSaveTimer = null;
const canonicalRequestedRefs = new Set();
let refinementRoadRef = null;
let refinementEditMode = null;
let refinementUndoStack = [];
let refinementChunks = [];
let refinementChunkIndex = 0;
let canonicalReferenceLayer = null;
let canonicalCoverageLayer = null;
let canonicalUncoveredLayer = null;
const canonicalRoads = new Map();
let canonicalLoadQueueRunning = false;
const API_BASE_URL = 'https://uk-road-tracker-api.onrender.com';

const fileInput = document.getElementById('timelineFile');
const fileStatus = document.getElementById('fileStatus');
const summaryCard = document.getElementById('summaryCard');
const mapCard = document.getElementById('mapCard');
const nextCard = document.getElementById('nextCard');
const journeyList = document.getElementById('journeyList');
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
const motorwayCard = document.getElementById('motorwayCard');
const motorwayList = document.getElementById('motorwayList');
const motorwaysDiscovered = document.getElementById('motorwaysDiscovered');
const unitMiles = document.getElementById('unitMiles');
const unitKm = document.getElementById('unitKm');
const canonicalMotorwayCard = document.getElementById('canonicalMotorwayCard');
const canonicalMotorwayList = document.getElementById('canonicalMotorwayList');
const canonicalRoadsReady = document.getElementById('canonicalRoadsReady');
const canonicalMotorwayStatus = document.getElementById('canonicalMotorwayStatus');
const canonicalRetry = document.getElementById('canonicalRetry');
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
const diagnosticCard = document.getElementById('diagnosticCard');
const createDiagnosticCopyButton = document.getElementById('createDiagnosticCopy');
const reviewDiagnosticCopyButton = document.getElementById('reviewDiagnosticCopy');
const downloadDiagnosticCopyButton = document.getElementById('downloadDiagnosticCopy');
const deleteDiagnosticCopyButton = document.getElementById('deleteDiagnosticCopy');
const diagnosticCopyStatus = document.getElementById('diagnosticCopyStatus');
const diagnosticPreview = document.getElementById('diagnosticPreview');
const diagnosticPreviewContent = document.getElementById('diagnosticPreviewContent');
const SANITISED_DIAGNOSTIC_STORAGE_KEY = 'roadprints.sanitisedTimelineDiagnostic.v1';
const SANITISED_DIAGNOSTIC_VERSION = 1;
const DIAGNOSTIC_DB_NAME = 'roadprints-diagnostics';
const DIAGNOSTIC_DB_VERSION = 1;
const DIAGNOSTIC_STORE_NAME = 'sanitised-copies';
const DIAGNOSTIC_RECORD_KEY = 'latest';
const MAX_DIAGNOSTIC_SEGMENTS = 250;
const MAX_DIAGNOSTIC_PREVIEW_CHARS = 40000;

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
const LOCAL_PROGRESS_KEY = 'uk-road-tracker-progress-v1';
let canonicalCache = null;
let canonicalCachePromise = null;


const MOTORWAY_CORRIDOR_CELL_M = 100;
const MOTORWAY_SAMPLE_SPACING_M = 25;

function localProgressRoadCount() {
  return [...persistedCoverageByRef.values()].filter(ids=>ids.size).length;
}

function updateLocalProgressNotice() {
  const roadCount=localProgressRoadCount();
  const hasProgress=roadCount>0 || persistedManualRefs.size>0;
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
    `${roadCount} motorway${roadCount===1?'':'s'} with saved coverage · ${range} · saved ${savedLabel}.`;
}

function loadLocalProgress() {
  try {
    const raw=localStorage.getItem(LOCAL_PROGRESS_KEY);
    if (!raw) return;
    const saved=JSON.parse(raw);
    if (!saved || saved.version!==1 || saved.canonicalVersion!==CANONICAL_CACHE_VERSION) return;

    for (const [id,ids] of Object.entries(saved.coverage || {})) {
      if (Array.isArray(ids)) persistedCoverageByRef.set(id,new Set(ids.map(Number).filter(Number.isInteger)));
    }
    for (const id of saved.manualMotorways || []) persistedManualRefs.add(String(id));
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
    for (const id of persistedProcessedJourneyIds) persistedSeenJourneyIds.add(id);
    const hasFileHashState =
      saved.fileHashTrackingStarted === true ||
      Array.isArray(saved.importedFileHashes);
    persistedFileHashTrackingStarted =
      hasFileHashState || persistedProcessedJourneyIds.size === 0;
    for (const hash of saved.importedFileHashes || []) {
      if (typeof hash==='string' && hash) persistedImportedFileHashes.add(hash);
    }
    for (const [journeyId,contributions] of Object.entries(saved.motorwayContributionsByJourney || {})) {
      if (!journeyId || !contributions || typeof contributions!=='object') continue;
      const clean={};
      for (const [roadId,distanceM] of Object.entries(contributions)) {
        const value=Number(distanceM);
        if (roadId && Number.isFinite(value) && value>0) clean[roadId]=value;
      }
      if (Object.keys(clean).length) persistedMotorwayContributionsByJourney.set(journeyId,clean);
    }
    persistedMileageHistoryComplete=typeof saved.mileageHistoryComplete==='boolean'
      ? saved.mileageHistoryComplete
      : persistedProcessedJourneyIds.size===0;
    if (saved.distanceUnit==='km') distanceUnit='km';
  } catch (err) {
    console.warn('Saved local progress could not be read:',err);
  }
}

function saveLocalProgressNow() {
  try {
    for (const road of canonicalRoads.values()) {
      if (road.status==='ready') {
        persistedCoverageByRef.set(road.id,new Set(road.coveredAnchorIds));
      }
    }

    const coverage={};
    for (const [id,ids] of persistedCoverageByRef) {
      if (ids.size) coverage[id]=[...ids].sort((a,b)=>a-b);
    }

    persistedSavedAt=new Date().toISOString();
    localStorage.setItem(LOCAL_PROGRESS_KEY,JSON.stringify({
      version:1,
      canonicalVersion:CANONICAL_CACHE_VERSION,
      savedAt:persistedSavedAt,
      distanceUnit,
      dataStartMs:persistedDataStartMs,
      dataEndMs:persistedDataEndMs,
      legacyCutoffMs:persistedLegacyCutoffMs,
      processedJourneyIds:[...persistedProcessedJourneyIds].sort(),
      seenJourneyTrackingStarted:persistedSeenJourneyTrackingStarted,
      seenJourneyIds:[...persistedSeenJourneyIds].sort(),
      fileHashTrackingStarted:persistedFileHashTrackingStarted,
      importedFileHashes:[...persistedImportedFileHashes].sort(),
      motorwayContributionsByJourney:Object.fromEntries(
        [...persistedMotorwayContributionsByJourney.entries()].sort(([a],[b])=>a.localeCompare(b))
      ),
      mileageHistoryComplete:persistedMileageHistoryComplete,
      manualMotorways:[...persistedManualRefs].sort(motorwayRefSort),
      coverage
    }));
    updateLocalProgressNotice();
  } catch (err) {
    console.warn('Local progress could not be saved:',err);
  }
}

function scheduleLocalProgressSave() {
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

function journeyTimestampMs(journey) {
  const end=Date.parse(journey?.end || '');
  if (Number.isFinite(end)) return end;
  const start=Date.parse(journey?.start || '');
  return Number.isFinite(start) ? start : null;
}

function journeyIdentity(journey) {
  return journey?.importId || journeyFingerprint(journey);
}

function recordJourneySeen(journey) {
  const id=journeyIdentity(journey);
  if (id) persistedSeenJourneyIds.add(id);
}

function journeyWasPreviouslyImported(journey) {
  const id=journeyIdentity(journey);
  if (persistedProcessedJourneyIds.has(id)) return true;
  const timeMs=journeyTimestampMs(journey);
  return persistedLegacyCutoffMs!==null && timeMs!==null && timeMs<=persistedLegacyCutoffMs;
}

function motorwayContributionsForJourney(journey) {
  const contributions={};
  for (const feature of journey?.motorwayGeoJson?.features || []) {
    const roadId=motorwayFeatureId(feature);
    const distanceM=Number(feature?.properties?.distance_m || 0);
    if (!roadId || !Number.isFinite(distanceM) || distanceM<=0) continue;
    contributions[roadId]=(contributions[roadId] || 0)+distanceM;
  }
  return contributions;
}

function recordJourneyProcessed(journey) {
  const id=journeyIdentity(journey);
  if (id) {
    persistedProcessedJourneyIds.add(id);
    const contributions=motorwayContributionsForJourney(journey);
    if (Object.keys(contributions).length) {
      persistedMotorwayContributionsByJourney.set(id,contributions);
    } else {
      persistedMotorwayContributionsByJourney.delete(id);
    }
  }
  const start=Date.parse(journey?.start || '');
  const end=Date.parse(journey?.end || '');
  if (Number.isFinite(start)) {
    persistedDataStartMs=persistedDataStartMs===null ? start : Math.min(persistedDataStartMs,start);
  }
  if (Number.isFinite(end)) {
    persistedDataEndMs=persistedDataEndMs===null ? end : Math.max(persistedDataEndMs,end);
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

function clearLocalProgress() {
  if (!window.confirm('Delete all motorway progress saved on this device?')) return;
  localStorage.removeItem(LOCAL_PROGRESS_KEY);
  persistedCoverageByRef.clear();
  persistedManualRefs.clear();
  persistedDataStartMs=null;
  persistedDataEndMs=null;
  persistedSavedAt=null;
  persistedLegacyCutoffMs=null;
  persistedProcessedJourneyIds.clear();
  persistedSeenJourneyIds.clear();
  persistedSeenJourneyTrackingStarted=true;
  persistedImportedFileHashes.clear();
  persistedFileHashTrackingStarted=true;
  persistedMotorwayContributionsByJourney.clear();
  persistedMileageHistoryComplete=true;
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
      label.className='manual-motorway-option';

      const checkbox=document.createElement('input');
      checkbox.type='checkbox';
      checkbox.value=id;
      checkbox.checked=manualMotorwayRefs.has(id);
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
      options.append(label);
    }

    group.append(heading,options);
    manualMotorwayList.append(group);
  }
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
  manualCoverageByRef.clear();
  canonicalRequestedRefs.clear();
  canonicalRoads.clear();
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
  canonicalMotorwayList.innerHTML = '';
  journeyCount.textContent = '0';
  pointCount.textContent = '0';
  selectedCount.textContent = '0';
  dataDateRange.querySelector('span').textContent = 'Date range unavailable';
  ignoredCount.textContent = '0';
  motorwaysDiscovered.textContent = '0';
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
  easyProgressText.textContent = 'Waiting…';
  updateEasyImportPauseButton();

  importModeCard.classList.add('hidden');
  easyProgress.classList.add('hidden');
  ignoredCard.classList.add('hidden');
  summaryCard.classList.add('hidden');
  motorwayCard.classList.add('hidden');
  canonicalMotorwayCard.classList.add('hidden');
  mapCard.classList.add('hidden');
  nextCard.classList.add('hidden');
  refinementPanel.classList.add('hidden');
  mapCard.classList.remove('refinement-active');

  for (const layer of [
    traceLayer, matchedLayer, creditedLayer, canonicalReferenceLayer,
    canonicalCoverageLayer, canonicalUncoveredLayer
  ]) {
    if (layer) layer.clearLayers();
  }

  if (map) {
    map.setView([54.5, -3], 5);
    requestAnimationFrame(() => map.invalidateSize(true));
  }
}

async function showSavedProgress() {
  if (!persistedCoverageByRef.size && !persistedManualRefs.size) return;

  resetTrackingSession();
  onboardingMode='saved';
  onboardingCard.classList.add('hidden');
  dataSourceCard.classList.add('hidden');
  manualMotorwayCard.classList.add('hidden');
  closeSavedProgress.classList.remove('hidden');
  mapTitle.textContent='Your saved Roadprints progress';
  mapIntro.textContent='This is the motorway coverage saved on this device. Return to the start to import new Timeline data or make manual changes.';
  mapCard.classList.remove('hidden');
  nextCard.classList.add('hidden');

  await ensureLeaflet();
  initMap();
  renderMap();
  requestAnimationFrame(()=>map?.invalidateSize(true));
}

async function showDataSourceChoice(mode) {
  resetTrackingSession();
  onboardingMode = mode;
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
  mapTitle.textContent = '3. Preview';
  mapIntro.textContent = 'Selected motorways are shown in blue as complete. In the next enhancement, you will be able to refine these into the individual sections you have driven.';
  mapCard.classList.remove('hidden');
  nextCard.classList.add('hidden');
  renderManualMotorwayOptions();
  await ensureLeaflet();
  initMap();
  renderMap();
}

function returnToOnboarding() {
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
    for (const ref of Object.keys(MOTORWAY_LENGTH_KM)) manualMotorwayRefs.add(ref);
    for (const ref of Object.keys(NI_MOTORWAY_LENGTH_KM)) manualMotorwayRefs.add(`NI:${ref}`);
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
document.getElementById('viewSavedProgress').addEventListener('click', showSavedProgress);
document.getElementById('clearLocalProgress').addEventListener('click', clearLocalProgress);
closeSavedProgress.addEventListener('click', returnToOnboarding);
loadLocalProgress();
unitMiles.classList.toggle('active',distanceUnit==='miles');
unitKm.classList.toggle('active',distanceUnit==='km');
unitMiles.setAttribute('aria-pressed',String(distanceUnit==='miles'));
unitKm.setAttribute('aria-pressed',String(distanceUnit==='km'));
renderManualMotorwayOptions();
updateLocalProgressNotice();
loadSanitisedDiagnosticCopy().finally(updateDiagnosticCopyUi);
updateDiagnosticCopyUi();

fileInput.addEventListener('change', async () => {
  const file = fileInput.files?.[0];
  if (!file) return;

  resetOutput();
  status(`Reading ${file.name} (${formatBytes(file.size)})…`);

  try {
    const text = await file.text();
    status(`Read complete. Identifying this file…`);
    const sourceFileHash = await timelineFileHash(text);
    const fileWasPreviouslySeen = persistedImportedFileHashes.has(sourceFileHash);
    const hadReliableFileHashHistory = persistedFileHashTrackingStarted;
    status(`File identified. Parsing JSON…`);
    await yieldToBrowser();

    const json = JSON.parse(text);
    currentTimelineJson = json;
    currentTimelineFileInfo = {
      size: file.size,
      type: file.type || 'application/json',
      hash: sourceFileHash
    };
    diagnosticCard.classList.remove('hidden');
    updateDiagnosticCopyUi();
    status(`JSON parsed. Inspecting Timeline structure…`);
    await yieldToBrowser();

    const result = extractVehicleJourneys(json);
    const allJourneys = result.journeys;
    diagnostics = result.diagnostics;

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
    diagnostics.journeysReadyForMatching=candidateJourneys.length;

    ignoredJourneys = candidateJourneys.filter(j => j.pathPointCount < 2);
    journeys = candidateJourneys.filter(j => j.pathPointCount >= 2);
    diagnostics.usableJourneys = journeys.length;
    diagnostics.ignoredSparseJourneys = ignoredJourneys.length;

    for (const journey of ignoredJourneys) recordJourneyProcessed(journey);
    if (ignoredJourneys.length) scheduleLocalProgressSave();

    showDiagnostics(file.name);
    renderIgnoredJourneys();

    if (!diagnostics.passengerVehicleActivities) {
      throw new Error(
        `Diagnostic result: ${diagnostics.semanticSegments.toLocaleString()} semantic segments were found, ` +
        `but 0 IN_PASSENGER_VEHICLE activities were detected.`
      );
    }

    if (!journeys.length) {
      fileStatus.className = 'muted';
      fileStatus.textContent =
        `${file.name} inspected successfully. No journeys currently need road matching. ` +
        `${diagnostics.previouslyImportedJourneys.toLocaleString()} successfully processed journey` +
        `${diagnostics.previouslyImportedJourneys===1?' was':'s were'} safely skipped.`;
      return;
    }

    journeys.forEach(j => j.selected = true);
    await ensureLeaflet();
    importMode = null;
    summaryCard.classList.add('hidden');
    mapCard.classList.add('hidden');
    nextCard.classList.add('hidden');
  importModeCard.classList.add('hidden');
  easyProgress.classList.add('hidden');
  ignoredCard.classList.add('hidden');
  motorwayCard.classList.add('hidden');
  canonicalMotorwayCard.classList.add('hidden');
    importModeCard.classList.remove('hidden');
    easyProgress.classList.add('hidden');
  } catch (err) {
    summaryCard.classList.add('hidden');
    mapCard.classList.add('hidden');
    nextCard.classList.add('hidden');
    fileStatus.className = 'error';
    const prefix = Object.keys(diagnostics).length ? diagnosticText() + '\n\n' : '';
    fileStatus.textContent = prefix + (err.message || String(err));
  }
});

document.getElementById('selectAll').addEventListener('click', () => {
  journeys.forEach(j => j.selected = true);
  syncCheckboxes();
  renderMap();
  updateSelectedCount();
});

document.getElementById('selectNone').addEventListener('click', () => {
  journeys.forEach(j => j.selected = false);
  syncCheckboxes();
  renderMap();
  updateSelectedCount();
});

document.getElementById('fitMap').addEventListener('click', fitSelected);
document.getElementById('clearMatches').addEventListener('click', clearMatchedRoads);
document.getElementById('easyImport').addEventListener('click', startEasyImport);
document.getElementById('detailedImport').addEventListener('click', startDetailedImport);
document.getElementById('stopEasyImport').addEventListener('click', () => {
  if (!easyImportRunning) return;

  easyImportPaused = !easyImportPaused;
  updateEasyImportPauseButton();

  if (!easyImportPaused) {
    easyProgressText.textContent = easyProgressText.dataset.resumeText || 'Resuming…';
  }
});
unitMiles.addEventListener('click', () => setDistanceUnit('miles'));
unitKm.addEventListener('click', () => setDistanceUnit('km'));
canonicalRetry.addEventListener('click', retryCanonicalRoads);
createDiagnosticCopyButton.addEventListener('click', () => createSanitisedDiagnosticCopy());
reviewDiagnosticCopyButton.addEventListener('click', () => {
  if (!sanitisedDiagnosticCopy) return;
  renderDiagnosticPreview();
  diagnosticPreview.open = true;
  diagnosticPreview.scrollIntoView({behavior: 'smooth', block: 'nearest'});
});
downloadDiagnosticCopyButton.addEventListener('click', downloadSanitisedDiagnosticCopy);
deleteDiagnosticCopyButton.addEventListener('click', () => deleteSanitisedDiagnosticCopy());

function openDiagnosticDatabase() {
  return new Promise((resolve, reject) => {
    if (!globalThis.indexedDB) {
      reject(new Error('This browser does not provide IndexedDB storage.'));
      return;
    }

    const request = indexedDB.open(DIAGNOSTIC_DB_NAME, DIAGNOSTIC_DB_VERSION);
    request.onerror = () => reject(request.error || new Error('Diagnostic storage could not be opened.'));
    request.onupgradeneeded = () => {
      const database = request.result;
      if (!database.objectStoreNames.contains(DIAGNOSTIC_STORE_NAME)) {
        database.createObjectStore(DIAGNOSTIC_STORE_NAME);
      }
    };
    request.onsuccess = () => resolve(request.result);
  });
}

async function diagnosticDatabaseOperation(mode, operation) {
  const database = await openDiagnosticDatabase();
  try {
    return await new Promise((resolve, reject) => {
      const transaction = database.transaction(DIAGNOSTIC_STORE_NAME, mode);
      const store = transaction.objectStore(DIAGNOSTIC_STORE_NAME);
      const request = operation(store);
      request.onerror = () => reject(request.error || new Error('Diagnostic storage operation failed.'));
      request.onsuccess = () => resolve(request.result);
      transaction.onabort = () => reject(transaction.error || new Error('Diagnostic storage transaction was aborted.'));
    });
  } finally {
    database.close();
  }
}

function readDiagnosticCopyFromDatabase() {
  return diagnosticDatabaseOperation('readonly', store => store.get(DIAGNOSTIC_RECORD_KEY));
}

function writeDiagnosticCopyToDatabase(copy) {
  return diagnosticDatabaseOperation('readwrite', store => store.put(copy, DIAGNOSTIC_RECORD_KEY));
}

function deleteDiagnosticCopyFromDatabase() {
  return diagnosticDatabaseOperation('readwrite', store => store.delete(DIAGNOSTIC_RECORD_KEY));
}

async function loadSanitisedDiagnosticCopy() {
  try {
    sanitisedDiagnosticCopy = await readDiagnosticCopyFromDatabase() || null;

    // Remove any earlier small localStorage copy after migration to IndexedDB.
    const legacy = localStorage.getItem(SANITISED_DIAGNOSTIC_STORAGE_KEY);
    if (!sanitisedDiagnosticCopy && legacy) {
      sanitisedDiagnosticCopy = JSON.parse(legacy);
      await writeDiagnosticCopyToDatabase(sanitisedDiagnosticCopy);
    }
    if (legacy) localStorage.removeItem(SANITISED_DIAGNOSTIC_STORAGE_KEY);
  } catch (error) {
    sanitisedDiagnosticCopy = null;
    console.warn('Privacy-safe diagnostic copy could not be loaded:', error);
  }
}

function updateDiagnosticCopyUi(message = '') {
  const hasCopy = Boolean(sanitisedDiagnosticCopy);
  diagnosticCard.classList.toggle('hidden', !currentTimelineJson && !hasCopy);
  reviewDiagnosticCopyButton.disabled = !hasCopy;
  downloadDiagnosticCopyButton.disabled = !hasCopy;
  deleteDiagnosticCopyButton.disabled = !hasCopy;
  diagnosticPreview.classList.toggle('hidden', !hasCopy);

  if (message) {
    diagnosticCopyStatus.textContent = message;
    return;
  }
  if (!hasCopy) {
    diagnosticCopyStatus.textContent = currentTimelineJson
      ? 'No privacy-safe copy is stored. Creating one is optional.'
      : 'No privacy-safe copy is stored.';
    return;
  }

  const count = Number(sanitisedDiagnosticCopy?.sanitisation?.segmentsRetained || 0);
  const created = new Date(sanitisedDiagnosticCopy.createdAt);
  const createdText = Number.isNaN(created.getTime())
    ? 'previously'
    : created.toLocaleString();
  diagnosticCopyStatus.textContent =
    `Privacy-safe copy stored locally (${count.toLocaleString()} representative segments, created ${createdText}).`;
}

function evenlySample(items, limit) {
  if (!Array.isArray(items) || items.length <= limit) return Array.isArray(items) ? [...items] : items;
  const sampled = [];
  for (let index = 0; index < limit; index++) {
    sampled.push(items[Math.round(index * (items.length - 1) / (limit - 1))]);
  }
  return sampled;
}

function prepareDiagnosticSource(value, key = '') {
  if (Array.isArray(value)) {
    const items = key === 'semanticSegments'
      ? evenlySample(value, MAX_DIAGNOSTIC_SEGMENTS)
      : value;
    return items.map(item => prepareDiagnosticSource(item));
  }
  if (!value || typeof value !== 'object') return value;

  const copy = {};
  for (const [childKey, childValue] of Object.entries(value)) {
    copy[childKey] = prepareDiagnosticSource(childValue, childKey);
  }
  return copy;
}

function collectDiagnosticTimestamps(value, timestamps = []) {
  if (Array.isArray(value)) {
    value.forEach(item => collectDiagnosticTimestamps(item, timestamps));
    return timestamps;
  }
  if (!value || typeof value !== 'object') return timestamps;

  for (const [key, child] of Object.entries(value)) {
    if (
      typeof child === 'string' &&
      /(time|date|start|end)/i.test(key) &&
      /^\d{4}-\d{2}-\d{2}T/.test(child)
    ) {
      const timestamp = Date.parse(child);
      if (Number.isFinite(timestamp)) timestamps.push(timestamp);
    }
    collectDiagnosticTimestamps(child, timestamps);
  }
  return timestamps;
}

function sanitiseDiagnosticValue(value, key, earliestTimestamp) {
  if (Array.isArray(value)) {
    return value.map(item => sanitiseDiagnosticValue(item, key, earliestTimestamp));
  }
  if (value && typeof value === 'object') {
    const result = {};
    for (const [childKey, childValue] of Object.entries(value)) {
      result[childKey] = sanitiseDiagnosticValue(childValue, childKey, earliestTimestamp);
    }
    return result;
  }

  const coordinateKey = /(^|_)(lat|lng|lon|latitude|longitude)(e7)?$|latlng|coordinate/i;
  const identifyingKey = /(address|placeid|place_id|deviceid|device_id|photo|name$|url$|uri$|identifier|account|email)/i;
  const timestampKey = /(time|date|start|end)/i;

  if (coordinateKey.test(key)) {
    if (typeof value === 'number') return 0;
    if (typeof value === 'string') return value.startsWith('geo:') ? 'geo:0.000000,0.000000' : '[redacted coordinate]';
  }
  if (identifyingKey.test(key) && value !== null && value !== '') {
    return '[redacted]';
  }
  if (
    typeof value === 'string' &&
    timestampKey.test(key) &&
    /^\d{4}-\d{2}-\d{2}T/.test(value)
  ) {
    const timestamp = Date.parse(value);
    if (Number.isFinite(timestamp) && Number.isFinite(earliestTimestamp)) {
      return new Date(Date.UTC(2000, 0, 1) + (timestamp - earliestTimestamp)).toISOString();
    }
  }
  if (typeof value === 'string') {
    return value
      .replace(/geo:-?\d+(?:\.\d+)?,-?\d+(?:\.\d+)?/gi, 'geo:0.000000,0.000000')
      .replace(/https?:\/\/\S+/gi, '[redacted URL]');
  }
  return value;
}

function countSemanticSegments(value) {
  if (Array.isArray(value)) return value.reduce((total, item) => total + countSemanticSegments(item), 0);
  if (!value || typeof value !== 'object') return 0;
  let total = 0;
  for (const [key, child] of Object.entries(value)) {
    if (key === 'semanticSegments' && Array.isArray(child)) total += child.length;
    else total += countSemanticSegments(child);
  }
  return total;
}

async function createSanitisedDiagnosticCopy() {
  if (!currentTimelineJson) {
    updateDiagnosticCopyUi('Choose and inspect a Timeline JSON file before creating a privacy-safe copy.');
    return;
  }

  try {
    const prepared = prepareDiagnosticSource(currentTimelineJson);
    const timestamps = collectDiagnosticTimestamps(prepared);
    let earliestTimestamp = null;
    for (const timestamp of timestamps) {
      earliestTimestamp = earliestTimestamp === null
        ? timestamp
        : Math.min(earliestTimestamp, timestamp);
    }
    const sanitisedData = sanitiseDiagnosticValue(prepared, '', earliestTimestamp);
    const originalSegments = countSemanticSegments(currentTimelineJson);
    const retainedSegments = countSemanticSegments(sanitisedData);

    sanitisedDiagnosticCopy = {
      format: 'roadprints-sanitised-timeline-diagnostic',
      version: SANITISED_DIAGNOSTIC_VERSION,
      createdAt: new Date().toISOString(),
      source: {
        originalSizeBytes: currentTimelineFileInfo?.size || null,
        originalMimeType: currentTimelineFileInfo?.type || 'application/json'
      },
      sanitisation: {
        coordinates: 'replaced with zero values',
        timestamps: 'shifted so the earliest retained timestamp begins on 2000-01-01',
        identifiers: 'names, addresses, URLs and identifying references redacted',
        originalSegments,
        segmentsRetained: retainedSegments,
        maximumSegments: MAX_DIAGNOSTIC_SEGMENTS
      },
      importDiagnostics: {...diagnostics},
      data: sanitisedData
    };

    await writeDiagnosticCopyToDatabase(sanitisedDiagnosticCopy);
    localStorage.removeItem(SANITISED_DIAGNOSTIC_STORAGE_KEY);
    renderDiagnosticPreview();
    updateDiagnosticCopyUi(
      `Privacy-safe copy created and stored on this device. ${retainedSegments.toLocaleString()} of ${originalSegments.toLocaleString()} Timeline segments were retained for analysis.`
    );
  } catch (error) {
    sanitisedDiagnosticCopy = null;
    updateDiagnosticCopyUi(
      `The privacy-safe copy could not be stored: ${error?.message || String(error)}`
    );
  }
}

function renderDiagnosticPreview() {
  if (!sanitisedDiagnosticCopy) {
    diagnosticPreviewContent.textContent = '';
    return;
  }
  const formatted = JSON.stringify(sanitisedDiagnosticCopy, null, 2);
  diagnosticPreviewContent.textContent = formatted.length > MAX_DIAGNOSTIC_PREVIEW_CHARS
    ? formatted.slice(0, MAX_DIAGNOSTIC_PREVIEW_CHARS) + '\n\n… Preview shortened. Download the JSON to review the complete sanitised sample.'
    : formatted;
}

function downloadSanitisedDiagnosticCopy() {
  if (!sanitisedDiagnosticCopy) return;
  const blob = new Blob(
    [JSON.stringify(sanitisedDiagnosticCopy, null, 2)],
    {type: 'application/json'}
  );
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = `roadprints-sanitised-timeline-${new Date().toISOString().slice(0, 10)}.json`;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
  updateDiagnosticCopyUi('Privacy-safe JSON downloaded. Review it before sharing, just as you would any exported file.');
}

async function deleteSanitisedDiagnosticCopy() {
  if (!sanitisedDiagnosticCopy) return;
  if (!window.confirm('Delete the privacy-safe diagnostic copy stored on this device?')) return;

  try {
    await deleteDiagnosticCopyFromDatabase();
    localStorage.removeItem(SANITISED_DIAGNOSTIC_STORAGE_KEY);
    sanitisedDiagnosticCopy = null;
    diagnosticPreview.open = false;
    diagnosticPreviewContent.textContent = '';
    updateDiagnosticCopyUi('Privacy-safe diagnostic copy deleted from this device.');
  } catch (error) {
    updateDiagnosticCopyUi(
      `The privacy-safe copy could not be deleted: ${error?.message || String(error)}`
    );
  }
}

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
  nextCard.classList.add('hidden');
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
    summaryStat(fmt(diagnostics.newPassengerVehicleJourneys), 'genuinely new'),
    summaryStat(fmt(diagnostics.previouslySeenUnmatchedJourneys), 'available to retry'),
    summaryStat(fmt(diagnostics.previouslyImportedJourneys), 'already imported'),
    summaryStat(fmt(diagnostics.ignoredSparseJourneys), 'unable to use')
  );

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
  fileStatus.append(heading, stats, explanation, details);
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
  const genuinelyNew = Number(diagnostics.newPassengerVehicleJourneys || 0);
  const retryable = Number(diagnostics.previouslySeenUnmatchedJourneys || 0);
  const previous = Number(diagnostics.previouslyImportedJourneys || 0);
  const ignored = Number(diagnostics.ignoredSparseJourneys || 0);
  const parts = [
    `${fmt(ready)} car journey${ready === 1 ? ' is' : 's are'} ready for road matching.`
  ];
  parts.push(`${fmt(genuinelyNew)} ${genuinelyNew === 1 ? 'is a genuinely new journey' : 'are genuinely new journeys'}.`);
  if (retryable) {
    parts.push(
      `${fmt(retryable)} ${retryable === 1 ? 'was seen before but was not successfully matched' : 'were seen before but were not successfully matched'} and can be retried.`
    );
  }
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
    ['Car journey entries', fmt(diagnostics.passengerVehicleActivities), 'Movement entries Google identified as travel in a passenger vehicle.'],
    ['Recorded route sections', fmt(diagnostics.timelinePathSegments), 'Route traces included in the Timeline file.'],
    ['Recorded location points', fmt(diagnostics.timelinePathPoints), 'Timestamped positions available for reconstructing routes.'],
    ['Car journeys with route points', fmt(diagnostics.vehiclesWithPathPoints), 'Car journeys that overlap recorded route positions.'],
    ['Car journeys with start and end points', fmt(diagnostics.vehiclesWithAnchors), 'Car journeys with enough information to identify their beginning and end.'],
    ['Journeys reconstructed', fmt(diagnostics.journeysConstructed), 'Journeys Roadprints successfully reconstructed from the source data.'],
    ['Genuinely new journeys', fmt(diagnostics.newPassengerVehicleJourneys), 'Journeys never previously seen in an imported file on this device.'],
    ['Previously seen, unmatched', fmt(diagnostics.previouslySeenUnmatchedJourneys), 'Journeys seen in an earlier import but not successfully road-matched, available to retry.'],
    ['Ready for road matching', fmt(diagnostics.usableJourneys), 'Genuinely new and retryable journeys containing at least two location points.'],
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

function updateEasyImportPauseButton() {
  const button = document.getElementById('stopEasyImport');
  if (!button) return;

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
  importMode = 'easy';
  easyImportPaused = false;
  easyImportRunning = true;
  updateEasyImportPauseButton();
  importModeCard.classList.add('hidden');
  easyProgress.classList.remove('hidden');
  updateEasyImportPauseButton();

  renderAll('Timeline.json');
  journeyList.style.display = 'none';

  const candidates = journeys.filter(j => j.points.length > 1);
  let completed = 0;
  let succeeded = 0;
  let failed = 0;

  easyProgressBar.max = candidates.length || 1;
  easyProgressBar.value = 0;

  for (const journey of candidates) {
    if (sessionId !== trackingSessionId) return;

    while (easyImportPaused && sessionId === trackingSessionId) {
      easyProgressText.dataset.resumeText =
        `${completed} / ${candidates.length} · ${succeeded} matched · ${failed} skipped`;
      easyProgressText.textContent =
        `Paused · ${completed} / ${candidates.length} · ${succeeded} matched · ${failed} skipped`;
      await new Promise(resolve => setTimeout(resolve, 250));
    }

    if (sessionId !== trackingSessionId) return;

    easyProgressText.textContent =
      `${completed} / ${candidates.length} · matching ${formatDate(journey.start)}`;

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
      journey.matchedDistanceKm = Number(data.matched_distance_m || 0) / 1000;
      journey.matchedTracepoints = Number(data.matched_tracepoints || 0);
      journey.pointsSentToMatcher = Number(data.points_sent_to_matcher || 0);
      journey.matchQuality = assessMatchQuality(journey, data);
      recordJourneyProcessed(journey);
      scheduleLocalProgressSave();
      succeeded++;
    } catch (err) {
      journey.easyImportError = err.message || String(err);
      failed++;
    }

    completed++;
    easyProgressBar.value = completed;
    easyProgressText.textContent =
      `${completed} / ${candidates.length} · ${succeeded} matched · ${failed} skipped`;
    renderMap();
    await new Promise(resolve => setTimeout(resolve, 250));
  }

  if (sessionId !== trackingSessionId) return;
  easyImportRunning = false;
  easyImportPaused = false;
  updateEasyImportPauseButton();
  if (diagnostics.mileageRebuild && failed===0) {
    persistedMileageHistoryComplete=true;
    scheduleLocalProgressSave();
  }
  easyProgressText.textContent =
    `Complete · ${succeeded} matched · ${failed} skipped`;
}

function renderAll(fileName) {
  const points = journeys.reduce((n, j) => n + j.points.length, 0);

  dataDateRange.querySelector('span').textContent = formatDataDateRange();
  journeyCount.textContent = journeys.length.toLocaleString();
  const journeyLabel = journeyCount.parentElement?.querySelector('span');
  if (journeyLabel) journeyLabel.textContent = 'usable journeys';
  pointCount.textContent = points.toLocaleString();

  summaryCard.classList.remove('hidden');
  mapCard.classList.remove('hidden');
  nextCard.classList.remove('hidden');

  renderJourneyList();
  journeyList.style.display = importMode === 'easy' ? 'none' : 'grid';
  initMap();
  renderMap();
  updateSelectedCount();

  setTimeout(fitSelected, 100);
}

function renderJourneyList() {
  journeyList.innerHTML = '';

  journeys.forEach((j, i) => {
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
    journey.matchedDistanceKm = Number(data.matched_distance_m || 0) / 1000;
    journey.matchedTracepoints = Number(data.matched_tracepoints || 0);
    journey.pointsSentToMatcher = Number(data.points_sent_to_matcher || 0);
    journey.matchQuality = assessMatchQuality(journey, data);
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
  journeys.forEach(j => {
    delete j.matchedGeoJson;
    delete j.motorwayGeoJson;
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
    journeys.filter(j => j.selected).length.toLocaleString();
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
    }).setView([54.5, -3], 5);

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
    map.on('click', handleRefinementMapClick);
    traceLayer = L.layerGroup();
    matchedLayer = L.layerGroup();
    creditedLayer = L.layerGroup().addTo(map);
    canonicalReferenceLayer = L.layerGroup();
    canonicalCoverageLayer = L.layerGroup().addTo(map);
    canonicalUncoveredLayer = L.layerGroup().addTo(map);

    mapLayerControl = L.control.layers(
      {},
      {
        'Credited roads': creditedLayer,
        'Matched journeys': matchedLayer,
        'Raw Timeline traces': traceLayer,
        'Canonical motorway references': canonicalReferenceLayer,
        'Motorway confirmed sections (blue)': canonicalCoverageLayer,
        'Motorway unconfirmed sections (red)': canonicalUncoveredLayer
      },
      {collapsed: true}
    ).addTo(map);

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

function buildCreditedSegments(drawable) {
  const unique = new Map();

  for (const journey of drawable) {
    if (!journey.matchedGeoJson) continue;

    for (const [a, b] of geometrySegments(journey.matchedGeoJson)) {
      const key = segmentKey(a, b);

      if (!unique.has(key)) {
        unique.set(key, {
          a,
          b,
          journeys: 0,
          quality: journey.matchQuality?.level || 'review'
        });
      }

      const item = unique.get(key);
      item.journeys += 1;

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

  if (map) renderMap();
  renderCanonicalMotorwayDashboard();
}

function displayDistance(km) {
  if (distanceUnit === 'km') {
    return `${km.toFixed(1)} km`;
  }

  const miles = km * 0.6213711922;
  return `${miles.toFixed(1)} mi`;
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

async function loadCanonicalRoad(ref, force=false) {
  const road=canonicalRoadState(ref);
  if (!force && ['loading','ready'].includes(road.status)) return road;

  road.status='loading';
  road.error=null;
  renderCanonicalMotorwayDashboard();

  try {
    /*
     * POC 18 first loads a prebuilt canonical motorway cache from GitHub Pages.
     * Live Overpass construction remains only as a fallback for roads absent
     * from the cache or while the cache is being expanded.
     */
    try {
      const cache = await loadCanonicalCache();
      const cached = cache.roads?.[road.ref];

      if (cached) {
        hydrateCanonicalRoadFromCache(road, cached);
        renderMap();
        return road;
      }
    } catch (cacheErr) {
      // Fall through to live construction; surface the live result instead.
      console.warn('Canonical cache unavailable, falling back to Overpass:', cacheErr);
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

    renderMap();
    return road;
  } catch (err) {
    road.status='error';
    road.error=err.message || String(err);
    renderCanonicalMotorwayDashboard();
    return road;
  }
}

async function ensureCanonicalRoadsForDiscoveredRefs(refs) {
  for (const ref of refs.map(normaliseMotorwayRef).filter(Boolean)) {
    canonicalRequestedRefs.add(ref);
  }
  if (canonicalLoadQueueRunning) return;

  canonicalLoadQueueRunning=true;
  try {
    while (true) {
      const ref=[...canonicalRequestedRefs]
        .find(candidate=>canonicalRoadState(candidate).status==='idle');
      if (!ref) break;
      await loadCanonicalRoad(ref);
      await new Promise(resolve=>setTimeout(resolve,400));
    }
  } finally {
    canonicalLoadQueueRunning=false;
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
  for (const journey of drawable) {
    for (const feature of journey.motorwayGeoJson?.features || []) {
      if (motorwayFeatureId(feature)!==road.id) continue;
      for (const [a,b] of geometrySegments({type:'FeatureCollection',features:[feature]})) {
        const lengthM=haversineMetres(a,b);
        if (!Number.isFinite(lengthM) || lengthM<=0) continue;
        const samples=Math.max(1,Math.ceil(lengthM/CANONICAL_MATCH_SAMPLE_M));
        for (let i=0;i<=samples;i++) {
          const id=nearestCanonicalAnchor(road,interpolateLngLat(a,b,i/samples));
          if (id!==null) covered.add(id);
        }
      }
    }
  }
  persistedCoverageByRef.set(road.id,new Set(covered));
  scheduleLocalProgressSave();
  return covered;
}

function renderCanonicalMapLayers() {
  if (!canonicalReferenceLayer || !canonicalCoverageLayer || !canonicalUncoveredLayer) return;
  canonicalReferenceLayer.clearLayers();
  canonicalCoverageLayer.clearLayers();
  canonicalUncoveredLayer.clearLayers();

  for (const road of canonicalRoads.values()) {
    if (road.status!=='ready') continue;
    if (refinementRoadRef && road.id!==refinementRoadRef) continue;

    for (const way of road.ways) {
      L.polyline(way.coords.map(p=>[p[1],p[0]]),{weight:2,opacity:.3,dashArray:'5,6',interactive:false}).addTo(canonicalReferenceLayer);
    }

    if (road.ways.length) {
      for (const way of road.ways) {
        for (let i=1; i<way.coords.length; i++) {
          const a=way.coords[i-1], b=way.coords[i];
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
                color:covered ? '#2f7df6' : '#d93a3a',
                interactive:false
              }
            ).addTo(covered ? canonicalCoverageLayer : canonicalUncoveredLayer);
          }
        }
      }
    } else {
      // Cached references store sampled anchors rather than source OSM ways.
      // Closely spaced markers form the completion line without joining
      // unrelated motorway branches or carriageways.
      for (const anchor of road.anchors) {
        const covered=road.coveredAnchorIds.has(anchor.id);
        L.circleMarker([anchor.lat,anchor.lng],{
          radius:3.2,
          weight:0,
          fillOpacity:.95,
          color:covered ? '#2f7df6' : '#d93a3a',
          fillColor:covered ? '#2f7df6' : '#d93a3a',
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

  if (drawable || onboardingMode==='manual' || onboardingMode==='saved') {
    for (const ref of discoveredRefs) {
      const road=canonicalRoadState(ref);
      if (road.status==='ready') road.coveredAnchorIds=calculateCanonicalCoverageForRoad(road,drawable || []);
    }
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
    const ref=document.createElement('div'); ref.className='canonical-road-ref';
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

function renderMap() {
  if (!map || !traceLayer || !matchedLayer || !creditedLayer) return;

  traceLayer.clearLayers();
  matchedLayer.clearLayers();
  creditedLayer.clearLayers();

  const drawable = journeys.filter(
    j => j.selected && j.points.length > 1
  );

  renderMotorwayDashboard(drawable);
  renderCanonicalMotorwayDashboard(drawable);

  for (const j of drawable) {
    L.polyline(
      j.points.map(p => [p.lat, p.lng]),
      {
        weight: 2,
        opacity: 0.28,
        interactive: false
      }
    ).addTo(traceLayer);

    if (j.matchedGeoJson) {
      L.geoJSON(j.matchedGeoJson, {
        style: {
          weight: 4,
          opacity: 0.55,
          dashArray: j.matchQuality?.level === 'low' ? '5,7' : null
        }
      }).addTo(matchedLayer);
    }
  }

  const credited = buildCreditedSegments(drawable);

  for (const seg of credited) {
    const line = L.polyline(
      [
        [seg.a[1], seg.a[0]],
        [seg.b[1], seg.b[0]]
      ],
      {
        weight: seg.quality === 'high' ? 5 : 4,
        opacity: seg.quality === 'low' ? 0.45 : 0.85,
        dashArray: seg.quality === 'low' ? '4,6' : null
      }
    ).addTo(creditedLayer);

    line.bindTooltip(
      `${seg.journeys} matched journey${seg.journeys === 1 ? '' : 's'} · ${seg.quality.toUpperCase()}`
    );
  }

  requestAnimationFrame(() => map.invalidateSize(true));

  const matchedCount = drawable.filter(j => j.matchedGeoJson).length;
  const highCount = drawable.filter(j => j.matchQuality?.level === 'high').length;
  const reviewCount = drawable.filter(
    j => j.matchQuality && j.matchQuality.level !== 'high'
  ).length;

  if (mapStatus) {
    mapStatus.className = 'muted map-status ok';
    if (refinementRoadRef) {
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
      mapStatus.textContent=`Saved progress loaded · ${ready} canonical motorway reference${ready===1?'':'s'} ready.`;
    } else {
      mapStatus.textContent =
        `Credited roads: ${credited.length.toLocaleString()} unique geometry segments · ` +
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

function extractVehicleJourneys(data) {
  const segments = Array.isArray(data?.semanticSegments)
    ? data.semanticSegments
    : [];

  const diag = {
    semanticSegments: segments.length,
    activitySegments: 0,
    passengerVehicleActivities: 0,
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

  for (const seg of segments) {
    recordDataTimestamp(seg?.startTime);
    recordDataTimestamp(seg?.endTime);
    if (seg?.activity) diag.activitySegments++;

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

  const out = [];

  for (const seg of segments) {
    const activity = seg?.activity;
    if (!activity) continue;

    const mode = String(activity?.topCandidate?.type || '').trim().toUpperCase();
    if (mode !== 'IN_PASSENGER_VEHICLE') continue;

    diag.passengerVehicleActivities++;

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

    // Important diagnostic behaviour:
    // do NOT discard a passenger-vehicle activity just because its trace is sparse.
    out.push({
      start: seg.startTime || null,
      end: seg.endTime || null,
      points: cleanPoints,
      pathPointCount: overlapping.length,
      googleDistanceKm: Number.isFinite(Number(activity?.distanceMeters))
        ? Number(activity.distanceMeters) / 1000
        : null,
      selected: true
    });
  }

  for (const journey of out) journey.importId=journeyFingerprint(journey);
  diag.journeysConstructed = out.length;

  out.sort((a, b) => {
    const aa = Date.parse(a.start || '');
    const bb = Date.parse(b.start || '');
    return (Number.isFinite(aa) ? aa : 0) - (Number.isFinite(bb) ? bb : 0);
  });

  return { journeys: out, diagnostics: diag };
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
