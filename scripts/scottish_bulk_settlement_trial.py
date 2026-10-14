#!/usr/bin/env python3
"""Generate isolated Scottish candidates using frozen official boundary inputs."""
import argparse
import json
import resource
import time
from pathlib import Path

import osmium
from shapely.geometry import shape
from bulk_settlement_inventory_trial import RoadAccumulator, SnapshotHandler, digest, read_poly


def run(inputs, output):
    inputs, output = Path(inputs), Path(output)
    if output.resolve().is_relative_to((Path(__file__).resolve().parents[1] / 'settlement-inventories-v1').resolve()):
        raise ValueError('Trial output must not overwrite published data')
    started = time.monotonic()
    features = json.loads((inputs / 'boundaries.geojson').read_text())['features']
    if {f['properties']['BUA22CD'] for f in features} != {'S45000308', 'S45000364', 'S45000635'}:
        raise ValueError('Expected exactly the three selected Scottish boundaries')
    boundaries = [shape(f['geometry']) for f in features]
    if any(not b.is_valid or b.is_empty for b in boundaries):
        raise ValueError('Invalid official boundary')
    source = json.loads((inputs / 'downloads.json').read_text())[0]
    pbf = inputs / 'scotland-latest.osm.pbf'
    if digest(pbf) != source['sha256']:
        raise ValueError('Snapshot hash mismatch')
    with osmium.io.Reader(str(pbf)) as reader:
        source['osm_timestamp'] = reader.header().get('osmosis_replication_timestamp')
    accumulator = RoadAccumulator(boundaries)
    SnapshotHandler(accumulator).apply_file(str(pbf), locations=True, idx='flex_mem')
    coverage = read_poly(inputs / 'scotland.poly')
    output.mkdir(parents=True, exist_ok=True)
    results = []
    for i, feature in enumerate(features):
        properties = feature['properties']
        roads = sorted(accumulator.names[i])
        candidate = {'version': 1, 'source': 'ONS Built Up Areas (December 2022) Boundaries GB',
                     'code': properties['BUA22CD'], 'name': properties['BUA22NM'],
                     'count': len(roads), 'roads': roads}
        path = output / (candidate['code'] + '.json')
        path.write_text(json.dumps(candidate, separators=(',', ':')) + '\n')
        results.append({'code': candidate['code'], 'name': candidate['name'], 'count': len(roads),
                        'candidate_bytes': path.stat().st_size, 'candidate_sha256': digest(path),
                        'boundary_coverage_fraction': boundaries[i].intersection(coverage).area / boundaries[i].area})
    report = {'status': 'new_candidates_only_not_published', 'source': source,
              'boundary_source': 'https://services1.arcgis.com/ESMARspQHYMw9BZ9/arcgis/rest/services/BUA_2022_GB/FeatureServer/0/query',
              'boundary_sha256': digest(inputs / 'boundaries.geojson'),
              'results': results, 'processing_seconds_including_hashing': round(time.monotonic() - started, 3),
              'peak_process_rss_kib': resource.getrusage(resource.RUSAGE_SELF).ru_maxrss,
              'limitations': ['No published Scottish inventories exist for before/after comparison',
                              'Approximate extraction coverage only; road counts need acceptance checks',
                              'No production database, Hosting or Android changes']}
    (output / 'report.json').write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--inputs', required=True)
    parser.add_argument('--output', required=True)
    args = parser.parse_args()
    run(args.inputs, args.output)
