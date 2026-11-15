"""Acceptance checks for the Stage 1.2C isolated coverage model."""

from collections import Counter
import json
import os
from pathlib import Path
from tempfile import TemporaryDirectory
import unittest

from all_road_coverage import Edge, Fix, Ledger, Network, Span, observed_coverage
from replay_all_road_coverage import REFERENCE, coverage_features, replay_legacy

MILE_MM = 1_609_344
BOUNDS = {"west": 0.35, "south": 51.425, "north": 51.445}


def observations(start, end, y=0, accuracy=3):
    intervals = int(abs(end - start) / 30) + 1
    return [Fix((start + i * (end - start) / intervals, y), i * 2_000, accuracy)
            for i in range(intervals + 1)]


class CoverageTests(unittest.TestCase):
    def setUp(self):
        distance = MILE_MM / 1000
        self.edge = Edge("osm:1:a:b", ("a", "b"), None, (0, 0), (distance * 9, 0))
        self.network = Network("test-1", [self.edge], BOUNDS)
        self.ledger = Ledger(self.network)

    def record(self, name, start, end, revision="1"):
        def project():
            spans, holds = observed_coverage(self.network, observations(start / 1000, end / 1000))
            self.assertEqual({}, holds)
            return spans
        return self.ledger.replace(name, revision, project)

    def test_nine_miles_complete_and_repeated_in_reverse(self):
        self.record("first", 0, 9 * MILE_MM)
        self.record("return", 9 * MILE_MM, 0)
        self.assertEqual(9 * MILE_MM, self.ledger.unique_mm)
        self.assertEqual(((0, 9 * MILE_MM),), self.ledger.intervals(self.edge.id))
        paths = list(coverage_features(self.ledger, "covered"))
        self.assertEqual(1, len(paths))
        self.assertIsNone(paths[0]["properties"]["name"])
        self.assertEqual(["first", "return"], self.ledger.contributors_at(self.edge.id, MILE_MM))

    def test_first_and_last_miles_leave_seven_mile_gap(self):
        self.record("first", 0, MILE_MM)
        self.record("last", 8 * MILE_MM, 9 * MILE_MM)
        self.record("repeat", MILE_MM, 0)
        self.assertEqual(2 * MILE_MM, self.ledger.unique_mm)
        self.assertEqual(((0, MILE_MM), (8 * MILE_MM, 9 * MILE_MM)),
                         self.ledger.intervals(self.edge.id))
        self.assertEqual(2, len(list(coverage_features(self.ledger, "covered"))))
        self.assertEqual([], self.ledger.contributors_at(self.edge.id, 4 * MILE_MM))
        self.record("fill", MILE_MM // 2, 2 * MILE_MM)
        self.assertEqual(3 * MILE_MM, self.ledger.unique_mm)
        self.assertEqual(((0, 2 * MILE_MM), (8 * MILE_MM, 9 * MILE_MM)),
                         self.ledger.intervals(self.edge.id))
        self.assertEqual(["fill", "first", "repeat"],
                         self.ledger.contributors_at(self.edge.id, 3 * MILE_MM // 4))

    def test_filter_recomputes_display_union_and_selection_only(self):
        self.record("first", 0, 2 * MILE_MM)
        self.record("last", MILE_MM, 3 * MILE_MM)
        selected, = coverage_features(self.ledger, "covered", {"last"})
        interval, = selected["properties"]["sections"]
        self.assertEqual((MILE_MM, 3 * MILE_MM), (interval["start_mm"], interval["end_mm"]))
        self.assertEqual(["last"], self.ledger.contributors_at(self.edge.id, 2 * MILE_MM, {"last"}))
        self.assertEqual(3 * MILE_MM, self.ledger.unique_mm)

    def test_incremental_reuse_replace_remove_and_revision(self):
        self.assertTrue(self.record("first", 0, MILE_MM))
        self.assertFalse(self.ledger.replace("first", "1", lambda: self.fail("cache missed")))
        self.record("other", 0, MILE_MM)
        before = self.ledger.aggregate[self.edge.id]
        self.ledger.remove("other")
        self.assertIs(before, self.ledger.aggregate[self.edge.id])
        with self.assertRaises(ValueError):
            self.ledger.replace("first", "bad", lambda: [Span(self.edge.id, 0, -1)])
        self.assertEqual(MILE_MM, self.ledger.unique_mm)
        self.record("first", 8 * MILE_MM, 9 * MILE_MM, "2")
        self.assertEqual(((8 * MILE_MM, 9 * MILE_MM),), self.ledger.intervals(self.edge.id))
        replacement = Ledger(Network("test-2", [self.edge]))
        self.assertNotEqual(self.ledger.cache_version, replacement.cache_version)
        self.assertNotEqual(self.ledger.cache_version, Ledger(self.network, "new").cache_version)
        self.assertEqual(0, replacement.unique_mm)
        self.ledger.remove("first")
        self.assertEqual(0, self.ledger.unique_mm)
        self.assertEqual({}, self.ledger.by_edge)
        self.assertEqual({}, self.ledger.interval_counts)

    def test_nearby_parallel_roads_with_same_name_hold_ambiguity(self):
        a = Edge("a", ("1", "2"), "Same label", (0, 0), (100, 0))
        b = Edge("b", ("3", "4"), "Same label", (0, 9), (100, 9))
        network = Network("parallel", [a, b])
        for lateral in (0, 4, 9):
            spans, holds = observed_coverage(network, observations(10, 90, lateral))
            self.assertEqual([], spans)
            self.assertEqual(3, holds["competing_sections"])
        for lateral, expected in ((0, "a"), (9, "b")):
            spans, holds = observed_coverage(network, observations(10, 90, lateral, accuracy=1))
            self.assertEqual({}, holds)
            self.assertEqual({expected}, {span.edge_id for span in spans})

    def test_intersection_does_not_claim_branch_or_bridge(self):
        a = Edge("osm:1:a:b", ("a", "b"), "Through", (0, 0), (10, 0))
        b = Edge("osm:1:b:c", ("b", "c"), "Through", (10, 0), (20, 0))
        branch = Edge("osm:2:b:d", ("b", "d"), "Branch", (10, 0), (10, 20))
        bridge = Edge("osm:3:e:f", ("e", "f"), "Bridge", (12, -10), (12, 10))
        network = Network("junction", [a, b, branch, bridge], BOUNDS)
        spans, holds = observed_coverage(network, observations(0, 20))
        self.assertEqual({}, holds)
        self.assertEqual([Span(a.id, 0, 10000), Span(b.id, 0, 10000)], spans)
        ledger = Ledger(network)
        ledger.replace("through", "1", lambda: spans)
        self.assertEqual(2, len(list(coverage_features(ledger, "covered"))))
        branch_spans, holds = observed_coverage(network, [Fix((10, 3), 0, 1), Fix((10, 15), 2000, 1)])
        self.assertEqual([], branch_spans)
        self.assertEqual({"competing_sections": 1}, holds)
        disconnected = Network("disconnected", [a, Edge(b.id, ("x", "c"), b.label, b.a, b.b)])
        self.assertEqual(((), "disconnected_sections"), disconnected.align((0, 0), (20, 0)))

    def test_sparse_gps_low_accuracy_and_out_of_scope_held(self):
        edge_id = self.edge.id
        cases = [
            ([Fix((0, 0), 0, 2), Fix((25, 0), 180_000, 2)], (), "time_gap"),
            ([Fix((0, 0), 0, 30), Fix((25, 0), 2_000, 2)], (), "fix_accuracy"),
            ([Fix((0, 0), 0, 2), Fix((100, 0), 2_000, 2)], (), "distance_gap"),
            (observations(0, 25), {edge_id}, "out_of_scope_road"),
            (observations(0, 25, 15), (), "no_alignment"),
            ([], (), "missing_original_fixes"),
        ]
        for fixes, excluded, reason in cases:
            with self.subTest(reason=reason):
                self.assertEqual(([], {reason: 1}), observed_coverage(self.network, fixes, excluded))


class GravesendTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.network, cls.excluded = Network.gravesend(REFERENCE)

    def test_snapshot_is_pinned_and_local_streets_have_distinct_ids(self):
        self.assertEqual("osm-gravesend-v1:97c9a14c999b1bf574003c4fd923b06da65a8dcec0eeaddbb910f1f993f85123",
                         self.network.revision)
        self.assertEqual(6760, len(self.network.edges) - len(self.excluded))
        counts = Counter(edge.label for edge in self.network.edges.values())
        self.assertEqual((16, 16, 21),
                         tuple(counts[name] for name in ("Ordnance Road", "Albion Terrace", "Milton Place")))

    def test_compact_reference_preserves_identity_geometry_and_eligibility(self):
        with TemporaryDirectory() as directory:
            path = Path(directory) / "reference.json.gz"
            path.write_bytes(self.network.compact_gravesend(self.excluded))
            compact, excluded = Network.gravesend(path)
        self.assertEqual(self.network.revision, compact.revision)
        self.assertEqual(self.network.bounds, compact.bounds)
        self.assertEqual(self.network.edges, compact.edges)
        self.assertEqual(self.excluded, excluded)

    def test_pinned_street_geometry_with_controlled_position_noise(self):
        # Synthetic fixes on actual local reference edges. Positive controls,
        # withholds and wrong sections counted separately.
        correct, held, wrong = 0, 0, 0
        streets = {"Ordnance Road", "Albion Terrace", "Milton Place"}
        for edge in self.network.edges.values():
            if edge.label not in streets or not 10 <= edge.length <= 40:
                continue
            for offset in (-2, 0, 2):
                dx = (edge.a[1] - edge.b[1]) * offset / edge.length
                dy = (edge.b[0] - edge.a[0]) * offset / edge.length
                points = [edge.at(t) for t in (0.2, 0.8)]
                fixes = [Fix((x + dx, y + dy), i * 2000, 3)
                         for i, (x, y) in enumerate(points)]
                spans, _ = observed_coverage(self.network, fixes, self.excluded)
                if not spans:
                    held += 1
                elif {span.edge_id for span in spans} == {edge.id}:
                    correct += 1
                else:
                    wrong += 1
        self.assertEqual((57, 0, 0), (correct, held, wrong))

    def test_private_ordnance_exports_remain_unresolved(self):
        root = os.environ.get("ROADPRINTS_ORDNANCE_FIXTURE_DIR")
        if not root:
            self.skipTest("Optional private owner exports")
        paths = [Path(root) / f"roadprints-journey-20261115-{time}.json"
                 for time in ("142604", "142731")]
        original = [path.read_bytes() for path in paths]
        approx, legacy, unique, holds = replay_legacy(
            self.network, self.excluded, paths, "Ordnance Road")
        first, second = (row["geometry"]["coordinates"] for row in legacy)
        self.assertEqual(first, second[8:])
        self.assertEqual(16, len(unique))
        self.assertEqual({"distance_gap": 3}, holds)
        self.assertEqual(144258, approx.unique_mm)
        for raw in original:
            data = json.loads(raw)["journey"]
            # The export contains positions, no raw per-fix accuracy envelope.
            self.assertNotIn("fixes", data)
        self.assertEqual(original, [path.read_bytes() for path in paths])
        self.assertEqual(7, sum(len(rows) == 2 for rows in approx.by_edge.values()))
        shared = "osm:4973303:32041399:32951605"
        self.assertEqual(sorted(row["properties"]["journey_id"] for row in legacy),
                         approx.contributors_at(shared, 5000))
        self.assertEqual([legacy[0]["properties"]["journey_id"]],
                         approx.contributors_at(shared, 5000, {legacy[0]["properties"]["journey_id"]}))


if __name__ == "__main__":
    unittest.main()
