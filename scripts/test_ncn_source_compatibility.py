"""Positive, negative and pinned research controls for the offline diagnostic."""
import copy
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from scripts.ncn_source_compatibility import compare, normalized_hash, run
from scripts.prepare_ncn_compatibility_controls import prepare, PINS

ROOT = Path(__file__).resolve().parents[1]
FIXTURE = ROOT/'tests/fixtures/ncn-source-compatibility/controls.json'


def record():
    feature = {'provider': 'osm', 'feature_id': '1', 'version': None,
               'node_ids': [10, 20, 30], 'coordinates': [[0, 51], [.001, 51], [.002, 51]],
               'route_memberships': ['NCN:1'], 'modes': ['on_foot'],
               'tags': {'foot': 'yes'}, 'provenance': {'snapshot_id': None}}
    return {'key': 'synthetic', 'route': 'NCN:1', 'mode': 'on_foot',
            'source': feature, 'reference': copy.deepcopy(feature)}


def histories():
    return [{'elements': [
        {'type': 'way', 'id': 1, 'version': 1, 'nodes': [10, 20, 30]},
        {'type': 'way', 'id': 1, 'version': 2, 'nodes': [10, 20, 30]},
        *[{'type': 'node', 'id': n, 'version': 1, 'lon': x, 'lat': 51, 'timestamp': '2026-01-01T00:00:00Z'} for n, x in [(10, 0), (20, .001), (30, .002)]],
        {'type': 'node', 'id': 20, 'version': 2, 'lon': .001, 'lat': 51.00001, 'timestamp': '2026-02-01T00:00:00Z'}]}]


class SyntheticTests(unittest.TestCase):
    def test_exact(self):
        r = compare(record()); self.assertEqual(r['classification'], 'exact_match')
        self.assertTrue(r['geometry']['exact_equal']); self.assertFalse(r['new_completion_credit'])

    def test_reversed(self):
        r = record(); r['reference']['coordinates'].reverse(); r['reference']['node_ids'].reverse()
        out = compare(r); self.assertEqual(out['classification'], 'exact_reversed_geometry')
        self.assertEqual(out['geometry']['node_chain_comparison'], 'reversed')

    def test_small_displacement_not_automatic_update(self):
        r = record(); r['reference']['coordinates'][1][1] += .00001
        out = compare(r); self.assertEqual(out['classification'], 'possible_correspondence_requiring_review')
        self.assertEqual(out['history']['status'], 'unavailable')

    def test_shared_node_movement(self):
        r = record(); r['reference']['coordinates'][1][1] += .00001; r['histories'] = histories()
        out = compare(r); self.assertEqual(out['classification'], 'same_identified_feature_with_documented_geometry_update')
        self.assertEqual(out['history']['changed_shared_nodes'], [20])
        self.assertIsNone(out['source']['version'])

    def test_changed_node_membership(self):
        r = record(); r['reference']['node_ids'][1] = 99
        self.assertEqual(compare(r)['classification'], 'possible_correspondence_requiring_review')

    def test_changed_history_membership_not_shared_node_update(self):
        r = record(); r['histories'] = histories(); r['histories'][0]['elements'][1]['nodes'][1] = 99
        r['reference']['coordinates'][1][1] += .00001
        out = compare(r); self.assertNotEqual(out['classification'], 'same_identified_feature_with_documented_geometry_update')
        self.assertEqual(out['history']['status'], 'changed_or_unproved_way_membership')

    def test_parallel_different_identity(self):
        r = record(); r['reference']['feature_id'] = '2'
        for p in r['reference']['coordinates']: p[1] += .00001
        self.assertEqual(compare(r)['classification'], 'incompatible_or_unsupported_correspondence')

    def test_identical_shapes_are_not_identity(self):
        r = record(); r['reference']['feature_id'] = '2'
        out = compare(r); self.assertTrue(out['geometry']['exact_equal']); self.assertFalse(out['identity_equal'])
        self.assertEqual(out['classification'], 'incompatible_or_unsupported_correspondence')

    def test_intersection_without_topology(self):
        r = record(); r['reference'].update(feature_id='2', node_ids=[40, 50], coordinates=[[.001, 50.999], [.001, 51.001]])
        out = compare(r); self.assertEqual(out['classification'], 'incompatible_or_unsupported_correspondence')
        self.assertFalse(out['geometry']['compatible_extents_screen'])

    def test_missing_version_stays_missing(self):
        out = compare(record()); self.assertIsNone(out['source']['version']); self.assertIn('version', out['source']['missing_metadata'])

    def test_missing_route_identity(self):
        r = record(); r['source']['route_memberships'] = None
        out = compare(r); self.assertEqual(out['route_identity']['status'], 'unknown')
        self.assertTrue(out['manual_review']); self.assertEqual(out['attribution_eligibility'], 'not_assessed_no_entitlement')

    def test_missing_feature_identity(self):
        r = record(); r['reference']['feature_id'] = None
        self.assertEqual(compare(r)['classification'], 'insufficient_evidence')

    def test_private_access(self):
        r = record(); r['reference']['tags'] = {'access': 'private'}
        self.assertEqual(compare(r)['classification'], 'incompatible_or_unsupported_correspondence')

    def test_explicit_mode_exclusion(self):
        r = record(); r['mode'] = 'cycling'
        self.assertEqual(compare(r)['classification'], 'incompatible_or_unsupported_correspondence')

    def test_walking_does_not_prove_cycling(self):
        r = record(); r['mode'] = 'cycling'
        for f in (r['source'], r['reference']): f['modes'] = None
        out = compare(r); self.assertEqual(out['mode_access']['reference'], 'unknown')
        self.assertIn('mode_access_unknown_do_not_infer_cycling_from_walking', out['review_reasons'])

    def test_incompatible_extent_same_id(self):
        r = record(); r['reference']['coordinates'] = [[.01, 51], [.011, 51], [.012, 51]]
        self.assertEqual(compare(r)['classification'], 'incompatible_or_unsupported_correspondence')

    def test_deferred_mask(self):
        r = record(); r['context'] = {'deferred': True}
        out = compare(r); self.assertIn('deferred_mask_retained', out['review_reasons']); self.assertFalse(out['new_completion_credit'])

    def test_invalid_geometry_and_node_mapping(self):
        for invalid in ([[0, 91], [1, 51]], [[0, 51], [float('nan'), 51]], [[0, 51], [0, 51]]):
            r = record(); r['reference']['coordinates'] = invalid
            with self.assertRaises(ValueError): compare(r)
        r = record(); r['reference']['node_ids'] = [10, 20]
        with self.assertRaises(ValueError): compare(r)

    def test_normalized_orientation_and_precision(self):
        points = [[0, 51.0], [.001000000001, 51.0], [.001, 51.0], [.002, 51.0]]
        self.assertEqual(normalized_hash(points), normalized_hash([[.002, 51.0], [.001, 51.0], [0, 51.0]]))

    def test_output_does_not_overwrite_input(self):
        result = subprocess.run([sys.executable, '-m', 'scripts.ncn_source_compatibility', '--input', str(FIXTURE), '--output', str(FIXTURE)], cwd=ROOT, capture_output=True)
        self.assertNotEqual(result.returncode, 0); self.assertIn(b'Output must differ', result.stderr)

    def test_work_limit_fails_explicitly(self):
        r = record(); r['reference']['coordinates'] = [[10, 51], [20, 51], [30, 51]]
        with self.assertRaisesRegex(ValueError, 'limit'): compare(r)


class PinnedTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.fixture = json.loads(FIXTURE.read_text()); cls.output = run(FIXTURE)
        cls.results = {r['key']: r for r in cls.output['results']}

    def test_fixture_reconstruction(self):
        self.assertEqual(prepare(ROOT), self.fixture)

    def test_repeat_deterministic_and_no_mutation(self):
        before = copy.deepcopy(self.fixture)
        for r in self.fixture['comparisons']: compare(r)
        self.assertEqual(self.fixture, before); self.assertEqual(run(FIXTURE), self.output)

    def test_east_sussex(self):
        r = self.results['1289002662']
        self.assertEqual(r['classification'], 'same_identified_feature_with_documented_geometry_update')
        self.assertEqual(set(r['history']['changed_shared_nodes']), {6925348794, 6925348793})
        self.assertAlmostEqual(r['geometry']['max_corresponding_vertex_displacement_m'], 1.5051875383180653, delta=.002)
        self.assertIsNone(r['reference']['version']); self.assertEqual(r['context']['binding_count'], 0)
        self.assertIn('proposal_note_does_not_confirm_route_membership', r['review_reasons'])

    def test_newry_and_mid_wales(self):
        for key in ('59306268', '31252441'):
            self.assertEqual(self.results[key]['classification'], 'exact_match')
            self.assertEqual(self.results[key]['route_identity']['status'], 'supported_bounded_membership')

    def test_hairpin_negative(self):
        r = self.results['44807950']; self.assertEqual(r['classification'], 'exact_match')
        self.assertIn('hairpin_or_parallel_target_ambiguity_retained', r['review_reasons'])
        self.assertLess(r['context']['outward_target_angle_deg'], 15)

    def test_london_source_extent(self):
        r = self.results['1050757473']
        self.assertEqual(r['context']['admitted_vertex_ranges'], [[1, 2]])
        self.assertFalse(r['context']['requested_span_within_admitted_section'])
        self.assertIn('requested_extent_outside_preserved_admission', r['review_reasons'])
        changed = copy.deepcopy(next(r for r in self.fixture['comparisons'] if r['key'] == '1050757473'))
        changed['context']['requested_vertex_range'] = [1, 2]
        self.assertTrue(compare(changed)['context']['requested_span_within_admitted_section'])

    def test_cumbria_identity(self):
        r = self.results['23385711']; self.assertTrue(r['geometry']['exact_equal'])
        self.assertEqual(r['route_identity']['status'], 'unproved_requested_route')
        self.assertFalse(r['context']['admitted_ncn_source_present'])

    def test_bradford_direction(self):
        r = self.results['159311690']; self.assertEqual(r['classification'], 'exact_match')
        angles = r['context']['target_alignment']['segment_angle_differences_deg']
        self.assertGreater(angles[0], 15); self.assertGreater(angles[1], 15)
        self.assertAlmostEqual(angles[0], 16.6, delta=.2)
        self.assertIn('target_direction_evidence_exceeds_unchanged_15_degree_gate', r['review_reasons'])

    def test_deal_extent_and_private_parallel(self):
        r = self.results['4471747']; self.assertFalse(r['context']['target_alignment']['compatible_extents_screen'])
        r = self.results['deal-private-parallel']
        self.assertEqual(r['classification'], 'incompatible_or_unsupported_correspondence')
        self.assertEqual(r['mode_access']['reference'], 'incompatible'); self.assertFalse(r['identity_equal'])

    def test_no_credit_and_no_import_version_recovery(self):
        self.assertEqual(self.output['examined_feature_count'], 9)
        self.assertEqual(self.output['comparison_count'], 9)
        self.assertEqual(self.output['documented_geometry_update_count'], 1)
        self.assertEqual(self.output['original_import_feature_versions_recovered'], 0)
        self.assertTrue(all(not r['new_completion_credit'] and r['attribution_eligibility'] == 'not_assessed_no_entitlement' for r in self.output['results']))

    def test_existing_pins_unchanged(self):
        import hashlib
        for path, pin in PINS.items(): self.assertEqual(hashlib.sha256((ROOT/path).read_bytes()).hexdigest(), pin)

    def test_all_preserved_hash_conventions(self):
        from scripts.ncn_source_compatibility import digest
        receipt = json.loads((ROOT/'docs/data-trials/ncn-post-seam-source-provenance-20261030.json').read_text())
        for sample in receipt['samples']:
            result = self.results[sample['osm_way_id']]
            self.assertEqual(result['source']['consumer_normalized_geometry_sha256'], sample['current_source_geometry_sha256'])
            self.assertEqual(result['reference']['consumer_normalized_geometry_sha256'], sample['walking_consumer_source_geometry_sha256'])
            self.assertEqual(result['reference']['ordered_geometry_sha256'], sample['walking_ordered_coordinate_sha256'])

    def test_pin_tamper_rejected(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp); first = next(iter(PINS)); (root/first).parent.mkdir(parents=True)
            (root/first).write_text('{}')
            with self.assertRaisesRegex(ValueError, 'Pinned research input changed'): prepare(root)


if __name__ == '__main__':
    unittest.main()
