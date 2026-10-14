"""Publication boundary tests: corrupt releases and unexpected files stay unpublished."""
import hashlib
import json
import shutil
import tempfile
import unittest
from pathlib import Path

from stage_firebase_reference_data import ROOT, stage


class ReferenceStagingTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name) / "source"
        shutil.copytree(ROOT / "collections/published", self.root / "collections/published")
        shutil.copy(ROOT / "collections/venue-collections-v1.json", self.root / "collections/venue-collections-v1.json")
        # A small valid settlement fixture keeps failure tests cheap.
        (self.root / "settlement-inventories-v1").mkdir()
        (self.root / "settlement-inventories-v1/E63000001.json").write_text(json.dumps({"code": "E63000001", "count": 2}))
        (self.root / "settlement-inventory-manifest-v1.json").write_text(json.dumps({"inventories": [{"code": "E63000001", "count": 2, "status": "available"}]}))
        self.out = Path(self.temp.name) / "public"

    def test_exact_public_assets_only_and_byte_identity(self):
        (self.root / "credentials.json").write_text('{"private_key":"fixture"}')
        (self.root / "collections/published/services/unexpected.json").write_text('{}')
        report = stage(self.root, self.root, self.out)
        self.assertFalse((self.out / "credentials.json").exists())
        self.assertFalse((self.out / "collections/published/services/unexpected.json").exists())
        self.assertFalse((self.out / "index.html").exists())
        for row in report["files"]:
            data = (self.out / row["path"]).read_bytes()
            self.assertEqual(data, (self.root / row["path"]).read_bytes())
            self.assertEqual(hashlib.sha256(data).hexdigest(), row["sha256"])

    def test_corrupt_payload_aborts_before_output(self):
        manifest = json.loads((self.root / "collections/published/services/manifest-v1.json").read_text())
        payload = self.root / "collections/published/services" / manifest["filename"]
        payload.write_bytes(payload.read_bytes() + b" ")
        with self.assertRaisesRegex(ValueError, "integrity"):
            stage(self.root, self.root, self.out)
        self.assertFalse(self.out.exists())

    def test_missing_inventory_aborts_before_output(self):
        (self.root / "settlement-inventories-v1/E63000001.json").unlink()
        with self.assertRaisesRegex(ValueError, "Missing"):
            stage(self.root, self.root, self.out)
        self.assertFalse(self.out.exists())

    def test_symlink_is_not_published(self):
        payload = self.root / "settlement-inventories-v1/E63000001.json"
        data = payload.read_bytes()
        payload.unlink()
        outside = Path(self.temp.name) / "private.json"
        outside.write_bytes(data)
        payload.symlink_to(outside)
        with self.assertRaisesRegex(ValueError, "unsafe"):
            stage(self.root, self.root, self.out)

    def test_nonempty_output_is_preserved(self):
        self.out.mkdir()
        keep = self.out / "keep.txt"
        keep.write_text("preserve")
        with self.assertRaisesRegex(ValueError, "empty"):
            stage(self.root, self.root, self.out)
        self.assertEqual(keep.read_text(), "preserve")

    def test_manifest_filename_cannot_escape(self):
        path = self.root / "collections/published/services/manifest-v1.json"
        manifest = json.loads(path.read_text())
        manifest["filename"] = "../../../credentials.json"
        path.write_text(json.dumps(manifest))
        with self.assertRaisesRegex(ValueError, "manifest"):
            stage(self.root, self.root, self.out)


if __name__ == "__main__":
    unittest.main()
