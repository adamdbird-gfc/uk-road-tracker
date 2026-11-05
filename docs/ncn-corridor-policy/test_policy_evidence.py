"""Bounded evidence regressions; no production matcher or entitlement changes."""
import copy
from collections import defaultdict
import importlib.util
import json
from pathlib import Path
import unittest

from shapely.geometry import LineString
from scripts.ncn_trial.open_completion import POLICY, edge_interval
from scripts.ncn_trial.journey_replay import projector
from matched_path_evidence import digest
from scripts.ncn_binding_attainability import network_intervals
from scripts.ncn_trial.coverage import merge_intervals

HERE = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location('corridor_analysis', HERE/'analyse.py')
analysis = importlib.util.module_from_spec(spec)
spec.loader.exec_module(analysis)


class CorridorEvidenceTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.evidence = json.loads((HERE/'evidence.json').read_text())
        cls.results = analysis.findings(cls.evidence)
        cls.byway = {c['way']: c for c in cls.evidence['cases']}
        cls.measures = {c['way']: c for c in cls.results['cases']}

    def test_exact_original_target_keys_and_topology(self):
        for c in self.evidence['cases']:
            for t in c['targets']:
                self.assertEqual(t['key'], 'open-edge:'+digest(t['metric_coordinates']))
                self.assertTrue(all(d >= 1 for d in t['endpoint_degrees']))

    def test_selected_edges_are_exact_source_occurrences(self):
        count = 0
        for c in self.evidence['cases']:
            for r in c['candidates']:
                i=r['source_edge']
                self.assertEqual(r['source_coordinates'], c['source']['coordinates'][i:i+2])
                self.assertEqual(r['source_geometry_sha256'],c['source']['source_geometry_sha256'])
                self.assertEqual(r['candidate_edge_within_admitted_section'],analysis.admitted(c['source'],i))
                count += 1
        self.assertEqual(count,50)

    def test_reproduces_without_mutating_evidence(self):
        before=copy.deepcopy(self.evidence)
        self.assertEqual(analysis.findings(self.evidence),self.results)
        self.assertEqual(self.evidence,before)
        self.assertEqual(self.results,json.loads((HERE/'findings.json').read_text()))

    def test_chapel_lane_does_not_silently_extrapolate(self):
        r=self.measures['4471919']['pieces'][0]
        self.assertAlmostEqual(r['local_mean_gap_to_candidate_edge_m'],.930475645,delta=.00001)
        self.assertAlmostEqual(r['local_sampled_max_gap_to_candidate_edge_m'],1.943654137,delta=.00001)
        self.assertGreater(r['target_end_unclipped_station_on_candidate_edge_m'],r['candidate_edge_ground_length_m']+1.2)
        c=self.byway['4471919'];project=projector({'projection_origin':c['origin']})
        target=next(t for t in c['targets'] if t['selected'])
        self.assertIsNone(edge_interval(*[project(x) for x in c['source']['coordinates'][24:26]],LineString(target['metric_coordinates'])))

    def test_chapel_source_junction_is_connected_but_not_an_approved_anchor(self):
        c=self.byway['4471919'];node=c['source']['osm_node_ids'][-1]
        neighbours=[p for p in c['source_endpoint_neighbours'] if p['way_id']!='4471919' and node in p['osm_node_ids']]
        self.assertEqual([p['way_id'] for p in neighbours],['169004647'])
        self.assertEqual(node,27412821)
        self.assertEqual(next(t for t in c['targets'] if t['selected'])['endpoint_degrees'],[2,2])
        self.assertFalse(self.measures['4471919']['pieces'][0]['new_binding_approved'])

    def test_bradford_does_not_bypass_three_way_junction_or_angle_gate(self):
        c=self.byway['159311690'];r=self.measures['159311690']
        self.assertEqual(c['origin'],[-1.238694665665012,53.734628280093446])
        self.assertAlmostEqual(r['gross_route_local_screen_m'],62.114800383885736)
        project=projector({'projection_origin':c['origin']})
        for row in c['candidates']:
            i=row['source_edge'];target=next(t for t in c['targets'] if t['key']==row['target_key'])
            self.assertIn(3,target['endpoint_degrees'])
            self.assertIsNone(edge_interval(*[project(x) for x in c['source']['coordinates'][i:i+2]],LineString(target['metric_coordinates'])))

    def test_sussex_nearest_is_a_crossing_not_a_correspondence(self):
        r=self.measures['1289002662'];c=self.byway['1289002662']
        self.assertEqual(c['source']['section_keys'],['1289002662:0:1'])
        self.assertEqual(r['original_source_binding_count'],0)
        self.assertLess(r['nearest_original_target']['local_distance_m'],1.3)
        self.assertGreater(r['nearest_original_target']['angle_to_admitted_source_edge_degrees'],89.)
        project=projector({'projection_origin':c['origin']})
        target=next(t for t in c['targets'] if t['selected'])
        self.assertIsNone(edge_interval(*[project(x) for x in c['source']['coordinates'][:2]],LineString(target['metric_coordinates'])))

    def test_derry_full_way_does_not_extend_finite_admission(self):
        c=self.measures['30268009']
        self.assertEqual((c['admitted_piece_count'],c['unadmitted_piece_count']),(3,9))
        self.assertAlmostEqual(c['admitted_screen_route_local_m'],28.442654851136275)
        self.assertAlmostEqual(c['unadmitted_screen_route_local_m'],2026.002758420773)

    def test_highland_queue_is_outside_admitted_end_span(self):
        c=self.measures['463641622']
        self.assertEqual((c['admitted_piece_count'],c['unadmitted_piece_count']),(0,35))
        self.assertEqual(c['source_section_keys'],['463641622:14:16'])
        self.assertAlmostEqual(c['gross_route_local_screen_m'],820.9411799680848)

    def test_ground_measurement_agrees_with_independent_geodesic(self):
        for c in self.results['cases']:
            self.assertAlmostEqual(c['source_length_ground_m'],c['source_length_geodesic_m'],delta=.0001)

    def test_shared_routes_and_repeated_alternatives_union_one_network_interval(self):
        # Synthetic accounting control; this does not assert Cumbria membership.
        network=defaultdict(list)
        for route in ('NCN:6','NCN:7'):
            target={'key':route+':shared','network':[[42,0.,10.,10.]]}
            network_intervals([target],{target['key']:[(0.,10.),(2.,8.)]},network)
        self.assertEqual(merge_intervals(network[42]),[(0.,10.)])
        self.assertEqual(sum(z-a for a,z in merge_intervals(network[42])),10.)

    def test_national_policy_and_new_gain_remain_unchanged(self):
        self.assertEqual((POLICY['maximum_offset_m'],POLICY['maximum_angle_degrees']),(10.,15.))
        self.assertFalse(POLICY['endpoint_extrapolation'])
        self.assertFalse(POLICY['changes_travelled_geometry'])
        self.assertEqual(self.results['confirmed_new_unique_network_m'],0.)
        self.assertEqual(self.results['confirmed_new_route_local_m'],0.)
        self.assertEqual(self.results['assessability_delta_percentage_points'],0.)
        self.assertIsNone(self.results['potential_unique_network_recovery_m'])


if __name__=='__main__':
    unittest.main()
