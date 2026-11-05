"""Export five bounded route contexts from pinned retained inputs; no catalogue build.

No new spatial discovery: only the 50 previously selected pieces are inspected.
The small exported fixture supports offline reproduction without either archive.
"""
import argparse
from collections import Counter
import gzip
import hashlib
import json
import math
from pathlib import Path

from shapely.geometry import LineString
from shapely.ops import unary_union

from matched_path_evidence import digest
from scripts.ncn_trial.completion_checkpoint import open_extent
from scripts.ncn_trial.journey_replay import projector

ROOT = Path(__file__).resolve().parents[2]
HERE = Path(__file__).resolve().parent
PINS_SHA = 'a706b3ce8c0307c5410954045e68d938ae7f2616dc8c6e32c29b48ca5b8d0641'
SUMMARY_SHA = 'e837b6810d7044d42c2a2eb7db6bce102cebb957032cc18a76487297b6621f0b'
CASES = [('NCN:1', '4471919'), ('NCN:66', '159311690'),
         ('RCN:90', '1289002662'), ('NCN:92', '30268009'), ('NCN:7', '463641622')]


def sha(raw):
    return hashlib.sha256(raw).hexdigest()


def encode(value):
    return (json.dumps(value, indent=2, sort_keys=True, allow_nan=False) + '\n').encode()


def export(checkpoint, candidates):
    checkpoint = Path(checkpoint)
    consumed = {}

    def read(relative, expected):
        raw = (checkpoint / relative).read_bytes()
        if sha(raw) != expected:
            raise ValueError('Input pin differs: ' + relative)
        consumed[relative] = sha(raw)
        return raw

    pins = json.loads(read('inputs/input-pins.json', PINS_SHA))
    summary = json.loads(read('plans/summary.json', SUMMARY_SHA))
    registry = {r['route_key']: r for r in pins['routes']}
    records = {r['route_key']: r for r in summary['routes']}
    filepins = {r['file']: r['sha256'] for r in pins['files']}
    # Preserve/reuse the prior queue, never search for new candidates.
    raw = Path(candidates).read_bytes()
    raw = gzip.decompress(raw) if raw[:2] == b'\x1f\x8b' else raw
    if sha(raw) != '9be024c03383cfea419a5017257a5ff2c32d02d0fc4d941ec9b1d0fdb27aa947':
        raise ValueError('Preserved national candidate register content pin differs')
    rows = json.loads(raw)
    frozen = json.loads((HERE.parent / 'ncn-viewer/parallel/review-data.json').read_text())
    expected_ids = {i for g in frozen['selected_national_groups'] for i in g['candidate_ids']}
    expected_ids |= {'PC-fd18f79dd345', 'PC-9053d0488bc5'}
    rows = [r for r in rows if r['id'] in expected_ids]
    if len(rows) != 50 or {r['id'] for r in rows} != expected_ids:
        raise ValueError('Exact fifty retained candidate records required')
    network = None
    results = []
    for route, way in CASES:
        folder = 'plans/' + route.replace(':', '-')
        record = records[route]
        planraw = gzip.decompress((checkpoint / folder / 'plan.json.gz').read_bytes())
        assocraw = gzip.decompress((checkpoint / folder / 'associations.json.gz').read_bytes())
        if sha(planraw) != record['plan_sha256'] or sha(assocraw) != record['association_sha256']:
            raise ValueError('Plan/association content pin differs')
        consumed[folder + '/plan.uncompressed.json'] = sha(planraw)
        consumed[folder + '/associations.uncompressed.json'] = sha(assocraw)
        plan = json.loads(planraw)
        assoc = json.loads(assocraw)
        source = next(p for p in assoc['paths'] if p['way_id'] == way)
        origin = plan['projection_origin']
        project = projector(plan)
        extentfile = registry[route]['folder'] + '/open-extent.json'
        if extentfile in filepins:
            extent = json.loads(read('inputs/' + extentfile, filepins[extentfile]))
        else:
            if network is None:
                path = pins['official_network']
                network = json.loads(gzip.decompress(read('inputs/' + path, filepins[path])))
            extent = open_extent(route, network['features'],
                                 {'national_reference_sha256': pins['official_network_sha256'],
                                  'projection_origin': origin}, pins['official_network_sha256'])
        lines = []
        for feature in extent['features']:
            g = feature['geometry']
            parts = [g['coordinates']] if g['type'] == 'LineString' else g['coordinates']
            lines.extend(LineString([project(c) for c in part]) for part in parts)
        union = unary_union(lines)
        parts = [union] if union.geom_type == 'LineString' else union.geoms
        edges = {}
        for part in parts:
            for a, z in zip(part.coords, list(part.coords)[1:]):
                a, z = sorted((tuple(a), tuple(z)))
                if math.dist(a, z) > 1e-7:
                    edges['open-edge:' + digest((a, z))] = [a, z]
        if set(edges) != {s['key'] for s in plan['logical_sections']}:
            raise ValueError('Recovered target identities differ from original plan')
        degrees = Counter(p for coords in edges.values() for p in coords)
        selected = sorted([r for r in rows if r['route'] == route and r['source_way'] == way],
                          key=lambda r: r['id'])
        keys = {r['target_key'] for r in selected}
        if not keys:
            line = LineString([project(c) for c in source['coordinates']])
            keys = {min(edges, key=lambda k: (LineString(edges[k]).distance(line), k))}
        vertices = {p for k in keys for p in edges[k]}
        neighbours = {k for k, coords in edges.items() if vertices.intersection(coords)}
        targetrows = []
        for key in sorted(neighbours):
            coords = edges[key]
            geo = [[origin[0] + math.degrees(x / (6371008.8 * math.cos(math.radians(origin[1])))),
                    origin[1] + math.degrees(y / 6371008.8)] for x, y in coords]
            targetrows.append(dict(key=key, metric_coordinates=coords, coordinates=geo,
                                   selected=key in keys, endpoint_degrees=[degrees[p] for p in coords]))
        endnodes = {source['osm_node_ids'][0], source['osm_node_ids'][-1]}
        connected = [p for p in assoc['paths'] if endnodes.intersection(p['osm_node_ids'])]
        results.append(dict(route=route, way=way, origin=origin, source=source,
                            source_endpoint_neighbours=sorted(connected, key=lambda p: p['way_id']),
                            candidates=selected, targets=targetrows,
                            existing_source_bindings=[b for b in plan['bindings']
                                                      if b['physical_key'] in source['section_keys']],
                            original_route_target_count=len(edges),
                            canonical_target_to_network_mapping=None))
    record=records['NCN:6']
    assocraw=gzip.decompress((checkpoint/'plans/NCN-6/associations.json.gz').read_bytes())
    if sha(assocraw) != record['association_sha256']:
        raise ValueError('Cumbria NCN6 association pin differs')
    consumed['plans/NCN-6/associations.uncompressed.json']=sha(assocraw)
    cumbria=dict(requested_route='NCN:6',way='23385711',
                 matching_original_source_rows=[p for p in json.loads(assocraw)['paths'] if p['way_id']=='23385711'],
                 note='NCN7 observed membership is preserved separately in the existing compatibility fixture; no NCN6 alias.')
    return dict(schema_version=1, cumbria=cumbria, baseline='92ab97489c8c228c3cdaaf35ac9c3a14ddad700d',
                reference_revision=4, diagnostic_only=True, consumed_input_sha256=consumed,
                candidate_subset_sha256=sha(encode(sorted(rows, key=lambda r: r['id']))),
                cases=results,
                limitations=['Original checkpoint target graph and source admissions, not a revision 4 binding replay.',
                             'Candidate residual intervals already subtract revision 4 seam/Deal additions.',
                             'Canonical target-to-network mappings are absent; no new gain can be certified.',
                             'Endpoint degree is target topology, not proof of physical connector equivalence.'])


if __name__ == '__main__':
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--checkpoint', required=True, type=Path)
    p.add_argument('--candidates', required=True, type=Path)
    p.add_argument('--output', required=True, type=Path)
    a = p.parse_args()
    a.output.write_bytes(encode(export(a.checkpoint, a.candidates)))
