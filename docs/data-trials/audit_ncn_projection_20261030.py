"""Bounded offline research. Writes diagnostic JSON only; never admissions or credit.

Run from repository root: PYTHONPATH=. python docs/data-trials/audit_ncn_projection_20261030.py --output /tmp/ncn-metric-results.json
"""
import argparse
import hashlib
import json
import math
from pathlib import Path
from collections import Counter
from shapely.geometry import LineString, Point
from shapely.ops import unary_union
from scripts.ncn_trial.network_geometry import metric_projector
from scripts.ncn_trial.journey_replay import projector
from scripts.ncn_trial.open_completion import edge_interval, ordinary_seam_partition
from scripts.ncn_source_compatibility import compare as compatibility_compare

ROOT = Path(__file__).resolve().parents[2]
R = 6371008.8
PINS = {
 'docs/data-trials/ncn1-deal-fixture-20261030.json': 'bb7fa4420151218c053cda1e93b6a592323e2c85a724965985b211d9386c779c',
 'tests/fixtures/ncn-source-compatibility/controls.json': '113bf75bbe0d89f1fa64b65d732eee4e94e43dbb533b619c86d29c27a445244e',
 'tests/fixtures/ncn-gap-seams/diagnostic-witnesses.json': '9b741fa37ef7f74e29269ed77de2d9d0068ed54744b18775b1a994b49a06612b',
 'tests/fixtures/ncn-binding-attainability/source-sections.json': '5e42cc0058ed71a4bead0ac9636e757e289f3aa1bc8e57b18be95bd31c57a91b',
}


def read_inputs():
    values = {}
    for name, pin in PINS.items():
        raw = (ROOT/name).read_bytes()
        assert hashlib.sha256(raw).hexdigest() == pin, name
        values[name] = json.loads(raw)
    return values


def independent_project(p, origin, space):
    """Independent formula implementation, checked against repository functions."""
    lon, lat = map(math.radians, p)
    lo, la = map(math.radians, origin)
    if space == 'eq':
        return R*math.radians(p[0]-origin[0])*math.cos(la), R*math.radians(p[1]-origin[1])
    d = lon-lo
    c = math.acos(max(-1., min(1., math.sin(la)*math.sin(lat)+math.cos(la)*math.cos(lat)*math.cos(d))))
    k = c/math.sin(c) if c >= 1e-12 else 1.
    return R*k*math.cos(lat)*math.sin(d), R*k*(math.cos(la)*math.sin(lat)-math.sin(la)*math.cos(lat)*math.cos(d))


def project(points, origin, space):
    f = metric_projector(origin) if space == 'aeqd' else projector({'projection_origin': origin})
    xy = [independent_project(p, origin, space) for p in points]
    assert all(math.dist(a, f(p)) < 1e-7 for a, p in zip(xy, points))
    return xy


def angle(a, z, q, b):
    u, v = (z[0]-a[0], z[1]-a[1]), (b[0]-q[0], b[1]-q[1])
    n = math.hypot(*u)*math.hypot(*v)
    return math.degrees(math.acos(min(1., abs((u[0]*v[0]+u[1]*v[1])/n)))) if n else None


def finite_pair(a, z, q, b):
    """Independent clipping of source parameter t in [0,1], using dot/cross gates."""
    u = (z[0]-a[0], z[1]-a[1]); v = (b[0]-q[0], b[1]-q[1])
    source_length, target_length = math.hypot(*u), math.hypot(*v)
    if min(source_length, target_length) <= 1e-7:
        return None
    ex, ey = v[0]/target_length, v[1]/target_length
    dot = u[0]*ex+u[1]*ey
    if abs(dot/source_length) < math.cos(math.radians(15)):
        return None
    along = (a[0]-q[0])*ex+(a[1]-q[1])*ey
    cross = (a[0]-q[0])*ey-(a[1]-q[1])*ex
    change = u[0]*ey-u[1]*ex
    low, high = 0., 1.
    for value, slope, first, last in ((along, dot, 0., target_length), (cross, change, -10., 10.)):
        if abs(slope) < 1e-12:
            if not first <= value <= last:
                return None
        else:
            x, y = sorted(((first-value)/slope, (last-value)/slope))
            low, high = max(low, x), min(high, y)
    if (high-low)*source_length <= 1e-7:
        return None
    return [low*source_length, high*source_length, max(0., min(target_length, along+low*dot)), max(0., min(target_length, along+high*dot))]


def pair(a, z, target):
    independent = finite_pair(a, z, *target)
    existing = edge_interval(a, z, LineString(target))
    assert (independent is None) == (existing is None)
    if existing:
        assert max(abs(a-b) for a, b in zip(independent, existing)) < 1e-7
    return independent


def parts(features):
    for f in features:
        g = f['geometry']
        yield from ([g['coordinates']] if g['type'] == 'LineString' else g['coordinates'])


def deal(f):
    coords = f['path']['coordinates']; origin = f['projection_origin']
    assert origin == f['admission_projection_origin']
    allowed = {i for s in f['identity_review_spans'] for i in range(s['from_vertex'], s['to_vertex'])}
    spaces = {}
    for space in ('aeqd', 'eq'):
        xy = project(coords, origin, space)
        target = project(f['target_original']['coordinates'], origin, space)
        approved = [LineString(project(c, origin, space)) for c in parts(f['nearby_official_features'])]
        corridor = unary_union([line.buffer(10) for line in approved])
        edges = []
        for i, (a, z) in enumerate(zip(xy, xy[1:])):
            line = LineString([a, z]); t = LineString(target)
            outside = line.difference(corridor).length
            edges.append({'edge': i, 'length_m': line.length,
                'finite_target_min_distance_m': line.distance(t),
                'finite_target_max_distance_m': max(Point(a).distance(t), Point(z).distance(t)),
                'outside_approved_union_10m_m': outside,
                'whole_edge_approved_corridor_only': outside <= 1e-5,
                'angle_deg': angle(a, z, *target), 'finite_pair': pair(a, z, target),
                'actual_admitted': i in allowed})
        spaces[space] = {'edges': edges, 'vertices_finite_target_distance_m': [Point(p).distance(LineString(target)) for p in xy],
            'private_min_distance_m': LineString(project(f['compatibility_controls'][1]['reference']['coordinates'], origin, space)).distance(LineString(target))}
    diffs = []
    for a, e in zip(spaces['aeqd']['edges'], spaces['eq']['edges']):
        diffs.append({'edge': a['edge'], 'eq_minus_aeqd_max_distance_m': e['finite_target_max_distance_m']-a['finite_target_max_distance_m'],
            'eq_minus_aeqd_angle_deg': e['angle_deg']-a['angle_deg'],
            'whole_edge_corridor_classification_changed': a['whole_edge_approved_corridor_only'] != e['whole_edge_approved_corridor_only'],
            'finite_pair_presence_changed': (a['finite_pair'] is None) != (e['finite_pair'] is None)})
    assert [i for i in range(13) if not spaces['aeqd']['edges'][i]['whole_edge_approved_corridor_only']] == [6,7,8,9,10]
    assert all(spaces['eq']['edges'][i]['whole_edge_approved_corridor_only'] for i in range(13))
    assert spaces['aeqd']['private_min_distance_m'] > 10 > spaces['eq']['private_min_distance_m']
    # Same interpolation fractions in both spaces: eliminates resampling as a cause.
    samples = []
    for i in range(6,11):
        count = math.ceil(max(spaces[s]['edges'][i]['length_m'] for s in spaces))
        distances = {}
        for space in spaces:
            a, z = project(coords[i:i+2], origin, space)
            t = LineString(project(f['target_original']['coordinates'], origin, space))
            distances[space] = [Point(a[0]+(z[0]-a[0])*j/count, a[1]+(z[1]-a[1])*j/count).distance(t) for j in range(count+1)]
        samples.append({'edge': i, 'same_parameter_sample_count': count+1,
            'aeqd_samples_over_10m': sum(d>10 for d in distances['aeqd']),
            'eq_samples_over_10m': sum(d>10 for d in distances['eq']),
            'maximum_sample_distance_difference_m': max(abs(a-b) for a,b in zip(distances['aeqd'],distances['eq']))})
    return {'origin': origin, 'spaces': spaces, 'differences': diffs, 'common_parameter_sampling': samples,
        'actual_held_edges': [6,7,8,9,10], 'hypothetical_eq_corridor_pass_does_not_override_identity_review': True,
        'private_eligible': False, 'new_credit_m': 0,
        'limits': 'Approved nearby feature set only. No complete excluded-feature set, identity review rewrite, admission replay or new entitlement.'}


def recover_witness_origin(w, coordinates):
    """Infer origin only when a unique original source edge replays both XY endpoints."""
    metric = w['source_edge_metric']; matches = []
    for i in range(len(coordinates)-1):
        for reverse in (False, True):
            a, z = coordinates[i:i+2][::(-1 if reverse else 1)]
            lat0 = a[1]-math.degrees(metric[0][1]/R)
            lon0 = a[0]-math.degrees(metric[0][0]/(R*math.cos(math.radians(lat0))))
            origin = [lon0, lat0]
            error = max(math.dist(x,y) for x,y in zip(project([a,z],origin,'eq'),metric))
            if error < 1e-7:
                matches.append((origin,i,reverse,error,[a,z]))
    assert len(matches) == 1, (w['source_way_id'], matches)
    return matches[0]


def inverse_eq(xy, origin):
    return [[origin[0]+math.degrees(x/(R*math.cos(math.radians(origin[1])))), origin[1]+math.degrees(y/R)] for x,y in xy]


def prepare_cross(inputs):
    controls = inputs['tests/fixtures/ncn-source-compatibility/controls.json']['comparisons']
    sections = inputs['tests/fixtures/ncn-binding-attainability/source-sections.json']['routes']
    wdata = inputs['tests/fixtures/ncn-gap-seams/diagnostic-witnesses.json']
    witnesses = {w['source_way_id']:w for w in [*wdata['ordinary_candidates'],wdata['genuine_hairpin_negative']]}
    origins = {s['path']['way_id']:s['plan']['projection_origin'] for s in sections}
    names = {'4471747':'Deal NCN1', '59306268':'Newry Canal Towpath NCN9', '31252441':'Mid-Wales NCN8',
        '44807950':'West Midlands NCN81 hairpin', '1050757473':'London NCN1 Mudlarks Boulevard',
        '159311690':'Bradford NCN66', '23385711':'Cumbria NCN6/NCN7 identity',
        '1289002662':'East Sussex source-version update RCN90', 'deal-private-parallel':'Deal private parallel negative'}
    cases = []
    for c in controls:
        key = c['key']; origin = origins.get(key)
        diagnostic = compatibility_compare(c)
        case = {'key': key, 'name': names[key], 'route': c['route'],
            'source': c['source']['coordinates'], 'reference': c['reference']['coordinates'],
            'actual_admitted_source_present': c.get('context',{}).get('admitted_ncn_source_present'),
            'existing_binding_count': c.get('context',{}).get('binding_count'),
            'eligibility_note': 'Preserved evidence only; source presence is not whole-way admission or target entitlement.',
            'target_pairs': [],
            'identity_access': {'source_provider':c['source']['provider'], 'source_id':c['source']['feature_id'],
                'reference_provider':c['reference']['provider'], 'reference_id':c['reference']['feature_id'],
                'source_memberships':c['source'].get('route_memberships'), 'reference_tags':c['reference'].get('tags')},
            'compatibility': {k:diagnostic[k] for k in ('classification','route_identity','mode_access','attribution_eligibility')},
            'history_status':diagnostic['history']['status']}
        if key == 'deal-private-parallel':
            case['actual_admitted_source_present'] = False
            case['eligibility_note'] = 'Distinct private identity; no confirmed NCN membership; no geometric identity transfer.'
        if key in ('4471747','deal-private-parallel'):
            f = inputs['docs/data-trials/ncn1-deal-fixture-20261030.json']; origin=f['projection_origin']
            case['origin_provenance']='exact PR246 fixture'
            line = case['reference'] if key == 'deal-private-parallel' else case['source']
            case['target_pairs']=[{'source':line,'targets':[f['target_original']['coordinates']], 'source_start_vertex':0}]
        elif key in witnesses:
            w = witnesses[key]
            origin,index,reverse,error,edge = recover_witness_origin(w,case['source'])
            case['origin_provenance']='uniquely inferred from pinned metric source edge and original WGS84 source vertices; not recovered route metadata'
            case['origin_replay_error_m']=error
            case['target_pairs']=[{'source':edge,'targets':[inverse_eq(t,origin) for t in w['target_edges_metric']],
                'source_start_vertex':index,'pinned_source_metric':w['source_edge_metric'],
                'pinned_target_metric':w['target_edges_metric'], 'target_keys':w['target_keys'], 'topology_control':key=='44807950'}]
        else:
            case['origin_provenance']='pinned source-section fixture' if origin else 'operational route origin unavailable; local proxy only'
            context=c.get('context',{})
            if 'target_coordinates' in context:
                case['target_pairs']=[{'source':case['source'],'targets':[context['target_coordinates']], 'source_start_vertex':0,
                    'precision_note':'publisher endpoints rounded to ten decimals in preserved research; original target digest not replayed'}]
            if key in origins and key != '1289002662':
                section=next(s for s in sections if s['path']['way_id']==key)
                case['target_pairs']=[{'source':case['source'],'targets':[t['coords'] for t in section['targets']], 'source_start_vertex':0,
                    'precision_note':'ten-decimal catalogue display endpoints; authoritative metric lengths/keys retained in source fixture'}]
            if key == '1050757473':
                case['admitted_vertex_ranges']=[[1,2]]
                case['eligibility_note']='Only source vertices1..2 admitted. Vertex0 prefix excluded regardless of geometry metric.'
            if key == '23385711':
                case['eligibility_note']='No admitted NCN6 source row; bounded observed membership NCN7. Proximity cannot establish NCN6 identity.'
            if key == '159311690':
                case['eligibility_note']='Source admitted in part; investigated historical target remains direction_gate. Local proxy cannot recreate route-wide original decision.'
            if key == '1289002662':
                case['eligibility_note']='Admitted RCN90 source, zero bindings. Stable-chain node movement is supported by pinned histories; proposal note is not NCN90 membership.'
        case['operational_origin']=origin
        cases.append(case)
    return {'schema_version':1, 'input_sha256':PINS, 'cases':cases,
        'limitation':'No national data. Inferred origins explicitly labelled; absent route origins remain absent. No full access/exclusion/mask reconstruction or new admissions.'}


def compare_cross(fixture):
    output=[]
    for c in fixture['cases']:
        local=c['source'][0]; origins={'local':local}
        if c['operational_origin'] is not None: origins['route']=c['operational_origin']
        result={'key':c['key'],'name':c['name'],'origin_provenance':c['origin_provenance'],
            'actual_admitted_source_present':c['actual_admitted_source_present'], 'eligibility_note':c['eligibility_note'],
            'spaces':{},'hypothetical_only':True, 'compatibility':c['compatibility'],
            'history_status':c['history_status'], 'existing_binding_count':c['existing_binding_count']}
        for scope,origin in origins.items():
            for projection in ('aeqd','eq'):
                label=scope+'_'+projection; source=project(c['source'],origin,projection); ref=project(c['reference'],origin,projection)
                first,second=LineString(source),LineString(ref)
                rows=[]
                for group in c['target_pairs']:
                    points=project(group['source'],origin,projection)
                    targets=[project(t,origin,projection) for t in group['targets']]
                    if scope=='route' and projection=='eq' and 'pinned_source_metric' in group:
                        assert max(math.dist(a,b) for a,b in zip(points,group['pinned_source_metric']))<1e-7
                        assert max(math.dist(a,b) for a,b in zip(sum(targets,[]),sum(group['pinned_target_metric'],[])))<1e-7
                        points=group['pinned_source_metric'];targets=group['pinned_target_metric']
                    for i,(a,z) in enumerate(zip(points,points[1:])):
                        for j,target in enumerate(targets):
                            line=LineString([a,z]);t=LineString(target)
                            rows.append({'source_edge':i+group['source_start_vertex'],'target_index':j,
                                'min_distance_m':line.distance(t),
                                'max_distance_m':max(Point(a).distance(t),Point(z).distance(t)),
                                'angle_deg':angle(a,z,*target),'finite_pair':pair(a,z,target)})
                topology=None
                if c['key']=='44807950':
                    group=c['target_pairs'][0];a,z=project(group['source'],origin,projection)
                    targets=[(str(i),LineString(project(t,origin,projection))) for i,t in enumerate(group['targets'])]
                    active=[(i,*v) for i,(_,t) in enumerate(targets) if (v:=edge_interval(a,z,t))]
                    low=max(v[1] for v in active); high=min(v[2] for v in active)
                    degrees=Counter(tuple(p) for _,t in targets for p in t.coords)
                    topology={'active_finite_pairs':len(active),'overlap_source_m':max(0.,high-low),
                        'seam_partition_rejected':ordinary_seam_partition(a,z,active,targets,degrees,low,high) is None}
                    assert topology['seam_partition_rejected']
                result['spaces'][label]={'origin':origin,'source_length_m':first.length,'reference_length_m':second.length,
                    'source_reference_hausdorff_m':first.hausdorff_distance(second),
                    'source_reference_min_distance_m':first.distance(second),'target_edge_pairs':rows,'topology':topology}
        result['projection_classification_changes']=[]
        for scope in origins:
            a=result['spaces'][scope+'_aeqd'];e=result['spaces'][scope+'_eq']
            assert len(a['target_edge_pairs'])==len(e['target_edge_pairs'])
            for x,y in zip(a['target_edge_pairs'],e['target_edge_pairs']):
                if ((x['finite_pair'] is None)!=(y['finite_pair'] is None) or (x['angle_deg']<=15)!=(y['angle_deg']<=15)):
                    result['projection_classification_changes'].append({'scope':scope,'source_edge':x['source_edge'],'target_index':x['target_index'],
                        'aeqd_angle_deg':x['angle_deg'],'eq_angle_deg':y['angle_deg'],
                        'aeqd_pair_present':x['finite_pair'] is not None,'eq_pair_present':y['finite_pair'] is not None})
        result['threshold_adjacent_pairs']=[{'space':name,'source_edge':p['source_edge'],'target_index':p['target_index'],
            'min_distance_m':p['min_distance_m'],'max_distance_m':p['max_distance_m'],'angle_deg':p['angle_deg']}
            for name,s in result['spaces'].items() for p in s['target_edge_pairs']
            if abs(p['angle_deg']-15)<=1 or min(abs(p['min_distance_m']-10),abs(p['max_distance_m']-10))<=1]
        output.append(result)
    return output


def validate_invariants(cases):
    checks=0
    for case in cases['cases']:
        origins=[case['source'][0]]+([case['operational_origin']] if case['operational_origin'] else [])
        for origin in origins:
            for space in ('aeqd','eq'):
                for group in case['target_pairs']:
                    source=project(group['source'],origin,space)
                    for target in group['targets']:
                        q,b=project(target,origin,space)
                        for a,z in zip(source,source[1:]):
                            full=pair(a,z,[q,b]);reverse=pair(z,a,[q,b]);flip=pair(a,z,[b,q])
                            assert (full is None)==(reverse is None)==(flip is None)
                            if full:
                                length=math.dist(a,z);total=math.dist(q,b)
                                assert max(abs(x-y) for x,y in zip(full[:2],[length-reverse[1],length-reverse[0]]))<1e-7
                                assert max(abs(x-y) for x,y in zip(full[:2],flip[:2]))<1e-7
                                assert max(abs(x-y) for x,y in zip(full[2:],[total-flip[2],total-flip[3]]))<1e-7
                            mid=[(x+y)/2 for x,y in zip(q,b)]
                            halves=[v for t in ([q,mid],[mid,b]) if (v:=pair(a,z,t))]
                            if full:
                                assert halves
                                assert abs(min(v[0] for v in halves)-full[0])<1e-7
                                assert abs(max(v[1] for v in halves)-full[1])<1e-7
                                if len(halves)==2:
                                    ordered=sorted(halves)
                                    assert abs(ordered[0][1]-ordered[1][0])<1e-7
                            else: assert not halves
                            checks+=1
    return {'edge_pair_orientation_and_midpoint_partition_checks':checks,
        'note':'Geometric finite-pair invariants only; new segmentation can still change multi-target ambiguity policy.'}


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--output', type=Path, required=True)
    p.add_argument('--cases-output', type=Path)
    p.add_argument('--deal-only', action='store_true')
    args = p.parse_args()
    inputs = read_inputs()
    fixture = next(v for k,v in inputs.items() if 'ncn1-deal-fixture' in k)
    result = {'schema_version': 1, 'baseline': '0f8199651dad6014410274dc1b968e2417efe1b2',
        'input_sha256': PINS, 'deal': deal(fixture), 'cross_route': [] if args.deal_only else compare_cross(prepare_cross(inputs)),
        'validation': {} if args.deal_only else validate_invariants(prepare_cross(inputs)),
        'no_operational_changes': True, 'new_completion_credit_m': 0}
    if args.deal_only: result.pop('validation')
    assert args.output.resolve() not in [(ROOT/k).resolve() for k in PINS]
    if args.cases_output:
        assert args.cases_output.resolve() not in [(ROOT/k).resolve() for k in PINS] + [args.output.resolve()]
        args.cases_output.write_text(json.dumps(prepare_cross(inputs),indent=2,sort_keys=True,allow_nan=False)+'\n')
    args.output.write_text(json.dumps(result, indent=2, sort_keys=True, allow_nan=False)+'\n')


if __name__ == '__main__':
    main()
