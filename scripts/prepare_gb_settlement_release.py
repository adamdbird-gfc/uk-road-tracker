#!/usr/bin/env python3
"""Prepare an additive GB release, preserving all existing published bytes."""
import argparse
import gzip
import hashlib
import json
import re
import shutil
from pathlib import Path


def digest(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def prepare(published, candidates, boundaries, output):
    published, candidates, boundaries, output = map(Path, (published, candidates, boundaries, output))
    if output.resolve().is_relative_to(published.resolve()):
        raise ValueError('Release preparation must not overwrite published data')
    if output.exists() and any(output.iterdir()):
        raise ValueError('Use a fresh empty output directory')
    current = json.loads((published / 'settlement-inventory-manifest-v1.json').read_text())
    legacy = {row['code']: row for row in current['inventories'] if row['status'] == 'available'}
    if len(legacy) != len(current['inventories']):
        raise ValueError('Unexpected non-available or duplicate legacy entries; review before preparing')
    manifest = json.loads((candidates / 'candidate-manifest.json').read_text())['inventories']
    entries = {row['code']: row for row in manifest}
    if len(entries) != len(manifest) or any(not re.fullmatch(r'[EWSK][0-9]{8}', code) for code in entries):
        raise ValueError('Duplicate candidate code')
    audit = json.loads((candidates / 'generation-audit.json').read_text())
    if digest(boundaries) != audit['boundary_snapshot_sha256']:
        raise ValueError('Boundary snapshot differs from inventory generation')
    with gzip.open(boundaries, 'rt') as stream:
        features = json.load(stream)['features']
    by_code = {row['properties']['code']: row for row in features}
    if len(by_code) != len(features) or set(by_code) != set(entries) or not set(legacy) <= set(entries):
        raise ValueError('Boundary/candidate/legacy code mismatch')
    # Validate every candidate before writing any release files.
    for code, entry in entries.items():
        path = candidates / 'inventories' / (code + '.json')
        if digest(path) != entry['sha256'] or path.stat().st_size != entry['bytes']:
            raise ValueError('Candidate integrity failure: ' + code)
        data = json.loads(path.read_text())
        if data['code'] != code or data['count'] != entry['count'] or data['count'] != len(set(data['roads'])):
            raise ValueError('Candidate identity/count mismatch: ' + code)
    output.mkdir(parents=True, exist_ok=True)
    assets = output / 'settlement-inventories-v1'
    assets.mkdir()
    release_entries, preserved, zeros = [], [], []
    for code in sorted(entries):
        if code in legacy:
            for suffix in ['.json', '-boundary.geojson']:
                source = published / 'settlement-inventories-v1' / (code + suffix)
                target = assets / (code + suffix)
                shutil.copyfile(source, target)
                if digest(source) != digest(target):
                    raise ValueError('Legacy byte preservation failed')
            data = json.loads((assets / (code + '.json')).read_text())
            if data['count'] != legacy[code]['count'] or data['count'] != len(set(data['roads'])):
                raise ValueError('Legacy count mismatch')
            preserved.append(code)
        else:
            shutil.copyfile(candidates / 'inventories' / (code + '.json'), assets / (code + '.json'))
            (assets / (code + '-boundary.geojson')).write_text(json.dumps(by_code[code], separators=(',', ':')) + '\n')
            data = json.loads((assets / (code + '.json')).read_text())
        count = data['count']
        if count == 0:
            zeros.append(code)
        release_entries.append({'code': code, 'status': 'available', 'count': count})
    released = dict(current, inventories=release_entries)
    (output / 'settlement-inventory-manifest-v1.json').write_text(json.dumps(released, indent=2) + '\n')
    report = {'status': 'prepared_only_not_published', 'settlements': len(entries),
              'preserved_existing_inventories': len(preserved), 'preserved_codes': preserved,
              'new_inventories': len(entries) - len(preserved), 'known_zero_eligible_codes': zeros,
              'deferred_existing_changes': audit['existing_inventory_differences'],
              'file_count': len(list(assets.iterdir())) + 1,
              'public_file_bytes': sum(p.stat().st_size for p in assets.iterdir()) + (output / 'settlement-inventory-manifest-v1.json').stat().st_size,
              'source_snapshot': audit['source'], 'candidate_boundary_sha256': digest(boundaries),
              'publication_gates': ['Stage and serving verification', 'Actual Firebase plan/usage check',
                                    'Explicit Postgres insert/reconciliation of missing inventory rows',
                                    'No claim of Northern Ireland coverage; queue retirement remains gated']}
    (output / 'release-preparation-report.json').write_text(json.dumps(report, indent=2) + '\n')
    return report


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--published', default='.')
    parser.add_argument('--candidates', required=True)
    parser.add_argument('--boundaries', required=True)
    parser.add_argument('--output', required=True)
    args = parser.parse_args()
    result = prepare(args.published, args.candidates, args.boundaries, args.output)
    print(json.dumps({k: v for k, v in result.items() if k not in {'preserved_codes', 'known_zero_eligible_codes', 'deferred_existing_changes'}}, indent=2))
