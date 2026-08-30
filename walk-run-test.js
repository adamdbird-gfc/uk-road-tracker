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
let matchedLayer = null;
let matching = false;

fileInput.addEventListener('change', loadTimelineFile);
matchButton.addEventListener('click', matchSample);
clearButton.addEventListener('click', clearTest);

async function loadTimelineFile() {
  const file = fileInput.files?.[0];
  if (!file) return;
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
    matchButton.textContent = `Match first ${activities.length}`;
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
  if (matching || !activities.length) return;
  matching = true;
  matchButton.disabled = true;
  let succeeded = 0;
  progress.value = 0;
  for (let index = 0; index < activities.length; index++) {
    const activity = activities[index];
    progressText.textContent = `${index} / ${activities.length} · matching ${formatActivityDate(activity.start)}`;
    try {
      const response = await fetch(`${API_BASE_URL}/match`, {
        method: 'POST',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify({points: activity.points})
      });
      const data = await response.json().catch(() => ({}));
      if (!response.ok) throw new Error(data.detail || `HTTP ${response.status}`);
      activity.matchedGeoJson = data.geojson;
      activity.pointsSent = Number(data.points_sent_to_matcher || 0);
      activity.pointsMatched = Number(data.matched_tracepoints || 0);
      activity.confidence = averageConfidence(data.geojson);
      activity.result = 'matched';
      succeeded++;
    } catch (error) {
      activity.result = 'failed';
      activity.error = error.message || String(error);
    }
    progress.value = index + 1;
    matchedNode.textContent = succeeded.toLocaleString();
    renderList();
    renderMap();
    await new Promise(resolve => setTimeout(resolve, 200));
  }
  progressText.textContent = `Complete · ${succeeded} matched · ${activities.length - succeeded} rejected by the matcher. Nothing was saved.`;
  matching = false;
  matchButton.disabled = false;
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
  matchedLayer = L.layerGroup().addTo(map);
  L.control.layers({}, {'Raw Timeline traces': rawLayer, 'Matched roads': matchedLayer}).addTo(map);
}

function renderMap() {
  if (!map) return;
  rawLayer.clearLayers();
  matchedLayer.clearLayers();
  for (const activity of activities) {
    L.polyline(activity.points.map(point => [point.lat, point.lng]), {
      color: '#657083', weight: 4, opacity: .75, dashArray: '7,5'
    }).addTo(rawLayer);
    if (activity.matchedGeoJson) {
      L.geoJSON(activity.matchedGeoJson, {style: {color: '#16803c', weight: 4, opacity: .85}})
        .addTo(matchedLayer);
    }
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
      details.push(`${activity.pointsMatched}/${activity.pointsSent} points matched`);
      if (Number.isFinite(activity.confidence)) details.push(`${Math.round(activity.confidence * 100)}% confidence`);
    }
    if (activity.error) details.push(activity.error);
    meta.textContent = details.join(' · ');
    item.append(heading, meta);
    activityList.append(item);
  });
}

function clearTest(clearFile = true) {
  if (matching) return;
  activities = [];
  if (clearFile) fileInput.value = '';
  resultsCard.classList.add('hidden');
  mapCard.classList.add('hidden');
  if (rawLayer) rawLayer.clearLayers();
  if (matchedLayer) matchedLayer.clearLayers();
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
