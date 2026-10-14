import gzip
import json
import tempfile
import unittest
from pathlib import Path
from prepare_gb_settlement_release import digest, prepare


class AdditiveReleaseTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.published = self.root / 'published'
        self.candidates = self.root / 'candidates'
        (self.published / 'settlement-inventories-v1').mkdir(parents=True)
        (self.candidates / 'inventories').mkdir(parents=True)
        self.output = self.root / 'release'
        self.boundaries = self.root / 'boundaries.geojson.gz'
        codes = ['E00000001', 'S45000364', 'E00000002']
        features = [{'type': 'Feature', 'properties': {'code': code, 'name': code},
                     'geometry': {'type': 'Polygon', 'coordinates': [[[0, 0], [1, 0], [1, 1], [0, 0]]]}}
                    for code in codes]
        with gzip.open(self.boundaries, 'wt') as stream:
            json.dump({'features': features}, stream)
        entries = []
        for code, roads in zip(codes, [['Changed Road', 'New Road'], ['Scottish Road'], []]):
            path = self.candidates / 'inventories' / (code + '.json')
            path.write_text(json.dumps({'code': code, 'name': code, 'count': len(roads), 'roads': roads}))
            entries.append({'code': code, 'count': len(roads), 'bytes': path.stat().st_size, 'sha256': digest(path)})
        (self.candidates / 'candidate-manifest.json').write_text(json.dumps({'inventories': entries}))
        (self.candidates / 'generation-audit.json').write_text(json.dumps({
            'boundary_snapshot_sha256': digest(self.boundaries), 'source': {'osm_timestamp': 'test'},
            'existing_inventory_differences': [{'code': 'E00000001'}]}))
        (self.published / 'settlement-inventory-manifest-v1.json').write_text(json.dumps({
            'version': 1, 'inventories': [{'code': 'E00000001', 'status': 'available', 'count': 1}]}))
        self.old = self.published / 'settlement-inventories-v1/E00000001.json'
        self.old.write_text('{"code":"E00000001","count":1,"roads":["Legacy Road"]}\n')
        self.old_boundary = self.published / 'settlement-inventories-v1/E00000001-boundary.geojson'
        self.old_boundary.write_text(json.dumps(features[0]) + '\n')

    def test_preserves_existing_bytes_and_adds_scottish_and_known_zero(self):
        report = prepare(self.published, self.candidates, self.boundaries, self.output)
        assets = self.output / 'settlement-inventories-v1'
        self.assertEqual((assets / self.old.name).read_bytes(), self.old.read_bytes())
        self.assertEqual((assets / self.old_boundary.name).read_bytes(), self.old_boundary.read_bytes())
        self.assertEqual(report['preserved_existing_inventories'], 1)
        self.assertEqual(report['new_inventories'], 2)
        self.assertEqual(report['known_zero_eligible_codes'], ['E00000002'])
        rows = json.loads((self.output / 'settlement-inventory-manifest-v1.json').read_text())['inventories']
        self.assertEqual({x['code']: x['count'] for x in rows}, {'E00000001': 1, 'E00000002': 0, 'S45000364': 1})

    def test_integrity_failure_writes_no_release(self):
        path = self.candidates / 'inventories/S45000364.json'
        path.write_text('{}')
        with self.assertRaises(ValueError):
            prepare(self.published, self.candidates, self.boundaries, self.output)
        self.assertFalse(self.output.exists())

    def test_boundary_snapshot_mismatch_rejected(self):
        self.boundaries.write_bytes(b'changed')
        with self.assertRaises(ValueError):
            prepare(self.published, self.candidates, self.boundaries, self.output)

    def test_published_root_cannot_be_overwritten(self):
        with self.assertRaises(ValueError):
            prepare(self.published, self.candidates, self.boundaries, self.published / 'release')

    def test_cannot_reuse_populated_output(self):
        self.output.mkdir()
        (self.output / 'old.txt').write_text('old')
        with self.assertRaises(ValueError):
            prepare(self.published, self.candidates, self.boundaries, self.output)
