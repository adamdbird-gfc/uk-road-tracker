#!/usr/bin/env python3
"""Remove overlapping valid polygon components in an isolated boundary candidate."""
import argparse
import gzip
import hashlib
import json
from pathlib import Path

from shapely.geometry import mapping, shape
from shapely.ops import unary_union
from shapely.validation import explain_validity


def normalise(geometry):
    original = shape(geometry)
    if original.is_valid:
        return geometry, None
    if original.geom_type != 'MultiPolygon' or not all(part.is_valid for part in original.geoms):
        raise ValueError('Source has invalid individual polygons; explicit review required')
    # MultiPolygon membership is the union of its parts. Union removes duplicated
    # interiors/nested shells without inventing holes or moving component edges.
    repaired = unary_union(list(original.geoms))
    if not repaired.is_valid or repaired.geom_type not in {'Polygon', 'MultiPolygon'} or repaired.bounds != original.bounds:
        raise ValueError('Component union did not produce an equivalent valid footprint')
    return mapping(repaired), {'reason': explain_validity(original), 'method': 'union_valid_polygon_components',
                              'component_count': len(original.geoms), 'bounds_unchanged': True,
                              'original_component_area_sum': original.area,
                              'union_footprint_area': repaired.area,
                              'note': 'Original summed area double-counted overlaps; component union preserves membership'}


def run(source, output):
    source, output = Path(source), Path(output)
    if source.resolve() == output.resolve():
        raise ValueError('Keep the source snapshot unchanged')
    with gzip.open(source, 'rt') as stream:
        data = json.load(stream)
    repairs = []
    for feature in data['features']:
        geometry, repair = normalise(feature['geometry'])
        if repair:
            feature['geometry'] = geometry
            repairs.append(dict(feature['properties'], **repair))
    raw = json.dumps(data, separators=(',', ':')).encode()
    with output.open('wb') as target:
        with gzip.GzipFile(fileobj=target, mode='wb', mtime=0) as compressed:
            compressed.write(raw)
    report = {'status': 'valid_candidate_not_published', 'count': len(data['features']),
              'repairs': repairs, 'source_sha256': hashlib.sha256(source.read_bytes()).hexdigest(),
              'candidate_sha256': hashlib.sha256(output.read_bytes()).hexdigest(),
              'candidate_compressed_bytes': output.stat().st_size,
              'limitations': ['Candidate only; no live geometry changed',
                              'Repair removes overlaps between already valid polygon components',
                              'Named-road inventory comparison remains required before publication']}
    output.with_name('normalisation-audit.json').write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', required=True)
    parser.add_argument('--output', required=True)
    args = parser.parse_args()
    run(args.source, args.output)
