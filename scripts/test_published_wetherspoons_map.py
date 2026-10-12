import json
from pathlib import Path
import tempfile
import unittest
from publish_wetherspoons_map import publish, verify


class PublishedMapTest(unittest.TestCase):
    def data(self):
        return {"schema_version": 1, "collection_id": "wetherspoons", "scope": "UK",
                "release_status": "approved", "approval_scope": "map_only_partial_beta",
                "ready_for_app": True, "coverage_complete": False, "visit_enabled": False,
                "completion_target": None, "catalogue_version": "2026-10-12.test",
                "attribution": "OSM", "review_summary": {"map_venues": 1},
                "venues": [{"id": "spoons-osm-n1", "status": "active",
                            "operating_status": "reviewed_current", "visit_enabled": False,
                            "map_reviewed_at": "2026-10-12", "name": "Pub", "town": "",
                            "lat": 51, "lon": 0, "osm_aliases": ["node/1"]}]}

    def test_publication_round_trip_and_unchanged_release(self):
        with tempfile.TemporaryDirectory() as tmp:
            source = Path(tmp, "input.json")
            source.write_text(json.dumps(self.data()))
            result = publish(source, 2026101201, Path(tmp, "published"))
            self.assertEqual(result, verify(Path(tmp, "published")))
            self.assertEqual(result, publish(source, 2026101201, Path(tmp, "published")))

    def test_existing_revision_is_immutable_and_downgrade_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            source, directory = Path(tmp, "input.json"), Path(tmp, "published")
            data = self.data()
            source.write_text(json.dumps(data))
            publish(source, 2026101202, directory)
            with self.assertRaises(ValueError):
                publish(source, 2026101201, directory)
            data["venues"][0]["name"] = "Other pub"
            source.write_text(json.dumps(data))
            with self.assertRaises(ValueError):
                publish(source, 2026101202, directory)

    def test_candidate_or_visit_enabled_data_never_publishes(self):
        for change in ({"release_status": "audit_only"}, {"visit_enabled": True},
                       {"ready_for_app": 1}, {"schema_version": 2}):
            with self.subTest(change=change), tempfile.TemporaryDirectory() as tmp:
                source = Path(tmp, "input.json")
                data = self.data()
                data.update(change)
                source.write_text(json.dumps(data))
                with self.assertRaises(ValueError):
                    publish(source, 2026101201, Path(tmp, "published"))

    def test_tampered_release_fails_verification(self):
        with tempfile.TemporaryDirectory() as tmp:
            source, directory = Path(tmp, "input.json"), Path(tmp, "published")
            source.write_text(json.dumps(self.data()))
            publish(source, 2026101201, directory)
            Path(directory, "catalogue-2026101201.json").write_text("{}")
            with self.assertRaises(ValueError):
                verify(directory)

    def test_manifest_cannot_escape_publication_directory(self):
        with tempfile.TemporaryDirectory() as tmp:
            source, directory = Path(tmp, "input.json"), Path(tmp, "published")
            source.write_text(json.dumps(self.data()))
            publish(source, 2026101201, directory)
            path = Path(directory, "manifest-v1.json")
            manifest = json.loads(path.read_text())
            manifest["filename"] = "../input.json"
            path.write_text(json.dumps(manifest))
            with self.assertRaises(ValueError):
                verify(directory)


if __name__ == "__main__":
    unittest.main()
