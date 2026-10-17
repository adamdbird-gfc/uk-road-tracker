"""Locate source/official scope exceptions without granting matching credit.

Consumes pinned inventory plans. Corridors are diagnostic only; no geometry,
association, denominator or readiness flag is changed by this review.
"""
import gzip
import hashlib
import json
import math
from pathlib import Path
from shapely.geometry import LineString, mapping
from shapely.ops import unary_union, transform
from .inventory_completion import read_completion
from .journey_replay import projector


RADII = (3, 10, 25)


def line_parts(geometry):
    if geometry.is_empty:
        return []
    if geometry.geom_type == 'LineString':
        return [geometry] if geometry.length > 1e-7 else []
    return [part for child in getattr(geometry, 'geoms', ()) for part in line_parts(child)]


def inverse_projector(origin):
    lon, lat = origin
    xscale = 6371008.8 * math.cos(math.radians(lat))
    def inverse(x, y, z=None):
        return lon + math.degrees(x / xscale), lat + math.degrees(y / 6371008.8)
    return inverse


def review_scope(plan, associations, network):
    """Review a verified plan against every official feature of its numbered key.

    Lengths use the plan's local projection, are approximate and route-scoped.
    Official feature-group lengths can overlap and must not be added together.
    """
    key = plan['route_key']
    kind, number = key.split(':')
    if plan['scope'] != key + ':source-inventory' or plan['whole_published_route_ready'] is not False:
        raise ValueError('Captured source-inventory scope required')
    project = projector(plan['metadata'])
    inverse = inverse_projector(plan['metadata']['projection_origin'])
    section_geometry = {s['key']: LineString([project(c) for c in s['coordinates']]) for s in plan['sections']}
    inventory_sections = {k: g for k, g in section_geometry.items() if k.startswith('inventory:')}
    if not inventory_sections:
        raise ValueError('Complete source inventory geometry required')
    source = unary_union(list(inventory_sections.values()))
    selected = {mode: set() for mode in ('on_foot', 'cycling', 'combined')}
    for path in associations['paths']:
        for section in path['section_keys']:
            if section not in section_geometry or section.startswith('inventory:'):
                raise ValueError('Eligible association section required')
            selected['combined'].add(section)
            for mode in path['modes']:
                if mode in selected:
                    selected[mode].add(section)
    eligible = {mode: unary_union([section_geometry[k] for k in keys]) for mode, keys in selected.items()}
    official_rows = []
    buckets = {}
    for feature in network['features']:
        p = feature['properties']
        if p.get('RouteType') != kind or p.get('RouteNo') != int(number):
            continue
        g = feature['geometry']
        if g['type'] not in ('LineString', 'MultiLineString'):
            raise ValueError('Official line geometry required')
        parts = [g['coordinates']] if g['type'] == 'LineString' else g['coordinates']
        geometry = unary_union([LineString([project(c) for c in points]) for points in parts])
        group = (p.get('RouteCat'), p.get('OpenStatus'), p.get('Desc_'))
        qualifying = group[0] == 'Main Route' and group[1] == 'Open' and group[2] in ('TrafficFree', 'OnRoad')
        buckets.setdefault(group, []).append(geometry)
        official_rows.append((p, geometry, qualifying))
    if not official_rows:
        raise ValueError('Official route key absent')
    official = unary_union([g for p, g, ok in official_rows if ok])
    excluded = unary_union([g for p, g, ok in official_rows if not ok])
    series = []
    for radius in RADII:
        corridor = official.buffer(radius)
        source_gap = official.difference(source.buffer(radius))
        outside = source.difference(corridor)
        series.append({'corridor_m': radius,
            'official_open_main_without_source_m': source_gap.length,
            'source_outside_official_open_main_m': outside.length,
            'official_open_main_without_eligible_m': {
                mode: official.difference(geometry.buffer(radius)).length for mode, geometry in eligible.items()}})
    exceptions = []
    def add(geometry, exception, properties):
        for part in line_parts(geometry):
            exceptions.append({'type': 'Feature', 'properties': {
                'route_key': key, 'exception': exception, 'length_m': part.length,
                'diagnostic_only': True, 'corridor_m': (10 if exception in ('official_open_main_without_source', 'official_open_main_without_eligible', 'source_outside_official_open_main') else None), **properties}, 'geometry': mapping(transform(inverse, part))})
    source_buffer = source.buffer(10)
    official_buffer = official.buffer(10)
    eligible_buffer = eligible['combined'].buffer(10)
    for p, geometry, ok in official_rows:
        identity = {field: p.get(field) for field in ('FID', 'SegmentID', 'GlobalID', 'RouteCat', 'OpenStatus', 'Desc_')}
        if ok:
            add(geometry.difference(source_buffer), 'official_open_main_without_source', identity)
            add(geometry.difference(eligible_buffer), 'official_open_main_without_eligible', identity)
        else:
            add(geometry, 'official_nonqualifying_scope', identity)
    for section, geometry in inventory_sections.items():
        add(geometry.difference(official_buffer), 'source_outside_official_open_main', {'way_id': int(section.split(':')[1])})
        add(geometry.difference(eligible['combined']), 'source_without_eligible_binding', {'way_id': int(section.split(':')[1])})
    ten = series[1]
    reasons = []
    twenty_five = series[2]
    if plan['unmapped_inventory_m'] > 1e-5:
        reasons.append('inventory_matching_gaps')
    if official.is_empty:
        reasons.append('no_official_open_main_extent')
    if ten['official_open_main_without_source_m'] > 1:
        reasons.append('official_geometry_outside_source_corridor')
        reasons.append('official_geometry_gap_persists_at_25m' if twenty_five['official_open_main_without_source_m'] > 1 else 'offset_sensitive_official_geometry')
    if ten['source_outside_official_open_main_m'] > 1:
        reasons.append('source_geometry_outside_official_corridor')
        reasons.append('source_geometry_gap_persists_at_25m' if twenty_five['source_outside_official_open_main_m'] > 1 else 'offset_sensitive_source_geometry')
    if not excluded.is_empty:
        reasons.append('official_nonqualifying_scope_present')
    report = {'schema_version': 1, 'route_key': key, 'reference_version': plan['reference_version'],
        'scope': plan['scope'], 'whole_published_route_ready': False, 'association_approved_by_review': False,
        'diagnostic_only': True, 'corridors_grant_progress': False,
        'projection_origin': plan['metadata']['projection_origin'],
        'official_feature_count': len(official_rows), 'official_open_main_union_m': official.length,
        'source_inventory_union_m': source.length, 'canonical_inventory_length_m': plan['canonical_inventory_length_m'],
        'unmapped_inventory_m': plan['unmapped_inventory_m'],
        'maximum_supported_coverage_m': plan['maximum_supported_coverage_m'],
        'official_feature_groups': [{'route_category': group[0], 'open_status': group[1], 'description': group[2],
                                    'union_m': unary_union(parts).length, 'feature_count': len(parts)}
                                   for group, parts in sorted(buckets.items(), key=lambda item: str(item[0]))],
        'corridor_diagnostics': series, 'review_reasons': reasons,
        'exception_features': len(exceptions),
        'published_scope_requires_independent_review': True,
        'length_note': 'Approximate local-projection diagnostics. Groups, features, modes and routes can overlap; do not sum.'}
    return report, {'type': 'FeatureCollection', 'features': exceptions}


def inventory_manifests(plans):
    inventory_summary = json.loads((plans / 'summary.json').read_bytes())
    expected = [row['route_key'].replace(':', '-') for row in inventory_summary['routes']]
    manifests = sorted(plans.glob('*/manifest.json'))
    if not expected or len(set(expected)) != len(expected) or set(expected) != {p.parent.name for p in manifests}:
        raise ValueError('Inventory plan set incomplete or duplicated')
    return manifests


def main():
    import argparse
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--plans', type=Path, required=True)
    parser.add_argument('--network', type=Path, required=True)
    parser.add_argument('--expected-network-sha256', required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    network_raw = gzip.decompress(args.network.read_bytes())
    if hashlib.sha256(network_raw).hexdigest() != args.expected_network_sha256:
        raise ValueError('Official source checksum changed')
    network = json.loads(network_raw)
    rows = []
    inputs = []
    manifests = inventory_manifests(args.plans)
    for folder in manifests:
        manifest = json.loads(folder.read_bytes())
        raw = gzip.decompress((folder.parent / 'plan.json.gz').read_bytes())
        assoc = gzip.decompress((folder.parent / 'associations.json.gz').read_bytes())
        _, _, plan = read_completion(raw, assoc, manifest['plan_sha256'], manifest['association_sha256'])
        if plan['reference_version'] != manifest['reference_version'] or plan['metadata'].get('national_reference_sha256', args.expected_network_sha256) != args.expected_network_sha256:
            raise ValueError('Reference source changed; rebuild inventory')
        if plan['route_key'].replace(':', '-') != folder.parent.name:
            raise ValueError('Inventory route identity differs from manifest folder')
        report, features = review_scope(plan, json.loads(assoc), network)
        report['inputs'] = {'plan_sha256': manifest['plan_sha256'], 'association_sha256': manifest['association_sha256'],
                            'official_sha256': args.expected_network_sha256}
        inputs.append({'route_key': plan['route_key'], **report['inputs']})
        out = args.output / folder.parent.name
        out.mkdir(parents=True, exist_ok=True)
        for name, value in [('report.json.gz', report), ('exceptions.geojson.gz', features)]:
            (out / name).write_bytes(gzip.compress(json.dumps(value, separators=(',', ':')).encode(), mtime=0))
        rows.append(report)
    if not rows:
        raise ValueError('No inventory plans supplied')
    summary = {'schema_version': 1, 'routes_assessed': len(rows), 'diagnostic_only': True,
        'whole_published_route_ready': False, 'official_sha256': args.expected_network_sha256,
        'input_manifests': inputs, 'routes': rows}
    (args.output / 'summary.json').write_text(json.dumps(summary, indent=2) + '\n')
    print(json.dumps({'routes_assessed': len(rows), 'fully_bound_inventories': sum(r['unmapped_inventory_m'] < 1e-5 for r in rows),
        'routes_with_official_extent_gaps_over_1m': sum('official_geometry_outside_source_corridor' in r['review_reasons'] for r in rows),
        'routes_with_source_outside_official_over_1m': sum('source_geometry_outside_official_corridor' in r['review_reasons'] for r in rows)}))


if __name__ == '__main__':
    main()
