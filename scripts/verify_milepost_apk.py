"""Fail closed on missing production catalogue/artwork or synthetic sandbox in owner APK."""
import hashlib
import json
import pathlib
import sys
import zipfile


def verify(path):
    with zipfile.ZipFile(path) as apk:
        names = set(apk.namelist())
        payload = apk.read('assets/millennium-mileposts/catalogue.json')
        assert hashlib.sha256(payload).hexdigest() == '1f7468a5ed054c5822c88614650fe33814554502a57779fc1b7375749d35c2ce'
        catalogue = json.loads(payload)
        assert catalogue['revision'] == 3 and len(catalogue['records']) == 887
        assert all(r['status'] == 'approved' for r in catalogue['records'])
        assert catalogue['licence'] == 'ODbL-1.0'
        aliases = {a for r in catalogue['records'] for a in r['aliases']}
        assert not aliases.intersection({'osm:node:416710572', 'osm:node:9825206150'})
        # Resource packaging can qualify drawable directories by Android version.
        expected = {p.name for p in pathlib.Path('android-prototype/app/src/main/res/drawable').glob('milepost_*.xml')}
        assert len(expected) >= 23
        packaged = {pathlib.PurePosixPath(n).name for n in names if n.startswith('res/drawable')}
        assert expected <= packaged, expected - packaged
        dex = b''.join(apk.read(n) for n in sorted(names) if n.endswith('.dex'))
        for production in (b'MilepostCatalogue;', b'MilepostIntegration;', b'MilepostReconciliationJob;', b'MilepostPresentationView;'):
            assert production in dex, production
        for forbidden in (b'MilepostAcceptance', b'synthetic-acceptance.db', b'milepostacceptance'):
            assert forbidden not in dex, forbidden
        assert not any('synthetic' in n.lower() or 'acceptance' in n.lower() for n in names if n.startswith('assets/'))
        print(f'APK production catalogue, held exclusions, {len(expected)} vectors, runtime classes and sandbox isolation verified')


if __name__ == '__main__':
    verify(sys.argv[1])
