#!/usr/bin/env python3
"""Recover only exact pinned county assets from a previous verified APK."""
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


if __name__ == '__main__':
    recover(sys.argv[1], sys.argv[2])
