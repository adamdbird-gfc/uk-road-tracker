"""Offline NCN99 discrepancy audit; proximity never admits collection paths."""
import argparse
import gzip
import hashlib
import json
from pathlib import Path

from shapely.geometry import LineString
from shapely.ops import unary_union

from .journey_replay import projector


def review(folder, expected_manifest_sha256):
    folder = Path(folder).resolve()
    raw = (folder / 'input-pins.json').read_bytes()
    if hashlib.sha256(raw).hexdigest() != expected_manifest_sha256:
        raise ValueError('Scope-review pins changed')
    pins = json.loads(raw)
    inputs = {}
    for row in pins['inputs']:
        path = (folder / row['file']).resolve()
        if not path.is_relative_to(folder):
            raise ValueError('Input escapes evidence folder')
        data = path.read_bytes()
        if hashlib.sha256(data).hexdigest() != row['sha256']:
            raise ValueError('Scope-review input changed')
        if path.suffix == '.gz':
            data = gzip.decompress(data)
        if row.get('role'):
            inputs.setdefault(row['role'], []).append(json.loads(data))
    extent, = inputs['extent']
    associations, = inputs['associations']
    reference, = inputs['reference']
    source, = inputs['source']
    if extent['route_key'] != 'NCN:99' or extent['completion_binding_ready'] is not False:
        raise ValueError('Expected unbound NCN99 open extent')
    project = projector(extent)
    supported = unary_union([
        LineString([project(c) for c in row['coordinates']])
        for row in reference['sections']
    ])
    root, = [e for e in source['elements'] if e['type'] == 'relation' and e['id'] == 931223]
    if root['tags'].get('network') != 'ncn' or root['tags'].get('ref') != '99':
        raise ValueError('NCN99 source identity changed')
    members = {m['ref'] for m in root['members'] if m['type'] == 'way'}
    index = {}
    for area in inputs['area']:
        if area.get('remark') or not area.get('elements'):
            raise ValueError('Incomplete area response')
        for element in area['elements']:
            key = element['type'], element['id']
            if key in index and index[key] != element:
                raise ValueError('Conflicting area element snapshots')
            index[key] = element
    nearby = []
    for (kind, identity), way in index.items():
        if kind != 'way' or not way.get('tags', {}).get('highway'):
            continue
        if any(('node', n) not in index for n in way['nodes']):
            continue  # Partial map-boundary ways cannot supply full geometry.
        coordinates = [[index['node', n]['lon'], index['node', n]['lat']] for n in way['nodes']]
        if len(coordinates) > 1:
            nearby.append((identity, way['tags'], LineString([project(c) for c in coordinates])))
    rows, lines = [], []
    decisions = {
        57295: ('bangor_newtownards_ballyrickard', 'geometry_and_source_membership_hold'),
        55854: ('belfast_middlepath_street', 'unlisted_source_membership_hold'),
        33298: ('belfast_ballymacarrett_walkway', 'unlisted_source_membership_hold'),
    }
    for feature in extent['features']:
        prop = feature['properties']
        if (prop['OpenStatus'], prop['RouteCat'], prop['RouteNo']) != ('Open', 'Main Route', 99):
            raise ValueError('Unexpected target classification')
        line = LineString([project(c) for c in feature['geometry']['coordinates']])
        lines.append(line)
        candidates = []
        for wid, tags, mapped in nearby:
            overlap = mapped.intersection(line.buffer(10)).length
            if overlap >= 40:
                candidates.append({'way_id': wid, 'highway': tags['highway'],
                                   'name': tags.get('name'), 'source_relation_member': wid in members,
                                   'mapped_length_inside_target_10m_m': overlap})
        segment = prop['SegmentID']
        location, determination = decisions.get(segment, ('belfast_comber', 'retain_existing_review'))
        rows.append({'segment_id': segment, 'capture_feature_id': feature['id'],
                     'location': location, 'determination': determination,
                     'official_open_length_m': line.length,
                     'outside_supported_10m_m': line.difference(supported.buffer(10)).length,
                     'start_lon_lat': feature['geometry']['coordinates'][0],
                     'end_lon_lat': feature['geometry']['coordinates'][-1],
                     'vertex_count': len(feature['geometry']['coordinates']),
                     'mapped_candidates_diagnostic_only': sorted(candidates, key=lambda c: -c['mapped_length_inside_target_10m_m'])[:20]})
    official = unary_union(lines)
    gap = official.difference(supported.buffer(10)).length
    if abs(official.length - extent['canonical_open_union_m']) > 0.001:
        raise ValueError('Official open denominator differs')
    if len(rows) != 20 or len(members) != 53 or len(associations['paths']) != 48:
        raise ValueError('Pinned baseline scope differs')
    return {'schema_version': 1, 'route_key': 'NCN:99',
            'input_pins_sha256': expected_manifest_sha256,
            'official_open_union_m': official.length, 'supported_source_union_m': supported.length,
            'outside_supported_10m_m': gap,
            'feature_residual_sum_m': sum(r['outside_supported_10m_m'] for r in rows),
            'feature_residual_sum_is_union': False,
            'source_member_way_count': len(members), 'supported_way_count': 48,
            'admitted_new_ways': 0, 'removed_official_open_features': 0,
            'completion_binding_ready': False,
            'whole_route_activation_blocked': True,
            'policy': 'Retain official open extent pending bounded adjudication; exclude planned links; nearby mapped paths are not NCN membership evidence.',
            'features': rows}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('folder')
    parser.add_argument('--expected-manifest-sha256', required=True)
    parser.add_argument('--output', required=True)
    args = parser.parse_args()
    result = review(args.folder, args.expected_manifest_sha256)
    Path(args.output).write_text(json.dumps(result, indent=2) + '\n')
