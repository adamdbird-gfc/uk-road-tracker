#!/usr/bin/env python3
"""Reproduce the reviewed public dataset; reject any source or output drift."""
import argparse
import gzip
import hashlib
import json
import shutil
import subprocess
import sys
from pathlib import Path


def sha(path):
    h = hashlib.sha256()
    with Path(path).open('rb') as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b''):
            h.update(block)
    return h.hexdigest()


def validate_recovered(data, code):
    roads = data.get('roads')
    if (data.get('code') != code or not isinstance(roads, list)
            or any(not isinstance(r, str) or not r.strip() for r in roads)
            or type(data.get('count')) is not int
            or data['count'] != len(roads) or len(set(roads)) != len(roads)):
        raise ValueError('Recovered public inventory identity/count mismatch')


def checked_hash(path, expected):
    if sha(path) != expected:
        raise ValueError('Reviewed snapshot hash mismatch: ' + str(path))


def build(root, app, work, request):
    root, app, work = map(lambda p: Path(p).resolve(), (root, app, work))
    if work.exists():
        raise ValueError('Preview build requires a fresh working directory')
    work.mkdir(parents=True)
    def run(script, *args):
        subprocess.run([sys.executable, str(root / 'scripts' / script), *map(str, args)], check=True)
    # A changed latest extract must stop before the national download.
    import urllib.request
    with urllib.request.urlopen(request['osm_url'] + '.md5', timeout=30) as response:
        supplier_md5 = response.read(256).decode().split()[0]
    if supplier_md5 != request['osm_md5']:
        raise ValueError('Reviewed OSM snapshot is no longer the latest extract')
    run('freeze_gb_settlement_boundaries.py', '--root', root, '--output', work / 'boundaries')
    # Gzip embeds this basename; retain the reviewed archive's exact header too.
    boundary = work / 'roadprints-gb-boundaries-2022-valid-candidate.geojson.gz'
    run('normalise_gb_boundary_snapshot.py', '--source', work / 'boundaries/boundaries.geojson.gz', '--output', boundary)
    checked_hash(boundary, request['boundary_sha256'])
    run('download_bulk_settlement_trial.py', '--regions', 'great-britain', '--output', work / 'source')
    checked_hash(work / 'source/great-britain-latest.osm.pbf', request['osm_sha256'])
    run('generate_gb_road_inventory_candidates.py', '--root', root, '--boundaries', boundary,
        '--source-dir', work / 'source', '--output', work / 'candidates')
    preservation = work / 'preservation'
    preservation.mkdir()
    shutil.copytree(root / 'settlement-inventories-v1', preservation / 'settlement-inventories-v1')
    manifest = json.loads((root / 'settlement-inventory-manifest-v1.json').read_text())
    with gzip.open(boundary, 'rt') as stream:
        features = {f['properties']['code']: f for f in json.load(stream)['features']}
    existing = {e['code'] for e in manifest['inventories']}
    for code, expected in request['recovered_inventory_sha256'].items():
        if code in existing:
            raise ValueError('Recovered inventory now has a published baseline; review again')
        path = root / '.github/gb-preview/recovered' / (code + '.json')
        checked_hash(path, expected)
        data = json.loads(path.read_text())
        validate_recovered(data, code)
        shutil.copyfile(path, preservation / 'settlement-inventories-v1' / path.name)
        (preservation / 'settlement-inventories-v1' / (code + '-boundary.geojson')).write_text(
            json.dumps(features[code], separators=(',', ':')) + '\n')
        manifest['inventories'].append({'code': code, 'status': 'available', 'count': data['count']})
    manifest['inventories'].sort(key=lambda e: e['code'])
    (preservation / 'settlement-inventory-manifest-v1.json').write_text(json.dumps(manifest, indent=2) + '\n')
    run('prepare_gb_settlement_release.py', '--published', preservation, '--candidates', work / 'candidates',
        '--boundaries', boundary, '--output', work / 'release')
    subprocess.run([sys.executable, 'scripts/stage_firebase_reference_data.py',
                    '--settlements-root', str(work / 'release')], cwd=app, check=True)
    checked_hash(app / 'hosting-stage-report.json', request['hosting_stage_report_sha256'])
    print('Reviewed public file hashes reproduced exactly; preview deployment permitted.', flush=True)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', default='.')
    parser.add_argument('--app', required=True)
    parser.add_argument('--work', required=True)
    parser.add_argument('--request', default='.github/gb-preview/request.json')
    args = parser.parse_args()
    build(args.root, args.app, args.work, json.loads(Path(args.request).read_text()))
