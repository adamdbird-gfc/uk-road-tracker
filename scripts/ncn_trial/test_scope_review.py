from copy import deepcopy
import unittest
import json
import tempfile
from pathlib import Path
from shapely.geometry import shape
from .scope_review import review_scope, inventory_manifests, inverse_projector
from .journey_replay import projector


def inputs():
    coordinates = [[0, 0], [.001, 0]]
    plan = {'route_key': 'NCN:1', 'scope': 'NCN:1:source-inventory',
        'whole_published_route_ready': False, 'reference_version': 'test',
        'metadata': {'projection_origin': [0, 0]},
        'sections': [{'key': 'inventory:7', 'coordinates': coordinates},
                     {'key': '7', 'coordinates': coordinates}],
        'unmapped_inventory_m': 0, 'canonical_inventory_length_m': 111.195,
        'maximum_supported_coverage_m': {'on_foot': 111.195, 'cycling': 0, 'combined': 111.195}}
    assoc = {'paths': [{'section_keys': ['7'], 'modes': ['on_foot']}]}
    official = {'type': 'FeatureCollection', 'features': [{'type': 'Feature',
        'properties': {'FID': 1, 'SegmentID': 2, 'RouteType': 'NCN', 'RouteNo': 1,
            'RouteCat': 'Main Route', 'OpenStatus': 'Open', 'Desc_': 'OnRoad'},
        'geometry': {'type': 'LineString', 'coordinates': coordinates}}]}
    return plan, assoc, official


class ScopeReviewTests(unittest.TestCase):
    def test_perfect_geometry_still_cannot_certify_published_scope(self):
        report, features = review_scope(*inputs())
        self.assertEqual(report['review_reasons'], [])
        self.assertFalse(report['whole_published_route_ready'])
        self.assertFalse(report['association_approved_by_review'])
        self.assertTrue(report['published_scope_requires_independent_review'])
        self.assertEqual(features['features'], [])
        self.assertGreater(report['corridor_diagnostics'][1]['official_open_main_without_eligible_m']['cycling'], 100)

    def test_short_source_fragment_exposes_official_gap_with_identity(self):
        plan, assoc, official = inputs()
        official['features'][0]['geometry']['coordinates'] = [[0, 0], [.002, 0]]
        report, features = review_scope(plan, assoc, official)
        self.assertIn('official_geometry_outside_source_corridor', report['review_reasons'])
        gaps = [f for f in features['features'] if f['properties']['exception'] == 'official_open_main_without_source']
        self.assertEqual(gaps[0]['properties']['SegmentID'], 2)
        self.assertGreater(gaps[0]['properties']['length_m'], 100)
        self.assertGreater(shape(gaps[0]['geometry']).bounds[0], .001)

    def test_no_eligible_paths_keeps_source_and_reports_binding_gap(self):
        plan, assoc, official = inputs()
        assoc['paths'] = []
        plan['unmapped_inventory_m'] = plan['canonical_inventory_length_m']
        report, features = review_scope(plan, assoc, official)
        self.assertIn('inventory_matching_gaps', report['review_reasons'])
        self.assertEqual(report['corridor_diagnostics'][1]['official_open_main_without_source_m'], 0)
        self.assertGreater(report['corridor_diagnostics'][1]['official_open_main_without_eligible_m']['combined'], 100)
        gaps = [f for f in features['features'] if f['properties']['exception'] == 'source_without_eligible_binding']
        self.assertEqual(gaps[0]['properties']['way_id'], 7)

    def test_offset_series_never_grants_credit(self):
        plan, assoc, official = inputs()
        official['features'][0]['geometry']['coordinates'] = [[0, .00015], [.001, .00015]]
        report, _ = review_scope(plan, assoc, official)
        self.assertGreater(report['corridor_diagnostics'][1]['official_open_main_without_source_m'], 100)
        self.assertEqual(report['corridor_diagnostics'][2]['official_open_main_without_source_m'], 0)
        self.assertIn('offset_sensitive_official_geometry', report['review_reasons'])
        self.assertNotIn('official_geometry_gap_persists_at_25m', report['review_reasons'])
        self.assertFalse(report['corridors_grant_progress'])
        self.assertFalse(report['whole_published_route_ready'])

    def test_official_closed_or_link_sections_are_preserved_separately(self):
        plan, assoc, official = inputs()
        closed = deepcopy(official['features'][0])
        closed['properties'].update(FID=3, OpenStatus='Proposed')
        closed['geometry']['coordinates'] = [[.002, 0], [.003, 0]]
        official['features'].append(closed)
        report, features = review_scope(plan, assoc, official)
        self.assertIn('official_nonqualifying_scope_present', report['review_reasons'])
        self.assertAlmostEqual(report['official_open_main_union_m'], 111.195, places=2)
        self.assertEqual(len(report['official_feature_groups']), 2)
        self.assertTrue(any(f['properties']['exception'] == 'official_nonqualifying_scope' and f['properties']['FID'] == 3 for f in features['features']))

    def test_duplicate_official_features_do_not_inflate_union_totals(self):
        plan, assoc, official = inputs()
        official['features'].append(deepcopy(official['features'][0]))
        report, _ = review_scope(plan, assoc, official)
        self.assertAlmostEqual(report['official_open_main_union_m'], 111.195, places=2)
        self.assertEqual(report['official_feature_groups'][0]['feature_count'], 2)

    def test_missing_or_nonqualifying_official_extent_cannot_look_complete(self):
        plan, assoc, official = inputs()
        official['features'][0]['properties']['OpenStatus'] = 'Closed'
        report, features = review_scope(plan, assoc, official)
        self.assertIn('no_official_open_main_extent', report['review_reasons'])
        self.assertGreater(report['corridor_diagnostics'][1]['source_outside_official_open_main_m'], 100)
        official['features'] = []
        with self.assertRaises(ValueError):
            review_scope(plan, assoc, official)

    def test_wrong_scope_and_inventory_binding_rejected_without_mutation(self):
        values = inputs()
        original = deepcopy(values)
        review_scope(*values)
        self.assertEqual(values, original)
        values[0]['scope'] = 'NCN:1:published-route'
        with self.assertRaises(ValueError):
            review_scope(*values)
        values = inputs()
        values[1]['paths'][0]['section_keys'] = ['inventory:7']
        with self.assertRaises(ValueError):
            review_scope(*values)

    def test_missing_manifest_or_duplicate_inventory_cannot_silently_shorten_batch(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            summary = {'routes': [{'route_key': 'NCN:1'}, {'route_key': 'NCN:2'}]}
            (root / 'summary.json').write_text(json.dumps(summary))
            (root / 'NCN-1').mkdir()
            (root / 'NCN-1/manifest.json').write_text('{}')
            with self.assertRaises(ValueError):
                inventory_manifests(root)
            (root / 'NCN-2').mkdir()
            (root / 'NCN-2/manifest.json').write_text('{}')
            self.assertEqual(len(inventory_manifests(root)), 2)
            summary['routes'].append({'route_key': 'NCN:2'})
            (root / 'summary.json').write_text(json.dumps(summary))
            with self.assertRaises(ValueError):
                inventory_manifests(root)

    def test_exception_geometry_returns_to_wgs84_at_scottish_latitude(self):
        origin = [-3.2, 58.5]
        original = [-3.16, 58.53]
        point = inverse_projector(origin)(*projector({'projection_origin': origin})(original))
        self.assertAlmostEqual(point[0], original[0], places=10)
        self.assertAlmostEqual(point[1], original[1], places=10)


if __name__ == '__main__':
    unittest.main()
