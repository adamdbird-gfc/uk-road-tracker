let journeys = [];
let map = null;
let traceLayer = null;

const fileInput = document.getElementById('timelineFile');
const fileStatus = document.getElementById('fileStatus');
const summaryCard = document.getElementById('summaryCard');
const mapCard = document.getElementById('mapCard');
const nextCard = document.getElementById('nextCard');
const journeyList = document.getElementById('journeyList');
const journeyCount = document.getElementById('journeyCount');
const pointCount = document.getElementById('pointCount');
const selectedCount = document.getElementById('selectedCount');

fileInput.addEventListener('change', async () => {
  const file = fileInput.files?.[0];
  if (!file) return;

  fileStatus.className = 'muted';
  fileStatus.textContent = `Reading ${file.name}…`;

  try {
    const text = await file.text();
    fileStatus.textContent = `Parsing ${file.name}…`;
    await yieldToBrowser();

    const json = JSON.parse(text);

    fileStatus.textContent = 'Finding passenger-vehicle journeys…';
    await yieldToBrowser();

    journeys = extractVehicleJourneys(json);

    if (!journeys.length) {
      throw new Error(
        'No passenger-vehicle journeys were found. This file does not appear to match the Google Timeline export format this POC supports.'
      );
    }

    journeys.forEach(j => j.selected = true);
    renderAll(file.name);
  } catch (err) {
    journeys = [];
    summaryCard.classList.add('hidden');
    mapCard.classList.add('hidden');
    nextCard.classList.add('hidden');
    fileStatus.className = 'error';
    fileStatus.textContent = err.message || String(err);
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

function yieldToBrowser() {
  return new Promise(resolve => setTimeout(resolve, 0));
}

function renderAll(fileName) {
  const points = journeys.reduce((n, j) => n + j.points.length, 0);
  const traced = journeys.filter(j => j.pathPointCount > 0).length;

  fileStatus.className = 'muted';
  fileStatus.textContent =
    `${fileName} loaded: ${journeys.length.toLocaleString()} car journeys found; ` +
    `${traced.toLocaleString()} have Timeline path points.`;

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
    const label = document.createElement('label');
    label.className = 'journey';

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

    if (j.pathPointCount === 0) {
      detail.push('start/end only');
    }

    meta.textContent = detail.join(' · ');

    body.append(title, meta);
    label.append(cb, body);
    journeyList.append(label);
  });
}

function syncCheckboxes() {
  journeyList
    .querySelectorAll('input[type=checkbox]')
    .forEach(cb => {
      cb.checked = journeys[Number(cb.dataset.i)].selected;
    });
}

function updateSelectedCount() {
  selectedCount.textContent =
    journeys.filter(j => j.selected).length.toLocaleString();
}

function initMap() {
  if (map) return;

  map = L.map('map', { preferCanvas: true }).setView([54.5, -3], 5.5);

  L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
    maxZoom: 19,
    attribution: '&copy; OpenStreetMap contributors'
  }).addTo(map);

  traceLayer = L.layerGroup().addTo(map);
}

function renderMap() {
  if (!map) return;

  traceLayer.clearLayers();

  journeys
    .filter(j => j.selected && j.points.length > 1)
    .forEach(j => {
      L.polyline(
        j.points.map(p => [p.lat, p.lng]),
        { weight: 3, opacity: 0.55 }
      )
        .addTo(traceLayer)
        .bindTooltip(
          `${formatDate(j.start)} · ${j.pathPointCount} Timeline points`
        );
    });
}

function fitSelected() {
  if (!map) return;

  const pts = journeys
    .filter(j => j.selected)
    .flatMap(j => j.points)
    .map(p => [p.lat, p.lng]);

  if (pts.length) {
    map.fitBounds(L.latLngBounds(pts), {
      padding: [20, 20],
      maxZoom: 13
    });
  }
}

/*
 * Google Timeline export format used by the supplied map-data.zip:
 *
 * data.semanticSegments contains:
 *   - activity segments with activity.topCandidate.type
 *   - separate timelinePath segments covering time windows
 *
 * The detailed path is therefore NOT necessarily nested inside the activity.
 * We build one chronological path-point index and give each car activity the
 * points whose timestamps overlap that activity's start/end times.
 */
function extractVehicleJourneys(data) {
  const segments = Array.isArray(data?.semanticSegments)
    ? data.semanticSegments
    : [];

  if (!segments.length) return [];

  const pathPoints = [];

  for (const seg of segments) {
    if (!Array.isArray(seg.timelinePath)) continue;

    for (const item of seg.timelinePath) {
      const point = parseLocation(item?.point);
      const timeMs = Date.parse(item?.time || '');

      if (point && Number.isFinite(timeMs)) {
        pathPoints.push({
          ...point,
          timeMs
        });
      }
    }
  }

  pathPoints.sort((a, b) => a.timeMs - b.timeMs);

  const out = [];

  for (const seg of segments) {
    const activity = seg?.activity;
    const mode = String(activity?.topCandidate?.type || '').toUpperCase();

    if (mode !== 'IN_PASSENGER_VEHICLE') continue;

    const startMs = Date.parse(seg.startTime || '');
    const endMs = Date.parse(seg.endTime || '');

    if (!Number.isFinite(startMs) || !Number.isFinite(endMs)) continue;

    const from = lowerBound(pathPoints, startMs);
    const to = upperBound(pathPoints, endMs);

    const overlapping = pathPoints
      .slice(from, to)
      .map(({ lat, lng }) => ({ lat, lng }));

    const startPoint = parseLocation(activity?.start?.latLng);
    const endPoint = parseLocation(activity?.end?.latLng);

    /*
     * Keep Google's activity start/end locations as anchors. This means all
     * 508 vehicle activities are still represented even where Google recorded
     * no detailed timelinePath point inside that exact time interval.
     */
    const points = [];

    if (startPoint) points.push(startPoint);
    points.push(...overlapping);
    if (endPoint) points.push(endPoint);

    const cleanPoints = dedupePoints(points).filter(validPoint);

    if (!cleanPoints.length) continue;

    out.push({
      start: seg.startTime,
      end: seg.endTime,
      points: cleanPoints,
      pathPointCount: overlapping.length,
      googleDistanceKm: Number.isFinite(Number(activity?.distanceMeters))
        ? Number(activity.distanceMeters) / 1000
        : null,
      selected: true
    });
  }

  return out.sort(
    (a, b) => Date.parse(a.start || '') - Date.parse(b.start || '')
  );
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
    /*
     * Supplied Timeline.json uses strings such as:
     * "51.4309771°, 0.3707566°"
     */
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

    if (typeof raw === 'string') {
      return parseLocation(raw);
    }

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
