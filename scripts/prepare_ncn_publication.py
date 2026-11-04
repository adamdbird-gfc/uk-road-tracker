"""Reproduce the exact reviewed revision 4 using verified GitHub cache only."""
import json
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
    (folder/'manifest.json').write_text(json.dumps(expected)+'\n')
    pack(folder)
    if json.loads((folder/'manifest.json').read_text()) != expected:
        raise ValueError('Exact prepared publication pins differ')
    print('Exact revision 4 reconstructed from verified offline cache; no Firebase download')


if __name__ == '__main__': run()
