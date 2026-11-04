"""Reproduce the exact reviewed revision 4 using verified GitHub cache only."""
import json
import gzip
import sqlite3
import struct
from pathlib import Path
from scripts.ncn_binding_attainability import content_sha
from scripts.reproduce_ncn_ordinary_seams import reproduce
from scripts.ncn_deal_correspondence import apply
from scripts.finalize_ncn_android import pack
from scripts.ncn_reference import verify_database


def run():
    root = Path('docs/data-trials/ncn-publication-readiness-20261104')
    expected = json.loads((root/'candidate-manifest.json').read_text())
    seed = json.loads((root/'live-manifest.json').read_text())
    database = Path('.cache/ncn/catalogue.db')
    verify_database(database, seed)
    work = Path('ncn-publication-evidence')
    seam = reproduce(database, work/'seam/ncn-national.db')
    folder = work/'candidate'
    candidate = apply(seam, folder/'ncn-national.db')
    receipt = json.loads((root/'receipt.json').read_text())
    if content_sha(candidate) != receipt['canonical_content_sha256']:
        raise ValueError('Combined canonical candidate differs')
    # SQLite records the last writer version in bytes 96..99. Changing this
    # informational header is permitted only when the complete original digest
    # proves that it is the sole difference. Never approve logical equality alone.
    original = json.loads(gzip.decompress(Path('.github/ncn-reference/stage1-binding-correction.json.gz').read_bytes()))
    writer = original['sqlite_writer_version']
    from scripts.ncn_reference import sha_file
    before = sha_file(candidate)
    if before != expected['sha256']:
        parts = [int(v) for v in writer.split('.')]
        stamp = parts[0]*1000000 + parts[1]*1000 + parts[2]
        with candidate.open('r+b') as out:
            out.seek(96); previous = out.read(4)
            out.seek(96); out.write(struct.pack('>I', stamp))
        if sha_file(candidate) != expected['sha256']:
            with candidate.open('r+b') as out:
                out.seek(96); out.write(previous)
            raise ValueError('Original SQLite bytes cannot be recovered by writer header normalization')
    print(json.dumps({'runtime_sqlite': sqlite3.sqlite_version, 'original_writer': writer,
                      'before_header_normalization': before, 'exact_database_sha256': sha_file(candidate)}))
    (folder/'manifest.json').write_text(json.dumps(expected)+'\n')
    pack(folder)
    if json.loads((folder/'manifest.json').read_text()) != expected:
        raise ValueError('Exact prepared publication pins differ')
    print('Exact revision 4 reconstructed from verified offline cache; no Firebase download')


if __name__ == '__main__': run()
