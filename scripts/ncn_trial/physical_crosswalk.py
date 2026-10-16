"""Route-scoped physical correspondence diagnostics, never progress approval.

Inputs are metric lines in one projection. Both directions are measured: a short
source fragment cannot establish completeness of a much longer official route.
Nearby geometry, even with matching route numbers, remains a review candidate.
"""
import math
from shapely.ops import unary_union
from shapely.strtree import STRtree


def compare_route(ways, sections, route_type, route_no, radii=(3, 10, 25)):
    if route_type not in ('NCN', 'RCN') or type(route_no) is not int or route_no <= 0:
        raise ValueError('Explicit positive numbered NCN/RCN key required')
    if not radii or any(not math.isfinite(r) or r <= 0 for r in radii):
        raise ValueError('Positive finite diagnostic radii required')
    for entries, field in ((ways, 'way_id'), (sections, 'key')):
        if len({s[field] for s in entries}) != len(entries):
            raise ValueError('Duplicate input identity')
        for s in entries:
            line = s['line']
            if line.geom_type not in ('LineString', 'MultiLineString') or line.is_empty or not line.is_valid or line.length <= 0:
                raise ValueError('Non-empty valid metric lines required')
    same = [s for s in sections if s['route_type'] == route_type and s['route_no'] == route_no]
    if not ways or not same:
        raise ValueError('Source ways and matching official route sections required')
    source = unary_union([w['line'] for w in ways])
    official = unary_union([s['line'] for s in same])
    tree = STRtree([s['line'] for s in sections])
    records = []
    for w in ways:
        nearby = [sections[int(i)] for i in tree.query(w['line'].buffer(max(radii)))]
        records.append({'way_id': w['way_id'], 'length_m': w['line'].length,
            'candidate_section_keys_by_radius': {
                str(r): sorted(s['key'] for s in nearby
                    if s['route_type'] == route_type and s['route_no'] == route_no and s['line'].distance(w['line']) <= r)
                for r in radii},
            'other_route_section_keys': sorted(s['key'] for s in nearby
                if (s['route_type'], s['route_no']) != (route_type, route_no)
                and s['line'].distance(w['line']) <= max(radii)),
            'same_route_corridor_m_by_radius': {
                str(r): w['line'].intersection(unary_union([s['line'].buffer(r) for s in nearby
                    if s['route_type'] == route_type and s['route_no'] == route_no])).length
                for r in radii}, 'association_approved': False})
    sensitivity = {}
    for r in radii:
        source_near = source.intersection(official.buffer(r)).length
        official_near = official.intersection(source.buffer(r)).length
        sensitivity[str(r)] = {'source_near_official_m': source_near,
            'source_outside_official_m': max(0, source.length-source_near),
            'official_near_source_m': official_near,
            'official_without_source_m': max(0, official.length-official_near),
            'source_fraction': source_near/source.length,
            'official_fraction': official_near/official.length}
    return {'purpose': 'physical_correspondence_triage_only',
        'route_type': route_type, 'route_no': route_no,
        'source_unique_length_m': source.length, 'official_unique_length_m': official.length,
        'source_way_count': len(ways), 'official_section_count': len(same),
        'corridor_sensitivity': sensitivity, 'ways': records,
        'official_sections': [{'key': s['key'], 'length_m': s['line'].length,
            'near_source_m_by_radius': {str(r): s['line'].intersection(source.buffer(r)).length for r in radii}}
            for s in same],
        'association_approved': False, 'completion_ready': False,
        'access_verified': False,
        'limitations': ['Corridor overlap cannot distinguish parallel paths.',
            'Source membership, official status, alternatives and extent require separate review.',
            'Lengths depend on the caller-specified metric projection.']}


def main():
    import argparse
    import json
    from pathlib import Path
    from shapely.geometry import LineString, shape, box
    from .catalogue_extract import extract_route
    from .journey_replay import projector
    from .reference_reconciliation import read_json
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--network', type=Path, required=True)
    parser.add_argument('--expected-network-sha256', required=True)
    parser.add_argument('--source', type=Path, required=True)
    parser.add_argument('--relation-id', type=int, required=True)
    parser.add_argument('--route-type', choices=('NCN','RCN'), required=True)
    parser.add_argument('--route-no', type=int, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    network, digest = read_json(args.network)
    if digest != args.expected_network_sha256:
        raise ValueError('Network snapshot checksum mismatch')
    summary, features = extract_route([args.source.read_bytes()], args.relation_id)
    source, _ = read_json(args.source)
    root = next(e for e in source['elements'] if e['type']=='relation' and e['id']==args.relation_id)
    tags = root.get('tags',{})
    if tags.get('network') != args.route_type.lower() or tags.get('ref') != str(args.route_no):
        raise ValueError('Source route classification or number differs')
    target = [f for f in network['features'] if
        f['properties'].get('RouteType') == args.route_type and
        f['properties'].get('RouteNo') == args.route_no]
    if not target or not features:
        raise ValueError('Matching source and official geometry required')
    bounds = unary_union([shape(f['geometry']) for f in target+features]).bounds
    origin = [(bounds[0]+bounds[2])/2,(bounds[1]+bounds[3])/2]
    project = projector({'projection_origin':origin})
    area = box(bounds[0]-.01,bounds[1]-.01,bounds[2]+.01,bounds[3]+.01)
    sections=[]
    for f in network['features']:
        geom = shape(f['geometry'])
        if not geom.intersects(area):continue
        parts=[geom] if geom.geom_type=='LineString' else list(geom.geoms)
        for i, part in enumerate(parts):
            sections.append({'key':f"FID:{f['properties']['FID']}:{i}",
                'route_type':f['properties'].get('RouteType'),
                'route_no':f['properties'].get('RouteNo'),
                'line':LineString([project(c) for c in part.coords])})
    ways=[{'way_id':f['properties']['osm_way'],
           'line':LineString([project(c) for c in f['geometry']['coordinates']])} for f in features]
    result=compare_route(ways, sections, args.route_type, args.route_no)
    result.update({'source_summary':summary, 'input_sha256':{
        'network':digest,'source':summary['source_sha256']},
        'projection':{'kind':'local_equirectangular_approximate','origin':origin},
        'official_section_properties':{f"FID:{f['properties']['FID']}":f['properties'] for f in target}})
    args.output.write_text(json.dumps(result,indent=2)+'\n')
    print(json.dumps({k:v for k,v in result.items() if k not in
        ('ways','source_summary','official_section_properties')},indent=2))


if __name__=='__main__':main()
