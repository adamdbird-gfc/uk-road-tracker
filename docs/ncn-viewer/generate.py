"""Generate a read-only offline viewer from immutable, bounded research receipts."""
import argparse
import hashlib
import importlib.util
import json
import math
from pathlib import Path

from shapely.geometry import LineString, Point, mapping
from shapely.ops import transform, unary_union

ROOT = Path(__file__).resolve().parents[2]
HERE = Path(__file__).resolve().parent
BASELINE = 'a87d60d4d5b2c4651c571e7f4316e514154734eb'
PINS = {
    'docs/data-trials/ncn1-deal-fixture-20261030.json': 'bb7fa4420151218c053cda1e93b6a592323e2c85a724965985b211d9386c779c',
    'docs/data-trials/ncn1-deal-results-20261030.json': '2976c849204b458576bd268f2386665c888d9a03e70890bf5746ced80aba3bdd',
    'docs/data-trials/ncn-projection-cases-20261030.json': '26a8b2aded983c65e7ba1c7fba1c3b04674604db64f8463908f9a19dfa5f0963',
    'docs/data-trials/ncn-projection-results-20261030.json': '43c23a8bddda07b7bf28f1a44c5e3ed058e4cd0e76550c7d2e31fcf55295c10a',
}
R = 6371008.8


def load_inputs():
    values = []
    for name, pin in PINS.items():
        raw = (ROOT / name).read_bytes()
        if hashlib.sha256(raw).hexdigest() != pin:
            raise ValueError('Evidence pin mismatch: ' + name)
        values.append(json.loads(raw))
    return values


def inverse(x, y, origin, space):
    lo, la = map(math.radians, origin)
    if space == 'eq':
        return origin[0] + math.degrees(x / (R * math.cos(la))), origin[1] + math.degrees(y / R)
    rho = math.hypot(x, y)
    if rho == 0:
        return tuple(origin)
    c = rho / R
    lat = math.asin(math.cos(c)*math.sin(la) + y*math.sin(c)*math.cos(la)/rho)
    lon = lo + math.atan2(x*math.sin(c), rho*math.cos(la)*math.cos(c)-y*math.sin(la)*math.sin(c))
    return math.degrees(lon), math.degrees(lat)


def audit_module():
    spec = importlib.util.spec_from_file_location('pinned_audit', ROOT / 'docs/data-trials/audit_ncn_projection_20261030.py')
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def feature(key, coordinates, layer, source_ref, route, status='unavailable', reason='not_replayed', **extra):
    geom = {'type': 'LineString', 'coordinates': coordinates}
    return {'type': 'Feature', 'id': key, 'geometry': geom, 'properties': {
        'layer': layer, 'source_id': key, 'route_id': route, 'status': status,
        'reason_code': reason, 'source_reference': source_ref,
        'geometry_sha256': hashlib.sha256(json.dumps(geom, separators=(',', ':')).encode()).hexdigest(),
        'coordinate_provenance': 'Pinned WGS84 longitude,latitude; see source_reference',
        'limitations': 'Diagnostic geometry does not confer completion eligibility.', **extra}}


def build():
    f, original, cases, results = load_inputs()
    audit = audit_module()
    deal = results['deal']
    origin = f['projection_origin']
    coords = f['path']['coordinates']
    # Replay cumulative full-way EQ offsets, keeping the historical station basis.
    xy = audit.project(coords, origin, 'eq')
    stations = [0.0]
    for a, b in zip(xy, xy[1:]):
        stations.append(stations[-1] + math.dist(a, b))
    assert max(abs(a-b) for a, b in zip(stations, original['source_positions_m'])) < 1e-7
    assert original['demonstrated_eligible_route_local_recovery_m'] == 0
    assert [d['edge'] for d in deal['differences'] if d['whole_edge_corridor_classification_changed']] == [6, 7, 8, 9, 10]
    ref = next(iter(PINS))
    features = []
    common_origin = coords[0]
    common_target = LineString(audit.project(f['target_original']['coordinates'], common_origin, 'aeqd'))
    common_lines = [LineString(audit.project(c, common_origin, 'aeqd')) for c in audit.parts(f['nearby_official_features'])]
    common_union = unary_union([g.buffer(10) for g in common_lines])
    for i in range(len(coords)-1):
        aeqd = deal['spaces']['aeqd']['edges'][i]
        eq = deal['spaces']['eq']['edges'][i]
        assert aeqd['actual_admitted'] == original['edge_results'][i]['admitted']
        common_xy = audit.project(coords[i:i+2], common_origin, 'aeqd')
        line = LineString(common_xy)
        outside = line.difference(common_union).length
        common = {'length_m': line.length, 'finite_target_min_distance_m': line.distance(common_target),
                  'finite_target_max_distance_m': max(Point(p).distance(common_target) for p in common_xy),
                  'outside_approved_union_10m_m': outside, 'whole_edge_approved_corridor_only': outside <= 1e-5,
                  'angle_deg': audit.angle(*common_xy, *common_target.coords),
                  'finite_pair': audit.pair(*common_xy, list(common_target.coords))}
        admitted = aeqd['actual_admitted']
        features.append(feature(f'4471747:edge:{i}', coords[i:i+2], 'admitted' if admitted else 'excluded',
            ref + '#path.coordinates/' + str(i), 'NCN:1', 'admitted' if admitted else 'excluded',
            'reviewed_provider_span' if admitted else 'unresolved_official_extent_edges',
            edge_index=i, osm_node_ids=f['path']['osm_node_ids'][i:i+2], source_id='4471747',
            original_source_geometry_sha256=f['path']['source_geometry_sha256'],
            station_m=original['source_positions_m'][i:i+2], station_basis='Historical route-centred EQ full-way offsets, not whole NCN1 chainage',
            measurements={'native_admission': aeqd, 'completion_space': eq, 'common_diagnostic': common},
            origins={'native_admission': origin, 'completion_space': origin, 'common_diagnostic': common_origin},
            origin_provenance={'native_admission': 'exact pinned admission_projection_origin',
                               'completion_space': 'exact pinned projection_origin',
                               'common_diagnostic': 'explicit first source vertex; diagnostic only'},
            thresholds={'corridor_m': 10, 'outside_epsilon_m': 1e-5, 'direction_deg': 15},
            predicate='Whole source edge outside approved nearby 10 m union <= 0.00001 m; finite pairs separately clip distance, direction and finite extent.',
            classification_changed=deal['differences'][i]['whole_edge_corridor_classification_changed'],
            correspondence=original['edge_results'][i],
            limitations=f['exclusion_limitation']))
    features.append(feature('recognised:4471747', coords, 'recognised', ref+'#compatibility_controls/0/reference', 'NCN:1',
                            'recognised', 'exact_walking_reference_match', original_provenance=f['compatibility_controls'][0]['reference']['provenance']))
    for a in f['adjoining_admitted_paths']:
        features.append(feature('adjoining:'+a['way_id'], a['coordinates'], 'recognised', ref+'#adjoining_admitted_paths', 'NCN:1',
                                'admitted', 'pinned_adjoining_admitted_path', original_source_geometry_sha256=a['source_geometry_sha256']))
    for official in f['nearby_official_features']:
        g = official['geometry']
        for j, chain in enumerate([g['coordinates']] if g['type']=='LineString' else g['coordinates']):
            features.append(feature(f'published:{official["id"]}:{j}', chain, 'target', ref+'#nearby_official_features', 'NCN:1',
                                    'published_open', 'approved_nearby_extent', publisher_properties=official['properties']))
    features.append(feature(f['target_key'], f['target_original']['coordinates'], 'focus_target', ref+'#target_original', 'NCN:1',
                            'published_open', 'investigated_target', target_length_m=f['target_length_m'],
                            existing_bindings=f['existing_target_bindings'], historical_classification=f['historical_target_classification']))
    private = f['compatibility_controls'][1]['reference']
    features.append(feature('77611523', private['coordinates'], 'negative', ref+'#compatibility_controls/1/reference', 'NCN:1',
                            'excluded', 'private_distinct_identity_no_confirmed_membership',
                            original_provenance=private['provenance'], tags=private['tags'], measurements=original['private_negative'],
                            origins={'native_admission': origin, 'completion_space': origin},
                            origin_provenance='exact pinned Deal origin; whole-path minimum distances, not whole-edge union test',
                            thresholds={'corridor_m':10}, station_m=None))
    # Derived corridor rings: inverse transforms preserve their true geographic location.
    for space in ('aeqd', 'eq'):
        union = unary_union([LineString(audit.project(c, origin, space)).buffer(10) for c in audit.parts(f['nearby_official_features'])])
        geographic = transform(lambda x,y: inverse(x,y,origin,space), union)
        features.append({'type':'Feature','id':'corridor:'+space,'geometry':mapping(geographic),'properties':{
            'layer':'corridor_'+space, 'source_id':'approved union 1079/1820/10909', 'route_id':'NCN:1',
            'status':'diagnostic_only','reason_code':'derived_10m_buffer','source_reference':ref+'#nearby_official_features',
            'geometry_sha256':hashlib.sha256(union.wkb).hexdigest(), 'hash_basis':'projected union WKB',
            'coordinate_provenance':f'Derived: pinned WGS84 -> spherical {space} at {origin} -> Shapely 10 m buffer -> inverse spherical {space}',
            'origin':origin, 'threshold_m':10, 'limitations':'Approved nearby union only; planar buffer discretisation, not surveyed boundaries.'}})
    output_cases = [{'key':'deal','name':'Deal · NCN1 · projection discrepancy','route':'NCN:1', 'features':features,
        'summary':'Five held edges pass the corridor test in completion space and fail it in admission space. Their recorded exclusion remains justified.',
        'limitations': f['exclusion_limitation'], 'timeline_basis':'Full OSM way 4471747, historical EQ metres from vertex 0. Continuous bounded corridor; not NCN1 national chainage.',
        'origin_provenance':'Exact pinned route origins; common local origin explicitly chosen from the first pinned source vertex.',
        'timeline_extent_m':original['source_positions_m'][-1], 'default_feature':'4471747:edge:7',
        'target_length_m':f['target_length_m'], 'existing_target_coverage_m':original['existing_target_coverage_m'],
        'historical_geometric_ceiling_m':original['geometric_only_unadmitted_upper_bound_m'],
        'demonstrated_additional_eligible_recovery_m':0, 'attribution':f['association_provenance']}]
    bykey = {r['key']:r for r in results['cross_route']}
    for c in cases['cases']:
        if c['key'] in ('4471747','deal-private-parallel'):
            continue
        reference = 'docs/data-trials/ncn-projection-cases-20261030.json#cases/key='+c['key']
        receipt = bykey[c['key']]
        fs = [feature('source:'+c['key'], c['source'], 'recognised', reference+'/source', c['route'],
                      'unavailable', 'whole_way_admission_not_replayed', evidence=c, projection_receipt=receipt,
                      limitations=c['eligibility_note'], origin_provenance=c['origin_provenance'])]
        fs.append(feature('reference:'+c['key'],c['reference'],'reference',reference+'/reference',c['route'],
                          'recognised','pinned_reference', limitations=c['eligibility_note']))
        for i, group in enumerate(c['target_pairs']):
            for j, chain in enumerate(group['targets']):
                fs.append(feature(f'target:{i}:{j}',chain,'target',reference+f'/target_pairs/{i}/targets/{j}',c['route'],
                    'diagnostic_only','pinned_target_pair', coordinate_provenance=c['origin_provenance']+'; '+group.get('precision_note','Derived from pinned witness metric coordinates where noted in evidence.'),
                    limitations=c['eligibility_note']))
        # Evidence does not assign every edge an operational status. Keep the entire distance bar unavailable.
        local = audit.project(c['source'], c['source'][0], 'aeqd')
        length = sum(math.dist(a,b) for a,b in zip(local,local[1:]))
        fs[0]['properties']['station_m']=[0,length]
        output_cases.append({'key':c['key'],'name':c['name'],'route':c['route'],'features':fs,
            'summary':c['eligibility_note'], 'limitations':c['origin_provenance']+'; '+cases['limitation'],
            'origin_provenance':c['origin_provenance'], 'timeline_extent_m':length,
            'timeline_basis':'Pinned source chain, cumulative local AEQD metres. Per-edge operational statuses unavailable; no admission inference.',
            'default_feature':fs[0]['id'], 'target_unavailable':not c['target_pairs'],
            'demonstrated_additional_eligible_recovery_m':0})
    return {'schema_version':1,'contract':'roadprints.ncn.visual-diagnostics','baseline':BASELINE,
            'input_sha256':PINS, 'crs':'WGS84 longitude,latitude (derived rings explicitly labelled)',
            'diagnostic_only':True,'cases':output_cases}


def generate(output):
    data = build()
    output = Path(output)
    output.mkdir(parents=True, exist_ok=True)
    (output/'evidence.json').write_text(json.dumps(data,indent=2,sort_keys=True)+'\n')
    payload = json.dumps(data,separators=(',',':')).replace('<','\\u003c')
    html = (HERE/'viewer.template.html').read_text().replace('__EVIDENCE_JSON__',payload)
    (output/'index.html').write_text(html)
    return data


if __name__ == '__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output',default=str(HERE/'demo'))
    args=parser.parse_args()
    data=generate(args.output)
    print(f'Wrote {len(data["cases"])} pinned cases to {args.output}/index.html')
