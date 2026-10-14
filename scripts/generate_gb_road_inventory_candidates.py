#!/usr/bin/env python3
"""Generate all GB inventories in isolation from one frozen OSM extract."""
import argparse
import gc
import gzip
import json
import resource
import time
from pathlib import Path

import osmium
from shapely.geometry import shape
from bulk_settlement_inventory_trial import RoadAccumulator, SnapshotHandler, digest, read_poly


def comparison(code, name, before, after):
    old, new = set(before), set(after)
    return {'code': code, 'name': name, 'baseline_count': len(old), 'candidate_count': len(new),
            'unchanged': len(old & new), 'added': sorted(new - old), 'removed': sorted(old - new)}


def generate(root, boundaries_path, source_dir, output):
    started = time.monotonic()
    root, boundaries_path, source_dir, output = map(Path, (root, boundaries_path, source_dir, output))
    if output.resolve().is_relative_to((root / 'settlement-inventories-v1').resolve()):
        raise ValueError('Candidates must not overwrite published inventories')
    boundary_hash = digest(boundaries_path)
    with gzip.open(boundaries_path, 'rt') as stream:
        data = json.load(stream)
    features = sorted(data['features'], key=lambda row: row['properties']['code'])
    catalogue = {row['code']: row for row in json.loads((root / 'settlement-catalogue-v1.json').read_text())['settlements']}
    identities = [(row['properties']['code'], row['properties']['name']) for row in features]
    if len(identities) != len(set(code for code, _ in identities)) or set(code for code, _ in identities) != set(catalogue):
        raise ValueError('Boundary/catalogue identity mismatch')
    geometries = [shape(row['geometry']) for row in features]
    if any(not g.is_valid or g.is_empty for g in geometries):
        raise ValueError('Candidate boundaries must all be valid')
    del features, data
    gc.collect()
    coverage = read_poly(source_dir / 'great-britain.poly')
    incomplete_coverage = []
    for (code, _), geometry in zip(identities, geometries):
        fraction = geometry.intersection(coverage).area / geometry.area
        if fraction < .999999:
            incomplete_coverage.append({'code': code, 'fraction': fraction})
    source = json.loads((source_dir / 'downloads.json').read_text())[0]
    pbf = source_dir / 'great-britain-latest.osm.pbf'
    if digest(pbf) != source['sha256']:
        raise ValueError('OSM extract checksum mismatch')
    with osmium.io.Reader(str(pbf)) as reader:
        source['osm_timestamp'] = reader.header().get('osmosis_replication_timestamp')
    output.mkdir(parents=True, exist_ok=True)
    accumulator = RoadAccumulator(geometries)
    index = output / 'node-locations.index'
    index.unlink(missing_ok=True)
    print('Validated', len(identities), 'boundaries; processing with a disk-backed node index', flush=True)
    process_started = time.monotonic()
    SnapshotHandler(accumulator).apply_file(str(pbf), locations=True, idx='sparse_file_array,' + str(index.resolve()))
    processing_seconds = time.monotonic() - process_started
    index_bytes = index.stat().st_size if index.exists() else 0
    print('OSM processing finished in', round(processing_seconds, 3), 'seconds', flush=True)
    manifest, differences, zero_counts = [], [], []
    outputs = output / 'inventories'
    outputs.mkdir(exist_ok=True)
    total_names, total_bytes, exact = 0, 0, 0
    for i, (code, name) in enumerate(identities):
        roads = sorted(accumulator.names[i])
        candidate = {'version': 1, 'source': 'ONS Built Up Areas (December 2022) Boundaries GB',
                     'code': code, 'name': name, 'count': len(roads), 'roads': roads}
        path = outputs / (code + '.json')
        path.write_text(json.dumps(candidate, separators=(',', ':')) + '\n')
        size = path.stat().st_size
        total_names += len(roads)
        total_bytes += size
        if not roads:
            zero_counts.append({'code': code, 'name': name})
        manifest.append({'code': code, 'status': 'available', 'count': len(roads), 'bytes': size, 'sha256': digest(path)})
        baseline = root / 'settlement-inventories-v1' / (code + '.json')
        if baseline.exists():
            old = json.loads(baseline.read_text())
            row = comparison(code, name, old['roads'], roads)
            if row['added'] or row['removed']:
                differences.append(row)
            else:
                exact += 1
    (output / 'candidate-manifest.json').write_text(json.dumps({'version': 1, 'inventories': manifest}, indent=2) + '\n')
    report = {'status': 'candidates_only_not_published', 'source': source,
              'boundary_snapshot_sha256': boundary_hash, 'settlement_count': len(identities),
              'total_distinct_names_across_settlements': total_names, 'inventory_file_bytes': total_bytes,
              'existing_inventories_exact_matches': exact, 'existing_inventory_differences': differences,
              'zero_named_road_settlements': zero_counts, 'incomplete_extraction_coverage': incomplete_coverage,
              'osm_processing_seconds': round(processing_seconds, 3),
              'total_seconds': round(time.monotonic() - started, 3),
              'peak_rss_kib': resource.getrusage(resource.RUSAGE_SELF).ru_maxrss,
              'node_index_bytes': index_bytes, 'qualifying_ways': accumulator.qualifying_ways,
              'limitations': ['GB only; Northern Ireland unresolved',
                              'No automatic publication or Postgres changes',
                              'Zero counts and existing-name differences require review',
                              'Approximate extraction coverage check only; full road/path coverage is outside these rules']}
    (output / 'generation-audit.json').write_text(json.dumps(report, indent=2) + '\n')
    index.unlink(missing_ok=True)
    print(json.dumps({key: value for key, value in report.items() if key not in {'existing_inventory_differences', 'zero_named_road_settlements'}}, indent=2))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', default='.')
    parser.add_argument('--boundaries', required=True)
    parser.add_argument('--source-dir', required=True)
    parser.add_argument('--output', required=True)
    args = parser.parse_args()
    generate(args.root, args.boundaries, args.source_dir, args.output)
