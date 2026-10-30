"""Offline source-geometry diagnostics. No matcher, catalogue writer or network I/O.

Input: JSON {schema_version: 1, comparisons: [{key, source, reference, ...}]}.
Each feature has provider, feature_id, coordinates (WGS84 lon/lat), optional
version, node_ids, tags, modes, provenance. Missing metadata stays null.
See docs/ncn-source-compatibility-20261030.md for policies and limitations.
"""
import argparse
from collections import Counter
import hashlib
import json
import math
from pathlib import Path

POLICY = 'ncn-offline-compatibility-v1'
RADIUS_M = 6371008.8
# Descriptive flags only: these never grant admission or modify production gates.
DISTANCE_M = 10.0
ANGLE_DEG = 15.0


def digest(value):
    return hashlib.sha256(json.dumps(value, separators=(',', ':'), sort_keys=True,
                                     allow_nan=False).encode()).hexdigest()


def coordinates(feature):
    points = feature.get('coordinates')
    if not isinstance(points, list) or not 2 <= len(points) <= 5000:
        raise ValueError('Expected 2..5000 WGS84 vertices')
    for p in points:
        if not isinstance(p, list) or len(p) != 2 or any(
                isinstance(v, bool) or not isinstance(v, (int, float)) or not math.isfinite(v) for v in p):
            raise ValueError('Invalid WGS84 vertex')
        if not -180 <= p[0] <= 180 or not -90 <= p[1] <= 90:
            raise ValueError('Invalid WGS84 range')
    if len({tuple(p) for p in points}) < 2:
        raise ValueError('Degenerate geometry')
    nodes = feature.get('node_ids')
    if nodes is not None and (len(nodes) != len(points) or any(type(n) != int or n <= 0 for n in nodes)):
        raise ValueError('Node chain must align with vertices; do not map OSRM annotations to overview vertices')
    return points


def normalized_hash(points):
    compact = []
    for x, y in points:
        p = [round(float(x), 7) or 0.0, round(float(y), 7) or 0.0]
        if not compact or compact[-1] != p:
            compact.append(p)
    if len(compact) < 2:
        raise ValueError('Geometry collapses at consumer precision')
    return digest(min(compact, compact[::-1]))


def project(points, origin):
    lon, lat = origin
    return [(RADIUS_M * math.radians(x-lon) * math.cos(math.radians(lat)),
             RADIUS_M * math.radians(y-lat)) for x, y in points]


def distance(a, b):
    return math.hypot(a[0]-b[0], a[1]-b[1])


def angle(a, b, directed=False):
    na, nb = math.hypot(*a), math.hypot(*b)
    if not na or not nb:
        return None
    c = max(-1.0, min(1.0, sum(x*y for x, y in zip(a, b))/(na*nb)))
    value = math.degrees(math.acos(c))
    return value if directed else min(value, 180-value)


def vector(a, b):
    return (b[0]-a[0], b[1]-a[1])


def nearest(p, line):
    hits = []
    for i, (a, b) in enumerate(zip(line, line[1:])):
        dx, dy = vector(a, b)
        n = dx*dx+dy*dy
        if not n:
            continue
        t = max(0.0, min(1.0, ((p[0]-a[0])*dx+(p[1]-a[1])*dy)/n))
        hits.append((distance(p, (a[0]+t*dx, a[1]+t*dy)), i))
    return min(hits)


def samples(line):
    # Fixed <=5 m spacing; reject excessive work rather than silently coarsen.
    out = []
    for a, b in zip(line, line[1:]):
        count = max(1, math.ceil(distance(a, b)/5))
        if len(out)+count > 20000:
            raise ValueError('Bounded diagnostic sampling limit exceeded')
        out.extend((a[0]+(b[0]-a[0])*i/count, a[1]+(b[1]-a[1])*i/count) for i in range(count))
    return out+[line[-1]]


def metrics(first, second, metric=False):
    origin = first[0]
    a, b = (first, second) if metric else (project(first, origin), project(second, origin))
    if len(a)*len(b) > 250000:
        raise ValueError('Bounded diagnostic vertex-pair limit exceeded')
    sa, sb = samples(a), samples(b)
    if len(sa)*len(b)+len(sb)*len(a) > 2000000:
        raise ValueError('Bounded diagnostic segment-sampling work limit exceeded')
    deviations = [nearest(p, b)[0] for p in sa]+[nearest(p, a)[0] for p in sb]
    forward = max(distance(a[0], b[0]), distance(a[-1], b[-1]))
    reverse = max(distance(a[0], b[-1]), distance(a[-1], b[0]))
    reversed_best = reverse < forward
    aligned = b[::-1] if reversed_best else b
    angles = []
    for x, y in zip(a, a[1:]):
        midpoint = ((x[0]+y[0])/2, (x[1]+y[1])/2)
        _, i = nearest(midpoint, b)
        value = angle(vector(x, y), vector(b[i], b[i+1]))
        if value is not None:
            angles.append(value)
    la = sum(distance(x, y) for x, y in zip(a, a[1:]))
    lb = sum(distance(x, y) for x, y in zip(b, b[1:]))
    ratio = lb/la
    dev = max(deviations)
    endpoint = min(forward, reverse)
    return {
        'projection': 'input metric coordinates' if metric else 'local equirectangular; radius 6371008.8 m; first source vertex origin',
        'source_length_m': la, 'reference_length_m': lb, 'length_ratio': ratio,
        'best_endpoint_orientation': 'reversed' if reversed_best else 'forward',
        'endpoint_displacement_m': endpoint,
        'max_corresponding_vertex_displacement_m': max(distance(x, y) for x, y in zip(a, aligned)) if len(a) == len(b) else None,
        'max_sampled_bidirectional_distance_m': dev,
        'sample_spacing_max_m': 5, 'sample_count': len(sa)+len(sb),
        'max_nearest_segment_undirected_angle_deg': max(angles) if angles else None,
        'segment_angle_differences_deg': angles,
        'endpoint_directed_angle_deg': angle(vector(a[0], a[-1]), vector(b[0], b[-1]), True),
        'compatible_extents_screen': endpoint <= DISTANCE_M and dev <= DISTANCE_M and .9 <= ratio <= 1.1,
        'warning': 'Sampled distances and nearest-segment headings are descriptive, not continuous Hausdorff bounds or finite admission proof.'}


def history_evidence(source, reference, histories):
    """Find coordinate-compatible historical states, never rewrite original versions.

    Way history must establish a stable chain; every old/new coordinate must be
    witnessed by a versioned node history for that node. These are compatible
    states, not a claim about which versions an old importer actually consumed.
    """
    unavailable = {'status': 'unavailable', 'limitation': 'Original chains/history unavailable; geometry alone cannot distinguish node movement, changed membership or independent redraw.'}
    if not histories or source.get('provider') != 'osm' or source.get('feature_id') != reference.get('feature_id'):
        return unavailable
    ways = [e for h in histories for e in h['elements'] if e.get('type') == 'way'
            and str(e['id']) == str(source.get('feature_id')) and e.get('version')]
    chains = {tuple(e.get('nodes', [])) for e in ways}
    if len(ways) < 2 or len(chains) != 1 or not next(iter(chains), ()):
        return {'status': 'changed_or_unproved_way_membership', 'way_history_versions': [e['version'] for e in ways],
                'limitation': 'Stable membership not proved; topology, access and route identity require review.'}
    nodes = list(next(iter(chains)))
    a, b = source['coordinates'], reference['coordinates']
    if len(a) != len(nodes) or len(b) != len(nodes):
        return unavailable
    # Historical chain may be reverse-oriented relative to either snapshot.
    states = []
    for coords in (a, b):
        found = None
        for points in (coords, coords[::-1]):
            matches = []
            for node, xy in zip(nodes, points):
                hits = [e for h in histories for e in h['elements'] if e.get('type') == 'node'
                        and e['id'] == node and [e.get('lon'), e.get('lat')] == xy and e.get('version')]
                if not hits:
                    break
                matches.append({'node_id': node, 'compatible_versions': sorted({e['version'] for e in hits}),
                                'timestamps': sorted({e['timestamp'] for e in hits if e.get('timestamp')})})
            if len(matches) == len(nodes):
                found = (points, matches)
                break
        if found is None:
            return unavailable
        states.append(found)
    changed = [n for n, x, y in zip(nodes, states[0][0], states[1][0]) if x != y]
    return {'status': 'documented_shared_node_movement' if changed else 'history_compatible_unchanged',
            'stable_node_chain': nodes, 'changed_shared_nodes': changed,
            'source_compatible_node_states': states[0][1], 'reference_compatible_node_states': states[1][1],
            'way_history_versions': [e['version'] for e in ways],
            'limitation': 'History-compatible states do not recover missing original import/capture versions. Access/tag/topology changes need review.'}


def metadata(feature):
    pts = coordinates(feature)
    return {**{k: feature.get(k) for k in ('provider', 'feature_id', 'version', 'node_ids', 'route_memberships', 'modes', 'tags', 'provenance')},
            'ordered_geometry_sha256': digest(pts), 'consumer_normalized_geometry_sha256': normalized_hash(pts),
            'normalization_policy': 'OSM seven decimals; adjacent duplicate removal; lexicographic forward/reverse minimum; compact JSON SHA256',
            'missing_metadata': [k for k in ('version', 'node_ids', 'route_memberships', 'modes') if feature.get(k) is None]}


def access_status(feature, mode):
    tags = feature.get('tags') or {}
    key = {'on_foot': 'foot', 'cycling': 'bicycle'}.get(mode)
    if key is None:
        return 'unsupported_mode'
    explicit = tags.get(key)
    if explicit in ('no', 'private') or (tags.get('access') in ('no', 'private') and explicit not in ('yes', 'designated', 'permissive')):
        return 'incompatible'
    modes = feature.get('modes')
    if modes is not None and mode not in modes:
        return 'incompatible'
    return 'supported' if explicit in ('yes', 'designated', 'permissive') or modes is not None and mode in modes else 'unknown'


def compare(record):
    source, reference = record['source'], record['reference']
    a, b = coordinates(source), coordinates(reference)
    same = bool(source.get('provider') and source.get('feature_id') and
                source.get('provider') == reference.get('provider') and
                str(source.get('feature_id')) == str(reference.get('feature_id')))
    known_identity = all(f.get('provider') and f.get('feature_id') for f in (source, reference))
    m = metrics(a, b)
    exact, reverse = a == b, a == b[::-1]
    history = history_evidence(source, reference, record.get('histories'))
    nodes_a, nodes_b = source.get('node_ids'), reference.get('node_ids')
    node_status = 'unknown'
    if nodes_a is not None and nodes_b is not None:
        node_status = 'same' if nodes_a == nodes_b else 'reversed' if nodes_a == nodes_b[::-1] else 'changed_membership'
    mode = record.get('mode', 'on_foot')
    access = {name: access_status(f, mode) for name, f in [('source', source), ('reference', reference)]}
    reasons = []
    if not known_identity:
        classification = 'insufficient_evidence'
        reasons.append('missing_feature_identity')
    elif not same or 'incompatible' in access.values() or 'unsupported_mode' in access.values():
        classification = 'incompatible_or_unsupported_correspondence'
        reasons.append('different_feature_identity_or_incompatible_mode_access')
    elif node_status == 'changed_membership':
        classification = 'possible_correspondence_requiring_review'
        reasons.append('changed_node_membership')
    elif exact:
        classification = 'exact_match'
    elif reverse:
        classification = 'exact_reversed_geometry'
    elif history['status'] == 'documented_shared_node_movement':
        classification = 'same_identified_feature_with_documented_geometry_update'
        reasons.append('upstream_node_coordinate_edit_not_import_version_recovery')
    elif m['compatible_extents_screen']:
        classification = 'possible_correspondence_requiring_review'
        reasons.append('proximity_does_not_prove_correspondence')
    else:
        classification = 'incompatible_or_unsupported_correspondence'
        reasons.append('incompatible_extents_or_alignment')
    route = record.get('route')
    memberships = source.get('route_memberships')
    route_state = 'unknown' if not route or memberships is None else 'supported_bounded_membership' if route in memberships else 'unproved_requested_route'
    if route_state != 'supported_bounded_membership':
        reasons.append('route_identity_requires_independent_evidence')
    if 'unknown' in access.values():
        reasons.append('mode_access_unknown_do_not_infer_cycling_from_walking')
    context = record.get('context', {})
    context_result = dict(context)
    if context.get('target_coordinates'):
        context_result['target_alignment'] = metrics(a, context['target_coordinates'])
    if context.get('target_edges_metric'):
        edges = context['target_edges_metric']
        # Both witness edges end at a shared point. A small outward angle is a
        # return/hairpin, not ordinary continuation through the junction.
        if edges[0][-1] == edges[1][-1]:
            outward = angle(vector(edges[0][-1], edges[0][0]), vector(edges[1][-1], edges[1][0]), True)
            context_result['outward_target_angle_deg'] = outward
            if outward is not None and outward <= ANGLE_DEG:
                reasons.append('hairpin_or_parallel_target_ambiguity_retained')
    ranges = context.get('admitted_vertex_ranges')
    request = context.get('requested_vertex_range')
    if request is not None:
        valid = len(request) == 2 and all(type(n) == int for n in request) and 0 <= request[0] < request[1] < len(a)
        contained = valid and any(lo <= request[0] and request[1] <= hi for lo, hi in (ranges or []))
        context_result['requested_span_within_admitted_section'] = bool(contained)
        if not contained:
            reasons.append('requested_extent_outside_preserved_admission')
    if context.get('binding_count') == 0:
        reasons.append('no_existing_bindings')
    if context.get('deferred'):
        reasons.append('deferred_mask_retained')
    if context.get('proposal_note'):
        reasons.append('proposal_note_does_not_confirm_route_membership')
    if context.get('target_coordinates'):
        alignment = context_result['target_alignment']
        if (alignment['max_nearest_segment_undirected_angle_deg'] or 0) > ANGLE_DEG:
            reasons.append('target_direction_evidence_exceeds_unchanged_15_degree_gate')
        if not alignment['compatible_extents_screen']:
            reasons.append('target_extent_not_proved_by_full_way_identity')
    review = classification not in ('exact_match', 'exact_reversed_geometry') or bool(reasons)
    return {'key': record['key'], 'source': metadata(source), 'reference': metadata(reference),
            'identity_equal': same, 'geometry': {**m, 'exact_equal': exact, 'reversed_equal': reverse,
                'vertex_count_difference': len(b)-len(a),
                'changed_coordinate_indices_in_best_orientation': [i for i, (x, y) in enumerate(zip(a, b[::-1] if m['best_endpoint_orientation'] == 'reversed' else b)) if x != y] if len(a) == len(b) else None,
                'node_chain_comparison': node_status},
            'history': history, 'classification': classification,
            'route_identity': {'requested': route, 'status': route_state, 'evidence': record.get('route_evidence')},
            'mode_access': {'requested': mode, **access}, 'context': context_result,
            'attribution_eligibility': 'not_assessed_no_entitlement', 'manual_review': review, 'review_reasons': reasons,
            'new_completion_credit': False}


def run(path):
    raw = Path(path).read_bytes()
    data = json.loads(raw)
    if data.get('schema_version') != 1 or not isinstance(data.get('comparisons'), list) or len(data['comparisons']) > 100:
        raise ValueError('Expected schema 1 and <=100 bounded comparisons')
    records = data['comparisons']
    keys = [r['key'] for r in records]
    if len(keys) != len(set(keys)):
        raise ValueError('Duplicate comparison keys')
    results = [compare(r) for r in records]
    identities = {(f.get('provider'), str(f.get('feature_id'))) for r in records for f in (r['source'], r['reference'])}
    return {'schema_version': 1, 'policy': POLICY, 'input_sha256': hashlib.sha256(raw).hexdigest(),
            'input_provenance': data.get('provenance'), 'examined_feature_count': len(identities),
            'comparison_count': len(results), 'classification_counts': dict(sorted(Counter(r['classification'] for r in results).items())),
            'documented_geometry_update_count': sum(r['history']['status'] == 'documented_shared_node_movement' for r in results),
            'original_import_feature_versions_recovered': 0,
            'review_queue': [r['key'] for r in results if r['manual_review']],
            'attribution_eligibility': 'not_assessed_no_entitlement', 'results': results}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--input', required=True, type=Path)
    parser.add_argument('--output', type=Path, help='Otherwise print JSON to stdout; never overwrites input.')
    args = parser.parse_args()
    if args.output and args.output.resolve() == args.input.resolve():
        parser.error('Output must differ from input')
    result = run(args.input)
    output = json.dumps(result, indent=2, sort_keys=True, allow_nan=False)+'\n'
    if args.output:
        args.output.write_text(output)
    else:
        print(output, end='')


if __name__ == '__main__':
    main()
