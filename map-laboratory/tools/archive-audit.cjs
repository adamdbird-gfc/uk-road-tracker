'use strict';

// Read the owner archive and pinned reference tiles locally. The JSON report
// contains counts and ZIP ordinals, so it can be used without exporting traces.
const fs = require('node:fs');
const path = require('node:path');
const {performance} = require('node:perf_hooks');

const radians = Math.PI / 180;
function vector(a, b) {
 const scale = 111320 * Math.cos(a[1] * radians);
 return [(b[0] - a[0]) * scale, (b[1] - a[1]) * 111320];
}
const distance = (a, b) => Math.hypot(...vector(a, b));
const point = (a, b, t) => [a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t];
const length = edges => edges.reduce((sum, edge) => sum + distance(edge.a, edge.b), 0);
const metres = value => Math.round(value * 10) / 10;
const segments = lines => (lines || []).flatMap(line =>
 line.slice(1).map((b, i) => ({a: line[i], b})));

function offset(p, {a, b}) {
 const [x, y] = vector(a, b), [u, v] = vector(a, p);
 const t = Math.max(0, Math.min(1, (u * x + v * y) / (x * x + y * y || 1)));
 return Math.hypot(u - x * t, v - y * t);
}

function parallel(first, second) {
 const [x, y] = vector(first.a, first.b), [u, v] = vector(second.a, second.b);
 return Math.abs((x * u + y * v) / (Math.hypot(x, y) * Math.hypot(u, v) || 1)) >= .95;
}

class SegmentIndex {
 constructor() {
  this.cells = new Map();
 }
 visit(a, b, radius, fn) {
  const latitude = radius / 111320;
  const longitude = latitude / Math.max(.01, Math.cos(Math.max(Math.abs(a[1]), Math.abs(b[1])) * radians));
  const south = Math.floor((Math.min(a[1], b[1]) - latitude) * 2000);
  const north = Math.floor((Math.max(a[1], b[1]) + latitude) * 2000);
  const west = Math.floor((Math.min(a[0], b[0]) - longitude) * 2000);
  const east = Math.floor((Math.max(a[0], b[0]) + longitude) * 2000);
  for(let x = west; x <= east; x++)
   for(let y = south; y <= north; y++)fn(x * 1000000 + y);
 }
 add(edge) {
  // Index long edges by short slices to avoid filling the entire bounding box.
  const slices = Math.max(1, Math.ceil(distance(edge.a, edge.b) / 40));
  const cells = new Set();
  for(let i = 0; i < slices; i++)
   this.visit(point(edge.a, edge.b, i / slices), point(edge.a, edge.b, (i + 1) / slices), 0, key => cells.add(key));
  for(const key of cells) {
   if(!this.cells.has(key))this.cells.set(key, []);
   this.cells.get(key).push(edge);
  }
 }
 near(p, radius) {
  const found = new Set();
  this.visit(p, p, radius, key => {
   for(const edge of this.cells.get(key) || [])
    if(offset(p, edge) <= radius)found.add(edge);
  });
  return found;
 }
}

function topology(coverage) {
 const edges = [...coverage.novel, ...coverage.repeat,
  ...segments(coverage.links), ...segments(coverage.accessLinks)];
 const index = new SegmentIndex();
 const sets = edges.map((_, i) => i);
 function root(i) {
  while(sets[i] !== i) {
   sets[i] = sets[sets[i]];
   i = sets[i];
  }
  return i;
 }
 edges.forEach((edge, id) => index.add({...edge, id}));
 edges.forEach((edge, id) => {
  for(const p of [edge.a, edge.b])
   for(const next of index.near(p, 2))sets[root(id)] = root(next.id);
 });
 const components = new Map();
 edges.forEach((edge, id) => {
  const key = root(id);
  if(!components.has(key))components.set(key, {length: 0, novel: 0});
  const component = components.get(key), size = distance(edge.a, edge.b);
  component.length += size;
  if(id < coverage.novel.length)component.novel += size;
 });
 const detached = [...components.values()].sort((a, b) => b.length - a.length).slice(1);
 return {index, components: components.size, detachedNovel: detached.reduce((sum, item) => sum + item.novel, 0)};
}

function crossingCandidates(edges, refs) {
 const surface = ref => !ref.bridge && !ref.tunnel && Number(ref.layer || 0) === 0;
 const lanes = new SegmentIndex(), crossings = new SegmentIndex();
 for(const ref of refs) {
  if(ref.footway === 'crossing' && surface(ref)) {
   for(const edge of segments([ref.coords]))crossings.add(edge);
  }
  if(!surface(ref) || !ref.name || !/^(trunk|primary|secondary|tertiary)$/.test(ref.kind))continue;
  for(const edge of segments([ref.coords]))lanes.add({...edge, ref});
 }
 // Two distinct parallel ways of the same name may be divided carriageways.
 // Absence of a crossing in this snapshot is a review signal only.
 return edges.filter(edge => {
  if(distance(edge.a, edge.b) < 4)return false;
  const midpoint = point(edge.a, edge.b, .5);
  const nearby = [...lanes.near(midpoint, 35)].filter(lane => !parallel(edge, lane));
  const crossed = nearby.filter(lane => {
   const [u, v] = vector(lane.a, lane.b);
   const [x, y] = vector(lane.a, edge.a), [p, q] = vector(lane.a, edge.b);
   return (u * y - v * x) * (u * q - v * p) < 0 &&
    [0, .25, .5, .75, 1].some(t => offset(point(edge.a, edge.b, t), lane) < 2);
  });
  return crossed.some((a, i) => crossed.slice(i + 1).some(b =>
   a.ref.id !== b.ref.id && a.ref.name === b.ref.name && parallel(a, b) &&
   offset(point(a.a, a.b, .5), b) >= 3)) &&
   ![.25, .5, .75].some(t => crossings.near(point(edge.a, edge.b, t), 4).size);
 }).length;
}

function assess(route, coverage, history) {
 const rendered = [...coverage.novel, ...coverage.repeat];
 const structure = topology(coverage);
 const parallelNovel = coverage.novel.filter(edge => {
  const midpoint = point(edge.a, edge.b, .5);
  return [...history.near(midpoint, 22)].some(prior => parallel(edge, prior) &&
   [.2, .5, .8].every(t => {
    const delta = offset(point(edge.a, edge.b, t), prior);
    return delta >= 3 && delta <= 22;
   }));
 });
 const fixes = route.sourceType === 'timeline_import' ? [] : (route.recorded || []).flat();
 const missedFixes = fixes.length >= 10 ?
  fixes.filter(p => !structure.index.near(p, 25).size).length : 0;
 return {
  rendered: metres(length(rendered)),
  novel: metres(length(coverage.novel)),
  repeat: metres(length(coverage.repeat)),
  detached: Math.round(structure.detachedNovel * 1000) / 1000,
  components: structure.components,
  parallel: metres(length(parallelNovel)),
  crossingCandidates: route.onFoot ?
   crossingCandidates(rendered, route.independentRoadReferences || []) : 0,
  fixes: fixes.length >= 10 ? fixes.length : 0,
  missedFixes
 };
}

async function run(archive, labRoot = path.resolve(__dirname, '..')) {
 const local = file => require(path.join(labRoot, file + '.js'));
 const [bulk, routeFirst, network, rail, routeLed] =
  ['bulk-diagnostics', 'route-first', 'independent-road-network', 'rail-transition', 'route-led-coverage'].map(local);
 const pair = fs.existsSync(path.join(labRoot, 'paired-walk.js')) ? local('paired-walk') : null;
 const request = async file => {
  try {return new Response(await fs.promises.readFile(path.join(labRoot, file)));}
  catch(error) {
   if(error.code !== 'ENOENT')throw error;
   return new Response(null, {status: 404});
  }
 };
 const roads = network.create(request);
 const driving = network.create(request, './reference-network/driving-v1/', Date.now, true);
 const transitions = rail.create(request);
 const zip = bulk.roadReplay(await bulk.open(new Blob([await fs.promises.readFile(archive)])));
 const started = performance.now();
 let ledger = routeLed.createLedger();
 const history = new Map(), rows = [], groups = new Map();
 const surface = route => route.surfaceParts ? {...route, parts: route.surfaceParts} : route;
 async function load(index) {
  const {journey} = await zip.read(index);
  const route = routeFirst.reconstruct(journey);
  const pinned = route.onFoot && route.source === 'saved match' ? await roads.lookup(route) : {refs: []};
  const drive = route.roadSteps?.length && route.sourceType === 'timeline_import' ?
   await driving.lookup(route) : null;
  return transitions({...route, independentRoadReferences: pinned.refs,
   displayRoadReferences: pinned.displayRoads, forkContext: pinned.forkContext,
   drivingReferences: drive?.refs, networkUnavailable: !!(pinned.unavailable || drive?.unavailable)}, zip.journeys[index]);
 }
 for(const [index, metadata] of zip.journeys.entries()) {
  if(process.env.ROADPRINTS_AUDIT_PROGRESS && index % 100 === 0)
   process.stderr.write(`Audited ${index} / ${zip.journeys.length} surface journeys\n`);
  const source = await load(index);
  const route = pair ? await pair.forReplay(index, surface(source), zip.journeys, load) : surface(source);
  const coverage = routeLed.accumulate(ledger, route);
  ledger = coverage.next;
  const mode = metadata.mode || 'unknown';
  if(!history.has(mode))history.set(mode, new SegmentIndex());
  const spatial = history.get(mode);
  const metrics = assess(route, coverage, spatial);
  for(const edge of coverage.novel)spatial.add(edge);
  const start = route.parts[0]?.[0];
  // Rounded geographic groups keep overlapping repeated journeys together
  // substantially better than a random split per journey.
  const key = start?.map(value => Math.floor(value * 20)).join(':') || 'empty';
  if(!groups.has(key))groups.set(key, groups.size + 1);
  rows.push({zip: metadata.archiveOrdinal, group: groups.get(key), mode,
   source: route.source, pinnedWays: route.independentRoadReferences.length,
   drivingWays: route.drivingReferences?.length || 0,
   separateSourceParts: route.parts.length,
   networkUnavailable: !!route.networkUnavailable, paired: !!route.pairedWalk,
   ...metrics});
 }
 return {archiveRecords: zip.archiveCount, hiddenRail: zip.railHidden,
  elapsedSeconds: Math.round((performance.now() - started) / 1000), rows};
}
// A group already used to tune a rule cannot serve as a holdout. Keep one
// candidate per failure type and group, plus geographically separate controls.
function reviewQueue(rows, knownOrdinals = []) {
 const excluded = new Set(rows.filter(row => knownOrdinals.includes(row.zip)).map(row => row.group));
 const walking = rows.filter(row => /^(walking|running)$/.test(row.mode) && row.pinnedWays);
 const order = row => row.crossingCandidates ? 4 : row.detached > 0 ? 3 :
  row.parallel >= 20 ? 2 : row.fixes && row.missedFixes / row.fixes > .2 ? 1 : 0;
 const ranked = walking.toSorted((a, b) => order(b) - order(a) ||
  b.crossingCandidates - a.crossingCandidates || b.detached - a.detached ||
  b.parallel - a.parallel || a.zip - b.zip);
 const seen = new Set(), flagged = [], controls = [];
 for(const row of ranked) {
  const severity = order(row), key = row.group + ':' + severity;
  if(excluded.has(row.group) || seen.has(key))continue;
  seen.add(key);
  (severity ? flagged : controls).push(row);
 }
 return {flagged, controls, excludedGroups: excluded.size};
}

if(require.main === module) {
 const [archive, labRoot, ...known] = process.argv.slice(2);
 if(!archive) {
  process.stderr.write('Usage: node archive-audit.cjs PRIVATE_ARCHIVE.zip [LAB_DIRECTORY] [KNOWN_ZIP_ORDINAL ...]\n');
  process.exitCode = 2;
 } else run(path.resolve(archive), labRoot && path.resolve(labRoot)).then(report => {
  const review = reviewQueue(report.rows, known.map(Number));
  process.stdout.write(JSON.stringify({...report, review}) + '\n');
 }).catch(error => {
   process.stderr.write(String(error) + '\n');
   process.exitCode = 1;
  });
}
module.exports = {assess, SegmentIndex, run, reviewQueue};
