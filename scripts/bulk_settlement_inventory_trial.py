#!/usr/bin/env python3
"""Compare bulk OSM snapshots with stored inventories without publishing data."""
import argparse
import hashlib
import json
import resource
import subprocess
import time
from datetime import datetime, timezone
from pathlib import Path

import osmium
from shapely.geometry import LineString, Polygon, shape
from shapely.ops import unary_union
from shapely.strtree import STRtree

HIGHWAYS = {'residential', 'unclassified', 'tertiary', 'living_street'}


class RoadAccumulator:
    def __init__(self, boundaries):
        self.boundaries = boundaries
        self.tree = STRtree(boundaries)
        self.names = [set() for _ in boundaries]
        self.qualifying_ways = 0

    def accept(self, highway, name, coordinates):
        name = (name or '').strip()
        if highway not in HIGHWAYS or not name or len(coordinates) < 2:
            return
        line = LineString(coordinates)
        self.qualifying_ways += 1
        for index in self.tree.query(line, predicate='intersects'):
            self.names[index].add(name)


class SnapshotHandler(osmium.SimpleHandler):
    def __init__(self, accumulator):
        super().__init__()
        self.accumulator = accumulator

    def way(self, way):
        highway, name = way.tags.get('highway'), way.tags.get('name')
        if highway not in HIGHWAYS or not (name or '').strip():
            return
        # A missing node aborts the trial; never silently publish incomplete data.
        coordinates = [(node.lon, node.lat) for node in way.nodes]
        self.accumulator.accept(highway, name, coordinates)


def read_poly(path):
    lines = Path(path).read_text().splitlines()[1:]
    include, exclude = [], []
    index = 0
    while index < len(lines) and lines[index].strip() != 'END':
        label = lines[index].strip()
        index += 1
        ring = []
        while lines[index].strip() != 'END':
            ring.append(tuple(map(float, lines[index].split())))
            index += 1
        index += 1
        (exclude if label.startswith('!') else include).append(Polygon(ring))
    return unary_union(include).difference(unary_union(exclude))


def digest(path):
    result = hashlib.sha256()
    with Path(path).open('rb') as stream:
        while chunk := stream.read(1024 * 1024):
            result.update(chunk)
    return result.hexdigest()


def run(root, inputs, output, codes):
    started = time.monotonic()
    root, inputs, output = map(Path, (root, inputs, output))
    if output.resolve().is_relative_to((root / 'settlement-inventories-v1').resolve()):
        raise ValueError('Trial output must not overwrite published inventories')
    output.mkdir(parents=True, exist_ok=True)
    baselines, features, boundaries = [], [], []
    for code in codes:
        baseline = json.loads((root / 'settlement-inventories-v1' / (code + '.json')).read_text())
        feature = json.loads((root / 'settlement-inventories-v1' / (code + '-boundary.geojson')).read_text())
        geometry = shape(feature['geometry'])
        if baseline['code'] != code or feature['properties']['code'] != code or not geometry.is_valid:
            raise ValueError('Invalid stored settlement identity/boundary: ' + code)
        baselines.append(baseline)
        features.append(feature)
        boundaries.append(geometry)
    sources = json.loads((inputs / 'downloads.json').read_text())
    accumulator = RoadAccumulator(boundaries)
    coverages = []
    for source in sources:
        pbf = inputs / (source['region'] + '-latest.osm.pbf')
        if digest(pbf) != source['sha256']:
            raise ValueError('Snapshot hash mismatch: ' + source['region'])
        with osmium.io.Reader(str(pbf)) as reader:
            source['osm_timestamp'] = reader.header().get('osmosis_replication_timestamp')
        coverages.append(read_poly(inputs / (source['region'] + '.poly')))
        start = time.monotonic()
        SnapshotHandler(accumulator).apply_file(str(pbf), locations=True, idx='flex_mem')
        source['processing_seconds'] = round(time.monotonic() - start, 3)
        print(source['region'], source['processing_seconds'], 'seconds', flush=True)
    coverage = unary_union(coverages)
    comparisons = []
    for index, baseline in enumerate(baselines):
        roads = sorted(accumulator.names[index])
        before = set(baseline['roads'])
        after = set(roads)
        candidate = dict(baseline, roads=roads, count=len(roads))
        candidate_path = output / (baseline['code'] + '.json')
        candidate_path.write_text(json.dumps(candidate, separators=(',', ':')) + '\n')
        geometry = boundaries[index]
        comparisons.append({
            'code': baseline['code'], 'name': baseline['name'],
            'baseline_count': len(before), 'candidate_count': len(after),
            'unchanged_names': len(before & after),
            'added': sorted(after - before), 'removed': sorted(before - after),
            'boundary_coverage_fraction': geometry.intersection(coverage).area / geometry.area,
            'boundary_sha256': digest(root / 'settlement-inventories-v1' / (baseline['code'] + '-boundary.geojson')),
            'baseline_sha256': digest(root / 'settlement-inventories-v1' / (baseline['code'] + '.json')),
            'candidate_bytes': candidate_path.stat().st_size,
            'candidate_sha256': digest(candidate_path),
        })
    catalogue = json.loads((root / 'settlement-catalogue-v1.json').read_text())['settlements']
    prefixes = {}
    for settlement in catalogue:
        prefix = settlement['code'][0]
        prefixes[prefix] = prefixes.get(prefix, 0) + 1
    report = {
        'generated_at': datetime.now(timezone.utc).isoformat(),
        'source_commit': subprocess.check_output(['git', '-C', str(root), 'rev-parse', 'HEAD'], text=True).strip(),
        'status': 'trial_only_not_published',
        'rules': {'highways': sorted(HIGHWAYS), 'identity': 'exact stripped distinct road names',
                  'geometry': 'way intersects unchanged saved settlement boundary'},
        'sources': sources, 'comparisons': comparisons,
        'total_seconds_including_hashing': round(time.monotonic() - started, 3),
        'peak_process_rss_kib': resource.getrusage(resource.RUSAGE_SELF).ru_maxrss,
        'qualifying_way_visits': accumulator.qualifying_ways,
        'catalogue_count': len(catalogue), 'catalogue_prefix_counts': prefixes,
        'output_bytes': sum(item['candidate_bytes'] for item in comparisons),
        'limitations': ['Not a UK-wide performance or coverage benchmark',
                        'Different snapshot dates; name differences require review',
                        'Published app inventories and queue are unchanged',
                        'Region polygons are approximate extraction coverage checks',
                        'Existing catalogue coverage must be assessed for all four nations'],
    }
    (output / 'comparison.json').write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', default='.')
    parser.add_argument('--inputs', required=True)
    parser.add_argument('--output', required=True)
    parser.add_argument('--codes', nargs='+', default=['E63005058', 'W45000596', 'K08000001'])
    args = parser.parse_args()
    run(args.root, args.inputs, args.output, args.codes)
