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


if __name__ == '__main__':
    unittest.main()
