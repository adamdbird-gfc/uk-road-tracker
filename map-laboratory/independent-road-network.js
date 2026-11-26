/* Immutable public OSM tiles supply network shape, never movement evidence. */
const IndependentRoadNetwork = (() => {
  const ROAD = /^(motorway|trunk|primary|secondary|tertiary|unclassified|residential|living_street|road)(_link)?$/;
  const PATH = /^(footway|pedestrian|path|steps|cycleway|bridleway|corridor)$/;
  const valid = p => Array.isArray(p) && p.length === 2 && p.every(Number.isFinite) &&
    Math.abs(p[0]) <= 180 && Math.abs(p[1]) <= 90;

  function coordinatesKey(coords) {
    const points = coords.map(p => p.map(v => v.toFixed(7)).join(','));
    const forward = points.join(';'), reverse = points.reverse().join(';');
    return forward < reverse ? forward : reverse;
  }

  function merge(local, external) {
    const refs = new Map();
    for (const ref of [...(external || []), ...(local || [])]) {
      if (!Array.isArray(ref?.coords) || ref.coords.length < 2 || !ref.coords.every(valid)) continue;
      const key = (ROAD.test(ref.kind) ? 'road' : ref.kind) + ':' + coordinatesKey(ref.coords);
      if (!refs.has(key)) refs.set(key, ref);
    }
    return [...refs.values()];
  }

  function windowFor(a, b, metres) {
    const lonScale = Math.cos((a[1] + b[1]) * Math.PI / 360);
    const dx = (b[0] - a[0]) * lonScale, dy = b[1] - a[1];
    if (Math.hypot(dx, dy) * 111320 > 500 || lonScale < 0.1) return null;
    const lonPad = metres / (111320 * lonScale), latPad = metres / 111320;
    return [Math.min(a[0], b[0]) - lonPad, Math.min(a[1], b[1]) - latPad,
      Math.max(a[0], b[0]) + lonPad, Math.max(a[1], b[1]) + latPad];
  }

  function routeWindows(route, scale, available, padding) {
    const windows = new Map();
    for (const line of route.parts || []) for (let i = 1; i < line.length; i++) {
      const a = line[i - 1], b = line[i];
      if (!valid(a) || !valid(b)) continue;
      const box = windowFor(a, b, padding);
      if (!box) continue;
      for (let x = Math.floor(box[0] * scale); x <= Math.floor(box[2] * scale); x++)
        for (let y = Math.floor(box[1] * scale); y <= Math.floor(box[3] * scale); y++) {
          const key = x + '_' + y;
          if (!available.has(key)) continue;
          const list = windows.get(key) || [];
          list.push(box);
          windows.set(key, list);
        }
    }
    return windows;
  }

  function intersects(coords, boxes) {
    for (let i = 1; i < coords.length; i++) {
      const a = coords[i - 1], b = coords[i];
      const minX = Math.min(a[0], b[0]), maxX = Math.max(a[0], b[0]);
      const minY = Math.min(a[1], b[1]), maxY = Math.max(a[1], b[1]);
      if (boxes.some(([west, south, east, north]) =>
        maxX >= west && minX <= east && maxY >= south && minY <= north)) return true;
    }
    return false;
  }

  function create(request = fetch, root = './reference-network/v1/', now = Date.now, driving = false) {
    const cache = new Map();

    async function read(name, retry) {
      const response = await request(root + name, {
        signal: AbortSignal.timeout(8000), ...(retry ? {cache: 'reload'} : {}),
      });
      if (!response.ok) throw Error('HTTP ' + response.status);
      if (!name.endsWith('.gz')) return response.json();
      const bytes = await response.arrayBuffer();
      const header = new Uint8Array(bytes, 0, Math.min(2, bytes.byteLength));
      // A browser or proxy may have already decoded a gzip HTTP response.
      if (header[0] !== 0x1f || header[1] !== 0x8b) return new Response(bytes).json();
      return new Response(new Blob([bytes]).stream().pipeThrough(new DecompressionStream('gzip'))).json();
    }

    function cached(name, validate) {
      const existing = cache.get(name);
      if (existing && (existing.retryAt === undefined || now() < existing.retryAt)) return existing.promise;
      const entry = {};
      entry.promise = read(name, false).then(validate)
        .catch(() => read(name, true).then(validate))
        .catch(error => {
          entry.retryAt = now() + 15000;
          throw error;
        });
      cache.set(name, entry);
      return entry.promise;
    }

    const validRef = ref => Number.isSafeInteger(ref?.id) && Array.isArray(ref.coords) &&
      ref.coords.length >= 2 && ref.coords.every(valid) && Array.isArray(ref.nodes) &&
      ref.nodes.length === ref.coords.length && ref.nodes.every(Number.isSafeInteger);

    function readIndex() {
      return cached(driving ? 'index.json' : 'r8-index.json', data => {
        if (data?.schema !== 1 || data.cellsPerDegree !== 50 || !Array.isArray(data.tiles) ||
            !data.tiles.every(key => /^-?\d+_-?\d+$/.test(key))) throw Error('Invalid network index');
        const corridors = driving ? [] : data.displayCorridors || [];
        const displayRoads = [], displayPavements = new Map();
        for (const {roads, pavements} of corridors) {
          const name = roads?.[0]?.name;
          if (!name || !roads.every(ref => validRef(ref) && ROAD.test(ref.kind) &&
              ref.name === name && ref.access === 'no') ||
              !Array.isArray(pavements) || !pavements.every(Number.isSafeInteger))
            throw Error('Invalid display corridor');
          displayRoads.push(...roads.map(ref => ({...ref, displayOnly: true})));
          for (const id of pavements) displayPavements.set(id, name);
        }
        const sections = driving ? [] : data.pavementSections || [];
        for (const {way, fromNode, toNode, name, roadWays} of sections) {
          if (![way, fromNode, toNode].every(Number.isSafeInteger) || fromNode === toNode ||
              typeof name !== 'string' || !name || !Array.isArray(roadWays) ||
              !roadWays.length || !roadWays.every(Number.isSafeInteger)) throw Error('Invalid pavement section');
        }
        const pavementSections = Map.groupBy(sections, section => section.way);
        const walkingCarriageways = new Map();
        for (const {name, representative, parallel} of driving ? [] : data.walkingCarriageways || []) {
          if (typeof name !== 'string' || !name || !Array.isArray(representative) ||
              !Array.isArray(parallel) || !representative.length || !parallel.length ||
              ![...representative, ...parallel].every(Number.isSafeInteger) ||
              parallel.some(id => representative.includes(id) || walkingCarriageways.has(id)))
            throw Error('Invalid walking carriageway');
          for (const id of parallel) walkingCarriageways.set(id, {name, representative});
        }

        return {scale: data.cellsPerDegree, available: new Set(data.tiles), displayRoads, displayPavements, pavementSections, walkingCarriageways,
          sharedCrossings: new Set(Array.isArray(data.sharedCrossings)?data.sharedCrossings:[])};
      });
    }

    function readTile(key) {
      return cached(key + '.json.gz', data => {
        if (!Array.isArray(data) || !data.every(ref => validRef(ref) &&
            (ROAD.test(ref.kind) || PATH.test(ref.kind) || driving && ref.kind === 'service'))) throw Error('Invalid network tile');
        return data;
      });
    }

    async function lookup(route) {
      if (!route.parts?.some(line => line.length >= 2)) return {refs: [], status: 'Own journey references'};
      try {
        const {scale, available, sharedCrossings, displayRoads, displayPavements, pavementSections, walkingCarriageways} = await readIndex();
        const windows = routeWindows(route, scale, available, driving ? 90 : 20);
        if (!windows.size) return {refs: [], status: 'Outside pinned network coverage · own references'};
        const results = await Promise.allSettled([...windows.keys()].map(readTile));
        const refs = new Map(), display = new Map(), nearbyRoads = new Map();
        // A cut-off fork cannot establish that its arms rejoin. Keep a
        // small halo of named road ways from tiles already in this replay.
        const roadWindows = driving ? new Map() : routeWindows(route, scale, new Set(windows.keys()), 36);
        let index = 0, failed = 0;
        for (const [key, boxes] of windows) {
          const tile = results[index++];
          if (tile.status === 'rejected') { failed++; continue; }
          for (const ref of tile.value) {
            if (refs.has(ref.id)) continue;
            if (!intersects(ref.coords, boxes)) {
              const halo = roadWindows.get(key);
              if (halo && ref.name && /^(tertiary|unclassified|residential)$/.test(ref.kind) &&
                  intersects(ref.coords, halo)) nearbyRoads.set(ref.id, ref);
              continue;
            }
            const carriageway = walkingCarriageways.get(ref.id);
            if (carriageway && carriageway.name === ref.name)
              refs.set(ref.id, {...ref, walkingCarriageway: carriageway.representative});
            else if (displayPavements.has(ref.id)) refs.set(ref.id, {...ref, displayPavement: displayPavements.get(ref.id)});
            else if (pavementSections.has(ref.id)) refs.set(ref.id, {...ref, pavementSections: pavementSections.get(ref.id)});
            else refs.set(ref.id, sharedCrossings.has(ref.id) ? {...ref, sharedCrossing: true} : ref);
          }
          for (const ref of displayRoads)
            if (!display.has(ref.id) && intersects(ref.coords, boxes)) display.set(ref.id, ref);
        }
        const pavementNames = new Set([...refs.values()].filter(ref =>
          ref.kind === 'footway' && ref.footway === 'sidewalk' && ref.name).map(ref => ref.name));
        const forkContext = [...nearbyRoads.values()].filter(ref => pavementNames.has(ref.name) &&
          !refs.has(ref.id));
        const result = [...refs.values()].sort((a, b) => a.id - b.id);
        // Missing tiles can hide a competing road. Fall back for the whole journey.
        if (failed) return {refs: [], status: 'Pinned network tile unavailable · own references', unavailable: true};
        return {refs: result, ...(forkContext.length ? {forkContext} : {}), ...(display.size ? {displayRoads: [...display.values()]} : {}), status: result.length ?
          result.filter(ref => ROAD.test(ref.kind)).length + ' pinned OSM road references' :
          'Pinned network has no nearby references · own references'};
      } catch {
        return {refs: [], status: 'Pinned network index unavailable · own references', unavailable: true};
      }
    }
    return {lookup};
  }

  return {create, merge, isRoad: kind => ROAD.test(kind)};
})();
if (typeof module !== 'undefined') module.exports = IndependentRoadNetwork;
