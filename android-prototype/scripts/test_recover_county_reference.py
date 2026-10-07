#!/usr/bin/env python3
import hashlib
import json
import pathlib
import tempfile
import unittest
import zipfile
from recover_county_reference import recover


class RecoveryTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = pathlib.Path(self.temp.name)
        self.assets = self.root / 'assets'
        self.assets.mkdir()
        self.data = b'pinned boundary bytes'
        self.catalogue = {'data_sha256': hashlib.sha256(self.data).hexdigest(),
                          'version': 'pinned', 'counties': [{'code': 'AAA', 'offset': 0}]}
        self.original = json.dumps(self.catalogue, indent=2)
        (self.assets / 'catalogue.json').write_text(self.original)
        (self.assets / 'boundaries.bin').write_bytes(b'existing')

    def apk(self, catalogue=None, data=None):
        apk = self.root / 'previous.apk'
        with zipfile.ZipFile(apk, 'w') as archive:
            archive.writestr('assets/historic-counties/catalogue.json',
                             json.dumps(self.catalogue if catalogue is None else catalogue))
            archive.writestr('assets/historic-counties/boundaries.bin', self.data if data is None else data)
        return apk

    def test_exact_reference_recovers_without_rewriting_catalogue(self):
        recover(self.apk(), self.assets)
        self.assertEqual(self.data, (self.assets / 'boundaries.bin').read_bytes())
        self.assertEqual(self.original, (self.assets / 'catalogue.json').read_text())

    def test_modified_boundary_bytes_are_rejected_without_overwriting(self):
        with self.assertRaisesRegex(ValueError, 'checksum'):
            recover(self.apk(data=b'changed'), self.assets)
        self.assertEqual(b'existing', (self.assets / 'boundaries.bin').read_bytes())

    def test_changed_catalogue_with_same_boundaries_is_rejected(self):
        changed = dict(self.catalogue, version='new')
        with self.assertRaisesRegex(ValueError, 'catalogue'):
            recover(self.apk(catalogue=changed), self.assets)
        self.assertEqual(b'existing', (self.assets / 'boundaries.bin').read_bytes())

    def test_missing_reference_is_rejected_without_overwriting(self):
        apk = self.root / 'empty.apk'
        with zipfile.ZipFile(apk, 'w'):
            pass
        with self.assertRaises(KeyError):
            recover(apk, self.assets)
        self.assertEqual(b'existing', (self.assets / 'boundaries.bin').read_bytes())


if __name__ == '__main__':
    unittest.main()
