"""Synthetic contract tests only: no personal journey exports in the repository."""
from copy import deepcopy
import math
import unittest
from shapely.geometry import LineString
from .coverage import Section, TrialReference
from .journey_replay import recorded_fixes, projector, replay


class JourneyReplayTests(unittest.TestCase):
    def setUp(self):
        self.meta = {'projection_origin': [0, 0], 'bounds': [-.01, -.01, .01, .01]}
        self.ref = TrialReference('contract-test', [Section('path', LineString([(0, 0), (200, 0)]), ('NCN:1',))])
        self.value = {'format': 'roadprints_journey_diagnostics', 'schema_version': 1, 'export_complete': True,
                      'journey': {'mode': 'walking', 'raw_capture_samples': [[x, 0, 3, 1700000000000 + i*2000, 2] for i, x in enumerate([.0001,.00015,.0002,.00025,.0003])],
                      'processing_result': {'geojson': {'features': [{'geometry': {'type': 'LineString', 'coordinates': [[0,0],[.001,0]]}}]}}}}

    def test_android_order_preserves_accuracy_and_epoch_time(self):
        fixes, meta = recorded_fixes(self.value['journey'], projector(self.meta))
        self.assertEqual(3, fixes[0].accuracy)
        self.assertEqual(1700000000, fixes[0].seconds)
        self.assertAlmostEqual(11.1195, fixes[0].x, places=3)
        self.assertEqual('raw_capture_samples', meta['sample_source'])

    def test_supported_candidates_never_become_verified_credit(self):
        result = replay(self.value, self.ref, self.meta)
        self.assertGreater(result['diagnostic_candidate_coverage']['on_foot_m'], 20)
        self.assertEqual(0, result['verified_collection_credit_m'])
        self.assertEqual('pending_evidence', result['status'])

    def test_sparse_import_does_not_get_fabricated_samples_from_matched_route(self):
        del self.value['journey']['raw_capture_samples']
        self.value['journey']['route_geometry'] = {'type':'LineString', 'coordinates':[[0,0],[.001,0]]}
        result = replay(self.value, self.ref, self.meta)
        self.assertGreater(result['matched_geometry_within_buffer_m']['3'], 100)
        self.assertEqual(0, result['diagnostic_candidate_coverage']['combined_m'])
        self.assertIn('missing_per_point_time_and_accuracy', result['flags'])

    def test_driving_never_awards_credit(self):
        result = replay(self.value, self.ref, self.meta, 'driving')
        self.assertEqual('ineligible', result['status'])
        self.assertEqual(0, result['diagnostic_candidate_coverage']['combined_m'])

    def test_mode_variant_does_not_retime_or_mutate_original(self):
        original = deepcopy(self.value)
        result = replay(self.value, self.ref, self.meta, 'cycling')
        self.assertTrue(result['synthetic_mode_override'])
        self.assertEqual('walking', result['declared_mode'])
        self.assertEqual(original, self.value)

    def test_invalid_sample_is_a_break_not_removed(self):
        rows = self.value['journey']['raw_capture_samples']
        rows[2] = [None, 0, 3, rows[2][3], 2]
        fixes, quality = recorded_fixes(self.value['journey'], projector(self.meta))
        self.assertEqual(5, len(fixes)); self.assertTrue(math.isnan(fixes[2].x))
        self.assertEqual(1, quality['invalid_samples'])
        self.assertEqual(0, replay(self.value, self.ref, self.meta)['diagnostic_candidate_coverage']['combined_m'])

    def test_raw_samples_take_priority_over_retained_match_samples(self):
        self.value['journey']['capture_route_samples'] = [[0,0,1,1700000000000]] * 100
        self.assertEqual(5, replay(self.value, self.ref, self.meta)['quality']['sample_count'])

    def test_removed_matched_edges_do_not_reappear_in_overlap(self):
        self.value['journey']['journey_corrections'] = {'removed_matched_segments': [0]}
        result = replay(self.value, self.ref, self.meta)
        self.assertEqual(0, result['matched_geometry_within_buffer_m']['3'])

    def test_incomplete_export_and_unknown_schema_are_rejected(self):
        for key, value in [('export_complete', False), ('schema_version', 2)]:
            changed = deepcopy(self.value); changed[key] = value
            with self.assertRaises(ValueError): replay(changed, self.ref, self.meta)

    def test_reference_bounds_remain_explicit(self):
        result = replay(self.value, self.ref, self.meta)
        self.assertEqual(self.meta['bounds'], result['fixture_bounds'])
        self.assertFalse(result['production_progress_written'])
