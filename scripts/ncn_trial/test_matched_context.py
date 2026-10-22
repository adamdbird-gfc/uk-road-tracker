from dataclasses import replace
import unittest
from .coverage import TrialLedger
from .matched_context import samples


class IndependentContextTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.reference, cls.source, cls.samples = samples()

    def test_three_independent_member_identities_and_licence(self):
        self.assertEqual({85488624, 554498838, 4973301}, {p['osm_way'] for p, _, _ in self.samples})
        self.assertEqual({p['osm_way'] for p, _, _ in self.samples}, {p['osm_way'] for p in self.source['memberships']})
        self.assertIn('Open Database', self.source['licence'])
        for props, _, _ in self.samples:
            self.assertEqual(13512082, props['ncn_relation'])
            self.assertEqual('1', str(props['route_ref']))
            self.assertEqual('ncn', props['network'])

    def test_independent_geometry_yields_bounded_coverage(self):
        for props, expected, fixes in self.samples:
            with self.subTest(way=props['osm_way']):
                ledger = TrialLedger(self.reference)
                ledger.put('ride', self.reference.observe('cycling', fixes))
                self.assertAlmostEqual(expected, ledger.summary()['cycling_m'], delta=2)

    def test_repeat_and_reimport_do_not_inflate(self):
        for _, expected, fixes in self.samples:
            ledger = TrialLedger(self.reference)
            for key in ('ride', 'ride', 'repeat'):
                ledger.put(key, self.reference.observe('cycling', fixes))
            self.assertAlmostEqual(expected, ledger.summary()['combined_m'], delta=2)

    def test_membership_does_not_rescue_driving_or_poor_accuracy(self):
        for _, _, fixes in self.samples:
            ledger = TrialLedger(self.reference)
            ledger.put('drive', self.reference.observe('driving', fixes))
            ledger.put('poor', self.reference.observe('cycling', [replace(f, accuracy=30) for f in fixes]))
            self.assertEqual(0, ledger.summary()['combined_m'])

    def test_walking_is_separate_from_cycling(self):
        for _, expected, fixes in self.samples:
            ledger = TrialLedger(self.reference)
            ledger.put('walk', self.reference.observe('walking', fixes))
            self.assertAlmostEqual(expected, ledger.summary()['on_foot_m'], delta=2)
            self.assertEqual(0, ledger.summary()['cycling_m'])
