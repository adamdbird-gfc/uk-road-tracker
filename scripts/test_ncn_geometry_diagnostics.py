"""Offline controls, provenance, boundaries and legacy receipt equivalence."""
from copy import deepcopy
import json
import math
from pathlib import Path
import tempfile
import unittest
from shapely.geometry import LineString
from scripts import ncn_geometry_diagnostics as d
from scripts.ncn_trial.open_completion import edge_interval


class GeometryDiagnosticsTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.fixture = json.loads((d.ROOT/d.CASE_PATH).read_bytes())
        cls.cases = {c['key']:c for c in cls.fixture['cases']}
        cls.report = d.run()
        cls.rows = {r['key']:r for r in cls.report['cases']}
        cls.old = json.loads((d.ROOT/'docs/data-trials/ncn-projection-results-20261030.json').read_bytes())

    def test_nine_cases_and_determinism(self):
        self.assertEqual(9, len(self.rows))
        self.assertEqual(d.dumps(self.report),d.dumps(d.run()))

    def test_input_objects_not_mutated(self):
        for c in self.fixture['cases']:
            before = deepcopy(c)
            d.report_case(c)
            self.assertEqual(before,c)

    def test_operational_records_identical(self):
        for key,c in self.cases.items():
            record = self.rows[key]['operational_result']['record']
            for name in ('actual_admitted_source_present','existing_binding_count','compatibility','eligibility_note','history_status','admitted_vertex_ranges'):
                self.assertEqual(c.get(name),record[name])
            self.assertEqual(0,self.rows[key]['attribution_eligibility']['new_completion_credit_m'])
            self.assertFalse(self.rows[key]['diagnostic_comparison']['exact_operational_replay'])

    def test_missing_origins_never_substituted(self):
        for key in ('159311690','23385711'):
            comparison = self.rows[key]['diagnostic_comparison']
            self.assertIsNone(comparison['hypothetical_alternative_projection_changes'])
            for label in ('native_admission_aeqd','native_correspondence_eq'):
                m = comparison['measurements'][label]
                self.assertEqual('unavailable',m['projection']['origin_status'])
                self.assertIsNone(m['projection']['origin_lon_lat'])
                self.assertIsNone(m['values'])
            self.assertIsNotNone(comparison['measurements']['common_local_eq']['values'])

    def test_inferred_origins_remain_inferred(self):
        for key in ('59306268','31252441','44807950'):
            c = self.rows[key]['diagnostic_comparison']
            self.assertEqual('inferred',c['origin_comparison_status'])
            self.assertFalse(c['exact_operational_replay'])
            self.assertEqual('inferred',c['measurements']['native_correspondence_eq']['projection']['origin_status'])

    def test_unknown_origin_evidence_fails_closed(self):
        c = deepcopy(self.cases['4471747']);c['origin_provenance']='assumed'
        with self.assertRaises(ValueError): d.report_case(c)
        with self.assertRaises(ValueError): d.projection('spherical_eq',[0,0],'unavailable','missing')
        with self.assertRaises(ValueError): d.projection('spherical_eq',None,'verified','missing')

    def test_deal_classification_changes_do_not_admit(self):
        c = self.rows['4471747']
        self.assertEqual({'finite_pair_presence':3,'whole_edge_corridor':5},c['diagnostic_comparison']['hypothetical_alternative_projection_changes'])
        spans = c['operational_result']['record']['reviewed_source_spans']
        self.assertEqual([[0,6],[11,13]],[[s['from_vertex'],s['to_vertex']] for s in spans])
        self.assertEqual(0,c['attribution_eligibility']['new_assessable_m'])

    def test_private_exclusion_survives_projection_flip(self):
        c = self.rows['deal-private-parallel']
        self.assertEqual(1,c['diagnostic_comparison']['hypothetical_alternative_projection_changes']['finite_pair_presence'])
        self.assertFalse(c['operational_result']['record']['actual_admitted_source_present'])
        self.assertEqual('incompatible',c['restrictions']['mode_access']['reference'])

    def test_controls_retain_outcomes(self):
        for key in ('59306268','31252441'):
            c = self.rows[key]
            self.assertTrue(c['operational_result']['record']['actual_admitted_source_present'])
            for name in ('native_admission_aeqd','native_correspondence_eq'):
                self.assertEqual(2,sum(p['finite_pair_present'] for p in c['diagnostic_comparison']['measurements'][name]['values']['finite_target_pairs']))
        for name,m in self.rows['44807950']['diagnostic_comparison']['measurements'].items():
            self.assertTrue(m['values']['topology']['ordinary_seam_rejected'],name)
        self.assertEqual([[1,2]],self.rows['1050757473']['operational_result']['record']['admitted_vertex_ranges'])
        self.assertTrue(all(not p['finite_pair_present'] for p in self.rows['159311690']['diagnostic_comparison']['measurements']['common_local_eq']['values']['finite_target_pairs']))
        self.assertFalse(self.rows['23385711']['operational_result']['record']['actual_admitted_source_present'])
        self.assertEqual('unproved_requested_route',self.rows['23385711']['restrictions']['route_identity']['status'])
        self.assertEqual('documented_shared_node_movement',self.rows['1289002662']['operational_result']['record']['history_status'])
        self.assertEqual(0,self.rows['1289002662']['operational_result']['record']['existing_binding_count'])

    def test_all_measurements_match_pinned_research(self):
        for old in self.old['cross_route']:
            new = self.rows[old['key']]['diagnostic_comparison']['measurements']
            for before,after in [('route_aeqd','native_admission_aeqd'),('route_eq','native_correspondence_eq'),('local_eq','common_local_eq')]:
                if before not in old['spaces']: continue
                a,b = old['spaces'][before],new[after]['values']
                self.assertAlmostEqual(a['source_length_m'],b['source_length_m'],delta=1e-7)
                self.assertAlmostEqual(a['source_reference_hausdorff_m'],b['continuous_source_reference_hausdorff_m'],delta=1e-7)
                self.assertEqual(len(a['target_edge_pairs']),len(b['finite_target_pairs']))
                for x,y in zip(a['target_edge_pairs'],b['finite_target_pairs']):
                    self.assertEqual(x['finite_pair'] is not None,y['finite_pair_present'])
                    for k,l in [('min_distance_m','min_distance_m'),('max_distance_m','max_endpoint_distance_m'),('angle_deg','direction_deg')]:
                        self.assertAlmostEqual(x[k],y[l],delta=1e-7)

    def test_projection_change_changes_measurements_only(self):
        c = deepcopy(self.cases['4471747'])
        before = d.report_case(c)
        c['operational_origin'] = c['source'][0]
        after = d.report_case(c)
        self.assertEqual(before['operational_result'],after['operational_result'])
        self.assertEqual(before['attribution_eligibility'],after['attribution_eligibility'])
        self.assertNotEqual(before['diagnostic_comparison']['measurements'],after['diagnostic_comparison']['measurements'])

    def test_synthetic_distance_boundary_existing_predicate(self):
        # Deliberately synthetic planar inputs, never geographic recovery evidence.
        target = LineString([(0,0),(100,0)])
        for offset,present in ((9.999999,True),(10.,True),(10.000001,False)):
            self.assertEqual(present,edge_interval((0,offset),(100,offset),target) is not None)

    def test_synthetic_angle_boundary_existing_predicate(self):
        target = LineString([(-100,0),(100,0)])
        for degrees,present in ((14.999999,True),(15.,True),(15.000001,False)):
            theta = math.radians(degrees)
            a,z = (-math.cos(theta),-math.sin(theta)),(math.cos(theta),math.sin(theta))
            self.assertEqual(present,edge_interval(a,z,target) is not None)

    def test_pin_mismatch_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            p=root/d.CASE_PATH;p.parent.mkdir(parents=True);p.write_text('{}')
            with self.assertRaisesRegex(ValueError,'Pinned input changed'): d.run(root)

    def test_no_general_blocker_or_entitlement(self):
        self.assertFalse(self.report['operational_changes'])
        self.assertEqual(0,self.report['new_assessable_m'])
        for r in self.rows.values():
            self.assertFalse(r['further_investigation']['blocks_ordinary_development_or_publication_review'])
            self.assertEqual('not_assessed_no_entitlement',r['attribution_eligibility']['status'])


if __name__ == '__main__': unittest.main()
