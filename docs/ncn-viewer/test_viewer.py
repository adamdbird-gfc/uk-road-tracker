"""Evidence-contract regressions; browser checks are separately executable."""
import copy
import hashlib
import importlib.util
import json
import math
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

HERE = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location('viewer_generate', HERE/'generate.py')
gen = importlib.util.module_from_spec(spec)
spec.loader.exec_module(gen)


class ViewerEvidenceTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.data = gen.build()
        cls.inputs = gen.load_inputs()
        cls.deal = cls.data['cases'][0]
        cls.edges = [f for f in cls.deal['features'] if 'edge_index' in f['properties']]

    def test_original_coordinates_and_node_chain(self):
        fixture = self.inputs[0]
        for i, f in enumerate(self.edges):
            self.assertEqual(f['geometry']['coordinates'], fixture['path']['coordinates'][i:i+2])
            self.assertEqual(f['properties']['osm_node_ids'], fixture['path']['osm_node_ids'][i:i+2])
        focus=next(f for f in self.deal['features'] if f['properties']['layer']=='focus_target')
        self.assertEqual(focus['geometry']['coordinates'],fixture['target_original']['coordinates'])

    def test_operational_spans_not_replaced_by_hypothetical_passes(self):
        expected = list(range(6))+[11,12]
        self.assertEqual([f['properties']['edge_index'] for f in self.edges if f['properties']['status']=='admitted'],expected)
        self.assertEqual([f['properties']['edge_index'] for f in self.edges if f['properties']['classification_changed']],[6,7,8,9,10])
        for f in self.edges:
            p=f['properties']; self.assertTrue(p['measurements']['completion_space']['whole_edge_approved_corridor_only'])
            self.assertEqual(p['measurements']['native_admission']['actual_admitted'],p['status']=='admitted')
        self.assertEqual(self.deal['demonstrated_additional_eligible_recovery_m'],0)

    def test_resolution_preserves_history_and_exposes_identity_access_and_separation(self):
        for f in self.edges:
            r=f['properties']['resolution_review']
            self.assertEqual(r['verdict'],'Admit')
            self.assertIn('NCN1 relation 75880',r['identity'])
            self.assertIn('designated',r['access'])
            self.assertEqual(r['thresholds']['operational_corridor_m'],10)
            self.assertEqual(r['thresholds']['operational_angle_degrees'],15)
            self.assertGreater(len(r['evidence_sources']),1)
        private=next(f for f in self.deal['features']if f['id']=='77611523')
        self.assertEqual(private['properties']['resolution_review']['verdict'],'Exclude')
        self.assertAlmostEqual(self.deal['resolution_delta']['unique_network_added_m'],561.8887365344943,places=6)

    def test_offsets_and_coverage_are_the_original_receipt(self):
        original=self.inputs[1]
        for i,f in enumerate(self.edges):
            self.assertEqual(f['properties']['station_m'],original['source_positions_m'][i:i+2])
        for a,b in zip(self.edges,self.edges[1:]):
            self.assertEqual(a['properties']['station_m'][1],b['properties']['station_m'][0])
        self.assertAlmostEqual(self.edges[11]['properties']['station_m'][0],835.1270332469487,places=9)
        self.assertEqual(self.deal['target_length_m'],495.388143301922)
        self.assertEqual(self.deal['existing_target_coverage_m'],original['existing_target_coverage_m'])
        self.assertGreater(self.deal['historical_geometric_ceiling_m'],445)

    def test_private_control_retains_identity_and_exclusion(self):
        f=next(f for f in self.deal['features'] if f['id']=='77611523')
        self.assertEqual(f['properties']['status'],'excluded')
        self.assertEqual(f['properties']['tags']['access'],'private')
        m=f['properties']['measurements']
        self.assertFalse(m['eligible'])
        self.assertGreater(m['admission_projection_min_distance_m'],10)
        self.assertLess(m['completion_projection_min_distance_m'],10)
        self.assertEqual(f['geometry']['coordinates'],self.inputs[0]['compatibility_controls'][1]['reference']['coordinates'])

    def test_corridor_ring_coordinates_are_reversible_pinned_transforms(self):
        audit=gen.audit_module();fixture=self.inputs[0]
        for space in ['aeqd','eq']:
            f=next(f for f in self.deal['features'] if f['id']=='corridor:'+space)
            self.assertIn('Derived:',f['properties']['coordinate_provenance'])
            coordinates=f['geometry']['coordinates']
            if f['geometry']['type']=='Polygon': coordinates=[coordinates]
            for polygon in coordinates:
                for ring in polygon:
                    for point in ring[::max(1,len(ring)//12)]:
                        xy=audit.project([point],fixture['projection_origin'],space)[0]
                        actual=gen.inverse(*xy,fixture['projection_origin'],space)
                        self.assertLess(math.dist(point,actual),1e-10)

    def test_additional_geometries_are_pinned_and_unknowns_stay_unknown(self):
        cases={c['key']:c for c in self.inputs[2]['cases']}
        self.assertEqual(len(self.data['cases']),8)
        for c in self.data['cases'][1:]:
            source=next(f for f in c['features'] if f['id'].startswith('source:'))
            pinned=cases[c['key']]
            self.assertEqual(source['geometry']['coordinates'],pinned['source'])
            self.assertEqual(source['properties']['status'],'unavailable')
            self.assertEqual(source['properties']['evidence']['operational_origin'],pinned['operational_origin'])
            for t in [f for f in c['features'] if f['properties']['layer']=='target']:
                self.assertIn(t['geometry']['coordinates'],[chain for g in pinned['target_pairs'] for chain in g['targets']])
        for key in ['159311690','23385711']:
            self.assertIsNone(cases[key]['operational_origin'])
        self.assertTrue(next(c for c in self.data['cases'] if c['key']=='1289002662')['target_unavailable'])

    def test_every_feature_preserves_provenance_and_reason(self):
        for c in self.data['cases']:
            ids=set()
            for f in c['features']:
                self.assertNotIn(f['id'],ids);ids.add(f['id'])
                for key in ['source_id','route_id','source_reference','geometry_sha256','coordinate_provenance','status','reason_code','limitations']:
                    self.assertTrue(f['properties'][key],(f['id'],key))

    def test_pin_changes_fail_closed(self):
        bad=copy.copy(gen.PINS);bad[next(iter(bad))]='0'*64
        with patch.object(gen,'PINS',bad):
            with self.assertRaisesRegex(ValueError,'Evidence pin mismatch'):
                gen.load_inputs()

    def test_generated_artifacts_are_deterministic_and_network_independent(self):
        with tempfile.TemporaryDirectory() as temp:
            gen.generate(temp)
            for name in ['index.html','evidence.json']:
                self.assertEqual((Path(temp)/name).read_bytes(),(HERE/'demo'/name).read_bytes())
            html=(Path(temp)/'index.html').read_text()
            self.assertNotIn('<script src=',html)
            self.assertNotIn('<link ',html)
            self.assertIn("connect-src 'none'",html)
            self.assertIn('type="application/json"',html)
        self.assertTrue(self.data['diagnostic_only'])


if __name__=='__main__':
    unittest.main()
