import copy
import unittest
from build_wetherspoons_map import build


class ReviewedMapTest(unittest.TestCase):
    def setUp(self):
        self.catalogue = {'collection_id': 'wetherspoons', 'scope': 'UK',
                          'catalogue_version': 'test-source', 'source': {'licence': 'ODbL-1.0'},
                          'venues': [{'id': 'pub-a', 'status': 'candidate', 'name': 'Pub A',
                                      'lat': 51, 'lon': 0, 'town': 'Town', 'county': {'code': 'KNT'},
                                      'osm_aliases': ['node/1']}]}
        self.review = {'id': 'pub-a', 'decision': 'active_map', 'source_name': 'Pub A',
                       'osm_aliases': ['node/1'], 'lat': 51, 'lon': 0,
                       'reviewed_at': '2026-10-11', 'evidence_url': 'https://example.org/pub-a'}
        self.ledger = {'format': 1, 'collection_id': 'wetherspoons', 'venues': [self.review]}

    def result(self, as_of='2026-10-11'):
        return build(self.catalogue, self.ledger, as_of)

    def test_unreviewed_candidate_never_activates(self):
        self.ledger['venues'] = []
        self.assertEqual([], self.result()['venues'])

    def test_held_position_is_hidden_without_claiming_pub_closure(self):
        self.review['decision'] = 'hold_map'
        r = self.result()
        self.assertEqual([], r['venues'])
        self.assertEqual(0, r['review_summary']['excluded_current'])
        self.assertEqual(['pub-a'], r['review_summary']['needs_rereview'])

    def test_approval_is_map_only_and_not_a_completion_target(self):
        r = self.result()
        self.assertEqual(1, len(r['venues']))
        self.assertFalse(r['visit_enabled'])
        self.assertFalse(r['coverage_complete'])
        self.assertIsNone(r['completion_target'])

    def test_exclusion_survives_stale_source_refresh_and_missing_source(self):
        self.review['decision'] = 'exclude_current'
        self.assertEqual([], self.result()['venues'])
        self.catalogue['venues'] = []
        self.assertEqual(1, self.result()['review_summary']['excluded_current'])

    def test_duplicate_or_future_reviews_fail(self):
        self.ledger['venues'].append(copy.deepcopy(self.review))
        with self.assertRaises(ValueError):
            self.result()
        self.ledger['venues'].pop()
        with self.assertRaises(ValueError):
            self.result('2026-10-10')

    def test_stale_review_is_withheld_until_rereview(self):
        self.assertEqual([], self.result('2027-02-01')['venues'])

    def test_renamed_moved_or_replaced_venue_requires_new_review(self):
        for key, value in [('name', 'New name'), ('lat', 52), ('osm_aliases', ['node/2'])]:
            with self.subTest(key=key):
                old = self.catalogue['venues'][0][key]
                self.catalogue['venues'][0][key] = value
                self.assertEqual([], self.result()['venues'])
                self.catalogue['venues'][0][key] = old

    def supplement(self):
        return {'format': 1, 'collection_id': 'wetherspoons', 'approval_scope': 'map_only_supplement',
                'venues': [{'id': 'spoons-curated-new-pub', 'decision': 'active_map',
                            'name': 'New Pub', 'town': 'Town', 'county': {'code': 'KNT'},
                            'lat': 51.1, 'lon': 0.1, 'osm_aliases': [], 'parent_osm_id': 'way/20',
                            'reviewed_at': '2026-10-11', 'operating_evidence_url': 'https://example.org/new-pub',
                            'location_evidence_url': 'https://example.org/map',
                            'location_precision': 'approximate_building', 'location_label': 'Approximate building location',
                            'attribution': 'Location: Roadprints review', 'confirmation_required': True, 'visit_enabled': False}]}

    def test_curated_building_point_uses_distinct_identity_and_disables_visits(self):
        r = build(self.catalogue, self.ledger, '2026-10-11', self.supplement())
        self.assertEqual(2, len(r['venues']))
        row = next(v for v in r['venues'] if v['id'].startswith('spoons-curated-'))
        self.assertEqual([], row['osm_aliases'])
        self.assertFalse(row['visit_enabled'])
        self.assertTrue(row['confirmation_required'])
        self.assertEqual('Approximate building location', row['location_label'])
        self.assertIsNone(r['completion_target'])

    def test_raw_research_cannot_be_used_as_supplement(self):
        s = self.supplement(); s['approval_scope'] = 'research_checkpoint_not_app_asset'
        with self.assertRaises(ValueError):
            build(self.catalogue, self.ledger, '2026-10-11', s)

    def test_parent_alias_and_existing_osm_alias_cannot_be_assigned_to_new_pub(self):
        for alias in ['way/20', 'node/1']:
            s = self.supplement(); s['venues'][0]['osm_aliases'] = [alias]
            with self.assertRaises(ValueError):
                build(self.catalogue, self.ledger, '2026-10-11', s)

    def test_unapproved_or_park_only_supplement_never_activates(self):
        for key, value in [('decision', 'hold_map'), ('visit_enabled', True),
                           ('confirmation_required', False), ('location_precision', 'parent_park_point_only')]:
            s = self.supplement(); s['venues'][0][key] = value
            with self.assertRaises(ValueError):
                build(self.catalogue, self.ledger, '2026-10-11', s)

    def test_duplicate_supplement_ids_are_rejected(self):
        s = self.supplement(); s['venues'].append(copy.deepcopy(s['venues'][0]))
        with self.assertRaises(ValueError):
            build(self.catalogue, self.ledger, '2026-10-11', s)

    def test_stale_supplement_is_withheld_and_future_review_rejected(self):
        r = build(self.catalogue, self.ledger, '2027-02-01', self.supplement())
        self.assertEqual([], r['venues'])
        self.assertIn('spoons-curated-new-pub', r['review_summary']['needs_rereview'])
        with self.assertRaises(ValueError):
            build(self.catalogue, self.ledger, '2026-10-10', self.supplement())

    def test_sourced_town_correction_preserves_identity_and_position(self):
        self.catalogue['venues'][0]['town']=''
        self.review.update(town='Rochester',town_evidence_url='https://example.org/address',town_reviewed_at='2026-10-11')
        row=self.result()['venues'][0]
        self.assertEqual('Rochester',row['town'])
        self.assertEqual('pub-a',row['id'])
        self.assertEqual(51,row['lat'])
        self.assertFalse(row['visit_enabled'])

    def test_town_override_requires_a_dated_source(self):
        self.review['town']='Rochester'
        with self.assertRaises(ValueError):self.result()


if __name__ == '__main__':
    unittest.main()
