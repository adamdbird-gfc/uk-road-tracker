import json
import tempfile
import unittest
from pathlib import Path
from reconcile_published_settlement_inventories import published_inventories


class PublishedInventoryTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        (self.root / 'settlement-inventories-v1').mkdir()

    def write(self, code='K08000001', count=1, manifest_count=1):
        (self.root / 'settlement-inventory-manifest-v1.json').write_text(json.dumps({
            'inventories': [{'code': code, 'status': 'available', 'count': manifest_count}]}))
        (self.root / 'settlement-inventories-v1' / (code + '.json')).write_text(json.dumps({
            'code': code, 'name': 'Chester', 'count': count, 'roads': ['Example Road']}))
        (self.root / 'settlement-inventories-v1' / (code + '-boundary.geojson')).write_text(json.dumps({
            'properties': {'code': code}, 'geometry': {'type': 'Polygon', 'coordinates': []}}))

    def test_cross_border_inventory_is_reconciled(self):
        self.write()
        self.assertEqual(published_inventories(self.root)[0]['code'], 'K08000001')

    def test_manifest_mismatch_refuses_reconciliation(self):
        self.write(manifest_count=2)
        with self.assertRaises(ValueError):
            published_inventories(self.root)

    def test_missing_boundary_refuses_reconciliation(self):
        self.write()
        (self.root / 'settlement-inventories-v1/K08000001-boundary.geojson').unlink()
        with self.assertRaises(FileNotFoundError):
            published_inventories(self.root)

    def test_path_injection_refused(self):
        (self.root / 'settlement-inventory-manifest-v1.json').write_text(json.dumps({
            'inventories': [{'code': '../outside', 'status': 'available', 'count': 1}]}))
        with self.assertRaises(ValueError):
            published_inventories(self.root)
