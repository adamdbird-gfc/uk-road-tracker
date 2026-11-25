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

    function readIndex() {
      return cached(driving ? 'index.json' : 'r4-index.json', data => {
        if (data?.schema !== 1 || data.cellsPerDegree !== 50 || !Array.isArray(data.tiles) ||
            !data.tiles.every(key => /^-?\d+_-?\d+$/.test(key))) throw Error('Invalid network index');
        return {scale: data.cellsPerDegree, available: new Set(data.tiles),
          sharedCrossings: new Set(Array.isArray(data.sharedCrossings)?data.sharedCrossings:[])};
      });
    }

    function readTile(key) {
      return cached(key + '.json.gz', data => {
        if (!Array.isArray(data) || !data.every(ref => Number.isSafeInteger(ref?.id) &&
            (ROAD.test(ref.kind) || PATH.test(ref.kind) || driving && ref.kind === 'service') && Array.isArray(ref.coords) &&
            ref.coords.length >= 2 && ref.coords.every(valid) && Array.isArray(ref.nodes) &&
            ref.nodes.length === ref.coords.length && ref.nodes.every(Number.isSafeInteger))) throw Error('Invalid network tile');
        return data;
      });
    }

    async function lookup(route) {
      if (!route.parts?.some(line => line.length >= 2)) return {refs: [], status: 'Own journey references'};
      try {
        const {scale, available, sharedCrossings} = await readIndex();
        const windows = routeWindows(route, scale, available, driving ? 90 : 20);
        if (!windows.size) return {refs: [], status: 'Outside pinned network coverage · own references'};
        const results = await Promise.allSettled([...windows.keys()].map(readTile));
        const refs = new Map();
        let index = 0, failed = 0;
        for (const boxes of windows.values()) {
          const tile = results[index++];
          if (tile.status === 'rejected') { failed++; continue; }
          for (const ref of tile.value)
              if (!refs.has(ref.id) && intersects(ref.coords, boxes))
                refs.set(ref.id, sharedCrossings.has(ref.id)?{...ref,sharedCrossing:true}:ref);
        }
        const result = [...refs.values()].sort((a, b) => a.id - b.id);
        // Missing tiles can hide a competing road. Fall back for the whole journey.
        if (failed) return {refs: [], status: 'Pinned network tile unavailable · own references', unavailable: true};
        return {refs: result, status: result.length ?
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
