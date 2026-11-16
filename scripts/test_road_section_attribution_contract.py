"""Stage 1.2A contract checks. Owner exports are optional, private, and read-only."""

import copy
import hashlib
import json
import os
from pathlib import Path
import unittest

from jsonschema import Draft202012Validator


SCHEMA = Path(__file__).resolve().parents[1] / (
    "docs/journey-intelligence/road-section-attribution-v1.schema.json"
)


class RoadSectionContractTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        schema = json.loads(SCHEMA.read_text())
        Draft202012Validator.check_schema(schema)
        cls.validator = Draft202012Validator(schema)

    def test_synthetic_shared_span_keeps_unique_contributors_and_separate_confidence(self):
        # Supplied by a hypothetical authoritative source. These are not real road IDs.
        longer = {
            "schema_version": 1,
            "journey_id": "synthetic-long",
            "status": "unresolved",
            "reason": "low_confidence",
            "section": {
                "source": "synthetic-graph",
                "dataset_version": "snapshot-1",
                "section_id": "edge-A",
                "centreline_ref": "centreline-A",
                "start_mm": 0,
                "end_mm": 263000,
                "direction": "forward",
            },
            "evidence": {"matcher": "synthetic", "match_sha256": "a" * 64, "route_confidence": 0},
        }
        shorter = copy.deepcopy(longer)
        shorter["journey_id"] = "synthetic-short"
        shorter["section"]["start_mm"] = 114000
        shorter["reason"] = "sparse_observations"
        shorter["evidence"].update(match_sha256="b" * 64, route_confidence=0.5642326653)

        def section_key(record):
            section = record["section"]
            return section["source"], section["dataset_version"], section["section_id"]

        # Identical offsets on a parallel road, a junction branch or a newer graph
        # are not the same section. A repeated record is still one contributor.
        parallel = copy.deepcopy(shorter)
        parallel["journey_id"] = "synthetic-parallel"
        parallel["section"].update(section_id="edge-B", centreline_ref="centreline-B")
        junction = copy.deepcopy(shorter)
        junction["journey_id"] = "synthetic-junction"
        junction["section"].update(section_id="edge-C", centreline_ref="centreline-C")
        newer = copy.deepcopy(shorter)
        newer["journey_id"] = "synthetic-newer"
        newer["section"]["dataset_version"] = "snapshot-2"
        records = [longer, shorter, shorter, parallel, junction, newer]
        shared_start, shared_end = 114000, 263000
        contributors = {}
        for record in records:
            self.validator.validate(record)
            section = record["section"]
            if (section_key(record) == section_key(shorter)
                    and section["start_mm"] <= shared_start and section["end_mm"] >= shared_end):
                contributors[record["journey_id"]] = record["evidence"]["route_confidence"]
        self.assertEqual({"synthetic-long": 0, "synthetic-short": 0.5642326653}, contributors)
        self.assertLess(longer["section"]["start_mm"], shared_start)

    def test_geometry_only_and_unversioned_candidates_cannot_claim_verification(self):
        record = {
            "schema_version": 1, "journey_id": "synthetic",
            "status": "unavailable", "reason": "missing_identity",
            "evidence": {"matcher": "synthetic", "match_sha256": "a" * 64, "route_confidence": 0.9},
        }
        self.validator.validate(record)
        record.update(status="verified")
        del record["reason"]
        record["evidence"].update(
            observations_sha256="b" * 64, from_observation_index=0, to_observation_index=1
        )
        self.assertFalse(self.validator.is_valid(record))
        record["section"] = {
            "source": "synthetic-graph", "section_id": "edge-A", "centreline_ref": "centreline-A",
            "start_mm": 0, "end_mm": 5000, "direction": "reverse",
        }
        self.assertFalse(self.validator.is_valid(record))
        record["section"]["dataset_version"] = "snapshot-1"
        self.validator.validate(record)  # Structurally valid, subject to the evidence policy.
        record["evidence"]["route_confidence"] = 0
        self.assertFalse(self.validator.is_valid(record))

    @unittest.skipUnless(os.getenv("ROADPRINTS_ORDNANCE_FIXTURE_DIR"), "Private owner exports unavailable")
    def test_build_288_ordnance_road_exports(self):
        directory = Path(os.environ["ROADPRINTS_ORDNANCE_FIXTURE_DIR"])
        cases = [
            ("roadprints-journey-20261115-142731.json", "timeline-a4d97b3c7f6fb11b45cf01f3", 2, 4, 263, 0),
            ("roadprints-journey-20261115-142604.json", "timeline-df72f4d23a71e0ce14b56cf1", 3, 5, 149, 0.5642326653),
        ]
        geometries = []
        for name, journey_id, route_points, saved_points, distance, confidence in cases:
            path = directory / name
            source_bytes = path.read_bytes()
            export = json.loads(source_bytes)
            journey = export["journey"]
            result = journey["processing_result"]
            self.assertEqual(288, export["app_version_code"])
            self.assertEqual(journey_id, journey["journey_id"])
            self.assertEqual("timeline_import", journey["source"]["type"])
            self.assertEqual(("driving", "confirmed"), (journey["mode"], journey["transport_confirmation"]))
            self.assertEqual("ok", result["status"])
            self.assertEqual(route_points, journey["capture_quality"]["source_route_points"])
            self.assertEqual(saved_points, len(journey["route_geometry"]["coordinates"]))
            times = journey["timeline_match_evidence"]["point_times_ms"]
            self.assertEqual(saved_points, len(times))
            self.assertEqual(180000, max(end - start for start, end in zip(times, times[1:])))
            feature, = (f for f in result["road_geojson"]["features"]
                        if f["properties"]["name"] == "Ordnance Road")
            self.assertEqual(distance, feature["properties"]["distance_m"])
            overview, = result["geojson"]["features"]
            self.assertEqual(confidence, overview["properties"]["confidence"])
            self.assertEqual({"road_ref", "name", "distance_m", "chunk_index"}, set(feature["properties"]))
            self.assertNotIn("road_section_attribution", result)
            self.assertNotIn("data_version", result)

            # Diagnostic view of legacy evidence only. No identity or coverage is assigned.
            saved_result_bytes = json.dumps(result, sort_keys=True, separators=(",", ":")).encode()
            self.validator.validate({
                "schema_version": 1,
                "journey_id": journey_id,
                "status": "unavailable",
                "reason": "missing_identity",
                "evidence": {
                    "matcher": "roadprints:/match",
                    "match_sha256": hashlib.sha256(saved_result_bytes).hexdigest(),
                    "route_confidence": confidence,
                },
            })
            geometries.append(feature["geometry"]["coordinates"])
            self.assertEqual(source_bytes, path.read_bytes())

        longer, shorter = geometries
        self.assertEqual((17, 9), (len(longer), len(shorter)))
        self.assertEqual(shorter, longer[8:])  # Exact ordered suffix, with no proximity tolerance.


if __name__ == "__main__":
    unittest.main()
