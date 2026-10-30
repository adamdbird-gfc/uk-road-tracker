"""Bounded Deal research only: extract pinned evidence or reproduce its receipt.

No catalogue, source admission, binding writer, network or journey access.
Run from repository root with PYTHONPATH=.; see the accompanying report.
"""
import argparse
import base64
import gzip
import hashlib
import io
import json
import math
from pathlib import Path
import struct
import xml.etree.ElementTree as ET

from shapely.geometry import LineString, Point, shape
from shapely.ops import unary_union
from scripts.build_ncn_national import positions, compile_path_bindings
from scripts.ncn_source_compatibility import compare
from scripts.ncn_trial.journey_replay import projector
from scripts.ncn_trial.network_geometry import metric_projector
from scripts.ncn_trial.open_completion import edge_interval
from types import SimpleNamespace
from matched_path_evidence import digest

TARGET = 'open-edge:a25d7e26207940a4e9df96e7a4c74582ba53cb3f575a95dd1c8c536813c5d188'
ROOT = Path(__file__).resolve().parents[2]
PINS_SHA = 'a706b3ce8c0307c5410954045e68d938ae7f2616dc8c6e32c29b48ca5b8d0641'
SUMMARY_SHA = 'e837b6810d7044d42c2a2eb7db6bce102cebb957032cc18a76487297b6621f0b'
CLASSIFIED_SHA = '1ebb14d7489e5add77dac77dcb25c5beda2ce53c7c3994eccc22696944955dcb'


def sha(raw):
    return hashlib.sha256(raw).hexdigest()


def write(path, value):
    Path(path).write_text(json.dumps(value, indent=2, sort_keys=True, allow_nan=False)+'\n')


def union(spans):
    out = []
    for low, high in sorted((min(a, z), max(a, z)) for a, z in spans):
        if high <= low:
            continue
        if out and low <= out[-1][1]:
            out[-1][1] = max(out[-1][1], high)
        else:
            out.append([low, high])
    return out


def extract(checkpoint, classified, osm):
    """Read existing archives' extracted inputs; never run a national assessment."""
    root = Path(checkpoint)
    pins_raw = (root/'inputs/input-pins.json').read_bytes()
    summary_raw = (root/'plans/summary.json').read_bytes()
    assert sha(pins_raw) == PINS_SHA and sha(summary_raw) == SUMMARY_SHA
    pins, summary = json.loads(pins_raw), json.loads(summary_raw)
    manifest = next(r for r in summary['routes'] if r['route_key'] == 'NCN:1')
    files = {p['file']: p['sha256'] for p in pins['files']}
    checked = {}

    def read(name, planned=False):
        raw = (root/('plans' if planned else 'inputs')/name).read_bytes()
        if not planned:
            assert sha(raw) == files[name], name
        checked[('plans/' if planned else 'inputs/')+name] = sha(raw)
        return gzip.decompress(raw) if name.endswith('.gz') else raw

    plan_raw = read('NCN-1/plan.json.gz', True)
    assoc_raw = read('NCN-1/associations.json.gz', True)
    assert sha(plan_raw) == manifest['plan_sha256']
    assert sha(assoc_raw) == manifest['association_sha256']
    plan, assoc = json.loads(plan_raw), json.loads(assoc_raw)
    catalogue = {n: json.loads(read('catalogues/NCN-1/'+n)) for n in
                 ('reference.json.gz', 'associations.json.gz', 'identity-review.json',
                  'audit.json.gz', 'open-extent.json', 'manifest.json')}
    review = catalogue['identity-review.json']
    official = catalogue['open-extent.json']['features']
    assert digest(official) == review['approved_open_features_sha256']
    assert {f['properties']['FID'] for f in official} == set(review['approved_official_feature_ids'])
    admission_origin = catalogue['reference.json.gz']['metadata']['projection_origin']
    assert admission_origin == plan['projection_origin']
    path = next(p for p in assoc['paths'] if p['way_id'] == '4471747')
    project = projector({'projection_origin': plan['projection_origin']})
    source_line = LineString([project(c) for c in path['coordinates']])
    near = []
    open_lines = []
    target_original = None
    for feature in official:
        g = feature['geometry']
        parts = [g['coordinates']] if g['type'] == 'LineString' else g['coordinates']
        if any(LineString([project(c) for c in part]).distance(source_line) <= 30 for part in parts):
            near.append(feature)
        if feature['properties']['FID'] in review['approved_official_feature_ids']:
            for part in parts:
                line = LineString([project(c) for c in part])
                if line.distance(source_line) <= 20:
                    open_lines.append(line)
                for i, (a, z) in enumerate(zip(part, part[1:])):
                    xy = sorted((project(a), project(z)))
                    if 'open-edge:'+digest(tuple(xy)) == TARGET:
                        target_original = {'coordinates': [a, z], 'metric': xy,
                                           'feature_properties': feature['properties'], 'edge_index': i}
    assert target_original is not None
    merged = unary_union(open_lines)
    lines = [merged] if merged.geom_type == 'LineString' else merged.geoms
    target_keys = {s['key'] for s in plan['logical_sections']}
    targets = []
    for line in lines:
        for a, z in zip(line.coords, list(line.coords)[1:]):
            a, z = sorted((tuple(a), tuple(z)))
            key = 'open-edge:'+digest((a, z))
            if LineString([a, z]).distance(source_line) <= 10:
                assert key in target_keys
                targets.append({'key': key, 'coordinates': [a, z]})
    targets = sorted(targets, key=lambda s: s['key'])
    logical = next(s for s in plan['logical_sections'] if s['key'] == TARGET)
    assert LineString(target_original['metric']).length == logical['length_m']
    sections = [s for s in plan['physical_sections_metric'] if s['key'] in path['section_keys']]
    existing = [b for b in plan['bindings'] if b['physical_key'] in path['section_keys']]
    target_existing = [b for b in plan['bindings'] if b['logical_key'] == TARGET]
    compiled = compile_path_bindings(SimpleNamespace(**path), plan, False)
    correction = json.loads(gzip.decompress((ROOT/'.github/ncn-reference/stage1-binding-correction.json.gz').read_bytes()))
    corrected = next(r for r in correction['updates'] if r['route'] == 'NCN:1' and r['way'] == '4471747')
    assert compiled == corrected['bindings']
    idxdir = ROOT/'.github/ncn-reference/ordinary-seams'
    idx = json.loads((idxdir/'index.json').read_bytes())
    raw = []
    for part in idx['parts']:
        b = (idxdir/part['file']).read_bytes()
        assert sha(b) == part['sha256']
        raw.append(base64.b64decode(b, validate=True))
    raw = b''.join(raw)
    assert sha(raw) == idx['artifact_sha256']
    stream = io.BytesIO(gzip.decompress(raw))
    assert stream.read(9) == b'NCNSEAM1\n'
    keys, fmt = sorted(target_keys), struct.Struct('<IIddddB')
    delta_source, delta_target = [], []
    while b := stream.read(4):
        size = struct.unpack('<I', b)[0]
        route, way, previous, count = json.loads(stream.read(size))
        for _ in range(count):
            position, target, start, end, low, high, mode = fmt.unpack(stream.read(fmt.size))
            if route != 'NCN:1':
                continue
            row = {'source_way': way, 'target': keys[target], 'start': start, 'end': end,
                   'low': low, 'high': high, 'mode_mask': mode, 'insertion_position': position}
            if way == '4471747':
                delta_source.append(row)
            if keys[target] == TARGET:
                delta_target.append(row)
    assert sha(Path(classified).read_bytes()) == CLASSIFIED_SHA
    history = [json.loads(s) for s in gzip.open(classified, 'rt') if TARGET in s]
    assert history and all(r['route_key'] == 'NCN:1' for r in history)
    raw_osm = Path(osm).read_bytes()
    evidence = json.loads((ROOT/'docs/data-trials/national-mapping-source-evidence-20261030.json').read_bytes())
    assert sha(raw_osm) == evidence['captures']['NCN1_unadmitted_Deal.osm']['sha256']
    xml = ET.fromstring(raw_osm)
    relation = xml.find("relation[@id='75880']")
    root_relation = xml.find("relation[@id='1992599']")
    membership = {'relation_id': 75880, 'version': int(relation.get('version')),
                  'timestamp': relation.get('timestamp'),
                  'tags': {t.get('k'): t.get('v') for t in relation.findall('tag')},
                  'source_members': [m.attrib for m in relation.findall('member') if m.get('ref') in ('4471747', '77611523')],
                  'root_id': 1992599, 'root_version': int(root_relation.get('version')),
                  'root_timestamp': root_relation.get('timestamp'),
                  'root_membership': [m.attrib for m in root_relation.findall('member') if m.get('ref') == '75880']}
    deferred = set(plan['deferred_alternative_way_ids'])
    owners = {k: p['way_id'] for p in assoc['paths'] for k in p['section_keys']}
    target_line = LineString(target_original['metric'])
    deferred_distance = min(LineString(s['coordinates']).distance(target_line)
                            for s in plan['physical_sections_metric'] if owners[s['key']] in deferred)
    neighbours = [p for p in assoc['paths'] if p['way_id'] != '4471747' and
                  (set(p['osm_node_ids']) & set(path['osm_node_ids']) or
                   LineString([project(c) for c in p['coordinates']]).distance(target_line) <= 10)]
    controls = json.loads((ROOT/'tests/fixtures/ncn-source-compatibility/controls.json').read_bytes())
    return {'schema_version': 1, 'baseline': 'da7bf0e0739ed260b15fe332c37b6e160832f594',
            'pins': {'input_pins': PINS_SHA, 'summary': SUMMARY_SHA, 'classified': CLASSIFIED_SHA,
                     'osm_capture': sha(raw_osm), 'seam_delta': sha(raw), 'consumed_files': checked,
                     'plan_decoded': sha(plan_raw), 'association_decoded': sha(assoc_raw)},
            'projection_origin': plan['projection_origin'], 'admission_projection_origin': admission_origin,
            'path': path, 'association_provenance': assoc['provenance'],
            'admitted_sections': sections, 'existing_plan_bindings': existing,
            'corrected_full_way_bindings': compiled, 'existing_target_bindings': target_existing,
            'seam_source_insertions': delta_source, 'seam_target_insertions': delta_target,
            'target_key': TARGET, 'target_length_m': logical['length_m'], 'target_original': target_original,
            'nearby_targets_metric': targets, 'nearby_official_features': near,
            'approved_nearby_feature_ids': [f['properties']['FID'] for f in near if f['properties']['FID'] in review['approved_official_feature_ids']],
            'excluded_original_features_complete': False,
            'exclusion_limitation': 'Only hash-verified approved original extent is needed to prove outside-corridor failure; original non-approved feature set is not reconstructed.',
            'identity_policy': review['policy'], 'identity_review_spans': [s for s in review['approved_source_spans'] if s['way_id'] == 4471747],
            'audit_hold': next(r for r in catalogue['audit.json.gz']['held'] if r['way_id'] == 4471747),
            'historical_target_classification': history, 'membership_observation': membership,
            'deferred_source': '4471747' in deferred, 'nearest_deferred_section_to_target_m': deferred_distance,
            'adjoining_admitted_paths': neighbours,
            'compatibility_controls': [r for r in controls['comparisons'] if r['key'] in ('4471747', 'deal-private-parallel')]}


def analyse(fixture, raw):
    assert fixture['schema_version'] == 1
    source = fixture['path']
    origin = fixture['projection_origin']
    project = projector({'projection_origin': origin})
    admission = metric_projector(fixture['admission_projection_origin'])
    xy = [project(c) for c in source['coordinates']]
    am = [admission(c) for c in source['coordinates']]
    pos = positions(source['coordinates'], origin)
    target = LineString(fixture['target_original']['metric'])
    assert 'open-edge:'+digest(tuple(sorted(map(tuple, target.coords)))) == TARGET
    assert target.length == fixture['target_length_m']
    admission_target = LineString([admission(c) for c in fixture['target_original']['coordinates']])
    good, bad = [], []
    for f in fixture['nearby_official_features']:
        props, g = f['properties'], f['geometry']
        allowed = (props['FID'] in fixture['approved_nearby_feature_ids'] and props['OpenStatus'] == 'Open'
                   and props['Desc_'] in ('TrafficFree', 'OnRoad') and props['RouteCat'] == 'Main Route')
        parts = [g['coordinates']] if g['type'] == 'LineString' else g['coordinates']
        (good if allowed else bad).extend(LineString([admission(c) for c in part]).buffer(10) for part in parts)
    good, bad = unary_union(good), unary_union(bad)
    allowed_edges = {i for s in fixture['identity_review_spans'] for i in range(s['from_vertex'], s['to_vertex'])}
    neighbours = [(s['key'], LineString(s['coordinates'])) for s in fixture['nearby_targets_metric']]
    rows, geometric = [], []
    tx = target.coords[-1][0]-target.coords[0][0]
    ty = target.coords[-1][1]-target.coords[0][1]
    for i, (a, z) in enumerate(zip(xy, xy[1:])):
        al = LineString([am[i], am[i+1]])
        outside, excluded = al.difference(good).length, al.intersection(bad).length
        gate = outside <= 1e-5 and excluded <= 1e-5
        assert gate == (i in allowed_edges)
        interval = edge_interval(a, z, target)
        cosine = ((z[0]-a[0])*tx+(z[1]-a[1])*ty)/(math.dist(a, z)*target.length)
        angle = math.degrees(math.acos(min(1., abs(cosine))))
        candidates = []
        for key, other in neighbours:
            v = edge_interval(a, z, other)
            if v:
                candidates.append({'target': key, 'source_local_m': list(v[:2]), 'target_m': list(v[2:])})
        if interval:
            geometric.append(list(interval[2:]))
        v = edge_interval(am[i], am[i+1], admission_target)
        rows.append({'edge_index': i, 'nodes': source['osm_node_ids'][i:i+2],
                     'source_full_way_m': pos[i:i+2], 'admitted': i in allowed_edges,
                     'admission_metric_length_m': al.length, 'outside_approved_10m_corridor_m': outside,
                     'inside_excluded_corridor_m': None, 'necessary_whole_edge_approved_corridor_gate': gate,
                     'completion_undirected_angle_deg': angle,
                     'completion_finite_candidate': None if interval is None else {
                         'source_full_way_m': [pos[i]+interval[0], pos[i]+interval[1]],
                         'target_m': list(interval[2:]), 'admitted_source': i in allowed_edges},
                     'admission_metric_finite_pair': list(v) if v else None,
                     'competing_completion_candidates': candidates})
    history = fixture['historical_target_classification']
    remaining = next(r for r in history if r['cause'] == 'unadmitted_span_of_known_way')
    existing = union((b['logical_start'], b['logical_end']) for b in fixture['existing_target_bindings'])
    assert existing == [[0.0, 49.58955939202579]]
    assert not fixture['seam_target_insertions'] and not fixture['deferred_source']
    assert fixture['nearest_deferred_section_to_target_m'] > 10
    assert fixture['audit_hold']['edge_indices'] == [6, 7, 8, 9, 10]
    assert [s['key'] for s in fixture['admitted_sections']] == ['4471747:0:6', '4471747:11:13']
    assert source['coordinates'] == fixture['compatibility_controls'][0]['source']['coordinates']
    assert source['osm_node_ids'] == fixture['compatibility_controls'][0]['source']['provenance']['current_observed_node_chain']
    minimal_plan = {'projection_origin': origin, 'physical_sections_metric': fixture['admitted_sections'],
                    'bindings': fixture['existing_plan_bindings']}
    assert compile_path_bindings(SimpleNamespace(**source), minimal_plan, False) == fixture['corrected_full_way_bindings']
    compatibility = [compare(r) for r in fixture['compatibility_controls']]
    assert compatibility[0]['classification'] == 'exact_match'
    assert compatibility[1]['classification'] == 'incompatible_or_unsupported_correspondence'
    assert compatibility[1]['mode_access']['reference'] == 'incompatible'
    private = fixture['compatibility_controls'][1]['reference']
    private_xy = [project(c) for c in private['coordinates']]
    private_candidates = []
    for i, (a, z) in enumerate(zip(private_xy, private_xy[1:])):
        v = edge_interval(a, z, target)
        if v:
            private_candidates.append({'edge_index': i, 'source_local_m': list(v[:2]), 'target_m': list(v[2:])})
    return {'schema_version': 1, 'fixture_sha256': sha(raw), 'baseline': fixture['baseline'],
            'decision': 'Existing exclusion justified under current whole-edge source-admission rules; no correction authorised or supported.',
            'source_way': '4471747', 'target': TARGET, 'projection_origin': origin,
            'source_positions_m': pos, 'admitted_full_way_intervals_m': [[pos[0], pos[6]], [pos[11], pos[13]]],
            'unadmitted_full_way_interval_m': [pos[6], pos[11]], 'edge_results': rows,
            'existing_target_coverage_m': existing, 'post_seam_target_additions': [],
            'historical_known_way_residual_m': [remaining['low_m'], remaining['high_m']],
            'historical_known_way_residual_length_m': remaining['high_m']-remaining['low_m'],
            'geometric_only_target_candidate_union_m': union(geometric),
            'geometric_only_unadmitted_upper_bound_m': remaining['high_m']-remaining['low_m'],
            'demonstrated_eligible_route_local_recovery_m': 0.0,
            'demonstrated_eligible_unique_network_recovery_m': 0.0,
            'geometric_only_unique_network_upper_bound_m': None,
            'unique_network_limit': 'No pinned target-to-network mappings materialized; no exact nonzero network increment claimed.',
            'other_route_changes': 0, 'new_bindings': 0, 'new_completion_credit': False,
            'nearest_deferred_section_to_target_m': fixture['nearest_deferred_section_to_target_m'],
            'compatibility_results': compatibility,
            'private_negative': {'feature_id': private['feature_id'], 'tags': private['tags'],
                                 'completion_projection_min_distance_m': LineString(private_xy).distance(target),
                                 'admission_projection_min_distance_m': LineString([admission(c) for c in private['coordinates']]).distance(admission_target),
                                 'geometric_only_completion_candidates': private_candidates,
                                 'eligible': False, 'reason': 'Different identity, private access, no confirmed NCN membership; proximity transfers no entitlement.'},
            'assertions': 'Pins, exact target identity/length, source sections, full-way offsets, held edges, both diagnostics, existing target coverage and unchanged seam/deferred status checked.'}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--checkpoint', type=Path)
    parser.add_argument('--classified', type=Path)
    parser.add_argument('--osm', type=Path)
    parser.add_argument('--fixture', type=Path)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    if args.fixture:
        assert args.output.resolve() != args.fixture.resolve()
        raw = args.fixture.read_bytes()
        result = analyse(json.loads(raw), raw)
    else:
        assert args.checkpoint and args.classified and args.osm
        result = extract(args.checkpoint, args.classified, args.osm)
    write(args.output, result)
