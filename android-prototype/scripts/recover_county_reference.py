#!/usr/bin/env python3
"""Recover exact pinned county boundaries from Firebase or an archived APK."""
import hashlib
import json
import pathlib
import sys
import zipfile


def recover(apk, asset_dir):
    asset_dir = pathlib.Path(asset_dir)
    expected = json.loads((asset_dir / 'catalogue.json').read_text())
    with zipfile.ZipFile(apk) as archive:
        catalogue = json.loads(archive.read('assets/historic-counties/catalogue.json'))
        data = archive.read('assets/historic-counties/boundaries.bin')
    if catalogue != expected:
        raise ValueError('Fallback county catalogue does not match the pinned reference')
    if hashlib.sha256(data).hexdigest() != expected['data_sha256']:
        raise ValueError('Fallback county boundaries do not match the pinned checksum')
    staging = asset_dir / 'boundaries.bin.tmp'
    staging.write_bytes(data)
    staging.replace(asset_dir / 'boundaries.bin')
    print('Exact pinned county catalogue and boundary checksum verified')


def recover_boundary_file(boundaries, asset_dir):
    asset_dir = pathlib.Path(asset_dir)
    expected = json.loads((asset_dir / 'catalogue.json').read_text())
    data = pathlib.Path(boundaries).read_bytes()
    if hashlib.sha256(data).hexdigest() != expected['data_sha256']:
        raise ValueError('Hosted county boundaries do not match the pinned checksum')
    staging = asset_dir / 'boundaries.bin.tmp'
    staging.write_bytes(data)
    staging.replace(asset_dir / 'boundaries.bin')
    print('Firebase county boundary checksum verified')


if __name__ == '__main__':
    if sys.argv[1] == '--boundaries':
        recover_boundary_file(sys.argv[2], sys.argv[3])
    else:
        recover(sys.argv[1], sys.argv[2])
