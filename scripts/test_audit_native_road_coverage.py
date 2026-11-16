"""Exercise the Android export adapter with generated rows in the app's wire format."""

import json
from pathlib import Path
from tempfile import TemporaryDirectory
import unittest

from all_road_coverage import Edge, Network, geographic_point
from audit_native_road_coverage import audit, original_fix


class NativeCoverageAuditTests(unittest.TestCase):
    def setUp(self):
        directory = TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        self.directory = Path(directory.name)
        self.bounds = {"west": 0.37, "east": 0.39, "south": 51.43, "north": 51.45}
        self.edge = Edge("osm:1:a:b", ("a", "b"), "Ordnance Road", (30, 30), (150, 30), 0)
        self.network = Network("test-reference", [self.edge], self.bounds, tagged=True)

    def row(self, x, time, provider="gps", accuracy=3):
        return [*geographic_point(self.bounds, (x, 30)), accuracy, time, 10, provider, 0, 0]

    def export(self, name, rows=None, *, source="android_activity_capture", complete=True,
               mode="driving", confirmation="confirmed"):
        path = self.directory / name
        journey = {"journey_id": "test", "source": {"type": source}, "mode": mode,
                   "transport_confirmation": confirmation,
                   "capture_route_samples": [self.row(40, 1000), self.row(60, 3000)]}
        if rows is not None:
            journey["raw_capture_samples"] = rows
        path.write_text(json.dumps({"format": "roadprints_journey_diagnostics",
                                    "schema_version": 1, "export_complete": complete,
                                    "journey": journey}))
        return path

    def test_reads_original_android_fix_fields_and_counts_duplicate_once(self):
        rows = [self.row(40, 1000), self.row(65, 3000, "fused", 4)]
        fix = original_fix(self.bounds, rows[-1])
        self.assertAlmostEqual(65, fix.point[0], places=7)
        self.assertEqual((3000, 4), (fix.time_ms, fix.accuracy_m))
        files = [self.export("first.json", rows), self.export("copy.json", rows)]
        result = audit(files, self.network, ())
        self.assertEqual(2, result["exports"]["native_driving"])
        self.assertEqual(1, result["unique_native_driving_captures"])
        row, = result["native_driving_captures"]
        self.assertEqual(1, row["accepted_pairs_unlabelled"])
        self.assertEqual(["Ordnance Road"], row["accepted_targets_unlabelled"])
        self.assertEqual(25, result["unlabelled_candidate_unique_m"])

    def test_never_substitutes_filtered_samples_or_other_sources_and_modes(self):
        rows = [self.row(40, 1000), self.row(60, 3000)]
        files = [self.export("filtered-only.json"),
                 self.export("timeline.json", rows, source="timeline_import"),
                 self.export("walk.json", rows, mode="walking"),
                 self.export("unconfirmed.json", rows, confirmation="suggested"),
                 self.export("incomplete.json", rows, complete=False)]
        result = audit(files, self.network, ())
        self.assertEqual({"missing_original_samples": 1, "other_source": 1,
                          "other_or_unconfirmed_mode": 2, "unsupported_or_incomplete": 1},
                         result["exports"])
        self.assertEqual(0, result["unique_native_driving_captures"])

    def test_network_provider_and_long_gap_do_not_create_new_adjacency(self):
        rows = [self.row(40, 1000), self.row(50, 2000, "network"), self.row(60, 3000),
                self.row(70, 15_000)]
        result = audit([self.export("gaps.json", rows)], self.network, ())
        row, = result["native_driving_captures"]
        self.assertEqual({"invalid_fix": 2, "time_gap": 1}, row["holds"])
        self.assertEqual(0, result["unlabelled_candidate_unique_m"])

    def test_distinct_original_revisions_must_be_selected_explicitly(self):
        files = [self.export("first.json", [self.row(40, 1000), self.row(60, 3000)]),
                 self.export("different.json", [self.row(40, 1000), self.row(70, 3000)])]
        with self.assertRaisesRegex(ValueError, "select one revision"):
            audit(files, self.network, ())

    def test_boundary_straddling_pair_is_withheld(self):
        rows = [self.row(-2, 1000), self.row(15, 3000)]
        result = audit([self.export("boundary.json", rows)], self.network, ())
        row, = result["native_driving_captures"]
        self.assertEqual(1, row["pairs_touching_extract"])
        self.assertEqual({"catalogue_boundary": 1}, row["holds"])
        self.assertEqual(0, result["unlabelled_candidate_paths"])


if __name__ == "__main__":
    unittest.main()
