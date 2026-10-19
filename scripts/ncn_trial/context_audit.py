"""Compare reference position to independently sourced OSM ways; no matching credit."""
import argparse
from collections import Counter
import json
import math
from pathlib import Path
import statistics

from shapely.geometry import LineString

from .fixtures import load_reference


def main(source, output):
    reference, metadata = load_reference()
    lng0, lat0 = metadata['projection_origin']
    def project(p):
        return (6371008.8 * math.radians(p['lon'] - lng0) * math.cos(math.radians(lat0)),
                6371008.8 * math.radians(p['lat'] - lat0))
    data = json.loads(source.read_text())
    ways = []
    for row in data['elements']:
        points = row.get('geometry', [])
        if row['type'] == 'way' and len(points) >= 2:
            ways.append((row, LineString([project(p) for p in points])))
    if not ways:
        raise ValueError('No independent road/path geometry')
    report = {'status': 'positional_comparison_only', 'osm_way_count': len(ways),
              'osm_timestamp': data['osm3s']['timestamp_osm_base'],
              'route_membership_verified': False, 'cases': []}
    for key in ['FID:954:0', 'FID:16404:0']:
        line = reference.sections[key].line
        distances, winners = [], Counter()
        for i in range(41):
            p = line.interpolate(.05 + .9 * i / 40, normalized=True)
            row, candidate = min(ways, key=lambda value: value[1].distance(p))
            distances.append(candidate.distance(p))
            winners[row['id']] += 1
        ordered = sorted(distances)
        records = []
        for identity, count in winners.most_common(5):
            row = next(r for r, _ in ways if r['id'] == identity)
            records.append({'osm_way': identity, 'samples': count,
                            'tags': {k: v for k, v in row['tags'].items()
                                     if k in ['name', 'highway', 'access', 'foot', 'bicycle', 'bridge', 'layer', 'ncn_ref']}})
        report['cases'].append({'source_section': key, 'samples': len(distances),
                                'median_nearest_way_m': statistics.median(distances),
                                'p95_nearest_way_m': ordered[math.ceil(.95 * len(ordered)) - 1],
                                'maximum_nearest_way_m': max(distances), 'nearest_ways': records})
    output.write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    main(args.source, args.output)
