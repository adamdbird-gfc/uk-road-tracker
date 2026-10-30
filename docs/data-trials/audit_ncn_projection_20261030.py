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


def main():
    inputs = read_inputs()
    fixture = next(v for k,v in inputs.items() if 'ncn1-deal-fixture' in k)
    result = {'schema_version': 1, 'baseline': '0f8199651dad6014410274dc1b968e2417efe1b2',
        'input_sha256': PINS, 'deal': deal(fixture), 'cross_route': [],
        'no_operational_changes': True, 'new_completion_credit_m': 0}
    p = argparse.ArgumentParser(description=__doc__); p.add_argument('--output', type=Path, required=True); args = p.parse_args()
    assert args.output.resolve() not in [(ROOT/k).resolve() for k in PINS]
    args.output.write_text(json.dumps(result, indent=2, sort_keys=True, allow_nan=False)+'\n')


if __name__ == '__main__':
    main()
