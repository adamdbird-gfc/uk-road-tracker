import unittest
import math
from dataclasses import replace

from shapely.geometry import LineString

from .coverage import Evidence, Fix, Section, TrialLedger, TrialReference, merge_intervals
from .fixtures import fixes_along, load_reference


class CoverageTrialTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.reference, cls.metadata = load_reference()
        cls.road = cls.reference.sections['FID:954:0']
        cls.path = cls.reference.sections['FID:16404:0']

    def ledger(self, mode, fixes, reference=None):
        reference = reference or self.reference
        ledger = TrialLedger(reference)
        evidence = reference.observe(mode, fixes)
        ledger.put('journey', evidence)
        return ledger, evidence

    def test_real_fixture_has_47_route1_features_with_verified_checksum(self):
        self.assertEqual(47, len(self.reference.sections))
        self.assertEqual({('NCN:1',)}, {s.routes for s in self.reference.sections.values()})

    def test_cycle_on_road_and_walk_off_road_are_separate(self):
        ledger = TrialLedger(self.reference)
        ledger.put('ride', self.reference.observe('cycling', fixes_along(self.road.line)))
        ledger.put('walk', self.reference.observe('walking', fixes_along(self.path.line, period=8)))
        result = ledger.summary()
        self.assertAlmostEqual(0.7 * self.road.line.length, result['cycling_m'], delta=.05)
        self.assertAlmostEqual(0.7 * self.path.line.length, result['on_foot_m'], delta=.05)
        self.assertAlmostEqual(result['cycling_m'] + result['on_foot_m'], result['combined_m'], delta=.05)

    def test_repeated_and_reversed_journey_add_no_unique_distance(self):
        ledger, _ = self.ledger('cycling', fixes_along(self.road.line))
        original = ledger.summary()['cycling_m']
        ledger.put('repeat', self.reference.observe('cycling', fixes_along(self.road.line)))
        ledger.put('reverse', self.reference.observe('cycling', fixes_along(self.road.line, .8, .1)))
        self.assertAlmostEqual(original, ledger.summary()['cycling_m'], delta=.05)

    def test_mixed_modes_count_shared_physical_distance_once(self):
        ledger, _ = self.ledger('cycling', fixes_along(self.road.line, .1, .7))
        ledger.put('walk', self.reference.observe('walking', fixes_along(self.road.line, .4, .9)))
        result = ledger.summary()
        self.assertAlmostEqual(.6 * self.road.line.length, result['cycling_m'], delta=.05)
        self.assertAlmostEqual(.5 * self.road.line.length, result['on_foot_m'], delta=.05)
        self.assertAlmostEqual(.8 * self.road.line.length, result['combined_m'], delta=.05)

    def test_driving_bus_unknown_and_ferry_do_not_earn_ncn_credit(self):
        for mode in ['driving', 'bus', 'unknown', 'ferry']:
            with self.subTest(mode=mode):
                ledger, _ = self.ledger(mode, fixes_along(self.road.line))
                self.assertEqual(0, ledger.summary()['combined_m'])

    def test_running_and_walking_share_on_foot_bucket(self):
        ledger, _ = self.ledger('walking', fixes_along(self.path.line))
        ledger.put('run', self.reference.observe('running', fixes_along(self.path.line)))
        self.assertAlmostEqual(.7 * self.path.line.length, ledger.summary()['on_foot_m'], delta=.05)

    def test_bad_accuracy_holds_exact_on_route_trace_uncredited(self):
        ledger, evidence = self.ledger('cycling', fixes_along(self.road.line, accuracy=25))
        self.assertEqual(0, ledger.summary()['combined_m'])
        self.assertIn('uncertain_accuracy', evidence.decisions)

    def test_long_timestamp_gap_is_not_bridged(self):
        fixes = fixes_along(self.road.line)
        middle = len(fixes) // 2
        fixes[middle:] = [replace(f, seconds=f.seconds + 120) for f in fixes[middle:]]
        ledger, evidence = self.ledger('cycling', fixes)
        self.assertIn('unsupported_gap_or_movement', evidence.decisions)
        self.assertLess(ledger.summary()['cycling_m'], .7 * self.road.line.length - 1)

    def test_missing_moving_fixes_do_not_fill_the_gap(self):
        fixes = fixes_along(self.road.line)
        fixes = fixes[:8] + fixes[20:]
        ledger, evidence = self.ledger('cycling', fixes)
        self.assertIn('unsupported_gap_or_movement', evidence.decisions)
        self.assertLess(ledger.summary()['cycling_m'], .7 * self.road.line.length - 50)

    def test_single_crossing_has_no_sustained_route_evidence(self):
        midpoint = self.road.line.interpolate(.5, normalized=True)
        before = self.road.line.interpolate(self.road.line.length * .5 - 5)
        after = self.road.line.interpolate(self.road.line.length * .5 + 5)
        dx, dy = after.x - before.x, after.y - before.y
        length = math.hypot(dx, dy)
        fixes = [Fix(midpoint.x - dy / length * offset, midpoint.y + dx / length * offset, i * 2)
                 for i, offset in enumerate([-40, -20, 0, 20, 40])]
        ledger, _ = self.ledger('walking', fixes)
        self.assertEqual(0, ledger.summary()['combined_m'])

    def test_known_parallel_path_and_ambiguous_midline_are_uncredited(self):
        section = Section('route', LineString([(0, 0), (200, 0)]), ('NCN:1',))
        reference = TrialReference('synthetic-parallel', [section], [LineString([(0, 8), (200, 8)])])
        for y, reason in [(8, 'nonqualifying_path'), (4, 'ambiguous_path')]:
            with self.subTest(y=y):
                fixes = [Fix(x, y, i * 2) for i, x in enumerate(range(20, 181, 8))]
                ledger, evidence = self.ledger('cycling', fixes, reference)
                self.assertEqual(0, ledger.summary()['combined_m'])
                self.assertIn(reason, evidence.decisions)

    def test_overlapping_route_labels_share_one_physical_total(self):
        second = Section('test-overlap', LineString(list(self.road.line.coords)[::-1]), ('TEST:overlap',))
        reference = TrialReference(self.reference.version, [self.road, second])
        ledger, _ = self.ledger('cycling', fixes_along(self.road.line), reference)
        result = ledger.summary()
        self.assertAlmostEqual(.7 * self.road.line.length, result['combined_m'], delta=.05)
        self.assertAlmostEqual(result['combined_m'], result['routes']['NCN:1']['cycling_m'], delta=.05)
        self.assertAlmostEqual(result['combined_m'], result['routes']['TEST:overlap']['cycling_m'], delta=.05)

    def test_activity_reimport_correction_and_deletion_are_rebuildable(self):
        ledger, evidence = self.ledger('cycling', fixes_along(self.road.line))
        ledger.put('journey', evidence)
        ledger.put('journey', self.reference.observe('walking', fixes_along(self.road.line)))
        result = ledger.summary()
        self.assertEqual(0, result['cycling_m'])
        self.assertAlmostEqual(.7 * self.road.line.length, result['on_foot_m'], delta=.05)
        ledger.remove('journey')
        self.assertEqual(0, ledger.summary()['combined_m'])

    def test_partial_overlapping_routes_with_different_vertices_use_one_basis(self):
        first = Section('whole', LineString([(0, 0), (100, 0), (200, 0)]), ('TEST:whole',))
        second = Section('partial', LineString([(150, 0), (50, 0)]), ('TEST:partial',))
        reference = TrialReference('synthetic-partial-overlap', [first, second])
        ledger = TrialLedger(reference)
        ledger.put('accepted', Evidence(reference.version, 'cycling', (('whole', 0, 200),), ()))
        result = ledger.summary()
        self.assertAlmostEqual(200, result['combined_m'])
        self.assertAlmostEqual(200, result['routes']['TEST:whole']['cycling_m'])
        self.assertAlmostEqual(100, result['routes']['TEST:partial']['cycling_m'])

    def test_correcting_activity_to_driving_removes_its_old_ncn_credit(self):
        ledger, _ = self.ledger('cycling', fixes_along(self.road.line))
        ledger.put('journey', self.reference.observe('driving', fixes_along(self.road.line)))
        self.assertEqual(0, ledger.summary()['combined_m'])

    def test_unmatched_fix_interrupts_coverage_instead_of_joining_across_it(self):
        fixes = fixes_along(self.road.line)
        middle = len(fixes) // 2
        fixes[middle] = replace(fixes[middle], x=fixes[middle].x + 1000)
        ledger, _ = self.ledger('cycling', fixes)
        self.assertLess(ledger.summary()['cycling_m'], .7 * self.road.line.length - 5)

    def test_reference_refresh_cannot_silently_reuse_old_evidence(self):
        ledger = TrialLedger(self.reference)
        with self.assertRaisesRegex(ValueError, 'reconciliation'):
            ledger.put('old', Evidence('different-reference', 'cycling', (), ()))
        self.assertEqual(0, ledger.summary()['combined_m'])

    def test_closed_or_nonqualifying_section_earns_nothing(self):
        reference = TrialReference('closed', [replace(self.road, eligible=False)])
        ledger, _ = self.ledger('walking', fixes_along(self.road.line), reference)
        self.assertEqual(0, ledger.summary()['combined_m'])

    def test_nonfinite_fixes_and_short_observation_do_not_credit(self):
        for fixes in [[Fix(float('nan'), 0, i) for i in range(3)], fixes_along(self.road.line)[:2]]:
            ledger, _ = self.ledger('walking', fixes)
            self.assertEqual(0, ledger.summary()['combined_m'])

    def test_interval_union_keeps_disjoint_sections_and_removes_overlap(self):
        self.assertEqual([(0, 30), (40, 50)], merge_intervals([(10, 20), (0, 15), (30, 20), (50, 40)]))


if __name__ == '__main__':
    unittest.main()
