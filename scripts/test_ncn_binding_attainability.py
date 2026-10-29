"""Stage 1.1 source-offset and retained-target regressions; no network required."""
import copy
import json
import math
from pathlib import Path
from types import SimpleNamespace
import unittest

from scripts.build_ncn_national import positions

FIXTURE = Path(__file__).resolve().parents[1] / 'tests/fixtures/ncn-binding-attainability/source-sections.json'


class BindingAttainabilityTests(unittest.TestCase):
    def setUp(self):
        self.fixture = json.loads(FIXTURE.read_text())

    def compile(self, row):
        from scripts.build_ncn_national import compile_path_bindings
        plan = dict(row['plan'], physical_sections_metric=row['sections'], bindings=row['bindings'])
        return compile_path_bindings(SimpleNamespace(**row['path']), plan, False)

    def test_pinned_ncn1_partial_source_starts_at_actual_source_vertex(self):
        row = self.fixture['routes'][1]
        result = self.compile(row)
        p = row['path']; pos = positions(p['coordinates'], row['plan']['projection_origin'])
        self.assertGreater(pos[1], 1)
        self.assertTrue(result)
        # The shipped row demonstrates the zero-offset failure, not just a
        # hypothetical compiler input. Its target bindings overlap the prefix.
        self.assertLess(row['published_bindings'][0]['start'], pos[1])
        for b, original in zip(result, row['bindings']):
            self.assertAlmostEqual(b['start'], pos[1] + original['physical_start'], places=7)
            self.assertAlmostEqual(b['end'], pos[1] + original['physical_end'], places=7)
            self.assertEqual((b['low'], b['high']), (original['logical_start'], original['logical_end']))
        # Travelling the unadmitted prefix must not manufacture these target metres.
        self.assertTrue(all(b['start'] >= pos[1] for b in result))
        self.assertTrue(all(b['end'] <= pos[2] for b in result))

    def test_rcn90_missing_source_is_preserved_not_filled_or_removed(self):
        from scripts.ncn_binding_attainability import route_capacity
        row = self.fixture['routes'][0]
        result = route_capacity(row['published_paths'], row['targets'])
        self.assertAlmostEqual(result['combined_m'], 276.8224097022896, places=6)
        self.assertAlmostEqual(result['target_m'], 2934.5068413689805, places=6)
        self.assertAlmostEqual(result['unbound_m'], 2657.684431666691, places=6)
        self.assertFalse(result['attainable'])
        self.assertEqual(self.compile(row), [])  # No binding on this admitted section.

    def test_mode_permissions_and_exact_section_identity_are_required(self):
        row = copy.deepcopy(self.fixture['routes'][1])
        row['sections'][0]['coordinates'][0][0] += 1
        with self.assertRaisesRegex(ValueError, 'source section'):
            self.compile(row)
        row = copy.deepcopy(self.fixture['routes'][1]);row['bindings'][0]['modes'] = ['driving']
        with self.assertRaisesRegex(ValueError, 'mode'):
            self.compile(row)

    def test_multiple_source_occurrences_require_an_explicit_verified_occurrence(self):
        from scripts.build_ncn_national import compile_path_bindings
        path=SimpleNamespace(way_id='42',coordinates=[[0,0],[.001,0],[0,0],[.001,0]],
            modes=['cycling'],section_keys=['42:2:3'])
        metric=[[0.,0.],[6371008.8*math.radians(.001),0.]]
        binding=dict(physical_key='42:2:3',physical_start=0.,physical_end=metric[1][0],
            logical_key='target',logical_start=0.,logical_end=100.,modes=['cycling'])
        plan=dict(projection_origin=[0,0],physical_sections_metric=[dict(key='42:2:3',coordinates=metric)],bindings=[binding])
        compiled=compile_path_bindings(path,plan,False)
        self.assertAlmostEqual(compiled[0]['start'],metric[1][0]*2)
        # A label without verified vertex provenance must not pick an occurrence.
        path.section_keys=['ambiguous'];plan['physical_sections_metric'][0]['key']='ambiguous';binding['physical_key']='ambiguous'
        with self.assertRaisesRegex(ValueError,'source section'):
            compile_path_bindings(path,plan,False)

    def test_unique_network_union_preserves_modes_and_deduplicates_shared_routes(self):
        from scripts.ncn_binding_attainability import route_capacity,network_intervals,target_intervals
        from scripts.ncn_trial.coverage import merge_intervals
        from collections import defaultdict
        targets=[dict(key='target',length=100.,network=[[7,0.,100.,100.]])]
        path=dict(modes=['on_foot','cycling'],bindings=[dict(target='target',low=0.,high=50.,modes=['on_foot']),
            dict(target='target',low=25.,high=100.,modes=['cycling'])])
        result=route_capacity([path,path],targets)
        self.assertEqual((result['on_foot_m'],result['cycling_m'],result['combined_m']),(50,75,100))
        self.assertTrue(result['attainable'])
        network=defaultdict(list)
        for _ in range(2):network_intervals(targets,target_intervals([path],'combined'),network)
        self.assertEqual(merge_intervals(network[7]),[(0,100)])
        path['bindings'][0]['modes']=['driving']
        with self.assertRaisesRegex(ValueError,'mode'):route_capacity([path],targets)

    def test_correction_artifact_serialization_survives_json_roundtrip(self):
        import gzip
        from scripts.ncn_binding_attainability import encoded
        row={'bindings':[{'start':10.,'end':20.,'target':'t','low':0.,'high':10.,'modes':['cycling']}], 'way':'42'}
        persisted=json.loads(encoded(row))
        self.assertEqual(gzip.compress(encoded(row),mtime=0),gzip.compress(encoded(persisted),mtime=0))

    def test_partial_improvements_cannot_pass_full_completion_gate(self):
        from scripts.ncn_binding_attainability import require_attainable
        report={'routes':[{'route_key':'RCN:90','after':{'attainable':False}}], 'after_network':{'attainable':False}}
        with self.assertRaisesRegex(ValueError,'Completion gate blocked: 1 routes'):
            require_attainable(report)
        report['routes'][0]['after']['attainable']=True
        with self.assertRaisesRegex(ValueError,'network attainable=False'):
            require_attainable(report)
        report['after_network']['attainable']=True
        require_attainable(report)


if __name__ == '__main__':
    unittest.main()
