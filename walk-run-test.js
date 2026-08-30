const API_BASE_URL = 'https://uk-road-tracker-api.onrender.com';
const SAMPLE_LIMIT = 20;

const fileInput = document.getElementById('testTimelineFile');
const fileStatus = document.getElementById('testFileStatus');
const resultsCard = document.getElementById('testResults');
const mapCard = document.getElementById('testMapCard');
const foundNode = document.getElementById('testFound');
const sampleNode = document.getElementById('testSample');
const matchedNode = document.getElementById('testMatched');
const matchButton = document.getElementById('matchTestActivities');
const clearButton = document.getElementById('clearTestResults');
const progress = document.getElementById('testProgress');
const progressText = document.getElementById('testProgressText');
const activityList = document.getElementById('testActivityList');

let activities = [];
let map = null;
let rawLayer = null;
let drivingLayer = null;
let walkingLayer = null;
let matching = false;
let matchRunId = 0;

fileInput.addEventListener('change', loadTimelineFile);
matchButton.addEventListener('click', matchSample);
clearButton.addEventListener('click', clearTest);

async function loadTimelineFile() {
  const file = fileInput.files?.[0];
  if (!file) return;
  matchRunId++;
  matching = false;
  matchButton.disabled = true;
  clearTest(false);
  fileStatus.className = 'muted map-status';
  fileStatus.textContent = `Reading ${file.name}…`;
  try {
    const json = JSON.parse(await file.text());
    const all = extractWalkRunActivities(json);
    activities = all.slice(0, SAMPLE_LIMIT);
    foundNode.textContent = all.length.toLocaleString();
    sampleNode.textContent = activities.length.toLocaleString();
    matchedNode.textContent = '0';
    progress.max = activities.length || 1;
    progress.value = 0;
    resultsCard.classList.remove('hidden');
    mapCard.classList.toggle('hidden', !activities.length);
    matchButton.disabled = !activities.length;
    matchButton.textContent = `Compare first ${activities.length}`;
    progressText.textContent = activities.length
      ? `${activities.length} activities ready. No results have been saved.`
      : 'No walking or running activities with at least two recorded path points were found.';
    fileStatus.textContent = `${file.name} inspected successfully.`;
    renderList();
    if (activities.length) {
      initMap();
      renderMap();
      fitAll();
    }
  } catch (error) {
    fileStatus.className = 'error map-status';
    fileStatus.textContent = `Could not inspect this file: ${error.message || error}`;
  }
}

async function matchSample() {
  if (!activities.length) {
    progressText.textContent = 'Choose a Timeline JSON file before starting the test.';
    return;
  }
  if (matching) {
    progressText.textContent = 'The walking and running test is already running.';
    return;
  }
  const runId = ++matchRunId;
  matching = true;
  matchButton.disabled = true;
  matchButton.textContent = 'Comparing…';
  let succeeded = 0;
  progress.value = 0;
  progressText.className = 'muted map-status';
  progressText.textContent = `Starting matcher for ${activities.length} activities…`;
  await new Promise(resolve => setTimeout(resolve, 0));
  try {
    for (let index = 0; index < activities.length; index++) {
      if (runId !== matchRunId) return;
      const activity = activities[index];
      progressText.textContent = `${index} / ${activities.length} · comparing ${formatActivityDate(activity.start)}`;
      const [drivingResult, walkingResult] = await Promise.allSettled([
        requestActivityMatch('/match', activity.points),
        requestActivityMatch('/match-walking', activity.points)
      ]);
      if (runId !== matchRunId) return;

      if (drivingResult.status === 'fulfilled') {
        applyMatchResult(activity, 'driving', drivingResult.value);
      } else {
        activity.drivingError = errorText(drivingResult.reason);
      }
      if (walkingResult.status === 'fulfilled') {
        applyMatchResult(activity, 'walking', walkingResult.value);
        activity.result = 'matched';
        succeeded++;
      } else {
        activity.result = 'failed';
        activity.walkingError = errorText(walkingResult.reason);
      }
      progress.value = index + 1;
      matchedNode.textContent = succeeded.toLocaleString();
      renderList();
      renderMapSafely();
      // The public pedestrian test service asks clients to stay at or below
      // one request per second. Leave a full interval between activities.
      await new Promise(resolve => setTimeout(resolve, 1100));
    }
    progressText.textContent = `Complete · ${succeeded} matched · ${activities.length - succeeded} rejected by the matcher. Nothing was saved.`;
  } catch (error) {
    progressText.className = 'error map-status';
    progressText.textContent = `The test stopped unexpectedly: ${error.message || error}`;
  } finally {
    if (runId === matchRunId) {
      matching = false;
      matchButton.disabled = false;
      matchButton.textContent = `Compare first ${activities.length}`;
    }
  }
}

async function requestActivityMatch(path, points) {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    method: 'POST',
    headers: {'Content-Type': 'application/json'},
    body: JSON.stringify({points})
  });
  const data = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(data.detail || `HTTP ${response.status}`);
  if (!data.geojson?.features?.length) throw new Error('Matcher returned no route geometry.');
  return data;
}

function applyMatchResult(activity, prefix, data) {
  activity[`${prefix}GeoJson`] = data.geojson;
  activity[`${prefix}PointsSent`] = Number(data.points_sent_to_matcher || 0);
  activity[`${prefix}PointsMatched`] = Number(data.matched_tracepoints || 0);
  activity[`${prefix}Confidence`] = averageConfidence(data.geojson);
}

function errorText(error) {
  return error?.message || String(error);
}

function extractWalkRunActivities(data) {
  const segments = Array.isArray(data?.semanticSegments) ? data.semanticSegments : [];
  const pathPoints = [];
  for (const segment of segments) {
    for (const item of Array.isArray(segment?.timelinePath) ? segment.timelinePath : []) {
      const point = parseLocation(item?.point);
      const timeMs = Date.parse(item?.time || '');
      if (point && Number.isFinite(timeMs)) pathPoints.push({...point, timeMs});
    }
  }
  pathPoints.sort((a, b) => a.timeMs - b.timeMs);
  const output = [];
  for (const segment of segments) {
    const activity = segment?.activity;
    const mode = String(activity?.topCandidate?.type || '').trim().toUpperCase();
    if (!['WALKING', 'RUNNING'].includes(mode)) continue;
    const startMs = Date.parse(segment.startTime || '');
    const endMs = Date.parse(segment.endTime || '');
    if (!Number.isFinite(startMs) || !Number.isFinite(endMs)) continue;
    const recorded = pathPoints
      .slice(lowerBound(pathPoints, startMs), upperBound(pathPoints, endMs))
      .map(({lat, lng}) => ({lat, lng}));
    const points = dedupePoints(recorded).filter(validPoint);
    if (points.length < 2) continue;
    output.push({
      mode,
      start: segment.startTime || null,
      end: segment.endTime || null,
      points,
      googleDistanceKm: Number.isFinite(Number(activity?.distanceMeters))
        ? Number(activity.distanceMeters) / 1000
        : null,
      result: 'pending'
    });
  }
  return output.sort((a, b) => Date.parse(a.start || '') - Date.parse(b.start || ''));
}

function initMap() {
  if (map) return;
  map = L.map('testMap').setView([53.3, -1.8], 6);
  L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
    maxZoom: 19,
    attribution: '&copy; OpenStreetMap contributors'
  }).addTo(map);
  rawLayer = L.layerGroup().addTo(map);
  drivingLayer = L.layerGroup().addTo(map);
  walkingLayer = L.layerGroup().addTo(map);
  L.control.layers({}, {
    'Raw Timeline traces': rawLayer,
    'Driving matches': drivingLayer,
    'Pedestrian matches': walkingLayer
  }).addTo(map);
}

function renderMap() {
  if (!map) return;
  rawLayer.clearLayers();
  drivingLayer.clearLayers();
  walkingLayer.clearLayers();
  for (const activity of activities) {
    L.polyline(activity.points.map(point => [point.lat, point.lng]), {
      color: '#657083', weight: 4, opacity: .75, dashArray: '7,5'
    }).addTo(rawLayer);
    if (activity.drivingGeoJson) {
      L.geoJSON(activity.drivingGeoJson, {style: {color: '#16803c', weight: 4, opacity: .8}})
        .addTo(drivingLayer);
    }
    if (activity.walkingGeoJson) {
      L.geoJSON(activity.walkingGeoJson, {style: {color: '#2f7df6', weight: 4, opacity: .9}})
        .addTo(walkingLayer);
    }
  }
}

function renderMapSafely() {
  try {
    renderMap();
  } catch (error) {
    console.warn('A test result could not be drawn:', error);
    progressText.className = 'error map-status';
    progressText.textContent = `Matching continued, but a map result could not be drawn: ${error.message || error}`;
  }
}

function renderList() {
  activityList.innerHTML = '';
  activities.forEach((activity, index) => {
    const item = document.createElement('div');
    item.className = 'test-activity';
    const heading = document.createElement('div');
    heading.className = 'test-activity-top';
    const title = document.createElement('strong');
    title.textContent = `${index + 1}. ${activity.mode === 'RUNNING' ? 'Running' : 'Walking'} · ${formatActivityDate(activity.start)}`;
    const state = document.createElement('span');
    state.className = `match-badge ${activity.result === 'matched' ? 'high' : activity.result === 'failed' ? 'low' : 'review'}`;
    state.textContent = activity.result === 'matched' ? 'MATCHED' : activity.result === 'failed' ? 'FAILED' : 'READY';
    heading.append(title, state);
    const meta = document.createElement('div');
    meta.className = 'test-activity-meta';
    const details = [`${activity.points.length} recorded points`];
    if (Number.isFinite(activity.googleDistanceKm)) details.push(`${activity.googleDistanceKm.toFixed(2)} km Google distance`);
    if (activity.result === 'matched') {
      details.push(`walking ${activity.walkingPointsMatched}/${activity.walkingPointsSent} points`);
      if (Number.isFinite(activity.walkingConfidence)) details.push(`${Math.round(activity.walkingConfidence * 100)}% walking confidence`);
    }
    if (activity.drivingGeoJson) {
      details.push(`driving ${activity.drivingPointsMatched}/${activity.drivingPointsSent} points`);
    }
    if (activity.walkingError) details.push(`walking failed: ${activity.walkingError}`);
    if (activity.drivingError) details.push(`driving failed: ${activity.drivingError}`);
    meta.textContent = details.join(' · ');
    item.append(heading, meta);
    activityList.append(item);
  });
}

function clearTest(clearFile = true) {
  if (clearFile) matchRunId++;
  matching = false;
  activities = [];
  if (clearFile) fileInput.value = '';
  resultsCard.classList.add('hidden');
  mapCard.classList.add('hidden');
  if (rawLayer) rawLayer.clearLayers();
  if (drivingLayer) drivingLayer.clearLayers();
  if (walkingLayer) walkingLayer.clearLayers();
  fileStatus.className = 'muted map-status';
  fileStatus.textContent = clearFile ? 'No file selected.' : 'Reading file…';
}

function fitAll() {
  const points = activities.flatMap(activity => activity.points.map(point => [point.lat, point.lng]));
  if (points.length) map.fitBounds(L.latLngBounds(points), {padding: [20, 20], maxZoom: 13});
  setTimeout(() => map.invalidateSize(true), 50);
}

function averageConfidence(geojson) {
  const values = (geojson?.features || []).map(feature => Number(feature?.properties?.confidence)).filter(Number.isFinite);
  return values.length ? values.reduce((sum, value) => sum + value, 0) / values.length : null;
}

function lowerBound(points, target) {
  let low = 0, high = points.length;
  while (low < high) { const middle = (low + high) >> 1; if (points[middle].timeMs < target) low = middle + 1; else high = middle; }
  return low;
}

function upperBound(points, target) {
  let low = 0, high = points.length;
  while (low < high) { const middle = (low + high) >> 1; if (points[middle].timeMs <= target) low = middle + 1; else high = middle; }
  return low;
}

function parseLocation(value) {
  if (!value) return null;
  if (typeof value === 'string') {
    const values = value.match(/-?\d+(?:\.\d+)?/g);
    if (!values || values.length < 2) return null;
    return {lat: Number(values[0]), lng: Number(values[1])};
  }
  const raw = value.point || value.geo || value.latLng || value.location || value;
  if (typeof raw === 'string') return parseLocation(raw);
  const lat = Number(raw?.latitude ?? raw?.lat ?? (Number.isFinite(Number(raw?.latitudeE7)) ? Number(raw.latitudeE7) / 1e7 : NaN));
  const lng = Number(raw?.longitude ?? raw?.lng ?? raw?.lon ?? (Number.isFinite(Number(raw?.longitudeE7)) ? Number(raw.longitudeE7) / 1e7 : NaN));
  return Number.isFinite(lat) && Number.isFinite(lng) ? {lat, lng} : null;
}

function validPoint(point) {
  return point && point.lat >= -90 && point.lat <= 90 && point.lng >= -180 && point.lng <= 180;
}

function dedupePoints(points) {
  return points.filter((point, index) => index === 0 || point.lat !== points[index - 1].lat || point.lng !== points[index - 1].lng);
}

function formatActivityDate(value) {
  const date = new Date(value || '');
  if (Number.isNaN(date.getTime())) return 'Unknown date';
  return new Intl.DateTimeFormat('en-GB', {dateStyle: 'medium', timeStyle: 'short'}).format(date);
}
