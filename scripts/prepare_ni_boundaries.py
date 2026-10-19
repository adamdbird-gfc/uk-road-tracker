#!/usr/bin/env python3
"""Create isolated, audited NI settlement boundary candidates from NISRA SDL15."""
import argparse
import gzip
import hashlib
import io
import json
from pathlib import Path
import re
import zipfile

import pyproj
from pyproj.transformer import TransformerGroup
import shapefile
import shapely
from shapely.geometry import mapping, shape
from shapely.strtree import STRtree

SOURCE_URL = 'https://www.nisra.gov.uk/files/nisra/publications/SDL15-ESRI_format.zip'
SOURCE_SHA256 = 'fa880d3effb0e05923a7a27d2ae44191394737a926af0b61ed1e88c30f4b107c'
SOURCE_NAME = 'NISRA Settlement Development Limits 2015'
STEM = 'settlements-2015-above-500-threshold'


def sha(raw):
    return hashlib.sha256(raw).hexdigest()


def canonical(data):
    return (json.dumps(data, sort_keys=True, separators=(',', ':'), ensure_ascii=False) + '\n').encode()


def normalise_records(records, transform, expected_count=527):
    if len(records) != expected_count:
        raise ValueError('Unexpected settlement count')
    seen, features, repairs, projected = set(), [], [], []
    for properties, geometry in sorted(records, key=lambda row: row[0]['Code']):
        code, name = properties.get('Code'), properties.get('Name')
        if (not isinstance(code, str) or not re.fullmatch(r'N11\d{6}', code)
                or code in seen or not isinstance(name, str) or not name.strip()):
            raise ValueError('Invalid or duplicate settlement identity')
        seen.add(code)
        if geometry.is_empty or geometry.geom_type not in ('Polygon', 'MultiPolygon') or geometry.area <= 0:
            raise ValueError('Empty or non-polygon source boundary')
        candidate = geometry
        if not geometry.is_valid:
            candidate = shapely.make_valid(geometry)
            relative_area_change = abs(candidate.area - geometry.area) / geometry.area
            distance = geometry.hausdorff_distance(candidate)
            if (candidate.geom_type not in ('Polygon', 'MultiPolygon') or not candidate.is_valid
                    or candidate.is_empty or relative_area_change > 1e-8 or distance > 1e-6):
                raise ValueError('Repair changes settlement footprint or leaves non-polygon material')
            repairs.append({'code': code, 'name': name, 'reason': shapely.is_valid_reason(geometry),
                            'method': 'GEOS make_valid', 'relative_area_change': relative_area_change,
                            'hausdorff_distance_metres': distance})
        projected.append(candidate)
        wgs84 = shapely.transform(candidate, transform, interleaved=False)
        west, south, east, north = wgs84.bounds
        if (not wgs84.is_valid or wgs84.is_empty or not (-9 < west <= east < -5 and 54 < south <= north < 56)):
            raise ValueError('Invalid transformed boundary or coordinates outside NI envelope')
        features.append({'type': 'Feature', 'properties': {'code': code, 'name': name.strip(),
                         'band': properties.get('Band'), 'nation': 'Northern Ireland'}, 'geometry': mapping(wgs84)})
    # Report genuine polygon overlaps, allowing shared edges and sub-square-metre noise.
    overlaps = []
    tree = STRtree(projected)
    for i, geometry in enumerate(projected):
        for position in tree.query(geometry, predicate='intersects'):
            j = int(position)
            if j <= i:
                continue
            area = geometry.intersection(projected[j]).area
            if area > 1:
                overlaps.append({'codes': [features[i]['properties']['code'], features[j]['properties']['code']],
                                 'area_square_metres': area})
    return {'type': 'FeatureCollection', 'features': features}, repairs, overlaps


def prepare(source_zip, output):
    source_zip, output = Path(source_zip), Path(output)
    raw = source_zip.read_bytes()
    if sha(raw) != SOURCE_SHA256:
        raise ValueError('NISRA source checksum differs from reviewed snapshot')
    with zipfile.ZipFile(io.BytesIO(raw)) as archive:
        reader = shapefile.Reader(shp=io.BytesIO(archive.read(STEM + '.shp')),
                                  shx=io.BytesIO(archive.read(STEM + '.shx')),
                                  dbf=io.BytesIO(archive.read(STEM + '.dbf')), encoding='latin1')
        source_crs = pyproj.CRS.from_wkt(archive.read(STEM + '.prj').decode())
        if source_crs.to_epsg() != 29902:
            raise ValueError('Unexpected declared source coordinate system')
        group = TransformerGroup(source_crs, pyproj.CRS.from_epsg(4326), always_xy=True, allow_ballpark=False)
        if not group.best_available or not group.transformers or not 0 <= group.transformers[0].accuracy <= 1:
            raise ValueError('Reviewed coordinate operation is not available')
        transformer = group.transformers[0]
        records = [(row.record.as_dict(), shape(row.shape.__geo_interface__)) for row in reader.iterShapeRecords()]
    data, repairs, overlaps = normalise_records(records, transformer.transform)
    output.mkdir(parents=True, exist_ok=True)
    candidate_raw = canonical(data)
    packed = gzip.compress(candidate_raw, mtime=0)
    (output / 'ni-boundary-candidates.geojson.gz').write_bytes(packed)
    catalogue = {'version': 1, 'source': SOURCE_NAME,
                 'settlements': [dict(f['properties']) for f in data['features']]}
    (output / 'ni-settlement-catalogue-candidate.json').write_bytes(canonical(catalogue))
    audit = {'status': 'candidates_only_not_published', 'source_url': SOURCE_URL,
             'source_sha256': SOURCE_SHA256, 'source_bytes': len(raw),
             'settlement_count': len(data['features']), 'unique_codes': len({f['properties']['code'] for f in data['features']}),
             'source_crs': 'EPSG:29902', 'target_crs': 'EPSG:4326',
             'coordinate_operation': transformer.description, 'operation_accuracy_metres': transformer.accuracy,
             'candidate_geojson_bytes': len(candidate_raw), 'candidate_gzip_bytes': len(packed),
             'candidate_geojson_sha256': sha(candidate_raw), 'candidate_gzip_sha256': sha(packed),
             'repairs': repairs, 'overlaps_over_one_square_metre': overlaps,
             'software': {'shapely': shapely.__version__, 'pyproj': pyproj.__version__, 'pyshp': shapefile.__version__},
             'licence_metadata': 'UK Open Government Licence (OGL), as listed by OpenDataNI',
             'attribution': 'Northern Ireland Statistics and Research Agency: Settlement Development Limits 2015',
             'licence_source': 'https://admin.opendatani.gov.uk/dataset/settlement-development-limits-2015',
             'limitations': ['Statistical settlement limits from the 2015 review; not a current developed-land survey',
                            'Coverage follows the official settlement inclusion thresholds',
                            'Names retained verbatim; aliases and historic county alignment need integration review',
                            'No Firebase, Postgres or app changes']}
    (output / 'boundary-audit.json').write_text(json.dumps(audit, indent=2) + '\n')
    print(json.dumps({k:v for k,v in audit.items() if k not in ('repairs','overlaps_over_one_square_metre')}, indent=2))
    print('Repaired source polygons:', len(repairs), '; overlaps above 1 m2:', len(overlaps), flush=True)
    return audit


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source-zip', required=True, type=Path)
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    prepare(args.source_zip, args.output)
