import unittest

from .coverage import TrialLedger
from .fixtures import fixes_along, load_reference

REGIONS = {'ncn-ni-route9': ('Northern Ireland', 9, 'FID:404:0', 'FID:65:0'),
           'ncn-wales-route8': ('Wales', 8, 'FID:27757:0', 'FID:6979:0'),
           'ncn-scotland-route7': ('Scotland', 7, 'FID:8437:0', 'FID:4419:0')}


def observations(section, mode='cycling'):
    covered = min(160.0, section.line.length * .6)
    start = .5 - covered / (2 * section.line.length)
    end = .5 + covered / (2 * section.line.length)
    return covered, fixes_along(section.line, start, end, period=8 if mode == 'walking' else 2)


class RegionalTrialTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.references = {name: load_reference(name) for name in REGIONS}

    def test_hashes_and_route_identity_are_preserved_for_every_region(self):
        for name, (nation, route, _, _) in REGIONS.items():
            with self.subTest(region=name):
                reference, metadata = self.references[name]
                self.assertEqual(nation, metadata['nation'])
                self.assertEqual(metadata['features'], len(reference.sections))
                self.assertEqual({(f'NCN:{route}',)}, {s.routes for s in reference.sections.values()})
                self.assertIn('OpenStreetMap', metadata['attribution'])
        self.assertIn('ODbL', self.references['ncn-ni-route9'][1]['licence'])

    def test_on_road_cycling_and_traffic_free_walking_earn_separate_coverage(self):
        for name, (_, _, road, path) in REGIONS.items():
            with self.subTest(region=name):
                reference, _ = self.references[name]
                ledger = TrialLedger(reference)
                cycle_expected, cycle = observations(reference.sections[road])
                foot_expected, foot = observations(reference.sections[path], 'walking')
                ledger.put('cycle', reference.observe('cycling', cycle))
                ledger.put('foot', reference.observe('walking', foot))
                summary = ledger.summary()
                self.assertAlmostEqual(cycle_expected, summary['cycling_m'], delta=.05)
                self.assertAlmostEqual(foot_expected, summary['on_foot_m'], delta=.05)
                self.assertAlmostEqual(cycle_expected + foot_expected, summary['combined_m'], delta=.05)

    def test_reimport_and_repeat_do_not_inflate_regional_coverage(self):
        for name, (_, _, road, _) in REGIONS.items():
            with self.subTest(region=name):
                reference, _ = self.references[name]
                expected, fixes = observations(reference.sections[road])
                ledger = TrialLedger(reference)
                for key in ['ride', 'ride', 'repeat']:
                    ledger.put(key, reference.observe('cycling', fixes))
                self.assertAlmostEqual(expected, ledger.summary()['combined_m'], delta=.05)

    def test_regional_driving_and_uncertain_accuracy_do_not_earn_collection_credit(self):
        from dataclasses import replace
        for name, (_, _, road, _) in REGIONS.items():
            with self.subTest(region=name):
                reference, _ = self.references[name]
                _, fixes = observations(reference.sections[road])
                ledger = TrialLedger(reference)
                ledger.put('drive', reference.observe('driving', fixes))
                ledger.put('poor', reference.observe('cycling', [replace(f, accuracy=25) for f in fixes]))
                self.assertEqual(0, ledger.summary()['combined_m'])

    def test_removing_one_activity_keeps_other_supported_regional_credit(self):
        for name, (_, _, road, path) in REGIONS.items():
            with self.subTest(region=name):
                reference, _ = self.references[name]
                _, cycle = observations(reference.sections[road])
                expected, foot = observations(reference.sections[path], 'walking')
                ledger = TrialLedger(reference)
                ledger.put('cycle', reference.observe('cycling', cycle))
                ledger.put('foot', reference.observe('walking', foot))
                ledger.remove('cycle')
                self.assertAlmostEqual(expected, ledger.summary()['combined_m'], delta=.05)

    def test_unknown_fixture_cannot_read_outside_trial_sources(self):
        with self.assertRaises(ValueError):
            load_reference('../../arbitrary')
