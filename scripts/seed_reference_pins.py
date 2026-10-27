#!/usr/bin/env python3
"""Import existing local reference files; no URLs, credentials or network code."""
import argparse
import hashlib
import json
import os
import shutil
import tempfile
from pathlib import Path
from prepare_ncn_reference import prepare

ROOT = Path(__file__).resolve().parents[1]


def seed(source, root=ROOT):
    # The source is a local directory, e.g. an already downloaded GitHub artifact.
    catalogue = json.loads((root / 'android-prototype/app/src/main/assets/historic-counties/catalogue.json').read_text())
    county = source / 'boundaries.bin'
    expected_bytes = max(row['offset'] + row['length'] for row in catalogue['counties'])
    if county.stat().st_size != expected_bytes:
        raise ValueError('County input length differs from pin')
    with county.open('rb') as stream:
        digest = hashlib.file_digest(stream, 'sha256').hexdigest()
    if digest != catalogue['data_sha256']:
        raise ValueError('County input checksum differs from pin')
    # Verify both inputs before publishing either to the build paths.
    from ncn_reference import verify_database
    manifest = root / 'android-prototype/app/src/main/assets/ncn-national/manifest.json'
    verify_database(source / 'catalogue.db', json.loads(manifest.read_text()))
    prepare(manifest, root / '.cache/ncn', source_database=source / 'catalogue.db')
    directory = root / 'android-prototype/app/src/main/assets/historic-counties'
    handle, name = tempfile.mkstemp(prefix='boundaries-', suffix='.tmp', dir=directory)
    temporary = Path(name)
    try:
        with os.fdopen(handle, 'wb') as out, county.open('rb') as incoming:
            shutil.copyfileobj(incoming, out)
            out.flush()
            os.fsync(out.fileno())
        with temporary.open('rb') as stream:
            if hashlib.file_digest(stream, 'sha256').hexdigest() != catalogue['data_sha256']:
                raise ValueError('County input changed during import')
        temporary.replace(directory / 'boundaries.bin')
    finally:
        temporary.unlink(missing_ok=True)
    print('Both local reference pins verified and installed; no Firebase access')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source-directory', required=True, type=Path)
    seed(parser.parse_args().source_directory)
