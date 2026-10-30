"""Additive offline geometry reports. Never writes admissions, bindings or credit.

CLI consumes the nine frozen PR #247 cases; report_case also accepts that schema.
No missing route origin is replaced with a local origin. Common local EQ is a
separate, explicitly derived diagnostic space, never an operational replay.
"""
import argparse
from copy import deepcopy
import hashlib
import json
import math
from pathlib import Path

from shapely.geometry import LineString, Point
from shapely.ops import unary_union
from scripts.ncn_source_compatibility import digest, coordinates
from scripts.ncn_trial.network_geometry import metric_projector
from scripts.ncn_trial.journey_replay import projector
from scripts.ncn_trial.open_completion import edge_interval, ordinary_seam_partition
from collections import Counter
from scripts.ncn_source_compatibility import angle, vector

ROOT = Path(__file__).resolve().parents[1]
CASE_PATH = 'docs/data-trials/ncn-projection-cases-20261030.json'
DEAL_PATH = 'docs/data-trials/ncn1-deal-fixture-20261030.json'
PINS = {
    CASE_PATH: '26a8b2aded983c65e7ba1c7fba1c3b04674604db64f8463908f9a19dfa5f0963',
    DEAL_PATH: 'bb7fa4420151218c053cda1e93b6a592323e2c85a724965985b211d9386c779c',
}


def dumps(value):
    return json.dumps(value, indent=2, sort_keys=True, allow_nan=False)+'\n'


def projection(space, origin, status, evidence):
    if space not in ('spherical_aeqd', 'spherical_eq'):
        raise ValueError('Unknown projection formula')
    if status not in ('verified', 'inferred', 'unavailable'):
        raise ValueError('Origin status must be explicit')
    if (origin is None) != (status == 'unavailable'):
        raise ValueError('Unavailable origins must remain null')
    if origin is not None and (not isinstance(origin,list) or len(origin)!=2 or any(isinstance(v,bool) or not isinstance(v,(int,float)) or not math.isfinite(v) for v in origin) or not -180 <= origin[0] <= 180 or not -90 <= origin[1] <= 90):
        raise ValueError('Invalid projection origin')
    return {'formula': space, 'origin_lon_lat': origin, 'origin_status': status,
            'origin_evidence': evidence, 'radius_m': 6371008.8,
            'units': {'distance': 'metres', 'direction': 'undirected degrees'}}


def origin_status(case):
    if case['operational_origin'] is None:
        return 'unavailable'
    if case['origin_provenance'].startswith('uniquely inferred'):
        return 'inferred'
    if case['origin_provenance'] in ('exact PR246 fixture', 'pinned source-section fixture'):
        return 'verified'
    raise ValueError('Unrecognised origin evidence; do not guess')


def measure(case, description, approved_features=None):
    origin = description['origin_lon_lat']
    if origin is None:
        return {'status': 'unavailable', 'reason': 'Native route origin missing; no substituted origin.', 'values': None}
    convert = metric_projector(origin) if description['formula'] == 'spherical_aeqd' else projector({'projection_origin': origin})
    def xy(points):
        coordinates({'coordinates': points})
        return [convert(p) for p in points]
    source, reference = LineString(xy(case['source'])), LineString(xy(case['reference']))
    rows = []
    for group_index, group in enumerate(case['target_pairs']):
        points = xy(group['source']); targets = [LineString(xy(t)) for t in group['targets']]
        for i, (a, z) in enumerate(zip(points, points[1:])):
            segment = LineString([a, z])
            for j, target in enumerate(targets):
                theta = angle(vector(a, z), vector(*target.coords))
                interval = edge_interval(a, z, target)
                rows.append({'group': group_index, 'source_edge': i+group['source_start_vertex'],
                             'target_index': j, 'target_key': (group.get('target_keys') or [None]*len(targets))[j],
                             'source_geometry_sha256': digest(group['source']),
                             'target_geometry_sha256': digest(group['targets'][j]),
                             'min_distance_m': segment.distance(target),
                             'max_endpoint_distance_m': max(Point(a).distance(target), Point(z).distance(target)),
                             'direction_deg': theta, 'direction_gate_pass': theta is not None and theta <= 15,
                             'finite_pair_present': interval is not None,
                             'finite_pair_local_stations_m': list(interval) if interval else None})
    topology = None
    for group in case['target_pairs']:
        if group.get('topology_control'):
            a, z = xy(group['source'])
            targets = [(str(i), LineString(xy(t))) for i, t in enumerate(group['targets'])]
            active = [(i, *v) for i, (_, t) in enumerate(targets) if (v := edge_interval(a, z, t))]
            if len(active) == 2:
                low, high = max(v[1] for v in active), min(v[2] for v in active)
                degrees = Counter(tuple(p) for _, t in targets for p in t.coords)
                topology = {'active_pairs': len(active), 'ordinary_seam_rejected': ordinary_seam_partition(a,z,active,targets,degrees,low,high) is None}
            else:
                topology = {'active_pairs': len(active), 'ordinary_seam_rejected': None}
    whole = None
    if approved_features is not None:
        lines = []
        for feature in approved_features:
            g = feature['geometry']
            for chain in ([g['coordinates']] if g['type'] == 'LineString' else g['coordinates']):
                lines.append(LineString(xy(chain)))
        corridor = unary_union([line.buffer(10) for line in lines])
        points = xy(case['source'])
        whole = []
        for i, (a, z) in enumerate(zip(points, points[1:])):
            outside = LineString([a,z]).difference(corridor).length
            whole.append({'source_edge': i, 'outside_approved_union_m': outside,
                          'necessary_corridor_pass': outside <= 1e-5})
    return {'status': 'diagnostic_only', 'values': {
        'source_length_m': source.length, 'reference_length_m': reference.length,
        'continuous_source_reference_hausdorff_m': source.hausdorff_distance(reference),
        'continuous_source_reference_min_distance_m': source.distance(reference),
        'finite_target_pairs': rows, 'topology': topology,
        'whole_edge_approved_union': whole}}


def report_case(case, deal=None):
    status = origin_status(case)
    native = {p: projection('spherical_'+p, case['operational_origin'], status, case['origin_provenance']) for p in ('aeqd','eq')}
    common = projection('spherical_eq', case['source'][0], 'verified', 'Explicit diagnostic convention: first source vertex; not recovered route metadata.')
    approved = deal['nearby_official_features'] if deal is not None and case['key']=='4471747' else None
    measurements = {name: {'projection': desc, **measure(case,desc,approved)} for name,desc in
                    [('native_admission_aeqd',native['aeqd']), ('native_correspondence_eq',native['eq']), ('common_local_eq',common)]}
    changes = None
    if status != 'unavailable':
        a, e = [measurements[n]['values'] for n in ('native_admission_aeqd','native_correspondence_eq')]
        changes = {'finite_pair_presence': sum(x['finite_pair_present'] != y['finite_pair_present'] for x,y in zip(a['finite_target_pairs'],e['finite_target_pairs'])),
                   'whole_edge_corridor': None if a['whole_edge_approved_union'] is None else sum(x['necessary_corridor_pass']!=y['necessary_corridor_pass'] for x,y in zip(a['whole_edge_approved_union'],e['whole_edge_approved_union']))}
    operational = {k: deepcopy(case.get(k)) for k in ('actual_admitted_source_present','existing_binding_count','admitted_vertex_ranges','compatibility','history_status','eligibility_note')}
    if deal is not None and case['key']=='4471747':
        operational['reviewed_source_spans'] = deepcopy(deal['identity_review_spans'])
    restrictions = {'identity_and_access':deepcopy(case['identity_access']), 'route_identity':deepcopy(case['compatibility']['route_identity']),
                    'mode_access':deepcopy(case['compatibility']['mode_access']),
                    'topology': 'Hairpin ambiguity retained' if case['key']=='44807950' else 'No new topology clearance; preserved restrictions remain authoritative',
                    'deferred_masks': 'Not replayed; no mask override permitted',
                    'whole_edge_policy': 'Retained; finite pieces cannot extend admission',
                    'source_offsets': 'Unchanged; measurement stations must never replace stored offsets'}
    incomplete = status != 'verified' or any(g.get('precision_note') for g in case['target_pairs'])
    return {'key':case['key'], 'name':case['name'], 'route':case['route'],
            'source_feature': deepcopy(case['identity_access']),
            'geometry_sha256': {k:digest(case[k]) for k in ('source','reference','target_pairs')},
            'operational_result': {'status':'preserved_pinned_record_not_new_admission_replay', 'record':operational},
            'diagnostic_comparison': {'hypothetical_only':True, 'exact_operational_replay':False,
                'origin_comparison_status':status, 'limitations': [case['origin_provenance'],case['eligibility_note']]+[g['precision_note'] for g in case['target_pairs'] if g.get('precision_note')],
                'measurements':measurements, 'hypothetical_alternative_projection_changes':changes},
            'restrictions': restrictions,
            'attribution_eligibility': {'status':'not_assessed_no_entitlement', 'new_assessable_m':0, 'new_completion_credit_m':0},
            'further_investigation': {'supported':bool(changes and any(v for v in changes.values() if v is not None)) or incomplete or case['history_status']=='documented_shared_node_movement',
                'blocks_ordinary_development_or_publication_review':False,
                'reason':'Projection sensitivity or provenance limitation may guide a specific future case; no additional eligible interval demonstrated and no general blocker established.'}}


def run(root=ROOT):
    data = {}
    for name, expected in PINS.items():
        raw = (root/name).read_bytes()
        if hashlib.sha256(raw).hexdigest() != expected:
            raise ValueError('Pinned input changed: '+name)
        data[name] = json.loads(raw)
    cases = data[CASE_PATH]
    return {'schema_version':1, 'policy':'ncn-geometry-diagnostics-v1',
            'input_snapshots_sha256':{**cases['input_sha256'], **PINS},
            'predicates': {'finite_pair':'Existing edge_interval: finite along-target clipping, perpendicular corridor <=10 m, undirected angle <=15 degrees, nonzero interval >1e-7 m.',
                'whole_edge':'Necessary approved-union corridor only: Shapely buffer(10), outside length <=1e-5 m; not full admission.',
                'source_reference':'Continuous planar Hausdorff and minimum distance; not sampled source-version classification.',
                'direction':'Undirected planar segment angle in degrees; separate from topology/identity/access.'},
            'cases':[report_case(c,data[DEAL_PATH]) for c in cases['cases']],
            'operational_changes':False, 'new_assessable_m':0, 'new_completion_credit_m':0}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    # Only create a new report. Never overwrite an input, catalogue or prior receipt.
    result = dumps(run())
    with args.output.open('x') as stream:
        stream.write(result)


if __name__ == '__main__':
    main()
