#!/usr/bin/env python3
"""Bounded parallel execution of the existing exact-byte Hosting verifier."""
import argparse
import json
import sys
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--app', required=True, type=Path)
    parser.add_argument('--url', required=True)
    args = parser.parse_args()
    sys.path.insert(0, str(args.app.resolve() / 'scripts'))
    from verify_firebase_reference_data import verify
    report = json.loads((args.app / 'hosting-stage-report.json').read_text())
    if len({r['path'] for r in report['files']}) != len(report['files']):
        raise ValueError('Duplicate file paths in staged report')
    chunks = [report['files'][i:i+512] for i in range(0, len(report['files']), 512)]
    def check(rows):
        return verify(args.url, {'schema_version': 1, 'files': rows})
    with ThreadPoolExecutor(max_workers=8) as pool:
        results = list(pool.map(check, chunks))
    result = {'url': args.url, 'files_verified': sum(r['files_verified'] for r in results),
              'bytes_verified': sum(r['bytes_verified'] for r in results)}
    if result['bytes_verified'] != report['total_bytes']:
        raise ValueError('Hosted byte total differs from staging')
    (args.app / 'gb-preview-verification.json').write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps(result, indent=2))
