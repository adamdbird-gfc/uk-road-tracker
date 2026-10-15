#!/usr/bin/env python3
"""Generate isolated NI inventory trial files with the existing GB road rules."""
import argparse
import gzip
import json
from pathlib import Path
import resource
import time

import osmium
from shapely.geometry import shape
from bulk_settlement_inventory_trial import HIGHWAYS, RoadAccumulator, SnapshotHandler, digest, read_poly

DEFAULT_CODES = ['N11000151', 'N11000243', 'N11000537']


def generate(boundary_dir, source_dir, output, codes=None):
    started = time.monotonic()
    boundary_dir, source_dir, output = map(Path, (boundary_dir, source_dir, output))
    if any(part in ('settlement-inventories-v1', 'hosting-public') for part in output.resolve().parts):
        raise ValueError('Trial output must not overwrite public reference data')
    codes = DEFAULT_CODES if codes is None else list(codes)
    if not codes or len(codes) != len(set(codes)):
        raise ValueError('Select unique settlement codes')
    audit = json.loads((boundary_dir / 'boundary-audit.json').read_text())
    with gzip.open(boundary_dir / 'ni-boundary-candidates.geojson.gz', 'rb') as stream:
        raw = stream.read()
    import hashlib
    if hashlib.sha256(raw).hexdigest() != audit['candidate_geojson_sha256']:
        raise ValueError('Boundary candidates changed since audit')
    all_features = json.loads(raw)['features']
    by_code = {f['properties']['code']: f for f in all_features}
    if len(by_code) != len(all_features) or not set(codes) <= set(by_code):
        raise ValueError('Unknown or duplicate settlement boundary identity')
    features = [by_code[c] for c in codes]
    geometries = [shape(f['geometry']) for f in features]
    if any(g.is_empty or not g.is_valid for g in geometries):
        raise ValueError('Trial boundaries must be valid')
    source = json.loads((source_dir / 'downloads.json').read_text())
    pbf = source_dir / source['file']
    if digest(pbf) != source['sha256']:
        raise ValueError('OSM source hash mismatch')
    poly_path = source_dir / 'ireland-and-northern-ireland.poly'
    if digest(poly_path) != source['poly_sha256']:
        raise ValueError('OSM extraction polygon changed')
    coverage = read_poly(poly_path)
    fractions = [g.intersection(coverage).area / g.area for g in geometries]
    if any(f < .999999 for f in fractions):
        raise ValueError('Selected settlement extends outside road extract')
    with osmium.io.Reader(str(pbf)) as reader:
        osm_timestamp = reader.header().get('osmosis_replication_timestamp')
    output.mkdir(parents=True, exist_ok=True)
    index = output / 'node-locations.index'
    index.unlink(missing_ok=True)
    accumulator = RoadAccumulator(geometries)
    process_started = time.monotonic()
    try:
        SnapshotHandler(accumulator).apply_file(str(pbf), locations=True, idx='sparse_file_array,' + str(index.resolve()))
        processing_seconds = time.monotonic() - process_started
        index_bytes = index.stat().st_size if index.exists() else 0
    finally:
        index.unlink(missing_ok=True)
    inventories = []
    for i, feature in enumerate(features):
        code, name = feature['properties']['code'], feature['properties']['name']
        roads = sorted(accumulator.names[i])
        data = {'version': 1, 'source': 'NISRA Settlement Development Limits 2015',
                'code': code, 'name': name, 'count': len(roads), 'roads': roads}
        path = output / (code + '.json')
        path.write_text(json.dumps(data, separators=(',', ':'), ensure_ascii=False) + '\n')
        inventories.append({'code': code, 'name': name, 'candidate_count': len(roads),
                            'bytes': path.stat().st_size, 'sha256': digest(path),
                            'boundary_coverage_fraction': fractions[i]})
    report = {'status': 'trial_only_not_published', 'source': source, 'osm_timestamp': osm_timestamp,
              'boundary_geojson_sha256': audit['candidate_geojson_sha256'],
              'rules': {'highways': sorted(HIGHWAYS), 'names': 'exact stripped distinct road names',
                        'membership': 'eligible OSM way intersects settlement polygon; same as GB'},
              'inventories': inventories, 'osm_processing_seconds': round(processing_seconds, 3),
              'total_seconds_including_hashing': round(time.monotonic() - started, 3),
              'peak_rss_kib': resource.getrusage(resource.RUSAGE_SELF).ru_maxrss,
              'node_index_bytes': index_bytes, 'qualifying_ways_in_extract': accumulator.qualifying_ways,
              'inventory_file_bytes': sum(r['bytes'] for r in inventories),
              'limitations': ['New candidate counts; there are no saved NI inventories to compare against',
                             'Not a full 527-settlement capacity or quality benchmark',
                             'Four source boundary overlaps require review before wider acceptance',
                             'Irish extract includes Republic of Ireland; only selected NI polygons contribute',
                             'NI code, catalogue, aliases, historic county and app compatibility need integration review',
                             'No Firebase deployment, Postgres registration or APK build']}
    (output / 'trial-audit.json').write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps(report, indent=2), flush=True)
    return report


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--boundary-dir', required=True, type=Path)
    parser.add_argument('--source-dir', required=True, type=Path)
    parser.add_argument('--output', required=True, type=Path)
    parser.add_argument('--codes', nargs='+', default=DEFAULT_CODES)
    args = parser.parse_args()
    generate(args.boundary_dir, args.source_dir, args.output, args.codes)
