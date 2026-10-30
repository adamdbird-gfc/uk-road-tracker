"""Reconstruct bounded controls from committed, hash-pinned research; no network."""
import argparse
import hashlib
import json
from pathlib import Path
from scripts.ncn_source_compatibility import normalized_hash

PINS = {
 'docs/data-trials/ncn-post-seam-source-provenance-20261030.json': '839d7c8cf1097a9fbb321529faac63384a63d662698246e1596680a08e0c763f',
 'docs/data-trials/national-mapping-source-evidence-20261030.json': 'a277ee02df110f21dfe6161924047c4d1e95b3adc87b8ff0156501759c824316',
 'tests/fixtures/ncn-gap-seams/diagnostic-witnesses.json': '9b741fa37ef7f74e29269ed77de2d9d0068ed54744b18775b1a994b49a06612b',
 'tests/fixtures/ncn-binding-attainability/source-sections.json': '5e42cc0058ed71a4bead0ac9636e757e289f3aa1bc8e57b18be95bd31c57a91b',
}
SUPPLEMENT = 'tests/fixtures/ncn-source-compatibility/deal-private-control.json'


def prepare(root):
    docs = {}
    for path, pin in PINS.items():
        raw = (root/path).read_bytes()
        if hashlib.sha256(raw).hexdigest() != pin:
            raise ValueError('Pinned research input changed: '+path)
        docs[path] = json.loads(raw)
    provenance, research, witnesses, sections = (docs[p] for p in PINS)
    live = {s['source_feature_id']: s for s in research['live_walking_reference']['samples']}
    paths = {r['path']['way_id']: r for r in sections['routes']}
    way_routes = {'23385711': 'NCN:6', '1289002662': 'RCN:90', '1050757473': 'NCN:1', '59306268': 'NCN:9',
                  '4471747': 'NCN:1', '31252441': 'NCN:8', '44807950': 'NCN:81', '159311690': 'NCN:66'}
    comparisons = []
    for s in provenance['samples']:
        way = s['osm_way_id']; row = s['retained_shipped_ncn_row']
        captured = research['captures'].get(way+'.json', {})
        source_coordinates = paths[way]['path']['coordinates'] if way in paths else s['current_coordinates']
        admitted = row.get('present') is not False
        if admitted and normalized_hash(source_coordinates) != row['source_geometry_sha256']:
            raise ValueError('Cannot reconstruct pinned source geometry '+way)
        source = {'provider': 'osm', 'feature_id': way, 'version': None, 'coordinates': source_coordinates,
                  'node_ids': paths[way]['path']['osm_node_ids'] if way in paths else None,
                  'route_memberships': [row['route']] if admitted else ['NCN:7'],
                  'modes': paths[way]['path']['modes'] if way in paths else None, 'tags': None,
                  'provenance': {'snapshot_id': provenance['rcn90_provenance']['source_snapshot_sha256'] if way == '1289002662' else None,
                     'input_receipt_sha256': PINS[next(iter(PINS))], 'capture_or_extraction_date': None,
                     'geometry_origin': 'pinned source fixture' if way in paths else 'observed geometry matches preserved NCN source hash' if admitted else 'physical OSM observation; no admitted NCN source row',
                     'current_observed_way_version': s['current_way_version'], 'current_observed_way_timestamp': s['current_way_timestamp'],
                     'current_observed_node_chain': [n['id'] for n in s['current_node_versions']],
                     'current_capture_sha256': s['capture_sha256'], 'current_capture_url': s['source_url'],
                     'current_observed_tags': captured.get('way', {}).get('tags'), 'original_feature_version': None}}
        reference = {'provider': 'osm', 'feature_id': way, 'version': None, 'node_ids': None,
                     'coordinates': s['walking_coordinates'], 'route_memberships': None,
                     'modes': ['on_foot'], 'tags': live[way]['tags'],
                     'provenance': {'snapshot_id': None, 'capture_or_extraction_date': None,
                         'input_receipt_sha256': PINS[next(iter(PINS))], 'walking_load_receipts': s['walking_load_receipts'],
                         'checksum_semantics': 'regional downloaded PBF file SHA256; Kent retains both receipts, edition not resolved',
                         'source_version_label': 'latest is not an OSM feature version or immutable snapshot', 'original_feature_version': None}}
        context = {'binding_count': row.get('binding_count'), 'admitted_ncn_source_present': admitted}
        record = {'key': way, 'source': source, 'reference': reference, 'route': way_routes[way],
                  'mode': 'on_foot', 'route_evidence': row, 'context': context}
        if way == '1289002662':
            record['histories'] = provenance['osm_histories']; context['proposal_note'] = captured['way']['tags']['note']
        if way == '44807950':
            context['target_edges_metric'] = witnesses['genuine_hairpin_negative']['target_edges_metric']
            context['witness_source_section_key'] = witnesses['genuine_hairpin_negative']['source_section_key']
        if way == '1050757473':
            context.update(admitted_vertex_ranges=[[1, 2]], requested_vertex_range=[0, 2],
                           section_keys=paths[way]['path']['section_keys'], existing_section_bindings=paths[way]['bindings'],
                           source_section_offset_policy='PR239 section-local anchors translate to full-way positions; prefix vertex 0 remains excluded')
        sample_names = {'23385711': 'NCN6_no_admitted.analysis.json', '159311690': 'NCN66_direction.analysis.json', '4471747': 'NCN1_unadmitted_Deal.analysis.json'}
        if way in sample_names:
            observation = research['samples'][sample_names[way]]
            context['target_coordinates'] = observation['target_coordinates']
            context['preserved_sample_observation'] = observation['top_candidates'][0]
            record['route_evidence'] = {'retained_ncn_row': row, 'bounded_osm_memberships': observation['top_candidates'][0]['bicycle_relation_memberships']}
        comparisons.append(record)
    raw = (root/SUPPLEMENT).read_bytes(); private = json.loads(raw)
    deal = next(r for r in comparisons if r['key'] == '4471747')
    comparisons.append({'key': 'deal-private-parallel', 'source': deal['source'], 'reference': private['feature'],
        'route': 'NCN:1', 'mode': 'cycling', 'context': {'target_coordinates': deal['context']['target_coordinates']},
        'route_evidence': 'Distinct private feature; no confirmed route membership; no identity transfer.'})
    return {'schema_version': 1, 'provenance': {'baseline': '8dea49475e287bfefad7fa93b75e201e94a44230',
        'pinned_input_sha256': {**PINS, SUPPLEMENT: hashlib.sha256(raw).hexdigest()},
        'controls': 'Eight PR243/244 source ways plus one pinned private parallel negative; no journeys or national assessment',
        'reconstruction_policy': 'Matching normalized geometry hash reconstructs shape only; never original feature/node versions or missing node chains.'}, 'comparisons': comparisons}


if __name__ == '__main__':
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--root', type=Path, default=Path(__file__).resolve().parents[1])
    p.add_argument('--output', type=Path, required=True)
    args = p.parse_args()
    if args.output.resolve() in [(args.root/x).resolve() for x in [*PINS, SUPPLEMENT]]:
        p.error('Output must differ from pinned inputs')
    args.output.write_text(json.dumps(prepare(args.root), indent=2, sort_keys=True)+'\n')
