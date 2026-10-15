#!/usr/bin/env python3
"""Promote the verified immutable Hosting version and register public counts."""
import argparse
from datetime import datetime, timezone
import gzip
import hashlib
import json
import os
from pathlib import Path
import re
import sys
import threading
import time

from verify_firebase_reference_data import verify
from verify_gb_preview import verify_batches

SITE = 'roadprints-data-822cc'
CHANNEL = 'gb-reviewed-20261014'
PREVIEW = 'https://roadprints-data-822cc--gb-reviewed-20261014-kmqv5qci.web.app'
LIVE = 'https://' + SITE + '.web.app'
REPORT_HASH = '33401ac0431acc84e79f35a95e70ce27cd8557c913dbe8d050a38fba6fe83015'
VERIFIED_AT = datetime.fromisoformat('2026-10-15T06:09:27.688044+00:00')
API = 'https://firebasehosting.googleapis.com/v1beta1/'
MANIFEST = 'settlement-inventory-manifest-v1.json'


def load_report(path):
    raw = gzip.decompress(Path(path).read_bytes())
    if hashlib.sha256(raw).hexdigest() != REPORT_HASH:
        raise ValueError('Reviewed report digest changed')
    report = json.loads(raw)
    paths = [r['path'] for r in report['files']]
    if (report['schema_version'] != 1 or len(paths) != 17098 or len(set(paths)) != len(paths)
            or report['total_bytes'] != 216636764
            or sum(r['bytes'] for r in report['files']) != report['total_bytes']):
        raise ValueError('Reviewed report is incomplete')
    return report


class Fetcher:
    """Keep four independent public connections; never send credentials here."""
    def __init__(self):
        self.local = threading.local()

    def __call__(self, url, limit):
        import requests
        if not hasattr(self.local, 'session'):
            self.local.session = requests.Session()
            self.local.session.headers.update({'Accept-Encoding': 'identity'})
        for attempt in range(4):
            try:
                with self.local.session.get(url, timeout=(15, 30), allow_redirects=False, stream=True) as response:
                    payload = response.raw.read(limit + 1, decode_content=False) if response.status_code == 200 else b''
                    result = response.status_code, dict(response.headers), payload
                public = url.endswith(('.json', '.geojson')) and not url.endswith(('firebase.json', 'google-services.json'))
                if result[0] not in (408, 429, 500, 502, 503, 504) and not (public and result[0] == 404):
                    return result
            except requests.RequestException:
                if attempt == 3:
                    raise OSError('Public file request failed') from None
            if attempt == 3:
                return result
            time.sleep(2 ** attempt)


def manifest_counts(manifest):
    rows = manifest['inventories']
    result = {}
    for row in rows:
        code, count = row['code'], row['count']
        if (not re.fullmatch(r'[EWSK]\d{8}', code) or code in result
                or row['status'] != 'available' or type(count) is not int or count < 0):
            raise ValueError('Invalid public inventory manifest')
        result[code] = count
    return result


def check_database(rows, counts):
    """Fail before writes if identities or a previously ready total drifted."""
    existing = {code: (status, count) for code, status, count in rows}
    if len(existing) != len(rows) or set(existing) != set(counts):
        raise ValueError('Active settlement catalogue differs from release')
    for code, (status, count) in existing.items():
        if status == 'building':
            raise ValueError('An inventory build is still active')
        if status == 'ready' and count != counts[code]:
            raise ValueError('A ready total differs from the reviewed release')


def register_counts(connection, counts, write=False):
    with connection.cursor() as cursor:
        cursor.execute("SET LOCAL lock_timeout = '15s'")
        cursor.execute("SET LOCAL statement_timeout = '60s'")
        if write:
            cursor.execute('LOCK TABLE settlements IN SHARE MODE')
            cursor.execute('LOCK TABLE settlement_inventories IN SHARE ROW EXCLUSIVE MODE')
        cursor.execute('''SELECT s.external_code, i.status, i.road_count FROM settlements s
                          LEFT JOIN settlement_inventories i ON i.settlement_id=s.id
                          WHERE s.is_active ORDER BY s.external_code''')
        check_database(cursor.fetchall(), counts)
        if not write:
            return
        cursor.execute('CREATE TEMP TABLE reviewed_counts (code text PRIMARY KEY, road_count integer NOT NULL) ON COMMIT DROP')
        with cursor.copy('COPY reviewed_counts (code, road_count) FROM STDIN') as copy:
            for code, count in sorted(counts.items()):
                copy.write_row((code, count))
        cursor.execute('''INSERT INTO settlement_inventories AS i
          (settlement_id,status,road_count,source_key,source_version,inventory_version,completed_at,updated_at)
          SELECT s.id,'ready',r.road_count,'osm-named-roads','gb-reviewed-2026-10-14','v1',NOW(),NOW()
          FROM reviewed_counts r JOIN settlements s ON s.external_code=r.code AND s.is_active
          ON CONFLICT (settlement_id) DO UPDATE SET status='ready',road_count=EXCLUDED.road_count,
          source_key=EXCLUDED.source_key,source_version=EXCLUDED.source_version,
          inventory_version=EXCLUDED.inventory_version,completed_at=NOW(),updated_at=NOW(),
          build_started_at=NULL,failed_at=NULL,failure_code=NULL
          WHERE i.status <> 'ready' ''')
        cursor.execute('''SELECT s.external_code,i.status,i.road_count FROM settlements s
                          LEFT JOIN settlement_inventories i ON i.settlement_id=s.id WHERE s.is_active''')
        rows = cursor.fetchall()
        check_database(rows, counts)
        if any(status != 'ready' for _, status, _ in rows):
            raise ValueError('Registration did not reach complete readiness')
    # The caller's connection context commits everything together or rolls back.


def checked_channel(channel):
    release = channel['release']
    version = release['version']
    if (channel['url'] != PREVIEW or version.get('status') != 'FINALIZED'
            or not re.fullmatch(r'sites/' + SITE + r'/versions/[A-Za-z0-9_-]+', version['name'])
            or datetime.fromisoformat(release['releaseTime'].replace('Z', '+00:00')) > VERIFIED_AT):
        raise ValueError('Preview changed since full verification')
    return version['name']


def api_json(session, method, path, **kwargs):
    response = session.request(method, API + path, timeout=60, **kwargs)
    if response.status_code not in (200, 201):
        raise RuntimeError('Hosting API request failed with status ' + str(response.status_code))
    return response.json()


def run(options):
    import psycopg
    from google.auth.transport.requests import AuthorizedSession
    from google.oauth2.service_account import Credentials
    report = load_report(options.report)
    options.work.mkdir(parents=True, exist_ok=True)
    fetch = Fetcher()
    # Verify all collection objects and the complete settlement manifest again.
    subset = [r for r in report['files'] if r['path'].startswith('collections/') or r['path'] == MANIFEST]
    verify(PREVIEW, {'schema_version': 1, 'files': subset}, fetch=fetch)
    manifest_row = next(r for r in subset if r['path'] == MANIFEST)
    _, _, raw = fetch(PREVIEW + '/' + MANIFEST, manifest_row['bytes'])
    if hashlib.sha256(raw).hexdigest() != manifest_row['sha256']:
        raise ValueError('Preview manifest changed')
    counts = manifest_counts(json.loads(raw))
    if len(counts) != 8545:
        raise ValueError('Incomplete GB coverage')
    # Retain all already-live identities and totals; do not roll back collections.
    status, _, live_raw = fetch(LIVE + '/' + MANIFEST, 4 * 1024 * 1024)
    if status != 200:
        raise ValueError('Live manifest unavailable')
    current = manifest_counts(json.loads(live_raw))
    if any(code not in counts or counts[code] != count for code, count in current.items()):
        raise ValueError('Release would replace existing live inventory totals')
    verify(LIVE, {'schema_version': 1, 'files': [r for r in subset if r['path'].startswith('collections/')]}, fetch=fetch)
    database_url = os.environ['DATABASE_URL']
    with psycopg.connect(database_url, connect_timeout=30, options='-c default_transaction_read_only=on') as connection:
        register_counts(connection, counts)
    credential = json.loads(os.environ['FIREBASE_HOSTING_SERVICE_ACCOUNT_JSON'])
    if (credential.get('project_id') != 'roadprints-822cc'
            or credential.get('type') != 'service_account'
            or os.environ['ROADPRINTS_DATA_HOSTING_SITE'] != SITE):
        raise ValueError('Unexpected Hosting project or site')
    session = AuthorizedSession(Credentials.from_service_account_info(
        credential, scopes=['https://www.googleapis.com/auth/firebase.hosting']))
    version = checked_channel(api_json(session, 'GET', f'sites/{SITE}/channels/{CHANNEL}'))
    previous = api_json(session, 'GET', f'sites/{SITE}/channels/live')['release']['version']['name']
    state = {'preview_version': version, 'previous_live_version': previous, 'report_sha256': REPORT_HASH}
    (options.work / 'promotion.json').write_text(json.dumps(state, indent=2) + '\n')
    # Publish the immutable version just checked, without rebuilding or uploading.
    if previous != version:
        release = api_json(session, 'POST', f'sites/{SITE}/releases', params={'versionName': version},
                           json={'message': 'Promote fully verified GB reference dataset 2026-10-14'})
        if release['version']['name'] != version:
            raise ValueError('Live release references an unexpected version')
    print('Verified preview version is now live; checking served files.', flush=True)
    # Checkpoint is URL/report-specific and is retained if the bounded run fails.
    result = verify_batches(LIVE, report, verify, fetch, options.work / 'live-progress.json')
    if result['bytes_verified'] != report['total_bytes']:
        raise ValueError('Incomplete live verification')
    if api_json(session, 'GET', f'sites/{SITE}/channels/live')['release']['version']['name'] != version:
        raise ValueError('Live version changed during verification')
    with psycopg.connect(database_url, connect_timeout=30) as connection:
        register_counts(connection, counts, write=True)
    state.update(result)
    state.update({'status': 'published_and_registered', 'settlements': len(counts),
                  'completed_at': datetime.now(timezone.utc).isoformat()})
    (options.work / 'release-result.json').write_text(json.dumps(state, indent=2) + '\n')
    print('Live verification and atomic reference-count registration passed.', flush=True)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--report', type=Path, default=Path('.github/gb-live/hosting-stage-report.json.gz'))
    parser.add_argument('--work', type=Path, default=Path('gb-live-evidence'))
    try:
        run(parser.parse_args())
    except Exception as error:
        # Do not put credential, connection or internal query details in public logs.
        print('Release stopped: ' + type(error).__name__ + '. Inspect the failed step and retained public verification evidence.', file=sys.stderr)
        sys.exit(1)
