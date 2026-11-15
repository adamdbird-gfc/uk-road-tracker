"""Offline acceptance checks for all-road coverage."""

import json
import os
from pathlib import Path
import unittest

from scripts.road_coverage.extract import driving_direction
from scripts.road_coverage.ledger import Contribution, Interval, Ledger, assess
from scripts.road_coverage.network import Edge, Fix, Network, POLICY, Span, point_at
from scripts.road_coverage.replay import EXTRACT, build_comparison

MILE_MM = 1_609_344


def fixes(*points, accuracy=2, gap=2000):
    return [Fix(point, i * gap, accuracy) for i, point in enumerate(points)]


def qualified(network, route):
    return assess(network, route, confirmed_driving=True, reference_in_period=True)


def traverse(network, start_mm, end_mm):
    distances = list(range(start_mm, end_mm, 20_000)) + [end_mm]
    if len(distances) > 2 and distances[-1] - distances[-2] < 3_000:
        del distances[-2]
    return qualified(network, fixes(*((mm / 1000, 0) for mm in distances)))


class NineMileAcceptance(unittest.TestCase):
    def setUp(self):
        self.network = Network("test-v1", [Edge("road", 1, 2, (0, 0), (9 * MILE_MM / 1000, 0))])
        self.ledger = Ledger(self.network)

    def test_entire_road_and_repeated_trips(self):
        drive = traverse(self.network, 0, 9 * MILE_MM)
        self.ledger.replace("first", drive)
        initial = list(self.ledger.strokes())
        self.assertEqual(1, len(initial))
        self.ledger.replace("again", drive)
        self.ledger.replace("third", drive)
        self.assertEqual(initial, list(self.ledger.strokes()))
        full = Interval(0, 9 * MILE_MM, "covered")
        self.assertEqual([full], self.ledger.states("road"))
        self.assertEqual(["again", "first", "third"], self.ledger.contributors("road", full))
        self.assertEqual(0, self.ledger.replace("third", drive))
        self.ledger.replace("third", None)
        self.assertEqual(["again", "first"], self.ledger.contributors("road", full))

    def test_partial_road_keeps_gap_then_only_fills_new_ground(self):
        for journey, start, end in (("first", 0, 1), ("last", 8, 9), ("last-again", 8, 9)):
            self.ledger.replace(journey, traverse(self.network, start * MILE_MM, end * MILE_MM))
        self.assertEqual([
            Interval(0, MILE_MM, "covered"),
            Interval(MILE_MM, 8 * MILE_MM, "uncovered"),
            Interval(8 * MILE_MM, 9 * MILE_MM, "covered"),
        ], self.ledger.states("road"))
        self.assertEqual(2, len(list(self.ledger.strokes())))
        self.assertEqual(2 * MILE_MM, sum(span.end_mm - span.start_mm for span in self.ledger.covered["road"]))
        # The next journey overlaps the existing first mile by half a mile.
        self.ledger.replace("fill", traverse(self.network, MILE_MM // 2, 2 * MILE_MM))
        self.assertEqual([
            Interval(0, 2 * MILE_MM, "covered"),
            Interval(2 * MILE_MM, 8 * MILE_MM, "uncovered"),
            Interval(8 * MILE_MM, 9 * MILE_MM, "covered"),
        ], self.ledger.states("road"))
        self.assertEqual(["first"], self.ledger.contributors("road", Interval(0, 1000, "covered")))
        self.assertEqual(["fill", "first"], self.ledger.contributors("road", Interval(MILE_MM // 2, MILE_MM, "covered")))
        self.assertEqual(["fill"], self.ledger.contributors("road", Interval(MILE_MM // 2, MILE_MM, "covered"), {"fill"}))
        self.assertEqual([
            Interval(0, MILE_MM // 2, "uncovered"),
            Interval(MILE_MM // 2, 2 * MILE_MM, "covered"),
            Interval(2 * MILE_MM, 9 * MILE_MM, "uncovered"),
        ], self.ledger.states("road", {"fill"}))


class AttributionAcceptance(unittest.TestCase):
    def test_parallel_carriageways_are_ambiguous_and_stay_uncredited(self):
        network = Network("v1", [
            Edge("eastbound", 1, 2, (0, 0), (60, 0), 1, "Same name"),
            Edge("westbound", 3, 4, (0, 10), (60, 10), -1, "Same name"),
        ])
        result = qualified(network, fixes((10, 5), (50, 5)))
        ledger = Ledger(network)
        ledger.replace("uncertain", result)
        self.assertEqual((), result.covered)
        self.assertEqual({"eastbound", "westbound"}, {s.edge for s in result.unresolved})
        self.assertEqual(["uncovered", "unresolved", "uncovered"],
                         [item.state for item in ledger.states("eastbound")])
        self.assertEqual([], list(ledger.strokes()))
        self.assertEqual(2, len(list(ledger.strokes(show_unresolved=True))))

    def test_intersection_continues_through_shared_node_without_covering_cross_street(self):
        network = Network("v1", [
            Edge("left", 1, 2, (0, 0), (30, 0), way=1),
            Edge("right", 2, 3, (30, 0), (60, 0), way=1),
            Edge("turn", 2, 4, (30, 0), (30, 50), way=2),
        ])
        drive = qualified(network, fixes((0, 0), (60, 0)))
        self.assertEqual({"left", "right"}, {s.edge for s in drive.covered})
        ledger = Ledger(network)
        ledger.replace("one", drive)
        self.assertEqual(2, len(list(ledger.strokes())))  # Do not stitch through a junction.
        self.assertEqual([Interval(0, 50_000, "uncovered")], ledger.states("turn"))
        self.assertEqual(["one"], ledger.contributors("right", Interval(0, 30_000, "covered")))

    def test_degree_two_joins_stitch_even_with_different_contributors(self):
        network = Network("v1", [
            Edge("left", 1, 2, (0, 0), (30, 0), way=1),
            Edge("right", 2, 3, (30, 0), (60, 0), way=1),
        ])
        ledger = Ledger(network)
        ledger.replace("first", qualified(network, fixes((0, 0), (30, 0))))
        ledger.replace("second", qualified(network, fixes((30, 0), (60, 0))))
        only, = ledger.strokes()
        self.assertEqual([("left", 0), ("right", 0)], only["sections"])
        self.assertEqual(3, len(only["coordinates"]))
        self.assertEqual([["first"], ["second"]], [f["properties"]["journeys"] for f in ledger.features()])

    def test_disconnected_edges_cannot_bridge_even_with_coincident_endpoints(self):
        network = Network("v1", [
            Edge("left", 1, 2, (0, 0), (30, 0)),
            Edge("right", 4, 3, (30, 0), (60, 0)),
        ])
        result = qualified(network, fixes((10, 0), (50, 0)))
        self.assertEqual((), result.covered)
        self.assertEqual(2, len(result.unresolved))

    def test_oneway_unnamed_road_and_unreliable_evidence(self):
        network = Network("v1", [Edge("unnamed", 1, 2, (0, 0), (60, 0), 1)])
        good = fixes((10, 0), (50, 0))
        self.assertEqual((Span("unnamed", 10000, 50000),), qualified(network, good).covered)
        for route, confirmed, in_period in (
            (good[::-1], True, True),  # non-monotonic time
            (fixes((50, 0), (10, 0)), True, True),  # wrong direction
            (fixes((10, 0), (50, 0), gap=180_000), True, True),
            (fixes((10, 0), (50, 0), gap=10), True, True),
            (fixes((10, 0), (50, 0), accuracy=None), True, True),
            (fixes((10, 10), (50, 10)), True, True),  # outside reported accuracy
            (good, False, True),
            (good, True, False),
        ):
            with self.subTest(route=route, confirmed=confirmed, in_period=in_period):
                result = assess(network, route, confirmed_driving=confirmed, reference_in_period=in_period)
                self.assertEqual((), result.covered)
                self.assertEqual((Span("unnamed", 10000, 50000),), result.unresolved)

    def test_dataset_policy_and_invalid_intervals_fail_without_mutating_ledger(self):
        edge = Edge("road", 1, 2, (0, 0), (60, 0))
        active = Network("v2", [edge])
        ledger = Ledger(active)
        old = qualified(Network("v1", [edge]), fixes((10, 0), (50, 0)))
        fresh = qualified(active, fixes((0, 0), (60, 0)))
        ledger.replace("trip", fresh)
        for invalid in (
            old,
            Contribution("v2", "old-policy", fresh.covered, (), 0),
            Contribution("v2", POLICY, (Span("road", 0, 60001),), (), 0),
        ):
            with self.assertRaises(ValueError):
                ledger.replace("trip", invalid)
        self.assertEqual([Interval(0, 60_000, "covered")], ledger.states("road"))
        ledger.replace("trip", None)
        self.assertEqual([Interval(0, 60_000, "uncovered")], ledger.states("road"))

    def test_covered_precedence_and_incremental_replacement_preserve_other_edges(self):
        network = Network("v1", [
            Edge("one", 1, 2, (0, 0), (60, 0)),
            Edge("two", 3, 4, (0, 100), (60, 100)),
        ])
        ledger = Ledger(network)
        ledger.replace("other", qualified(network, fixes((0, 100), (60, 100))))
        other = ledger.covered["two"]
        ledger.replace("held", qualified(network, fixes((0, 0), (60, 0), accuracy=None)))
        ledger.replace("covered", qualified(network, fixes((0, 0), (30, 0))))
        self.assertEqual([Interval(0, 30_000, "covered"), Interval(30_000, 60_000, "unresolved")],
                         ledger.states("one"))
        self.assertEqual(1, ledger.replace("covered", qualified(network, fixes((30, 0), (60, 0)))))
        self.assertEqual([Interval(0, 30_000, "unresolved"), Interval(30_000, 60_000, "covered")],
                         ledger.states("one"))
        self.assertIs(other, ledger.covered["two"])

    def test_private_parallel_road_blocks_false_public_alignment(self):
        network = Network("v1", [
            Edge("public", 1, 2, (0, 0), (60, 0)),
            Edge("private", 3, 4, (0, 7), (60, 7), eligible=False),
        ])
        result = qualified(network, fixes((5, 7), (55, 7)))
        self.assertFalse(result.covered)
        self.assertEqual({"private", "public"}, {span.edge for span in result.unresolved})

    def test_extract_boundary_holds_evidence_even_without_candidate(self):
        network = Network("v1", [Edge("edge", 1, 2, (0, 20), (100, 20))],
                          bounds=((0, 0), (100, 100)))
        ledger = Ledger(network)
        result = qualified(network, fixes((5, 20), (50, 20)))
        self.assertEqual((), result.covered)
        ledger.replace("trip", result)
        self.assertEqual({"trip": 1}, ledger.held_pairs)
        ledger.replace("trip", None)
        self.assertEqual({}, ledger.held_pairs)

    def test_reference_access_direction_policy(self):
        self.assertIsNone(driving_direction({"highway": "service", "service": "driveway"}))
        self.assertIsNone(driving_direction({"highway": "residential", "motorcar": "private"}))
        self.assertIsNone(driving_direction({"highway": "service", "access:conditional": "no @ (Mo-Fr)"}))
        self.assertEqual(-1, driving_direction({"highway": "residential", "oneway": "-1"}))
        self.assertEqual(1, driving_direction({"highway": "secondary", "junction": "roundabout"}))


class GravesendAcceptance(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.network = Network.load(EXTRACT)

    def test_labelled_catalogue_pins_ids_and_handles_real_network_with_synthetic_fixes(self):
        cases = [
            ("osm:4973303:1409703727:613239608", "Ordnance Road", True),
            ("osm:4973303:32951615:4066655297", "Ordnance Road", False),
            ("osm:4973490:32953776:32857215", "Albion Terrace", True),
            ("osm:4973300:32951591:12476340302", "Milton Place", True),
        ]
        for edge_id, label, expected in cases:
            with self.subTest(edge=edge_id):
                edge = self.network.edges[edge_id]
                self.assertEqual(label, edge.label)
                first, second = (point_at(edge.a, edge.b, fraction) for fraction in (0.2, 0.8))
                result = qualified(self.network, fixes(first, second))
                self.assertEqual(expected, bool(result.covered))
                if expected:
                    only, = result.covered
                    self.assertEqual(edge.id, only.edge)
                else:
                    self.assertGreaterEqual(len(result.unresolved), 2)
        result = qualified(self.network, fixes(second, first))
        self.assertFalse(result.covered)  # Reverse direction on the Milton Place one-way.

    @unittest.skipUnless(os.getenv("ROADPRINTS_ORDNANCE_FIXTURE_DIR"), "Private owner exports unavailable")
    def test_owner_export_replay_preserves_evidence_and_credits_no_metres(self):
        root = Path(os.environ["ROADPRINTS_ORDNANCE_FIXTURE_DIR"])
        paths = [root / name for name in (
            "roadprints-journey-20261115-142604.json", "roadprints-journey-20261115-142731.json",
        )]
        originals = [path.read_bytes() for path in paths]
        metrics, comparison = build_comparison(self.network, [json.loads(data) for data in originals])
        self.assertEqual(8, metrics["original"]["exact_duplicate_legs"])
        self.assertGreater(metrics["diagnostic_only"]["shared_metres"], 50)
        self.assertEqual({"metres": 0, "paths": 0}, metrics["coverage"])
        self.assertEqual({"original", "unresolved"}, {
            feature["properties"]["state"] for feature in comparison["features"]
        })
        self.assertEqual(originals, [path.read_bytes() for path in paths])


if __name__ == "__main__":
    unittest.main()
