"""Recover or reuse exact reviewed NCN bytes; never repin a candidate."""
import argparse
import gzip
import json
from pathlib import Path
import sqlite3
import tempfile
import zlib
from scripts.ncn_reference import sha_file, verify_database

ROOT = Path('docs/data-trials/ncn-publication-readiness-20261104')


def verify_archive(folder, expected, canonical):
    """Separate manifest, compressed bytes, expanded bytes and logical checks."""
    from scripts.ncn_binding_attainability import content_sha
    from scripts.ncn_binding_attainability import encoded
    import hashlib
    manifest = json.loads((folder/'manifest.json').read_text())
    if manifest != expected:
        raise ValueError('Exact prepared publication pins differ')
    asset = folder/'catalogue.db.gz'
    if asset.stat().st_size != expected['compressed_bytes'] or sha_file(asset) != expected['compressed_sha256']:
        raise ValueError('Compressed NCN asset differs')
    with tempfile.TemporaryDirectory() as work:
        database = Path(work)/'catalogue.db'
        with gzip.open(asset, 'rb') as source, database.open('wb') as target:
            total = 0
            for chunk in iter(lambda: source.read(65536), b''):
                total += len(chunk)
                if total > expected['database_bytes']:
                    raise ValueError('NCN expanded size exceeds manifest')
                target.write(chunk)
        verify_database(database, expected)
        actual = content_sha(database)
        if actual != canonical:
            raise ValueError('Combined canonical candidate differs')
        with sqlite3.connect(database.resolve().as_uri()+'?mode=ro', uri=True) as db:
            schema = list(db.execute('SELECT type,name,tbl_name,sql FROM sqlite_master ORDER BY type,name'))
            tables = [r[0] for r in db.execute("SELECT name FROM sqlite_master WHERE type='table' ORDER BY name")]
            counts = {name: db.execute('SELECT count(*) FROM '+name).fetchone()[0] for name in tables}
    return {'canonical_content_sha256': actual, 'schema_sha256': hashlib.sha256(encoded(schema)).hexdigest(),
            'table_counts': counts, 'manifest': expected, 'status': 'all_integrity_properties_verified'}


def run(reconstruct=False):
    expected = json.loads((ROOT/'candidate-manifest.json').read_text())
    seed = json.loads((ROOT/'live-manifest.json').read_text())
    database = Path('.cache/ncn/catalogue.db')
    verify_database(database, seed)
    work = Path('ncn-publication-evidence')
    folder = work/'candidate'
    canonical = json.loads((ROOT/'receipt.json').read_text())['canonical_content_sha256']
    recovery = {'method': 'reuse_verified_archive'}
    if not (folder/'catalogue.db.gz').exists():
        if not reconstruct:
            raise ValueError('Exact preserved candidate archive required; regeneration is explicit only')
        if sqlite3.sqlite_version != '3.53.1' or zlib.ZLIB_RUNTIME_VERSION != '1.3.2':
            raise ValueError('Exact reviewed SQLite and zlib writer versions required')
        with sqlite3.connect(':memory:') as writer:
            if writer.execute('PRAGMA secure_delete').fetchone()[0] != 0:
                raise ValueError('Reviewed SQLite secure_delete=OFF writer required')
        from scripts.reproduce_ncn_ordinary_seams import reproduce
        from scripts.ncn_deal_correspondence import apply
        from scripts.ncn_binding_attainability import content_sha
        from scripts.finalize_ncn_android import pack
        seam = reproduce(database, work/'seam/ncn-national.db')
        candidate = apply(seam, folder/'ncn-national.db')
        if content_sha(candidate) != canonical:
            raise ValueError('Combined canonical candidate differs')
        if sha_file(candidate) != expected['sha256']:
            raise ValueError('Exact original database bytes differ')
        recovery = {'method': 'pinned_original_writer', 'sqlite': sqlite3.sqlite_version,
                    'zlib': zlib.ZLIB_RUNTIME_VERSION}
        (folder/'manifest.json').write_text(json.dumps(expected)+'\n')
        pack(folder)
    result = verify_archive(folder, expected, canonical)
    result['recovery'] = recovery
    (work/'candidate-verification.json').write_text(json.dumps(result, indent=2)+'\n')
    print(json.dumps(result, indent=2))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--reconstruct', action='store_true')
    run(parser.parse_args().reconstruct)
