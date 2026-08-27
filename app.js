let journeys = [];
let diagnostics = {};
let map = null;
let traceLayer = null;
let matchedLayer = null;
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
const mapStatus = document.getElementById('mapStatus');

fileInput.addEventListener('change', async () => {
  const file = fileInput.files?.[0];
  if (!file) return;

  resetOutput();
  status(`Reading ${file.name} (${formatBytes(file.size)})…`);

  try {
    const text = await file.text();
    status(`Read complete. Parsing JSON…`);
    await yieldToBrowser();

    const json = JSON.parse(text);
    status(`JSON parsed. Inspecting Timeline structure…`);
    await yieldToBrowser();

    const result = extractVehicleJourneys(json);
    journeys = result.journeys;
    diagnostics = result.diagnostics;

    showDiagnostics(file.name);

    if (!diagnostics.passengerVehicleActivities) {
      throw new Error(
        `Diagnostic result: ${diagnostics.semanticSegments.toLocaleString()} semantic segments were found, ` +
        `but 0 IN_PASSENGER_VEHICLE activities were detected.`
      );
    }

    journeys.forEach(j => j.selected = true);
    await ensureLeaflet();
    renderAll(file.name);
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

function resetOutput() {
  journeys = [];
  diagnostics = {};
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

function showDiagnostics(fileName) {
  fileStatus.className = 'muted';
  fileStatus.style.whiteSpace = 'pre-line';
  fileStatus.textContent =
    `${fileName} inspected successfully.\n\n${diagnosticText()}`;
}

function diagnosticText() {
  return [
    `Semantic segments: ${fmt(diagnostics.semanticSegments)}`,
    `Activity segments: ${fmt(diagnostics.activitySegments)}`,
    `Passenger-vehicle activities: ${fmt(diagnostics.passengerVehicleActivities)}`,
    `timelinePath segments: ${fmt(diagnostics.timelinePathSegments)}`,
    `Timestamped timelinePath points: ${fmt(diagnostics.timelinePathPoints)}`,
    `Vehicle activities with overlapping path points: ${fmt(diagnostics.vehiclesWithPathPoints)}`,
    `Vehicle activities with usable start/end anchors: ${fmt(diagnostics.vehiclesWithAnchors)}`,
    `Journeys constructed: ${fmt(diagnostics.journeysConstructed)}`
  ].join('\n');
}

function fmt(n) {
  return Number(n || 0).toLocaleString();
}

function renderAll(fileName) {
  const points = journeys.reduce((n, j) => n + j.points.length, 0);

  journeyCount.textContent = journeys.length.toLocaleString();
  pointCount.textContent = points.toLocaleString();

  summaryCard.classList.remove('hidden');
  mapCard.classList.remove('hidden');
  nextCard.classList.remove('hidden');

  renderJourneyList();
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
    matchButton.textContent = 'Match road';
    matchButton.disabled = j.points.length < 2;

    const status = document.createElement('div');
    status.className = 'match-status';
    status.textContent = j.points.length < 2
      ? 'Not enough coordinates to road-match.'
      : 'Not matched yet.';

    matchButton.addEventListener('click', () => matchJourney(i, matchButton, status));

    actions.append(matchButton);
    body.append(title, meta, actions, status);
    wrapper.append(cb, body);
    journeyList.append(wrapper);
  });
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
    journey.matchedDistanceKm = Number(data.matched_distance_m || 0) / 1000;
    journey.matchedTracepoints = Number(data.matched_tracepoints || 0);
    journey.pointsSentToMatcher = Number(data.points_sent_to_matcher || 0);

    statusNode.className = 'match-status ok';
    statusNode.textContent =
      `Matched ${journey.matchedTracepoints}/${journey.pointsSentToMatcher} sampled points · ` +
      `${journey.matchedDistanceKm.toFixed(1)} km matched`;

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
      if (mapStatus) {
        mapStatus.className = 'muted map-status ok';
        mapStatus.textContent =
          `Map loaded. ${journeys.filter(j => j.selected && j.points.length > 1).length.toLocaleString()} selected journey traces are available.`;
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
    traceLayer = L.layerGroup().addTo(map);
    matchedLayer = L.layerGroup().addTo(map);

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

function renderMap() {
  if (!map || !traceLayer || !matchedLayer) return;

  traceLayer.clearLayers();
  matchedLayer.clearLayers();

  const drawable = journeys.filter(
    j => j.selected && j.points.length > 1
  );

  for (const j of drawable) {
    L.polyline(
      j.points.map(p => [p.lat, p.lng]),
      {
        weight: 2,
        opacity: 0.30,
        interactive: false
      }
    ).addTo(traceLayer);

    if (j.matchedGeoJson) {
      L.geoJSON(j.matchedGeoJson, {
        style: {
          weight: 5,
          opacity: 0.95
        }
      }).addTo(matchedLayer);
    }
  }

  requestAnimationFrame(() => map.invalidateSize(true));

  const matchedCount = drawable.filter(j => j.matchedGeoJson).length;

  if (mapStatus) {
    mapStatus.className = 'muted map-status ok';
    mapStatus.textContent =
      `Map ready: ${drawable.length.toLocaleString()} raw journey traces · ` +
      `${matchedCount.toLocaleString()} road-matched journey${matchedCount === 1 ? '' : 's'}.`;
  }
}

function fitSelected() {
  if (!map || !window.L) return;

  const pts = journeys
    .filter(j => j.selected)
    .flatMap(j => j.points)
    .filter(validPoint)
    .map(p => [p.lat, p.lng]);

  if (!pts.length) {
    if (mapStatus) {
      mapStatus.className = 'muted map-status warn';
      mapStatus.textContent = 'No valid selected coordinates are available to fit on the map.';
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
    journeysConstructed: 0
  };

  const pathPoints = [];

  for (const seg of segments) {
    if (seg?.activity) diag.activitySegments++;

    if (Array.isArray(seg?.timelinePath)) {
      diag.timelinePathSegments++;

      for (const item of seg.timelinePath) {
        const point = parseLocation(item?.point);
        const timeMs = Date.parse(item?.time || '');

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
