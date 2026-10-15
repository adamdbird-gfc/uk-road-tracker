#!/usr/bin/env python3
"""Add the exact pinned county boundary binary without changing reference data."""
import gzip
import hashlib
import json
import os
import time
from pathlib import Path
import zipfile

import requests
from google.auth.transport.requests import AuthorizedSession
from google.oauth2.service_account import Credentials
from preview_ni_reference_data import Hosting, SITE, version_name

DIGEST = 'b81fe3bf42400548c45576750d4e40ca2795975ddbc8903b83a9bb6072c6e850'
ASSET = 'build-references/historic-counties/' + DIGEST + '/boundaries.bin'
CHANNEL = 'county-build-reference'


def digest(raw):
    return hashlib.sha256(raw).hexdigest()


class Publisher(Hosting):
    def request(self, method, path, **kwargs):
        # This tool can publish only versions that pass the preservation checks below.
        response = self.session.request(method, 'https://firebasehosting.googleapis.com/v1beta1/' + path,
                                        timeout=60, allow_redirects=False, **kwargs)
        if response.status_code not in (200, 201):
            raise RuntimeError(f'Hosting operation failed: HTTP {response.status_code}')
        return response.json()


def served(url):
    for attempt in range(12):
        response = requests.get(url + '/' + ASSET, timeout=90)
        if response.status_code == 200 and digest(response.content) == DIGEST:
            return
        # A newly released preview/live version can take a few seconds to reach
        # the CDN. Never promote until actual serving bytes pass the checksum.
        if attempt < 11:
            time.sleep(5)
    raise ValueError('Hosted county reference did not serve the pinned bytes')


def main():
    expected = json.loads(Path('app-source/android-prototype/app/src/main/assets/historic-counties/catalogue.json').read_text())
    with zipfile.ZipFile('pinned.apk') as archive:
        if json.loads(archive.read('assets/historic-counties/catalogue.json')) != expected:
            raise ValueError('Pinned source catalogue changed')
        raw = archive.read('assets/historic-counties/boundaries.bin')
    if expected['data_sha256'] != DIGEST or digest(raw) != DIGEST:
        raise ValueError('Pinned source boundaries changed')
    credentials = json.loads(os.environ['FIREBASE_HOSTING_SERVICE_ACCOUNT_JSON'])
    if credentials.get('project_id') != 'roadprints-822cc' or os.environ['ROADPRINTS_DATA_HOSTING_SITE'] != SITE:
        raise ValueError('Unexpected Firebase destination')
    session = AuthorizedSession(Credentials.from_service_account_info(credentials,
        scopes=['https://www.googleapis.com/auth/firebase.hosting']))
    hosting = Publisher(session)
    live_path = f'sites/{SITE}/channels/live'
    source = version_name(hosting.request('GET', live_path)['release']['version']['name'])
    before = hosting.files(source)
    config = hosting.request('GET', source).get('config', {})
    manifest_response = requests.get(f'https://{SITE}.web.app/settlement-inventory-manifest-v1.json', timeout=60)
    manifest_response.raise_for_status()
    inventories = manifest_response.json()['inventories']
    if len(inventories) != 9072 or len({r['code'] for r in inventories}) != 9072:
        raise ValueError('Full UK dataset must already be live')
    packed = gzip.compress(raw, compresslevel=9, mtime=0)
    content_hash = digest(packed)
    if ASSET in before and before[ASSET] != content_hash:
        raise ValueError('Existing pinned asset differs')
    target = version_name(hosting.wait(hosting.request('POST', f'sites/{SITE}/versions:clone',
        json={'sourceVersion': source, 'finalize': False}))['name'])
    if hosting.files(target) != before:
        raise ValueError('Clone does not preserve live files')
    result = hosting.request('POST', target + ':populateFiles', json={'files': {'/' + ASSET: content_hash}})
    required = set(result.get('uploadRequiredHashes', []))
    if not required <= {content_hash}:
        raise ValueError('Unexpected upload requested')
    if required:
        upload = result['uploadUrl']
        if upload != 'https://upload-firebasehosting.googleapis.com/upload/' + target + '/files':
            raise ValueError('Unexpected upload destination')
        response = session.post(upload + '/' + content_hash, data=packed,
            headers={'Content-Type': 'application/octet-stream'}, timeout=90, allow_redirects=False)
        response.raise_for_status()
    hosting.request('PATCH', target, params={'updateMask': 'status'}, json={'status': 'FINALIZED'})
    after = hosting.files(target)
    if after != {**before, ASSET: content_hash} or hosting.request('GET', target).get('config', {}) != config:
        raise ValueError('Existing files or configuration changed')
    # Create a short-lived preview before touching the live channel.
    response = session.get('https://firebasehosting.googleapis.com/v1beta1/' + f'sites/{SITE}/channels/{CHANNEL}', timeout=60)
    if response.status_code == 404:
        hosting.request('POST', f'sites/{SITE}/channels', params={'channelId': CHANNEL},
                        json={'ttl': '86400s', 'retainedReleaseCount': 1})
    else:
        response.raise_for_status()
    hosting.request('POST', f'sites/{SITE}/channels/{CHANNEL}/releases', params={'versionName': target}, json={})
    preview = hosting.request('GET', f'sites/{SITE}/channels/{CHANNEL}')['url']
    if not preview.startswith('https://' + SITE + '--' + CHANNEL + '-') or not preview.endswith('.web.app'):
        raise ValueError('Unexpected preview origin')
    served(preview)
    if hosting.request('GET', live_path)['release']['version']['name'] != source:
        raise ValueError('Live publication changed during preparation')
    hosting.request('POST', f'sites/{SITE}/releases', params={'versionName': target},
                    json={'message': 'Add verified county build reference; preserve all UK settlement data'})
    served('https://' + SITE + '.web.app')
    if hosting.files(target) != after or hosting.request('GET', live_path)['release']['version']['name'] != target:
        raise ValueError('Live version verification failed')
    Path('maintenance-evidence').mkdir(exist_ok=True)
    Path('maintenance-evidence/county-publication.json').write_text(json.dumps({
        'status': 'live_verified', 'source_version': source, 'version': target,
        'preserved_files': len(before), 'boundary_sha256': DIGEST, 'bytes': len(raw),
        'url': f'https://{SITE}.web.app/{ASSET}'}, indent=2) + '\n')
    print('Pinned county reference published and verified; all existing files preserved.')


if __name__ == '__main__':
    main()
