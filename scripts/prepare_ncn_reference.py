#!/usr/bin/env python3
"""Verify or import local NCN test pins. This module has no network transport."""
import argparse
import gzip
import json
import os
import shutil
import tempfile
from pathlib import Path
from ncn_reference import validate, verify_database, sha_file


def prepare(manifest, directory, source_database=None, source_archive=None):
    m = validate(json.loads(manifest.read_text()))
    directory.mkdir(parents=True, exist_ok=True)
    database = directory / 'catalogue.db'
    if database.exists():
        try:
            verify_database(database, m)
            return database
        except (ValueError, OSError):
            pass
    if source_database is not None and source_archive is not None:
        raise ValueError('Provide one local NCN input')
    if source_database is None and source_archive is None:
        raise RuntimeError('Pinned NCN reference missing or invalid; offline mode forbids Firebase access. Provision --source-database or --source-archive from an existing verified local file.')
    if source_database is not None:
        verify_database(source_database, m)
    else:
        if source_archive.stat().st_size != m['compressed_bytes'] or sha_file(source_archive) != m['compressed_sha256']:
            raise ValueError('Compressed NCN input differs from pin')
    # Never overwrite a valid old generation before verifying the complete replacement.
    handle, name = tempfile.mkstemp(prefix='catalogue-', suffix='.tmp', dir=directory)
    temporary = Path(name)
    try:
        with os.fdopen(handle, 'wb') as target:
            if source_database is not None:
                with source_database.open('rb') as source:
                    shutil.copyfileobj(source, target)
            else:
                total = 0
                with gzip.open(source_archive, 'rb') as source:
                    for chunk in iter(lambda: source.read(65536), b''):
                        total += len(chunk)
                        if total > m['database_bytes']:
                            raise ValueError('NCN decoded limit exceeded')
                        target.write(chunk)
            target.flush()
            os.fsync(target.fileno())
        verify_database(temporary, m)
        temporary.replace(database)
        return database
    finally:
        temporary.unlink(missing_ok=True)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--directory', type=Path, default=Path('.cache/ncn'))
    local = parser.add_mutually_exclusive_group()
    local.add_argument('--source-database', type=Path)
    local.add_argument('--source-archive', type=Path)
    args = parser.parse_args()
    path = prepare(Path('android-prototype/app/src/main/assets/ncn-national/manifest.json'),
                   args.directory, args.source_database, args.source_archive).resolve()
    if os.environ.get('GITHUB_ENV'):
        with open(os.environ['GITHUB_ENV'], 'a') as target:
            target.write('NCN_TEST_DATABASE=' + str(path) + '\n')
    print('Verified local NCN test reference:', path)
