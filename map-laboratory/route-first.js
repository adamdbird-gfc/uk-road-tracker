/* Journey display, independent of the Lab's road-coverage interval ledger. */
const RouteFirst = (() => {
  const DEG = Math.PI / 180, METRES = 111320, SEAM = 1.25;
  const roads = /^(motorway|trunk|primary|secondary|tertiary|unclassified|residential|living_street|road|service)(_link)?$/;
  const paths = /^(footway|pedestrian|path|steps|cycleway|bridleway|track)$/;
  const valid = p => Array.isArray(p) && Number.isFinite(p[0]) && Number.isFinite(p[1]) &&
    Math.abs(p[0]) <= 180 && Math.abs(p[1]) <= 90;
  const same = (a, b) => a[0] === b[0] && a[1] === b[1];
  const distance = (a, b) => Math.hypot((a[0] - b[0]) * METRES * Math.cos((a[1] + b[1]) * DEG / 2), (a[1] - b[1]) * METRES);

  function lines(geometry) {
    const candidates = geometry?.type === 'LineString' ? [geometry.coordinates] :
      geometry?.type === 'MultiLineString' ? geometry.coordinates : [];
    return Array.isArray(candidates) ? candidates.filter(line =>
      Array.isArray(line) && line.length > 1 && line.every(valid)) : [];
  }

  function features(collection) {
    return Array.isArray(collection?.features) ? collection.features : [];
  }

  function matched(journey) {
    const source = features(journey.processing_result?.geojson).flatMap(feature => lines(feature?.geometry));
    const indices = journey.journey_corrections?.removed_matched_segments;
    const removed = new Set(Array.isArray(indices) ? indices.filter(n => Number.isInteger(n) && n >= 0) : []);
    let edge = 0;
    const parts = [], legCount = source.length;
    for (const line of source) {
      let part = [line[0]];
      for (let i = 1; i < line.length; i++) {
        if (removed.has(edge++)) {
          if (part.length > 1) parts.push(part);
          part = [line[i]];
        } else part.push(line[i]);
      }
      if (part.length > 1) parts.push(part);
    }
    return {parts, corrected: removed.size > 0, legCount};
  }

  function references(journey) {
    const result = journey.processing_result;
    const seen = new Set(), output = [];
    for (const key of ['road_geojson', 'cycling_geojson', 'a_road_geojson', 'motorway_geojson']) {
      for (const feature of features(result?.[key])) {
        const kind = feature?.properties?.reference_kind || feature?.properties?.highway || '';
        for (const coords of lines(feature?.geometry)) {
          const fingerprint = JSON.stringify([kind, coords]);
          if (!seen.has(fingerprint)) {
            seen.add(fingerprint);
            output.push({coords, kind, name: feature.properties?.name || ''});
          }
        }
      }
    }
    return output;
  }

  function append(line, point) {
    if (!line.length || !same(line.at(-1), point)) line.push(point);
  }

  function arc(line) {
    const offsets = [0];
    for (let i = 1; i < line.length; i++) offsets.push(offsets[i - 1] + distance(line[i - 1], line[i]));
    return offsets;
  }

  function project(point, a, b) {
    const scale = Math.cos((a[1] + b[1]) * DEG / 2);
    const dx = (b[0] - a[0]) * scale, dy = b[1] - a[1];
    const length = dx * dx + dy * dy;
    const t = length > 0 ? Math.min(1, Math.max(0,
      ((point[0] - a[0]) * scale * dx + (point[1] - a[1]) * dy) / length)) : 0;
    const position = [a[0] + (b[0] - a[0]) * t, a[1] + dy * t];
    return {t, metres: distance(point, position), position};
  }

  function anchors(line, offsets, point, limit) {
    const matches = [];
    for (let i = 1; i < line.length; i++) {
      const p = project(point, line[i - 1], line[i]);
      const at = offsets[i - 1] + p.t * (offsets[i] - offsets[i - 1]);
      if (p.metres <= limit && (!matches.length || at - matches.at(-1).at > 0.05))
        matches.push({...p, at, edge: i});
    }
    return matches;
  }

  function slice(line, a, b) {
    const result = [a.position];
    for (let i = a.edge; i < b.edge; i++) append(result, line[i]);
    append(result, b.position);
    return result;
  }

  function follows(corridor, candidate, tolerance) {
    const offsets = arc(corridor), length = offsets.at(-1);
    let progress = 0;
    for (let i = 1; i < candidate.length; i++) {
      const a = candidate[i - 1], b = candidate[i];
      const samples = Math.max(1, Math.ceil(distance(a, b) / 5));
      for (let part = 1; part <= samples; part++) {
        const t = part / samples;
        const point = [a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t];
        const hits = anchors(corridor, offsets, point, tolerance);
        const next = hits.filter(p => p.at >= progress - 1.5 && p.at <= progress + tolerance * 2 + 7)
          .sort((a, b) => a.metres - b.metres || a.at - b.at)[0];
        if (!next) return false;
        progress = Math.max(progress, next.at);
      }
    }
    return length - progress <= 2;
  }

  /** Replace only source spans bracketed by two shared network locations.
   * Test both directions along the source and the reference so crossings,
   * dead ends, turns and different routes cannot disappear through proximity. */
  function centreline(line, refs) {
    const offsets = arc(line), candidates = [];
    for (const {coords, kind} of refs) {
      if (!roads.test(kind) || kind === 'service') continue;
      const referenceLength = arc(coords).at(-1);
      if (referenceLength < 12 || referenceLength > 500) continue;
      for (const way of [coords, [...coords].reverse()]) {
        const start = anchors(line, offsets, way[0], SEAM);
        const end = anchors(line, offsets, way.at(-1), SEAM);
        for (const a of start) for (const b of end) {
          const length = b.at - a.at;
          if (length < 12 || length < referenceLength * 0.8 || length > referenceLength * 1.18) continue;
          const source = slice(line, a, b);
          if (!follows(source, way, 10) || !follows(way, source, 10)) continue;
          const exact = source.every(point => anchors(way, arc(way), point, 0.2).length);
          if (!exact) candidates.push({a, b, way, length});
        }
      }
    }
    const chosen = [];
    candidates.sort((a, b) => b.length - a.length || a.a.at - b.a.at);
    for (const candidate of candidates) {
      if (candidates.some(other => other !== candidate &&
        Math.abs(candidate.a.at - other.a.at) < 2 && Math.abs(candidate.b.at - other.b.at) < 2 &&
        (!follows(candidate.way, other.way, 1) || !follows(other.way, candidate.way, 1)))) continue;
      if (!chosen.some(other => candidate.a.at < other.b.at - 0.01 && candidate.b.at > other.a.at + 0.01))
        chosen.push(candidate);
    }
    if (!chosen.length) return {line, projected: 0};
    const output = [];
    let edge = 0;
    for (const {a, b, way} of chosen.sort((a, b) => a.a.at - b.a.at)) {
      while (edge < a.edge) append(output, line[edge++]);
      append(output, a.position);
      for (const point of way) append(output, point);
      append(output, b.position);
      edge = b.edge;
    }
    while (edge < line.length) append(output, line[edge++]);
    return {line: output, projected: chosen.length};
  }

  function junction(a, b, recorded, refs, allowed) {
    const start = a.at(-1), end = b[0], gap = distance(start, end);
    if (same(start, end)) return [start];
    if (gap > 35 || !recorded.length) return null;
    const candidates = [];
    for (const line of recorded) {
      const offsets = arc(line);
      for (const from of anchors(line, offsets, start, 6)) for (const to of anchors(line, offsets, end, 6)) {
        const length = to.at - from.at;
        if (length <= 0 || length > Math.max(12, gap * 1.8)) continue;
        const evidence = slice(line, from, to);
        for (const ref of refs) {
          if (!allowed(ref.kind)) continue;
          for (const way of [ref.coords, [...ref.coords].reverse()]) {
            const positions = arc(way);
            for (const x of anchors(way, positions, start, SEAM)) for (const y of anchors(way, positions, end, SEAM)) {
              if (y.at <= x.at || y.at - x.at > Math.max(6, gap * 1.6)) continue;
              const link = slice(way, x, y);
              if (follows(evidence, link, 7) && follows(link, evidence, 7)) candidates.push(link);
            }
          }
        }
      }
    }
    if (!candidates.length) return null;
    const [first, ...others] = candidates;
    return others.every(link => follows(first, link, 1) && follows(link, first, 1)) ? first : null;
  }

  function reconstruct(journey) {
    const recorded = lines(journey.route_geometry);
    const {parts, corrected, legCount} = matched(journey);
    const refs = references(journey);
    const mode = journey.mode;
    const foot = ['walk', 'walking', 'running', 'run', 'on_foot'].includes(mode);
    const cycle = ['bicycle', 'cycling', 'cycle'].includes(mode);
    const drive = ['driving', 'car', 'bus', 'motorcycling', 'truck'].includes(mode);
    const allowed = kind => roads.test(kind) || ((foot || cycle) && paths.test(kind));
    if (!legCount) return {parts: recorded, recorded, refs, onFoot: foot, corrected, source: recorded.length ? 'recorded' : 'unavailable', projected: 0, joins: 0};
    const output = [];
    let projected = 0, joins = 0;
    for (const part of parts) {
      const result = foot ? centreline(part, refs) : {line: part, projected: 0};
      projected += result.projected;
      const previous = output.at(-1);
      const link = previous && !corrected ?
        junction(previous, result.line, recorded, refs, (foot || cycle || drive) ? allowed : () => false) : null;
      if (link) {
        for (const point of [...link, ...result.line]) append(previous, point);
        joins++;
      } else output.push([...result.line]);
    }
    // Driving results retain the traversed matcher steps alongside the overview.
    const roadSteps = drive ? features(journey.processing_result?.road_geojson)
      .filter(feature => Number.isFinite(feature?.properties?.distance_m) && feature.properties.distance_m > 0)
      .flatMap(feature => lines(feature.geometry)) : [];
    return {parts: output, recorded, refs, onFoot: foot, corrected, source: 'saved match',
      sourceType: journey.source?.type, roadSteps, projected, joins};
  }

  return {reconstruct, lines};
})();
if (typeof module !== 'undefined') module.exports = RouteFirst;
