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
    const json = JSON.parse(text);
    journeys = extractVehicleJourneys(json);
    if (!journeys.length) throw new Error('No passenger-vehicle journeys were found. The export format may differ from the formats this POC currently recognises.');
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
  syncCheckboxes(); renderMap(); updateSelectedCount();
});
document.getElementById('selectNone').addEventListener('click', () => {
  journeys.forEach(j => j.selected = false);
  syncCheckboxes(); renderMap(); updateSelectedCount();
});
document.getElementById('fitMap').addEventListener('click', fitSelected);

function renderAll(fileName) {
  const points = journeys.reduce((n,j) => n + j.points.length, 0);
  fileStatus.textContent = `${fileName} loaded successfully.`;
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
    cb.type = 'checkbox'; cb.checked = j.selected; cb.dataset.i = i;
    cb.addEventListener('change', e => {
      journeys[Number(e.target.dataset.i)].selected = e.target.checked;
      updateSelectedCount(); renderMap();
    });
    const body = document.createElement('div');
    const title = document.createElement('div');
    title.className = 'journey-title';
    title.textContent = `${formatDate(j.start)}${j.end ? ` · ${formatTime(j.start)}–${formatTime(j.end)}` : ''}`;
    const meta = document.createElement('div');
    meta.className = 'journey-meta';
    meta.textContent = `${j.points.length.toLocaleString()} points${j.distanceKm ? ` · ${j.distanceKm.toFixed(1)} km trace` : ''}`;
    body.append(title, meta); label.append(cb, body); journeyList.append(label);
  });
}

function syncCheckboxes() {
  journeyList.querySelectorAll('input[type=checkbox]').forEach(cb => cb.checked = journeys[Number(cb.dataset.i)].selected);
}
function updateSelectedCount() { selectedCount.textContent = journeys.filter(j => j.selected).length.toLocaleString(); }

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
  journeys.filter(j => j.selected && j.points.length > 1).forEach(j => {
    L.polyline(j.points.map(p => [p.lat, p.lng]), {weight: 3, opacity: .55}).addTo(traceLayer)
      .bindTooltip(`${formatDate(j.start)} · ${j.points.length} points`);
  });
}
function fitSelected() {
  if (!map) return;
  const pts = journeys.filter(j=>j.selected).flatMap(j=>j.points).map(p=>[p.lat,p.lng]);
  if (pts.length) map.fitBounds(L.latLngBounds(pts), {padding:[20,20], maxZoom:13});
}

function extractVehicleJourneys(data) {
  const out = [];
  const segments = Array.isArray(data?.semanticSegments) ? data.semanticSegments
                 : Array.isArray(data?.timelineObjects) ? data.timelineObjects
                 : Array.isArray(data) ? data : [];

  for (const seg of segments) {
    const activity = seg.activity || seg.activitySegment || seg;
    const mode = String(activity?.topCandidate?.type || activity?.activityType || activity?.type || '').toUpperCase();
    const isCar = mode.includes('IN_PASSENGER_VEHICLE') || mode.includes('PASSENGER_VEHICLE') || mode === 'IN_VEHICLE';
    if (!isCar) continue;

    const start = seg.startTime || seg.duration?.startTimestamp || activity?.duration?.startTimestamp || activity?.startTime;
    const end = seg.endTime || seg.duration?.endTimestamp || activity?.duration?.endTimestamp || activity?.endTime;
    let points = [];

    // Newer Timeline export: timelinePath [{point:'geo:lat,lng', time:'...'}]
    const pathCandidates = [seg.timelinePath, activity.timelinePath, activity.simplifiedRawPath?.points, activity.waypointPath?.waypoints].filter(Array.isArray);
    for (const path of pathCandidates) points.push(...extractPoints(path));

    // Older semantic locationHistory format.
    if (!points.length && activity.startLocation && activity.endLocation) {
      const a = parseLocation(activity.startLocation);
      const b = parseLocation(activity.endLocation);
      if (a) points.push(a); if (b) points.push(b);
    }

    points = dedupePoints(points).filter(validPoint);
    if (!points.length) continue;
    out.push({ start, end, points, distanceKm: polylineKm(points), selected: true });
  }
  return out.sort((a,b) => new Date(a.start || 0) - new Date(b.start || 0));
}

function extractPoints(arr) {
  const pts = [];
  for (const item of arr) {
    const p = parseLocation(item);
    if (p) pts.push(p);
  }
  return pts;
}
function parseLocation(v) {
  if (!v) return null;
  if (typeof v === 'string') return parseGeoString(v);
  const raw = v.point || v.geo || v.latLng || v.location || v;
  if (typeof raw === 'string') return parseGeoString(raw);
  if (raw && typeof raw === 'object') {
    const lat = numberish(raw.latitude ?? raw.lat ?? raw.latitudeE7/1e7);
    const lng = numberish(raw.longitude ?? raw.lng ?? raw.lon ?? raw.longitudeE7/1e7);
    if (Number.isFinite(lat) && Number.isFinite(lng)) return {lat,lng};
  }
  return null;
}
function parseGeoString(s) {
  const m = String(s).match(/(?:geo:)?\s*(-?\d+(?:\.\d+)?)\s*,\s*(-?\d+(?:\.\d+)?)/i);
  return m ? {lat:Number(m[1]), lng:Number(m[2])} : null;
}
function numberish(v) { const n = Number(v); return Number.isFinite(n) ? n : NaN; }
function validPoint(p) { return p && p.lat >= -90 && p.lat <= 90 && p.lng >= -180 && p.lng <= 180; }
function dedupePoints(pts) { return pts.filter((p,i,a)=> i===0 || p.lat!==a[i-1].lat || p.lng!==a[i-1].lng); }
function polylineKm(pts) {
  let d=0; for(let i=1;i<pts.length;i++) d+=haversine(pts[i-1],pts[i]); return d;
}
function haversine(a,b) {
  const R=6371, r=Math.PI/180, dLat=(b.lat-a.lat)*r, dLon=(b.lng-a.lng)*r;
  const x=Math.sin(dLat/2)**2 + Math.cos(a.lat*r)*Math.cos(b.lat*r)*Math.sin(dLon/2)**2;
  return 2*R*Math.asin(Math.sqrt(x));
}
function formatDate(v) {
  if (!v) return 'Unknown date'; const d=new Date(v); if (Number.isNaN(d)) return String(v);
  return new Intl.DateTimeFormat('en-GB',{day:'2-digit',month:'short',year:'numeric'}).format(d);
}
function formatTime(v) {
  if (!v) return ''; const d=new Date(v); if (Number.isNaN(d)) return '';
  return new Intl.DateTimeFormat('en-GB',{hour:'2-digit',minute:'2-digit'}).format(d);
}
