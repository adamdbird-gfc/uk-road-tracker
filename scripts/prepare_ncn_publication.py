"""Recover or reuse exact reviewed NCN bytes; never repin a candidate."""
import argparse
import gzip
import json
from pathlib import Path
import sqlite3
import struct
import tempfile
from scripts.ncn_reference import sha_file, verify_database

ROOT = Path('docs/data-trials/ncn-publication-readiness-20261104')


def recover_writer_header(candidate, expected_sha, writer):
    """Restore informational writer bytes only if the full pinned hash proves it.

    SQLite header 96..99 is SQLITE_VERSION_NUMBER, not reference data. No page,
    change counter, schema or application metadata is rewritten. Restore on failure.
    """
    before = sha_file(candidate)
    result = {'before_sha256': before, 'original_writer': writer,
              'runtime_sqlite': sqlite3.sqlite_version, 'changed_offsets': []}
    if before == expected_sha:
        return result
    major, minor, patch = map(int, writer.split('.'))
    stamp = struct.pack('>I', major*1000000 + minor*1000 + patch)
    with candidate.open('r+b') as out:
        if out.read(16) != b'SQLite format 3\x00':
            raise ValueError('Invalid SQLite header')
        out.seek(96); previous = out.read(4)
        out.seek(96); out.write(stamp)
    if sha_file(candidate) != expected_sha:
        with candidate.open('r+b') as out:
            out.seek(96); out.write(previous)
        raise ValueError('Original SQLite bytes cannot be recovered by writer header normalization')
    result.update(changed_offsets=[96+i for i in range(4) if previous[i] != stamp[i]],
                  previous_writer_number=struct.unpack('>I', previous)[0],
                  restored_writer_number=struct.unpack('>I', stamp)[0])
    return result


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


def run(reconstruct=False, prepare_replacement=False):
    original_manifest = json.loads((ROOT/'candidate-manifest.json').read_text())
    request = json.loads(Path('.github/ncn-reference/request.json').read_text())
    manifest_path = Path(request.get('candidate_manifest', str(ROOT/'candidate-manifest.json')))
    if manifest_path.parent != ROOT:
        raise ValueError('Unexpected candidate manifest path')
    expected = json.loads(manifest_path.read_text())
    seed = json.loads((ROOT/'live-manifest.json').read_text())
    database = Path('.cache/ncn/catalogue.db')
    verify_database(database, seed)
    work = Path('ncn-publication-evidence')
    folder = work/'candidate'
    canonical = json.loads((ROOT/'receipt.json').read_text())['canonical_content_sha256']
    recovery = {'method': 'reuse_verified_archive'}
    if not (folder/'catalogue.db.gz').exists():
        if not (reconstruct or prepare_replacement):
            raise ValueError('Exact preserved candidate archive required; regeneration is explicit only')
        from scripts.reproduce_ncn_ordinary_seams import reproduce
        from scripts.ncn_deal_correspondence import apply
        from scripts.ncn_binding_attainability import content_sha
        from scripts.finalize_ncn_android import pack
        seam = reproduce(database, work/'seam/ncn-national.db')
        candidate = apply(seam, folder/'ncn-national.db')
        if content_sha(candidate) != canonical:
            raise ValueError('Combined canonical candidate differs')
        original = json.loads(gzip.decompress(Path('.github/ncn-reference/stage1-binding-correction.json.gz').read_bytes()))
        if prepare_replacement:
            recovery = {'method': 'separately_validated_replacement', 'runtime_sqlite': sqlite3.sqlite_version,
                        'original_writer': original['sqlite_writer_version'], 'superseded_manifest': original_manifest}
        else:
            recovery = recover_writer_header(candidate, expected['sha256'], original['sqlite_writer_version'])
            recovery['method'] = 'exact_checksum_proven_writer_header_recovery'
        (folder/'manifest.json').write_text(json.dumps(expected)+'\n')
        pack(folder)
        if prepare_replacement:
            expected = json.loads((folder/'manifest.json').read_text())
    result = verify_archive(folder, expected, canonical)
    result['recovery'] = recovery
    (work/'candidate-verification.json').write_text(json.dumps(result, indent=2)+'\n')
    print(json.dumps(result, indent=2))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    group = parser.add_mutually_exclusive_group()
    group.add_argument('--reconstruct', action='store_true')
    group.add_argument('--prepare-replacement', action='store_true')
    args = parser.parse_args()
    run(args.reconstruct, args.prepare_replacement)
