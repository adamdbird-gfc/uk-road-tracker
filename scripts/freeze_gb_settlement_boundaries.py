#!/usr/bin/env python3
"""Freeze and audit official GB boundaries as candidates, without publication."""
import argparse
import gzip
import hashlib
import http.client
import json
import re
import time
import urllib.parse
import urllib.request
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone
from pathlib import Path

from shapely.geometry import shape

SERVICE = 'https://services1.arcgis.com/ESMARspQHYMw9BZ9/arcgis/rest/services/BUA_2022_GB/FeatureServer/0'


def sha(data):
    return hashlib.sha256(data).hexdigest()


def query(url, params):
    target = url + '?' + urllib.parse.urlencode(params)
    for attempt in range(3):
        try:
            with urllib.request.urlopen(target, timeout=120) as response:
                raw = response.read()
            result = json.loads(raw)
            if 'error' in result:
                raise ValueError('Source query error: ' + repr(result['error']))
            return result, raw
        except (OSError, ValueError, http.client.HTTPException):
            if attempt == 2:
                raise
            time.sleep(2 * (attempt + 1))


def validate_page(data, expected_ids, id_field):
    if data.get('exceededTransferLimit'):
        raise ValueError('Truncated source page')
    features = data.get('features', [])
    ids = [feature['properties'][id_field] for feature in features]
    if len(ids) != len(set(ids)) or set(ids) != set(expected_ids):
        raise ValueError('Source page IDs are missing, duplicated or unexpected')
    codes = [feature['properties']['BUA22CD'] for feature in features]
    if len(codes) != len(set(codes)) or any(not re.fullmatch(r'[EWSK][0-9]{8}', code) for code in codes):
        raise ValueError('Invalid or duplicate stable settlement codes')
    for feature in features:
        geometry = shape(feature['geometry'])
        if geometry.is_empty or geometry.geom_type not in {'Polygon', 'MultiPolygon'}:
            raise ValueError('Missing polygon boundary')
        west, south, east, north = geometry.bounds
        if not (-180 <= west <= east <= 180 and -90 <= south <= north <= 90):
            raise ValueError('Boundary is not in longitude/latitude coordinates')
    return features


def freeze(root, output, page_size=100):
    root, output = Path(root), Path(output)
    if output.resolve().is_relative_to((root / 'settlement-inventories-v1').resolve()):
        raise ValueError('Snapshot cannot overwrite published boundaries')
    if not 1 <= page_size <= 1000:
        raise ValueError('Unsupported page size')
    started = time.monotonic()
    output.mkdir(parents=True, exist_ok=True)
    pages_dir = output / 'source-pages'
    boundaries_dir = output / 'boundaries'
    pages_dir.mkdir(exist_ok=True)
    boundaries_dir.mkdir(exist_ok=True)
    metadata, raw_metadata = query(SERVICE, {'f': 'json'})
    (output / 'source-metadata.json').write_bytes(raw_metadata)
    id_result, raw_ids = query(SERVICE + '/query', {'where': '1=1', 'returnIdsOnly': 'true', 'f': 'json'})
    (output / 'source-ids.json').write_bytes(raw_ids)
    ids = sorted(id_result['objectIds'])
    if not ids or len(ids) != len(set(ids)):
        raise ValueError('Invalid frozen source IDs')
    id_field = id_result['objectIdFieldName']
    groups = [ids[i:i + page_size] for i in range(0, len(ids), page_size)]

    def download(item):
        index, group = item
        data, raw = query(SERVICE + '/query', {
            'objectIds': ','.join(map(str, group)), 'outFields': id_field + ',BUA22CD,BUA22NM',
            'returnGeometry': 'true', 'outSR': '4326', 'f': 'geojson',
        })
        features = validate_page(data, group, id_field)
        path = pages_dir / f'{index:03d}.geojson'
        path.write_bytes(raw)
        print('Downloaded page', index + 1, '/', len(groups), len(raw), 'bytes', flush=True)
        return features, {'page': index, 'count': len(features), 'bytes': len(raw), 'sha256': sha(raw)}

    with ThreadPoolExecutor(max_workers=2) as pool:
        results = list(pool.map(download, enumerate(groups)))
    features = [feature for rows, _ in results for feature in rows]
    codes = [feature['properties']['BUA22CD'] for feature in features]
    if len(codes) != len(set(codes)):
        raise ValueError('Duplicate codes across source pages')
    after, _ = query(SERVICE, {'f': 'json'})
    before_edit = metadata.get('editingInfo', {}).get('lastEditDate')
    after_edit = after.get('editingInfo', {}).get('lastEditDate')
    if before_edit != after_edit:
        raise ValueError('Source changed during snapshot; restart with fresh IDs')
    catalogue = json.loads((root / 'settlement-catalogue-v1.json').read_text())['settlements']
    existing = {row['code']: row for row in catalogue}
    if len(existing) != len(catalogue):
        raise ValueError('Duplicate catalogue IDs')
    invalid, renamed, changed, matched = [], [], [], []
    by_prefix, published_bytes = {}, 0
    all_features = []
    for feature in sorted(features, key=lambda f: f['properties']['BUA22CD']):
        properties = feature['properties']
        code, name = properties['BUA22CD'], properties['BUA22NM']
        geometry = shape(feature['geometry'])
        if not geometry.is_valid:
            invalid.append(code)
        if code in existing and name != existing[code]['name']:
            renamed.append({'code': code, 'catalogue_name': existing[code]['name'], 'source_name': name})
        candidate = {'type': 'Feature', 'properties': {'code': code, 'name': name}, 'geometry': feature['geometry']}
        raw = json.dumps(candidate, separators=(',', ':')).encode()
        (boundaries_dir / (code + '-boundary.geojson')).write_bytes(raw)
        published_bytes += len(raw)
        by_prefix[code[0]] = by_prefix.get(code[0], 0) + 1
        saved = root / 'settlement-inventories-v1' / (code + '-boundary.geojson')
        if saved.exists():
            old = json.loads(saved.read_text())
            # Preserve existing geometries in a later release unless explicitly approved.
            if old['geometry'] == candidate['geometry'] or shape(old['geometry']).equals(geometry):
                matched.append(code)
            else:
                changed.append(code)
        all_features.append(candidate)
    collection = json.dumps({'type': 'FeatureCollection', 'features': all_features}, separators=(',', ':')).encode()
    with (output / 'boundaries.geojson.gz').open('wb') as target:
        with gzip.GzipFile(fileobj=target, mode='wb', mtime=0) as compressed:
            compressed.write(collection)
    compressed = (output / 'boundaries.geojson.gz').read_bytes()
    report = {
        'generated_at': datetime.now(timezone.utc).isoformat(), 'status': 'candidate_snapshot_not_published',
        'source': SERVICE, 'source_last_edit_date': before_edit,
        'source_metadata_sha256': sha(raw_metadata), 'source_ids_sha256': sha(raw_ids),
        'source_pages': [report for _, report in results],
        'source_count': len(features), 'catalogue_count': len(catalogue), 'by_prefix': by_prefix,
        'missing_catalogue_codes': sorted(set(existing) - set(codes)),
        'extra_source_codes': sorted(set(codes) - set(existing)), 'renamed': renamed,
        'invalid_geometries': invalid, 'saved_boundaries_matching': len(matched),
        'saved_boundaries_changed': changed,
        'boundary_file_bytes': published_bytes, 'combined_geojson_bytes': len(collection),
        'compressed_bytes': len(compressed), 'compressed_sha256': sha(compressed),
        'download_bytes': sum(report['bytes'] for _, report in results),
        'elapsed_seconds': round(time.monotonic() - started, 3),
        'limitations': ['GB only; Northern Ireland is a separate unresolved source',
                        'No publication, database import or app change',
                        'Invalid geometries or changed identities block unreviewed national adoption'],
    }
    (output / 'audit.json').write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps({key: value for key, value in report.items() if key != 'source_pages'}, indent=2))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', default='.')
    parser.add_argument('--output', required=True)
    parser.add_argument('--page-size', type=int, default=100)
    args = parser.parse_args()
    freeze(args.root, args.output, args.page_size)
