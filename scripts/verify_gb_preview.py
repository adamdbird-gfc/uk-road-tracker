#!/usr/bin/env python3
"""Verify a reviewed preview in bounded, resumable batches."""
import argparse
import hashlib
import http.client
import json
import sys
import time
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path


def transient_fetch(fetch, sleep=time.sleep):
    def retry(url, limit):
        public_asset = (url.endswith(('.json', '.geojson'))
                        and not url.endswith(('firebase.json', 'google-services.json')))
        for attempt in range(4):
            try:
                result = fetch(url, limit)
                transient = result[0] in (408, 429, 500, 502, 503, 504) or (public_asset and result[0] == 404)
                if not transient or attempt == 3:
                    return result
            except (OSError, http.client.HTTPException):
                if attempt == 3:
                    raise
            sleep(2 ** attempt)
    return retry


def verify_batches(url, report, verify, fetch, checkpoint, batch_size=100, workers=4):
    if not 1 <= workers <= 4 or not 1 <= batch_size <= 100:
        raise ValueError('Use at most 100 files per batch and four workers')
    rows = report['files']
    by_path = {r['path']: r for r in rows}
    if len(by_path) != len(rows) or not rows:
        raise ValueError('Missing or duplicate staged file paths')
    report_hash = hashlib.sha256(json.dumps(report, sort_keys=True).encode()).hexdigest()
    checkpoint = Path(checkpoint)
    state = json.loads(checkpoint.read_text()) if checkpoint.exists() else {
        'url': url, 'report_sha256': report_hash, 'verified': {}}
    if (state['url'] != url or state['report_sha256'] != report_hash
            or not set(state['verified']) <= set(by_path)):
        raise ValueError('Checkpoint belongs to a different preview or report')
    pending = [r for r in rows if state['verified'].get(r['path']) != r['sha256']]
    checked_fetch = transient_fetch(fetch)
    def check(chunk):
        return verify(url, {'schema_version': 1, 'files': chunk}, fetch=checked_fetch)
    with ThreadPoolExecutor(max_workers=workers) as pool:
        for offset in range(0, len(pending), batch_size):
            batch = pending[offset:offset + batch_size]
            # Submit only this batch. An error cannot enqueue the remaining dataset.
            chunks = [batch[i::workers] for i in range(workers) if batch[i::workers]]
            list(pool.map(check, chunks))
            state['verified'].update({r['path']: r['sha256'] for r in batch})
            temp = checkpoint.with_suffix('.tmp')
            temp.write_text(json.dumps(state, sort_keys=True) + '\n')
            temp.replace(checkpoint)
            print('Verified', len(state['verified']), '/', len(rows), 'files', flush=True)
    # Even a fully resumed run checks that source/config paths still return 404.
    check(rows[:1])
    return {'url': url, 'files_verified': len(rows),
            'bytes_verified': sum(r['bytes'] for r in rows),
            'batch_size': batch_size, 'workers': workers}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--app', required=True, type=Path)
    parser.add_argument('--url', required=True)
    args = parser.parse_args()
    sys.path.insert(0, str(args.app.resolve() / 'scripts'))
    from verify_firebase_reference_data import verify, download
    report = json.loads((args.app / 'hosting-stage-report.json').read_text())
    result = verify_batches(args.url, report, verify, download,
                            args.app / 'gb-preview-progress.json')
    if result['bytes_verified'] != report['total_bytes']:
        raise ValueError('Hosted byte total differs from staging')
    (args.app / 'gb-preview-verification.json').write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps(result, indent=2))
